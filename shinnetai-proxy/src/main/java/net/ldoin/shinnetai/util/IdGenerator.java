package net.ldoin.shinnetai.util;

import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Set;

public class IdGenerator {

    private int nextId = 0;
    private final PriorityQueue<Integer> freeIds = new PriorityQueue<>();
    private final Set<Integer> allocated = new HashSet<>();

    public synchronized int getNextId() {
        int id;
        if (!freeIds.isEmpty()) {
            id = freeIds.poll();
        } else {
            id = nextId++;
        }

        allocated.add(id);
        return id;
    }

    public synchronized void releaseId(int id) {
        if (allocated.remove(id)) {
            freeIds.add(id);
        } else {
            throw new IllegalArgumentException("ID " + id + " is not allocated or already released.");
        }
    }

    public synchronized boolean isAllocated(int id) {
        return allocated.contains(id);
    }

    public synchronized void clear() {
        allocated.clear();
        freeIds.clear();
        nextId = 0;
    }
}