# KỊCH BẢN 15 SLIDE THUYẾT TRÌNH BẢO VỆ ĐỒ ÁN CUỐI KỲ
## Đề tài: Zalo Secure TCP Chat (Môn: Lập trình Mạng - ĐH Sài Gòn)
> **Thời lượng thuyết trình khuyến nghị**: 10 - 15 phút  
> **Người thực hiện**: [Họ và tên sinh viên] - MSSV: [Mã số sinh viên]

---

### Slide 1: Trang bìa (Title Slide)
* **Tiêu đề lớn**: XÂY DỰNG ỨNG DỤNG TRÒ CHUYỆN BẢO MẬT ZALO SECURE CHAT QUA GIAO THỨC TCP VÀ TLS 1.3
* **Thông tin**:
  * Giảng viên hướng dẫn: ThS. Trần Minh Nhật
  * Sinh viên thực hiện: [Tên của bạn]
  * Bộ môn: Mạng máy tính & Lập trình mạng
* **Lời nói mở đầu (Speaker Notes)**:
  > *"Kính thưa quý Thầy Cô trong Hội đồng chấm thi, hôm nay em xin phép được báo cáo đồ án cuối kỳ môn Lập trình mạng với đề tài: Xây dựng ứng dụng trò chuyện bảo mật Zalo Secure Chat dựa trên nền tảng giao thức TCP và TLS 1.3 theo đúng bài giảng thực hành của bộ môn."*

---

### Slide 2: Đặt vấn đề & Mục tiêu đề tài (Motivation & Goals)
* **Nội dung slide**:
  * Bài toán thực tế: Nhu cầu trò chuyện thời gian thực (Zalo, Messenger) ngày càng phổ biến.
  * Thách thức an ninh mạng: Nguy cơ bị nghe lén (Sniffing), tấn công xen giữa (MITM), rò rỉ tài khoản trên mạng công cộng.
  * Mục tiêu đề tài:
    1. Xây dựng Server TCP đa luồng phục vụ nhiều Client đồng thời.
    2. Mã hóa toàn diện kênh truyền bằng TLS 1.3 / SSLSocket.
    3. Tái hiện trải nghiệm giao diện người dùng Zalo Desktop hiện đại.
* **Lời nói (Speaker Notes)**:
  > *"Trên môi trường mạng, việc trao đổi tin nhắn bằng Socket TCP thuần túy sẽ để lộ toàn bộ thông tin đăng nhập và nội dung riêng tư. Mục tiêu của đề tài là áp dụng kiến thức Chương 16.1 để bảo vệ kênh truyền bằng mật mã học và xây dựng một phần mềm hoàn chỉnh mang giao diện quen thuộc của Zalo."*

---

### Slide 3: Kiến trúc Tổng thể Hệ thống (System Architecture)
* **Nội dung slide**: Sơ đồ 3 tầng (Client ➔ TLS Channel ➔ Server).
  * Client: Java Swing FlatLaf, điều khiển qua `SSLSocket`.
  * Transport: Kênh mã hóa TLS 1.3 qua cổng `5000`.
  * Server: `SSLServerSocket`, `ConcurrentHashMap`, `UserStore` (PBKDF2), `ReentrantLock sendLock`.
* **Lời nói (Speaker Notes)**:
  > *"Hệ thống được thiết kế theo mô hình Client-Server hướng kết nối. Mọi kết nối vật lý đều được bọc bởi một lớp vỏ bảo vệ TLS 1.3 trước khi bất kỳ thông tin nào được trao đổi."*

---

### Slide 4: Chu trình Hoạt động của Chương trình (State Machine)
* **Nội dung slide**: 6 giai đoạn tuần tự từ Slide 4 bài giảng:
  `DISCONNECTED ➔ TCP_CONNECTED ➔ TLS_READY ➔ AUTHENTICATED ➔ CHATTING ➔ CLOSED`
  * Nguyên tắc bảo mật: Chỉ cho phép gửi tin nhắn khi trạng thái là `AUTHENTICATED`.
* **Lời nói (Speaker Notes)**:
  > *"Chương trình vận hành như một máy trạng thái hữu hạn. Dù Client đã kết nối TCP và TLS xong, Server vẫn khóa chặt quyền gửi tin nhắn cho đến khi hoàn tất bước xác thực đăng nhập tài khoản."*

---

### Slide 5: Cơ chế Bảo mật Kênh truyền TLS 1.3 (Security Layer)
* **Nội dung slide**:
  * Kho khóa Server: `server.p12` (PKCS12 Keystore chứa Private Key RSA 2048-bit).
  * Chứng chỉ công khai: `server.crt` (X.509 Certificate cung cấp cho Client).
  * Quá trình TLS Handshake: Đàm phán Session Key đối xứng (AES-256-GCM).
  * Cảnh báo bảo mật: Tuyệt đối không chia sẻ `server.p12` cho Client.
* **Lời nói (Speaker Notes)**:
  > *"Server tự quản lý khóa bí mật trong file PKCS12, Client chỉ cần chứng chỉ công khai để kiểm tra tính chính danh. Điều này bảo đảm kẻ xấu không thể can thiệp giả mạo máy chủ."*

---

### Slide 6: Giao thức Phân khung Thông điệp (Protocol & Framing Engine)
* **Nội dung slide**:
  * Đặc tính TCP: Hướng luồng byte liên tục (Byte-stream), không có ranh giới gói tin.
  * Giải pháp: Kỹ thuật phân khung bằng ký tự xuống dòng `\n` (Line Delimiter).
  * Định dạng thông điệp: Mỗi thông điệp là một chuỗi JSON UTF-8 hoàn chỉnh trên 1 dòng.
  * Cơ chế phòng thủ: Giới hạn tối đa 16KB/thông điệp để chống tấn công DoS/OOM.
* **Lời nói (Speaker Notes)**:
  > *"Do TCP có thể gây ra hiện tượng dính gói hoặc cắt gói, nhóm em đã chuẩn hóa giao thức với ranh giới ký tự xuống dòng kết hợp thư viện Gson để bóc tách gói tin an toàn 100%."*

---

### Slide 7: Cơ chế Đa luồng & Khóa ghi độc quyền SendLock (Slide 20 & 26)
* **Nội dung slide**:
  * Thách thức: Trong phòng chat nhóm, nhiều luồng cùng Broadcast tới 1 Client có thể gây lỗi đan xen byte (`Byte Interleaving`).
  * Giải pháp: Mỗi `ClientSession` sở hữu một khóa `ReentrantLock sendLock`.
  * Quản lý phiên: Dùng `ConcurrentHashMap` bảo đảm an toàn đa luồng.
* **Lời nói (Speaker Notes)**:
  > *"Điểm đặc sắc của đồ án là việc sử dụng ReentrantLock tại mỗi ClientSession. Bất kể có bao nhiêu người cùng gửi tin vào phòng chat nhóm, gói tin đi xuống socket của từng người nhận luôn được ghi trọn vẹn, không bao giờ bị vỡ cú pháp JSON."*

---

### Slide 8: Lưu trữ Mật khẩu An toàn (User Authentication - Slide 22)
* **Nội dung slide**:
  * Không lưu plain text, không dùng MD5/SHA-256 thuần.
  * Thuật toán: **PBKDF2WithHmacSHA256** kết hợp Salt ngẫu nhiên 16 bytes và 10,000 vòng lặp.
  * So khớp bằng thuật toán thời gian hằng số (chống Timing Attack).
  * Lưu trữ bền vững tại file `data/users.json`.
* **Lời nói (Speaker Notes)**:
  > *"Mật khẩu người dùng được băm chuẩn NIST với muối ngẫu nhiên, giúp chống lại hoàn toàn hình thức tấn công dò bảng cầu vồng Rainbow Table."*

---

### Slide 9: Các Tính năng Chuẩn Zalo PC Việt Nam
* **Nội dung slide**:
  1. Trò chuyện 1-1 trực tiếp (Direct Message).
  2. Phòng chat nhóm Toàn Khoa (Group Broadcast).
  3. **Thu hồi tin nhắn (Recall Message)** ở cả 2 phía.
  4. **Đang soạn tin nhắn... (Typing Indicator)** thời gian thực.
  5. **Truyền tệp tin & Hình ảnh (File Transfer)** qua luồng socket.
  6. **Cloud của tôi (My Cloud)** lưu trữ cá nhân.
  7. Trạng thái bạn bè trực tuyến (Chấm tròn xanh Online/Offline).

---

### Slide 10: Thiết kế Giao diện Zalo Desktop (FlatLaf Swing)
* **Nội dung slide**:
  * Bố cục 3.5-Pane: Sidebar Zalo Blue `#0068FF`, Cột hội thoại với 2 tab "Ưu tiên/Khác", Khung chat với bong bóng bo tròn xanh/trắng, và Ngăn kéo thông tin hội thoại (Ctrl+I).
  * Tách biệt luồng UI và luồng mạng: Dùng `SwingUtilities.invokeLater()` để giao diện luôn mượt mà 100%.

---

### Slide 11: Kết quả Kiểm thử Bảo mật & Chức năng (Slide 35)
* **Nội dung slide**: Bảng kết quả 10 kịch bản kiểm thử tự động (TC01 ➔ TC10) đạt **PASS 100%**.
  * Chặn mật khẩu sai, chặn kết nối TCP không có TLS (TLS Fatal Alert 21), dọn dẹp kết nối đột ngột an toàn.

---

### Slide 12: Chứng minh Thực nghiệm trên Wireshark
* **Nội dung slide**: Ảnh chụp màn hình Wireshark bắt gói tin cổng `5000`.
  * Thấy rõ: Bắt tay 3 bước TCP `[SYN]`, bắt tay `TLSv1.3`, và các gói tin dữ liệu `Application Data` bị mã hóa hoàn toàn thành byte nhị phân vô nghĩa.

---

### Slide 13: Đóng gói Ứng dụng Thành Phẩm (.EXE / .JAR)
* **Nội dung slide**:
  * File chạy độc lập: `dist/ZaloChat/ZaloChat.exe` (đóng gói bằng `jpackage`).
  * Chạy trên mọi máy tính Windows mà không cần cấu hình phức tạp.
  * Kèm script thực thi tự động: `run_server.bat`, `run_client.bat`.

---

### Slide 14: Demo Trực Tiếp Chương Trình (Live Demo)
* **Nội dung slide**:
  * Bước 1: Khởi động Server cổng 5000.
  * Bước 2: Mở 2 Client An và Bình ➔ Đăng nhập.
  * Bước 3: Chat 1-1, thấy typing indicator, gửi file và thử tính năng Thu hồi tin nhắn.
  * Bước 4: Mở thêm Client Chi ➔ Chat vào Nhóm Chung (Broadcast).

---

### Slide 15: Kết luận & Cảm ơn (Conclusion & Q&A)
* **Nội dung slide**:
  * Tóm tắt kết quả: Hoàn thành 100% mục tiêu đề tài môn Lập trình mạng.
  * Lời cảm ơn Thầy Cô hướng dẫn.
  * Sẵn sàng lắng nghe câu hỏi vấn đáp từ Hội đồng.
* **Lời kết (Speaker Notes)**:
  > *"Em xin chân thành cảm ơn Thầy Cô đã lắng nghe bài báo cáo. Em xin phép được bắt đầu phần Live Demo và giải đáp các câu hỏi vấn đáp của Thầy Cô ạ!"*

