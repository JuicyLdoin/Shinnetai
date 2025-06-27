package net.ldoin.shinnetai.packet.response;

import net.ldoin.shinnetai.packet.AbstractPacket;
import net.ldoin.shinnetai.util.IdGenerator;

import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public class PacketResponseWaiter {

    private final ConcurrentHashMap<Integer, SyncWaiter> syncWaiters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, CompletableFuture<Optional<AbstractPacket<?, ?>>>> asyncWaiters = new ConcurrentHashMap<>();
    private final IdGenerator idGenerator = new IdGenerator();

    private final ScheduledExecutorService scheduler;

    public PacketResponseWaiter() {
        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread t = new Thread(runnable);
            t.setDaemon(true);
            t.setName("shinnetai-timeout-scheduler");
            return t;
        });

        scheduler.setKeepAliveTime(10, TimeUnit.SECONDS);
        scheduler.allowCoreThreadTimeOut(true);
        this.scheduler = scheduler;
    }

    public int waitersCount() {
        return syncWaiters.size() + asyncWaiters.size();
    }

    public int addWaiter(boolean isAsync, CompletableFuture<Optional<AbstractPacket<?, ?>>> asyncHandler, long timeoutMillis) {
        int waiterId = idGenerator.getNextId();
        if (isAsync) {
            asyncWaiters.put(waiterId, asyncHandler);
            scheduleAsyncTimeout(waiterId, timeoutMillis);
        } else {
            syncWaiters.put(waiterId, new SyncWaiter());
            scheduleTimeout(waiterId, timeoutMillis);
        }

        return waiterId;
    }

    public AbstractPacket<?, ?> waitForResponse(int waiterId, long timeoutMillis) throws TimeoutException, InterruptedException {
        SyncWaiter waiter = syncWaiters.get(waiterId);
        if (waiter == null) {
            throw new TimeoutException("No waiter found for ID: " + waiterId);
        }

        boolean completed = waiter.latch.await(timeoutMillis, TimeUnit.MILLISECONDS);
        syncWaiters.remove(waiterId);

        if (!completed) {
            idGenerator.releaseId(waiterId);
            throw new TimeoutException("Timed out waiting for response");
        }

        AbstractPacket<?, ?> response = waiter.packetRef.get();
        if (response == null) {
            throw new TimeoutException("No response received");
        }

        return response;
    }

    public void handleResponse(int waiterId, AbstractPacket<?, ?> response) {
        SyncWaiter syncWaiter = syncWaiters.remove(waiterId);
        if (syncWaiter != null) {
            syncWaiter.packetRef.set(response);
            syncWaiter.latch.countDown();
            idGenerator.releaseId(waiterId);
            return;
        }

        CompletableFuture<Optional<AbstractPacket<?, ?>>> asyncFuture = asyncWaiters.remove(waiterId);
        if (asyncFuture != null) {
            asyncFuture.complete(Optional.ofNullable(response));
            idGenerator.releaseId(waiterId);
        }
    }

    private void scheduleTimeout(int waiterId, long timeoutMillis) {
        scheduler.schedule(() -> {
            SyncWaiter waiter = syncWaiters.remove(waiterId);
            if (waiter != null) {
                waiter.latch.countDown();
                idGenerator.releaseId(waiterId);
            }
        }, timeoutMillis, TimeUnit.MILLISECONDS);
    }

    private void scheduleAsyncTimeout(int waiterId, long timeoutMillis) {
        scheduler.schedule(() -> {
            CompletableFuture<Optional<AbstractPacket<?, ?>>> asyncFuture = asyncWaiters.remove(waiterId);
            if (asyncFuture != null) {
                asyncFuture.completeExceptionally(new TimeoutException("Response not received"));
                idGenerator.releaseId(waiterId);
            }
        }, timeoutMillis, TimeUnit.MILLISECONDS);
    }

    public void shutdown() {
        scheduler.shutdown();
    }

    private static class SyncWaiter {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<AbstractPacket<?, ?>> packetRef = new AtomicReference<>();
    }
}