package net.ldoin.shinnetai.stream.type;

import net.ldoin.shinnetai.packet.AbstractPacket;
import net.ldoin.shinnetai.packet.ReadPacket;
import net.ldoin.shinnetai.packet.side.PacketSide;
import net.ldoin.shinnetai.stream.ShinnetaiStream;
import net.ldoin.shinnetai.stream.ShinnetaiStreamType;
import net.ldoin.shinnetai.stream.options.ShinnetaiStreamOptions;
import net.ldoin.shinnetai.worker.ShinnetaiIOWorker;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;

public class ShinnetaiInStream extends ShinnetaiStream {

    private final BlockingQueue<ReadPacket> queue;

    public ShinnetaiInStream(ShinnetaiIOWorker<?> worker, ShinnetaiStreamOptions options) {
        this(worker.getStreamIdGenerator().getNextId(), worker, options);
    }

    public ShinnetaiInStream(int id, ShinnetaiIOWorker<?> worker, ShinnetaiStreamOptions options) {
        super(id, worker, options);
        this.queue = new LinkedBlockingQueue<>();
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
    @SuppressWarnings("unchecked")
    public ShinnetaiInStream self() {
        return this;
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
                ReadPacket packet = queue.take();
                attachWorker(packet.wrapped().getPacket());
                handlePacket(packet);
                handledPackets++;
            } catch (InterruptedException e) {
                getLogger().log(Level.SEVERE, "Error when take packet from queue", e);
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Error handling packet", e);
            }
        }

        super.run();
    }

    public boolean receive(ReadPacket packet) {
        return receive(packet, false);
    }

    public boolean receive(ReadPacket packet, boolean force) {
        if (force) {
            queue.add(packet);
            return true;
        }

        if (canAccept(packet)) {
            queue.add(packet);
            return true;
        } else {
            return false;
        }
    }
}