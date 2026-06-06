package net.ldoin.shinnetai.packet.schedule;

import net.ldoin.shinnetai.packet.AbstractPacket;
import net.ldoin.shinnetai.server.ShinnetaiServer;
import net.ldoin.shinnetai.server.connection.ShinnetaiConnection;
import net.ldoin.shinnetai.worker.ShinnetaiWorkerContext;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PacketScheduler {

    private static final Logger LOGGER = Logger.getLogger(PacketScheduler.class.getName());

    private final Supplier<AbstractPacket<?, ?>> packetSupplier;
    private final long periodMs;
    private final long initialDelayMs;
    private final ShinnetaiWorkerContext<?> target;
    private final ShinnetaiServer<?> server;
    private final Predicate<ShinnetaiConnection<?>> connectionFilter;

    private ScheduledExecutorService executor;
    private ScheduledFuture<?> future;
    private volatile boolean running = false;

    private PacketScheduler(Builder builder) {
        this.packetSupplier = builder.packetSupplier;
        this.periodMs = builder.periodMs;
        this.initialDelayMs = builder.initialDelayMs;
        this.target = builder.target;
        this.server = builder.server;
        this.connectionFilter = builder.connectionFilter;
    }

    public static Builder builder() {
        return new Builder();
    }

    public synchronized void start() {
        if (running) {
            return;
        }

        running = true;
        executor = Executors.newSingleThreadScheduledExecutor(r -> Thread.ofVirtual().name("packet-scheduler").unstarted(r));
        future = executor.scheduleAtFixedRate(this::tick, initialDelayMs, periodMs, TimeUnit.MILLISECONDS);
    }

    public synchronized void stop() {
        if (!running) {
            return;
        }

        running = false;
        if (future != null) {
            future.cancel(false);
            future = null;
        }

        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    public boolean isRunning() {
        return running;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void tick() {
        try {
            AbstractPacket<?, ?> packet = packetSupplier.get();
            if (packet == null) {
                return;
            }

            if (server != null) {
                if (connectionFilter != null) {
                    server.broadcastPacketTo(packet, (Predicate) connectionFilter);
                } else {
                    server.broadcastPacket(packet);
                }
            } else if (target != null) {
                target.sendPacket(packet);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "PacketScheduler tick error", e);
        }
    }

    public static class Builder {

        private Supplier<AbstractPacket<?, ?>> packetSupplier;
        private long periodMs;
        private long initialDelayMs = 0;
        private ShinnetaiWorkerContext<?> target;
        private ShinnetaiServer<?> server;
        private Predicate<ShinnetaiConnection<?>> connectionFilter;

        public Builder every(Duration period) {
            this.periodMs = period.toMillis();
            return this;
        }

        public Builder every(long amount, TimeUnit unit) {
            this.periodMs = unit.toMillis(amount);
            return this;
        }

        public Builder initialDelay(Duration delay) {
            this.initialDelayMs = delay.toMillis();
            return this;
        }

        public Builder send(Supplier<AbstractPacket<?, ?>> supplier) {
            this.packetSupplier = supplier;
            return this;
        }

        public Builder to(ShinnetaiWorkerContext<?> target) {
            this.target = target;
            return this;
        }

        public Builder toServer(ShinnetaiServer<?> server) {
            this.server = server;
            return this;
        }

        public Builder toServer(ShinnetaiServer<?> server, Predicate<ShinnetaiConnection<?>> filter) {
            this.server = server;
            this.connectionFilter = filter;
            return this;
        }

        public PacketScheduler build() {
            if (packetSupplier == null) {
                throw new IllegalStateException("packetSupplier must be set");
            }

            if (periodMs <= 0) {
                throw new IllegalStateException("period must be positive");
            }

            if (target == null && server == null) {
                throw new IllegalStateException("target or server must be set");
            }

            return new PacketScheduler(this);
        }
    }
}