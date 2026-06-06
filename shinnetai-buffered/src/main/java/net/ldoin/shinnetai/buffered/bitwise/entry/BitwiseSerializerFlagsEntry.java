package net.ldoin.shinnetai.buffered.bitwise.entry;

import net.ldoin.shinnetai.buffered.bitwise.BitwiseTrain;

import java.util.LinkedHashMap;
import java.util.Map;

public class BitwiseSerializerFlagsEntry implements BitwiseSerializerEntry {

    private final LinkedHashMap<String, Boolean> flags;

    public BitwiseSerializerFlagsEntry(String... flagNames) {
        this.flags = new LinkedHashMap<>(flagNames.length * 2);
        for (String name : flagNames) {
            flags.put(name, false);
        }
    }

    public BitwiseSerializerFlagsEntry set(String flag, boolean value) {
        if (!flags.containsKey(flag)) {
            throw new IllegalArgumentException("Unknown flag: '" + flag + '\'');
        }

        flags.put(flag, value);
        return this;
    }

    public boolean get(String flag) {
        Boolean value = flags.get(flag);
        if (value == null) {
            throw new IllegalArgumentException("Unknown flag: '" + flag + '\'');
        }

        return value;
    }

    public Map<String, Boolean> getAll() {
        return Map.copyOf(flags);
    }

    @Override
    public int bits() {
        return flags.size();
    }

    @Override
    public void write(BitwiseTrain train) {
        long packed = 0;
        int i = 0;
        for (boolean value : flags.values()) {
            if (value) packed |= (1L << i);
            i++;
        }

        train.writeBits(packed, flags.size());
    }

    @Override
    public void read(BitwiseTrain train) {
        long packed = train.readBits(flags.size());
        int i = 0;
        for (String key : flags.keySet()) {
            flags.put(key, ((packed >>> i) & 1L) == 1L);
            i++;
        }
    }

    @Override
    public String toString() {
        return "BitwiseSerializerFlagsEntry" + flags;
    }
}