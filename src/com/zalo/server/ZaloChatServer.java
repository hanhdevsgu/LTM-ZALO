package com.zalo.server;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Máy chủ Zalo Secure TCP Chat Server
 * Khởi tạo và điều phối Server: * - Khởi tạo SSLContext từ certificates/server.p12
 * - Mở cổng SSLServerSocket 5000 (TLS 1.2 / TLS 1.3)
 * - Vòng lặp Accept Loop tiếp nhận nhiều Client đồng thời qua ExecutorService
 * - Phân phối định danh Client ID duy nhất (nextClientId++)
 */
public class ZaloChatServer {
    public static final int DEFAULT_PORT = 5000;
    private static final AtomicInteger nextClientId = new AtomicInteger(1);

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
            }
        }

        System.out.println("==================================================================");
        System.out.println("   ZALO SECURE TCP CHAT SERVER (JAVA 17+ / TLS 1.3 / LẬP TRÌNH MẠNG)   ");
        System.out.println("==================================================================");

        try {
            // 1. Khởi tạo SQLite Database và nạp các bộ quản lý
            DatabaseManager.init();
            UserStore userStore = new UserStore();
            GroupManager groupManager = new GroupManager();
            SessionRegistry sessionRegistry = new SessionRegistry(userStore);
            MessageDispatcher dispatcher = new MessageDispatcher(userStore, sessionRegistry, groupManager);

            // 2. Tạo SSLContext từ Keystore
            System.out.println("[Server] Đang nạp chứng chỉ TLS từ " + SSLContextFactory.KEYSTORE_PATH + "...");
            SSLContext sslContext = SSLContextFactory.createServerSSLContext();
            SSLServerSocketFactory ssf = sslContext.getServerSocketFactory();

            // 3. Mở cổng SSLServerSocket
            SSLServerSocket serverSocket = (SSLServerSocket) ssf.createServerSocket(port);
            SSLContextFactory.configureServerSocket(serverSocket);

            System.out.println("[Server] Đã khởi động thành công và đang lắng nghe tại cổng " + port + " (TLS 1.2 / TLS 1.3)");
            System.out.println("[Server] Sẵn sàng phục vụ các Client kết nối an toàn...");

            // 4. ThreadPool phục vụ các Client đồng thời
            ExecutorService threadPool = Executors.newCachedThreadPool();

            // 4.1 Khởi động dịch vụ phụ trợ
            UdpDiscoveryServer udpServer = new UdpDiscoveryServer(port);
            Thread udpThread = new Thread(udpServer, "UDP-Discovery");
            udpThread.setDaemon(true);
            udpThread.start();

            MiniHttpServer httpServer = new MiniHttpServer(sessionRegistry, dispatcher, userStore, port);
            httpServer.start();

            MiniFtpServer ftpServer = new MiniFtpServer(userStore);
            Thread ftpThread = new Thread(ftpServer, "FTP-Server");
            ftpThread.setDaemon(true);
            ftpThread.start();

            // Đăng ký hook dọn dẹp khi tắt server
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n[Server] Đang đóng máy chủ và giải phóng socket...");
                try {
                    udpServer.stop();
                    httpServer.stop();
                    ftpServer.stop();
                    serverSocket.close();
                    threadPool.shutdownNow();
                } catch (Exception ignored) {
                }
            }));

            // 5. Vòng lặp đón tiếp Client (Accept Loop)
            while (!serverSocket.isClosed()) {
                try {
                    // Chấp nhận kết nối mới và bắt tay TLS tự động
                    SSLSocket clientSocket = (SSLSocket) serverSocket.accept();
                    int clientId = nextClientId.getAndIncrement();

                    ClientSession session = new ClientSession(clientId, clientSocket);
                    sessionRegistry.registerInitialSession(session);

                    // Phục vụ client trên thread riêng biệt
                    threadPool.submit(new ClientHandler(session, dispatcher, sessionRegistry));
                } catch (IOException e) {
                    if (serverSocket.isClosed()) {
                        break;
                    }
                    System.err.println("[Server] Lỗi khi đón nhận Client: " + e.getMessage());
                }
            }

        } catch (Exception e) {
            System.err.println("[Server Fatal] Không thể khởi động máy chủ: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

