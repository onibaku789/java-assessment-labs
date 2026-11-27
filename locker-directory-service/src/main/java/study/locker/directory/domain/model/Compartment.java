package study.locker.directory.domain.model;

public record Compartment(String id, String site, Size size) {
    public record Size(String value) {
    }
}
