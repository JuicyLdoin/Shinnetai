package net.ldoin.shinnetai.worker.pipeline;

import net.ldoin.shinnetai.packet.AbstractPacket;
import net.ldoin.shinnetai.packet.serializer.PacketSerializer;
import net.ldoin.shinnetai.worker.pipeline.context.ShinnetaiPipelineContext;
import net.ldoin.shinnetai.worker.pipeline.context.ShinnetaiPipelineExecutionContext;
import net.ldoin.shinnetai.worker.pipeline.handler.ShinnetaiPipelineHandlerUnit;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ShinnetaiPipeline implements Cloneable {

    private final Map<ShinnetaiPipelineHandleType, List<ShinnetaiPipelineHandlerUnit>> handlers;
    private volatile PacketSerializer serializer;

    public ShinnetaiPipeline() {
        this.handlers = new ConcurrentHashMap<>();
    }

    public ShinnetaiPipeline(Map<ShinnetaiPipelineHandleType, List<ShinnetaiPipelineHandlerUnit>> handlers) {
        this.handlers = new ConcurrentHashMap<>(handlers);
    }

    public ShinnetaiPipeline(ShinnetaiPipeline target) {
        this.handlers = new ConcurrentHashMap<>();
        for (Map.Entry<ShinnetaiPipelineHandleType, List<ShinnetaiPipelineHandlerUnit>> entry : target.getHandlers().entrySet()) {
            this.handlers.put(entry.getKey(), new CopyOnWriteArrayList<>(entry.getValue()));
        }
        this.serializer = target.serializer;
    }

    public Map<ShinnetaiPipelineHandleType, List<ShinnetaiPipelineHandlerUnit>> getHandlers() {
        return handlers;
    }

    public PacketSerializer getSerializer() {
        return serializer;
    }

    public ShinnetaiPipeline setSerializer(PacketSerializer serializer) {
        this.serializer = serializer;
        return this;
    }

    public List<ShinnetaiPipelineHandlerUnit> getHandlers(ShinnetaiPipelineHandleType type) {
        return handlers.get(type);
    }

    protected List<ShinnetaiPipelineHandlerUnit> checkType(ShinnetaiPipelineHandleType type) {
        return handlers.computeIfAbsent(type, t -> new CopyOnWriteArrayList<>());
    }

    public ShinnetaiPipeline add(ShinnetaiPipelineHandleType type, ShinnetaiPipelineHandlerUnit handler, int index) {
        checkType(type).add(index, handler);
        return this;
    }

    public ShinnetaiPipeline addLast(ShinnetaiPipelineHandleType type, ShinnetaiPipelineHandlerUnit handler) {
        checkType(type).add(handler);
        return this;
    }

    public ShinnetaiPipeline add(ShinnetaiPipelineHandleType type, ShinnetaiPipelineHandlerUnit handler) {
        return addLast(type, handler);
    }

    public ShinnetaiPipeline addFirst(ShinnetaiPipelineHandleType type, ShinnetaiPipelineHandlerUnit handler) {
        checkType(type).addFirst(handler);
        return this;
    }

    public ShinnetaiPipeline addAll(ShinnetaiPipelineHandleType type, ShinnetaiPipelineHandlerUnit... handlers) {
        checkType(type).addAll(Arrays.asList(handlers));
        return this;
    }

    public ShinnetaiPipeline addAll(ShinnetaiPipelineHandleType type, List<ShinnetaiPipelineHandlerUnit> handlers) {
        checkType(type).addAll(handlers);
        return this;
    }

    public ShinnetaiPipeline remove(ShinnetaiPipelineHandleType type, String name) {
        List<ShinnetaiPipelineHandlerUnit> list = handlers.get(type);
        if (list != null && list.removeIf(h -> h.name().equalsIgnoreCase(name)) && list.isEmpty()) {
            handlers.remove(type);
        }

        return this;
    }

    public ShinnetaiPipeline clear() {
        handlers.clear();
        return this;
    }

    public AbstractPacket<?, ?> handle(ShinnetaiPipelineHandleType type, AbstractPacket<?, ?> packet) {
        if (!handlers.containsKey(type)) {
            return packet;
        }

        return createChainContext().handle(type, packet, this, 0);
    }

    public ShinnetaiPipelineContext createChainContext() {
        return new ShinnetaiPipelineExecutionContext();
    }

    @Override
    public ShinnetaiPipeline clone() {
        return new ShinnetaiPipeline(this);
    }
}