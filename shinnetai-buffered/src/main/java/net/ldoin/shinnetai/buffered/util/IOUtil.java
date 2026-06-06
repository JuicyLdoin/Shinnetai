package net.ldoin.shinnetai.buffered.util;

import net.ldoin.shinnetai.buffered.ByteReader;
import net.ldoin.shinnetai.buffered.ByteWriter;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

public class IOUtil {

    private static final ThreadLocal<ByteBuffer> SINGLE_BYTE_BUF = ThreadLocal.withInitial(() -> ByteBuffer.allocate(1));
    private static final ThreadLocal<ByteBuffer> SINGLE_WRITE_BYTE_BUF = ThreadLocal.withInitial(() -> ByteBuffer.allocate(1));

    private static void readFullyInto(ReadableByteChannel ch, ByteBuffer buf) throws IOException {
        while (buf.hasRemaining()) {
            int n = ch.read(buf);
            if (n == -1) {
                throw new EOFException();
            }
        }
    }

    public static byte readByte(ReadableByteChannel ch) throws IOException {
        ByteBuffer buf = SINGLE_BYTE_BUF.get();
        buf.clear();
        readFullyInto(ch, buf);
        return buf.get(0);
    }

    public static void writeByte(WritableByteChannel ch, int value) throws IOException {
        ByteBuffer buf = SINGLE_WRITE_BYTE_BUF.get();
        buf.clear();
        buf.put((byte) value);
        buf.flip();
        while (buf.hasRemaining()) {
            ch.write(buf);
        }
    }

    public static short readShort(ReadableByteChannel ch) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(2);
        readFullyInto(ch, buf);
        buf.flip();
        return buf.getShort();
    }

    public static void writeShort(WritableByteChannel ch, int value) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(2);
        buf.putShort((short) value);
        buf.flip();
        while (buf.hasRemaining()) {
            ch.write(buf);
        }
    }

    public static int readInt(ReadableByteChannel ch) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(4);
        readFullyInto(ch, buf);
        buf.flip();
        return buf.getInt();
    }

    public static void writeInt(WritableByteChannel ch, int value) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(4);
        buf.putInt(value);
        buf.flip();
        while (buf.hasRemaining()) {
            ch.write(buf);
        }
    }

    public static long readLong(ReadableByteChannel ch) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(8);
        readFullyInto(ch, buf);
        buf.flip();
        return buf.getLong();
    }

    public static void writeLong(WritableByteChannel ch, long value) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(8);
        buf.putLong(value);
        buf.flip();
        while (buf.hasRemaining()) {
            ch.write(buf);
        }
    }

    public static int readVarInt(ReadableByteChannel ch) throws IOException {
        return readVarInt(() -> readByte(ch));
    }

    public static void writeVarInt(WritableByteChannel ch, int value) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(5);
        while ((value & ~0x7F) != 0) {
            buf.put((byte) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }

        buf.put((byte) value);
        buf.flip();
        while (buf.hasRemaining()) {
            ch.write(buf);
        }
    }

    public static long readVarLong(ReadableByteChannel ch) throws IOException {
        return readVarLong(() -> readByte(ch));
    }

    public static void writeVarLong(WritableByteChannel ch, long value) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(10);
        while ((value & ~0x7FL) != 0) {
            buf.put((byte) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }

        buf.put((byte) value);
        buf.flip();
        while (buf.hasRemaining()) {
            ch.write(buf);
        }
    }

    public static int readFully(ReadableByteChannel ch, byte[] buffer, int length) throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(buffer, 0, length);
        int totalRead = 0;
        while (buf.hasRemaining()) {
            int read = ch.read(buf);
            if (read == -1) {
                return totalRead;
            }

            totalRead += read;
        }

        return totalRead;
    }

    public static byte readByte(ByteReader in) throws IOException {
        return in.read();
    }

    public static void writeByte(ByteWriter out, int value) throws IOException {
        out.write(value);
    }

    public static short readShort(ByteReader in) throws IOException {
        int ch1 = in.read() & 0xFF;
        int ch2 = in.read() & 0xFF;
        return (short) ((ch1 << 8) + ch2);
    }

    public static void writeShort(ByteWriter out, int value) throws IOException {
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    public static int readInt(ByteReader in) throws IOException {
        int ch1 = in.read() & 0xFF;
        int ch2 = in.read() & 0xFF;
        int ch3 = in.read() & 0xFF;
        int ch4 = in.read() & 0xFF;
        return ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + ch4);
    }

    public static void writeInt(ByteWriter out, int value) throws IOException {
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    public static long readLong(ByteReader in) throws IOException {
        return ((long) readInt(in) << 32) + (readInt(in) & 0xFFFFFFFFL);
    }

    public static void writeLong(ByteWriter out, long value) throws IOException {
        out.write((int) (value >>> 56) & 0xFF);
        out.write((int) (value >>> 48) & 0xFF);
        out.write((int) (value >>> 40) & 0xFF);
        out.write((int) (value >>> 32) & 0xFF);
        out.write((int) (value >>> 24) & 0xFF);
        out.write((int) (value >>> 16) & 0xFF);
        out.write((int) (value >>> 8) & 0xFF);
        out.write((int) (value) & 0xFF);
    }

    public static int readVarInt(ByteReader in) throws IOException {
        int result = 0;
        int shift = 0;
        for (int i = 0; i < 5; i++) {
            byte b = in.read();
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }

            shift += 7;
        }

        throw new IOException("VarInt is too big");
    }

    public static void writeVarInt(ByteWriter out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }

        out.write(value);
    }

    public static long readVarLong(ByteReader in) throws IOException {
        long result = 0;
        int shift = 0;
        for (int i = 0; i < 10; i++) {
            byte b = in.read();
            result |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }

            shift += 7;
        }

        throw new IOException("VarLong is too big");
    }

    public static void writeVarLong(ByteWriter out, long value) throws IOException {
        while ((value & ~0x7FL) != 0) {
            out.write((int) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }

        out.write((int) value);
    }
}
