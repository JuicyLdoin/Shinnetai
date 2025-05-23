package net.ldoin.shinnetai.statistic.server;

import net.ldoin.shinnetai.statistic.ShinnetaiStatistic;

public class ShinnetaiConnectionStatistic implements ShinnetaiStatistic {

    private final ShinnetaiServerStatistic serverStatistic;
    private final long startTime = System.currentTimeMillis();
    private int inputPackets;
    private int outputPackets;
    private long input;
    private long output;

    public ShinnetaiConnectionStatistic(ShinnetaiServerStatistic serverStatistic) {
        this.serverStatistic = serverStatistic;
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
        serverStatistic.receive(bytes);
    }

    @Override
    public void send(byte[] bytes) {
        outputPackets++;
        output += bytes.length;
        serverStatistic.send(bytes);
    }
}