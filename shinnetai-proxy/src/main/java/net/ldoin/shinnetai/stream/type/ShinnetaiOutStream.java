package net.ldoin.shinnetai.stream.type;

import net.ldoin.shinnetai.ShinnetaiIOWorker;
import net.ldoin.shinnetai.ShinnetaiWorkerContext;
import net.ldoin.shinnetai.packet.AbstractPacket;
import net.ldoin.shinnetai.packet.response.PacketResponseOptions;
import net.ldoin.shinnetai.packet.side.PacketSide;
import net.ldoin.shinnetai.stream.ShinnetaiStream;
import net.ldoin.shinnetai.stream.ShinnetaiStreamType;
import net.ldoin.shinnetai.stream.options.ShinnetaiStreamOptions;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BiConsumer;
import java.util.logging.Level;

public class ShinnetaiOutStream extends ShinnetaiStream {

    private final BlockingQueue<SendPair> queue;

    public ShinnetaiOutStream(ShinnetaiIOWorker<?> worker, ShinnetaiStreamOptions options) {
        this(UUID.randomUUID(), worker, options);
    }

    public ShinnetaiOutStream(UUID uuid, ShinnetaiIOWorker<?> worker, ShinnetaiStreamOptions options) {
        super(uuid, worker, options);
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
    public void run() {
        while (canRun()) {
            try {
                SendPair sendPair = queue.take();
                attachWorker(sendPair.packet);
                sendPair.accept(worker);
                handledPackets++;
            } catch (InterruptedException e) {
                getLogger().log(Level.SEVERE, "Error when take packet from queue", e);
            }
        }

        super.run();
    }

    public boolean send(AbstractPacket<?, ?> packet) {
        return send(packet, PacketResponseOptions.empty());
    }

    public boolean send(AbstractPacket<?, ?> packet, PacketResponseOptions responseOptions) {
        return send((context, packet1) -> {
            try {
                context.sendPacket(packet1, responseOptions);
            } catch (IOException e) {
                getLogger().warning("Cannot send packet");
            }
        }, packet);
    }

    public boolean send(BiConsumer<ShinnetaiWorkerContext<?>, AbstractPacket<?, ?>> consumer, AbstractPacket<?, ?> packet) {
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

        private final BiConsumer<ShinnetaiWorkerContext<?>, AbstractPacket<?, ?>> function;
        private final AbstractPacket<?, ?> packet;

        protected SendPair(BiConsumer<ShinnetaiWorkerContext<?>, AbstractPacket<?, ?>> function, AbstractPacket<?, ?> packet) {
            this.function = function;
            this.packet = packet;
        }

        public void accept(ShinnetaiWorkerContext<?> worker) {
            function.accept(worker, packet);
        }
    }
}