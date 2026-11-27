package study.locker.delivery;

import io.micrometer.core.instrument.MeterRegistry;

import study.locker.common.EventLog;

public record DeliveryEvents(MeterRegistry metrics) {
    public void record(String topic, String outcome, String reservation, String event) {
        metrics.counter("locker.worker.deliveries", "topic", topic, "outcome", outcome).increment();
        EventLog.record("worker_delivery", topic, outcome, reservation, null, event);
    }
}
