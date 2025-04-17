package net.ldoin.shinnetai.buffered;

import net.ldoin.shinnetai.buffered.buf.ByteBuf;
import net.ldoin.shinnetai.buffered.buf.smart.SmartByteBuf;
import net.ldoin.shinnetai.buffered.map.ByteMap;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class BufferedSerializer {

    private static final BufferedSerializer SERIALIZER;

    static {
        SERIALIZER = new BufferedSerializer();

        register(1, Byte.class, SmartByteBuf::readByte, (o, buf) -> buf.writeByte(o));
        register(2, Short.class, SmartByteBuf::readShort, (o, buf) -> buf.writeShort(o));
        register(3, Integer.class, SmartByteBuf::readVarInt, (o, buf) -> buf.writeVarInt(o));
        register(4, Long.class, SmartByteBuf::readVarLong, (o, buf) -> buf.writeVarLong(o));
        register(5, Float.class, SmartByteBuf::readFloat, (o, buf) -> buf.writeFloat(o));
        register(6, Double.class, SmartByteBuf::readDouble, (o, buf) -> buf.writeDouble(o));
        register(7, Boolean.class, SmartByteBuf::readBoolean, (o, buf) -> buf.writeBoolean(o));
        register(8, boolean[].class, SmartByteBuf::readBooleanArray, (o, buf) -> buf.writeBooleanArray(o));
        register(9, String.class, SmartByteBuf::readString, (o, buf) -> buf.writeString(o));
        register(10, UUID.class, SmartByteBuf::readUUID, (o, buf) -> buf.writeUUID(o));
        register(11, ByteBuf.class, buf -> ByteBuf.of(buf.readBytes()), (o, buf) -> buf.writeBytes(o.toBytes()));
        register(12, SmartByteBuf.class, buf -> SmartByteBuf.of(buf.readBytes()), (o, buf) -> buf.writeBytes(o.toBytes()));
        register(13, ByteMap.class, buf -> ByteMap.of(buf.readBytes()), (o, buf) -> buf.writeBytes(o.toBytes()));
    }

    public static BufferedSerializer get() {
        return SERIALIZER;
    }

    @SuppressWarnings("unchecked")
    public static <T> void register(int id, Class<T> clazz, Function<SmartByteBuf, T> deserialize, BiConsumer<T, SmartByteBuf> serialize) {
        SERIALIZER.deserializeMap.put(id, (Function<SmartByteBuf, Object>) deserialize);
        SERIALIZER.serializeMap.put(clazz, (BiConsumer<Object, SmartByteBuf>) serialize);
        SERIALIZER.idMap.put(clazz, id);
    }

    private final Map<Integer, Function<SmartByteBuf, Object>> deserializeMap = new ConcurrentHashMap<>();
    private final Map<Class<?>, BiConsumer<Object, SmartByteBuf>> serializeMap = new ConcurrentHashMap<>();
    private final Map<Class<?>, Integer> idMap = new ConcurrentHashMap<>();

    public int getId(Class<?> clazz) {
        Integer id = idMap.get(clazz);
        if (id == null) {
            throw new IllegalArgumentException("Class " + clazz.getName() + " is not registered");
        }

        return id;
    }

    @SuppressWarnings("unchecked")
    public <O> O deserialize(int id, SmartByteBuf buf) {
        return (O) deserializeMap.get(id).apply(buf);
    }

    @SuppressWarnings("unchecked")
    public <O> O deserializeIncludeId(SmartByteBuf buf) {
        return (O) deserializeMap.get(buf.readVarInt()).apply(buf);
    }

    public <O> SmartByteBuf serialize(O object) {
        return serialize(object, SmartByteBuf.empty());
    }

    public <O> SmartByteBuf serialize(O object, SmartByteBuf buf) {
        serializeMap.get(object.getClass()).accept(object, buf);
        return buf;
    }

    public <O> SmartByteBuf serializeIncludeId(O object) {
        return serializeIncludeId(object, SmartByteBuf.empty());
    }

    public <O> SmartByteBuf serializeIncludeId(O object, SmartByteBuf buf) {
        buf.writeVarInt(getId(object.getClass()));
        serializeMap.get(object.getClass()).accept(object, buf);
        return buf;
    }
}