# Changelog

All notable changes to the **Zalo Secure TCP Chat** project will be documented in this file.

## [1.3.0] - 2026-11-22: FTP Server (RFC 959) & Rich Media / Emoji Picker
### Added
- **Chương 14/15 — FTP Server (`MiniFtpServer`, cổng `2121`)**:
  - Hỗ trợ đầy đủ giao thức FTP Passive Mode (`USER`, `PASS`, `PWD`, `CWD`, `CDUP`, `TYPE`, `PASV`, `LIST`, `NLST`, `RETR`, `STOR`, `DELE`, `QUIT`), xác thực qua `UserStore` và bảo vệ Path Traversal trong `data/ftp_files/`.
  - Giao diện `FtpClientDialog` tích hợp trên Sidebar & Quick Toolbar của `MainChatFrame` cho phép upload/download/xóa tệp dung lượng lớn (video, âm thanh, tài liệu) qua FTP.
- **Rich Media & Emoji Picker (`MainChatFrame` & `ChatBubblePanel`)**:
  - Bảng chọn Emoji/Biểu tượng cảm xúc (32 icon) ngay trên thanh công cụ chat.
  - Nút gửi Video / Âm thanh (`🎬`), hiển thị thumbnail xem trước cho ảnh và cho phép mở trực tiếp tệp đính kèm bằng ứng dụng hệ thống hoặc lưu về máy.
- **Tests**: Bổ sung `TC13` trong `SecurityAndFunctionalTests` kiểm thử `MiniFtpServer` (`STOR`, `NLST`, `RETR`, `DELE`).

## [1.2.0] - 2026-11-22: Full Slide Coverage (Chapters 1–16.1)
### Added
- **Chương 8 & 9 — UDP Datagram & Broadcast Discovery**:
  - `UdpDiscoveryServer` (cổng UDP `5001`) lắng nghe gói `"DISCOVER_ZALO_SERVER"` và trả về cổng TCP.
  - `UdpDiscoveryClient` và nút `"🔍 Tìm Server LAN (UDP Broadcast)"` trên `LoginFrame`.
- **Chương 10 — UDP Multicast**:
  - `MulticastBroadcaster` (Server) và `MulticastReceiverTask` (Client) qua nhóm `239.1.1.1:8000`, hiển thị thông báo toàn khoa trực tiếp trong `"chung"`.
- **Chương 12 & 13 — Mini HTTP Server & Web API**:
  - `MiniHttpServer` (cổng HTTP `8080`, `http://localhost:8080`) phục vụ trang HTML trạng thái và các endpoint JSON: `GET /api/status`, `GET /api/users`, `POST /api/announce`.
- **Tests**: Bổ sung `TC12` trong `SecurityAndFunctionalTests` kiểm thử đồng thời UDP Discovery, HTTP API và Multicast (**12/12 PASS**).

## [1.1.1] - 2026-11-22: Bug Fixes & Slide Alignment
### Fixed
- **`ProtocolFramer`**: Added `MAX_FILE_FRAME_SIZE` (8 MB) for `file_transfer` frames while keeping `MAX_FRAME_SIZE` (16 KB) DoS guard for normal messages, fixing silent drops on files/images > ~12 KB.
- **`MainChatFrame` & `ConversationListPanel`**:
  - Fixed duplicate `FILE_TRANSFER` bubble on sender side and incorrect target routing for 1-1 files.
  - Added client-side handling for `GROUP_CREATE` so newly created groups appear immediately in the conversation list.
  - Preserved `lastMessage`, `timestamp`, `unreadCount`, and `isPinned` across `STATUS_UPDATE`/`USER_LIST` refreshes and search filtering; implemented `"Ưu tiên"` / `"Khác"` tab filtering and pinned-first sorting.
  - Updated Right Info Drawer dynamically with actual conversation name, avatar, and shared image/file counts; added image extension filter and `Files.readAllBytes` for file uploads.
- **`GroupManager` & `MessageDispatcher`**: Automatically add authenticated users to `"chung"` (`ensureDefaultMembership`) and sync custom groups on login.
- **Tests**: Added `TC11` in `SecurityAndFunctionalTests` verifying >16 KB `FILE_TRANSFER` (32 KB) and `GROUP_CREATE` (11/11 PASS).

## [1.1.0] - 2026-11-07: Zalo PC Vietnam 100% Authentic Edition
### Added
- **100% Authentic Zalo PC Features**:
  - **Recall Message (`recall_message`)**: Right-click context menu on own bubble to recall message within session; updates both sender and recipient bubbles to `[Tin nhắn đã được thu hồi]`.
  - **Typing Indicator (`typing_indicator`)**: Real-time `"✍️ [Tên] đang soạn tin..."` under chat header with auto-fadeout.
  - **File & Image Transfer (`file_transfer`)**: File chooser allowing users to send files and photos (Base64 over TLS stream) with interactive download/open file cards.
  - **My Cloud (`my_cloud`)**: Dedicated personal storage conversation for saving notes, files, and links.
  - **Zalo Tabs ("Ưu tiên" / "Khác")**: Segmented conversation filtering.
  - **Collapsible Info Drawer (280px)**: Shortcut `Ctrl+I` or header `ℹ` button toggling right pane with avatar, shared media files, and group info.
  - **Dialogs**: `CreateGroupDialog` with multi-contact selection and `ProfileDialog` with account security details.
- **Automated Test Suite Expansion**:
  - Expanded `SecurityAndFunctionalTests` from 6 to 10 automated test scenarios (TC01-TC10), achieving **100% PASS**.

## [1.0.0] - 2026-11-07

### Added
- **TLS 1.3 Secure Transport Layer**:
  - `certificates/server.p12` PKCS12 Keystore and `server.crt` X.509 public certificate via `generate_certs.bat`.
  - `SSLContextFactory` configuring `SSLServerSocket` on port 5000 with TLS 1.2 / 1.3 protocol enforcement.
  - `TrustManagerHelper` configuring client-side trust store verification without OS CA pollution.
- **Protocol & Stream Framing Engine**:
  - `ChatMessage` POJO handling all message types.
  - `MessageType` enum (`LOGIN`, `LOGIN_RESULT`, `REGISTER`, `DIRECT_MESSAGE`, `GROUP_MESSAGE`, `GROUP_CREATE`, `USER_LIST`, `STATUS_UPDATE`, `SYSTEM`, `DISCONNECT`, `PING`).
  - `ProtocolFramer` enforcing 16KB max packet guard and line-delimiter (`\n`) JSON streaming.
- **Server Concurrency & Security**:
  - `SecurityHelper` providing PBKDF2WithHmacSHA256 password hashing with 16-byte random salt and constant-time string comparison.
  - `UserStore` persistent user storage in `data/users.json` with pre-seeded sample users (`an`, `binh`, `chi`).
  - `ClientSession` with per-client `ReentrantLock sendLock` preventing byte interleaving during broadcast.
  - `ClientHandler` executing in cached thread pool for non-blocking multi-client accept loop.
  - `SessionRegistry` managing thread-safe sessions via `ConcurrentHashMap` with real-time presence broadcast.
  - `GroupManager` managing group chat rooms and memberships.
  - `MessageDispatcher` routing 1-1 direct messages and group broadcasts.
- **Zalo Desktop UI Layer**:
  - `ZaloTheme` implementing Zalo's brand colors (`#0068FF`, `#005AE0`, `#EBF3FF`), avatar generators, and FlatLaf theme.
  - `LoginFrame` featuring instant demo account pickers, host/port config, and tabbed register/login.
  - `MainChatFrame` featuring 3-column desktop layout (Sidebar, Conversation list, Chat area).
  - `ConversationListPanel` with live search, online/offline green status dots, and group list.
  - `ChatBubblePanel` with rounded chat bubbles, avatars, timestamps, and multi-line word wrapping.
- **Testing & Tooling**:
  - `SecurityAndFunctionalTests` automating all 6 test scenarios (TC01-TC06) from Slide 35 (100% PASS).
  - Batch scripts: `build.bat`, `run_server.bat`, `run_client.bat`, `run_tests.bat`, `generate_certs.bat`.
  - Comprehensive documentation in `docs/01-analysis-spec.md`, `docs/02-implementation-plan.md`, and `README.md`.

