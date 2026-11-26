package com.zalo.server;

import java.io.Serializable;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quản lý các nhóm trò chuyện (Group Chat Room) lưu bền vững trong SQLite (data/zalo.db).
 * Hỗ trợ trưởng nhóm, thêm thành viên, chuyển quyền trưởng nhóm, rời nhóm và giải tán nhóm.
 */
public class GroupManager {
    public static class GroupInfo implements Serializable {
        private final String groupId;
        private final String groupName;
        private String createdBy;
        private final Set<String> members = Collections.synchronizedSet(new HashSet<>());

        public GroupInfo(String groupId, String groupName, String createdBy) {
            this.groupId = groupId;
            this.groupName = groupName;
            this.createdBy = createdBy != null ? createdBy.toLowerCase() : "system";
            if (!"system".equals(this.createdBy)) {
                this.members.add(this.createdBy);
            }
        }

        public String getGroupId() { return groupId; }
        public String getGroupName() { return groupName; }
        public String getCreatedBy() { return createdBy; }
        public void setCreatedBy(String owner) { this.createdBy = owner != null ? owner.toLowerCase() : "system"; }
        public Set<String> getMembers() { return members; }
        public void addMember(String username) { if (username != null) members.add(username.toLowerCase()); }
        public void removeMember(String username) { if (username != null) members.remove(username.toLowerCase()); }
        public boolean hasMember(String username) { return username != null && members.contains(username.toLowerCase()); }
    }

    private final Map<String, GroupInfo> groups = new ConcurrentHashMap<>();

    public GroupManager() {
        DatabaseManager.init();
        loadFromDb();
        if (!groups.containsKey("chung")) {
            GroupInfo general = new GroupInfo("chung", "Nhóm Chung Toàn Khoa (SGU)", "system");
            general.addMember("an");
            general.addMember("binh");
            general.addMember("chi");
            saveGroupToDb(general);
            groups.put("chung", general);
        }
    }

    public synchronized GroupInfo createGroup(String groupName, String creator, List<String> initialMembers) {
        String groupId = "grp_" + System.currentTimeMillis();
        GroupInfo group = new GroupInfo(groupId, groupName, creator);
        if (initialMembers != null) {
            for (String m : initialMembers) group.addMember(m);
        }
        saveGroupToDb(group);
        groups.put(groupId, group);
        return group;
    }

    public GroupInfo getGroup(String groupId) {
        return groups.get(groupId);
    }

    /** [Group] Trưởng nhóm thêm thành viên mới vào nhóm và lưu SQLite */
    public synchronized boolean addMember(String groupId, String actor, String newMember) {
        GroupInfo g = groups.get(groupId);
        if (g == null || newMember == null) return false;
        if (!g.getCreatedBy().equalsIgnoreCase(actor) && !"system".equals(g.getCreatedBy())) return false;
        String u = newMember.trim().toLowerCase();
        g.addMember(u);
        addMemberToDb(groupId, u);
        return true;
    }

    /** [Group] Trưởng nhóm xóa thành viên khỏi nhóm và cập nhật SQLite */
    public synchronized boolean removeMember(String groupId, String actor, String target) {
        GroupInfo g = groups.get(groupId);
        if (g == null || target == null) return false;
        if (!g.getCreatedBy().equalsIgnoreCase(actor) && !("chung".equals(groupId) && "system".equals(g.getCreatedBy()))) return false;
        String u = target.trim().toLowerCase();
        if (u.equalsIgnoreCase(actor) || !g.hasMember(u)) return false;
        g.removeMember(u);
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM group_members WHERE group_id = ? AND username = ?")) {
            ps.setString(1, groupId);
            ps.setString(2, u);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
        return true;
    }

    /** [Group] Trưởng nhóm chuyển quyền trưởng nhóm cho thành viên khác */
    public synchronized boolean transferOwner(String groupId, String actor, String newOwner) {
        GroupInfo g = groups.get(groupId);
        if (g == null) return false;
        if ((!g.getCreatedBy().equalsIgnoreCase(actor) && !"system".equals(g.getCreatedBy())) || !g.hasMember(newOwner)) return false;
        g.setCreatedBy(newOwner);
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE groups SET owner = ? WHERE group_id = ?")) {
            ps.setString(1, g.getCreatedBy());
            ps.setString(2, groupId);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
        return true;
    }

    /** Rời nhóm: bắt buộc trưởng nhóm phải chuyển quyền trước khi rời (trừ nhóm chung) */
    public synchronized String leaveGroup(String groupId, String username) {
        GroupInfo g = groups.get(groupId);
        if (g == null) return "Nhóm không tồn tại!";
        String u = username.toLowerCase();
        if (g.getCreatedBy().equalsIgnoreCase(u) && g.getMembers().size() > 1) {
            return "Trưởng nhóm phải chuyển quyền trưởng nhóm trước khi rời!";
        }
        g.removeMember(u);
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM group_members WHERE group_id = ? AND username = ?")) {
            ps.setString(1, groupId);
            ps.setString(2, u);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
        if (g.getMembers().isEmpty()) {
            disbandGroup(groupId, u);
        }
        return "OK";
    }

    /** [Group] Giải tán nhóm và xóa toàn bộ thành viên liên quan khỏi SQLite */
    public synchronized boolean disbandGroup(String groupId, String actor) {
        GroupInfo g = groups.get(groupId);
        if (g == null) return false;
        if (!g.getMembers().isEmpty() && !g.getCreatedBy().equalsIgnoreCase(actor) && !"system".equals(g.getCreatedBy())) return false;
        groups.remove(groupId);
        try (Connection conn = DatabaseManager.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM group_members WHERE group_id = ?")) {
                ps.setString(1, groupId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM groups WHERE group_id = ?")) {
                ps.setString(1, groupId);
                ps.executeUpdate();
            }
        } catch (SQLException ignored) {}
        return true;
    }

    public void ensureDefaultMembership(String username) {
        if (username == null || username.trim().isEmpty()) return;
        String u = username.trim().toLowerCase();
        GroupInfo general = groups.get("chung");
        if (general != null && !general.hasMember(u)) {
            general.addMember(u);
            addMemberToDb("chung", u);
        }
    }

    public Collection<GroupInfo> getAllGroups() { return groups.values(); }

    public List<GroupInfo> getGroupsForUser(String username) {
        List<GroupInfo> userGroups = new ArrayList<>();
        String u = username.toLowerCase();
        for (GroupInfo g : groups.values()) {
            if (g.hasMember(u)) userGroups.add(g);
        }
        return userGroups;
    }

    private void loadFromDb() {
        try (Connection conn = DatabaseManager.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("SELECT group_id, group_name, owner FROM groups");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    GroupInfo g = new GroupInfo(rs.getString("group_id"), rs.getString("group_name"), rs.getString("owner"));
                    groups.put(g.getGroupId(), g);
                }
            }
            try (PreparedStatement ps = conn.prepareStatement("SELECT group_id, username FROM group_members");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    GroupInfo g = groups.get(rs.getString("group_id"));
                    if (g != null) g.addMember(rs.getString("username"));
                }
            }
        } catch (SQLException e) {
            System.err.println("[GroupManager] Lỗi nạp nhóm từ SQLite: " + e.getMessage());
        }
    }

    private void saveGroupToDb(GroupInfo g) {
        try (Connection conn = DatabaseManager.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("INSERT OR IGNORE INTO groups(group_id, group_name, owner) VALUES(?,?,?)")) {
                ps.setString(1, g.getGroupId());
                ps.setString(2, g.getGroupName());
                ps.setString(3, g.getCreatedBy());
                ps.executeUpdate();
            }
            for (String m : g.getMembers()) addMemberToDb(g.getGroupId(), m);
        } catch (SQLException e) {
            System.err.println("[GroupManager] Lỗi lưu nhóm vào SQLite: " + e.getMessage());
        }
    }

    private void addMemberToDb(String groupId, String username) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT OR IGNORE INTO group_members(group_id, username) VALUES(?,?)")) {
            ps.setString(1, groupId);
            ps.setString(2, username);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }
}
