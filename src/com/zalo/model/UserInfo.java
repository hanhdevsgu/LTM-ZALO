package com.zalo.model;

import java.io.Serializable;

/**
 * Thông tin định danh và trạng thái hiển thị của một người dùng
 */
public class UserInfo implements Serializable {
    private String username;
    private String displayName;
    private boolean online;
    private String lastSeen;
    private String avatarUrl;

    public UserInfo() {
    }

    public UserInfo(String username, String displayName, boolean online) {
        this.username = username;
        this.displayName = displayName;
        this.online = online;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName != null && !displayName.trim().isEmpty() ? displayName : username;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    public String getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(String lastSeen) {
        this.lastSeen = lastSeen;
    }

    public String getAvatarUrl() {
        return avatarUrl != null ? avatarUrl : "";
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    @Override
    public String toString() {
        return getDisplayName() + (online ? " (Online)" : " (Offline)");
    }
}

