# locker-contracts responsibility review

[All modules and counting conventions](README.md)

6 Java files. 7 types, 30 methods and 6 explicit constructors. 7 types and 11 members have multiple reasons to change.

## EventLog

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class EventLog`](../../locker-contracts/src/main/java/study/locker/common/EventLog.java#L13) | 2 | Structured log fields and UUID redaction · OpenTelemetry span event attributes. |
| [`EventLog()`](../../locker-contracts/src/main/java/study/locker/common/EventLog.java#L14) | 0 | Empty private constructor prevents instantiation and owns no independent behavior. |
| [`record(String, String, String, String, String, String)`](../../locker-contracts/src/main/java/study/locker/common/EventLog.java#L16) | 2 | Structured log fields and UUID redaction · OpenTelemetry span event attributes. Writes the structured line and attaches an event to the active span. **Suggested change.** Send the same observation to a log writer and a span-event writer. |
| [`id(LoggingEventBuilder, String, String)`](../../locker-contracts/src/main/java/study/locker/common/EventLog.java#L43) | 1 | Structured log fields and UUID redaction. Admits only parseable UUIDs into indexed correlation fields. |

**Refactor to consider.** Move span event emission to a trace listener so log redaction can evolve independently.

## PickupSecrets

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class PickupSecrets`](../../locker-contracts/src/main/java/study/locker/common/PickupSecrets.java#L18) | 3 | Pickup code length and random generation · HMAC key format, derivation and verification · Versioned AES-GCM mail envelope and its associated data. |
| [`PickupSecrets(String)`](../../locker-contracts/src/main/java/study/locker/common/PickupSecrets.java#L22) | 1 | HMAC key format, derivation and verification. |
| [`code()`](../../locker-contracts/src/main/java/study/locker/common/PickupSecrets.java#L28) | 1 | Pickup code length and random generation. Chooses the digit count, RNG range and padding of a pickup code. |
| [`fingerprint(String)`](../../locker-contracts/src/main/java/study/locker/common/PickupSecrets.java#L32) | 1 | HMAC key format, derivation and verification. Depends on the keyed digest, its input representation or its comparison. |
| [`matches(String, String, String)`](../../locker-contracts/src/main/java/study/locker/common/PickupSecrets.java#L36) | 1 | HMAC key format, derivation and verification. Depends on the keyed digest, its input representation or its comparison. |
| [`seal(String, String)`](../../locker-contracts/src/main/java/study/locker/common/PickupSecrets.java#L41) | 1 | Versioned AES-GCM mail envelope and its associated data. Depends on the envelope encoding, IV, authentication data or key derivation. |
| [`open(String, String)`](../../locker-contracts/src/main/java/study/locker/common/PickupSecrets.java#L54) | 1 | Versioned AES-GCM mail envelope and its associated data. Depends on the envelope encoding, IV, authentication data or key derivation. |
| [`crypt(int, byte[], String, byte[])`](../../locker-contracts/src/main/java/study/locker/common/PickupSecrets.java#L64) | 2 | HMAC key format, derivation and verification · Versioned AES-GCM mail envelope and its associated data. Depends on the envelope encoding, IV, authentication data or key derivation. **Suggested change.** Inject the derived envelope key into an AES-GCM codec and keep HMAC key derivation outside it. |
| [`mac(String)`](../../locker-contracts/src/main/java/study/locker/common/PickupSecrets.java#L78) | 1 | HMAC key format, derivation and verification. Depends on the keyed digest, its input representation or its comparison. |

**Refactor to consider.** Separate code generation, keyed verification and mail encryption behind the existing pickup ports.

## Rpc

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class Rpc`](../../locker-contracts/src/main/java/study/locker/common/Rpc.java#L9) | 3 | gRPC server listener and interceptor setup · Client channel transport and retry configuration · Unary reply completion and exception translation. |
| [`Rpc()`](../../locker-contracts/src/main/java/study/locker/common/Rpc.java#L10) | 0 | Empty private constructor prevents instantiation and owns no independent behavior. |
| [`serve(int, BindableService, ServerInterceptor[])`](../../locker-contracts/src/main/java/study/locker/common/Rpc.java#L12) | 1 | gRPC server listener and interceptor setup. Installs interceptors before starting the service listener. |
| [`channel(String)`](../../locker-contracts/src/main/java/study/locker/common/Rpc.java#L20) | 1 | Client channel transport and retry configuration. Configures plaintext transport and disables transport-owned retries. |
| [`reply(StreamObserver<T>, Supplier<T>)`](../../locker-contracts/src/main/java/study/locker/common/Rpc.java#L24) | 1 | Unary reply completion and exception translation. Completes a unary response or converts an exception into a status. |

**Refactor to consider.** Keep channel construction and server response handling in separate transport helpers.

## RpcFailures

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class RpcFailures`](../../locker-contracts/src/main/java/study/locker/common/RpcFailures.java#L12) | 3 | Attribution of downstream failures to a breaker · Which gRPC responses are eligible for replay · Internal trailers carrying the failure origin and load-shedding flag. |
| [`RpcFailures()`](../../locker-contracts/src/main/java/study/locker/common/RpcFailures.java#L18) | 0 | Empty private constructor prevents instantiation and owns no independent behavior. |
| [`ignoreInBreaker(Throwable)`](../../locker-contracts/src/main/java/study/locker/common/RpcFailures.java#L20) | 2 | Attribution of downstream failures to a breaker · Internal trailers carrying the failure origin and load-shedding flag. **Suggested change.** Decode the origin once, then pass the local/downstream distinction to the breaker policy. |
| [`countsAgainstDependency(Throwable)`](../../locker-contracts/src/main/java/study/locker/common/RpcFailures.java#L25) | 2 | Attribution of downstream failures to a breaker · Internal trailers carrying the failure origin and load-shedding flag. **Suggested change.** Decode the origin once, then pass the local/downstream distinction to the breaker policy. |
| [`canRetry(StatusRuntimeException)`](../../locker-contracts/src/main/java/study/locker/common/RpcFailures.java#L36) | 2 | Which gRPC responses are eligible for replay · Internal trailers carrying the failure origin and load-shedding flag. **Suggested change.** Expose load shedding and origin through typed trailer read/write operations. |
| [`fromDependency(String, StatusRuntimeException)`](../../locker-contracts/src/main/java/study/locker/common/RpcFailures.java#L41) | 2 | Attribution of downstream failures to a breaker · Internal trailers carrying the failure origin and load-shedding flag. **Suggested change.** Expose load shedding and origin through typed trailer read/write operations. |
| [`loadShed(String)`](../../locker-contracts/src/main/java/study/locker/common/RpcFailures.java#L54) | 1 | Internal trailers carrying the failure origin and load-shedding flag. |
| [`hasTrailer(StatusRuntimeException, Metadata.Key<String>)`](../../locker-contracts/src/main/java/study/locker/common/RpcFailures.java#L63) | 1 | Internal trailers carrying the failure origin and load-shedding flag. |

**Refactor to consider.** Encapsulate the trailer format and let retry and breaker policies consume decoded failure information.

## RpcGuard

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class RpcGuard`](../../locker-contracts/src/main/java/study/locker/common/RpcGuard.java#L26) | 3 | Logical-call and retry policy under one deadline · Concurrency admission and immediate load shedding · Metric names, labels and breaker state encoding. |
| [`RpcGuard(String, Duration, Settings, int, MeterRegistry)`](../../locker-contracts/src/main/java/study/locker/common/RpcGuard.java#L54) | 3 | Logical-call and retry policy under one deadline · Concurrency admission and immediate load shedding · Metric names, labels and breaker state encoding. **Suggested change.** Build the breaker and admission limiter in configuration and bind their meters separately. |
| [`call(boolean, Function<Deadline, T>)`](../../locker-contracts/src/main/java/study/locker/common/RpcGuard.java#L75) | 1 | Logical-call and retry policy under one deadline. Measures the logical request through the breaker and preserves downstream attribution. |
| [`callDeadline()`](../../locker-contracts/src/main/java/study/locker/common/RpcGuard.java#L88) | 1 | Logical-call and retry policy under one deadline. Chooses the earlier of the caller deadline and the configured timeout. |
| [`callWithRetry(Supplier<T>, Deadline, boolean)`](../../locker-contracts/src/main/java/study/locker/common/RpcGuard.java#L94) | 2 | Logical-call and retry policy under one deadline · Metric names, labels and breaker state encoding. Shares the original deadline across transient failures, jitter and the follow-up attempt. **Suggested change.** Expose a retry notification to the meter binder at the point a second attempt is sent. |
| [`retriesEnabled()`](../../locker-contracts/src/main/java/study/locker/common/RpcGuard.java#L117) | 1 | Logical-call and retry policy under one deadline. |
| [`attempt(Deadline, Function<Deadline, T>)`](../../locker-contracts/src/main/java/study/locker/common/RpcGuard.java#L121) | 2 | Logical-call and retry policy under one deadline · Concurrency admission and immediate load shedding. Checks deadline/cancellation before acquiring a bulkhead permit. **Suggested change.** Let the deadline check guard a separately acquired permit. |
| [`rejectCall()`](../../locker-contracts/src/main/java/study/locker/common/RpcGuard.java#L135) | 2 | Concurrency admission and immediate load shedding · Metric names, labels and breaker state encoding. Counts a refusal and emits the marked unavailable status. **Suggested change.** Report limiter rejections through a callback while keeping their wire status in the failure mapper. |
| [`breakerState(CircuitBreaker)`](../../locker-contracts/src/main/java/study/locker/common/RpcGuard.java#L140) | 1 | Metric names, labels and breaker state encoding. |

**Refactor to consider.** Keep the deadline and retry coordinator at the call boundary and move limiter admission and metric binding into focused collaborators.

## RpcGuard.Settings

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`record Settings`](../../locker-contracts/src/main/java/study/locker/common/RpcGuard.java#L27) | 2 | Binding and validation of RPC policy settings · Resilience4j breaker construction and failure classification. |
| [`Settings(Integer, int, int, float, Duration, int, Duration, boolean)`](../../locker-contracts/src/main/java/study/locker/common/RpcGuard.java#L32) | 1 | Binding and validation of RPC policy settings. |
| [`breakerConfig()`](../../locker-contracts/src/main/java/study/locker/common/RpcGuard.java#L38) | 1 | Resilience4j breaker construction and failure classification. |

**Refactor to consider.** Have the gRPC composition root translate settings into the breaker configuration.

## RpcGuardTest

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class RpcGuardTest`](../../locker-contracts/src/test/java/study/locker/common/RpcGuardTest.java#L17) | 3 | Dependency-specific breaker isolation and recovery · Permit exhaustion and release · Eligibility and deadline sharing for retries. |
| [`guard(String, int)`](../../locker-contracts/src/test/java/study/locker/common/RpcGuardTest.java#L20) | 3 | Dependency-specific breaker isolation and recovery · Permit exhaustion and release · Eligibility and deadline sharing for retries. **Suggested change.** Give the fixture named breaker, permit and retry settings so a test overrides only the policy it exercises. |
| [`directoryOutageShedsSearchesAndLeavesIndependentLookupsAvailable()`](../../locker-contracts/src/test/java/study/locker/common/RpcGuardTest.java#L30) | 1 | Dependency-specific breaker isolation and recovery. |
| [`fullBulkheadRejectsImmediatelyAndReturnsPermit()`](../../locker-contracts/src/test/java/study/locker/common/RpcGuardTest.java#L63) | 1 | Permit exhaustion and release. |
| [`aRetrySharesTheOriginalDeadlineAndBusinessErrorsDoNotRetry()`](../../locker-contracts/src/test/java/study/locker/common/RpcGuardTest.java#L91) | 1 | Eligibility and deadline sharing for retries. |

**Refactor to consider.** Organize the independent behaviors into named test groups that share a small guard fixture.
