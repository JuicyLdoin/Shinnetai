package net.ldoin.shinnetai.buffered.exception;

public class ProtocolEncodeException extends RuntimeException {

    public ProtocolEncodeException(String message) {
        super(message);
    }

    public ProtocolEncodeException(String message, Throwable cause) {
        super(message, cause);
    }
}