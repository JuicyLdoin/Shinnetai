package net.ldoin.shinnetai.buffered.bitwise;

import net.ldoin.shinnetai.buffered.bitwise.entry.BitwiseSerializerEntry;

import java.util.LinkedList;
import java.util.List;

public class BitwiseSerializer {

    public static BitwiseSerializerBuilder builder() {
        return new BitwiseSerializerBuilder();
    }

    private final List<BitwiseSerializerEntry> entries;
    private int sum = 0;

    public BitwiseSerializer() {
        this.entries = new LinkedList<>();
    }

    public BitwiseSerializer(List<BitwiseSerializerEntry> entries) {
        this.entries = entries;
        for (BitwiseSerializerEntry entry : entries) {
            sum += entry.bits();
        }
    }

    public BitwiseSerializer addEntry(BitwiseSerializerEntry entry) {
        entries.add(entry);
        sum += entry.bits();
        return this;
    }

    public byte[] pack() {
        BitwiseTrain train = new BitwiseTrain(sum);
        for (BitwiseSerializerEntry entry : entries) {
            entry.write(train);
        }

        return train.bytes();
    }

    public void unpack(byte[] bytes) {
        BitwiseTrain train = new BitwiseTrain(bytes);
        for (BitwiseSerializerEntry entry : entries) {
            entry.read(train);
        }
    }
}