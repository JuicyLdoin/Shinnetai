package net.ldoin.shinnetai.util;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLongArray;

public class IdGenerator {

    private static final int INITIAL_CAPACITY = 16;

    private final ConcurrentLinkedQueue<Integer> freeIds = new ConcurrentLinkedQueue<>();
    private final AtomicInteger nextId;

    private volatile AtomicLongArray bitmap;

    public IdGenerator() {
        this(1);
    }

    public IdGenerator(int start) {
        nextId = new AtomicInteger(start);
        bitmap = new AtomicLongArray(INITIAL_CAPACITY);
    }

    public int getNextId() {
        Integer id = freeIds.poll();
        if (id == null) {
            id = nextId.getAndIncrement();
        }

        ensureCapacity(id);
        if (!markAllocated(id)) {
            throw new IllegalStateException("ID " + id + " is already allocated (race or bug).");
        }

        return id;
    }

    public void releaseId(int id) {
        ensureCapacity(id);
        if (!unmarkAllocated(id)) {
            return;
        }

        freeIds.offer(id);
    }

    public void clear() {
        bitmap = new AtomicLongArray(INITIAL_CAPACITY);
        freeIds.clear();
        nextId.set(0);
    }

    private boolean markAllocated(int id) {
        int word = id >>> 6;
        long mask = 1L << (id & 63);
        while (true) {
            long current = bitmap.get(word);
            if ((current & mask) != 0) {
                return false;
            }

            if (bitmap.compareAndSet(word, current, current | mask)) {
                return true;
            }
        }
    }

    private boolean unmarkAllocated(int id) {
        int word = id >>> 6;
        long mask = 1L << (id & 63);
        while (true) {
            long current = bitmap.get(word);
            if ((current & mask) == 0) {
                return false;
            }

            if (bitmap.compareAndSet(word, current, current & ~mask)) {
                return true;
            }
        }
    }

    private void ensureCapacity(int id) {
        int requiredWordIndex = id >>> 6;
        AtomicLongArray current = bitmap;
        if (requiredWordIndex < current.length()) {
            return;
        }

        synchronized (this) {
            current = bitmap;
            if (requiredWordIndex < current.length()) {
                return;
            }

            int newSize = current.length();
            while (newSize <= requiredWordIndex) {
                newSize <<= 1;
            }

            AtomicLongArray newBitmap = new AtomicLongArray(newSize);
            for (int i = 0; i < current.length(); i++) {
                newBitmap.set(i, current.get(i));
            }

            bitmap = newBitmap;
        }
    }
}