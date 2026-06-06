package net.ldoin.shinnetai.delivery;

import net.ldoin.shinnetai.packet.WrappedPacket;

public record PacketOutboxResult(WrappedPacket packet, PacketOutboxStoreResult result) {

    public boolean stored() {
        return result == PacketOutboxStoreResult.STORED || result == PacketOutboxStoreResult.ALREADY_STORED;
    }
}