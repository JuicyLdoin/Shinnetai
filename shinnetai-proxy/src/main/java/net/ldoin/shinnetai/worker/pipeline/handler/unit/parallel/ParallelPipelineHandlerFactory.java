package net.ldoin.shinnetai.worker.pipeline.handler.unit.parallel;

@FunctionalInterface
public interface ParallelPipelineHandlerFactory {

    ParallelPipelineHandler create(int index);

}