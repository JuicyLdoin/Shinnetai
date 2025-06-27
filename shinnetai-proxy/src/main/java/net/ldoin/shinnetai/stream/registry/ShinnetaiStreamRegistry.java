package net.ldoin.shinnetai.stream.registry;

import net.ldoin.shinnetai.stream.IShinnetaiStreamType;
import net.ldoin.shinnetai.stream.ShinnetaiStream;
import net.ldoin.shinnetai.stream.ShinnetaiStreamType;
import net.ldoin.shinnetai.stream.options.ShinnetaiStreamOptions;
import net.ldoin.shinnetai.worker.ShinnetaiIOWorker;

import java.util.HashMap;
import java.util.Map;

public class ShinnetaiStreamRegistry {

    private static final Map<Integer, IShinnetaiStreamType> REGISTRY = new HashMap<>();

    static {
        register(ShinnetaiStreamType.VALUES);
    }

    public static IShinnetaiStreamType getStream(int id) {
        return REGISTRY.get(id);
    }

    public static ShinnetaiStream createStream(int typeId, int id, ShinnetaiIOWorker<?> worker, ShinnetaiStreamOptions options) {
        return getStream(typeId).create(id, worker, options);
    }

    public static void register(IShinnetaiStreamType streamType) {
        int id = streamType.id();
        if (REGISTRY.containsKey(id)) {
            throw new IllegalArgumentException("Stream with id " + id + " already registered");
        }

        REGISTRY.put(id, streamType);
    }

    public static void register(IShinnetaiStreamType... streamTypes) {
        for (IShinnetaiStreamType streamType : streamTypes) {
            register(streamType);
        }
    }
}