package net.aquarealm.shinnetai;

import net.aquarealm.shinnetai.buffered.buf.smart.ReadOnlySmartByteBuf;
import net.aquarealm.shinnetai.buffered.buf.smart.WriteOnlySmartByteBuf;
import net.aquarealm.shinnetai.exception.ShinnetaiException;
import net.aquarealm.shinnetai.exception.ShinnetaiExceptions;
import net.aquarealm.shinnetai.packet.AbstractPacket;
import net.aquarealm.shinnetai.packet.common.ServerDisablePacket;
import net.aquarealm.shinnetai.packet.registry.PacketRegistry;
import net.aquarealm.shinnetai.packet.response.PacketResponseOptions;
import net.aquarealm.shinnetai.packet.response.PacketResponseWaiter;
import net.aquarealm.shinnetai.packet.side.PacketSide;
import net.aquarealm.shinnetai.server.connection.ShinnetaiConnection;
import net.aquarealm.shinnetai.statistic.ShinnetaiStatistic;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedList;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

public abstract class ShinnetaiIOWorker<S extends ShinnetaiStatistic> extends Thread {

    protected final Logger logger;
    protected final PacketRegistry registry;
    protected final InputStream input;
    protected final OutputStream out;
    protected final Queue<AbstractPacket<?, ?>> outQueue;
    protected final PacketResponseWaiter responseWaiter;

    protected final S statistic;

    protected volatile boolean running;

    public ShinnetaiIOWorker(Logger logger, PacketRegistry registry, InputStream input, OutputStream out, S statistic) {
        this.logger = logger;
        this.registry = registry;
        this.input = input;
        this.out = out;
        this.statistic = statistic;
        this.outQueue = new LinkedList<>();
        this.responseWaiter = new PacketResponseWaiter();
    }

    protected abstract <W extends ShinnetaiIOWorker<?>> W self();

    public abstract PacketSide getSide();

    public Logger getLogger() {
        return logger;
    }

    public PacketRegistry getRegistry() {
        return registry;
    }

    public InputStream getInput() {
        return input;
    }

    public OutputStream getOut() {
        return out;
    }

    public S getStatistic() {
        return statistic;
    }

    public void addPacket(AbstractPacket<?, ?> packet) {
        outQueue.add(packet);
    }

    @SuppressWarnings("unchecked")
    public void sendPacket(AbstractPacket<?, ?> packet, PacketResponseOptions options, WriteOnlySmartByteBuf buf) throws IOException {
        if (packet == null || !running) {
            return;
        }

        applyHandler(packet);

        int id = registry.getId((Class<? extends AbstractPacket<?, ?>>) packet.getClass());

        try {
            buf.writeVarInt(id);
            options.write(buf);
            packet.write(buf);
        } catch (Exception exception) {
            logger.error(exception);
            sendException(ShinnetaiExceptions.FAILED_WRITE_PACKET, id);
            return;
        }

        try {
            byte[] bytes = buf.toBytes();
            try {
                statistic.send(bytes);
            } catch (Exception exception) {
                logger.error("Error on write sent bytes statistics", exception);
            }

            out.write(bytes);
            out.flush();
        } catch (Exception exception) {
            logger.error(exception);
            sendException(ShinnetaiExceptions.FAILED_SEND_PACKET, id);
        }
    }

    public void sendPacket(AbstractPacket<?, ?> packet, WriteOnlySmartByteBuf buf) throws IOException {
        sendPacket(packet, PacketResponseOptions.empty(), buf);
    }

    public void sendPacket(AbstractPacket<?, ?> packet, PacketResponseOptions options) throws IOException {
        sendPacket(packet, options, WriteOnlySmartByteBuf.empty());
    }

    public void sendPacket(AbstractPacket<?, ?> packet) throws IOException {
        sendPacket(packet, WriteOnlySmartByteBuf.empty());
    }

    @SuppressWarnings("unchecked")
    public <P extends AbstractPacket<?, ?>> P sendAndWaitForResponse(AbstractPacket<?, ?> packet, long timeoutMillis) throws IOException, TimeoutException, InterruptedException, ArrayIndexOutOfBoundsException {
        int waiterId = responseWaiter.addWaiter(false, null, timeoutMillis);
        sendPacket(packet, PacketResponseOptions.waitResponse(waiterId));
        return (P) responseWaiter.waitForResponse(waiterId, timeoutMillis);
    }

    public void sendAsyncWithResponse(AbstractPacket<?, ?> packet, Consumer<Optional<AbstractPacket<?, ?>>> responseHandler, long timeoutMillis) throws ArrayIndexOutOfBoundsException {
        int waiterId = responseWaiter.addWaiter(true, responseHandler, timeoutMillis);
        try {
            sendPacket(packet, PacketResponseOptions.waitResponse(waiterId));
        } catch (IOException e) {
            logger.error("Failed to send packet with waiterId: " + waiterId, e);
            responseWaiter.handleResponse(waiterId, ShinnetaiExceptions.FAILED_SEND_RESPONSE.toPacket(waiterId));
        }
    }

    protected void readPacket(byte[] bytes) throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException, IOException {
        statistic.receive(bytes);

        ReadOnlySmartByteBuf buf = ReadOnlySmartByteBuf.of(bytes);
        int id = buf.readVarInt();
        PacketResponseOptions responseOptions = PacketResponseOptions.of(buf);

        AbstractPacket<?, ?> packet = registry.createPacket(id);
        applyHandler(packet);

        try {
            packet.read(buf);
        } catch (Exception exception) {
            logger.error(exception);
            sendException(ShinnetaiExceptions.FAILED_READ_PACKET, id);
            return;
        }

        try {
            packet.handle(getSide());
        } catch (Exception exception) {
            logger.error(exception);
            sendException(ShinnetaiExceptions.FAILED_HANDLE_PACKET, id);
            return;
        }

        if (responseOptions.isResponse()) {
            int response = responseOptions.getResponseId();
            try {
                responseWaiter.handleResponse(response, packet);
            } catch (Exception exception) {
                logger.error(exception);
                sendException(ShinnetaiExceptions.FAILED_HANDLE_RESPONSE, id, response);
            }
        }

        if (responseOptions.isWaitResponse()) {
            int waitResponse = responseOptions.getWaitResponse();
            try {
                AbstractPacket<?, ?> response = packet.response();
                if (response != null) {
                    sendPacket(response, PacketResponseOptions.response(waitResponse));
                } else {
                    logger.error("Cannot find response for " + id);
                    sendException(ShinnetaiExceptions.FAILED_FIND_RESPONSE, id);
                }
            } catch (Exception exception) {
                logger.error(exception);
                sendException(ShinnetaiExceptions.FAILED_SEND_RESPONSE, id, waitResponse);
            }
        }
    }

    public void sendException(ShinnetaiException exception, Object... objects) throws IOException {
        sendPacket(exception.toPacket(objects));
    }

    public void sendException(ShinnetaiException exception) throws IOException {
        sendPacket(exception.toPacket());
    }

    protected void applyHandler(AbstractPacket<?, ?> packet) {
        switch (getSide()) {
            case CLIENT -> packet.attachClientWorker(self());
            case SERVER -> packet.attachServerWorker(self());
            case MULTIPLE -> {
                packet.attachClientWorker(self());
                packet.attachServerWorker(self());
            }
        }
    }

    @Override
    public synchronized void start() {
        super.start();
        running = true;
    }

    @Override
    public void run() {
        try {
            byte[] readBuffer;
            while (running) {
                int available = input.available();
                if (available > 0) {
                    readBuffer = new byte[available];
                    int bytesRead = input.read(readBuffer);
                    if (bytesRead > 0) {
                        try {
                            readPacket(readBuffer);
                        } catch (InvocationTargetException | IllegalAccessException | InstantiationException |
                                 NoSuchMethodException e) {
                            logger.error("Error handling packet", e);
                        }
                    }
                }

                try {
                    AbstractPacket<?, ?> packetToSend = outQueue.poll();
                    if (packetToSend != null) {
                        sendPacket(packetToSend);
                    }
                } catch (IOException e) {
                    logger.error("Error sending packet", e);
                }

                Thread.sleep(2);
            }
        } catch (IOException e) {
            logger.error("Connection error", e);
        } catch (InterruptedException ignored) {
        } finally {
            if (running) {
                close();
            }
        }
    }

    public synchronized void close() {
        running = false;
        interrupt();
    }
}