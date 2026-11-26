package com.zalo.client;

import com.zalo.model.ChatMessage;
import com.zalo.model.MessageType;
import com.zalo.model.ProtocolFramer;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Lớp điều khiển mạng chính phía Client
 * Xử lý bảo mật & kết nối: * - Khởi tạo SSLSocket và thực hiện TLS Handshake với Server.
 * - Quản lý luồng gửi với ReentrantLock và luồng nhận với ReceiveTask.
 */
public class ZaloChatClient implements AutoCloseable {
    private SSLSocket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private final ReentrantLock sendLock = new ReentrantLock();

    private Thread receiveThread;
    private ReceiveTask receiveTask;

    private String currentUsername;
    private String currentDisplayName;
    private String connectedHost = "127.0.0.1";
    private boolean authenticated = false;

    private Consumer<ChatMessage> onMessageReceived;
    private Runnable onDisconnected;

    public ZaloChatClient() {
    }

    public void setOnMessageReceived(Consumer<ChatMessage> callback) {
        this.onMessageReceived = callback;
    }

    public void setOnDisconnected(Runnable callback) {
        this.onDisconnected = callback;
    }

    /**
     * Kết nối TCP và thực hiện TLS Handshake tới Server
     */
    public boolean connect(String host, int port) {
        try {
            System.out.println("[Client] Đang kết nối tới Server: " + host + ":" + port + "...");
            this.connectedHost = host;
            SSLSocketFactory factory = TrustManagerHelper.createClientSocketFactory(true);
            this.socket = (SSLSocket) factory.createSocket(host, port);

            // Chỉ cho phép TLS 1.2 / TLS 1.3
            this.socket.setEnabledProtocols(new String[]{"TLSv1.3", "TLSv1.2"});
            this.socket.startHandshake(); // Bắt đầu đàm phán TLS

            System.out.println("[Client] TLS Handshake thành công! Kênh truyền đã được mã hóa an toàn.");

            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            this.writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            // Bắt đầu luồng nhận nền
            this.receiveTask = new ReceiveTask(reader, this::handleIncomingMessage, this::handleDisconnect);
            this.receiveThread = new Thread(receiveTask, "ZaloClient-Receiver");
            this.receiveThread.setDaemon(true);
            this.receiveThread.start();

            return true;
        } catch (Exception e) {
            System.err.println("[Client] Lỗi kết nối TLS: " + e.getMessage());
            close();
            return false;
        }
    }

    /**
     * Gửi an toàn thông điệp với ReentrantLock
     */
    public boolean sendMessage(ChatMessage msg) {
        if (socket == null || socket.isClosed() || writer == null) {
            return false;
        }
        sendLock.lock();
        try {
            String json = ProtocolFramer.serialize(msg);
            writer.print(json);
            writer.flush();
            return !writer.checkError();
        } catch (Exception e) {
            System.err.println("[Client] Lỗi khi gửi thông điệp: " + e.getMessage());
            return false;
        } finally {
            sendLock.unlock();
        }
    }

    public void login(String username, String password) {
        this.currentUsername = username;
        ChatMessage msg = ChatMessage.createLogin(username, password);
        sendMessage(msg);
    }

    public void register(String username, String password, String displayName) {
        ChatMessage msg = ChatMessage.createRegister(username, password, displayName);
        sendMessage(msg);
    }

    public void sendDirectMessage(String toUser, String content) {
        ChatMessage msg = ChatMessage.createDirectMessage(currentUsername, toUser, content);
        msg.setDisplayName(currentDisplayName);
        sendMessage(msg);
    }

    public void sendGroupMessage(String groupId, String content) {
        ChatMessage msg = ChatMessage.createGroupMessage(currentUsername, groupId, null, content);
        msg.setDisplayName(currentDisplayName);
        sendMessage(msg);
    }

    public void requestUserList() {
        ChatMessage msg = new ChatMessage(MessageType.USER_LIST.getValue());
        sendMessage(msg);
    }

    private void handleIncomingMessage(ChatMessage msg) {
        if (msg == null) return;

        // Nếu là kết quả đăng nhập
        if (MessageType.LOGIN_RESULT.getValue().equals(msg.getType())) {
            if (msg.getSuccess()) {
                this.authenticated = true;
                this.currentDisplayName = msg.getDisplayName();
            }
        }

        if (onMessageReceived != null) {
            onMessageReceived.accept(msg);
        }
    }

    private void handleDisconnect() {
        System.out.println("[Client] Kết nối máy chủ đã đóng.");
        authenticated = false;
        if (onDisconnected != null) {
            onDisconnected.run();
        }
    }

    public String getCurrentUsername() {
        return currentUsername;
    }

    public String getCurrentDisplayName() {
        return currentDisplayName != null ? currentDisplayName : currentUsername;
    }

    public String getConnectedHost() {
        return connectedHost;
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    @Override
    public void close() {
        try {
            if (authenticated) {
                ChatMessage disc = new ChatMessage(MessageType.DISCONNECT.getValue());
                disc.setUsername(currentUsername);
                sendMessage(disc);
            }
            if (receiveTask != null) {
                receiveTask.stop();
            }
            if (writer != null) {
                writer.close();
            }
            if (reader != null) {
                reader.close();
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (Exception ignored) {
        }
    }
}

