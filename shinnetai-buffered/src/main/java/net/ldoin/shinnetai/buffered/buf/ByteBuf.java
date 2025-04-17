package net.ldoin.shinnetai.buffered.buf;

import net.ldoin.shinnetai.buffered.Buffered;

import java.util.Arrays;

@SuppressWarnings({"unused"})
public class ByteBuf implements Buffered, Cloneable {

    public static ByteBuf empty() {
        return new ByteBuf();
    }

    public static ByteBuf of(byte[] bytes) {
        return new ByteBuf(bytes, 0, bytes.length);
    }

    public static ByteBuf of(byte[] bytes, int offset, int length) {
        return new ByteBuf(bytes, offset, length);
    }

    protected byte[] bytes;
    protected int offset;
    protected int readIndex;
    protected int writeIndex;

    protected ByteBuf() {
        this.bytes = new byte[0];
        this.offset = 0;
    }

    protected ByteBuf(byte[] bytes) {
        this(bytes, 0, bytes.length);
    }

    protected ByteBuf(byte[] bytes, int offset, int length) {
        this.bytes = bytes;
        this.offset = offset;
        this.readIndex = offset;
        this.writeIndex = offset + length;
    }

    public ByteBuf writeByte(byte value) {
        expand(1);
        bytes[writeIndex] = value;
        return writeValue(1);
    }

    public ByteBuf writeBytes(byte[] values) {
        expand(values.length);
        System.arraycopy(values, 0, bytes, writeIndex, values.length);
        return writeValue(values.length);
    }

    public ByteBuf writeShort(int value) {
        expand(2);
        bytes[writeIndex] = (byte) (value >>> 8);
        bytes[writeIndex + 1] = (byte) value;
        return writeValue(2);
    }

    public ByteBuf writeShortLE(int value) {
        expand(2);
        bytes[writeIndex] = (byte) value;
        bytes[writeIndex + 1] = (byte) (value >>> 8);
        return writeValue(2);
    }

    public ByteBuf writeInt(int value) {
        expand(4);
        bytes[writeIndex] = (byte) (value >>> 24);
        bytes[writeIndex + 1] = (byte) (value >>> 16);
        bytes[writeIndex + 2] = (byte) (value >>> 8);
        bytes[writeIndex + 3] = (byte) value;
        return writeValue(4);
    }

    public ByteBuf writeIntLE(int value) {
        expand(4);
        bytes[writeIndex] = (byte) value;
        bytes[writeIndex + 1] = (byte) (value >>> 8);
        bytes[writeIndex + 2] = (byte) (value >>> 16);
        bytes[writeIndex + 3] = (byte) (value >>> 24);
        return writeValue(4);
    }

    public ByteBuf writeLong(long value) {
        expand(8);
        bytes[writeIndex] = (byte) ((int) (value >>> 56));
        bytes[writeIndex + 1] = (byte) ((int) (value >>> 48));
        bytes[writeIndex + 2] = (byte) ((int) (value >>> 40));
        bytes[writeIndex + 3] = (byte) ((int) (value >>> 32));
        bytes[writeIndex + 4] = (byte) ((int) (value >>> 24));
        bytes[writeIndex + 5] = (byte) ((int) (value >>> 16));
        bytes[writeIndex + 6] = (byte) ((int) (value >>> 8));
        bytes[writeIndex + 7] = (byte) ((int) value);
        return writeValue(8);
    }

    public ByteBuf writeLongLE(long value) {
        expand(8);
        bytes[writeIndex] = (byte) ((int) value);
        bytes[writeIndex + 1] = (byte) ((int) (value >>> 8));
        bytes[writeIndex + 2] = (byte) ((int) (value >>> 16));
        bytes[writeIndex + 3] = (byte) ((int) (value >>> 24));
        bytes[writeIndex + 4] = (byte) ((int) (value >>> 32));
        bytes[writeIndex + 5] = (byte) ((int) (value >>> 40));
        bytes[writeIndex + 6] = (byte) ((int) (value >>> 48));
        bytes[writeIndex + 7] = (byte) ((int) (value >>> 56));
        return writeValue(8);
    }

    public ByteBuf writeFloat(float value) {
        return writeInt(Float.floatToRawIntBits(value));
    }

    public ByteBuf writeDouble(double value) {
        return writeLong(Double.doubleToRawLongBits(value));
    }

    public ByteBuf writeChar(int value) {
        return writeShort(value);
    }

    public ByteBuf writeString(String value) {
        writeInt(value.length());
        for (char c : value.toCharArray()) {
            writeChar(c);
        }

        return this;
    }

    public ByteBuf writeBoolean(boolean value) {
        expand(1);
        bytes[writeIndex] = (byte) (value ? 1 : 0);
        return writeValue(1);
    }

    public byte readByte() {
        checkReadable(1);
        return bytes[readIndex++];
    }

    public byte[] readBytes(int amount) {
        byte[] bytes = new byte[amount];
        for (int i = 0; i < amount; i++) {
            bytes[i] = readByte();
        }

        return bytes;
    }

    public short readShort() {
        checkReadable(2);
        return (short) (bytes[readIndex++] << 8 | bytes[readIndex++] & 255);
    }

    public short readShortLE() {
        checkReadable(2);
        return (short) (bytes[readIndex++] & 255 | bytes[readIndex++] << 8);
    }

    public int readInt() {
        checkReadable(4);
        return (bytes[readIndex++] & 255) << 24 | (bytes[readIndex++] & 255) << 16 | (bytes[readIndex++] & 255) << 8 | bytes[readIndex++] & 255;
    }

    public int readIntLE() {
        checkReadable(4);
        return bytes[readIndex++] & 255 | (bytes[readIndex++] & 255) << 8 | (bytes[readIndex++] & 255) << 16 | (bytes[readIndex++] & 255) << 24;
    }

    public long readLong() {
        checkReadable(8);
        return ((long) bytes[readIndex++] & 255L) << 56 | ((long) bytes[readIndex++] & 255L) << 48 | ((long) bytes[readIndex++] & 255L) << 40 | ((long) bytes[readIndex++] & 255L) << 32 | ((long) bytes[readIndex++] & 255L) << 24 | ((long) bytes[readIndex++] & 255L) << 16 | ((long) bytes[readIndex++] & 255L) << 8 | (long) bytes[readIndex++] & 255L;
    }

    public long readLongLE() {
        checkReadable(8);
        return (long) bytes[readIndex++] & 255L | ((long) bytes[readIndex++] & 255L) << 8 | ((long) bytes[readIndex++] & 255L) << 16 | ((long) bytes[readIndex++] & 255L) << 24 | ((long) bytes[readIndex++] & 255L) << 32 | ((long) bytes[readIndex++] & 255L) << 40 | ((long) bytes[readIndex++] & 255L) << 48 | ((long) bytes[readIndex++] & 255L) << 56;
    }

    public float readFloat() {
        return Float.intBitsToFloat(readInt());
    }

    public double readDouble() {
        return Double.longBitsToDouble(readLong());
    }

    public char readChar() {
        return (char) readShort();
    }

    public String readString() {
        StringBuilder str = new StringBuilder();
        int length = readInt();
        for (int i = 0; i < length; i++) {
            str.append((char) readShort());
        }

        return str.toString();
    }

    public boolean readBoolean() {
        return readByte() != 0;
    }

    public ByteBuf merge(byte[] bytes) {
        expand(bytes);
        return this;
    }

    public ByteBuf merge(Buffered buffered) {
        return merge(buffered.toBytes());
    }

    public ByteBuf skipBytes(int length) {
        checkReadable(length);
        readIndex += length;
        return this;
    }

    @Override
    public byte[] toBytes() {
        return Arrays.copyOfRange(bytes, offset, writeIndex);
    }

    public int length() {
        return writeIndex - offset;
    }

    public int remain() {
        return writeIndex - readIndex;
    }

    public boolean isEmpty() {
        return remain() <= 0;
    }

    @Override
    protected ByteBuf clone() throws CloneNotSupportedException {
        return (ByteBuf) super.clone();
    }

    protected void expand(int additional) {
        int needed = writeIndex + additional;
        if (needed <= bytes.length) {
            return;
        }

        int newCapacity = bytes.length == 0 ? 16 : bytes.length;
        while (newCapacity < needed) {
            newCapacity <<= 1;
        }

        byte[] newArray = new byte[newCapacity];
        System.arraycopy(bytes, 0, newArray, 0, bytes.length);

        bytes = newArray;
    }

    protected void expand(byte[] target) {
        int old = bytes.length;
        int to = target.length + old;
        if (old >= to) {
            return;
        }

        byte[] newArray = new byte[to];
        System.arraycopy(bytes, 0, newArray, 0, old);
        System.arraycopy(target, 0, newArray, old, to);

        bytes = newArray;
    }

    protected ByteBuf writeValue(int bytes) {
        writeIndex += bytes;
        return this;
    }

    protected void checkReadable(int toRead) {
        if (readIndex > writeIndex - toRead) {
            throw new IndexOutOfBoundsException(String.format("try to read buf readIndex(%d), writeIndex(%d), length(%d), need(%d)",
                    readIndex, writeIndex, bytes.length, toRead));
        }
    }
}