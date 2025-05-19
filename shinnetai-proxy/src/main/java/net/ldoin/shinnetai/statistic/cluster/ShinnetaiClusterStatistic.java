package net.ldoin.shinnetai.statistic.cluster;

import net.ldoin.shinnetai.cluster.node.registered.ShinnetaiNodeConnection;
import net.ldoin.shinnetai.cluster.node.registered.ShinnetaiRegisteredNode;
import net.ldoin.shinnetai.statistic.ShinnetaiStatistic;

public class ShinnetaiClusterStatistic implements ShinnetaiStatistic {

    private final long startTime = System.currentTimeMillis();
    private int allTimeConnectedNodes;
    private int allTimeConnectedClients;

    public void connectNode(ShinnetaiRegisteredNode node) {
        allTimeConnectedNodes++;
    }

    public void connectClient(ShinnetaiNodeConnection connection) {
        allTimeConnectedClients++;
    }

    public int getAllTimeConnectedNodes() {
        return allTimeConnectedNodes;
    }

    public int getAllTimeConnectedClients() {
        return allTimeConnectedClients;
    }

    @Override
    public int getInputPackets() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getOutputPackets() {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getStartTime() {
        return startTime;
    }

    @Override
    public long getInputBytes() {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getOutputBytes() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void receive(byte[] bytes) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void send(byte[] bytes) {
        throw new UnsupportedOperationException();
    }
}