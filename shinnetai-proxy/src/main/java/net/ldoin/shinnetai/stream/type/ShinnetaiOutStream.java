package net.ldoin.shinnetai.stream.type;

import net.ldoin.shinnetai.packet.WrappedPacket;
import net.ldoin.shinnetai.packet.side.PacketSide;
import net.ldoin.shinnetai.stream.ShinnetaiStream;
import net.ldoin.shinnetai.stream.ShinnetaiStreamType;
import net.ldoin.shinnetai.stream.options.ShinnetaiStreamOptions;
import net.ldoin.shinnetai.worker.ShinnetaiIOWorker;
import net.ldoin.shinnetai.worker.ShinnetaiWorkerContext;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BiConsumer;
import java.util.logging.Level;

public class ShinnetaiOutStream extends ShinnetaiStream {

    private final BlockingQueue<SendPair> queue;

    public ShinnetaiOutStream(ShinnetaiIOWorker<?> worker, ShinnetaiStreamOptions options) {
        this(worker.getStreamIdGenerator().getNextId(), worker, options);
    }

    public ShinnetaiOutStream(int id, ShinnetaiIOWorker<?> worker, ShinnetaiStreamOptions options) {
        super(id, worker, options);
        this.queue = new LinkedBlockingQueue<>();
    }

    @Override
    public final ShinnetaiStreamType getType() {
        return ShinnetaiStreamType.OUT;
    }

    @Override
    public PacketSide getSide() {
        return worker.getSide();
    }

    @Override
    @SuppressWarnings("unchecked")
    public ShinnetaiOutStream self() {
        return this;
    }

    @Override
    protected ShinnetaiStream createStreamInstance(int id, ShinnetaiIOWorker<?> worker, ShinnetaiStreamOptions options) {
        return new ShinnetaiOutStream(id, worker, options);
    }

    @Override
    public void run() {
        while (canRun()) {
            try {
                SendPair sendPair = queue.take();
                attachWorker(sendPair.packet.getPacket());
                sendPair.accept(worker);
                handledPackets++;
            } catch (InterruptedException e) {
                getLogger().log(Level.SEVERE, "Error when take packet from queue", e);
            }
        }

        super.run();
    }

    public boolean send(WrappedPacket packet) {
        return send((context, packet1) -> {
            try {
                context.sendPacket(packet1);
            } catch (IOException e) {
                getLogger().warning("Cannot send packet");
            }
        }, packet);
    }

    public boolean send(BiConsumer<ShinnetaiWorkerContext<?>, WrappedPacket> consumer, WrappedPacket packet) {
        return enqueue(new SendPair(consumer, packet));
    }

    protected boolean enqueue(SendPair sendPair) {
        if (canAccept(sendPair.packet)) {
            return queue.offer(sendPair);
        } else {
            return false;
        }
    }

    protected static class SendPair {

        private final BiConsumer<ShinnetaiWorkerContext<?>, WrappedPacket> function;
        private final WrappedPacket packet;

        protected SendPair(BiConsumer<ShinnetaiWorkerContext<?>, WrappedPacket> function, WrappedPacket packet) {
            this.function = function;
            this.packet = packet;
        }

        public void accept(ShinnetaiWorkerContext<?> worker) {
            function.accept(worker, packet);
        }
    }
}