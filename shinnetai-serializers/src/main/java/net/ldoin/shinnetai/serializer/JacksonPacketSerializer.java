package net.ldoin.shinnetai.serializer;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import net.ldoin.shinnetai.packet.AbstractPacket;
import net.ldoin.shinnetai.packet.serializer.PacketSerializer;

import java.io.IOException;

public abstract class JacksonPacketSerializer implements PacketSerializer {

    @JsonIgnoreProperties({"clientWorker", "serverWorker"})
    private abstract static class AbstractPacketMixin {
    }

    protected final ObjectMapper mapper;

    protected JacksonPacketSerializer(ObjectMapper mapper) {
        this.mapper = mapper
                .setVisibility(mapper.getSerializationConfig().getDefaultVisibilityChecker()
                        .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                        .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                        .withIsGetterVisibility(JsonAutoDetect.Visibility.NONE)
                        .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                        .withCreatorVisibility(JsonAutoDetect.Visibility.NONE))
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
                .addMixIn(AbstractPacket.class, AbstractPacketMixin.class);
    }

    @Override
    public byte[] serialize(AbstractPacket<?, ?> packet) throws IOException {
        return mapper.writeValueAsBytes(packet);
    }

    @Override
    public void deserialize(AbstractPacket<?, ?> packet, byte[] data, int offset, int length) throws IOException {
        mapper.readerForUpdating(packet).readValue(data, offset, length);
    }
}