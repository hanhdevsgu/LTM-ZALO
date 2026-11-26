# BÁO CÁO ĐỒ ÁN CUỐI KỲ: LẬP TRÌNH MẠNG
# ĐỀ TÀI: XÂY DỰNG ỨNG DỤNG TRÒ CHUYỆN BẢO MẬT ZALO SECURE CHAT QUA GIAO THỨC TCP VÀ TLS 1.3

---

* **Môn học**: Lập trình Mạng (Network Programming)
* **Giảng viên hướng dẫn**: ThS. Trần Minh Nhật
* **Sinh viên thực hiện**: [Họ và tên sinh viên] - MSSV: [Mã số sinh viên]
* **Công nghệ sử dụng**: Java 17+, SSLSocket, PKCS12 Keystore, Gson, FlatLaf Swing
* **Năm học**: 2026

---

## MỤC LỤC

1. [CHƯƠNG 1: GIỚI THIỆU ĐỀ TÀI & BÀI TOÁN THỰC TẾ](#chương-1-giới-thiệu-đề-tài--bài-toán-thực-tế)
2. [CHƯƠNG 2: CƠ SỞ LÝ THUYẾT VÀ NỀN TẢNG MẠNG](#chương-2-cơ-sở-lý-thuyết-và-nền-tảng-mạng)
3. [CHƯƠNG 3: THIẾT KẾ KIẾN TRÚC VÀ GIAO THỨC HỆ THỐNG](#chương-3-thiết-kế-kiến-trúc-và-giao-thức-hệ-thống)
4. [CHƯƠNG 4: HIỆN THỰC HÓA CHI TIẾT CÁC PHÂN HỆ](#chương-4-hiện-thực-hóa-chi-tiết-các-phân-hệ)
5. [CHƯƠNG 5: KẾT QUẢ THỰC NGHIỆM & KIỂM THỬ BẢO MẬT (SLIDE 35)](#chương-5-kết-quả-thực-nghiệm--kiểm-thử-bảo-mật-slide-35)
6. [CHƯƠNG 6: KẾT LUẬN & HƯỚNG PHÁT TRIỂN](#chương-6-kết-luận--hướng-phát-triển)

---

## CHƯƠNG 1: GIỚI THIỆU ĐỀ TÀI & BÀI TOÁN THỰC TẾ

### 1.1 Tính cấp thiết của đề tài
Trong kỷ nguyên số, các ứng dụng trò chuyện trực tuyến (Instant Messaging) như Zalo, Telegram, WhatsApp đóng vai trò huyết mạch trong liên lạc cá nhân và làm việc nhóm. Tuy nhiên, các bài toán giao tiếp thời gian thực luôn phải đối mặt với hai thách thức lớn nhất của an toàn thông tin:
- **Nguy cơ bị nghe lén (Sniffing/Eavesdropping)**: Gói tin truyền trên mạng LAN hoặc Internet công cộng nếu không được mã hóa sẽ bị kẻ tấn công dùng Wireshark bắt trọn mật khẩu và nội dung tin nhắn.
- **Tấn công giả mạo (Man-in-the-Middle - MITM)**: Kẻ xấu có thể can thiệp vào giữa luồng truyền để đọc trộm hoặc sửa đổi nội dung tin nhắn.

Nhằm giải quyết triệt để các nguy cơ này, đồ án **"Zalo Secure TCP Chat"** được xây dựng nhằm ứng dụng toàn bộ kiến thức chuyên sâu của môn Lập trình mạng (bài giảng Chương 16.1): kết hợp Socket TCP hướng kết nối với tầng bảo mật TLS 1.3 (Transport Layer Security), chứng chỉ số X.509 và kỹ thuật phân đoạn luồng byte an toàn.

### 1.2 Mục tiêu đề tài
- Hiện thực hóa thành công mô hình **Client-Server đa luồng (Multi-threaded TCP Server)** có khả năng phục vụ đồng thời nhiều người dùng.
- Thiết lập đường hầm truyền dữ liệu bảo mật bằng **TLS 1.3 qua `SSLSocket`** trong Java.
- Triển khai giao diện máy tính hiện đại mô phỏng ngôn ngữ thiết kế của **Zalo Desktop** (sử dụng thư viện FlatLaf UI).
- Hỗ trợ đầy đủ các tính năng: Đăng nhập/Đăng ký tài khoản bảo mật bằng mật khẩu băm **PBKDF2/SHA-256 + Salt**, nhắn tin riêng 1-1, nhắn tin nhóm (Broadcast Room), và cập nhật trạng thái trực tuyến (Online/Offline) tức thời.

---

## CHƯƠNG 2: CƠ SỞ LÝ THUYẾT VÀ NỀN TẢNG MẠNG

### 2.1 Giao thức TCP (Transmission Control Protocol)
TCP là giao thức ở tầng Giao vận (Transport Layer) hoạt động theo cơ chế **hướng kết nối (Connection-oriented)**:
- **Bắt tay 3 bước (3-way handshake)**: Client gửi gói `SYN`, Server phản hồi `SYN-ACK`, Client xác nhận `ACK`. Kết nối chỉ được thiết lập khi cả hai bên sẵn sàng.
- **Đặc tính luồng byte (Byte-stream nature)**: TCP không duy trì ranh giới của từng thông điệp riêng lẻ (Message Boundary). Khi nhiều gói tin được gửi liên tiếp, chúng có thể bị gộp lại (`Coalescing`) hoặc bị phân mảnh (`Fragmentation`). Do đó, tầng ứng dụng phải tự định nghĩa cơ chế phân khung (Framing), ví dụ: sử dụng ký tự phân cách dòng `\n` (Line Delimiter).

### 2.2 Giao thức Bảo mật TLS 1.3 (Transport Layer Security)
TLS hoạt động giữa tầng Giao vận (TCP) và tầng Ứng dụng (Application), chịu trách nhiệm:
1. **Xác thực đối tác (Authentication)**: Thông qua chứng chỉ số X.509 (Certificate), Client kiểm tra định danh danh tính của Server.
2. **Bí mật thông tin (Confidentiality)**: Dữ liệu được mã hóa bằng thuật toán mã hóa đối xứng mạnh (AES-GCM hoặc ChaCha20) với khóa phiên sinh ngẫu nhiên.
3. **Toàn vẹn dữ liệu (Integrity)**: Sử dụng mã xác thực thông điệp HMAC hoặc AEAD để phát hiện bất kỳ sự can thiệp, sửa đổi gói tin nào trên đường truyền.

### 2.3 Kho khóa Java Keystore (PKCS12)
- Server lưu trữ cặp khóa (Private Key RSA 2048-bit và Certificate) trong file định dạng chuẩn **PKCS12 (`server.p12`)** được bảo vệ bởi mật khẩu.
- Client chỉ nhận **Chứng chỉ công khai (`server.crt`)** chứa Public Key để xác minh tính hợp pháp của Server, tuyệt đối không được cấp phát Private Key cho Client.

---

## CHƯƠNG 3: THIẾT KẾ KIẾN TRÚC VÀ GIAO THỨC HỆ THỐNG

### 3.1 Mô hình Kiến trúc Phân tầng

```text
┌────────────────────────────────────────────────────────┐
│             Zalo Desktop Client (Swing / FlatLaf)      │
│  - Màn hình đăng nhập/đăng ký                          │
│  - Sidebar Zalo Blue (#0068FF), Danh bạ Online/Offline │
│  - Khung bong bóng chat (Xanh / Trắng bo tròn)         │
└──────────────────────────┬─────────────────────────────┘
                           │ javax.net.ssl.SSLSocket
                           ▼
┌────────────────────────────────────────────────────────┐
│           Kênh Truyền Mã Hóa Bảo Mật (TLS 1.3)          │
│  - Bắt tay đàm phán Session Key (ECDHE / RSA 2048)     │
│  - Chống nghe lén (Sniffing), Chống MITM               │
└──────────────────────────┬─────────────────────────────┘
                           │ Cổng lắng nghe 5000
                           ▼
┌────────────────────────────────────────────────────────┐
│             Zalo Chat Server Core (Java 17+)           │
│  - SSLServerSocket: Vòng lặp Accept đa luồng           │
│  - SessionRegistry: Quản lý Client bằng ConcurrentMap  │
│  - ReentrantLock sendLock: Chống đan xen byte          │
│  - UserStore: Mật khẩu băm PBKDF2 + Salt ngẫu nhiên    │
│  - GroupManager: Quản lý thành viên & Broadcast        │
└────────────────────────────────────────────────────────┘
```

### 3.2 Chu trình Trạng thái Kết nối (State Machine)
Luồng kết nối tuân thủ nghiêm ngặt theo Slide 4 của bài giảng:
```text
DISCONNECTED ➔ TCP_CONNECTED ➔ TLS_READY ➔ AUTHENTICATED ➔ CHATTING ➔ CLOSED
```
- **Quy tắc bất di bất dịch**: Server chỉ cho phép Client gửi tin nhắn khi trạng thái phiên là `AUTHENTICATED`. Mọi cố gắng gửi tin nhắn khi chưa đăng nhập đều bị máy chủ từ chối và ghi log cảnh báo.

### 3.3 Hợp đồng Giao thức Gói tin (Protocol Specification)
Gói tin được mã hóa dưới dạng chuỗi UTF-8 JSON trên một dòng duy nhất, kết thúc bằng `\n`:
- Ví dụ gói tin Đăng nhập:
  ```json
  {"type":"login","username":"an","password":"***"}
  ```
- Ví dụ gói tin Tin nhắn 1-1:
  ```json
  {"type":"direct_message","username":"an","toUser":"binh","content":"Xin chào Bình!"}
  ```
- Ví dụ gói tin Tin nhắn Nhóm:
  ```json
  {"type":"group_message","username":"an","groupId":"chung","content":"Chào mọi người trong nhóm!"}
  ```

---

## CHƯƠNG 4: HIỆN THỰC HÓA CHI TIẾT CÁC PHÂN HỆ

### 4.1 Khóa ghi độc quyền SendLock chống đan xen byte (Slide 20 & 26)
Khi nhiều luồng (ví dụ nhiều người cùng gửi tin vào nhóm chat) đồng thời ghi dữ liệu xuống socket của một Client, nếu không có khóa đồng bộ, các luồng byte sẽ bị đan xen vào nhau khiến chuỗi JSON bị vỡ cú pháp (`JsonSyntaxException`).
Giải pháp được cài đặt trong `ClientSession.java`:
```java
public boolean send(ChatMessage message) {
    sendLock.lock();
    try {
        String jsonPayload = ProtocolFramer.serialize(message);
        writer.print(jsonPayload);
        writer.flush();
        return !writer.checkError();
    } finally {
        sendLock.unlock();
    }
}
```

### 4.2 Lưu trữ và Xác thực Mật khẩu An toàn (Slide 22)
Mật khẩu được băm bằng thuật toán **PBKDF2WithHmacSHA256** với 10,000 vòng lặp và muối ngẫu nhiên 16 bytes:
- Mật khẩu lưu trong file `data/users.json` hoàn toàn ở dạng băm (Hash Base64).
- Quá trình kiểm tra mật khẩu sử dụng hàm so sánh độ phức tạp thời gian hằng số `slowEquals` để triệt tiêu lỗ hổng tấn công phân tích thời gian (Timing Attacks).

### 4.3 Tầng Giao diện Người dùng Zalo Desktop (Slide 33 & 34)
- **Tách biệt luồng xử lý**: Luồng nhận mạng `ReceiveTask` chạy ngầm hoàn toàn độc lập với luồng đồ họa. Mọi thao tác vẽ bong bóng chat hay cập nhật danh sách bạn bè online đều được ủy quyền an toàn qua `SwingUtilities.invokeLater()`.
- **Thiết kế chuẩn Zalo**:
  - Tông màu chủ đạo: Xanh Zalo `#0068FF`.
  - Bong bóng tin nhắn đi: Màu xanh nhạt `#D2E9FF`, bo góc tròn 16px, căn lề phải.
  - Bong bóng tin nhắn đến: Màu trắng tinh `#FFFFFF` kèm viền xám mờ và Avatar tròn có chữ cái viết tắt, căn lề trái.

---

## CHƯƠNG 5: KẾT QUẢ THỰC NGHIỆM & KIỂM THỬ BẢO MẬT (SLIDE 35)

Bộ kiểm thử tự động hóa tại `tests/SecurityAndFunctionalTests.java` đã thực hiện toàn bộ 6 kịch bản bắt buộc theo Slide 35 của môn học:

| Mã TC | Kịch bản kiểm thử | Hành động kiểm thử thực tế | Kết quả mong đợi | Trạng thái |
|:---:|:---|:---|:---|:---:|
| **TC01** | Đăng nhập đúng | Tài khoản `an`, mật khẩu `123456` | Đăng nhập thành công, nhận Client ID | **PASS** |
| **TC02** | Sai mật khẩu | Tài khoản `an`, mật khẩu `sai_mat_khau` | Từ chối an toàn, ngắt kết nối, không lộ pass | **PASS** |
| **TC03** | Chat riêng 1-1 | Client An gửi tin riêng cho Client Bình | Chỉ Bình nhận được tin qua đường hầm TLS | **PASS** |
| **TC04** | Chat nhóm Broadcast | Client An gửi tin vào "Nhóm Chung" | Tất cả client online trong nhóm nhận được tin | **PASS** |
| **TC05** | Chặn kết nối TCP không TLS | Socket TCP trần gửi text thô tới cổng 5000 | Server gửi TLS Fatal Alert 21 và ngắt socket | **PASS** |
| **TC06** | Client ngắt đột ngột | Đột ngột tắt socket client mà không báo trước | Server dọn sạch tài nguyên, không crash hệ thống | **PASS** |

**Kết luận thực nghiệm**: Toàn bộ **6/6 kịch bản đều đạt chuẩn 100%**.

---

## CHƯƠNG 6: KẾT LUẬN & HƯỚNG PHÁT TRIỂN

### 6.1 Kết quả đạt được
- Đã xây dựng hoàn chỉnh một ứng dụng trò chuyện bảo mật Client-Server bằng Java có giao diện đồ họa đẹp mắt và chuyên nghiệp như Zalo Desktop.
- Nắm vững và làm chủ toàn bộ kỹ thuật lập trình mạng cấp thấp: Socket, SSLServerSocket, bắt tay TLS, đàm phán khóa phiên, luồng byte I/O, quản lý đa luồng đồng thời và cơ chế đồng bộ hóa luồng.

### 6.2 Hướng phát triển trong tương lai
1. **Mã hóa đầu cuối (End-to-End Encryption - E2EE)**: Triển khai giao thức Double Ratchet (Signal Protocol) để máy chủ Zalo hoàn toàn không đọc được nội dung tin nhắn.
2. **Truyền nhận tệp đa phương tiện (Media Sharing)**: Bổ sung module tải ảnh/video qua kênh HTTP/S3 Object Storage.
3. **Cuộc gọi thoại/video độ trễ thấp**: Tích hợp giao thức UDP kết hợp công nghệ WebRTC để hỗ trợ gọi điện trực tiếp giữa hai máy khách.

