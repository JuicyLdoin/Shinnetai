package net.aquarealm.shinnetai.client;

import net.aquarealm.shinnetai.ShinnetaiIOWorker;
import net.aquarealm.shinnetai.buffered.buf.smart.SmartByteBuf;
import net.aquarealm.shinnetai.client.options.ClientOptions;
import net.aquarealm.shinnetai.packet.common.DisconnectPacket;
import net.aquarealm.shinnetai.packet.registry.PacketRegistry;
import net.aquarealm.shinnetai.packet.side.PacketSide;
import net.aquarealm.shinnetai.statistic.client.ShinnetaiClientStatistic;
import org.apache.logging.log4j.LogManager;

import java.io.IOException;
import java.net.Socket;

public class ShinnetaiClient extends ShinnetaiIOWorker<ShinnetaiClientStatistic> {

    private final ClientOptions options;
    private final Socket socket;

    public ShinnetaiClient(ClientOptions options) throws IOException {
        this(PacketRegistry.getCommons(), options);
    }

    public ShinnetaiClient(PacketRegistry registry, ClientOptions options) throws IOException {
        this(registry, options, options.toSocket());
    }

    private ShinnetaiClient(PacketRegistry registry, ClientOptions options, Socket socket) throws IOException {
        super(LogManager.getLogger("Client (" + socket.getInetAddress() + ":" + socket.getPort() + ")"), registry, socket.getInputStream(), socket.getOutputStream(), new ShinnetaiClientStatistic());
        this.options = options;
        this.socket = socket;

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (running) {
                close();
            }
        }));
    }

    @Override
    @SuppressWarnings("unchecked")
    protected ShinnetaiClient self() {
        return this;
    }

    @Override
    public final PacketSide getSide() {
        return PacketSide.CLIENT;
    }

    public Socket getSocket() {
        return socket;
    }

    @Override
    public synchronized void start() {
        super.start();
        try {
            out.write(SmartByteBuf.empty().writeVarInt(options.getId()).toBytes());
        } catch (IOException e) {
            closeClient(true);
            throw new RuntimeException(e);
        }
    }

    public void disconnect() {
    }

    @Override
    public synchronized void close() {
        closeClient(false);
    }

    public synchronized void closeClient(boolean packet) {
        if (!packet) {
            try {
                sendPacket(new DisconnectPacket());
                Thread.sleep(100);
            } catch (Exception exception) {
                logger.error("Error while close connection", exception);
            }
        } else {
            disconnect();
        }

        super.close();

        try {
            socket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}