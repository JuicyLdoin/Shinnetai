package net.ldoin.shinnetai.delivery;

public final class PacketDeliveryState {

    private long firstSentMs;
    private long lastSentMs;
    private int attempts;

    public long getFirstSentMs() {
        return firstSentMs;
    }

    public long getLastSentMs() {
        return lastSentMs;
    }

    public int getAttempts() {
        return attempts;
    }

    public void markSent(long now) {
        if (firstSentMs == 0) {
            firstSentMs = now;
        }
        
        this.lastSentMs = now;
        this.attempts++;
    }
}