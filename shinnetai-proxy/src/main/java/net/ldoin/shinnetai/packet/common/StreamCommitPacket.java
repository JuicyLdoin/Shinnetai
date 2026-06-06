package net.ldoin.shinnetai.packet.common;

import net.ldoin.shinnetai.buffered.buf.smart.ReadOnlySmartByteBuf;
import net.ldoin.shinnetai.buffered.buf.smart.WriteOnlySmartByteBuf;
import net.ldoin.shinnetai.packet.EmptyPacket;
import net.ldoin.shinnetai.packet.registry.ShinnetaiPacket;
import net.ldoin.shinnetai.packet.serializer.BinaryPacketSerializer;
import net.ldoin.shinnetai.packet.serializer.PacketSerializer;

@ShinnetaiPacket(id = -9)
public class StreamCommitPacket extends EmptyPacket {

    private int streamId;

    public StreamCommitPacket() {
    }

    public StreamCommitPacket(int streamId) {
        this.streamId = streamId;
    }

    public int getStreamId() {
        return streamId;
    }

    @Override
    public void read(ReadOnlySmartByteBuf buf) {
        this.streamId = buf.readVarInt();
    }

    @Override
    public void write(WriteOnlySmartByteBuf buf) {
        buf.writeVarInt(streamId);
    }

    @Override
    public PacketSerializer serializer() {
        return BinaryPacketSerializer.INSTANCE;
    }
}
