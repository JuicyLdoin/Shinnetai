package net.ldoin.shinnetai.cluster.options;

import net.ldoin.shinnetai.cluster.node.options.ClusterNodeOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ClusterOptions {

    public static ClusterOptions of(ClusterMode mode, int port) {
        return new Builder(mode, port).build();
    }

    public static Builder builder(ClusterMode mode, int port) {
        return new Builder(mode, port);
    }

    private final ClusterMode mode;
    private final int port;
    private final List<ClusterNodeOptions> embeddedServers;
    private final int pingInterval;
    private final int serverTimeout;

    private ClusterOptions(Builder builder) {
        this.mode = builder.mode;
        this.port = builder.port;
        this.embeddedServers = builder.embeddedServers;
        this.pingInterval = builder.pingInterval;
        this.serverTimeout = builder.serverTimeout;
    }

    public ClusterMode getMode() {
        return mode;
    }

    public int getPort() {
        return port;
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

    public static class Builder {

        private final ClusterMode mode;
        private final int port;
        private final List<ClusterNodeOptions> embeddedServers;
        private int pingInterval = 3000;
        private int serverTimeout = 10_000;

        public Builder(ClusterMode mode, int port) {
            this.mode = mode;
            this.port = port;

            if (mode == ClusterMode.REGISTRY) {
                this.embeddedServers = new ArrayList<>();
            } else {
                this.embeddedServers = Collections.emptyList();
            }
        }

        public Builder addEmbeddedServer(ClusterNodeOptions nodeOptions) {
            if (mode == ClusterMode.REGISTRY) {
                throw new UnsupportedOperationException("Cannot add embedded servers in REGISTRY mode");
            }

            this.embeddedServers.add(nodeOptions);
            return this;
        }

        public Builder setPingInterval(int intervalMillis) {
            this.pingInterval = intervalMillis;
            return this;
        }

        public Builder setServerTimeout(int timeoutMillis) {
            this.serverTimeout = timeoutMillis;
            return this;
        }

        public ClusterOptions build() {
            return new ClusterOptions(this);
        }
    }
}