package net.ldoin.shinnetai.packet.common.stream;

import net.ldoin.shinnetai.ShinnetaiIOWorker;
import net.ldoin.shinnetai.buffered.buf.smart.ReadOnlySmartByteBuf;
import net.ldoin.shinnetai.buffered.buf.smart.WriteOnlySmartByteBuf;
import net.ldoin.shinnetai.client.ShinnetaiClient;
import net.ldoin.shinnetai.packet.AbstractPacket;
import net.ldoin.shinnetai.packet.registry.ShinnetaiPacket;

import java.util.UUID;

@ShinnetaiPacket(id = -51)
public class StreamClosePacket extends AbstractPacket<ShinnetaiClient, ShinnetaiIOWorker<?>> {

    private UUID uuid;

    public StreamClosePacket() {
    }

    public StreamClosePacket(UUID uuid) {
        this.uuid = uuid;
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
        getCurrentWorker().closeStream(uuid);
    }

    @Override
    public void read(ReadOnlySmartByteBuf buf) {
        this.uuid = buf.readUUID();
    }

    @Override
    public void write(WriteOnlySmartByteBuf buf) {
        buf.writeUUID(uuid);
    }
}