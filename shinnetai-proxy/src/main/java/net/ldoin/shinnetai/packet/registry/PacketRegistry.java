package net.ldoin.shinnetai.packet.registry;

import net.ldoin.shinnetai.packet.AbstractPacket;
import net.ldoin.shinnetai.util.ReflectionUtil;

import java.lang.reflect.Constructor;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@SuppressWarnings("all")
public class PacketRegistry {

    private static class CommonsHolder {
        private static final ImmutablePacketRegistry INSTANCE = new PacketRegistry("net.ldoin.shinnetai.packet.common").toImmutable();
    }

    private static class ExtendedHolder {
        private static final ImmutablePacketRegistry INSTANCE = new PacketRegistry("net.ldoin.shinnetai.packet.extended").withCommons().toImmutable();
    }

    public static ImmutablePacketRegistry getCommons() {
        return CommonsHolder.INSTANCE;
    }

    public static ImmutablePacketRegistry getExtended() {
        return ExtendedHolder.INSTANCE;
    }

    private final ConcurrentHashMap<Class<? extends AbstractPacket<?, ?>>, Integer> idPacketMap;
    private final ConcurrentHashMap<Integer, Class<? extends AbstractPacket<?, ?>>> packetMap;
    private final ConcurrentHashMap<Integer, Supplier<AbstractPacket<?, ?>>> factories;

    public PacketRegistry() {
        this(getCommons());
    }

    public PacketRegistry(PacketRegistry target) {
        this.idPacketMap = new ConcurrentHashMap<>(target.idPacketMap);
        this.packetMap = new ConcurrentHashMap<>(target.packetMap);
        this.factories = new ConcurrentHashMap<>(target.factories);
    }

    @SuppressWarnings("unchecked")
    public PacketRegistry(String packageName) {
        this.idPacketMap = new ConcurrentHashMap<>();
        this.packetMap = new ConcurrentHashMap<>();
        this.factories = new ConcurrentHashMap<>();

        ReflectionUtil.getClassesImplement(packageName, AbstractPacket.class).forEach(packetClass -> {
            ShinnetaiPacket annotation = packetClass.getAnnotation(ShinnetaiPacket.class);
            if (annotation != null) {
                register(annotation.id(), (Class<? extends AbstractPacket<?, ?>>) packetClass);
            }
        });
    }

    public AbstractPacket<?, ?> createPacket(int id) {
        Supplier<AbstractPacket<?, ?>> factory = factories.get(id);
        if (factory == null) {
            throw new IllegalArgumentException("No packet registered with id: " + id);
        }

        return factory.get();
    }

    public int getId(Class<? extends AbstractPacket> clazz) {
        Integer id = idPacketMap.get(clazz);
        if (id == null) {
            throw new IllegalArgumentException("No id registered for class: " + clazz.getName());
        }

        return id;
    }

    public PacketRegistry register(Class<? extends AbstractPacket<?, ?>> packetClass) {
        ShinnetaiPacket annotation = packetClass.getAnnotation(ShinnetaiPacket.class);
        if (annotation != null) {
            return register(annotation.id(), (Class<? extends AbstractPacket<?, ?>>) packetClass);
        }

        return this;
    }

    public PacketRegistry register(int id, Class<? extends AbstractPacket<?, ?>> packetClass) {
        return register(id, packetClass, false);
    }

    public PacketRegistry register(int id, Class<? extends AbstractPacket<?, ?>> packetClass, boolean override) {
        if (!override && packetMap.containsKey(id)) {
            throw new IllegalArgumentException("Packet with id " + id + " already exists");
        }

        if (packetMap.containsKey(id)) {
            idPacketMap.remove(packetMap.get(id));
        }

        Constructor<? extends AbstractPacket<?, ?>> constructor;
        try {
            constructor = packetClass.getDeclaredConstructor();
            constructor.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Packet class must have a no-arg constructor: " + packetClass.getName(), e);
        }

        packetMap.put(id, packetClass);
        idPacketMap.put(packetClass, id);
        factories.put(id, () -> {
            try {
                return constructor.newInstance();
            } catch (Exception e) {
                throw new RuntimeException("Failed to create packet for id: " + id, e);
            }
        });

        return this;
    }

    public PacketRegistry unregister(int id) {
        Class<? extends AbstractPacket<?, ?>> clazz = packetMap.remove(id);
        if (clazz != null) {
            idPacketMap.remove(clazz);
            factories.remove(id);
        }

        return this;
    }

    public PacketRegistry clear() {
        idPacketMap.clear();
        packetMap.clear();
        factories.clear();
        return this;
    }

    public PacketRegistry registerAll(PacketRegistry target) {
        return registerAll(target, false);
    }

    public PacketRegistry registerAll(PacketRegistry target, boolean override) {
        target.idPacketMap.forEach((clazz, id) -> register(id, clazz, override));
        return this;
    }

    public PacketRegistry withCommons() {
        return registerAll(getCommons(), true);
    }

    public PacketRegistry withExtended() {
        return registerAll(getExtended(), true);
    }

    public ImmutablePacketRegistry toImmutable() {
        return new ImmutablePacketRegistry(this);
    }
}