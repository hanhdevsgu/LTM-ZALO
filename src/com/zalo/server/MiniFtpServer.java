package com.zalo.server;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Chương 14/15: FTP Server (File Transfer Protocol - RFC 959)
 * Hỗ trợ 2 kênh TCP tách biệt:
 * - Control Connection (cổng 2121): USER, PASS, PWD, CWD, CDUP, TYPE, PASV, LIST, NLST, RETR, STOR, DELE, SYST, FEAT, NOOP, QUIT
 * - Data Connection (Passive Mode PASV): truyền danh sách tệp và luồng byte tệp tin (ảnh, video, âm thanh, tài liệu)
 */
public class MiniFtpServer implements Runnable {
    public static final int FTP_PORT = 2121;
    private static final String ROOT_DIR_NAME = "data/ftp_files";

    private final UserStore userStore;
    private final Path rootDir;
    private ServerSocket serverSocket;
    private ExecutorService pool;
    private volatile boolean running = true;

    public MiniFtpServer(UserStore userStore) {
        this.userStore = userStore;
        this.rootDir = Paths.get(ROOT_DIR_NAME).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.rootDir);
        } catch (IOException e) {
            System.err.println("[FTP] Không thể tạo thư mục gốc FTP: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(FTP_PORT);
            pool = Executors.newCachedThreadPool();
            System.out.println("[FTP Server] Đang lắng nghe tại cổng " + FTP_PORT + " (Thư mục: " + rootDir + ")");

            while (running && !serverSocket.isClosed()) {
                Socket client = serverSocket.accept();
                pool.submit(new FtpClientSession(client));
            }
        } catch (IOException e) {
            if (running) {
                System.err.println("[FTP Server] Lỗi: " + e.getMessage());
            }
        }
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            if (pool != null) {
                pool.shutdownNow();
            }
        } catch (IOException ignored) {
        }
    }

    private class FtpClientSession implements Runnable {
        private final Socket controlSocket;
        private BufferedReader reader;
        private PrintWriter writer;
        private ServerSocket passiveServer;
        private String pendingUser;
        private boolean authenticated = false;
        private Path currentDir = rootDir;

        FtpClientSession(Socket controlSocket) {
            this.controlSocket = controlSocket;
        }

        @Override
        public void run() {
            try {
                reader = new BufferedReader(new InputStreamReader(controlSocket.getInputStream(), StandardCharsets.UTF_8));
                writer = new PrintWriter(new OutputStreamWriter(controlSocket.getOutputStream(), StandardCharsets.UTF_8), true);

                sendReply(220, "Zalo FTP Server Ready (RFC 959)");

                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty()) continue;

                    int sp = trimmed.indexOf(' ');
                    String cmd = (sp == -1 ? trimmed : trimmed.substring(0, sp)).toUpperCase(Locale.ROOT);
                    String arg = sp == -1 ? "" : trimmed.substring(sp + 1).trim();

                    if ("QUIT".equals(cmd)) {
                        sendReply(221, "Goodbye.");
                        break;
                    }
                    handleCommand(cmd, arg);
                }
            } catch (IOException ignored) {
            } finally {
                closePassive();
                try {
                    controlSocket.close();
                } catch (IOException ignored) {
                }
            }
        }

        private void handleCommand(String cmd, String arg) {
            switch (cmd) {
                case "USER":
                    pendingUser = arg;
                    authenticated = false;
                    sendReply(331, "Username ok, need password.");
                    break;
                case "PASS":
                    if (pendingUser != null && userStore.authenticate(pendingUser, arg)) {
                        authenticated = true;
                        sendReply(230, "User logged in, proceed.");
                    } else {
                        authenticated = false;
                        sendReply(530, "Login incorrect.");
                    }
                    break;
                case "SYST":
                    sendReply(215, "UNIX Type: L8");
                    break;
                case "FEAT":
                    writer.print("211-Features:\r\n UTF8\r\n PASV\r\n SIZE\r\n211 End\r\n");
                    writer.flush();
                    break;
                case "OPTS":
                    sendReply(200, "Options accepted.");
                    break;
                case "NOOP":
                    sendReply(200, "NOOP ok.");
                    break;
                case "TYPE":
                    sendReply(200, "Type set to " + (arg.isEmpty() ? "I" : arg) + ".");
                    break;
                default:
                    if (!authenticated) {
                        sendReply(530, "Please login with USER and PASS.");
                        return;
                    }
                    handleAuthenticatedCommand(cmd, arg);
            }
        }

        private void handleAuthenticatedCommand(String cmd, String arg) {
            switch (cmd) {
                case "PWD":
                case "XPWD":
                    sendReply(257, "\"" + toFtpPath(currentDir) + "\" is current directory.");
                    break;
                case "CWD":
                    Path targetDir = resolveSafePath(arg);
                    if (targetDir != null && Files.isDirectory(targetDir)) {
                        currentDir = targetDir;
                        sendReply(250, "Directory changed to " + toFtpPath(currentDir));
                    } else {
                        sendReply(550, "Failed to change directory.");
                    }
                    break;
                case "CDUP":
                    if (!currentDir.equals(rootDir) && currentDir.getParent() != null) {
                        currentDir = currentDir.getParent();
                    }
                    sendReply(250, "Directory changed to " + toFtpPath(currentDir));
                    break;
                case "PASV":
                    openPassive();
                    break;
                case "LIST":
                case "NLST":
                    handleList("NLST".equals(cmd));
                    break;
                case "SIZE":
                    Path sizePath = resolveSafePath(arg);
                    if (sizePath != null && Files.isRegularFile(sizePath)) {
                        try {
                            sendReply(213, String.valueOf(Files.size(sizePath)));
                        } catch (IOException e) {
                            sendReply(550, "Cannot get file size.");
                        }
                    } else {
                        sendReply(550, "File not found.");
                    }
                    break;
                case "RETR":
                    handleRetr(arg);
                    break;
                case "STOR":
                    handleStor(arg);
                    break;
                case "DELE":
                    Path delPath = resolveSafePath(arg);
                    if (delPath != null && Files.isRegularFile(delPath)) {
                        try {
                            Files.delete(delPath);
                            sendReply(250, "File deleted.");
                        } catch (IOException e) {
                            sendReply(550, "Delete failed.");
                        }
                    } else {
                        sendReply(550, "File not found.");
                    }
                    break;
                case "MKD":
                case "XMKD":
                    Path mkPath = resolveSafePath(arg);
                    if (mkPath != null) {
                        try {
                            Files.createDirectories(mkPath);
                            sendReply(257, "\"" + toFtpPath(mkPath) + "\" created.");
                        } catch (IOException e) {
                            sendReply(550, "Create directory failed.");
                        }
                    } else {
                        sendReply(550, "Invalid path.");
                    }
                    break;
                default:
                    sendReply(502, "Command not implemented.");
            }
        }

        private void openPassive() {
            closePassive();
            try {
                passiveServer = new ServerSocket(0, 1, controlSocket.getLocalAddress());
                passiveServer.setSoTimeout(15000);
                int port = passiveServer.getLocalPort();
                InetAddress addr = controlSocket.getLocalAddress();
                byte[] ip = addr.getAddress();
                if (ip.length != 4) {
                    ip = new byte[]{127, 0, 0, 1};
                }
                int p1 = (port >> 8) & 0xFF;
                int p2 = port & 0xFF;
                sendReply(227, String.format("Entering Passive Mode (%d,%d,%d,%d,%d,%d).",
                        ip[0] & 0xFF, ip[1] & 0xFF, ip[2] & 0xFF, ip[3] & 0xFF, p1, p2));
            } catch (IOException e) {
                sendReply(425, "Cannot open passive connection.");
            }
        }

        private void handleList(boolean namesOnly) {
            if (passiveServer == null) {
                sendReply(425, "Use PASV first.");
                return;
            }
            sendReply(150, "Here comes the directory listing.");
            try (Socket dataSocket = passiveServer.accept();
                 PrintWriter dataOut = new PrintWriter(new OutputStreamWriter(dataSocket.getOutputStream(), StandardCharsets.UTF_8), true)) {
                File[] files = currentDir.toFile().listFiles();
                SimpleDateFormat df = new SimpleDateFormat("MMM dd HH:mm", Locale.ENGLISH);
                if (files != null) {
                    for (File f : files) {
                        if (namesOnly) {
                            dataOut.print(f.getName() + "\r\n");
                        } else {
                            String perm = f.isDirectory() ? "drwxr-xr-x" : "-rw-r--r--";
                            String dateStr = df.format(new Date(f.lastModified()));
                            dataOut.printf("%s 1 zalo zalo %10d %s %s\r\n", perm, f.length(), dateStr, f.getName());
                        }
                    }
                }
                dataOut.flush();
                sendReply(226, "Directory send OK.");
            } catch (IOException e) {
                sendReply(426, "Connection closed; transfer aborted.");
            } finally {
                closePassive();
            }
        }

        private void handleRetr(String filename) {
            if (passiveServer == null) {
                sendReply(425, "Use PASV first.");
                return;
            }
            Path filePath = resolveSafePath(filename);
            if (filePath == null || !Files.isRegularFile(filePath)) {
                closePassive();
                sendReply(550, "File not found.");
                return;
            }
            sendReply(150, "Opening BINARY mode data connection for " + filePath.getFileName());
            try (Socket dataSocket = passiveServer.accept();
                 InputStream in = Files.newInputStream(filePath);
                 OutputStream out = dataSocket.getOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
                out.flush();
                sendReply(226, "Transfer complete.");
            } catch (IOException e) {
                sendReply(426, "Transfer aborted.");
            } finally {
                closePassive();
            }
        }

        private void handleStor(String filename) {
            if (passiveServer == null) {
                sendReply(425, "Use PASV first.");
                return;
            }
            Path filePath = resolveSafePath(filename);
            if (filePath == null || Files.isDirectory(filePath)) {
                closePassive();
                sendReply(553, "Invalid file name.");
                return;
            }
            sendReply(150, "Ok to send data.");
            try (Socket dataSocket = passiveServer.accept();
                 InputStream in = dataSocket.getInputStream();
                 OutputStream out = Files.newOutputStream(filePath)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
                out.flush();
                sendReply(226, "Transfer complete.");
            } catch (IOException e) {
                sendReply(426, "Transfer aborted.");
            } finally {
                closePassive();
            }
        }

        private Path resolveSafePath(String raw) {
            if (raw == null || raw.trim().isEmpty()) return null;
            String cleaned = raw.trim().replace('\\', '/');
            Path resolved = cleaned.startsWith("/")
                    ? rootDir.resolve(cleaned.substring(1)).normalize()
                    : currentDir.resolve(cleaned).normalize();
            if (!resolved.startsWith(rootDir)) {
                return null; // Chặn Path Traversal
            }
            return resolved;
        }

        private String toFtpPath(Path dir) {
            Path rel = rootDir.relativize(dir);
            String s = rel.toString().replace('\\', '/');
            return s.isEmpty() ? "/" : "/" + s;
        }

        private void sendReply(int code, String msg) {
            writer.print(code + " " + msg + "\r\n");
            writer.flush();
        }

        private void closePassive() {
            if (passiveServer != null && !passiveServer.isClosed()) {
                try {
                    passiveServer.close();
                } catch (IOException ignored) {
                }
            }
            passiveServer = null;
        }
    }
}
