package net.ldoin.shinnetai.buffered;

import net.ldoin.shinnetai.buffered.buf.ByteBuf;
import net.ldoin.shinnetai.buffered.buf.smart.SmartByteBuf;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BufferedSerializerTest {

    private final BufferedSerializer ser = BufferedSerializer.get();

    @Test
    void serialize_deserialize_Byte() {
        SmartByteBuf buf = ser.serialize((byte) 77);
        SmartByteBuf readBuf = SmartByteBuf.of(buf.toBytes());
        assertEquals((byte) 77, (byte) ser.deserialize(1, readBuf));
    }

    @Test
    void serialize_deserialize_Short() {
        SmartByteBuf buf = ser.serialize((short) 1000);
        SmartByteBuf readBuf = SmartByteBuf.of(buf.toBytes());
        assertEquals((short) 1000, (short) ser.deserialize(2, readBuf));
    }

    @Test
    void serialize_deserialize_Integer() {
        SmartByteBuf buf = ser.serialize(123456);
        SmartByteBuf readBuf = SmartByteBuf.of(buf.toBytes());
        assertEquals(123456, (int) ser.deserialize(3, readBuf));
    }

    @Test
    void serialize_deserialize_Long() {
        SmartByteBuf buf = ser.serialize(Long.MIN_VALUE);
        SmartByteBuf readBuf = SmartByteBuf.of(buf.toBytes());
        assertEquals(Long.MIN_VALUE, (long) ser.deserialize(4, readBuf));
    }

    @Test
    void serialize_deserialize_Float() {
        SmartByteBuf buf = ser.serialize(2.71828f);
        SmartByteBuf readBuf = SmartByteBuf.of(buf.toBytes());
        assertEquals(2.71828f, (float) ser.deserialize(5, readBuf), 0.0001f);
    }

    @Test
    void serialize_deserialize_Double() {
        SmartByteBuf buf = ser.serialize(Math.E);
        SmartByteBuf readBuf = SmartByteBuf.of(buf.toBytes());
        assertEquals(Math.E, (double) ser.deserialize(6, readBuf), 0.000001);
    }

    @Test
    void serialize_deserialize_Boolean_true() {
        SmartByteBuf buf = ser.serialize(Boolean.TRUE);
        SmartByteBuf readBuf = SmartByteBuf.of(buf.toBytes());
        assertTrue((boolean) ser.deserialize(7, readBuf));
    }

    @Test
    void serialize_deserialize_Boolean_false() {
        SmartByteBuf buf = ser.serialize(Boolean.FALSE);
        SmartByteBuf readBuf = SmartByteBuf.of(buf.toBytes());
        assertFalse((boolean) ser.deserialize(7, readBuf));
    }

    @Test
    void serialize_deserialize_BooleanArray() {
        boolean[] arr = {true, false, true, false, true, false, true};
        SmartByteBuf buf = ser.serialize(arr);
        SmartByteBuf readBuf = SmartByteBuf.of(buf.toBytes());
        assertArrayEquals(arr, (boolean[]) ser.deserialize(8, readBuf));
    }

    @Test
    void serialize_deserialize_String() {
        SmartByteBuf buf = ser.serialize("shinnetai");
        SmartByteBuf readBuf = SmartByteBuf.of(buf.toBytes());
        assertEquals("shinnetai", ser.deserialize(9, readBuf));
    }

    @Test
    void serialize_deserialize_UUID() {
        UUID id = UUID.randomUUID();
        SmartByteBuf buf = ser.serialize(id);
        SmartByteBuf readBuf = SmartByteBuf.of(buf.toBytes());
        assertEquals(id, ser.deserialize(10, readBuf));
    }

    @Test
    void serialize_deserialize_ByteBuf() {
        ByteBuf inner = ByteBuf.empty();
        inner.writeInt(99);
        SmartByteBuf buf = ser.serialize(inner);
        SmartByteBuf readBuf = SmartByteBuf.of(buf.toBytes());
        ByteBuf result = ser.deserialize(11, readBuf);
        assertEquals(99, result.readInt());
    }

    @Test
    void serialize_deserialize_SmartByteBuf() {
        SmartByteBuf inner = SmartByteBuf.empty();
        inner.writeVarInt(55);
        SmartByteBuf buf = ser.serialize(inner);
        SmartByteBuf readBuf = SmartByteBuf.of(buf.toBytes());
        SmartByteBuf result = ser.deserialize(12, readBuf);
        assertEquals(55, result.readVarInt());
    }

    @Test
    void serializeIncludeId_deserializeIncludeId_Integer() {
        SmartByteBuf buf = ser.serializeIncludeId(999);
        SmartByteBuf readBuf = SmartByteBuf.of(buf.toBytes());
        assertEquals(999, (int) ser.deserializeIncludeId(readBuf));
    }

    @Test
    void serializeIncludeId_deserializeIncludeId_String() {
        SmartByteBuf buf = ser.serializeIncludeId("hello");
        SmartByteBuf readBuf = SmartByteBuf.of(buf.toBytes());
        assertEquals("hello", (String) ser.deserializeIncludeId(readBuf));
    }

    @Test
    void getId_Integer() {
        assertEquals(3, ser.getId(Integer.class));
    }

    @Test
    void getId_String() {
        assertEquals(9, ser.getId(String.class));
    }

    @Test
    void getId_UUID() {
        assertEquals(10, ser.getId(UUID.class));
    }

    @Test
    void getId_unregisteredClass_throws() {
        assertThrows(IllegalArgumentException.class, () -> ser.getId(Thread.class));
    }

    @Test
    void serialize_unregisteredType_throws() {
        assertThrows(Exception.class, () -> ser.serialize(new Object()));
    }
}
