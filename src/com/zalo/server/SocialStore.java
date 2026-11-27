package com.zalo.server;

import java.sql.*;
import java.util.*;

/**
 * Tầng truy xuất dữ liệu mạng xã hội & tương tác Zalo trong SQLite:
 * - Kết bạn, lời mời kết bạn, hủy kết bạn, chặn/gỡ chặn
 * - Trang cá nhân (avatar, tiểu sử) & bài viết (nhật ký)
 * - Cảm xúc tin nhắn (reactions), ẩn tin nhắn 1 phía, xóa đoạn chat, ghim tin nhắn, đếm chưa đọc & tắt thông báo.
 */
public class SocialStore {

    public SocialStore() {
        DatabaseManager.init();
    }

    // ==================== KẾT BẠN & CHẶN ====================

    public boolean isBlocked(String userA, String userB) {
        if (userA == null || userB == null) return false;
        String a = userA.toLowerCase(), b = userB.toLowerCase();
        String sql = "SELECT 1 FROM user_blocks WHERE (blocker=? AND blocked=?) OR (blocker=? AND blocked=?) LIMIT 1";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, a); ps.setString(2, b); ps.setString(3, b); ps.setString(4, a);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) { return false; }
    }

    public boolean hasBlocked(String blocker, String blocked) {
        String sql = "SELECT 1 FROM user_blocks WHERE blocker=? AND blocked=? LIMIT 1";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, blocker.toLowerCase()); ps.setString(2, blocked.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) { return false; }
    }

    public void setBlock(String blocker, String blocked, boolean doBlock) {
        String a = blocker.toLowerCase(), b = blocked.toLowerCase();
        try (Connection conn = DatabaseManager.getConnection()) {
            if (doBlock) {
                try (PreparedStatement ps = conn.prepareStatement("INSERT OR IGNORE INTO user_blocks(blocker, blocked) VALUES(?,?)")) {
                    ps.setString(1, a); ps.setString(2, b); ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM user_blocks WHERE blocker=? AND blocked=?")) {
                    ps.setString(1, a); ps.setString(2, b); ps.executeUpdate();
                }
            }
        } catch (SQLException ignored) {}
    }

    public boolean areFriends(String userA, String userB) {
        String a = userA.toLowerCase(), b = userB.toLowerCase();
        if (a.compareTo(b) > 0) { String t = a; a = b; b = t; }
        String sql = "SELECT 1 FROM friendships WHERE user_a=? AND user_b=? LIMIT 1";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, a); ps.setString(2, b);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) { return false; }
    }

    public String sendFriendRequest(String from, String to) {
        String a = from.toLowerCase(), b = to.toLowerCase();
        if (a.equals(b)) return "Không thể tự kết bạn với chính mình!";
        if (isBlocked(a, b)) return "Không thể gửi lời mời do có chặn giữa hai tài khoản!";
        if (areFriends(a, b)) return "Hai người đã là bạn bè!";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO friend_requests(from_user, to_user, status) VALUES(?,?, 'pending')")) {
            ps.setString(1, a); ps.setString(2, b); ps.executeUpdate();
            return "OK";
        } catch (SQLException e) { return e.getMessage(); }
    }

    public void respondFriendRequest(String toUser, String fromUser, boolean accept) {
        String a = fromUser.toLowerCase(), b = toUser.toLowerCase();
        try (Connection conn = DatabaseManager.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM friend_requests WHERE from_user=? AND to_user=?")) {
                ps.setString(1, a); ps.setString(2, b); ps.executeUpdate();
            }
            if (accept) {
                String u1 = a.compareTo(b) <= 0 ? a : b;
                String u2 = a.compareTo(b) <= 0 ? b : a;
                try (PreparedStatement ps = conn.prepareStatement("INSERT OR IGNORE INTO friendships(user_a, user_b) VALUES(?,?)")) {
                    ps.setString(1, u1); ps.setString(2, u2); ps.executeUpdate();
                }
            }
        } catch (SQLException ignored) {}
    }

    public void unfriend(String userA, String userB) {
        String a = userA.toLowerCase(), b = userB.toLowerCase();
        if (a.compareTo(b) > 0) { String t = a; a = b; b = t; }
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM friendships WHERE user_a=? AND user_b=?")) {
            ps.setString(1, a); ps.setString(2, b); ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    public List<Map<String, String>> getIncomingRequests(String username, UserStore userStore) {
        List<Map<String, String>> list = new ArrayList<>();
        String sql = "SELECT from_user, created_at FROM friend_requests WHERE to_user=? AND status='pending'";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String u = rs.getString("from_user");
                    Map<String, String> m = new HashMap<>();
                    m.put("username", u);
                    m.put("displayName", userStore.getDisplayName(u));
                    m.put("createdAt", rs.getString("created_at"));
                    list.add(m);
                }
            }
        } catch (SQLException ignored) {}
        return list;
    }

    public Set<String> getFriendUsernames(String username) {
        Set<String> set = new HashSet<>();
        String u = username.toLowerCase();
        String sql = "SELECT user_a, user_b FROM friendships WHERE user_a=? OR user_b=?";
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, u); ps.setString(2, u);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String a = rs.getString("user_a"), b = rs.getString("user_b");
                    set.add(a.equals(u) ? b : a);
                }
            }
        } catch (SQLException ignored) {}
        return set;
    }

    // ==================== TRANG CÁ NHÂN & BÀI VIẾT ====================

    public Map<String, Object> getProfile(String targetUser, String viewer, UserStore userStore) {
        String u = targetUser.toLowerCase();
        Map<String, Object> map = new HashMap<>();
        map.put("username", u);
        map.put("displayName", userStore.getDisplayName(u));
        map.put("avatarUrl", "");
        map.put("bio", "");
        map.put("isFriend", viewer != null && areFriends(viewer, u));
        map.put("isBlockedByMe", viewer != null && hasBlocked(viewer, u));

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT avatar_url, bio FROM user_profiles WHERE username=?")) {
            ps.setString(1, u);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    map.put("avatarUrl", rs.getString("avatar_url") != null ? rs.getString("avatar_url") : "");
                    map.put("bio", rs.getString("bio") != null ? rs.getString("bio") : "");
                }
            }
        } catch (SQLException ignored) {}

        List<Map<String, Object>> posts = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT id, content, created_at FROM user_posts WHERE username=? ORDER BY id DESC LIMIT 30")) {
            ps.setString(1, u);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> p = new HashMap<>();
                    p.put("id", rs.getInt("id"));
                    p.put("content", rs.getString("content"));
                    p.put("createdAt", rs.getString("created_at"));
                    posts.add(p);
                }
            }
        } catch (SQLException ignored) {}
        map.put("posts", posts);
        return map;
    }

    public String getAvatarUrl(String username) {
        if (username == null) return "";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT avatar_url FROM user_profiles WHERE username=?")) {
            ps.setString(1, username.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String u = rs.getString("avatar_url");
                    return u != null ? u : "";
                }
            }
        } catch (SQLException ignored) {}
        return "";
    }

    public void updateProfile(String username, String avatarUrl, String bio) {
        String u = username.toLowerCase();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO user_profiles(username, avatar_url, bio) VALUES(?,?,?) "
                             + "ON CONFLICT(username) DO UPDATE SET avatar_url=COALESCE(NULLIF(excluded.avatar_url,''), user_profiles.avatar_url), bio=excluded.bio")) {
            ps.setString(1, u);
            ps.setString(2, avatarUrl != null ? avatarUrl : "");
            ps.setString(3, bio != null ? bio : "");
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    public void createPost(String username, String content) {
        if (content == null || content.trim().isEmpty()) return;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO user_posts(username, content) VALUES(?,?)")) {
            ps.setString(1, username.toLowerCase());
            ps.setString(2, content.trim());
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    // ==================== CẢM XÚC, ẨN TIN, GHIM TIN, TRẠNG THÁI HỘI THOẠI ====================

    /** Thả hoặc gỡ cảm xúc (mỗi người 1 emoji/tin nhắn; bấm lại đúng emoji cũ sẽ gỡ) */
    /** [Emoji] Lưu/cộng dồn nhiều cảm xúc hoặc xóa toàn bộ cảm xúc của người dùng trên một tin nhắn */
    public Map<String, Integer> toggleReaction(String messageId, String username, String emoji) {
        String u = username.toLowerCase();
        try (Connection conn = DatabaseManager.getConnection()) {
            if (emoji == null || emoji.trim().isEmpty()) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM message_reactions WHERE message_id=? AND username=?")) {
                    ps.setString(1, messageId); ps.setString(2, u); ps.executeUpdate();
                }
            } else {
                String existing = null;
                try (PreparedStatement ps = conn.prepareStatement("SELECT emoji FROM message_reactions WHERE message_id=? AND username=?")) {
                    ps.setString(1, messageId); ps.setString(2, u);
                    try (ResultSet rs = ps.executeQuery()) { if (rs.next()) existing = rs.getString(1); }
                }
                String nextVal = (existing == null || existing.isEmpty()) ? emoji : (existing + "," + emoji);
                try (PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO message_reactions(message_id, username, emoji) VALUES(?,?,?)")) {
                    ps.setString(1, messageId); ps.setString(2, u); ps.setString(3, nextVal); ps.executeUpdate();
                }
            }
        } catch (SQLException ignored) {}
        return getReactionsForMessage(messageId);
    }

    public Map<String, Integer> getReactionsForMessage(String messageId) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT emoji FROM message_reactions WHERE message_id=?")) {
            ps.setString(1, messageId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    for (String e : rs.getString(1).split(",")) {
                        if (!e.isEmpty()) counts.merge(e, 1, Integer::sum);
                    }
                }
            }
        } catch (SQLException ignored) {}
        return counts;
    }

    public Map<String, Map<String, Integer>> getAllReactionsMap() {
        Map<String, Map<String, Integer>> map = new HashMap<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT message_id, emoji FROM message_reactions");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Integer> inner = map.computeIfAbsent(rs.getString(1), k -> new LinkedHashMap<>());
                for (String e : rs.getString(2).split(",")) {
                    if (!e.isEmpty()) inner.merge(e, 1, Integer::sum);
                }
            }
        } catch (SQLException ignored) {}
        return map;
    }

    public void hideMessageForUser(String messageId, String username) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT OR IGNORE INTO message_user_hidden(message_id, username) VALUES(?,?)")) {
            ps.setString(1, messageId); ps.setString(2, username.toLowerCase()); ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    public Set<String> getHiddenMessageIds(String username) {
        Set<String> set = new HashSet<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT message_id FROM message_user_hidden WHERE username=?")) {
            ps.setString(1, username.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) set.add(rs.getString(1));
            }
        } catch (SQLException ignored) {}
        return set;
    }

    public void clearConversationForUser(String username, String convId) {
        try (Connection conn = DatabaseManager.getConnection()) {
            int maxId = 0;
            try (PreparedStatement ps = conn.prepareStatement("SELECT COALESCE(MAX(id), 0) FROM messages");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) maxId = rs.getInt(1);
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO conversation_user_state(username, conv_id, cleared_after_id) VALUES(?,?,?) "
                            + "ON CONFLICT(username, conv_id) DO UPDATE SET cleared_after_id=excluded.cleared_after_id")) {
                ps.setString(1, username.toLowerCase()); ps.setString(2, convId); ps.setInt(3, maxId); ps.executeUpdate();
            }
        } catch (SQLException ignored) {}
    }

    public void setPinnedMessage(String convId, String messageId, String content, String pinnedBy) {
        try (Connection conn = DatabaseManager.getConnection()) {
            if (messageId == null || messageId.isEmpty()) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM pinned_messages WHERE conv_id=?")) {
                    ps.setString(1, convId); ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO pinned_messages(conv_id, message_id, content, pinned_by) VALUES(?,?,?,?)")) {
                    ps.setString(1, convId); ps.setString(2, messageId); ps.setString(3, content); ps.setString(4, pinnedBy); ps.executeUpdate();
                }
            }
        } catch (SQLException ignored) {}
    }

    public Map<String, Map<String, String>> getAllPinned() {
        Map<String, Map<String, String>> map = new HashMap<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT conv_id, message_id, content, pinned_by FROM pinned_messages");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, String> p = new HashMap<>();
                p.put("messageId", rs.getString("message_id"));
                p.put("content", rs.getString("content"));
                p.put("pinnedBy", rs.getString("pinned_by"));
                map.put(rs.getString("conv_id"), p);
            }
        } catch (SQLException ignored) {}
        return map;
    }

    public void toggleMute(String username, String convId, boolean muted) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO conversation_user_state(username, conv_id, muted) VALUES(?,?,?) "
                             + "ON CONFLICT(username, conv_id) DO UPDATE SET muted=excluded.muted")) {
            ps.setString(1, username.toLowerCase()); ps.setString(2, convId); ps.setInt(3, muted ? 1 : 0); ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    /** [State] Tăng bộ đếm tin nhắn chưa đọc (unread_count) trong SQLite */
    public void incrementUnread(String username, String convId) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO conversation_user_state(username, conv_id, unread_count) VALUES(?,?,1) "
                             + "ON CONFLICT(username, conv_id) DO UPDATE SET unread_count=conversation_user_state.unread_count+1")) {
            ps.setString(1, username.toLowerCase()); ps.setString(2, convId); ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    /** [State] Đánh dấu đã đọc (đặt unread_count = 0) khi người dùng mở hội thoại */
    public void markRead(String username, String convId) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO conversation_user_state(username, conv_id, unread_count) VALUES(?,?,0) "
                             + "ON CONFLICT(username, conv_id) DO UPDATE SET unread_count=0")) {
            ps.setString(1, username.toLowerCase()); ps.setString(2, convId); ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    public Map<String, Map<String, Object>> getConvStates(String username) {
        Map<String, Map<String, Object>> map = new HashMap<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT conv_id, unread_count, muted, cleared_after_id FROM conversation_user_state WHERE username=?")) {
            ps.setString(1, username.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> st = new HashMap<>();
                    st.put("unread", rs.getInt("unread_count"));
                    st.put("muted", rs.getInt("muted") == 1);
                    st.put("clearedAfterId", rs.getInt("cleared_after_id"));
                    map.put(rs.getString("conv_id"), st);
                }
            }
        } catch (SQLException ignored) {}
        return map;
    }

    /** Đánh dấu hoặc bỏ đánh dấu sao tin nhắn cho riêng từng user */
    public boolean toggleStarMessage(String username, String messageId, String convId, String senderName, String content, String timestamp) {
        String u = username.toLowerCase();
        try (Connection conn = DatabaseManager.getConnection()) {
            boolean exists = false;
            try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM starred_messages WHERE username=? AND message_id=?")) {
                ps.setString(1, u); ps.setString(2, messageId);
                try (ResultSet rs = ps.executeQuery()) { exists = rs.next(); }
            }
            if (exists) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM starred_messages WHERE username=? AND message_id=?")) {
                    ps.setString(1, u); ps.setString(2, messageId); ps.executeUpdate();
                }
                return false;
            } else {
                try (PreparedStatement ps = conn.prepareStatement("INSERT INTO starred_messages(username, message_id, conv_id, sender_name, content, timestamp) VALUES(?,?,?,?,?,?)")) {
                    ps.setString(1, u); ps.setString(2, messageId); ps.setString(3, convId);
                    ps.setString(4, senderName); ps.setString(5, content); ps.setString(6, timestamp);
                    ps.executeUpdate();
                }
                return true;
            }
        } catch (SQLException e) { return false; }
    }

    public List<Map<String, String>> getStarredMessages(String username) {
        List<Map<String, String>> list = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT message_id, conv_id, sender_name, content, timestamp FROM starred_messages WHERE username=?")) {
            ps.setString(1, username.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> m = new HashMap<>();
                    m.put("messageId", rs.getString("message_id"));
                    m.put("convId", rs.getString("conv_id"));
                    m.put("senderName", rs.getString("sender_name"));
                    m.put("content", rs.getString("content"));
                    m.put("timestamp", rs.getString("timestamp"));
                    list.add(m);
                }
            }
        } catch (SQLException ignored) {}
        return list;
    }

    public void saveWebSession(String token, String username) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO web_sessions(token, username) VALUES(?,?)")) {
            ps.setString(1, token); ps.setString(2, username.toLowerCase()); ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    public String getWebSessionUsername(String token) {
        if (token == null || token.isEmpty()) return null;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT username FROM web_sessions WHERE token=?")) {
            ps.setString(1, token);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getString("username") : null; }
        } catch (SQLException e) { return null; }
    }

    public void deleteWebSession(String token) {
        if (token == null) return;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM web_sessions WHERE token=?")) {
            ps.setString(1, token); ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    public Map<String, Integer> getDbStats() {
        Map<String, Integer> stats = new HashMap<>();
        try (Connection conn = DatabaseManager.getConnection(); Statement st = conn.createStatement()) {
            for (String tbl : new String[]{"users", "groups", "messages"}) {
                try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + tbl)) {
                    stats.put(tbl, rs.next() ? rs.getInt(1) : 0);
                }
            }
        } catch (SQLException ignored) {}
        return stats;
    }
}
