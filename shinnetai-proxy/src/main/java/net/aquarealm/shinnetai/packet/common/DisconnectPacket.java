package net.aquarealm.shinnetai.packet.common;

import net.aquarealm.shinnetai.buffered.buf.smart.ReadOnlySmartByteBuf;
import net.aquarealm.shinnetai.buffered.buf.smart.WriteOnlySmartByteBuf;
import net.aquarealm.shinnetai.client.ShinnetaiClient;
import net.aquarealm.shinnetai.packet.AbstractPacket;
import net.aquarealm.shinnetai.packet.registry.ShinnetaiPacket;
import net.aquarealm.shinnetai.server.connection.ShinnetaiConnection;

@ShinnetaiPacket(id = 1)
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
    }

    @Override
    public void handleServer() {
        getServerWorker().closeConnection(true);
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