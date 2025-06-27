package net.ldoin.shinnetai.worker.pipeline.context;

import net.ldoin.shinnetai.packet.AbstractPacket;
import net.ldoin.shinnetai.worker.pipeline.ShinnetaiPipeline;
import net.ldoin.shinnetai.worker.pipeline.ShinnetaiPipelineHandleType;
import net.ldoin.shinnetai.worker.pipeline.handler.ShinnetaiPipelineHandlerUnit;

import java.util.LinkedList;

public class ShinnetaiPipelineExecutionContext implements ShinnetaiPipelineContext {

    protected ShinnetaiPipeline currentPipeline;
    private int index;

    @Override
    public void switchTo(ShinnetaiPipeline newPipeline, int index) {
        this.currentPipeline = newPipeline;
        this.index = index;
    }

    @Override
    public void jumpTo(int newIndex) {
        if (newIndex < 0) {
            throw new IllegalArgumentException("Index cannot be negative");
        }

        this.index = newIndex;
    }

    @Override
    public int currentIndex() {
        return index;
    }

    @Override
    public AbstractPacket<?, ?> handle(ShinnetaiPipelineHandleType type, AbstractPacket<?, ?> packet, ShinnetaiPipeline pipeline, int startIndex) {
        this.currentPipeline = pipeline;
        this.index = startIndex;
        if (currentPipeline != null) {
            LinkedList<ShinnetaiPipelineHandlerUnit> list = currentPipeline.getHandlers(type);
            if (list == null || index >= list.size()) {
                return packet;
            }

            while (index < list.size()) {
                int currentIndex = index;

                ShinnetaiPipelineHandlerUnit handler = list.get(index);
                packet = handler.handle(packet, this);
                if (packet == null) {
                    break;
                }

                if (currentIndex == index) {
                    index++;
                }
            }
        }

        return packet;
    }
}