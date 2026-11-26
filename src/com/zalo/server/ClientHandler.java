package com.zalo.server;

import com.zalo.model.ChatMessage;
import com.zalo.model.ProtocolFramer;

import java.io.IOException;

/**
 * Tác vụ xử lý kết nối riêng biệt cho từng Client (Worker Thread)
 * Xử lý bảo mật & kết nối: * - Chạy bất đồng bộ trong Thread / ExecutorService riêng biệt.
 * - Đọc tin nhắn liên tục, bắt ngoại lệ và thu hồi tài nguyên an toàn trong finally.
 */
public class ClientHandler implements Runnable {
    private final ClientSession session;
    private final MessageDispatcher dispatcher;
    private final SessionRegistry sessionRegistry;

    public ClientHandler(ClientSession session, MessageDispatcher dispatcher, SessionRegistry sessionRegistry) {
        this.session = session;
        this.dispatcher = dispatcher;
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public void run() {
        System.out.println("[ClientHandler] Bắt đầu phục vụ Client ID: " + session.getId() +
                " từ IP: " + session.getSocket().getInetAddress().getHostAddress());

        try {
            while (!session.getSocket().isClosed()) {
                ChatMessage message = ProtocolFramer.readMessage(session.getReader());
                if (message == null) {
                    // Dòng byte trả về null nghĩa là Client đã đóng kết nối (EOF)
                    break;
                }
                dispatcher.dispatch(session, message);
            }
        } catch (IOException e) {
            System.out.println("[ClientHandler] Kết nối ngắt quãng với Client ID: " + session.getId() +
                    " (" + (session.getUsername() != null ? session.getUsername() : "Chưa login") + "): " + e.getMessage());
        } catch (Exception e) {
            System.err.println("[ClientHandler] Lỗi bất ngờ: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Dọn dẹp tài nguyên an toàn
            sessionRegistry.removeSession(session);
        }
    }
}

