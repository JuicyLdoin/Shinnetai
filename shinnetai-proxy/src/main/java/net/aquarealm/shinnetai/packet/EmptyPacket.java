package net.aquarealm.shinnetai.packet;

import net.aquarealm.shinnetai.ShinnetaiIOWorker;
import net.aquarealm.shinnetai.buffered.buf.smart.ReadOnlySmartByteBuf;
import net.aquarealm.shinnetai.buffered.buf.smart.WriteOnlySmartByteBuf;

public abstract class EmptyPacket extends AbstractPacket<ShinnetaiIOWorker<?>, ShinnetaiIOWorker<?>> {

    @Override
    public void read(ReadOnlySmartByteBuf buf) {
    }

    @Override
    public void write(WriteOnlySmartByteBuf buf) {
    }
}