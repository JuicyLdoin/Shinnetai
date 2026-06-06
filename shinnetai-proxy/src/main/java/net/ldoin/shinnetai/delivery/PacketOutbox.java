package net.ldoin.shinnetai.delivery;

import net.ldoin.shinnetai.packet.PacketOptions;
import net.ldoin.shinnetai.packet.WrappedPacket;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PacketOutbox {

    private static final Logger LOGGER = Logger.getLogger(PacketOutbox.class.getName());

    private final ConcurrentHashMap<Integer, PacketOutboxSession> sessions = new ConcurrentHashMap<>();
    private final AtomicLong internalIdGenerator = new AtomicLong(1);
    private final long sessionTtlMs;
    private final int maxPacketsPerSession;
    private final int maxSessions;
    private final ScheduledExecutorService cleaner;

    private PacketOutbox(Builder builder) {
        this.sessionTtlMs = builder.sessionTtlMs;
        this.maxPacketsPerSession = builder.maxPacketsPerSession;
        this.maxSessions = builder.maxSessions;

        this.cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "shinnetai-outbox-cleaner");
            t.setDaemon(true);
            return t;
        });

        long periodMs = sessionTtlMs > 0 ? Math.max(100L, sessionTtlMs / 4) : 60_000L;
        cleaner.scheduleAtFixedRate(this::evictExpired, periodMs, periodMs, TimeUnit.MILLISECONDS);
    }

    public static Builder builder() {
        return new Builder();
    }

    public void retain(int clientId, Collection<WrappedPacket> pendingPackets) {
        retain(clientId, pendingPackets, internalIdGenerator::getAndIncrement);
    }

    public void retain(int clientId, Collection<WrappedPacket> pendingPackets, LongSupplier packetIdSupplier) {
        tryRetain(clientId, pendingPackets, packetIdSupplier);
    }

    public List<PacketOutboxResult> tryRetain(int clientId, Collection<WrappedPacket> pendingPackets, LongSupplier packetIdSupplier) {
        if (pendingPackets.isEmpty()) {
            return Collections.emptyList();
        }

        if (maxSessions > 0 && !sessions.containsKey(clientId) && sessions.size() >= maxSessions) {
            LOGGER.log(Level.WARNING, "PacketOutbox: max sessions ({0}) reached, dropping retain for client {1}", new Object[]{maxSessions, clientId});
            List<PacketOutboxResult> rejected = new java.util.ArrayList<>(pendingPackets.size());
            for (WrappedPacket packet : pendingPackets) {
                rejected.add(new PacketOutboxResult(ensureTracked(packet, packetIdSupplier), PacketOutboxStoreResult.REJECTED_MAX_SESSIONS));
            }
            return rejected;
        }

        PacketOutboxSession entry = sessions.computeIfAbsent(clientId, id -> new PacketOutboxSession(maxPacketsPerSession));
        List<PacketOutboxResult> results = new java.util.ArrayList<>(pendingPackets.size());
        for (WrappedPacket packet : pendingPackets) {
            WrappedPacket tracked = ensureTracked(packet, packetIdSupplier);
            results.add(new PacketOutboxResult(tracked, entry.store(tracked)));
        }

        entry.touch();
        return results;
    }

    public void enqueue(int clientId, WrappedPacket packet) {
        enqueue(clientId, packet, internalIdGenerator::getAndIncrement);
    }

    public void enqueue(int clientId, WrappedPacket packet, LongSupplier packetIdSupplier) {
        tryEnqueue(clientId, packet, packetIdSupplier);
    }

    public PacketOutboxResult tryEnqueue(int clientId, WrappedPacket packet, LongSupplier packetIdSupplier) {
        if (maxSessions > 0 && !sessions.containsKey(clientId) && sessions.size() >= maxSessions) {
            LOGGER.log(Level.WARNING, "PacketOutbox: max sessions ({0}) reached, dropping enqueue for client {1}", new Object[]{maxSessions, clientId});
            return new PacketOutboxResult(ensureTracked(packet, packetIdSupplier), PacketOutboxStoreResult.REJECTED_MAX_SESSIONS);
        }

        WrappedPacket tracked = ensureTracked(packet, packetIdSupplier);
        PacketOutboxSession entry = sessions.computeIfAbsent(clientId, id -> new PacketOutboxSession(maxPacketsPerSession));
        PacketOutboxStoreResult result = entry.store(tracked);
        entry.touch();
        return new PacketOutboxResult(tracked, result);
    }

    public WrappedPacket track(int clientId, WrappedPacket packet, LongSupplier packetIdSupplier) {
        return tryTrack(clientId, packet, packetIdSupplier).packet();
    }

    public PacketOutboxResult tryTrack(int clientId, WrappedPacket packet, LongSupplier packetIdSupplier) {
        WrappedPacket tracked = ensureTracked(packet, packetIdSupplier);
        if (maxSessions > 0 && !sessions.containsKey(clientId) && sessions.size() >= maxSessions) {
            LOGGER.log(Level.WARNING, "PacketOutbox: max sessions ({0}) reached, dropping track for client {1}", new Object[]{maxSessions, clientId});
            return new PacketOutboxResult(tracked, PacketOutboxStoreResult.REJECTED_MAX_SESSIONS);
        }

        PacketOutboxSession entry = sessions.computeIfAbsent(clientId, id -> new PacketOutboxSession(maxPacketsPerSession));
        PacketOutboxStoreResult result = entry.store(tracked);
        return new PacketOutboxResult(tracked, result);
    }

    public boolean ack(int clientId, long packetId) {
        PacketOutboxSession entry = sessions.get(clientId);
        if (entry == null) {
            return false;
        }

        boolean removed = entry.ack(packetId);
        if (removed && entry.isEmpty()) {
            sessions.remove(clientId, entry);
        }

        return removed;
    }

    public void markSent(int clientId, long packetId, long now) {
        PacketOutboxSession entry = sessions.get(clientId);
        if (entry == null) {
            return;
        }

        entry.markSent(packetId, now);
    }

    public List<WrappedPacket> collectForReplay(int clientId, long now, int maxRetries) {
        return collectForReplay(clientId, now, maxRetries, null);
    }

    public List<WrappedPacket> collectForReplay(int clientId, long now, int maxRetries, Consumer<PacketOutboxResult> discardedConsumer) {
        PacketOutboxSession entry = sessions.get(clientId);
        if (entry == null) {
            return Collections.emptyList();
        }

        List<WrappedPacket> replay = entry.collectForReplay(now, maxRetries, discardedConsumer);
        if (entry.isEmpty()) {
            sessions.remove(clientId, entry);
        }
        
        return replay;
    }

    public List<WrappedPacket> collectForResend(int clientId, long now, long retryIntervalMs, int maxRetries) {
        return collectForResend(clientId, now, retryIntervalMs, maxRetries, null);
    }

    public List<WrappedPacket> collectForResend(int clientId, long now, long retryIntervalMs, int maxRetries, Consumer<PacketOutboxResult> discardedConsumer) {
        PacketOutboxSession entry = sessions.get(clientId);
        if (entry == null) {
            return Collections.emptyList();
        }

        List<WrappedPacket> resend = entry.collectForResend(now, retryIntervalMs, maxRetries, sessionTtlMs, discardedConsumer);
        if (entry.isEmpty()) {
            sessions.remove(clientId, entry);
        }

        return resend;
    }

    public List<WrappedPacket> drain(int clientId) {
        PacketOutboxSession entry = sessions.get(clientId);
        if (entry == null) {
            return Collections.emptyList();
        }

        List<WrappedPacket> drained = entry.drainAll();
        if (entry.isEmpty()) {
            sessions.remove(clientId, entry);
        }

        return drained;
    }

    public boolean hasSession(int clientId) {
        PacketOutboxSession entry = sessions.get(clientId);
        return entry != null && !entry.isEmpty();
    }

    public void clear(int clientId) {
        sessions.remove(clientId);
    }

    public int getSessionCount() {
        return sessions.size();
    }

    public long getSessionTtlMs() {
        return sessionTtlMs;
    }

    public void close() {
        cleaner.shutdownNow();
    }

    private void evictExpired() {
        long now = System.currentTimeMillis();
        long cutoff = sessionTtlMs > 0 ? now - sessionTtlMs : -1;
        int evicted = 0;
        for (Map.Entry<Integer, PacketOutboxSession> entry : sessions.entrySet()) {
            PacketOutboxSession session = entry.getValue();
            boolean expired = cutoff > 0 && session.lastTouchMs() < cutoff;
            boolean empty = session.isEmpty();
            if (expired || empty) {
                if (sessions.remove(entry.getKey(), session)) {
                    evicted++;
                }
            }
        }

        if (evicted > 0) {
            LOGGER.log(Level.FINE, "PacketOutbox: evicted {0} session(s)", evicted);
        }
    }

    private WrappedPacket ensureTracked(WrappedPacket packet, LongSupplier packetIdSupplier) {
        if (packet.getOptionValue(PacketOptions.DELIVERY_TRACKED) && packet.getPacketId() > 0) {
            return packet;
        }

        long packetId = packetIdSupplier.getAsLong();
        return WrappedPacket.builder(packet)
                .withOption(PacketOptions.DELIVERY_TRACKED)
                .packetId(packetId)
                .build();
    }

    public static final class Builder {

        private long sessionTtlMs = 120_000L;
        private int maxPacketsPerSession = 200;
        private int maxSessions = 10_000;

        private Builder() {
        }

        public Builder sessionTtl(Duration ttl) {
            this.sessionTtlMs = ttl.toMillis();
            return this;
        }

        public Builder maxPacketsPerSession(int max) {
            if (max < 1) {
                throw new IllegalArgumentException("maxPacketsPerSession must be >= 1");
            }
            
            this.maxPacketsPerSession = max;
            return this;
        }

        public Builder maxSessions(int maxSessions) {
            if (maxSessions < 0) {
                throw new IllegalArgumentException("maxSessions must be >= 0 (0 = unlimited)");
            }
            
            this.maxSessions = maxSessions;
            return this;
        }

        public PacketOutbox build() {
            return new PacketOutbox(this);
        }
    }
}