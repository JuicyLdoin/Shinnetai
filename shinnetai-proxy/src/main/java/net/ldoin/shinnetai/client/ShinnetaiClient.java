package net.ldoin.shinnetai.client;

import net.ldoin.shinnetai.ConnectionType;
import net.ldoin.shinnetai.ShinnetaiIOWorker;
import net.ldoin.shinnetai.buffered.buf.smart.SmartByteBuf;
import net.ldoin.shinnetai.client.options.ClientOptions;
import net.ldoin.shinnetai.packet.common.DisconnectPacket;
import net.ldoin.shinnetai.packet.registry.PacketRegistry;
import net.ldoin.shinnetai.packet.side.PacketSide;
import net.ldoin.shinnetai.statistic.client.ShinnetaiClientStatistic;

import java.io.IOException;
import java.net.Socket;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ShinnetaiClient extends ShinnetaiIOWorker<ShinnetaiClientStatistic> {

    private final ClientOptions options;
    private Socket socket;
    private String clusterGroup;
    protected SmartByteBuf startData;
    private boolean redirecting = false;

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
        super(logger, registry, socket.getInputStream(), socket.getOutputStream(), new ShinnetaiClientStatistic());
        this.options = options;
        this.socket = socket;
        this.startData = SmartByteBuf.empty().writeVarInt(connectionType.ordinal()).writeVarInt(options.getId());

        if (connectionType == ConnectionType.CLIENT && options.isClustering()) {
            Set<String> clusterGroups = options.getClusterGroups();
            this.startData.writeVarInt(clusterGroups.size());
            for (String group : clusterGroups) {
                this.startData.writeString(group);
            }
        }

        Runtime.getRuntime().addShutdownHook(Thread.ofVirtual().unstarted(() -> {
            if (isRunning()) {
                close();
            }
        }));
    }

    @Override
    @SuppressWarnings("unchecked")
    protected ShinnetaiClient self() {
        return this;
    }

    @Override
    public final PacketSide getSide() {
        return PacketSide.CLIENT;
    }

    public Socket getSocket() {
        return socket;
    }

    public String getClusterGroup() {
        if (!options.isClustering()) {
            throw new UnsupportedOperationException("Client is not connected to the cluster");
        }

        return clusterGroup;
    }

    @Override
    public synchronized void start() {
        super.start();
        try {
            getOut().write(startData.toBytes());
        } catch (IOException e) {
            closeClient(true);
            throw new RuntimeException(e);
        }
    }

    public void disconnect() {
    }

    public void redirect(String clusterGroup, String address, int port) throws IOException {
        redirecting = true;

        boolean debug = !options.getAddress().equals(address) && options.getPort() != port;
        if (debug) {
            getLogger().info(String.format("Redirecting to %s:%d...", address, port));
        }

        closeClient(true, true);
        Thread.interrupted();

        socket = new Socket(address, port);
        attachIOStreams(socket);
        start();

        this.clusterGroup = clusterGroup;
        if (debug) {
            getLogger().info("Redirected");
        }

        redirecting = false;
    }

    @Override
    public synchronized void close() {
        closeClient(false);
    }

    public synchronized void closeClient(boolean packet) {
        closeClient(packet, false);
    }

    public synchronized void closeClient(boolean packet, boolean clearIO) {
        if (!packet) {
            try {
                sendPacket(new DisconnectPacket());
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            } catch (Exception exception) {
                getLogger().log(Level.SEVERE, "Error while close connection", exception);
            }
        } else if (options.isClustering() && options.isRedirecting() && !redirecting) {
            try {
                redirect(null, options.getAddress(), options.getPort());
            } catch (IOException exception) {
                getLogger().log(Level.SEVERE, "Error while trying redirect to cluster", exception);
            }
        } else {
            disconnect();
        }

        try {
            socket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        super.close(clearIO);
    }
}