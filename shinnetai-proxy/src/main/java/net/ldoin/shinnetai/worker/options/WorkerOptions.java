package net.ldoin.shinnetai.worker.options;

import net.ldoin.shinnetai.worker.pipeline.ShinnetaiPipeline;

import java.io.File;

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
    private final boolean sslSocket;
    private final File sslKeystore;
    private final String sslKeystorePassword;
    private final String sslKeyPassword;
    private final ShinnetaiPipeline pipeline;
    private final int maxPacketSize;

    protected WorkerOptions(Builder<?> builder) {
        this.readerThreads = builder.readerThreads;
        this.writerThreads = builder.writerThreads;
        this.virtualThreads = builder.virtualThreads;
        this.sslSocket = builder.sslSocket;
        this.sslKeystore = builder.sslKeystore;
        this.sslKeystorePassword = builder.sslKeystorePassword;
        this.sslKeyPassword = builder.sslKeyPassword;
        this.pipeline = builder.pipeline;
        this.maxPacketSize = builder.maxPacketSize;
    }

    public int getReaderThreads() {
        return readerThreads;
    }
    
    public int getMaxPacketSize() {
        return maxPacketSize;
    }

    public int getWriterThreads() {
        return writerThreads;
    }

    public boolean isVirtualThreads() {
        return virtualThreads;
    }

    public boolean isSSL() {
        return sslSocket;
    }

    public File getSSLKeystore() {
        return sslKeystore;
    }

    public String getSSLKeystorePassword() {
        return sslKeystorePassword;
    }

    public String getSSLKeyPassword() {
        return sslKeyPassword;
    }

    public ShinnetaiPipeline getPipeline() {
        return pipeline;
    }

    public static class Builder<B extends Builder<?>> {

        private int readerThreads = 1;
        private int writerThreads = 1;
        private boolean virtualThreads = true;
        private boolean sslSocket = false;
        private File sslKeystore;
        private String sslKeystorePassword;
        private String sslKeyPassword;
        private ShinnetaiPipeline pipeline;
        private int maxPacketSize = 64 * 1024;

        public B setReaderThreads(int readerThreads) {
            this.readerThreads = readerThreads;
            return self();
        }
        
        public B setMaxPacketSize(int maxPacketSize) {
            this.maxPacketSize = maxPacketSize;
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

        public B setSSL(boolean sslSocket) {
            this.sslSocket = sslSocket;
            return self();
        }

        public B setSSLKeystore(File sslKeystore) {
            this.sslKeystore = sslKeystore;
            return self();
        }

        public B setSSLKeystorePassword(String sslKeystorePassword) {
            this.sslKeystorePassword = sslKeystorePassword;
            return self();
        }

        public B setSSLKeyPassword(String sslKeyPassword) {
            this.sslKeyPassword = sslKeyPassword;
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