package net.ldoin.shinnetai.packet.side;

import net.ldoin.shinnetai.ShinnetaiIOWorker;
import net.ldoin.shinnetai.packet.AbstractPacket;

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