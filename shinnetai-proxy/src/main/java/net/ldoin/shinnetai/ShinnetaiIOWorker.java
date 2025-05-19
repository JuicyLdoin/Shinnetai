package net.ldoin.shinnetai;

import net.ldoin.shinnetai.buffered.buf.smart.ReadOnlySmartByteBuf;
import net.ldoin.shinnetai.buffered.buf.smart.WriteOnlySmartByteBuf;
import net.ldoin.shinnetai.exception.ShinnetaiException;
import net.ldoin.shinnetai.exception.ShinnetaiExceptions;
import net.ldoin.shinnetai.log.ShinnetaiLog;
import net.ldoin.shinnetai.packet.AbstractPacket;
import net.ldoin.shinnetai.packet.registry.PacketRegistry;
import net.ldoin.shinnetai.packet.response.PacketResponseOptions;
import net.ldoin.shinnetai.packet.response.PacketResponseWaiter;
import net.ldoin.shinnetai.packet.side.PacketSide;
import net.ldoin.shinnetai.statistic.ShinnetaiStatistic;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.Socket;
import java.net.SocketException;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class ShinnetaiIOWorker<S extends ShinnetaiStatistic> {

    private final Logger logger;
    private final PacketRegistry registry;
    private InputStream input;
    private OutputStream out;
    protected final BlockingQueue<AbstractPacket<?, ?>> outQueue;
    protected final PacketResponseWaiter responseWaiter;

    private final S statistic;

    protected Thread readerThread;
    protected Thread writerThread;
    private volatile boolean running;

    protected ShinnetaiIOWorker(Logger logger, PacketRegistry registry, InputStream input, OutputStream out, S statistic) {
        ShinnetaiLog.init();

        this.logger = logger;
        this.registry = registry;
        this.input = input;
        this.out = out;
        this.statistic = statistic;
        this.outQueue = new LinkedBlockingQueue<>();
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

    public boolean isRunning() {
        return running;
    }

    public void addPacket(AbstractPacket<?, ?> packet) {
        outQueue.add(packet);
    }

    @SuppressWarnings("unchecked")
    public void sendPacket(AbstractPacket<?, ?> packet, PacketResponseOptions options, WriteOnlySmartByteBuf buf) throws IOException {
        if (packet == null || out == null) {
            return;
        }

        applyHandler(packet);

        int id = registry.getId((Class<? extends AbstractPacket<?, ?>>) packet.getClass());
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
            try {
                statistic.send(bytes);
            } catch (Exception exception) {
                logger.log(Level.SEVERE, "Error on write sent bytes statistics", exception);
            }

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
        int waiterId = responseWaiter.addWaiter(false, null, timeoutMillis);
        sendPacket(packet, PacketResponseOptions.waitResponse(waiterId));
        return (P) responseWaiter.waitForResponse(waiterId, timeoutMillis);
    }

    public CompletableFuture<Optional<AbstractPacket<?, ?>>> sendAsyncWithResponse(AbstractPacket<?, ?> packet, TimeUnit timeUnit, int timeout) throws ArrayIndexOutOfBoundsException {
        return sendAsyncWithResponse(packet, timeUnit.toMillis(timeout));
    }

    public CompletableFuture<Optional<AbstractPacket<?, ?>>> sendAsyncWithResponse(AbstractPacket<?, ?> packet, long timeoutMillis) throws ArrayIndexOutOfBoundsException {
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
            logger.log(Level.SEVERE, "", exception);
            sendException(ShinnetaiExceptions.FAILED_READ_PACKET, id);
            return;
        }

        try {
            packet.handle(getSide());
        } catch (Exception exception) {
            logger.log(Level.SEVERE, "", exception);
            sendException(ShinnetaiExceptions.FAILED_HANDLE_PACKET, id);
            return;
        }

        if (responseOptions.isResponse()) {
            int response = responseOptions.getResponseId();
            try {
                responseWaiter.handleResponse(response, packet);
            } catch (Exception exception) {
                logger.log(Level.SEVERE, "", exception);
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
                    logger.log(Level.SEVERE, "Cannot find response for " + id);
                    sendException(ShinnetaiExceptions.FAILED_FIND_RESPONSE, id);
                }
            } catch (Exception exception) {
                logger.log(Level.SEVERE, "", exception);
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

    public synchronized void start() {
        if (input == null || out == null) {
            throw new UnsupportedOperationException("Cannot start IO worker without IO streams");
        }

        if (readerThread != null || writerThread != null) {
            close();
        }

        running = true;
        startReader();
        startWriter();
    }

    private void startReader() {
        readerThread = Thread.ofVirtual().start(() -> {
            try {
                byte[] readBuffer = new byte[1024];
                while (running) {
                    int bytesRead = input.read(readBuffer);
                    if (bytesRead == -1) {
                        break;
                    }

                    if (bytesRead > 0) {
                        byte[] actualData = new byte[bytesRead];
                        System.arraycopy(readBuffer, 0, actualData, 0, bytesRead);

                        try {
                            readPacket(actualData);
                        } catch (InvocationTargetException | IllegalAccessException |
                                 InstantiationException | NoSuchMethodException e) {
                            logger.log(Level.SEVERE, "Error handling packet", e);
                        }
                    }
                }
            } catch (SocketException ignored) {
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Reader I/O error", e);
            } finally {
                if (running) {
                    close();
                }
            }
        });
    }

    private void startWriter() {
        writerThread = Thread.ofVirtual().start(() -> {
            try {
                while (running) {
                    AbstractPacket<?, ?> packet = outQueue.take();
                    sendPacket(packet);
                }
            } catch (InterruptedException ignored) {
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Writer I/O error", e);
            } finally {
                if (running) {
                    close();
                }
            }
        });
    }

    public synchronized void close() {
        close(false);
    }

    protected synchronized void close(boolean clearIO) {
        running = false;

        if (readerThread != null) {
            readerThread.interrupt();
            readerThread = null;
        }

        if (writerThread != null) {
            writerThread.interrupt();
            writerThread = null;
        }

        if (clearIO) {
            input = null;
            out = null;
        }
    }

    protected void attachIOStreams(Socket socket) throws IOException {
        attachIOStreams(socket.getInputStream(), socket.getOutputStream());
    }

    protected void attachIOStreams(InputStream input, OutputStream out) {
        if (running) {
            throw new UnsupportedOperationException("Cannot attach IO streams while worker running");
        }

        this.input = input;
        this.out = out;
    }
}