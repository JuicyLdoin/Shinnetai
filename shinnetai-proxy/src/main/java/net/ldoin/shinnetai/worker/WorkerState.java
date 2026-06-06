package net.ldoin.shinnetai.worker;

public enum WorkerState {

    NEW,
    CONNECTING,
    HANDSHAKING,
    RUNNING,
    CLOSING,
    CLOSED,
    FAILED

}