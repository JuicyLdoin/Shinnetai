package net.ldoin.shinnetai.packet;

public enum PacketOptions {

    WAIT_RESPONSE,
    IS_RESPONSE,
    IN_STREAM,
    REQUIRE_RESPONSE,
    ;

    public static final PacketOptions[] VALUES = values();

}