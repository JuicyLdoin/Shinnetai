package net.ldoin.shinnetai.buffered.util;

import net.ldoin.shinnetai.buffered.ByteReader;
import net.ldoin.shinnetai.buffered.ByteWriter;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class IOUtil {

    public static byte readByte(InputStream in) throws IOException {
        int read = in.read();
        if (read == -1) {
            throw new EOFException();
        }
        return (byte) read;
    }

    public static byte readByte(ByteReader in) throws IOException {
        return in.read();
    }

    public static void writeByte(OutputStream out, int value) throws IOException {
        out.write(value);
    }

    public static void writeByte(ByteWriter out, int value) throws IOException {
        out.write(value);
    }

    public static short readShort(InputStream in) throws IOException {
        int ch1 = in.read();
        int ch2 = in.read();
        if ((ch1 | ch2) < 0) {
            throw new EOFException();
        }

        return (short) ((ch1 << 8) + ch2);
    }

    public static short readShort(ByteReader in) throws IOException {
        int ch1 = in.read() & 0xFF;
        int ch2 = in.read() & 0xFF;
        return (short) ((ch1 << 8) + ch2);
    }

    public static void writeShort(OutputStream out, int value) throws IOException {
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    public static void writeShort(ByteWriter out, int value) throws IOException {
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    public static int readInt(InputStream in) throws IOException {
        int ch1 = in.read();
        int ch2 = in.read();
        int ch3 = in.read();
        int ch4 = in.read();
        if ((ch1 | ch2 | ch3 | ch4) < 0) {
            throw new EOFException();
        }

        return ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + ch4);
    }

    public static int readInt(ByteReader in) throws IOException {
        int ch1 = in.read() & 0xFF;
        int ch2 = in.read() & 0xFF;
        int ch3 = in.read() & 0xFF;
        int ch4 = in.read() & 0xFF;
        return ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + ch4);
    }

    public static void writeInt(OutputStream out, int value) throws IOException {
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    public static void writeInt(ByteWriter out, int value) throws IOException {
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    public static long readLong(InputStream in) throws IOException {
        return ((long) readInt(in) << 32) + (readInt(in) & 0xFFFFFFFFL);
    }

    public static long readLong(ByteReader in) throws IOException {
        return ((long) readInt(in) << 32) + (readInt(in) & 0xFFFFFFFFL);
    }

    public static void writeLong(OutputStream out, long value) throws IOException {
        out.write((int) (value >>> 56) & 0xFF);
        out.write((int) (value >>> 48) & 0xFF);
        out.write((int) (value >>> 40) & 0xFF);
        out.write((int) (value >>> 32) & 0xFF);
        out.write((int) (value >>> 24) & 0xFF);
        out.write((int) (value >>> 16) & 0xFF);
        out.write((int) (value >>> 8) & 0xFF);
        out.write((int) (value) & 0xFF);
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

    public static int readVarInt(InputStream in) throws IOException {
        return readVarInt(() -> {
            int read = in.read();
            if (read == -1) {
                throw new EOFException();
            }

            return (byte) read;
        });
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

    public static void writeVarInt(OutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }

        out.write(value);
    }

    public static void writeVarInt(ByteWriter out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }

        out.write(value);
    }

    public static long readVarLong(InputStream in) throws IOException {
        return readVarLong(() -> {
            int read = in.read();
            if (read == -1) {
                throw new EOFException();
            }

            return (byte) read;
        });
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

    public static void writeVarLong(OutputStream out, long value) throws IOException {
        while ((value & ~0x7FL) != 0) {
            out.write((int) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }

        out.write((int) value);
    }

    public static void writeVarLong(ByteWriter out, long value) throws IOException {
        while ((value & ~0x7FL) != 0) {
            out.write((int) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }

        out.write((int) value);
    }

    public static int readFully(InputStream in, byte[] buffer, int length) throws IOException {
        int totalRead = 0;
        while (totalRead < length) {
            int read = in.read(buffer, totalRead, length - totalRead);
            if (read == -1) {
                return totalRead;
            }

            totalRead += read;
        }

        return totalRead;
    }
}
