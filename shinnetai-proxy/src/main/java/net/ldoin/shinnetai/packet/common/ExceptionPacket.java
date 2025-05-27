package net.ldoin.shinnetai.packet.common;

import net.ldoin.shinnetai.ShinnetaiWorkerContext;
import net.ldoin.shinnetai.buffered.BufferedSerializer;
import net.ldoin.shinnetai.buffered.buf.smart.ReadOnlySmartByteBuf;
import net.ldoin.shinnetai.buffered.buf.smart.WriteOnlySmartByteBuf;
import net.ldoin.shinnetai.client.ShinnetaiClient;
import net.ldoin.shinnetai.exception.ShinnetaiException;
import net.ldoin.shinnetai.exception.registry.ExceptionRegistry;
import net.ldoin.shinnetai.packet.AbstractPacket;
import net.ldoin.shinnetai.packet.registry.ShinnetaiPacket;

import java.util.logging.Level;

@ShinnetaiPacket(id = -1)
public class ExceptionPacket extends AbstractPacket<ShinnetaiClient, ShinnetaiWorkerContext<?>> {

    private static final Object[] EMPTY_ARGUMENTS = new Object[0];

    private ShinnetaiException exception;
    private Object[] objects;

    public ExceptionPacket() {
    }

    public ExceptionPacket(int id) {
        this(ExceptionRegistry.getException(id));
    }

    public ExceptionPacket(ShinnetaiException exception) {
        this(exception, EMPTY_ARGUMENTS);
    }

    public ExceptionPacket(int id, Object... objects) {
        this(ExceptionRegistry.getException(id), objects);
    }

    public ExceptionPacket(ShinnetaiException exception, Object... objects) {
        this.exception = exception;
        this.objects = objects;
    }

    @Override
    public void handleClient() {
        getClientWorker().getLogger().log(Level.WARNING, "Received exception: " + ExceptionRegistry.getMessage(exception, objects));
        exception.handleClient(getClientWorker());
    }

    @Override
    public void handleServer() {
        getServerWorker().getLogger().log(Level.WARNING, "Received exception: " + ExceptionRegistry.getMessage(exception, objects));
        exception.handleServer(getCurrentContext());
    }

    @Override
    public void read(ReadOnlySmartByteBuf buf) {
        exception = ExceptionRegistry.getException(buf.readVarInt());

        if (!buf.readBoolean()) {
            objects = EMPTY_ARGUMENTS;
            return;
        }

        int length = buf.readVarInt();
        objects = new Object[length];
        for (int i = 0; i < length; i++) {
            objects[i] = BufferedSerializer.get().deserializeIncludeId(buf);
        }
    }

    @Override
    public void write(WriteOnlySmartByteBuf buf) {
        buf.writeVarInt(exception.getId());

        int length = objects.length;
        buf.writeBoolean(length > 0);
        if (length == 0) {
            return;
        }

        buf.writeVarInt(length);
        for (int i = 0; i < length; i++) {
            BufferedSerializer.get().serializeIncludeId(objects[i], buf);
        }
    }
}