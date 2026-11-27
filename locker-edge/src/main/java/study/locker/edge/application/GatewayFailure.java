package study.locker.edge.application;

public final class GatewayFailure extends RuntimeException {
    private final String code;

    public GatewayFailure(String code, String description) {
        super(description == null ? code : description);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
