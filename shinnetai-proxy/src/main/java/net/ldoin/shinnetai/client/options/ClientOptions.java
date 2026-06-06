package net.ldoin.shinnetai.client.options;

import net.ldoin.shinnetai.resilience.CircuitBreaker;
import net.ldoin.shinnetai.util.SSLUtil;
import net.ldoin.shinnetai.worker.options.WorkerOptions;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.SocketChannel;
import java.util.UUID;

public class ClientOptions extends WorkerOptions {

    public static ClientOptions of(String address, int port) {
        return new Builder<>(address, port).build();
    }

    public static Builder<?> builder(String address, int port) {
        return new Builder<>(address, port);
    }

    private final String address;
    private final int port;
    private final int id;
    private final boolean autoReconnect;
    private final int reconnectDelayMs;
    private final int maxReconnectAttempts;
    private final CircuitBreaker circuitBreaker;
    private final String sessionToken;

    protected ClientOptions(Builder<?> builder) {
        super(builder);
        this.address = builder.address;
        this.port = builder.port;
        this.id = builder.id;
        this.autoReconnect = builder.autoReconnect;
        this.reconnectDelayMs = builder.reconnectDelayMs;
        this.maxReconnectAttempts = builder.maxReconnectAttempts;
        this.circuitBreaker = builder.circuitBreaker;
        this.sessionToken = builder.sessionToken;
    }

    public String getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    public int getId() {
        return id;
    }

    public boolean isAutoReconnect() {
        return autoReconnect;
    }

    public int getReconnectDelayMs() {
        return reconnectDelayMs;
    }

    public int getMaxReconnectAttempts() {
        return maxReconnectAttempts;
    }

    public CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public Socket toSocket() throws IOException {
        Socket socket;
        if (isSSL()) {
            try {
                if (getSSLKeystore() != null) {
                    socket = SSLUtil.createSocketFactory(
                            getSSLKeystore(),
                            getSSLKeystorePassword(),
                            getSSLKeyPassword()
                    ).createSocket(address, port);
                } else {
                    socket = SSLUtil.getDefaultSocketFactory().createSocket(address, port);
                }
            } catch (Exception e) {
                throw new IOException("Failed to create SSL socket", e);
            }
        } else {
            socket = new Socket(address, port);
        }

        return socket;
    }

    public SocketChannel toSocketChannel() throws IOException {
        SocketChannel channel = SocketChannel.open();
        channel.connect(new InetSocketAddress(address, port));
        return channel;
    }

    public static class Builder<B extends Builder<?>> extends WorkerOptions.Builder<B> {

        private final String address;
        private final int port;
        private int id;
        private boolean autoReconnect = false;
        private int reconnectDelayMs = 3000;
        private int maxReconnectAttempts = 0;
        private CircuitBreaker circuitBreaker;
        private String sessionToken = UUID.randomUUID().toString();

        public Builder(String address, int port) {
            this.address = address;
            this.port = port;
            this.id = 0;
        }

        public B id(int id) {
            this.id = id;
            return self();
        }

        public B autoReconnect(boolean autoReconnect) {
            this.autoReconnect = autoReconnect;
            return self();
        }

        public B reconnectDelayMs(int reconnectDelayMs) {
            this.reconnectDelayMs = reconnectDelayMs;
            return self();
        }

        public B maxReconnectAttempts(int maxReconnectAttempts) {
            this.maxReconnectAttempts = maxReconnectAttempts;
            return self();
        }

        public B circuitBreaker(CircuitBreaker circuitBreaker) {
            this.circuitBreaker = circuitBreaker;
            return self();
        }

        public B sessionToken(String sessionToken) {
            this.sessionToken = sessionToken;
            return self();
        }

        @SuppressWarnings("unchecked")
        protected B self() {
            return (B) this;
        }

        public ClientOptions build() {
            return new ClientOptions(this);
        }
    }
}