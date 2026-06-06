package net.ldoin.shinnetai.client;

import net.ldoin.shinnetai.ConnectionType;
import net.ldoin.shinnetai.buffered.buf.smart.SmartByteBuf;
import net.ldoin.shinnetai.client.options.ClientOptions;
import net.ldoin.shinnetai.packet.common.DisconnectPacket;
import net.ldoin.shinnetai.packet.common.HandshakePacket;
import net.ldoin.shinnetai.packet.registry.PacketRegistry;
import net.ldoin.shinnetai.packet.side.PacketSide;
import net.ldoin.shinnetai.resilience.CircuitBreaker;
import net.ldoin.shinnetai.statistic.client.ShinnetaiClientStatistic;
import net.ldoin.shinnetai.worker.CloseReason;
import net.ldoin.shinnetai.worker.ShinnetaiIOWorker;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ShinnetaiClient extends ShinnetaiIOWorker<ShinnetaiClientStatistic> {

    private final ClientOptions options;
    private AutoCloseable connection;
    protected SmartByteBuf startData;
    private final Thread shutdownHook;
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);
    private volatile boolean userClosed = false;

    public ShinnetaiClient(ClientOptions options) throws IOException {
        this(PacketRegistry.getCommons(), options);
    }

    public ShinnetaiClient(PacketRegistry registry, ClientOptions options) throws IOException {
        this(registry, options, ConnectionType.CLIENT);
    }

    protected ShinnetaiClient(PacketRegistry registry, ClientOptions options, ConnectionType connectionType) throws IOException {
        this(registry, options, connectionType, null);
    }

    protected ShinnetaiClient(PacketRegistry registry, ClientOptions options, ConnectionType connectionType, Logger logger) throws IOException {
        super(resolveLogger(logger, options), registry, null, null, new ShinnetaiClientStatistic(), options);
        this.options = options;

        this.startData = SmartByteBuf.empty()
                .writeVarInt(connectionType.ordinal())
                .writeVarInt(options.getId());

        initConnection();

        this.shutdownHook = Thread.ofPlatform().unstarted(() -> {
            if (isRunning()) {
                close();
            }
        });
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    private void initConnection() throws IOException {
        if (options.isSSL()) {
            SSLSocket sslSocket = (SSLSocket) options.toSocket();
            try {
                sslSocket.setSoTimeout(options.getReadTimeout());
                sslSocket.setKeepAlive(options.isKeepAlive());
            } catch (SocketException e) {
                getLogger().log(Level.WARNING, "Failed to apply socket options", e);
            }

            try {
                sslSocket.startHandshake();
                getLogger().info("SSL handshake completed with " + sslSocket.getInetAddress() + ":" + sslSocket.getPort());
            } catch (SSLHandshakeException e) {
                sslSocket.close();
                throw e;
            } catch (SSLException e) {
                sslSocket.close();
                throw e;
            }

            attachChannels(Channels.newChannel(sslSocket.getInputStream()), Channels.newChannel(sslSocket.getOutputStream()));
            this.connection = sslSocket;
        } else {
            SocketChannel channel = options.toSocketChannel();
            try {
                channel.socket().setKeepAlive(options.isKeepAlive());
            } catch (SocketException e) {
                getLogger().log(Level.WARNING, "Failed to apply socket options", e);
            }

            attachChannels(channel, channel);
            this.connection = channel;
        }
    }

    private static Logger resolveLogger(Logger logger, ClientOptions options) {
        return logger != null ? logger
                : Logger.getLogger("Client (" + options.getAddress() + ":" + options.getPort() + ")");
    }

    @Override
    public final PacketSide getSide() {
        return PacketSide.CLIENT;
    }

    public Socket getSocket() {
        if (connection instanceof Socket s) {
            return s;
        }

        if (connection instanceof SocketChannel ch) {
            return ch.socket();
        }

        return null;
    }

    @Override
    public synchronized void start() {
        userClosed = false;

        super.start();
        try {
            synchronized (writeLock) {
                ByteBuffer data = ByteBuffer.wrap(startData.toBytes());
                while (data.hasRemaining()) {
                    outChannel.write(data);
                }
            }

            sendPacket(new HandshakePacket(options.getProtocolVersion(), options.getPacketMagic(), options.getSupportedFeatures(), options.getSessionToken(), System.currentTimeMillis()));
        } catch (IOException e) {
            closeClient(true, false);
            throw new RuntimeException(e);
        }
    }

    public void disconnect() {
    }

    @Override
    public synchronized void close() {
        closeClient(false);
    }

    public synchronized void closeClient(boolean forceClose) {
        closeClient(forceClose, true);
    }

    private synchronized void closeClient(boolean forceClose, boolean userRequested) {
        if (userRequested) {
            userClosed = true;
        }

        if (connection == null && !isRunning()) {
            return;
        }

        if (!forceClose) {
            try {
                if (getOut() != null) {
                    sendPacket(new DisconnectPacket());
                    closeAfterFlushing();
                    awaitQueueDrain(Math.max(500L, options.getSendTimeoutMs()));
                }
            } catch (Exception exception) {
                getLogger().log(Level.SEVERE, "Error while closing connection", exception);
            }
        } else {
            disconnect();
        }

        super.close(userRequested ? CloseReason.USER_REQUEST : CloseReason.IO_ERROR);
    }

    @Override
    protected synchronized void onClosed(CloseReason reason) {
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (Exception e) {
            getLogger().log(Level.FINE, "Error while closing socket", e);
        }

        connection = null;

        if (reason == CloseReason.USER_REQUEST) {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException ignored) {
            }
        }
    }

    private void awaitQueueDrain(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (getQueueSize() == 0) {
                return;
            }

            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    @Override
    protected void onConnectionLost(Throwable cause) {
        if (options.isAutoReconnect() && !userClosed && getLastCloseReason() != CloseReason.USER_REQUEST) {
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        if (!reconnecting.compareAndSet(false, true)) {
            return;
        }

        CircuitBreaker breaker = options.getCircuitBreaker();
        int maxAttempts = options.getMaxReconnectAttempts();
        int delayMs = options.getReconnectDelayMs();
        Thread.ofVirtual().name("shinnetai-reconnect").start(() -> {
            try {
                int attempt = 0;
                while (!userClosed && (maxAttempts == 0 || attempt < maxAttempts)) {
                    if (breaker != null && !breaker.isCallAllowed()) {
                        getLogger().warning("Circuit breaker " + breaker.getState() + ", reconnect suspended");
                        try {
                            Thread.sleep(delayMs);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }

                        continue;
                    }

                    attempt++;
                    try {
                        Thread.sleep(delayMs);
                        if (userClosed) {
                            return;
                        }

                        getLogger().info("Reconnecting... (attempt " + attempt + ")");
                        synchronized (ShinnetaiClient.this) {
                            initConnection();
                            start();
                        }

                        if (breaker != null) {
                            breaker.recordSuccess();
                        }

                        getLogger().info("Reconnected successfully");
                        return;
                    } catch (Exception e) {
                        if (breaker != null) {
                            breaker.recordFailure();
                        }

                        getLogger().log(Level.WARNING, "Reconnect attempt " + attempt + " failed: " + e.getMessage());
                    }
                }

                getLogger().warning("Auto-reconnect exhausted after " + attempt + " attempts");
            } finally {
                reconnecting.set(false);
            }
        });
    }
}