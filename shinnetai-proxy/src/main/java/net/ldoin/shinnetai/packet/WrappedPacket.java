package net.ldoin.shinnetai.packet;

import net.ldoin.shinnetai.buffered.buf.smart.ReadOnlySmartByteBuf;
import net.ldoin.shinnetai.buffered.buf.smart.WriteOnlySmartByteBuf;
import net.ldoin.shinnetai.packet.response.PacketResponseOptions;

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

    protected WrappedPacket(Builder builder) {
        this.options = builder.options;
        this.packet = builder.packet;
        this.responseOptions = builder.responseOptions;
        this.buffer = builder.buffer;
        this.streamId = builder.streamId;
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
        return options[option.ordinal()];
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

    public void write(WriteOnlySmartByteBuf buf) {
        buf.writeBooleanArray(options);
        if (getOptionValue(PacketOptions.REQUIRE_RESPONSE) || getOptionValue(PacketOptions.WAIT_RESPONSE) || getOptionValue(PacketOptions.IS_RESPONSE)) {
            responseOptions.write(buf);
        }

        if (getOptionValue(PacketOptions.IN_STREAM)) {
            buf.writeVarInt(streamId);
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
    }

    public static class Builder {

        private final AbstractPacket<?, ?> packet;
        private final boolean[] options;
        private PacketResponseOptions responseOptions;
        private WriteOnlySmartByteBuf buffer;
        private int streamId;

        public Builder(AbstractPacket<?, ?> packet) {
            this.packet = Objects.requireNonNull(packet, "Packet cannot be null");
            this.options = new boolean[7];
        }

        public Builder(WrappedPacket packet) {
            this.packet = packet.packet;
            this.options = packet.options;
            this.responseOptions = packet.responseOptions;
            this.buffer = packet.buffer;
            this.streamId = packet.streamId;
        }

        public Builder setOption(PacketOptions option, boolean value) {
            this.options[option.ordinal()] = value;
            return this;
        }

        public Builder withOption(PacketOptions option) {
            return setOption(option, true);
        }

        public Builder withOptions(PacketOptions... options) {
            for (PacketOptions option : options) {
                withOption(option);
            }
            return this;
        }

        public Builder withoutOption(PacketOptions option) {
            return setOption(option, false);
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

        public WrappedPacket build() {
            return new WrappedPacket(this);
        }
    }
}