package net.ldoin.shinnetai.packet;

import net.ldoin.shinnetai.buffered.buf.smart.ReadOnlySmartByteBuf;
import net.ldoin.shinnetai.buffered.buf.smart.WriteOnlySmartByteBuf;
import net.ldoin.shinnetai.packet.side.PacketSide;
import net.ldoin.shinnetai.worker.ShinnetaiIOWorker;
import net.ldoin.shinnetai.worker.ShinnetaiWorkerContext;

public abstract class AbstractPacket<C extends ShinnetaiWorkerContext<?>, S extends ShinnetaiWorkerContext<?>> {

    private C clientWorker;
    private S serverWorker;

    public void attachClientWorker(C clientWorker) {
        this.clientWorker = clientWorker;
    }

    public void attachServerWorker(S serverWorker) {
        this.serverWorker = serverWorker;
    }

    public C getClientWorker() {
        return clientWorker;
    }

    public S getServerWorker() {
        return serverWorker;
    }

    protected ShinnetaiWorkerContext<?> getCurrentContext() {
        return getClientWorker() != null ? getClientWorker() : getServerWorker();
    }

    protected ShinnetaiIOWorker<?> getCurrentWorker() {
        return (ShinnetaiIOWorker<?>) (getClientWorker() != null ? getClientWorker() : getServerWorker());
    }

    public int getPacketId() {
        return getCurrentContext().getRegistry().getId(getClass());
    }

    public PacketSide getHandleSide() {
        return PacketSide.MULTIPLE;
    }

    public final void handle(PacketSide side) {
        if (!getHandleSide().canHandle(side)) {
            return;
        }

        switch (side) {
            case CLIENT -> handleClient();
            case SERVER -> handleServer();
        }
    }

    public void handleClient() {
    }

    public void handleServer() {
    }

    public <P extends AbstractPacket<?, ?>> P response() {
        return null;
    }

    public abstract void read(ReadOnlySmartByteBuf buf);

    public abstract void write(WriteOnlySmartByteBuf buf);
}