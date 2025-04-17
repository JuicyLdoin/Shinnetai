package net.ldoin.shinnetai.worker.pipeline.handler.unit.parallel;

import net.ldoin.shinnetai.worker.pipeline.handler.ShinnetaiPipelineHandler;

public abstract class ParallelPipelineHandler implements ShinnetaiPipelineHandler {

    protected final int index;

    public ParallelPipelineHandler(int index) {
        this.index = index;
    }
}