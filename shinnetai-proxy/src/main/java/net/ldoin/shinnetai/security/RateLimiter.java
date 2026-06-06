package net.ldoin.shinnetai.security;

public class RateLimiter {

    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private final long maxPacketsPerSecond;
    private final long maxBytesPerSecond;

    private long packetTokens;
    private long byteTokens;
    private long lastRefillNanos;

    public RateLimiter(long maxPacketsPerSecond, long maxBytesPerSecond) {
        this.maxPacketsPerSecond = maxPacketsPerSecond;
        this.maxBytesPerSecond = maxBytesPerSecond;
        this.packetTokens = maxPacketsPerSecond;
        this.byteTokens = maxBytesPerSecond;
        this.lastRefillNanos = System.nanoTime();
    }

    public synchronized boolean tryAcquire(int bytes) {
        if (bytes < 0) {
            throw new IllegalArgumentException("bytes must be >= 0");
        }

        return tryAcquire(1, bytes);
    }

    public synchronized boolean tryAcquire(RateLimitCost cost) {
        if (cost == null || cost.isFree()) {
            return true;
        }

        return tryAcquire(cost.packetTokens(), cost.byteTokens());
    }

    public synchronized boolean tryAcquire(long packetTokensRequired, long byteTokensRequired) {
        if (packetTokensRequired < 0) {
            throw new IllegalArgumentException("packetTokensRequired must be >= 0");
        }

        if (byteTokensRequired < 0) {
            throw new IllegalArgumentException("byteTokensRequired must be >= 0");
        }

        refill();
        long currentPackets = packetTokens;
        if (maxPacketsPerSecond > 0 && currentPackets < packetTokensRequired) {
            return false;
        }

        long currentBytes = byteTokens;
        if (maxBytesPerSecond > 0 && currentBytes < byteTokensRequired) {
            return false;
        }

        if (maxPacketsPerSecond > 0) {
            packetTokens = currentPackets - packetTokensRequired;
        }

        if (maxBytesPerSecond > 0) {
            byteTokens = currentBytes - byteTokensRequired;
        }

        return true;
    }

    private void refill() {
        long now = System.nanoTime();
        long elapsed = now - lastRefillNanos;
        if (elapsed < NANOS_PER_SECOND) {
            return;
        }

        long seconds = elapsed / NANOS_PER_SECOND;
        lastRefillNanos = now - (elapsed % NANOS_PER_SECOND);
        if (maxPacketsPerSecond > 0) {
            long add = seconds * maxPacketsPerSecond;
            packetTokens = Math.min(packetTokens + add, maxPacketsPerSecond);
        }

        if (maxBytesPerSecond > 0) {
            long add = seconds * maxBytesPerSecond;
            byteTokens = Math.min(byteTokens + add, maxBytesPerSecond);
        }
    }

    public long getMaxPacketsPerSecond() {
        return maxPacketsPerSecond;
    }

    public long getMaxBytesPerSecond() {
        return maxBytesPerSecond;
    }

    public RateLimiter copy() {
        return new RateLimiter(maxPacketsPerSecond, maxBytesPerSecond);
    }
}