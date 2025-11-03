package net.ldoin.shinnetai.worker;

import net.ldoin.shinnetai.buffered.buf.ByteBuf;
import net.ldoin.shinnetai.buffered.buf.smart.ReadOnlySmartByteBuf;
import net.ldoin.shinnetai.buffered.buf.smart.WriteOnlySmartByteBuf;
import net.ldoin.shinnetai.exception.ShinnetaiException;
import net.ldoin.shinnetai.exception.ShinnetaiExceptions;
import net.ldoin.shinnetai.log.ShinnetaiLog;
import net.ldoin.shinnetai.packet.AbstractPacket;
import net.ldoin.shinnetai.packet.PacketOptions;
import net.ldoin.shinnetai.packet.ReadPacket;
import net.ldoin.shinnetai.packet.WrappedPacket;
import net.ldoin.shinnetai.packet.common.EmptyResponsePacket;
import net.ldoin.shinnetai.packet.registry.PacketRegistry;
import net.ldoin.shinnetai.packet.response.PacketResponseOptions;
import net.ldoin.shinnetai.packet.response.PacketResponseWaiter;
import net.ldoin.shinnetai.packet.side.PacketSide;
import net.ldoin.shinnetai.statistic.ShinnetaiStatistic;
import net.ldoin.shinnetai.worker.pipeline.ShinnetaiPipeline;
import net.ldoin.shinnetai.worker.pipeline.ShinnetaiPipelineHandleType;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class ShinnetaiWorkerContext<S extends ShinnetaiStatistic> {

    protected Logger logger;
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

    public ShinnetaiPipeline getPipeline() {
        return null;
    }

    public void withPipeline(ShinnetaiPipeline pipeline) {
    }

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

    public void sendPacket(WrappedPacket packet, WriteOnlySmartByteBuf buf) throws IOException {
        sendPacket(WrappedPacket.builder(packet).buffer(buf).build());
    }

    public void sendPacket(WrappedPacket packet) throws IOException {
        if (out == null) {
            logger.log(Level.SEVERE, "OutputStream not found", new NullPointerException());
            return;
        }

        if (packet == null) {
            return;
        }

        WriteOnlySmartByteBuf buf = packet.getBuffer();
        if (buf == null) {
            buf = WriteOnlySmartByteBuf.empty();
        }

        AbstractPacket<?, ?> abstractPacket = packet.getPacket();
        attachWorker(abstractPacket);

        int id = registry.getId(abstractPacket.getClass());

        ShinnetaiPipeline pipeline = getPipeline();
        if (pipeline != null) {
            try {
                abstractPacket = pipeline.handle(ShinnetaiPipelineHandleType.BEFORE_SEND, abstractPacket);
            } catch (Exception exception) {
                logger.log(Level.SEVERE, "Failed send packet in pipeline", exception);
                sendException(ShinnetaiExceptions.FAILED_SEND_PACKET, id);
                return;
            }
        }

        if (abstractPacket == null) {
            return;
        }

        try {
            buf.writeVarInt(id);
            packet.write(buf);
            abstractPacket.write(buf);
        } catch (Exception exception) {
            logger.log(Level.SEVERE, "Failed to write packet", exception);
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

        if (pipeline != null) {
            try {
                pipeline.handle(ShinnetaiPipelineHandleType.AFTER_SEND, abstractPacket);
            } catch (Exception exception) {
                logger.log(Level.SEVERE, "Failed send packet in pipeline", exception);
            }
        }
    }

    public void sendPacket(AbstractPacket<?, ?> packet) throws IOException {
        sendPacket(WrappedPacket.of(packet));
    }

    public void sendPacket(AbstractPacket<?, ?> packet, PacketResponseOptions responseOptions) throws IOException {
        sendPacket(WrappedPacket.of(packet, responseOptions));
    }

    public AbstractPacket<?, ?> sendAndWaitForResponse(WrappedPacket packet, TimeUnit timeUnit, int timeout) throws IOException, TimeoutException, InterruptedException, ArrayIndexOutOfBoundsException {
        return sendAndWaitForResponse(packet, timeUnit.toMillis(timeout));
    }

    public AbstractPacket<?, ?> sendAndWaitForResponse(WrappedPacket packet, long timeoutMillis) throws IOException, TimeoutException, InterruptedException, ArrayIndexOutOfBoundsException {
        if (responseWaiter == null) {
            throw new UnsupportedOperationException();
        }

        int waiterId = responseWaiter.addWaiter(false, null, timeoutMillis);
        sendPacket(WrappedPacket.builder(packet)
                .responseOptions(PacketResponseOptions.waitResponse(waiterId))
                .build());
        return responseWaiter.waitForResponse(waiterId, timeoutMillis);
    }

    public CompletableFuture<Optional<AbstractPacket<?, ?>>> sendAsyncWithResponse(WrappedPacket packet, TimeUnit timeUnit, int timeout) throws ArrayIndexOutOfBoundsException {
        return sendAsyncWithResponse(packet, timeUnit.toMillis(timeout));
    }

    public CompletableFuture<Optional<AbstractPacket<?, ?>>> sendAsyncWithResponse(WrappedPacket packet, long timeoutMillis) throws ArrayIndexOutOfBoundsException {
        if (responseWaiter == null) {
            throw new UnsupportedOperationException();
        }

        CompletableFuture<Optional<AbstractPacket<?, ?>>> future = new CompletableFuture<>();
        int waiterId = responseWaiter.addWaiter(true, future, timeoutMillis);
        try {
            sendPacket(WrappedPacket.builder(packet)
                    .responseOptions(PacketResponseOptions.waitResponse(waiterId))
                    .build());
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to send packet with waiterId: " + waiterId, e);
            responseWaiter.handleResponse(waiterId, ShinnetaiExceptions.FAILED_SEND_RESPONSE.toPacket(waiterId));
        }

        return future;
    }

    public AbstractPacket<?, ?> sendAndWaitForResponse(AbstractPacket<?, ?> packet, TimeUnit timeUnit, int timeout) throws IOException, TimeoutException, InterruptedException, ArrayIndexOutOfBoundsException {
        return sendAndWaitForResponse(packet, timeUnit.toMillis(timeout));
    }

    public AbstractPacket<?, ?> sendAndWaitForResponse(AbstractPacket<?, ?> packet, long timeoutMillis) throws IOException, TimeoutException, InterruptedException, ArrayIndexOutOfBoundsException {
        return sendAndWaitForResponse(WrappedPacket.of(packet), timeoutMillis);
    }

    public CompletableFuture<Optional<AbstractPacket<?, ?>>> sendAsyncWithResponse(AbstractPacket<?, ?> packet, TimeUnit timeUnit, int timeout) throws ArrayIndexOutOfBoundsException {
        return sendAsyncWithResponse(packet, timeUnit.toMillis(timeout));
    }

    public CompletableFuture<Optional<AbstractPacket<?, ?>>> sendAsyncWithResponse(AbstractPacket<?, ?> packet, long timeoutMillis) throws ArrayIndexOutOfBoundsException {
        return sendAsyncWithResponse(WrappedPacket.of(packet), timeoutMillis);
    }

    public ReadPacket readPacket(byte[] bytes) throws IOException {
        if (statistic != null) {
            statistic.receive(bytes);
        }

        ReadOnlySmartByteBuf buf = ReadOnlySmartByteBuf.of(bytes);
        int id = buf.readVarInt();
        AbstractPacket<?, ?> packet = registry.createPacket(id);
        WrappedPacket wrapped = WrappedPacket.of(packet, buf);

        attachWorker(packet);

        try {
            packet.read(buf);
        } catch (Exception exception) {
            logger.log(Level.SEVERE, "Failed read packet", exception);
            sendException(ShinnetaiExceptions.FAILED_READ_PACKET, id);
            return null;
        }

        return new ReadPacket(id, wrapped);
    }

    public void handlePacket(ReadPacket readPacket) throws IOException {
        int id = readPacket.id();
        WrappedPacket wrapped = readPacket.wrapped();
        AbstractPacket<?, ?> packet = wrapped.getPacket();
        PacketResponseOptions responseOptions = wrapped.getResponseOptions();

        ShinnetaiPipeline pipeline = getPipeline();
        if (pipeline != null) {
            try {
                packet = pipeline.handle(ShinnetaiPipelineHandleType.BEFORE_HANDLE, packet);
            } catch (Exception exception) {
                logger.log(Level.SEVERE, "Failed handle packet in pipeline", exception);
                sendException(ShinnetaiExceptions.FAILED_HANDLE_PACKET, id);
                return;
            }
        }

        if (packet == null) {
            return;
        }

        try {
            if (responseOptions == null || !responseOptions.isResponse()) {
                packet.handle(getSide());
            }
        } catch (Exception exception) {
            logger.log(Level.SEVERE, "Failed handle packet", exception);
            sendException(ShinnetaiExceptions.FAILED_HANDLE_PACKET, id);
            return;
        }

        if (pipeline != null) {
            try {
                pipeline.handle(ShinnetaiPipelineHandleType.AFTER_HANDLE, packet);
            } catch (Exception exception) {
                logger.log(Level.SEVERE, "Failed handle packet in pipeline", exception);
            }
        }

        if (responseOptions == null) {
            return;
        }

        if (wrapped.getOptionValue(PacketOptions.IS_RESPONSE) && responseWaiter != null) {
            int response = responseOptions.getResponseId();
            try {
                responseWaiter.handleResponse(response, packet);
            } catch (Exception exception) {
                logger.log(Level.SEVERE, "", exception);
                sendException(ShinnetaiExceptions.FAILED_HANDLE_RESPONSE, id, response);
            }
        }

        if (responseWaiter != null) {
            int waitResponse = responseOptions.getWaitResponse();
            PacketResponseOptions options = PacketResponseOptions.response(waitResponse);
            if (wrapped.getOptionValue(PacketOptions.WAIT_RESPONSE)) {
                try {
                    AbstractPacket<?, ?> response = packet.response();
                    if (response != null) {
                        sendPacket(WrappedPacket.of(response, options));
                    } else {
                        logger.log(Level.SEVERE, "Cannot find response for " + id + ", waiter " + waitResponse);
                        sendException(ShinnetaiExceptions.FAILED_FIND_RESPONSE, id, waitResponse);
                    }
                } catch (Exception exception) {
                    logger.log(Level.SEVERE, "Failed send response", exception);
                    sendException(ShinnetaiExceptions.FAILED_SEND_RESPONSE, id, waitResponse);
                }
            } else if (wrapped.getOptionValue(PacketOptions.REQUIRE_RESPONSE)) {
                sendPacket(WrappedPacket.builder(new EmptyResponsePacket())
                        .withOption(PacketOptions.IS_RESPONSE)
                        .responseOptions(options)
                        .build());
            }
        }
    }

    public void sendException(ShinnetaiException exception, Object... objects) throws IOException {
        sendPacket(WrappedPacket.of(exception.toPacket(objects)));
    }

    public void sendException(ShinnetaiException exception) throws IOException {
        sendPacket(WrappedPacket.of(exception.toPacket()));
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