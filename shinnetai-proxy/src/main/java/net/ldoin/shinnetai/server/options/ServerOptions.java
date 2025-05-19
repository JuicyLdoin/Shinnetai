package net.ldoin.shinnetai.server.options;

public class ServerOptions {

    public static ServerOptions of(int port) {
        return new Builder(port).build();
    }

    public static Builder builder(int port) {
        return new Builder(port);
    }

    private final int port;
    private final int maxConnections;

    private ServerOptions(Builder builder) {
        this.port = builder.port;
        this.maxConnections = builder.maxConnections;
    }

    public int getPort() {
        return port;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public static class Builder {

        private final int port;
        private int maxConnections = 0;

        public Builder(int port) {
            this.port = port;
        }

        public Builder setMaxConnections(int maxConnections) {
            if (maxConnections < 0) {
                throw new UnsupportedOperationException("Connection limit cannot be set below 0 (unlimited)");
            }

            this.maxConnections = maxConnections;
            return this;
        }

        public ServerOptions build() {
            return new ServerOptions(this);
        }
    }
}