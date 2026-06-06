package net.ldoin.shinnetai.packet.registry;

import net.ldoin.shinnetai.packet.AbstractPacket;
import net.ldoin.shinnetai.util.ReflectionUtil;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

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

    public static PacketRegistry empty() {
        return new PacketRegistry(false);
    }

    private final ConcurrentHashMap<Class<? extends AbstractPacket<?, ?>>, Integer> idPacketMap;
    private final ConcurrentHashMap<Integer, Class<? extends AbstractPacket<?, ?>>> packetMap;
    private final ConcurrentHashMap<Integer, Supplier<AbstractPacket<?, ?>>> factories;

    public PacketRegistry() {
        this(getCommons());
    }

    private PacketRegistry(boolean withCommons) {
        this.idPacketMap = new ConcurrentHashMap<>();
        this.packetMap = new ConcurrentHashMap<>();
        this.factories = new ConcurrentHashMap<>();
        if (withCommons) {
            registerAll(getCommons(), true);
        }
    }

    public PacketRegistry(PacketRegistry target) {
        this.idPacketMap = new ConcurrentHashMap<>(target.idPacketMap);
        this.packetMap = new ConcurrentHashMap<>(target.packetMap);
        this.factories = new ConcurrentHashMap<>(target.factories);
    }

    public PacketRegistry(String packageName) {
        this.idPacketMap = new ConcurrentHashMap<>();
        this.packetMap = new ConcurrentHashMap<>();
        this.factories = new ConcurrentHashMap<>();

        ReflectionUtil.getClassesImplement(packageName, AbstractPacket.class).forEach(packetClass -> {
            ShinnetaiPacket annotation = packetClass.getAnnotation(ShinnetaiPacket.class);
            if (annotation != null) {
                register(annotation.id(), asPacketClass(packetClass));
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

    public int getId(Class<?> clazz) {
        if (!AbstractPacket.class.isAssignableFrom(clazz)) {
            throw new IllegalArgumentException("Class is not a packet: " + clazz.getName());
        }

        Class<? extends AbstractPacket<?, ?>> packetClass = asPacketClass(clazz.asSubclass(AbstractPacket.class));
        Integer id = idPacketMap.get(packetClass);
        if (id == null) {
            throw new IllegalArgumentException("No id registered for class: " + clazz.getName());
        }

        return id;
    }

    public PacketRegistry register(Class<? extends AbstractPacket<?, ?>> packetClass) {
        ShinnetaiPacket annotation = packetClass.getAnnotation(ShinnetaiPacket.class);
        if (annotation != null) {
            return register(annotation.id(), packetClass);
        }

        return this;
    }

    public PacketRegistry register(int id, Class<? extends AbstractPacket<?, ?>> packetClass) {
        return register(id, packetClass, false);
    }

    public PacketRegistry register(int id, Class<? extends AbstractPacket<?, ?>> packetClass, boolean override) {
        if (id == 0) {
            throw new IllegalArgumentException("Packet id 0 is reserved and cannot be registered");
        }

        if (Modifier.isAbstract(packetClass.getModifiers()) || packetClass.isInterface()) {
            throw new IllegalArgumentException("Packet class must be concrete: " + packetClass.getName());
        }

        if (!override && packetMap.containsKey(id)) {
            throw new IllegalArgumentException("Packet with id " + id + " already exists");
        }

        if (packetMap.containsKey(id)) {
            idPacketMap.remove(packetMap.get(id));
        }

        Constructor<? extends AbstractPacket<?, ?>> constructor;
        try {
            constructor = packetClass.getDeclaredConstructor();
            try {
                constructor.setAccessible(true);
            } catch (RuntimeException e) {
                try {
                    constructor = packetClass.getConstructor();
                } catch (NoSuchMethodException ex) {
                    throw new IllegalArgumentException(
                            "Cannot access no-arg constructor of " + packetClass.getName() +
                            ". Either make it public or open the containing module to reflection.", e);
                }
            }
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Packet class must have a no-arg constructor: " + packetClass.getName(), e);
        }

        final Constructor<? extends AbstractPacket<?, ?>> resolvedConstructor = constructor;
        packetMap.put(id, packetClass);
        idPacketMap.put(packetClass, id);
        factories.put(id, () -> {
            try {
                return resolvedConstructor.newInstance();
            } catch (Exception e) {
                throw new RuntimeException("Failed to create packet for id: " + id, e);
            }
        });

        return this;
    }

    public boolean containsId(int id) {
        return packetMap.containsKey(id);
    }

    public boolean containsPacket(Class<? extends AbstractPacket<?, ?>> packetClass) {
        return idPacketMap.containsKey(packetClass);
    }

    public int size() {
        return packetMap.size();
    }

    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        packetMap.forEach((id, clazz) -> {
            if (id == 0) {
                errors.add("Packet id 0 is reserved: " + clazz.getName());
            }

            if (Modifier.isAbstract(clazz.getModifiers()) || clazz.isInterface()) {
                errors.add("Packet class must be concrete: " + clazz.getName());
            }

            if (!idPacketMap.containsKey(clazz)) {
                errors.add("Packet class missing reverse id mapping: " + clazz.getName());
            }

            if (!factories.containsKey(id)) {
                errors.add("Packet id missing factory: " + id);
            }
        });

        idPacketMap.forEach((clazz, id) -> {
            Class<? extends AbstractPacket<?, ?>> mapped = packetMap.get(id);
            if (mapped == null || !mapped.equals(clazz)) {
                errors.add("Reverse mapping mismatch for packet class: " + clazz.getName());
            }
        });

        return Collections.unmodifiableList(errors);
    }

    public PacketRegistry validateOrThrow() {
        List<String> errors = validate();
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Invalid packet registry: " + String.join("; ", errors));
        }

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

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Class<? extends AbstractPacket<?, ?>> asPacketClass(Class<? extends AbstractPacket> packetClass) {
        return (Class<? extends AbstractPacket<?, ?>>) packetClass;
    }
}