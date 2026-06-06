package net.ldoin.shinnetai.debug;

import net.ldoin.shinnetai.buffered.buf.smart.ReadOnlySmartByteBuf;
import net.ldoin.shinnetai.packet.AbstractPacket;
import net.ldoin.shinnetai.packet.WrappedPacket;
import net.ldoin.shinnetai.packet.registry.PacketRegistry;
import net.ldoin.shinnetai.worker.ShinnetaiWorkerContext;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

public class TrafficPlayer {

    public static Builder builder() {
        return new Builder();
    }

    private final List<TrafficEvent> events;
    private final TrafficDirection filter;
    private final boolean realtime;
    private final ReplayErrorHandler onError;

    private TrafficPlayer(Builder builder) {
        this.events = builder.events;
        this.filter = builder.filter;
        this.realtime = builder.realtime;
        this.onError = builder.onError != null ? builder.onError : (e, ev) -> {
        };
    }

    public void replay(PacketRegistry registry, Consumer<AbstractPacket<?, ?>> handler) {
        List<TrafficEvent> filtered = filter != null
                ? events.stream().filter(e -> e.direction() == filter).toList()
                : events;

        long previousTs = filtered.isEmpty() ? 0 : filtered.getFirst().timestamp();
        for (TrafficEvent event : filtered) {
            if (realtime) {
                long delta = event.timestamp() - previousTs;
                if (delta > 0) {
                    try {
                        Thread.sleep(delta);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }

                previousTs = event.timestamp();
            }

            try {
                AbstractPacket<?, ?> packet = deserialize(registry, event);
                handler.accept(packet);
            } catch (Exception e) {
                onError.onError(e, event);
            }
        }
    }

    public void replay(PacketRegistry registry, ShinnetaiWorkerContext<?> target) {
        replay(registry, packet -> {
            try {
                target.sendPacket(WrappedPacket.of(packet));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public static void replayFile(Path file, PacketRegistry registry, ShinnetaiWorkerContext<?> target) throws IOException {
        List<TrafficEvent> events = TrafficLog.loadFrom(file);
        builder().events(events).build().replay(registry, target);
    }

    public static void replayFile(Path file, PacketRegistry registry, Consumer<AbstractPacket<?, ?>> handler) throws IOException {
        List<TrafficEvent> events = TrafficLog.loadFrom(file);
        builder().events(events).build().replay(registry, handler);
    }

    public static void replayDirectory(Path dir, PacketRegistry registry, Consumer<AbstractPacket<?, ?>> handler) throws IOException {
        List<TrafficEvent> events = TrafficLog.loadAll(dir);
        builder().events(events).build().replay(registry, handler);
    }

    private static AbstractPacket<?, ?> deserialize(PacketRegistry registry, TrafficEvent event) {
        ReadOnlySmartByteBuf buf = ReadOnlySmartByteBuf.of(event.rawBytes());
        int id = buf.readVarInt();
        AbstractPacket<?, ?> packet = registry.createPacket(id);
        packet.read(buf);
        return packet;
    }

    @FunctionalInterface
    public interface ReplayErrorHandler {
        void onError(Exception error, TrafficEvent event);
    }

    public static class Builder {

        private List<TrafficEvent> events;
        private TrafficDirection filter;
        private boolean realtime = false;
        private ReplayErrorHandler onError;

        public Builder events(List<TrafficEvent> events) {
            this.events = events;
            return this;
        }

        public Builder filter(TrafficDirection filter) {
            this.filter = filter;
            return this;
        }

        public Builder realtime(boolean realtime) {
            this.realtime = realtime;
            return this;
        }

        public Builder onError(ReplayErrorHandler onError) {
            this.onError = onError;
            return this;
        }

        public TrafficPlayer build() {
            if (events == null) {
                throw new IllegalStateException("events must be set");
            }

            return new TrafficPlayer(this);
        }
    }
}