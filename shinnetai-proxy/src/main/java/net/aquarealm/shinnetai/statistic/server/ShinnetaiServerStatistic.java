package net.aquarealm.shinnetai.statistic.server;

import net.aquarealm.shinnetai.server.connection.ShinnetaiConnection;
import net.aquarealm.shinnetai.statistic.ShinnetaiStatistic;

import java.util.HashMap;
import java.util.Map;

public class ShinnetaiServerStatistic implements ShinnetaiStatistic {

    private final Map<Integer, ShinnetaiConnectionStatistic> connectionStatistics = new HashMap<>();
    private final long startTime = System.currentTimeMillis();
    private int allTimeConnected;
    private int inputPackets;
    private int outputPackets;
    private long input;
    private long output;

    public void connect(ShinnetaiConnection<?> connection) {
        connectionStatistics.put(connection.getConnectionId(), connection.getStatistic());
        allTimeConnected++;
    }

    public int getAllTimeConnected() {
        return allTimeConnected;
    }

    @Override
    public int getInputPackets() {
        return inputPackets;
    }

    @Override
    public int getOutputPackets() {
        return outputPackets;
    }

    @Override
    public long getStartTime() {
        return startTime;
    }

    @Override
    public long getInputBytes() {
        return input;
    }

    @Override
    public long getOutputBytes() {
        return output;
    }

    @Override
    public void receive(byte[] bytes) {
        inputPackets++;
        input += bytes.length;
    }

    @Override
    public void send(byte[] bytes) {
        outputPackets++;
        output += bytes.length;
    }

    public ShinnetaiConnectionStatistic disconnect(int id) {
        return connectionStatistics.remove(id);
    }
}