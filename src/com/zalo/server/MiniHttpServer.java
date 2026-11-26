package com.zalo.server;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.*;
import com.zalo.model.ChatMessage;
import com.zalo.model.UserInfo;

import javax.net.ssl.SSLContext;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Chương 12 & 13: HTTP (8080) & HTTPS (8443) Server + Web API (Thuần Java)
 * - HTTPS cổng 8443 dùng certificates/server.p12 (bắt buộc cho ghi âm Voice Message trên trình duyệt).
 * - Lưu tệp upload ra thư mục data/uploads/ và hỗ trợ HTTP Range streaming cho Video/Âm thanh.
 * - Cung cấp đầy đủ API: Chat, Reaction, Thu hồi/Ẩn tin, Ghim tin, Kết bạn, Chặn, Trang cá nhân, Bài viết, Nhóm.
 */
public class MiniHttpServer {
    public static final int HTTP_PORT = 8080;
    public static final int HTTPS_PORT = 8443;
    private static final Path WEB_ROOT = Paths.get("web").toAbsolutePath().normalize();
    private static final Path UPLOAD_ROOT = Paths.get("data/uploads").toAbsolutePath().normalize();
    private static final AtomicInteger nextWebId = new AtomicInteger(9000);

    private final SessionRegistry sessionRegistry;
    private final MessageDispatcher dispatcher;
    private final UserStore userStore;
    private final int tlsPort;
    private final Gson gson = new Gson();
    private final Map<String, WebClientSession> webSessions = new ConcurrentHashMap<>();
    private HttpServer httpServer;
    private HttpsServer httpsServer;

    public MiniHttpServer(SessionRegistry sessionRegistry, MessageDispatcher dispatcher, UserStore userStore, int tlsPort) {
        this.sessionRegistry = sessionRegistry;
        this.dispatcher = dispatcher;
        this.userStore = userStore;
        this.tlsPort = tlsPort;
    }

    public void start() {
        try {
            Files.createDirectories(UPLOAD_ROOT.resolve("avatars"));
            httpServer = HttpServer.create(new InetSocketAddress(HTTP_PORT), 0);
            registerContexts(httpServer);
            httpServer.setExecutor(Executors.newFixedThreadPool(16));
            httpServer.start();
            System.out.println("[HTTP/API] Đang chạy tại http://localhost:" + HTTP_PORT);

            // Bật thêm HTTPS cổng 8443 dùng chung certificates/server.p12 để ghi âm giọng nói hoạt động trên mọi IP
            try {
                SSLContext sslCtx = SSLContextFactory.createServerSSLContext();
                httpsServer = HttpsServer.create(new InetSocketAddress(HTTPS_PORT), 0);
                httpsServer.setHttpsConfigurator(new HttpsConfigurator(sslCtx));
                registerContexts(httpsServer);
                httpsServer.setExecutor(Executors.newFixedThreadPool(16));
                httpsServer.start();
                System.out.println("[HTTPS/API] Đang chạy tại https://localhost:" + HTTPS_PORT + " (Hỗ trợ ghi âm Voice)");
            } catch (Exception ex) {
                System.err.println("[HTTPS/API] Không thể bật cổng " + HTTPS_PORT + ": " + ex.getMessage());
            }
        } catch (IOException e) {
            System.err.println("[HTTP/API] Không thể mở cổng " + HTTP_PORT + ": " + e.getMessage());
        }
    }

    private void registerContexts(HttpServer srv) {
        srv.createContext("/", ex -> serveStaticFile(ex, "index.html", "text/html; charset=utf-8"));
        srv.createContext("/style.css", ex -> serveStaticFile(ex, "style.css", "text/css; charset=utf-8"));
        srv.createContext("/app.js", ex -> serveStaticFile(ex, "app.js", "application/javascript; charset=utf-8"));

        srv.createContext("/api/status", this::handleApiStatus);
        srv.createContext("/api/users", this::handleApiUsers);
        srv.createContext("/api/announce", this::handleApiAnnounce);
        srv.createContext("/api/login", this::handleApiLogin);
        srv.createContext("/api/resume", this::handleApiResume);
        srv.createContext("/api/register", this::handleApiRegister);
        srv.createContext("/api/send", this::handleApiSend);
        srv.createContext("/api/poll", this::handleApiPoll);
        srv.createContext("/api/logout", this::handleApiLogout);

        // Upload & Stream tệp lớn (Video / Âm thanh / Ảnh / Avatar) với HTTP Range
        srv.createContext("/api/upload", this::handleApiUpload);
        srv.createContext("/api/files/", this::handleServeUploadedFile);

        // Tương tác tin nhắn & hội thoại
        srv.createContext("/api/react", this::handleApiReact);
        srv.createContext("/api/hide-message", this::handleApiHideMessage);
        srv.createContext("/api/clear-chat", this::handleApiClearChat);
        srv.createContext("/api/pin", this::handleApiPin);
        srv.createContext("/api/star", this::handleApiStar);
        srv.createContext("/api/mute", this::handleApiMute);
        srv.createContext("/api/read", this::handleApiRead);

        // Kết bạn, Chặn, Trang cá nhân, Bài viết
        srv.createContext("/api/friends/request", this::handleFriendRequest);
        srv.createContext("/api/friends/respond", this::handleFriendRespond);
        srv.createContext("/api/friends/unfriend", this::handleUnfriend);
        srv.createContext("/api/block", this::handleBlock);
        srv.createContext("/api/profile", this::handleProfile);
        srv.createContext("/api/posts", this::handlePosts);

        // Quản lý nhóm nâng cao
        srv.createContext("/api/groups/create", this::handleGroupCreate);
        srv.createContext("/api/groups/add-member", this::handleGroupAddMember);
        srv.createContext("/api/groups/remove-member", this::handleGroupRemoveMember);
        srv.createContext("/api/groups/transfer-owner", this::handleGroupTransferOwner);
        srv.createContext("/api/groups/leave", this::handleGroupLeave);
        srv.createContext("/api/groups/disband", this::handleGroupDisband);
    }

    private static class WebClientSession extends ClientSession {
        final Queue<ChatMessage> inbox = new ConcurrentLinkedQueue<>();
        WebClientSession(int id) { super(id); }
        @Override public boolean send(ChatMessage message) {
            if (message != null) inbox.offer(message);
            return true;
        }
    }

    private WebClientSession requireAuth(HttpExchange ex) throws IOException {
        String token = ex.getRequestHeaders().getFirst("X-Zalo-Token");
        if (token == null) token = extractQueryParam(ex.getRequestURI().getQuery(), "token");
        WebClientSession ws = token != null ? webSessions.get(token) : null;
        if (ws == null && token != null) {
            String savedUser = dispatcher.getSocialStore().getWebSessionUsername(token);
            if (savedUser != null) {
                ws = new WebClientSession(nextWebId.getAndIncrement());
                ws.setUsername(savedUser);
                ws.setDisplayName(userStore.getDisplayName(savedUser));
                ws.setAuthenticated(true);
                dispatcher.getGroupManager().ensureDefaultMembership(savedUser);
                sessionRegistry.bindAuthenticatedUser(ws, savedUser, ws.getDisplayName());
                webSessions.put(token, ws);
            }
        }
        if (ws == null || !ws.isAuthenticated()) {
            sendJson(ex, 401, Map.of("error", "Unauthorized"));
            return null;
        }
        return ws;
    }

    private void serveStaticFile(HttpExchange ex, String fileName, String contentType) throws IOException {
        Path file = WEB_ROOT.resolve(fileName).normalize();
        if (!file.startsWith(WEB_ROOT) || !Files.isRegularFile(file)) {
            sendText(ex, 404, "text/plain; charset=utf-8", "404 Not Found: " + fileName);
            return;
        }
        byte[] bytes = Files.readAllBytes(file);
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private void handleApiLogin(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { sendJson(ex, 405, Map.of("error", "POST required")); return; }
        JsonObject req = gson.fromJson(readBody(ex), JsonObject.class);
        String username = getStr(req, "username").trim();
        String password = getStr(req, "password").trim();

        if (!userStore.authenticate(username, password)) {
            sendJson(ex, 401, Map.of("success", false, "message", "Sai tên đăng nhập hoặc mật khẩu!"));
            return;
        }

        WebClientSession ws = new WebClientSession(nextWebId.getAndIncrement());
        String token = UUID.randomUUID().toString();
        webSessions.put(token, ws);
        dispatcher.dispatch(ws, ChatMessage.createLogin(username, password));
        dispatcher.getSocialStore().saveWebSession(token, ws.getUsername());
        sendJson(ex, 200, buildSessionPayload(token, ws));
    }

    private void handleApiResume(HttpExchange ex) throws IOException {
        WebClientSession ws = requireAuth(ex);
        if (ws == null) return;
        String token = ex.getRequestHeaders().getFirst("X-Zalo-Token");
        sendJson(ex, 200, buildSessionPayload(token, ws));
    }

    private Map<String, Object> buildSessionPayload(String token, WebClientSession ws) {
        String me = ws.getUsername();
        SocialStore ss = dispatcher.getSocialStore();
        Map<String, Object> out = new HashMap<>();
        out.put("success", true);
        out.put("token", token);
        out.put("username", me);
        out.put("displayName", ws.getDisplayName());
        out.put("events", drainInbox(ws));
        out.put("users", buildUsersWithAvatars());
        out.put("history", dispatcher.getHistoryForUser(me));
        out.put("reactions", ss.getAllReactionsMap());
        out.put("pinned", ss.getAllPinned());
        out.put("convStates", ss.getConvStates(me));
        out.put("friends", ss.getFriendUsernames(me));
        out.put("friendRequests", ss.getIncomingRequests(me, userStore));
        out.put("groups", buildGroupListForUser(me));
        out.put("profile", ss.getProfile(me, me, userStore));
        out.put("starred", ss.getStarredMessages(me));
        return out;
    }

    private void handleApiRegister(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { sendJson(ex, 405, Map.of("error", "POST required")); return; }
        JsonObject req = gson.fromJson(readBody(ex), JsonObject.class);
        String username = getStr(req, "username").trim();
        String password = getStr(req, "password").trim();
        String displayName = getStr(req, "displayName").trim();
        if (displayName.isEmpty()) displayName = username;

        if (!userStore.registerUser(username, password, displayName)) {
            sendJson(ex, 400, Map.of("success", false, "message", "Tài khoản đã tồn tại hoặc không hợp lệ!"));
            return;
        }
        sendJson(ex, 200, Map.of("success", true, "message", "Đăng ký thành công! Hãy đăng nhập."));
    }

    private void handleApiSend(HttpExchange ex) throws IOException {
        WebClientSession ws = requireAuth(ex);
        if (ws == null) return;
        ChatMessage msg = gson.fromJson(readBody(ex), ChatMessage.class);
        if (msg == null || msg.getType() == null) { sendJson(ex, 400, Map.of("error", "Invalid message")); return; }
        msg.setUsername(ws.getUsername());
        msg.setDisplayName(ws.getDisplayName());
        if (msg.getMessageId() == null) msg.setMessageId(UUID.randomUUID().toString());
        dispatcher.dispatch(ws, msg);
        sendJson(ex, 200, Map.of("ok", true, "messageId", msg.getMessageId()));
    }

    private void handleApiPoll(HttpExchange ex) throws IOException {
        WebClientSession ws = requireAuth(ex);
        if (ws == null) return;
        String me = ws.getUsername();
        SocialStore ss = dispatcher.getSocialStore();
        Map<String, Object> out = new HashMap<>();
        out.put("events", drainInbox(ws));
        out.put("users", buildUsersWithAvatars());
        out.put("reactions", ss.getAllReactionsMap());
        out.put("pinned", ss.getAllPinned());
        out.put("convStates", ss.getConvStates(me));
        out.put("friends", ss.getFriendUsernames(me));
        out.put("friendRequests", ss.getIncomingRequests(me, userStore));
        out.put("groups", buildGroupListForUser(me));
        sendJson(ex, 200, out);
    }

    private void handleApiLogout(HttpExchange ex) throws IOException {
        String token = ex.getRequestHeaders().getFirst("X-Zalo-Token");
        WebClientSession ws = token != null ? webSessions.remove(token) : null;
        if (ws != null) sessionRegistry.removeSession(ws);
        dispatcher.getSocialStore().deleteWebSession(token);
        sendJson(ex, 200, Map.of("ok", true));
    }

    /** POST /api/upload?name=...&folder=avatars|chat : Lưu file nhị phân trực tiếp ra đĩa */
    private void handleApiUpload(HttpExchange ex) throws IOException {
        WebClientSession ws = requireAuth(ex);
        if (ws == null) return;
        String query = ex.getRequestURI().getQuery();
        String origName = extractQueryParam(query, "name");
        String folder = extractQueryParam(query, "folder");
        if (origName == null || origName.isEmpty()) origName = "file.bin";
        origName = java.net.URLDecoder.decode(origName, StandardCharsets.UTF_8).replaceAll("[^a-zA-Z0-9._-]", "_");
        String ext = "";
        int dot = origName.lastIndexOf('.');
        if (dot >= 0) ext = origName.substring(dot);

        String subDir = "avatars".equals(folder) ? "avatars" : "chat";
        Path dir = UPLOAD_ROOT.resolve(subDir);
        Files.createDirectories(dir);
        String storedName = UUID.randomUUID() + ext;
        Path dest = dir.resolve(storedName).normalize();

        byte[] data;
        try (InputStream is = ex.getRequestBody()) {
            data = is.readAllBytes();
        }
        Files.write(dest, data);
        String url = "/api/files/" + subDir + "/" + storedName;
        sendJson(ex, 200, Map.of("ok", true, "url", url, "fileName", origName, "fileSize", data.length));
    }

    /** GET /api/files/{sub}/{name} : Phục vụ tệp từ data/uploads/ hỗ trợ HTTP Range (tua Video/Audio) */
    private void handleServeUploadedFile(HttpExchange ex) throws IOException {
        String rel = ex.getRequestURI().getPath().substring("/api/files/".length());
        Path file = UPLOAD_ROOT.resolve(rel).normalize();
        if (!file.startsWith(UPLOAD_ROOT) || !Files.isRegularFile(file)) {
            sendText(ex, 404, "text/plain; charset=utf-8", "File not found");
            return;
        }
        long len = Files.size(file);
        String mime = guessMime(file.getFileName().toString());
        ex.getResponseHeaders().set("Content-Type", mime);
        ex.getResponseHeaders().set("Accept-Ranges", "bytes");

        String rangeHeader = ex.getRequestHeaders().getFirst("Range");
        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            String[] parts = rangeHeader.substring(6).split("-", 2);
            long start = parts[0].isEmpty() ? 0 : Long.parseLong(parts[0]);
            long end = (parts.length > 1 && !parts[1].isEmpty()) ? Long.parseLong(parts[1]) : len - 1;
            if (end >= len) end = len - 1;
            long chunk = Math.max(0, end - start + 1);
            ex.getResponseHeaders().set("Content-Range", "bytes " + start + "-" + end + "/" + len);
            ex.sendResponseHeaders(206, chunk);
            try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r");
                 OutputStream os = ex.getResponseBody()) {
                raf.seek(start);
                byte[] buf = new byte[16384];
                long rem = chunk;
                while (rem > 0) {
                    int n = raf.read(buf, 0, (int) Math.min(buf.length, rem));
                    if (n == -1) break;
                    os.write(buf, 0, n);
                    rem -= n;
                }
            }
        } else {
            ex.sendResponseHeaders(200, len);
            try (InputStream is = Files.newInputStream(file); OutputStream os = ex.getResponseBody()) {
                is.transferTo(os);
            }
        }
    }

    private void handleApiReact(HttpExchange ex) throws IOException {
        WebClientSession ws = requireAuth(ex);
        if (ws == null) return;
        JsonObject req = gson.fromJson(readBody(ex), JsonObject.class);
        Map<String, Integer> res = dispatcher.getSocialStore().toggleReaction(getStr(req, "messageId"), ws.getUsername(), getStr(req, "emoji"));
        sendJson(ex, 200, Map.of("ok", true, "reactions", res));
    }

    private void handleApiHideMessage(HttpExchange ex) throws IOException {
        WebClientSession ws = requireAuth(ex);
        if (ws == null) return;
        JsonObject req = gson.fromJson(readBody(ex), JsonObject.class);
        dispatcher.getSocialStore().hideMessageForUser(getStr(req, "messageId"), ws.getUsername());
        sendJson(ex, 200, Map.of("ok", true));
    }

    private void handleApiClearChat(HttpExchange ex) throws IOException {
        WebClientSession ws = requireAuth(ex);
        if (ws == null) return;
        JsonObject req = gson.fromJson(readBody(ex), JsonObject.class);
        dispatcher.getSocialStore().clearConversationForUser(ws.getUsername(), getStr(req, "convId"));
        sendJson(ex, 200, Map.of("ok", true));
    }

    private void handleApiPin(HttpExchange ex) throws IOException {
        WebClientSession ws = requireAuth(ex);
        if (ws == null) return;
        JsonObject req = gson.fromJson(readBody(ex), JsonObject.class);
        dispatcher.getSocialStore().setPinnedMessage(getStr(req, "convId"), getStr(req, "messageId"), getStr(req, "content"), ws.getDisplayName());
        sendJson(ex, 200, Map.of("ok", true));
    }

    private void handleApiStar(HttpExchange ex) throws IOException {
        WebClientSession ws = requireAuth(ex);
        if (ws == null) return;
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 200, Map.of("starred", dispatcher.getSocialStore().getStarredMessages(ws.getUsername())));
            return;
        }
        JsonObject req = gson.fromJson(readBody(ex), JsonObject.class);
        boolean starred = dispatcher.getSocialStore().toggleStarMessage(
                ws.getUsername(), getStr(req, "messageId"), getStr(req, "convId"),
                getStr(req, "senderName"), getStr(req, "content"), getStr(req, "timestamp"));
        sendJson(ex, 200, Map.of("ok", true, "starred", starred, "list", dispatcher.getSocialStore().getStarredMessages(ws.getUsername())));
    }

    private void handleApiMute(HttpExchange ex) throws IOException {
        WebClientSession ws = requireAuth(ex);
        if (ws == null) return;
        JsonObject req = gson.fromJson(readBody(ex), JsonObject.class);
        boolean muted = req.has("muted") && req.get("muted").getAsBoolean();
        dispatcher.getSocialStore().toggleMute(ws.getUsername(), getStr(req, "convId"), muted);
        sendJson(ex, 200, Map.of("ok", true));
    }

    private void handleApiRead(HttpExchange ex) throws IOException {
        WebClientSession ws = requireAuth(ex);
        if (ws == null) return;
        JsonObject req = gson.fromJson(readBody(ex), JsonObject.class);
        dispatcher.getSocialStore().markRead(ws.getUsername(), getStr(req, "convId"));
        sendJson(ex, 200, Map.of("ok", true));
    }

    private void handleFriendRequest(HttpExchange ex) throws IOException {
        WebClientSession ws = requireAuth(ex);
        if (ws == null) return;
        JsonObject req = gson.fromJson(readBody(ex), JsonObject.class);
        String msg = dispatcher.getSocialStore().sendFriendRequest(ws.getUsername(), getStr(req, "toUser"));
        sendJson(ex, "OK".equals(msg) ? 200 : 400, Map.of("ok", "OK".equals(msg), "message", msg));
    }

    private void handleFriendRespond(HttpExchange ex) throws IOException {
        WebClientSession ws = requireAuth(ex);
        if (ws == null) return;
        JsonObject req = gson.fromJson(readBody(ex), JsonObject.class);
        boolean accept = req.has("accept") && req.get("accept").getAsBoolean();
        dispatcher.getSocialStore().respondFriendRequest(ws.getUsername(), getStr(req, "fromUser"), accept);
        sendJson(ex, 200, Map.of("ok", true));
    }

    private void handleUnfriend(HttpExchange ex) throws IOException {
        WebClientSession ws = requireAuth(ex);
        if (ws == null) return;
        JsonObject req = gson.fromJson(readBody(ex), JsonObject.class);
        dispatcher.getSocialStore().unfriend(ws.getUsername(), getStr(req, "targetUser"));
        sendJson(ex, 200, Map.of("ok", true));
    }

    private void handleBlock(HttpExchange ex) throws IOException {
        WebClientSession ws = requireAuth(ex);
        if (ws == null) return;
        JsonObject req = gson.fromJson(readBody(ex), JsonObject.class);
        boolean block = req.has("block") && req.get("block").getAsBoolean();
        dispatcher.getSocialStore().setBlock(ws.getUsername(), getStr(req, "targetUser"), block);
        sendJson(ex, 200, Map.of("ok", true));
    }

    private void handleProfile(HttpExchange ex) throws IOException {
        WebClientSession ws = requireAuth(ex);
        if (ws == null) return;
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            String target = extractQueryParam(ex.getRequestURI().getQuery(), "user");
            if (target == null || target.isEmpty()) target = ws.getUsername();
            sendJson(ex, 200, dispatcher.getSocialStore().getProfile(target, ws.getUsername(), userStore));
        } else {
            JsonObject req = gson.fromJson(readBody(ex), JsonObject.class);
            String newName = getStr(req, "displayName").trim();
            if (!newName.isEmpty()) userStore.updateDisplayName(ws.getUsername(), newName);
            dispatcher.getSocialStore().updateProfile(ws.getUsername(), getStr(req, "avatarUrl"), getStr(req, "bio"));
            sendJson(ex, 200, Map.of("ok", true));
        }
    }

    private void handlePosts(HttpExchange ex) throws IOException {
        WebClientSession ws = requireAuth(ex);
        if (ws == null) return;
        JsonObject req = gson.fromJson(readBody(ex), JsonObject.class);
        dispatcher.getSocialStore().createPost(ws.getUsername(), getStr(req, "content"));
        sendJson(ex, 200, Map.of("ok", true));
    }

    private void handleGroupCreate(HttpExchange ex) throws IOException {
        WebClientSession ws = requireAuth(ex);
        if (ws == null) return;
        JsonObject req = gson.fromJson(readBody(ex), JsonObject.class);
        String name = getStr(req, "groupName").trim();
        if (name.isEmpty()) { sendJson(ex, 400, Map.of("error", "Tên nhóm rỗng")); return; }
        List<String> members = new ArrayList<>();
        if (req.has("members") && req.get("members").isJsonArray()) {
            JsonArray arr = req.getAsJsonArray("members");
            for (JsonElement el : arr) members.add(el.getAsString());
        }
        GroupManager.GroupInfo g = dispatcher.getGroupManager().createGroup(name, ws.getUsername(), members);
        sendJson(ex, 200, Map.of("ok", true, "groupId", g.getGroupId(), "groupName", g.getGroupName()));
    }

    private void handleGroupAddMember(HttpExchange ex) throws IOException {
        WebClientSession ws = requireAuth(ex);
        if (ws == null) return;
        JsonObject req = gson.fromJson(readBody(ex), JsonObject.class);
        boolean ok = dispatcher.getGroupManager().addMember(getStr(req, "groupId"), ws.getUsername(), getStr(req, "username"));
        sendJson(ex, ok ? 200 : 403, Map.of("ok", ok));
    }

    private void handleGroupRemoveMember(HttpExchange ex) throws IOException {
        WebClientSession ws = requireAuth(ex);
        if (ws == null) return;
        JsonObject req = gson.fromJson(readBody(ex), JsonObject.class);
        boolean ok = dispatcher.getGroupManager().removeMember(getStr(req, "groupId"), ws.getUsername(), getStr(req, "username"));
        sendJson(ex, ok ? 200 : 403, Map.of("ok", ok));
    }

    private void handleGroupTransferOwner(HttpExchange ex) throws IOException {
        WebClientSession ws = requireAuth(ex);
        if (ws == null) return;
        JsonObject req = gson.fromJson(readBody(ex), JsonObject.class);
        boolean ok = dispatcher.getGroupManager().transferOwner(getStr(req, "groupId"), ws.getUsername(), getStr(req, "newOwner"));
        sendJson(ex, ok ? 200 : 403, Map.of("ok", ok));
    }

    private void handleGroupLeave(HttpExchange ex) throws IOException {
        WebClientSession ws = requireAuth(ex);
        if (ws == null) return;
        JsonObject req = gson.fromJson(readBody(ex), JsonObject.class);
        String res = dispatcher.getGroupManager().leaveGroup(getStr(req, "groupId"), ws.getUsername());
        sendJson(ex, "OK".equals(res) ? 200 : 400, Map.of("ok", "OK".equals(res), "message", res));
    }

    private void handleGroupDisband(HttpExchange ex) throws IOException {
        WebClientSession ws = requireAuth(ex);
        if (ws == null) return;
        JsonObject req = gson.fromJson(readBody(ex), JsonObject.class);
        boolean ok = dispatcher.getGroupManager().disbandGroup(getStr(req, "groupId"), ws.getUsername());
        sendJson(ex, ok ? 200 : 403, Map.of("ok", ok));
    }

    private List<UserInfo> buildUsersWithAvatars() {
        List<UserInfo> list = sessionRegistry.buildContactsList();
        for (UserInfo u : list) {
            u.setAvatarUrl(dispatcher.getSocialStore().getAvatarUrl(u.getUsername()));
        }
        return list;
    }

    private List<Map<String, Object>> buildGroupListForUser(String username) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (GroupManager.GroupInfo g : dispatcher.getGroupManager().getGroupsForUser(username)) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", g.getGroupId());
            m.put("name", g.getGroupName());
            m.put("owner", g.getCreatedBy());
            m.put("members", new ArrayList<>(g.getMembers()));
            list.add(m);
        }
        return list;
    }

    private void handleApiStatus(HttpExchange ex) throws IOException {
        Map<String, Object> data = new HashMap<>();
        data.put("status", "online");
        data.put("tlsPort", tlsPort);
        data.put("httpsPort", HTTPS_PORT);
        data.put("udpDiscoveryPort", UdpDiscoveryServer.DISCOVERY_PORT);
        data.put("ftpPort", MiniFtpServer.FTP_PORT);
        data.put("multicastGroup", MulticastBroadcaster.GROUP_IP + ":" + MulticastBroadcaster.GROUP_PORT);
        data.put("onlineCount", sessionRegistry.getAllOnlineSessions().size());
        data.put("dbPath", "data/zalo.db");
        data.put("dbStats", dispatcher.getSocialStore().getDbStats());
        sendJson(ex, 200, data);
    }

    private void handleApiUsers(HttpExchange ex) throws IOException {
        List<UserInfo> users = buildUsersWithAvatars();
        sendJson(ex, 200, users);
    }

    private void handleApiAnnounce(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { sendJson(ex, 405, Map.of("error", "POST required")); return; }
        String body = readBody(ex).trim();
        if (body.isEmpty()) { sendJson(ex, 400, Map.of("error", "Nội dung rỗng")); return; }
        MulticastBroadcaster.sendNotice(body);
        sessionRegistry.broadcastSystemMessage("[Thông báo chung] " + body);
        sendJson(ex, 200, Map.of("ok", true));
    }

    private List<ChatMessage> drainInbox(WebClientSession ws) {
        List<ChatMessage> list = new ArrayList<>();
        ChatMessage m;
        while ((m = ws.inbox.poll()) != null) list.add(m);
        return list;
    }

    private String readBody(HttpExchange ex) throws IOException {
        try (InputStream is = ex.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String getStr(JsonObject obj, String key) {
        return (obj != null && obj.has(key) && !obj.get(key).isJsonNull()) ? obj.get(key).getAsString() : "";
    }

    private String extractQueryParam(String query, String key) {
        if (query == null) return null;
        for (String pair : query.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0 && pair.substring(0, idx).equals(key)) return pair.substring(idx + 1);
        }
        return null;
    }

    private String guessMime(String fn) {
        String l = fn.toLowerCase();
        if (l.endsWith(".png")) return "image/png";
        if (l.endsWith(".jpg") || l.endsWith(".jpeg")) return "image/jpeg";
        if (l.endsWith(".gif")) return "image/gif";
        if (l.endsWith(".webp")) return "image/webp";
        if (l.endsWith(".mp4")) return "video/mp4";
        if (l.endsWith(".webm")) return "video/webm";
        if (l.endsWith(".mp3")) return "audio/mpeg";
        if (l.endsWith(".wav")) return "audio/wav";
        if (l.endsWith(".ogg")) return "audio/ogg";
        if (l.endsWith(".pdf")) return "application/pdf";
        return "application/octet-stream";
    }

    private void sendJson(HttpExchange ex, int status, Object obj) throws IOException {
        sendText(ex, status, "application/json; charset=utf-8", gson.toJson(obj));
    }

    private void sendText(HttpExchange ex, int status, String ct, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", ct);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    public void stop() {
        if (httpServer != null) httpServer.stop(0);
        if (httpsServer != null) httpsServer.stop(0);
    }
}
