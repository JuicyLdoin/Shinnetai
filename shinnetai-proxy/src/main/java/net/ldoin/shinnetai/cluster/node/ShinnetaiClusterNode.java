package net.ldoin.shinnetai.cluster.node;

import net.ldoin.shinnetai.cluster.node.options.ClusterNodeOptions;
import net.ldoin.shinnetai.cluster.node.registered.ShinnetaiNodeConnection;
import net.ldoin.shinnetai.packet.common.DisconnectPacket;
import net.ldoin.shinnetai.packet.registry.PacketRegistry;
import net.ldoin.shinnetai.server.ShinnetaiServer;
import net.ldoin.shinnetai.server.options.ServerOptions;

import java.io.IOException;
import java.util.logging.Logger;

public class ShinnetaiClusterNode<C extends ShinnetaiNodeConnection> extends ShinnetaiServer<C> {

    private final ServerOptions options;
    private final ShinnetaiNodeClient clusterClient;

    public ShinnetaiClusterNode(ServerOptions options, ClusterNodeOptions clientOptions) throws IOException {
        this(PacketRegistry.getCommons(), options, clientOptions);
    }

    public ShinnetaiClusterNode(PacketRegistry registry, ServerOptions options, ClusterNodeOptions clientOptions) throws IOException {
        super(registry, options, Logger.getLogger("Node[" + clientOptions.getGroup() + "] (" + options.getPort() + ")"));
        this.options = options;
        this.clusterClient = new ShinnetaiNodeClient(clientOptions, this);
    }

    public ServerOptions getOptions() {
        return options;
    }

    public ShinnetaiNodeClient getClusterClient() {
        return clusterClient;
    }

    @Override
    public synchronized void start() {
        clusterClient.start();
        super.start();
    }

    @Override
    public synchronized void close() {
        try {
            clusterClient.sendPacket(new DisconnectPacket());
            Thread.sleep(100);

            clusterClient.closeClient(true);
            super.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}