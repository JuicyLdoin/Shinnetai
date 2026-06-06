package net.ldoin.shinnetai.serializer;

import com.fasterxml.jackson.databind.ObjectMapper;

public final class JsonPacketSerializer extends JacksonPacketSerializer {

    public static final JsonPacketSerializer INSTANCE = new JsonPacketSerializer();

    public JsonPacketSerializer() {
        super(new ObjectMapper());
    }

    @Override
    public String name() {
        return "json";
    }
}