package net.aquarealm.shinnetai.client.options;

import java.io.IOException;
import java.net.Socket;

public class ClientOptions {

    public static ClientOptions of(String address, int port) {
        return new ClientOptions(address, port);
    }

    public static ClientOptions of(String address, int port, int id) {
        return new ClientOptions(address, port, id);
    }

    public static Builder builder(String address, int port) {
        return new Builder(address, port);
    }

    private final String serverAddress;
    private final int serverPort;
    private final int id;

    public ClientOptions(String serverAddress, int serverPort) {
        this(serverAddress, serverPort, 0);
    }

    private ClientOptions(String serverAddress, int serverPort, int id) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.id = id;
    }

    public String getServerAddress() {
        return serverAddress;
    }

    public int getServerPort() {
        return serverPort;
    }

    public int getId() {
        return id;
    }

    public Socket toSocket() throws IOException {
        return new Socket(serverAddress, serverPort);
    }

    public static class Builder {

        private final String serverAddress;
        private final int serverPort;
        private int id;

        public Builder(String serverAddress, int serverPort) {
            this.serverAddress = serverAddress;
            this.serverPort = serverPort;
            this.id = 0;
        }

        public Builder setId(int id) {
            this.id = id;
            return this;
        }

        public ClientOptions build() {
            return new ClientOptions(serverAddress, serverPort, id);
        }
    }
}