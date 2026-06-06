package net.ldoin.shinnetai.buffered.exception;

public class ProtocolDecodeException extends RuntimeException {

    public ProtocolDecodeException(String message) {
        super(message);
    }

    public ProtocolDecodeException(String message, Throwable cause) {
        super(message, cause);
    }
}