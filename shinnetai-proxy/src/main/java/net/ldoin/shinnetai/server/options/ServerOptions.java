package net.ldoin.shinnetai.server.options;

import net.ldoin.shinnetai.delivery.PacketOutbox;
import net.ldoin.shinnetai.delivery.PacketOutboxResult;
import net.ldoin.shinnetai.security.IpFilter;
import net.ldoin.shinnetai.server.connection.ShinnetaiConnection;
import net.ldoin.shinnetai.util.SSLUtil;
import net.ldoin.shinnetai.worker.options.WorkerOptions;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.channels.ServerSocketChannel;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class ServerOptions extends WorkerOptions {

    public static ServerOptions of(int port) {
        return new Builder<>(port).build();
    }

    public static Builder<?> builder(int port) {
        return new Builder<>(port);
    }

    private final int port;
    private final int maxConnections;
    private final int maxConnectionsPerIp;
    private final IpFilter ipFilter;
    private final Consumer<ShinnetaiConnection<?>> onConnect;
    private final Consumer<ShinnetaiConnection<?>> onDisconnect;
    private final PacketOutbox packetOutbox;
    private final Consumer<PacketOutboxResult> onReliablePacketRejected;
    private final Predicate<String> sessionTokenValidator;
    private final boolean allowClientGeneratedSessionTokens;

    protected ServerOptions(Builder<?> builder) {
        super(builder);
        this.port = builder.port;
        this.maxConnections = builder.maxConnections;
        this.maxConnectionsPerIp = builder.maxConnectionsPerIp;
        this.ipFilter = builder.ipFilter;
        this.onConnect = builder.onConnect;
        this.onDisconnect = builder.onDisconnect;
        this.packetOutbox = builder.packetOutbox;
        this.onReliablePacketRejected = builder.onReliablePacketRejected;
        this.sessionTokenValidator = builder.sessionTokenValidator;
        this.allowClientGeneratedSessionTokens = builder.allowClientGeneratedSessionTokens;
    }

    public int getPort() {
        return port;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public int getMaxConnectionsPerIp() {
        return maxConnectionsPerIp;
    }

    public IpFilter getIpFilter() {
        return ipFilter;
    }

    public Consumer<ShinnetaiConnection<?>> getOnConnect() {
        return onConnect;
    }

    public Consumer<ShinnetaiConnection<?>> getOnDisconnect() {
        return onDisconnect;
    }

    public PacketOutbox getPacketOutbox() {
        return packetOutbox;
    }

    public Consumer<PacketOutboxResult> getOnReliablePacketRejected() {
        return onReliablePacketRejected;
    }

    public Predicate<String> getSessionTokenValidator() {
        return sessionTokenValidator;
    }

    public boolean isAllowClientGeneratedSessionTokens() {
        return allowClientGeneratedSessionTokens;
    }

    public ServerSocket toSocket() throws IOException {
        ServerSocket socket;
        if (isSSL()) {
            try {
                if (getSSLKeystore() != null) {
                    socket = SSLUtil.createServerSocketFactory(
                            getSSLKeystore(),
                            getSSLKeystorePassword(),
                            getSSLKeyPassword()
                    ).createServerSocket(port);
                } else {
                    socket = SSLUtil.getDefaultServerSocketFactory().createServerSocket(port);
                }
            } catch (Exception e) {
                throw new IOException("Failed to create SSL server socket", e);
            }
        } else {
            socket = new ServerSocket(port);
        }

        return socket;
    }

    public ServerSocketChannel toServerSocketChannel() throws IOException {
        ServerSocketChannel channel = ServerSocketChannel.open();
        channel.bind(new InetSocketAddress(port));
        return channel;
    }

    public static class Builder<B extends Builder<?>> extends WorkerOptions.Builder<B> {

        private final int port;
        private int maxConnections = 0;
        private int maxConnectionsPerIp = 0;
        private IpFilter ipFilter;
        private Consumer<ShinnetaiConnection<?>> onConnect;
        private Consumer<ShinnetaiConnection<?>> onDisconnect;
        private PacketOutbox packetOutbox;
        private Consumer<PacketOutboxResult> onReliablePacketRejected;
        private Predicate<String> sessionTokenValidator;
        private boolean allowClientGeneratedSessionTokens;

        public Builder(int port) {
            this.port = port;
        }

        public B maxConnections(int maxConnections) {
            if (maxConnections < 0) {
                throw new UnsupportedOperationException("Connection limit cannot be set below 0 (unlimited)");
            }

            this.maxConnections = maxConnections;
            return self();
        }

        public B maxConnectionsPerIp(int maxConnectionsPerIp) {
            this.maxConnectionsPerIp = maxConnectionsPerIp;
            return self();
        }

        public B ipFilter(IpFilter ipFilter) {
            this.ipFilter = ipFilter;
            return self();
        }

        public B onConnect(Consumer<ShinnetaiConnection<?>> onConnect) {
            this.onConnect = onConnect;
            return self();
        }

        public B onDisconnect(Consumer<ShinnetaiConnection<?>> onDisconnect) {
            this.onDisconnect = onDisconnect;
            return self();
        }

        public B packetOutbox(PacketOutbox outbox) {
            this.packetOutbox = outbox;
            return self();
        }

        public B onReliablePacketRejected(Consumer<PacketOutboxResult> onReliablePacketRejected) {
            this.onReliablePacketRejected = onReliablePacketRejected;
            return self();
        }

        public B sessionTokenValidator(Predicate<String> sessionTokenValidator) {
            this.sessionTokenValidator = sessionTokenValidator;
            return self();
        }

        public B allowClientGeneratedSessionTokens(boolean allowClientGeneratedSessionTokens) {
            this.allowClientGeneratedSessionTokens = allowClientGeneratedSessionTokens;
            return self();
        }

        @Override
        @SuppressWarnings("unchecked")
        protected B self() {
            return (B) this;
        }

        public ServerOptions build() {
            if (isRequireSessionTokenRequested() && sessionTokenValidator == null && !allowClientGeneratedSessionTokens) {
                throw new IllegalStateException("requireSessionToken(true) requires sessionTokenValidator(...) unless allowClientGeneratedSessionTokens(true) is explicitly enabled");
            }

            return new ServerOptions(this);
        }
    }
}