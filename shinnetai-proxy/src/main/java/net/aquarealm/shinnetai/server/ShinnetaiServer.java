package net.aquarealm.shinnetai.server;

import net.aquarealm.shinnetai.buffered.buf.smart.SmartByteBuf;
import net.aquarealm.shinnetai.exception.ShinnetaiExceptions;
import net.aquarealm.shinnetai.packet.AbstractPacket;
import net.aquarealm.shinnetai.packet.common.ServerDisablePacket;
import net.aquarealm.shinnetai.packet.registry.PacketRegistry;
import net.aquarealm.shinnetai.server.connection.ShinnetaiConnection;
import net.aquarealm.shinnetai.server.options.ServerOptions;
import net.aquarealm.shinnetai.statistic.server.ShinnetaiConnectionStatistic;
import net.aquarealm.shinnetai.statistic.server.ShinnetaiServerStatistic;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.naming.OperationNotSupportedException;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.*;

public class ShinnetaiServer<C extends ShinnetaiConnection<?>> extends Thread {

    protected int freeConnectionId = 1;
    private final Map<Integer, C> connections;

    protected final PacketRegistry registry;
    protected final Logger logger;
    protected final ServerOptions options;
    private ServerSocket serverSocket;

    protected final ShinnetaiServerStatistic statistic;

    protected boolean running;

    public ShinnetaiServer(ServerOptions options) {
        this(PacketRegistry.getCommons(), options);
    }

    public ShinnetaiServer(PacketRegistry registry, ServerOptions options) {
        this.registry = registry;
        this.connections = new HashMap<>();
        this.options = options;
        this.statistic = new ShinnetaiServerStatistic();
        this.logger = LogManager.getLogger("Server (" + options.getPort() + ")");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (running) {
                close();
            }
        }));
    }

    protected int getFreeConnectionId() {
        int id = freeConnectionId;
        for (int i = id; i < Integer.MAX_VALUE; i++) {
            if (!connections.containsKey(i)) {
                freeConnectionId = ++i;
                break;
            }
        }

        return id;
    }

    public C getConnection(int id) {
        return connections.get(id);
    }

    public Collection<C> getConnections() {
        return Collections.unmodifiableCollection(connections.values());
    }

    public void sendPacketToAll(AbstractPacket<?, ?> packet) throws IOException {
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

    @Override
    public synchronized void start() {
        if (running) {
            throw new UnsupportedOperationException("Server already started");
        }

        onPreStart();
        super.start();

        running = true;
        onStart();
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(options.getPort());

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    C connection = newConnection(clientSocket);
                    InputStream input = connection.getInput();

                    byte[] readBuffer;
                    int id;
                    while (true) {
                        int available = input.available();
                        if (available > 0) {
                            readBuffer = new byte[available];
                            int bytesRead = input.read(readBuffer);

                            if (bytesRead > 0) {
                                id = SmartByteBuf.of(readBuffer).readVarInt();
                                break;
                            }
                        }
                    }

                    try {
                        if (id == 0) {
                            id = getFreeConnectionId();
                        }

                        if (connections.containsKey(id)) {
                            logger.warn("Failed connect, id {} is busy", id);
                            connection.sendException(ShinnetaiExceptions.FAILED_CONNECTION_ID_BUSY);
                            disconnect(connection);
                            continue;
                        }

                        connection.changeConnectionId(id);
                    } catch (OperationNotSupportedException e) {
                        logger.error("Failed connect", e);
                        connection.sendException(ShinnetaiExceptions.FAILED_ASSIGN_CONNECTION_ID);
                        disconnect(connection);
                        continue;
                    }

                    statistic.connect(connection);

                    onConnect(connection);
                    connections.put(id, connection);
                    connection.start();
                } catch (SocketException ignored) {
                } catch (IOException e) {
                    logger.error("Failed connect", e);
                }
            }
        } catch (IOException e) {
            logger.error("Failed while running server: ", e);
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
            for (C connection : new ArrayList<>(connections.values())) {
                connection.sendPacket(new ServerDisablePacket());
                connection.close();
            }

            Thread.sleep(100);
        } catch (Exception exception) {
            logger.error("Error while disable server", exception);
        }

        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            logger.error("Server could not disable: ", e);
        }

        interrupt();
        connections.clear();
        freeConnectionId = 1;

        onStop();
    }

    @SuppressWarnings("unchecked")
    protected C newConnection(Socket socket) throws IOException {
        return (C) new ShinnetaiConnection<>(this, 0, registry, new ShinnetaiConnectionStatistic(statistic), socket);
    }

    public void disconnect(C connection) {
        int id = connection.getConnectionId();
        connections.remove(id);
        statistic.disconnect(id);

        freeConnectionId = Math.min(id, freeConnectionId);

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
        logger.info("Client connected: {}, {}:{}", connection.getConnectionId(), socket.getInetAddress(), socket.getPort());
    }

    protected void onDisconnect(C connection) {
        Socket socket = connection.getSocket();
        logger.info("Client disconnected: {}, {}:{}", connection.getConnectionId(), socket.getInetAddress(), socket.getPort());
    }
}