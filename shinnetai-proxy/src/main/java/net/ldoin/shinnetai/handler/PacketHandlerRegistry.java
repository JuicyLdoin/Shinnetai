package net.ldoin.shinnetai.handler;

import net.ldoin.shinnetai.packet.AbstractPacket;
import net.ldoin.shinnetai.worker.ShinnetaiWorkerContext;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PacketHandlerRegistry {

    private static final Logger LOGGER = Logger.getLogger(PacketHandlerRegistry.class.getName());

    public static PacketHandlerRegistry create() {
        return new PacketHandlerRegistry();
    }

    private final Map<Class<?>, List<BiConsumer<AbstractPacket<?, ?>, ShinnetaiWorkerContext<?>>>> handlers = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <P extends AbstractPacket<?, ?>> PacketHandlerRegistry on(Class<P> packetType, BiConsumer<P, ShinnetaiWorkerContext<?>> handler) {
        handlers.computeIfAbsent(packetType, k -> new ArrayList<>()).add((BiConsumer<AbstractPacket<?, ?>, ShinnetaiWorkerContext<?>>) handler);
        return this;
    }

    public PacketHandlerRegistry register(Object handlerInstance) {
        for (Method method : handlerInstance.getClass().getMethods()) {
            if (!method.isAnnotationPresent(PacketHandler.class)) {
                continue;
            }

            Class<?>[] params = method.getParameterTypes();
            if (params.length == 0 || !AbstractPacket.class.isAssignableFrom(params[0])) {
                continue;
            }

            boolean hasContext = params.length >= 2 && ShinnetaiWorkerContext.class.isAssignableFrom(params[1]);

            @SuppressWarnings("unchecked")
            Class<? extends AbstractPacket<?, ?>> packetType = (Class<? extends AbstractPacket<?, ?>>) params[0];
            method.setAccessible(true);
            handlers.computeIfAbsent(packetType, k -> new ArrayList<>()).add((packet, ctx) -> {
                try {
                    if (hasContext) {
                        method.invoke(handlerInstance, packet, ctx);
                    } else {
                        method.invoke(handlerInstance, packet);
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error invoking @PacketHandler method " + method.getName(), e);
                }
            });
        }

        return this;
    }

    public boolean dispatch(AbstractPacket<?, ?> packet, ShinnetaiWorkerContext<?> ctx) {
        List<BiConsumer<AbstractPacket<?, ?>, ShinnetaiWorkerContext<?>>> list = handlers.get(packet.getClass());
        if (list == null || list.isEmpty()) {
            return false;
        }

        for (BiConsumer<AbstractPacket<?, ?>, ShinnetaiWorkerContext<?>> handler : list) {
            try {
                handler.accept(packet, ctx);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error dispatching packet to handler", e);
            }
        }

        return true;
    }

    public boolean hasHandlers(Class<? extends AbstractPacket<?, ?>> packetType) {
        List<?> list = handlers.get(packetType);
        return list != null && !list.isEmpty();
    }
}