package com.zalo.server;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;

/**
 * UDP Datagram & UDP Broadcast
 * Server lắng nghe gói tin UDP ở cổng 5001 để Client trong mạng LAN tự tìm thấy IP/Port.
 */
public class UdpDiscoveryServer implements Runnable {
    public static final int DISCOVERY_PORT = 5001;
    private final int tcpPort;
    private DatagramSocket socket;
    private volatile boolean running = true;

    public UdpDiscoveryServer(int tcpPort) {
        this.tcpPort = tcpPort;
    }

    @Override
    public void run() {
        try {
            // Mở cổng UDP 5001 để nhận gói tin tìm kiếm từ Client
            socket = new DatagramSocket(DISCOVERY_PORT);
            System.out.println("[UDP Discovery] Đang lắng nghe tại cổng UDP " + DISCOVERY_PORT);

            byte[] buffer = new byte[1024];
            while (running && !socket.isClosed()) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet); // Chờ nhận Datagram từ Client

                String msg = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8).trim();
                String clientIp = packet.getAddress().getHostAddress();
                int clientPort = packet.getPort();

                // Nếu đúng yêu cầu tìm Server Zalo -> trả về cổng TCP đang chạy
                if ("DISCOVER_ZALO_SERVER".equals(msg)) {
                    System.out.println("[UDP Discovery] Nhận yêu cầu từ " + clientIp + ":" + clientPort);
                    String reply = "ZALO_SERVER:" + tcpPort;
                    byte[] replyBytes = reply.getBytes(StandardCharsets.UTF_8);

                    DatagramPacket replyPacket = new DatagramPacket(
                            replyBytes, replyBytes.length, packet.getAddress(), clientPort);
                    socket.send(replyPacket);
                }
            }
        } catch (Exception e) {
            if (running) {
                System.err.println("[UDP Discovery] Lỗi: " + e.getMessage());
            }
        }
    }

    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }
}
