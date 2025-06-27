package net.ldoin.shinnetai.worker.pipeline;

public enum ShinnetaiPipelineHandleType {

    BEFORE_HANDLE,
    AFTER_HANDLE,
    BEFORE_SEND,
    AFTER_SEND;

    public static final ShinnetaiPipelineHandleType[] VALUES = values();

}