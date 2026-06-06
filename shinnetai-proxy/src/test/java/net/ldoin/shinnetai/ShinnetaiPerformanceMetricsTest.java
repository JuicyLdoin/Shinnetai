package net.ldoin.shinnetai;

import net.ldoin.shinnetai.client.ShinnetaiClient;
import net.ldoin.shinnetai.client.options.ClientOptions;
import net.ldoin.shinnetai.metric.ShinnetaiRuntimeMetrics;
import net.ldoin.shinnetai.packet.registry.PacketRegistry;
import net.ldoin.shinnetai.packet.test.TestCounterPacket;
import net.ldoin.shinnetai.server.ShinnetaiServer;
import net.ldoin.shinnetai.server.options.ServerOptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ShinnetaiPerformanceMetricsTest {

    @Test
    void performanceAndMetricsSnapshot() throws Exception {
        int port = 8650;
        int clientsCount = 5;
        int packetsPerClient = 200;
        int totalPackets = clientsCount * packetsPerClient;

        PacketRegistry registry = new PacketRegistry().withCommons().register(TestCounterPacket.class);
        CountDownLatch serverReceived = new CountDownLatch(totalPackets);

        ShinnetaiServer<?> server = new ShinnetaiServer<>(registry, ServerOptions.builder(port).build());
        server.on(TestCounterPacket.class, (packet, ctx) -> serverReceived.countDown());

        List<ShinnetaiClient> clients = new ArrayList<>();

        try {
            server.start();

            for (int i = 0; i < clientsCount; i++) {
                ShinnetaiClient client = new ShinnetaiClient(registry,
                        ClientOptions.builder("localhost", port)
                                .id(1000 + i)
                                .build());
                client.start();
                clients.add(client);
            }

            Thread.sleep(300);

            long startNs = System.nanoTime();
            for (ShinnetaiClient client : clients) {
                for (int i = 0; i < packetsPerClient; i++) {
                    client.sendPacket(new TestCounterPacket(i));
                }
            }

            boolean delivered = serverReceived.await(20, TimeUnit.SECONDS);
            long elapsedNs = System.nanoTime() - startNs;

            assertTrue(delivered, "Not all packets were delivered to server in time");

            double elapsedSeconds = elapsedNs / 1_000_000_000.0;
            double throughput = totalPackets / elapsedSeconds;

            ShinnetaiRuntimeMetrics metrics = server.getRuntimeMetrics();

            assertTrue(metrics.getUptimeMs() > 0);
            assertTrue(metrics.getHeapUsedBytes() > 0);
            assertTrue(metrics.getAppInputPackets() >= totalPackets);

            System.out.println("PERF packets=" + totalPackets +
                    " seconds=" + String.format("%.3f", elapsedSeconds) +
                    " throughput_pps=" + String.format("%.2f", throughput) +
                    " process_cpu_percent=" + metrics.getProcessCpuLoad() +
                    " system_cpu_percent=" + metrics.getSystemCpuLoad() +
                    " heap_used_mb=" + String.format("%.2f", metrics.getHeapUsedBytes() / 1024.0 / 1024.0) +
                    " app_input_bytes=" + metrics.getAppInputBytes() +
                    " app_output_bytes=" + metrics.getAppOutputBytes() +
                    " uptime_ms=" + metrics.getUptimeMs());
        } finally {
            for (ShinnetaiClient client : clients) {
                try {
                    client.close();
                } catch (Exception ignored) {
                }
            }
            try {
                server.close();
            } catch (Exception ignored) {
            }
        }
    }
}
