package com.zalo.model;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Cấu trúc thông điệp chuẩn hóa theo định dạng JSON
 * Nâng cấp toàn diện hỗ trợ 100% tính năng Zalo PC Việt Nam
 */
public class ChatMessage implements Serializable {
    private String messageId;
    private String type;
    private String username;
    private String password;
    private String displayName;
    private String toUser;
    private String groupId;
    private String groupName;
    private List<String> members;
    private String content;
    private String timestamp;
    private Boolean success;
    private Integer clientId;
    private List<UserInfo> users;
    private String reason;

    // Các trường đặc trưng Zalo PC
    private Boolean isRecalled = false;
    private Boolean isTyping = false;
    private String fileName;
    private Long fileSize;
    private String fileData; // Dữ liệu Base64
    private String status = "SENT"; // SENT, DELIVERED, SEEN
    private String tab = "PRIORITY"; // PRIORITY, OTHER

    public ChatMessage() {
        this.messageId = UUID.randomUUID().toString().substring(0, 8);
        this.timestamp = new SimpleDateFormat("HH:mm").format(new Date());
    }

    public ChatMessage(String type) {
        this();
        this.type = type;
    }

    // Static Factory Helpers
    public static ChatMessage createLogin(String username, String password) {
        ChatMessage msg = new ChatMessage(MessageType.LOGIN.getValue());
        msg.setUsername(username);
        msg.setPassword(password);
        return msg;
    }

    public static ChatMessage createLoginResult(boolean success, int clientId, String reason) {
        ChatMessage msg = new ChatMessage(MessageType.LOGIN_RESULT.getValue());
        msg.setSuccess(success);
        msg.setClientId(clientId);
        msg.setReason(reason);
        return msg;
    }

    public static ChatMessage createRegister(String username, String password, String displayName) {
        ChatMessage msg = new ChatMessage(MessageType.REGISTER.getValue());
        msg.setUsername(username);
        msg.setPassword(password);
        msg.setDisplayName(displayName);
        return msg;
    }

    public static ChatMessage createRegisterResult(boolean success, String reason) {
        ChatMessage msg = new ChatMessage(MessageType.REGISTER_RESULT.getValue());
        msg.setSuccess(success);
        msg.setReason(reason);
        return msg;
    }

    public static ChatMessage createDirectMessage(String fromUser, String toUser, String content) {
        ChatMessage msg = new ChatMessage(MessageType.DIRECT_MESSAGE.getValue());
        msg.setUsername(fromUser);
        msg.setToUser(toUser);
        msg.setContent(content);
        return msg;
    }

    public static ChatMessage createGroupMessage(String fromUser, String groupId, String groupName, String content) {
        ChatMessage msg = new ChatMessage(MessageType.GROUP_MESSAGE.getValue());
        msg.setUsername(fromUser);
        msg.setGroupId(groupId);
        msg.setGroupName(groupName);
        msg.setContent(content);
        return msg;
    }

    public static ChatMessage createRecall(String fromUser, String targetId, String messageId, boolean isGroup) {
        ChatMessage msg = new ChatMessage(MessageType.RECALL_MESSAGE.getValue());
        msg.setUsername(fromUser);
        msg.setMessageId(messageId);
        if (isGroup) {
            msg.setGroupId(targetId);
        } else {
            msg.setToUser(targetId);
        }
        msg.setIsRecalled(true);
        msg.setContent("Tin nhắn đã được thu hồi");
        return msg;
    }

    public static ChatMessage createTyping(String fromUser, String targetId, boolean isTyping, boolean isGroup) {
        ChatMessage msg = new ChatMessage(MessageType.TYPING_INDICATOR.getValue());
        msg.setUsername(fromUser);
        msg.setIsTyping(isTyping);
        if (isGroup) {
            msg.setGroupId(targetId);
        } else {
            msg.setToUser(targetId);
        }
        return msg;
    }

    public static ChatMessage createFile(String fromUser, String targetId, String fileName, long fileSize, String fileBase64, boolean isGroup) {
        ChatMessage msg = new ChatMessage(MessageType.FILE_TRANSFER.getValue());
        msg.setUsername(fromUser);
        msg.setFileName(fileName);
        msg.setFileSize(fileSize);
        msg.setFileData(fileBase64);
        msg.setContent("[Tệp tin] " + fileName);
        if (isGroup) {
            msg.setGroupId(targetId);
        } else {
            msg.setToUser(targetId);
        }
        return msg;
    }

    public static ChatMessage createSystem(String content) {
        ChatMessage msg = new ChatMessage(MessageType.SYSTEM.getValue());
        msg.setContent(content);
        return msg;
    }

    // Getters and Setters
    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDisplayName() {
        return displayName != null && !displayName.trim().isEmpty() ? displayName : username;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getToUser() {
        return toUser;
    }

    public void setToUser(String toUser) {
        this.toUser = toUser;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public List<String> getMembers() {
        return members;
    }

    public void setMembers(List<String> members) {
        this.members = members;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public Boolean getSuccess() {
        return success != null && success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public Integer getClientId() {
        return clientId;
    }

    public void setClientId(Integer clientId) {
        this.clientId = clientId;
    }

    public List<UserInfo> getUsers() {
        return users;
    }

    public void setUsers(List<UserInfo> users) {
        this.users = users;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Boolean getIsRecalled() {
        return isRecalled != null && isRecalled;
    }

    public void setIsRecalled(Boolean isRecalled) {
        this.isRecalled = isRecalled;
    }

    public Boolean getIsTyping() {
        return isTyping != null && isTyping;
    }

    public void setIsTyping(Boolean isTyping) {
        this.isTyping = isTyping;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getFileData() {
        return fileData;
    }

    public void setFileData(String fileData) {
        this.fileData = fileData;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTab() {
        return tab;
    }

    public void setTab(String tab) {
        this.tab = tab;
    }
}
