package net.ldoin.shinnetai.buffered.buf;

@SuppressWarnings({"unused"})
public final class ReadOnlyByteBuf extends ByteBuf {

    public static ReadOnlyByteBuf of(byte[] bytes) {
        return new ReadOnlyByteBuf(bytes);
    }

    private ReadOnlyByteBuf(byte[] bytes) {
        super(bytes);
    }

    @Override
    public ByteBuf writeByte(byte value) {
        throw new UnsupportedOperationException("Only read ByteBuf");
    }

    @Override
    public ByteBuf writeBytes(byte[] values) {
        throw new UnsupportedOperationException("Only read ByteBuf");
    }

    @Override
    public ByteBuf writeShort(int value) {
        throw new UnsupportedOperationException("Only read ByteBuf");
    }

    @Override
    public ByteBuf writeShortLE(int value) {
        throw new UnsupportedOperationException("Only read ByteBuf");
    }

    @Override
    public ByteBuf writeInt(int value) {
        throw new UnsupportedOperationException("Only read ByteBuf");
    }

    @Override
    public ByteBuf writeIntLE(int value) {
        throw new UnsupportedOperationException("Only read ByteBuf");
    }

    @Override
    public ByteBuf writeLong(long value) {
        throw new UnsupportedOperationException("Only read ByteBuf");
    }

    @Override
    public ByteBuf writeLongLE(long value) {
        throw new UnsupportedOperationException("Only read ByteBuf");
    }

    @Override
    public ByteBuf writeFloat(float value) {
        throw new UnsupportedOperationException("Only read ByteBuf");
    }

    @Override
    public ByteBuf writeDouble(double value) {
        throw new UnsupportedOperationException("Only read ByteBuf");
    }

    @Override
    public ByteBuf writeChar(int value) {
        throw new UnsupportedOperationException("Only read ByteBuf");
    }

    @Override
    public ByteBuf writeString(String value) {
        throw new UnsupportedOperationException("Only read ByteBuf");
    }

    @Override
    public ByteBuf writeBoolean(boolean value) {
        throw new UnsupportedOperationException("Only read ByteBuf");
    }

    @Override
    protected ByteBuf writeValue(int bytes) {
        throw new UnsupportedOperationException("Only read ByteBuf");
    }
}