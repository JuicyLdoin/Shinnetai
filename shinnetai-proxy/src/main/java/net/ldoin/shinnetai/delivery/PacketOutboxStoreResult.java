package net.ldoin.shinnetai.delivery;

public enum PacketOutboxStoreResult {

    STORED,
    ALREADY_STORED,
    REJECTED_INVALID_PACKET_ID,
    REJECTED_MAX_SESSIONS,
    REJECTED_MAX_PACKETS,
    DISCARDED_MAX_RETRIES,
    DISCARDED_EXPIRED

}