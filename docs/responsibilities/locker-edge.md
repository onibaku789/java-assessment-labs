# locker-edge responsibility review

[All modules and counting conventions](README.md)

11 Java files. 17 types, 39 methods and 6 explicit constructors. 6 types and 10 members have multiple reasons to change.

## GrpcLockerOperations

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class GrpcLockerOperations`](../../locker-edge/src/main/java/study/locker/edge/adapter/grpc/GrpcLockerOperations.java#L16) | 3 | Translation between gateway operations and Reservations protobuf messages · Forwarding the caller credential and gRPC failures · Choosing which outbound operations may be replayed. |
| [`GrpcLockerOperations(Channel, RpcGuard)`](../../locker-edge/src/main/java/study/locker/edge/adapter/grpc/GrpcLockerOperations.java#L20) | 1 | Translation between gateway operations and Reservations protobuf messages. |
| [`stub(String)`](../../locker-edge/src/main/java/study/locker/edge/adapter/grpc/GrpcLockerOperations.java#L25) | 1 | Forwarding the caller credential and gRPC failures. Attaches the bearer credential to the blocking stub. |
| [`available(String, String, String)`](../../locker-edge/src/main/java/study/locker/edge/adapter/grpc/GrpcLockerOperations.java#L31) | 2 | Translation between gateway operations and Reservations protobuf messages · Choosing which outbound operations may be replayed. **Suggested change.** Declare replay eligibility once at the operation boundary and pass the wire call to an authenticated executor. |
| [`get(String, String)`](../../locker-edge/src/main/java/study/locker/edge/adapter/grpc/GrpcLockerOperations.java#L39) | 2 | Translation between gateway operations and Reservations protobuf messages · Choosing which outbound operations may be replayed. **Suggested change.** Declare replay eligibility once at the operation boundary and pass the wire call to an authenticated executor. |
| [`command(String, Action, String, String, String)`](../../locker-edge/src/main/java/study/locker/edge/adapter/grpc/GrpcLockerOperations.java#L44) | 2 | Translation between gateway operations and Reservations protobuf messages · Choosing which outbound operations may be replayed. Dispatches the wire operation and uses a valid command UUID to determine replay eligibility. **Suggested change.** Declare replay eligibility once at the operation boundary and pass the wire call to an authenticated executor. |
| [`reservation(Booking)`](../../locker-edge/src/main/java/study/locker/edge/adapter/grpc/GrpcLockerOperations.java#L67) | 1 | Translation between gateway operations and Reservations protobuf messages. |
| [`validKey(String)`](../../locker-edge/src/main/java/study/locker/edge/adapter/grpc/GrpcLockerOperations.java#L71) | 1 | Choosing which outbound operations may be replayed. |
| [`rpc(String, boolean, Function<ReservationsGrpc.ReservationsBlockingStub, T>)`](../../locker-edge/src/main/java/study/locker/edge/adapter/grpc/GrpcLockerOperations.java#L80) | 2 | Forwarding the caller credential and gRPC failures · Choosing which outbound operations may be replayed. Applies the shared guard and translates gRPC status into a gateway failure. **Suggested change.** Have the transport executor own status translation and apply the supplied replay policy around it. |
| [`text(String)`](../../locker-edge/src/main/java/study/locker/edge/adapter/grpc/GrpcLockerOperations.java#L93) | 1 | Translation between gateway operations and Reservations protobuf messages. |

**Refactor to consider.** Keep command replay eligibility alongside request-ID handling and extract authenticated transport invocation from protobuf mapping.

## ObservedLockerOperations

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`record ObservedLockerOperations`](../../locker-edge/src/main/java/study/locker/edge/adapter/observability/ObservedLockerOperations.java#L14) | 1 | Gateway observation names, outcomes and durations for business operations. |
| [`available(String, String, String)`](../../locker-edge/src/main/java/study/locker/edge/adapter/observability/ObservedLockerOperations.java#L17) | 1 | Gateway observation names, outcomes and durations for business operations. |
| [`get(String, String)`](../../locker-edge/src/main/java/study/locker/edge/adapter/observability/ObservedLockerOperations.java#L22) | 1 | Gateway observation names, outcomes and durations for business operations. |
| [`command(String, Action, String, String, String)`](../../locker-edge/src/main/java/study/locker/edge/adapter/observability/ObservedLockerOperations.java#L27) | 1 | Gateway observation names, outcomes and durations for business operations. |
| [`observe(String, String, String, Supplier<T>)`](../../locker-edge/src/main/java/study/locker/edge/adapter/observability/ObservedLockerOperations.java#L37) | 1 | Gateway observation names, outcomes and durations for business operations. |

## CallerQuota

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class CallerQuota`](../../locker-edge/src/main/java/study/locker/edge/adapter/security/CallerQuota.java#L22) | 3 | Token budget and shared-to-local fallback policy · Stable pseudonymous keys for issuer and subject · Quota telemetry and protocol-facing rejection information. |
| [`CallerQuota(Settings, Consumer<String>)`](../../locker-edge/src/main/java/study/locker/edge/adapter/security/CallerQuota.java#L50) | 1 | Token budget and shared-to-local fallback policy. |
| [`charge(String, String)`](../../locker-edge/src/main/java/study/locker/edge/adapter/security/CallerQuota.java#L66) | 3 | Token budget and shared-to-local fallback policy · Stable pseudonymous keys for issuer and subject · Quota telemetry and protocol-facing rejection information. Uses the shared decision first and translates an exhausted budget into a timed rejection. **Suggested change.** Build the caller key separately and translate the admission result at the gateway boundary. |
| [`consumeLocally(String)`](../../locker-edge/src/main/java/study/locker/edge/adapter/security/CallerQuota.java#L84) | 2 | Token budget and shared-to-local fallback policy · Quota telemetry and protocol-facing rejection information. Selects the caller’s bounded local bucket and records fallback. **Suggested change.** Have the caller observing a store outage record fallback and let the local bucket only consume. |
| [`perMinute(int, int)`](../../locker-edge/src/main/java/study/locker/edge/adapter/security/CallerQuota.java#L91) | 1 | Token budget and shared-to-local fallback policy. |
| [`identityHash(String, String)`](../../locker-edge/src/main/java/study/locker/edge/adapter/security/CallerQuota.java#L98) | 1 | Stable pseudonymous keys for issuer and subject. Keys the bucket by a digest of the issuer and subject separated by a zero byte. |
| [`close()`](../../locker-edge/src/main/java/study/locker/edge/adapter/security/CallerQuota.java#L110) | 1 | Token budget and shared-to-local fallback policy. |

**Refactor to consider.** Encapsulate identity-key generation and expose admission outcomes to a metrics and error-mapping decorator.

## CallerQuota.Settings

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`record Settings`](../../locker-edge/src/main/java/study/locker/edge/adapter/security/CallerQuota.java#L23) | 1 | External names and valid ranges of gateway quota settings. |
| [`Settings(String, Duration, int, int, int, int, int)`](../../locker-edge/src/main/java/study/locker/edge/adapter/security/CallerQuota.java#L32) | 1 | External names and valid ranges of gateway quota settings. |

## QuotaOperations

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`record QuotaOperations`](../../locker-edge/src/main/java/study/locker/edge/adapter/security/QuotaOperations.java#L10) | 1 | Charging the authenticated issuer and subject once for every external business operation. |
| [`charge()`](../../locker-edge/src/main/java/study/locker/edge/adapter/security/QuotaOperations.java#L12) | 1 | Charging the authenticated issuer and subject once for every external business operation. Requires an authenticated JWT before charging the issuer and subject. |
| [`available(String, String, String)`](../../locker-edge/src/main/java/study/locker/edge/adapter/security/QuotaOperations.java#L19) | 1 | Charging the authenticated issuer and subject once for every external business operation. |
| [`get(String, String)`](../../locker-edge/src/main/java/study/locker/edge/adapter/security/QuotaOperations.java#L24) | 1 | Charging the authenticated issuer and subject once for every external business operation. |
| [`command(String, Action, String, String, String)`](../../locker-edge/src/main/java/study/locker/edge/adapter/security/QuotaOperations.java#L29) | 1 | Charging the authenticated issuer and subject once for every external business operation. |

## ValkeyQuotaStore

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class ValkeyQuotaStore`](../../locker-edge/src/main/java/study/locker/edge/adapter/security/ValkeyQuotaStore.java#L25) | 2 | Lettuce connection lifecycle, timeouts and reconnection · Distributed bucket consumption and expiry settings. |
| [`ValkeyQuotaStore(String, Duration, Bandwidth)`](../../locker-edge/src/main/java/study/locker/edge/adapter/security/ValkeyQuotaStore.java#L33) | 2 | Lettuce connection lifecycle, timeouts and reconnection · Distributed bucket consumption and expiry settings. **Suggested change.** Supply the connected client or connection manager to the bucket store so network lifecycle and budget consumption can be tested independently. |
| [`tryConsume(String)`](../../locker-edge/src/main/java/study/locker/edge/adapter/security/ValkeyQuotaStore.java#L51) | 2 | Lettuce connection lifecycle, timeouts and reconnection · Distributed bucket consumption and expiry settings. Bounds the distributed consumption request and distinguishes an unavailable store from a denied request. **Suggested change.** Supply the connected client or connection manager to the bucket store so network lifecycle and budget consumption can be tested independently. |
| [`connect()`](../../locker-edge/src/main/java/study/locker/edge/adapter/security/ValkeyQuotaStore.java#L67) | 1 | Lettuce connection lifecycle, timeouts and reconnection. Shares the initial connection attempt and controls reconnection after a failed start. |
| [`bucketManager(StatefulRedisConnection<byte[], byte[]>)`](../../locker-edge/src/main/java/study/locker/edge/adapter/security/ValkeyQuotaStore.java#L82) | 1 | Distributed bucket consumption and expiry settings. |
| [`close()`](../../locker-edge/src/main/java/study/locker/edge/adapter/security/ValkeyQuotaStore.java#L94) | 1 | Lettuce connection lifecycle, timeouts and reconnection. |

**Refactor to consider.** Keep Redis connection ownership separate from the Bucket4j proxy configuration and consumption call.

## GatewayFailure

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class GatewayFailure`](../../locker-edge/src/main/java/study/locker/edge/application/GatewayFailure.java#L3) | 1 | Gateway failure code, public message and retry-after metadata. |
| [`GatewayFailure(String, String)`](../../locker-edge/src/main/java/study/locker/edge/application/GatewayFailure.java#L8) | 1 | Gateway failure code, public message and retry-after metadata. |
| [`retryAfterSeconds()`](../../locker-edge/src/main/java/study/locker/edge/application/GatewayFailure.java#L12) | 1 | Gateway failure code, public message and retry-after metadata. |
| [`GatewayFailure(String, String, long)`](../../locker-edge/src/main/java/study/locker/edge/application/GatewayFailure.java#L16) | 1 | Gateway failure code, public message and retry-after metadata. |
| [`code()`](../../locker-edge/src/main/java/study/locker/edge/application/GatewayFailure.java#L22) | 1 | Gateway failure code, public message and retry-after metadata. |

## LockerOperations

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`interface LockerOperations`](../../locker-edge/src/main/java/study/locker/edge/application/LockerOperations.java#L5) | 1 | Protocol-neutral operations exposed by the gateway application. |
| [`available(String, String, String)`](../../locker-edge/src/main/java/study/locker/edge/application/LockerOperations.java#L20) | 1 | Protocol-neutral operations exposed by the gateway application. |
| [`get(String, String)`](../../locker-edge/src/main/java/study/locker/edge/application/LockerOperations.java#L22) | 1 | Protocol-neutral operations exposed by the gateway application. |
| [`command(String, Action, String, String, String)`](../../locker-edge/src/main/java/study/locker/edge/application/LockerOperations.java#L24) | 1 | Protocol-neutral operations exposed by the gateway application. |

## LockerOperations.Action

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`enum Action`](../../locker-edge/src/main/java/study/locker/edge/application/LockerOperations.java#L6) | 1 | Supported gateway command vocabulary. |

## LockerOperations.Slot

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`record Slot`](../../locker-edge/src/main/java/study/locker/edge/application/LockerOperations.java#L14) | 1 | Compartment fields returned to gateway protocols. |

## LockerOperations.Reservation

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`record Reservation`](../../locker-edge/src/main/java/study/locker/edge/application/LockerOperations.java#L17) | 1 | Reservation fields returned to gateway protocols. |

## EdgeConfig

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class EdgeConfig`](../../locker-edge/src/main/java/study/locker/edge/bootstrap/EdgeConfig.java#L19) | 1 | Wiring gateway gRPC calls and application decorators. |
| [`reservationChannel(String)`](../../locker-edge/src/main/java/study/locker/edge/bootstrap/EdgeConfig.java#L23) | 1 | Wiring gateway gRPC calls and application decorators. |
| [`gateway(ManagedChannel, MeterRegistry, String, Duration, RpcGuard.Settings, CallerQuota)`](../../locker-edge/src/main/java/study/locker/edge/bootstrap/EdgeConfig.java#L28) | 1 | Wiring gateway gRPC calls and application decorators. |

## EdgeWebSecurity

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class EdgeWebSecurity`](../../locker-edge/src/main/java/study/locker/edge/bootstrap/EdgeWebSecurity.java#L16) | 3 | Authentication rules for web endpoints · Cross-origin access for authorized browser clients · Gateway quota construction and measurement. |
| [`webSecurity(HttpSecurity)`](../../locker-edge/src/main/java/study/locker/edge/bootstrap/EdgeWebSecurity.java#L19) | 1 | Authentication rules for web endpoints. |
| [`corsConfigurationSource(List<String>)`](../../locker-edge/src/main/java/study/locker/edge/bootstrap/EdgeWebSecurity.java#L29) | 1 | Cross-origin access for authorized browser clients. |
| [`callerQuota(CallerQuota.Settings, MeterRegistry)`](../../locker-edge/src/main/java/study/locker/edge/bootstrap/EdgeWebSecurity.java#L41) | 1 | Gateway quota construction and measurement. |

**Refactor to consider.** Group the quota factory with admission control and isolate CORS policy from endpoint authentication.

## GrpcBoundaryTest

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class GrpcBoundaryTest`](../../locker-edge/src/test/java/study/locker/edge/integration/GrpcBoundaryTest.java#L22) | 3 | Forwarded bearer credentials · Surface status for an unavailable upstream · Deadline on a stalled lookup. |
| [`forwardsCallerPropagatesOutageAndBoundsAStalledLookup()`](../../locker-edge/src/test/java/study/locker/edge/integration/GrpcBoundaryTest.java#L23) | 3 | Forwarded bearer credentials · Surface status for an unavailable upstream · Deadline on a stalled lookup. **Suggested change.** Exercise metadata forwarding, unavailable status and timeout in three named checks against the same upstream fixture. |

**Refactor to consider.** Share the server fixture across separate credential, status and deadline assertions.

## GrpcBoundaryTest.anonymous@28

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class anonymous@28`](../../locker-edge/src/test/java/study/locker/edge/integration/GrpcBoundaryTest.java#L28) | 1 | Capture of caller credentials and deadlines observed by the test server. |
| [`interceptCall(ServerCall<Q, R>, Metadata, ServerCallHandler<Q, R>)`](../../locker-edge/src/test/java/study/locker/edge/integration/GrpcBoundaryTest.java#L29) | 1 | Capture of caller credentials and deadlines observed by the test server. |

## GrpcBoundaryTest.anonymous@40

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class anonymous@40`](../../locker-edge/src/test/java/study/locker/edge/integration/GrpcBoundaryTest.java#L40) | 1 | Simulated offline and stalled lookup behavior in the test upstream. |
| [`get(Key, StreamObserver<Booking>)`](../../locker-edge/src/test/java/study/locker/edge/integration/GrpcBoundaryTest.java#L41) | 1 | Simulated offline and stalled lookup behavior in the test upstream. |

## QuotaIT

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class QuotaIT`](../../locker-edge/src/test/java/study/locker/edge/integration/QuotaIT.java#L17) | 2 | Shared token accounting across replicas · Bounded per-instance admission during a store outage and return to shared decisions. |
| [`replicasShareTokensAndRecoverFromABoundedLocalFallback()`](../../locker-edge/src/test/java/study/locker/edge/integration/QuotaIT.java#L23) | 2 | Shared token accounting across replicas · Bounded per-instance admission during a store outage and return to shared decisions. **Suggested change.** Split the healthy shared-store sequence from the outage/recovery sequence. |

**Refactor to consider.** Give shared-budget and fallback assertions separate scenarios with the same Valkey fixture.
