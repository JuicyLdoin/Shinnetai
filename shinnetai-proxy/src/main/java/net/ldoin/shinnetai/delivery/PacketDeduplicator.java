package net.ldoin.shinnetai.delivery;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PacketDeduplicator {

    private final long ttlMs;
    private final int maxEntries;
    private final LinkedHashMap<Long, Long> seen;

    public PacketDeduplicator(long ttlMs, int maxEntries) {
        this.ttlMs = ttlMs;
        this.maxEntries = maxEntries;
        this.seen = new LinkedHashMap<>(Math.min(maxEntries, 1024), 0.75f, false);
    }

    public synchronized boolean isDuplicate(long packetId) {
        if (packetId <= 0) {
            return true;
        }

        long now = System.currentTimeMillis();
        evictExpired(now);
        if (seen.containsKey(packetId)) {
            return true;
        }

        seen.put(packetId, now);
        while (seen.size() > maxEntries) {
            Iterator<Long> iterator = seen.keySet().iterator();
            if (!iterator.hasNext()) {
                break;
            }

            iterator.next();
            iterator.remove();
        }

        return false;
    }

    private void evictExpired(long now) {
        if (ttlMs <= 0 || seen.isEmpty()) {
            return;
        }

        long cutoff = now - ttlMs;
        Iterator<Map.Entry<Long, Long>> iterator = seen.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Long> entry = iterator.next();
            Long value = entry.getValue();
            if (value == null || value < cutoff) {
                iterator.remove();
                continue;
            }

            break;
        }
    }
}