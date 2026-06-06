package net.ldoin.shinnetai.worker;

import net.ldoin.shinnetai.buffered.buf.smart.ReadOnlySmartByteBuf;
import net.ldoin.shinnetai.buffered.buf.smart.WriteOnlySmartByteBuf;
import net.ldoin.shinnetai.buffered.util.IOUtil;
import net.ldoin.shinnetai.exception.ShinnetaiException;
import net.ldoin.shinnetai.exception.ShinnetaiExceptions;
import net.ldoin.shinnetai.log.ShinnetaiLog;
import net.ldoin.shinnetai.metric.ShinnetaiMetricsCollector;
import net.ldoin.shinnetai.metric.ShinnetaiRuntimeMetrics;
import net.ldoin.shinnetai.packet.AbstractPacket;
import net.ldoin.shinnetai.packet.PacketOptions;
import net.ldoin.shinnetai.packet.ReadPacket;
import net.ldoin.shinnetai.packet.WrappedPacket;
import net.ldoin.shinnetai.packet.common.EmptyResponsePacket;
import net.ldoin.shinnetai.packet.registry.PacketRegistry;
import net.ldoin.shinnetai.packet.response.PacketResponseOptions;
import net.ldoin.shinnetai.packet.response.PacketResponseWaiter;
import net.ldoin.shinnetai.packet.serializer.BinaryPacketSerializer;
import net.ldoin.shinnetai.packet.serializer.PacketSerializer;
import net.ldoin.shinnetai.packet.side.PacketSide;
import net.ldoin.shinnetai.protocol.ShinnetaiFeature;
import net.ldoin.shinnetai.resilience.RetryPolicy;
import net.ldoin.shinnetai.security.AuthenticationContext;
import net.ldoin.shinnetai.security.RateLimitCost;
import net.ldoin.shinnetai.statistic.ShinnetaiStatistic;
import net.ldoin.shinnetai.worker.pipeline.ShinnetaiPipeline;
import net.ldoin.shinnetai.worker.pipeline.ShinnetaiPipelineHandleType;

import java.io.IOException;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class ShinnetaiWorkerContext<S extends ShinnetaiStatistic> {

    private static final ThreadLocal<Boolean> SENDING_EXCEPTION = ThreadLocal.withInitial(() -> Boolean.FALSE);

    protected Logger logger;
    private final PacketRegistry registry;
    protected volatile ReadableByteChannel inputChannel;
    protected volatile WritableByteChannel outChannel;
    protected final Object writeLock = new Object();
    protected final PacketResponseWaiter responseWaiter;
    private final S statistic;

    protected ShinnetaiWorkerContext(Logger logger, PacketRegistry registry, ReadableByteChannel inputChannel, WritableByteChannel outChannel) {
        this(logger, registry, inputChannel, outChannel, null, null);
    }

    protected ShinnetaiWorkerContext(Logger logger, PacketRegistry registry, ReadableByteChannel inputChannel, WritableByteChannel outChannel, PacketResponseWaiter responseWaiter) {
        this(logger, registry, inputChannel, outChannel, responseWaiter, null);
    }

    protected ShinnetaiWorkerContext(Logger logger, PacketRegistry registry, ReadableByteChannel inputChannel, WritableByteChannel outChannel, S statistic) {
        this(logger, registry, inputChannel, outChannel, null, statistic);
    }

    protected ShinnetaiWorkerContext(Logger logger, PacketRegistry registry, ReadableByteChannel inputChannel, WritableByteChannel outChannel, PacketResponseWaiter responseWaiter, S statistic) {
        ShinnetaiLog.init();

        this.logger = logger;
        this.registry = registry;
        this.inputChannel = inputChannel;
        this.outChannel = outChannel;
        this.responseWaiter = responseWaiter;
        this.statistic = statistic;
    }

    public abstract PacketSide getSide();

    public ShinnetaiPipeline getPipeline() {
        return null;
    }

    public void withPipeline(ShinnetaiPipeline pipeline) {
    }

    protected PacketSerializer getPacketSerializer() {
        return BinaryPacketSerializer.INSTANCE;
    }

    private PacketSerializer resolveSerializer(AbstractPacket<?, ?> packet, WrappedPacket wrapped) {
        PacketSerializer perPacket = packet.serializer();
        if (perPacket != null) {
            return perPacket;
        }

        if (wrapped != null) {
            PacketSerializer perSend = wrapped.getSerializer();
            if (perSend != null) {
                return perSend;
            }
        }

        ShinnetaiPipeline pl = getPipeline();
        if (pl != null) {
            PacketSerializer perPipeline = pl.getSerializer();
            if (perPipeline != null) {
                return perPipeline;
            }
        }

        return getPacketSerializer();
    }

    private PacketSerializer resolveSerializer(AbstractPacket<?, ?> packet) {
        return resolveSerializer(packet, null);
    }

    public long getNegotiatedFeatures() {
        return 0L;
    }

    public boolean hasFeature(ShinnetaiFeature feature) {
        return false;
    }

    public Logger getLogger() {
        return logger;
    }

    public PacketRegistry getRegistry() {
        return registry;
    }

    public ReadableByteChannel getInput() {
        return inputChannel;
    }

    public WritableByteChannel getOut() {
        return outChannel;
    }

    public PacketResponseWaiter getResponseWaiter() {
        return responseWaiter;
    }

    public S getStatistic() {
        return statistic;
    }

    public ShinnetaiRuntimeMetrics getRuntimeMetrics() {
        return ShinnetaiMetricsCollector.collect(statistic);
    }

    public AuthenticationContext getAuthenticationContext() {
        return AuthenticationContext.anonymous();
    }

    public boolean tryConsumeRateLimit(long actionTokens) {
        return tryConsumeRateLimit(RateLimitCost.packets(actionTokens));
    }

    public boolean tryConsumeRateLimit(long actionTokens, long byteTokens) {
        return tryConsumeRateLimit(RateLimitCost.of(actionTokens, byteTokens));
    }

    public boolean tryConsumeRateLimit(RateLimitCost cost) {
        return true;
    }

    public void sendPacket(WrappedPacket packet, WriteOnlySmartByteBuf buf) throws IOException {
        sendPacket(WrappedPacket.builder(packet).buffer(buf).build());
    }

    public void sendPacket(WrappedPacket packet) throws IOException {
        if (outChannel == null) {
            logger.log(Level.SEVERE, "WritableByteChannel not found", new NullPointerException());
            return;
        }

        if (packet == null) {
            return;
        }

        WriteOnlySmartByteBuf buf = packet.getBuffer();
        if (buf == null) {
            buf = WriteOnlySmartByteBuf.empty();
        }

        AbstractPacket<?, ?> abstractPacket = packet.getPacket();
        attachWorker(abstractPacket);

        int id = registry.getId(abstractPacket.getClass());

        ShinnetaiPipeline pipeline = getPipeline();
        if (pipeline != null) {
            try {
                abstractPacket = pipeline.handle(ShinnetaiPipelineHandleType.BEFORE_SEND, abstractPacket);
            } catch (Exception exception) {
                logger.log(Level.SEVERE, "Failed send packet in pipeline", exception);
                sendException(ShinnetaiExceptions.FAILED_SEND_PACKET, id);
                return;
            }
        }

        if (abstractPacket == null) {
            return;
        }

        try {
            buf.writeVarInt(id);
            packet.write(buf);
            byte[] payload = resolveSerializer(abstractPacket, packet).serialize(abstractPacket);
            buf.writeBytesRaw(payload);
        } catch (Exception exception) {
            logger.log(Level.SEVERE, "Failed to write packet", exception);
            sendException(ShinnetaiExceptions.FAILED_WRITE_PACKET, id);
            return;
        }

        try {
            byte[] bytes = buf.toBytes();
            if (statistic != null) {
                try {
                    statistic.send(bytes);
                } catch (Exception exception) {
                    logger.log(Level.SEVERE, "Error on write sent bytes statistics", exception);
                }
            }

            synchronized (writeLock) {
                writeFrame(bytes);
            }
        } catch (ClosedChannelException exception) {
            logger.log(Level.WARNING, String.format("Trying send packet %d to closed channel", id), exception);
        } catch (SocketException exception) {
            logger.log(Level.WARNING, String.format("Trying send packet %d to closed socket", id), exception);
        } catch (Exception exception) {
            logger.log(Level.SEVERE, "", exception);
            sendException(ShinnetaiExceptions.FAILED_SEND_PACKET, id);
        }

        if (pipeline != null) {
            try {
                pipeline.handle(ShinnetaiPipelineHandleType.AFTER_SEND, abstractPacket);
            } catch (Exception exception) {
                logger.log(Level.SEVERE, "Failed send packet in pipeline", exception);
            }
        }
    }

    public void sendPacket(AbstractPacket<?, ?> packet) throws IOException {
        sendPacket(WrappedPacket.of(packet));
    }

    public void sendPacket(AbstractPacket<?, ?> packet, PacketResponseOptions responseOptions) throws IOException {
        sendPacket(WrappedPacket.of(packet, responseOptions));
    }

    public void sendWithRetry(AbstractPacket<?, ?> packet, RetryPolicy policy)
            throws IOException, InterruptedException {
        sendWithRetry(WrappedPacket.of(packet), policy);
    }

    public void sendWithRetry(WrappedPacket packet, RetryPolicy policy) throws IOException, InterruptedException {
        IOException lastError = null;
        for (int attempt = 0; attempt <= policy.getMaxAttempts(); attempt++) {
            if (attempt > 0) {
                long delay = policy.delayForAttempt(attempt - 1);
                if (delay > 0) {
                    Thread.sleep(delay);
                }
            }

            try {
                sendPacket(packet);
                return;
            } catch (IOException e) {
                lastError = e;
                logger.log(Level.WARNING, "sendWithRetry attempt " + (attempt + 1) + "/" + (policy.getMaxAttempts() + 1) + " failed", e);
            }
        }

        assert lastError != null;
        throw lastError;
    }

    public AbstractPacket<?, ?> sendAndWaitForResponse(WrappedPacket packet, TimeUnit timeUnit, int timeout) throws IOException, TimeoutException, InterruptedException, ArrayIndexOutOfBoundsException {
        return sendAndWaitForResponse(packet, timeUnit.toMillis(timeout));
    }

    public AbstractPacket<?, ?> sendAndWaitForResponse(WrappedPacket packet, long timeoutMillis) throws IOException, TimeoutException, InterruptedException, ArrayIndexOutOfBoundsException {
        if (responseWaiter == null) {
            throw new UnsupportedOperationException();
        }

        int waiterId = responseWaiter.addWaiter(false, null, timeoutMillis);
        sendPacket(WrappedPacket.builder(packet)
                .responseOptions(PacketResponseOptions.waitResponse(waiterId))
                .build());
        return responseWaiter.waitForResponse(waiterId, timeoutMillis);
    }

    public CompletableFuture<Optional<AbstractPacket<?, ?>>> sendAsyncWithResponse(WrappedPacket packet, TimeUnit timeUnit, int timeout) throws ArrayIndexOutOfBoundsException {
        return sendAsyncWithResponse(packet, timeUnit.toMillis(timeout));
    }

    public CompletableFuture<Optional<AbstractPacket<?, ?>>> sendAsyncWithResponse(WrappedPacket packet, long timeoutMillis) throws ArrayIndexOutOfBoundsException {
        if (responseWaiter == null) {
            throw new UnsupportedOperationException();
        }

        CompletableFuture<Optional<AbstractPacket<?, ?>>> future = new CompletableFuture<>();
        int waiterId = responseWaiter.addWaiter(true, future, timeoutMillis);
        try {
            sendPacket(WrappedPacket.builder(packet)
                    .responseOptions(PacketResponseOptions.waitResponse(waiterId))
                    .build());
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to send packet with waiterId: " + waiterId, e);
            responseWaiter.handleResponse(waiterId, ShinnetaiExceptions.FAILED_SEND_RESPONSE.toPacket(waiterId));
        }

        return future;
    }

    public AbstractPacket<?, ?> sendAndWaitForResponse(AbstractPacket<?, ?> packet, TimeUnit timeUnit, int timeout) throws IOException, TimeoutException, InterruptedException, ArrayIndexOutOfBoundsException {
        return sendAndWaitForResponse(packet, timeUnit.toMillis(timeout));
    }

    public AbstractPacket<?, ?> sendAndWaitForResponse(AbstractPacket<?, ?> packet, long timeoutMillis) throws IOException, TimeoutException, InterruptedException, ArrayIndexOutOfBoundsException {
        return sendAndWaitForResponse(WrappedPacket.of(packet), timeoutMillis);
    }

    public <P extends AbstractPacket<?, ?>> P sendAndWaitForResponse(AbstractPacket<?, ?> packet, Class<P> responseClass, TimeUnit timeUnit, int timeout) throws IOException, TimeoutException, InterruptedException {
        return responseClass.cast(sendAndWaitForResponse(packet, timeUnit.toMillis(timeout)));
    }

    public <P extends AbstractPacket<?, ?>> CompletableFuture<Optional<P>> sendAsyncWithResponse(AbstractPacket<?, ?> packet, Class<P> responseClass, TimeUnit timeUnit, int timeout) {
        return sendAsyncWithResponse(packet, timeUnit.toMillis(timeout))
                .thenApply(opt -> opt.map(responseClass::cast));
    }

    public CompletableFuture<Optional<AbstractPacket<?, ?>>> sendAsyncWithResponse(AbstractPacket<?, ?> packet, TimeUnit timeUnit, int timeout) throws ArrayIndexOutOfBoundsException {
        return sendAsyncWithResponse(packet, timeUnit.toMillis(timeout));
    }

    public CompletableFuture<Optional<AbstractPacket<?, ?>>> sendAsyncWithResponse(AbstractPacket<?, ?> packet, long timeoutMillis) throws ArrayIndexOutOfBoundsException {
        return sendAsyncWithResponse(WrappedPacket.of(packet), timeoutMillis);
    }

    public ReadPacket readPacket(byte[] bytes) throws IOException {
        return readPacket(bytes, 0, bytes.length);
    }

    public ReadPacket readPacket(byte[] bytes, int offset, int length) throws IOException {
        ReadOnlySmartByteBuf buf = ReadOnlySmartByteBuf.of(bytes, offset, length);
        int id = buf.readVarInt();
        AbstractPacket<?, ?> packet;
        try {
            packet = registry.createPacket(id);
        } catch (IllegalArgumentException exception) {
            logger.log(Level.WARNING, "Received unknown packet id: " + id);
            sendException(ShinnetaiExceptions.FAILED_READ_PACKET, id);
            return null;
        }

        WrappedPacket wrapped = WrappedPacket.of(packet, buf);
        attachWorker(packet);

        try {
            int payloadLen = buf.remain();
            byte[] payload = buf.readBytes(payloadLen);
            resolveSerializer(packet).deserialize(packet, payload, 0, payloadLen);
        } catch (Exception exception) {
            logger.log(Level.SEVERE, "Failed read packet", exception);
            sendException(ShinnetaiExceptions.FAILED_READ_PACKET, id);
            return null;
        }

        if (statistic != null) {
            statistic.receive(bytes);
        }

        return new ReadPacket(id, wrapped);
    }

    public void handlePacket(ReadPacket readPacket) throws IOException {
        int id = readPacket.id();
        WrappedPacket wrapped = readPacket.wrapped();
        AbstractPacket<?, ?> packet = wrapped.getPacket();
        PacketResponseOptions responseOptions = wrapped.getResponseOptions();

        ShinnetaiPipeline pipeline = getPipeline();
        if (pipeline != null) {
            try {
                packet = pipeline.handle(ShinnetaiPipelineHandleType.BEFORE_HANDLE, packet);
            } catch (Exception exception) {
                logger.log(Level.SEVERE, "Failed handle packet in pipeline", exception);
                sendException(ShinnetaiExceptions.FAILED_HANDLE_PACKET, id);
                return;
            }
        }

        if (packet == null) {
            return;
        }

        try {
            if (responseOptions == null || !responseOptions.isResponse()) {
                packet.handle(getSide());
            }
        } catch (Exception exception) {
            logger.log(Level.SEVERE, "Failed handle packet", exception);
            sendException(ShinnetaiExceptions.FAILED_HANDLE_PACKET, id);
            return;
        }

        if (pipeline != null) {
            try {
                pipeline.handle(ShinnetaiPipelineHandleType.AFTER_HANDLE, packet);
            } catch (Exception exception) {
                logger.log(Level.SEVERE, "Failed handle packet in pipeline", exception);
            }
        }

        if (responseOptions == null) {
            return;
        }

        if (wrapped.getOptionValue(PacketOptions.IS_RESPONSE) && responseWaiter != null) {
            int response = responseOptions.getResponseId();
            try {
                responseWaiter.handleResponse(response, packet);
            } catch (Exception exception) {
                logger.log(Level.SEVERE, "", exception);
                sendException(ShinnetaiExceptions.FAILED_HANDLE_RESPONSE, id, response);
            }
        }

        if (responseWaiter != null) {
            int waitResponse = responseOptions.getWaitResponse();
            PacketResponseOptions options = PacketResponseOptions.response(waitResponse);
            if (wrapped.getOptionValue(PacketOptions.WAIT_RESPONSE)) {
                try {
                    AbstractPacket<?, ?> response = packet.response();
                    if (response != null) {
                        sendPacket(WrappedPacket.of(response, options));
                    } else {
                        logger.log(Level.SEVERE, "Cannot find response for " + id + ", waiter " + waitResponse);
                        sendException(ShinnetaiExceptions.FAILED_FIND_RESPONSE, id, waitResponse);
                    }
                } catch (Exception exception) {
                    logger.log(Level.SEVERE, "Failed send response", exception);
                    sendException(ShinnetaiExceptions.FAILED_SEND_RESPONSE, id, waitResponse);
                }
            } else if (wrapped.getOptionValue(PacketOptions.REQUIRE_RESPONSE)) {
                sendPacket(WrappedPacket.builder(new EmptyResponsePacket())
                        .withOption(PacketOptions.IS_RESPONSE)
                        .responseOptions(options)
                        .build());
            }
        }
    }

    public void sendException(ShinnetaiException exception, Object... objects) throws IOException {
        if (SENDING_EXCEPTION.get()) {
            logger.log(Level.SEVERE, "Failed to send exception packet (recursive call prevented): " + exception);
            return;
        }

        SENDING_EXCEPTION.set(true);
        try {
            sendPacket(WrappedPacket.of(exception.toPacket(objects)));
        } finally {
            SENDING_EXCEPTION.remove();
        }
    }

    public void sendException(ShinnetaiException exception) throws IOException {
        if (SENDING_EXCEPTION.get()) {
            logger.log(Level.SEVERE, "Failed to send exception packet (recursive call prevented): " + exception);
            return;
        }

        SENDING_EXCEPTION.set(true);
        try {
            sendPacket(WrappedPacket.of(exception.toPacket()));
        } finally {
            SENDING_EXCEPTION.remove();
        }
    }

    protected void attachWorker(AbstractPacket<?, ?> packet) {
        try {
            switch (getSide()) {
                case CLIENT -> attachClientWorker(packet);
                case SERVER -> attachServerWorker(packet);
                case MULTIPLE -> {
                    attachClientWorker(packet);
                    attachServerWorker(packet);
                }
            }
        } catch (UnsupportedOperationException ignored) {
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void attachClientWorker(AbstractPacket<?, ?> packet) {
        ((AbstractPacket) packet).attachClientWorker(this);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void attachServerWorker(AbstractPacket<?, ?> packet) {
        ((AbstractPacket) packet).attachServerWorker(this);
    }

    protected void writeFrame(byte[] payload) throws IOException {
        IOUtil.writeVarInt(outChannel, payload.length);
        ByteBuffer buf = ByteBuffer.wrap(payload);
        while (buf.hasRemaining()) {
            outChannel.write(buf);
        }
    }
}