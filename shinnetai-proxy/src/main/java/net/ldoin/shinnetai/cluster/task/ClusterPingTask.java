package net.ldoin.shinnetai.cluster.task;

import net.ldoin.shinnetai.ConnectionType;
import net.ldoin.shinnetai.cluster.ShinnetaiCluster;
import net.ldoin.shinnetai.cluster.node.registered.ShinnetaiNodeConnection;
import net.ldoin.shinnetai.packet.extended.node.NodePingPacket;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

public class ClusterPingTask<C extends ShinnetaiNodeConnection> implements Runnable {

    private final ShinnetaiCluster<C> cluster;
    private final Set<C> pinging;

    public ClusterPingTask(ShinnetaiCluster<C> cluster) {
        this.cluster = cluster;
        this.pinging = new HashSet<>();
    }

    @Override
    public void run() {
        if (!cluster.isRunning()) {
            return;
        }

        try {
            for (C node : cluster.getConnections()) {
                if (!node.isRunning() || node.getConnectionType() == ConnectionType.CLIENT) {
                    continue;
                }

                if (pinging.add(node)) {
                    node.sendAsyncWithResponse(new NodePingPacket(), cluster.getOptions().getServerTimeout())
                            .whenComplete((packet, throwable) -> pinging.remove(node));
                    Thread.sleep(10);
                } else if (System.currentTimeMillis() - node.getNode().getLastPing() >= cluster.getOptions().getServerTimeout()) {
                    node.close();
                    cluster.disconnect(node);
                    pinging.remove(node);
                }
            }
        } catch (Exception exception) {
            cluster.getLogger().log(Level.WARNING, "Error when ping nodes", exception);
        }
    }
}