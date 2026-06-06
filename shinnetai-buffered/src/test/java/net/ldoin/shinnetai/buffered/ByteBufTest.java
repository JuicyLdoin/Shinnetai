package net.ldoin.shinnetai.buffered;

import net.ldoin.shinnetai.buffered.buf.ByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ByteBufTest {

    @Test
    void writeByte_readByte() {
        ByteBuf buf = ByteBuf.empty();
        buf.writeByte((byte) 42);
        assertEquals((byte) 42, buf.readByte());
    }

    @Test
    void writeByte_negative() {
        ByteBuf buf = ByteBuf.empty();
        buf.writeByte((byte) -1);
        assertEquals((byte) -1, buf.readByte());
    }

    @Test
    void writeShort_readShort() {
        ByteBuf buf = ByteBuf.empty();
        buf.writeShort(1234);
        assertEquals((short) 1234, buf.readShort());
    }

    @Test
    void writeShortLE_readShortLE() {
        ByteBuf buf = ByteBuf.empty();
        buf.writeShortLE(5678);
        assertEquals((short) 5678, buf.readShortLE());
    }

    @Test
    void writeInt_readInt() {
        ByteBuf buf = ByteBuf.empty();
        buf.writeInt(0xDEADBEEF);
        assertEquals(0xDEADBEEF, buf.readInt());
    }

    @Test
    void writeIntLE_readIntLE() {
        ByteBuf buf = ByteBuf.empty();
        buf.writeIntLE(12345678);
        assertEquals(12345678, buf.readIntLE());
    }

    @Test
    void writeLong_readLong() {
        ByteBuf buf = ByteBuf.empty();
        buf.writeLong(0xCAFEBABEDEADBEEFL);
        assertEquals(0xCAFEBABEDEADBEEFL, buf.readLong());
    }

    @Test
    void writeLongLE_readLongLE() {
        ByteBuf buf = ByteBuf.empty();
        buf.writeLongLE(Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, buf.readLongLE());
    }

    @Test
    void writeFloat_readFloat() {
        ByteBuf buf = ByteBuf.empty();
        buf.writeFloat(3.14f);
        assertEquals(3.14f, buf.readFloat(), 0.0001f);
    }

    @Test
    void writeDouble_readDouble() {
        ByteBuf buf = ByteBuf.empty();
        buf.writeDouble(Math.PI);
        assertEquals(Math.PI, buf.readDouble(), 0.000001);
    }

    @Test
    void writeBoolean_true() {
        ByteBuf buf = ByteBuf.empty();
        buf.writeBoolean(true);
        assertTrue(buf.readBoolean());
    }

    @Test
    void writeBoolean_false() {
        ByteBuf buf = ByteBuf.empty();
        buf.writeBoolean(false);
        assertFalse(buf.readBoolean());
    }

    @Test
    void writeBytes_readBytes() {
        ByteBuf buf = ByteBuf.empty();
        byte[] data = {1, 2, 3, 4, 5};
        buf.writeBytes(data);
        assertArrayEquals(data, buf.readBytes(5));
    }

    @Test
    void writeString_readString() {
        ByteBuf buf = ByteBuf.empty();
        buf.writeString("hello");
        assertEquals("hello", buf.readString());
    }

    @Test
    void writeChar_readChar() {
        ByteBuf buf = ByteBuf.empty();
        buf.writeChar('Z');
        assertEquals('Z', buf.readChar());
    }

    @Test
    void multipleValues_sequential() {
        ByteBuf buf = ByteBuf.empty();
        buf.writeByte((byte) 1);
        buf.writeShort(200);
        buf.writeInt(300000);

        assertEquals((byte) 1, buf.readByte());
        assertEquals((short) 200, buf.readShort());
        assertEquals(300000, buf.readInt());
    }

    @Test
    void length_empty() {
        ByteBuf buf = ByteBuf.empty();
        assertEquals(0, buf.length());
    }

    @Test
    void length_afterWrite() {
        ByteBuf buf = ByteBuf.empty();
        buf.writeInt(0);
        assertEquals(4, buf.length());
    }

    @Test
    void remain_decreasesOnRead() {
        ByteBuf buf = ByteBuf.empty();
        buf.writeInt(1);
        buf.writeInt(2);
        assertEquals(8, buf.remain());
        buf.readInt();
        assertEquals(4, buf.remain());
    }

    @Test
    void isEmpty_afterFullRead() {
        ByteBuf buf = ByteBuf.empty();
        buf.writeByte((byte) 0);
        assertFalse(buf.isEmpty());
        buf.readByte();
        assertTrue(buf.isEmpty());
    }

    @Test
    void toBytes_roundtrip() {
        ByteBuf buf = ByteBuf.empty();
        buf.writeInt(0xABCD1234);
        byte[] bytes = buf.toBytes();
        ByteBuf restored = ByteBuf.of(bytes);
        assertEquals(0xABCD1234, restored.readInt());
    }

    @Test
    void readPastEnd_throwsIndexOutOfBounds() {
        ByteBuf buf = ByteBuf.empty();
        buf.writeByte((byte) 1);
        buf.readByte();
        assertThrows(IndexOutOfBoundsException.class, () -> buf.readByte());
    }

    @Test
    void skipBytes_skipsData() {
        ByteBuf buf = ByteBuf.empty();
        buf.writeInt(111);
        buf.writeInt(222);
        buf.skipBytes(4);
        assertEquals(222, buf.readInt());
    }

    @Test
    void ofWithOffset_readsCorrectly() {
        byte[] data = {0, 0, (byte) 0xAB, (byte) 0xCD};
        ByteBuf buf = ByteBuf.of(data, 2, 2);
        assertEquals((short) 0xABCD, buf.readShort());
    }
}
