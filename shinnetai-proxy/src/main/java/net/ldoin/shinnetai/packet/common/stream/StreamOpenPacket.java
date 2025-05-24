package net.ldoin.shinnetai.packet.common.stream;

import net.ldoin.shinnetai.ShinnetaiIOWorker;
import net.ldoin.shinnetai.buffered.buf.smart.ReadOnlySmartByteBuf;
import net.ldoin.shinnetai.buffered.buf.smart.WriteOnlySmartByteBuf;
import net.ldoin.shinnetai.client.ShinnetaiClient;
import net.ldoin.shinnetai.packet.AbstractPacket;
import net.ldoin.shinnetai.packet.registry.ShinnetaiPacket;
import net.ldoin.shinnetai.stream.options.ShinnetaiStreamOptions;
import net.ldoin.shinnetai.stream.type.ShinnetaiInStream;

import java.util.HashSet;
import java.util.Set;

@ShinnetaiPacket(id = -50)
public class StreamOpenPacket extends AbstractPacket<ShinnetaiClient, ShinnetaiIOWorker<?>> {

    private int id;
    private ShinnetaiStreamOptions options;

    public StreamOpenPacket() {
    }

    public StreamOpenPacket(int id) {
        this(id, null);
    }

    public StreamOpenPacket(int id, ShinnetaiStreamOptions options) {
        this.id = id;
        this.options = options;
    }

    public int getId() {
        return id;
    }

    @Override
    public void handleClient() {
        handle();
    }

    @Override
    public void handleServer() {
        handle();
    }

    private void handle() {
        ShinnetaiIOWorker<?> worker = getCurrentWorker();
        worker.openStream(new ShinnetaiInStream(id, worker, options));
    }

    @Override
    @SuppressWarnings("unchecked")
    public StreamOpenPacket response() {
        return new StreamOpenPacket(id);
    }

    @Override
    public void read(ReadOnlySmartByteBuf buf) {
        this.id = buf.readVarInt();
        if (!buf.readBoolean()) {
            this.options = ShinnetaiStreamOptions.of(getCurrentWorker());
            return;
        }

        int packetsAmount = buf.readVarInt();

        int size = buf.readVarInt();
        Set<Integer> packetsFilter = new HashSet<>(size);
        for (int i = 0; i < size; i++) {
            packetsFilter.add(buf.readVarInt());
        }

        long lifetime = buf.readVarLong();
        boolean autoCloseable = buf.readBoolean();

        this.options = ShinnetaiStreamOptions.builder(getCurrentWorker())
                .setPacketsAmount(packetsAmount)
                .setPacketsFilter(packetsFilter)
                .setLifeTime(lifetime)
                .setAutoCloseable(autoCloseable)
                .build();
    }

    @Override
    public void write(WriteOnlySmartByteBuf buf) {
        buf.writeVarInt(id);
        if (options != null) {
            buf.writeBoolean(true);
        } else {
            buf.writeBoolean(false);
            return;
        }

        buf.writeVarInt(options.getPacketsAmount());

        Set<Integer> packetsFilter = options.getPacketsFilter();
        buf.writeVarInt(packetsFilter.size());
        for (int filter : packetsFilter) {
            buf.writeVarInt(filter);
        }

        buf.writeVarLong(options.getLifetime());
        buf.writeBoolean(options.isAutoCloseable());
    }
}