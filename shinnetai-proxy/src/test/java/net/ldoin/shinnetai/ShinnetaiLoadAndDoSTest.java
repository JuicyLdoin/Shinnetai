package net.ldoin.shinnetai;

import net.ldoin.shinnetai.client.ShinnetaiClient;
import net.ldoin.shinnetai.client.options.ClientOptions;
import net.ldoin.shinnetai.packet.registry.PacketRegistry;
import net.ldoin.shinnetai.packet.test.TestCounterPacket;
import net.ldoin.shinnetai.server.ShinnetaiServer;
import net.ldoin.shinnetai.server.options.ServerOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.Socket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShinnetaiLoadAndDoSTest {

    private static final int BASE_PORT = 8900;

    @Test
    @Timeout(25)
    void manyClientsConnectAndExchangePackets() throws Exception {
        int clientsCount = 180;
        int port = BASE_PORT;
        PacketRegistry registry = new PacketRegistry().withCommons().register(TestCounterPacket.class);
        CountDownLatch connected = new CountDownLatch(clientsCount);
        CountDownLatch received = new CountDownLatch(clientsCount);

        ShinnetaiServer<?> server = new ShinnetaiServer<>(registry,
                ServerOptions.builder(port)
                        .keepAlive(false)
                        .maxConnections(clientsCount + 20)
                        .maxPendingHandshakes(clientsCount + 20)
                        .maxHandshakeDurationMs(2_000)
                        .onConnect(connection -> connected.countDown())
                        .build());
        server.on(TestCounterPacket.class, (packet, ctx) -> received.countDown());

        List<ShinnetaiClient> clients = new ArrayList<>(clientsCount);
        try {
            server.start();
            for (int i = 0; i < clientsCount; i++) {
                ShinnetaiClient client = new ShinnetaiClient(registry,
                        ClientOptions.builder("localhost", port)
                                .id(20_000 + i)
                                .keepAlive(false)
                                .maxQueueSize(256)
                                .build());
                client.start();
                clients.add(client);
            }

            assertTrue(connected.await(10, TimeUnit.SECONDS));
            assertEquals(clientsCount, server.getConnectionCount());

            for (int i = 0; i < clients.size(); i++) {
                clients.get(i).sendPacket(new TestCounterPacket(i));
            }

            assertTrue(received.await(10, TimeUnit.SECONDS));
        } finally {
            for (ShinnetaiClient client : clients) {
                client.closeClient(true);
            }
            server.close();
        }
    }

    @Test
    @Timeout(10)
    void slowPreambleConnectionsAreCappedAndTimedOut() throws Exception {
        int port = BASE_PORT + 1;
        int maxPending = 8;
        ShinnetaiServer<?> server = new ShinnetaiServer<>(
                ServerOptions.builder(port)
                        .keepAlive(false)
                        .maxPendingHandshakes(maxPending)
                        .maxConnectionsPerIp(maxPending)
                        .maxHandshakeDurationMs(250)
                        .build());

        List<Socket> sockets = new ArrayList<>();
        try {
            server.start();
            for (int i = 0; i < maxPending * 3; i++) {
                try {
                    sockets.add(new Socket("localhost", port));
                } catch (Exception ignored) {
                }
            }

            assertEventually(Duration.ofSeconds(2), () -> server.getPendingHandshakeCount() <= maxPending);
            assertEventually(Duration.ofSeconds(4), () -> server.getPendingHandshakeCount() == 0);
            assertTrue(server.getRejectedHandshakeCount() > 0);
            assertEquals(0, server.getConnectionCount());
        } finally {
            for (Socket socket : sockets) {
                try {
                    socket.close();
                } catch (Exception ignored) {
                }
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
}
