package net.aquarealm.shinnetai.buffered.buf.smart;

import net.aquarealm.shinnetai.buffered.buf.ByteBuf;

import java.util.Collection;
import java.util.UUID;
import java.util.function.BiConsumer;

@SuppressWarnings({"unused"})
public final class ReadOnlySmartByteBuf extends SmartByteBuf {

    public static ReadOnlySmartByteBuf of(byte[] bytes) {
        return new ReadOnlySmartByteBuf(bytes);
    }

    private ReadOnlySmartByteBuf(byte[] bytes) {
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
    public SmartByteBuf writeString(String value) {
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

    @Override
    public SmartByteBuf writeBooleanArray(boolean[] value) {
        throw new UnsupportedOperationException("Only read ByteBuf");
    }

    @Override
    public <V> SmartByteBuf writeCollection(Collection<V> collection, BiConsumer<SmartByteBuf, V> writer) {
        throw new UnsupportedOperationException("Only read ByteBuf");
    }

    @Override
    public SmartByteBuf writeIdsArray(int[] ids) {
        throw new UnsupportedOperationException("Only read ByteBuf");
    }

    @Override
    public SmartByteBuf writeIdsArray(int[] ids, boolean sorted) {
        throw new UnsupportedOperationException("Only read ByteBuf");
    }

    @Override
    public SmartByteBuf writeIdsArraySorted(int[] ids) {
        throw new UnsupportedOperationException("Only read ByteBuf");
    }

    @Override
    public SmartByteBuf writeStringLetters(String value) {
        throw new UnsupportedOperationException("Only read ByteBuf");
    }

    @Override
    public SmartByteBuf writeUUID(UUID value) {
        throw new UnsupportedOperationException("Only read ByteBuf");
    }

    @Override
    public SmartByteBuf writeVarInt(int value) {
        throw new UnsupportedOperationException("Only read ByteBuf");
    }

    @Override
    public SmartByteBuf writeVarLong(long value) {
        throw new UnsupportedOperationException("Only read ByteBuf");
    }
}