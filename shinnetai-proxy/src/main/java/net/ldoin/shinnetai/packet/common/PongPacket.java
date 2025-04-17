package net.ldoin.shinnetai.packet.common;

import net.ldoin.shinnetai.buffered.buf.smart.ReadOnlySmartByteBuf;
import net.ldoin.shinnetai.buffered.buf.smart.WriteOnlySmartByteBuf;
import net.ldoin.shinnetai.packet.EmptyPacket;
import net.ldoin.shinnetai.packet.registry.ShinnetaiPacket;

@ShinnetaiPacket(id = -3)
public class PongPacket extends EmptyPacket {

    private long timestamp;

    public PongPacket() {
    }

    public PongPacket(long timestamp) {
        this.timestamp = timestamp;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public void read(ReadOnlySmartByteBuf buf) {
        this.timestamp = buf.readLong();
    }

    @Override
    public void write(WriteOnlySmartByteBuf buf) {
        buf.writeLong(timestamp);
    }
}
