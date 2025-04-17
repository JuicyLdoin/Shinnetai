package net.ldoin.shinnetai.client.options;

import net.ldoin.shinnetai.util.SSLUtil;
import net.ldoin.shinnetai.worker.options.WorkerOptions;

import java.io.IOException;
import java.net.Socket;

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

    protected ClientOptions(Builder<?> builder) {
        super(builder);
        this.address = builder.address;
        this.port = builder.port;
        this.id = builder.id;
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

    public static class Builder<B extends Builder<?>> extends WorkerOptions.Builder<B> {

        private final String address;
        private final int port;
        private int id;

        public Builder(String address, int port) {
            this.address = address;
            this.port = port;
            this.id = 0;
        }

        public B setId(int id) {
            this.id = id;
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