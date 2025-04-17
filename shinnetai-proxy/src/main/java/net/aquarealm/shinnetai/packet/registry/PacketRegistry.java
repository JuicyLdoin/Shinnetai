package net.aquarealm.shinnetai.packet.registry;

import net.aquarealm.shinnetai.packet.AbstractPacket;
import net.aquarealm.shinnetai.util.ReflectionUtil;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

public class PacketRegistry {

    private static final PacketRegistry COMMONS = new PacketRegistry("net.aquarealm.shinnetai.packet.common");

    public static PacketRegistry getCommons() {
        return COMMONS;
    }

    private final Map<Class<? extends AbstractPacket<?, ?>>, Integer> idPacketMap;
    private final Map<Integer, Class<? extends AbstractPacket<?, ?>>> packetMap;

    public PacketRegistry() {
        idPacketMap = new HashMap<>(COMMONS.idPacketMap);
        packetMap = new HashMap<>(COMMONS.packetMap);
    }

    @SuppressWarnings("unchecked")
    public PacketRegistry(String packageName) {
        idPacketMap = new HashMap<>();
        packetMap = new HashMap<>();

        ReflectionUtil.getClassesImplement(packageName, AbstractPacket.class).forEach(packetClass -> {
            ShinnetaiPacket packet = packetClass.getAnnotation(ShinnetaiPacket.class);
            if (packet == null) {
                return;
            }

            register(packet.id(), (Class<? extends AbstractPacket<?, ?>>) packetClass);
        });
    }

    public AbstractPacket<?, ?> createPacket(int id) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        return get(id).getConstructor().newInstance();
    }

    protected Class<? extends AbstractPacket<?, ?>> get(int id) {
        return packetMap.get(id);
    }

    public int getId(Class<? extends AbstractPacket<?, ?>> clazz) {
        return idPacketMap.get(clazz);
    }

    public void register(int id, Class<? extends AbstractPacket<?, ?>> packet) {
        if (packetMap.containsKey(id)) {
            throw new IllegalArgumentException("Packet with id " + id + " already exists");
        }

        idPacketMap.put(packet, id);
        packetMap.put(id, packet);
    }

    public void unregister(int id) {
        idPacketMap.remove(get(id));
        packetMap.remove(id);
    }

    public void clear() {
        idPacketMap.clear();
        packetMap.clear();
    }

    public void registerAll(PacketRegistry packetRegistry) {
        for (Map.Entry<Class<? extends AbstractPacket<?, ?>>, Integer> entry : packetRegistry.idPacketMap.entrySet()) {
            int id = entry.getValue();
            register(id, entry.getKey());
        }
    }
}