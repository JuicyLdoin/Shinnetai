package net.ldoin.shinnetai.worker;

public enum EnqueueResult {

    ACCEPTED,
    DROPPED,
    REJECTED_FULL,
    TIMED_OUT,
    CLOSED

}