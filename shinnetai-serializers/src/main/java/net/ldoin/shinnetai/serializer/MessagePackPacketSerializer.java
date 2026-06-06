package net.ldoin.shinnetai.serializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.msgpack.jackson.dataformat.MessagePackFactory;

public final class MessagePackPacketSerializer extends JacksonPacketSerializer {

    public static final MessagePackPacketSerializer INSTANCE = new MessagePackPacketSerializer();

    public MessagePackPacketSerializer() {
        super(new ObjectMapper(new MessagePackFactory()));
    }

    @Override
    public String name() {
        return "msgpack";
    }
}