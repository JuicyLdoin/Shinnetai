package net.ldoin.shinnetai.server.connection;

import net.ldoin.shinnetai.ShinnetaiIOWorker;
import net.ldoin.shinnetai.packet.common.DisconnectPacket;
import net.ldoin.shinnetai.packet.registry.PacketRegistry;
import net.ldoin.shinnetai.packet.side.PacketSide;
import net.ldoin.shinnetai.server.ShinnetaiServer;
import net.ldoin.shinnetai.statistic.server.ShinnetaiConnectionStatistic;

import javax.naming.OperationNotSupportedException;
import java.io.IOException;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ShinnetaiConnection<S extends ShinnetaiServer> extends ShinnetaiIOWorker<ShinnetaiConnectionStatistic> {

    protected final S server;
    private int connectionId;
    private final Socket socket;

    public ShinnetaiConnection(S server, int connectionId, PacketRegistry registry, ShinnetaiConnectionStatistic statistic, Socket socket) throws IOException {
        this(server, connectionId, registry, statistic, socket, Logger.getLogger("Connection (" + socket.getInetAddress() + ":" + socket.getPort() + ")"));
    }

    protected ShinnetaiConnection(S server, int connectionId, PacketRegistry registry, ShinnetaiConnectionStatistic statistic, Socket socket, Logger logger) throws IOException {
        super(logger, registry, socket.getInputStream(), socket.getOutputStream(), statistic);
        this.server = server;
        this.connectionId = connectionId;
        this.socket = socket;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ShinnetaiConnection<S> self() {
        return this;
    }

    @Override
    public final PacketSide getSide() {
        return PacketSide.SERVER;
    }

    public S getServer() {
        return server;
    }

    public int getConnectionId() {
        return connectionId;
    }

    public void changeConnectionId(int connectionId) throws OperationNotSupportedException {
        if (this.connectionId != 0) {
            throw new OperationNotSupportedException("ID has already been assigned");
        }

        this.connectionId = connectionId;
    }

    public Socket getSocket() {
        return socket;
    }

    @Override
    public synchronized void close() {
        close(false);
    }

    public synchronized void close(boolean packet) {
        if (!packet) {
            try {
                sendPacket(new DisconnectPacket());
                Thread.sleep(100);
            } catch (Exception exception) {
                getLogger().log(Level.SEVERE, "Error while close connection", exception);
            }
        }

        server.disconnect(this);

        try {
            socket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        super.close();
    }
}