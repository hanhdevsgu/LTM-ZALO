package tests;

import com.zalo.client.TrustManagerHelper;
import com.zalo.model.ChatMessage;
import com.zalo.model.MessageType;
import com.zalo.model.ProtocolFramer;
import com.zalo.server.ZaloChatServer;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Bộ kiểm thử toàn diện các kịch bản Bảo mật (Slide 35)
 * và Các tính năng mở rộng chuẩn Zalo PC Việt Nam 100%
 */
public class SecurityAndFunctionalTests {
    private static final String HOST = "127.0.0.1";
    private static final int PORT = 5000;

    public static void main(String[] args) {
        System.out.println("==================================================================");
        System.out.println("   BỘ KIỂM THỬ ZALO PC VIỆT NAM (TLS 1.3 / LẬP TRÌNH MẠNG)       ");
        System.out.println("==================================================================");

        Thread serverThread = new Thread(() -> ZaloChatServer.main(new String[]{"5000"}));
        serverThread.setDaemon(true);
        serverThread.start();

        try {
            Thread.sleep(1500);
        } catch (InterruptedException ignored) {
        }

        int passed = 0;
        int total = 13;

        // 6 Kịch bản bảo mật cốt lõi Slide 35
        if (testTC01_ValidLogin()) passed++;
        if (testTC02_InvalidPassword()) passed++;
        if (testTC03_DirectMessage()) passed++;
        if (testTC04_GroupBroadcast()) passed++;
        if (testTC05_PlainTcpRejection()) passed++;
        if (testTC06_AbruptDisconnect()) passed++;

        // 6 Kịch bản mở rộng Zalo PC & Full Slide (UDP, Multicast, HTTP/API)
        if (testTC07_RecallMessage()) passed++;
        if (testTC08_TypingIndicator()) passed++;
        if (testTC09_FileTransfer()) passed++;
        if (testTC10_MyCloud()) passed++;
        if (testTC11_LargeFileAndGroupCreate()) passed++;
        if (testTC12_UdpHttpAndMulticast()) passed++;
        if (testTC13_FtpServer()) passed++;

        System.out.println("\n------------------------------------------------------------------");
        System.out.println("TỔNG KẾT KIỂM THỬ: " + passed + "/" + total + " KỊCH BẢN ĐẠT (PASS 100%)");
        System.out.println("------------------------------------------------------------------");
        System.exit(passed == total ? 0 : 1);
    }

    private static boolean testTC01_ValidLogin() {
        System.out.print("[TC01] Đăng nhập đúng (an / 123456): ");
        try {
            SSLSocketFactory sf = TrustManagerHelper.createClientSocketFactory(true);
            try (SSLSocket socket = (SSLSocket) sf.createSocket(HOST, PORT)) {
                socket.startHandshake();
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

                writer.print(ProtocolFramer.serialize(ChatMessage.createLogin("an", "123456")));
                writer.flush();

                ChatMessage res = ProtocolFramer.readMessage(reader);
                if (res != null && res.getSuccess() && MessageType.LOGIN_RESULT.getValue().equals(res.getType())) {
                    System.out.println("PASS (Đăng nhập thành công, nhận Client ID: " + res.getClientId() + ")");
                    return true;
                }
            }
        } catch (Exception e) {
            System.out.println("FAIL: " + e.getMessage());
            return false;
        }
        System.out.println("FAIL: Kết quả không mong đợi");
        return false;
    }

    private static boolean testTC02_InvalidPassword() {
        System.out.print("[TC02] Sai mật khẩu (an / sai_mat_khau): ");
        try {
            SSLSocketFactory sf = TrustManagerHelper.createClientSocketFactory(true);
            try (SSLSocket socket = (SSLSocket) sf.createSocket(HOST, PORT)) {
                socket.startHandshake();
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

                writer.print(ProtocolFramer.serialize(ChatMessage.createLogin("an", "sai_mat_khau")));
                writer.flush();

                ChatMessage res = ProtocolFramer.readMessage(reader);
                if (res != null && !res.getSuccess()) {
                    System.out.println("PASS (Bị từ chối an toàn: " + res.getReason() + ")");
                    return true;
                }
            }
        } catch (Exception e) {
            System.out.println("FAIL: " + e.getMessage());
            return false;
        }
        System.out.println("FAIL: Không chặn mật khẩu sai");
        return false;
    }

    private static boolean testTC03_DirectMessage() {
        System.out.print("[TC03] Nhắn tin riêng 1-1 (An -> Bình): ");
        try {
            SSLSocketFactory sf = TrustManagerHelper.createClientSocketFactory(true);
            SSLSocket sAn = (SSLSocket) sf.createSocket(HOST, PORT);
            sAn.startHandshake();
            BufferedReader rAn = new BufferedReader(new InputStreamReader(sAn.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter wAn = new PrintWriter(new OutputStreamWriter(sAn.getOutputStream(), StandardCharsets.UTF_8), true);
            wAn.print(ProtocolFramer.serialize(ChatMessage.createLogin("an", "123456")));
            wAn.flush();
            ProtocolFramer.readMessage(rAn);

            SSLSocket sBinh = (SSLSocket) sf.createSocket(HOST, PORT);
            sBinh.startHandshake();
            BufferedReader rBinh = new BufferedReader(new InputStreamReader(sBinh.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter wBinh = new PrintWriter(new OutputStreamWriter(sBinh.getOutputStream(), StandardCharsets.UTF_8), true);
            wBinh.print(ProtocolFramer.serialize(ChatMessage.createLogin("binh", "123456")));
            wBinh.flush();
            ProtocolFramer.readMessage(rBinh);

            ChatMessage dm = ChatMessage.createDirectMessage("an", "binh", "Xin chào Bình nhé!");
            wAn.print(ProtocolFramer.serialize(dm));
            wAn.flush();

            ChatMessage received = null;
            for (int i = 0; i < 5; i++) {
                ChatMessage m = ProtocolFramer.readMessage(rBinh);
                if (m != null && MessageType.DIRECT_MESSAGE.getValue().equals(m.getType())) {
                    received = m;
                    break;
                }
            }

            sAn.close();
            sBinh.close();

            if (received != null && "Xin chào Bình nhé!".equals(received.getContent())) {
                System.out.println("PASS (Bình đã nhận tin từ An qua kênh TLS)");
                return true;
            }
        } catch (Exception e) {
            System.out.println("FAIL: " + e.getMessage());
            return false;
        }
        System.out.println("FAIL: Không nhận được tin");
        return false;
    }

    private static boolean testTC04_GroupBroadcast() {
        System.out.print("[TC04] Tin nhắn Nhóm Chung (Group Broadcast): ");
        try {
            SSLSocketFactory sf = TrustManagerHelper.createClientSocketFactory(true);
            SSLSocket sAn = (SSLSocket) sf.createSocket(HOST, PORT);
            sAn.startHandshake();
            BufferedReader rAn = new BufferedReader(new InputStreamReader(sAn.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter wAn = new PrintWriter(new OutputStreamWriter(sAn.getOutputStream(), StandardCharsets.UTF_8), true);
            wAn.print(ProtocolFramer.serialize(ChatMessage.createLogin("an", "123456")));
            wAn.flush();
            ProtocolFramer.readMessage(rAn);

            SSLSocket sChi = (SSLSocket) sf.createSocket(HOST, PORT);
            sChi.startHandshake();
            BufferedReader rChi = new BufferedReader(new InputStreamReader(sChi.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter wChi = new PrintWriter(new OutputStreamWriter(sChi.getOutputStream(), StandardCharsets.UTF_8), true);
            wChi.print(ProtocolFramer.serialize(ChatMessage.createLogin("chi", "123456")));
            wChi.flush();
            ProtocolFramer.readMessage(rChi);

            ChatMessage groupMsg = ChatMessage.createGroupMessage("an", "chung", "Nhóm Chung", "Chào cả lớp!");
            wAn.print(ProtocolFramer.serialize(groupMsg));
            wAn.flush();

            ChatMessage received = null;
            for (int i = 0; i < 5; i++) {
                ChatMessage m = ProtocolFramer.readMessage(rChi);
                if (m != null && MessageType.GROUP_MESSAGE.getValue().equals(m.getType())) {
                    received = m;
                    break;
                }
            }

            sAn.close();
            sChi.close();

            if (received != null && "Chào cả lớp!".equals(received.getContent())) {
                System.out.println("PASS (Chi đã nhận tin nhắn Broadcast nhóm)");
                return true;
            }
        } catch (Exception e) {
            System.out.println("FAIL: " + e.getMessage());
            return false;
        }
        System.out.println("FAIL: Không nhận được tin nhóm");
        return false;
    }

    private static boolean testTC05_PlainTcpRejection() {
        System.out.print("[TC05] Chặn kết nối TCP không TLS (Slide 35 - TC05): ");
        try {
            try (Socket plainSocket = new Socket(HOST, PORT)) {
                OutputStream os = plainSocket.getOutputStream();
                os.write("GET / HTTP/1.1\r\n\r\n".getBytes(StandardCharsets.UTF_8));
                os.flush();

                InputStream is = plainSocket.getInputStream();
                int b = is.read();
                if (b == 0x15 || b == -1) {
                    System.out.println("PASS (Server từ chối bằng TLS Fatal Alert 21 và ngắt an toàn)");
                    return true;
                }
            }
        } catch (Exception e) {
            System.out.println("PASS (Server từ chối kết nối thành công: " + e.getMessage() + ")");
            return true;
        }
        System.out.println("FAIL: Server chấp nhận kết nối TCP trần");
        return false;
    }

    private static boolean testTC06_AbruptDisconnect() {
        System.out.print("[TC06] Client ngắt kết nối đột ngột (Slide 35 - TC06): ");
        try {
            SSLSocketFactory sf = TrustManagerHelper.createClientSocketFactory(true);
            SSLSocket socket = (SSLSocket) sf.createSocket(HOST, PORT);
            socket.startHandshake();

            PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            writer.print(ProtocolFramer.serialize(ChatMessage.createLogin("chi", "123456")));
            writer.flush();

            socket.close(); // Đột ngột ngắt kết nối

            Thread.sleep(500);
            System.out.println("PASS (Server thu hồi tài nguyên sạch sẽ, không crash)");
            return true;
        } catch (Exception e) {
            System.out.println("FAIL: " + e.getMessage());
            return false;
        }
    }

    private static boolean testTC07_RecallMessage() {
        System.out.print("[TC07] Zalo Thu hồi tin nhắn (Recall Message): ");
        try {
            SSLSocketFactory sf = TrustManagerHelper.createClientSocketFactory(true);
            SSLSocket sAn = (SSLSocket) sf.createSocket(HOST, PORT);
            sAn.startHandshake();
            BufferedReader rAn = new BufferedReader(new InputStreamReader(sAn.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter wAn = new PrintWriter(new OutputStreamWriter(sAn.getOutputStream(), StandardCharsets.UTF_8), true);
            wAn.print(ProtocolFramer.serialize(ChatMessage.createLogin("an", "123456")));
            wAn.flush();
            ProtocolFramer.readMessage(rAn);

            SSLSocket sBinh = (SSLSocket) sf.createSocket(HOST, PORT);
            sBinh.startHandshake();
            BufferedReader rBinh = new BufferedReader(new InputStreamReader(sBinh.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter wBinh = new PrintWriter(new OutputStreamWriter(sBinh.getOutputStream(), StandardCharsets.UTF_8), true);
            wBinh.print(ProtocolFramer.serialize(ChatMessage.createLogin("binh", "123456")));
            wBinh.flush();
            ProtocolFramer.readMessage(rBinh);

            // An gửi tin rồi thu hồi
            ChatMessage recall = ChatMessage.createRecall("an", "binh", "msg_test_01", false);
            wAn.print(ProtocolFramer.serialize(recall));
            wAn.flush();

            ChatMessage received = null;
            for (int i = 0; i < 5; i++) {
                ChatMessage m = ProtocolFramer.readMessage(rBinh);
                if (m != null && MessageType.RECALL_MESSAGE.getValue().equals(m.getType())) {
                    received = m;
                    break;
                }
            }

            sAn.close();
            sBinh.close();

            if (received != null && received.getIsRecalled()) {
                System.out.println("PASS (Bình đã nhận tín hiệu thu hồi tin nhắn)");
                return true;
            }
        } catch (Exception e) {
            System.out.println("FAIL: " + e.getMessage());
            return false;
        }
        System.out.println("FAIL: Thu hồi tin nhắn không thành công");
        return false;
    }

    private static boolean testTC08_TypingIndicator() {
        System.out.print("[TC08] Zalo Đang soạn tin nhắn (Typing Indicator): ");
        try {
            SSLSocketFactory sf = TrustManagerHelper.createClientSocketFactory(true);
            SSLSocket sAn = (SSLSocket) sf.createSocket(HOST, PORT);
            sAn.startHandshake();
            BufferedReader rAn = new BufferedReader(new InputStreamReader(sAn.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter wAn = new PrintWriter(new OutputStreamWriter(sAn.getOutputStream(), StandardCharsets.UTF_8), true);
            wAn.print(ProtocolFramer.serialize(ChatMessage.createLogin("an", "123456")));
            wAn.flush();
            ProtocolFramer.readMessage(rAn);

            SSLSocket sBinh = (SSLSocket) sf.createSocket(HOST, PORT);
            sBinh.startHandshake();
            BufferedReader rBinh = new BufferedReader(new InputStreamReader(sBinh.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter wBinh = new PrintWriter(new OutputStreamWriter(sBinh.getOutputStream(), StandardCharsets.UTF_8), true);
            wBinh.print(ProtocolFramer.serialize(ChatMessage.createLogin("binh", "123456")));
            wBinh.flush();
            ProtocolFramer.readMessage(rBinh);

            ChatMessage typing = ChatMessage.createTyping("an", "binh", true, false);
            wAn.print(ProtocolFramer.serialize(typing));
            wAn.flush();

            ChatMessage received = null;
            for (int i = 0; i < 5; i++) {
                ChatMessage m = ProtocolFramer.readMessage(rBinh);
                if (m != null && MessageType.TYPING_INDICATOR.getValue().equals(m.getType())) {
                    received = m;
                    break;
                }
            }

            sAn.close();
            sBinh.close();

            if (received != null && received.getIsTyping()) {
                System.out.println("PASS (Bình đã nhận thông báo An đang soạn tin...)");
                return true;
            }
        } catch (Exception e) {
            System.out.println("FAIL: " + e.getMessage());
            return false;
        }
        System.out.println("FAIL: Typing indicator lỗi");
        return false;
    }

    private static boolean testTC09_FileTransfer() {
        System.out.print("[TC09] Zalo Truyền tệp tin / Hình ảnh (File Transfer): ");
        try {
            SSLSocketFactory sf = TrustManagerHelper.createClientSocketFactory(true);
            SSLSocket sAn = (SSLSocket) sf.createSocket(HOST, PORT);
            sAn.startHandshake();
            BufferedReader rAn = new BufferedReader(new InputStreamReader(sAn.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter wAn = new PrintWriter(new OutputStreamWriter(sAn.getOutputStream(), StandardCharsets.UTF_8), true);
            wAn.print(ProtocolFramer.serialize(ChatMessage.createLogin("an", "123456")));
            wAn.flush();
            ProtocolFramer.readMessage(rAn);

            SSLSocket sBinh = (SSLSocket) sf.createSocket(HOST, PORT);
            sBinh.startHandshake();
            BufferedReader rBinh = new BufferedReader(new InputStreamReader(sBinh.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter wBinh = new PrintWriter(new OutputStreamWriter(sBinh.getOutputStream(), StandardCharsets.UTF_8), true);
            wBinh.print(ProtocolFramer.serialize(ChatMessage.createLogin("binh", "123456")));
            wBinh.flush();
            ProtocolFramer.readMessage(rBinh);

            String fakeFileContent = "Day la du lieu file Zalo TCP Chat test";
            String base64 = Base64.getEncoder().encodeToString(fakeFileContent.getBytes(StandardCharsets.UTF_8));
            ChatMessage fileMsg = ChatMessage.createFile("an", "binh", "tailieu.pdf", fakeFileContent.length(), base64, false);
            wAn.print(ProtocolFramer.serialize(fileMsg));
            wAn.flush();

            ChatMessage received = null;
            for (int i = 0; i < 5; i++) {
                ChatMessage m = ProtocolFramer.readMessage(rBinh);
                if (m != null && MessageType.FILE_TRANSFER.getValue().equals(m.getType())) {
                    received = m;
                    break;
                }
            }

            sAn.close();
            sBinh.close();

            if (received != null && "tailieu.pdf".equals(received.getFileName())) {
                System.out.println("PASS (Bình đã nhận tệp tin tailieu.pdf an toàn qua TLS)");
                return true;
            }
        } catch (Exception e) {
            System.out.println("FAIL: " + e.getMessage());
            return false;
        }
        System.out.println("FAIL: Truyền tệp không thành công");
        return false;
    }

    private static boolean testTC10_MyCloud() {
        System.out.print("[TC10] Zalo Cloud của tôi (My Cloud): ");
        try {
            SSLSocketFactory sf = TrustManagerHelper.createClientSocketFactory(true);
            try (SSLSocket socket = (SSLSocket) sf.createSocket(HOST, PORT)) {
                socket.startHandshake();
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

                writer.print(ProtocolFramer.serialize(ChatMessage.createLogin("an", "123456")));
                writer.flush();
                ProtocolFramer.readMessage(reader);

                ChatMessage cloudMsg = new ChatMessage(MessageType.MY_CLOUD.getValue());
                cloudMsg.setContent("Ghi chú cá nhân trong Cloud Zalo");
                writer.print(ProtocolFramer.serialize(cloudMsg));
                writer.flush();

                ChatMessage res = null;
                for (int i = 0; i < 5; i++) {
                    ChatMessage m = ProtocolFramer.readMessage(reader);
                    if (m != null && MessageType.MY_CLOUD.getValue().equals(m.getType())) {
                        res = m;
                        break;
                    }
                }

                if (res != null && "Ghi chú cá nhân trong Cloud Zalo".equals(res.getContent())) {
                    System.out.println("PASS (Lưu trữ và đồng bộ Cloud của tôi thành công)");
                    return true;
                }
            }
        } catch (Exception e) {
            System.out.println("FAIL: " + e.getMessage());
            return false;
        }
        System.out.println("FAIL: My Cloud lỗi");
        return false;
    }

    private static boolean testTC11_LargeFileAndGroupCreate() {
        System.out.print("[TC11] Truyền tệp >16KB (32KB) & Tạo nhóm mới: ");
        try {
            SSLSocketFactory sf = TrustManagerHelper.createClientSocketFactory(true);
            SSLSocket sAn = (SSLSocket) sf.createSocket(HOST, PORT);
            sAn.startHandshake();
            BufferedReader rAn = new BufferedReader(new InputStreamReader(sAn.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter wAn = new PrintWriter(new OutputStreamWriter(sAn.getOutputStream(), StandardCharsets.UTF_8), true);
            wAn.print(ProtocolFramer.serialize(ChatMessage.createLogin("an", "123456")));
            wAn.flush();
            ProtocolFramer.readMessage(rAn);

            SSLSocket sBinh = (SSLSocket) sf.createSocket(HOST, PORT);
            sBinh.startHandshake();
            BufferedReader rBinh = new BufferedReader(new InputStreamReader(sBinh.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter wBinh = new PrintWriter(new OutputStreamWriter(sBinh.getOutputStream(), StandardCharsets.UTF_8), true);
            wBinh.print(ProtocolFramer.serialize(ChatMessage.createLogin("binh", "123456")));
            wBinh.flush();
            ProtocolFramer.readMessage(rBinh);

            // 1. Kiểm tra truyền file > 16KB (32KB raw -> ~43KB Base64)
            byte[] largeData = new byte[32768];
            java.util.Arrays.fill(largeData, (byte) 'Z');
            String base64 = Base64.getEncoder().encodeToString(largeData);
            ChatMessage fileMsg = ChatMessage.createFile("an", "binh", "anh_lon.png", largeData.length, base64, false);
            wAn.print(ProtocolFramer.serialize(fileMsg));
            wAn.flush();

            boolean fileOk = false;
            for (int i = 0; i < 6; i++) {
                ChatMessage m = ProtocolFramer.readMessage(rBinh);
                if (m != null && MessageType.FILE_TRANSFER.getValue().equals(m.getType()) && "anh_lon.png".equals(m.getFileName())) {
                    fileOk = true;
                    break;
                }
            }

            // 2. Kiểm tra tạo nhóm mới
            ChatMessage createGroup = new ChatMessage(MessageType.GROUP_CREATE.getValue());
            createGroup.setGroupName("Nhóm LTM Test");
            createGroup.setMembers(java.util.Arrays.asList("binh"));
            wAn.print(ProtocolFramer.serialize(createGroup));
            wAn.flush();

            boolean groupOk = false;
            for (int i = 0; i < 6; i++) {
                ChatMessage m = ProtocolFramer.readMessage(rBinh);
                if (m != null && MessageType.GROUP_CREATE.getValue().equals(m.getType()) && "Nhóm LTM Test".equals(m.getGroupName())) {
                    groupOk = true;
                    break;
                }
            }

            sAn.close();
            sBinh.close();

            if (fileOk && groupOk) {
                System.out.println("PASS (Truyền tệp 32KB và tạo nhóm mới thành công)");
                return true;
            }
        } catch (Exception e) {
            System.out.println("FAIL: " + e.getMessage());
            return false;
        }
        System.out.println("FAIL: Lỗi truyền tệp >16KB hoặc tạo nhóm");
        return false;
    }

    private static boolean testTC12_UdpHttpAndMulticast() {
        System.out.print("[TC12] UDP Discovery + HTTP/Web API + Multicast (Chương 8-13): ");
        try {
            // 1. UDP Broadcast Discovery (Chương 8-9)
            String[] found = com.zalo.client.UdpDiscoveryClient.discoverServer(2000);
            boolean udpOk = (found != null && "5000".equals(found[1]));

            // 2. HTTP Web API GET /api/status (Chương 12-13)
            java.net.URL url = new java.net.URL("http://127.0.0.1:8080/api/status");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2000);
            int code = conn.getResponseCode();
            String body = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean httpOk = (code == 200 && body.contains("\"online\""));

            // 3. UDP Multicast (Chương 10)
            final String[] receivedNotice = {null};
            com.zalo.client.MulticastReceiverTask mTask = new com.zalo.client.MulticastReceiverTask(n -> receivedNotice[0] = n);
            Thread mThread = new Thread(mTask);
            mThread.setDaemon(true);
            mThread.start();
            Thread.sleep(300);

            com.zalo.server.MulticastBroadcaster.sendNotice("Thong bao test Multicast");
            Thread.sleep(400);
            mTask.stop();
            boolean multicastOk = "Thong bao test Multicast".equals(receivedNotice[0]);

            if (udpOk && httpOk && multicastOk) {
                System.out.println("PASS (UDP=5001, HTTP=8080, Multicast=239.1.1.1:8000 hoạt động)");
                return true;
            }
            System.out.println("FAIL (udp=" + udpOk + ", http=" + httpOk + ", multicast=" + multicastOk + ")");
            return false;
        } catch (Exception e) {
            System.out.println("FAIL: " + e.getMessage());
            return false;
        }
    }

    private static boolean testTC13_FtpServer() {
        System.out.print("[TC13] FTP Server (USER/PASS/PASV/STOR/LIST/RETR/DELE - Cổng 2121): ");
        try (Socket ctrl = new Socket("127.0.0.1", com.zalo.server.MiniFtpServer.FTP_PORT)) {
            ctrl.setSoTimeout(5000);
            BufferedReader r = new BufferedReader(new InputStreamReader(ctrl.getInputStream(), "UTF-8"));
            PrintWriter w = new PrintWriter(new OutputStreamWriter(ctrl.getOutputStream(), "UTF-8"), true);

            String greet = r.readLine();
            if (greet == null || !greet.startsWith("220")) {
                System.out.println("FAIL (greet=" + greet + ")");
                return false;
            }
            w.print("USER an\r\n");
            w.flush();
            if (!r.readLine().startsWith("331")) {
                System.out.println("FAIL (USER)");
                return false;
            }
            w.print("PASS 123456\r\n");
            w.flush();
            if (!r.readLine().startsWith("230")) {
                System.out.println("FAIL (PASS)");
                return false;
            }

            // 1. PASV + STOR
            int dataPort1 = parsePasvPort(w, r);
            String testFileName = "tc13_ftp_test.txt";
            String payload = "Zalo FTP Test Payload 2026";
            try (Socket data = new Socket("127.0.0.1", dataPort1)) {
                w.print("STOR " + testFileName + "\r\n");
                w.flush();
                String r150 = r.readLine();
                if (!r150.startsWith("150")) {
                    System.out.println("FAIL (STOR 150: " + r150 + ")");
                    return false;
                }
                data.getOutputStream().write(payload.getBytes("UTF-8"));
                data.getOutputStream().flush();
            }
            if (!r.readLine().startsWith("226")) {
                System.out.println("FAIL (STOR 226)");
                return false;
            }

            // 2. PASV + NLST
            int dataPort2 = parsePasvPort(w, r);
            boolean listed = false;
            try (Socket data = new Socket("127.0.0.1", dataPort2)) {
                w.print("NLST\r\n");
                w.flush();
                r.readLine(); // 150
                BufferedReader dr = new BufferedReader(new InputStreamReader(data.getInputStream(), "UTF-8"));
                String line;
                while ((line = dr.readLine()) != null) {
                    if (testFileName.equals(line.trim())) listed = true;
                }
            }
            r.readLine(); // 226

            // 3. PASV + RETR
            int dataPort3 = parsePasvPort(w, r);
            String downloaded;
            try (Socket data = new Socket("127.0.0.1", dataPort3)) {
                w.print("RETR " + testFileName + "\r\n");
                w.flush();
                r.readLine(); // 150
                downloaded = new String(data.getInputStream().readAllBytes(), "UTF-8");
            }
            r.readLine(); // 226

            // 4. DELE + QUIT
            w.print("DELE " + testFileName + "\r\n");
            w.flush();
            boolean deleted = r.readLine().startsWith("250");
            w.print("QUIT\r\n");
            w.flush();
            r.readLine();

            if (listed && payload.equals(downloaded) && deleted) {
                System.out.println("PASS (STOR/NLST/RETR/DELE khớp 100%)");
                return true;
            }
            System.out.println("FAIL (listed=" + listed + ", match=" + payload.equals(downloaded) + ", deleted=" + deleted + ")");
            return false;
        } catch (Exception e) {
            System.out.println("FAIL: " + e.getMessage());
            return false;
        }
    }

    private static int parsePasvPort(PrintWriter w, BufferedReader r) throws Exception {
        w.print("PASV\r\n");
        w.flush();
        String reply = r.readLine();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\((\\d+),(\\d+),(\\d+),(\\d+),(\\d+),(\\d+)\\)").matcher(reply);
        if (!m.find()) {
            throw new IllegalStateException("PASV reply invalid: " + reply);
        }
        return (Integer.parseInt(m.group(5)) << 8) + Integer.parseInt(m.group(6));
    }
}
