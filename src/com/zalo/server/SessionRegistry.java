package com.zalo.server;

import com.zalo.model.ChatMessage;
import com.zalo.model.MessageType;
import com.zalo.model.UserInfo;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sổ đăng ký phiên làm việc tập trung (Session Registry)
 * Tuân thủ Slide 20 & Slide 28:
 * - Dùng ConcurrentHashMap đảm bảo Thread-safe khi nhiều luồng truy cập đồng thời.
 * - Quản lý trạng thái Online / Offline thời gian thực.
 */
public class SessionRegistry {
    // Ánh xạ username -> ClientSession (cho các client đã AUTHENTICATED)
    private final Map<String, ClientSession> onlineUsers = new ConcurrentHashMap<>();
    private final Map<String, Long> lastSeenMap = new ConcurrentHashMap<>();
    // Ánh xạ clientId -> ClientSession (cho toàn bộ client đã kết nối socket)
    private final Map<Integer, ClientSession> activeSessions = new ConcurrentHashMap<>();
    private final UserStore userStore;

    public SessionRegistry(UserStore userStore) {
        this.userStore = userStore;
        initLastSeenTable();
    }

    private void initLastSeenTable() {
        try (Connection c = DatabaseManager.getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS user_last_seen (username TEXT PRIMARY KEY, last_seen INTEGER NOT NULL)");
            try (ResultSet rs = st.executeQuery("SELECT username, last_seen FROM user_last_seen")) {
                while (rs.next()) lastSeenMap.put(rs.getString(1).toLowerCase(), rs.getLong(2));
            }
        } catch (SQLException ignored) {}
    }

    private void saveLastSeen(String u, long ts) {
        lastSeenMap.put(u, ts);
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement("INSERT OR REPLACE INTO user_last_seen(username, last_seen) VALUES(?,?)")) {
            ps.setString(1, u);
            ps.setLong(2, ts);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    public void registerInitialSession(ClientSession session) {
        activeSessions.put(session.getId(), session);
        System.out.println("[Registry] Đăng ký kết nối mới Client ID: " + session.getId());
    }

    public synchronized void bindAuthenticatedUser(ClientSession session, String username, String displayName) {
        String u = username.toLowerCase();
        session.setUsername(u);
        session.setDisplayName(displayName);
        session.setAuthenticated(true);
        onlineUsers.put(u, session);
        saveLastSeen(u, System.currentTimeMillis());
        System.out.println("[Registry] Xác thực thành công: " + u + " (Client ID: " + session.getId() + ")");
        
        // Broadcast thông báo user đã online tới tất cả các client khác
        broadcastUserStatusChange(u, displayName, true);
    }

    public synchronized void removeSession(ClientSession session) {
        if (session == null) return;
        activeSessions.remove(session.getId());
        String u = session.getUsername();
        if (u != null) {
            onlineUsers.remove(u.toLowerCase());
            saveLastSeen(u.toLowerCase(), System.currentTimeMillis());
            System.out.println("[Registry] Client đã ngắt kết nối: " + u + " (Client ID: " + session.getId() + ")");
            // Broadcast user đã offline
            broadcastUserStatusChange(u, session.getDisplayName(), false);
        }
        session.close();
    }

    public ClientSession getSessionByUsername(String username) {
        if (username == null) return null;
        return onlineUsers.get(username.trim().toLowerCase());
    }

    public boolean isUserOnline(String username) {
        return username != null && onlineUsers.containsKey(username.trim().toLowerCase());
    }

    public List<UserInfo> buildContactsList() {
        List<UserInfo> list = new ArrayList<>();
        Map<String, UserStore.UserRecord> all = userStore.getAllUsers();
        for (UserStore.UserRecord record : all.values()) {
            boolean online = isUserOnline(record.getUsername());
            UserInfo info = new UserInfo(record.getUsername(), record.getDisplayName(), online);
            Long ls = lastSeenMap.get(record.getUsername().toLowerCase());
            if (online) info.setLastSeen(String.valueOf(System.currentTimeMillis()));
            else if (ls != null) info.setLastSeen(String.valueOf(ls));
            list.add(info);
        }
        // Sắp xếp: Online lên đầu, sau đó theo tên
        list.sort((a, b) -> {
            if (a.isOnline() != b.isOnline()) {
                return a.isOnline() ? -1 : 1;
            }
            return a.getDisplayName().compareToIgnoreCase(b.getDisplayName());
        });
        return list;
    }

    public void broadcastUserStatusChange(String username, String displayName, boolean isOnline) {
        ChatMessage update = new ChatMessage(MessageType.STATUS_UPDATE.getValue());
        update.setUsername(username);
        update.setDisplayName(displayName);
        update.setSuccess(isOnline);
        update.setUsers(buildContactsList());

        for (ClientSession client : onlineUsers.values()) {
            // Không gửi lại chính người vừa online vì họ đã có danh sách từ login_result
            if (client.getUsername() != null && !client.getUsername().equalsIgnoreCase(username)) {
                client.send(update);
            }
        }
    }

    public void broadcastSystemMessage(String text) {
        ChatMessage sys = ChatMessage.createSystem(text);
        for (ClientSession client : onlineUsers.values()) {
            client.send(sys);
        }
    }

    public Collection<ClientSession> getAllOnlineSessions() {
        return onlineUsers.values();
    }
}
