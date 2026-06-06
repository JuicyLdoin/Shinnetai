package net.ldoin.shinnetai.buffered;

import net.ldoin.shinnetai.buffered.buf.smart.SmartByteBuf;
import net.ldoin.shinnetai.buffered.exception.FrameTooLargeException;
import net.ldoin.shinnetai.buffered.exception.MalformedVarIntException;
import net.ldoin.shinnetai.buffered.exception.ProtocolEncodeException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class SmartByteBufTest {

    @Test
    void varInt_zero() {
        SmartByteBuf buf = SmartByteBuf.empty();
        buf.writeVarInt(0);
        assertEquals(0, buf.readVarInt());
    }

    @Test
    void varInt_smallPositive() {
        SmartByteBuf buf = SmartByteBuf.empty();
        buf.writeVarInt(127);
        assertEquals(127, buf.readVarInt());
    }

    @Test
    void varInt_twoByteValue() {
        SmartByteBuf buf = SmartByteBuf.empty();
        buf.writeVarInt(128);
        assertEquals(128, buf.readVarInt());
    }

    @Test
    void varInt_maxInt() {
        SmartByteBuf buf = SmartByteBuf.empty();
        buf.writeVarInt(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, buf.readVarInt());
    }

    @Test
    void varInt_negative() {
        SmartByteBuf buf = SmartByteBuf.empty();
        buf.writeVarInt(-1);
        assertEquals(-1, buf.readVarInt());
    }

    @Test
    void varInt_largeValues() {
        SmartByteBuf buf = SmartByteBuf.empty();
        int[] values = {0, 1, 127, 128, 255, 256, 16383, 16384, 2097151, 2097152};
        for (int v : values) {
            buf.writeVarInt(v);
        }
        for (int v : values) {
            assertEquals(v, buf.readVarInt());
        }
    }

    @Test
    void varLong_zero() {
        SmartByteBuf buf = SmartByteBuf.empty();
        buf.writeVarLong(0L);
        assertEquals(0L, buf.readVarLong());
    }

    @Test
    void varLong_maxLong() {
        SmartByteBuf buf = SmartByteBuf.empty();
        buf.writeVarLong(Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, buf.readVarLong());
    }

    @Test
    void varLong_negative() {
        SmartByteBuf buf = SmartByteBuf.empty();
        buf.writeVarLong(-1L);
        assertEquals(-1L, buf.readVarLong());
    }

    @Test
    void uuid_roundtrip() {
        SmartByteBuf buf = SmartByteBuf.empty();
        UUID id = UUID.randomUUID();
        buf.writeUUID(id);
        assertEquals(id, buf.readUUID());
    }

    @Test
    void uuid_nilUUID() {
        SmartByteBuf buf = SmartByteBuf.empty();
        UUID nil = new UUID(0L, 0L);
        buf.writeUUID(nil);
        assertEquals(nil, buf.readUUID());
    }

    @Test
    void string_empty() {
        SmartByteBuf buf = SmartByteBuf.empty();
        buf.writeString("");
        assertEquals("", buf.readString());
    }

    @Test
    void string_ascii() {
        SmartByteBuf buf = SmartByteBuf.empty();
        buf.writeString("Hello, World!");
        assertEquals("Hello, World!", buf.readString());
    }

    @Test
    void string_unicode() {
        SmartByteBuf buf = SmartByteBuf.empty();
        String value = "Привет мир";
        buf.writeString(value);
        assertEquals(value, buf.readString());
    }

    @Test
    void string_multipleStrings() {
        SmartByteBuf buf = SmartByteBuf.empty();
        buf.writeString("first");
        buf.writeString("second");
        buf.writeString("third");
        assertEquals("first", buf.readString());
        assertEquals("second", buf.readString());
        assertEquals("third", buf.readString());
    }

    @Test
    void booleanArray_allFalse() {
        SmartByteBuf buf = SmartByteBuf.empty();
        boolean[] arr = {false, false, false, false, false, false, false};
        buf.writeBooleanArray(arr);
        assertArrayEquals(arr, buf.readBooleanArray());
    }

    @Test
    void booleanArray_allTrue() {
        SmartByteBuf buf = SmartByteBuf.empty();
        boolean[] arr = {true, true, true, true, true, true, true};
        buf.writeBooleanArray(arr);
        assertArrayEquals(arr, buf.readBooleanArray());
    }

    @Test
    void booleanArray_mixed() {
        SmartByteBuf buf = SmartByteBuf.empty();
        boolean[] arr = {true, false, true, false, true, true, false};
        buf.writeBooleanArray(arr);
        assertArrayEquals(arr, buf.readBooleanArray());
    }

    @Test
    void booleanArray_singleElement() {
        SmartByteBuf buf = SmartByteBuf.empty();
        boolean[] arr = {true, false, false, false, false, false, false};
        buf.writeBooleanArray(arr);
        assertArrayEquals(arr, buf.readBooleanArray());
    }

    @Test
    void collection_writeRead() {
        SmartByteBuf buf = SmartByteBuf.empty();
        List<String> original = List.of("a", "b", "c");
        buf.writeCollection(original, SmartByteBuf::writeString);
        List<String> result = buf.readCollection(
                (Function<Integer, ArrayList<String>>) ArrayList::new,
                SmartByteBuf::readString);
        assertEquals(original, result);
    }

    @Test
    void collection_empty() {
        SmartByteBuf buf = SmartByteBuf.empty();
        List<String> original = List.of();
        buf.writeCollection(original, SmartByteBuf::writeString);
        List<String> result = buf.readCollection(
                (Function<Integer, ArrayList<String>>) ArrayList::new,
                SmartByteBuf::readString);
        assertTrue(result.isEmpty());
    }

    @Test
    void idsArray_roundtrip() {
        SmartByteBuf buf = SmartByteBuf.empty();
        int[] ids = {1, 2, 3, 5, 8};
        buf.writeIdsArray(ids);
        int[] result = buf.readIdsArray();
        assertArrayEquals(ids, result);
    }

    @Test
    void idsArray_emptyRoundtrip() {
        SmartByteBuf buf = SmartByteBuf.empty();
        buf.writeIdsArray(new int[0], true);
        assertArrayEquals(new int[0], buf.readIdsArray());
    }

    @Test
    void idsArray_sparseSpanRejected() {
        SmartByteBuf buf = SmartByteBuf.empty();
        assertThrows(ProtocolEncodeException.class, () -> buf.writeIdsArray(new int[]{0, SmartByteBuf.MAX_IDS_ARRAY_SPAN + 10}));
    }

    @Test
    void malformedVarInt_hasProtocolException() {
        SmartByteBuf buf = SmartByteBuf.of(new byte[]{(byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80});
        assertThrows(MalformedVarIntException.class, buf::readVarInt);
    }

    @Test
    void declaredByteArrayTooLargeRejectedBeforeAllocation() {
        SmartByteBuf buf = SmartByteBuf.empty();
        buf.writeVarInt(SmartByteBuf.MAX_ARRAY_BYTES + 1);
        assertThrows(FrameTooLargeException.class, buf::readBytes);
    }

    @Test
    void writeBytes_includesLength() {
        SmartByteBuf buf = SmartByteBuf.empty();
        byte[] data = {10, 20, 30};
        buf.writeBytes(data);
        assertArrayEquals(data, buf.readBytes());
    }

    @Test
    void toBytes_of_roundtrip() {
        SmartByteBuf original = SmartByteBuf.empty();
        original.writeVarInt(42);
        original.writeString("test");
        original.writeBoolean(true);

        SmartByteBuf restored = SmartByteBuf.of(original.toBytes());
        assertEquals(42, restored.readVarInt());
        assertEquals("test", restored.readString());
        assertTrue(restored.readBoolean());
    }
}
