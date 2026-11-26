package study.parcel;

record Shipment(String eventId, String trackingId, String status) {
    Shipment {
        if (eventId == null || !eventId.matches("[A-Za-z0-9_-]{1,64}")
                || trackingId == null || trackingId.isBlank()
                || status == null || status.isBlank()) {
            throw new IllegalArgumentException("Expected an event ID, tracking ID and status");
        }
    }
}
