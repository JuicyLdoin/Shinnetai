package net.ldoin.shinnetai.cluster;

import net.ldoin.shinnetai.ConnectionType;
import net.ldoin.shinnetai.buffered.buf.smart.ReadOnlySmartByteBuf;
import net.ldoin.shinnetai.cluster.node.ShinnetaiClusterNode;
import net.ldoin.shinnetai.cluster.node.options.ClusterNodeOptions;
import net.ldoin.shinnetai.cluster.node.registered.ShinnetaiNodeConnection;
import net.ldoin.shinnetai.cluster.node.registered.ShinnetaiRegisteredNode;
import net.ldoin.shinnetai.cluster.options.ClusterMode;
import net.ldoin.shinnetai.cluster.options.ClusterOptions;
import net.ldoin.shinnetai.cluster.task.ClusterPingTask;
import net.ldoin.shinnetai.exception.ShinnetaiException;
import net.ldoin.shinnetai.exception.ShinnetaiExceptions;
import net.ldoin.shinnetai.packet.common.RedirectPacket;
import net.ldoin.shinnetai.packet.registry.PacketRegistry;
import net.ldoin.shinnetai.server.ShinnetaiServer;
import net.ldoin.shinnetai.server.options.ServerOptions;
import net.ldoin.shinnetai.statistic.cluster.ShinnetaiClusterStatistic;
import net.ldoin.shinnetai.statistic.server.ShinnetaiConnectionStatistic;

import java.io.IOException;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ShinnetaiCluster<C extends ShinnetaiNodeConnection> extends ShinnetaiServer<C> {

    private final Map<String, List<C>> nodes = new ConcurrentHashMap<>();
    private final ClusterOptions options;
    private final ShinnetaiClusterStatistic clusterStatistic;
    private final ScheduledExecutorService executorService;

    public ShinnetaiCluster(ClusterOptions options) {
        this(PacketRegistry.getCommons(), options);
    }

    public ShinnetaiCluster(PacketRegistry packetRegistry, ClusterOptions options) {
        this(packetRegistry, options, Logger.getLogger("Cluster (" + options.getPort() + ")"));
    }

    private ShinnetaiCluster(PacketRegistry packetRegistry, ClusterOptions options, Logger logger) {
        super(packetRegistry, ServerOptions.builder(options.getPort()).build(), logger);
        this.options = options;
        this.clusterStatistic = new ShinnetaiClusterStatistic();
        this.executorService = Executors.newSingleThreadScheduledExecutor();
        this.executorService.scheduleAtFixedRate(new ClusterPingTask<>(this), 0, options.getPingInterval(), TimeUnit.MILLISECONDS);
    }

    public ClusterOptions getOptions() {
        return options;
    }

    public ShinnetaiClusterStatistic getClusterStatistic() {
        return clusterStatistic;
    }

    public List<C> getGroupNodes(String group) {
        return nodes.getOrDefault(group, Collections.emptyList());
    }

    public boolean hasGroup(List<String> groups) {
        return groups.stream().anyMatch(nodes::containsKey);
    }

    public boolean hasGroup(String group) {
        return nodes.containsKey(group);
    }

    public Optional<C> firstAvailable() {
        return firstAvailable("default");
    }

    public Optional<C> firstAvailable(List<String> groups) {
        return groups.stream()
                .filter(this::hasGroup)
                .map(this::firstAvailable)
                .flatMap(Optional::stream)
                .findFirst();
    }

    public Optional<C> firstAvailable(String group) {
        return nodes.getOrDefault(group, Collections.emptyList())
                .stream()
                .findFirst();
    }

    public Optional<String> findOptimal(List<String> groups) {
        return groups.stream()
                .filter(nodes::containsKey)
                .findFirst();
    }

    @Override
    public synchronized void start() {
        super.start();
        if (options.getMode() == ClusterMode.EMBEDDED) {
            for (ClusterNodeOptions nodeOptions : options.getEmbeddedServers()) {
                try {
                    createNode(nodeOptions.toServerOptions(), nodeOptions).start();
                } catch (IOException e) {
                    getLogger().log(Level.SEVERE, "Failed to start embedded node", e);
                }
            }
        }
    }

    @Override
    public synchronized void close() {
        executorService.shutdownNow();
        super.close();
    }

    @Override
    protected boolean canAcceptConnection(C connection, ConnectionType connectionType, ReadOnlySmartByteBuf data) {
        return connectionType == ConnectionType.NODE;
    }

    @Override
    protected void cannotAccept(C connection, ConnectionType connectionType, ReadOnlySmartByteBuf data) {
        String group = connection.getNode().getGroup();
        firstAvailable(group).ifPresentOrElse(
                c -> {
                    try {
                        ShinnetaiRegisteredNode node = c.getNode();
                        getLogger().info(String.format("Redirecting to Node[%s] (%s:%d)", group, node.getAddress(), node.getPort()));
                        connection.sendPacket(new RedirectPacket(group, node.getAddress(), node.getPort()));
                    } catch (IOException e) {
                        handleRedirectException(connection, "Failed to redirect connection", e, ShinnetaiExceptions.FAILED_REDIRECT_TO_NODE);
                    }
                },
                () -> handleRedirectException(connection, "No available node found", null, ShinnetaiExceptions.NO_AVAILABLE_NODE_FOUND)
        );
    }

    private void handleRedirectException(C connection, String logMessage, Throwable throwable, ShinnetaiException exceptionToSend) {
        if (throwable != null) {
            getLogger().log(Level.WARNING, logMessage, throwable);
        } else {
            getLogger().warning(logMessage);
        }

        try {
            connection.sendException(exceptionToSend);
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "Failed to send exception to client", e);
        }

        try {
            Thread.sleep(100);
        } catch (InterruptedException ignored) {
        }

        connection.close();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected C newConnection(Socket socket, ConnectionType connectionType, ReadOnlySmartByteBuf data) throws IOException {
        int port = socket.getPort();
        String group = "default";
        int maxConnections = 1;
        if (connectionType == ConnectionType.NODE) {
            port = data.readVarInt();
            group = data.readString();
            maxConnections = data.readVarInt();
        } else if (data.remain() > 0) {
            List<String> groups = new ArrayList<>();
            int size = data.readVarInt();
            for (int i = 0; i < size; i++) {
                groups.add(data.readString());
            }

            group = findOptimal(groups).orElse("default");
        }

        ShinnetaiRegisteredNode node = new ShinnetaiRegisteredNode(socket.getInetAddress().getHostAddress(), port, group, maxConnections);
        C connection = (C) new ShinnetaiNodeConnection(this, 0, getRegistry(), new ShinnetaiConnectionStatistic(getStatistic()), socket, node, connectionType);
        if (connectionType == ConnectionType.NODE) {
            clusterStatistic.connectNode(node);
        } else {
            clusterStatistic.connectClient(connection);
        }

        return connection;
    }

    @Override
    public void connect(C connection) {
        nodes.computeIfAbsent(connection.getNode().getGroup(), s -> new CopyOnWriteArrayList<>()).add(connection);

        if (connection.getConnectionType() == ConnectionType.NODE) {
            ShinnetaiRegisteredNode node = connection.getNode();
            getLogger().info(String.format("Node connected: %d, %s:%d", connection.getConnectionId(), node.getAddress(), node.getPort()));
        } else {
            super.onConnect(connection);
        }
    }

    @Override
    public void disconnect(C connection) {
        super.disconnect(connection);
        if (connection.getConnectionType() != ConnectionType.NODE) {
            return;
        }

        String group = connection.getNode().getGroup();
        List<C> groupList = nodes.get(group);
        if (groupList != null && groupList.remove(connection) && groupList.isEmpty()) {
            nodes.remove(group);
        }

        getLogger().info(String.format("Node[%s] disconnected: %d, %s:%d", group, connection.getConnectionId(), connection.getNode().getAddress(), connection.getNode().getPort()));
    }

    @Override
    protected void onDisconnect(C connection) {
    }

    protected ShinnetaiClusterNode<?> createNode(ServerOptions serverOptions, ClusterNodeOptions nodeOptions) throws IOException {
        return new ShinnetaiClusterNode<>(serverOptions, nodeOptions);
    }
}