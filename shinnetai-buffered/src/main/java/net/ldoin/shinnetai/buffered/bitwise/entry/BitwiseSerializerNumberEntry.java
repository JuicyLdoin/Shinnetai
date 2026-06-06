package net.ldoin.shinnetai.buffered.bitwise.entry;

import net.ldoin.shinnetai.buffered.bitwise.BitwiseTrain;

public class BitwiseSerializerNumberEntry implements BitwiseSerializerEntry {

    private Number value;
    private final int bits;
    private final Class<? extends Number> type;
    private final String name;

    public BitwiseSerializerNumberEntry(int bits) {
        this.bits = bits;
        this.type = Long.class;
        this.name = null;
    }

    public BitwiseSerializerNumberEntry(int bits, Class<? extends Number> type) {
        this.bits = bits;
        this.type = type;
        this.name = null;
    }

    public BitwiseSerializerNumberEntry(String name, int bits) {
        this.bits = bits;
        this.type = Long.class;
        this.name = name;
    }

    public BitwiseSerializerNumberEntry(String name, int bits, Class<? extends Number> type) {
        this.bits = bits;
        this.type = type;
        this.name = name;
    }

    public BitwiseSerializerNumberEntry(Number value, int bits) {
        this.value = value;
        this.bits = bits;
        this.type = value.getClass();
        this.name = null;
    }

    public BitwiseSerializerNumberEntry(String name, Number value, int bits) {
        this.value = value;
        this.bits = bits;
        this.type = value.getClass();
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public boolean booleanValue() {
        return value.byteValue() == 1;
    }

    public byte byteValue() {
        return value.byteValue();
    }

    public short shortValue() {
        return value.shortValue();
    }

    public int intValue() {
        return value.intValue();
    }

    public long longValue() {
        return value.longValue();
    }

    public float floatValue() {
        return value.floatValue();
    }

    public double doubleValue() {
        return value.doubleValue();
    }

    @Override
    public int bits() {
        return bits;
    }

    @Override
    public void write(BitwiseTrain train) {
        long valueLong;
        if (value instanceof Float) {
            valueLong = Float.floatToRawIntBits(value.floatValue()) & 0xFFFFFFFFL;
        } else if (value instanceof Double) {
            valueLong = Double.doubleToRawLongBits(value.doubleValue());
        } else {
            valueLong = value.longValue();
        }

        train.writeBits(valueLong, bits);
    }

    @Override
    public void read(BitwiseTrain train) {
        this.value = restoreType(train.readBits(bits));
    }

    private Number restoreType(long rawBits) {
        if (type == Byte.class) {
            return (byte) rawBits;
        }

        if (type == Short.class) {
            return (short) rawBits;
        }

        if (type == Integer.class) {
            return (int) rawBits;
        }

        if (type == Float.class) {
            return Float.intBitsToFloat((int) rawBits);
        }

        if (type == Double.class) {
            return Double.longBitsToDouble(rawBits);
        }

        return rawBits;
    }

    @Override
    public String toString() {
        return "BitwiseSerializerNumberEntry{" +
                "name='" + name + '\'' +
                ", bits=" + bits +
                ", type=" + type.getSimpleName() +
                ", value=" + value +
                '}';
    }
}