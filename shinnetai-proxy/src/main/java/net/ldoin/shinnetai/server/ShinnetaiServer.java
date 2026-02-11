package net.ldoin.shinnetai.server;

import net.ldoin.shinnetai.ConnectionType;
import net.ldoin.shinnetai.buffered.buf.smart.ReadOnlySmartByteBuf;
import net.ldoin.shinnetai.exception.ShinnetaiExceptions;
import net.ldoin.shinnetai.log.ShinnetaiLog;
import net.ldoin.shinnetai.packet.WrappedPacket;
import net.ldoin.shinnetai.packet.common.ServerDisablePacket;
import net.ldoin.shinnetai.packet.registry.PacketRegistry;
import net.ldoin.shinnetai.server.connection.ShinnetaiConnection;
import net.ldoin.shinnetai.server.options.ServerOptions;
import net.ldoin.shinnetai.statistic.server.ShinnetaiConnectionStatistic;
import net.ldoin.shinnetai.statistic.server.ShinnetaiServerStatistic;
import net.ldoin.shinnetai.util.IdGenerator;

import javax.naming.OperationNotSupportedException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ShinnetaiServer<C extends ShinnetaiConnection<?>> implements Runnable {

    private final Map<Integer, C> connections;
    private final IdGenerator connectionIdGenerator;

    private final PacketRegistry registry;
    private final Logger logger;
    protected final ServerOptions options;
    private ServerSocket serverSocket;

    private final ShinnetaiServerStatistic statistic;

    protected Thread workingThread;
    private volatile boolean running;

    public ShinnetaiServer(ServerOptions options) {
        this(PacketRegistry.getCommons(), options);
    }

    public ShinnetaiServer(PacketRegistry registry, ServerOptions options) {
        this(registry, options, Logger.getLogger("Server (" + options.getPort() + ")"));
    }

    protected ShinnetaiServer(PacketRegistry registry, ServerOptions options, Logger logger) {
        ShinnetaiLog.init();

        this.connections = new ConcurrentHashMap<>();
        this.connectionIdGenerator = new IdGenerator();
        this.registry = registry;
        this.logger = logger;
        this.options = options;
        this.statistic = new ShinnetaiServerStatistic();

        Runtime.getRuntime().addShutdownHook(Thread.ofVirtual().unstarted(() -> {
            if (running) {
                close();
            }
        }));
    }

    public C getConnection(int id) {
        return connections.get(id);
    }

    public Collection<C> getConnections() {
        return Collections.unmodifiableCollection(connections.values());
    }

    public void sendPacketToAll(WrappedPacket packet) throws IOException {
        for (C connection : connections.values()) {
            connection.sendPacket(packet);
        }
    }

    public PacketRegistry getRegistry() {
        return registry;
    }

    public Logger getLogger() {
        return logger;
    }

    public ShinnetaiServerStatistic getStatistic() {
        return statistic;
    }

    public ServerSocket getServerSocket() {
        return serverSocket;
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

        workingThread = new Thread(this);
        workingThread.start();

        running = true;
        onStart();
    }

    @Override
    public void run() {
        try {
            serverSocket = options.toSocket();
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    try {
                        clientSocket.setSoTimeout(options.getReadTimeout());
                        clientSocket.setKeepAlive(options.isKeepAlive());
                    } catch (SocketException e) {
                        logger.log(Level.WARNING, "Failed to apply socket options", e);
                        clientSocket.close();
                        continue;
                    }

                    if (clientSocket instanceof SSLSocket sslSocket) {
                        try {
                            sslSocket.startHandshake();
                        } catch (SSLHandshakeException e) {
                            logger.log(Level.WARNING, "SSL handshake failed: " + e.getMessage());
                            sslSocket.close();
                            continue;
                        }
                    }

                    InputStream input = clientSocket.getInputStream();
                    byte[] readBuffer = new byte[1024];
                    int bytesRead = input.read(readBuffer);
                    if (bytesRead == -1) {
                        break;
                    }

                    byte[] actualData = new byte[bytesRead];
                    System.arraycopy(readBuffer, 0, actualData, 0, bytesRead);

                    ReadOnlySmartByteBuf data = ReadOnlySmartByteBuf.of(actualData);
                    ConnectionType connectionType = ConnectionType.VALUES[data.readVarInt()];
                    int id = data.readVarInt();

                    C connection = newConnection(clientSocket, connectionType, data);
                    try {
                        if (id == 0) {
                            id = connectionIdGenerator.getNextId();
                        }

                        if (connections.containsKey(id)) {
                            logger.log(Level.WARNING, String.format("Failed connect, id %s is busy", id));
                            connection.sendException(ShinnetaiExceptions.FAILED_CONNECTION_ID_BUSY);
                            disconnect(connection);
                            continue;
                        }

                        connection.changeConnectionId(id);
                    } catch (OperationNotSupportedException e) {
                        logger.log(Level.SEVERE, "Failed connect", e);
                        connection.sendException(ShinnetaiExceptions.FAILED_ASSIGN_CONNECTION_ID);
                        disconnect(connection);
                        continue;
                    }

                    if (!canAcceptConnection(connection, connectionType, ReadOnlySmartByteBuf.of(data.toBytes()))) {
                        cannotAccept(connection, connectionType, ReadOnlySmartByteBuf.of(data.toBytes()));
                        continue;
                    }

                    statistic.connect(connection);
                    connections.put(id, connection);
                    connection.start();

                    connect(connection);
                } catch (SocketException ignored) {
                } catch (IOException e) {
                    logger.log(Level.SEVERE, "Failed connect", e);
                }
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed while running server: ", e);
        } finally {
            if (running) {
                close();
            }
        }
    }

    public synchronized void close() {
        if (!running) {
            throw new UnsupportedOperationException("Server is not started");
        }

        running = false;
        onPreStop();

        try {
            for (C connection : connections.values()) {
                connection.sendPacket(new ServerDisablePacket());
                connection.close(true);
            }

            Thread.sleep(100);
        } catch (Exception exception) {
            logger.log(Level.SEVERE, "Error while disable server", exception);
        }

        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Server could not disable: ", e);
        }

        if (workingThread != null) {
            workingThread.interrupt();
        }

        workingThread = null;
        connections.clear();
        connectionIdGenerator.clear();

        onStop();
    }

    protected boolean canAcceptConnection(C connection, ConnectionType connectionType, ReadOnlySmartByteBuf data) {
        return options.getMaxConnections() == 0 || connections.size() < options.getMaxConnections();
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
    protected C newConnection(Socket socket, ConnectionType connectionType, ReadOnlySmartByteBuf data) throws IOException {
        return (C) new ShinnetaiConnection<>(this, 0, registry, new ShinnetaiConnectionStatistic(statistic), socket, options);
    }

    public void connect(C connection) {
        onConnect(connection);
    }

    public void disconnect(C connection) {
        int id = connection.getConnectionId();
        connections.remove(id);
        statistic.disconnect(id);

        connectionIdGenerator.releaseId(id);

        onDisconnect(connection);
    }

    protected void onPreStart() {
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
        Socket socket = connection.getSocket();
        logger.info(String.format("Client connected: %d, %s:%d", connection.getConnectionId(), socket.getInetAddress(), socket.getPort()));
    }

    protected void onDisconnect(C connection) {
        Socket socket = connection.getSocket();
        logger.info(String.format("Client disconnected: %d, %s:%d", connection.getConnectionId(), socket.getInetAddress(), socket.getPort()));
    }
}