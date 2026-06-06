package net.ldoin.shinnetai.buffered.bitwise.entry;

import net.ldoin.shinnetai.buffered.bitwise.BitwiseTrain;

public class BitwiseSerializerBooleanEntry implements BitwiseSerializerEntry {

    private boolean value;
    private final String name;

    public BitwiseSerializerBooleanEntry() {
        this.name = null;
    }

    public BitwiseSerializerBooleanEntry(boolean value) {
        this.value = value;
        this.name = null;
    }

    public BitwiseSerializerBooleanEntry(String name) {
        this.name = name;
    }

    public BitwiseSerializerBooleanEntry(String name, boolean value) {
        this.value = value;
        this.name = name;
    }

    public boolean getValue() {
        return value;
    }

    public String getName() {
        return name;
    }

    @Override
    public int bits() {
        return 1;
    }

    @Override
    public void write(BitwiseTrain train) {
        train.writeBits(value ? 1L : 0L, 1);
    }

    @Override
    public void read(BitwiseTrain train) {
        this.value = train.readBits(1) == 1L;
    }

    @Override
    public String toString() {
        return "BitwiseSerializerBooleanEntry{name='" + name + "', value=" + value + '}';
    }
}