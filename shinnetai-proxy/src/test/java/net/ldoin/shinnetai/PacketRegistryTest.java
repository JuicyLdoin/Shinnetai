package net.ldoin.shinnetai;

import net.ldoin.shinnetai.buffered.buf.smart.ReadOnlySmartByteBuf;
import net.ldoin.shinnetai.buffered.buf.smart.WriteOnlySmartByteBuf;
import net.ldoin.shinnetai.packet.AbstractPacket;
import net.ldoin.shinnetai.packet.EmptyPacket;
import net.ldoin.shinnetai.packet.common.HandshakePacket;
import net.ldoin.shinnetai.packet.registry.PacketRegistry;
import net.ldoin.shinnetai.packet.registry.ShinnetaiPacket;
import net.ldoin.shinnetai.worker.ShinnetaiWorkerContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PacketRegistryTest {

    @ShinnetaiPacket(id = 100)
    public static class CustomPacket extends EmptyPacket {
    }

    @ShinnetaiPacket(id = 101)
    public static class AnotherCustomPacket extends EmptyPacket {
    }

    public abstract static class AbstractCustomPacket extends EmptyPacket {
    }

    @Test
    void commons_containsHandshakePacket() {
        PacketRegistry registry = new PacketRegistry(PacketRegistry.getCommons());
        assertDoesNotThrow(() -> registry.getId(HandshakePacket.class));
    }

    @Test
    void createPacket_returnsNewInstance() {
        PacketRegistry registry = new PacketRegistry(PacketRegistry.getCommons());
        int id = registry.getId(HandshakePacket.class);
        AbstractPacket<?, ?> packet = registry.createPacket(id);
        assertNotNull(packet);
        assertInstanceOf(HandshakePacket.class, packet);
    }

    @Test
    void createPacket_twiceReturnsDifferentInstances() {
        PacketRegistry registry = new PacketRegistry(PacketRegistry.getCommons());
        int id = registry.getId(HandshakePacket.class);
        AbstractPacket<?, ?> p1 = registry.createPacket(id);
        AbstractPacket<?, ?> p2 = registry.createPacket(id);
        assertNotSame(p1, p2);
    }

    @Test
    void register_customPacket_byId() {
        PacketRegistry registry = new PacketRegistry(PacketRegistry.getCommons());
        registry.register(100, CustomPacket.class);
        assertEquals(100, registry.getId(CustomPacket.class));
    }

    @Test
    void register_customPacket_createByFactory() {
        PacketRegistry registry = new PacketRegistry(PacketRegistry.getCommons());
        registry.register(100, CustomPacket.class);
        AbstractPacket<?, ?> packet = registry.createPacket(100);
        assertInstanceOf(CustomPacket.class, packet);
    }

    @Test
    void register_duplicateId_throwsWithoutOverride() {
        PacketRegistry registry = new PacketRegistry(PacketRegistry.getCommons());
        registry.register(100, CustomPacket.class);
        assertThrows(IllegalArgumentException.class,
                () -> registry.register(100, AnotherCustomPacket.class));
    }

    @Test
    void register_override_replacesExistingPacket() {
        PacketRegistry registry = new PacketRegistry(PacketRegistry.getCommons());
        registry.register(100, CustomPacket.class);
        registry.register(100, AnotherCustomPacket.class, true);
        assertInstanceOf(AnotherCustomPacket.class, registry.createPacket(100));
    }

    @Test
    void getId_unknownClass_throws() {
        PacketRegistry registry = new PacketRegistry(PacketRegistry.getCommons());
        assertThrows(IllegalArgumentException.class, () -> registry.getId(CustomPacket.class));
    }

    @Test
    void createPacket_unknownId_throws() {
        PacketRegistry registry = new PacketRegistry(PacketRegistry.getCommons());
        assertThrows(IllegalArgumentException.class, () -> registry.createPacket(99999));
    }

    @Test
    void commons_handshakeId_isMinusOne() {
        PacketRegistry registry = new PacketRegistry(PacketRegistry.getCommons());
        assertEquals(-1, registry.getId(HandshakePacket.class));
    }

    @Test
    void copyConstructor_isIndependent() {
        PacketRegistry original = new PacketRegistry(PacketRegistry.getCommons());
        PacketRegistry copy = new PacketRegistry(original);
        copy.register(100, CustomPacket.class);
        assertThrows(IllegalArgumentException.class, () -> original.getId(CustomPacket.class));
    }

    @Test
    void emptyRegistry_disablesReflectionAndCommonsByDefault() {
        PacketRegistry registry = PacketRegistry.empty();
        assertEquals(0, registry.size());
        assertThrows(IllegalArgumentException.class, () -> registry.getId(HandshakePacket.class));
    }

    @Test
    void register_zeroId_throws() {
        PacketRegistry registry = PacketRegistry.empty();
        assertThrows(IllegalArgumentException.class, () -> registry.register(0, CustomPacket.class));
    }

    @Test
    void register_abstractPacket_throwsAtStartup() {
        PacketRegistry registry = PacketRegistry.empty();
        assertThrows(IllegalArgumentException.class, () -> registry.register(102, AbstractCustomPacket.class));
    }

    @Test
    void validateOrThrow_acceptsHealthyRegistry() {
        PacketRegistry registry = PacketRegistry.empty().register(100, CustomPacket.class);
        assertDoesNotThrow(registry::validateOrThrow);
        assertTrue(registry.validate().isEmpty());
    }
}
