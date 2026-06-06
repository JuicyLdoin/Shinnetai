package net.ldoin.shinnetai.serializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;

public final class CborPacketSerializer extends JacksonPacketSerializer {

    public static final CborPacketSerializer INSTANCE = new CborPacketSerializer();

    public CborPacketSerializer() {
        super(new ObjectMapper(new CBORFactory()));
    }

    @Override
    public String name() {
        return "cbor";
    }
}