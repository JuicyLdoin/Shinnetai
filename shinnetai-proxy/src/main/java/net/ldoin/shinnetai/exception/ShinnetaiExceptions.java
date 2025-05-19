package net.ldoin.shinnetai.exception;

public enum ShinnetaiExceptions implements ShinnetaiException {

    FAILED_READ_PACKET(1, "Failed to read packet %d"),
    FAILED_HANDLE_PACKET(2, "Failed to handle packet %d"),
    FAILED_WRITE_PACKET(3, "Failed to write packet %d"),
    FAILED_SEND_PACKET(4, "Failed to send packet %d"),
    FAILED_HANDLE_RESPONSE(5, "Failed to handle response for packet %d, waiter %d"),
    FAILED_FIND_RESPONSE(6, "Failed to find response for packet %d"),
    FAILED_SEND_RESPONSE(7, "Failed to send response for packet %d, waiter %d"),
    FAILED_CONNECTION_ID_BUSY(8, "Failed to assign connection id it is already busy"),
    FAILED_ASSIGN_CONNECTION_ID(9, "Failed to assign connection id"),
    CANNOT_ACCEPT_CONNECTION(10, "Cannot accept connection"),
    FAILED_REDIRECT_TO_NODE(11, "Failed redirect to node"),
    NO_AVAILABLE_NODE_FOUND(12, "No available node found"),
    ;

    private final int id;
    private final String message;
    private final Runnable handler;

    ShinnetaiExceptions(int id, String message) {
        this(id, message, null);
    }

    ShinnetaiExceptions(int id, String message, Runnable handler) {
        this.id = id;
        this.message = message;
        this.handler = handler;
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public void handle() {
        if (handler != null) {
            handler.run();
        }
    }
}