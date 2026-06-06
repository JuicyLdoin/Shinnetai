package net.ldoin.shinnetai.stream;

import net.ldoin.shinnetai.stream.options.ShinnetaiStreamOptions;
import net.ldoin.shinnetai.stream.type.ShinnetaiInStream;
import net.ldoin.shinnetai.stream.type.ShinnetaiOutStream;
import net.ldoin.shinnetai.worker.ShinnetaiIOWorker;

public enum ShinnetaiStreamType implements IShinnetaiStreamType {

    IN {
        @Override
        public ShinnetaiStream create(int id, ShinnetaiIOWorker<?> worker, ShinnetaiStreamOptions options) {
            return new ShinnetaiInStream(id, worker, options);
        }
    },
    OUT {
        @Override
        public ShinnetaiStream create(int id, ShinnetaiIOWorker<?> worker, ShinnetaiStreamOptions options) {
            return new ShinnetaiOutStream(id, worker, options);
        }
    };

    public static final ShinnetaiStreamType[] VALUES = values();

    @Override
    public int id() {
        return ordinal();
    }

    @Override
    public abstract ShinnetaiStream create(int id, ShinnetaiIOWorker<?> worker, ShinnetaiStreamOptions options);
}