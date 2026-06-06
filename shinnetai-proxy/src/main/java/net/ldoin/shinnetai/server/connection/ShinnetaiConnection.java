package net.ldoin.shinnetai.server.connection;

import net.ldoin.shinnetai.delivery.PacketOutbox;
import net.ldoin.shinnetai.packet.WrappedPacket;
import net.ldoin.shinnetai.packet.common.DisconnectPacket;
import net.ldoin.shinnetai.packet.common.HandshakePacket;
import net.ldoin.shinnetai.packet.registry.PacketRegistry;
import net.ldoin.shinnetai.packet.side.PacketSide;
import net.ldoin.shinnetai.security.AuthenticationContext;
import net.ldoin.shinnetai.server.ShinnetaiServer;
import net.ldoin.shinnetai.statistic.server.ShinnetaiConnectionStatistic;
import net.ldoin.shinnetai.worker.CloseReason;
import net.ldoin.shinnetai.worker.EnqueueResult;
import net.ldoin.shinnetai.worker.ShinnetaiIOWorker;
import net.ldoin.shinnetai.worker.options.WorkerOptions;

import javax.naming.OperationNotSupportedException;
import java.io.IOException;
import java.net.Socket;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ShinnetaiConnection<S extends ShinnetaiServer<?>> extends ShinnetaiIOWorker<ShinnetaiConnectionStatistic> {

    private static String resolveAddress(AutoCloseable connection) {
        if (connection instanceof SocketChannel ch) {
            try {
                Socket socket = ch.socket();
                if (socket != null && socket.getInetAddress() != null) {
                    return socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
                }

                return ch.getRemoteAddress().toString();
            } catch (IOException e) {
                return "unknown";
            }
        } else if (connection instanceof Socket s) {
            return s.getInetAddress().getHostAddress() + ":" + s.getPort();
        }

        return "unknown";
    }

    protected final S server;
    private int connectionId;
    private final AutoCloseable connection;
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    public ShinnetaiConnection(S server, int connectionId, PacketRegistry registry, ShinnetaiConnectionStatistic statistic, ReadableByteChannel in, WritableByteChannel out, AutoCloseable connection, WorkerOptions options) throws IOException {
        this(server, connectionId, registry, statistic, in, out, connection, Logger.getLogger("Connection (" + resolveAddress(connection) + ")"), options);
    }

    protected ShinnetaiConnection(S server, int connectionId, PacketRegistry registry, ShinnetaiConnectionStatistic statistic, ReadableByteChannel in, WritableByteChannel out, AutoCloseable connection, Logger logger, WorkerOptions options) throws IOException {
        super(logger, registry, in, out, statistic, options);
        this.server = server;
        this.connectionId = connectionId;
        this.connection = connection;
    }

    @Override
    public final PacketSide getSide() {
        return PacketSide.SERVER;
    }

    public S getServer() {
        return server;
    }

    public int getConnectionId() {
        return connectionId;
    }

    public void changeConnectionId(int connectionId) throws OperationNotSupportedException {
        if (this.connectionId != 0) {
            throw new OperationNotSupportedException("ID has already been assigned");
        }

        this.connectionId = connectionId;
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

    public String getRemoteAddress() {
        return resolveAddress(connection);
    }

    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key, T defaultValue) {
        Object value = attributes.get(key);
        return value != null ? (T) value : defaultValue;
    }

    public void removeAttribute(String key) {
        attributes.remove(key);
    }

    public boolean hasAttribute(String key) {
        return attributes.containsKey(key);
    }

    @Override
    public AuthenticationContext getAuthenticationContext() {
        if (!isHandshaked()) {
            return AuthenticationContext.anonymous();
        }

        return AuthenticationContext.authenticated("client:" + connectionId, Set.of("client"));
    }

    @Override
    protected void onHandshaked() {
        if (!server.completeHandshake(this)) {
            close(CloseReason.HANDSHAKE_REJECTED);
            return;
        }

        PacketOutbox outbox = server.getOptions().getPacketOutbox();
        if (outbox != null && outbox.hasSession(connectionId) && isRunning()) {
            long now = System.currentTimeMillis();
            for (WrappedPacket p : outbox.collectForReplay(connectionId, now, getOptions().getReliableMaxRetries(),
                    result -> server.notifyReliablePacketRejected(connectionId, result))) {
                if (!isRunning()) {
                    break;
                }

                EnqueueResult result = tryAddPacket(p);
                if (result == EnqueueResult.ACCEPTED && p.getPacketId() > 0) {
                    outbox.markSent(connectionId, p.getPacketId(), now);
                } else if (result != EnqueueResult.ACCEPTED) {
                    getLogger().warning("Replay packet was not queued for client " + connectionId + ": " + result);
                    break;
                }
            }
        }
    }

    @Override
    protected boolean validateHandshake(HandshakePacket handshake) {
        return server.validateSessionToken(connectionId, handshake.getSessionToken());
    }

    @Override
    protected void onHandshakeRejected() {
        close(CloseReason.HANDSHAKE_REJECTED);
    }

    @Override
    protected void onDeliveryAck(long packetId) {
        PacketOutbox outbox = server.getOptions().getPacketOutbox();
        if (outbox != null) {
            outbox.ack(connectionId, packetId);
        }
    }

    @Override
    public synchronized void close() {
        close(false);
    }

    public synchronized void close(boolean packet) {
        if (!packet) {
            try {
                sendPacket(new DisconnectPacket());
                closeAfterFlushing();
            } catch (Exception exception) {
                getLogger().log(Level.SEVERE, "Error while close connection", exception);
            }
        }

        close(packet ? CloseReason.USER_REQUEST : CloseReason.REMOTE_DISCONNECT);
    }

    @Override
    protected synchronized void onClosed(CloseReason reason) {
        server.disconnect(this);
        try {
            connection.close();
        } catch (Exception ignored) {
        }
    }
}