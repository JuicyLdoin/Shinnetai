package net.ldoin.shinnetai.worker;

public enum QueueOverflowStrategy {

    DROP,
    WARN,
    THROW,
    BLOCK,
    DROP_OLDEST,
    DROP_NEWEST

}