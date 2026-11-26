# BÍ KÍP VẤN ĐÁP BẢO VỆ ĐỒ ÁN LẬP TRÌNH MẠNG (ĐIỂM 10 TUYỆT ĐỐI)
## Đề tài: Zalo Secure TCP Chat (Java SSLSocket / TLS 1.3)

Tài liệu này tổng hợp **12 câu hỏi kinh điển nhất mà các Thầy Cô chấm đồ án Mạng máy tính & Lập trình mạng thường hỏi**, kèm câu trả lời chuẩn kiến trúc chuyên gia để bạn tự tin đạt điểm A+!

---

### Câu 1: Tại sao đồ án của em dùng giao thức TCP mà không dùng UDP cho ứng dụng chat?
* **Trả lời chuẩn**:
  * Giao thức chat tin nhắn văn bản đòi hỏi **tính toàn vẹn và độ tin cậy 100% (Reliability)**. Tin nhắn phải đến đúng thứ tự, không bị rơi rụng gói tin (Packet Loss), và phải có cơ chế truyền lại nếu gặp lỗi đường truyền. TCP cung cấp sẵn cơ chế bắt tay 3 bước, kiểm soát luồng (Flow Control), kiểm soát tắc nghẽn (Congestion Control) và xác nhận ACK.
  * Nếu dùng UDP, gói tin có thể bị mất hoặc đảo lộn thứ tự khi mạng chập chờn. UDP chỉ nên dùng cho các tính năng thời gian thực chấp nhận mất gói nhẹ như Livestream hoặc Gọi thoại/Video (Voice Call WebRTC).

---

### Câu 2: Quá trình bắt tay 3 bước (TCP 3-Way Handshake) diễn ra ở dòng code nào?
* **Trả lời chuẩn**:
  * Phía Client: Khi gọi lệnh `socket.connect(new InetSocketAddress(host, port))` (hoặc khởi tạo socket với host và port), hệ điều hành sẽ tự động gửi gói `SYN`, nhận `SYN-ACK` từ Server và gửi lại `ACK`.
  * Phía Server: Khi chạy lệnh `serverSocket.accept()`, hàm này ở trạng thái chờ (blocking) cho đến khi bắt tay 3 bước TCP ở tầng Transport hoàn tất thì mới trả về đối tượng `Socket` kết nối.

---

### Câu 3: Quá trình TLS Handshake diễn ra khi nào? Ai gửi chứng chỉ cho ai?
* **Trả lời chuẩn**:
  * Sau khi kết nối TCP đã thông suốt, hai bên tiến hành bắt tay mật mã TLS qua lệnh `sslSocket.startHandshake()`.
  * Quá trình:
    1. Client gửi `ClientHello` (chứa phiên bản TLS 1.3/1.2 và danh sách Cipher Suites hỗ trợ).
    2. Server phản hồi `ServerHello` chọn bộ mã hóa mạnh nhất, đồng thời **gửi Chứng chỉ công khai X.509 (`server.crt`) cho Client**.
    3. Client kiểm tra tính hợp lệ của chứng chỉ (Domain/SAN, thời hạn, chữ ký số).
    4. Hai bên đàm phán bộ khóa phiên đối xứng (Session Key qua thuật toán ECDHE/RSA).
    5. Cả hai gửi thông báo `Finished` và từ thời điểm này, 100% dữ liệu được mã hóa an toàn.

---

### Câu 4: File `server.p12` và `server.crt` khác nhau như thế nào? Tại sao cấm sao chép `server.p12` sang máy Client?
* **Trả lời chuẩn**:
  * `server.p12` là kho khóa bảo mật **PKCS12 Keystore**, bên trong chứa **Private Key (Khóa bí mật)** và Certificate của Server. Khóa bí mật này dùng để giải mã dữ liệu và ký điện tử. Nếu lộ Private Key cho Client, kẻ xấu có thể giả mạo làm Server hoặc giải mã toàn bộ dữ liệu của tất cả người dùng khác.
  * `server.crt` là **Public Certificate (Chứng chỉ công khai)**, chỉ chứa Public Key và thông tin định danh Server. Client chỉ cần file này để xác minh tính chính danh của Server (chống tấn công Man-in-the-Middle).

---

### Câu 5: Nếu em dùng phần mềm Wireshark để bắt gói tin mạng, em sẽ thấy gì?
* **Trả lời chuẩn**:
  * Với Socket TCP thông thường (không TLS): Wireshark sẽ đọc được toàn bộ chuỗi JSON dạng rõ, thấy rõ tài khoản, mật khẩu đăng nhập và từng câu chat của người dùng.
  * Với **Zalo Secure TCP Chat (TLS 1.3)**: Wireshark chỉ nhìn thấy các gói tin giao thức `TLSv1.3` với dữ liệu payload là `Application Data` dạng mã hóa hỗn loạn (Encrypted Bytes), hoàn toàn không thể đọc trộm hay can thiệp vào nội dung tin nhắn.

---

### Câu 6: Tại sao sau mỗi thông điệp JSON bắt buộc phải có ký tự `\n`? Vấn đề "Dính gói / Cắt gói" là gì?
* **Trả lời chuẩn**:
  * TCP là giao thức hướng luồng byte liên tục (**Byte-Stream Protocol**), **không có khái niệm ranh giới gói tin (Message Boundary)**.
  * Nếu Client gửi liên tiếp 2 tin nhắn nhanh chóng, thuật toán Nagle của TCP có thể gộp 2 gói thành 1 (gọi là hiện tượng **Dính gói - Packet Coalescing**), hoặc một tin nhắn quá dài bị chia làm 2 phần (gọi là **Cắt gói - Packet Fragmentation**).
  * Do đó, tầng ứng dụng bắt buộc phải dùng kỹ thuật phân khung (**Framing**). Trong đồ án này, em áp dụng kỹ thuật **Line Delimiter**: mỗi thông điệp là 1 chuỗi JSON hoàn chỉnh kết thúc bằng `\n`. Khi Server gọi `reader.readLine()`, hàm này đọc chính xác từng dòng một, đảm bảo không bao giờ bị dính gói hay lỗi parse JSON.

---

### Câu 7: Tại sao mỗi `ClientSession` lại cần một `ReentrantLock sendLock` riêng? Nếu không có lock này thì bị lỗi gì?
* **Trả lời chuẩn**:
  * Trong mô hình Chat phòng nhóm (Group Broadcast), Server có thể có 5 luồng xử lý nhận tin nhắn từ 5 người cùng một lúc và đồng thời muốn gửi (ghi dữ liệu) xuống socket của Client A.
  * Trong Java, thao tác `write()` xuống luồng `OutputStream` của socket nếu bị nhiều luồng ghi cùng một lúc mà không có đồng bộ thì các byte dữ liệu sẽ bị đan xen vào nhau (**Byte Interleaving**), làm chuỗi JSON bị biến dạng và vỡ cú pháp.
  * Khóa `sendLock` đảm bảo tại một thời điểm, chỉ duy nhất **một luồng** được quyền ghi trọn vẹn một gói tin JSON xuống socket của Client đó.

---

### Câu 8: Tại sao mật khẩu người dùng không được lưu dạng rõ hay băm bằng MD5/SHA-256 thông thường?
* **Trả lời chuẩn**:
  * Lưu plain text: Nếu bị rò rỉ cơ sở dữ liệu `users.json`, kẻ xấu sẽ lấy được toàn bộ tài khoản người dùng.
  * MD5 hoặc SHA-256 thuần: Quá nhanh và dễ bị bẻ khóa bằng bảng tra cứu trước (**Rainbow Table Attack**).
  * Trong đồ án, em sử dụng thuật toán **PBKDF2WithHmacSHA256 kèm Salt ngẫu nhiên 16 bytes và 10,000 vòng lặp**:
    - Salt giúp 2 người dùng có cùng mật khẩu vẫn sinh ra 2 chuỗi băm hoàn toàn khác nhau.
    - 10,000 vòng băm làm chậm tốc độ tính toán của phần cứng kẻ tấn công (chống Brute-force hiệu quả cao).

---

### Câu 9: Làm thế nào để giao diện Zalo Desktop không bị đơ (Not Responding) khi đang đợi tin nhắn mạng?
* **Trả lời chuẩn**:
  * Em áp dụng mô hình **Đa luồng (Multi-threading)**:
    - Luồng chính (Event Dispatch Thread - EDT): Chỉ đảm nhận việc vẽ giao diện, bắt sự kiện chuột và phím bấm của người dùng.
    - Luồng phụ (`ReceiveTask`): Chạy ngầm trong một Daemon Thread riêng biệt, chịu trách nhiệm lắng nghe socket và thực hiện lệnh chờ `readLine()`.
  * Khi có tin nhắn đến từ mạng, `ReceiveTask` sử dụng lệnh `SwingUtilities.invokeLater(() -> ...)` để đẩy tác vụ vẽ bong bóng chat về cho luồng EDT cập nhật an toàn. Nhờ vậy, giao diện luôn mượt mà 100%.

---

### Câu 10: Tại sao ở Server lại dùng `ConcurrentHashMap` mà không dùng `HashMap` thông thường?
* **Trả lời chuẩn**:
  * `HashMap` thông thường không an toàn đa luồng (Non-thread-safe). Khi có nhiều client đồng thời đăng nhập (thêm session) và ngắt kết nối (xóa session), `HashMap` có thể bị lỗi vòng lặp vô tận (Infinite Loop làm CPU 100%) hoặc ném ngoại lệ `ConcurrentModificationException`.
  * `ConcurrentHashMap` áp dụng cơ chế khóa phân đoạn (Lock Striping / CAS operations), cho phép hàng trăm luồng vừa đọc vừa ghi dữ liệu đồng thời mà hiệu năng vẫn tối ưu và tuyệt đối an toàn.

---

### Câu 11: Nếu một Client bị cúp điện, rút dây mạng hoặc tắt cửa sổ đột ngột, Server xử lý thế nào?
* **Trả lời chuẩn**:
  * Khi Client tắt đột ngột, luồng `read()` ở Server sẽ bị đứt quãng và ném ra ngoại lệ `IOException` (Connection reset by peer).
  * Code của Server được bọc trong khối `try - catch - finally`:
    - Khối `catch`: Ghi nhận thông tin ngắt kết nối an toàn.
    - Khối `finally`: Luôn luôn được thực thi để đóng socket, giải phóng khóa `sendLock`, xóa client khỏi `ConcurrentHashMap`, và phát tin nhắn báo trạng thái Offline cho các bạn bè khác cập nhật danh bạ. Nhờ đó, Server không bao giờ bị rò rỉ tài nguyên bộ nhớ hay bị crash.

---

### Câu 12: Đồ án này có gì giống và khác so với ứng dụng Zalo thực tế đang chạy trên thị trường?
* **Trả lời chuẩn**:
  * **Điểm giống (Nguyên lý nền tảng)**: Đều dùng kết nối hai chiều bền vững (Persistent Socket) được bảo vệ bằng TLS, cơ chế phân khung thông điệp và quản lý phiên trực tuyến thời gian thực.
  * **Điểm khác (Quy mô thực tế)**:
    1. Zalo thực tế dùng cụm máy chủ phân tán (Distributed Gateway Cluster) hàng ngàn server, định tuyến qua Message Broker (Kafka/Pulsar) và lưu trữ phân tán (Cassandra/RocksDB).
    2. Zalo dùng chuẩn nhị phân Protocol Buffers (Protobuf) để nén dữ liệu cực nhẹ cho mạng di động 4G thay vì JSON text.
    3. Zalo có mã hóa đầu cuối (E2EE Signal Protocol) và tích hợp cuộc gọi thoại qua giao thức UDP / WebRTC.

