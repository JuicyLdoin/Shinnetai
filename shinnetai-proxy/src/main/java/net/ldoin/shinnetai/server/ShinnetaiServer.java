package net.ldoin.shinnetai.server;

import net.ldoin.shinnetai.ConnectionType;
import net.ldoin.shinnetai.buffered.buf.smart.ReadOnlySmartByteBuf;
import net.ldoin.shinnetai.buffered.buf.smart.SmartByteBuf;
import net.ldoin.shinnetai.buffered.util.IOUtil;
import net.ldoin.shinnetai.delivery.PacketOutbox;
import net.ldoin.shinnetai.delivery.PacketOutboxResult;
import net.ldoin.shinnetai.delivery.PacketOutboxStoreResult;
import net.ldoin.shinnetai.exception.ShinnetaiExceptions;
import net.ldoin.shinnetai.log.ShinnetaiLog;
import net.ldoin.shinnetai.metric.HealthStatus;
import net.ldoin.shinnetai.metric.ShinnetaiMetricsCollector;
import net.ldoin.shinnetai.metric.ShinnetaiRuntimeMetrics;
import net.ldoin.shinnetai.packet.AbstractPacket;
import net.ldoin.shinnetai.packet.PacketOptions;
import net.ldoin.shinnetai.packet.WrappedPacket;
import net.ldoin.shinnetai.packet.common.ServerDisablePacket;
import net.ldoin.shinnetai.packet.registry.PacketRegistry;
import net.ldoin.shinnetai.security.IpFilter;
import net.ldoin.shinnetai.server.connection.ShinnetaiConnection;
import net.ldoin.shinnetai.server.options.ServerOptions;
import net.ldoin.shinnetai.statistic.server.ShinnetaiConnectionStatistic;
import net.ldoin.shinnetai.statistic.server.ShinnetaiServerStatistic;
import net.ldoin.shinnetai.util.IdGenerator;
import net.ldoin.shinnetai.worker.EnqueueResult;
import net.ldoin.shinnetai.worker.ShinnetaiWorkerContext;

import javax.naming.OperationNotSupportedException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ShinnetaiServer<C extends ShinnetaiConnection<?>> implements Runnable {

    private static final int MIN_SESSION_TOKEN_LENGTH = 32;

    private final Map<Integer, C> connections;
    private final Set<C> pendingConnections;
    private final IdGenerator connectionIdGenerator;
    private final AtomicLong packetIdGenerator;
    private final AtomicInteger pendingHandshakes = new AtomicInteger();
    private final AtomicLong rejectedHandshakes = new AtomicLong();
    private final ConcurrentHashMap<String, Integer> connectionsPerIp = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> pendingHandshakesPerIp = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, String> sessionTokens = new ConcurrentHashMap<>();

    private final PacketRegistry registry;
    private final Logger logger;
    protected final ServerOptions options;
    private ServerSocket serverSocket;
    private ServerSocketChannel serverChannel;

    private final ShinnetaiServerStatistic statistic;

    protected Thread workingThread;
    private volatile boolean running;
    private CountDownLatch readyLatch;
    private final Thread shutdownHook;
    private volatile ScheduledExecutorService retransmitExecutor;
    private volatile ScheduledExecutorService handshakeTimeoutExecutor;

    public ShinnetaiServer(ServerOptions options) {
        this(PacketRegistry.getCommons(), options);
    }

    public ShinnetaiServer(PacketRegistry registry, ServerOptions options) {
        this(registry, options, Logger.getLogger("Server (" + options.getPort() + ")"));
    }

    protected ShinnetaiServer(PacketRegistry registry, ServerOptions options, Logger logger) {
        ShinnetaiLog.init();

        this.connections = new ConcurrentHashMap<>();
        this.pendingConnections = ConcurrentHashMap.newKeySet();
        this.connectionIdGenerator = new IdGenerator();
        this.packetIdGenerator = new AtomicLong(1);
        this.registry = registry;
        this.logger = logger;
        this.options = options;
        this.statistic = new ShinnetaiServerStatistic();

        this.shutdownHook = Thread.ofPlatform().unstarted(() -> {
            if (running) {
                close();
            }
        });
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    public C getConnection(int id) {
        return connections.get(id);
    }

    public Collection<C> getConnections() {
        return Collections.unmodifiableCollection(connections.values());
    }

    public int getConnectionCount() {
        return connections.size();
    }

    public int getPendingHandshakeCount() {
        return pendingHandshakes.get();
    }

    public long getRejectedHandshakeCount() {
        return rejectedHandshakes.get();
    }

    public HealthStatus health() {
        if (!running) {
            return HealthStatus.DOWN;
        }

        int maxPending = options.getMaxPendingHandshakes();
        if (maxPending > 0 && pendingHandshakes.get() >= maxPending) {
            return HealthStatus.DEGRADED;
        }

        return HealthStatus.UP;
    }

    public Collection<C> findConnections(Predicate<C> predicate) {
        List<C> result = new ArrayList<>();
        for (C connection : connections.values()) {
            if (predicate.test(connection)) {
                result.add(connection);
            }
        }

        return Collections.unmodifiableList(result);
    }

    public void sendPacketTo(AbstractPacket<?, ?> packet, Predicate<C> predicate) throws IOException {
        sendPacketTo(WrappedPacket.of(packet), predicate);
    }

    public void sendPacketTo(WrappedPacket packet, Predicate<C> predicate) throws IOException {
        for (C connection : connections.values()) {
            if (predicate.test(connection)) {
                connection.sendPacket(packet);
            }
        }
    }

    public void broadcastPacket(AbstractPacket<?, ?> packet) {
        broadcastPacket(WrappedPacket.of(packet));
    }

    public void broadcastPacket(WrappedPacket packet) {
        for (C connection : connections.values()) {
            connection.addPacket(packet);
        }
    }

    public void broadcastPacketTo(AbstractPacket<?, ?> packet, Predicate<C> predicate) {
        broadcastPacketTo(WrappedPacket.of(packet), predicate);
    }

    public void broadcastPacketTo(WrappedPacket packet, Predicate<C> predicate) {
        for (C connection : connections.values()) {
            if (predicate.test(connection)) {
                connection.addPacket(packet);
            }
        }
    }

    public void registerHandlers(Object handler) {
        options.getPacketHandlerRegistry().register(handler);
    }

    @SuppressWarnings("unchecked")
    public <P extends AbstractPacket<?, ?>> void on(Class<P> packetType,
                                                    BiConsumer<P, ShinnetaiWorkerContext<?>> handler) {
        options.getPacketHandlerRegistry().on(packetType, handler);
    }

    public void sendPacketToAll(WrappedPacket packet) throws IOException {
        for (C connection : connections.values()) {
            connection.sendPacket(packet);
        }
    }

    public PacketRegistry getRegistry() {
        return registry;
    }

    public ServerOptions getOptions() {
        return options;
    }

    public Logger getLogger() {
        return logger;
    }

    public ShinnetaiServerStatistic getStatistic() {
        return statistic;
    }

    public ShinnetaiRuntimeMetrics getRuntimeMetrics() {
        return ShinnetaiMetricsCollector.collect(statistic);
    }

    public ServerSocket getServerSocket() {
        if (serverSocket != null) {
            return serverSocket;
        }

        if (serverChannel != null) {
            return serverChannel.socket();
        }

        return null;
    }

    public boolean isRunning() {
        return running;
    }

    public int getMaxConnections() {
        return options.getMaxConnections();
    }

    public synchronized void start() {
        if (running) {
            throw new UnsupportedOperationException("Server already started");
        }

        onPreStart();
        if (workingThread != null) {
            close();
        }

        running = true;
        readyLatch = new CountDownLatch(1);
        handshakeTimeoutExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "shinnetai-handshake-timeouts");
            thread.setDaemon(true);
            return thread;
        });
        workingThread = new Thread(this);
        workingThread.start();

        try {
            readyLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for server to start", e);
        }

        onStart();
        startRetransmitLoop();
    }

    @Override
    public void run() {
        if (options.isSSL()) {
            runSSL();
        } else {
            runNIO();
        }
    }

    private void runNIO() {
        try {
            serverChannel = options.toServerSocketChannel();
            readyLatch.countDown();
            while (running) {
                try {
                    SocketChannel clientChannel = serverChannel.accept();
                    InetAddress remoteAddress = clientChannel.socket().getInetAddress();
                    if (!reservePendingHandshake(remoteAddress, clientChannel)) {
                        continue;
                    }

                    Thread.ofVirtual().start(() -> handleNIOConnection(clientChannel));
                } catch (ClosedChannelException ignored) {
                } catch (IOException e) {
                    logger.log(Level.SEVERE, "Failed connect", e);
                }
            }
        } catch (IOException e) {
            readyLatch.countDown();
            logger.log(Level.SEVERE, "Failed while running server: ", e);
        } finally {
            if (running) {
                close();
            }
        }
    }

    private void runSSL() {
        try {
            serverSocket = options.toSocket();
            readyLatch.countDown();
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    InetAddress remoteAddress = clientSocket.getInetAddress();
                    if (!reservePendingHandshake(remoteAddress, clientSocket)) {
                        continue;
                    }

                    Thread.ofVirtual().start(() -> handleSSLConnection((SSLSocket) clientSocket));
                } catch (SocketException ignored) {
                } catch (IOException e) {
                    logger.log(Level.SEVERE, "Failed connect", e);
                }
            }
        } catch (IOException e) {
            readyLatch.countDown();
            logger.log(Level.SEVERE, "Failed while running server: ", e);
        } finally {
            if (running) close();
        }
    }

    private void handleNIOConnection(SocketChannel clientChannel) {
        InetAddress remoteAddress = clientChannel.socket().getInetAddress();
        ScheduledFuture<?> preambleTimeout = scheduleTransportTimeout(clientChannel);
        boolean handedToWorker = false;
        try {
            if (!isIpAllowed(remoteAddress)) {
                logger.warning("Connection rejected by IP filter: " + remoteAddress.getHostAddress());
                clientChannel.close();
                return;
            }

            try {
                clientChannel.socket().setKeepAlive(options.isKeepAlive());
            } catch (SocketException e) {
                logger.log(Level.WARNING, "Failed to apply socket options", e);
                clientChannel.close();
                return;
            }

            int typeId;
            int id;
            try {
                typeId = IOUtil.readVarInt(clientChannel);
                id = IOUtil.readVarInt(clientChannel);
            } catch (IOException e) {
                clientChannel.close();
                return;
            }

            handedToWorker = handleConnectionCore(clientChannel, clientChannel, clientChannel, typeId, id);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed handling NIO connection", e);
            try {
                clientChannel.close();
            } catch (IOException ignored) {
            }
        } finally {
            if (preambleTimeout != null) {
                preambleTimeout.cancel(false);
            }

            if (!handedToWorker) {
                releasePendingHandshake(remoteAddress);
            }
        }
    }

    private void handleSSLConnection(SSLSocket sslSocket) {
        InetAddress remoteAddress = sslSocket.getInetAddress();
        ScheduledFuture<?> preambleTimeout = scheduleTransportTimeout(sslSocket);
        boolean handedToWorker = false;
        try {
            if (!isIpAllowed(remoteAddress)) {
                logger.warning("Connection rejected by IP filter: " + remoteAddress.getHostAddress());
                sslSocket.close();
                return;
            }

            try {
                sslSocket.setSoTimeout(options.getReadTimeout());
                sslSocket.setKeepAlive(options.isKeepAlive());
            } catch (SocketException e) {
                logger.log(Level.WARNING, "Failed to apply socket options", e);
                sslSocket.close();
                return;
            }

            try {
                sslSocket.startHandshake();
            } catch (SSLHandshakeException e) {
                logger.log(Level.WARNING, "SSL handshake failed: " + e.getMessage());
                sslSocket.close();
                return;
            }

            ReadableByteChannel in = Channels.newChannel(sslSocket.getInputStream());
            WritableByteChannel out = Channels.newChannel(sslSocket.getOutputStream());

            int typeId;
            int id;
            try {
                typeId = IOUtil.readVarInt(in);
                id = IOUtil.readVarInt(in);
                sslSocket.setSoTimeout(options.getReadTimeout());
            } catch (IOException e) {
                sslSocket.close();
                return;
            }

            handedToWorker = handleConnectionCore(in, out, sslSocket, typeId, id);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed handling SSL connection", e);
            try {
                sslSocket.close();
            } catch (IOException ignored) {
            }
        } finally {
            if (preambleTimeout != null) {
                preambleTimeout.cancel(false);
            }

            if (!handedToWorker) {
                releasePendingHandshake(remoteAddress);
            }
        }
    }

    private boolean handleConnectionCore(ReadableByteChannel in, WritableByteChannel out, AutoCloseable closeTarget, int typeId, int id) throws Exception {
        if (typeId < 0 || typeId >= ConnectionType.VALUES.length) {
            closeTarget.close();
            return false;
        }

        ConnectionType connectionType = ConnectionType.VALUES[typeId];

        SmartByteBuf handshakeBuf = SmartByteBuf.empty();
        handshakeBuf.writeVarInt(typeId);
        handshakeBuf.writeVarInt(id);
        ReadOnlySmartByteBuf data = ReadOnlySmartByteBuf.of(handshakeBuf.toBytes());

        C connection = newConnection(in, out, closeTarget, connectionType, data);
        try {
            if (id == 0) {
                id = connectionIdGenerator.getNextId();
            }

            connection.changeConnectionId(id);
        } catch (OperationNotSupportedException e) {
            logger.log(Level.SEVERE, "Failed connect", e);
            connection.sendException(ShinnetaiExceptions.FAILED_ASSIGN_CONNECTION_ID);
            disconnect(connection);
            safeClose(closeTarget);
            return false;
        }

        int maxPending = options.getMaxPendingHandshakes();
        if (maxPending > 0 && pendingHandshakes.get() > maxPending) {
            logger.warning("Connection rejected: max pending handshakes reached");
            rejectedHandshakes.incrementAndGet();
            cannotAccept(connection, connectionType, ReadOnlySmartByteBuf.of(data.toBytes()));
            safeClose(closeTarget);
            return false;
        }

        pendingConnections.add(connection);
        try {
            connection.start();
        } catch (RuntimeException e) {
            pendingConnections.remove(connection);
            throw e;
        }
        scheduleHandshakeTimeout(connection);
        return true;
    }

    private void scheduleHandshakeTimeout(C connection) {
        long timeoutMs = options.getMaxHandshakeDurationMs();
        if (timeoutMs <= 0) {
            return;
        }

        handshakeTimeoutExecutor.schedule(() -> {
            if (pendingConnections.remove(connection)) {
                releasePendingHandshake(connection);
                rejectedHandshakes.incrementAndGet();
                logger.warning("Connection rejected: handshake timed out for id " + connection.getConnectionId());
                connection.close(true);
            }
        }, timeoutMs, TimeUnit.MILLISECONDS);
    }

    private ScheduledFuture<?> scheduleTransportTimeout(AutoCloseable closeTarget) {
        long timeoutMs = options.getMaxHandshakeDurationMs();
        if (timeoutMs <= 0 || handshakeTimeoutExecutor == null) {
            return null;
        }

        return handshakeTimeoutExecutor.schedule(() -> safeClose(closeTarget), timeoutMs, TimeUnit.MILLISECONDS);
    }

    private boolean reservePendingHandshake(InetAddress remoteAddress, AutoCloseable closeTarget) {
        if (!isIpAllowed(remoteAddress)) {
            logger.warning("Connection rejected by IP filter: " + remoteAddress.getHostAddress());
            safeClose(closeTarget);
            return false;
        }

        int totalPending = pendingHandshakes.incrementAndGet();
        int maxPending = options.getMaxPendingHandshakes();
        if (maxPending > 0 && totalPending > maxPending) {
            pendingHandshakes.decrementAndGet();
            rejectedHandshakes.incrementAndGet();
            logger.warning("Connection rejected: max pending handshakes reached");
            safeClose(closeTarget);
            return false;
        }

        String host = remoteAddress.getHostAddress();
        AtomicInteger perIpPending = pendingHandshakesPerIp.computeIfAbsent(host, ignored -> new AtomicInteger());
        int pendingForIp = perIpPending.incrementAndGet();
        int perIpLimit = options.getMaxConnectionsPerIp();
        int activeForIp = connectionsPerIp.getOrDefault(host, 0);
        if (perIpLimit > 0 && activeForIp + pendingForIp > perIpLimit) {
            decrementPendingIp(host, perIpPending);
            pendingHandshakes.decrementAndGet();
            rejectedHandshakes.incrementAndGet();
            logger.warning("Connection rejected: max pending/active connections per IP reached for " + host);
            safeClose(closeTarget);
            return false;
        }

        return true;
    }

    private void releasePendingHandshake(ShinnetaiConnection<?> connection) {
        Socket socket = connection.getSocket();
        releasePendingHandshake(socket != null ? socket.getInetAddress() : null);
    }

    private void releasePendingHandshake(InetAddress remoteAddress) {
        int remaining = pendingHandshakes.updateAndGet(value -> value > 0 ? value - 1 : 0);
        if (remaining == 0) {
            pendingHandshakesPerIp.entrySet().removeIf(entry -> entry.getValue().get() <= 0);
        }

        if (remoteAddress == null) {
            return;
        }

        String host = remoteAddress.getHostAddress();
        AtomicInteger counter = pendingHandshakesPerIp.get(host);
        if (counter != null) {
            decrementPendingIp(host, counter);
        }
    }

    private void decrementPendingIp(String host, AtomicInteger counter) {
        int value = counter.updateAndGet(current -> current > 0 ? current - 1 : 0);
        if (value == 0) {
            pendingHandshakesPerIp.remove(host, counter);
        }
    }

    private void safeClose(AutoCloseable closeTarget) {
        if (closeTarget == null) {
            return;
        }

        try {
            closeTarget.close();
        } catch (Exception e) {
            logger.log(Level.FINE, "Failed to close rejected connection transport", e);
        }
    }

    public synchronized void close() {
        if (!running) {
            return;
        }

        running = false;
        onPreStop();

        try {
            for (C connection : new ArrayList<>(connections.values())) {
                try {
                    connection.sendPacket(new ServerDisablePacket());
                    connection.close(true);
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error closing connection " + connection.getConnectionId(), e);
                }
            }

            for (C connection : new ArrayList<>(pendingConnections)) {
                try {
                    connection.close(true);
                } catch (Exception e) {
                    logger.log(Level.FINE, "Error closing pending connection " + connection.getConnectionId(), e);
                }
            }
        } catch (Exception exception) {
            logger.log(Level.SEVERE, "Error while disable server", exception);
        }

        try {
            if (serverSocket != null) {
                serverSocket.close();
                serverSocket = null;
            }
            if (serverChannel != null) {
                serverChannel.close();
                serverChannel = null;
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Server could not disable: ", e);
        }

        if (workingThread != null) {
            workingThread.interrupt();
        }

        workingThread = null;
        connections.clear();
        pendingConnections.clear();
        pendingHandshakes.set(0);
        pendingHandshakesPerIp.clear();
        connectionIdGenerator.clear();
        sessionTokens.clear();

        if (retransmitExecutor != null) {
            retransmitExecutor.shutdownNow();
            retransmitExecutor = null;
        }

        if (handshakeTimeoutExecutor != null) {
            handshakeTimeoutExecutor.shutdownNow();
            handshakeTimeoutExecutor = null;
        }

        PacketOutbox outbox = options.getPacketOutbox();
        if (outbox != null) {
            outbox.close();
        }

        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException ignored) {
        }

        onStop();
    }

    protected boolean canAcceptConnection(C connection, ConnectionType connectionType, ReadOnlySmartByteBuf data) {
        if (options.getMaxConnections() > 0 && connections.size() >= options.getMaxConnections()) {
            return false;
        }

        int perIpLimit = options.getMaxConnectionsPerIp();
        if (perIpLimit > 0) {
            String ip = connection.getRemoteAddress();
            if (ip != null && !ip.isEmpty()) {
                String host = ip.contains(":") ? ip.substring(0, ip.lastIndexOf(':')) : ip;
                int count = connectionsPerIp.getOrDefault(host, 0);
                return count < perIpLimit;
            }
        }

        return true;
    }

    protected void cannotAccept(C connection, ConnectionType connectionType, ReadOnlySmartByteBuf data) {
        logger.log(Level.WARNING, "Cannot accept", connection.getConnectionId());
        try {
            connection.sendException(ShinnetaiExceptions.CANNOT_ACCEPT_CONNECTION);
        } catch (IOException exception) {
            logger.log(Level.SEVERE, "Failed connect", exception);
        }
    }

    @SuppressWarnings("unchecked")
    protected C newConnection(ReadableByteChannel in, WritableByteChannel out, AutoCloseable connection, ConnectionType connectionType, ReadOnlySmartByteBuf data) throws IOException {
        return (C) new ShinnetaiConnection<>(this, 0, registry, new ShinnetaiConnectionStatistic(statistic), in, out, connection, options);
    }

    @SuppressWarnings("unchecked")
    public boolean completeHandshake(ShinnetaiConnection<?> rawConnection) {
        C connection = (C) rawConnection;
        int id = connection.getConnectionId();
        synchronized (connections) {
            if (pendingConnections.remove(connection)) {
                releasePendingHandshake(connection);
            }

            if (!canPromoteConnection(connection)) {
                rejectedHandshakes.incrementAndGet();
                try {
                    connection.sendException(ShinnetaiExceptions.CANNOT_ACCEPT_CONNECTION);
                } catch (IOException e) {
                    logger.log(Level.FINE, "Failed to send connection rejection", e);
                }
                return false;
            }

            C existing = connections.putIfAbsent(id, connection);
            if (existing != null && existing != connection) {
                rejectedHandshakes.incrementAndGet();
                logger.log(Level.WARNING, String.format("Failed connect, id %s is busy", id));
                try {
                    connection.sendException(ShinnetaiExceptions.FAILED_CONNECTION_ID_BUSY);
                } catch (IOException e) {
                    logger.log(Level.FINE, "Failed to send busy-id rejection", e);
                }

                return false;
            }

            if (existing == null) {
                statistic.connect(connection);
            }
        }

        connect(connection);
        return true;
    }

    private boolean canPromoteConnection(C connection) {
        if (options.getMaxConnections() > 0 && connections.size() >= options.getMaxConnections()) {
            return false;
        }

        int perIpLimit = options.getMaxConnectionsPerIp();
        if (perIpLimit > 0) {
            String ip = extractHost(connection.getRemoteAddress());
            if (ip != null && !ip.isEmpty()) {
                int count = connectionsPerIp.getOrDefault(ip, 0);
                return count < perIpLimit;
            }
        }

        return true;
    }

    public void connect(C connection) {
        String ip = extractHost(connection.getRemoteAddress());
        if (ip != null) {
            connectionsPerIp.merge(ip, 1, Integer::sum);
        }

        onConnect(connection);
    }

    public void disconnect(ShinnetaiConnection<?> connection) {
        if (pendingConnections.remove(connection)) {
            releasePendingHandshake(connection);
        }

        int id = connection.getConnectionId();
        boolean removedActive = connections.remove(id, connection);
        if (!removedActive) {
            return;
        }

        String ip = extractHost(connection.getRemoteAddress());
        if (ip != null) {
            connectionsPerIp.computeIfPresent(ip, (k, v) -> v <= 1 ? null : v - 1);
        }

        statistic.disconnect(id);
        if (!options.isRequireSessionToken() || !sessionTokens.containsKey(id)) {
            connectionIdGenerator.releaseId(id);
        }

        PacketOutbox outbox = options.getPacketOutbox();
        if (outbox != null) {
            List<WrappedPacket> pending = connection.drainQueue();
            if (!pending.isEmpty()) {
                List<WrappedPacket> reliablePending = new ArrayList<>(pending.size());
                for (WrappedPacket wrapped : pending) {
                    if (wrapped.getOptionValue(PacketOptions.DELIVERY_TRACKED) || options.shouldTrackDelivery(wrapped.getPacket())) {
                        reliablePending.add(wrapped);
                    }
                }

                if (!reliablePending.isEmpty()) {
                    List<PacketOutboxResult> results = outbox.tryRetain(id, reliablePending, packetIdGenerator::getAndIncrement);
                    for (PacketOutboxResult result : results) {
                        if (!result.stored()) {
                            notifyReliablePacketRejected(id, result);
                        }
                    }

                    logger.fine("Deferred " + reliablePending.size() + " packet(s) for client " + id + " to outbox");
                }
            }
        }

        onDisconnect(connection);
    }

    public boolean validateSessionToken(int clientId, String sessionToken) {
        if (!options.isRequireSessionToken()) {
            return true;
        }

        if (sessionToken == null || sessionToken.isBlank()) {
            return false;
        }

        if (sessionToken.length() < MIN_SESSION_TOKEN_LENGTH) {
            logger.warning("Session token rejected for client " + clientId + ": insufficient length (minimum " + MIN_SESSION_TOKEN_LENGTH + " characters required)");
            return false;
        }

        Predicate<String> validator = options.getSessionTokenValidator();
        if (validator != null && !validator.test(sessionToken)) {
            logger.warning("Session token rejected for client " + clientId + ": custom validator rejected token");
            return false;
        }

        String existing = sessionTokens.putIfAbsent(clientId, sessionToken);
        return existing == null || existing.equals(sessionToken);
    }

    public void sendToClient(int clientId, AbstractPacket<?, ?> packet) {
        sendToClient(clientId, WrappedPacket.of(packet));
    }

    public void sendToClient(int clientId, WrappedPacket packet) {
        PacketOutbox outbox = options.getPacketOutbox();
        boolean reliable = packet.getOptionValue(PacketOptions.DELIVERY_TRACKED) || options.shouldTrackDelivery(packet.getPacket());

        WrappedPacket prepared = packet;
        PacketOutboxResult trackResult = null;
        if (outbox != null && reliable) {
            trackResult = outbox.tryTrack(clientId, packet, packetIdGenerator::getAndIncrement);
            prepared = trackResult.packet();
            if (!trackResult.stored()) {
                notifyReliablePacketRejected(clientId, trackResult);
                return;
            }
        }

        C connection = getConnection(clientId);
        if (connection != null && connection.isRunning()) {
            EnqueueResult result = connection.tryAddPacket(prepared);
            if (result == EnqueueResult.ACCEPTED && outbox != null && reliable && prepared.getPacketId() > 0) {
                outbox.markSent(clientId, prepared.getPacketId(), System.currentTimeMillis());
            }

            if (result != EnqueueResult.ACCEPTED && reliable) {
                logger.warning("Reliable packet for client " + clientId + " was not queued: " + result);
            }

            return;
        }
        
        if (outbox != null && reliable) {
            if (trackResult != null && trackResult.result() == PacketOutboxStoreResult.ALREADY_STORED) {
                return;
            }

            PacketOutboxResult result = outbox.tryEnqueue(clientId, prepared, packetIdGenerator::getAndIncrement);
            if (!result.stored()) {
                notifyReliablePacketRejected(clientId, result);
            }
        }
    }

    public void notifyReliablePacketRejected(int clientId, PacketOutboxResult result) {
        logger.warning("Reliable packet for client " + clientId + " changed delivery state: " + result.result());
        Consumer<PacketOutboxResult> listener = options.getOnReliablePacketRejected();
        if (listener != null) {
            try {
                listener.accept(result);
            } catch (Exception e) {
                logger.log(Level.WARNING, "onReliablePacketRejected listener threw an exception", e);
            }
        }
    }

    public void sendReliableToClient(int clientId, AbstractPacket<?, ?> packet) {
        sendReliableToClient(clientId, WrappedPacket.of(packet));
    }

    public void sendReliableToClient(int clientId, WrappedPacket packet) {
        sendToClient(clientId, WrappedPacket.builder(packet)
                .withOption(PacketOptions.DELIVERY_TRACKED)
                .build());
    }

    public void sendBestEffortToClient(int clientId, AbstractPacket<?, ?> packet) {
        sendBestEffortToClient(clientId, WrappedPacket.of(packet));
    }

    public void sendBestEffortToClient(int clientId, WrappedPacket packet) {
        sendToClient(clientId, WrappedPacket.builder(packet)
                .withoutOption(PacketOptions.DELIVERY_TRACKED)
                .withoutOption(PacketOptions.DELIVERY_ACK)
                .packetId(0)
                .build());
    }

    private boolean isIpAllowed(InetAddress address) {
        IpFilter filter = options.getIpFilter();
        return filter == null || filter.isAllowed(address);
    }

    private static String extractHost(String remoteAddress) {
        if (remoteAddress == null || remoteAddress.isEmpty()) {
            return null;
        }

        int colonIdx = remoteAddress.lastIndexOf(':');
        return colonIdx > 0 ? remoteAddress.substring(0, colonIdx) : remoteAddress;
    }

    protected void onPreStart() {
        if (options.isRequireSessionToken() && options.isRequireTlsForSessionTokens() && !options.isSSL()) {
            throw new IllegalStateException("Session tokens require TLS when requireTlsForSessionTokens is enabled");
        }

        if (options.isRequireSessionToken() && !options.isSSL()) {
            logger.warning("Session tokens are enabled but SSL is disabled. " +
                    "Session tokens will be transmitted in plaintext, which is insecure. " +
                    "Enable SSL to protect session tokens.");
        }
    }

    protected void onStart() {
        logger.info("Server started");
    }

    protected void onPreStop() {
    }

    protected void onStop() {
        logger.info("Server stopped");
    }

    protected void onConnect(C connection) {
        Consumer<ShinnetaiConnection<?>> listener = options.getOnConnect();
        if (listener != null) {
            try {
                listener.accept(connection);
            } catch (Exception e) {
                logger.log(Level.WARNING, "onConnect listener threw an exception", e);
            }
        }

        Socket socket = connection.getSocket();
        if (socket != null) {
            logger.info(String.format("Client connected: %d, %s:%d", connection.getConnectionId(), socket.getInetAddress(), socket.getPort()));
        } else {
            logger.info(String.format("Client connected: %d", connection.getConnectionId()));
        }
    }

    protected void onDisconnect(ShinnetaiConnection<?> connection) {
        Consumer<ShinnetaiConnection<?>> listener = options.getOnDisconnect();
        if (listener != null) {
            try {
                listener.accept(connection);
            } catch (Exception e) {
                logger.log(Level.WARNING, "onDisconnect listener threw an exception", e);
            }
        }

        Socket socket = connection.getSocket();
        if (socket != null) {
            logger.info(String.format("Client disconnected: %d, %s:%d", connection.getConnectionId(), socket.getInetAddress(), socket.getPort()));
        } else {
            logger.info(String.format("Client disconnected: %d", connection.getConnectionId()));
        }
    }

    private void startRetransmitLoop() {
        if (!options.isReliableDelivery()) {
            return;
        }

        PacketOutbox outbox = options.getPacketOutbox();
        if (outbox == null) {
            return;
        }

        long intervalMs = options.getReliableRetransmitIntervalMs();
        if (intervalMs <= 0) {
            return;
        }

        retransmitExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "shinnetai-reliable-retransmit");
            thread.setDaemon(true);
            return thread;
        });

        retransmitExecutor.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            int maxRetries = options.getReliableMaxRetries();
            for (C connection : connections.values()) {
                List<WrappedPacket> resend = outbox.collectForResend(
                        connection.getConnectionId(),
                        now,
                        intervalMs,
                        maxRetries,
                        result -> notifyReliablePacketRejected(connection.getConnectionId(), result)
                );
                for (WrappedPacket packet : resend) {
                    EnqueueResult result = connection.tryAddPacket(packet);
                    if (result == EnqueueResult.ACCEPTED && packet.getPacketId() > 0) {
                        outbox.markSent(connection.getConnectionId(), packet.getPacketId(), now);
                    } else if (result != EnqueueResult.ACCEPTED) {
                        logger.fine("Reliable resend was not queued for client " + connection.getConnectionId() + ": " + result);
                    }
                }
            }
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }
}