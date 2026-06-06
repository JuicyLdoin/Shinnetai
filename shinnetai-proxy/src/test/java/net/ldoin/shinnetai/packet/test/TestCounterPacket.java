package net.ldoin.shinnetai.packet.test;

import net.ldoin.shinnetai.buffered.buf.smart.ReadOnlySmartByteBuf;
import net.ldoin.shinnetai.buffered.buf.smart.WriteOnlySmartByteBuf;
import net.ldoin.shinnetai.packet.EmptyPacket;
import net.ldoin.shinnetai.packet.registry.ShinnetaiPacket;

@ShinnetaiPacket(id = 9001)
public class TestCounterPacket extends EmptyPacket {

    private int value;

    public TestCounterPacket() {
    }

    public TestCounterPacket(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    @Override
    public void read(ReadOnlySmartByteBuf buf) {
        value = buf.readVarInt();
    }

    @Override
    public void write(WriteOnlySmartByteBuf buf) {
        buf.writeVarInt(value);
    }
}
