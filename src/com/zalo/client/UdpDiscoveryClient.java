package com.zalo.client;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

/**
 * UDP Broadcast Client
 * Gửi gói tin Broadcast để tự động tìm địa chỉ IP và Port của Zalo Server trong mạng LAN.
 */
public class UdpDiscoveryClient {

    /**
     * Trả về mảng [ip, port] nếu tìm thấy Server, hoặc null nếu hết thời gian chờ.
     */
    public static String[] discoverServer(int timeoutMs) {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true); // Bật chế độ gửi Broadcast
            socket.setSoTimeout(timeoutMs);

            byte[] sendData = "DISCOVER_ZALO_SERVER".getBytes(StandardCharsets.UTF_8);
            InetAddress broadcastIp = InetAddress.getByName("255.255.255.255");
            DatagramPacket sendPacket = new DatagramPacket(
                    sendData, sendData.length, broadcastIp, 5001);

            System.out.println("[UDP Client] Đang gửi Broadcast tìm Server...");
            socket.send(sendPacket);

            // Chờ phản hồi từ Server
            byte[] recvBuffer = new byte[1024];
            DatagramPacket recvPacket = new DatagramPacket(recvBuffer, recvBuffer.length);
            socket.receive(recvPacket);

            String reply = new String(recvPacket.getData(), 0, recvPacket.getLength(), StandardCharsets.UTF_8).trim();
            if (reply.startsWith("ZALO_SERVER:")) {
                String serverIp = recvPacket.getAddress().getHostAddress();
                String serverPort = reply.substring("ZALO_SERVER:".length());
                System.out.println("[UDP Client] Tìm thấy Server tại " + serverIp + ":" + serverPort);
                return new String[]{serverIp, serverPort};
            }
        } catch (Exception e) {
            System.out.println("[UDP Client] Không tìm thấy Server LAN: " + e.getMessage());
        }
        return null;
    }
}
