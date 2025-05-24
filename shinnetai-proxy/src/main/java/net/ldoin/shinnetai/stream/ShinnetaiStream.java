package net.ldoin.shinnetai.stream;

import net.ldoin.shinnetai.ShinnetaiIOWorker;
import net.ldoin.shinnetai.ShinnetaiWorkerContext;
import net.ldoin.shinnetai.packet.AbstractPacket;
import net.ldoin.shinnetai.packet.ReadPacket;
import net.ldoin.shinnetai.statistic.ShinnetaiStatistic;
import net.ldoin.shinnetai.stream.options.ShinnetaiStreamOptions;

import java.util.UUID;
import java.util.logging.Logger;

public abstract class ShinnetaiStream extends ShinnetaiWorkerContext<ShinnetaiStatistic> implements Runnable {

    private final int id;
    protected final ShinnetaiIOWorker<?> worker;
    protected final ShinnetaiStreamOptions options;

    protected Thread thread;
    private Thread lifetimeThread;
    private long startedAt;
    protected volatile boolean running;
    protected int handledPackets;

    protected ShinnetaiStream(ShinnetaiIOWorker<?> worker, ShinnetaiStreamOptions options) {
        this(worker.getStreamIdGenerator().getNextId(), worker, options);
    }

    protected ShinnetaiStream(int id, ShinnetaiIOWorker<?> worker, ShinnetaiStreamOptions options) {
        super(Logger.getLogger("Stream (" + id + ")"), worker.getRegistry(), worker.getInput(), worker.getOut(), worker.getResponseWaiter());

        this.id = id;
        this.worker = worker;
        this.options = options;
    }

    public int getId() {
        return id;
    }

    public abstract IShinnetaiStreamType getType();

    public ShinnetaiIOWorker<?> getWorker() {
        return worker;
    }

    public ShinnetaiStreamOptions getOptions() {
        return options;
    }

    public boolean isRunning() {
        return running;
    }

    public boolean canAccept(ReadPacket packet) {
        return options.getPacketsFilter().contains(packet.id());
    }

    public boolean canAccept(AbstractPacket<?, ?> packet) {
        return options.getPacketsFilter().contains(getRegistry().getId(packet.getClass()));
    }

    public boolean canRun() {
        return running && (options.getPacketsAmount() == -1 || handledPackets < options.getPacketsAmount());
    }

    @Override
    public void run() {
        getLogger().info("Stream finished");
        if (options.isAutoCloseable()) {
            close();
        }
    }

    public synchronized void open() {
        open(false);
    }

    public synchronized void open(boolean fromWorker) {
        if (worker == null) {
            throw new UnsupportedOperationException("Cannot open stream without IO worker");
        }

        getLogger().info("Stream opened");

        if (thread != null) {
            close();
        }

        startedAt = System.currentTimeMillis();
        running = true;
        thread = Thread.ofVirtual().start(() -> {
            try {
                run();
            } finally {
                stopLifetimeThread();
            }
        });

        if (options.getLifetime() > 0) {
            lifetimeThread = Thread.ofVirtual().start(() -> {
                try {
                    Thread.sleep(options.getLifetime());
                    if (running) {
                        getLogger().warning("Stream exceeded lifetime, closing...");
                        close();
                    }
                } catch (InterruptedException ignored) {
                }
            });
        }

        if (!fromWorker) {
            worker.openStream(this);
        }
    }

    public synchronized void close() {
        running = false;
        if (thread == null) {
            return;
        }

        thread.interrupt();
        thread = null;

        stopLifetimeThread();
        worker.closeStream(this);

        getLogger().info(String.format("Stream closed with work time %dms", System.currentTimeMillis() - startedAt));
    }

    private synchronized void stopLifetimeThread() {
        if (lifetimeThread == null) {
            return;
        }

        lifetimeThread.interrupt();
        lifetimeThread = null;
    }
}