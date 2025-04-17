package net.ldoin.shinnetai.buffered.buf.smart;

import net.ldoin.shinnetai.buffered.Buffered;
import net.ldoin.shinnetai.buffered.BufferedSerializer;
import net.ldoin.shinnetai.buffered.buf.ByteBuf;
import net.ldoin.shinnetai.buffered.map.ByteMap;
import net.ldoin.shinnetai.buffered.util.IOUtil;

import java.io.IOException;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

@SuppressWarnings({"unused"})
public class SmartByteBuf extends ByteBuf {

    public static SmartByteBuf empty() {
        return new SmartByteBuf();
    }

    public static SmartByteBuf of(byte[] bytes) {
        return new SmartByteBuf(bytes);
    }

    public static SmartByteBuf of(byte[] bytes, int offset, int length) {
        return new SmartByteBuf(bytes, offset, length);
    }

    protected SmartByteBuf() {
    }

    protected SmartByteBuf(byte[] bytes) {
        super(bytes);
    }

    protected SmartByteBuf(byte[] bytes, int offset, int length) {
        super(bytes, offset, length);
    }

    @Override
    public ByteBuf writeBytes(byte[] values) {
        writeVarInt(values.length);
        return super.writeBytes(values);
    }

    public SmartByteBuf writeVarInt(int value) {
        while ((value & ~0x7F) != 0) {
            writeByte((byte) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }

        writeByte((byte) value);
        return this;
    }

    public SmartByteBuf writeVarLong(long value) {
        while ((value & ~0x7FL) != 0) {
            writeByte((byte) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }

        writeByte((byte) value);
        return this;
    }

    public SmartByteBuf writeBooleanArray(boolean[] value) {
        int bitIndex = 0;
        byte b = 0;
        for (int i = 0; i < value.length; i++) {
            b |= (byte) ((value[i] ? 1 : 0) << bitIndex++);
            if (bitIndex == 7) {
                writeByte((byte) (b | ((i < value.length - 1) ? 0x80 : 0)));
                bitIndex = 0;
                b = 0;
            }
        }

        if (bitIndex > 0) {
            writeByte(b);
        }

        return this;
    }

    public SmartByteBuf writeIdsArray(int[] ids, boolean sorted) {
        int low = Integer.MAX_VALUE;
        if (sorted) {
            low = ids[0];
        } else {
            for (int id : ids) {
                low = Math.min(id, low);
            }
        }

        BitSet bitSet = new BitSet();
        for (int id : ids) {
            bitSet.set(id - low);
        }

        byte[] bytes = bitSet.toByteArray();
        writeVarInt(ids.length);
        writeVarInt(low);
        writeBytes(bytes);
        return this;
    }

    public SmartByteBuf writeIdsArray(int[] ids) {
        return writeIdsArray(ids, false);
    }

    public SmartByteBuf writeIdsArraySorted(int[] ids) {
        return writeIdsArray(ids, true);
    }

    @Override
    public SmartByteBuf writeString(String value) {
        return (SmartByteBuf) writeBytes(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    protected final int bitsForWrite(int value) {
        return 32 - Integer.numberOfLeadingZeros(value);
    }

    public SmartByteBuf writeStringLetters(String value) {
        int length = value.length();
        char[] chars = value.toCharArray();
        writeVarInt(length);

        int asciiStartIndex = 128;
        int maxValue = 0;
        boolean hasUpper = false;
        for (char c : chars) {
            boolean upper = Character.isUpperCase(c);
            if (upper) {
                hasUpper = true;
            }

            int asciiValue = charValue(c, upper);
            asciiStartIndex = Math.min(asciiStartIndex, asciiValue);
            maxValue = Math.max(maxValue, asciiValue);
        }

        int bitsPerLetter = Math.max(1, bitsForWrite(maxValue - asciiStartIndex));

        byte data = 0;
        data |= (byte) (hasUpper ? 1 : 0);
        data |= (byte) ((bitsPerLetter & 0x07) << 1);
        writeByte(data);

        byte[] bytes = new byte[(int) Math.ceil(value.length() * (bitsPerLetter + (hasUpper ? 1 : 0)) / 8D)];
        int bitIndex = 0;
        for (char c : chars) {
            boolean upper = Character.isUpperCase(c);
            int charValue = charValue(c, upper);
            if (hasUpper) {
                if (upper) {
                    bytes[bitIndex / 8] |= (byte) (1 << bitIndex % 8);
                }

                bitIndex++;
            }

            for (int i = 0; i < bitsPerLetter; i++) {
                if ((charValue & (1 << i)) != 0) {
                    bytes[bitIndex / 8] |= (byte) (1 << bitIndex % 8);
                }

                bitIndex++;
            }
        }

        writeBytes(bytes);
        return this;
    }

    public <V> SmartByteBuf writeCollection(Collection<V> collection, BiConsumer<SmartByteBuf, V> writer) {
        writeVarInt(collection.size());
        for (V v : collection) {
            writer.accept(this, v);
        }

        return this;
    }

    public <V, C extends Collection<V>> C readCollection(Function<Integer, C> collectionFactory, Function<SmartByteBuf, V> reader) {
        int size = readVarInt();
        C collection = collectionFactory.apply(size);
        for (int i = 0; i < size; i++) {
            collection.add(reader.apply(this));
        }

        return collection;
    }

    public SmartByteBuf writeUUID(UUID value) {
        writeLong(value.getMostSignificantBits());
        writeLong(value.getLeastSignificantBits());
        return this;
    }

    public <O> SmartByteBuf writeObject(O object) {
        BufferedSerializer.get().serializeIncludeId(object, this);
        return this;
    }

    public SmartByteBuf writeByteMap(ByteMap map) {
        writeBytes(map.toBytes());
        return this;
    }

    public byte[] readBytes() {
        return readBytes(readVarInt());
    }

    public int readVarInt() {
        try {
            return IOUtil.readVarInt(this::readByte);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public long readVarLong() {
        try {
            return IOUtil.readVarLong(this::readByte);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean[] readBooleanArray() {
        boolean[] result = new boolean[16];
        int size = 0;

        while (true) {
            byte b = readByte();
            boolean hasNext = (b & 0x80) != 0;
            if (size + 7 > result.length) {
                result = Arrays.copyOf(result, result.length * 2);
            }

            for (int i = 0; i < 7; i++) {
                result[size++] = (b & (1 << i)) != 0;
            }

            if (!hasNext) {
                break;
            }
        }

        return Arrays.copyOf(result, size);
    }

    public int[] readIdsArray() {
        int length = readVarInt();
        int[] array = new int[length];
        int index = 0;
        int start = readVarInt();

        BitSet bitSet = BitSet.valueOf(readBytes((int) Math.ceil(length / 8D)));
        for (int i = 0; i < bitSet.length(); i++) {
            if (bitSet.get(i)) {
                array[index] = start + i;
                index++;
            }
        }

        return array;
    }

    @Override
    public String readString() {
        return new String(readBytes(), java.nio.charset.StandardCharsets.UTF_8);
    }

    public String readStringLetters() {
        int length = readVarInt();
        byte data = readByte();

        boolean hasUpper = (data & 1) == 1;
        int bitsPerLetter = ((data >> 1) & 0x07);

        StringBuilder str = new StringBuilder();
        byte[] bytes = readBytes();
        int bitIndex = 0;
        for (int i = 0; i < length; i++) {
            int number = 0;
            boolean upper;
            if (hasUpper) {
                upper = (bytes[bitIndex / 8] & (1 << bitIndex % 8)) != 0;
                bitIndex++;
            } else {
                upper = false;
            }

            for (int j = 0; j < bitsPerLetter; j++) {
                if ((bytes[bitIndex / 8] & (1 << bitIndex % 8)) != 0) {
                    number |= (1 << j);
                }

                bitIndex++;
            }

            str.append(charValue(number, upper));
        }

        return str.toString();
    }

    public <V, C extends Collection<V>> C readCollection(C collection, Function<SmartByteBuf, V> reader) {
        for (int i = 0; i < readVarInt(); i++) {
            collection.add(reader.apply(this));
        }

        return collection;
    }

    public UUID readUUID() {
        return new UUID(readLong(), readLong());
    }

    public <O> O readObject() {
        return BufferedSerializer.get().deserializeIncludeId(this);
    }

    public ByteMap readByteMap() {
        return ByteMap.of(bytes);
    }

    protected final int asciiStartIndex(boolean upper) {
        return upper ? 65 : 97;
    }

    protected final int charValue(char value, boolean upper) {
        return value - asciiStartIndex(upper);
    }

    protected final char charValue(int value, boolean upper) {
        return (char) (value + asciiStartIndex(upper));
    }

    @Override
    public SmartByteBuf merge(byte[] bytes) {
        return (SmartByteBuf) super.merge(bytes);
    }

    @Override
    public SmartByteBuf merge(Buffered buffered) {
        return (SmartByteBuf) super.merge(buffered);
    }
}