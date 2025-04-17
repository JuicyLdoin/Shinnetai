package net.ldoin.shinnetai.stream;

import net.ldoin.shinnetai.stream.options.ShinnetaiStreamOptions;
import net.ldoin.shinnetai.worker.ShinnetaiIOWorker;

public interface IShinnetaiStreamType {

    int id();

    ShinnetaiStream create(int id, ShinnetaiIOWorker<?> worker, ShinnetaiStreamOptions options);

}