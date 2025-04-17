package net.ldoin.shinnetai.buffered;

import java.io.IOException;

public interface ByteWriter {

    void write(int value) throws IOException;

}