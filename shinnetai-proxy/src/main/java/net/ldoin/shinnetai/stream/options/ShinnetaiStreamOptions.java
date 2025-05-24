package net.ldoin.shinnetai.stream.options;

import net.ldoin.shinnetai.ShinnetaiIOWorker;
import net.ldoin.shinnetai.packet.AbstractPacket;
import net.ldoin.shinnetai.packet.registry.PacketRegistry;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

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

    public ShinnetaiStreamOptions(Builder builder) {
        this.packetsAmount = builder.packetsAmount;
        this.packetsFilter = builder.packetsFilter;
        this.lifetime = builder.lifetime;
        this.autoCloseable = builder.autoCloseable;
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

    public static class Builder {

        private final PacketRegistry registry;
        private int packetsAmount;
        private Set<Integer> packetsFilter;
        private long lifetime;
        private boolean autoCloseable;

        public Builder(ShinnetaiIOWorker<?> worker) {
            this.registry = worker == null ? null : worker.getRegistry();
            this.packetsAmount = -1;
            this.packetsFilter = new HashSet<>();
            this.lifetime = -1;
            this.autoCloseable = true;
        }

        public Builder setPacketsAmount(int packetsAmount) {
            this.packetsAmount = packetsAmount;
            return this;
        }

        public Builder addPacket(int id) {
            this.packetsFilter.add(id);
            return this;
        }

        public Builder addPacket(Class<? extends AbstractPacket<?, ?>> packet) {
            if (registry == null) {
                throw new UnsupportedOperationException("Registry not fount");
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

        public Builder setPacketsFilter(Set<Integer> packetsFilter) {
            this.packetsFilter = packetsFilter;
            return this;
        }

        public Builder setLifeTime(long lifetime) {
            this.lifetime = lifetime;
            return this;
        }

        public Builder setLifeTime(TimeUnit timeUnit, int lifetime) {
            return setLifeTime(timeUnit.toMillis(lifetime));
        }

        public Builder setAutoCloseable(boolean autoCloseable) {
            this.autoCloseable = autoCloseable;
            return this;
        }

        public ShinnetaiStreamOptions build() {
            return new ShinnetaiStreamOptions(this);
        }
    }
}