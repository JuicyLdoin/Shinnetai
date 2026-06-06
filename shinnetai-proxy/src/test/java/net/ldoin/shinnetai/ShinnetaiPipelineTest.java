package net.ldoin.shinnetai;

import net.ldoin.shinnetai.buffered.buf.smart.ReadOnlySmartByteBuf;
import net.ldoin.shinnetai.buffered.buf.smart.WriteOnlySmartByteBuf;
import net.ldoin.shinnetai.packet.AbstractPacket;
import net.ldoin.shinnetai.worker.ShinnetaiWorkerContext;
import net.ldoin.shinnetai.worker.pipeline.ShinnetaiPipeline;
import net.ldoin.shinnetai.worker.pipeline.ShinnetaiPipelineHandleType;
import net.ldoin.shinnetai.worker.pipeline.context.ShinnetaiPipelineContext;
import net.ldoin.shinnetai.worker.pipeline.handler.ShinnetaiPipelineHandler;
import net.ldoin.shinnetai.worker.pipeline.handler.unit.SingleHandlerUnit;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShinnetaiPipelineTest {

    private static class TestPacket extends AbstractPacket<ShinnetaiWorkerContext<?>, ShinnetaiWorkerContext<?>> {
        @Override public void read(ReadOnlySmartByteBuf buf) {}
        @Override public void write(WriteOnlySmartByteBuf buf) {}
    }

    private static AbstractPacket<?, ?> newPacket() {
        return new TestPacket();
    }

    private static SingleHandlerUnit tag(List<String> calls, String name) {
        return SingleHandlerUnit.of(new ShinnetaiPipelineHandler() {
            @Override
            public AbstractPacket<?, ?> handle(AbstractPacket<?, ?> packet, ShinnetaiPipelineContext ctx) {
                calls.add(name);
                return packet;
            }
        });
    }

    @Test
    void emptyPipeline_returnsPacketUnchanged() {
        ShinnetaiPipeline pipeline = new ShinnetaiPipeline();
        AbstractPacket<?, ?> packet = newPacket();
        AbstractPacket<?, ?> result = pipeline.handle(ShinnetaiPipelineHandleType.BEFORE_HANDLE, packet);
        assertSame(packet, result);
    }

    @Test
    void singleHandler_invoked() {
        ShinnetaiPipeline pipeline = new ShinnetaiPipeline();
        List<String> calls = new ArrayList<>();
        pipeline.addLast(ShinnetaiPipelineHandleType.BEFORE_HANDLE, tag(calls, "handler"));
        pipeline.handle(ShinnetaiPipelineHandleType.BEFORE_HANDLE, newPacket());
        assertEquals(List.of("handler"), calls);
    }

    @Test
    void multipleHandlers_invokedInOrder() {
        ShinnetaiPipeline pipeline = new ShinnetaiPipeline();
        List<Integer> order = new ArrayList<>();

        for (int i = 1; i <= 3; i++) {
            final int idx = i;
            pipeline.addLast(ShinnetaiPipelineHandleType.AFTER_HANDLE,
                    SingleHandlerUnit.of(new ShinnetaiPipelineHandler() {
                        @Override
                        public AbstractPacket<?, ?> handle(AbstractPacket<?, ?> p, ShinnetaiPipelineContext ctx) {
                            order.add(idx);
                            return p;
                        }
                    }));
        }

        pipeline.handle(ShinnetaiPipelineHandleType.AFTER_HANDLE, newPacket());
        assertEquals(List.of(1, 2, 3), order);
    }

    @Test
    void handlerReturnsNull_stopsChain() {
        ShinnetaiPipeline pipeline = new ShinnetaiPipeline();
        List<String> calls = new ArrayList<>();

        pipeline.addLast(ShinnetaiPipelineHandleType.BEFORE_SEND,
                SingleHandlerUnit.of(new ShinnetaiPipelineHandler() {
                    @Override
                    public AbstractPacket<?, ?> handle(AbstractPacket<?, ?> p, ShinnetaiPipelineContext ctx) {
                        calls.add("first");
                        return null;
                    }
                }));
        pipeline.addLast(ShinnetaiPipelineHandleType.BEFORE_SEND, tag(calls, "second"));

        AbstractPacket<?, ?> result = pipeline.handle(ShinnetaiPipelineHandleType.BEFORE_SEND, newPacket());

        assertNull(result);
        assertEquals(List.of("first"), calls);
    }

    @Test
    void addFirst_prependsHandler() {
        ShinnetaiPipeline pipeline = new ShinnetaiPipeline();
        List<String> calls = new ArrayList<>();

        pipeline.addLast(ShinnetaiPipelineHandleType.BEFORE_HANDLE, tag(calls, "second"));
        pipeline.addFirst(ShinnetaiPipelineHandleType.BEFORE_HANDLE, tag(calls, "first"));

        pipeline.handle(ShinnetaiPipelineHandleType.BEFORE_HANDLE, newPacket());
        assertEquals(List.of("first", "second"), calls);
    }

    @Test
    void remove_byName_removesHandler() {
        ShinnetaiPipeline pipeline = new ShinnetaiPipeline();
        List<String> calls = new ArrayList<>();

        pipeline.addLast(ShinnetaiPipelineHandleType.BEFORE_HANDLE,
                SingleHandlerUnit.of(new ShinnetaiPipelineHandler() {
                    @Override
                    public String name() { return "target"; }

                    @Override
                    public AbstractPacket<?, ?> handle(AbstractPacket<?, ?> p, ShinnetaiPipelineContext ctx) {
                        calls.add("target");
                        return p;
                    }
                }));

        pipeline.remove(ShinnetaiPipelineHandleType.BEFORE_HANDLE, "target");
        pipeline.handle(ShinnetaiPipelineHandleType.BEFORE_HANDLE, newPacket());

        assertTrue(calls.isEmpty());
    }

    @Test
    void clear_removesAllHandlers() {
        ShinnetaiPipeline pipeline = new ShinnetaiPipeline();
        List<String> calls = new ArrayList<>();

        pipeline.addLast(ShinnetaiPipelineHandleType.BEFORE_HANDLE, tag(calls, "h"));
        pipeline.clear();
        pipeline.handle(ShinnetaiPipelineHandleType.BEFORE_HANDLE, newPacket());

        assertTrue(calls.isEmpty());
    }

    @Test
    void skipNext_skipsFollowingHandler() {
        ShinnetaiPipeline pipeline = new ShinnetaiPipeline();
        List<String> calls = new ArrayList<>();

        pipeline.addLast(ShinnetaiPipelineHandleType.BEFORE_HANDLE,
                SingleHandlerUnit.of(new ShinnetaiPipelineHandler() {
                    @Override
                    public AbstractPacket<?, ?> handle(AbstractPacket<?, ?> p, ShinnetaiPipelineContext ctx) {
                        calls.add("first");
                        ctx.skipNext();
                        return p;
                    }
                }));
        pipeline.addLast(ShinnetaiPipelineHandleType.BEFORE_HANDLE, tag(calls, "skipped"));
        pipeline.addLast(ShinnetaiPipelineHandleType.BEFORE_HANDLE, tag(calls, "third"));

        pipeline.handle(ShinnetaiPipelineHandleType.BEFORE_HANDLE, newPacket());
        assertEquals(List.of("first", "third"), calls);
    }

    @Test
    void differentHandleTypes_isolatedFromEachOther() {
        ShinnetaiPipeline pipeline = new ShinnetaiPipeline();
        List<String> calls = new ArrayList<>();

        pipeline.addLast(ShinnetaiPipelineHandleType.BEFORE_SEND, tag(calls, "send"));

        pipeline.handle(ShinnetaiPipelineHandleType.BEFORE_HANDLE, newPacket());
        assertTrue(calls.isEmpty());

        pipeline.handle(ShinnetaiPipelineHandleType.BEFORE_SEND, newPacket());
        assertEquals(List.of("send"), calls);
    }

    @Test
    void jumpTo_negative_throws() {
        ShinnetaiPipeline pipeline = new ShinnetaiPipeline();
        pipeline.addLast(ShinnetaiPipelineHandleType.BEFORE_HANDLE,
                SingleHandlerUnit.of(new ShinnetaiPipelineHandler() {
                    @Override
                    public AbstractPacket<?, ?> handle(AbstractPacket<?, ?> p, ShinnetaiPipelineContext ctx) {
                        ctx.jumpTo(-1);
                        return p;
                    }
                }));

        assertThrows(IllegalArgumentException.class,
                () -> pipeline.handle(ShinnetaiPipelineHandleType.BEFORE_HANDLE, newPacket()));
    }
}

