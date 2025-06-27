package net.ldoin.shinnetai.packet.extended;

import net.ldoin.shinnetai.buffered.buf.smart.ReadOnlySmartByteBuf;
import net.ldoin.shinnetai.buffered.buf.smart.WriteOnlySmartByteBuf;
import net.ldoin.shinnetai.client.ShinnetaiClient;
import net.ldoin.shinnetai.packet.registry.ShinnetaiPacket;
import net.ldoin.shinnetai.packet.side.ClientSidePacket;

import java.io.IOException;
import java.net.Socket;

@ShinnetaiPacket(id = -20)
public class RedirectPacket extends ClientSidePacket<ShinnetaiClient> {

    private String clusterGroup;
    private String address;
    private int port;

    public RedirectPacket() {
    }

    public RedirectPacket(String clusterGroup, Socket socket) {
        this(clusterGroup, socket.getInetAddress().getHostAddress(), socket.getPort());
    }

    public RedirectPacket(String clusterGroup, String address, int port) {
        this.clusterGroup = clusterGroup;
        this.address = address;
        this.port = port;
    }

    @Override
    public void handleClient() {
        try {
            getClientWorker().redirect(clusterGroup, address, port);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void read(ReadOnlySmartByteBuf buf) {
        clusterGroup = buf.readString();
        address = buf.readString();
        port = buf.readVarInt();
    }

    @Override
    public void write(WriteOnlySmartByteBuf buf) {
        buf.writeString(clusterGroup);
        buf.writeString(address);
        buf.writeVarInt(port);
    }
}