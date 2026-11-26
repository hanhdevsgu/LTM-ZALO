package com.zalo.client;

import javax.net.ssl.*;
import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

/**
 * Tiện ích cấu hình xác thực chứng chỉ Server phía Client
 * Xử lý bảo mật & kết nối: KIỂM TRA CHỨNG CHỈ SERVER (JAVA)
 * - Nạp chứng chỉ công khai certificates/server.crt vào TrustStore tạm thời của Client
 * - Hỗ trợ bắt tay TLS an toàn mà không cần cài đặt CA vào hệ điều hành
 */
public class TrustManagerHelper {
    public static final String CERT_PATH = "certificates/server.crt";

    public static SSLSocketFactory createClientSocketFactory(boolean verifyWithCert) throws Exception {
        SSLContext sslContext = SSLContext.getInstance("TLS");

        if (verifyWithCert) {
            File certFile = new File(CERT_PATH);
            if (certFile.exists()) {
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                X509Certificate caCert;
                try (FileInputStream fis = new FileInputStream(certFile)) {
                    caCert = (X509Certificate) cf.generateCertificate(fis);
                }

                KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
                trustStore.load(null, null);
                trustStore.setCertificateEntry("server-cert", caCert);

                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(trustStore);

                sslContext.init(null, tmf.getTrustManagers(), null);
                return sslContext.getSocketFactory();
            }
        }

        // Chế độ dự phòng cho môi trường Lab/Dev khi chưa copy chứng chỉ
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }

                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    }

                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    }
                }
        };

        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
        return sslContext.getSocketFactory();
    }
}

