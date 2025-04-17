package net.aquarealm.shinnetai.buffered.map;

import net.aquarealm.shinnetai.buffered.Buffered;
import net.aquarealm.shinnetai.buffered.BufferedSerializer;
import net.aquarealm.shinnetai.buffered.buf.ByteBuf;
import net.aquarealm.shinnetai.buffered.buf.smart.SmartByteBuf;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ByteMap extends HashMap<String, Object> implements Buffered {

    public static ByteMap empty() {
        return new ByteMap();
    }

    public static ByteMap of(Map<String, Object> map) {
        return new ByteMap(map);
    }

    public static ByteMap of(byte[] bytes) {
        return new ByteMap(bytes);
    }

    protected ByteMap() {
    }

    protected ByteMap(Map<String, Object> map) {
        super(map);
    }

    protected ByteMap(byte[] bytes) {
        SmartByteBuf buf = SmartByteBuf.of(bytes);
        while (buf.remain() > 0) {
            put(buf.readString(), BufferedSerializer.get().deserialize(buf.readVarInt(), SmartByteBuf.of(buf.readBytes())));
        }
    }

    @Override
    public byte[] toBytes() {
        SmartByteBuf buf = SmartByteBuf.empty();
        for (Map.Entry<String, Object> entry : entrySet()) {
            buf.writeString(entry.getKey());

            Object object = entry.getValue();
            buf.writeVarInt(BufferedSerializer.get().getId(object.getClass()));
            buf.writeBytes(BufferedSerializer.get().serializeIncludeId(object).toBytes());
        }

        return buf.toBytes();
    }

    public ByteMap put(String key, Object value) {
        super.put(key, value);
        return this;
    }

    public String getString(String key) {
        return (String) get(key);
    }

    public byte getByte(String key) {
        return ((Number) get(key)).byteValue();
    }

    public short getShort(String key) {
        return ((Number) get(key)).shortValue();
    }

    public float getFloat(String key) {
        return ((Number) get(key)).floatValue();
    }

    public double getDouble(String key) {
        return ((Number) get(key)).doubleValue();
    }

    public int getInt(String key) {
        return ((Number) get(key)).intValue();
    }

    public long getLong(String key) {
        return ((Number) get(key)).longValue();
    }

    public boolean getBoolean(String key) {
        return (Boolean) get(key);
    }

    public boolean[] getBooleanArray(String key) {
        return (boolean[]) get(key);
    }

    public UUID getUUID(String key) {
        return (UUID) get(key);
    }

    public ByteBuf getBuf(String key) {
        return (ByteBuf) get(key);
    }

    public SmartByteBuf getSmartBuf(String key) {
        return (SmartByteBuf) get(key);
    }

    public ByteMap getMap(String key) {
        return (ByteMap) get(key);
    }

    public String getString(String key, String def) {
        Object o = get(key);
        return o == null ? def : (String) o;
    }

    public byte getByte(String key, byte def) {
        Object o = get(key);
        return o == null ? def : ((Number) o).byteValue();
    }

    public short getShort(String key, short def) {
        Object o = get(key);
        return o == null ? def : ((Number) o).shortValue();
    }

    public float getFloat(String key, float def) {
        Object o = get(key);
        return o == null ? def : ((Number) o).floatValue();
    }

    public double getDouble(String key, double def) {
        Object o = get(key);
        return o == null ? def : ((Number) o).doubleValue();
    }

    public int getInt(String key, int def) {
        Object o = get(key);
        return o == null ? def : ((Number) o).intValue();
    }

    public long getLong(String key, long def) {
        Object o = get(key);
        return o == null ? def : ((Number) o).longValue();
    }

    public boolean getBoolean(String key, boolean def) {
        Object o = get(key);
        return o == null ? def : (Boolean) o;
    }

    public boolean[] getBooleanArray(String key, boolean[] def) {
        Object o = get(key);
        return o == null ? def : (boolean[]) o;
    }

    public ByteBuf getBuf(String key, ByteBuf def) {
        Object o = get(key);
        return o == null ? def : (ByteBuf) o;
    }

    public SmartByteBuf getSmartBuf(String key, SmartByteBuf def) {
        Object o = get(key);
        return o == null ? def : (SmartByteBuf) o;
    }

    public ByteMap getMap(String key, ByteMap def) {
        Object o = get(key);
        return o == null ? def : (ByteMap) o;
    }
}