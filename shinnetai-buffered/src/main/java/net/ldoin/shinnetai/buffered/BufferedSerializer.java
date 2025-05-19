package net.ldoin.shinnetai.buffered;

import net.ldoin.shinnetai.buffered.buf.ByteBuf;
import net.ldoin.shinnetai.buffered.buf.smart.SmartByteBuf;
import net.ldoin.shinnetai.buffered.map.ByteMap;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class BufferedSerializer {

    private static final BufferedSerializer SERIALIZER;
    private static int ID = 0;

    static {
        SERIALIZER = new BufferedSerializer();

        register(Byte.class, SmartByteBuf::readByte, (o, buf) -> buf.writeByte(o));
        register(Short.class, SmartByteBuf::readShort, (o, buf) -> buf.writeShort(o));
        register(Integer.class, SmartByteBuf::readVarInt, (o, buf) -> buf.writeVarInt(o));
        register(Long.class, SmartByteBuf::readVarLong, (o, buf) -> buf.writeVarLong(o));
        register(Float.class, SmartByteBuf::readFloat, (o, buf) -> buf.writeFloat(o));
        register(Double.class, SmartByteBuf::readDouble, (o, buf) -> buf.writeDouble(o));
        register(Boolean.class, SmartByteBuf::readBoolean, (o, buf) -> buf.writeBoolean(o));
        register(boolean[].class, SmartByteBuf::readBooleanArray, (o, buf) -> buf.writeBooleanArray(o));
        register(String.class, SmartByteBuf::readString, (o, buf) -> buf.writeString(o));
        register(UUID.class, SmartByteBuf::readUUID, (o, buf) -> buf.writeUUID(o));
        register(ByteBuf.class, buf -> ByteBuf.of(buf.readBytes()), (o, buf) -> buf.writeBytes(o.toBytes()));
        register(SmartByteBuf.class, buf -> SmartByteBuf.of(buf.readBytes()), (o, buf) -> buf.writeBytes(o.toBytes()));
        register(ByteMap.class, buf -> ByteMap.of(buf.readBytes()), (o, buf) -> buf.writeBytes(o.toBytes()));
    }

    public static BufferedSerializer get() {
        return SERIALIZER;
    }

    @SuppressWarnings("unchecked")
    public static <T> void register(Class<T> clazz, Function<SmartByteBuf, T> deserialize, BiConsumer<T, SmartByteBuf> serialize) {
        ID++;

        SERIALIZER.deserializeMap.put(ID, (Function<SmartByteBuf, Object>) deserialize);
        SERIALIZER.serializeMap.put(clazz, (BiConsumer<Object, SmartByteBuf>) serialize);
        SERIALIZER.idMap.put(clazz, ID);
    }

    private final Map<Integer, Function<SmartByteBuf, Object>> deserializeMap = new HashMap<>();
    private final Map<Class<?>, BiConsumer<Object, SmartByteBuf>> serializeMap = new HashMap<>();
    private final Map<Class<?>, Integer> idMap = new HashMap<>();

    public int getId(Class<?> clazz) {
        return idMap.get(clazz);
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