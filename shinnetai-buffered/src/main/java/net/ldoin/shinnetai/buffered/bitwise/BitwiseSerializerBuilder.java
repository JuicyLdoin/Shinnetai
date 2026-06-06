package net.ldoin.shinnetai.buffered.bitwise;

import net.ldoin.shinnetai.buffered.bitwise.entry.BitwiseSerializerEntry;
import net.ldoin.shinnetai.buffered.bitwise.entry.BitwiseSerializerEntryBuilder;

import java.util.ArrayList;
import java.util.List;

public class BitwiseSerializerBuilder {

    private final List<BitwiseSerializerEntry> entries = new ArrayList<>();

    public BitwiseSerializerBuilder() {
    }

    public BitwiseSerializerEntryBuilder entry() {
        return new BitwiseSerializerEntryBuilder(this);
    }

    public BitwiseSerializerBuilder entry(BitwiseSerializerEntry entry) {
        entries.add(entry);
        return this;
    }

    public void commit(BitwiseSerializerEntry entry) {
        entries.add(entry);
    }

    public BitwiseSerializer build() {
        BitwiseSerializer ser = new BitwiseSerializer();
        for (BitwiseSerializerEntry entry : entries) {
            ser.addEntry(entry);
        }

        return ser;
    }
}
