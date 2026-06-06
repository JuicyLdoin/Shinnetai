package net.ldoin.shinnetai.packet.serializer;

import net.ldoin.shinnetai.buffered.buf.smart.ReadOnlySmartByteBuf;
import net.ldoin.shinnetai.buffered.buf.smart.WriteOnlySmartByteBuf;
import net.ldoin.shinnetai.packet.AbstractPacket;

import java.io.IOException;

public final class BinaryPacketSerializer implements PacketSerializer {

    public static final BinaryPacketSerializer INSTANCE = new BinaryPacketSerializer();

    private BinaryPacketSerializer() {
    }

    @Override
    public String name() {
        return "binary";
    }

    @Override
    public byte[] serialize(AbstractPacket<?, ?> packet) throws IOException {
        WriteOnlySmartByteBuf buf = WriteOnlySmartByteBuf.empty();
        packet.write(buf);
        return buf.toBytes();
    }

    @Override
    public void deserialize(AbstractPacket<?, ?> packet, byte[] data, int offset, int length) throws IOException {
        packet.read(ReadOnlySmartByteBuf.of(data, offset, length));
    }
}