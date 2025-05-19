package net.ldoin.shinnetai.packet.common;

import net.ldoin.shinnetai.buffered.buf.smart.ReadOnlySmartByteBuf;
import net.ldoin.shinnetai.buffered.buf.smart.WriteOnlySmartByteBuf;
import net.ldoin.shinnetai.client.ShinnetaiClient;
import net.ldoin.shinnetai.packet.AbstractPacket;
import net.ldoin.shinnetai.packet.registry.ShinnetaiPacket;
import net.ldoin.shinnetai.server.connection.ShinnetaiConnection;

@ShinnetaiPacket(id = -2)
public class DisconnectPacket extends AbstractPacket<ShinnetaiClient, ShinnetaiConnection<?>> {

    private int connection = 0;

    public DisconnectPacket() {
    }

    public DisconnectPacket(int connection) {
        this.connection = connection;
    }

    public int getConnectionId() {
        return connection;
    }

    @Override
    public void handleClient() {
        getClientWorker().closeClient(true);
        getClientWorker().getLogger().info("Disconnect: Unconnected");
    }

    @Override
    public void handleServer() {
        getServerWorker().close(true);
    }

    @Override
    public void read(ReadOnlySmartByteBuf buf) {
        connection = buf.readVarInt();
    }

    @Override
    public void write(WriteOnlySmartByteBuf buf) {
        buf.writeVarInt(connection);
    }
}