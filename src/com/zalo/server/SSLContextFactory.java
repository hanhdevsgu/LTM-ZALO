package com.zalo.server;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;

/**
 * Khởi tạo SSLContext và cấu hình bảo mật TLS cho Server
 * Tuân thủ Slide 12, Slide 16 và Slide 18:
 * - Nạp chứng chỉ từ certificates/server.p12 (PKCS12 Keystore).
 * - Ép buộc giao thức mạnh TLS 1.3 và TLS 1.2, chặn SSL 2.0/3.0, TLS 1.0/1.1.
 */
public class SSLContextFactory {
    public static final String KEYSTORE_PATH = "certificates/server.p12";
    public static final String KEYSTORE_PASS = "LabOnly_ChangeMe";

    public static SSLContext createServerSSLContext() throws Exception {
        File keystoreFile = new File(KEYSTORE_PATH);
        if (!keystoreFile.exists()) {
            throw new IllegalStateException("Không tìm thấy file Keystore tại: " + keystoreFile.getAbsolutePath() +
                    "\nHãy chạy generate_certs.bat trước khi khởi động server!");
        }

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream(keystoreFile)) {
            keyStore.load(fis, KEYSTORE_PASS.toCharArray());
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, KEYSTORE_PASS.toCharArray());

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), null, null);

        return sslContext;
    }

    public static void configureServerSocket(SSLServerSocket serverSocket) {
        // Chỉ cho phép TLS 1.2 và TLS 1.3 như Slide 18
        serverSocket.setEnabledProtocols(new String[]{"TLSv1.3", "TLSv1.2"});
        serverSocket.setNeedClientAuth(false); // Không bắt buộc client cert cho đồ án chat
    }
}

