package com.zalo.server;

import com.zalo.model.ChatMessage;
import com.zalo.model.MessageType;

import java.sql.*;
import java.util.*;

/**
 * Lưu trữ lịch sử tin nhắn tập trung trong SQLite (data/zalo.db).
 * Hỗ trợ lọc tin nhắn đã ẩn 1 phía, đoạn chat đã xóa theo tài khoản, và đính kèm reactions.
 */
public class MessageHistoryStore {

    public MessageHistoryStore() {
        DatabaseManager.init();
    }

    /** [History / Time] Ghi một tin nhắn (chữ, file, ảnh, video, voice kèm timestamp) vào bảng messages trong SQLite */
    public void record(ChatMessage msg) {
        if (msg == null || msg.getType() == null) return;
        String sql = "INSERT INTO messages(message_id, type, sender, display_name, to_user, group_id, group_name, content, file_name, file_size, file_data, timestamp, is_recalled) "
                + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, msg.getMessageId());
            ps.setString(2, msg.getType());
            ps.setString(3, msg.getUsername() != null ? msg.getUsername().toLowerCase() : ""); // [Sender] username người gửi
            ps.setString(4, msg.getDisplayName());                                             // [Sender] tên hiển thị
            ps.setString(5, msg.getToUser() != null ? msg.getToUser().toLowerCase() : null);   // [P2P] người nhận 1-1
            ps.setString(6, msg.getGroupId());                                                 // [Group] mã nhóm
            ps.setString(7, msg.getGroupName());                                               // [Group] tên nhóm
            ps.setString(8, msg.getContent());                                                 // Nội dung chữ / [STICKER:id]
            ps.setString(9, msg.getFileName());                                                // [File / Image / Video / Sound] tên tệp
            if (msg.getFileSize() != null) ps.setLong(10, msg.getFileSize());                  // [File / Image / Video / Sound] dung lượng
            else ps.setNull(10, Types.BIGINT);
            ps.setString(11, msg.getFileData());                                               // [File / Image / Video / Sound] đường dẫn /api/files/...
            ps.setString(12, msg.getTimestamp());                                              // [Time] mốc giờ gửi HH:MM
            ps.setInt(13, msg.getIsRecalled() ? 1 : 0);
            ps.executeUpdate(); // [History] Thực thi INSERT ghi thật xuống file SQLite data/zalo.db
        } catch (SQLException e) {
            System.err.println("[History] Lỗi lưu tin nhắn SQLite: " + e.getMessage());
        }
    }

    /** Thu hồi tin nhắn: chỉ người gửi mới có quyền thu hồi */
    public boolean markRecalled(String messageId, String actor) {
        if (messageId == null || actor == null) return false;
        String sql = "UPDATE messages SET is_recalled = 1, content = ? WHERE message_id = ? AND LOWER(sender) = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "Tin nhắn đã được thu hồi");
            ps.setString(2, messageId);
            ps.setString(3, actor.toLowerCase());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public Map<String, List<ChatMessage>> getHistoryForUser(String username, GroupManager groupManager, SocialStore socialStore) {
        Map<String, List<ChatMessage>> result = new LinkedHashMap<>();
        if (username == null) return result;
        String u = username.trim().toLowerCase();

        Set<String> myGroups = new HashSet<>();
        myGroups.add("chung");
        if (groupManager != null) {
            for (GroupManager.GroupInfo g : groupManager.getGroupsForUser(u)) {
                myGroups.add(g.getGroupId());
            }
        }

        Set<String> hiddenIds = socialStore != null ? socialStore.getHiddenMessageIds(u) : Collections.emptySet();
        Map<String, Map<String, Object>> convStates = socialStore != null ? socialStore.getConvStates(u) : Collections.emptyMap();

        String sql = "SELECT id, message_id, type, sender, display_name, to_user, group_id, group_name, content, file_name, file_size, file_data, timestamp, is_recalled "
                + "FROM messages ORDER BY id ASC";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int rowId = rs.getInt("id");
                String msgId = rs.getString("message_id");
                if (msgId != null && hiddenIds.contains(msgId)) continue;

                ChatMessage m = new ChatMessage(rs.getString("type"));
                m.setMessageId(msgId);
                m.setUsername(rs.getString("sender"));
                m.setDisplayName(rs.getString("display_name"));
                m.setToUser(rs.getString("to_user"));
                m.setGroupId(rs.getString("group_id"));
                m.setGroupName(rs.getString("group_name"));
                m.setContent(rs.getString("content"));
                m.setFileName(rs.getString("file_name"));
                long fs = rs.getLong("file_size");
                if (!rs.wasNull()) m.setFileSize(fs);
                m.setFileData(rs.getString("file_data"));
                m.setTimestamp(rs.getString("timestamp"));
                m.setIsRecalled(rs.getInt("is_recalled") == 1);

                String type = m.getType();
                String sender = m.getUsername() != null ? m.getUsername().toLowerCase() : "";
                String to = m.getToUser() != null ? m.getToUser().toLowerCase() : "";

                String targetConv = null;
                if (MessageType.MY_CLOUD.getValue().equals(type)) {
                    if (u.equals(sender)) targetConv = "my_cloud";
                } else if (MessageType.GROUP_MESSAGE.getValue().equals(type)
                        || (MessageType.FILE_TRANSFER.getValue().equals(type) && m.getGroupId() != null)) {
                    String gid = m.getGroupId() != null ? m.getGroupId() : "chung";
                    if (myGroups.contains(gid)) targetConv = gid;
                } else if (MessageType.DIRECT_MESSAGE.getValue().equals(type)
                        || (MessageType.FILE_TRANSFER.getValue().equals(type) && m.getGroupId() == null)) {
                    if (u.equals(sender) && !to.isEmpty()) targetConv = u.equals(to) ? "my_cloud" : to;
                    else if (u.equals(to) && !sender.isEmpty()) targetConv = sender;
                }

                if (targetConv != null) {
                    int clearedAfter = 0;
                    Map<String, Object> st = convStates.get(targetConv);
                    if (st != null && st.get("clearedAfterId") instanceof Integer) {
                        clearedAfter = (Integer) st.get("clearedAfterId");
                    }
                    if (rowId > clearedAfter) {
                        result.computeIfAbsent(targetConv, k -> new ArrayList<>()).add(m);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[History] Lỗi truy vấn lịch sử SQLite: " + e.getMessage());
        }
        return result;
    }
}
