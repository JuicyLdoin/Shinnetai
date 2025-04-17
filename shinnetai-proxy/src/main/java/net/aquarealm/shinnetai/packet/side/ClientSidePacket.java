package net.aquarealm.shinnetai.packet.side;

import net.aquarealm.shinnetai.ShinnetaiIOWorker;
import net.aquarealm.shinnetai.packet.AbstractPacket;

public abstract class ClientSidePacket<C extends ShinnetaiIOWorker<?>> extends AbstractPacket<C, ShinnetaiIOWorker<?>> {

    @Override
    public void attachServerWorker(ShinnetaiIOWorker<?> serverWorker) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ShinnetaiIOWorker<?> getServerWorker() {
        throw new UnsupportedOperationException();
    }

    @Override
    public PacketSide getHandleSide() {
        return PacketSide.CLIENT;
    }
}