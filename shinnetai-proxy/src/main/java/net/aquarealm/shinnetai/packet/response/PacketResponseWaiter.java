package net.aquarealm.shinnetai.packet.response;

import net.aquarealm.shinnetai.packet.AbstractPacket;

import java.util.Optional;
import java.util.PriorityQueue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class PacketResponseWaiter {

    private final ConcurrentHashMap<Integer, Optional<AbstractPacket<?, ?>>> syncWaiters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Consumer<Optional<AbstractPacket<?, ?>>>> asyncWaiters = new ConcurrentHashMap<>();
    private final PriorityQueue<Integer> availableIds = new PriorityQueue<>(15);
    private final AtomicInteger nextIdGenerator = new AtomicInteger(1);
    private final ConcurrentHashMap<Integer, Lock> waiterLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Condition> waiterConditions = new ConcurrentHashMap<>();

    public int waitersCount() {
        return syncWaiters.size() + asyncWaiters.size();
    }

    public int addWaiter(boolean isAsync, Consumer<Optional<AbstractPacket<?, ?>>> asyncHandler, long timeoutMillis) throws ArrayIndexOutOfBoundsException {
        int waiterId = getNextWaiterId();

        Lock lock = new ReentrantLock();
        Condition condition = lock.newCondition();
        waiterLocks.put(waiterId, lock);
        waiterConditions.put(waiterId, condition);

        if (isAsync) {
            asyncWaiters.put(waiterId, asyncHandler);
        } else {
            syncWaiters.put(waiterId, Optional.empty());
            scheduleTimeout(waiterId, timeoutMillis);
        }

        return waiterId;
    }

    public AbstractPacket<?, ?> waitForResponse(int waiterId, long timeoutMillis) throws TimeoutException, InterruptedException {
        Lock lock = waiterLocks.get(waiterId);
        Condition condition = waiterConditions.get(waiterId);

        if (lock == null || condition == null) {
            throw new IllegalStateException("No lock or condition found for waiter ID: " + waiterId);
        }

        long startTime = System.currentTimeMillis();
        lock.lock();
        try {
            while (syncWaiters.containsKey(waiterId)) {
                if (syncWaiters.get(waiterId).isPresent()) {
                    break;
                }

                long elapsedTime = System.currentTimeMillis() - startTime;
                if (elapsedTime >= timeoutMillis) {
                    syncWaiters.remove(waiterId);
                    cleanupWaiter(waiterId);
                    throw new TimeoutException("Response timed out for waiter ID: " + waiterId);
                }

                condition.await(timeoutMillis - elapsedTime, TimeUnit.MILLISECONDS);
            }

            if (!syncWaiters.containsKey(waiterId)) {
                throw new TimeoutException("Response not received for waiter ID: " + waiterId);
            }

            return syncWaiters.remove(waiterId).orElseThrow(() -> new TimeoutException("Response not received for waiter ID: " + waiterId));
        } finally {
            lock.unlock();
            cleanupWaiter(waiterId);
        }
    }

    public void handleResponse(int waiterId, AbstractPacket<?, ?> response) {
        Lock lock = waiterLocks.get(waiterId);
        Condition condition = waiterConditions.get(waiterId);

        if (lock == null || condition == null) {
            return;
        }

        lock.lock();
        try {
            if (syncWaiters.containsKey(waiterId)) {
                syncWaiters.put(waiterId, Optional.of(response));
                condition.signalAll();
            } else if (asyncWaiters.containsKey(waiterId)) {
                asyncWaiters.get(waiterId).accept(Optional.ofNullable(response));
                asyncWaiters.remove(waiterId);
                cleanupWaiter(waiterId);
            }
        } finally {
            lock.unlock();
        }
    }

    private void cleanupWaiter(int waiterId) {
        waiterLocks.remove(waiterId);
        waiterConditions.remove(waiterId);
        releaseWaiterId(waiterId);
    }

    private void scheduleTimeout(int waiterId, long timeoutMillis) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.schedule(() -> {
            Lock lock = waiterLocks.get(waiterId);
            Condition condition = waiterConditions.get(waiterId);

            if (lock == null || condition == null) {
                return;
            }

            lock.lock();
            try {
                if (syncWaiters.containsKey(waiterId) && syncWaiters.get(waiterId).isEmpty()) {
                    syncWaiters.remove(waiterId);
                    releaseWaiterId(waiterId);
                    condition.signalAll();
                }
            } finally {
                lock.unlock();
                scheduler.shutdown();
            }
        }, timeoutMillis, TimeUnit.MILLISECONDS);
    }

    private int getNextWaiterId() throws ArrayIndexOutOfBoundsException {
        synchronized (this) {
            if (availableIds.isEmpty()) {
                if (nextIdGenerator.get() >= 15) {
                    throw new ArrayIndexOutOfBoundsException("Too many response waiters (15/15)");
                }
                return nextIdGenerator.getAndIncrement();
            } else {
                return availableIds.poll();
            }
        }
    }

    private void releaseWaiterId(int waiterId) {
        synchronized (this) {
            availableIds.offer(waiterId);
        }
    }
}