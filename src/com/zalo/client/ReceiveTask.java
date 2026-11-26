package com.zalo.client;

import com.zalo.model.ChatMessage;
import com.zalo.model.ProtocolFramer;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * Tác vụ nhận tin nhắn bất đồng bộ chạy nền phía Client
 * Xử lý luồng mạng và giao thức: * - Chạy trong Thread riêng biệt, không khóa (block) luồng giao diện UI.
 * - Đọc liên tục các dòng tin nhắn JSON từ máy chủ và gửi về UI thông qua Consumer callback.
 */
public class ReceiveTask implements Runnable {
    private final BufferedReader reader;
    private final Consumer<ChatMessage> messageCallback;
    private final Runnable disconnectCallback;
    private volatile boolean running = true;

    public ReceiveTask(BufferedReader reader, Consumer<ChatMessage> messageCallback, Runnable disconnectCallback) {
        this.reader = reader;
        this.messageCallback = messageCallback;
        this.disconnectCallback = disconnectCallback;
    }

    @Override
    public void run() {
        System.out.println("[ReceiveTask] Bắt đầu luồng lắng nghe tin nhắn nền...");
        try {
            while (running) {
                ChatMessage msg = ProtocolFramer.readMessage(reader);
                if (msg == null) {
                    // Server đã đóng kết nối
                    break;
                }
                if (messageCallback != null) {
                    messageCallback.accept(msg);
                }
            }
        } catch (IOException e) {
            if (running) {
                System.out.println("[ReceiveTask] Kết nối mạng bị ngắt: " + e.getMessage());
            }
        } finally {
            running = false;
            if (disconnectCallback != null) {
                disconnectCallback.run();
            }
        }
    }

    public void stop() {
        running = false;
    }
}

