package net.ldoin.shinnetai.client.pool;

import net.ldoin.shinnetai.client.ShinnetaiClient;
import net.ldoin.shinnetai.client.options.ClientOptions;
import net.ldoin.shinnetai.packet.AbstractPacket;
import net.ldoin.shinnetai.packet.WrappedPacket;
import net.ldoin.shinnetai.packet.registry.PacketRegistry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ShinnetaiClientPool implements AutoCloseable {

    public static Builder builder() {
        return new Builder();
    }

    private final ClientOptions options;
    private final PacketRegistry registry;
    private final int size;
    private final List<ShinnetaiClient> clients;
    private final AtomicInteger roundRobin = new AtomicInteger(0);
    private final Logger logger;

    private ShinnetaiClientPool(Builder builder) {
        this.options = builder.options;
        this.registry = builder.registry != null ? builder.registry : PacketRegistry.getCommons();
        this.size = builder.size;
        this.clients = new ArrayList<>(size);
        this.logger = Logger.getLogger("ClientPool(" + options.getAddress() + ":" + options.getPort() + ")");
    }

    public synchronized void start() throws IOException {
        if (!clients.isEmpty()) {
            throw new IllegalStateException("Pool already started");
        }

        for (int i = 0; i < size; i++) {
            ShinnetaiClient client = new ShinnetaiClient(registry, options);
            client.start();
            clients.add(client);
        }

        logger.info("Client pool started with " + size + " connections");
    }

    public void send(AbstractPacket<?, ?> packet) throws IOException {
        send(WrappedPacket.of(packet));
    }

    public void send(WrappedPacket packet) throws IOException {
        ShinnetaiClient target = leastLoaded();
        if (target == null) {
            throw new IOException("No active connections in pool");
        }

        target.sendPacket(packet);
    }

    public void enqueue(AbstractPacket<?, ?> packet) {
        ShinnetaiClient target = leastLoaded();
        if (target == null) {
            logger.warning("No active connections in pool, dropping packet");
            return;
        }

        target.addPacket(packet);
    }

    public List<ShinnetaiClient> getClients() {
        return Collections.unmodifiableList(clients);
    }

    public int getSize() {
        return size;
    }

    public boolean isAllRunning() {
        return clients.stream().allMatch(ShinnetaiClient::isRunning);
    }

    @Override
    public synchronized void close() {
        for (ShinnetaiClient client : clients) {
            try {
                client.close();
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error closing pooled client", e);
            }
        }

        clients.clear();
        logger.info("Client pool closed");
    }

    private ShinnetaiClient leastLoaded() {
        if (clients.isEmpty()) {
            return null;
        }

        ShinnetaiClient best = null;
        double bestLoad = Double.MAX_VALUE;
        for (ShinnetaiClient client : clients) {
            if (!client.isRunning()) {
                continue;
            }

            double load = client.getQueueLoad();
            if (load < bestLoad) {
                bestLoad = load;
                best = client;
            }
        }

        if (best == null) {
            int idx = Math.abs(roundRobin.getAndIncrement() % clients.size());
            best = clients.get(idx);
        }

        return best;
    }

    public static class Builder {

        private ClientOptions options;
        private PacketRegistry registry;
        private int size = 4;

        public Builder options(ClientOptions options) {
            this.options = options;
            return this;
        }

        public Builder registry(PacketRegistry registry) {
            this.registry = registry;
            return this;
        }

        public Builder size(int size) {
            if (size < 1) {
                throw new IllegalArgumentException("Pool size must be >= 1");
            }

            this.size = size;
            return this;
        }

        public ShinnetaiClientPool build() {
            if (options == null) {
                throw new IllegalStateException("options must be set");
            }

            return new ShinnetaiClientPool(this);
        }
    }
}