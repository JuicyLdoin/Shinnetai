package net.ldoin.shinnetai.packet.registry;

import net.ldoin.shinnetai.packet.AbstractPacket;

public class ImmutablePacketRegistry extends PacketRegistry {

    public ImmutablePacketRegistry() {
        super();
    }

    public ImmutablePacketRegistry(PacketRegistry target) {
        super(target);
    }

    public ImmutablePacketRegistry(String packageName) {
        super(packageName);
    }

    @Override
    public PacketRegistry register(int id, Class<? extends AbstractPacket<?, ?>> packet) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PacketRegistry register(int id, Class<? extends AbstractPacket<?, ?>> packet, boolean override) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PacketRegistry unregister(int id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PacketRegistry clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public PacketRegistry registerAll(PacketRegistry packetRegistry) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PacketRegistry registerAll(PacketRegistry packetRegistry, boolean override) {
        throw new UnsupportedOperationException();
    }

    public PacketRegistry toMutable() {
        return new PacketRegistry(this);
    }
}