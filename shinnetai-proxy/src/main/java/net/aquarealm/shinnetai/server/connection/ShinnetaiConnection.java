package net.aquarealm.shinnetai.server.connection;

import net.aquarealm.shinnetai.ShinnetaiIOWorker;
import net.aquarealm.shinnetai.packet.common.DisconnectPacket;
import net.aquarealm.shinnetai.packet.registry.PacketRegistry;
import net.aquarealm.shinnetai.packet.side.PacketSide;
import net.aquarealm.shinnetai.server.ShinnetaiServer;
import net.aquarealm.shinnetai.statistic.server.ShinnetaiConnectionStatistic;
import org.apache.logging.log4j.LogManager;

import javax.naming.OperationNotSupportedException;
import java.io.IOException;
import java.net.Socket;

public class ShinnetaiConnection<S extends ShinnetaiServer> extends ShinnetaiIOWorker<ShinnetaiConnectionStatistic> {

    protected final S server;
    private int connectionId;
    private final Socket socket;

    public ShinnetaiConnection(S server, int connectionId, PacketRegistry registry, ShinnetaiConnectionStatistic statistic, Socket socket) throws IOException {
        super(LogManager.getLogger("Connection (" + socket.getInetAddress() + ":" + socket.getPort() + ")"), registry, socket.getInputStream(), socket.getOutputStream(), statistic);
        this.server = server;
        this.connectionId = connectionId;
        this.socket = socket;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected ShinnetaiConnection<S> self() {
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
        closeConnection(false);
    }

    public synchronized void closeConnection(boolean packet) {
        if (!packet) {
            try {
                sendPacket(new DisconnectPacket());
                Thread.sleep(100);
            } catch (Exception exception) {
                logger.error("Error while close connection", exception);
            }
        }

        server.disconnect(this);
        super.close();

        try {
            socket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}