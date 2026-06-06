package net.ldoin.shinnetai.packet.common;

import net.ldoin.shinnetai.buffered.buf.smart.ReadOnlySmartByteBuf;
import net.ldoin.shinnetai.buffered.buf.smart.WriteOnlySmartByteBuf;
import net.ldoin.shinnetai.packet.EmptyPacket;
import net.ldoin.shinnetai.packet.registry.ShinnetaiPacket;
import net.ldoin.shinnetai.packet.serializer.BinaryPacketSerializer;
import net.ldoin.shinnetai.packet.serializer.PacketSerializer;
import net.ldoin.shinnetai.protocol.ShinnetaiFeature;

import java.util.Set;

@ShinnetaiPacket(id = -1)
public class HandshakePacket extends EmptyPacket {

    private int protocolVersion;
    private int magic;
    private long featureFlags;
    private String sessionToken;
    private long handshakeTimestamp;

    public HandshakePacket() {
    }

    public HandshakePacket(int protocolVersion, int magic) {
        this.protocolVersion = protocolVersion;
        this.magic = magic;
    }

    public HandshakePacket(int protocolVersion, int magic, long featureFlags) {
        this.protocolVersion = protocolVersion;
        this.magic = magic;
        this.featureFlags = featureFlags;
    }

    public HandshakePacket(int protocolVersion, int magic, long featureFlags, String sessionToken) {
        this.protocolVersion = protocolVersion;
        this.magic = magic;
        this.featureFlags = featureFlags;
        this.sessionToken = sessionToken;
    }

    public HandshakePacket(int protocolVersion, int magic, long featureFlags, String sessionToken, long handshakeTimestamp) {
        this.protocolVersion = protocolVersion;
        this.magic = magic;
        this.featureFlags = featureFlags;
        this.sessionToken = sessionToken;
        this.handshakeTimestamp = handshakeTimestamp;
    }

    public int getProtocolVersion() {
        return protocolVersion;
    }

    public int getMagic() {
        return magic;
    }

    public long getFeatureFlags() {
        return featureFlags;
    }

    public Set<ShinnetaiFeature> getFeatures() {
        return ShinnetaiFeature.fromFlags(featureFlags);
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public long getHandshakeTimestamp() {
        return handshakeTimestamp;
    }

    @Override
    public void read(ReadOnlySmartByteBuf buf) {
        this.protocolVersion = buf.readVarInt();
        this.magic = buf.readInt();
        this.featureFlags = buf.remain() > 0 ? buf.readVarLong() : 0;
        if (buf.remain() > 0 && buf.readBoolean()) {
            this.sessionToken = buf.readString();
        } else {
            this.sessionToken = null;
        }

        this.handshakeTimestamp = buf.remain() > 0 ? buf.readVarLong() : 0;
    }

    @Override
    public void write(WriteOnlySmartByteBuf buf) {
        buf.writeVarInt(protocolVersion);
        buf.writeInt(magic);
        buf.writeVarLong(featureFlags);
        boolean hasToken = sessionToken != null && !sessionToken.isBlank();
        buf.writeBoolean(hasToken);
        if (hasToken) {
            buf.writeString(sessionToken);
        }
        
        buf.writeVarLong(handshakeTimestamp);
    }

    @Override
    public PacketSerializer serializer() {
        return BinaryPacketSerializer.INSTANCE;
    }
}