package net.ldoin.shinnetai;

import net.ldoin.shinnetai.client.ShinnetaiClient;
import net.ldoin.shinnetai.client.options.ClientOptions;
import net.ldoin.shinnetai.exception.ShinnetaiExceptions;
import net.ldoin.shinnetai.handler.PacketHandler;
import net.ldoin.shinnetai.handler.PacketHandlerRegistry;
import net.ldoin.shinnetai.packet.AbstractPacket;
import net.ldoin.shinnetai.packet.common.ExceptionPacket;
import net.ldoin.shinnetai.protocol.ShinnetaiFeature;
import net.ldoin.shinnetai.resilience.CircuitBreaker;
import net.ldoin.shinnetai.packet.schedule.PacketScheduler;
import net.ldoin.shinnetai.server.ShinnetaiServer;
import net.ldoin.shinnetai.server.options.ServerOptions;
import net.ldoin.shinnetai.worker.QueueOverflowStrategy;
import net.ldoin.shinnetai.worker.ShinnetaiWorkerContext;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.List;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ShinnetaiFeaturesTest {

    private static final int BASE_PORT = 8400;

    @Test
    @Order(1)
    @DisplayName("CircuitBreaker: CLOSED → OPEN after failureThreshold")
    void circuitBreaker_openAfterFailures() {
        CircuitBreaker breaker = CircuitBreaker.builder()
                .failureThreshold(3)
                .openDuration(60_000)
                .build();

        assertEquals(CircuitBreaker.State.CLOSED, breaker.getState());
        assertTrue(breaker.isCallAllowed());

        breaker.recordFailure();
        breaker.recordFailure();
        assertEquals(CircuitBreaker.State.CLOSED, breaker.getState());

        breaker.recordFailure();
        assertEquals(CircuitBreaker.State.OPEN, breaker.getState());
        assertFalse(breaker.isCallAllowed());
    }

    @Test
    @Order(2)
    @DisplayName("CircuitBreaker: OPEN → HALF_OPEN after openDuration")
    void circuitBreaker_halfOpenAfterDuration() throws InterruptedException {
        CircuitBreaker breaker = CircuitBreaker.builder()
                .failureThreshold(1)
                .openDuration(50)
                .halfOpenMaxCalls(1)
                .build();

        breaker.recordFailure();
        assertEquals(CircuitBreaker.State.OPEN, breaker.getState());
        assertFalse(breaker.isCallAllowed());

        Thread.sleep(70);
        assertEquals(CircuitBreaker.State.HALF_OPEN, breaker.getState());
        assertTrue(breaker.isCallAllowed());
    }

    @Test
    @Order(3)
    @DisplayName("CircuitBreaker: HALF_OPEN → CLOSED after success")
    void circuitBreaker_closedAfterHalfOpenSuccess() throws InterruptedException {
        CircuitBreaker breaker = CircuitBreaker.builder()
                .failureThreshold(1)
                .openDuration(50)
                .halfOpenMaxCalls(1)
                .build();

        breaker.recordFailure();
        Thread.sleep(70);
        assertEquals(CircuitBreaker.State.HALF_OPEN, breaker.getState());

        breaker.recordSuccess();
        assertEquals(CircuitBreaker.State.CLOSED, breaker.getState());
        assertTrue(breaker.isCallAllowed());
    }

    @Test
    @Order(4)
    @DisplayName("CircuitBreaker: HALF_OPEN → OPEN on failure")
    void circuitBreaker_backToOpenOnHalfOpenFailure() throws InterruptedException {
        CircuitBreaker breaker = CircuitBreaker.builder()
                .failureThreshold(1)
                .openDuration(50)
                .halfOpenMaxCalls(1)
                .build();

        breaker.recordFailure();
        Thread.sleep(70);
        assertEquals(CircuitBreaker.State.HALF_OPEN, breaker.getState());

        breaker.recordFailure();
        assertEquals(CircuitBreaker.State.OPEN, breaker.getState());
    }

    @Test
    @Order(5)
    @DisplayName("CircuitBreaker: onStateChange callback fires")
    void circuitBreaker_stateChangeCallback() {
        List<CircuitBreaker.State> states = new CopyOnWriteArrayList<>();
        CircuitBreaker breaker = CircuitBreaker.builder()
                .failureThreshold(1)
                .openDuration(60_000)
                .onStateChange(states::add)
                .build();

        breaker.recordFailure();
        assertEquals(1, states.size());
        assertEquals(CircuitBreaker.State.OPEN, states.get(0));
    }

    @Test
    @Order(6)
    @DisplayName("CircuitBreaker: reset() returns to CLOSED")
    void circuitBreaker_reset() {
        CircuitBreaker breaker = CircuitBreaker.builder()
                .failureThreshold(1)
                .openDuration(60_000)
                .build();

        breaker.recordFailure();
        assertEquals(CircuitBreaker.State.OPEN, breaker.getState());

        breaker.reset();
        assertEquals(CircuitBreaker.State.CLOSED, breaker.getState());
        assertTrue(breaker.isCallAllowed());
    }

    @Test
    @Order(7)
    @DisplayName("PacketHandlerRegistry: lambda handler receives correct packet")
    void packetHandlerRegistry_lambdaDispatch() {
        PacketHandlerRegistry registry = PacketHandlerRegistry.create();
        List<AbstractPacket<?, ?>> received = new CopyOnWriteArrayList<>();

        registry.on(ExceptionPacket.class, (pkt, ctx) -> received.add(pkt));

        ExceptionPacket ep = new ExceptionPacket(ShinnetaiExceptions.FAILED_READ_PACKET, 1);
        registry.dispatch(ep, null);

        assertEquals(1, received.size());
        assertSame(ep, received.get(0));
    }

    @Test
    @Order(8)
    @DisplayName("PacketHandlerRegistry: multiple lambdas all called")
    void packetHandlerRegistry_multipleLambdas() {
        PacketHandlerRegistry registry = PacketHandlerRegistry.create();
        AtomicInteger count = new AtomicInteger();

        registry.on(ExceptionPacket.class, (p, c) -> count.incrementAndGet());
        registry.on(ExceptionPacket.class, (p, c) -> count.incrementAndGet());

        registry.dispatch(new ExceptionPacket(ShinnetaiExceptions.FAILED_READ_PACKET, 0), null);

        assertEquals(2, count.get());
    }

    @Test
    @Order(9)
    @DisplayName("PacketHandlerRegistry: no handler → dispatch returns false")
    void packetHandlerRegistry_noHandler() {
        PacketHandlerRegistry registry = PacketHandlerRegistry.create();
        boolean dispatched = registry.dispatch(new ExceptionPacket(ShinnetaiExceptions.FAILED_READ_PACKET, 0), null);
        assertFalse(dispatched);
    }

    static class MyPacketHandlers {
        final List<ExceptionPacket> received = new CopyOnWriteArrayList<>();
        final List<ShinnetaiWorkerContext<?>> contexts = new CopyOnWriteArrayList<>();

        @PacketHandler
        public void onException(ExceptionPacket p) {
            received.add(p);
        }

        @PacketHandler
        public void onExceptionWithCtx(ExceptionPacket p, ShinnetaiWorkerContext<?> ctx) {
            contexts.add(ctx);
        }
    }

    @Test
    @Order(10)
    @DisplayName("@PacketHandler: 1-arg method is scanned and dispatched")
    void packetHandlerAnnotation_oneArgMethod() {
        MyPacketHandlers handler = new MyPacketHandlers();
        PacketHandlerRegistry registry = PacketHandlerRegistry.create();
        registry.register(handler);

        ExceptionPacket ep = new ExceptionPacket(ShinnetaiExceptions.FAILED_READ_PACKET, 0);
        registry.dispatch(ep, null);

        assertEquals(1, handler.received.size());
        assertSame(ep, handler.received.get(0));
    }

    @Test
    @Order(11)
    @DisplayName("@PacketHandler: 2-arg method (with context) is scanned and dispatched")
    void packetHandlerAnnotation_twoArgMethod() {
        MyPacketHandlers handler = new MyPacketHandlers();
        PacketHandlerRegistry registry = PacketHandlerRegistry.create();
        registry.register(handler);

        registry.dispatch(new ExceptionPacket(ShinnetaiExceptions.FAILED_READ_PACKET, 0), null);

        assertEquals(1, handler.contexts.size());
    }

    @Test
    @Order(12)
    @DisplayName("QueueOverflowStrategy has DROP_OLDEST and DROP_NEWEST")
    void queueOverflowStrategy_enumValues() {
        Set<String> names = new java.util.HashSet<>();
        for (QueueOverflowStrategy s : QueueOverflowStrategy.values()) {
            names.add(s.name());
        }
        assertTrue(names.contains("DROP_OLDEST"));
        assertTrue(names.contains("DROP_NEWEST"));
    }

    @Test
    @Order(13)
    @DisplayName("DROP_OLDEST: oldest packet evicted when queue is full")
    void dropOldest_evictsHead() throws Exception {
        AtomicInteger droppedCount = new AtomicInteger();
        List<String> dropOrder = new CopyOnWriteArrayList<>();

        ShinnetaiServer<?> server = new ShinnetaiServer<>(
                ServerOptions.of(BASE_PORT));
        server.start();

        ClientOptions clientOpts = ClientOptions.builder("localhost", BASE_PORT)
                .maxQueueSize(1)
                .queueOverflowStrategy(QueueOverflowStrategy.DROP_OLDEST)
                .onPacketDropped(wp -> {
                    droppedCount.incrementAndGet();
                    dropOrder.add("dropped");
                })
                .build();

        ShinnetaiClient client = new ShinnetaiClient(clientOpts);

        ExceptionPacket p1 = new ExceptionPacket(ShinnetaiExceptions.FAILED_READ_PACKET, 1);
        ExceptionPacket p2 = new ExceptionPacket(ShinnetaiExceptions.FAILED_READ_PACKET, 2);
        ExceptionPacket p3 = new ExceptionPacket(ShinnetaiExceptions.FAILED_READ_PACKET, 3);

        client.addPacket(p1);
        client.addPacket(p2);
        client.addPacket(p3);

        assertEquals(2, droppedCount.get(), "Two packets should have been dropped (p1, p2)");
    }

    @Test
    @Order(14)
    @DisplayName("DROP_NEWEST: incoming packet dropped when queue is full")
    void dropNewest_dropsIncoming() throws Exception {
        AtomicInteger droppedCount = new AtomicInteger();

        ShinnetaiServer<?> server = new ShinnetaiServer<>(
                ServerOptions.of(BASE_PORT + 1));
        server.start();

        ClientOptions clientOpts = ClientOptions.builder("localhost", BASE_PORT + 1)
                .maxQueueSize(1)
                .queueOverflowStrategy(QueueOverflowStrategy.DROP_NEWEST)
                .onPacketDropped(wp -> droppedCount.incrementAndGet())
                .build();

        ShinnetaiClient client = new ShinnetaiClient(clientOpts);

        ExceptionPacket p1 = new ExceptionPacket(ShinnetaiExceptions.FAILED_READ_PACKET, 1);
        ExceptionPacket p2 = new ExceptionPacket(ShinnetaiExceptions.FAILED_READ_PACKET, 2);

        client.addPacket(p1);
        client.addPacket(p2);

        assertEquals(1, droppedCount.get(), "Only the incoming packet p2 should be dropped");
    }

    @Test
    @Order(15)
    @DisplayName("ShinnetaiFeature: toFlags / fromFlags round-trip")
    void shinnetaiFeature_flagsRoundTrip() {
        long flags = ShinnetaiFeature.toFlags(ShinnetaiFeature.COMPRESSION, ShinnetaiFeature.TRAFFIC_LOG);
        Set<ShinnetaiFeature> parsed = ShinnetaiFeature.fromFlags(flags);

        assertEquals(EnumSet.of(ShinnetaiFeature.COMPRESSION, ShinnetaiFeature.TRAFFIC_LOG), parsed);
    }

    @Test
    @Order(16)
    @DisplayName("ShinnetaiFeature: feature intersection (negotiation logic)")
    void shinnetaiFeature_intersection() {
        long clientFlags = ShinnetaiFeature.toFlags(ShinnetaiFeature.COMPRESSION);
        long serverFlags = ShinnetaiFeature.toFlags(ShinnetaiFeature.COMPRESSION, ShinnetaiFeature.TRAFFIC_LOG);

        long negotiated = clientFlags & serverFlags;

        Set<ShinnetaiFeature> features = ShinnetaiFeature.fromFlags(negotiated);
        assertTrue(features.contains(ShinnetaiFeature.COMPRESSION));
        assertFalse(features.contains(ShinnetaiFeature.TRAFFIC_LOG));
    }

    @Test
    @Order(17)
    @DisplayName("Protocol negotiation: client becomes handshaked after server ack")
    void protocolNegotiation_clientHandshaked() throws Exception {
        ShinnetaiServer<?> server = new ShinnetaiServer<>(
                ServerOptions.builder(BASE_PORT + 2)
                        .supportedFeatures(ShinnetaiFeature.COMPRESSION, ShinnetaiFeature.TRAFFIC_LOG)
                        .build());
        server.start();

        ShinnetaiClient client = new ShinnetaiClient(
                ClientOptions.builder("localhost", BASE_PORT + 2)
                        .supportedFeatures(ShinnetaiFeature.COMPRESSION)
                        .build());
        client.start();

        Thread.sleep(300);

        assertTrue(client.hasFeature(ShinnetaiFeature.COMPRESSION),
                "Client should have COMPRESSION feature negotiated");
        assertFalse(client.hasFeature(ShinnetaiFeature.TRAFFIC_LOG),
                "Client should NOT have TRAFFIC_LOG (client didn't advertise it)");
    }

    @Test
    @Order(18)
    @DisplayName("Protocol negotiation: server stores negotiated features")
    void protocolNegotiation_serverNegotiated() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Boolean> serverHasCompression = new AtomicReference<>(null);

        ShinnetaiServer<?> server = new ShinnetaiServer<>(
                ServerOptions.builder(BASE_PORT + 3)
                        .supportedFeatures(ShinnetaiFeature.COMPRESSION, ShinnetaiFeature.TRAFFIC_LOG)
                        .build());

        server.on(ExceptionPacket.class, (pkt, ctx) -> {
            serverHasCompression.set(ctx.hasFeature(ShinnetaiFeature.COMPRESSION));
            latch.countDown();
        });

        server.start();

        ShinnetaiClient client = new ShinnetaiClient(
                ClientOptions.builder("localhost", BASE_PORT + 3)
                        .supportedFeatures(ShinnetaiFeature.COMPRESSION)
                        .build());
        client.start();

        Thread.sleep(100);
        client.addPacket(new ExceptionPacket(ShinnetaiExceptions.FAILED_READ_PACKET, 1));

        assertTrue(latch.await(3, TimeUnit.SECONDS), "ExceptionPacket handler should have fired");
        assertNotNull(serverHasCompression.get());
        assertTrue(serverHasCompression.get(), "Server connection should have COMPRESSION negotiated");
    }

    @Test
    @Order(19)
    @DisplayName("PacketScheduler: sends packets at configured interval to server")
    void packetScheduler_broadcastsToServer() throws Exception {
        AtomicInteger received = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(2);

        ShinnetaiServer<?> server = new ShinnetaiServer<>(
                ServerOptions.of(BASE_PORT + 4));

        server.on(ExceptionPacket.class, (pkt, ctx) -> {
            received.incrementAndGet();
            latch.countDown();
        });

        server.start();

        ShinnetaiClient client = new ShinnetaiClient(
                ClientOptions.of("localhost", BASE_PORT + 4));
        client.start();

        Thread.sleep(200);

        PacketScheduler scheduler = PacketScheduler.builder()
                .every(Duration.ofMillis(100))
                .send(() -> new ExceptionPacket(ShinnetaiExceptions.FAILED_READ_PACKET, 0))
                .to(client)
                .build();

        scheduler.start();
        assertTrue(scheduler.isRunning());

        boolean done = latch.await(3, TimeUnit.SECONDS);
        scheduler.stop();
        assertFalse(scheduler.isRunning());
        assertTrue(done, "Scheduler should have sent ≥2 packets in 3 seconds");
        assertTrue(received.get() >= 2);
    }

    static class ServerSideHandler {
        final List<ExceptionPacket> received = new CopyOnWriteArrayList<>();

        @PacketHandler
        public void onException(ExceptionPacket p) {
            received.add(p);
        }
    }

    @Test
    @Order(20)
    @DisplayName("ShinnetaiServer.on(): lambda handler is invoked for incoming packet")
    void server_on_lambdaHandler() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ExceptionPacket> capture = new AtomicReference<>();

        ShinnetaiServer<?> server = new ShinnetaiServer<>(
                ServerOptions.of(BASE_PORT + 5));

        server.on(ExceptionPacket.class, (pkt, ctx) -> {
            capture.set(pkt);
            latch.countDown();
        });

        server.start();

        ShinnetaiClient client = new ShinnetaiClient(
                ClientOptions.of("localhost", BASE_PORT + 5));
        client.start();

        Thread.sleep(100);
        client.addPacket(new ExceptionPacket(ShinnetaiExceptions.FAILED_READ_PACKET, 42));

        assertTrue(latch.await(3, TimeUnit.SECONDS), "Handler should have been called");
        assertNotNull(capture.get(), "Captured packet should not be null");
    }

    @Test
    @Order(21)
    @DisplayName("ShinnetaiServer.registerHandlers(): @PacketHandler method is invoked")
    void server_registerHandlers_annotationMethod() throws Exception {
        ServerSideHandler handler = new ServerSideHandler();
        CountDownLatch latch = new CountDownLatch(1);

        PacketHandlerRegistry registry = PacketHandlerRegistry.create();
        registry.register(handler);

        ShinnetaiServer<?> server = new ShinnetaiServer<>(
                ServerOptions.builder(BASE_PORT + 6)
                        .packetHandlerRegistry(registry)
                        .build());
        server.start();

        ShinnetaiClient client = new ShinnetaiClient(
                ClientOptions.of("localhost", BASE_PORT + 6));
        client.start();

        Thread.sleep(100);
        client.addPacket(new ExceptionPacket(ShinnetaiExceptions.FAILED_READ_PACKET, 0));

        long deadline = System.currentTimeMillis() + 2000;
        while (handler.received.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertFalse(handler.received.isEmpty(), "@PacketHandler method should have been invoked");
    }

    @Test
    @Order(22)
    @DisplayName("ClientOptions.circuitBreaker() sets the breaker correctly")
    void clientOptions_circuitBreaker_stored() {
        CircuitBreaker breaker = CircuitBreaker.builder().failureThreshold(3).build();
        ClientOptions opts = ClientOptions.builder("localhost", 9999)
                .circuitBreaker(breaker)
                .build();
        assertSame(breaker, opts.getCircuitBreaker());
    }
}
