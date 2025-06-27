package net.ldoin.shinnetai.worker.options;

import net.ldoin.shinnetai.worker.pipeline.ShinnetaiPipeline;

public class WorkerOptions {

    public static Builder<?> builder() {
        return new Builder<>();
    }

    public static WorkerOptions empty() {
        return new Builder<>().build();
    }

    private final int readerThreads;
    private final int writerThreads;
    private final boolean virtualThreads;
    private final ShinnetaiPipeline pipeline;

    protected WorkerOptions(Builder<?> builder) {
        this.readerThreads = builder.readerThreads;
        this.writerThreads = builder.writerThreads;
        this.virtualThreads = builder.virtualThreads;
        this.pipeline = builder.pipeline;
    }

    public int getReaderThreads() {
        return readerThreads;
    }

    public int getWriterThreads() {
        return writerThreads;
    }

    public boolean isVirtualThreads() {
        return virtualThreads;
    }

    public ShinnetaiPipeline getPipeline() {
        return pipeline;
    }

    public static class Builder<B extends Builder<?>> {

        private int readerThreads = 1;
        private int writerThreads = 1;
        private boolean virtualThreads = true;
        private ShinnetaiPipeline pipeline;

        public B setReaderThreads(int readerThreads) {
            this.readerThreads = readerThreads;
            return self();
        }

        public B setWriterThreads(int writerThreads) {
            this.writerThreads = writerThreads;
            return self();
        }

        public B setVirtualThreads(boolean virtualThreads) {
            this.virtualThreads = virtualThreads;
            return self();
        }

        public B setPipeline(ShinnetaiPipeline pipeline) {
            this.pipeline = pipeline;
            return self();
        }

        @SuppressWarnings("unchecked")
        protected B self() {
            return (B) this;
        }

        public WorkerOptions build() {
            return new WorkerOptions(this);
        }
    }
}