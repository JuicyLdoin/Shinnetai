package net.ldoin.shinnetai.worker.pipeline.handler.unit;

import net.ldoin.shinnetai.packet.AbstractPacket;
import net.ldoin.shinnetai.worker.pipeline.context.ShinnetaiPipelineContext;
import net.ldoin.shinnetai.worker.pipeline.handler.ShinnetaiPipelineHandler;
import net.ldoin.shinnetai.worker.pipeline.handler.ShinnetaiPipelineHandlerUnit;

public class SingleHandlerUnit implements ShinnetaiPipelineHandlerUnit {

    public static SingleHandlerUnit of(ShinnetaiPipelineHandler handler) {
        return new SingleHandlerUnit(handler);
    }

    private final ShinnetaiPipelineHandler handler;

    protected SingleHandlerUnit(ShinnetaiPipelineHandler handler) {
        this.handler = handler;
    }

    @Override
    public AbstractPacket<?, ?> handle(AbstractPacket<?, ?> packet, ShinnetaiPipelineContext context) {
        return handler.handle(packet, context);
    }

    @Override
    public String name() {
        return handler.name();
    }
}