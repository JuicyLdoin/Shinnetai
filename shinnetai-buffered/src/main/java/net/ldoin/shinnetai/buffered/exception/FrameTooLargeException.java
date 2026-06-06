package net.ldoin.shinnetai.buffered.exception;

public final class FrameTooLargeException extends ProtocolDecodeException {

    public FrameTooLargeException(String message) {
        super(message);
    }
}