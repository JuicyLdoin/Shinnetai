package net.ldoin.shinnetai.cluster.node.registered;

import net.ldoin.shinnetai.ConnectionType;
import net.ldoin.shinnetai.cluster.ShinnetaiCluster;
import net.ldoin.shinnetai.packet.registry.PacketRegistry;
import net.ldoin.shinnetai.server.connection.ShinnetaiConnection;
import net.ldoin.shinnetai.statistic.server.ShinnetaiConnectionStatistic;
import net.ldoin.shinnetai.worker.options.WorkerOptions;

import java.io.IOException;
import java.net.Socket;
import java.util.logging.Logger;

public class ShinnetaiNodeConnection extends ShinnetaiConnection<ShinnetaiCluster<?>> {

    private final ShinnetaiRegisteredNode node;
    private final ConnectionType connectionType;

    public ShinnetaiNodeConnection(ShinnetaiCluster<?> server, int connectionId, PacketRegistry registry, ShinnetaiConnectionStatistic statistic, Socket socket, ShinnetaiRegisteredNode node, ConnectionType connectionType, WorkerOptions options) throws IOException {
        super(server, connectionId, registry, statistic, socket, Logger.getLogger("Cluster Node[" + node.getGroup() + "] (" + node.getAddress() + ":" + node.getPort() + ")"), options);
        this.node = node;
        this.connectionType = connectionType;
    }

    public ShinnetaiRegisteredNode getNode() {
        return node;
    }

    public ConnectionType getConnectionType() {
        return connectionType;
    }
}