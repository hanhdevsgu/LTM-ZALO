package com.zalo.server;

import com.zalo.model.ChatMessage;
import com.zalo.model.MessageType;

import java.util.Collections;

/**
 * Bộ định tuyến và xử lý các loại thông điệp nghiệp vụ
 * Hỗ trợ đầy đủ các tính năng Zalo PC Việt Nam:
 * - Đăng nhập, đăng ký
 * - Nhắn tin 1-1, nhắn tin nhóm
 * - Thu hồi tin nhắn (Recall)
 * - Đang soạn tin nhắn (Typing Indicator)
 * - Truyền tệp tin / Ảnh (File Transfer)
 * - Cloud của tôi (My Cloud)
 */
public class MessageDispatcher {
    private final UserStore userStore;
    private final SessionRegistry sessionRegistry;
    private final GroupManager groupManager;
    private final MessageHistoryStore historyStore = new MessageHistoryStore();
    private final SocialStore socialStore = new SocialStore();

    public MessageDispatcher(UserStore userStore, SessionRegistry sessionRegistry, GroupManager groupManager) {
        this.userStore = userStore;
        this.sessionRegistry = sessionRegistry;
        this.groupManager = groupManager;
    }

    public GroupManager getGroupManager() { return groupManager; }
    public SocialStore getSocialStore() { return socialStore; }
    public MessageHistoryStore getHistoryStore() { return historyStore; }

    public java.util.Map<String, java.util.List<ChatMessage>> getHistoryForUser(String username) {
        return historyStore.getHistoryForUser(username, groupManager, socialStore);
    }

    public void dispatch(ClientSession session, ChatMessage msg) {
        if (msg == null || msg.getType() == null) {
            return;
        }

        MessageType msgType = MessageType.fromString(msg.getType());

        switch (msgType) {
            case LOGIN:
                handleLogin(session, msg);
                break;

            case REGISTER:
                handleRegister(session, msg);
                break;

            case DIRECT_MESSAGE:
                handleDirectMessage(session, msg);
                break;

            case GROUP_MESSAGE:
                handleGroupMessage(session, msg);
                break;

            case GROUP_CREATE:
                handleGroupCreate(session, msg);
                break;

            case RECALL_MESSAGE:
                handleRecallMessage(session, msg);
                break;

            case TYPING_INDICATOR:
                handleTypingIndicator(session, msg);
                break;

            case FILE_TRANSFER:
                handleFileTransfer(session, msg);
                break;

            case MY_CLOUD:
                handleMyCloud(session, msg);
                break;

            case USER_LIST:
                handleUserList(session);
                break;

            case PING:
                ChatMessage pong = new ChatMessage(MessageType.PONG.getValue());
                session.send(pong);
                break;

            case DISCONNECT:
                sessionRegistry.removeSession(session);
                break;

            default:
                System.out.println("[Dispatcher] Nhận loại thông điệp chưa hỗ trợ: " + msg.getType());
                break;
        }
    }

    private void handleLogin(ClientSession session, ChatMessage msg) {
        String username = msg.getUsername();
        String password = msg.getPassword();

        System.out.println("[Auth] Yêu cầu đăng nhập từ tài khoản: " + username);

        boolean ok = userStore.authenticate(username, password);
        if (ok) {
            String displayName = userStore.getDisplayName(username);

            // 1. Trả kết quả đăng nhập thành công trước cho Client
            ChatMessage res = ChatMessage.createLoginResult(true, session.getId(), "Đăng nhập thành công!");
            res.setUsername(username);
            res.setDisplayName(displayName);
            res.setUsers(sessionRegistry.buildContactsList());
            session.send(res);

            // 2. Kích hoạt trạng thái authenticated và broadcast trạng thái online cho các bạn bè
            groupManager.ensureDefaultMembership(username);
            sessionRegistry.bindAuthenticatedUser(session, username, displayName);

            // 3. Đồng bộ các nhóm chat riêng mà user là thành viên
            for (GroupManager.GroupInfo g : groupManager.getGroupsForUser(username)) {
                if (!"chung".equals(g.getGroupId())) {
                    ChatMessage groupNotify = new ChatMessage(MessageType.GROUP_CREATE.getValue());
                    groupNotify.setGroupId(g.getGroupId());
                    groupNotify.setGroupName(g.getGroupName());
                    groupNotify.setMembers(new java.util.ArrayList<>(g.getMembers()));
                    session.send(groupNotify);
                }
            }

            // 4. Gửi thông báo chào mừng
            session.send(ChatMessage.createSystem("Chào mừng " + displayName + " đã kết nối vào Zalo PC!"));
        } else {
            System.out.println("[Auth] Đăng nhập thất bại: sai tài khoản hoặc mật khẩu cho " + username);
            ChatMessage res = ChatMessage.createLoginResult(false, session.getId(), "Sai tên đăng nhập hoặc mật khẩu!");
            session.send(res);
            sessionRegistry.removeSession(session);
        }
    }

    private void handleRegister(ClientSession session, ChatMessage msg) {
        String username = msg.getUsername();
        String password = msg.getPassword();
        String displayName = msg.getDisplayName();

        boolean created = userStore.registerUser(username, password, displayName);
        if (created) {
            session.send(ChatMessage.createRegisterResult(true, "Đăng ký tài khoản thành công! Bạn có thể đăng nhập ngay."));
        } else {
            session.send(ChatMessage.createRegisterResult(false, "Tên tài khoản đã tồn tại hoặc không hợp lệ!"));
        }
    }

    private void handleDirectMessage(ClientSession session, ChatMessage msg) {
        if (!session.isAuthenticated()) return;

        String targetUsername = msg.getToUser();
        String content = msg.getContent();

        if (targetUsername == null || content == null || content.trim().isEmpty()) {
            return;
        }
        if (socialStore.isBlocked(session.getUsername(), targetUsername)) {
            session.send(ChatMessage.createSystem("Không thể gửi tin nhắn do có chặn giữa hai tài khoản."));
            return;
        }

        // Chống giả mạo người gửi và lưu lịch sử lên Server
        msg.setUsername(session.getUsername());
        msg.setDisplayName(session.getDisplayName());
        historyStore.record(msg);
        socialStore.incrementUnread(targetUsername, session.getUsername());

        ClientSession targetSession = sessionRegistry.getSessionByUsername(targetUsername);
        if (targetSession != null && targetSession.isAuthenticated()) {
            targetSession.send(msg);

            // Gửi ACK về cho người gửi (✓✓ Đã nhận)
            ChatMessage ack = ChatMessage.createDirectMessage(session.getUsername(), targetUsername, content);
            ack.setMessageId(msg.getMessageId());
            ack.setDisplayName(session.getDisplayName());
            ack.setStatus("DELIVERED");
            ack.setSuccess(true);
            session.send(ack);
        } else {
            ChatMessage offlineNotice = ChatMessage.createSystem("Người dùng [" + targetUsername + "] hiện đang Offline.");
            offlineNotice.setToUser(targetUsername);
            session.send(offlineNotice);
        }
    }

    private void handleGroupMessage(ClientSession session, ChatMessage msg) {
        if (!session.isAuthenticated()) return;

        String groupId = msg.getGroupId();
        if (groupId == null) groupId = "chung";

        GroupManager.GroupInfo group = groupManager.getGroup(groupId);
        if (group == null) {
            session.send(ChatMessage.createSystem("Lỗi: Nhóm chat [" + groupId + "] không tồn tại!"));
            return;
        }

        msg.setUsername(session.getUsername());
        msg.setDisplayName(session.getDisplayName());
        msg.setGroupName(group.getGroupName());
        historyStore.record(msg);

        // Broadcast an toàn tới các thành viên online trong nhóm
        for (String memberUser : group.getMembers()) {
            ClientSession memberSession = sessionRegistry.getSessionByUsername(memberUser);
            if (memberSession != null && memberSession.isAuthenticated()) {
                memberSession.send(msg);
            }
        }
    }

    private void handleRecallMessage(ClientSession session, ChatMessage msg) {
        if (!session.isAuthenticated()) return;

        msg.setUsername(session.getUsername());
        msg.setIsRecalled(true);
        msg.setContent("Tin nhắn đã được thu hồi");
        if (!historyStore.markRecalled(msg.getMessageId(), session.getUsername())) {
            return;
        }

        if (msg.getGroupId() != null) {
            // Thu hồi trong nhóm
            GroupManager.GroupInfo group = groupManager.getGroup(msg.getGroupId());
            if (group != null) {
                for (String m : group.getMembers()) {
                    ClientSession ms = sessionRegistry.getSessionByUsername(m);
                    if (ms != null) ms.send(msg);
                }
            }
        } else if (msg.getToUser() != null) {
            // Thu hồi tin 1-1
            ClientSession target = sessionRegistry.getSessionByUsername(msg.getToUser());
            if (target != null) target.send(msg);
            session.send(msg); // Cập nhật lại chính máy người gửi
        }
    }

    private void handleTypingIndicator(ClientSession session, ChatMessage msg) {
        if (!session.isAuthenticated()) return;

        msg.setUsername(session.getUsername());
        msg.setDisplayName(session.getDisplayName());

        if (msg.getGroupId() != null) {
            GroupManager.GroupInfo group = groupManager.getGroup(msg.getGroupId());
            if (group != null) {
                for (String m : group.getMembers()) {
                    if (!m.equalsIgnoreCase(session.getUsername())) {
                        ClientSession ms = sessionRegistry.getSessionByUsername(m);
                        if (ms != null) ms.send(msg);
                    }
                }
            }
        } else if (msg.getToUser() != null) {
            ClientSession target = sessionRegistry.getSessionByUsername(msg.getToUser());
            if (target != null) target.send(msg);
        }
    }

    private void handleFileTransfer(ClientSession session, ChatMessage msg) {
        if (!session.isAuthenticated()) return;

        msg.setUsername(session.getUsername());
        msg.setDisplayName(session.getDisplayName());
        historyStore.record(msg);

        if (msg.getGroupId() != null) {
            GroupManager.GroupInfo group = groupManager.getGroup(msg.getGroupId());
            if (group != null) {
                for (String m : group.getMembers()) {
                    ClientSession ms = sessionRegistry.getSessionByUsername(m);
                    if (ms != null) ms.send(msg);
                }
            }
        } else if (msg.getToUser() != null) {
            ClientSession target = sessionRegistry.getSessionByUsername(msg.getToUser());
            if (target != null) target.send(msg);
            session.send(msg); // Echo lại cho người gửi
        }
    }

    private void handleMyCloud(ClientSession session, ChatMessage msg) {
        if (!session.isAuthenticated()) return;
        msg.setUsername(session.getUsername());
        msg.setDisplayName(session.getDisplayName());
        historyStore.record(msg);
        msg.setToUser(session.getUsername());
        session.send(msg); // Lưu và phản hồi lại chính người dùng
    }

    private void handleGroupCreate(ClientSession session, ChatMessage msg) {
        if (!session.isAuthenticated()) return;

        String groupName = msg.getGroupName();
        if (groupName == null || groupName.trim().isEmpty()) {
            groupName = "Nhóm của " + session.getDisplayName();
        }

        GroupManager.GroupInfo newGroup = groupManager.createGroup(groupName, session.getUsername(), msg.getMembers());
        ChatMessage notify = new ChatMessage(MessageType.GROUP_CREATE.getValue());
        notify.setGroupId(newGroup.getGroupId());
        notify.setGroupName(newGroup.getGroupName());
        notify.setMembers(new java.util.ArrayList<>(newGroup.getMembers()));

        for (String member : newGroup.getMembers()) {
            ClientSession memberSession = sessionRegistry.getSessionByUsername(member);
            if (memberSession != null) {
                memberSession.send(notify);
            }
        }
    }

    private void handleUserList(ClientSession session) {
        if (!session.isAuthenticated()) return;
        ChatMessage res = new ChatMessage(MessageType.USER_LIST.getValue());
        res.setUsers(sessionRegistry.buildContactsList());
        session.send(res);
    }
}
