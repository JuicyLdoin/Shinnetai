package net.ldoin.shinnetai.worker.pipeline;

import net.ldoin.shinnetai.packet.AbstractPacket;
import net.ldoin.shinnetai.worker.pipeline.context.ShinnetaiPipelineContext;
import net.ldoin.shinnetai.worker.pipeline.context.ShinnetaiPipelineExecutionContext;
import net.ldoin.shinnetai.worker.pipeline.handler.ShinnetaiPipelineHandlerUnit;

import java.util.*;

public class ShinnetaiPipeline implements Cloneable {

    private final Map<ShinnetaiPipelineHandleType, LinkedList<ShinnetaiPipelineHandlerUnit>> handlers;

    public ShinnetaiPipeline() {
        this.handlers = new HashMap<>();
    }

    public ShinnetaiPipeline(Map<ShinnetaiPipelineHandleType, LinkedList<ShinnetaiPipelineHandlerUnit>> handlers) {
        this.handlers = handlers;
    }

    public ShinnetaiPipeline(ShinnetaiPipeline target) {
        this.handlers = target.getHandlers();
    }

    public Map<ShinnetaiPipelineHandleType, LinkedList<ShinnetaiPipelineHandlerUnit>> getHandlers() {
        return handlers;
    }

    public LinkedList<ShinnetaiPipelineHandlerUnit> getHandlers(ShinnetaiPipelineHandleType type) {
        return handlers.get(type);
    }

    protected LinkedList<ShinnetaiPipelineHandlerUnit> checkType(ShinnetaiPipelineHandleType type) {
        return handlers.computeIfAbsent(type, t -> new LinkedList<>());
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
        LinkedList<ShinnetaiPipelineHandlerUnit> list = handlers.get(type);
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
        try {
            return (ShinnetaiPipeline) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }
}