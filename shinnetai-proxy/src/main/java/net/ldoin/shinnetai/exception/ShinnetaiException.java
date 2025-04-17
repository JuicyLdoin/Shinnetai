package net.ldoin.shinnetai.exception;

import net.ldoin.shinnetai.client.ShinnetaiClient;
import net.ldoin.shinnetai.packet.common.ExceptionPacket;
import net.ldoin.shinnetai.worker.ShinnetaiWorkerContext;

public interface ShinnetaiException {

    int getId();

    String getMessage();

    void handleClient(ShinnetaiClient client);

    void handleServer(ShinnetaiWorkerContext<?> context);

    default ExceptionPacket toPacket() {
        return new ExceptionPacket(this);
    }

    default ExceptionPacket toPacket(Object... objects) {
        return new ExceptionPacket(this, objects);
    }
}