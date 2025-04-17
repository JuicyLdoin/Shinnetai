package net.aquarealm.shinnetai.packet.common;

import net.aquarealm.shinnetai.buffered.BufferedSerializer;
import net.aquarealm.shinnetai.buffered.buf.smart.ReadOnlySmartByteBuf;
import net.aquarealm.shinnetai.buffered.buf.smart.WriteOnlySmartByteBuf;
import net.aquarealm.shinnetai.client.ShinnetaiClient;
import net.aquarealm.shinnetai.exception.ShinnetaiException;
import net.aquarealm.shinnetai.exception.registry.ExceptionRegistry;
import net.aquarealm.shinnetai.packet.AbstractPacket;
import net.aquarealm.shinnetai.packet.registry.ShinnetaiPacket;
import net.aquarealm.shinnetai.server.connection.ShinnetaiConnection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@ShinnetaiPacket(id = 0)
public class ExceptionPacket extends AbstractPacket<ShinnetaiClient, ShinnetaiConnection<?>> {

    private static final Logger LOGGER = LogManager.getLogger("Exceptions");
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
    public void handle() {
        LOGGER.warn("EXCEPTION: " + ExceptionRegistry.getMessage(exception, objects));
    }

    @Override
    public void read(ReadOnlySmartByteBuf buf) {
        exception = ExceptionRegistry.getException(buf.readVarInt());

        if (buf.isEmpty()) {
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
        if (length == 0) {
            return;
        }

        buf.writeVarInt(length);
        for (int i = 0; i < length; i++) {
            BufferedSerializer.get().serializeIncludeId(objects[i], buf);
        }
    }
}