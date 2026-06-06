package net.ldoin.shinnetai.worker.pipeline.context;

import net.ldoin.shinnetai.packet.AbstractPacket;
import net.ldoin.shinnetai.worker.pipeline.ShinnetaiPipeline;
import net.ldoin.shinnetai.worker.pipeline.ShinnetaiPipelineHandleType;

public interface ShinnetaiPipelineContext {

    default void switchTo(ShinnetaiPipeline newPipeline) {
        switchTo(newPipeline, 0);
    }

    void switchTo(ShinnetaiPipeline newPipeline, int index);

    default void skipNext() {
        jumpTo(currentIndex() + 2);
    }

    void jumpTo(int index);

    int currentIndex();

    default AbstractPacket<?, ?> handle(ShinnetaiPipelineHandleType type, AbstractPacket<?, ?> packet, ShinnetaiPipeline pipeline) {
        return handle(type, packet, pipeline, 0);
    }

    AbstractPacket<?, ?> handle(ShinnetaiPipelineHandleType type, AbstractPacket<?, ?> packet, ShinnetaiPipeline pipeline, int startIndex);
}