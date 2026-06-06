package net.ldoin.shinnetai.statistic;

public interface ShinnetaiStatistic {

    int getInputPackets();

    int getOutputPackets();

    default double getAverageInputPacketSize() {
        return round((double) getInputBytes() / getInputPackets());
    }

    default double getAverageOutputPacketSize() {
        return round((double) getOutputBytes() / getOutputPackets());
    }

    long getStartTime();

    default long getTimeAlive() {
        return System.currentTimeMillis() - getStartTime();
    }

    long getInputBytes();

    default double getInputKiloBytes() {
        return round((double) getInputBytes() / 1024D);
    }

    default double getInputMegaBytes() {
        return round(getInputKiloBytes() / 1024D);
    }

    long getOutputBytes();

    default double getOutputKiloBytes() {
        return round(getOutputBytes() / 1024D);
    }

    default double getOutputMegaBytes() {
        return round(getOutputKiloBytes() / 1024D);
    }

    void receive(byte[] bytes);

    void send(byte[] bytes);

    default double round(double val) {
        return Math.round(val * 100.0) / 100.0;
    }
}