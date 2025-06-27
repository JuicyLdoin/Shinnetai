package net.ldoin.shinnetai.packet.extended.stream;

import net.ldoin.shinnetai.buffered.buf.smart.ReadOnlySmartByteBuf;
import net.ldoin.shinnetai.buffered.buf.smart.WriteOnlySmartByteBuf;
import net.ldoin.shinnetai.client.ShinnetaiClient;
import net.ldoin.shinnetai.packet.AbstractPacket;
import net.ldoin.shinnetai.packet.registry.ShinnetaiPacket;
import net.ldoin.shinnetai.worker.ShinnetaiIOWorker;

@ShinnetaiPacket(id = -51)
public class StreamClosePacket extends AbstractPacket<ShinnetaiClient, ShinnetaiIOWorker<?>> {

    private int id;

    public StreamClosePacket() {
    }

    public StreamClosePacket(int id) {
        this.id = id;
    }

    @Override
    public void handleClient() {
        handle();
    }

    @Override
    public void handleServer() {
        handle();
    }

    private void handle() {
        getCurrentWorker().closeStream(id);
    }

    @Override
    public void read(ReadOnlySmartByteBuf buf) {
        this.id = buf.readVarInt();
    }

    @Override
    public void write(WriteOnlySmartByteBuf buf) {
        buf.writeVarInt(id);
    }
}