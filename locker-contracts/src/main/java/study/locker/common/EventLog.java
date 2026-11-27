package study.locker.common;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;

import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;

import java.util.UUID;

/** Structured operational events. Only UUIDs from request or event data enter the log. */
public final class EventLog {
    private EventLog() {}

    public static void record(
            String event,
            String operation,
            String outcome,
            String reservation,
            String request,
            String delivery) {
        var line =
                LoggerFactory.getLogger("locker.events")
                        .atInfo()
                        .addKeyValue("locker_event", event)
                        .addKeyValue("operation", operation)
                        .addKeyValue("outcome", outcome);
        id(line, "reservation_id", reservation);
        id(line, "request_id", request);
        id(line, "event_id", delivery);
        line.log(event);
        Span.current()
                .addEvent(
                        event,
                        Attributes.of(
                                AttributeKey.stringKey("locker.operation"),
                                operation,
                                AttributeKey.stringKey("locker.outcome"),
                                outcome));
    }

    private static void id(LoggingEventBuilder line, String key, String value) {
        if (value == null || value.length() != 36) return;
        try {
            line.addKeyValue(key, UUID.fromString(value).toString());
        } catch (IllegalArgumentException ignored) {
            /* Invalid caller input is omitted. */
        }
    }
}
