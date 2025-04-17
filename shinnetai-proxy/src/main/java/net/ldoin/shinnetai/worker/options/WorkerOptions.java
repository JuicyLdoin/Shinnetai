package net.ldoin.shinnetai.worker.options;

import net.ldoin.shinnetai.worker.pipeline.ShinnetaiPipeline;

import java.io.File;
import java.util.function.Consumer;

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
    private final int readTimeout;
    private final boolean keepAlive;
    private final int protocolVersion;
    private final int packetMagic;
    private final Consumer<Throwable> exceptionHandler;
    private final int maxQueueSize;

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
        this.readTimeout = builder.readTimeout;
        this.keepAlive = builder.keepAlive;
        this.protocolVersion = builder.protocolVersion;
        this.packetMagic = builder.packetMagic;
        this.exceptionHandler = builder.exceptionHandler;
        this.maxQueueSize = builder.maxQueueSize;
    }

    public int getReaderThreads() {
        return readerThreads;
    }

    public int getMaxPacketSize() {
        return maxPacketSize;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public boolean isKeepAlive() {
        return keepAlive;
    }

    public int getProtocolVersion() {
        return protocolVersion;
    }

    public int getPacketMagic() {
        return packetMagic;
    }

    public Consumer<Throwable> getExceptionHandler() {
        return exceptionHandler;
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

    public int getMaxQueueSize() {
        return maxQueueSize;
    }

    public static class Builder<B extends Builder<?>> {

        private int readerThreads = 1;
        private int readTimeout = 30000;
        private boolean keepAlive = true;
        private int writerThreads = 1;
        private boolean virtualThreads = true;
        private boolean sslSocket = false;
        private File sslKeystore;
        private String sslKeystorePassword;
        private String sslKeyPassword;
        private ShinnetaiPipeline pipeline;
        private int maxPacketSize = 64 * 1024;
        private int protocolVersion = 1;
        private int packetMagic = 0xCAFEBABE;
        private Consumer<Throwable> exceptionHandler;
        private int maxQueueSize = 10000;

        public B setReaderThreads(int readerThreads) {
            this.readerThreads = readerThreads;
            return self();
        }

        public B setMaxPacketSize(int maxPacketSize) {
            this.maxPacketSize = maxPacketSize;
            return self();
        }

        public B setReadTimeout(int readTimeout) {
            this.readTimeout = readTimeout;
            return self();
        }

        public B setKeepAlive(boolean keepAlive) {
            this.keepAlive = keepAlive;
            return self();
        }

        public B setProtocolVersion(int protocolVersion) {
            this.protocolVersion = protocolVersion;
            return self();
        }

        public B setPacketMagic(int packetMagic) {
            this.packetMagic = packetMagic;
            return self();
        }

        public B setExceptionHandler(Consumer<Throwable> exceptionHandler) {
            this.exceptionHandler = exceptionHandler;
            return self();
        }

        public B setMaxQueueSize(int maxQueueSize) {
            this.maxQueueSize = maxQueueSize;
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