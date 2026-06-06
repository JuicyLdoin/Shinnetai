package net.ldoin.shinnetai.worker;

public enum CloseReason {

    USER_REQUEST,
    REMOTE_DISCONNECT,
    SERVER_SHUTDOWN,
    HANDSHAKE_REJECTED,
    IO_ERROR,
    TIMEOUT,
    PROTOCOL_ERROR,
    QUEUE_OVERFLOW

}