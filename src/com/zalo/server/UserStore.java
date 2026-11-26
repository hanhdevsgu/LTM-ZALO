package com.zalo.server;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quản lý kho tài khoản người dùng và xác thực mật khẩu bằng SQLite (data/zalo.db)
 * Tự động di trú dữ liệu từ data/users.json (nếu có) sang SQLite.
 */
public class UserStore {
    private static final String LEGACY_JSON = "data/users.json";
    private final Map<String, UserRecord> users = new ConcurrentHashMap<>();

    public static class UserRecord {
        private String username;
        private String passwordHash;
        private String salt;
        private String displayName;

        public UserRecord() {}

        public UserRecord(String username, String passwordHash, String salt, String displayName) {
            this.username = username;
            this.passwordHash = passwordHash;
            this.salt = salt;
            this.displayName = displayName;
        }

        public String getUsername() { return username; }
        public String getPasswordHash() { return passwordHash; }
        public String getSalt() { return salt; }
        public String getDisplayName() { return displayName != null ? displayName : username; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
    }

    public UserStore() {
        DatabaseManager.init();
        loadUsersFromDb();
        migrateLegacyJsonIfNeeded();
        seedDefaultUsersIfEmpty();
    }

    private void seedDefaultUsersIfEmpty() {
        if (!users.containsKey("an")) registerUser("an", "123456", "Nguyễn Văn An");
        if (!users.containsKey("binh")) registerUser("binh", "123456", "Trần Thị Bình");
        if (!users.containsKey("chi")) registerUser("chi", "123456", "Lê Kim Chi");
    }

    public synchronized boolean registerUser(String username, String rawPassword, String displayName) {
        if (username == null || rawPassword == null || username.trim().isEmpty() || rawPassword.trim().isEmpty()) {
            return false;
        }
        String u = username.trim().toLowerCase();
        if (users.containsKey(u)) return false;

        String salt = SecurityHelper.generateSalt();
        String hash = SecurityHelper.hashPassword(rawPassword, salt);
        String dName = (displayName != null && !displayName.trim().isEmpty()) ? displayName.trim() : u;

        UserRecord record = new UserRecord(u, hash, salt, dName);
        insertUserToDb(record);
        users.put(u, record);
        System.out.println("[UserStore] Đã lưu tài khoản vào SQLite: " + u + " (" + dName + ")");
        return true;
    }

    public boolean authenticate(String username, String rawPassword) {
        if (username == null || rawPassword == null) return false;
        UserRecord record = users.get(username.trim().toLowerCase());
        if (record == null) return false;
        return SecurityHelper.verifyPassword(rawPassword, record.getPasswordHash(), record.getSalt());
    }

    public String getDisplayName(String username) {
        if (username == null) return "Ẩn danh";
        UserRecord r = users.get(username.trim().toLowerCase());
        return r != null ? r.getDisplayName() : username;
    }

    public synchronized boolean updateDisplayName(String username, String newDisplayName) {
        if (username == null || newDisplayName == null || newDisplayName.trim().isEmpty()) return false;
        String u = username.trim().toLowerCase();
        String dName = newDisplayName.trim();
        UserRecord r = users.get(u);
        if (r == null) return false;
        r.setDisplayName(dName);
        String sql = "UPDATE users SET display_name = ? WHERE username = ?";
        try (Connection c = DatabaseManager.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, dName);
            ps.setString(2, u);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("[UserStore] Lỗi cập nhật displayName: " + e.getMessage());
            return false;
        }
    }

    public Map<String, UserRecord> getAllUsers() {
        return users;
    }

    private void loadUsersFromDb() {
        String sql = "SELECT username, password_hash, salt, display_name FROM users";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                UserRecord r = new UserRecord(
                        rs.getString("username"),
                        rs.getString("password_hash"),
                        rs.getString("salt"),
                        rs.getString("display_name"));
                users.put(r.getUsername().toLowerCase(), r);
            }
            System.out.println("[UserStore] Đã nạp " + users.size() + " tài khoản từ SQLite.");
        } catch (SQLException e) {
            System.err.println("[UserStore] Lỗi đọc SQLite: " + e.getMessage());
        }
    }

    private void insertUserToDb(UserRecord r) {
        String sql = "INSERT OR IGNORE INTO users(username, password_hash, salt, display_name) VALUES(?,?,?,?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, r.getUsername());
            ps.setString(2, r.getPasswordHash());
            ps.setString(3, r.getSalt());
            ps.setString(4, r.getDisplayName());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[UserStore] Lỗi ghi SQLite: " + e.getMessage());
        }
    }

    private void migrateLegacyJsonIfNeeded() {
        if (!users.isEmpty()) return;
        File file = new File(LEGACY_JSON);
        if (!file.exists()) return;
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, UserRecord>>() {}.getType();
            Map<String, UserRecord> loaded = new Gson().fromJson(reader, type);
            if (loaded != null) {
                for (UserRecord r : loaded.values()) {
                    if (r.getUsername() != null && !users.containsKey(r.getUsername().toLowerCase())) {
                        insertUserToDb(r);
                        users.put(r.getUsername().toLowerCase(), r);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }
}
