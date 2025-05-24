package net.ldoin.shinnetai;

import net.ldoin.shinnetai.buffered.buf.ByteBuf;
import net.ldoin.shinnetai.buffered.buf.smart.ReadOnlySmartByteBuf;
import net.ldoin.shinnetai.buffered.buf.smart.WriteOnlySmartByteBuf;
import net.ldoin.shinnetai.exception.ShinnetaiException;
import net.ldoin.shinnetai.exception.ShinnetaiExceptions;
import net.ldoin.shinnetai.log.ShinnetaiLog;
import net.ldoin.shinnetai.packet.AbstractPacket;
import net.ldoin.shinnetai.packet.ReadPacket;
import net.ldoin.shinnetai.packet.registry.PacketRegistry;
import net.ldoin.shinnetai.packet.response.PacketResponseOptions;
import net.ldoin.shinnetai.packet.response.PacketResponseWaiter;
import net.ldoin.shinnetai.packet.side.PacketSide;
import net.ldoin.shinnetai.statistic.ShinnetaiStatistic;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.SocketException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class ShinnetaiWorkerContext<S extends ShinnetaiStatistic> {

    private final Logger logger;
    private final PacketRegistry registry;
    protected InputStream input;
    protected OutputStream out;
    protected final PacketResponseWaiter responseWaiter;

    private final S statistic;

    protected ShinnetaiWorkerContext(Logger logger, PacketRegistry registry, InputStream input, OutputStream out) {
        this(logger, registry, input, out, null, null);
    }

    protected ShinnetaiWorkerContext(Logger logger, PacketRegistry registry, InputStream input, OutputStream out, PacketResponseWaiter responseWaiter) {
        this(logger, registry, input, out, responseWaiter, null);
    }

    protected ShinnetaiWorkerContext(Logger logger, PacketRegistry registry, InputStream input, OutputStream out, S statistic) {
        this(logger, registry, input, out, null, statistic);
    }

    protected ShinnetaiWorkerContext(Logger logger, PacketRegistry registry, InputStream input, OutputStream out, PacketResponseWaiter responseWaiter, S statistic) {
        ShinnetaiLog.init();

        this.logger = logger;
        this.registry = registry;
        this.input = input;
        this.out = out;
        this.responseWaiter = responseWaiter;
        this.statistic = statistic;
    }

    protected abstract <W extends ShinnetaiWorkerContext<?>> W self();

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

    public PacketResponseWaiter getResponseWaiter() {
        return responseWaiter;
    }

    public S getStatistic() {
        return statistic;
    }

    public void sendPacket(AbstractPacket<?, ?> packet, PacketResponseOptions options, WriteOnlySmartByteBuf buf) throws IOException {
        if (packet == null || out == null) {
            return;
        }

        attachWorker(packet);

        int id = registry.getId(packet.getClass());
        try {
            buf.writeVarInt(id);
            options.write(buf);
            packet.write(buf);
        } catch (Exception exception) {
            logger.log(Level.SEVERE, "", exception);
            sendException(ShinnetaiExceptions.FAILED_WRITE_PACKET, id);
            return;
        }

        try {
            byte[] bytes = buf.toBytes();
            if (statistic != null) {
                try {
                    statistic.send(bytes);
                } catch (Exception exception) {
                    logger.log(Level.SEVERE, "Error on write sent bytes statistics", exception);
                }
            }

            bytes = ByteBuf.empty().writeShort(bytes.length).writeBytes(bytes).toBytes();

            out.write(bytes);
            out.flush();
        } catch (SocketException exception) {
            logger.log(Level.WARNING, String.format("Trying send packet %d to closed socket", id), exception);
        } catch (Exception exception) {
            logger.log(Level.SEVERE, "", exception);
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

    public <P extends AbstractPacket<?, ?>> P sendAndWaitForResponse(AbstractPacket<?, ?> packet, TimeUnit timeUnit, int timeout) throws IOException, TimeoutException, InterruptedException, ArrayIndexOutOfBoundsException {
        return sendAndWaitForResponse(packet, timeUnit.toMillis(timeout));
    }

    @SuppressWarnings("unchecked")
    public <P extends AbstractPacket<?, ?>> P sendAndWaitForResponse(AbstractPacket<?, ?> packet, long timeoutMillis) throws IOException, TimeoutException, InterruptedException, ArrayIndexOutOfBoundsException {
        if (responseWaiter == null) {
            throw new UnsupportedOperationException();
        }

        int waiterId = responseWaiter.addWaiter(false, null, timeoutMillis);
        sendPacket(packet, PacketResponseOptions.waitResponse(waiterId));
        return (P) responseWaiter.waitForResponse(waiterId, timeoutMillis);
    }

    public CompletableFuture<Optional<AbstractPacket<?, ?>>> sendAsyncWithResponse(AbstractPacket<?, ?> packet, TimeUnit timeUnit, int timeout) throws ArrayIndexOutOfBoundsException {
        return sendAsyncWithResponse(packet, timeUnit.toMillis(timeout));
    }

    public CompletableFuture<Optional<AbstractPacket<?, ?>>> sendAsyncWithResponse(AbstractPacket<?, ?> packet, long timeoutMillis) throws ArrayIndexOutOfBoundsException {
        if (responseWaiter == null) {
            throw new UnsupportedOperationException();
        }

        CompletableFuture<Optional<AbstractPacket<?, ?>>> future = new CompletableFuture<>();
        int waiterId = responseWaiter.addWaiter(true, future, timeoutMillis);
        try {
            sendPacket(packet, PacketResponseOptions.waitResponse(waiterId));
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to send packet with waiterId: " + waiterId, e);
            responseWaiter.handleResponse(waiterId, ShinnetaiExceptions.FAILED_SEND_RESPONSE.toPacket(waiterId));
        }

        return future;
    }

    public ReadPacket readPacket(byte[] bytes) throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException, IOException {
        if (statistic != null) {
            statistic.receive(bytes);
        }

        ReadOnlySmartByteBuf buf = ReadOnlySmartByteBuf.of(bytes);
        int id = buf.readVarInt();
        PacketResponseOptions responseOptions = PacketResponseOptions.of(buf);

        AbstractPacket<?, ?> packet = registry.createPacket(id);
        attachWorker(packet);

        try {
            packet.read(buf);
        } catch (Exception exception) {
            logger.log(Level.SEVERE, "Failed read packet", exception);
            sendException(ShinnetaiExceptions.FAILED_READ_PACKET, id);
            return null;
        }

        return new ReadPacket(id, packet, responseOptions);
    }

    public void handlePacket(ReadPacket readPacket) throws IOException {
        int id = readPacket.id();
        AbstractPacket<?, ?> packet = readPacket.packet();
        PacketResponseOptions responseOptions = readPacket.responseOptions();

        try {
            if (!responseOptions.isResponse()) {
                packet.handle(getSide());
            }
        } catch (Exception exception) {
            logger.log(Level.SEVERE, "Failed handle packet", exception);
            sendException(ShinnetaiExceptions.FAILED_HANDLE_PACKET, id);
            return;
        }

        if (responseWaiter != null && responseOptions.isResponse()) {
            int response = responseOptions.getResponseId();
            try {
                responseWaiter.handleResponse(response, packet);
            } catch (Exception exception) {
                logger.log(Level.SEVERE, "", exception);
                sendException(ShinnetaiExceptions.FAILED_HANDLE_RESPONSE, id, response);
            }
        }

        if (responseWaiter != null && responseOptions.isWaitResponse()) {
            int waitResponse = responseOptions.getWaitResponse();
            try {
                AbstractPacket<?, ?> response = packet.response();
                if (response != null) {
                    sendPacket(response, PacketResponseOptions.response(waitResponse));
                } else {
                    logger.log(Level.SEVERE, "Cannot find response for " + id);
                    sendException(ShinnetaiExceptions.FAILED_FIND_RESPONSE, id);
                }
            } catch (Exception exception) {
                logger.log(Level.SEVERE, "Failed send response", exception);
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

    protected void attachWorker(AbstractPacket<?, ?> packet) {
        try {
            switch (getSide()) {
                case CLIENT -> packet.attachClientWorker(self());
                case SERVER -> packet.attachServerWorker(self());
                case MULTIPLE -> {
                    packet.attachClientWorker(self());
                    packet.attachServerWorker(self());
                }
            }
        } catch (UnsupportedOperationException ignored) {
        }
    }
}