package net.ldoin.shinnetai.worker;

import net.ldoin.shinnetai.buffered.util.IOUtil;
import net.ldoin.shinnetai.buffered.exception.FrameTooLargeException;
import net.ldoin.shinnetai.buffered.exception.MalformedVarIntException;
import net.ldoin.shinnetai.buffered.exception.ProtocolDecodeException;
import net.ldoin.shinnetai.delivery.PacketDeduplicator;
import net.ldoin.shinnetai.debug.TrafficLog;
import net.ldoin.shinnetai.handler.PacketHandlerRegistry;
import net.ldoin.shinnetai.log.ShinnetaiLog;
import net.ldoin.shinnetai.packet.AbstractPacket;
import net.ldoin.shinnetai.packet.PacketOptions;
import net.ldoin.shinnetai.packet.ReadPacket;
import net.ldoin.shinnetai.packet.WrappedPacket;
import net.ldoin.shinnetai.packet.common.DeliveryAckPacket;
import net.ldoin.shinnetai.packet.common.HandshakePacket;
import net.ldoin.shinnetai.packet.common.PingPacket;
import net.ldoin.shinnetai.packet.common.PongPacket;
import net.ldoin.shinnetai.packet.common.StreamCommitAckPacket;
import net.ldoin.shinnetai.packet.common.StreamCommitPacket;
import net.ldoin.shinnetai.stream.commit.StreamCommitResult;
import net.ldoin.shinnetai.packet.registry.PacketRegistry;
import net.ldoin.shinnetai.packet.response.PacketResponseWaiter;
import net.ldoin.shinnetai.packet.side.PacketSide;
import net.ldoin.shinnetai.protocol.ShinnetaiFeature;
import net.ldoin.shinnetai.security.RateLimitCost;
import net.ldoin.shinnetai.security.RateLimiter;
import net.ldoin.shinnetai.statistic.ShinnetaiStatistic;
import net.ldoin.shinnetai.stream.IShinnetaiStreamType;
import net.ldoin.shinnetai.stream.ShinnetaiStream;
import net.ldoin.shinnetai.stream.ShinnetaiStreamType;
import net.ldoin.shinnetai.stream.options.ShinnetaiStreamOptions;
import net.ldoin.shinnetai.stream.registry.ShinnetaiStreamRegistry;
import net.ldoin.shinnetai.stream.type.ShinnetaiInStream;
import net.ldoin.shinnetai.stream.type.ShinnetaiOutStream;
import net.ldoin.shinnetai.util.IdGenerator;
import net.ldoin.shinnetai.packet.serializer.PacketSerializer;
import net.ldoin.shinnetai.worker.options.WorkerOptions;
import net.ldoin.shinnetai.worker.pipeline.ShinnetaiPipeline;
import net.ldoin.shinnetai.worker.pipeline.ShinnetaiPipelineHandleType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public abstract class ShinnetaiIOWorker<S extends ShinnetaiStatistic> extends ShinnetaiWorkerContext<S> {

    private static byte[] compress(byte[] data) {
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        deflater.setInput(data);
        deflater.finish();

        ByteArrayOutputStream baos = new ByteArrayOutputStream(data.length);
        byte[] buffer = new byte[8192];
        while (!deflater.finished()) {
            int count = deflater.deflate(buffer);
            baos.write(buffer, 0, count);
        }

        deflater.end();
        return baos.toByteArray();
    }

    private static byte[] decompress(byte[] data, int offset, int length, int uncompressedSize) {
        Inflater inflater = new Inflater(true);
        inflater.setInput(data, offset, length);
        byte[] output = new byte[uncompressedSize];
        try {
            inflater.inflate(output);
        } catch (DataFormatException e) {
            throw new ProtocolDecodeException("Failed to decompress packet data", e);
        } finally {
            inflater.end();
        }

        return output;
    }

    private static byte[] encodeVarInt(int value) {
        byte[] buf = new byte[5];
        int size = 0;
        while ((value & ~0x7F) != 0) {
            buf[size++] = (byte) ((value & 0x7F) | 0x80);
            value >>>= 7;
        }

        buf[size++] = (byte) value;
        return Arrays.copyOf(buf, size);
    }

    private static int readVarIntAt(byte[] data) {
        int result = 0;
        int shift = 0;
        for (int i = 0; i < 5; i++) {
            if (i >= data.length) {
                throw new MalformedVarIntException("VarInt is truncated");
            }

            byte b = data[i];
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }

            shift += 7;
        }

        throw new MalformedVarIntException("VarInt is too big");
    }

    private static int varIntSize(int value) {
        int size = 1;
        while ((value & ~0x7F) != 0) {
            value >>>= 7;
            size++;
        }

        return size;
    }

    protected final BlockingQueue<WrappedPacket> outQueue;

    private ExecutorService readerExecutor;
    private ExecutorService writerExecutor;
    private final AtomicBoolean running;
    private final AtomicReference<WorkerState> state = new AtomicReference<>(WorkerState.NEW);
    private volatile CountDownLatch closedLatch = new CountDownLatch(1);
    private volatile CloseReason lastCloseReason;

    private final Map<IShinnetaiStreamType, Map<Integer, ShinnetaiStream>> streams;
    private final IdGenerator streamIdGenerator;

    private final WorkerOptions options;
    private final RateLimiter rateLimiter;
    private volatile ShinnetaiPipeline pipeline;

    private volatile boolean handshaked = false;
    private volatile long lastHeartbeat = System.currentTimeMillis();
    private volatile long negotiatedFeatures = 0;
    private final PacketDeduplicator deduplicator;

    private final AtomicBoolean closing = new AtomicBoolean(false);
    private final AtomicBoolean overloaded = new AtomicBoolean(false);
    private volatile Thread keepAliveThread;
    private final ConcurrentHashMap<Integer, CompletableFuture<StreamCommitResult>> pendingStreamCommits = new ConcurrentHashMap<>();

    protected ShinnetaiIOWorker(Logger logger, PacketRegistry registry, ReadableByteChannel inputChannel, WritableByteChannel outChannel, S statistic, WorkerOptions options) {
        super(logger, registry, inputChannel, outChannel, new PacketResponseWaiter(), statistic);

        this.outQueue = new ArrayBlockingQueue<>(options.getMaxQueueSize());
        this.running = new AtomicBoolean();
        this.streams = new ConcurrentHashMap<>();
        this.streamIdGenerator = new IdGenerator();
        this.options = options;
        this.rateLimiter = options.createRateLimiter();
        this.pipeline = options.getPipeline();
        this.deduplicator = new PacketDeduplicator(options.getDeduplicationWindowMs(), options.getDeduplicationMaxEntries());
    }

    @Override
    protected PacketSerializer getPacketSerializer() {
        return options.getPacketSerializer();
    }

    public boolean isRunning() {
        return running.get();
    }

    public WorkerState getState() {
        return state.get();
    }

    public CloseReason getLastCloseReason() {
        return lastCloseReason;
    }

    public boolean isHandshaked() {
        return handshaked;
    }

    @Override
    public boolean tryConsumeRateLimit(RateLimitCost cost) {
        return rateLimiter == null || rateLimiter.tryAcquire(cost);
    }

    public boolean awaitClosed(long timeout, TimeUnit unit) throws InterruptedException {
        return closedLatch.await(timeout, unit);
    }

    protected boolean transition(WorkerState expected, WorkerState next) {
        return state.compareAndSet(expected, next);
    }

    public void addPacket(AbstractPacket<?, ?> packet) {
        addPacket(WrappedPacket.of(packet));
    }

    public void addPacket(WrappedPacket packet) {
        EnqueueResult result = tryAddPacket(packet);
        if (result != EnqueueResult.ACCEPTED && options.getQueueOverflowStrategy() == QueueOverflowStrategy.THROW) {
            throw new IllegalStateException("Packet was not enqueued: " + result);
        }
    }

    public void addPacketOrThrow(AbstractPacket<?, ?> packet) {
        addPacketOrThrow(WrappedPacket.of(packet));
    }

    public void addPacketOrThrow(WrappedPacket packet) {
        EnqueueResult result = tryAddPacket(packet);
        if (result != EnqueueResult.ACCEPTED) {
            throw new IllegalStateException("Packet was not enqueued: " + result);
        }
    }

    public EnqueueResult tryAddPacket(AbstractPacket<?, ?> packet) {
        return tryAddPacket(WrappedPacket.of(packet));
    }

    public EnqueueResult tryAddPacket(WrappedPacket packet) {
        if (packet == null) {
            return EnqueueResult.DROPPED;
        }

        WorkerState currentState = state.get();
        if (!running.get() && currentState != WorkerState.NEW && currentState != WorkerState.HANDSHAKING) {
            return EnqueueResult.CLOSED;
        }

        switch (options.getQueueOverflowStrategy()) {
            case DROP, DROP_NEWEST -> {
                if (!outQueue.offer(packet)) {
                    if (isReliable(packet)) {
                        notifyPacketDropped(packet);
                        return EnqueueResult.REJECTED_FULL;
                    }

                    notifyPacketDropped(packet);
                    return EnqueueResult.DROPPED;
                }
            }
            case WARN -> {
                if (!outQueue.offer(packet)) {
                    if (isReliable(packet)) {
                        getLogger().warning("Reliable packet queue is full, rejecting packet");
                        notifyPacketDropped(packet);
                        return EnqueueResult.REJECTED_FULL;
                    }

                    getLogger().warning("Packet queue is full, dropping packet");
                    notifyPacketDropped(packet);
                    return EnqueueResult.DROPPED;
                }
            }
            case THROW -> {
                if (!outQueue.offer(packet)) {
                    notifyPacketDropped(packet);
                    return EnqueueResult.REJECTED_FULL;
                }
            }
            case BLOCK -> {
                int timeout = options.getSendTimeoutMs();
                try {
                    if (timeout > 0) {
                        if (!outQueue.offer(packet, timeout, TimeUnit.MILLISECONDS)) {
                            notifyPacketDropped(packet);
                            notifyQueueTimeout();
                            return EnqueueResult.TIMED_OUT;
                        }
                    } else {
                        outQueue.put(packet);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return EnqueueResult.TIMED_OUT;
                }
            }
            case DROP_OLDEST -> {
                if (isReliable(packet) && outQueue.remainingCapacity() == 0) {
                    notifyPacketDropped(packet);
                    return EnqueueResult.REJECTED_FULL;
                }

                while (!outQueue.offer(packet)) {
                    WrappedPacket dropped = outQueue.poll();
                    if (dropped != null) {
                        notifyPacketDropped(dropped);
                    }
                }
            }
        }

        updateQueueLoadSignals();
        return EnqueueResult.ACCEPTED;
    }

    private void notifyPacketDropped(WrappedPacket packet) {
        if (options.getOnPacketDropped() != null) {
            try {
                options.getOnPacketDropped().accept(packet);
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "onPacketDropped callback threw an exception", e);
            }
        }
    }

    private void notifyQueueTimeout() {
        Runnable callback = options.getOnQueueTimeout();
        if (callback != null) {
            try {
                callback.run();
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "onQueueTimeout callback threw an exception", e);
            }
        }
    }

    private boolean isReliable(WrappedPacket packet) {
        return packet != null && packet.getOptionValue(PacketOptions.DELIVERY_TRACKED);
    }

    private void updateQueueLoadSignals() {
        double load = getQueueLoad();
        if (load >= options.getQueueHighWatermark() && overloaded.compareAndSet(false, true)) {
            Runnable callback = options.getOnOverloaded();
            if (callback != null) {
                try {
                    callback.run();
                } catch (Exception e) {
                    getLogger().log(Level.WARNING, "onOverloaded callback threw an exception", e);
                }
            }
        } else if (load <= options.getQueueLowWatermark() && overloaded.compareAndSet(true, false)) {
            Runnable callback = options.getOnRecovered();
            if (callback != null) {
                try {
                    callback.run();
                } catch (Exception e) {
                    getLogger().log(Level.WARNING, "onRecovered callback threw an exception", e);
                }
            }
        }
    }

    public int getQueueSize() {
        return outQueue.size();
    }

    public boolean isQueueFull() {
        return outQueue.remainingCapacity() == 0;
    }

    public double getQueueLoad() {
        int max = options.getMaxQueueSize();
        return max > 0 ? (double) outQueue.size() / max : 0.0;
    }

    public List<WrappedPacket> drainQueue() {
        List<WrappedPacket> drained = new ArrayList<>();
        outQueue.drainTo(drained);
        return drained;
    }

    @SuppressWarnings("unchecked")
    public <C extends ShinnetaiStream> C getStream(IShinnetaiStreamType type, int id) {
        Map<Integer, ShinnetaiStream> streamMap = streams.get(type);
        if (streamMap != null && streamMap.containsKey(id)) {
            return (C) streamMap.get(id);
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    public <V extends ShinnetaiStream> V getStream(int id) {
        for (Map<Integer, ShinnetaiStream> streamMap : streams.values()) {
            if (streamMap != null && streamMap.containsKey(id)) {
                return (V) streamMap.get(id);
            }
        }

        return null;
    }

    public <C extends ShinnetaiInStream> C getInStream(int id) {
        return getStream(ShinnetaiStreamType.IN, id);
    }

    public <C extends ShinnetaiOutStream> C getOutStream(int id) {
        return getStream(ShinnetaiStreamType.OUT, id);
    }

    public int openStream(int typeId, int id, ShinnetaiStreamOptions options) {
        return openStream(ShinnetaiStreamRegistry.createStream(typeId, id, this, options));
    }

    public int openStream(ShinnetaiStream stream) {
        int id = stream.getId();
        if (id == -1) {
            id = streamIdGenerator.getNextId();
            stream = stream.withId(id);
        } else {
            streamIdGenerator.reserve(id);
        }

        Map<Integer, ShinnetaiStream> list = streams.computeIfAbsent(stream.getType(), s -> new ConcurrentHashMap<>());
        if (list.putIfAbsent(id, stream) != null) {
            throw new IllegalArgumentException(String.format("Stream %s already opened", stream.getId()));
        }

        if (!stream.isRunning()) {
            stream.open(true);
        }

        return id;
    }

    public void closeStream(ShinnetaiStream stream) {
        IShinnetaiStreamType type = stream.getType();
        int id = stream.getId();
        streams.computeIfPresent(type, (t, map) -> {
            map.remove(id, stream);
            return map.isEmpty() ? null : map;
        });

        if (stream.isRunning()) {
            stream.close();
        }

        streamIdGenerator.releaseId(id);
    }

    public void closeStream(int id) {
        for (Map<Integer, ShinnetaiStream> streamMap : streams.values()) {
            ShinnetaiStream stream = streamMap.get(id);
            if (stream != null) {
                closeStream(stream);
                break;
            }
        }
    }

    public IdGenerator getStreamIdGenerator() {
        return streamIdGenerator;
    }

    public WorkerOptions getOptions() {
        return options;
    }

    public long getNegotiatedFeatures() {
        return negotiatedFeatures;
    }

    public boolean hasFeature(ShinnetaiFeature feature) {
        return (negotiatedFeatures & feature.flag()) != 0;
    }

    public Set<ShinnetaiFeature> getNegotiatedFeatureSet() {
        return ShinnetaiFeature.fromFlags(negotiatedFeatures);
    }

    @Override
    public ShinnetaiPipeline getPipeline() {
        return pipeline;
    }

    @Override
    public void withPipeline(ShinnetaiPipeline pipeline) {
        this.pipeline = pipeline;
    }

    public synchronized void start() {
        if (inputChannel == null || outChannel == null) {
            throw new UnsupportedOperationException("Cannot start IO worker without IO channels");
        }

        WorkerState current = state.get();
        if (current != WorkerState.NEW && current != WorkerState.CLOSED && current != WorkerState.FAILED) {
            return;
        }

        if (!running.compareAndSet(false, true)) {
            return;
        }

        closedLatch = new CountDownLatch(1);
        closing.set(false);
        handshaked = false;
        lastCloseReason = null;
        state.set(WorkerState.HANDSHAKING);
        lastHeartbeat = System.currentTimeMillis();

        if (options.isVirtualThreads()) {
            readerExecutor = Executors.newVirtualThreadPerTaskExecutor();
            writerExecutor = Executors.newVirtualThreadPerTaskExecutor();
        } else {
            readerExecutor = Executors.newFixedThreadPool(options.getReaderThreads());
            writerExecutor = Executors.newFixedThreadPool(options.getWriterThreads());
        }

        readerExecutor.execute(this::readerLoop);
        writerExecutor.execute(this::writerLoop);

        TrafficLog trafficLog = options.getTrafficLog();
        if (trafficLog == null) {
            trafficLog = ShinnetaiLog.getTrafficLog();
        }

        if (trafficLog != null) {
            if (pipeline == null) {
                pipeline = new ShinnetaiPipeline();
            }

            pipeline.addFirst(ShinnetaiPipelineHandleType.BEFORE_HANDLE, trafficLog.inUnit());
            pipeline.addFirst(ShinnetaiPipelineHandleType.BEFORE_SEND, trafficLog.outUnit());
        }

        if (options.isKeepAlive()) {
            keepAliveThread = Thread.ofVirtual().name("keep-alive-worker").start(this::keepAliveLoop);
        }
    }

    private void keepAliveLoop() {
        while (running.get()) {
            try {
                Thread.sleep(options.getReadTimeout() / 3);
                if (!running.get()) {
                    break;
                }

                if (System.currentTimeMillis() - lastHeartbeat > options.getReadTimeout()) {
                    getLogger().warning("Connection timed out (KeepAlive)");
                    close(CloseReason.TIMEOUT);
                    break;
                }

                addPacket(new PingPacket(System.currentTimeMillis()));
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                if (running.get()) {
                    if (options.getExceptionHandler() != null) {
                        options.getExceptionHandler().accept(e);
                    } else {
                        getLogger().log(Level.WARNING, "KeepAlive warning", e);
                    }
                }
            }
        }
    }

    @Override
    public void handlePacket(ReadPacket readPacket) throws IOException {
        WrappedPacket wrapped = readPacket.wrapped();
        if (options.isReliableDelivery()) {
            if (wrapped.getOptionValue(PacketOptions.DELIVERY_ACK)) {
                handleDeliveryAck(readPacket);
                return;
            }

            if (wrapped.getOptionValue(PacketOptions.DELIVERY_TRACKED)) {
                long packetId = wrapped.getPacketId();
                sendDeliveryAck(packetId);
                if (deduplicator.isDuplicate(packetId)) {
                    return;
                }
            }
        }

        AbstractPacket<?, ?> packet = readPacket.wrapped().getPacket();
        if (packet instanceof HandshakePacket handshake) {
            if (!validateHandshake(handshake)) {
                getLogger().warning("Handshake rejected");
                onHandshakeRejected();
                return;
            }

            if (options.getProtocolVersion() != handshake.getProtocolVersion()) {
                getLogger().warning("Handshake failed: Protocol version mismatch " + handshake.getProtocolVersion() + " != " + options.getProtocolVersion());
                onHandshakeRejected();
                return;
            }
            if (options.getPacketMagic() != handshake.getMagic()) {
                getLogger().warning("Handshake failed: Magic mismatch");
                onHandshakeRejected();
                return;
            }

            if (getSide() == PacketSide.SERVER) {
                long tsWindow = options.getHandshakeTimestampWindowMs();
                if (tsWindow > 0) {
                    long ts = handshake.getHandshakeTimestamp();
                    if (ts == 0 || Math.abs(System.currentTimeMillis() - ts) > tsWindow) {
                        getLogger().warning("Handshake failed: Timestamp missing or outside acceptable window (" + tsWindow + "ms)");
                        onHandshakeRejected();
                        return;
                    }
                }
            }

            long peerFeatures = handshake.getFeatureFlags();
            long myFeatures = options.getSupportedFeatures();
            this.negotiatedFeatures = peerFeatures & myFeatures;

            if (getSide() == PacketSide.SERVER) {
                addPacket(new HandshakePacket(options.getProtocolVersion(), options.getPacketMagic(), myFeatures));
            }

            handshaked = true;
            state.compareAndSet(WorkerState.HANDSHAKING, WorkerState.RUNNING);
            onHandshaked();
            return;
        }

        if (!handshaked) {
            getLogger().warning("Received packet before handshake: " + packet.getClass().getSimpleName());
            close(CloseReason.PROTOCOL_ERROR);
            return;
        }

        if (packet instanceof PingPacket ping) {
            addPacket(new PongPacket(ping.getTimestamp()));
            return;
        }

        if (packet instanceof PongPacket) {
            lastHeartbeat = System.currentTimeMillis();
            return;
        }

        if (packet instanceof StreamCommitPacket commitPacket) {
            ShinnetaiInStream inStream = getInStream(commitPacket.getStreamId());
            if (inStream != null) {
                inStream.receiveCommit();
            }
            
            return;
        }

        if (packet instanceof StreamCommitAckPacket ackPacket) {
            CompletableFuture<StreamCommitResult> future = pendingStreamCommits.remove(ackPacket.getStreamId());
            if (future != null) {
                StreamCommitResult result = ackPacket.isSuccess()
                        ? StreamCommitResult.ok(ackPacket.getStreamId())
                        : StreamCommitResult.failed(ackPacket.getStreamId(), ackPacket.getMessage());
                future.complete(result);
            }

            return;
        }

        if (!isPacketAuthorized(packet)) {
            getLogger().warning("Packet authorization rejected: " + packet.getClass().getName());
            return;
        }

        super.handlePacket(readPacket);

        PacketHandlerRegistry registry = options.getPacketHandlerRegistry();
        if (registry != null) {
            registry.dispatch(packet, this);
        }
    }

    private void readerLoop() {
        Throwable lostCause = null;
        try {
            while (running.get()) {
                int length;
                try {
                    length = IOUtil.readVarInt(inputChannel);
                } catch (IOException e) {
                    if (running.get()) {
                        lostCause = e;
                    }

                    break;
                }

                if (length <= 0 || length > options.getMaxPacketSize()) {
                    getLogger().log(Level.SEVERE, "Invalid packet length: " + length + " (max " + options.getMaxPacketSize() + ")");
                    lostCause = new IllegalArgumentException("Invalid packet length: " + length);
                    close(CloseReason.PROTOCOL_ERROR);
                    break;
                }

                if (rateLimiter != null && !rateLimiter.tryAcquire(length)) {
                    getLogger().warning("Rate limit exceeded, closing inbound connection before frame allocation");
                    lostCause = new IOException("Inbound rate limit exceeded");
                    close(CloseReason.PROTOCOL_ERROR);
                    break;
                }

                byte[] frameData;
                try {
                    frameData = new byte[length];
                    int totalRead = IOUtil.readFully(inputChannel, frameData, length);
                    if (totalRead < length) {
                        if (running.get()) {
                            lostCause = new IOException("Remote channel closed while reading frame");
                        }

                        break;
                    }
                } catch (IOException e) {
                    if (running.get()) {
                        lostCause = e;
                    }

                    break;
                }

                byte[] packetData;
                try {
                    packetData = parseFrame(frameData);
                } catch (Exception e) {
                    getLogger().log(Level.SEVERE, "Failed to decompress packet frame", e);
                    lostCause = e;
                    close(CloseReason.PROTOCOL_ERROR);
                    break;
                }

                if (packetData.length > options.getMaxPacketSize()) {
                    getLogger().log(Level.SEVERE, "Decompressed packet too large: " + packetData.length);
                    lostCause = new IllegalArgumentException("Decompressed packet too large: " + packetData.length);
                    close(CloseReason.PROTOCOL_ERROR);
                    break;
                }

                ReadPacket readPacket;
                try {
                    readPacket = readPacket(packetData, 0, packetData.length);
                } catch (Exception e) {
                    getLogger().log(Level.WARNING, "Failed to parse incoming packet", e);
                    continue;
                }

                if (readPacket == null) {
                    continue;
                }

                RateLimitCost packetCost = options.resolveRateLimitCost(readPacket.wrapped().getPacket());
                if (!tryConsumeRateLimit(packetCost)) {
                    getLogger().warning("Packet rate limit exceeded, dropping inbound packet: " + readPacket.wrapped().getPacket().getClass().getName());
                    continue;
                }

                try {
                    WrappedPacket wrappedPacket = readPacket.wrapped();
                    boolean anyReceive = wrappedPacket.getOptionValue(PacketOptions.IN_STREAM);
                    if (anyReceive) {
                        ShinnetaiInStream stream = getInStream(wrappedPacket.getStreamId());
                        if (stream != null) {
                            anyReceive = stream.receive(readPacket, true);
                        }
                    }

                    if (!anyReceive) {
                        Map<Integer, ShinnetaiStream> inStreams = streams.get(ShinnetaiStreamType.IN);
                        if (inStreams != null) {
                            for (ShinnetaiStream stream : inStreams.values()) {
                                if (((ShinnetaiInStream) stream).receive(readPacket)) {
                                    anyReceive = true;
                                }
                            }
                        }
                    }

                    if (!anyReceive) {
                        handlePacket(readPacket);
                    }
                } catch (Exception e) {
                    getLogger().log(Level.WARNING, "Error handling packet", e);
                }
            }
        } catch (Exception e) {
            if (running.get()) {
                lostCause = e;
                if (options.getExceptionHandler() != null) {
                    options.getExceptionHandler().accept(e);
                } else {
                    getLogger().log(Level.INFO, "Reader closed: " + e.getMessage());
                }
            }
        } finally {
            CloseReason reason = lostCause == null ? CloseReason.REMOTE_DISCONNECT : CloseReason.IO_ERROR;
            close(reason);
            if (lostCause != null && shouldNotifyConnectionLost(reason)) {
                onConnectionLost(lostCause);
            }
        }
    }

    private boolean isPacketAuthorized(AbstractPacket<?, ?> packet) {
        if (options.getPacketAuthorizer() == null) {
            return true;
        }

        try {
            return options.getPacketAuthorizer().canHandle(getAuthenticationContext(), packet, this);
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Packet authorizer threw an exception", e);
            return false;
        }
    }

    protected boolean shouldNotifyConnectionLost(CloseReason reason) {
        return reason != CloseReason.USER_REQUEST && lastCloseReason != CloseReason.USER_REQUEST;
    }

    protected void onConnectionLost(Throwable cause) {
    }

    protected void onHandshaked() {
    }

    protected boolean validateHandshake(HandshakePacket handshake) {
        return true;
    }

    protected void onHandshakeRejected() {
        close(CloseReason.HANDSHAKE_REJECTED);
    }

    protected void onDeliveryAck(long packetId) {
    }

    public void registerStreamCommit(int streamId, CompletableFuture<StreamCommitResult> future) {
        pendingStreamCommits.put(streamId, future);
    }

    public void unregisterStreamCommit(int streamId) {
        pendingStreamCommits.remove(streamId);
    }

    private void handleDeliveryAck(ReadPacket readPacket) {
        AbstractPacket<?, ?> packet = readPacket.wrapped().getPacket();
        if (packet instanceof DeliveryAckPacket ackPacket) {
            onDeliveryAck(ackPacket.getDeliveryPacketId());
            return;
        }

        onDeliveryAck(readPacket.wrapped().getPacketId());
    }

    private void sendDeliveryAck(long packetId) {
        if (packetId <= 0) {
            return;
        }

        addPacket(WrappedPacket.builder(new DeliveryAckPacket(packetId))
                .withOption(PacketOptions.DELIVERY_ACK)
                .packetId(packetId)
                .build());
    }

    private void writerLoop() {
        try {
            while (running.get()) {
                WrappedPacket packet = outQueue.poll(100, TimeUnit.MILLISECONDS);
                if (packet == null) {
                    if (closing.get() && outQueue.isEmpty()) {
                        break;
                    }

                    continue;
                }

                updateQueueLoadSignals();

                boolean anySend = false;
                Map<Integer, ShinnetaiStream> outStreams = streams.get(ShinnetaiStreamType.OUT);
                if (outStreams != null) {
                    for (ShinnetaiStream stream : outStreams.values()) {
                        if (((ShinnetaiOutStream) stream).send(packet)) {
                            anySend = true;
                        }
                    }
                }

                if (!anySend) {
                    sendPacket(packet);
                }
            }
        } catch (InterruptedException ignored) {
        } catch (IOException e) {
            if (running.get()) {
                if (options.getExceptionHandler() != null) {
                    options.getExceptionHandler().accept(e);
                } else {
                    getLogger().log(Level.SEVERE, "Writer error", e);
                }
            }
        } finally {
            close(closing.get() ? CloseReason.REMOTE_DISCONNECT : CloseReason.IO_ERROR);
        }
    }

    public void closeAfterFlushing() {
        closing.set(true);
    }

    public synchronized void close() {
        close(CloseReason.USER_REQUEST);
    }

    public synchronized void close(CloseReason reason) {
        internalClose(reason);
    }

    protected synchronized void internalClose(CloseReason reason) {
        WorkerState current = state.get();
        if (current == WorkerState.CLOSED || current == WorkerState.CLOSING) {
            return;
        }

        lastCloseReason = reason;
        state.set(WorkerState.CLOSING);
        running.set(false);
        if (keepAliveThread != null) {
            keepAliveThread.interrupt();
            keepAliveThread = null;
        }

        if (readerExecutor != null) {
            readerExecutor.shutdownNow();
            readerExecutor = null;
        }

        if (writerExecutor != null) {
            writerExecutor.shutdownNow();
            writerExecutor = null;
        }

        for (Map<Integer, ShinnetaiStream> streamMap : streams.values()) {
            for (ShinnetaiStream stream : streamMap.values()) {
                if (stream.isRunning()) stream.close();
            }
        }

        streams.clear();
        streamIdGenerator.clear();

        if (responseWaiter != null) {
            responseWaiter.shutdown();
        }

        clearIO();
        state.set(reason == CloseReason.IO_ERROR || reason == CloseReason.PROTOCOL_ERROR || reason == CloseReason.TIMEOUT
                ? WorkerState.FAILED
                : WorkerState.CLOSED);
        try {
            onClosed(reason);
        } finally {
            closedLatch.countDown();
        }
    }

    protected void onClosed(CloseReason reason) {
    }

    protected void clearIO() {
        this.inputChannel = null;
        this.outChannel = null;
    }

    protected void attachChannels(ReadableByteChannel in, WritableByteChannel out) {
        if (running.get()) {
            throw new UnsupportedOperationException("Cannot attach IO channels while worker running");
        }

        this.inputChannel = in;
        this.outChannel = out;
    }

    @Override
    protected void writeFrame(byte[] payload) throws IOException {
        int threshold = options.getCompressionThreshold();
        if (threshold <= 0) {
            super.writeFrame(payload);
            return;
        }

        super.writeFrame(buildCompressedFrame(payload, threshold));
    }

    private byte[] buildCompressedFrame(byte[] payload, int threshold) {
        if (payload.length >= threshold) {
            byte[] compressed = compress(payload);
            byte[] sizePrefix = encodeVarInt(payload.length);
            byte[] frame = new byte[sizePrefix.length + compressed.length];
            System.arraycopy(sizePrefix, 0, frame, 0, sizePrefix.length);
            System.arraycopy(compressed, 0, frame, sizePrefix.length, compressed.length);
            return frame;
        } else {
            byte[] frame = new byte[1 + payload.length];
            frame[0] = 0;
            System.arraycopy(payload, 0, frame, 1, payload.length);
            return frame;
        }
    }

    private byte[] parseFrame(byte[] frame) {
        int threshold = options.getCompressionThreshold();
        if (threshold <= 0) {
            return frame;
        }

        int uncompressedSize = readVarIntAt(frame);
        int headerSize = varIntSize(uncompressedSize);
        if (uncompressedSize == 0) {
            return Arrays.copyOfRange(frame, headerSize, frame.length);
        }

        if (uncompressedSize < 0 || uncompressedSize > options.getMaxPacketSize()) {
            throw new FrameTooLargeException("Decompressed size exceeds maximum packet size: " + uncompressedSize);
        }

        return decompress(frame, headerSize, frame.length - headerSize, uncompressedSize);
    }
}