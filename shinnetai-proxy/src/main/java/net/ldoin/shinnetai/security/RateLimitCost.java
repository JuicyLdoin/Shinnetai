package net.ldoin.shinnetai.security;

public record RateLimitCost(long packetTokens, long byteTokens) {

    private static final RateLimitCost NONE = new RateLimitCost(0, 0);

    public RateLimitCost {
        if (packetTokens < 0) {
            throw new IllegalArgumentException("packetTokens must be >= 0");
        }

        if (byteTokens < 0) {
            throw new IllegalArgumentException("byteTokens must be >= 0");
        }
    }

    public static RateLimitCost none() {
        return NONE;
    }

    public static RateLimitCost of(long packetTokens, long byteTokens) {
        return packetTokens == 0 && byteTokens == 0 ? NONE : new RateLimitCost(packetTokens, byteTokens);
    }

    public static RateLimitCost packets(long packetTokens) {
        return of(packetTokens, 0);
    }

    public static RateLimitCost bytes(long byteTokens) {
        return of(0, byteTokens);
    }

    public boolean isFree() {
        return packetTokens == 0 && byteTokens == 0;
    }
}