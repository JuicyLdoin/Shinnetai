package net.ldoin.shinnetai.packet.common;

import net.ldoin.shinnetai.buffered.buf.smart.ReadOnlySmartByteBuf;
import net.ldoin.shinnetai.buffered.buf.smart.WriteOnlySmartByteBuf;
import net.ldoin.shinnetai.packet.EmptyPacket;
import net.ldoin.shinnetai.packet.registry.ShinnetaiPacket;
import net.ldoin.shinnetai.packet.serializer.BinaryPacketSerializer;
import net.ldoin.shinnetai.packet.serializer.PacketSerializer;

@ShinnetaiPacket(id = -8)
public class DeliveryAckPacket extends EmptyPacket {

    private long packetId;

    public DeliveryAckPacket() {
    }

    public DeliveryAckPacket(long packetId) {
        this.packetId = packetId;
    }

    public long getDeliveryPacketId() {
        return packetId;
    }

    @Override
    public void read(ReadOnlySmartByteBuf buf) {
        this.packetId = buf.readVarLong();
    }

    @Override
    public void write(WriteOnlySmartByteBuf buf) {
        buf.writeVarLong(packetId);
    }

    @Override
    public PacketSerializer serializer() {
        return BinaryPacketSerializer.INSTANCE;
    }
}