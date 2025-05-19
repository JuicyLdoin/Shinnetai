package net.ldoin.shinnetai.client.options;

import java.io.IOException;
import java.net.Socket;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ClientOptions {

    public static ClientOptions of(String address, int port) {
        return new Builder(address, port).build();
    }

    public static Builder builder(String address, int port) {
        return new Builder(address, port);
    }

    private final String address;
    private final int port;
    private final int id;
    private final boolean clustering;
    private final Set<String> clusterGroups;
    private final boolean redirecting;

    private ClientOptions(Builder builder) {
        this.address = builder.address;
        this.port = builder.port;
        this.id = builder.id;
        this.clustering = builder.clustering;
        this.clusterGroups = builder.clusterGroups;
        this.redirecting = builder.redirecting;
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

    public boolean isClustering() {
        return clustering;
    }

    public Set<String> getClusterGroups() {
        return Collections.unmodifiableSet(clusterGroups);
    }

    public boolean isRedirecting() {
        return redirecting;
    }

    public Socket toSocket() throws IOException {
        return new Socket(address, port);
    }

    public static class Builder {

        private final String address;
        private final int port;
        private int id;
        private boolean clustering;
        private final Set<String> clusterGroups;
        private boolean redirecting;

        public Builder(String address, int port) {
            this.address = address;
            this.port = port;
            this.id = 0;
            this.clustering = false;
            this.clusterGroups = new HashSet<>();
            this.clusterGroups.add("default");
            this.redirecting = true;
        }

        public Builder setId(int id) {
            this.id = id;
            return this;
        }

        public Builder setClustering(boolean clustering) {
            this.clustering = clustering;
            return this;
        }

        public Builder addClusterGroup(String clusterGroup) {
            this.clusterGroups.add(clusterGroup);
            return this;
        }

        public Builder clearClusterGroups() {
            this.clusterGroups.clear();
            return this;
        }

        public Builder setRedirecting(boolean redirecting) {
            this.redirecting = redirecting;
            return this;
        }

        public ClientOptions build() {
            return new ClientOptions(this);
        }
    }
}