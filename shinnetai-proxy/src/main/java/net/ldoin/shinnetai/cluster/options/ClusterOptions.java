package net.ldoin.shinnetai.cluster.options;

import net.ldoin.shinnetai.cluster.node.options.ClusterNodeOptions;
import net.ldoin.shinnetai.server.options.ServerOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ClusterOptions extends ServerOptions {

    public static ClusterOptions of(ClusterMode mode, int port) {
        return new Builder<>(mode, port).build();
    }

    public static Builder<?> builder(ClusterMode mode, int port) {
        return new Builder<>(mode, port);
    }

    private final ClusterMode mode;
    private final List<ClusterNodeOptions> embeddedServers;
    private final int pingInterval;
    private final int serverTimeout;

    protected ClusterOptions(Builder<?> builder) {
        super(builder);
        this.mode = builder.mode;
        this.embeddedServers = builder.embeddedServers;
        this.pingInterval = builder.pingInterval;
        this.serverTimeout = builder.serverTimeout;
    }

    public ClusterMode getMode() {
        return mode;
    }

    public List<ClusterNodeOptions> getEmbeddedServers() {
        return Collections.unmodifiableList(embeddedServers);
    }

    public int getPingInterval() {
        return pingInterval;
    }

    public int getServerTimeout() {
        return serverTimeout;
    }

    public static class Builder<B extends Builder<?>> extends ServerOptions.Builder<B> {

        private final ClusterMode mode;
        private final List<ClusterNodeOptions> embeddedServers;
        private int pingInterval = 3000;
        private int serverTimeout = 10_000;

        public Builder(ClusterMode mode, int port) {
            super(port);
            this.mode = mode;

            if (mode == ClusterMode.REGISTRY) {
                this.embeddedServers = Collections.emptyList();
            } else {
                this.embeddedServers = new ArrayList<>();
            }
        }

        public B addEmbeddedServer(ClusterNodeOptions nodeOptions) {
            if (mode == ClusterMode.REGISTRY) {
                throw new UnsupportedOperationException("Cannot add embedded servers in REGISTRY mode");
            }

            this.embeddedServers.add(nodeOptions);
            return self();
        }

        public B setPingInterval(int intervalMillis) {
            this.pingInterval = intervalMillis;
            return self();
        }

        public B setServerTimeout(int timeoutMillis) {
            this.serverTimeout = timeoutMillis;
            return self();
        }

        @Override
        @SuppressWarnings("unchecked")
        protected B self() {
            return (B) this;
        }

        public ClusterOptions build() {
            return new ClusterOptions(this);
        }
    }
}