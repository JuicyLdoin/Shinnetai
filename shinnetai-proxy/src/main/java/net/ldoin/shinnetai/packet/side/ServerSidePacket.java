package net.ldoin.shinnetai.packet.side;

import net.ldoin.shinnetai.packet.AbstractPacket;
import net.ldoin.shinnetai.worker.ShinnetaiIOWorker;

public abstract class ServerSidePacket<S extends ShinnetaiIOWorker<?>> extends AbstractPacket<ShinnetaiIOWorker<?>, S> {

    @Override
    public void attachClientWorker(ShinnetaiIOWorker<?> clientWorker) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ShinnetaiIOWorker<?> getClientWorker() {
        throw new UnsupportedOperationException();
    }

    @Override
    public PacketSide getHandleSide() {
        return PacketSide.SERVER;
    }
}