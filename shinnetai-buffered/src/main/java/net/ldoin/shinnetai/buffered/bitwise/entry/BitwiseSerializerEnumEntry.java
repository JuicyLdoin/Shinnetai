package net.ldoin.shinnetai.buffered.bitwise.entry;

import net.ldoin.shinnetai.buffered.bitwise.BitwiseTrain;
import net.ldoin.shinnetai.buffered.util.ByteUtil;

public class BitwiseSerializerEnumEntry<E extends Enum<E>> implements BitwiseSerializerEntry {

    private static <T extends Enum<T>> Class<T> declaredClass(T value) {
        return value.getDeclaringClass();
    }

    private static int bitsForEnum(Class<? extends Enum<?>> clazz) {
        int count = clazz.getEnumConstants().length;
        if (count <= 1) {
            return 1;
        }

        return ByteUtil.calculateBitsNeeded(count - 1);
    }

    private E value;
    private final Class<E> enumClass;
    private final int bits;
    private final String name;

    public BitwiseSerializerEnumEntry(E value) {
        this.value = value;
        this.enumClass = declaredClass(value);
        this.bits = bitsForEnum(this.enumClass);
        this.name = null;
    }

    public BitwiseSerializerEnumEntry(String name, E value) {
        this.value = value;
        this.enumClass = declaredClass(value);
        this.bits = bitsForEnum(this.enumClass);
        this.name = name;
    }

    public BitwiseSerializerEnumEntry(Class<E> enumClass) {
        this.enumClass = enumClass;
        this.bits = bitsForEnum(enumClass);
        this.name = null;
    }

    public BitwiseSerializerEnumEntry(String name, Class<E> enumClass) {
        this.enumClass = enumClass;
        this.bits = bitsForEnum(enumClass);
        this.name = name;
    }

    public E getValue() {
        return value;
    }

    public String getName() {
        return name;
    }

    @Override
    public int bits() {
        return bits;
    }

    @Override
    public void write(BitwiseTrain train) {
        train.writeBits(value.ordinal(), bits);
    }

    @Override
    public void read(BitwiseTrain train) {
        int ordinal = (int) train.readBits(bits);
        E[] constants = enumClass.getEnumConstants();
        if (ordinal < 0 || ordinal >= constants.length) {
            throw new IllegalStateException("Invalid ordinal " + ordinal + " for enum " + enumClass.getSimpleName() + " (max " + (constants.length - 1) + ')');
        }

        this.value = constants[ordinal];
    }

    @Override
    public String toString() {
        return "BitwiseSerializerEnumEntry{name='" + name + '\''
                + ", enum=" + enumClass.getSimpleName()
                + ", bits=" + bits
                + ", value=" + value + '}';
    }
}