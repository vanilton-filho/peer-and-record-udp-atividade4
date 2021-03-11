package peerudp.peer;

import java.net.DatagramPacket;
import java.net.InetAddress;

public class DataRequest {
    private InetAddress address;
    private int port;
    private DatagramPacket packet;

    public DataRequest(DatagramPacket packet) {
        this.packet = packet;
        this.address = packet.getAddress();
        this.port = packet.getPort();
    }

    public InetAddress getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    public String getHostAddress() {
        return address.getHostAddress();
    }

    public String getHostName() {
        return address.getHostName();
    }

    public String getData() {
        return new String(packet.getData(), 0, packet.getLength());
    }
}