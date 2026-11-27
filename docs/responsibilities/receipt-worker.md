# receipt-worker responsibility review

[All modules and counting conventions](README.md)

10 Java files. 14 types, 21 methods and 1 explicit constructors. 4 types and 3 members have multiple reasons to change.

## ReceiptApp

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class ReceiptApp`](../../receipt-worker/src/main/java/study/locker/receipt/ReceiptApp.java#L6) | 1 | Spring Boot startup of the receipt worker. |
| [`main(String[])`](../../receipt-worker/src/main/java/study/locker/receipt/ReceiptApp.java#L8) | 1 | Spring Boot startup of the receipt worker. |

## LifecycleListener

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class LifecycleListener`](../../receipt-worker/src/main/java/study/locker/receipt/adapter/kafka/LifecycleListener.java#L14) | 1 | Validated lifecycle record binding and forwarding Kafka delivery attempts. |
| [`LifecycleListener(DeliveryHandler<Receipt>)`](../../receipt-worker/src/main/java/study/locker/receipt/adapter/kafka/LifecycleListener.java#L19) | 1 | Validated lifecycle record binding and forwarding Kafka delivery attempts. |
| [`receive(LifecycleMessage, int)`](../../receipt-worker/src/main/java/study/locker/receipt/adapter/kafka/LifecycleListener.java#L23) | 1 | Validated lifecycle record binding and forwarding Kafka delivery attempts. |

## LifecycleMessage

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`record LifecycleMessage`](../../receipt-worker/src/main/java/study/locker/receipt/adapter/kafka/LifecycleMessage.java#L11) | 1 | Versioned receipt-event schema and conversion of event identifiers into domain values. |
| [`toReceipt()`](../../receipt-worker/src/main/java/study/locker/receipt/adapter/kafka/LifecycleMessage.java#L18) | 1 | Versioned receipt-event schema and conversion of event identifiers into domain values. |

## S3ReceiptArchive

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`record S3ReceiptArchive`](../../receipt-worker/src/main/java/study/locker/receipt/adapter/s3/S3ReceiptArchive.java#L14) | 2 | Canonical receipt object key and payload format · Conditional creation, replay lookup and classification of S3 errors. |
| [`key(Receipt)`](../../receipt-worker/src/main/java/study/locker/receipt/adapter/s3/S3ReceiptArchive.java#L17) | 1 | Canonical receipt object key and payload format. |
| [`createIfAbsent(Receipt)`](../../receipt-worker/src/main/java/study/locker/receipt/adapter/s3/S3ReceiptArchive.java#L21) | 2 | Canonical receipt object key and payload format · Conditional creation, replay lookup and classification of S3 errors. Writes JSON at the derived key with a conditional create. **Suggested change.** Provide a receipt representation codec shared by the read and conditional-write paths. |
| [`existing(Receipt)`](../../receipt-worker/src/main/java/study/locker/receipt/adapter/s3/S3ReceiptArchive.java#L39) | 2 | Canonical receipt object key and payload format · Conditional creation, replay lookup and classification of S3 errors. Reads the stored representation for a replay comparison. **Suggested change.** Provide a receipt representation codec shared by the read and conditional-write paths. |
| [`classify(SdkException)`](../../receipt-worker/src/main/java/study/locker/receipt/adapter/s3/S3ReceiptArchive.java#L49) | 1 | Conditional creation, replay lookup and classification of S3 errors. Classifies SDK/service exceptions for Kafka redelivery. |

**Refactor to consider.** Factor the receipt key and codec out of the conditional object-store adapter.

## StoreLifecycleReceipt

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`record StoreLifecycleReceipt`](../../receipt-worker/src/main/java/study/locker/receipt/application/StoreLifecycleReceipt.java#L5) | 1 | Idempotent receipt creation and detection of conflicting payloads for the same identity. |
| [`store(Receipt)`](../../receipt-worker/src/main/java/study/locker/receipt/application/StoreLifecycleReceipt.java#L17) | 1 | Idempotent receipt creation and detection of conflicting payloads for the same identity. Accepts an identical replay and rejects a payload conflicting at the same receipt key. |

## StoreLifecycleReceipt.Archive

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`interface Archive`](../../receipt-worker/src/main/java/study/locker/receipt/application/StoreLifecycleReceipt.java#L6) | 1 | Conditional receipt creation and retrieval contract required for replay comparison. |
| [`createIfAbsent(Receipt)`](../../receipt-worker/src/main/java/study/locker/receipt/application/StoreLifecycleReceipt.java#L7) | 1 | Conditional receipt creation and retrieval contract required for replay comparison. |
| [`existing(Receipt)`](../../receipt-worker/src/main/java/study/locker/receipt/application/StoreLifecycleReceipt.java#L9) | 1 | Conditional receipt creation and retrieval contract required for replay comparison. |

## StoreLifecycleReceipt.Outcome

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`enum Outcome`](../../receipt-worker/src/main/java/study/locker/receipt/application/StoreLifecycleReceipt.java#L12) | 1 | Successful first-write and identical-replay outcomes. |

## ReceiptConfiguration

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class ReceiptConfiguration`](../../receipt-worker/src/main/java/study/locker/receipt/bootstrap/ReceiptConfiguration.java#L21) | 3 | S3 endpoint, credentials chain, timeouts and SDK retry setup · Assembly of the receipt use case · Correlation and observation of Kafka delivery outcomes. |
| [`storage(URI, String, Duration)`](../../receipt-worker/src/main/java/study/locker/receipt/bootstrap/ReceiptConfiguration.java#L24) | 1 | S3 endpoint, credentials chain, timeouts and SDK retry setup. |
| [`receipts(S3Client, ObjectMapper, String)`](../../receipt-worker/src/main/java/study/locker/receipt/bootstrap/ReceiptConfiguration.java#L43) | 1 | Assembly of the receipt use case. |
| [`handler(DeliveryEvents, StoreLifecycleReceipt)`](../../receipt-worker/src/main/java/study/locker/receipt/bootstrap/ReceiptConfiguration.java#L51) | 1 | Correlation and observation of Kafka delivery outcomes. |

**Refactor to consider.** Keep the S3 client factory independent of receipt assembly and observation.

## Receipt

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`record Receipt`](../../receipt-worker/src/main/java/study/locker/receipt/domain/model/Receipt.java#L5) | 1 | Stored receipt fields used for lifecycle audit and replay comparison. |

## LifecycleMessageTest

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class LifecycleMessageTest`](../../receipt-worker/src/test/java/study/locker/receipt/adapter/kafka/LifecycleMessageTest.java#L15) | 1 | Rejection of malformed lifecycle JSON before bean validation. |
| [`rejectsWrongJsonTypesBeforeListenerValidation()`](../../receipt-worker/src/test/java/study/locker/receipt/adapter/kafka/LifecycleMessageTest.java#L16) | 1 | Rejection of malformed lifecycle JSON before bean validation. |

## ReceiptDeliveryIT

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class ReceiptDeliveryIT`](../../receipt-worker/src/test/java/study/locker/receipt/integration/ReceiptDeliveryIT.java#L30) | 3 | Durable pending offsets through an S3 outage and consumer restart · Idempotent receipt storage and conflicts · Validation diagnostics and routing of poison records to Kafka dead letters. |
| [`unavailableStorageKeepsTheOffsetAcrossAConsumerRestart()`](../../receipt-worker/src/test/java/study/locker/receipt/integration/ReceiptDeliveryIT.java#L56) | 1 | Durable pending offsets through an S3 outage and consumer restart. Pauses S3 and restarts the listener while the original Kafka record is pending. |
| [`receiptKeys(String)`](../../receipt-worker/src/test/java/study/locker/receipt/integration/ReceiptDeliveryIT.java#L81) | 1 | Idempotent receipt storage and conflicts. |
| [`deliveries(String, String)`](../../receipt-worker/src/test/java/study/locker/receipt/integration/ReceiptDeliveryIT.java#L86) | 1 | Durable pending offsets through an S3 outage and consumer restart. |
| [`duplicateReceiptsConvergeAndPoisonRecordsGoToDeadLettersWithoutBlockingLaterEvents()`](../../receipt-worker/src/test/java/study/locker/receipt/integration/ReceiptDeliveryIT.java#L91) | 2 | Idempotent receipt storage and conflicts · Validation diagnostics and routing of poison records to Kafka dead letters. **Suggested change.** Separate the conflicting-ID case from malformed-payload routing and assert later delivery in each scenario. |

**Refactor to consider.** Keep outage recovery, conflicting receipt identity and malformed input as named scenarios using the same containers.

## ReceiptDeliveryIT.LifecycleEvent

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`record LifecycleEvent`](../../receipt-worker/src/test/java/study/locker/receipt/integration/ReceiptDeliveryIT.java#L37) | 1 | Versioned sample lifecycle payload written to Kafka by the integration tests. |

## ReceiptInfrastructure

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class ReceiptInfrastructure`](../../receipt-worker/src/test/java/study/locker/receipt/integration/ReceiptInfrastructure.java#L22) | 2 | Kafka and object-store container lifecycle and worker connection settings · Dead-letter consumer setup for receipt verification. |
| [`kafkaProperties(DynamicPropertyRegistry)`](../../receipt-worker/src/test/java/study/locker/receipt/integration/ReceiptInfrastructure.java#L31) | 1 | Kafka and object-store container lifecycle and worker connection settings. |
| [`deadLetters()`](../../receipt-worker/src/test/java/study/locker/receipt/integration/ReceiptInfrastructure.java#L50) | 1 | Dead-letter consumer setup for receipt verification. |

**Refactor to consider.** Give dead-letter inspection its own consumer fixture alongside the shared containers.

## ReceiptInfrastructure.Storage

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class Storage`](../../receipt-worker/src/test/java/study/locker/receipt/integration/ReceiptInfrastructure.java#L36) | 1 | Test S3 client endpoint, credentials, deadline and retry behavior. |
| [`testStorage()`](../../receipt-worker/src/test/java/study/locker/receipt/integration/ReceiptInfrastructure.java#L38) | 1 | Test S3 client endpoint, credentials, deadline and retry behavior. |
