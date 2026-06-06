package net.ldoin.shinnetai.delivery;

import net.ldoin.shinnetai.packet.WrappedPacket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class PacketOutboxSession {

    private final int maxSize;
    private final LinkedHashMap<Long, WrappedPacket> packets;
    private final Map<Long, PacketDeliveryState> deliveryStates;
    private volatile long lastTouchMs;

    public PacketOutboxSession(int maxSize) {
        this.maxSize = maxSize;
        this.packets = new LinkedHashMap<>(Math.min(maxSize, 64));
        this.deliveryStates = new HashMap<>(Math.min(maxSize, 64));
        this.lastTouchMs = System.currentTimeMillis();
    }

    public synchronized PacketOutboxStoreResult store(WrappedPacket packet) {
        long packetId = packet.getPacketId();
        if (packetId <= 0) {
            return PacketOutboxStoreResult.REJECTED_INVALID_PACKET_ID;
        }

        if (packets.containsKey(packetId)) {
            touch();
            return PacketOutboxStoreResult.ALREADY_STORED;
        }

        if (packets.size() >= maxSize) {
            return PacketOutboxStoreResult.REJECTED_MAX_PACKETS;
        }

        packets.put(packetId, packet);
        deliveryStates.putIfAbsent(packetId, new PacketDeliveryState());
        touch();
        return PacketOutboxStoreResult.STORED;
    }

    public synchronized boolean ack(long packetId) {
        boolean removed = packets.remove(packetId) != null;
        if (removed) {
            deliveryStates.remove(packetId);
        }

        if (removed) {
            touch();
        }

        return removed;
    }

    public synchronized void markSent(long packetId, long now) {
        PacketDeliveryState state = deliveryStates.computeIfAbsent(packetId, k -> new PacketDeliveryState());
        state.markSent(now);
        touch();
    }

    public synchronized List<WrappedPacket> collectForReplay(long now, int maxRetries) {
        return collectForReplay(now, maxRetries, null);
    }

    public synchronized List<WrappedPacket> collectForReplay(long now, int maxRetries, Consumer<PacketOutboxResult> discardedConsumer) {
        List<WrappedPacket> result = new ArrayList<>(packets.size());
        Iterator<Map.Entry<Long, WrappedPacket>> iterator = packets.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, WrappedPacket> entry = iterator.next();
            PacketDeliveryState state = deliveryStates.computeIfAbsent(entry.getKey(), key -> new PacketDeliveryState());
            if (maxRetries > 0 && state.getAttempts() >= maxRetries) {
                notifyDiscarded(discardedConsumer, entry.getValue(), PacketOutboxStoreResult.DISCARDED_MAX_RETRIES);
                iterator.remove();
                deliveryStates.remove(entry.getKey());
                continue;
            }

            result.add(entry.getValue());
        }

        if (!result.isEmpty()) {
            touch();
        }

        return result;
    }

    public synchronized List<WrappedPacket> collectForResend(long now, long retryIntervalMs, int maxRetries, long maxAgeMs) {
        return collectForResend(now, retryIntervalMs, maxRetries, maxAgeMs, null);
    }

    public synchronized List<WrappedPacket> collectForResend(long now, long retryIntervalMs, int maxRetries, long maxAgeMs, Consumer<PacketOutboxResult> discardedConsumer) {
        List<WrappedPacket> result = new ArrayList<>();
        Iterator<Map.Entry<Long, WrappedPacket>> iterator = packets.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, WrappedPacket> entry = iterator.next();
            PacketDeliveryState state = deliveryStates.computeIfAbsent(entry.getKey(), key -> new PacketDeliveryState());
            if (maxRetries > 0 && state.getAttempts() >= maxRetries) {
                notifyDiscarded(discardedConsumer, entry.getValue(), PacketOutboxStoreResult.DISCARDED_MAX_RETRIES);
                iterator.remove();
                deliveryStates.remove(entry.getKey());
                continue;
            }

            if (maxAgeMs > 0 && state.getFirstSentMs() > 0 && now - state.getFirstSentMs() > maxAgeMs) {
                notifyDiscarded(discardedConsumer, entry.getValue(), PacketOutboxStoreResult.DISCARDED_EXPIRED);
                iterator.remove();
                deliveryStates.remove(entry.getKey());
                continue;
            }

            long lastSentMs = state.getLastSentMs();
            if (lastSentMs == 0 || now - lastSentMs >= retryIntervalMs) {
                result.add(entry.getValue());
            }
        }

        if (!result.isEmpty()) {
            touch();
        }
        
        return result;
    }

    private void notifyDiscarded(Consumer<PacketOutboxResult> discardedConsumer,
                                 WrappedPacket packet,
                                 PacketOutboxStoreResult result) {
        if (discardedConsumer != null) {
            discardedConsumer.accept(new PacketOutboxResult(packet, result));
        }
    }

    public synchronized List<WrappedPacket> drainAll() {
        List<WrappedPacket> result = new ArrayList<>(packets.values());
        packets.clear();
        deliveryStates.clear();
        touch();
        return result;
    }

    public synchronized boolean isEmpty() {
        return packets.isEmpty();
    }

    public void touch() {
        lastTouchMs = System.currentTimeMillis();
    }

    public long lastTouchMs() {
        return lastTouchMs;
    }
}