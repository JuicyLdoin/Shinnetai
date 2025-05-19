package net.ldoin.shinnetai.cluster.node.options;

import net.ldoin.shinnetai.server.options.ServerOptions;

public class ClusterNodeOptions {

    public static ClusterNodeOptions of(String address, int port) {
        return new Builder(address, port).build();
    }

    public static Builder builder(String address, int port) {
        return new Builder(address, port);
    }

    private final String address;
    private final int port;
    private final String group;
    private final int maxConnections;

    private ClusterNodeOptions(Builder builder) {
        this.address = builder.address;
        this.port = builder.port;
        this.group = builder.group;
        this.maxConnections = builder.maxConnections;
    }

    public String getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    public String getGroup() {
        return group;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public ServerOptions toServerOptions() {
        return ServerOptions.builder(port).setMaxConnections(maxConnections).build();
    }

    public static class Builder {

        private final String address;
        private final int port;
        private String group;
        private int maxConnections;

        public Builder(String address, int port) {
            this.address = address;
            this.port = port;
            this.group = "default";
        }

        public Builder setGroup(String group) {
            this.group = group;
            return this;
        }

        public Builder setMaxConnections(int maxConnections) {
            this.maxConnections = maxConnections;
            return this;
        }

        public ClusterNodeOptions build() {
            return new ClusterNodeOptions(this);
        }
    }
}