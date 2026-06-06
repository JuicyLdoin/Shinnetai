package net.ldoin.shinnetai.packet;

import net.ldoin.shinnetai.buffered.buf.smart.ReadOnlySmartByteBuf;
import net.ldoin.shinnetai.buffered.buf.smart.WriteOnlySmartByteBuf;
import net.ldoin.shinnetai.packet.response.PacketResponseOptions;
import net.ldoin.shinnetai.packet.serializer.PacketSerializer;

import java.util.Objects;

public class WrappedPacket {

    public static Builder builder(AbstractPacket<?, ?> packet) {
        return new Builder(packet);
    }

    public static Builder builder(WrappedPacket packet) {
        return new Builder(packet);
    }

    public static WrappedPacket of(AbstractPacket<?, ?> packet) {
        return builder(packet).build();
    }

    public static WrappedPacket of(WrappedPacket packet) {
        return builder(packet).build();
    }

    public static WrappedPacket of(AbstractPacket<?, ?> packet, PacketResponseOptions responseOptions) {
        return builder(packet).responseOptions(responseOptions).build();
    }

    public static WrappedPacket of(WrappedPacket packet, PacketResponseOptions responseOptions) {
        return builder(packet).responseOptions(responseOptions).build();
    }

    public static WrappedPacket of(AbstractPacket<?, ?> packet, ReadOnlySmartByteBuf buffer) {
        return new WrappedPacket(packet, buffer);
    }

    protected AbstractPacket<?, ?> packet;
    protected boolean[] options;
    protected PacketResponseOptions responseOptions;
    protected WriteOnlySmartByteBuf buffer;
    protected int streamId;
    protected long packetId;
    protected PacketSerializer serializer;

    protected WrappedPacket(Builder builder) {
        this.options = builder.options;
        this.packet = builder.packet;
        this.responseOptions = builder.responseOptions;
        this.buffer = builder.buffer;
        this.streamId = builder.streamId;
        this.packetId = builder.packetId;
        this.serializer = builder.serializer;
    }

    protected WrappedPacket(AbstractPacket<?, ?> packet, ReadOnlySmartByteBuf buffer) {
        this.packet = packet;
        read(buffer);
    }

    public AbstractPacket<?, ?> getPacket() {
        return packet;
    }

    public boolean[] getOptions() {
        return options;
    }

    public boolean getOptionValue(PacketOptions option) {
        int index = option.ordinal();
        if (options == null || index >= options.length) {
            return false;
        }

        return options[index];
    }

    public PacketResponseOptions getResponseOptions() {
        return responseOptions;
    }

    public WriteOnlySmartByteBuf getBuffer() {
        return buffer;
    }

    public int getStreamId() {
        return streamId;
    }

    public long getPacketId() {
        return packetId;
    }

    public PacketSerializer getSerializer() {
        return serializer;
    }

    public void write(WriteOnlySmartByteBuf buf) {
        buf.writeBooleanArray(options);
        if (getOptionValue(PacketOptions.REQUIRE_RESPONSE) || getOptionValue(PacketOptions.WAIT_RESPONSE) || getOptionValue(PacketOptions.IS_RESPONSE)) {
            responseOptions.write(buf);
        }

        if (getOptionValue(PacketOptions.IN_STREAM)) {
            buf.writeVarInt(streamId);
        }

        if (getOptionValue(PacketOptions.DELIVERY_TRACKED) || getOptionValue(PacketOptions.DELIVERY_ACK)) {
            buf.writeVarLong(packetId);
        }
    }

    public void read(ReadOnlySmartByteBuf buf) {
        options = buf.readBooleanArray();
        if (getOptionValue(PacketOptions.REQUIRE_RESPONSE) || getOptionValue(PacketOptions.WAIT_RESPONSE) || getOptionValue(PacketOptions.IS_RESPONSE)) {
            responseOptions = PacketResponseOptions.of(buf);
        }

        if (getOptionValue(PacketOptions.IN_STREAM)) {
            streamId = buf.readVarInt();
        }

        if (getOptionValue(PacketOptions.DELIVERY_TRACKED) || getOptionValue(PacketOptions.DELIVERY_ACK)) {
            packetId = buf.readVarLong();
        }
    }

    public static class Builder {

        private final AbstractPacket<?, ?> packet;
        private final boolean[] options;
        private PacketResponseOptions responseOptions;
        private WriteOnlySmartByteBuf buffer;
        private int streamId;
        private long packetId;
        private PacketSerializer serializer;

        public Builder(AbstractPacket<?, ?> packet) {
            this.packet = Objects.requireNonNull(packet, "Packet cannot be null");
            this.options = new boolean[PacketOptions.VALUES.length];
        }

        public Builder(WrappedPacket packet) {
            this.packet = packet.packet;
            this.options = packet.options != null ? packet.options.clone() : new boolean[PacketOptions.VALUES.length];
            this.responseOptions = packet.responseOptions;
            this.buffer = packet.buffer;
            this.streamId = packet.streamId;
            this.packetId = packet.packetId;
            this.serializer = packet.serializer;
        }

        public Builder option(PacketOptions option, boolean value) {
            this.options[option.ordinal()] = value;
            return this;
        }

        public Builder serializer(PacketSerializer serializer) {
            this.serializer = serializer;
            return this;
        }

        public Builder withOption(PacketOptions option) {
            return option(option, true);
        }

        public Builder withOptions(PacketOptions... options) {
            for (PacketOptions option : options) {
                withOption(option);
            }
            return this;
        }

        public Builder withoutOption(PacketOptions option) {
            return option(option, false);
        }

        public Builder withoutOptions(PacketOptions... options) {
            for (PacketOptions option : options) {
                withoutOption(option);
            }
            return this;
        }

        public Builder responseOptions(PacketResponseOptions responseOptions) {
            if (!options[PacketOptions.REQUIRE_RESPONSE.ordinal()]) {
                if (responseOptions.isResponse()) {
                    withOption(PacketOptions.IS_RESPONSE);
                }

                if (responseOptions.isWaitResponse()) {
                    withOption(PacketOptions.WAIT_RESPONSE);
                }
            }

            this.responseOptions = responseOptions;
            return this;
        }

        public Builder buffer(WriteOnlySmartByteBuf buffer) {
            this.buffer = buffer;
            return this;
        }

        public Builder toStream(int streamId) {
            this.streamId = streamId;
            return withOption(PacketOptions.IN_STREAM);
        }

        public Builder packetId(long packetId) {
            this.packetId = packetId;
            return this;
        }

        public WrappedPacket build() {
            return new WrappedPacket(this);
        }
    }
}