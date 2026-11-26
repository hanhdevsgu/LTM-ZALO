package com.zalo.server;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

/**
 * UDP Multicast (Phía gửi thông báo từ Server)
 * Gửi thông báo toàn hệ thống tới nhóm Multicast 239.1.1.1:8000.
 */
public class MulticastBroadcaster {
    public static final String GROUP_IP = "239.1.1.1";
    public static final int GROUP_PORT = 8000;

    public static void sendNotice(String text) {
        if (text == null || text.trim().isEmpty()) return;
        try (DatagramSocket socket = new DatagramSocket()) {
            byte[] data = text.trim().getBytes(StandardCharsets.UTF_8);
            InetAddress group = InetAddress.getByName(GROUP_IP);
            DatagramPacket packet = new DatagramPacket(data, data.length, group, GROUP_PORT);
            socket.send(packet);
            System.out.println("[Multicast] Đã phát thông báo tới " + GROUP_IP + ":" + GROUP_PORT + " -> " + text);
        } catch (Exception e) {
            System.err.println("[Multicast] Lỗi gửi thông báo: " + e.getMessage());
        }
    }
}
