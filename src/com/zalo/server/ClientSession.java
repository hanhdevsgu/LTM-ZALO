package com.zalo.server;

import com.zalo.model.ChatMessage;
import com.zalo.model.ProtocolFramer;

import javax.net.ssl.SSLSocket;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Quản lý phiên kết nối của một Client
 * Tuân thủ nghiêm ngặt Slide 20 & Slide 26:
 * - Mỗi ClientSession sở hữu riêng một ReentrantLock sendLock.
 * - Mọi thao tác gửi tin (ghi OutputStream) đều phải bọc trong sendLock để chống đan xen byte.
 */
public class ClientSession implements AutoCloseable {
    private final int id;
    private final SSLSocket socket;
    private final BufferedReader reader;
    private final PrintWriter writer;
    private final ReentrantLock sendLock = new ReentrantLock();

    private volatile String username;
    private volatile String displayName;
    private volatile boolean authenticated = false;

    public ClientSession(int id, SSLSocket socket) throws IOException {
        this.id = id;
        this.socket = socket;
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
    }

    /**
     * Constructor dành cho phiên ảo (ví dụ WebClientSession qua HTTP)
     */
    protected ClientSession(int id) {
        this.id = id;
        this.socket = null;
        this.reader = null;
        this.writer = null;
    }

    /**
     * Gửi an toàn một thông điệp qua SSLSocket với ReentrantLock
     */
    public boolean send(ChatMessage message) {
        if (socket.isClosed() || !socket.isConnected()) {
            return false;
        }
        sendLock.lock();
        try {
            String jsonPayload = ProtocolFramer.serialize(message);
            writer.print(jsonPayload);
            writer.flush();
            return !writer.checkError();
        } catch (Exception e) {
            System.err.println("[ClientSession] Gửi gói tin lỗi tới Client " + id + " (" + username + "): " + e.getMessage());
            return false;
        } finally {
            sendLock.unlock();
        }
    }

    public int getId() {
        return id;
    }

    public SSLSocket getSocket() {
        return socket;
    }

    public BufferedReader getReader() {
        return reader;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName != null ? displayName : username;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
    }

    public ReentrantLock getSendLock() {
        return sendLock;
    }

    @Override
    public void close() {
        try {
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

