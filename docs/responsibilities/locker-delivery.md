# locker-delivery responsibility review

[All modules and counting conventions](README.md)

13 Java files. 17 types, 28 methods and 3 explicit constructors. 5 types and 4 members have multiple reasons to change.

## DeliveryConfig

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class DeliveryConfig`](../../locker-delivery/src/main/java/study/locker/delivery/DeliveryConfig.java#L10) | 2 | Requesting Kafka delivery-attempt headers · Registration of worker telemetry. |
| [`deliveryAttempts()`](../../locker-delivery/src/main/java/study/locker/delivery/DeliveryConfig.java#L14) | 1 | Requesting Kafka delivery-attempt headers. Enables the attempt header used by both consumers. |
| [`deliveryEvents(MeterRegistry)`](../../locker-delivery/src/main/java/study/locker/delivery/DeliveryConfig.java#L19) | 1 | Registration of worker telemetry. Supplies the meter-backed worker event reporter. |

**Refactor to consider.** Register DeliveryEvents with the observation setup and keep listener container options together.

## DeliveryEvents

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`record DeliveryEvents`](../../locker-delivery/src/main/java/study/locker/delivery/DeliveryEvents.java#L7) | 2 | Worker delivery counter dimensions · Operational event correlation and logging. |
| [`record(String, String, String, String)`](../../locker-delivery/src/main/java/study/locker/delivery/DeliveryEvents.java#L8) | 2 | Worker delivery counter dimensions · Operational event correlation and logging. **Suggested change.** Pass the structured outcome to separate counter and event-log writers. |

**Refactor to consider.** Give the counter and structured event separate consumers of a delivery outcome.

## DeliveryHandler

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`interface DeliveryHandler`](../../locker-delivery/src/main/java/study/locker/delivery/DeliveryHandler.java#L3) | 1 | Input contract for handling one Kafka record and its delivery attempt. |
| [`deliver(String, T, int)`](../../locker-delivery/src/main/java/study/locker/delivery/DeliveryHandler.java#L5) | 1 | Input contract for handling one Kafka record and its delivery attempt. |

## DeliveryRecoveryConfiguration

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class DeliveryRecoveryConfiguration`](../../locker-delivery/src/main/java/study/locker/delivery/DeliveryRecoveryConfiguration.java#L12) | 2 | Listener pause and resume scheduling · Classification of delivery failures and dead-letter recovery. |
| [`deliveryScheduler()`](../../locker-delivery/src/main/java/study/locker/delivery/DeliveryRecoveryConfiguration.java#L14) | 1 | Listener pause and resume scheduling. |
| [`consumerErrorHandler(KafkaTemplate<String, String>, DeliveryEvents, ObjectMapper, DeliveryRetryProperties, KafkaListenerEndpointRegistry, ThreadPoolTaskScheduler)`](../../locker-delivery/src/main/java/study/locker/delivery/DeliveryRecoveryConfiguration.java#L21) | 1 | Classification of delivery failures and dead-letter recovery. |

**Refactor to consider.** Keep recoverer construction separate from the scheduler lifecycle when recovery policy next changes.

## DeliveryRetryListener

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`record DeliveryRetryListener`](../../locker-delivery/src/main/java/study/locker/delivery/DeliveryRetryListener.java#L9) | 2 | Which Kafka retry and recovery outcomes are reported · Extracting event identity from a possibly malformed payload. |
| [`failedDelivery(ConsumerRecord<?, ?>, Exception, int)`](../../locker-delivery/src/main/java/study/locker/delivery/DeliveryRetryListener.java#L11) | 2 | Which Kafka retry and recovery outcomes are reported · Extracting event identity from a possibly malformed payload. Records retry progress and identifies when a valid record is waiting for its dependency. **Suggested change.** Extract the Kafka record correlation reader used by each callback. |
| [`recovered(ConsumerRecord<?, ?>, Exception)`](../../locker-delivery/src/main/java/study/locker/delivery/DeliveryRetryListener.java#L20) | 2 | Which Kafka retry and recovery outcomes are reported · Extracting event identity from a possibly malformed payload. Reports the result of the dead-letter publication attempt. **Suggested change.** Extract the Kafka record correlation reader used by each callback. |
| [`recoveryFailed(ConsumerRecord<?, ?>, Exception, Exception)`](../../locker-delivery/src/main/java/study/locker/delivery/DeliveryRetryListener.java#L25) | 2 | Which Kafka retry and recovery outcomes are reported · Extracting event identity from a possibly malformed payload. Reports the result of the dead-letter publication attempt. **Suggested change.** Extract the Kafka record correlation reader used by each callback. |
| [`report(ConsumerRecord<?, ?>, String, String)`](../../locker-delivery/src/main/java/study/locker/delivery/DeliveryRetryListener.java#L30) | 1 | Which Kafka retry and recovery outcomes are reported. |
| [`eventId(ConsumerRecord<?, ?>)`](../../locker-delivery/src/main/java/study/locker/delivery/DeliveryRetryListener.java#L34) | 1 | Extracting event identity from a possibly malformed payload. Extracts an identifier without requiring malformed records to parse successfully. |

**Refactor to consider.** Isolate the payload correlation reader from the Kafka retry callbacks.

## DeliveryRetryProperties

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`record DeliveryRetryProperties`](../../locker-delivery/src/main/java/study/locker/delivery/DeliveryRetryProperties.java#L14) | 1 | Timing and number of short retries before dependency probes. |
| [`outage()`](../../locker-delivery/src/main/java/study/locker/delivery/DeliveryRetryProperties.java#L20) | 1 | Timing and number of short retries before dependency probes. |

## DeliveryRetryProperties.anonymous@22

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class anonymous@22`](../../locker-delivery/src/main/java/study/locker/delivery/DeliveryRetryProperties.java#L22) | 1 | Advancing from short retry delays to the recovery probe interval. |
| [`nextBackOff()`](../../locker-delivery/src/main/java/study/locker/delivery/DeliveryRetryProperties.java#L25) | 1 | Advancing from short retry delays to the recovery probe interval. |

## DeliveryUnavailable

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class DeliveryUnavailable`](../../locker-delivery/src/main/java/study/locker/delivery/DeliveryUnavailable.java#L4) | 1 | Classification of an infrastructure outage through wrapped exceptions. |
| [`DeliveryUnavailable(String, Throwable)`](../../locker-delivery/src/main/java/study/locker/delivery/DeliveryUnavailable.java#L5) | 1 | Classification of an infrastructure outage through wrapped exceptions. |
| [`causedBy(Throwable)`](../../locker-delivery/src/main/java/study/locker/delivery/DeliveryUnavailable.java#L9) | 1 | Classification of an infrastructure outage through wrapped exceptions. |

## DeliveryValidationConfiguration

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class DeliveryValidationConfiguration`](../../locker-delivery/src/main/java/study/locker/delivery/DeliveryValidationConfiguration.java#L13) | 2 | Deserialization of event wire payloads · Listener validation and field-safe diagnostic messages. |
| [`eventDecoder(ObjectMapper)`](../../locker-delivery/src/main/java/study/locker/delivery/DeliveryValidationConfiguration.java#L15) | 1 | Deserialization of event wire payloads. |
| [`listenerValidation(LocalValidatorFactoryBean)`](../../locker-delivery/src/main/java/study/locker/delivery/DeliveryValidationConfiguration.java#L20) | 1 | Listener validation and field-safe diagnostic messages. |
| [`eventFieldErrors()`](../../locker-delivery/src/main/java/study/locker/delivery/DeliveryValidationConfiguration.java#L25) | 1 | Listener validation and field-safe diagnostic messages. |

**Refactor to consider.** Move the event converter factory alongside the decoder, keeping the validator and its diagnostics together.

## EventDecoder

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class EventDecoder`](../../locker-delivery/src/main/java/study/locker/delivery/EventDecoder.java#L17) | 1 | Accepted JSON shapes, coercion rules and safe field names for invalid delivery payloads. |
| [`EventDecoder(ObjectMapper)`](../../locker-delivery/src/main/java/study/locker/delivery/EventDecoder.java#L20) | 1 | Accepted JSON shapes, coercion rules and safe field names for invalid delivery payloads. |
| [`extractAndConvertValue(ConsumerRecord<?, ?>, Type)`](../../locker-delivery/src/main/java/study/locker/delivery/EventDecoder.java#L39) | 1 | Accepted JSON shapes, coercion rules and safe field names for invalid delivery payloads. Routes raw string payloads with a concrete target class to strict decoding. |
| [`decode(String, Class<T>)`](../../locker-delivery/src/main/java/study/locker/delivery/EventDecoder.java#L47) | 1 | Accepted JSON shapes, coercion rules and safe field names for invalid delivery payloads. Rejects unexpected JSON shape or field types with a field-safe diagnostic. |

## EventTimestamp

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`annotation type EventTimestamp`](../../locker-delivery/src/main/java/study/locker/delivery/EventTimestamp.java#L12) | 1 | Bean Validation contract for timestamps that must represent an instant. |
| [`message()`](../../locker-delivery/src/main/java/study/locker/delivery/EventTimestamp.java#L16) | 1 | Bean Validation contract for timestamps that must represent an instant. |
| [`groups()`](../../locker-delivery/src/main/java/study/locker/delivery/EventTimestamp.java#L18) | 1 | Bean Validation contract for timestamps that must represent an instant. |
| [`payload()`](../../locker-delivery/src/main/java/study/locker/delivery/EventTimestamp.java#L20) | 1 | Bean Validation contract for timestamps that must represent an instant. |

## EventTimestamp.Check

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class Check`](../../locker-delivery/src/main/java/study/locker/delivery/EventTimestamp.java#L22) | 1 | Recognition of timestamp strings accepted by Instant.parse. |
| [`isValid(String, ConstraintValidatorContext)`](../../locker-delivery/src/main/java/study/locker/delivery/EventTimestamp.java#L23) | 1 | Recognition of timestamp strings accepted by Instant.parse. |

## EventUuid

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`annotation type EventUuid`](../../locker-delivery/src/main/java/study/locker/delivery/EventUuid.java#L14) | 1 | Bean Validation contract for canonical hexadecimal event identifiers. |
| [`message()`](../../locker-delivery/src/main/java/study/locker/delivery/EventUuid.java#L21) | 1 | Bean Validation contract for canonical hexadecimal event identifiers. |
| [`groups()`](../../locker-delivery/src/main/java/study/locker/delivery/EventUuid.java#L23) | 1 | Bean Validation contract for canonical hexadecimal event identifiers. |
| [`payload()`](../../locker-delivery/src/main/java/study/locker/delivery/EventUuid.java#L25) | 1 | Bean Validation contract for canonical hexadecimal event identifiers. |

## InvalidEventFields

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class InvalidEventFields`](../../locker-delivery/src/main/java/study/locker/delivery/InvalidEventFields.java#L3) | 1 | Public-safe failure description for invalid event fields. |
| [`InvalidEventFields(String)`](../../locker-delivery/src/main/java/study/locker/delivery/InvalidEventFields.java#L4) | 1 | Public-safe failure description for invalid event fields. |

## ObservedDelivery

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`record ObservedDelivery`](../../locker-delivery/src/main/java/study/locker/delivery/ObservedDelivery.java#L6) | 1 | Observing repeat delivery and the final outcome around a worker operation. |
| [`deliver(String, T, int)`](../../locker-delivery/src/main/java/study/locker/delivery/ObservedDelivery.java#L16) | 1 | Observing repeat delivery and the final outcome around a worker operation. |

## ObservedDelivery.Correlation

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`record Correlation`](../../locker-delivery/src/main/java/study/locker/delivery/ObservedDelivery.java#L9) | 1 | Identifiers carried into worker telemetry. |

## ObservedDelivery.Operation

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`interface Operation`](../../locker-delivery/src/main/java/study/locker/delivery/ObservedDelivery.java#L11) | 1 | Callback contract for the worker effect and its reported outcome. |
| [`deliver(T)`](../../locker-delivery/src/main/java/study/locker/delivery/ObservedDelivery.java#L13) | 1 | Callback contract for the worker effect and its reported outcome. |
