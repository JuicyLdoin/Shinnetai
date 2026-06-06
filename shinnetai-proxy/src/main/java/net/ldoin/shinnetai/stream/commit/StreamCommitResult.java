package net.ldoin.shinnetai.stream.commit;

public final class StreamCommitResult {

    public enum Status {
        SUCCESS,
        TIMEOUT,
        FAILED
    }

    public static StreamCommitResult ok(int streamId) {
        return new StreamCommitResult(streamId, Status.SUCCESS, null);
    }

    public static StreamCommitResult timeout(int streamId) {
        return new StreamCommitResult(streamId, Status.TIMEOUT, "Stream commit timed out");
    }

    public static StreamCommitResult failed(int streamId, String message) {
        return new StreamCommitResult(streamId, Status.FAILED, message);
    }

    private final int streamId;
    private final Status status;
    private final String message;

    private StreamCommitResult(int streamId, Status status, String message) {
        this.streamId = streamId;
        this.status = status;
        this.message = message;
    }

    public int getStreamId() {
        return streamId;
    }

    public Status getStatus() {
        return status;
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "StreamCommitResult{streamId=" + streamId + ", status=" + status + ", message='" + message + "'}";
    }
}
