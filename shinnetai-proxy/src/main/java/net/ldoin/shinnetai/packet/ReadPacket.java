package net.ldoin.shinnetai.packet;

import net.ldoin.shinnetai.packet.response.PacketResponseOptions;

public record ReadPacket(int id, AbstractPacket<?, ?> packet, PacketResponseOptions responseOptions) {
}