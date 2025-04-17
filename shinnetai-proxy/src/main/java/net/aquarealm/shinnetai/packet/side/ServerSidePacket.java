package net.aquarealm.shinnetai.packet.side;

import net.aquarealm.shinnetai.ShinnetaiIOWorker;
import net.aquarealm.shinnetai.packet.AbstractPacket;

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