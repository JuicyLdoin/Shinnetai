package net.aquarealm.shinnetai.statistic.client;

import net.aquarealm.shinnetai.statistic.ShinnetaiStatistic;

public class ShinnetaiClientStatistic implements ShinnetaiStatistic {

    private final long startTime = System.currentTimeMillis();
    private int inputPackets;
    private int outputPackets;
    private long input;
    private long output;

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
}