package net.ldoin.shinnetai.buffered.exception;

public final class MalformedVarIntException extends ProtocolDecodeException {

    public MalformedVarIntException(String message) {
        super(message);
    }

    public MalformedVarIntException(String message, Throwable cause) {
        super(message, cause);
    }
}