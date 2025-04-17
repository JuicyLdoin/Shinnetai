package net.ldoin.shinnetai.packet.common;

import net.ldoin.shinnetai.buffered.buf.smart.ReadOnlySmartByteBuf;
import net.ldoin.shinnetai.buffered.buf.smart.WriteOnlySmartByteBuf;
import net.ldoin.shinnetai.packet.EmptyPacket;
import net.ldoin.shinnetai.packet.registry.ShinnetaiPacket;

@ShinnetaiPacket(id = -1)
public class HandshakePacket extends EmptyPacket {

    private int protocolVersion;
    private int magic;

    public HandshakePacket() {
    }

    public HandshakePacket(int protocolVersion, int magic) {
        this.protocolVersion = protocolVersion;
        this.magic = magic;
    }

    public int getProtocolVersion() {
        return protocolVersion;
    }

    public int getMagic() {
        return magic;
    }

    @Override
    public void read(ReadOnlySmartByteBuf buf) {
        this.protocolVersion = buf.readVarInt();
        this.magic = buf.readInt();
    }

    @Override
    public void write(WriteOnlySmartByteBuf buf) {
        buf.writeVarInt(protocolVersion);
        buf.writeInt(magic);
    }
}
