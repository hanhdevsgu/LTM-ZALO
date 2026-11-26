package com.zalo.server;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

/**
 * Tiện ích bảo mật băm mật khẩu chuẩn NIST (PBKDF2WithHmacSHA256)
 * Tuân thủ Slide 22: Lưu Password an toàn, không so sánh và không lưu plain text
 */
public class SecurityHelper {
    private static final int ITERATIONS = 10000;
    private static final int KEY_LENGTH = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Sinh salt ngẫu nhiên an toàn mật mã học
     */
    public static String generateSalt() {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    /**
     * Băm mật khẩu kèm Salt bằng PBKDF2
     */
    public static String hashPassword(String password, String salt) {
        try {
            char[] chars = password.toCharArray();
            byte[] saltBytes = Base64.getDecoder().decode(salt);
            PBEKeySpec spec = new PBEKeySpec(chars, saltBytes, ITERATIONS, KEY_LENGTH);
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = skf.generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException("Lỗi thuật toán băm mật khẩu: " + e.getMessage(), e);
        }
    }

    /**
     * So khớp mật khẩu nhập vào với mật khẩu băm đã lưu (chống Timing Attack)
     */
    public static boolean verifyPassword(String enteredPassword, String storedHash, String salt) {
        String enteredHash = hashPassword(enteredPassword, salt);
        return slowEquals(enteredHash.getBytes(), storedHash.getBytes());
    }

    private static boolean slowEquals(byte[] a, byte[] b) {
        int diff = a.length ^ b.length;
        for (int i = 0; i < a.length && i < b.length; i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }
}

