package net.ldoin.shinnetai.debug;

import net.ldoin.shinnetai.buffered.buf.smart.ReadOnlySmartByteBuf;
import net.ldoin.shinnetai.buffered.buf.smart.WriteOnlySmartByteBuf;
import net.ldoin.shinnetai.packet.AbstractPacket;
import net.ldoin.shinnetai.packet.registry.ShinnetaiPacket;
import net.ldoin.shinnetai.worker.pipeline.context.ShinnetaiPipelineContext;
import net.ldoin.shinnetai.worker.pipeline.handler.ShinnetaiPipelineHandler;
import net.ldoin.shinnetai.worker.pipeline.handler.ShinnetaiPipelineHandlerUnit;
import net.ldoin.shinnetai.worker.pipeline.handler.unit.SingleHandlerUnit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class TrafficLog {

    private static final int FILE_MAGIC = 0x54524343;
    private static final byte FILE_VERSION = 2;
    private static final DateTimeFormatter CHUNK_NAME_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    public static final Pattern CHUNK_FILE_PATTERN = Pattern.compile("traffic_\\d{8}_\\d{6}_\\d+\\.trcl");

    public static Builder builder() {
        return new Builder();
    }

    public static TrafficLog inMemory() {
        return builder().build();
    }

    public static TrafficLog toDirectory(Path dir) {
        return builder().directory(dir).build();
    }

    private final Path directory;
    private final int maxChunkEvents;
    private final int ringBufferSize;

    private final LinkedList<TrafficEvent> ring = new LinkedList<>();
    private final List<TrafficEvent> pending = new CopyOnWriteArrayList<>();
    private final AtomicInteger chunkCounter = new AtomicInteger(0);
    private volatile boolean recording = true;

    private TrafficLog(Builder builder) {
        this.directory = builder.directory;
        this.maxChunkEvents = builder.maxChunkEvents;
        this.ringBufferSize = builder.ringBufferSize;
    }

    public void start() {
        recording = true;
    }

    public void stop() {
        recording = false;
    }

    public boolean isRecording() {
        return recording;
    }

    public void clear() {
        synchronized (ring) {
            ring.clear();
        }

        pending.clear();
        chunkCounter.set(0);
    }

    public List<TrafficEvent> getEvents() {
        synchronized (ring) {
            return List.copyOf(ring);
        }
    }

    public int size() {
        synchronized (ring) {
            return ring.size();
        }
    }

    public ShinnetaiPipelineHandlerUnit inUnit() {
        return SingleHandlerUnit.of(new ShinnetaiPipelineHandler() {
            @Override
            public AbstractPacket<?, ?> handle(AbstractPacket<?, ?> packet, ShinnetaiPipelineContext ctx) {
                if (recording) capture(packet, TrafficDirection.IN);
                return packet;
            }

            @Override
            public String name() {
                return "TrafficLog-IN";
            }
        });
    }

    public ShinnetaiPipelineHandlerUnit outUnit() {
        return SingleHandlerUnit.of(new ShinnetaiPipelineHandler() {
            @Override
            public AbstractPacket<?, ?> handle(AbstractPacket<?, ?> packet, ShinnetaiPipelineContext ctx) {
                if (recording) {
                    capture(packet, TrafficDirection.OUT);
                }

                return packet;
            }

            @Override
            public String name() {
                return "TrafficLog-OUT";
            }
        });
    }

    private void capture(AbstractPacket<?, ?> packet, TrafficDirection direction) {
        try {
            WriteOnlySmartByteBuf buf = WriteOnlySmartByteBuf.empty();
            ShinnetaiPacket annotation = packet.getClass().getAnnotation(ShinnetaiPacket.class);
            if (annotation != null) {
                buf.writeVarInt(annotation.id());
            }

            packet.write(buf);
            addEvent(new TrafficEvent(
                    System.currentTimeMillis(),
                    direction,
                    packet.getClass().getSimpleName(),
                    buf.toBytes()
            ));
        } catch (Exception ignored) {
        }
    }

    private void addEvent(TrafficEvent event) {
        synchronized (ring) {
            ring.addLast(event);
            if (ring.size() > ringBufferSize) {
                ring.removeFirst();
            }
        }

        if (directory != null) {
            pending.add(event);
            if (pending.size() >= maxChunkEvents) {
                try {
                    flushChunk();
                } catch (IOException ignored) {
                }
            }
        }
    }

    public synchronized void flushChunk() throws IOException {
        if (directory == null || pending.isEmpty()) {
            return;
        }

        Files.createDirectories(directory);
        List<TrafficEvent> toWrite = new ArrayList<>(pending);
        pending.clear();
        String ts = LocalDateTime.now().format(CHUNK_NAME_FMT);
        Path file = directory.resolve(String.format("traffic_%s_%03d.trcl", ts, chunkCounter.getAndIncrement()));
        writeEvents(file, toWrite);
    }

    public void saveTo(Path file) throws IOException {
        writeEvents(file, getEvents());
    }

    public static void writeEvents(Path file, List<TrafficEvent> events) throws IOException {
        WriteOnlySmartByteBuf buf = WriteOnlySmartByteBuf.empty();
        buf.writeInt(FILE_MAGIC);
        buf.writeByte(FILE_VERSION);
        long prevTs = 0;
        for (TrafficEvent e : events) {
            buf.writeVarLong(e.timestamp() - prevTs);
            prevTs = e.timestamp();
            buf.writeByte((byte) e.direction().ordinal());
            buf.writeString(e.packetClass());
            buf.writeBytes(e.rawBytes());
        }

        Files.write(file, buf.toBytes());
    }

    public static List<TrafficEvent> loadFrom(Path file) throws IOException {
        byte[] data = Files.readAllBytes(file);
        ReadOnlySmartByteBuf buf = ReadOnlySmartByteBuf.of(data);
        int magic = buf.readInt();
        if (magic != FILE_MAGIC) {
            throw new IOException("Not a valid TrafficLog file: " + file);
        }

        int version = buf.readByte() & 0xFF;
        if (version != FILE_VERSION) {
            throw new IOException("Unsupported TrafficLog version: " + version);
        }

        List<TrafficEvent> events = new ArrayList<>();
        long prevTs = 0;
        while (buf.remain() > 0) {
            long delta = buf.readVarLong();
            long ts = prevTs + delta;
            prevTs = ts;
            int dirOrd = buf.readByte() & 0xFF;
            TrafficDirection dir = dirOrd < TrafficDirection.VALUES.length ? TrafficDirection.VALUES[dirOrd] : TrafficDirection.IN;
            String cls = buf.readString();
            byte[] raw = buf.readBytes();
            events.add(new TrafficEvent(ts, dir, cls, raw));
        }

        return events;
    }

    public static List<TrafficEvent> loadAll(Path directory) throws IOException {
        List<TrafficEvent> all = new ArrayList<>();
        try (Stream<Path> files = Files.list(directory)) {
            List<Path> chunks = files
                    .filter(p -> CHUNK_FILE_PATTERN.matcher(p.getFileName().toString()).matches())
                    .sorted()
                    .toList();
            for (Path chunk : chunks) {
                all.addAll(loadFrom(chunk));
            }
        }

        return all;
    }

    public static final class Builder {

        private Path directory = null;
        private int maxChunkEvents = 10_000;
        private int ringBufferSize = 1_000;

        public Builder directory(Path directory) {
            this.directory = directory;
            return this;
        }

        public Builder maxChunkEvents(int maxChunkEvents) {
            this.maxChunkEvents = maxChunkEvents;
            return this;
        }

        public Builder ringBufferSize(int ringBufferSize) {
            this.ringBufferSize = ringBufferSize;
            return this;
        }

        public TrafficLog build() {
            return new TrafficLog(this);
        }
    }
}