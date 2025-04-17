package net.ldoin.shinnetai.buffered.buf.smart;

import net.ldoin.shinnetai.buffered.map.ByteMap;

import java.util.Collection;
import java.util.UUID;
import java.util.function.Function;

@SuppressWarnings({"unused"})
public final class WriteOnlySmartByteBuf extends SmartByteBuf {

    public static WriteOnlySmartByteBuf empty() {
        return new WriteOnlySmartByteBuf();
    }

    public static WriteOnlySmartByteBuf of(byte[] bytes) {
        return new WriteOnlySmartByteBuf(bytes);
    }

    private WriteOnlySmartByteBuf() {
    }

    private WriteOnlySmartByteBuf(byte[] bytes) {
        super(bytes);
    }

    @Override
    public byte readByte() {
        throw new UnsupportedOperationException("Only write ByteBuf");
    }

    @Override
    public byte[] readBytes(int amount) {
        throw new UnsupportedOperationException("Only write ByteBuf");
    }

    @Override
    public short readShort() {
        throw new UnsupportedOperationException("Only write ByteBuf");
    }

    @Override
    public short readShortLE() {
        throw new UnsupportedOperationException("Only write ByteBuf");
    }

    @Override
    public int readInt() {
        throw new UnsupportedOperationException("Only write ByteBuf");
    }

    @Override
    public int readIntLE() {
        throw new UnsupportedOperationException("Only write ByteBuf");
    }

    @Override
    public long readLong() {
        throw new UnsupportedOperationException("Only write ByteBuf");
    }

    @Override
    public long readLongLE() {
        throw new UnsupportedOperationException("Only write ByteBuf");
    }

    @Override
    public float readFloat() {
        throw new UnsupportedOperationException("Only write ByteBuf");
    }

    @Override
    public double readDouble() {
        throw new UnsupportedOperationException("Only write ByteBuf");
    }

    @Override
    public char readChar() {
        throw new UnsupportedOperationException("Only write ByteBuf");
    }

    @Override
    public String readString() {
        throw new UnsupportedOperationException("Only write ByteBuf");
    }

    @Override
    public boolean readBoolean() {
        throw new UnsupportedOperationException("Only write ByteBuf");
    }

    @Override
    public byte[] readBytes() {
        throw new UnsupportedOperationException("Only write ByteBuf");
    }

    @Override
    public boolean[] readBooleanArray() {
        throw new UnsupportedOperationException("Only write ByteBuf");
    }

    @Override
    public <V, C extends Collection<V>> C readCollection(C collection, Function<SmartByteBuf, V> reader) {
        throw new UnsupportedOperationException("Only write ByteBuf");
    }

    @Override
    public int readVarInt() {
        throw new UnsupportedOperationException("Only write ByteBuf");
    }

    @Override
    public int[] readIdsArray() {
        throw new UnsupportedOperationException("Only write ByteBuf");
    }

    @Override
    public long readVarLong() {
        throw new UnsupportedOperationException("Only write ByteBuf");
    }

    @Override
    public String readStringLetters() {
        throw new UnsupportedOperationException("Only write ByteBuf");
    }

    @Override
    public UUID readUUID() {
        throw new UnsupportedOperationException("Only write ByteBuf");
    }

    @Override
    public <O> O readObject() {
        throw new UnsupportedOperationException("Only write ByteBuf");
    }

    @Override
    public ByteMap readByteMap() {
        throw new UnsupportedOperationException("Only write ByteBuf");
    }
}