package net.ldoin.shinnetai.packet.serializer;

import net.ldoin.shinnetai.packet.AbstractPacket;

import java.io.IOException;

public interface PacketSerializer {

    String name();

    byte[] serialize(AbstractPacket<?, ?> packet) throws IOException;

    void deserialize(AbstractPacket<?, ?> packet, byte[] data, int offset, int length) throws IOException;
    
}