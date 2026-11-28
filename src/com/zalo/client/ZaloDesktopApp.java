package com.zalo.client;

import com.formdev.flatlaf.FlatLightLaf;
import com.google.gson.*;
import com.zalo.model.ChatMessage;

import javax.imageio.ImageIO;
import javax.sound.sampled.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Ứng dụng Java Desktop (Swing + FlatLaf) — giao diện chuẩn Zalo PC 3.5-Pane giống hệt bản Web.
 * Đầy đủ 11 mục Excel: P2P, Group, File, Emoji, History, Image, Video, Sound, State, Time, Sender.
 */
public class ZaloDesktopApp {
    private static final Color ZALO_BLUE = new Color(0x0068FF);
    private static final Color ZALO_DARK = new Color(0x0054CC);
    private static final Color BG_CHAT = new Color(0xEBEEF3);
    private static final Color BG_ACTIVE = new Color(0xE5EFFC);
    private static final Color BG_ME = new Color(0xE5EFFC);
    private static final Color BORDER_COL = new Color(0xE2E8F0);
    private static final Color MUTED = new Color(0x64748B);
    private static final Color TEXT_MAIN = new Color(0x0F172A);
    private static final Color[] AV_COLORS = {
            new Color(0x3B82F6), new Color(0x10B981), new Color(0xF59E0B),
            new Color(0xEF4444), new Color(0x8B5CF6), new Color(0xEC4899)
    };
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final Gson gson = new Gson();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ExecutorService ioExec = Executors.newCachedThreadPool();
    private final Map<String, ImageIcon> thumbCache = new ConcurrentHashMap<>();

    private String serverBase = System.getenv("ZALO_SERVER") != null && !System.getenv("ZALO_SERVER").isBlank()
            ? System.getenv("ZALO_SERVER").trim()
            : "http://26.3.15.18:8080";
    private String token = null;
    private String me = "";
    private String myName = "";

    private String curId = "chung";
    private String curName = "Nhóm Chung Toàn Khoa (SGU)";
    private boolean curIsGroup = true;
    private String convFilter = "all"; // all | group | unread
    private boolean drawerOpen = true;

    private final List<JsonObject> contacts = new ArrayList<>();
    private final List<JsonObject> groups = new ArrayList<>();
    private final Map<String, List<ChatMessage>> history = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Integer>> reactions = new ConcurrentHashMap<>();
    private final Map<String, Integer> unreadMap = new ConcurrentHashMap<>();

    private JFrame mainFrame;
    private JPanel convListPanel;
    private JPanel msgListPanel;
    private JPanel rightDrawer;
    private JScrollPane msgScroll;
    private JTextField txtSearch;
    private JLabel lblChatTitle, lblChatSub, lblTypingHead, lblTypingBar;
    private AvatarCircle lblHeaderAv, lblDrawerAv;
    private JLabel lblDrawerName, lblDrawerSub;
    private JPanel drawerMediaBox, drawerFileBox;
    private JTextField txtMsg;
    private JButton btnMainSend, btnVoice;
    private JButton tabAll, tabGroup, tabUnread;
    private javax.swing.Timer pollTimer, typingClearTimer;
    private long lastTypingSentMs = 0;
    private int lastBadgeUnread = -1;

    private volatile boolean recording = false;
    private TargetDataLine micLine;
    private ByteArrayOutputStream wavBuffer;

    public static void main(String[] args) {
        try {
            FlatLightLaf.setup();
            UIManager.put("Button.arc", 10);
            UIManager.put("TextComponent.arc", 16);
            UIManager.put("ScrollBar.thumbArc", 999);
            UIManager.put("ScrollBar.width", 6);
        } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new ZaloDesktopApp(args != null && args.length > 0 ? args[0] : null).showLoginWindow());
    }

    public ZaloDesktopApp() {}
    public ZaloDesktopApp(String cliServer) {
        if (cliServer != null && !cliServer.isBlank()) this.serverBase = cliServer.trim().replaceAll("/+$", "");
    }

    // ==================== 1. ĐĂNG NHẬP / ĐĂNG KÝ (2 TAB GIỐNG WEB) ====================
    private void showLoginWindow() {
        JFrame loginFrame = new JFrame("Zalo — Đăng nhập");
        loginFrame.setIconImages(createZaloIcons(0));
        loginFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        loginFrame.setSize(440, 520);
        loginFrame.setLocationRelativeTo(null);
        loginFrame.setResizable(false);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(new Color(0xE8F1FF));
        root.setBorder(new EmptyBorder(28, 34, 20, 34));

        JPanel head = new JPanel();
        head.setOpaque(false);
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        JLabel logo = new JLabel("Zalo");
        logo.setFont(new Font("Segoe UI", Font.BOLD, 42));
        logo.setForeground(ZALO_BLUE);
        logo.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel sub = new JLabel("Đăng nhập tài khoản Zalo để kết nối với ứng dụng Zalo");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        sub.setForeground(MUTED);
        sub.setAlignmentX(Component.CENTER_ALIGNMENT);
        head.add(logo);
        head.add(Box.createVerticalStrut(4));
        head.add(sub);
        head.add(Box.createVerticalStrut(16));
        root.add(head, BorderLayout.NORTH);

        RoundedPanel card = new RoundedPanel(14, Color.WHITE, BORDER_COL);
        card.setBorder(new EmptyBorder(14, 24, 22, 24));
        card.setLayout(new BorderLayout(0, 12));

        // Thanh 2 tab: Đăng nhập | Đăng ký
        JPanel tabs = new JPanel(new GridLayout(1, 2));
        tabs.setOpaque(false);
        JButton tabIn = new JButton("Đăng nhập");
        JButton tabUp = new JButton("Đăng ký");
        tabIn.setFocusPainted(false); tabUp.setFocusPainted(false);
        tabIn.setContentAreaFilled(false); tabUp.setContentAreaFilled(false);
        tabIn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        tabUp.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        tabIn.setForeground(ZALO_BLUE);
        tabUp.setForeground(MUTED);
        tabIn.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, ZALO_BLUE));
        tabUp.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COL));
        tabs.add(tabIn);
        tabs.add(tabUp);
        card.add(tabs, BorderLayout.NORTH);

        CardLayout cl = new CardLayout();
        JPanel formStack = new JPanel(cl);
        formStack.setOpaque(false);

        // Panel Đăng nhập
        JPanel pLogin = new JPanel();
        pLogin.setOpaque(false);
        pLogin.setLayout(new BoxLayout(pLogin, BoxLayout.Y_AXIS));
        JTextField txtUser = new JTextField();
        txtUser.putClientProperty("JTextField.placeholderText", "Nhập số điện thoại hoặc tên đăng nhập");
        JPasswordField txtPass = new JPasswordField();
        txtPass.putClientProperty("JTextField.placeholderText", "Nhập mật khẩu");
        JLabel lblLoginErr = new JLabel(" ");
        lblLoginErr.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblLoginErr.setForeground(new Color(0xDC2626));
        JButton btnLogin = new JButton("Đăng nhập với mật khẩu");
        btnLogin.setBackground(ZALO_BLUE);
        btnLogin.setForeground(Color.WHITE);
        btnLogin.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnLogin.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        btnLogin.setAlignmentX(Component.LEFT_ALIGNMENT);

        addField(pLogin, "Số điện thoại / Tên đăng nhập", txtUser);
        addField(pLogin, "Mật khẩu", txtPass);
        pLogin.add(lblLoginErr);
        pLogin.add(Box.createVerticalStrut(10));
        pLogin.add(btnLogin);

        // Panel Đăng ký
        JPanel pReg = new JPanel();
        pReg.setOpaque(false);
        pReg.setLayout(new BoxLayout(pReg, BoxLayout.Y_AXIS));
        JTextField txtRegName = new JTextField();
        txtRegName.putClientProperty("JTextField.placeholderText", "Ví dụ: Nguyễn Văn Nam");
        JTextField txtRegUser = new JTextField();
        txtRegUser.putClientProperty("JTextField.placeholderText", "Ví dụ: namnguyen hoặc 0901234567");
        JPasswordField txtRegPass = new JPasswordField();
        txtRegPass.putClientProperty("JTextField.placeholderText", "Nhập mật khẩu");
        JPasswordField txtRegPass2 = new JPasswordField();
        txtRegPass2.putClientProperty("JTextField.placeholderText", "Nhập lại mật khẩu");
        JLabel lblRegMsg = new JLabel(" ");
        lblRegMsg.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        JButton btnRegister = new JButton("Đăng ký tài khoản");
        btnRegister.setBackground(ZALO_BLUE);
        btnRegister.setForeground(Color.WHITE);
        btnRegister.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnRegister.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        btnRegister.setAlignmentX(Component.LEFT_ALIGNMENT);

        addField(pReg, "Họ và tên hiển thị", txtRegName);
        addField(pReg, "Số điện thoại / Tên đăng nhập", txtRegUser);
        addField(pReg, "Mật khẩu", txtRegPass);
        addField(pReg, "Xác nhận mật khẩu", txtRegPass2);
        pReg.add(lblRegMsg);
        pReg.add(Box.createVerticalStrut(6));
        pReg.add(btnRegister);

        formStack.add(pLogin, "in");
        formStack.add(pReg, "up");
        card.add(formStack, BorderLayout.CENTER);
        root.add(card, BorderLayout.CENTER);

        // Footer nhỏ chỉnh Server URL nếu cần
        JButton btnSrv = new JButton("Server: " + serverBase + " (Đổi)");
        btnSrv.setContentAreaFilled(false);
        btnSrv.setBorderPainted(false);
        btnSrv.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnSrv.setForeground(MUTED);
        btnSrv.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnSrv.addActionListener(e -> {
            String next = JOptionPane.showInputDialog(loginFrame, "Nhập địa chỉ Server:", serverBase);
            if (next != null && !next.trim().isEmpty()) {
                serverBase = next.trim().replaceAll("/+$", "");
                btnSrv.setText("Server: " + serverBase + " (Đổi)");
            }
        });
        root.add(btnSrv, BorderLayout.SOUTH);

        tabIn.addActionListener(e -> {
            cl.show(formStack, "in");
            tabIn.setForeground(ZALO_BLUE); tabIn.setFont(new Font("Segoe UI", Font.BOLD, 14));
            tabIn.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, ZALO_BLUE));
            tabUp.setForeground(MUTED); tabUp.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            tabUp.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COL));
            loginFrame.getRootPane().setDefaultButton(btnLogin);
        });
        tabUp.addActionListener(e -> {
            cl.show(formStack, "up");
            tabUp.setForeground(ZALO_BLUE); tabUp.setFont(new Font("Segoe UI", Font.BOLD, 14));
            tabUp.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, ZALO_BLUE));
            tabIn.setForeground(MUTED); tabIn.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            tabIn.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COL));
            loginFrame.getRootPane().setDefaultButton(btnRegister);
        });

        loginFrame.setContentPane(root);
        loginFrame.getRootPane().setDefaultButton(btnLogin);

        btnLogin.addActionListener(e -> {
            String u = txtUser.getText().trim();
            String p = new String(txtPass.getPassword()).trim();
            if (u.isEmpty() || p.isEmpty()) { lblLoginErr.setText("Vui lòng nhập tài khoản và mật khẩu!"); return; }
            btnLogin.setEnabled(false);
            lblLoginErr.setForeground(MUTED);
            lblLoginErr.setText("Đang đăng nhập...");
            ioExec.submit(() -> {
                try {
                    JsonObject body = new JsonObject();
                    body.addProperty("username", u);
                    body.addProperty("password", p);
                    JsonObject res = postJson("/api/login", body);
                    SwingUtilities.invokeLater(() -> {
                        btnLogin.setEnabled(true);
                        if (res != null && res.has("success") && res.get("success").getAsBoolean()) {
                            loginFrame.dispose();
                            applySessionPayload(res);
                            showMainWindow();
                        } else {
                            lblLoginErr.setForeground(new Color(0xDC2626));
                            lblLoginErr.setText(res != null && res.has("message") ? res.get("message").getAsString() : "Sai tài khoản hoặc mật khẩu!");
                        }
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        btnLogin.setEnabled(true);
                        lblLoginErr.setForeground(new Color(0xDC2626));
                        lblLoginErr.setText("Không kết nối được Server: " + ex.getMessage());
                    });
                }
            });
        });

        btnRegister.addActionListener(e -> {
            String dn = txtRegName.getText().trim();
            String u = txtRegUser.getText().trim();
            String p = new String(txtRegPass.getPassword()).trim();
            String p2 = new String(txtRegPass2.getPassword()).trim();
            if (u.isEmpty() || p.isEmpty()) { lblRegMsg.setForeground(new Color(0xDC2626)); lblRegMsg.setText("Vui lòng nhập đủ thông tin!"); return; }
            if (!p.equals(p2)) { lblRegMsg.setForeground(new Color(0xDC2626)); lblRegMsg.setText("Mật khẩu xác nhận không khớp!"); return; }
            ioExec.submit(() -> {
                try {
                    JsonObject body = new JsonObject();
                    body.addProperty("username", u);
                    body.addProperty("password", p);
                    body.addProperty("displayName", dn.isEmpty() ? u : dn);
                    JsonObject res = postJson("/api/register", body);
                    SwingUtilities.invokeLater(() -> {
                        boolean ok = res != null && res.has("success") && res.get("success").getAsBoolean();
                        lblRegMsg.setForeground(ok ? new Color(0x16A34A) : new Color(0xDC2626));
                        lblRegMsg.setText(res != null && res.has("message") ? res.get("message").getAsString() : "Lỗi đăng ký");
                        if (ok) {
                            txtUser.setText(u);
                            txtPass.setText(p);
                            tabIn.doClick();
                            lblLoginErr.setForeground(new Color(0x16A34A));
                            lblLoginErr.setText("Đăng ký thành công! Bấm Đăng nhập để vào Zalo.");
                        }
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> lblRegMsg.setText("Lỗi kết nối: " + ex.getMessage()));
                }
            });
        });

        loginFrame.setVisible(true);
    }

    private void addField(JPanel parent, String label, JComponent field) {
        JLabel l = new JLabel(label);
        l.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        l.setForeground(TEXT_MAIN);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        parent.add(l);
        parent.add(Box.createVerticalStrut(3));
        parent.add(field);
        parent.add(Box.createVerticalStrut(9));
    }

    // ==================== 2. GIAO DIỆN ZALO PC 3.5-PANE ====================
    private void showMainWindow() {
        mainFrame = new JFrame("Zalo — " + myName + " (@" + me + ")");
        mainFrame.setIconImages(createZaloIcons(0));
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.setSize(1220, 760);
        mainFrame.setMinimumSize(new Dimension(920, 600));
        mainFrame.setLocationRelativeTo(null);
        mainFrame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                if (pollTimer != null) pollTimer.stop();
                ioExec.submit(() -> { try { postJson("/api/logout", new JsonObject()); } catch (Exception ignored) {} });
            }
        });

        JPanel root = new JPanel(new BorderLayout());

        // CỘT 1: Nav Rail (#0068FF)
        JPanel navRail = new JPanel();
        navRail.setBackground(ZALO_BLUE);
        navRail.setPreferredSize(new Dimension(64, 0));
        navRail.setLayout(new BoxLayout(navRail, BoxLayout.Y_AXIS));
        navRail.setBorder(new EmptyBorder(14, 10, 14, 10));

        AvatarCircle meAv = new AvatarCircle(44, ZALO_DARK, myName.substring(0, 1).toUpperCase(), false);
        meAv.setToolTipText(myName + " (@" + me + ")");
        meAv.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton navChat = railBtn("chat", "Tin nhắn", true);
        JButton navGrp = railBtn("group", "Tạo nhóm chat", false);
        JButton navCloud = railBtn("cloud", "Cloud của tôi", false);
        JButton navOut = railBtn("logout", "Đăng xuất", false);

        navChat.addActionListener(e -> selectConv("chung", "Nhóm Chung Toàn Khoa (SGU)", true));
        navGrp.addActionListener(e -> openCreateGroupDialog());
        navCloud.addActionListener(e -> selectConv("my_cloud", "Cloud của tôi", false));
        navOut.addActionListener(e -> {
            if (pollTimer != null) pollTimer.stop();
            ioExec.submit(() -> { try { postJson("/api/logout", new JsonObject()); } catch (Exception ignored) {} });
            mainFrame.dispose();
            showLoginWindow();
        });

        navRail.add(meAv);
        navRail.add(Box.createVerticalStrut(18));
        navRail.add(navChat);
        navRail.add(Box.createVerticalStrut(10));
        navRail.add(navGrp);
        navRail.add(Box.createVerticalStrut(10));
        navRail.add(navCloud);
        navRail.add(Box.createVerticalGlue());
        navRail.add(navOut);

        // CỘT 2: Danh sách hội thoại (Search + Tabs Tất cả/Nhóm/Chưa đọc + Items)
        JPanel leftPane = new JPanel(new BorderLayout());
        leftPane.setPreferredSize(new Dimension(320, 0));
        leftPane.setBackground(Color.WHITE);
        leftPane.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, BORDER_COL));

        JPanel leftTop = new JPanel();
        leftTop.setBackground(Color.WHITE);
        leftTop.setLayout(new BoxLayout(leftTop, BoxLayout.Y_AXIS));
        leftTop.setBorder(new EmptyBorder(12, 12, 0, 12));

        JPanel searchRow = new JPanel(new BorderLayout(6, 0));
        searchRow.setOpaque(false);
        searchRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        txtSearch = new JTextField();
        txtSearch.putClientProperty("JTextField.placeholderText", "Tìm kiếm");
        txtSearch.setBackground(new Color(0xF1F5F9));
        txtSearch.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { renderConvList(); }
            @Override public void removeUpdate(DocumentEvent e) { renderConvList(); }
            @Override public void changedUpdate(DocumentEvent e) {}
        });
        JButton btnCreateGrp = new JButton("+");
        btnCreateGrp.setToolTipText("Tạo nhóm chat");
        btnCreateGrp.setPreferredSize(new Dimension(34, 34));
        btnCreateGrp.setBackground(new Color(0xF1F5F9));
        btnCreateGrp.addActionListener(e -> openCreateGroupDialog());
        searchRow.add(txtSearch, BorderLayout.CENTER);
        searchRow.add(btnCreateGrp, BorderLayout.EAST);

        JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 6));
        filterRow.setOpaque(false);
        tabAll = filterTab("Tất cả", true);
        tabGroup = filterTab("Nhóm", false);
        tabUnread = filterTab("Chưa đọc", false);
        tabAll.addActionListener(e -> setFilter("all"));
        tabGroup.addActionListener(e -> setFilter("group"));
        tabUnread.addActionListener(e -> setFilter("unread"));
        filterRow.add(tabAll);
        filterRow.add(tabGroup);
        filterRow.add(tabUnread);

        leftTop.add(searchRow);
        leftTop.add(Box.createVerticalStrut(6));
        leftTop.add(filterRow);
        leftTop.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COL),
                new EmptyBorder(12, 12, 2, 12)
        ));

        convListPanel = new JPanel();
        convListPanel.setLayout(new BoxLayout(convListPanel, BoxLayout.Y_AXIS));
        convListPanel.setBackground(Color.WHITE);
        JScrollPane convScroll = new JScrollPane(convListPanel);
        convScroll.setBorder(null);
        convScroll.getVerticalScrollBar().setUnitIncrement(16);

        leftPane.add(leftTop, BorderLayout.NORTH);
        leftPane.add(convScroll, BorderLayout.CENTER);

        // CỘT 3: Khung Chat chính
        JPanel chatPane = new JPanel(new BorderLayout());
        chatPane.setBackground(BG_CHAT);

        JPanel chatHead = new JPanel(new BorderLayout());
        chatHead.setBackground(Color.WHITE);
        chatHead.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COL),
                new EmptyBorder(10, 16, 10, 16)
        ));

        JPanel headLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        headLeft.setOpaque(false);
        lblHeaderAv = new AvatarCircle(40, ZALO_BLUE, "N", false);
        JPanel titleStack = new JPanel();
        titleStack.setOpaque(false);
        titleStack.setLayout(new BoxLayout(titleStack, BoxLayout.Y_AXIS));

        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        titleRow.setOpaque(false);
        titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        lblChatTitle = new JLabel(curName);
        lblChatTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblChatTitle.setForeground(TEXT_MAIN);
        lblTypingHead = new JLabel("");
        lblTypingHead.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        lblTypingHead.setForeground(ZALO_BLUE);
        lblTypingHead.setBorder(new EmptyBorder(0, 8, 0, 0));
        titleRow.add(lblChatTitle);
        titleRow.add(lblTypingHead);

        lblChatSub = new JLabel("Nhóm trò chuyện");
        lblChatSub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblChatSub.setForeground(MUTED);
        lblChatSub.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleStack.add(titleRow);
        titleStack.add(Box.createVerticalStrut(2));
        titleStack.add(lblChatSub);

        headLeft.add(lblHeaderAv);
        headLeft.add(titleStack);

        JButton btnToggleDrawer = new JButton("◫");
        btnToggleDrawer.setToolTipText("Thông tin hội thoại");
        btnToggleDrawer.setBackground(new Color(0xF1F5F9));
        btnToggleDrawer.addActionListener(e -> {
            drawerOpen = !drawerOpen;
            rightDrawer.setVisible(drawerOpen);
        });
        chatHead.add(headLeft, BorderLayout.WEST);
        chatHead.add(btnToggleDrawer, BorderLayout.EAST);

        msgListPanel = new JPanel();
        msgListPanel.setLayout(new BoxLayout(msgListPanel, BoxLayout.Y_AXIS));
        msgListPanel.setBackground(BG_CHAT);
        msgListPanel.setBorder(new EmptyBorder(14, 18, 14, 18));
        msgScroll = new JScrollPane(msgListPanel);
        msgScroll.setBorder(null);
        msgScroll.getVerticalScrollBar().setUnitIncrement(18);

        // Composer (Toolbar + TypingBar + SendRow)
        JPanel composer = new JPanel(new BorderLayout());
        composer.setBackground(Color.WHITE);
        composer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COL));

        JPanel toolBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        toolBar.setBackground(Color.WHITE);
        toolBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xF1F5F9)));
        JButton btnEmoji = toolBtn("emoji", "Emoji");
        JButton btnImg = toolBtn("image", "Gửi hình ảnh");
        JButton btnFile = toolBtn("attach", "Gửi File / Thư mục");
        btnVoice = toolBtn("mic", "Ghi âm tin nhắn thoại");
        JButton btnShot = toolBtn("shot", "Chụp màn hình gửi nhanh");
        btnEmoji.addActionListener(e -> showEmojiMenu(btnEmoji));
        btnImg.addActionListener(e -> pickAndSendFile(true));
        btnFile.addActionListener(e -> showAttachMenu(btnFile));
        btnVoice.addActionListener(e -> toggleVoiceRecord());
        btnShot.addActionListener(e -> captureAndSendScreen());
        toolBar.add(btnEmoji);
        toolBar.add(btnImg);
        toolBar.add(btnFile);
        toolBar.add(btnVoice);
        toolBar.add(btnShot);

        JPanel inputArea = new JPanel();
        inputArea.setBackground(Color.WHITE);
        inputArea.setLayout(new BoxLayout(inputArea, BoxLayout.Y_AXIS));
        inputArea.setBorder(new EmptyBorder(6, 14, 10, 14));

        lblTypingBar = new JLabel("");
        lblTypingBar.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        lblTypingBar.setForeground(ZALO_BLUE);
        lblTypingBar.setVisible(false);
        lblTypingBar.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel sendRow = new JPanel(new BorderLayout(8, 0));
        sendRow.setOpaque(false);
        sendRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        txtMsg = new JTextField();
        txtMsg.putClientProperty("JTextField.placeholderText", "Nhập @, tin nhắn...");
        txtMsg.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        txtMsg.setBorder(new EmptyBorder(6, 6, 6, 6));
        txtMsg.addActionListener(e -> sendOrLike());
        txtMsg.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { onMsgInput(); }
            @Override public void removeUpdate(DocumentEvent e) { onMsgInput(); }
            @Override public void changedUpdate(DocumentEvent e) {}
        });

        btnMainSend = new JButton("👍");
        btnMainSend.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 16));
        btnMainSend.setBackground(Color.WHITE);
        btnMainSend.setForeground(ZALO_BLUE);
        btnMainSend.setFocusPainted(false);
        btnMainSend.setBorderPainted(false);
        btnMainSend.addActionListener(e -> sendOrLike());

        sendRow.add(txtMsg, BorderLayout.CENTER);
        sendRow.add(btnMainSend, BorderLayout.EAST);

        inputArea.add(lblTypingBar);
        inputArea.add(sendRow);

        composer.add(toolBar, BorderLayout.NORTH);
        composer.add(inputArea, BorderLayout.CENTER);

        chatPane.add(chatHead, BorderLayout.NORTH);
        chatPane.add(msgScroll, BorderLayout.CENTER);
        chatPane.add(composer, BorderLayout.SOUTH);

        // CỘT 4: Ngăn kéo Thông tin hội thoại
        rightDrawer = new JPanel();
        rightDrawer.setPreferredSize(new Dimension(280, 0));
        rightDrawer.setBackground(Color.WHITE);
        rightDrawer.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, BORDER_COL));
        rightDrawer.setLayout(new BoxLayout(rightDrawer, BoxLayout.Y_AXIS));

        JPanel drHeadWrap = new JPanel(new BorderLayout());
        drHeadWrap.setBackground(Color.WHITE);
        drHeadWrap.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COL));
        drHeadWrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
        JLabel drHead = new JLabel("Thông tin hội thoại", SwingConstants.CENTER);
        drHead.setFont(new Font("Segoe UI", Font.BOLD, 15));
        drHead.setBorder(new EmptyBorder(16, 12, 16, 12));
        drHeadWrap.add(drHead, BorderLayout.CENTER);

        JPanel drProfile = new JPanel();
        drProfile.setBackground(Color.WHITE);
        drProfile.setLayout(new BoxLayout(drProfile, BoxLayout.Y_AXIS));
        drProfile.setBorder(new EmptyBorder(18, 14, 16, 14));
        drProfile.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
        lblDrawerAv = new AvatarCircle(56, ZALO_BLUE, "N", false);
        lblDrawerAv.setAlignmentX(Component.CENTER_ALIGNMENT);
        lblDrawerName = new JLabel(curName);
        lblDrawerName.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lblDrawerName.setAlignmentX(Component.CENTER_ALIGNMENT);
        lblDrawerSub = new JLabel("");
        lblDrawerSub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblDrawerSub.setForeground(MUTED);
        lblDrawerSub.setAlignmentX(Component.CENTER_ALIGNMENT);
        drProfile.add(lblDrawerAv);
        drProfile.add(Box.createVerticalStrut(8));
        drProfile.add(lblDrawerName);
        drProfile.add(Box.createVerticalStrut(3));
        drProfile.add(lblDrawerSub);
        drProfile.add(Box.createVerticalStrut(12));

        JPanel drActions = new JPanel(new GridLayout(1, 3, 6, 0));
        drActions.setOpaque(false);
        drActions.setMaximumSize(new Dimension(250, 56));
        drActions.setAlignmentX(Component.CENTER_ALIGNMENT);
        drActions.add(drawerActionBtn("bell", "Tắt thông báo", () -> JOptionPane.showMessageDialog(mainFrame, "Đã bật/tắt thông báo cho " + curName)));
        drActions.add(drawerActionBtn("pin", "Ghim hội thoại", () -> JOptionPane.showMessageDialog(mainFrame, "Đã ghim hội thoại " + curName)));
        drActions.add(drawerActionBtn("group", "Tạo nhóm", this::openCreateGroupDialog));
        drProfile.add(drActions);

        drawerMediaBox = new JPanel();
        drawerMediaBox.setBackground(Color.WHITE);
        drawerMediaBox.setLayout(new BoxLayout(drawerMediaBox, BoxLayout.Y_AXIS));
        drawerMediaBox.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(6, 0, 0, 0, new Color(0xF1F5F9)),
                new EmptyBorder(12, 14, 12, 14)
        ));
        drawerMediaBox.setAlignmentX(Component.CENTER_ALIGNMENT);
        drawerMediaBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 210));

        drawerFileBox = new JPanel();
        drawerFileBox.setBackground(Color.WHITE);
        drawerFileBox.setLayout(new BoxLayout(drawerFileBox, BoxLayout.Y_AXIS));
        drawerFileBox.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(6, 0, 0, 0, new Color(0xF1F5F9)),
                new EmptyBorder(12, 14, 12, 14)
        ));
        drawerFileBox.setAlignmentX(Component.CENTER_ALIGNMENT);
        drawerFileBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 220));

        rightDrawer.add(drHeadWrap);
        rightDrawer.add(drProfile);
        rightDrawer.add(drawerMediaBox);
        rightDrawer.add(drawerFileBox);
        rightDrawer.add(Box.createVerticalGlue());

        JPanel centerArea = new JPanel(new BorderLayout());
        centerArea.add(leftPane, BorderLayout.WEST);
        centerArea.add(chatPane, BorderLayout.CENTER);
        centerArea.add(rightDrawer, BorderLayout.EAST);

        root.add(navRail, BorderLayout.WEST);
        root.add(centerArea, BorderLayout.CENTER);
        mainFrame.setContentPane(root);

        renderConvList();
        renderHeaderAndDrawer();
        renderMessages();

        pollTimer = new javax.swing.Timer(1200, e -> pollServer());
        pollTimer.start();

        mainFrame.setVisible(true);
    }

    private JButton railBtn(String iconKey, String tip, boolean active) {
        JButton b = new JButton(new VectorIcon(iconKey, 20, Color.WHITE));
        b.setToolTipText(tip);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setBackground(active ? ZALO_DARK : ZALO_BLUE);
        b.setMaximumSize(new Dimension(44, 42));
        b.setPreferredSize(new Dimension(44, 42));
        b.setAlignmentX(Component.CENTER_ALIGNMENT);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private JButton filterTab(String text, boolean active) {
        JButton b = new JButton(text);
        b.setFocusPainted(false);
        b.setContentAreaFilled(false);
        b.setFont(new Font("Segoe UI", active ? Font.BOLD : Font.PLAIN, 12));
        b.setForeground(active ? ZALO_BLUE : MUTED);
        b.setBorder(active ? BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 2, 0, ZALO_BLUE),
                new EmptyBorder(2, 2, 4, 2)) : new EmptyBorder(2, 2, 6, 2));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private void setFilter(String f) {
        convFilter = f;
        JButton[] tabs = {tabAll, tabGroup, tabUnread};
        String[] keys = {"all", "group", "unread"};
        for (int i = 0; i < tabs.length; i++) {
            boolean act = keys[i].equals(f);
            tabs[i].setForeground(act ? ZALO_BLUE : MUTED);
            tabs[i].setFont(new Font("Segoe UI", act ? Font.BOLD : Font.PLAIN, 12));
            tabs[i].setBorder(act ? BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 2, 0, ZALO_BLUE),
                    new EmptyBorder(2, 2, 4, 2)) : new EmptyBorder(2, 2, 6, 2));
        }
        renderConvList();
    }

    private JButton toolBtn(String iconKey, String tip) {
        JButton b = new JButton(new VectorIcon(iconKey, 18, new Color(0x475569)));
        b.setToolTipText(tip);
        b.setBackground(Color.WHITE);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setPreferredSize(new Dimension(34, 30));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private void onMsgInput() {
        String val = txtMsg.getText().trim();
        btnMainSend.setText(val.isEmpty() ? "👍" : "Gửi");
        btnMainSend.setFont(new Font(val.isEmpty() ? "Segoe UI Emoji" : "Segoe UI", val.isEmpty() ? Font.PLAIN : Font.BOLD, val.isEmpty() ? 16 : 13));
        if (!val.isEmpty() && token != null && !"my_cloud".equals(curId)) {
            long now = System.currentTimeMillis();
            if (now - lastTypingSentMs > 1000) {
                lastTypingSentMs = now;
                ioExec.submit(() -> {
                    try {
                        JsonObject tp = new JsonObject();
                        tp.addProperty("type", "typing_indicator");
                        if (curIsGroup) tp.addProperty("groupId", curId);
                        else tp.addProperty("toUser", curId);
                        postJson("/api/send", tp);
                    } catch (Exception ignored) {}
                });
            }
        }
    }

    private void sendOrLike() {
        if (txtMsg.getText().trim().isEmpty()) {
            txtMsg.setText("👍");
        }
        sendTextMessage();
    }

    // ==================== 3. ĐỒNG BỘ DỮ LIỆU & POLLING ====================
    private void applySessionPayload(JsonObject d) {
        token = d.get("token").getAsString();
        me = d.get("username").getAsString();
        myName = d.has("displayName") ? d.get("displayName").getAsString() : me;

        contacts.clear();
        if (d.has("users") && d.get("users").isJsonArray()) {
            for (JsonElement el : d.getAsJsonArray("users")) contacts.add(el.getAsJsonObject());
        }
        groups.clear();
        if (d.has("groups") && d.get("groups").isJsonArray()) {
            for (JsonElement el : d.getAsJsonArray("groups")) groups.add(el.getAsJsonObject());
        }
        history.clear();
        if (d.has("history") && d.get("history").isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : d.getAsJsonObject("history").entrySet()) {
                List<ChatMessage> list = Collections.synchronizedList(new ArrayList<>());
                for (JsonElement mEl : entry.getValue().getAsJsonArray()) list.add(gson.fromJson(mEl, ChatMessage.class));
                history.put(entry.getKey(), list);
            }
        }
        reactions.clear();
        if (d.has("reactions") && d.get("reactions").isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : d.getAsJsonObject("reactions").entrySet()) {
                Map<String, Integer> map = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> re : entry.getValue().getAsJsonObject().entrySet()) map.put(re.getKey(), re.getValue().getAsInt());
                reactions.put(entry.getKey(), map);
            }
        }
        unreadMap.clear();
        if (d.has("convStates") && d.get("convStates").isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : d.getAsJsonObject("convStates").entrySet()) {
                JsonObject st = entry.getValue().getAsJsonObject();
                if (st.has("unread")) unreadMap.put(entry.getKey(), st.get("unread").getAsInt());
            }
        }
    }

    private void pollServer() {
        if (token == null) return;
        ioExec.submit(() -> {
            try {
                JsonObject d = getJson("/api/poll?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8));
                if (d == null) return;
                SwingUtilities.invokeLater(() -> {
                    if (d.has("users") && d.get("users").isJsonArray()) {
                        contacts.clear();
                        for (JsonElement el : d.getAsJsonArray("users")) contacts.add(el.getAsJsonObject());
                    }
                    if (d.has("groups") && d.get("groups").isJsonArray()) {
                        groups.clear();
                        for (JsonElement el : d.getAsJsonArray("groups")) groups.add(el.getAsJsonObject());
                    }
                    if (d.has("reactions") && d.get("reactions").isJsonObject()) {
                        reactions.clear();
                        for (Map.Entry<String, JsonElement> entry : d.getAsJsonObject("reactions").entrySet()) {
                            Map<String, Integer> map = new LinkedHashMap<>();
                            for (Map.Entry<String, JsonElement> re : entry.getValue().getAsJsonObject().entrySet()) map.put(re.getKey(), re.getValue().getAsInt());
                            reactions.put(entry.getKey(), map);
                        }
                    }
                    if (d.has("convStates") && d.get("convStates").isJsonObject()) {
                        for (Map.Entry<String, JsonElement> entry : d.getAsJsonObject("convStates").entrySet()) {
                            String k = entry.getKey();
                            if (k.equalsIgnoreCase(curId)) unreadMap.put(k, 0);
                            else {
                                JsonObject st = entry.getValue().getAsJsonObject();
                                if (st.has("unread")) {
                                    int sv = st.get("unread").getAsInt();
                                    if (sv > unreadMap.getOrDefault(k, 0)) unreadMap.put(k, sv);
                                }
                            }
                        }
                    }
                    if (d.has("events") && d.get("events").isJsonArray()) {
                        for (JsonElement el : d.getAsJsonArray("events")) handleEvent(gson.fromJson(el, ChatMessage.class));
                    }
                    renderHeaderAndDrawer();
                    renderConvList();
                });
            } catch (Exception ignored) {}
        });
    }

    private void handleEvent(ChatMessage m) {
        if (m == null || m.getType() == null) return;
        String type = m.getType();
        if ("typing_indicator".equals(type)) {
            String fromU = m.getUsername() != null ? m.getUsername().toLowerCase() : "";
            String gid = m.getGroupId() != null ? m.getGroupId().toLowerCase() : "";
            if (!fromU.equals(me.toLowerCase()) && (fromU.equals(curId.toLowerCase()) || gid.equals(curId.toLowerCase()))) {
                String who = m.getDisplayName() != null ? m.getDisplayName() : m.getUsername();
                String txt = "✍️ " + who + " đang soạn tin...";
                lblTypingHead.setText(txt);
                lblTypingBar.setText(txt);
                lblTypingBar.setVisible(true);
                if (typingClearTimer != null) typingClearTimer.stop();
                typingClearTimer = new javax.swing.Timer(4000, ev -> {
                    lblTypingHead.setText("");
                    lblTypingBar.setVisible(false);
                });
                typingClearTimer.setRepeats(false);
                typingClearTimer.start();
            }
            return;
        }
        if ("message_status".equals(type) && "SEEN".equals(m.getStatus()) && m.getUsername() != null) {
            String peer = m.getUsername().toLowerCase();
            List<ChatMessage> arr = history.get(peer);
            if (arr != null) {
                for (ChatMessage x : arr) {
                    if (me.equalsIgnoreCase(x.getUsername())) x.setStatus("SEEN");
                }
                if (curId.equalsIgnoreCase(peer)) renderMessages();
            }
            return;
        }
        if ("recall_message".equals(type)) {
            for (List<ChatMessage> arr : history.values()) {
                for (ChatMessage x : arr) {
                    if (m.getMessageId() != null && m.getMessageId().equals(x.getMessageId())) {
                        x.setIsRecalled(true);
                        x.setContent("Tin nhắn đã được thu hồi");
                    }
                }
            }
            renderMessages();
            return;
        }
        if ("system".equals(type)) { pushMsg("chung", m); return; }
        if ("my_cloud".equals(type)) { pushMsg("my_cloud", m); return; }
        if ("group_message".equals(type)) { pushMsg(m.getGroupId() != null ? m.getGroupId() : "chung", m); return; }
        if ("direct_message".equals(type)) {
            String cid = me.equalsIgnoreCase(m.getUsername()) ? m.getToUser() : m.getUsername();
            if (me.equalsIgnoreCase(m.getUsername()) && "DELIVERED".equals(m.getStatus()) && m.getMessageId() != null) {
                List<ChatMessage> arr = history.get(cid);
                if (arr != null) {
                    for (ChatMessage x : arr) {
                        if (m.getMessageId().equals(x.getMessageId()) && !"SEEN".equals(x.getStatus())) {
                            x.setStatus("DELIVERED");
                            if (cid.equalsIgnoreCase(curId)) renderMessages();
                            return;
                        }
                    }
                }
            }
            pushMsg(cid, m);
            return;
        }
        if ("file_transfer".equals(type)) {
            String cid = m.getGroupId() != null ? m.getGroupId() : (me.equalsIgnoreCase(m.getUsername()) ? m.getToUser() : m.getUsername());
            pushMsg(cid, m);
        }
    }

    private void pushMsg(String cid, ChatMessage m) {
        if (cid == null) return;
        List<ChatMessage> arr = history.computeIfAbsent(cid, k -> Collections.synchronizedList(new ArrayList<>()));
        if (m.getMessageId() != null) {
            for (ChatMessage x : arr) {
                if (m.getMessageId().equals(x.getMessageId())) return;
            }
        }
        arr.add(m);
        if (m.getUsername() != null && !me.equalsIgnoreCase(m.getUsername()) && !cid.equalsIgnoreCase(curId)) {
            unreadMap.put(cid, unreadMap.getOrDefault(cid, 0) + 1);
        }
        if (cid.equalsIgnoreCase(curId)) {
            unreadMap.put(cid, 0);
            renderMessages();
            renderHeaderAndDrawer();
            if (m.getUsername() != null && !me.equalsIgnoreCase(m.getUsername()) && !curIsGroup && !"my_cloud".equals(cid)) {
                markReadAsync(cid);
            }
        }
        renderConvList();
    }

    private void selectConv(String id, String name, boolean isGrp) {
        curId = id;
        curName = name;
        curIsGroup = isGrp;
        unreadMap.put(id, 0);
        lblTypingHead.setText("");
        lblTypingBar.setVisible(false);
        markReadAsync(id);
        renderHeaderAndDrawer();
        renderConvList();
        renderMessages();
    }

    private void markReadAsync(String convId) {
        ioExec.submit(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("convId", convId);
                postJson("/api/read", body);
            } catch (Exception ignored) {}
        });
    }

    // ==================== 4. GỬI TIN / EMOJI / FILE / GHI ÂM ====================
    private void sendTextMessage() {
        String txt = txtMsg.getText().trim();
        if (txt.isEmpty()) return;
        txtMsg.setText("");
        String ts = LocalTime.now().format(HH_MM);
        String type = "my_cloud".equals(curId) ? "my_cloud" : (curIsGroup ? "group_message" : "direct_message");

        JsonObject payload = new JsonObject();
        payload.addProperty("type", type);
        payload.addProperty("content", txt);
        payload.addProperty("timestamp", ts);
        if (curIsGroup) payload.addProperty("groupId", curId);
        else if (!"my_cloud".equals(curId)) payload.addProperty("toUser", curId);

        String targetConv = curId;
        ioExec.submit(() -> {
            try {
                JsonObject res = postJson("/api/send", payload);
                if (res != null && res.has("messageId")) {
                    ChatMessage local = new ChatMessage(type);
                    local.setMessageId(res.get("messageId").getAsString());
                    local.setUsername(me);
                    local.setDisplayName(myName);
                    local.setContent(txt);
                    local.setTimestamp(ts);
                    local.setStatus("SENT");
                    if (curIsGroup) local.setGroupId(targetConv);
                    else if (!"my_cloud".equals(targetConv)) local.setToUser(targetConv);
                    SwingUtilities.invokeLater(() -> pushMsg(targetConv, local));
                }
            } catch (Exception ignored) {}
        });
    }

    private void showEmojiMenu(Component anchor) {
        String[] emojis = {"😀", "😂", "😍", "🥰", "😎", "😭", "👍", "❤️", "🔥", "🎉", "👏", "💯"};
        JPopupMenu pop = new JPopupMenu();
        JPanel grid = new JPanel(new GridLayout(2, 6, 4, 4));
        grid.setBorder(new EmptyBorder(6, 6, 6, 6));
        for (String em : emojis) {
            JButton b = new JButton(em);
            b.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 18));
            b.addActionListener(e -> {
                txtMsg.setText(txtMsg.getText() + em);
                pop.setVisible(false);
                txtMsg.requestFocus();
            });
            grid.add(b);
        }
        pop.add(grid);
        pop.show(anchor, 0, -90);
    }

    private void showAttachMenu(Component anchor) {
        JPopupMenu pop = new JPopupMenu();
        JMenuItem miFile = new JMenuItem("Chọn File", UIManager.getIcon("FileView.fileIcon"));
        JMenuItem miFolder = new JMenuItem("Chọn Thư mục", UIManager.getIcon("FileView.directoryIcon"));
        miFile.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        miFolder.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        miFile.addActionListener(e -> pickAndSendFile(false));
        miFolder.addActionListener(e -> pickAndSendFolder());
        pop.add(miFile);
        pop.add(miFolder);
        pop.show(anchor, 0, -64);
    }

    private void pickAndSendFile(boolean imagesOnly) {
        FileDialog fd = new FileDialog(mainFrame, imagesOnly ? "Chọn hình ảnh" : "Chọn File", FileDialog.LOAD);
        if (imagesOnly) {
            fd.setFile("*.png;*.jpg;*.jpeg;*.gif;*.bmp;*.webp");
        }
        fd.setVisible(true);
        String dir = fd.getDirectory();
        String file = fd.getFile();
        if (dir == null || file == null) return;
        File f = new File(dir, file);
        if (f.length() > 50L * 1024 * 1024) { JOptionPane.showMessageDialog(mainFrame, "Tệp vượt quá 50MB!"); return; }
        ioExec.submit(() -> {
            try { uploadAndSendBytes(Files.readAllBytes(f.toPath()), f.getName()); }
            catch (Exception ex) { SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(mainFrame, "Lỗi gửi tệp: " + ex.getMessage())); }
        });
    }

    private void pickAndSendFolder() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Chọn Thư mục");
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fc.showOpenDialog(mainFrame) != JFileChooser.APPROVE_OPTION) return;
        File dir = fc.getSelectedFile();
        if (dir == null || !dir.isDirectory()) return;
        ioExec.submit(() -> {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                Path root = dir.toPath();
                try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                    Files.walk(root).filter(Files::isRegularFile).forEach(p -> {
                        try {
                            String rel = root.relativize(p).toString().replace("\\", "/");
                            zos.putNextEntry(new ZipEntry(rel));
                            Files.copy(p, zos);
                            zos.closeEntry();
                        } catch (IOException ex) {
                            throw new UncheckedIOException(ex);
                        }
                    });
                }
                byte[] zipBytes = baos.toByteArray();
                if (zipBytes.length > 50L * 1024 * 1024) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(mainFrame, "Thư mục nén vượt quá 50MB!"));
                    return;
                }
                uploadAndSendBytes(zipBytes, dir.getName() + ".zip");
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(mainFrame, "Lỗi gửi thư mục: " + ex.getMessage()));
            }
        });
    }

    private void toggleVoiceRecord() {
        if (!recording) {
            try {
                AudioFormat fmt = new AudioFormat(16000, 16, 1, true, false);
                micLine = (TargetDataLine) AudioSystem.getLine(new DataLine.Info(TargetDataLine.class, fmt));
                micLine.open(fmt);
                micLine.start();
                recording = true;
                wavBuffer = new ByteArrayOutputStream();
                btnVoice.setIcon(new VectorIcon("stop", 18, new Color(0xDC2626)));
                btnVoice.setToolTipText("Dừng ghi âm");
                ioExec.submit(() -> {
                    byte[] buf = new byte[4096];
                    while (recording) {
                        int n = micLine.read(buf, 0, buf.length);
                        if (n > 0) wavBuffer.write(buf, 0, n);
                    }
                });
            } catch (Exception ex) { JOptionPane.showMessageDialog(mainFrame, "Không bật được Micro: " + ex.getMessage()); }
        } else {
            recording = false;
            btnVoice.setIcon(new VectorIcon("mic", 18, new Color(0x475569)));
            btnVoice.setToolTipText("Ghi âm tin nhắn thoại");
            try {
                if (micLine != null) { micLine.stop(); micLine.close(); }
                byte[] pcm = wavBuffer.toByteArray();
                AudioFormat fmt = new AudioFormat(16000, 16, 1, true, false);
                ByteArrayOutputStream wavOut = new ByteArrayOutputStream();
                try (AudioInputStream ais = new AudioInputStream(new ByteArrayInputStream(pcm), fmt, pcm.length / fmt.getFrameSize())) {
                    AudioSystem.write(ais, AudioFileFormat.Type.WAVE, wavOut);
                }
                String fname = "voice_" + System.currentTimeMillis() + ".wav";
                ioExec.submit(() -> { try { uploadAndSendBytes(wavOut.toByteArray(), fname); } catch (Exception ignored) {} });
            } catch (Exception ex) { JOptionPane.showMessageDialog(mainFrame, "Lỗi lưu ghi âm: " + ex.getMessage()); }
        }
    }

    private void uploadAndSendBytes(byte[] bytes, String fileName) throws Exception {
        String enc = URLEncoder.encode(fileName, StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(serverBase + "/api/upload?name=" + enc))
                .header("X-Zalo-Token", token)
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonObject up = gson.fromJson(resp.body(), JsonObject.class);
        if (up != null && up.has("ok") && up.get("ok").getAsBoolean()) {
            String url = up.get("url").getAsString();
            String ts = LocalTime.now().format(HH_MM);
            String targetConv = curId;
            JsonObject payload = new JsonObject();
            payload.addProperty("type", "file_transfer");
            payload.addProperty("fileName", fileName);
            payload.addProperty("fileSize", (long) bytes.length);
            payload.addProperty("fileData", url);
            payload.addProperty("timestamp", ts);
            if (curIsGroup) payload.addProperty("groupId", targetConv);
            else if (!"my_cloud".equals(targetConv)) payload.addProperty("toUser", targetConv);
            else payload.addProperty("toUser", me);

            JsonObject sent = postJson("/api/send", payload);
            ChatMessage local = new ChatMessage("file_transfer");
            if (sent != null && sent.has("messageId")) local.setMessageId(sent.get("messageId").getAsString());
            local.setUsername(me);
            local.setDisplayName(myName);
            local.setFileName(fileName);
            local.setFileSize((long) bytes.length);
            local.setFileData(url);
            local.setTimestamp(ts);
            local.setStatus("SENT");
            if (curIsGroup) local.setGroupId(targetConv);
            else local.setToUser(targetConv);
            SwingUtilities.invokeLater(() -> pushMsg(targetConv, local));
        }
    }

    private void openCreateGroupDialog() {
        JTextField txtName = new JTextField();
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.add(new JLabel("Tên nhóm mới:"));
        p.add(txtName);
        p.add(Box.createVerticalStrut(8));
        p.add(new JLabel("Chọn thành viên:"));
        List<JCheckBox> checks = new ArrayList<>();
        for (JsonObject u : contacts) {
            String uname = u.get("username").getAsString();
            if (uname.equalsIgnoreCase(me)) continue;
            String dn = u.has("displayName") ? u.get("displayName").getAsString() : uname;
            JCheckBox cb = new JCheckBox(dn + " (@" + uname + ")");
            cb.putClientProperty("uname", uname);
            checks.add(cb);
            p.add(cb);
        }
        if (JOptionPane.showConfirmDialog(mainFrame, p, "Tạo nhóm chat", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        String gname = txtName.getText().trim();
        if (gname.isEmpty()) return;
        JsonArray mems = new JsonArray();
        for (JCheckBox cb : checks) if (cb.isSelected()) mems.add((String) cb.getClientProperty("uname"));
        ioExec.submit(() -> {
            try {
                JsonObject req = new JsonObject();
                req.addProperty("groupName", gname);
                req.add("members", mems);
                JsonObject res = postJson("/api/groups/create", req);
                if (res != null && res.has("ok") && res.get("ok").getAsBoolean()) {
                    String gid = res.get("groupId").getAsString();
                    SwingUtilities.invokeLater(() -> selectConv(gid, gname, true));
                }
            } catch (Exception ignored) {}
        });
    }

    // ==================== 5. VẼ HEADER, DRAWER, DANH SÁCH & TIN NHẮN ====================
    private void renderHeaderAndDrawer() {
        lblChatTitle.setText(curName);
        lblDrawerName.setText(curName);
        if (txtMsg != null) {
            txtMsg.putClientProperty("JTextField.placeholderText", "Nhập @, tin nhắn tới " + curName);
        }
        Color c = "my_cloud".equals(curId) ? ZALO_BLUE : avColor(curName);
        String init = "my_cloud".equals(curId) ? "☁" : curName.substring(0, 1).toUpperCase();
        lblHeaderAv.update(c, init, false);
        lblDrawerAv.update(c, init, false);

        if ("my_cloud".equals(curId)) {
            lblChatSub.setText("Lưu trữ cá nhân");
            lblDrawerSub.setText("Lưu trữ cá nhân");
        } else if (curIsGroup) {
            int cnt = 0;
            for (JsonObject g : groups) {
                if (curId.equals(g.get("id").getAsString()) && g.has("members")) cnt = g.getAsJsonArray("members").size();
            }
            lblChatSub.setText(cnt + " thành viên");
            lblDrawerSub.setText(cnt + " thành viên");
        } else {
            String st = formatLastSeen(findContact(curId));
            lblChatSub.setText(st);
            lblDrawerSub.setText(st);
        }

        drawerMediaBox.removeAll();
        drawerFileBox.removeAll();
        JLabel mTitle = new JLabel("Ảnh/Video");
        mTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        mTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        drawerMediaBox.add(mTitle);
        drawerMediaBox.add(Box.createVerticalStrut(6));

        JLabel fTitle = new JLabel("File");
        fTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        fTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        drawerFileBox.add(fTitle);
        drawerFileBox.add(Box.createVerticalStrut(6));

        JPanel mediaGrid = new JPanel(new GridLayout(0, 4, 4, 4));
        mediaGrid.setOpaque(false);
        mediaGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
        mediaGrid.setMaximumSize(new Dimension(248, 126));

        List<ChatMessage> arr = history.get(curId);
        int imgs = 0, files = 0;
        if (arr != null) {
            List<ChatMessage> snap;
            synchronized (arr) { snap = new ArrayList<>(arr); }
            for (int i = snap.size() - 1; i >= 0; i--) {
                ChatMessage m = snap.get(i);
                if (!"file_transfer".equals(m.getType()) || m.getIsRecalled() || m.getFileName() == null) continue;
                String fn = m.getFileName().toLowerCase();
                if (fn.matches(".*\\.(png|jpg|jpeg|gif|bmp|webp)$") && m.getFileData() != null) {
                    if (imgs < 8) {
                        JLabel thumb = new JLabel("📷", SwingConstants.CENTER);
                        thumb.setPreferredSize(new Dimension(56, 56));
                        thumb.setOpaque(true);
                        thumb.setBackground(new Color(0xF1F5F9));
                        thumb.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                        String url = m.getFileData();
                        String title = m.getFileName();
                        loadMiniThumbAsync(url, thumb);
                        thumb.addMouseListener(new MouseAdapter() {
                            @Override public void mouseClicked(MouseEvent e) { openImageDialog(url, title); }
                        });
                        mediaGrid.add(thumb);
                        imgs++;
                    }
                } else if (files < 3 && m.getFileData() != null) {
                    JPanel fRow = new JPanel(new BorderLayout(8, 0));
                    fRow.setOpaque(false);
                    fRow.setAlignmentX(Component.LEFT_ALIGNMENT);
                    fRow.setMaximumSize(new Dimension(248, 38));
                    fRow.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    JLabel extBadge = createFileExtBadge(fn, 28);
                    String dispName = m.getFileName().length() > 22 ? m.getFileName().substring(0, 20) + "..." : m.getFileName();
                    JLabel fName = new JLabel("<html><b>" + escapeHtml(dispName) + "</b><br><span style='color:#64748b;font-size:10px'>" + formatFileSize(m.getFileSize()) + "</span></html>");
                    fName.setFont(new Font("Segoe UI", Font.PLAIN, 11));
                    fRow.add(extBadge, BorderLayout.WEST);
                    fRow.add(fName, BorderLayout.CENTER);
                    String url = m.getFileData();
                    String title = m.getFileName();
                    fRow.addMouseListener(new MouseAdapter() {
                        @Override public void mouseClicked(MouseEvent e) { playOrOpenFile(url, title, fn.endsWith(".wav")); }
                    });
                    drawerFileBox.add(fRow);
                    drawerFileBox.add(Box.createVerticalStrut(4));
                    files++;
                }
            }
        }
        if (imgs > 0) {
            drawerMediaBox.add(mediaGrid);
        } else {
            JLabel e = new JLabel("Chưa có ảnh/video nào"); e.setFont(new Font("Segoe UI", Font.PLAIN, 12)); e.setForeground(MUTED); drawerMediaBox.add(e);
        }
        if (files == 0) {
            JLabel e = new JLabel("Chưa có tệp nào"); e.setFont(new Font("Segoe UI", Font.PLAIN, 12)); e.setForeground(MUTED); drawerFileBox.add(e);
        }
        drawerMediaBox.revalidate();
        drawerFileBox.revalidate();
    }

    private void renderConvList() {
        convListPanel.removeAll();
        String q = txtSearch != null ? txtSearch.getText().trim().toLowerCase() : "";
        addConvItemIfMatch("my_cloud", "Cloud của tôi", false, true, "Lưu trữ cá nhân", q);
        for (JsonObject g : groups) {
            String gid = g.get("id").getAsString();
            String gname = g.has("name") ? g.get("name").getAsString() : gid;
            int cnt = g.has("members") ? g.getAsJsonArray("members").size() : 0;
            addConvItemIfMatch(gid, gname, true, true, cnt + " thành viên", q);
        }
        for (JsonObject u : contacts) {
            String uname = u.get("username").getAsString();
            if (uname.equalsIgnoreCase(me)) continue;
            String dn = u.has("displayName") ? u.get("displayName").getAsString() : uname;
            boolean onl = u.has("online") && u.get("online").getAsBoolean();
            addConvItemIfMatch(uname, dn, false, onl, onl ? "Đang hoạt động" : "Ngoại tuyến", q);
        }
        convListPanel.add(Box.createVerticalGlue());
        convListPanel.revalidate();
        convListPanel.repaint();
        int totalUnread = 0;
        for (int v : unreadMap.values()) totalUnread += Math.max(0, v);
        updateTaskbarBadge(totalUnread);
    }

    private void addConvItemIfMatch(String id, String name, boolean isGrp, boolean online, String defSub, String q) {
        if (!q.isEmpty() && !name.toLowerCase().contains(q)) return;
        if ("group".equals(convFilter) && !isGrp) return;
        int unread = unreadMap.getOrDefault(id, 0);
        if ("unread".equals(convFilter) && unread == 0) return;

        boolean active = id.equalsIgnoreCase(curId);
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
        row.setPreferredSize(new Dimension(290, 64));
        row.setBackground(active ? BG_ACTIVE : Color.WHITE);
        row.setBorder(new EmptyBorder(9, 12, 9, 12));
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        AvatarCircle av = new AvatarCircle(42, "my_cloud".equals(id) ? ZALO_BLUE : avColor(name),
                "my_cloud".equals(id) ? "☁" : name.substring(0, 1).toUpperCase(),
                online && !"my_cloud".equals(id));

        List<ChatMessage> arr = history.get(id);
        ChatMessage last = (arr != null && !arr.isEmpty()) ? arr.get(arr.size() - 1) : null;
        String prev = defSub;
        String time = "";
        if (last != null) {
            time = last.getTimestamp() != null ? last.getTimestamp() : "";
            if (last.getIsRecalled()) prev = "Tin nhắn đã được thu hồi";
            else if (last.getFileName() != null) {
                String fnl = last.getFileName().toLowerCase();
                String tag = fnl.matches(".*\\.(png|jpg|jpeg|gif|bmp|webp)$") ? "[Ảnh] "
                        : (fnl.matches(".*\\.(mp4|mov|mkv)$") ? "[Video] "
                        : (fnl.matches(".*\\.(wav|mp3|ogg|m4a|webm)$") ? "[Thoại] " : "[Tệp] "));
                prev = tag + last.getFileName();
            }
            else if (last.getContent() != null) prev = last.getContent();
        }
        if (prev.length() > 26) prev = prev.substring(0, 26) + "...";

        JPanel mid = new JPanel(new GridLayout(2, 1, 0, 2));
        mid.setOpaque(false);
        JLabel lblN = new JLabel(name);
        lblN.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblN.setForeground(TEXT_MAIN);
        JLabel lblP = new JLabel(prev);
        lblP.setFont(new Font("Segoe UI", unread > 0 ? Font.BOLD : Font.PLAIN, 12));
        lblP.setForeground(unread > 0 ? TEXT_MAIN : MUTED);
        mid.add(lblN);
        mid.add(lblP);

        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        JLabel lblT = new JLabel(time);
        lblT.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblT.setForeground(MUTED);
        lblT.setAlignmentX(Component.RIGHT_ALIGNMENT);
        right.add(lblT);
        if (unread > 0) {
            JLabel badge = new JLabel(" " + unread + " ");
            badge.setOpaque(true);
            badge.setBackground(new Color(0xEF4444));
            badge.setForeground(Color.WHITE);
            badge.setFont(new Font("Segoe UI", Font.BOLD, 10));
            badge.setAlignmentX(Component.RIGHT_ALIGNMENT);
            right.add(Box.createVerticalStrut(4));
            right.add(badge);
        }

        row.add(av, BorderLayout.WEST);
        row.add(mid, BorderLayout.CENTER);
        row.add(right, BorderLayout.EAST);
        row.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { selectConv(id, name, isGrp); }
        });
        convListPanel.add(row);
    }

    private void renderMessages() {
        msgListPanel.removeAll();
        List<ChatMessage> arr = history.get(curId);
        if (arr == null || arr.isEmpty()) {
            JLabel empty = new JLabel("Chưa có tin nhắn nào. Hãy gửi lời chào!");
            empty.setForeground(MUTED);
            empty.setAlignmentX(Component.CENTER_ALIGNMENT);
            msgListPanel.add(empty);
        } else {
            JPanel pillWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 4));
            pillWrap.setOpaque(false);
            pillWrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
            JLabel datePill = new JLabel("  Hôm nay  ");
            datePill.setOpaque(true);
            datePill.setBackground(new Color(0xB4BCC8));
            datePill.setForeground(Color.WHITE);
            datePill.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            pillWrap.add(datePill);
            msgListPanel.add(pillWrap);
            msgListPanel.add(Box.createVerticalStrut(6));

            List<ChatMessage> snap;
            synchronized (arr) { snap = new ArrayList<>(arr); }
            for (int i = 0; i < snap.size(); i++) {
                ChatMessage m = snap.get(i);
                boolean isLastMine = me.equalsIgnoreCase(m.getUsername()) && i == snap.size() - 1;
                msgListPanel.add(buildMessageRow(m, isLastMine));
                msgListPanel.add(Box.createVerticalStrut(8));
            }
        }
        msgListPanel.revalidate();
        msgListPanel.repaint();
        SwingUtilities.invokeLater(() -> msgScroll.getVerticalScrollBar().setValue(msgScroll.getVerticalScrollBar().getMaximum()));
    }

    private JComponent buildMessageRow(ChatMessage m, boolean isLastMine) {
        if ("system".equals(m.getType())) {
            JLabel sys = new JLabel(m.getContent());
            sys.setFont(new Font("Segoe UI", Font.ITALIC, 12));
            sys.setForeground(MUTED);
            sys.setAlignmentX(Component.CENTER_ALIGNMENT);
            return sys;
        }
        boolean isMe = me.equalsIgnoreCase(m.getUsername());
        JPanel outer = new JPanel();
        outer.setOpaque(false);
        outer.setLayout(new BoxLayout(outer, BoxLayout.Y_AXIS));
        outer.setMaximumSize(new Dimension(Integer.MAX_VALUE, 320));

        JPanel line = new JPanel(new FlowLayout(isMe ? FlowLayout.RIGHT : FlowLayout.LEFT, 8, 0));
        line.setOpaque(false);

        if (!isMe) {
            String sn = m.getDisplayName() != null ? m.getDisplayName() : m.getUsername();
            line.add(new AvatarCircle(32, avColor(sn), sn.substring(0, 1).toUpperCase(), false));
        }

        RoundedPanel bubble = new RoundedPanel(14, isMe ? BG_ME : Color.WHITE, isMe ? new Color(0xCBE0FF) : BORDER_COL);
        bubble.setLayout(new BoxLayout(bubble, BoxLayout.Y_AXIS));
        bubble.setBorder(new EmptyBorder(9, 13, 9, 13));

        if (!isMe && curIsGroup) {
            JLabel sender = new JLabel(m.getDisplayName() != null ? m.getDisplayName() : m.getUsername());
            sender.setFont(new Font("Segoe UI", Font.BOLD, 11));
            sender.setForeground(ZALO_BLUE);
            sender.setAlignmentX(Component.LEFT_ALIGNMENT);
            bubble.add(sender);
            bubble.add(Box.createVerticalStrut(3));
        }

        String fn = m.getFileName() != null ? m.getFileName().toLowerCase() : "";
        if ("file_transfer".equals(m.getType()) && m.getFileData() != null && !m.getIsRecalled()) {
            String url = m.getFileData();
            if (fn.matches(".*\\.(png|jpg|jpeg|gif|bmp|webp)$")) {
                JLabel imgLbl = new JLabel("Đang tải ảnh: " + m.getFileName());
                imgLbl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                loadThumbAsync(url, imgLbl);
                imgLbl.addMouseListener(new MouseAdapter() {
                    @Override public void mouseClicked(MouseEvent e) { openImageDialog(url, m.getFileName()); }
                });
                bubble.add(imgLbl);
            } else if (fn.matches(".*\\.(wav|mp3|ogg|m4a|webm)$")) {
                bubble.add(buildVoiceCard(m, url, fn));
            } else if (fn.matches(".*\\.(mp4|mov|mkv)$")) {
                bubble.add(buildVideoCard(m, url));
            } else {
                bubble.add(buildFileCard(m, url, fn));
            }
        } else {
            JLabel txt = new JLabel("<html><body style='max-width:340px'>" + escapeHtml(m.getContent() != null ? m.getContent() : "") + "</body></html>");
            txt.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 13));
            txt.setForeground(m.getIsRecalled() ? MUTED : TEXT_MAIN);
            bubble.add(txt);
        }

        StringBuilder meta = new StringBuilder(m.getTimestamp() != null ? m.getTimestamp() : "");
        Map<String, Integer> reacts = m.getMessageId() != null ? reactions.get(m.getMessageId()) : null;
        if (reacts != null && !reacts.isEmpty()) {
            meta.append("   ");
            int total = 0;
            for (Map.Entry<String, Integer> re : reacts.entrySet()) { meta.append(re.getKey()); total += re.getValue(); }
            meta.append(" ").append(total);
        }
        JLabel metaLbl = new JLabel(meta.toString());
        metaLbl.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 11));
        metaLbl.setForeground(MUTED);
        metaLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        bubble.add(Box.createVerticalStrut(4));
        bubble.add(metaLbl);

        if (m.getMessageId() != null && !m.getIsRecalled()) {
            JPopupMenu ctx = new JPopupMenu();
            for (String em : new String[]{"👍", "❤️", "😆", "😮", "😢", "😡"}) {
                JMenuItem it = new JMenuItem("Thả " + em);
                it.addActionListener(e -> reactMessage(m.getMessageId(), em));
                ctx.add(it);
            }
            if ("file_transfer".equals(m.getType()) && m.getFileData() != null && m.getFileName() != null) {
                ctx.addSeparator();
                JMenuItem saveIt = new JMenuItem("Tải về máy (Downloads\\ZaloFiles)");
                saveIt.addActionListener(e -> openInZaloFilesFolder(m.getFileData(), m.getFileName()));
                ctx.add(saveIt);
            }
            if (isMe) {
                ctx.addSeparator();
                JMenuItem recallIt = new JMenuItem("Thu hồi tin nhắn");
                recallIt.addActionListener(e -> recallMessage(m.getMessageId()));
                ctx.add(recallIt);
            }
            bubble.setComponentPopupMenu(ctx);
        }

        line.add(bubble);
        outer.add(line);

        if (isLastMine) {
            String st = "SEEN".equals(m.getStatus()) ? "Đã xem" : ("DELIVERED".equals(m.getStatus()) ? "Đã nhận" : "Đã gửi");
            JPanel pillRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 2));
            pillRow.setOpaque(false);
            JLabel pill = new JLabel(" " + st + " ", new VectorIcon("check", 11, Color.WHITE), SwingConstants.LEFT);
            pill.setOpaque(true);
            pill.setBackground(new Color(0x94A3B8));
            pill.setForeground(Color.WHITE);
            pill.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            pill.setBorder(new EmptyBorder(2, 6, 2, 6));
            pillRow.add(pill);
            outer.add(pillRow);
        }
        return outer;
    }

    // ==================== 6. HELPERS & CUSTOM SWING COMPONENTS ====================
    private static class RoundedPanel extends JPanel {
        private final int arc;
        private final Color bg;
        private final Color border;

        RoundedPanel(int arc, Color bg, Color border) {
            this.arc = arc;
            this.bg = bg;
            this.border = border;
            setOpaque(false);
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Shape rr = new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            g2.setColor(bg);
            g2.fill(rr);
            if (border != null) {
                g2.setColor(border);
                g2.draw(rr);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static class AvatarCircle extends JComponent {
        private final int size;
        private Color bg;
        private String letter;
        private boolean online;

        AvatarCircle(int size, Color bg, String letter, boolean online) {
            this.size = size;
            this.bg = bg;
            this.letter = letter;
            this.online = online;
            setPreferredSize(new Dimension(size, size));
            setMinimumSize(new Dimension(size, size));
            setMaximumSize(new Dimension(size, size));
        }

        void update(Color bg, String letter, boolean online) {
            this.bg = bg;
            this.letter = letter;
            this.online = online;
            repaint();
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(bg);
            g2.fill(new Ellipse2D.Float(0, 0, size - 1, size - 1));
            g2.setColor(Color.WHITE);
            if ("☁".equals(letter)) {
                int cw = Math.round(size * 0.56f);
                int cx = (size - cw) / 2;
                int cy = Math.round(size * 0.34f);
                g2.fillOval(cx, cy + cw / 5, cw / 2, cw / 2);
                g2.fillOval(cx + cw / 5, cy, cw / 2, cw / 2);
                g2.fillOval(cx + cw / 2, cy + cw / 6, cw / 2, cw / 2);
                g2.fillRect(cx + cw / 4, cy + cw / 3, cw / 2, cw / 3 + 1);
            } else {
                g2.setFont(new Font("Segoe UI", Font.BOLD, Math.max(12, size / 3)));
                FontMetrics fm = g2.getFontMetrics();
                int x = (size - fm.stringWidth(letter)) / 2;
                int y = (size - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(letter, x, y);
            }
            if (online) {
                int d = Math.max(9, size / 4);
                g2.setColor(Color.WHITE);
                g2.fill(new Ellipse2D.Float(size - d - 1, size - d - 1, d + 1, d + 1));
                g2.setColor(new Color(0x22C55E));
                g2.fill(new Ellipse2D.Float(size - d, size - d, d - 1, d - 1));
            }
            g2.dispose();
        }
    }

    private Color avColor(String name) {
        int h = 0;
        for (int i = 0; i < name.length(); i++) h = 31 * h + name.charAt(i);
        return AV_COLORS[Math.abs(h) % AV_COLORS.length];
    }

    private void reactMessage(String msgId, String emoji) {
        ioExec.submit(() -> {
            try {
                JsonObject req = new JsonObject();
                req.addProperty("messageId", msgId);
                req.addProperty("emoji", emoji);
                postJson("/api/react", req);
            } catch (Exception ignored) {}
        });
    }

    private JPanel drawerActionBtn(String icon, String label, Runnable onClick) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        JButton b = new JButton(new VectorIcon(icon, 16, new Color(0x475569)));
        b.setPreferredSize(new Dimension(34, 34));
        b.setMaximumSize(new Dimension(34, 34));
        b.setBackground(new Color(0xF1F5F9));
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setAlignmentX(Component.CENTER_ALIGNMENT);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addActionListener(e -> onClick.run());
        JLabel l = new JLabel("<html><center>" + label + "</center></html>", SwingConstants.CENTER);
        l.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        l.setForeground(MUTED);
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        p.add(b);
        p.add(Box.createVerticalStrut(3));
        p.add(l);
        return p;
    }

    private JLabel createFileExtBadge(String fnLower, int size) {
        String ext = "FILE";
        Color bg = new Color(0x64748B);
        if (fnLower.endsWith(".pdf")) { ext = "PDF"; bg = new Color(0xEF4444); }
        else if (fnLower.endsWith(".doc") || fnLower.endsWith(".docx")) { ext = "DOC"; bg = new Color(0x2563EB); }
        else if (fnLower.endsWith(".xls") || fnLower.endsWith(".xlsx")) { ext = "XLS"; bg = new Color(0x16A34A); }
        else if (fnLower.endsWith(".zip") || fnLower.endsWith(".rar") || fnLower.endsWith(".7z")) { ext = "ZIP"; bg = new Color(0xD97706); }
        else if (fnLower.endsWith(".mp4") || fnLower.endsWith(".mov") || fnLower.endsWith(".mkv")) { ext = "MP4"; bg = new Color(0x7C3AED); }
        JLabel badge = new JLabel(ext, SwingConstants.CENTER);
        badge.setOpaque(true);
        badge.setBackground(bg);
        badge.setForeground(Color.WHITE);
        badge.setFont(new Font("Segoe UI", Font.BOLD, size > 32 ? 11 : 9));
        badge.setPreferredSize(new Dimension(size, size));
        badge.setMinimumSize(new Dimension(size, size));
        badge.setMaximumSize(new Dimension(size, size));
        return badge;
    }

    private String formatFileSize(Long bytes) {
        long b = bytes != null ? bytes : 0L;
        if (b >= 1024L * 1024L) return String.format("%.2f MB", b / (1024.0 * 1024.0));
        return Math.max(1, b / 1024L) + " KB";
    }

    private JPanel buildFileCard(ChatMessage m, String url, String fnLower) {
        JPanel card = new JPanel(new BorderLayout(10, 0));
        card.setOpaque(false);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(340, 56));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        JLabel badge = createFileExtBadge(fnLower, 38);
        String disp = m.getFileName().length() > 28 ? m.getFileName().substring(0, 25) + "..." : m.getFileName();
        JLabel info = new JLabel("<html><b>" + escapeHtml(disp) + "</b><br><span style='color:#64748b;font-size:11px'>" + formatFileSize(m.getFileSize()) + "</span> <span style='color:#16a34a;font-size:11px'>• Đã có trên server</span></html>");
        info.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 4));
        btns.setOpaque(false);
        JButton folderBtn = new JButton(new VectorIcon("folder", 15, new Color(0x475569)));
        folderBtn.setPreferredSize(new Dimension(30, 30));
        folderBtn.setBackground(Color.WHITE);
        folderBtn.setToolTipText(fnLower.endsWith(".zip") ? "Giải nén & mở thư mục" : "Lưu & mở thư mục");
        folderBtn.addActionListener(e -> openInZaloFilesFolder(url, m.getFileName()));

        JButton openBtn = new JButton(new VectorIcon("download", 15, new Color(0x475569)));
        openBtn.setPreferredSize(new Dimension(30, 30));
        openBtn.setBackground(Color.WHITE);
        openBtn.setToolTipText("Mở tệp");
        openBtn.addActionListener(e -> playOrOpenFile(url, m.getFileName(), false));
        btns.add(folderBtn);
        btns.add(openBtn);

        card.add(badge, BorderLayout.WEST);
        card.add(info, BorderLayout.CENTER);
        card.add(btns, BorderLayout.EAST);
        card.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { playOrOpenFile(url, m.getFileName(), false); }
        });
        return card;
    }

    private JPanel buildVoiceCard(ChatMessage m, String url, String fnLower) {
        JPanel card = new JPanel(new BorderLayout(10, 0));
        card.setOpaque(false);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(320, 48));

        JButton playBtn = new JButton(new VectorIcon("play", 28, Color.WHITE));
        playBtn.setPreferredSize(new Dimension(34, 34));
        playBtn.setContentAreaFilled(false);
        playBtn.setBorderPainted(false);
        playBtn.setFocusPainted(false);
        playBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        playBtn.setToolTipText("Phát tin nhắn thoại");
        playBtn.addActionListener(e -> playOrOpenFile(url, m.getFileName(), fnLower.endsWith(".wav")));

        JLabel info = new JLabel("<html><b>Tin nhắn thoại</b><br><span style='color:#64748b;font-size:11px'>" + formatFileSize(m.getFileSize()) + " • " + escapeHtml(m.getFileName()) + "</span></html>");
        info.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 4));
        btns.setOpaque(false);
        JButton folderBtn = new JButton(new VectorIcon("folder", 14, new Color(0x475569)));
        folderBtn.setPreferredSize(new Dimension(28, 28));
        folderBtn.setBackground(Color.WHITE);
        folderBtn.setToolTipText("Lưu & mở thư mục");
        folderBtn.addActionListener(e -> openInZaloFilesFolder(url, m.getFileName()));

        JButton dlBtn = new JButton(new VectorIcon("download", 14, new Color(0x475569)));
        dlBtn.setPreferredSize(new Dimension(28, 28));
        dlBtn.setBackground(Color.WHITE);
        dlBtn.setToolTipText("Tải về máy");
        dlBtn.addActionListener(e -> openInZaloFilesFolder(url, m.getFileName()));
        btns.add(folderBtn);
        btns.add(dlBtn);

        card.add(playBtn, BorderLayout.WEST);
        card.add(info, BorderLayout.CENTER);
        card.add(btns, BorderLayout.EAST);
        return card;
    }

    private JPanel buildVideoCard(ChatMessage m, String url) {
        JPanel wrap = new JPanel();
        wrap.setOpaque(false);
        wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));
        wrap.setAlignmentX(Component.LEFT_ALIGNMENT);

        RoundedPanel screen = new RoundedPanel(10, new Color(0x0F172A), new Color(0x334155));
        screen.setPreferredSize(new Dimension(270, 150));
        screen.setMaximumSize(new Dimension(270, 150));
        screen.setLayout(new BorderLayout());
        screen.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        screen.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton playCenter = new JButton(new VectorIcon("play", 32, Color.WHITE));
        playCenter.setContentAreaFilled(false);
        playCenter.setBorderPainted(false);
        playCenter.setFocusPainted(false);
        playCenter.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        playCenter.addActionListener(e -> openVideoPlayer(url, m.getFileName()));

        JPanel botBar = new JPanel(new BorderLayout(6, 0));
        botBar.setOpaque(false);
        botBar.setBorder(new EmptyBorder(4, 10, 6, 8));
        String disp = m.getFileName().length() > 24 ? m.getFileName().substring(0, 21) + "..." : m.getFileName();
        JLabel cap = new JLabel(disp + " • " + formatFileSize(m.getFileSize()));
        cap.setForeground(new Color(0xE2E8F0));
        cap.setFont(new Font("Segoe UI", Font.PLAIN, 11));

        JButton dlVid = new JButton(new VectorIcon("download", 14, Color.WHITE));
        dlVid.setContentAreaFilled(false);
        dlVid.setBorderPainted(false);
        dlVid.setFocusPainted(false);
        dlVid.setToolTipText("Tải video về máy");
        dlVid.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        dlVid.addActionListener(e -> openInZaloFilesFolder(url, m.getFileName()));

        botBar.add(cap, BorderLayout.CENTER);
        botBar.add(dlVid, BorderLayout.EAST);

        screen.add(playCenter, BorderLayout.CENTER);
        screen.add(botBar, BorderLayout.SOUTH);
        screen.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { openVideoPlayer(url, m.getFileName()); }
        });
        wrap.add(screen);
        return wrap;
    }

    private void openVideoPlayer(String url, String fileName) {
        ioExec.submit(() -> {
            try {
                String fullUrl = url.startsWith("http") ? url : (serverBase + url);
                Path html = Files.createTempFile("zalo_video_", ".html");
                String page = "<!doctype html><html><head><meta charset='utf-8'><title>Zalo Video - "
                        + escapeHtml(fileName) + "</title><style>html,body{margin:0;height:100%;background:#0b1120;display:flex;align-items:center;justify-content:center}video{width:100%;height:100%;outline:none}</style></head><body>"
                        + "<video src='" + fullUrl + "' controls autoplay playsinline></video></body></html>";
                Files.writeString(html, page, StandardCharsets.UTF_8);
                String edge = "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe";
                if (new File(edge).exists()) {
                    new ProcessBuilder(edge, "--app=" + html.toUri().toString(), "--window-size=880,540").start();
                } else {
                    playOrOpenFile(url, fileName, false);
                }
            } catch (Exception ex) {
                playOrOpenFile(url, fileName, false);
            }
        });
    }

    private void openInZaloFilesFolder(String url, String fileName) {
        ioExec.submit(() -> {
            try {
                byte[] data = downloadBytes(url);
                Path baseDir = Path.of(System.getProperty("user.home"), "Downloads", "ZaloFiles");
                Files.createDirectories(baseDir);
                String safeName = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
                if (safeName.toLowerCase().endsWith(".zip")) {
                    String folderName = safeName.substring(0, safeName.length() - 4);
                    Path outDir = baseDir.resolve(folderName);
                    Files.createDirectories(outDir);
                    try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(data))) {
                        ZipEntry ze;
                        while ((ze = zis.getNextEntry()) != null) {
                            Path target = outDir.resolve(ze.getName()).normalize();
                            if (!target.startsWith(outDir)) continue;
                            if (ze.isDirectory()) Files.createDirectories(target);
                            else {
                                Files.createDirectories(target.getParent());
                                Files.copy(zis, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            }
                        }
                    }
                    Desktop.getDesktop().open(outDir.toFile());
                } else {
                    Path out = baseDir.resolve(safeName);
                    Files.write(out, data);
                    new ProcessBuilder("explorer.exe", "/select," + out.toAbsolutePath()).start();
                }
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(mainFrame, "Không mở được thư mục: " + ex.getMessage()));
            }
        });
    }

    private void recallMessage(String msgId) {
        ioExec.submit(() -> {
            try {
                JsonObject req = new JsonObject();
                req.addProperty("type", "recall_message");
                req.addProperty("messageId", msgId);
                postJson("/api/send", req);
            } catch (Exception ignored) {}
        });
    }

    private void captureAndSendScreen() {
        try {
            mainFrame.setState(Frame.ICONIFIED);
            javax.swing.Timer t = new javax.swing.Timer(350, ev -> {
                try {
                    Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
                    BufferedImage cap = new Robot().createScreenCapture(screenRect);
                    mainFrame.setState(Frame.NORMAL);
                    mainFrame.toFront();
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(cap, "png", baos);
                    String fname = "screenshot_" + System.currentTimeMillis() + ".png";
                    ioExec.submit(() -> {
                        try { uploadAndSendBytes(baos.toByteArray(), fname); }
                        catch (Exception ex) { SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(mainFrame, "Lỗi gửi ảnh chụp: " + ex.getMessage())); }
                    });
                } catch (Exception ex) {
                    mainFrame.setState(Frame.NORMAL);
                    JOptionPane.showMessageDialog(mainFrame, "Không chụp được màn hình: " + ex.getMessage());
                }
            });
            t.setRepeats(false);
            t.start();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(mainFrame, "Lỗi chụp màn hình: " + ex.getMessage());
        }
    }

    private void loadMiniThumbAsync(String url, JLabel target) {
        ioExec.submit(() -> {
            try {
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(downloadBytes(url)));
                if (img != null) {
                    ImageIcon icon = new ImageIcon(img.getScaledInstance(56, 56, Image.SCALE_SMOOTH));
                    SwingUtilities.invokeLater(() -> { target.setText(""); target.setIcon(icon); });
                }
            } catch (Exception ignored) {}
        });
    }

    private void loadThumbAsync(String url, JLabel target) {
        ImageIcon cached = thumbCache.get(url);
        if (cached != null) { target.setText(""); target.setIcon(cached); return; }
        ioExec.submit(() -> {
            try {
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(downloadBytes(url)));
                if (img != null) {
                    int w = Math.min(220, img.getWidth());
                    int h = (int) ((double) img.getHeight() * w / img.getWidth());
                    ImageIcon icon = new ImageIcon(img.getScaledInstance(w, Math.max(1, h), Image.SCALE_SMOOTH));
                    thumbCache.put(url, icon);
                    SwingUtilities.invokeLater(() -> { target.setText(""); target.setIcon(icon); msgListPanel.revalidate(); });
                }
            } catch (Exception ignored) {}
        });
    }

    private void openImageDialog(String url, String title) {
        ioExec.submit(() -> {
            try {
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(downloadBytes(url)));
                if (img == null) return;
                SwingUtilities.invokeLater(() -> {
                    JDialog dlg = new JDialog(mainFrame, title, true);
                    int w = Math.min(820, img.getWidth());
                    int h = (int) ((double) img.getHeight() * w / img.getWidth());
                    dlg.add(new JScrollPane(new JLabel(new ImageIcon(img.getScaledInstance(w, h, Image.SCALE_SMOOTH)))));
                    dlg.pack();
                    dlg.setLocationRelativeTo(mainFrame);
                    dlg.setVisible(true);
                });
            } catch (Exception ignored) {}
        });
    }

    private void playOrOpenFile(String url, String fileName, boolean playWav) {
        ioExec.submit(() -> {
            try {
                byte[] data = downloadBytes(url);
                if (playWav) {
                    try (AudioInputStream ais = AudioSystem.getAudioInputStream(new BufferedInputStream(new ByteArrayInputStream(data)))) {
                        Clip clip = AudioSystem.getClip();
                        clip.open(ais);
                        clip.start();
                    }
                } else {
                    Path tmp = Files.createTempFile("zalo_", "_" + fileName.replaceAll("[^a-zA-Z0-9._-]", "_"));
                    Files.write(tmp, data);
                    Desktop.getDesktop().open(tmp.toFile());
                }
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(mainFrame, "Không mở được tệp: " + ex.getMessage()));
            }
        });
    }

    private JsonObject postJson(String path, JsonObject body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(serverBase + path))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
        if (token != null) b.header("X-Zalo-Token", token);
        HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return gson.fromJson(r.body(), JsonObject.class);
    }

    private JsonObject getJson(String path) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder().uri(URI.create(serverBase + path)).GET();
        if (token != null) b.header("X-Zalo-Token", token);
        HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return r.statusCode() == 200 ? gson.fromJson(r.body(), JsonObject.class) : null;
    }

    private byte[] downloadBytes(String path) throws Exception {
        String full = path.startsWith("http") ? path : (serverBase + path);
        return http.send(HttpRequest.newBuilder().uri(URI.create(full)).GET().build(), HttpResponse.BodyHandlers.ofByteArray()).body();
    }

    private JsonObject findContact(String uname) {
        for (JsonObject u : contacts) if (uname.equalsIgnoreCase(u.get("username").getAsString())) return u;
        return null;
    }

    private String formatLastSeen(JsonObject u) {
        if (u == null) return "Ngoại tuyến";
        if (u.has("online") && u.get("online").getAsBoolean()) return "Đang hoạt động";
        long ts = u.has("lastSeen") ? u.get("lastSeen").getAsLong() : 0L;
        if (ts <= 0) return "Ngoại tuyến";
        long diffSec = Math.max(0, (System.currentTimeMillis() - ts) / 1000);
        if (diffSec < 60) return "Vừa mới truy cập";
        if (diffSec < 3600) return "Hoạt động " + (diffSec / 60) + " phút trước";
        return "Hoạt động " + (diffSec / 3600) + " giờ trước";
    }

    private void updateTaskbarBadge(int totalUnread) {
        if (mainFrame == null || totalUnread == lastBadgeUnread) return;
        lastBadgeUnread = totalUnread;
        mainFrame.setIconImages(createZaloIcons(totalUnread));
    }

    private List<Image> createZaloIcons(int unread) {
        List<Image> imgs = new ArrayList<>();
        for (int sz : new int[]{16, 32, 64, 128}) {
            imgs.add(drawZaloIcon(sz, unread));
        }
        return imgs;
    }

    private BufferedImage drawZaloIcon(int sz, int unread) {
        BufferedImage img = new BufferedImage(sz, sz, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Nền xanh Zalo bo tròn
        int pad = Math.max(1, sz / 16);
        int arc = Math.max(4, sz / 4);
        g2.setColor(ZALO_BLUE);
        g2.fillRoundRect(pad, pad, sz - pad * 2, sz - pad * 2, arc, arc);

        // Bong bóng chat trắng + đuôi góc dưới trái
        int bx = Math.round(sz * 0.14f);
        int by = Math.round(sz * 0.16f);
        int bw = Math.round(sz * 0.72f);
        int bh = Math.round(sz * 0.64f);
        g2.setColor(Color.WHITE);
        g2.fillOval(bx, by, bw, bh);
        int[] tx = {bx + Math.round(bw * 0.12f), bx - Math.round(sz * 0.03f), bx + Math.round(bw * 0.34f)};
        int[] ty = {by + Math.round(bh * 0.72f), by + bh + Math.round(sz * 0.03f), by + Math.round(bh * 0.90f)};
        g2.fillPolygon(tx, ty, 3);

        // Chữ "Zalo" xanh ở giữa
        if (sz >= 24) {
            g2.setColor(ZALO_BLUE);
            Font f = new Font("Segoe UI", Font.BOLD, Math.max(8, Math.round(sz * 0.25f)));
            g2.setFont(f);
            FontMetrics fm = g2.getFontMetrics();
            String txt = "Zalo";
            int txPos = bx + (bw - fm.stringWidth(txt)) / 2;
            int tyPos = by + (bh - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(txt, txPos, tyPos);
        }

        // Badge đỏ số tin chưa đọc (1..5+) ở góc trên phải
        if (unread > 0 && sz >= 24) {
            int r = Math.round(sz * 0.40f);
            int rx = sz - r;
            int ry = 0;
            g2.setColor(new Color(0xEF4444));
            g2.fillOval(rx, ry, r, r);
            String btxt = unread > 5 ? "5+" : String.valueOf(unread);
            g2.setColor(Color.WHITE);
            Font bf = new Font("Segoe UI", Font.BOLD, Math.max(8, Math.round(r * 0.56f)));
            g2.setFont(bf);
            FontMetrics bfm = g2.getFontMetrics();
            g2.drawString(btxt, rx + (r - bfm.stringWidth(btxt)) / 2, ry + (r - bfm.getHeight()) / 2 + bfm.getAscent());
        }
        g2.dispose();
        return img;
    }

    private String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
    }

    private static class VectorIcon implements Icon {
        private final String kind;
        private final int sz;
        private final Color col;

        VectorIcon(String kind, int sz, Color col) { this.kind = kind; this.sz = sz; this.col = col; }
        @Override public int getIconWidth() { return sz; }
        @Override public int getIconHeight() { return sz; }

        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(col);
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.translate(x, y);
            int s = sz;
            switch (kind) {
                case "chat" -> {
                    g2.drawRoundRect(2, 3, s - 4, s - 7, 5, 5);
                    g2.drawLine(6, s - 4, 4, s - 1);
                    g2.drawLine(4, s - 1, 10, s - 4);
                }
                case "group" -> {
                    g2.drawOval(4, 3, 6, 6);
                    g2.drawArc(2, 10, 10, 7, 0, 180);
                    g2.drawOval(11, 5, 5, 5);
                    g2.drawArc(10, 11, 8, 6, 0, 180);
                }
                case "cloud" -> {
                    g2.drawArc(3, 6, 7, 7, 60, 200);
                    g2.drawArc(7, 4, 8, 8, 0, 180);
                    g2.drawArc(11, 7, 6, 6, -90, 180);
                    g2.drawLine(6, 13, 14, 13);
                }
                case "logout" -> {
                    g2.drawArc(4, 3, s - 8, s - 6, 125, 290);
                    g2.drawLine(s / 2, 2, s / 2, s / 2);
                }
                case "emoji" -> {
                    g2.drawOval(2, 2, s - 4, s - 4);
                    g2.fillOval(6, 7, 2, 2);
                    g2.fillOval(s - 8, 7, 2, 2);
                    g2.drawArc(6, 7, s - 12, s - 12, 200, 140);
                }
                case "image" -> {
                    g2.drawRoundRect(2, 3, s - 4, s - 6, 3, 3);
                    g2.fillOval(5, 6, 3, 3);
                    g2.drawPolyline(new int[]{3, 8, 11, 14, s - 3}, new int[]{s - 4, 10, 12, 8, s - 4}, 5);
                }
                case "attach" -> {
                    g2.drawArc(4, 3, 8, 8, 0, 180);
                    g2.drawLine(4, 7, 4, 13);
                    g2.drawArc(4, 10, 6, 6, 180, 180);
                    g2.drawLine(10, 13, 10, 6);
                    g2.drawArc(6, 5, 4, 4, 0, 180);
                }
                case "mic" -> {
                    g2.drawRoundRect(6, 2, 6, 9, 6, 6);
                    g2.drawArc(4, 6, 10, 8, 180, 180);
                    g2.drawLine(9, 14, 9, 17);
                    g2.drawLine(6, 17, 12, 17);
                }
                case "stop" -> g2.fillRoundRect(4, 4, s - 8, s - 8, 3, 3);
                case "shot" -> {
                    g2.drawRoundRect(2, 2, s - 4, s - 4, 3, 3);
                    g2.drawLine(6, 9, 12, 9);
                    g2.drawLine(9, 6, 9, 12);
                }
                case "folder" -> {
                    g2.drawPolyline(new int[]{2, 2, 6, 8, s - 2, s - 2, 2}, new int[]{s - 3, 3, 3, 5, 5, s - 3, s - 3}, 7);
                }
                case "download" -> {
                    g2.drawLine(s / 2, 2, s / 2, s - 5);
                    g2.drawPolyline(new int[]{s / 2 - 4, s / 2, s / 2 + 4}, new int[]{s - 9, s - 5, s - 9}, 3);
                    g2.drawLine(3, s - 2, s - 3, s - 2);
                }
                case "play" -> {
                    g2.setColor(new Color(0, 0, 0, 120));
                    g2.fillOval(0, 0, s, s);
                    g2.setColor(col);
                    g2.drawOval(0, 0, s - 1, s - 1);
                    int[] px = {s / 2 - 4, s / 2 + 6, s / 2 - 4};
                    int[] py = {s / 2 - 6, s / 2, s / 2 + 6};
                    g2.fillPolygon(px, py, 3);
                }
                case "bell" -> {
                    g2.drawArc(4, 3, s - 8, s - 7, 0, 180);
                    g2.drawLine(4, 7, 3, s - 5);
                    g2.drawLine(s - 4, 7, s - 3, s - 5);
                    g2.drawLine(2, s - 5, s - 2, s - 5);
                    g2.drawArc(s / 2 - 2, s - 5, 4, 3, 180, 180);
                }
                case "pin" -> {
                    g2.drawLine(5, 3, s - 5, 3);
                    g2.drawLine(6, 3, 5, 9);
                    g2.drawLine(s - 6, 3, s - 5, 9);
                    g2.drawLine(3, 9, s - 3, 9);
                    g2.drawLine(s / 2, 9, s / 2, s - 2);
                }
                case "check" -> {
                    g2.drawPolyline(new int[]{2, s / 2 - 1, s - 2}, new int[]{s / 2, s - 3, 3}, 3);
                }
            }
            g2.dispose();
        }
    }
}
