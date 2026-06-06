package net.ldoin.shinnetai.serializer;

import net.ldoin.shinnetai.packet.serializer.BinaryPacketSerializer;
import net.ldoin.shinnetai.packet.serializer.PacketSerializer;

public final class PacketSerializers {

    public static final PacketSerializer BINARY = BinaryPacketSerializer.INSTANCE;
    public static final PacketSerializer JSON = JsonPacketSerializer.INSTANCE;
    public static final PacketSerializer SMILE = SmilePacketSerializer.INSTANCE;
    public static final PacketSerializer CBOR = CborPacketSerializer.INSTANCE;
    public static final PacketSerializer MSGPACK = MessagePackPacketSerializer.INSTANCE;
    public static final PacketSerializer FLEXBUFFERS = FlexBuffersPacketSerializer.INSTANCE;

    private PacketSerializers() {
    }
}