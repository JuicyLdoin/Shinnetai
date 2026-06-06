package net.ldoin.shinnetai.resilience;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class CircuitBreaker {

    public enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    private final int failureThreshold;
    private final long openDurationMs;
    private final int halfOpenMaxCalls;

    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger halfOpenCallCount = new AtomicInteger(0);
    private final AtomicInteger halfOpenSuccessCount = new AtomicInteger(0);
    private final AtomicLong openedAt = new AtomicLong(0);
    private volatile Consumer<State> onStateChange;

    private CircuitBreaker(Builder builder) {
        this.failureThreshold = builder.failureThreshold;
        this.openDurationMs = builder.openDurationMs;
        this.halfOpenMaxCalls = builder.halfOpenMaxCalls;
        this.onStateChange = builder.onStateChange;
    }

    public static Builder builder() {
        return new Builder();
    }

    public State getState() {
        checkTransition();
        return state.get();
    }

    public boolean isCallAllowed() {
        checkTransition();
        State current = state.get();
        return switch (current) {
            case CLOSED -> true;
            case OPEN -> false;
            case HALF_OPEN -> halfOpenCallCount.get() < halfOpenMaxCalls;
        };
    }

    public void recordSuccess() {
        State current = state.get();
        if (current == State.HALF_OPEN) {
            halfOpenCallCount.incrementAndGet();
            if (halfOpenSuccessCount.incrementAndGet() >= halfOpenMaxCalls) {
                transition(State.CLOSED);
            }
        } else {
            failureCount.set(0);
        }
    }

    public void recordFailure() {
        State current = state.get();
        if (current == State.HALF_OPEN) {
            transition(State.OPEN);
        } else if (current == State.CLOSED) {
            if (failureCount.incrementAndGet() >= failureThreshold) {
                transition(State.OPEN);
            }
        }
    }

    public void reset() {
        failureCount.set(0);
        halfOpenCallCount.set(0);
        halfOpenSuccessCount.set(0);
        openedAt.set(0);
        state.set(State.CLOSED);
    }

    public void setOnStateChange(Consumer<State> listener) {
        this.onStateChange = listener;
    }

    private void checkTransition() {
        if (state.get() == State.OPEN) {
            long opened = openedAt.get();
            if (opened > 0 && System.currentTimeMillis() - opened >= openDurationMs) {
                transition(State.HALF_OPEN);
            }
        }
    }

    private void transition(State next) {
        State prev = state.getAndSet(next);
        if (prev == next) {
            return;
        }

        if (next == State.OPEN) {
            openedAt.set(System.currentTimeMillis());
            halfOpenCallCount.set(0);
            halfOpenSuccessCount.set(0);
        } else if (next == State.CLOSED) {
            failureCount.set(0);
            halfOpenCallCount.set(0);
            halfOpenSuccessCount.set(0);
            openedAt.set(0);
        } else {
            halfOpenCallCount.set(0);
            halfOpenSuccessCount.set(0);
        }

        Consumer<State> listener = onStateChange;
        if (listener != null) {
            try {
                listener.accept(next);
            } catch (Exception ignored) {
            }
        }
    }

    public static class Builder {

        private int failureThreshold = 5;
        private long openDurationMs = 30_000;
        private int halfOpenMaxCalls = 1;
        private Consumer<State> onStateChange;

        public Builder failureThreshold(int threshold) {
            this.failureThreshold = threshold;
            return this;
        }

        public Builder openDuration(long ms) {
            this.openDurationMs = ms;
            return this;
        }

        public Builder halfOpenMaxCalls(int calls) {
            this.halfOpenMaxCalls = calls;
            return this;
        }

        public Builder onStateChange(Consumer<State> listener) {
            this.onStateChange = listener;
            return this;
        }

        public CircuitBreaker build() {
            return new CircuitBreaker(this);
        }
    }
}