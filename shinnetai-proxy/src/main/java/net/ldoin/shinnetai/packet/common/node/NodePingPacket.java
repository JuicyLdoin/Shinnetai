package net.ldoin.shinnetai.packet.common.node;

import net.ldoin.shinnetai.ShinnetaiIOWorker;
import net.ldoin.shinnetai.buffered.buf.smart.ReadOnlySmartByteBuf;
import net.ldoin.shinnetai.buffered.buf.smart.WriteOnlySmartByteBuf;
import net.ldoin.shinnetai.cluster.node.ShinnetaiNodeClient;
import net.ldoin.shinnetai.cluster.node.registered.ShinnetaiNodeConnection;
import net.ldoin.shinnetai.cluster.node.registered.ShinnetaiRegisteredNode;
import net.ldoin.shinnetai.packet.AbstractPacket;
import net.ldoin.shinnetai.packet.registry.ShinnetaiPacket;

@ShinnetaiPacket(id = -5)
public class NodePingPacket extends AbstractPacket<ShinnetaiIOWorker<?>, ShinnetaiNodeConnection> {

    private int clients;

    public NodePingPacket() {
    }

    public NodePingPacket(int clients) {
        this.clients = clients;
    }

    @Override
    public void handleClient() {
        if (getClientWorker() instanceof ShinnetaiNodeClient nodeClient) {
            clients = nodeClient.getNode().getConnections().size();
        } else {
            throw new UnsupportedOperationException("Received NodePingPacket without cluster system");
        }
    }

    @Override
    public void handleServer() {
        ShinnetaiRegisteredNode node = getServerWorker().getNode();
        node.setCurrentClients(clients);
        node.setLastPing(System.currentTimeMillis());
    }

    @Override
    @SuppressWarnings("unchecked")
    public NodePingPacket response() {
        return new NodePingPacket(clients);
    }

    @Override
    public void read(ReadOnlySmartByteBuf buf) {
        clients = buf.readVarInt();
    }

    @Override
    public void write(WriteOnlySmartByteBuf buf) {
        buf.writeVarInt(clients);
    }
}