package net.ldoin.shinnetai.buffered.buf;

@SuppressWarnings({"unused"})
public final class WriteOnlyByteBuf extends ByteBuf {

    public static WriteOnlyByteBuf empty() {
        return new WriteOnlyByteBuf();
    }

    public static WriteOnlyByteBuf of(byte[] bytes) {
        return new WriteOnlyByteBuf(bytes);
    }

    private WriteOnlyByteBuf() {
    }

    private WriteOnlyByteBuf(byte[] bytes) {
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
}