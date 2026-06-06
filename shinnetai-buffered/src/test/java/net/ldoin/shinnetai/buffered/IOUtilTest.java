package net.ldoin.shinnetai.buffered;

import net.ldoin.shinnetai.buffered.util.IOUtil;
import org.junit.jupiter.api.Test;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IOUtilTest {

    private static final class BufferChannel implements ReadableByteChannel, WritableByteChannel {

        private final ByteBuffer buf;
        private boolean open = true;

        BufferChannel(int capacity) {
            this.buf = ByteBuffer.allocate(capacity);
        }

        @Override
        public int write(ByteBuffer src) {
            int n = src.remaining();
            buf.put(src);
            return n;
        }

        void flip() {
            buf.flip();
        }

        @Override
        public int read(ByteBuffer dst) {
            if (!buf.hasRemaining()) return -1;
            int n = Math.min(dst.remaining(), buf.remaining());
            int savedLimit = buf.limit();
            buf.limit(buf.position() + n);
            dst.put(buf);
            buf.limit(savedLimit);
            return n;
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() {
            open = false;
        }
    }

    @Test
    void varInt_zero_roundtrip() throws IOException {
        BufferChannel ch = new BufferChannel(16);
        IOUtil.writeVarInt(ch, 0);
        ch.flip();
        assertEquals(0, IOUtil.readVarInt(ch));
    }

    @Test
    void varInt_127_singleByte() throws IOException {
        BufferChannel ch = new BufferChannel(16);
        IOUtil.writeVarInt(ch, 127);
        ch.flip();
        assertEquals(1, ch.buf.remaining(), "127 should fit in one byte");
        assertEquals(127, IOUtil.readVarInt(ch));
    }

    @Test
    void varInt_128_twoBytes() throws IOException {
        BufferChannel ch = new BufferChannel(16);
        IOUtil.writeVarInt(ch, 128);
        ch.flip();
        assertEquals(2, ch.buf.remaining(), "128 needs two bytes");
        assertEquals(128, IOUtil.readVarInt(ch));
    }

    @Test
    void varInt_maxInt_fiveBytes() throws IOException {
        BufferChannel ch = new BufferChannel(16);
        IOUtil.writeVarInt(ch, Integer.MAX_VALUE);
        ch.flip();
        assertEquals(5, ch.buf.remaining(), "MAX_VALUE needs 5 bytes");
        assertEquals(Integer.MAX_VALUE, IOUtil.readVarInt(ch));
    }

    @Test
    void varInt_negative_roundtrip() throws IOException {
        BufferChannel ch = new BufferChannel(16);
        IOUtil.writeVarInt(ch, -1);
        ch.flip();
        assertEquals(-1, IOUtil.readVarInt(ch));
    }

    @Test
    void varLong_zero_roundtrip() throws IOException {
        BufferChannel ch = new BufferChannel(16);
        IOUtil.writeVarLong(ch, 0L);
        ch.flip();
        assertEquals(0L, IOUtil.readVarLong(ch));
    }

    @Test
    void varLong_maxLong_roundtrip() throws IOException {
        BufferChannel ch = new BufferChannel(16);
        IOUtil.writeVarLong(ch, Long.MAX_VALUE);
        ch.flip();
        assertEquals(Long.MAX_VALUE, IOUtil.readVarLong(ch));
    }

    @Test
    void writeInt_readInt_roundtrip() throws IOException {
        BufferChannel ch = new BufferChannel(16);
        IOUtil.writeInt(ch, 0xABCDEF01);
        ch.flip();
        assertEquals(0xABCDEF01, IOUtil.readInt(ch));
    }

    @Test
    void writeShort_readShort_roundtrip() throws IOException {
        BufferChannel ch = new BufferChannel(16);
        IOUtil.writeShort(ch, 0x1234);
        ch.flip();
        assertEquals((short) 0x1234, IOUtil.readShort(ch));
    }

    @Test
    void writeLong_readLong_roundtrip() throws IOException {
        BufferChannel ch = new BufferChannel(16);
        IOUtil.writeLong(ch, Long.MIN_VALUE);
        ch.flip();
        assertEquals(Long.MIN_VALUE, IOUtil.readLong(ch));
    }

    @Test
    void writeByte_readByte_roundtrip() throws IOException {
        BufferChannel ch = new BufferChannel(4);
        IOUtil.writeByte(ch, 0xFF);
        ch.flip();
        assertEquals((byte) 0xFF, IOUtil.readByte(ch));
    }

    @Test
    void readByte_emptyChannel_throwsEOFException() {
        BufferChannel ch = new BufferChannel(4);
        ch.flip();
        assertThrows(EOFException.class, () -> IOUtil.readByte(ch));
    }

    @Test
    void readInt_emptyChannel_throwsEOFException() {
        BufferChannel ch = new BufferChannel(4);
        ch.flip();
        assertThrows(EOFException.class, () -> IOUtil.readInt(ch));
    }

    @Test
    void readShort_emptyChannel_throwsEOFException() {
        BufferChannel ch = new BufferChannel(4);
        ch.flip();
        assertThrows(EOFException.class, () -> IOUtil.readShort(ch));
    }

    @Test
    void multipleVarInts_sequential() throws IOException {
        BufferChannel ch = new BufferChannel(64);
        int[] values = {0, 1, 127, 128, 300, 16383, 16384};
        for (int v : values) {
            IOUtil.writeVarInt(ch, v);
        }
        ch.flip();
        for (int v : values) {
            assertEquals(v, IOUtil.readVarInt(ch));
        }
    }
}
