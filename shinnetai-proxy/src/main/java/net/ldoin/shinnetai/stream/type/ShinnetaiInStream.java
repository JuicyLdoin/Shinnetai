package net.ldoin.shinnetai.stream.type;

import net.ldoin.shinnetai.packet.AbstractPacket;
import net.ldoin.shinnetai.packet.ReadPacket;
import net.ldoin.shinnetai.packet.WrappedPacket;
import net.ldoin.shinnetai.packet.common.StreamCommitAckPacket;
import net.ldoin.shinnetai.packet.side.PacketSide;
import net.ldoin.shinnetai.stream.ShinnetaiStream;
import net.ldoin.shinnetai.stream.ShinnetaiStreamType;
import net.ldoin.shinnetai.stream.options.ShinnetaiStreamOptions;
import net.ldoin.shinnetai.worker.ShinnetaiIOWorker;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class ShinnetaiInStream extends ShinnetaiStream {

    private final BlockingQueue<ReadPacket> queue;
    private volatile boolean commitPending = false;

    public ShinnetaiInStream(ShinnetaiIOWorker<?> worker, ShinnetaiStreamOptions options) {
        this(worker.getStreamIdGenerator().getNextId(), worker, options);
    }

    public ShinnetaiInStream(int id, ShinnetaiIOWorker<?> worker, ShinnetaiStreamOptions options) {
        super(id, worker, options);
        int capacity = options.getPacketsAmount() > 0 ? Math.min(options.getPacketsAmount(), options.getMaxQueueSize()) : options.getMaxQueueSize();
        this.queue = new LinkedBlockingQueue<>(capacity);
    }

    @Override
    public final ShinnetaiStreamType getType() {
        return ShinnetaiStreamType.IN;
    }

    @Override
    public PacketSide getSide() {
        return worker.getSide();
    }

    @Override
    protected ShinnetaiStream createStreamInstance(int id, ShinnetaiIOWorker<?> worker, ShinnetaiStreamOptions options) {
        return new ShinnetaiInStream(id, worker, options);
    }

    @Override
    public boolean canAccept(ReadPacket packet) {
        return super.canAccept(packet) && canAccept();
    }

    @Override
    public boolean canAccept(AbstractPacket<?, ?> packet) {
        return super.canAccept(packet) && canAccept();
    }

    protected boolean canAccept() {
        return options.getPacketsAmount() == -1 || queue.size() < options.getPacketsAmount() - handledPackets - 1;
    }

    @Override
    public void run() {
        while (canRun()) {
            try {
                ReadPacket packet = queue.poll(50, TimeUnit.MILLISECONDS);
                if (packet == null) {
                    if (commitPending && queue.isEmpty()) {
                        sendCommitAck(true, null);
                        break;
                    }

                    continue;
                }

                attachWorker(packet.wrapped().getPacket());
                handlePacket(packet);
                handledPackets++;

                if (commitPending && queue.isEmpty()) {
                    sendCommitAck(true, null);
                    break;
                }
            } catch (InterruptedException e) {
                if (commitPending) {
                    sendCommitAck(false, "Stream interrupted before commit drained");
                }
                
                break;
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Error handling packet", e);
            }
        }

        super.run();
    }

    public void receiveCommit() {
        this.commitPending = true;
    }

    private void sendCommitAck(boolean success, String message) {
        worker.addPacket(WrappedPacket.of(new StreamCommitAckPacket(getId(), success, message)));
    }

    public boolean receive(ReadPacket packet) {
        return receive(packet, false);
    }

    public boolean receive(ReadPacket packet, boolean force) {
        if (force) {
            return queue.offer(packet);
        }

        if (canAccept(packet)) {
            return queue.offer(packet);
        }

        return false;
    }
}