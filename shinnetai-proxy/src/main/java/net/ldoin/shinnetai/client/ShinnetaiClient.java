package net.ldoin.shinnetai.client;

import net.ldoin.shinnetai.ConnectionType;
import net.ldoin.shinnetai.buffered.buf.smart.SmartByteBuf;
import net.ldoin.shinnetai.client.options.ClientOptions;
import net.ldoin.shinnetai.packet.common.DisconnectPacket;
import net.ldoin.shinnetai.packet.common.HandshakePacket;
import net.ldoin.shinnetai.packet.registry.PacketRegistry;
import net.ldoin.shinnetai.packet.side.PacketSide;
import net.ldoin.shinnetai.statistic.client.ShinnetaiClientStatistic;
import net.ldoin.shinnetai.worker.ShinnetaiIOWorker;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ShinnetaiClient extends ShinnetaiIOWorker<ShinnetaiClientStatistic> {

    private final ClientOptions options;
    private Socket socket;
    protected SmartByteBuf startData;

    public ShinnetaiClient(ClientOptions options) throws IOException {
        this(PacketRegistry.getCommons(), options);
    }

    public ShinnetaiClient(PacketRegistry registry, ClientOptions options) throws IOException {
        this(registry, options, ConnectionType.CLIENT);
    }

    protected ShinnetaiClient(PacketRegistry registry, ClientOptions options, ConnectionType connectionType) throws IOException {
        this(registry, options, options.toSocket(), connectionType);
    }

    protected ShinnetaiClient(PacketRegistry registry, ClientOptions options, Socket socket, ConnectionType connectionType) throws IOException {
        this(registry, options, socket, connectionType, Logger.getLogger("Client (" + socket.getInetAddress() + ":" + socket.getPort() + ")"));
    }

    protected ShinnetaiClient(PacketRegistry registry, ClientOptions options, ConnectionType connectionType, Logger logger) throws IOException {
        this(registry, options, options.toSocket(), connectionType, logger);
    }

    protected ShinnetaiClient(PacketRegistry registry, ClientOptions options, Socket socket, ConnectionType connectionType, Logger logger) throws IOException {
        super(logger, registry, socket.getInputStream(), socket.getOutputStream(), new ShinnetaiClientStatistic(), options);
        this.options = options;
        this.socket = socket;

        try {
            socket.setSoTimeout(options.getReadTimeout());
            socket.setKeepAlive(options.isKeepAlive());
        } catch (SocketException e) {
            logger.log(Level.WARNING, "Failed to apply socket options", e);
        }

        if (socket instanceof SSLSocket sslSocket) {
            try {
                sslSocket.startHandshake();
                logger.info("SSL handshake completed with " +
                        socket.getInetAddress() + ":" + socket.getPort());
            } catch (SSLHandshakeException e) {
                logger.log(Level.SEVERE, "SSL handshake failed: " + e.getMessage(), e);
                throw e;
            } catch (SSLException e) {
                logger.log(Level.SEVERE, "SSL connection error: " + e.getMessage(), e);
                throw e;
            }
        }

        this.startData = SmartByteBuf.empty()
                .writeVarInt(connectionType.ordinal())
                .writeVarInt(options.getId());

        Runtime.getRuntime().addShutdownHook(Thread.ofVirtual().unstarted(() -> {
            if (isRunning()) {
                close();
            }
        }));
    }

    @Override
    @SuppressWarnings("unchecked")
    public ShinnetaiClient self() {
        return this;
    }

    @Override
    public final PacketSide getSide() {
        return PacketSide.CLIENT;
    }

    public Socket getSocket() {
        return socket;
    }

    @Override
    public synchronized void start() {
        super.start();
        try {
            getOut().write(startData.toBytes());
            sendPacket(new HandshakePacket(options.getProtocolVersion(), options.getPacketMagic()));
        } catch (IOException e) {
            closeClient(true);
            throw new RuntimeException(e);
        }
    }

    public void disconnect() {
    }

    @Override
    public synchronized void close() {
        closeClient(false);
    }

    public synchronized void closeClient(boolean packet) {
        if (!packet) {
            try {
                sendPacket(new DisconnectPacket());
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            } catch (Exception exception) {
                getLogger().log(Level.SEVERE, "Error while closing connection", exception);
            }
        } else {
            disconnect();
        }

        try {
            socket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        super.close();
    }
}