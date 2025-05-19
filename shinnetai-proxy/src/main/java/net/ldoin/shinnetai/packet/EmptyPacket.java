package net.ldoin.shinnetai.packet;

import net.ldoin.shinnetai.ShinnetaiIOWorker;
import net.ldoin.shinnetai.buffered.buf.smart.ReadOnlySmartByteBuf;
import net.ldoin.shinnetai.buffered.buf.smart.WriteOnlySmartByteBuf;
import net.ldoin.shinnetai.client.ShinnetaiClient;

public abstract class EmptyPacket extends AbstractPacket<ShinnetaiClient, ShinnetaiIOWorker<?>> {

    @Override
    public void read(ReadOnlySmartByteBuf buf) {
    }

    @Override
    public void write(WriteOnlySmartByteBuf buf) {
    }
}