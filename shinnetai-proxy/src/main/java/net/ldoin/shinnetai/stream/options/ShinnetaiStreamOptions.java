package net.ldoin.shinnetai.stream.options;

import net.ldoin.shinnetai.packet.AbstractPacket;
import net.ldoin.shinnetai.packet.registry.PacketRegistry;
import net.ldoin.shinnetai.packet.serializer.PacketSerializer;
import net.ldoin.shinnetai.stream.commit.StreamCommitResult;
import net.ldoin.shinnetai.worker.ShinnetaiIOWorker;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class ShinnetaiStreamOptions {

    public static ShinnetaiStreamOptions empty() {
        return new Builder(null).build();
    }

    public static ShinnetaiStreamOptions of(ShinnetaiIOWorker<?> worker) {
        return new Builder(worker).build();
    }

    public static Builder builder() {
        return new Builder(null);
    }

    public static Builder builder(ShinnetaiIOWorker<?> worker) {
        return new Builder(worker);
    }

    private final int packetsAmount;
    private final Set<Integer> packetsFilter;
    private final long lifetime;
    private final boolean autoCloseable;
    private final PacketSerializer serializer;
    private final boolean commitGuarantee;
    private final long commitTimeoutMs;
    private final Consumer<StreamCommitResult> onCommit;
    private final int maxQueueSize;

    public ShinnetaiStreamOptions(Builder builder) {
        this.packetsAmount = builder.packetsAmount;
        this.packetsFilter = builder.packetsFilter;
        this.lifetime = builder.lifetime;
        this.autoCloseable = builder.autoCloseable;
        this.serializer = builder.serializer;
        this.commitGuarantee = builder.commitGuarantee;
        this.commitTimeoutMs = builder.commitTimeoutMs;
        this.onCommit = builder.onCommit;
        this.maxQueueSize = builder.maxQueueSize;
    }

    public int getPacketsAmount() {
        return packetsAmount;
    }

    public Set<Integer> getPacketsFilter() {
        return packetsFilter;
    }

    public long getLifetime() {
        return lifetime;
    }

    public boolean isAutoCloseable() {
        return autoCloseable;
    }

    public PacketSerializer getSerializer() {
        return serializer;
    }

    public boolean isCommitGuarantee() {
        return commitGuarantee;
    }

    public long getCommitTimeoutMs() {
        return commitTimeoutMs;
    }

    public Consumer<StreamCommitResult> getOnCommit() {
        return onCommit;
    }

    public int getMaxQueueSize() {
        return maxQueueSize;
    }

    public static class Builder {

        private final PacketRegistry registry;
        private int packetsAmount;
        private Set<Integer> packetsFilter;
        private long lifetime;
        private boolean autoCloseable;
        private PacketSerializer serializer;
        private boolean commitGuarantee;
        private long commitTimeoutMs = 30_000L;
        private Consumer<StreamCommitResult> onCommit;
        private int maxQueueSize = 10_000;

        public Builder(ShinnetaiIOWorker<?> worker) {
            this.registry = worker == null ? null : worker.getRegistry();
            this.packetsAmount = -1;
            this.packetsFilter = new HashSet<>();
            this.lifetime = -1;
            this.autoCloseable = true;
        }

        public Builder packetsAmount(int packetsAmount) {
            this.packetsAmount = packetsAmount;
            return this;
        }

        public Builder addPacket(int id) {
            this.packetsFilter.add(id);
            return this;
        }

        public Builder addPacket(Class<? extends AbstractPacket<?, ?>> packet) {
            if (registry == null) {
                throw new UnsupportedOperationException("Registry not found");
            }

            this.packetsFilter.add(registry.getId(packet));
            return this;
        }

        public Builder addPackets(int... ids) {
            for (int id : ids) {
                this.packetsFilter.add(id);
            }
            return this;
        }

        @SafeVarargs
        public final Builder addPackets(Class<? extends AbstractPacket<?, ?>>... packets) {
            if (registry == null) {
                throw new UnsupportedOperationException("Registry not fount");
            }

            for (Class<? extends AbstractPacket<?, ?>> packet : packets) {
                this.packetsFilter.add(registry.getId(packet));
            }
            return this;
        }

        public Builder packetsFilter(Set<Integer> packetsFilter) {
            this.packetsFilter = packetsFilter;
            return this;
        }

        public Builder lifeTime(long lifetime) {
            this.lifetime = lifetime;
            return this;
        }

        public Builder lifeTime(TimeUnit timeUnit, int lifetime) {
            return lifeTime(timeUnit.toMillis(lifetime));
        }

        public Builder autoCloseable(boolean autoCloseable) {
            this.autoCloseable = autoCloseable;
            return this;
        }

        public Builder serializer(PacketSerializer serializer) {
            this.serializer = serializer;
            return this;
        }

        public Builder commitGuarantee(boolean commitGuarantee) {
            this.commitGuarantee = commitGuarantee;
            return this;
        }

        public Builder commitTimeoutMs(long commitTimeoutMs) {
            this.commitTimeoutMs = commitTimeoutMs;
            return this;
        }

        public Builder onCommit(Consumer<StreamCommitResult> onCommit) {
            this.onCommit = onCommit;
            return this;
        }

        public Builder maxQueueSize(int maxQueueSize) {
            if (maxQueueSize < 1) {
                throw new IllegalArgumentException("maxQueueSize must be >= 1");
            }

            this.maxQueueSize = maxQueueSize;
            return this;
        }

        public ShinnetaiStreamOptions build() {
            if (packetsAmount > 0) {
                maxQueueSize = Math.min(maxQueueSize, packetsAmount);
            }

            return new ShinnetaiStreamOptions(this);
        }
    }
}