package net.ldoin.shinnetai.worker.pipeline.handler;

import net.ldoin.shinnetai.packet.AbstractPacket;
import net.ldoin.shinnetai.worker.pipeline.context.ShinnetaiPipelineContext;

public interface ShinnetaiPipelineHandler {

    default AbstractPacket<?, ?> handle(AbstractPacket<?, ?> packet, ShinnetaiPipelineContext context) {
        return packet;
    }

    default String name() {
        return getClass().getSimpleName();
    }
}