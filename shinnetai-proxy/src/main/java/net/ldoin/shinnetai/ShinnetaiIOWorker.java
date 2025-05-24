package net.ldoin.shinnetai;

import net.ldoin.shinnetai.packet.AbstractPacket;
import net.ldoin.shinnetai.packet.ReadPacket;
import net.ldoin.shinnetai.packet.registry.PacketRegistry;
import net.ldoin.shinnetai.packet.response.PacketResponseWaiter;
import net.ldoin.shinnetai.packet.side.PacketSide;
import net.ldoin.shinnetai.statistic.ShinnetaiStatistic;
import net.ldoin.shinnetai.stream.IShinnetaiStreamType;
import net.ldoin.shinnetai.stream.ShinnetaiStream;
import net.ldoin.shinnetai.stream.ShinnetaiStreamType;
import net.ldoin.shinnetai.stream.type.ShinnetaiInStream;
import net.ldoin.shinnetai.stream.type.ShinnetaiOutStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class ShinnetaiIOWorker<S extends ShinnetaiStatistic> extends ShinnetaiWorkerContext<S> {

    protected final BlockingQueue<AbstractPacket<?, ?>> outQueue;

    private Thread readerThread;
    private Thread writerThread;
    private final AtomicBoolean running;

    private final Map<IShinnetaiStreamType, Map<UUID, ShinnetaiStream>> streams;

    protected ShinnetaiIOWorker(Logger logger, PacketRegistry registry, InputStream input, OutputStream out, S statistic) {
        super(logger, registry, input, out, new PacketResponseWaiter(), statistic);

        this.outQueue = new LinkedBlockingQueue<>();
        this.running = new AtomicBoolean();
        this.streams = new ConcurrentHashMap<>();
    }

    public abstract PacketSide getSide();

    public boolean isRunning() {
        return running.get();
    }

    public void addPacket(AbstractPacket<?, ?> packet) {
        outQueue.add(packet);
    }

    public ShinnetaiInStream getInStream(UUID uuid) {
        return (ShinnetaiInStream) streams.get(ShinnetaiStreamType.IN).get(uuid);
    }

    public ShinnetaiOutStream getOutStream(UUID uuid) {
        return (ShinnetaiOutStream) streams.get(ShinnetaiStreamType.OUT).get(uuid);
    }

    public ShinnetaiStream getStream(ShinnetaiStreamType type, UUID uuid) {
        if (!streams.containsKey(type)) {
            return null;
        }

        return streams.get(type).get(uuid);
    }

    public void openStream(ShinnetaiStream stream) {
        UUID uuid = stream.getUuid();
        Map<UUID, ShinnetaiStream> list = streams.computeIfAbsent(stream.getType(), s -> new ConcurrentHashMap<>());
        if (list.containsKey(uuid)) {
            throw new IllegalArgumentException(String.format("Stream %s already opened", stream.getUuid()));
        }

        list.put(uuid, stream);
        if (!stream.isRunning()) {
            stream.open(true);
        }
    }

    public void closeStream(ShinnetaiStream stream) {
        IShinnetaiStreamType type = stream.getType();
        if (!streams.containsKey(type)) {
            return;
        }

        UUID uuid = stream.getUuid();
        Map<UUID, ShinnetaiStream> streamList = streams.get(type);
        if (streamList.remove(uuid, stream) && streamList.isEmpty()) {
            streams.remove(type);
        }

        if (stream.isRunning()) {
            stream.close();
        }
    }

    public void closeStream(UUID uuid) {
        for (Map<UUID, ShinnetaiStream> streamMap : streams.values()) {
            if (streamMap.containsKey(uuid)) {
                streamMap.get(uuid).close();
                break;
            }
        }
    }

    public synchronized void start() {
        if (input == null || out == null) {
            throw new UnsupportedOperationException("Cannot start IO worker without IO streams");
        }

        if (!running.compareAndSet(false, true)) {
            return;
        }

        if (readerThread != null || writerThread != null) {
            close();
        }

        startReader();
        startWriter();
    }

    private void startReader() {
        readerThread = Thread.ofVirtual().start(() -> {
            try {
                while (running.get()) {
                    byte[] lengthBuf = input.readNBytes(2);
                    if (lengthBuf.length != 2) {
                        break;
                    }

                    short length = (short) (((lengthBuf[0] & 0xFF) << 8) | (lengthBuf[1] & 0xFF));
                    byte[] packetBuf = input.readNBytes(length);
                    if (packetBuf.length != length) {
                        break;
                    }

                    ReadPacket readPacket = readPacket(packetBuf);
                    if (readPacket == null) {
                        continue;
                    }

                    boolean anyReceive = false;
                    if (streams.containsKey(ShinnetaiStreamType.IN)) {
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
            } catch (SocketException ignored) {
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Reader error", e);
            } finally {
                if (running.get()) {
                    close();
                }
            }
        });
    }

    private void startWriter() {
        writerThread = Thread.ofVirtual().start(() -> {
            try {
                while (running.get()) {
                    AbstractPacket<?, ?> packet = outQueue.take();
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
                getLogger().log(Level.SEVERE, "Writer error", e);
            } finally {
                if (running.get()) {
                    close();
                }
            }
        });
    }

    public synchronized void close() {
        internalClose();
    }

    protected synchronized void internalClose() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        if (readerThread != null) {
            readerThread.interrupt();
            readerThread = null;
        }

        if (writerThread != null) {
            writerThread.interrupt();
            writerThread = null;
        }

        for (Map<UUID, ShinnetaiStream> streamMap : streams.values()) {
            for (ShinnetaiStream stream : streamMap.values()) {
                if (stream.isRunning()) {
                    stream.close();
                }
            }
        }

        streams.clear();
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