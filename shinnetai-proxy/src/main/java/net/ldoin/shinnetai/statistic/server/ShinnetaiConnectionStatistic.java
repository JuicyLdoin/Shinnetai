package net.ldoin.shinnetai.statistic.server;

import net.ldoin.shinnetai.statistic.ShinnetaiStatistic;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ShinnetaiConnectionStatistic implements ShinnetaiStatistic {

    private final ShinnetaiServerStatistic serverStatistic;
    private final long startTime = System.currentTimeMillis();
    private final AtomicInteger inputPackets = new AtomicInteger();
    private final AtomicInteger outputPackets = new AtomicInteger();
    private final AtomicLong input = new AtomicLong();
    private final AtomicLong output = new AtomicLong();

    public ShinnetaiConnectionStatistic(ShinnetaiServerStatistic serverStatistic) {
        this.serverStatistic = serverStatistic;
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
        serverStatistic.receive(bytes);
    }

    @Override
    public void send(byte[] bytes) {
        outputPackets.incrementAndGet();
        output.addAndGet(bytes.length);
        serverStatistic.send(bytes);
    }
}