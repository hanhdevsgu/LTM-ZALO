package com.zalo.client;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Chương 10: Nhận Multicast liên tục (Java)
 * Client tham gia nhóm 239.1.1.1:8000 để nhận thông báo chung từ Server.
 */
public class MulticastReceiverTask implements Runnable {
    private final Consumer<String> onNotice;
    private MulticastSocket socket;
    private volatile boolean running = true;

    public MulticastReceiverTask(Consumer<String> onNotice) {
        this.onNotice = onNotice;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void run() {
        try {
            socket = new MulticastSocket(8000);
            InetAddress group = InetAddress.getByName("239.1.1.1");
            socket.joinGroup(group); // Tham gia nhóm Multicast
            System.out.println("[Multicast Client] Đã tham gia nhóm 239.1.1.1:8000");

            byte[] buffer = new byte[2048];
            while (running && !socket.isClosed()) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet); // Chờ nhận Datagram Multicast

                String msg = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                if (onNotice != null) {
                    onNotice.accept(msg);
                }
            }
        } catch (Exception e) {
            if (running) {
                System.out.println("[Multicast Client] Đã dừng nhận Multicast: " + e.getMessage());
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
