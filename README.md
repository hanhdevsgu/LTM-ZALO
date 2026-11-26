# ĐỒ ÁN MẠNG MÁY TÍNH & LẬP TRÌNH MẠNG: ZALO SECURE TCP CHAT
> **Ứng dụng Trò chuyện Bảo mật (Web + Desktop Chat App) phong cách Zalo**  
> Xây dựng bằng **Java 17+**, **SQLite (`data/zalo.db`)**, **TLS 1.3**, **UDP Discovery/Multicast**, **HTTP/HTTPS** & **FTP**.  
> 📥 **Tải Slide (ZIP):** [Nhấn vào đây để tải `slideltmt.zip` (288.86 MB)](https://github.com/hanhdevsgu/LTM-ZALO/releases/download/slides/slideltmt.zip)

---

## 📸 1. Điểm nổi bật của Đồ án

1. **Bảo mật kênh truyền cấp cao (TLS 1.3 & X.509 Certificate)**:
   - Toàn bộ lưu lượng gói tin (từ thông tin đăng nhập đến nội dung tin nhắn) đều được mã hóa bằng thuật toán đối xứng mạnh đàm phán trong quá trình TLS Handshake.
   - Server quản lý khóa bí mật (Private Key) trong kho khóa **PKCS12 Keystore (`server.p12`)**. Client chỉ nhận chứng chỉ công khai **(`server.crt`)** để tin cậy Server.
2. **Khóa ghi độc quyền (Thread-Safe SendLock)**:
   - Mỗi `ClientSession` sở hữu một khóa độc quyền `private final ReentrantLock sendLock`.
   - Ngăn chặn hoàn toàn lỗi đan xen byte (`Byte Interleaving`) khi nhiều luồng cùng Broadcast tin nhắn tới một Client.
3. **Mật khẩu băm an toàn (PBKDF2 / Salt)**:
   - Mật khẩu người dùng được băm kèm Salt ngẫu nhiên và lưu tại `data/users.json`. Tuyệt đối không lưu và không in mật khẩu dạng rõ ra màn hình hay file log.
4. **Giao diện hiện đại phong cách Zalo Desktop (FlatLaf Swing)**:
   - Bố cục 3 cột: Thanh Sidebar Zalo Blue `#0068FF`, Cột danh bạ & tìm kiếm có chấm tròn trạng thái trực tuyến (Online/Offline), Khung chat với bong bóng xanh/trắng, emoji bar và tự động cuộn.
   - Chạy độc lập trên mọi máy chỉ cần JDK tiêu chuẩn, không cần cài đặt thêm module JavaFX phức tạp.
5. **Bộ kiểm thử tự động 6 kịch bản (TC01 ➔ TC06 theo đúng Slide 35)**:
   - Tự động kiểm thử đăng nhập đúng, từ chối mật khẩu sai, gửi nhận tin 1-1, broadcast tin nhóm, chặn kết nối TCP không có TLS, và ngắt kết nối đột ngột mà server không bị crash. Đạt tỷ lệ **PASS 100% (6/6)**.

---

## 📂 2. Cấu trúc Thư mục Dự án

```text
SLIDE/
├── certificates/                       # Thư mục lưu trữ chứng chỉ số
│   ├── server.p12                      # Keystore PKCS12 (chỉ Server giữ)
│   └── server.crt                      # Public Certificate (phân phối cho Client)
├── data/                               # Cơ sở dữ liệu người dùng
│   └── users.json                      # Danh sách tài khoản và mật khẩu đã băm
├── docs/                               # Hồ sơ thiết kế & đặc tả đồ án
│   ├── 01-analysis-spec.md             # Đặc tả 33 chức năng nguyên tử, Use Case & ERD
│   └── 02-implementation-plan.md       # Lộ trình 4 Phase và Ma trận truy vết
├── lib/                                # Thư viện phụ thuộc chuẩn
│   ├── flatlaf-3.5.4.jar               # Giao diện phẳng phong cách Zalo Desktop
│   └── gson-2.10.1.jar                 # Bộ phân giải JSON Stream Frame
├── src/com/zalo/
│   ├── model/                          # [TẦNG DỮ LIỆU DÙNG CHUNG - SLIDE 6 & 10]
│   │   ├── ChatMessage.java            # Cấu trúc thông điệp chuẩn (JSON POJO)
│   │   ├── MessageType.java            # Enum phân loại thông điệp
│   │   ├── ProtocolFramer.java         # Bộ phân khung dòng byte (\n delimiter & max 16KB)
│   │   └── UserInfo.java               # Đối tượng danh bạ & trạng thái online
│   ├── server/                         # [TẦNG MÁY CHỦ BẢO MẬT - SLIDE 12, 14, 18, 20]
│   │   ├── ZaloChatServer.java         # Hàm main mở SSLServerSocket cổng 5000
│   │   ├── SSLContextFactory.java      # Nạp Keystore server.p12 và ép chuẩn TLS 1.3
│   │   ├── SecurityHelper.java         # Băm mật khẩu PBKDF2WithHmacSHA256 + Salt
│   │   ├── UserStore.java              # Quản lý tài khoản (An, Bình, Chi...)
│   │   ├── ClientSession.java          # Phiên làm việc chứa ReentrantLock sendLock
│   │   ├── ClientHandler.java          # Luồng phục vụ riêng biệt cho từng Client
│   │   ├── SessionRegistry.java        # Sổ phiên ConcurrentHashMap & Broadcast trạng thái
│   │   ├── GroupManager.java           # Quản lý phòng chat nhóm
│   │   └── MessageDispatcher.java      # Bộ định tuyến thông điệp an toàn
│   └── client/                         # [TẦNG CLIENT & GIAO DIỆN ZALO - SLIDE 30, 32, 34]
│       ├── ZaloClientMain.java         # Hàm main khởi chạy Client
│       ├── ZaloChatClient.java         # Bộ điều khiển mạng SSLSocket phía Client
│       ├── ReceiveTask.java            # Luồng nhận tin ngầm liên tục
│       ├── TrustManagerHelper.java     # Nạp chứng chỉ server.crt vào TrustStore
│       └── ui/
│           ├── ZaloTheme.java          # Bộ nhận diện màu sắc Zalo Blue, font chữ & avatar
│           ├── LoginFrame.java         # Màn hình đăng nhập / đăng ký
│           ├── MainChatFrame.java      # Cửa sổ chính 3 cột phong cách Zalo
│           ├── ConversationListPanel.java # Cột danh bạ & danh sách cuộc trò chuyện
│           └── ChatBubblePanel.java    # Khung bong bóng chat xanh/trắng bo tròn
├── tests/
│   └── SecurityAndFunctionalTests.java # Kiểm thử tự động 6 kịch bản (Slide 35)
├── build.bat                           # Script 1-click biên dịch toàn bộ dự án
├── run_server.bat                      # Script 1-click khởi động Server
├── run_client.bat                      # Script 1-click mở giao diện Client
├── run_tests.bat                       # Script 1-click chạy kiểm thử tự động
└── generate_certs.bat                  # Script tạo lại kho khóa & chứng chỉ TLS
```

---

## 🚀 3. Hướng dẫn Cài đặt & Chạy Thực tế (3 Bước)

### Bước 1: Biên dịch mã nguồn
Chạy file script:
```bash
build.bat
```
*(Hoặc gõ lệnh: `javac -encoding UTF-8 -cp "lib/*" -d bin src/com/zalo/model/*.java src/com/zalo/server/*.java src/com/zalo/client/*.java src/com/zalo/client/ui/*.java tests/*.java`)*

### Bước 2: Khởi động Server
Chạy file script:
```bash
run_server.bat
```
Server sẽ tự động nạp chứng chỉ `server.p12` và mở cổng lắng nghe tại `0.0.0.0:5000 (TLS 1.2 / TLS 1.3)`.

### Bước 3: Mở các cửa sổ Client để trò chuyện
Chạy file script:
```bash
run_client.bat
```
- Bạn có thể nhấp đúp vào `run_client.bat` **nhiều lần** để mở 2 đến 3 cửa sổ Zalo Client cùng lúc trên màn hình máy tính.
- Hệ thống đã tạo sẵn 3 tài khoản thử nghiệm từ Slide giáo trình:
  - Tài khoản 1: **An** (`an` / `123456`)
  - Tài khoản 2: **Bình** (`binh` / `123456`)
  - Tài khoản 3: **Chi** (`chi` / `123456`)
- Trên màn hình đăng nhập, bạn chỉ cần bấm nút chọn nhanh **"An"** hoặc **"Bình"** và bấm **"Đăng nhập"**.
- Trải nghiệm gửi tin nhắn 1-1 qua lại hoặc chat vào **"Nhóm Chung Toàn Khoa (SGU)"**. Chấm tròn trạng thái Online xanh lá sẽ sáng lên ngay lập tức!

---

## 🧪 4. Chạy Kiểm thử Tự động Hóa (10 Kịch bản Bảo mật & Tính năng Zalo PC)

Để kiểm chứng toàn diện các kịch bản an ninh mạng và tính năng chuẩn Zalo PC Việt Nam:
```bash
run_tests.bat
```

**Kết quả kiểm thử thực tế đạt 100%:**
```text
==================================================================
   BỘ KIỂM THỬ ZALO PC VIỆT NAM (TLS 1.3 / LẬP TRÌNH MẠNG)       
==================================================================
[TC01] Đăng nhập đúng (an / 123456): PASS (Đăng nhập thành công, nhận Client ID: 1)
[TC02] Sai mật khẩu (an / sai_mat_khau): PASS (Bị từ chối an toàn: Sai tên đăng nhập hoặc mật khẩu!)
[TC03] Nhắn tin riêng 1-1 (An -> Bình): PASS (Bình đã nhận tin từ An qua kênh TLS)
[TC04] Tin nhắn Nhóm Chung (Group Broadcast): PASS (Chi đã nhận tin nhắn Broadcast nhóm)
[TC05] Chặn kết nối TCP không TLS: PASS (Server từ chối bằng TLS Fatal Alert 21 và ngắt an toàn)
[TC06] Client ngắt kết nối đột ngột: PASS (Server thu hồi tài nguyên sạch sẽ, không crash)
[TC07] Zalo Thu hồi tin nhắn (Recall Message): PASS (Bình đã nhận tín hiệu thu hồi tin nhắn)
[TC08] Zalo Đang soạn tin nhắn (Typing Indicator): PASS (Bình đã nhận thông báo An đang soạn tin...)
[TC09] Zalo Truyền tệp tin / Hình ảnh (File Transfer): PASS (Bình đã nhận tệp tin tailieu.pdf an toàn qua TLS)
[TC10] Zalo Cloud của tôi (My Cloud): PASS (Lưu trữ và đồng bộ Cloud của tôi thành công)

------------------------------------------------------------------
TỔNG KẾT KIỂM THỬ: 10/10 KỊCH BẢN ĐẠT (PASS 100%)
------------------------------------------------------------------
```

---

## 🎓 5. Bảng Đối Chiếu Khớp 100% Slide Bài Giảng Chuong16.1

| Slide | Nội dung kỹ thuật trong Slide bài giảng | Tệp mã nguồn hiện thực trong Project |
|:---|:---|:---|
| **Slide 6** | Tách thư mục Server - Client - Model - Chứng chỉ | Gói `com.zalo.server`, `com.zalo.client`, `com.zalo.model`, `certificates/` |
| **Slide 7-8** | Tạo chứng chỉ TLS và Keystore PKCS12 bằng `keytool` | `generate_certs.bat` (`server.p12`, `server.crt`) |
| **Slide 10** | Giao thức thông điệp JSON mỗi dòng kết thúc bằng `\n` | [ProtocolFramer.java](file:///c:/Users/Lenovo/Desktop/tong%20hop/ltm/SLIDE/src/com/zalo/model/ProtocolFramer.java), [MessageType.java](file:///c:/Users/Lenovo/Desktop/tong%20hop/ltm/SLIDE/src/com/zalo/model/MessageType.java) |
| **Slide 12** | Khởi tạo `SSLContext` và `SSLServerSocket` (Java) | [SSLContextFactory.java](file:///c:/Users/Lenovo/Desktop/tong%20hop/ltm/SLIDE/src/com/zalo/server/SSLContextFactory.java), [ZaloChatServer.java](file:///c:/Users/Lenovo/Desktop/tong%20hop/ltm/SLIDE/src/com/zalo/server/ZaloChatServer.java) |
| **Slide 14** | Vòng lặp Accept Loop và ThreadPool phục vụ nhiều Client | [ClientHandler.java](file:///c:/Users/Lenovo/Desktop/tong%20hop/ltm/SLIDE/src/com/zalo/server/ClientHandler.java) |
| **Slide 18** | Đàm phán TLS Handshake và ép giao thức TLS 1.2 / TLS 1.3 | [SSLContextFactory.java](file:///c:/Users/Lenovo/Desktop/tong%20hop/ltm/SLIDE/src/com/zalo/server/SSLContextFactory.java) |
| **Slide 20** | `ConcurrentHashMap` & Khóa ghi độc quyền `ReentrantLock sendLock` | [ClientSession.java](file:///c:/Users/Lenovo/Desktop/tong%20hop/ltm/SLIDE/src/com/zalo/server/ClientSession.java), [SessionRegistry.java](file:///c:/Users/Lenovo/Desktop/tong%20hop/ltm/SLIDE/src/com/zalo/server/SessionRegistry.java) |
| **Slide 22** | Xác thực người dùng bằng Mật khẩu băm (PBKDF2/BCrypt) | [SecurityHelper.java](file:///c:/Users/Lenovo/Desktop/tong%20hop/ltm/SLIDE/src/com/zalo/server/SecurityHelper.java), [UserStore.java](file:///c:/Users/Lenovo/Desktop/tong%20hop/ltm/SLIDE/src/com/zalo/server/UserStore.java) |
| **Slide 24** | Đọc dữ liệu dòng byte bất đồng bộ và kiểm tra tính toàn vẹn | [ProtocolFramer.java](file:///c:/Users/Lenovo/Desktop/tong%20hop/ltm/SLIDE/src/com/zalo/model/ProtocolFramer.java), [ClientHandler.java](file:///c:/Users/Lenovo/Desktop/tong%20hop/ltm/SLIDE/src/com/zalo/server/ClientHandler.java) |
| **Slide 26** | Broadcast tin nhắn an toàn đa luồng không đan xen byte | [MessageDispatcher.java](file:///c:/Users/Lenovo/Desktop/tong%20hop/ltm/SLIDE/src/com/zalo/server/MessageDispatcher.java) |
| **Slide 28** | Xử lý Client ngắt kết nối an toàn trong khối `finally` | [ClientHandler.java](file:///c:/Users/Lenovo/Desktop/tong%20hop/ltm/SLIDE/src/com/zalo/server/ClientHandler.java), [SessionRegistry.java](file:///c:/Users/Lenovo/Desktop/tong%20hop/ltm/SLIDE/src/com/zalo/server/SessionRegistry.java) |
| **Slide 30-32**| Phía Client tạo `SSLSocket`, bắt tay TLS và kiểm tra chứng chỉ | [ZaloChatClient.java](file:///c:/Users/Lenovo/Desktop/tong%20hop/ltm/SLIDE/src/com/zalo/client/ZaloChatClient.java), [TrustManagerHelper.java](file:///c:/Users/Lenovo/Desktop/tong%20hop/ltm/SLIDE/src/com/zalo/client/TrustManagerHelper.java) |
| **Slide 34** | Luồng nhận tin nhắn chạy ngầm song song giao diện chính | [ReceiveTask.java](file:///c:/Users/Lenovo/Desktop/tong%20hop/ltm/SLIDE/src/com/zalo/client/ReceiveTask.java), [MainChatFrame.java](file:///c:/Users/Lenovo/Desktop/tong%20hop/ltm/SLIDE/src/com/zalo/client/ui/MainChatFrame.java) |
| **Slide 35** | 6 kịch bản kiểm thử bảo mật & chức năng thực tế | [SecurityAndFunctionalTests.java](file:///c:/Users/Lenovo/Desktop/tong%20hop/ltm/SLIDE/tests/SecurityAndFunctionalTests.java) |

