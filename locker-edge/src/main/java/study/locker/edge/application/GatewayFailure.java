package study.locker.edge.application;

public final class GatewayFailure extends RuntimeException {
    private final String code;

    private final long retryAfterSeconds;

    public GatewayFailure(String code, String description) {
        this(code, description, 0);
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }

    public GatewayFailure(String code, String description, long retryAfterSeconds) {
        super(description == null ? code : description);
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public String code() {
        return code;
    }
}
