package net.ldoin.shinnetai.worker;

import net.ldoin.shinnetai.packet.AbstractPacket;
import net.ldoin.shinnetai.packet.PacketOptions;
import net.ldoin.shinnetai.packet.ReadPacket;
import net.ldoin.shinnetai.packet.WrappedPacket;
import net.ldoin.shinnetai.packet.registry.PacketRegistry;
import net.ldoin.shinnetai.packet.response.PacketResponseWaiter;
import net.ldoin.shinnetai.statistic.ShinnetaiStatistic;
import net.ldoin.shinnetai.stream.IShinnetaiStreamType;
import net.ldoin.shinnetai.stream.ShinnetaiStream;
import net.ldoin.shinnetai.stream.ShinnetaiStreamType;
import net.ldoin.shinnetai.stream.options.ShinnetaiStreamOptions;
import net.ldoin.shinnetai.stream.registry.ShinnetaiStreamRegistry;
import net.ldoin.shinnetai.stream.type.ShinnetaiInStream;
import net.ldoin.shinnetai.stream.type.ShinnetaiOutStream;
import net.ldoin.shinnetai.util.IdGenerator;
import net.ldoin.shinnetai.worker.options.WorkerOptions;
import net.ldoin.shinnetai.worker.pipeline.ShinnetaiPipeline;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class ShinnetaiIOWorker<S extends ShinnetaiStatistic> extends ShinnetaiWorkerContext<S> {

    protected final BlockingQueue<WrappedPacket> outQueue;

    private ExecutorService readerExecutor;
    private ExecutorService writerExecutor;
    private final AtomicBoolean running;

    private final Map<IShinnetaiStreamType, Map<Integer, ShinnetaiStream>> streams;
    private final IdGenerator streamIdGenerator;

    private final WorkerOptions options;
    private volatile ShinnetaiPipeline pipeline;

    protected ShinnetaiIOWorker(Logger logger, PacketRegistry registry, InputStream input, OutputStream out, S statistic, WorkerOptions options) {
        super(logger, registry, input, out, new PacketResponseWaiter(), statistic);

        this.outQueue = new LinkedBlockingQueue<>();
        this.running = new AtomicBoolean();
        this.streams = new ConcurrentHashMap<>();
        this.streamIdGenerator = new IdGenerator();
        this.options = options;
        this.pipeline = options.getPipeline();
    }

    public boolean isRunning() {
        return running.get();
    }

    public void addPacket(AbstractPacket<?, ?> packet) {
        outQueue.add(WrappedPacket.of(packet));
    }

    public void addPacket(WrappedPacket packet) {
        outQueue.add(packet);
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
        }

        Map<Integer, ShinnetaiStream> list = streams.computeIfAbsent(stream.getType(), s -> new ConcurrentHashMap<>());
        if (list.containsKey(id)) {
            throw new IllegalArgumentException(String.format("Stream %s already opened", stream.getId()));
        }

        list.put(id, stream);
        if (!stream.isRunning()) {
            stream.open(true);
        }

        return id;
    }

    public void closeStream(ShinnetaiStream stream) {
        IShinnetaiStreamType type = stream.getType();
        if (!streams.containsKey(type)) {
            return;
        }

        int id = stream.getId();
        Map<Integer, ShinnetaiStream> streamList = streams.get(type);
        if (streamList.remove(id, stream) && streamList.isEmpty()) {
            streams.remove(type);
        }

        if (stream.isRunning()) {
            stream.close();
        }

        streamIdGenerator.releaseId(id);
    }

    public void closeStream(int id) {
        for (Map<Integer, ShinnetaiStream> streamMap : streams.values()) {
            if (!streamMap.containsKey(id)) {
                continue;
            }

            streamMap.get(id).close();
            streamIdGenerator.releaseId(id);
            break;
        }
    }

    public IdGenerator getStreamIdGenerator() {
        return streamIdGenerator;
    }

    public WorkerOptions getOptions() {
        return options;
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
        if (input == null || out == null) {
            throw new UnsupportedOperationException("Cannot start IO worker without IO streams");
        }

        if (!running.compareAndSet(false, true)) {
            return;
        }

        if (options.isVirtualThreads()) {
            readerExecutor = Executors.newVirtualThreadPerTaskExecutor();
            writerExecutor = Executors.newVirtualThreadPerTaskExecutor();
        } else {
            readerExecutor = Executors.newFixedThreadPool(options.getReaderThreads());
            writerExecutor = Executors.newFixedThreadPool(options.getWriterThreads());
        }

        readerExecutor.execute(this::readerLoop);
        writerExecutor.execute(this::writerLoop);
    }

    private int readFully(byte[] buffer, int offset, int length) throws IOException {
        int totalRead = 0;
        while (totalRead < length && running.get()) {
            int read = input.read(buffer, offset + totalRead, length - totalRead);
            if (read == -1) {
                return totalRead;
            }

            totalRead += read;
        }

        return totalRead;
    }
    
    private void readerLoop() {
        byte[] reusedBuffer = new byte[options.getMaxPacketSize()];
        try {
            while (running.get()) {
                try {
                    int bytesRead = readFully(reusedBuffer, 0, 2);
                    if (bytesRead < 2) {
                        break;
                    }
                } catch (IOException e) {
                   break;
                }

                int length = ((reusedBuffer[0] & 0xFF) << 8) | (reusedBuffer[1] & 0xFF);
                if (length > options.getMaxPacketSize()) {
                    getLogger().log(Level.WARNING, "Packet too large: " + length + " > " + options.getMaxPacketSize());
                    continue;
                }

                try {
                    int bytesRead = readFully(reusedBuffer, 0, length);
                    if (bytesRead < length) {
                        break;
                    }
                } catch (IOException e) {
                   break;
                }

                byte[] packetData = Arrays.copyOf(reusedBuffer, length);
                ReadPacket readPacket = readPacket(packetData);
                if (readPacket == null) {
                    continue;
                }

                WrappedPacket wrappedPacket = readPacket.wrapped();
                boolean anyReceive = wrappedPacket.getOptionValue(PacketOptions.IN_STREAM);
                if (anyReceive) {
                    ShinnetaiInStream stream = getInStream(wrappedPacket.getStreamId());
                    if (stream != null) {
                        anyReceive = stream.receive(readPacket, true);
                    }
                }

                if (!anyReceive && streams.containsKey(ShinnetaiStreamType.IN)) {
                    for (ShinnetaiStream stream : streams.get(ShinnetaiStreamType.IN).values()) {
                        if (((ShinnetaiInStream) stream).receive(readPacket)) {
                            anyReceive = true;
                        }
                    }
                }

                if (!anyReceive) {
                    handlePacket(readPacket);
                }
            }
        } catch (SocketTimeoutException e) {
        } catch (Exception e) {
            if (running.get()) {
                getLogger().log(Level.SEVERE, "Reader error", e);
            }
        } finally {
            close();
        }
    }

    private void writerLoop() {
        try {
            while (running.get()) {
                WrappedPacket packet = outQueue.take();
                AbstractPacket<?, ?> abstractPacket = packet.getPacket();
                boolean anySend = false;
                if (streams.containsKey(ShinnetaiStreamType.OUT)) {
                    for (ShinnetaiStream stream : streams.get(ShinnetaiStreamType.OUT).values()) {
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
                getLogger().log(Level.SEVERE, "Writer error", e);
            }
        } finally {
            close();
        }
    }

    public synchronized void close() {
        internalClose();
    }

    protected synchronized void internalClose() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        if (readerExecutor != null) {
            readerExecutor.shutdown();
            readerExecutor = null;
        }

        if (writerExecutor != null) {
            writerExecutor.shutdown();
            writerExecutor = null;
        }

        for (Map<Integer, ShinnetaiStream> streamMap : streams.values()) {
            for (ShinnetaiStream stream : streamMap.values()) {
                if (stream.isRunning()) stream.close();
            }
        }

        streams.clear();
        streamIdGenerator.clear();
        clearIO();
    }

    protected void clearIO() {
        this.input = null;
        this.out = null;
    }

    protected void attachIOStreams(Socket socket) throws IOException {
        attachIOStreams(socket.getInputStream(), socket.getOutputStream());
    }

    private void attachIOStreams(InputStream input, OutputStream out) {
        if (running.get()) {
            throw new UnsupportedOperationException("Cannot attach IO streams while worker running");
        }

        this.input = input;
        this.out = out;
    }
}