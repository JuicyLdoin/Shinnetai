package net.ldoin.shinnetai.statistic.server;

import net.ldoin.shinnetai.server.connection.ShinnetaiConnection;
import net.ldoin.shinnetai.statistic.ShinnetaiStatistic;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ShinnetaiServerStatistic implements ShinnetaiStatistic {

    private final Map<Integer, ShinnetaiConnectionStatistic> connectionStatistics = new ConcurrentHashMap<>();
    private final long startTime = System.currentTimeMillis();
    private final AtomicInteger allTimeConnected = new AtomicInteger();
    private final AtomicInteger inputPackets = new AtomicInteger();
    private final AtomicInteger outputPackets = new AtomicInteger();
    private final AtomicLong input = new AtomicLong();
    private final AtomicLong output = new AtomicLong();

    public void connect(ShinnetaiConnection<?> connection) {
        connectionStatistics.put(connection.getConnectionId(), connection.getStatistic());
        allTimeConnected.incrementAndGet();
    }

    public ShinnetaiConnectionStatistic getStatistic(ShinnetaiConnection<?> connection) {
        return connectionStatistics.get(connection.getConnectionId());
    }

    public int getAllTimeConnected() {
        return allTimeConnected.get();
    }

    @Override
    public int getInputPackets() {
        return inputPackets.get();
    }

    @Override
    public int getOutputPackets() {
        return outputPackets.get();
    }

    @Override
    public long getStartTime() {
        return startTime;
    }

    @Override
    public long getInputBytes() {
        return input.get();
    }

    @Override
    public long getOutputBytes() {
        return output.get();
    }

    @Override
    public void receive(byte[] bytes) {
        inputPackets.incrementAndGet();
        input.addAndGet(bytes.length);
    }

    @Override
    public void send(byte[] bytes) {
        outputPackets.incrementAndGet();
        output.addAndGet(bytes.length);
    }

    public ShinnetaiConnectionStatistic disconnect(int id) {
        return connectionStatistics.remove(id);
    }
}