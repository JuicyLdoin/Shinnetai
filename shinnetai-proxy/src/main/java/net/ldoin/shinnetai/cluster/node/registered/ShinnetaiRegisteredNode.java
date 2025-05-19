package net.ldoin.shinnetai.cluster.node.registered;

public class ShinnetaiRegisteredNode {

    private final String address;
    private final int port;
    private final String group;
    private final int maxClients;
    private int currentClients;
    private long lastPing;

    public ShinnetaiRegisteredNode(String address, int port, String group, int maxClients) {
        this.address = address;
        this.port = port;
        this.group = group;
        this.maxClients = maxClients;
        this.currentClients = 0;
        this.lastPing = System.currentTimeMillis();
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

    public int getMaxClients() {
        return maxClients;
    }

    public int getCurrentClients() {
        return currentClients;
    }

    public void setCurrentClients(int currentClients) {
        this.currentClients = currentClients;
    }

    public long getLastPing() {
        return lastPing;
    }

    public void setLastPing(long lastPing) {
        this.lastPing = lastPing;
    }
}