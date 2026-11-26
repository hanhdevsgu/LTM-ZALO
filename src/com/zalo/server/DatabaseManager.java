package com.zalo.server;

import java.io.File;
import java.sql.*;

/**
 * Quản lý kết nối và khởi tạo Schema cơ sở dữ liệu SQLite (data/zalo.db).
 * Bao gồm đầy đủ các bảng: users, groups, group_members, messages,
 * friendships, friend_requests, user_blocks, user_profiles, user_posts,
 * message_reactions, message_user_hidden, pinned_messages, conversation_user_state.
 */
public final class DatabaseManager {
    private static final String DB_PATH = "data/zalo.db";
    private static final String JDBC_URL = "jdbc:sqlite:" + DB_PATH;
    private static volatile boolean initialized = false;

    private DatabaseManager() {}

    /** Khởi tạo thư mục data/ và tạo toàn bộ bảng SQLite nếu chưa tồn tại */
    public static synchronized void init() {
        if (initialized) return;
        File dbFile = new File(DB_PATH);
        if (dbFile.getParentFile() != null) dbFile.getParentFile().mkdirs();
        new File("data/uploads/avatars").mkdirs();

        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL;");

            // 1. Tài khoản
            st.execute("CREATE TABLE IF NOT EXISTS users ("
                    + "username TEXT PRIMARY KEY, "
                    + "password_hash TEXT NOT NULL, "
                    + "salt TEXT NOT NULL, "
                    + "display_name TEXT NOT NULL, "
                    + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");

            // 2. Nhóm chat & thành viên
            st.execute("CREATE TABLE IF NOT EXISTS groups ("
                    + "group_id TEXT PRIMARY KEY, "
                    + "group_name TEXT NOT NULL, "
                    + "owner TEXT DEFAULT 'system', "
                    + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
            try { st.execute("ALTER TABLE groups ADD COLUMN owner TEXT DEFAULT 'system'"); } catch (SQLException ignored) {}

            st.execute("CREATE TABLE IF NOT EXISTS group_members ("
                    + "group_id TEXT NOT NULL, "
                    + "username TEXT NOT NULL, "
                    + "PRIMARY KEY (group_id, username))");

            // 3. Tin nhắn
            st.execute("CREATE TABLE IF NOT EXISTS messages ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "message_id TEXT, "
                    + "type TEXT NOT NULL, "
                    + "sender TEXT NOT NULL, "
                    + "display_name TEXT, "
                    + "to_user TEXT, "
                    + "group_id TEXT, "
                    + "group_name TEXT, "
                    + "content TEXT, "
                    + "file_name TEXT, "
                    + "file_size INTEGER, "
                    + "file_data TEXT, "
                    + "timestamp TEXT, "
                    + "is_recalled INTEGER DEFAULT 0, "
                    + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_messages_msg_id ON messages(message_id)");

            // 4. Kết bạn & Chặn
            st.execute("CREATE TABLE IF NOT EXISTS friend_requests ("
                    + "from_user TEXT NOT NULL, "
                    + "to_user TEXT NOT NULL, "
                    + "status TEXT DEFAULT 'pending', "
                    + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, "
                    + "PRIMARY KEY (from_user, to_user))");

            st.execute("CREATE TABLE IF NOT EXISTS friendships ("
                    + "user_a TEXT NOT NULL, "
                    + "user_b TEXT NOT NULL, "
                    + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, "
                    + "PRIMARY KEY (user_a, user_b))");

            st.execute("CREATE TABLE IF NOT EXISTS user_blocks ("
                    + "blocker TEXT NOT NULL, "
                    + "blocked TEXT NOT NULL, "
                    + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, "
                    + "PRIMARY KEY (blocker, blocked))");

            // 5. Trang cá nhân & Bài viết
            st.execute("CREATE TABLE IF NOT EXISTS user_profiles ("
                    + "username TEXT PRIMARY KEY, "
                    + "avatar_url TEXT, "
                    + "bio TEXT DEFAULT '')");

            st.execute("CREATE TABLE IF NOT EXISTS user_posts ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "username TEXT NOT NULL, "
                    + "content TEXT NOT NULL, "
                    + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");

            // 6. Cảm xúc, Ẩn tin nhắn 1 phía, Ghim tin, Trạng thái hội thoại
            st.execute("CREATE TABLE IF NOT EXISTS message_reactions ("
                    + "message_id TEXT NOT NULL, "
                    + "username TEXT NOT NULL, "
                    + "emoji TEXT NOT NULL, "
                    + "PRIMARY KEY (message_id, username))");

            st.execute("CREATE TABLE IF NOT EXISTS message_user_hidden ("
                    + "message_id TEXT NOT NULL, "
                    + "username TEXT NOT NULL, "
                    + "PRIMARY KEY (message_id, username))");

            st.execute("CREATE TABLE IF NOT EXISTS pinned_messages ("
                    + "conv_id TEXT PRIMARY KEY, "
                    + "message_id TEXT NOT NULL, "
                    + "content TEXT, "
                    + "pinned_by TEXT)");

            st.execute("CREATE TABLE IF NOT EXISTS starred_messages ("
                    + "username TEXT NOT NULL, "
                    + "message_id TEXT NOT NULL, "
                    + "conv_id TEXT, "
                    + "sender_name TEXT, "
                    + "content TEXT, "
                    + "timestamp TEXT, "
                    + "PRIMARY KEY (username, message_id))");

            st.execute("CREATE TABLE IF NOT EXISTS conversation_user_state ("
                    + "username TEXT NOT NULL, "
                    + "conv_id TEXT NOT NULL, "
                    + "unread_count INTEGER DEFAULT 0, "
                    + "muted INTEGER DEFAULT 0, "
                    + "cleared_after_id INTEGER DEFAULT 0, "
                    + "PRIMARY KEY (username, conv_id))");

            st.execute("CREATE TABLE IF NOT EXISTS web_sessions ("
                    + "token TEXT PRIMARY KEY, "
                    + "username TEXT NOT NULL, "
                    + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");

            // 8. Thời điểm hoạt động gần nhất (Last Seen) của từng tài khoản
            st.execute("CREATE TABLE IF NOT EXISTS user_last_seen ("
                    + "username TEXT PRIMARY KEY, "
                    + "last_seen INTEGER NOT NULL)");

            initialized = true;
            System.out.println("[Database] Đã khởi tạo đầy đủ Schema SQLite: " + DB_PATH);
        } catch (SQLException e) {
            System.err.println("[Database] Lỗi khởi tạo SQLite: " + e.getMessage());
        }
    }

    /** Mở kết nối JDBC tới SQLite */
    public static Connection getConnection() throws SQLException {
        if (!initialized) {
            try { Class.forName("org.sqlite.JDBC"); } catch (ClassNotFoundException ignored) {}
        }
        return DriverManager.getConnection(JDBC_URL);
    }
}
