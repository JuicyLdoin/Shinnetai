package net.ldoin.shinnetai.security;

import net.ldoin.shinnetai.packet.AbstractPacket;
import net.ldoin.shinnetai.worker.ShinnetaiWorkerContext;

@FunctionalInterface
public interface PacketAuthorizer {

    PacketAuthorizer ALLOW_ALL = (auth, packet, context) -> true;

    boolean canHandle(AuthenticationContext auth, AbstractPacket<?, ?> packet, ShinnetaiWorkerContext<?> context);

}