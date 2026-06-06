package net.ldoin.shinnetai.serializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;

public final class SmilePacketSerializer extends JacksonPacketSerializer {

    public static final SmilePacketSerializer INSTANCE = new SmilePacketSerializer();

    public SmilePacketSerializer() {
        super(new ObjectMapper(new SmileFactory()));
    }

    @Override
    public String name() {
        return "smile";
    }
}