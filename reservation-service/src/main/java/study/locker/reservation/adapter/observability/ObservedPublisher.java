package study.locker.reservation.adapter.observability;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;

import study.locker.common.EventLog;
import study.locker.reservation.application.PublishOutbox;

import java.util.Map;

public record ObservedPublisher(PublishOutbox.Publisher delegate)
        implements PublishOutbox.Publisher {
    @Override
    public PublishOutbox.Publication publish(PublishOutbox.Message message) {
        if (message.traceParent() == null) return send(message);
        var parent =
                W3CTraceContextPropagator.getInstance()
                        .extract(
                                Context.root(),
                                Map.of("traceparent", message.traceParent()),
                                HEADERS);
        var span =
                GlobalOpenTelemetry.getTracer("locker.outbox")
                        .spanBuilder("outbox.publish")
                        .setParent(parent)
                        .setAttribute("locker.topic", message.topic())
                        .startSpan();
        try (var scope = span.makeCurrent()) {
            var outcome = send(message);
            if (outcome != PublishOutbox.Publication.ACKNOWLEDGED) span.setStatus(StatusCode.ERROR);
            return outcome;
        } finally {
            span.end();
        }
    }

    private static final TextMapGetter<Map<String, String>> HEADERS =
            new TextMapGetter<>() {
                public Iterable<String> keys(Map<String, String> headers) {
                    return headers.keySet();
                }

                public String get(Map<String, String> headers, String key) {
                    return headers.get(key);
                }
            };

    private PublishOutbox.Publication send(PublishOutbox.Message message) {
        var outcome = delegate.publish(message);
        if (outcome == PublishOutbox.Publication.FAILED)
            EventLog.record(
                    "outbox_publish",
                    message.topic(),
                    "failed",
                    message.reservation().toString(),
                    null,
                    message.eventId().toString());
        return outcome;
    }
}
