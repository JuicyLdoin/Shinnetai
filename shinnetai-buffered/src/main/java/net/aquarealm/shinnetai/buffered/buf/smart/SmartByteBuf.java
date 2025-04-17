package net.aquarealm.shinnetai.buffered.buf.smart;

import net.aquarealm.shinnetai.buffered.Buffered;
import net.aquarealm.shinnetai.buffered.BufferedSerializer;
import net.aquarealm.shinnetai.buffered.buf.ByteBuf;

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

    protected SmartByteBuf() {
    }

    protected SmartByteBuf(byte[] bytes) {
        super(bytes);
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
        int length = value.length;
        writeVarInt(length);
        byte b = 0;

        for (int i = 0; i < length; i++) {
            if (i >= 7 && i % 7 == 0) {
                writeByte(b);
                b = 0;
            }

            b |= (byte) ((value[i] ? 1 : 0) << i % 7);
        }

        if (length % 8 != 0) {
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
        int length = value.length();
        boolean[] index = new boolean[length];

        for (int i = 0; i < length; i++) {
            index[i] = value.charAt(i) >= 128;
        }

        writeVarInt(length);
        writeBooleanArray(index);

        for (char c : value.toCharArray()) {
            if (c >= 128) {
                writeShort(c);
            } else {
                writeByte((byte) c);
            }
        }

        return this;
    }

    protected final int bitsForWrite(int value) {
        return (int) Math.ceil(Math.log(value + 1) / Math.log(2));
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

        byte data = 0;
        int bitsPerLetter = Math.max(1, bitsForWrite(maxValue - asciiStartIndex));

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

    public SmartByteBuf writeUUID(UUID value) {
        writeLong(value.getMostSignificantBits());
        writeLong(value.getLeastSignificantBits());
        return this;
    }

    public <O> SmartByteBuf writeObject(O object) {
        BufferedSerializer.get().serializeIncludeId(object, this);
        return this;
    }

    public byte[] readBytes() {
        return readBytes(readVarInt());
    }

    public int readVarInt() {
        int result = 0;
        int shift = 0;

        for (int i = 0; i < 5; i++) {
            byte b = readByte();
            result |= (b & 0x7F) << shift;

            if ((b & 0x80) == 0) {
                return result;
            }

            shift += 7;
        }

        throw new IllegalArgumentException("VarInt is too big");
    }

    public long readVarLong() {
        long result = 0;
        int shift = 0;

        for (int i = 0; i < 10; i++) {
            byte b = readByte();
            result |= (long) (b & 0x7F) << shift;

            if ((b & 0x80) == 0) {
                return result;
            }

            shift += 7;
        }

        throw new IllegalArgumentException("VarLong is too big");
    }

    public boolean[] readBooleanArray() {
        int size = readVarInt();
        boolean[] array = new boolean[size];
        byte b = 0;

        for (int i = 0; i < size; i++) {
            if (i % 7 == 0) {
                b = readByte();
            }

            array[i] = (b & (1 << i % 7)) != 0;
        }

        return array;
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
        StringBuilder str = new StringBuilder();
        int length = readVarInt();
        boolean[] index = readBooleanArray();

        for (int i = 0; i < length; i++) {
            if (index[i]) {
                str.append((char) readShort());
            } else {
                str.append((char) readByte());
            }
        }

        return str.toString();
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

    public <V> Collection<V> readCollection(Collection<V> collection, Function<SmartByteBuf, V> reader) {
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