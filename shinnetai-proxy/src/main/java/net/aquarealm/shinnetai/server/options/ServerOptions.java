package net.aquarealm.shinnetai.server.options;

public class ServerOptions {

    public static ServerOptions of(int port) {
        return new ServerOptions(port);
    }

    public static Builder builder(int port) {
        return new Builder(port);
    }

    private final int port;

    private ServerOptions(int port) {
        this.port = port;
    }

    public int getPort() {
        return port;
    }

    public static class Builder {

        private final int port;

        public Builder(int port) {
            this.port = port;
        }

        public ServerOptions build() {
            return new ServerOptions(port);
        }
    }
}