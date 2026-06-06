package net.ldoin.shinnetai.buffered.bitwise.entry;

import net.ldoin.shinnetai.buffered.bitwise.BitwiseTrain;

public interface BitwiseSerializerEntry {

    int bits();

    void write(BitwiseTrain train);

    void read(BitwiseTrain train);

}