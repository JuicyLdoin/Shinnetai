package net.ldoin.shinnetai.resilience;

public class RetryPolicy {

    public static Builder builder() {
        return new Builder();
    }

    public static RetryPolicy once() {
        return builder().maxAttempts(1).initialDelayMs(0).build();
    }

    public static RetryPolicy fixed(int attempts, long delayMs) {
        return builder().maxAttempts(attempts).initialDelayMs(delayMs).backoffMultiplier(1.0).build();
    }

    public static RetryPolicy exponential(int attempts, long initialDelayMs, long maxDelayMs) {
        return builder().maxAttempts(attempts).initialDelayMs(initialDelayMs)
                .backoffMultiplier(2.0).maxDelayMs(maxDelayMs).build();
    }

    private final int maxAttempts;
    private final long initialDelayMs;
    private final double backoffMultiplier;
    private final long maxDelayMs;

    private RetryPolicy(Builder builder) {
        this.maxAttempts = builder.maxAttempts;
        this.initialDelayMs = builder.initialDelayMs;
        this.backoffMultiplier = builder.backoffMultiplier;
        this.maxDelayMs = builder.maxDelayMs;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public long getInitialDelayMs() {
        return initialDelayMs;
    }

    public double getBackoffMultiplier() {
        return backoffMultiplier;
    }

    public long getMaxDelayMs() {
        return maxDelayMs;
    }

    public long delayForAttempt(int attempt) {
        if (initialDelayMs <= 0) {
            return 0;
        }

        double delay = initialDelayMs * Math.pow(backoffMultiplier, attempt);
        return maxDelayMs > 0 ? (long) Math.min(delay, maxDelayMs) : (long) delay;
    }

    public static class Builder {

        private int maxAttempts = 3;
        private long initialDelayMs = 200;
        private double backoffMultiplier = 2.0;
        private long maxDelayMs = 5000;

        public Builder maxAttempts(int maxAttempts) {
            if (maxAttempts < 1) {
                throw new IllegalArgumentException("maxAttempts must be >= 1");
            }

            this.maxAttempts = maxAttempts;
            return this;
        }

        public Builder initialDelayMs(long initialDelayMs) {
            this.initialDelayMs = initialDelayMs;
            return this;
        }

        public Builder backoffMultiplier(double backoffMultiplier) {
            if (backoffMultiplier < 1.0) {
                throw new IllegalArgumentException("backoffMultiplier must be >= 1.0");
            }

            this.backoffMultiplier = backoffMultiplier;
            return this;
        }

        public Builder maxDelayMs(long maxDelayMs) {
            this.maxDelayMs = maxDelayMs;
            return this;
        }

        public RetryPolicy build() {
            return new RetryPolicy(this);
        }
    }
}