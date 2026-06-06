package net.ldoin.shinnetai;

import net.ldoin.shinnetai.buffered.buf.smart.ReadOnlySmartByteBuf;
import net.ldoin.shinnetai.buffered.buf.smart.WriteOnlySmartByteBuf;
import net.ldoin.shinnetai.client.ShinnetaiClient;
import net.ldoin.shinnetai.client.options.ClientOptions;
import net.ldoin.shinnetai.debug.TrafficDirection;
import net.ldoin.shinnetai.debug.TrafficEvent;
import net.ldoin.shinnetai.debug.TrafficLog;
import net.ldoin.shinnetai.debug.TrafficPlayer;
import net.ldoin.shinnetai.exception.ShinnetaiExceptions;
import net.ldoin.shinnetai.log.ShinnetaiLog;
import net.ldoin.shinnetai.packet.AbstractPacket;
import net.ldoin.shinnetai.packet.common.ExceptionPacket;
import net.ldoin.shinnetai.packet.registry.ImmutablePacketRegistry;
import net.ldoin.shinnetai.packet.registry.PacketRegistry;
import net.ldoin.shinnetai.packet.registry.ShinnetaiPacket;
import net.ldoin.shinnetai.server.ShinnetaiServer;
import net.ldoin.shinnetai.server.options.ServerOptions;
import net.ldoin.shinnetai.worker.ShinnetaiWorkerContext;
import net.ldoin.shinnetai.worker.pipeline.ShinnetaiPipeline;
import net.ldoin.shinnetai.worker.pipeline.ShinnetaiPipelineHandleType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ShinnetaiTrafficLogTest {

    @ShinnetaiPacket(id = 999)
    private static class ValuePacket extends AbstractPacket<ShinnetaiWorkerContext<?>, ShinnetaiWorkerContext<?>> {
        int value;

        ValuePacket() {
        }

        ValuePacket(int value) {
            this.value = value;
        }

        @Override
        public void read(ReadOnlySmartByteBuf buf) {
            value = buf.readVarInt();
        }

        @Override
        public void write(WriteOnlySmartByteBuf buf) {
            buf.writeVarInt(value);
        }
    }

    @Test
    @Order(1)
    @DisplayName("capture() adds events to ring buffer")
    void capture_addsEventsToRing() {
        TrafficLog log = TrafficLog.inMemory();
        ShinnetaiPipeline pipeline = new ShinnetaiPipeline();
        pipeline.addLast(ShinnetaiPipelineHandleType.BEFORE_HANDLE, log.inUnit());
        pipeline.addLast(ShinnetaiPipelineHandleType.BEFORE_SEND, log.outUnit());

        pipeline.handle(ShinnetaiPipelineHandleType.BEFORE_HANDLE, new ValuePacket(1));
        pipeline.handle(ShinnetaiPipelineHandleType.BEFORE_SEND, new ValuePacket(2));

        List<TrafficEvent> events = log.getEvents();
        assertEquals(2, events.size());
        assertEquals(TrafficDirection.IN, events.get(0).direction());
        assertEquals(TrafficDirection.OUT, events.get(1).direction());
        assertEquals("ValuePacket", events.get(0).packetClass());
    }

    @Test
    @Order(2)
    @DisplayName("ring buffer evicts oldest when full")
    void ringBuffer_evictsOldest() {
        TrafficLog log = TrafficLog.builder().ringBufferSize(3).build();
        ShinnetaiPipeline pipeline = new ShinnetaiPipeline();
        pipeline.addLast(ShinnetaiPipelineHandleType.BEFORE_HANDLE, log.inUnit());

        for (int i = 0; i < 5; i++) {
            pipeline.handle(ShinnetaiPipelineHandleType.BEFORE_HANDLE, new ValuePacket(i));
        }

        assertEquals(3, log.size(), "Ring should hold at most 3 events");
    }

    @Test
    @Order(3)
    @DisplayName("stop() pauses recording, start() resumes it")
    void stop_pausesRecording() {
        TrafficLog log = TrafficLog.inMemory();
        ShinnetaiPipeline pipeline = new ShinnetaiPipeline();
        pipeline.addLast(ShinnetaiPipelineHandleType.BEFORE_HANDLE, log.inUnit());

        pipeline.handle(ShinnetaiPipelineHandleType.BEFORE_HANDLE, new ValuePacket(1));
        log.stop();
        pipeline.handle(ShinnetaiPipelineHandleType.BEFORE_HANDLE, new ValuePacket(2));

        assertEquals(1, log.size());
        log.start();
        pipeline.handle(ShinnetaiPipelineHandleType.BEFORE_HANDLE, new ValuePacket(3));
        assertEquals(2, log.size());
    }

    @Test
    @Order(4)
    @DisplayName("clear() resets ring buffer")
    void clear_resetsRing() {
        TrafficLog log = TrafficLog.inMemory();
        ShinnetaiPipeline pipeline = new ShinnetaiPipeline();
        pipeline.addLast(ShinnetaiPipelineHandleType.BEFORE_HANDLE, log.inUnit());

        pipeline.handle(ShinnetaiPipelineHandleType.BEFORE_HANDLE, new ValuePacket(1));
        assertEquals(1, log.size());
        log.clear();
        assertEquals(0, log.size());
    }

    @Test
    @Order(5)
    @DisplayName("saveTo + loadFrom round-trips events correctly")
    void saveLoad_roundtrip(@TempDir Path tmp) throws IOException {
        TrafficLog log = TrafficLog.inMemory();
        ShinnetaiPipeline pipeline = new ShinnetaiPipeline();
        pipeline.addLast(ShinnetaiPipelineHandleType.BEFORE_HANDLE, log.inUnit());
        pipeline.addLast(ShinnetaiPipelineHandleType.BEFORE_SEND, log.outUnit());

        pipeline.handle(ShinnetaiPipelineHandleType.BEFORE_HANDLE, new ValuePacket(42));
        pipeline.handle(ShinnetaiPipelineHandleType.BEFORE_SEND, new ValuePacket(99));

        Path file = tmp.resolve("test.trcl");
        log.saveTo(file);

        List<TrafficEvent> loaded = TrafficLog.loadFrom(file);
        assertEquals(2, loaded.size());
        assertEquals(TrafficDirection.IN, loaded.get(0).direction());
        assertEquals(TrafficDirection.OUT, loaded.get(1).direction());
        assertEquals("ValuePacket", loaded.get(0).packetClass());
        assertNotNull(loaded.get(0).rawBytes());
        assertTrue(loaded.getFirst().rawBytes().length > 0);
    }

    @Test
    @Order(6)
    @DisplayName("v2 VarInt format is compact for typical payloads")
    void binaryFormat_isCompact(@TempDir Path tmp) throws IOException {
        TrafficLog log = TrafficLog.inMemory();
        ShinnetaiPipeline pipeline = new ShinnetaiPipeline();
        pipeline.addLast(ShinnetaiPipelineHandleType.BEFORE_HANDLE, log.inUnit());

        for (int i = 0; i < 100; i++) {
            pipeline.handle(ShinnetaiPipelineHandleType.BEFORE_HANDLE, new ValuePacket(i));
        }

        Path file = tmp.resolve("compact.trcl");
        log.saveTo(file);

        long fileSize = java.nio.file.Files.size(file);
        assertTrue(fileSize < 3_072, "File too large for 100 small events: " + fileSize + " bytes");
    }

    @Test
    @Order(7)
    @DisplayName("chunked flush creates chunk files in directory")
    void chunkedFlush_createsFiles(@TempDir Path tmp) throws IOException {
        TrafficLog log = TrafficLog.builder()
                .directory(tmp)
                .maxChunkEvents(3)
                .build();
        ShinnetaiPipeline pipeline = new ShinnetaiPipeline();
        pipeline.addLast(ShinnetaiPipelineHandleType.BEFORE_HANDLE, log.inUnit());

        for (int i = 0; i < 7; i++) {
            pipeline.handle(ShinnetaiPipelineHandleType.BEFORE_HANDLE, new ValuePacket(i));
        }
        log.flushChunk();

        try (var stream = java.nio.file.Files.list(tmp)) {
            long chunkCount = stream
                    .filter(p -> TrafficLog.CHUNK_FILE_PATTERN.matcher(p.getFileName().toString()).matches())
                    .count();
            assertEquals(3, chunkCount, "Expected 3 chunk files for 7 events at maxChunkEvents=3");
        }
    }

    @Test
    @Order(8)
    @DisplayName("loadAll merges all chunk files in order")
    void loadAll_mergesChunks(@TempDir Path tmp) throws IOException {
        TrafficLog log = TrafficLog.builder()
                .directory(tmp)
                .maxChunkEvents(2)
                .build();
        ShinnetaiPipeline pipeline = new ShinnetaiPipeline();
        pipeline.addLast(ShinnetaiPipelineHandleType.BEFORE_HANDLE, log.inUnit());

        for (int i = 0; i < 4; i++) {
            pipeline.handle(ShinnetaiPipelineHandleType.BEFORE_HANDLE, new ValuePacket(i));
        }
        log.flushChunk();

        List<TrafficEvent> all = TrafficLog.loadAll(tmp);
        assertEquals(4, all.size());
    }

    @Test
    @Order(9)
    @DisplayName("TrafficPlayer replays IN events through handler")
    void player_replaysInEvents(@TempDir Path tmp) throws IOException {
        TrafficLog log = TrafficLog.inMemory();
        ShinnetaiPipeline pipeline = new ShinnetaiPipeline();
        pipeline.addLast(ShinnetaiPipelineHandleType.BEFORE_HANDLE, log.inUnit());
        pipeline.handle(ShinnetaiPipelineHandleType.BEFORE_HANDLE, new ValuePacket(7));
        pipeline.handle(ShinnetaiPipelineHandleType.BEFORE_HANDLE, new ValuePacket(13));

        Path file = tmp.resolve("replay.trcl");
        log.saveTo(file);

        List<AbstractPacket<?, ?>> replayed = new ArrayList<>();
        PacketRegistry registry = new PacketRegistry();
        registry.register(999, ValuePacket.class);

        TrafficPlayer.builder()
                .events(TrafficLog.loadFrom(file))
                .filter(TrafficDirection.IN)
                .build()
                .replay(registry, replayed::add);

        assertEquals(2, replayed.size());
    }

    @Test
    @Order(10)
    @DisplayName("TrafficPlayer with filter(OUT) replays only outbound events")
    void player_filterOut() {
        TrafficLog log = TrafficLog.inMemory();
        ShinnetaiPipeline pipeline = new ShinnetaiPipeline();
        pipeline.addLast(ShinnetaiPipelineHandleType.BEFORE_HANDLE, log.inUnit());
        pipeline.addLast(ShinnetaiPipelineHandleType.BEFORE_SEND, log.outUnit());
        pipeline.handle(ShinnetaiPipelineHandleType.BEFORE_HANDLE, new ValuePacket(1));
        pipeline.handle(ShinnetaiPipelineHandleType.BEFORE_SEND, new ValuePacket(2));

        List<AbstractPacket<?, ?>> replayed = new ArrayList<>();
        PacketRegistry registry = new PacketRegistry();
        registry.register(999, ValuePacket.class);

        TrafficPlayer.builder()
                .events(log.getEvents())
                .filter(TrafficDirection.OUT)
                .build()
                .replay(registry, replayed::add);

        assertEquals(1, replayed.size(), "Only 1 OUT event should be replayed");
    }

    @Test
    @Order(11)
    @DisplayName("time-travel: replaying recorded traffic reproduces the same error")
    void timeTravelDebug_reproducesError(@TempDir Path tmp) throws IOException {
        List<TrafficEvent> replayErrors = new CopyOnWriteArrayList<>();

        TrafficLog recorder = TrafficLog.inMemory();
        ShinnetaiPipeline pipeline = new ShinnetaiPipeline();
        pipeline.addLast(ShinnetaiPipelineHandleType.BEFORE_SEND, recorder.outUnit());

        ExceptionPacket bugPacket = new ExceptionPacket(ShinnetaiExceptions.FAILED_READ_PACKET, 1);
        pipeline.handle(ShinnetaiPipelineHandleType.BEFORE_SEND, bugPacket);

        assertEquals(1, recorder.size(), "Should have captured 1 event");
        assertEquals(TrafficDirection.OUT, recorder.getEvents().getFirst().direction());
        assertEquals("ExceptionPacket", recorder.getEvents().getFirst().packetClass());

        Path file = tmp.resolve("bug_report.trcl");
        recorder.saveTo(file);

        List<TrafficEvent> loaded = TrafficLog.loadFrom(file);
        assertEquals(1, loaded.size(), "Should load exactly 1 event from file");

        List<AbstractPacket<?, ?>> replayed = new ArrayList<>();
        ImmutablePacketRegistry registry = PacketRegistry.getCommons();

        TrafficPlayer.builder()
                .events(loaded)
                .filter(TrafficDirection.OUT)
                .onError((e, ev) -> replayErrors.add(ev))
                .build()
                .replay(registry, replayed::add);

        assertTrue(replayErrors.isEmpty(), "No errors expected during replay: " + replayErrors);
        assertEquals(1, replayed.size(), "One packet should be replayed");
        assertInstanceOf(ExceptionPacket.class, replayed.getFirst(),
                "Replayed packet must be ExceptionPacket  the original bug trigger");
    }

    @Test
    @Order(12)
    @DisplayName("WorkerOptions.setTrafficLog auto-wires TrafficLog on worker start")
    void workerOptions_autoWiresTrafficLog() throws Exception {
        TrafficLog log = TrafficLog.builder().ringBufferSize(50).build();

        int port = 9900;
        ShinnetaiServer<?> server = new ShinnetaiServer<>(ServerOptions.builder(port)
                .keepAlive(false)
                .build());
        server.start();

        ShinnetaiClient client = new ShinnetaiClient(ClientOptions.builder("localhost", port)
                .keepAlive(false)
                .trafficLog(log)
                .build());
        client.start();

        Thread.sleep(300);

        List<TrafficEvent> events = log.getEvents();
        assertFalse(events.isEmpty(), "TrafficLog should have captured at least the HandshakePacket");
        assertTrue(events.stream().anyMatch(e -> e.direction() == TrafficDirection.OUT),
                "Expected at least one OUT packet (HandshakePacket)");

        client.close();
    }

    @Test
    @Order(13)
    @DisplayName("ShinnetaiLog.enableTrafficRecording / disableTrafficRecording work correctly")
    void shinnetaiLog_enableDisableTrafficRecording(@TempDir Path tmp) {
        assertNull(ShinnetaiLog.getTrafficLog(), "Should start with no global log");

        TrafficLog log = ShinnetaiLog.enableTrafficRecording(tmp);
        assertNotNull(log, "enableTrafficRecording should return a non-null TrafficLog");
        assertSame(log, ShinnetaiLog.getTrafficLog(), "getTrafficLog should return the same instance");

        ShinnetaiLog.disableTrafficRecording();
        assertNull(ShinnetaiLog.getTrafficLog(), "After disable, global log should be null");
    }
}
