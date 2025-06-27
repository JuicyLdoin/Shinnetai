package net.ldoin.shinnetai.worker.pipeline.handler.unit.parallel;

import net.ldoin.shinnetai.packet.AbstractPacket;
import net.ldoin.shinnetai.worker.pipeline.context.ShinnetaiPipelineContext;
import net.ldoin.shinnetai.worker.pipeline.handler.ShinnetaiPipelineHandler;
import net.ldoin.shinnetai.worker.pipeline.handler.ShinnetaiPipelineHandlerUnit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

public class ParallelHandlerUnit implements ShinnetaiPipelineHandlerUnit {

    public static ParallelHandlerUnit of(String name, ExecutorService service, ShinnetaiPipelineHandler... handlers) {
        return new ParallelHandlerUnit(name, service, handlers);
    }

    public static ParallelHandlerUnit of(String name, ExecutorService service, List<ShinnetaiPipelineHandler> handlers) {
        return new ParallelHandlerUnit(name, service, handlers);
    }

    public static ParallelHandlerUnit of(String name, ExecutorService service, ParallelPipelineHandlerFactory factory, int amount) {
        return new ParallelHandlerUnit(name, service, factory, amount);
    }

    private final String name;
    private final List<ShinnetaiPipelineHandler> handlers;
    private final ExecutorService executor;

    protected ParallelHandlerUnit(String name, ExecutorService executor, ShinnetaiPipelineHandler... handlers) {
        this(name, executor, Arrays.asList(handlers));
    }

    protected ParallelHandlerUnit(String name, ExecutorService executor, List<ShinnetaiPipelineHandler> handlers) {
        this.name = name;
        this.handlers = handlers;
        this.executor = executor;
    }

    protected ParallelHandlerUnit(String name, ExecutorService executor, ParallelPipelineHandlerFactory factory, int amount) {
        this.name = name;
        this.handlers = new ArrayList<>();
        for (int i = 0; i < amount; i++) {
            this.handlers.add(factory.create(i));
        }

        this.executor = executor;
    }

    @Override
    public AbstractPacket<?, ?> handle(AbstractPacket<?, ?> packet, ShinnetaiPipelineContext context) {
        CountDownLatch latch = new CountDownLatch(handlers.size());
        AtomicReference<AbstractPacket<?, ?>> result = new AtomicReference<>(packet);
        for (ShinnetaiPipelineHandler handler : handlers) {
            executor.execute(() -> {
                try {
                    AbstractPacket<?, ?> r = handler.handle(result.get(), context);
                    if (r == null) {
                        result.set(null);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }

        return result.get();
    }

    @Override
    public String name() {
        return name;
    }
}