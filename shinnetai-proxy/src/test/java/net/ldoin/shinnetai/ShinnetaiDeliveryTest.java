package net.ldoin.shinnetai;

import net.ldoin.shinnetai.client.ShinnetaiClient;
import net.ldoin.shinnetai.client.options.ClientOptions;
import net.ldoin.shinnetai.delivery.DeliveryGuarantee;
import net.ldoin.shinnetai.delivery.PacketOutbox;
import net.ldoin.shinnetai.delivery.PacketOutboxStoreResult;
import net.ldoin.shinnetai.handler.PacketHandlerRegistry;
import net.ldoin.shinnetai.packet.PacketOptions;
import net.ldoin.shinnetai.packet.WrappedPacket;
import net.ldoin.shinnetai.packet.common.ExceptionPacket;
import net.ldoin.shinnetai.packet.registry.PacketRegistry;
import net.ldoin.shinnetai.packet.test.TestCounterPacket;
import net.ldoin.shinnetai.server.ShinnetaiServer;
import net.ldoin.shinnetai.server.options.ServerOptions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShinnetaiDeliveryTest {

    private static final int BASE_PORT = 8610;

    @Test
    void outboxTrackAndAck() {
        PacketOutbox outbox = PacketOutbox.builder()
                .sessionTtl(Duration.ofMinutes(1))
                .maxPacketsPerSession(20)
                .build();

        WrappedPacket tracked = outbox.track(42,
                WrappedPacket.of(new ExceptionPacket()),
                () -> 77L);

        assertTrue(tracked.getOptionValue(PacketOptions.DELIVERY_TRACKED));
        assertEquals(77L, tracked.getPacketId());
        assertTrue(outbox.hasSession(42));

        assertTrue(outbox.ack(42, 77L));
        assertFalse(outbox.hasSession(42));
    }

    @Test
    void outboxRejectsOverflowWithoutEvictingOlderReliablePackets() {
        PacketOutbox outbox = PacketOutbox.builder()
                .sessionTtl(Duration.ofMinutes(1))
                .maxPacketsPerSession(1)
                .build();

        var first = outbox.tryEnqueue(42, WrappedPacket.of(new ExceptionPacket()), () -> 101L);
        var second = outbox.tryEnqueue(42, WrappedPacket.of(new ExceptionPacket()), () -> 102L);

        assertEquals(PacketOutboxStoreResult.STORED, first.result());
        assertEquals(PacketOutboxStoreResult.REJECTED_MAX_PACKETS, second.result());

        List<WrappedPacket> replay = outbox.collectForReplay(42, System.currentTimeMillis(), 0);
        assertEquals(1, replay.size());
        assertEquals(101L, replay.getFirst().getPacketId());
        outbox.close();
    }

    @Test
    void outboxRejectsNewSessionsWhenMaxSessionLimitReached() {
        PacketOutbox outbox = PacketOutbox.builder()
                .sessionTtl(Duration.ofMinutes(1))
                .maxSessions(1)
                .build();

        var first = outbox.tryEnqueue(1, WrappedPacket.of(new ExceptionPacket()), () -> 201L);
        var second = outbox.tryEnqueue(2, WrappedPacket.of(new ExceptionPacket()), () -> 202L);

        assertEquals(PacketOutboxStoreResult.STORED, first.result());
        assertEquals(PacketOutboxStoreResult.REJECTED_MAX_SESSIONS, second.result());
        assertTrue(outbox.hasSession(1));
        assertFalse(outbox.hasSession(2));
        outbox.close();
    }

    @Test
    void outboxEvictsByTtl() throws Exception {
        PacketOutbox outbox = PacketOutbox.builder()
                .sessionTtl(Duration.ofMillis(120))
                .maxPacketsPerSession(20)
                .build();

        outbox.enqueue(7, WrappedPacket.of(new ExceptionPacket()), () -> 1L);
        assertTrue(outbox.hasSession(7));

        long deadline = System.currentTimeMillis() + 1500;
        while (System.currentTimeMillis() < deadline && outbox.hasSession(7)) {
            Thread.sleep(20);
        }

        assertFalse(outbox.hasSession(7));
        outbox.close();
    }

    @Test
    void deduplicatesTrackedPacketsOnServer() throws Exception {
        int port = BASE_PORT;
        PacketRegistry registry = new PacketRegistry().withCommons().register(TestCounterPacket.class);

        AtomicInteger handled = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(1);

        ShinnetaiServer<?> server = new ShinnetaiServer<>(registry, ServerOptions.builder(port).build());
        server.on(TestCounterPacket.class, (packet, ctx) -> {
            handled.incrementAndGet();
            latch.countDown();
        });

        ShinnetaiClient client = null;
        try {
            server.start();
            client = new ShinnetaiClient(registry, ClientOptions.builder("localhost", port).id(300).build());
            client.start();
            Thread.sleep(300);

            WrappedPacket duplicate = WrappedPacket.builder(new TestCounterPacket(1))
                    .withOption(PacketOptions.DELIVERY_TRACKED)
                    .packetId(991L)
                    .build();

            client.sendPacket(duplicate);
            client.sendPacket(duplicate);

            assertTrue(latch.await(3, TimeUnit.SECONDS));
            Thread.sleep(200);
            assertEquals(1, handled.get());
        } finally {
            if (client != null) {
                client.close();
            }
            server.close();
        }
    }

    @Test
    void ackRemovesTrackedPacketsAfterDelivery() throws Exception {
        int port = BASE_PORT + 1;
        PacketRegistry registry = new PacketRegistry().withCommons().register(TestCounterPacket.class);
        PacketOutbox outbox = PacketOutbox.builder().sessionTtl(Duration.ofMinutes(2)).build();

        CountDownLatch clientReceived = new CountDownLatch(1);
        PacketHandlerRegistry clientHandlers = PacketHandlerRegistry.create()
                .on(TestCounterPacket.class, (packet, ctx) -> clientReceived.countDown());

        ShinnetaiServer<?> server = new ShinnetaiServer<>(registry,
                ServerOptions.builder(port)
                        .packetOutbox(outbox)
                        .build());

        ShinnetaiClient client = null;
        try {
            server.start();
            client = new ShinnetaiClient(registry,
                    ClientOptions.builder("localhost", port)
                            .id(400)
                            .packetHandlerRegistry(clientHandlers)
                            .build());
            client.start();
            Thread.sleep(300);

            server.sendToClient(400, new TestCounterPacket(7));
            assertTrue(clientReceived.await(3, TimeUnit.SECONDS));

            long deadline = System.currentTimeMillis() + 3000;
            while (System.currentTimeMillis() < deadline && outbox.hasSession(400)) {
                Thread.sleep(20);
            }

            assertFalse(outbox.hasSession(400));
        } finally {
            if (client != null) {
                client.close();
            }
            server.close();
            outbox.close();
        }
    }

    @Test
    void replaysOfflinePacketsOnReconnect() throws Exception {
        int port = BASE_PORT + 2;
        PacketRegistry registry = new PacketRegistry().withCommons().register(TestCounterPacket.class);
        PacketOutbox outbox = PacketOutbox.builder().sessionTtl(Duration.ofMinutes(2)).build();

        CountDownLatch clientReceived = new CountDownLatch(2);
        AtomicInteger valuesSum = new AtomicInteger();

        PacketHandlerRegistry clientHandlers = PacketHandlerRegistry.create()
                .on(TestCounterPacket.class, (packet, ctx) -> {
                    valuesSum.addAndGet(packet.getValue());
                    clientReceived.countDown();
                });

        ShinnetaiServer<?> server = new ShinnetaiServer<>(registry,
                ServerOptions.builder(port)
                        .packetOutbox(outbox)
                        .build());

        ShinnetaiClient client = null;
        try {
            server.start();

            server.sendToClient(500, new TestCounterPacket(10));
            server.sendToClient(500, new TestCounterPacket(15));
            assertTrue(outbox.hasSession(500));

            client = new ShinnetaiClient(registry,
                    ClientOptions.builder("localhost", port)
                            .id(500)
                            .packetHandlerRegistry(clientHandlers)
                            .build());
            client.start();

            assertTrue(clientReceived.await(4, TimeUnit.SECONDS));
            assertEquals(25, valuesSum.get());

            long deadline = System.currentTimeMillis() + 3000;
            while (System.currentTimeMillis() < deadline && outbox.hasSession(500)) {
                Thread.sleep(20);
            }
            assertFalse(outbox.hasSession(500));
        } finally {
            if (client != null) {
                client.close();
            }
            server.close();
            outbox.close();
        }
    }

    @Test
    void duplicateTrackedServerToClientHandledOnce() throws Exception {
        int port = BASE_PORT + 3;
        PacketRegistry registry = new PacketRegistry().withCommons().register(TestCounterPacket.class);
        PacketOutbox outbox = PacketOutbox.builder().sessionTtl(Duration.ofMinutes(2)).build();

        AtomicInteger handled = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(1);
        PacketHandlerRegistry clientHandlers = PacketHandlerRegistry.create()
                .on(TestCounterPacket.class, (packet, ctx) -> {
                    handled.incrementAndGet();
                    latch.countDown();
                });

        ShinnetaiServer<?> server = new ShinnetaiServer<>(registry,
                ServerOptions.builder(port)
                        .packetOutbox(outbox)
                        .build());

        ShinnetaiClient client = null;
        try {
            server.start();
            client = new ShinnetaiClient(registry,
                    ClientOptions.builder("localhost", port)
                            .id(600)
                            .packetHandlerRegistry(clientHandlers)
                            .build());
            client.start();
            Thread.sleep(300);

            WrappedPacket duplicate = WrappedPacket.builder(new TestCounterPacket(99))
                    .withOption(PacketOptions.DELIVERY_TRACKED)
                    .packetId(123456L)
                    .build();

            server.sendToClient(600, duplicate);
            server.sendToClient(600, duplicate);

            assertTrue(latch.await(4, TimeUnit.SECONDS));
            Thread.sleep(250);
            assertEquals(1, handled.get());

            List<WrappedPacket> drained = outbox.drain(600);
            assertTrue(drained.isEmpty());
        } finally {
            if (client != null) {
                client.close();
            }
            server.close();
            outbox.close();
        }
    }

    @Test
    void bestEffortOfflinePacketsAreNotBuffered() throws Exception {
        int port = BASE_PORT + 4;
        PacketRegistry registry = new PacketRegistry().withCommons().register(TestCounterPacket.class);
        PacketOutbox outbox = PacketOutbox.builder().sessionTtl(Duration.ofMinutes(2)).build();

        ShinnetaiServer<?> server = new ShinnetaiServer<>(registry,
                ServerOptions.builder(port)
                        .packetOutbox(outbox)
                        .defaultDeliveryGuarantee(DeliveryGuarantee.BEST_EFFORT)
                        .build());

        try {
            server.start();
            server.sendToClient(777, new TestCounterPacket(1));
            assertFalse(outbox.hasSession(777));
        } finally {
            server.close();
            outbox.close();
        }
    }

    @Test
    void reliablePredicateBuffersOnlySelectedPackets() throws Exception {
        int port = BASE_PORT + 5;
        PacketRegistry registry = new PacketRegistry().withCommons().register(TestCounterPacket.class);
        PacketOutbox outbox = PacketOutbox.builder().sessionTtl(Duration.ofMinutes(2)).build();

        ShinnetaiServer<?> server = new ShinnetaiServer<>(registry,
                ServerOptions.builder(port)
                        .packetOutbox(outbox)
                        .defaultDeliveryGuarantee(DeliveryGuarantee.BEST_EFFORT)
                        .reliablePacketPredicate(packet -> packet instanceof ExceptionPacket)
                        .build());

        try {
            server.start();
            server.sendToClient(888, new TestCounterPacket(5));
            server.sendToClient(888, new ExceptionPacket());

            List<WrappedPacket> drained = outbox.drain(888);
            assertEquals(1, drained.size());
            assertTrue(drained.getFirst().getPacket() instanceof ExceptionPacket);
        } finally {
            server.close();
            outbox.close();
        }
    }

    @Test
    void serverReportsReliablePacketRejectedWhenOutboxIsFull() throws Exception {
        int port = BASE_PORT + 8;
        PacketRegistry registry = new PacketRegistry().withCommons().register(TestCounterPacket.class);
        PacketOutbox outbox = PacketOutbox.builder()
                .sessionTtl(Duration.ofMinutes(2))
                .maxPacketsPerSession(1)
                .build();

        AtomicReference<PacketOutboxStoreResult> rejected = new AtomicReference<>();
        ShinnetaiServer<?> server = new ShinnetaiServer<>(registry,
                ServerOptions.builder(port)
                        .packetOutbox(outbox)
                        .onReliablePacketRejected(result -> rejected.set(result.result()))
                        .build());

        try {
            server.start();
            server.sendReliableToClient(999, new TestCounterPacket(1));
            server.sendReliableToClient(999, new TestCounterPacket(2));

            assertEquals(PacketOutboxStoreResult.REJECTED_MAX_PACKETS, rejected.get());
            List<WrappedPacket> replay = outbox.collectForReplay(999, System.currentTimeMillis(), 0);
            assertEquals(1, replay.size());
            assertEquals(1, ((TestCounterPacket) replay.getFirst().getPacket()).getValue());
        } finally {
            server.close();
            outbox.close();
        }
    }

    @Test
    void outboxResendAfterRetryInterval() {
        PacketOutbox outbox = PacketOutbox.builder().sessionTtl(Duration.ofMinutes(2)).build();
        WrappedPacket tracked = outbox.track(900, WrappedPacket.of(new ExceptionPacket()), () -> 1001L);
        long now = System.currentTimeMillis();
        outbox.markSent(900, tracked.getPacketId(), now);

        List<WrappedPacket> early = outbox.collectForResend(900, now + 100, 500, 0);
        List<WrappedPacket> due = outbox.collectForResend(900, now + 700, 500, 0);

        assertTrue(early.isEmpty());
        assertEquals(1, due.size());
        assertEquals(1001L, due.getFirst().getPacketId());
    }

    @Test
    void outboxReportsDiscardWhenMaxRetriesExhausted() {
        PacketOutbox outbox = PacketOutbox.builder().sessionTtl(Duration.ofMinutes(2)).build();
        WrappedPacket tracked = outbox.track(901, WrappedPacket.of(new ExceptionPacket()), () -> 1002L);
        long now = System.currentTimeMillis();
        outbox.markSent(901, tracked.getPacketId(), now);

        AtomicReference<PacketOutboxStoreResult> discarded = new AtomicReference<>();
        List<WrappedPacket> resend = outbox.collectForResend(901, now + 700, 500, 1,
                result -> discarded.set(result.result()));

        assertTrue(resend.isEmpty());
        assertEquals(PacketOutboxStoreResult.DISCARDED_MAX_RETRIES, discarded.get());
        assertFalse(outbox.hasSession(901));
    }

    @Test
    void sessionTokenRejectsHijackReconnect() throws Exception {
        int port = BASE_PORT + 6;
        PacketRegistry registry = new PacketRegistry().withCommons().register(TestCounterPacket.class);

        ShinnetaiServer<?> server = new ShinnetaiServer<>(registry,
                ServerOptions.builder(port)
                        .requireSessionToken(true)
                        .sessionTokenValidator(token -> token.startsWith("trusted-token-"))
                        .build());

        ShinnetaiClient trusted = null;
        ShinnetaiClient hijacker = null;
        try {
            server.start();

            trusted = new ShinnetaiClient(registry,
                    ClientOptions.builder("localhost", port)
                            .id(910)
                            .sessionToken("trusted-token-aabbccddee112233445566")
                            .build());
            trusted.start();
            Thread.sleep(250);
            var trustedConnection = server.getConnection(910);
            assertNotNull(trustedConnection);

            trustedConnection.close(true);
            trusted.closeClient(true);
                assertNull(server.getConnection(910));

            hijacker = new ShinnetaiClient(registry,
                    ClientOptions.builder("localhost", port)
                            .id(910)
                            .sessionToken("hijacker-token-aabbccddee112233445566")
                            .build());
            hijacker.start();
            Thread.sleep(350);

            assertNull(server.getConnection(910));
        } finally {
            if (trusted != null) {
                trusted.closeClient(true);
            }
            if (hijacker != null) {
                hijacker.closeClient(true);
            }
            server.close();
        }
    }

    @Test
    void sessionTokenAllowsTrustedReconnectAndReplay() throws Exception {
        int port = BASE_PORT + 7;
        PacketRegistry registry = new PacketRegistry().withCommons().register(TestCounterPacket.class);
        PacketOutbox outbox = PacketOutbox.builder().sessionTtl(Duration.ofMinutes(2)).build();

        CountDownLatch trustedReceived = new CountDownLatch(1);
        PacketHandlerRegistry handlers = PacketHandlerRegistry.create()
                .on(TestCounterPacket.class, (packet, ctx) -> trustedReceived.countDown());

        ShinnetaiServer<?> server = new ShinnetaiServer<>(registry,
                ServerOptions.builder(port)
                        .packetOutbox(outbox)
                        .requireSessionToken(true)
                        .sessionTokenValidator(token -> token.startsWith("session-920-"))
                        .build());

        ShinnetaiClient trustedFirst = null;
        ShinnetaiClient trustedReconnect = null;
        ShinnetaiClient hijacker = null;
        try {
            server.start();

            trustedFirst = new ShinnetaiClient(registry,
                    ClientOptions.builder("localhost", port)
                            .id(920)
                            .sessionToken("session-920-aabbccddee112233445566")
                            .build());
            trustedFirst.start();
            Thread.sleep(250);

            var firstConnection = server.getConnection(920);
            assertNotNull(firstConnection);
            firstConnection.close(true);
            trustedFirst.closeClient(true);
                assertNull(server.getConnection(920));

            server.sendReliableToClient(920, new TestCounterPacket(44));
            assertTrue(outbox.hasSession(920));

            hijacker = new ShinnetaiClient(registry,
                    ClientOptions.builder("localhost", port)
                            .id(920)
                            .sessionToken("wrong-session-aabbccddee112233445566")
                            .build());
            hijacker.start();
            Thread.sleep(300);
            assertTrue(outbox.hasSession(920));

            trustedReconnect = new ShinnetaiClient(registry,
                    ClientOptions.builder("localhost", port)
                            .id(920)
                            .sessionToken("session-920-aabbccddee112233445566")
                            .packetHandlerRegistry(handlers)
                            .build());
            trustedReconnect.start();

            assertTrue(trustedReceived.await(4, TimeUnit.SECONDS));
        } finally {
            if (trustedFirst != null) {
                trustedFirst.closeClient(true);
            }
            if (trustedReconnect != null) {
                trustedReconnect.closeClient(true);
            }
            if (hijacker != null) {
                hijacker.closeClient(true);
            }
            server.close();
            outbox.close();
        }
    }

}
