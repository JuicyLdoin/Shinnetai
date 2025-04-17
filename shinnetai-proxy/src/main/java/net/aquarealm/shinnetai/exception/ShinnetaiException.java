package net.aquarealm.shinnetai.exception;

import net.aquarealm.shinnetai.packet.common.ExceptionPacket;

public interface ShinnetaiException {

    int getId();

    String getMessage();

    void handle();

    default ExceptionPacket toPacket() {
        return new ExceptionPacket(this);
    }

    default ExceptionPacket toPacket(Object... objects) {
        return new ExceptionPacket(this, objects);
    }
}