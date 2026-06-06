package net.ldoin.shinnetai.packet.common;

import net.ldoin.shinnetai.buffered.buf.smart.ReadOnlySmartByteBuf;
import net.ldoin.shinnetai.buffered.buf.smart.WriteOnlySmartByteBuf;
import net.ldoin.shinnetai.packet.EmptyPacket;
import net.ldoin.shinnetai.packet.registry.ShinnetaiPacket;
import net.ldoin.shinnetai.packet.serializer.BinaryPacketSerializer;
import net.ldoin.shinnetai.packet.serializer.PacketSerializer;

@ShinnetaiPacket(id = -10)
public class StreamCommitAckPacket extends EmptyPacket {

    private int streamId;
    private boolean success;
    private String message;

    public StreamCommitAckPacket() {
    }

    public StreamCommitAckPacket(int streamId, boolean success, String message) {
        this.streamId = streamId;
        this.success = success;
        this.message = message;
    }

    public int getStreamId() {
        return streamId;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public void read(ReadOnlySmartByteBuf buf) {
        this.streamId = buf.readVarInt();
        this.success = buf.readBoolean();
        if (!success) {
            this.message = buf.readString();
        }
    }

    @Override
    public void write(WriteOnlySmartByteBuf buf) {
        buf.writeVarInt(streamId);
        buf.writeBoolean(success);
        if (!success) {
            buf.writeString(message != null ? message : "");
        }
    }

    @Override
    public PacketSerializer serializer() {
        return BinaryPacketSerializer.INSTANCE;
    }
}
