package net.ldoin.shinnetai;

import net.ldoin.shinnetai.buffered.buf.smart.ReadOnlySmartByteBuf;
import net.ldoin.shinnetai.buffered.buf.smart.WriteOnlySmartByteBuf;
import net.ldoin.shinnetai.client.ShinnetaiClient;
import net.ldoin.shinnetai.client.options.ClientOptions;
import net.ldoin.shinnetai.packet.EmptyPacket;
import net.ldoin.shinnetai.packet.registry.PacketRegistry;
import net.ldoin.shinnetai.packet.registry.ShinnetaiPacket;
import net.ldoin.shinnetai.security.RateLimitCost;
import net.ldoin.shinnetai.security.RateLimiter;
import net.ldoin.shinnetai.server.ShinnetaiServer;
import net.ldoin.shinnetai.server.options.ServerOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShinnetaiRateLimiterTest {

    private static final int BASE_PORT = 8970;

    @Test
    void weightedLimiterConsumesPacketAndByteTokensAtomically() {
        RateLimiter limiter = new RateLimiter(5, 1_000);

        assertTrue(limiter.tryAcquire(3, 100));
        assertFalse(limiter.tryAcquire(3, 0));
        assertTrue(limiter.tryAcquire(2, 0));
        assertFalse(limiter.tryAcquire(0, 901));
    }

    @Test
    @Timeout(10)
    void packetSpecificCostDropsHeavyPacketButAllowsSmallPacket() throws Exception {
        int port = BASE_PORT;
        PacketRegistry registry = new PacketRegistry()
                .withCommons()
                .register(HeavyCostPacket.class)
                .register(SmallCostPacket.class);

        AtomicInteger heavyHandled = new AtomicInteger();
        AtomicInteger smallHandled = new AtomicInteger();
        CountDownLatch smallLatch = new CountDownLatch(1);

        ShinnetaiServer<?> server = new ShinnetaiServer<>(registry,
                ServerOptions.builder(port)
                        .keepAlive(false)
                        .rateLimiterFactory(() -> new RateLimiter(3, 1024 * 1024))
                        .rateLimitCostResolver(packet -> packet instanceof HeavyCostPacket
                                ? RateLimitCost.packets(2)
                                : RateLimitCost.none())
                        .build());
        server.on(HeavyCostPacket.class, (packet, ctx) -> heavyHandled.incrementAndGet());
        server.on(SmallCostPacket.class, (packet, ctx) -> {
            smallHandled.incrementAndGet();
            smallLatch.countDown();
        });

        ShinnetaiClient client = null;
        try {
            server.start();
            client = new ShinnetaiClient(registry,
                    ClientOptions.builder("localhost", port)
                            .keepAlive(false)
                            .build());
            client.start();
            assertEventually(Duration.ofSeconds(3), client::isHandshaked);

            client.sendPacket(new HeavyCostPacket(1));
            client.sendPacket(new SmallCostPacket(2));

            assertTrue(smallLatch.await(3, TimeUnit.SECONDS));
            Thread.sleep(150);
            assertEquals(0, heavyHandled.get());
            assertEquals(1, smallHandled.get());
        } finally {
            if (client != null) {
                client.closeClient(true);
            }
            server.close();
        }
    }

    @Test
    @Timeout(10)
    void handlerCanConsumeAdditionalActionTokens() throws Exception {
        int port = BASE_PORT + 1;
        PacketRegistry registry = new PacketRegistry()
                .withCommons()
                .register(ActionCostPacket.class);

        AtomicInteger acceptedActions = new AtomicInteger();
        AtomicInteger rejectedActions = new AtomicInteger();
        CountDownLatch handled = new CountDownLatch(2);

        ShinnetaiServer<?> server = new ShinnetaiServer<>(registry,
                ServerOptions.builder(port)
                        .keepAlive(false)
                        .rateLimiterFactory(() -> new RateLimiter(6, 1024 * 1024))
                        .build());
        server.on(ActionCostPacket.class, (packet, ctx) -> {
            if (ctx.tryConsumeRateLimit(3)) {
                acceptedActions.incrementAndGet();
            } else {
                rejectedActions.incrementAndGet();
            }
            handled.countDown();
        });

        ShinnetaiClient client = null;
        try {
            server.start();
            client = new ShinnetaiClient(registry,
                    ClientOptions.builder("localhost", port)
                            .keepAlive(false)
                            .build());
            client.start();
            assertEventually(Duration.ofSeconds(3), client::isHandshaked);

            client.sendPacket(new ActionCostPacket(1));
            client.sendPacket(new ActionCostPacket(2));

            assertTrue(handled.await(3, TimeUnit.SECONDS));
            assertEquals(1, acceptedActions.get());
            assertEquals(1, rejectedActions.get());
        } finally {
            if (client != null) {
                client.closeClient(true);
            }
            server.close();
        }
    }

    private static void assertEventually(Duration timeout, Condition condition) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.test()) {
                return;
            }
            Thread.sleep(20);
        }

        assertTrue(condition.test());
    }

    @FunctionalInterface
    private interface Condition {
        boolean test();
    }

    @ShinnetaiPacket(id = 9101)
    public static class HeavyCostPacket extends IntPacket {
        public HeavyCostPacket() {
        }

        public HeavyCostPacket(int value) {
            super(value);
        }
    }

    @ShinnetaiPacket(id = 9102)
    public static class SmallCostPacket extends IntPacket {
        public SmallCostPacket() {
        }

        public SmallCostPacket(int value) {
            super(value);
        }
    }

    @ShinnetaiPacket(id = 9103)
    public static class ActionCostPacket extends IntPacket {
        public ActionCostPacket() {
        }

        public ActionCostPacket(int value) {
            super(value);
        }
    }

    public abstract static class IntPacket extends EmptyPacket {

        private int value;

        protected IntPacket() {
        }

        protected IntPacket(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
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
}
