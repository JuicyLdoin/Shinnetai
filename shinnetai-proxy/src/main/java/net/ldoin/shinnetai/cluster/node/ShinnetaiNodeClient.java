package net.ldoin.shinnetai.cluster.node;

import net.ldoin.shinnetai.ConnectionType;
import net.ldoin.shinnetai.client.ShinnetaiClient;
import net.ldoin.shinnetai.client.options.ClientOptions;
import net.ldoin.shinnetai.cluster.node.options.ClusterNodeOptions;
import net.ldoin.shinnetai.exception.ShinnetaiExceptions;
import net.ldoin.shinnetai.packet.common.RedirectPacket;
import net.ldoin.shinnetai.packet.registry.PacketRegistry;
import net.ldoin.shinnetai.server.connection.ShinnetaiConnection;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ShinnetaiNodeClient extends ShinnetaiClient {

    private final ClusterNodeOptions options;
    private final ShinnetaiClusterNode<?> node;

    public ShinnetaiNodeClient(ClusterNodeOptions options, ShinnetaiClusterNode<?> node) throws IOException {
        this(PacketRegistry.getCommons(), options, node);
    }

    public ShinnetaiNodeClient(PacketRegistry registry, ClusterNodeOptions options, ShinnetaiClusterNode<?> node) throws IOException {
        super(registry, ClientOptions.builder(options.getAddress(), options.getPort()).build(), ConnectionType.NODE, Logger.getLogger("Client Node[" + options.getGroup() + "] (" + options.getPort() + ")"));
        this.options = options;
        this.node = node;

        this.startData.writeVarInt(node.getOptions().getPort());
        this.startData.writeString(options.getGroup());
        this.startData.writeVarInt(options.getMaxConnections());
    }

    public ShinnetaiClusterNode<?> getNode() {
        return node;
    }

    @Override
    public synchronized void closeClient(boolean packet) {
        super.closeClient(packet);

        for (ShinnetaiConnection<?> connection : node.getConnections()) {
            try {
                connection.sendPacket(new RedirectPacket(options.getGroup(), getSocket()));
            } catch (IOException e) {
                getLogger().log(Level.WARNING, "Cannot redirect connection", e);
                try {
                    connection.sendException(ShinnetaiExceptions.FAILED_REDIRECT_TO_NODE);
                } catch (IOException ex) {
                    getLogger().log(Level.WARNING, "Cannot send exception", e);
                } finally {
                    connection.close();
                }
            }
        }
    }
}