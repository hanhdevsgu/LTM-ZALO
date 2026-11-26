package com.zalo.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import java.io.BufferedReader;
import java.io.IOException;

/**
 * Động cơ Phân khung và Giải mã gói tin Stream TCP
 * Xử lý bảo mật & kết nối: * - Ký tự xuống dòng '\n' là ranh giới phân tách thông điệp (Line Delimiter).
 * - Giới hạn kích thước tối đa 16KB để phòng chống tấn công DoS/OOM.
 * - Tuần tự hóa JSON an toàn với Gson.
 */
public class ProtocolFramer {
    public static final int MAX_FRAME_SIZE = 16384; // 16 KB giới hạn tối đa cho tin nhắn thường
    public static final int MAX_FILE_FRAME_SIZE = 8 * 1024 * 1024; // 8 MB cho gói tin truyền tệp Base64
    private static final Gson gson = new GsonBuilder().serializeNulls().create();

    /**
     * Chuyển đổi ChatMessage thành chuỗi JSON UTF-8 kèm ký tự '\n'
     */
    public static String serialize(ChatMessage message) {
        if (message == null) {
            return "{}\n";
        }
        return gson.toJson(message) + "\n";
    }

    /**
     * Giải tuần tự hóa chuỗi JSON thành đối tượng ChatMessage
     */
    public static ChatMessage deserialize(String jsonLine) throws JsonSyntaxException {
        if (jsonLine == null || jsonLine.trim().isEmpty()) {
            return null;
        }
        if (jsonLine.length() > MAX_FILE_FRAME_SIZE) {
            throw new IllegalArgumentException("Kích thước gói tin vượt quá giới hạn tối đa 8MB!");
        }
        ChatMessage msg = gson.fromJson(jsonLine.trim(), ChatMessage.class);
        if (msg != null && !MessageType.FILE_TRANSFER.getValue().equals(msg.getType())
                && jsonLine.length() > MAX_FRAME_SIZE) {
            throw new IllegalArgumentException("Kích thước thông điệp vượt quá giới hạn an toàn 16KB!");
        }
        return msg;
    }

    /**
     * Đọc một dòng tin nhắn hoàn chỉnh từ luồng BufferedReader
     */
    public static ChatMessage readMessage(BufferedReader reader) throws IOException {
        String line = reader.readLine();
        if (line == null) {
            return null; // Kết nối đã đóng
        }
        try {
            return deserialize(line);
        } catch (Exception e) {
            System.err.println("[ProtocolFramer] Bỏ qua gói tin không đúng định dạng JSON: " + e.getMessage());
            return null;
        }
    }
}

