package net.ldoin.shinnetai.worker.options;

import net.ldoin.shinnetai.delivery.DeliveryGuarantee;
import net.ldoin.shinnetai.debug.TrafficLog;
import net.ldoin.shinnetai.handler.PacketHandlerRegistry;
import net.ldoin.shinnetai.packet.AbstractPacket;
import net.ldoin.shinnetai.packet.WrappedPacket;
import net.ldoin.shinnetai.packet.serializer.BinaryPacketSerializer;
import net.ldoin.shinnetai.packet.serializer.PacketSerializer;
import net.ldoin.shinnetai.protocol.ShinnetaiFeature;
import net.ldoin.shinnetai.protocol.ShinnetaiProtocol;
import net.ldoin.shinnetai.resilience.RetryPolicy;
import net.ldoin.shinnetai.security.PacketAuthorizer;
import net.ldoin.shinnetai.security.RateLimitCost;
import net.ldoin.shinnetai.security.RateLimiter;
import net.ldoin.shinnetai.worker.QueueOverflowStrategy;
import net.ldoin.shinnetai.worker.pipeline.ShinnetaiPipeline;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

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
    private final Path sslKeystore;
    private final char[] sslKeystorePassword;
    private final char[] sslKeyPassword;
    private final ShinnetaiPipeline pipeline;
    private final int maxPacketSize;
    private final int readTimeout;
    private final boolean keepAlive;
    private final int protocolVersion;
    private final int packetMagic;
    private final Consumer<Throwable> exceptionHandler;
    private final int maxQueueSize;
    private final QueueOverflowStrategy queueOverflowStrategy;
    private final int compressionThreshold;
    private final Consumer<WrappedPacket> onPacketDropped;
    private final int sendTimeoutMs;
    private final RetryPolicy defaultRetryPolicy;
    private final RateLimiter rateLimiter;
    private final Supplier<RateLimiter> rateLimiterFactory;
    private final TrafficLog trafficLog;
    private final PacketHandlerRegistry packetHandlerRegistry;
    private final long supportedFeatures;
    private final boolean reliableDelivery;
    private final long deduplicationWindowMs;
    private final int deduplicationMaxEntries;
    private final DeliveryGuarantee defaultDeliveryGuarantee;
    private final Predicate<AbstractPacket<?, ?>> reliablePacketPredicate;
    private final long reliableRetransmitIntervalMs;
    private final int reliableMaxRetries;
    private final boolean requireSessionToken;
    private final PacketSerializer packetSerializer;
    private final long handshakeTimestampWindowMs;
    private final long maxHandshakeDurationMs;
    private final int maxPendingHandshakes;
    private final double queueHighWatermark;
    private final double queueLowWatermark;
    private final Runnable onOverloaded;
    private final Runnable onRecovered;
    private final Runnable onQueueTimeout;
    private final boolean requireTlsForSessionTokens;
    private final PacketAuthorizer packetAuthorizer;
    private final Function<AbstractPacket<?, ?>, RateLimitCost> rateLimitCostResolver;

    protected WorkerOptions(Builder<?> builder) {
        this.readerThreads = builder.readerThreads;
        this.writerThreads = builder.writerThreads;
        this.virtualThreads = builder.virtualThreads;
        this.sslSocket = builder.sslSocket;
        this.sslKeystore = builder.sslKeystore;
        this.sslKeystorePassword = builder.sslKeystorePassword != null ? builder.sslKeystorePassword.clone() : null;
        this.sslKeyPassword = builder.sslKeyPassword != null ? builder.sslKeyPassword.clone() : null;
        this.pipeline = builder.pipeline;
        this.maxPacketSize = builder.maxPacketSize;
        this.readTimeout = builder.readTimeout;
        this.keepAlive = builder.keepAlive;
        this.protocolVersion = builder.protocolVersion;
        this.packetMagic = builder.packetMagic;
        this.exceptionHandler = builder.exceptionHandler;
        this.maxQueueSize = builder.maxQueueSize;
        this.queueOverflowStrategy = builder.queueOverflowStrategy;
        this.compressionThreshold = builder.compressionThreshold;
        this.onPacketDropped = builder.onPacketDropped;
        this.sendTimeoutMs = builder.sendTimeoutMs;
        this.defaultRetryPolicy = builder.defaultRetryPolicy;
        this.rateLimiter = builder.rateLimiter;
        this.rateLimiterFactory = builder.rateLimiterFactory;
        this.trafficLog = builder.trafficLog;
        this.packetHandlerRegistry = builder.packetHandlerRegistry;
        this.supportedFeatures = builder.supportedFeatures;
        this.reliableDelivery = builder.reliableDelivery;
        this.deduplicationWindowMs = builder.deduplicationWindowMs;
        this.deduplicationMaxEntries = builder.deduplicationMaxEntries;
        this.defaultDeliveryGuarantee = builder.defaultDeliveryGuarantee;
        this.reliablePacketPredicate = builder.reliablePacketPredicate;
        this.reliableRetransmitIntervalMs = builder.reliableRetransmitIntervalMs;
        this.reliableMaxRetries = builder.reliableMaxRetries;
        this.requireSessionToken = builder.requireSessionToken;
        this.packetSerializer = builder.packetSerializer;
        this.handshakeTimestampWindowMs = builder.handshakeTimestampWindowMs;
        this.maxHandshakeDurationMs = builder.maxHandshakeDurationMs;
        this.maxPendingHandshakes = builder.maxPendingHandshakes;
        this.queueHighWatermark = builder.queueHighWatermark;
        this.queueLowWatermark = builder.queueLowWatermark;
        this.onOverloaded = builder.onOverloaded;
        this.onRecovered = builder.onRecovered;
        this.onQueueTimeout = builder.onQueueTimeout;
        this.requireTlsForSessionTokens = builder.requireTlsForSessionTokens;
        this.packetAuthorizer = builder.packetAuthorizer;
        this.rateLimitCostResolver = builder.rateLimitCostResolver;

        if (requireSessionToken && requireTlsForSessionTokens && !sslSocket) {
            throw new IllegalStateException("Session tokens require TLS when requireTlsForSessionTokens is enabled");
        }
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

    public Path getSSLKeystore() {
        return sslKeystore;
    }

    public char[] getSSLKeystorePassword() {
        return sslKeystorePassword != null ? sslKeystorePassword.clone() : null;
    }

    public char[] getSSLKeyPassword() {
        return sslKeyPassword != null ? sslKeyPassword.clone() : null;
    }

    public ShinnetaiPipeline getPipeline() {
        return pipeline;
    }

    public int getMaxQueueSize() {
        return maxQueueSize;
    }

    public QueueOverflowStrategy getQueueOverflowStrategy() {
        return queueOverflowStrategy;
    }

    public int getCompressionThreshold() {
        return compressionThreshold;
    }

    public Consumer<WrappedPacket> getOnPacketDropped() {
        return onPacketDropped;
    }

    public int getSendTimeoutMs() {
        return sendTimeoutMs;
    }

    public RetryPolicy getDefaultRetryPolicy() {
        return defaultRetryPolicy;
    }

    public RateLimiter getRateLimiter() {
        return rateLimiter;
    }

    public RateLimiter createRateLimiter() {
        return rateLimiterFactory != null ? rateLimiterFactory.get() : null;
    }

    public TrafficLog getTrafficLog() {
        return trafficLog;
    }

    public PacketHandlerRegistry getPacketHandlerRegistry() {
        return packetHandlerRegistry;
    }

    public long getSupportedFeatures() {
        long base = supportedFeatures;
        if (compressionThreshold > 0) {
            base |= ShinnetaiFeature.COMPRESSION.flag();
        }
        if (reliableDelivery) {
            base |= ShinnetaiFeature.RELIABLE_DELIVERY.flag();
        }
        return base;
    }

    public boolean isReliableDelivery() {
        return reliableDelivery;
    }

    public long getDeduplicationWindowMs() {
        return deduplicationWindowMs;
    }

    public int getDeduplicationMaxEntries() {
        return deduplicationMaxEntries;
    }

    public DeliveryGuarantee getDefaultDeliveryGuarantee() {
        return defaultDeliveryGuarantee;
    }

    public boolean shouldTrackDelivery(AbstractPacket<?, ?> packet) {
        if (!reliableDelivery || packet == null) {
            return false;
        }

        if (defaultDeliveryGuarantee == DeliveryGuarantee.RELIABLE) {
            return reliablePacketPredicate == null || reliablePacketPredicate.test(packet);
        }

        return reliablePacketPredicate != null && reliablePacketPredicate.test(packet);
    }

    public long getReliableRetransmitIntervalMs() {
        return reliableRetransmitIntervalMs;
    }

    public int getReliableMaxRetries() {
        return reliableMaxRetries;
    }

    public boolean isRequireSessionToken() {
        return requireSessionToken;
    }

    public PacketSerializer getPacketSerializer() {
        return packetSerializer;
    }

    public long getHandshakeTimestampWindowMs() {
        return handshakeTimestampWindowMs;
    }

    public long getMaxHandshakeDurationMs() {
        return maxHandshakeDurationMs;
    }

    public int getMaxPendingHandshakes() {
        return maxPendingHandshakes;
    }

    public double getQueueHighWatermark() {
        return queueHighWatermark;
    }

    public double getQueueLowWatermark() {
        return queueLowWatermark;
    }

    public Runnable getOnOverloaded() {
        return onOverloaded;
    }

    public Runnable getOnRecovered() {
        return onRecovered;
    }

    public Runnable getOnQueueTimeout() {
        return onQueueTimeout;
    }

    public boolean isRequireTlsForSessionTokens() {
        return requireTlsForSessionTokens;
    }

    public PacketAuthorizer getPacketAuthorizer() {
        return packetAuthorizer;
    }

    public RateLimitCost resolveRateLimitCost(AbstractPacket<?, ?> packet) {
        if (packet == null || rateLimitCostResolver == null) {
            return RateLimitCost.none();
        }

        RateLimitCost cost = rateLimitCostResolver.apply(packet);
        return cost != null ? cost : RateLimitCost.none();
    }

    public static class Builder<B extends Builder<?>> {

        private static final int MAX_ALLOWED_PACKET_SIZE = ShinnetaiProtocol.ABSOLUTE_MAX_FRAME_SIZE;

        private int readerThreads = 1;
        private int readTimeout = 30000;
        private boolean keepAlive = true;
        private int writerThreads = 1;
        private boolean virtualThreads = true;
        private boolean sslSocket = false;
        private Path sslKeystore;
        private char[] sslKeystorePassword;
        private char[] sslKeyPassword;
        private ShinnetaiPipeline pipeline;
        private int maxPacketSize = ShinnetaiProtocol.DEFAULT_MAX_FRAME_SIZE;
        private int protocolVersion = ShinnetaiProtocol.VERSION;
        private int packetMagic = ShinnetaiProtocol.DEFAULT_MAGIC;
        private Consumer<Throwable> exceptionHandler;
        private int maxQueueSize = 10000;
        private QueueOverflowStrategy queueOverflowStrategy = QueueOverflowStrategy.WARN;
        private int compressionThreshold = 0;
        private Consumer<WrappedPacket> onPacketDropped;
        private int sendTimeoutMs = 5000;
        private RetryPolicy defaultRetryPolicy;
        private RateLimiter rateLimiter = new RateLimiter(20_000, 64L * 1024L * 1024L);
        private Supplier<RateLimiter> rateLimiterFactory = rateLimiter::copy;
        private TrafficLog trafficLog;
        private PacketHandlerRegistry packetHandlerRegistry = new PacketHandlerRegistry();
        private long supportedFeatures = 0;
        private boolean reliableDelivery = true;
        private long deduplicationWindowMs = 120_000L;
        private int deduplicationMaxEntries = 20_000;
        private DeliveryGuarantee defaultDeliveryGuarantee = DeliveryGuarantee.RELIABLE;
        private Predicate<AbstractPacket<?, ?>> reliablePacketPredicate;
        private long reliableRetransmitIntervalMs = 1500L;
        private int reliableMaxRetries = 0;
        private boolean requireSessionToken = false;
        private PacketSerializer packetSerializer = BinaryPacketSerializer.INSTANCE;
        private long handshakeTimestampWindowMs = 30_000L;
        private long maxHandshakeDurationMs = 10_000L;
        private int maxPendingHandshakes = 1024;
        private double queueHighWatermark = 0.8D;
        private double queueLowWatermark = 0.5D;
        private Runnable onOverloaded;
        private Runnable onRecovered;
        private Runnable onQueueTimeout;
        private boolean requireTlsForSessionTokens = false;
        private PacketAuthorizer packetAuthorizer = PacketAuthorizer.ALLOW_ALL;
        private Function<AbstractPacket<?, ?>, RateLimitCost> rateLimitCostResolver = AbstractPacket::rateLimitCost;

        public B readerThreads(int readerThreads) {
            if (readerThreads < 1) {
                throw new IllegalArgumentException("readerThreads must be >= 1");
            }

            this.readerThreads = readerThreads;
            return self();
        }

        public B maxPacketSize(int maxPacketSize) {
            if (maxPacketSize <= 0 || maxPacketSize > MAX_ALLOWED_PACKET_SIZE) {
                throw new IllegalArgumentException("maxPacketSize must be between 1 and " + MAX_ALLOWED_PACKET_SIZE);
            }

            this.maxPacketSize = maxPacketSize;
            return self();
        }

        public B readTimeout(int readTimeout) {
            if (readTimeout < 1) {
                throw new IllegalArgumentException("readTimeout must be >= 1");
            }

            this.readTimeout = readTimeout;
            return self();
        }

        public B keepAlive(boolean keepAlive) {
            this.keepAlive = keepAlive;
            return self();
        }

        public B protocolVersion(int protocolVersion) {
            this.protocolVersion = protocolVersion;
            return self();
        }

        public B packetMagic(int packetMagic) {
            this.packetMagic = packetMagic;
            return self();
        }

        public B exceptionHandler(Consumer<Throwable> exceptionHandler) {
            this.exceptionHandler = exceptionHandler;
            return self();
        }

        public B maxQueueSize(int maxQueueSize) {
            if (maxQueueSize < 1) {
                throw new IllegalArgumentException("maxQueueSize must be >= 1");
            }

            this.maxQueueSize = maxQueueSize;
            return self();
        }

        public B queueOverflowStrategy(QueueOverflowStrategy queueOverflowStrategy) {
            if (queueOverflowStrategy == null) {
                throw new IllegalArgumentException("queueOverflowStrategy cannot be null");
            }

            this.queueOverflowStrategy = queueOverflowStrategy;
            return self();
        }

        public B compressionThreshold(int compressionThreshold) {
            if (compressionThreshold < 0 || compressionThreshold > maxPacketSize) {
                throw new IllegalArgumentException("compressionThreshold must be between 0 and maxPacketSize");
            }

            this.compressionThreshold = compressionThreshold;
            return self();
        }

        public B onPacketDropped(Consumer<WrappedPacket> onPacketDropped) {
            this.onPacketDropped = onPacketDropped;
            return self();
        }

        public B sendTimeoutMs(int sendTimeoutMs) {
            if (sendTimeoutMs < 0) {
                throw new IllegalArgumentException("sendTimeoutMs must be >= 0");
            }

            this.sendTimeoutMs = sendTimeoutMs;
            return self();
        }

        public B defaultRetryPolicy(RetryPolicy retryPolicy) {
            this.defaultRetryPolicy = retryPolicy;
            return self();
        }

        public B rateLimiter(RateLimiter rateLimiter) {
            this.rateLimiter = rateLimiter;
            this.rateLimiterFactory = rateLimiter != null ? rateLimiter::copy : null;
            return self();
        }

        public B rateLimiterFactory(Supplier<RateLimiter> rateLimiterFactory) {
            this.rateLimiter = null;
            this.rateLimiterFactory = rateLimiterFactory;
            return self();
        }

        public B trafficLog(TrafficLog trafficLog) {
            this.trafficLog = trafficLog;
            return self();
        }

        public B packetHandlerRegistry(PacketHandlerRegistry registry) {
            if (registry == null) {
                throw new IllegalArgumentException("registry cannot be null");
            }

            this.packetHandlerRegistry = registry;
            return self();
        }

        public B registerHandlers(Object handler) {
            this.packetHandlerRegistry.register(handler);
            return self();
        }

        public B supportedFeatures(ShinnetaiFeature... features) {
            this.supportedFeatures = ShinnetaiFeature.toFlags(features);
            return self();
        }

        public B reliableDelivery(boolean reliableDelivery) {
            this.reliableDelivery = reliableDelivery;
            return self();
        }

        public B deduplicationWindowMs(long deduplicationWindowMs) {
            if (deduplicationWindowMs < 0) {
                throw new IllegalArgumentException("deduplicationWindowMs must be >= 0");
            }
            this.deduplicationWindowMs = deduplicationWindowMs;
            return self();
        }

        public B deduplicationMaxEntries(int deduplicationMaxEntries) {
            if (deduplicationMaxEntries < 1) {
                throw new IllegalArgumentException("deduplicationMaxEntries must be >= 1");
            }
            this.deduplicationMaxEntries = deduplicationMaxEntries;
            return self();
        }

        public B defaultDeliveryGuarantee(DeliveryGuarantee defaultDeliveryGuarantee) {
            if (defaultDeliveryGuarantee == null) {
                throw new IllegalArgumentException("defaultDeliveryGuarantee cannot be null");
            }

            this.defaultDeliveryGuarantee = defaultDeliveryGuarantee;
            return self();
        }

        public B reliablePacketPredicate(Predicate<AbstractPacket<?, ?>> reliablePacketPredicate) {
            this.reliablePacketPredicate = reliablePacketPredicate;
            return self();
        }

        public B reliableRetransmitIntervalMs(long reliableRetransmitIntervalMs) {
            if (reliableRetransmitIntervalMs < 1) {
                throw new IllegalArgumentException("reliableRetransmitIntervalMs must be >= 1");
            }
            this.reliableRetransmitIntervalMs = reliableRetransmitIntervalMs;
            return self();
        }

        public B reliableMaxRetries(int reliableMaxRetries) {
            if (reliableMaxRetries < 0) {
                throw new IllegalArgumentException("reliableMaxRetries must be >= 0");
            }
            this.reliableMaxRetries = reliableMaxRetries;
            return self();
        }

        public B requireSessionToken(boolean requireSessionToken) {
            this.requireSessionToken = requireSessionToken;
            return self();
        }

        public B requireTlsForSessionTokens(boolean requireTlsForSessionTokens) {
            this.requireTlsForSessionTokens = requireTlsForSessionTokens;
            return self();
        }

        public B writerThreads(int writerThreads) {
            if (writerThreads < 1) {
                throw new IllegalArgumentException("writerThreads must be >= 1");
            }
            
            this.writerThreads = writerThreads;
            return self();
        }

        public B virtualThreads(boolean virtualThreads) {
            this.virtualThreads = virtualThreads;
            return self();
        }

        public B ssl(boolean sslSocket) {
            this.sslSocket = sslSocket;
            return self();
        }

        public B sslKeystore(Path sslKeystore) {
            this.sslKeystore = sslKeystore;
            return self();
        }

        public B sslKeystorePassword(char[] sslKeystorePassword) {
            this.sslKeystorePassword = sslKeystorePassword != null ? Arrays.copyOf(sslKeystorePassword, sslKeystorePassword.length) : null;
            return self();
        }

        public B sslKeyPassword(char[] sslKeyPassword) {
            this.sslKeyPassword = sslKeyPassword != null ? Arrays.copyOf(sslKeyPassword, sslKeyPassword.length) : null;
            return self();
        }

        public B pipeline(ShinnetaiPipeline pipeline) {
            this.pipeline = pipeline;
            return self();
        }

        public B packetSerializer(PacketSerializer packetSerializer) {
            if (packetSerializer == null) {
                throw new IllegalArgumentException("packetSerializer must not be null");
            }

            this.packetSerializer = packetSerializer;
            return self();
        }

        public B handshakeTimestampWindowMs(long handshakeTimestampWindowMs) {
            if (handshakeTimestampWindowMs < 0) {
                throw new IllegalArgumentException("handshakeTimestampWindowMs must be >= 0 (0 = disabled)");
            }
            this.handshakeTimestampWindowMs = handshakeTimestampWindowMs;
            return self();
        }

        public B maxHandshakeDurationMs(long maxHandshakeDurationMs) {
            if (maxHandshakeDurationMs < 0) {
                throw new IllegalArgumentException("maxHandshakeDurationMs must be >= 0 (0 = disabled)");
            }

            this.maxHandshakeDurationMs = maxHandshakeDurationMs;
            return self();
        }

        public B maxPendingHandshakes(int maxPendingHandshakes) {
            if (maxPendingHandshakes < 0) {
                throw new IllegalArgumentException("maxPendingHandshakes must be >= 0 (0 = unlimited)");
            }

            this.maxPendingHandshakes = maxPendingHandshakes;
            return self();
        }

        public B queueWatermarks(double highWatermark, double lowWatermark) {
            if (highWatermark <= 0D || highWatermark > 1D) {
                throw new IllegalArgumentException("highWatermark must be in (0, 1]");
            }

            if (lowWatermark < 0D || lowWatermark > 1D) {
                throw new IllegalArgumentException("lowWatermark must be in [0, 1]");
            }

            if (lowWatermark > highWatermark) {
                throw new IllegalArgumentException("lowWatermark must be <= highWatermark");
            }

            this.queueHighWatermark = highWatermark;
            this.queueLowWatermark = lowWatermark;
            return self();
        }

        public B onOverloaded(Runnable onOverloaded) {
            this.onOverloaded = onOverloaded;
            return self();
        }

        public B onRecovered(Runnable onRecovered) {
            this.onRecovered = onRecovered;
            return self();
        }

        public B onQueueTimeout(Runnable onQueueTimeout) {
            this.onQueueTimeout = onQueueTimeout;
            return self();
        }

        public B packetAuthorizer(PacketAuthorizer packetAuthorizer) {
            if (packetAuthorizer == null) {
                throw new IllegalArgumentException("packetAuthorizer must not be null");
            }

            this.packetAuthorizer = packetAuthorizer;
            return self();
        }

        public B rateLimitCostResolver(Function<AbstractPacket<?, ?>, RateLimitCost> rateLimitCostResolver) {
            if (rateLimitCostResolver == null) {
                throw new IllegalArgumentException("rateLimitCostResolver must not be null");
            }

            this.rateLimitCostResolver = rateLimitCostResolver;
            return self();
        }

        @SuppressWarnings("unchecked")
        protected B self() {
            return (B) this;
        }

        protected boolean isRequireSessionTokenRequested() {
            return requireSessionToken;
        }

        public WorkerOptions build() {
            if (requireSessionToken && requireTlsForSessionTokens && !sslSocket) {
                throw new IllegalStateException("Session tokens require TLS when requireTlsForSessionTokens is enabled");
            }

            return new WorkerOptions(this);
        }
    }
}
