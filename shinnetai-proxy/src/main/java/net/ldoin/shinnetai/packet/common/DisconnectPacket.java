package net.ldoin.shinnetai.packet.common;

import net.ldoin.shinnetai.buffered.buf.smart.ReadOnlySmartByteBuf;
import net.ldoin.shinnetai.buffered.buf.smart.WriteOnlySmartByteBuf;
import net.ldoin.shinnetai.client.ShinnetaiClient;
import net.ldoin.shinnetai.packet.AbstractPacket;
import net.ldoin.shinnetai.packet.registry.ShinnetaiPacket;
import net.ldoin.shinnetai.packet.serializer.BinaryPacketSerializer;
import net.ldoin.shinnetai.packet.serializer.PacketSerializer;
import net.ldoin.shinnetai.server.connection.ShinnetaiConnection;

@ShinnetaiPacket(id = -5)
public class DisconnectPacket extends AbstractPacket<ShinnetaiClient, ShinnetaiConnection<?>> {

    private int connection = 0;
    private String reason = "";

    public DisconnectPacket() {
    }

    public DisconnectPacket(int connection) {
        this.connection = connection;
    }

    public DisconnectPacket(int connection, String reason) {
        this.connection = connection;
        this.reason = reason != null ? reason : "";
    }

    public int getConnectionId() {
        return connection;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public void handleClient() {
        getClientWorker().closeClient(true);
        String msg = reason.isEmpty() ? "Disconnected" : "Disconnected: " + reason;
        getClientWorker().getLogger().info(msg);
    }

    @Override
    public void handleServer() {
        getServerWorker().close(true);
    }

    @Override
    public void read(ReadOnlySmartByteBuf buf) {
        connection = buf.readVarInt();
        reason = buf.readString();
    }

    @Override
    public void write(WriteOnlySmartByteBuf buf) {
        buf.writeVarInt(connection);
        buf.writeString(reason);
    }

    @Override
    public PacketSerializer serializer() {
        return BinaryPacketSerializer.INSTANCE;
    }
}