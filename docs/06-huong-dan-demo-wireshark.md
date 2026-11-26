# HƯỚNG DẪN BẮT GÓI TIN WIRESHARK & CHỨNG MINH BẢO MẬT TLS 1.3
## Đề tài: Zalo Secure TCP Chat (Java SSLSocket / Port 5000)

Khi bảo vệ đồ án môn **Mạng máy tính / Lập trình Mạng**, các Thầy Cô thường rất ấn tượng nếu sinh viên chủ động mở **Wireshark** lên và bắt gói tin trực tiếp trên máy chiếu để chứng minh: **"Kênh truyền của ứng dụng em hoàn toàn được mã hóa, tin tặc nghe lén không thể đọc được nội dung!"**.

Tài liệu này hướng dẫn chi tiết từng bước cách thao tác với Wireshark để ghi điểm tuyệt đối.

---

## 1. Chuẩn bị Môi trường

1. Cài đặt phần mềm **Wireshark** (tải miễn phí tại: `https://www.wireshark.org/`).
2. Nếu chạy Server và Client trên cùng một máy tính (`localhost` / `127.0.0.1`):
   - Trong Wireshark, chọn giao tiếp mạng là: **`Adapter for loopback traffic capture`** (hoặc Npcap Loopback Adapter).
3. Nếu chạy Server và Client trên 2 máy khác nhau trong mạng LAN Wi-Fi:
   - Chọn card mạng **`Wi-Fi`** hoặc **`Ethernet`** tương ứng.

---

## 2. Các Bước Thực Hiện Bắt Gói Tin

### Bước 1: Thiết lập Bộ lọc (Display Filter) trong Wireshark
Ở thanh tìm kiếm trên cùng của Wireshark, nhập bộ lọc sau và nhấn Enter:
```text
tcp.port == 5000
```
*Bộ lọc này giúp ẩn toàn bộ các gói tin web, youtube, hệ thống khác, chỉ giữ lại các gói tin trao đổi giữa Zalo Client và Zalo Server.*

### Bước 2: Bắt đầu Ghi (Start Capture)
- Bấm vào nút **Vây cá mập xanh (Shark Fin)** ở góc trên bên trái để bắt đầu bắt gói tin.

### Bước 3: Khởi động Server & Client và Thực hiện Hành động
1. Bật Server: Chạy file `run_server.bat`.
2. Mở Client: Chạy `run_client.bat` hoặc file `.exe`.
3. Đăng nhập tài khoản `an` (mật khẩu `123456`).
4. Gõ một câu chat: `"Thầy ơi đây là tin nhắn bí mật của em"`.
5. Bấm **"Gửi"**.

### Bước 4: Dừng Ghi (Stop Capture)
- Bấm vào nút **Hình vuông màu đỏ** ở thanh công cụ Wireshark để dừng bắt gói tin.

---

## 3. Cách Thuyết Minh và Phân Tích Gói Tin Trước Hội Đồng Chấm Thi

Khi nhìn vào danh sách gói tin trong Wireshark, bạn chỉ tay vào màn hình và thuyết minh theo 3 giai đoạn:

```text
GIAI ĐOẠN 1: Bắt tay 3 bước TCP (Transport Layer)
GIAI ĐOẠN 2: Bắt tay mật mã TLS 1.3 (Security Handshake)
GIAI ĐOẠN 3: Trao đổi dữ liệu mã hóa (Application Data)
```

### 📌 Điểm 1: Chỉ cho Thầy Cô thấy "Bắt tay 3 bước TCP"
- Tìm 3 dòng đầu tiên có cờ (Flags):
  1. `[SYN]` từ Client ➔ Server
  2. `[SYN, ACK]` từ Server ➔ Client
  3. `[ACK]` từ Client ➔ Server
- **Lời thuyết minh**: *"Thưa thầy cô, đây là quá trình 3-way handshake chuẩn của giao thức TCP ở tầng Transport để mở kênh truyền vật lý trước khi ứng dụng bắt đầu nói chuyện."*

### 📌 Điểm 2: Chỉ cho Thầy Cô thấy "TLS 1.3 Handshake"
- Tìm các dòng có cột Protocol là `TLSv1.3`:
  1. `Client Hello`: Client gửi danh sách mã hóa hỗ trợ.
  2. `Server Hello, Change Cipher Spec, Encrypted Extensions, Certificate`: Server phản hồi và gửi chứng chỉ số X.509 (`server.crt`) cho Client kiểm tra.
- **Lời thuyết minh**: *"Sau khi có kết nối TCP, `SSLSocket` của Java lập tức thực hiện TLS Handshake để đàm phán bộ khóa phiên đối xứng bí mật (Session Key). Toàn bộ danh tính Server được xác thực bằng chứng chỉ số X.509."*

### 📌 Điểm 3: Điểm mấu chốt - Chứng minh dữ liệu được MÃ HÓA 100%
- Tìm các dòng tiếp theo có nội dung Info là **`Application Data`**.
- Nhấp đúp chuột vào gói tin đó ➔ Nhìn vào khung hiển thị Hex/ASCII ở dưới cùng.
- **Hiện tượng thực tế**:
  - Bạn sẽ thấy toàn bộ dữ liệu là các ký tự vô nghĩa, lộn xộn (ví dụ: `7a 3f b1 8c e4 99 ...`).
  - Hoàn toàn **KHÔNG TÌM THẤY** từ khóa `"123456"` (mật khẩu) hay câu chat `"Thầy ơi đây là tin nhắn bí mật của em"`.
- **Lời thuyết minh**: *"Thưa Thầy Cô, ngay cả khi kẻ tấn công đứng cùng mạng Wi-Fi và dùng Wireshark bắt trọn toàn bộ gói tin thì cũng chỉ thu được các khối dữ liệu nhị phân đã được mã hóa bằng AES-256-GCM. Kẻ xấu hoàn toàn không thể đọc trộm hay sửa đổi nội dung tin nhắn của người dùng!"*

---

## 4. Bảng So Sánh Bắt Gói Tin: Socket Thường vs Zalo Secure TCP Chat

| Chỉ tiêu | Socket TCP Thông Thường (Không có TLS) | Zalo Secure TCP Chat (Slide Chương 16.1) |
|:---|:---|:---|
| **Protocol trên Wireshark** | Chỉ hiện `TCP` thuần túy | Hiện `TLSv1.3` và `Application Data` |
| **Mật khẩu đăng nhập** | Thấy rõ dạng văn bản: `{"password":"123456"}` | **Bị mã hóa hoàn toàn**, không thể xem |
| **Nội dung tin nhắn** | Thấy rõ từng chữ chat | **Bị mã hóa hoàn toàn** thành byte ngẫu nhiên |
| **Chống tấn công MITM** | Không có (kẻ xấu có thể sửa tin nhắn) | **Có chữ ký số X.509**, phát hiện giả mạo ngay lập tức |

> 💡 **Mẹo ăn điểm tối đa**: Hãy chụp sẵn một ảnh chụp màn hình Wireshark có gói tin `Application Data` dán vào Slide thuyết trình hoặc tài liệu báo cáo của bạn!

