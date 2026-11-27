# reservation-service responsibility review

[All modules and counting conventions](README.md)

59 Java files. 84 types, 180 methods and 13 explicit constructors. 21 types and 18 members have multiple reasons to change.

## ReservationApp

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class ReservationApp`](../../reservation-service/src/main/java/study/locker/reservation/ReservationApp.java#L7) | 1 | Spring Boot startup and scheduling activation for Reservations. |
| [`main(String[])`](../../reservation-service/src/main/java/study/locker/reservation/ReservationApp.java#L10) | 1 | Spring Boot startup and scheduling activation for Reservations. |

## SecurePickupCodes

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`record SecurePickupCodes`](../../reservation-service/src/main/java/study/locker/reservation/adapter/crypto/SecurePickupCodes.java#L11) | 2 | Issuance of a fresh code with the configured lifetime and failure budget · Binding the verifier and mail envelope to their respective identities. |
| [`issue(Reservation, Instant)`](../../reservation-service/src/main/java/study/locker/reservation/adapter/crypto/SecurePickupCodes.java#L13) | 2 | Issuance of a fresh code with the configured lifetime and failure budget · Binding the verifier and mail envelope to their respective identities. **Suggested change.** Let the issuance policy choose the fresh candidate and delegate verifier and envelope construction. |
| [`matches(UUID, String, String)`](../../reservation-service/src/main/java/study/locker/reservation/adapter/crypto/SecurePickupCodes.java#L30) | 1 | Binding the verifier and mail envelope to their respective identities. |

**Refactor to consider.** Keep the issuance policy in a factory and the verifier/envelope construction in the crypto adapter.

## GrpcDirectory

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`record GrpcDirectory`](../../reservation-service/src/main/java/study/locker/reservation/adapter/grpcclient/GrpcDirectory.java#L9) | 1 | Directory protobuf lookup/search contract and deadline propagation on reservation calls. |
| [`search(String, String)`](../../reservation-service/src/main/java/study/locker/reservation/adapter/grpcclient/GrpcDirectory.java#L11) | 1 | Directory protobuf lookup/search contract and deadline propagation on reservation calls. |
| [`requireCompartment(String)`](../../reservation-service/src/main/java/study/locker/reservation/adapter/grpcclient/GrpcDirectory.java#L21) | 1 | Directory protobuf lookup/search contract and deadline propagation on reservation calls. |

## ReservationEndpoint

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class ReservationEndpoint`](../../reservation-service/src/main/java/study/locker/reservation/adapter/grpcserver/ReservationEndpoint.java#L19) | 3 | Translation of protobuf requests and replies · Authentication and public gRPC status for rejected work · Command fingerprint inputs used on replay. |
| [`ReservationEndpoint(ReservationAccess, PickupSecrets)`](../../reservation-service/src/main/java/study/locker/reservation/adapter/grpcserver/ReservationEndpoint.java#L23) | 1 | Translation of protobuf requests and replies. |
| [`available(Search, StreamObserver<Compartments>)`](../../reservation-service/src/main/java/study/locker/reservation/adapter/grpcserver/ReservationEndpoint.java#L28) | 1 | Translation of protobuf requests and replies. |
| [`get(Key, StreamObserver<Booking>)`](../../reservation-service/src/main/java/study/locker/reservation/adapter/grpcserver/ReservationEndpoint.java#L34) | 1 | Translation of protobuf requests and replies. |
| [`reserve(Command, StreamObserver<Booking>)`](../../reservation-service/src/main/java/study/locker/reservation/adapter/grpcserver/ReservationEndpoint.java#L38) | 1 | Translation of protobuf requests and replies. |
| [`cancel(Command, StreamObserver<Booking>)`](../../reservation-service/src/main/java/study/locker/reservation/adapter/grpcserver/ReservationEndpoint.java#L42) | 1 | Translation of protobuf requests and replies. |
| [`deposit(Command, StreamObserver<Booking>)`](../../reservation-service/src/main/java/study/locker/reservation/adapter/grpcserver/ReservationEndpoint.java#L46) | 1 | Translation of protobuf requests and replies. |
| [`reissue(Command, StreamObserver<Booking>)`](../../reservation-service/src/main/java/study/locker/reservation/adapter/grpcserver/ReservationEndpoint.java#L50) | 1 | Translation of protobuf requests and replies. |
| [`collect(Command, StreamObserver<Booking>)`](../../reservation-service/src/main/java/study/locker/reservation/adapter/grpcserver/ReservationEndpoint.java#L54) | 1 | Translation of protobuf requests and replies. |
| [`command(Action, Command, StreamObserver<Booking>)`](../../reservation-service/src/main/java/study/locker/reservation/adapter/grpcserver/ReservationEndpoint.java#L58) | 2 | Translation of protobuf requests and replies · Command fingerprint inputs used on replay. Fingerprints the serialized command and constructs the application request. **Suggested change.** Use one canonical request-fingerprint builder independent of protobuf-to-command mapping. |
| [`wire(Reservation.View)`](../../reservation-service/src/main/java/study/locker/reservation/adapter/grpcserver/ReservationEndpoint.java#L67) | 1 | Translation of protobuf requests and replies. |
| [`reply(StreamObserver<T>, Supplier<T>)`](../../reservation-service/src/main/java/study/locker/reservation/adapter/grpcserver/ReservationEndpoint.java#L72) | 1 | Authentication and public gRPC status for rejected work. Runs the operation with an authenticated caller and maps validation or domain failure to gRPC. |
| [`status(ReservationFailure)`](../../reservation-service/src/main/java/study/locker/reservation/adapter/grpcserver/ReservationEndpoint.java#L86) | 1 | Authentication and public gRPC status for rejected work. Maps the domain failure reason to the public RPC status. |

**Refactor to consider.** Isolate the authenticated reply mapper and the canonical fingerprint input from RPC dispatch.

## KafkaPublisher

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`record KafkaPublisher`](../../reservation-service/src/main/java/study/locker/reservation/adapter/kafka/KafkaPublisher.java#L10) | 1 | Kafka send and acknowledgement deadline for one outbox event. |
| [`publish(PublishOutbox.Message)`](../../reservation-service/src/main/java/study/locker/reservation/adapter/kafka/KafkaPublisher.java#L12) | 1 | Kafka send and acknowledgement deadline for one outbox event. |

## ObservedCommands

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`record ObservedCommands`](../../reservation-service/src/main/java/study/locker/reservation/adapter/observability/ObservedCommands.java#L7) | 1 | Recording committed, replayed and failed reservation command outcomes. |
| [`execute(Caller, Command)`](../../reservation-service/src/main/java/study/locker/reservation/adapter/observability/ObservedCommands.java#L8) | 1 | Recording committed, replayed and failed reservation command outcomes. |

## ObservedPublisher

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`record ObservedPublisher`](../../reservation-service/src/main/java/study/locker/reservation/adapter/observability/ObservedPublisher.java#L14) | 2 | Trace context propagation from the outbox row · Logging failed broker publications. |
| [`publish(PublishOutbox.Message)`](../../reservation-service/src/main/java/study/locker/reservation/adapter/observability/ObservedPublisher.java#L16) | 2 | Trace context propagation from the outbox row · Logging failed broker publications. Restores parent trace context, emits a publish span and reports the outcome. **Suggested change.** Wrap the traced publisher with separate failure reporting while preserving the parent trace. |
| [`send(PublishOutbox.Message)`](../../reservation-service/src/main/java/study/locker/reservation/adapter/observability/ObservedPublisher.java#L51) | 1 | Logging failed broker publications. |

**Refactor to consider.** Keep the trace decorator and failed-publication log handler separately testable.

## ObservedPublisher.anonymous@41

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class anonymous@41`](../../reservation-service/src/main/java/study/locker/reservation/adapter/observability/ObservedPublisher.java#L41) | 1 | Reading trace context keys and values from a header map. |
| [`keys(Map<String, String>)`](../../reservation-service/src/main/java/study/locker/reservation/adapter/observability/ObservedPublisher.java#L42) | 1 | Reading trace context keys and values from a header map. |
| [`get(Map<String, String>, String)`](../../reservation-service/src/main/java/study/locker/reservation/adapter/observability/ObservedPublisher.java#L46) | 1 | Reading trace context keys and values from a header map. |

## TraceCommandTransaction

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`record TraceCommandTransaction`](../../reservation-service/src/main/java/study/locker/reservation/adapter/observability/TraceCommandTransaction.java#L15) | 1 | Recording the sampled trace parent in the PostgreSQL transaction for downstream correlation. |
| [`execute(Caller, Command)`](../../reservation-service/src/main/java/study/locker/reservation/adapter/observability/TraceCommandTransaction.java#L17) | 1 | Recording the sampled trace parent in the PostgreSQL transaction for downstream correlation. Uses a local PostgreSQL transaction setting to correlate sampled work. |

## BookingRow

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class BookingRow`](../../reservation-service/src/main/java/study/locker/reservation/adapter/persistence/BookingRow.java#L10) | 1 | Persistence representation and round-trip reconstruction of a reservation. |
| [`BookingRow()`](../../reservation-service/src/main/java/study/locker/reservation/adapter/persistence/BookingRow.java#L13) | 1 | Persistence representation and round-trip reconstruction of a reservation. |
| [`domain()`](../../reservation-service/src/main/java/study/locker/reservation/adapter/persistence/BookingRow.java#L31) | 1 | Persistence representation and round-trip reconstruction of a reservation. |
| [`apply(Reservation)`](../../reservation-service/src/main/java/study/locker/reservation/adapter/persistence/BookingRow.java#L38) | 1 | Persistence representation and round-trip reconstruction of a reservation. |

## BookingRows

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`interface BookingRows`](../../reservation-service/src/main/java/study/locker/reservation/adapter/persistence/BookingRows.java#L16) | 1 | PostgreSQL queries for availability, exclusive insertion and row locking. |
| [`findForUpdateById(UUID)`](../../reservation-service/src/main/java/study/locker/reservation/adapter/persistence/BookingRows.java#L17) | 1 | PostgreSQL queries for availability, exclusive insertion and row locking. |
| [`findOccupiedCompartments(List<String>)`](../../reservation-service/src/main/java/study/locker/reservation/adapter/persistence/BookingRows.java#L20) | 1 | PostgreSQL queries for availability, exclusive insertion and row locking. |
| [`insertIfAvailable(UUID, String, String, String)`](../../reservation-service/src/main/java/study/locker/reservation/adapter/persistence/BookingRows.java#L27) | 1 | PostgreSQL queries for availability, exclusive insertion and row locking. |

## CommandRow

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class CommandRow`](../../reservation-service/src/main/java/study/locker/reservation/adapter/persistence/CommandRow.java#L10) | 2 | Column layout of an idempotent result · Spring Data new/existing lifecycle for an assigned composite key. |
| [`CommandRow()`](../../reservation-service/src/main/java/study/locker/reservation/adapter/persistence/CommandRow.java#L30) | 1 | Column layout of an idempotent result. |
| [`CommandRow(Key, String, byte[], String)`](../../reservation-service/src/main/java/study/locker/reservation/adapter/persistence/CommandRow.java#L33) | 1 | Column layout of an idempotent result. |
| [`getId()`](../../reservation-service/src/main/java/study/locker/reservation/adapter/persistence/CommandRow.java#L40) | 1 | Column layout of an idempotent result. |
| [`isNew()`](../../reservation-service/src/main/java/study/locker/reservation/adapter/persistence/CommandRow.java#L45) | 1 | Spring Data new/existing lifecycle for an assigned composite key. |
| [`markPersisted()`](../../reservation-service/src/main/java/study/locker/reservation/adapter/persistence/CommandRow.java#L50) | 1 | Spring Data new/existing lifecycle for an assigned composite key. Updates the assigned-ID lifecycle flag after insertion or database hydration. |

**Refactor to consider.** Keep assigned-key lifecycle behavior isolated from snapshot columns if additional entities need the same callbacks.

## CommandRow.Key

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`record Key`](../../reservation-service/src/main/java/study/locker/reservation/adapter/persistence/CommandRow.java#L13) | 1 | Composite identity of a stored command result. |

## CommandRows

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`interface CommandRows`](../../reservation-service/src/main/java/study/locker/reservation/adapter/persistence/CommandRows.java#L6) | 1 | Serialization of concurrent attempts to apply one caller request key. |
| [`lockRequest(String)`](../../reservation-service/src/main/java/study/locker/reservation/adapter/persistence/CommandRows.java#L8) | 1 | Serialization of concurrent attempts to apply one caller request key. |

## JpaCommandJournal

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class JpaCommandJournal`](../../reservation-service/src/main/java/study/locker/reservation/adapter/persistence/JpaCommandJournal.java#L15) | 2 | Persistence and locking of the per-caller request journal · Compatibility of the serialized Booking snapshot in existing result rows. |
| [`JpaCommandJournal(CommandRows)`](../../reservation-service/src/main/java/study/locker/reservation/adapter/persistence/JpaCommandJournal.java#L19) | 1 | Persistence and locking of the per-caller request journal. |
| [`lock(String, UUID)`](../../reservation-service/src/main/java/study/locker/reservation/adapter/persistence/JpaCommandJournal.java#L23) | 1 | Persistence and locking of the per-caller request journal. |
| [`find(String, UUID)`](../../reservation-service/src/main/java/study/locker/reservation/adapter/persistence/JpaCommandJournal.java#L28) | 2 | Persistence and locking of the per-caller request journal · Compatibility of the serialized Booking snapshot in existing result rows. Loads a snapshot in the persisted wire format and reconstructs the original result. **Suggested change.** Move the stored Booking representation into a round-trip codec shared by both paths. |
| [`decode(CommandRow)`](../../reservation-service/src/main/java/study/locker/reservation/adapter/persistence/JpaCommandJournal.java#L33) | 1 | Compatibility of the serialized Booking snapshot in existing result rows. Loads a snapshot in the persisted wire format and reconstructs the original result. |
| [`save(String, UUID, Entry)`](../../reservation-service/src/main/java/study/locker/reservation/adapter/persistence/JpaCommandJournal.java#L47) | 2 | Persistence and locking of the per-caller request journal · Compatibility of the serialized Booking snapshot in existing result rows. Encodes the result as a Booking snapshot and stores it with the caller’s replay key. **Suggested change.** Move the stored Booking representation into a round-trip codec shared by both paths. |

**Refactor to consider.** Extract a stored-result codec with compatibility fixtures while retaining the journal port.

## JpaReservationStore

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class JpaReservationStore`](../../reservation-service/src/main/java/study/locker/reservation/adapter/persistence/JpaReservationStore.java#L13) | 1 | Mapping reservation storage operations to JPA inserts, updates, locking and occupancy queries. |
| [`JpaReservationStore(BookingRows)`](../../reservation-service/src/main/java/study/locker/reservation/adapter/persistence/JpaReservationStore.java#L17) | 1 | Mapping reservation storage operations to JPA inserts, updates, locking and occupancy queries. |
| [`insertIfAvailable(Reservation)`](../../reservation-service/src/main/java/study/locker/reservation/adapter/persistence/JpaReservationStore.java#L21) | 1 | Mapping reservation storage operations to JPA inserts, updates, locking and occupancy queries. |
| [`find(UUID, boolean)`](../../reservation-service/src/main/java/study/locker/reservation/adapter/persistence/JpaReservationStore.java#L29) | 1 | Mapping reservation storage operations to JPA inserts, updates, locking and occupancy queries. |
| [`update(Reservation)`](../../reservation-service/src/main/java/study/locker/reservation/adapter/persistence/JpaReservationStore.java#L35) | 1 | Mapping reservation storage operations to JPA inserts, updates, locking and occupancy queries. |
| [`occupied(List<String>)`](../../reservation-service/src/main/java/study/locker/reservation/adapter/persistence/JpaReservationStore.java#L40) | 1 | Mapping reservation storage operations to JPA inserts, updates, locking and occupancy queries. |

## OutboxRow

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class OutboxRow`](../../reservation-service/src/main/java/study/locker/reservation/adapter/persistence/OutboxRow.java#L9) | 1 | Database representation of a pending publication and its conversion to a relay message. |
| [`OutboxRow()`](../../reservation-service/src/main/java/study/locker/reservation/adapter/persistence/OutboxRow.java#L12) | 1 | Database representation of a pending publication and its conversion to a relay message. |
| [`OutboxRow(String, UUID, UUID, String)`](../../reservation-service/src/main/java/study/locker/reservation/adapter/persistence/OutboxRow.java#L14) | 1 | Database representation of a pending publication and its conversion to a relay message. |
| [`message()`](../../reservation-service/src/main/java/study/locker/reservation/adapter/persistence/OutboxRow.java#L36) | 1 | Database representation of a pending publication and its conversion to a relay message. |

## OutboxRows

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`interface OutboxRows`](../../reservation-service/src/main/java/study/locker/reservation/adapter/persistence/OutboxRows.java#L11) | 1 | PostgreSQL claim, sequence ordering, pending count and acknowledgement semantics of the outbox. |
| [`claimBatch()`](../../reservation-service/src/main/java/study/locker/reservation/adapter/persistence/OutboxRows.java#L13) | 1 | PostgreSQL claim, sequence ordering, pending count and acknowledgement semantics of the outbox. |
| [`findByOrderBySeqAsc(Limit)`](../../reservation-service/src/main/java/study/locker/reservation/adapter/persistence/OutboxRows.java#L17) | 1 | PostgreSQL claim, sequence ordering, pending count and acknowledgement semantics of the outbox. |
| [`pending(int)`](../../reservation-service/src/main/java/study/locker/reservation/adapter/persistence/OutboxRows.java#L19) | 1 | PostgreSQL claim, sequence ordering, pending count and acknowledgement semantics of the outbox. |
| [`acknowledge(long)`](../../reservation-service/src/main/java/study/locker/reservation/adapter/persistence/OutboxRows.java#L24) | 1 | PostgreSQL claim, sequence ordering, pending count and acknowledgement semantics of the outbox. |

## ReservationEventWriter

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class ReservationEventWriter`](../../reservation-service/src/main/java/study/locker/reservation/adapter/persistence/ReservationEventWriter.java#L15) | 3 | Lifecycle event payload and topic contract · Pickup-mail event payload and topic contract · Persistence of a serialized outbox row. |
| [`ReservationEventWriter(OutboxRows, ObjectMapper)`](../../reservation-service/src/main/java/study/locker/reservation/adapter/persistence/ReservationEventWriter.java#L20) | 1 | Persistence of a serialized outbox row. |
| [`lifecycle(Reservation.View, String, Instant)`](../../reservation-service/src/main/java/study/locker/reservation/adapter/persistence/ReservationEventWriter.java#L25) | 1 | Lifecycle event payload and topic contract. Serializes the public reservation view and event time for the lifecycle topic. |
| [`pickupMail(Reservation, PickupCodes.Issue)`](../../reservation-service/src/main/java/study/locker/reservation/adapter/persistence/ReservationEventWriter.java#L47) | 1 | Pickup-mail event payload and topic contract. Builds the notification envelope with the original event identity, recipient and expiration. |
| [`append(String, UUID, UUID, Map<String, Object>)`](../../reservation-service/src/main/java/study/locker/reservation/adapter/persistence/ReservationEventWriter.java#L69) | 1 | Persistence of a serialized outbox row. Persists the topic, reservation, event ID and serialized message in one outbox row. |

**Refactor to consider.** Separate the two message constructors from the shared outbox appender.

## ReservationScopes

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class ReservationScopes`](../../reservation-service/src/main/java/study/locker/reservation/adapter/security/ReservationScopes.java#L9) | 1 | Mapping operation and reader privileges to required locker scopes. |
| [`read(Authentication)`](../../reservation-service/src/main/java/study/locker/reservation/adapter/security/ReservationScopes.java#L11) | 1 | Mapping operation and reader privileges to required locker scopes. |
| [`command(Authentication, Action)`](../../reservation-service/src/main/java/study/locker/reservation/adapter/security/ReservationScopes.java#L15) | 1 | Mapping operation and reader privileges to required locker scopes. |
| [`canReadAnyReservation(Authentication)`](../../reservation-service/src/main/java/study/locker/reservation/adapter/security/ReservationScopes.java#L24) | 1 | Mapping operation and reader privileges to required locker scopes. |

## ReservationSubmission

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class ReservationSubmission`](../../reservation-service/src/main/java/study/locker/reservation/adapter/security/ReservationSubmission.java#L16) | 1 | Validated boundary for submitting a reservation command and returning its business result. |
| [`ReservationSubmission(CommandHandler)`](../../reservation-service/src/main/java/study/locker/reservation/adapter/security/ReservationSubmission.java#L21) | 1 | Validated boundary for submitting a reservation command and returning its business result. |
| [`submit(Caller, Action, UUID, String, String, String)`](../../reservation-service/src/main/java/study/locker/reservation/adapter/security/ReservationSubmission.java#L25) | 1 | Validated boundary for submitting a reservation command and returning its business result. |

## SecuredReservations

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class SecuredReservations`](../../reservation-service/src/main/java/study/locker/reservation/adapter/security/SecuredReservations.java#L15) | 1 | Enforcing operation scopes, ownership and caller identity at the reservation application boundary. |
| [`SecuredReservations(ReservationQueries, ReservationSubmission)`](../../reservation-service/src/main/java/study/locker/reservation/adapter/security/SecuredReservations.java#L20) | 1 | Enforcing operation scopes, ownership and caller identity at the reservation application boundary. |
| [`available(String, String)`](../../reservation-service/src/main/java/study/locker/reservation/adapter/security/SecuredReservations.java#L25) | 1 | Enforcing operation scopes, ownership and caller identity at the reservation application boundary. |
| [`get(String)`](../../reservation-service/src/main/java/study/locker/reservation/adapter/security/SecuredReservations.java#L31) | 1 | Enforcing operation scopes, ownership and caller identity at the reservation application boundary. |
| [`execute(ReservationCommands.Action, Request)`](../../reservation-service/src/main/java/study/locker/reservation/adapter/security/SecuredReservations.java#L42) | 1 | Enforcing operation scopes, ownership and caller identity at the reservation application boundary. |
| [`authentication()`](../../reservation-service/src/main/java/study/locker/reservation/adapter/security/SecuredReservations.java#L56) | 1 | Enforcing operation scopes, ownership and caller identity at the reservation application boundary. |

## TransactionalCommands

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`record TransactionalCommands`](../../reservation-service/src/main/java/study/locker/reservation/adapter/transaction/TransactionalCommands.java#L8) | 1 | Transaction boundary around command execution and stored results. |
| [`execute(Caller, Command)`](../../reservation-service/src/main/java/study/locker/reservation/adapter/transaction/TransactionalCommands.java#L10) | 1 | Transaction boundary around command execution and stored results. |

## TransactionalOutbox

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`record TransactionalOutbox`](../../reservation-service/src/main/java/study/locker/reservation/adapter/transaction/TransactionalOutbox.java#L7) | 1 | Transaction boundary around claiming, publishing and acknowledging an outbox batch. |
| [`publish()`](../../reservation-service/src/main/java/study/locker/reservation/adapter/transaction/TransactionalOutbox.java#L9) | 1 | Transaction boundary around claiming, publishing and acknowledging an outbox batch. |

## Caller

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`record Caller`](../../reservation-service/src/main/java/study/locker/reservation/application/Caller.java#L5) | 1 | Verified customer email required for a fresh reservation. |
| [`verifiedEmail()`](../../reservation-service/src/main/java/study/locker/reservation/application/Caller.java#L6) | 1 | Verified customer email required for a fresh reservation. |
| [`forbidden(String)`](../../reservation-service/src/main/java/study/locker/reservation/application/Caller.java#L12) | 1 | Verified customer email required for a fresh reservation. |

## CommandHandler

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`interface CommandHandler`](../../reservation-service/src/main/java/study/locker/reservation/application/CommandHandler.java#L8) | 1 | Contract for executing one reservation command and distinguishing a replay. |
| [`execute(Caller, Command)`](../../reservation-service/src/main/java/study/locker/reservation/application/CommandHandler.java#L26) | 1 | Contract for executing one reservation command and distinguishing a replay. |

## CommandHandler.Command

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`record Command`](../../reservation-service/src/main/java/study/locker/reservation/application/CommandHandler.java#L9) | 1 | Inputs needed to execute and identify one reservation command. |

## CommandHandler.Result

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`record Result`](../../reservation-service/src/main/java/study/locker/reservation/application/CommandHandler.java#L16) | 1 | Stored business outcome and conversion of a rejected result to a domain failure. |
| [`requireSuccess()`](../../reservation-service/src/main/java/study/locker/reservation/application/CommandHandler.java#L17) | 1 | Stored business outcome and conversion of a rejected result to a domain failure. |

## CommandHandler.Execution

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`record Execution`](../../reservation-service/src/main/java/study/locker/reservation/application/CommandHandler.java#L24) | 1 | Replay marker accompanying a command result. |

## CommandJournal

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`interface CommandJournal`](../../reservation-service/src/main/java/study/locker/reservation/application/CommandJournal.java#L6) | 1 | Port for locking, reading and recording one subject/request result. |
| [`lock(String, UUID)`](../../reservation-service/src/main/java/study/locker/reservation/application/CommandJournal.java#L10) | 1 | Port for locking, reading and recording one subject/request result. |
| [`find(String, UUID)`](../../reservation-service/src/main/java/study/locker/reservation/application/CommandJournal.java#L12) | 1 | Port for locking, reading and recording one subject/request result. |
| [`save(String, UUID, Entry)`](../../reservation-service/src/main/java/study/locker/reservation/application/CommandJournal.java#L14) | 1 | Port for locking, reading and recording one subject/request result. |

## CommandJournal.Entry

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`record Entry`](../../reservation-service/src/main/java/study/locker/reservation/application/CommandJournal.java#L8) | 1 | Fingerprint and replayable result retained per command. |

## Directory

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`interface Directory`](../../reservation-service/src/main/java/study/locker/reservation/application/Directory.java#L5) | 1 | Contract for finding compartment candidates and requiring a valid compartment. |
| [`search(String, String)`](../../reservation-service/src/main/java/study/locker/reservation/application/Directory.java#L8) | 1 | Contract for finding compartment candidates and requiring a valid compartment. |
| [`requireCompartment(String)`](../../reservation-service/src/main/java/study/locker/reservation/application/Directory.java#L10) | 1 | Contract for finding compartment candidates and requiring a valid compartment. |

## Directory.Slot

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`record Slot`](../../reservation-service/src/main/java/study/locker/reservation/application/Directory.java#L6) | 1 | Catalog attributes the reservation service needs to describe availability. |

## IdempotentCommands

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`record IdempotentCommands`](../../reservation-service/src/main/java/study/locker/reservation/application/IdempotentCommands.java#L5) | 1 | Serializing, matching and replaying commands under the same subject/request key. |
| [`execute(Caller, Command)`](../../reservation-service/src/main/java/study/locker/reservation/application/IdempotentCommands.java#L7) | 1 | Serializing, matching and replaying commands under the same subject/request key. Locks the caller/request pair, detects conflicting inputs and reuses the stored result on replay. |

## OutboxRelay

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`interface OutboxRelay`](../../reservation-service/src/main/java/study/locker/reservation/application/OutboxRelay.java#L3) | 1 | Application entry point for publishing pending events. |
| [`publish()`](../../reservation-service/src/main/java/study/locker/reservation/application/OutboxRelay.java#L4) | 1 | Application entry point for publishing pending events. |

## PickupCodes

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`interface PickupCodes`](../../reservation-service/src/main/java/study/locker/reservation/application/PickupCodes.java#L8) | 1 | Port for issuing and verifying reservation-bound pickup credentials. |
| [`issue(Reservation, Instant)`](../../reservation-service/src/main/java/study/locker/reservation/application/PickupCodes.java#L11) | 1 | Port for issuing and verifying reservation-bound pickup credentials. |
| [`matches(UUID, String, String)`](../../reservation-service/src/main/java/study/locker/reservation/application/PickupCodes.java#L13) | 1 | Port for issuing and verifying reservation-bound pickup credentials. |

## PickupCodes.Issue

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`record Issue`](../../reservation-service/src/main/java/study/locker/reservation/application/PickupCodes.java#L9) | 1 | Event identity, verifier policy and mail envelope for one issued pickup code. |

## PublishOutbox

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`record PublishOutbox`](../../reservation-service/src/main/java/study/locker/reservation/application/PublishOutbox.java#L5) | 1 | Ordered publication with acknowledgement before removing each item from a claimed batch. |
| [`publish()`](../../reservation-service/src/main/java/study/locker/reservation/application/PublishOutbox.java#L35) | 1 | Ordered publication with acknowledgement before removing each item from a claimed batch. Stops at the first unacknowledged message and acknowledges successful sends in order. |

## PublishOutbox.Message

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`record Message`](../../reservation-service/src/main/java/study/locker/reservation/application/PublishOutbox.java#L7) | 1 | Outbox event identity, routing, payload and trace context required by a publisher. |

## PublishOutbox.Queue

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`interface Queue`](../../reservation-service/src/main/java/study/locker/reservation/application/PublishOutbox.java#L15) | 1 | Persistence contract for claiming batches, enumerating pending messages and acknowledging delivery. |
| [`claimBatch()`](../../reservation-service/src/main/java/study/locker/reservation/application/PublishOutbox.java#L16) | 1 | Persistence contract for claiming batches, enumerating pending messages and acknowledging delivery. |
| [`pending(int)`](../../reservation-service/src/main/java/study/locker/reservation/application/PublishOutbox.java#L18) | 1 | Persistence contract for claiming batches, enumerating pending messages and acknowledging delivery. |
| [`acknowledge(long)`](../../reservation-service/src/main/java/study/locker/reservation/application/PublishOutbox.java#L20) | 1 | Persistence contract for claiming batches, enumerating pending messages and acknowledging delivery. |
| [`count()`](../../reservation-service/src/main/java/study/locker/reservation/application/PublishOutbox.java#L22) | 1 | Persistence contract for claiming batches, enumerating pending messages and acknowledging delivery. |

## PublishOutbox.Publication

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`enum Publication`](../../reservation-service/src/main/java/study/locker/reservation/application/PublishOutbox.java#L25) | 1 | Broker acknowledgement, failure and interruption outcomes. |

## PublishOutbox.Publisher

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`interface Publisher`](../../reservation-service/src/main/java/study/locker/reservation/application/PublishOutbox.java#L31) | 1 | Port returning the delivery outcome of one outbox message. |
| [`publish(Message)`](../../reservation-service/src/main/java/study/locker/reservation/application/PublishOutbox.java#L32) | 1 | Port returning the delivery outcome of one outbox message. |

## ReservationAccess

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`interface ReservationAccess`](../../reservation-service/src/main/java/study/locker/reservation/application/ReservationAccess.java#L8) | 1 | Application contract for exposed reservation reads and commands. |
| [`available(String, String)`](../../reservation-service/src/main/java/study/locker/reservation/application/ReservationAccess.java#L15) | 1 | Application contract for exposed reservation reads and commands. |
| [`get(String)`](../../reservation-service/src/main/java/study/locker/reservation/application/ReservationAccess.java#L17) | 1 | Application contract for exposed reservation reads and commands. |
| [`execute(ReservationCommands.Action, Request)`](../../reservation-service/src/main/java/study/locker/reservation/application/ReservationAccess.java#L19) | 1 | Application contract for exposed reservation reads and commands. |

## ReservationAccess.Request

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`record Request`](../../reservation-service/src/main/java/study/locker/reservation/application/ReservationAccess.java#L9) | 1 | Request identity, target and replay inputs arriving at the application boundary. |
| [`parsedId()`](../../reservation-service/src/main/java/study/locker/reservation/application/ReservationAccess.java#L10) | 1 | Request identity, target and replay inputs arriving at the application boundary. |

## ReservationCommands

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`record ReservationCommands`](../../reservation-service/src/main/java/study/locker/reservation/application/ReservationCommands.java#L9) | 2 | Use-case orchestration of creation and state transitions · Emission of lifecycle and pickup-mail events after specific transitions. |
| [`execute(Caller, Action, String, String)`](../../reservation-service/src/main/java/study/locker/reservation/application/ReservationCommands.java#L15) | 1 | Use-case orchestration of creation and state transitions. |
| [`reserve(Caller, String)`](../../reservation-service/src/main/java/study/locker/reservation/application/ReservationCommands.java#L19) | 2 | Use-case orchestration of creation and state transitions · Emission of lifecycle and pickup-mail events after specific transitions. Reserves a valid directory compartment and emits its initial lifecycle event. **Suggested change.** Return the transition’s event intents and publish them through the existing transactional outbox boundary. |
| [`transition(Caller, Action, String, String)`](../../reservation-service/src/main/java/study/locker/reservation/application/ReservationCommands.java#L28) | 2 | Use-case orchestration of creation and state transitions · Emission of lifecycle and pickup-mail events after specific transitions. Persists the locked transition and chooses which successful outcomes publish a lifecycle event. **Suggested change.** Return the transition’s event intents and publish them through the existing transactional outbox boundary. |
| [`issuePickup(Reservation, Action, java.time.Instant)`](../../reservation-service/src/main/java/study/locker/reservation/application/ReservationCommands.java#L50) | 2 | Use-case orchestration of creation and state transitions · Emission of lifecycle and pickup-mail events after specific transitions. Issues a fresh verifier and emits the associated mail event on deposit or reissue. **Suggested change.** Return the transition’s event intents and publish them through the existing transactional outbox boundary. |

**Refactor to consider.** Move the choice of events into domain outcomes and let the use case persist those outcomes.

## ReservationCommands.Action

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`enum Action`](../../reservation-service/src/main/java/study/locker/reservation/application/ReservationCommands.java#L11) | 1 | Commands understood by the reservation use case. |

## ReservationEvents

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`interface ReservationEvents`](../../reservation-service/src/main/java/study/locker/reservation/application/ReservationEvents.java#L7) | 2 | Lifecycle publication contract · Pickup-mail publication contract. |
| [`lifecycle(Reservation.View, String, Instant)`](../../reservation-service/src/main/java/study/locker/reservation/application/ReservationEvents.java#L8) | 1 | Lifecycle publication contract. |
| [`pickupMail(Reservation, PickupCodes.Issue)`](../../reservation-service/src/main/java/study/locker/reservation/application/ReservationEvents.java#L10) | 1 | Pickup-mail publication contract. |

**Refactor to consider.** Separate lifecycle and mail event ports if their consumers require independent evolution.

## ReservationQueries

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`record ReservationQueries`](../../reservation-service/src/main/java/study/locker/reservation/application/ReservationQueries.java#L7) | 3 | Filtering catalog candidates against active bookings · Reading a reservation under an optional ownership constraint · Public UUID parsing and invalid-input semantics. |
| [`available(String, String)`](../../reservation-service/src/main/java/study/locker/reservation/application/ReservationQueries.java#L8) | 1 | Filtering catalog candidates against active bookings. Removes occupied IDs from the directory’s candidate list. |
| [`get(Optional<String>, String)`](../../reservation-service/src/main/java/study/locker/reservation/application/ReservationQueries.java#L15) | 2 | Reading a reservation under an optional ownership constraint · Public UUID parsing and invalid-input semantics. Parses the public identifier and enforces the requested ownership constraint. **Suggested change.** Parse the external key at the port boundary before applying the owner-constrained read. |
| [`parseId(String)`](../../reservation-service/src/main/java/study/locker/reservation/application/ReservationQueries.java#L21) | 1 | Public UUID parsing and invalid-input semantics. |

**Refactor to consider.** Keep availability filtering independent of owner-scoped lookups and share one identifier parser at the input boundary.

## ReservationStore

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`interface ReservationStore`](../../reservation-service/src/main/java/study/locker/reservation/application/ReservationStore.java#L7) | 1 | Application persistence contract for booking insertion, locks, updates and occupied compartments. |
| [`insertIfAvailable(Reservation)`](../../reservation-service/src/main/java/study/locker/reservation/application/ReservationStore.java#L8) | 1 | Application persistence contract for booking insertion, locks, updates and occupied compartments. |
| [`find(UUID, boolean)`](../../reservation-service/src/main/java/study/locker/reservation/application/ReservationStore.java#L10) | 1 | Application persistence contract for booking insertion, locks, updates and occupied compartments. |
| [`update(Reservation)`](../../reservation-service/src/main/java/study/locker/reservation/application/ReservationStore.java#L12) | 1 | Application persistence contract for booking insertion, locks, updates and occupied compartments. |
| [`occupied(List<String>)`](../../reservation-service/src/main/java/study/locker/reservation/application/ReservationStore.java#L14) | 1 | Application persistence contract for booking insertion, locks, updates and occupied compartments. |

## CommandConfiguration

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class CommandConfiguration`](../../reservation-service/src/main/java/study/locker/reservation/bootstrap/CommandConfiguration.java#L10) | 1 | Ordering journal replay, trace propagation, transactions and observation around a business command. |
| [`commands(ReservationCommands, CommandJournal, JdbcClient, TransactionTemplate)`](../../reservation-service/src/main/java/study/locker/reservation/bootstrap/CommandConfiguration.java#L12) | 1 | Ordering journal replay, trace propagation, transactions and observation around a business command. |

## DirectoryClientConfiguration

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class DirectoryClientConfiguration`](../../reservation-service/src/main/java/study/locker/reservation/bootstrap/DirectoryClientConfiguration.java#L15) | 1 | Assembly of the guarded gRPC client used to reach Directory. |
| [`directoryChannel(String)`](../../reservation-service/src/main/java/study/locker/reservation/bootstrap/DirectoryClientConfiguration.java#L18) | 1 | Assembly of the guarded gRPC client used to reach Directory. |
| [`directory(ManagedChannel, Duration, RpcGuard.Settings, MeterRegistry)`](../../reservation-service/src/main/java/study/locker/reservation/bootstrap/DirectoryClientConfiguration.java#L23) | 1 | Assembly of the guarded gRPC client used to reach Directory. |

## OutboxConfiguration

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class OutboxConfiguration`](../../reservation-service/src/main/java/study/locker/reservation/bootstrap/OutboxConfiguration.java#L16) | 3 | Publisher, transaction and batch assembly · When and how often the outbox is polled · Backlog gauge definition. |
| [`outbox(PublishOutbox.Queue, KafkaTemplate<String, String>, TransactionTemplate, OutboxProperties)`](../../reservation-service/src/main/java/study/locker/reservation/bootstrap/OutboxConfiguration.java#L19) | 1 | Publisher, transaction and batch assembly. |
| [`relaySchedule(OutboxRelay)`](../../reservation-service/src/main/java/study/locker/reservation/bootstrap/OutboxConfiguration.java#L26) | 1 | When and how often the outbox is polled. |
| [`outboxBacklog(PublishOutbox.Queue)`](../../reservation-service/src/main/java/study/locker/reservation/bootstrap/OutboxConfiguration.java#L32) | 1 | Backlog gauge definition. |

**Refactor to consider.** Keep the scheduling trigger and backlog meter independent of publisher assembly.

## OutboxConfiguration.RelaySchedule

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`record RelaySchedule`](../../reservation-service/src/main/java/study/locker/reservation/bootstrap/OutboxConfiguration.java#L37) | 1 | Configured polling trigger for the outbox relay. |
| [`publish()`](../../reservation-service/src/main/java/study/locker/reservation/bootstrap/OutboxConfiguration.java#L38) | 1 | Configured polling trigger for the outbox relay. |

## OutboxProperties

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`record OutboxProperties`](../../reservation-service/src/main/java/study/locker/reservation/bootstrap/OutboxProperties.java#L12) | 1 | External configuration and valid bounds for batch size, acknowledgement timeout and polling. |

## PickupProperties

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`record PickupProperties`](../../reservation-service/src/main/java/study/locker/reservation/bootstrap/PickupProperties.java#L12) | 1 | External configuration and valid bounds for code lifetime and failed attempts. |

## ReservationConfiguration

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class ReservationConfiguration`](../../reservation-service/src/main/java/study/locker/reservation/bootstrap/ReservationConfiguration.java#L15) | 2 | Domain service and query assembly · Secret-file loading and the cryptographic pickup policy. |
| [`clock()`](../../reservation-service/src/main/java/study/locker/reservation/bootstrap/ReservationConfiguration.java#L18) | 1 | Domain service and query assembly. |
| [`secrets(String)`](../../reservation-service/src/main/java/study/locker/reservation/bootstrap/ReservationConfiguration.java#L23) | 1 | Secret-file loading and the cryptographic pickup policy. |
| [`queries(ReservationStore, Directory)`](../../reservation-service/src/main/java/study/locker/reservation/bootstrap/ReservationConfiguration.java#L28) | 1 | Domain service and query assembly. |
| [`pickupCodes(PickupSecrets, PickupProperties)`](../../reservation-service/src/main/java/study/locker/reservation/bootstrap/ReservationConfiguration.java#L33) | 1 | Secret-file loading and the cryptographic pickup policy. |
| [`businessCommands(ReservationStore, Directory, PickupCodes, ReservationEvents, Clock)`](../../reservation-service/src/main/java/study/locker/reservation/bootstrap/ReservationConfiguration.java#L38) | 1 | Domain service and query assembly. |

**Refactor to consider.** Move secret loading and pickup-code construction into pickup configuration and keep domain service wiring together.

## ReservationServerConfiguration

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class ReservationServerConfiguration`](../../reservation-service/src/main/java/study/locker/reservation/bootstrap/ReservationServerConfiguration.java#L15) | 1 | Publishing the authenticated reservation gRPC endpoint and enabling method security. |
| [`reservationServer(ReservationAccess, PickupSecrets, JwtDecoder, int)`](../../reservation-service/src/main/java/study/locker/reservation/bootstrap/ReservationServerConfiguration.java#L19) | 1 | Publishing the authenticated reservation gRPC endpoint and enabling method security. |

## PickupCode

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`record PickupCode`](../../reservation-service/src/main/java/study/locker/reservation/domain/model/PickupCode.java#L5) | 1 | Lifetime and failed-attempt budget captured when one code is issued. |
| [`issue(String, Instant, Duration, int)`](../../reservation-service/src/main/java/study/locker/reservation/domain/model/PickupCode.java#L6) | 1 | Lifetime and failed-attempt budget captured when one code is issued. |
| [`usableAt(Instant)`](../../reservation-service/src/main/java/study/locker/reservation/domain/model/PickupCode.java#L10) | 1 | Lifetime and failed-attempt budget captured when one code is issued. |
| [`failedAttempt()`](../../reservation-service/src/main/java/study/locker/reservation/domain/model/PickupCode.java#L14) | 1 | Lifetime and failed-attempt budget captured when one code is issued. |

## Reservation

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class Reservation`](../../reservation-service/src/main/java/study/locker/reservation/domain/model/Reservation.java#L8) | 3 | Allowed reservation state transitions · Owner visibility and access · Handling of pickup verification, expiry and attempts during collection. |
| [`Reservation(UUID, String, String, String, State, PickupCode)`](../../reservation-service/src/main/java/study/locker/reservation/domain/model/Reservation.java#L23) | 1 | Allowed reservation state transitions. |
| [`reserve(String, String, String)`](../../reservation-service/src/main/java/study/locker/reservation/domain/model/Reservation.java#L38) | 1 | Allowed reservation state transitions. |
| [`view()`](../../reservation-service/src/main/java/study/locker/reservation/domain/model/Reservation.java#L42) | 1 | Allowed reservation state transitions. |
| [`owner()`](../../reservation-service/src/main/java/study/locker/reservation/domain/model/Reservation.java#L46) | 1 | Owner visibility and access. |
| [`email()`](../../reservation-service/src/main/java/study/locker/reservation/domain/model/Reservation.java#L50) | 1 | Allowed reservation state transitions. |
| [`pickup()`](../../reservation-service/src/main/java/study/locker/reservation/domain/model/Reservation.java#L54) | 1 | Handling of pickup verification, expiry and attempts during collection. |
| [`ensureOwner(String)`](../../reservation-service/src/main/java/study/locker/reservation/domain/model/Reservation.java#L58) | 1 | Owner visibility and access. Conceals another owner’s reservation behind the missing-reservation failure. |
| [`cancel()`](../../reservation-service/src/main/java/study/locker/reservation/domain/model/Reservation.java#L62) | 1 | Allowed reservation state transitions. |
| [`deposit(PickupCode)`](../../reservation-service/src/main/java/study/locker/reservation/domain/model/Reservation.java#L67) | 2 | Allowed reservation state transitions · Handling of pickup verification, expiry and attempts during collection. **Suggested change.** Delegate pickup credential replacement and verification to the pickup model while retaining booking transitions in Reservation. |
| [`reissue(PickupCode)`](../../reservation-service/src/main/java/study/locker/reservation/domain/model/Reservation.java#L73) | 2 | Allowed reservation state transitions · Handling of pickup verification, expiry and attempts during collection. **Suggested change.** Delegate pickup credential replacement and verification to the pickup model while retaining booking transitions in Reservation. |
| [`collect(Instant, Predicate<String>)`](../../reservation-service/src/main/java/study/locker/reservation/domain/model/Reservation.java#L78) | 2 | Allowed reservation state transitions · Handling of pickup verification, expiry and attempts during collection. Requires a deposited booking, consumes a failed attempt or clears the credential after a successful collection. **Suggested change.** Delegate pickup credential replacement and verification to the pickup model while retaining booking transitions in Reservation. |
| [`require(State)`](../../reservation-service/src/main/java/study/locker/reservation/domain/model/Reservation.java#L90) | 1 | Allowed reservation state transitions. |

**Refactor to consider.** Encapsulate pickup verification and expose an outcome to the reservation while retaining its state-transition invariants.

## Reservation.State

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`enum State`](../../reservation-service/src/main/java/study/locker/reservation/domain/model/Reservation.java#L9) | 1 | Permitted lifecycle states for a reservation. |

## Reservation.View

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`record View`](../../reservation-service/src/main/java/study/locker/reservation/domain/model/Reservation.java#L16) | 1 | Public projection of a reservation identity, compartment and state. |

## ReservationFailure

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class ReservationFailure`](../../reservation-service/src/main/java/study/locker/reservation/domain/model/ReservationFailure.java#L3) | 1 | Representation of reservation rejections and their reason. |
| [`ReservationFailure(Reason, String)`](../../reservation-service/src/main/java/study/locker/reservation/domain/model/ReservationFailure.java#L15) | 1 | Representation of reservation rejections and their reason. |
| [`reason()`](../../reservation-service/src/main/java/study/locker/reservation/domain/model/ReservationFailure.java#L20) | 1 | Representation of reservation rejections and their reason. |
| [`missing()`](../../reservation-service/src/main/java/study/locker/reservation/domain/model/ReservationFailure.java#L24) | 1 | Representation of reservation rejections and their reason. |

## ReservationFailure.Reason

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`enum Reason`](../../reservation-service/src/main/java/study/locker/reservation/domain/model/ReservationFailure.java#L4) | 1 | Categories of reservation business failures. |

## CommandRowsIT

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class CommandRowsIT`](../../reservation-service/src/test/java/study/locker/reservation/adapter/persistence/CommandRowsIT.java#L22) | 1 | Assigned-key persist/merge lifecycle and round-trip lookup in PostgreSQL. |
| [`database(DynamicPropertyRegistry)`](../../reservation-service/src/test/java/study/locker/reservation/adapter/persistence/CommandRowsIT.java#L37) | 1 | Assigned-key persist/merge lifecycle and round-trip lookup in PostgreSQL. |
| [`assignedKeyEntryIsNewUntilInsertedAndIsExistingWhenReloaded()`](../../reservation-service/src/test/java/study/locker/reservation/adapter/persistence/CommandRowsIT.java#L49) | 1 | Assigned-key persist/merge lifecycle and round-trip lookup in PostgreSQL. |

## CommandRowsIT.PersistenceConfig

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class PersistenceConfig`](../../reservation-service/src/test/java/study/locker/reservation/adapter/persistence/CommandRowsIT.java#L27) | 1 | JPA test configuration for the command journal entity and repository. |

## PickupPropertiesTest

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class PickupPropertiesTest`](../../reservation-service/src/test/java/study/locker/reservation/bootstrap/PickupPropertiesTest.java#L10) | 1 | Startup rejection of invalid pickup lifetime and attempt settings. |
| [`refusesInvalidPolicyOnStartup(String, int, String)`](../../reservation-service/src/test/java/study/locker/reservation/bootstrap/PickupPropertiesTest.java#L14) | 1 | Startup rejection of invalid pickup lifetime and attempt settings. |

## PickupPropertiesTest.Binding

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class Binding`](../../reservation-service/src/test/java/study/locker/reservation/bootstrap/PickupPropertiesTest.java#L11) | 1 | Configuration binding fixture for validating pickup properties. |

## ReservationTest

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class ReservationTest`](../../reservation-service/src/test/java/study/locker/reservation/domain/model/ReservationTest.java#L10) | 2 | Owner and lifecycle restrictions on cancel and deposit · Expiry, lockout and reissue semantics for collection. |
| [`onlyTheOwnerCanCancelAndOnlyBeforeDeposit()`](../../reservation-service/src/test/java/study/locker/reservation/domain/model/ReservationTest.java#L15) | 1 | Owner and lifecycle restrictions on cancel and deposit. |
| [`reissuingAfterFiveFailuresAllowsOneCollectionWithTheNewCode()`](../../reservation-service/src/test/java/study/locker/reservation/domain/model/ReservationTest.java#L30) | 1 | Expiry, lockout and reissue semantics for collection. |
| [`reissueSnapshotsANewLifetimeAndFailureLimit()`](../../reservation-service/src/test/java/study/locker/reservation/domain/model/ReservationTest.java#L46) | 1 | Expiry, lockout and reissue semantics for collection. |
| [`codeExpiresAtTheFifteenMinuteBoundary()`](../../reservation-service/src/test/java/study/locker/reservation/domain/model/ReservationTest.java#L59) | 1 | Expiry, lockout and reissue semantics for collection. |

**Refactor to consider.** Use separate state-transition and pickup-policy test groups with a common reservation fixture.

## LostCommandReplyIT

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class LostCommandReplyIT`](../../reservation-service/src/test/java/study/locker/reservation/integration/LostCommandReplyIT.java#L19) | 1 | Idempotent recovery of committed mutations and failed pickup attempts after the response is lost. |
| [`lostCommandResponsesReplayTheCommittedReservationAndRejectedPickup()`](../../reservation-service/src/test/java/study/locker/reservation/integration/LostCommandReplyIT.java#L48) | 1 | Idempotent recovery of committed mutations and failed pickup attempts after the response is lost. Checks that retry preserves one reservation, one failed attempt and the journaled outcomes. |

## LostCommandReplyIT.DropFirstReplyPerMethod

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class DropFirstReplyPerMethod`](../../reservation-service/src/test/java/study/locker/reservation/integration/LostCommandReplyIT.java#L23) | 1 | Injecting one lost server reply per gRPC method after the call executes. |
| [`interceptCall(ServerCall<Q, R>, Metadata, ServerCallHandler<Q, R>)`](../../reservation-service/src/test/java/study/locker/reservation/integration/LostCommandReplyIT.java#L26) | 1 | Injecting one lost server reply per gRPC method after the call executes. |

## LostCommandReplyIT.DropFirstReplyPerMethod.anonymous@33

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class anonymous@33`](../../reservation-service/src/test/java/study/locker/reservation/integration/LostCommandReplyIT.java#L33) | 1 | Discarding a completed attempt's response and returning an unavailable status. |
| [`sendMessage(R)`](../../reservation-service/src/test/java/study/locker/reservation/integration/LostCommandReplyIT.java#L34) | 1 | Discarding a completed attempt's response and returning an unavailable status. |
| [`close(Status, Metadata)`](../../reservation-service/src/test/java/study/locker/reservation/integration/LostCommandReplyIT.java#L39) | 1 | Discarding a completed attempt's response and returning an unavailable status. |

## ManualAuditIT

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class ManualAuditIT`](../../reservation-service/src/test/java/study/locker/reservation/integration/ManualAuditIT.java#L14) | 3 | Repeatable installation of the manual audit schema · Transactional visibility and data retention · Reader privileges and redacted columns. |
| [`existingDatabaseCanInstallTwiceAndOnlyCommittedSafeRowsBecomeVisible()`](../../reservation-service/src/test/java/study/locker/reservation/integration/ManualAuditIT.java#L22) | 3 | Repeatable installation of the manual audit schema · Transactional visibility and data retention · Reader privileges and redacted columns. Exercises repeat installation, rollback, retention and the reader’s redacted view. **Suggested change.** Split repeat installation, retention, rollback visibility and privacy into focused SQL integration cases. |
| [`copy(Path, String, String)`](../../reservation-service/src/test/java/study/locker/reservation/integration/ManualAuditIT.java#L100) | 1 | Repeatable installation of the manual audit schema. |
| [`psql(String[])`](../../reservation-service/src/test/java/study/locker/reservation/integration/ManualAuditIT.java#L105) | 1 | Repeatable installation of the manual audit schema. |

**Refactor to consider.** Give installation, visibility/retention and reader privacy distinct cases against the same PostgreSQL fixture.

## OutboxRelayIT

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class OutboxRelayIT`](../../reservation-service/src/test/java/study/locker/reservation/integration/OutboxRelayIT.java#L15) | 2 | Exclusive batch claiming and ordered dispatch · Recovery of unacknowledged broker publications after rollback or a lost acknowledgement. |
| [`onlyOneRelayCanClaimTheOutboxUntilItsTransactionEnds()`](../../reservation-service/src/test/java/study/locker/reservation/integration/OutboxRelayIT.java#L22) | 1 | Exclusive batch claiming and ordered dispatch. |
| [`relayPublishesTheOldestConfiguredNumberOfRowsAndLeavesTheRestForTheNextBatch()`](../../reservation-service/src/test/java/study/locker/reservation/integration/OutboxRelayIT.java#L35) | 1 | Exclusive batch claiming and ordered dispatch. |
| [`outboxSurvivesBrokerFailureAndReplaysIdenticalEventsAfterLostAcknowledgement()`](../../reservation-service/src/test/java/study/locker/reservation/integration/OutboxRelayIT.java#L58) | 1 | Recovery of unacknowledged broker publications after rollback or a lost acknowledgement. |
| [`relay(String, boolean)`](../../reservation-service/src/test/java/study/locker/reservation/integration/OutboxRelayIT.java#L89) | 1 | Recovery of unacknowledged broker publications after rollback or a lost acknowledgement. |

**Refactor to consider.** Keep ordering and locking tests on PostgreSQL and isolate the real broker failure scenario.

## ReservationAuthorizationIT

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class ReservationAuthorizationIT`](../../reservation-service/src/test/java/study/locker/reservation/integration/ReservationAuthorizationIT.java#L20) | 3 | Scope and owner visibility rules · Ordering of authorization, input validation and replay · Restoring thread-local identity after an authorized or rejected RPC. |
| [`validatesCommandTargetsAfterAuthorizationAndUuidParsing()`](../../reservation-service/src/test/java/study/locker/reservation/integration/ReservationAuthorizationIT.java#L24) | 2 | Scope and owner visibility rules · Ordering of authorization, input validation and replay. **Suggested change.** Express the ordering guarantee as named cases with an expected status for each caller/input combination. |
| [`customersCannotReadOrCollectAnotherCustomersReservation()`](../../reservation-service/src/test/java/study/locker/reservation/integration/ReservationAuthorizationIT.java#L41) | 1 | Scope and owner visibility rules. |
| [`authorizeEveryRetryAndCheckVerifiedEmailOnlyForFreshReservations()`](../../reservation-service/src/test/java/study/locker/reservation/integration/ReservationAuthorizationIT.java#L50) | 2 | Scope and owner visibility rules · Ordering of authorization, input validation and replay. **Suggested change.** Express the ordering guarantee as named cases with an expected status for each caller/input combination. |
| [`twoCallersOnOneGrpcThreadRestoreThePreviousIdentityEvenAfterDenial()`](../../reservation-service/src/test/java/study/locker/reservation/integration/ReservationAuthorizationIT.java#L67) | 1 | Restoring thread-local identity after an authorized or rejected RPC. |

**Refactor to consider.** Test identity restoration separately from scope and input-ordering cases.

## ReservationBookIT

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class ReservationBookIT`](../../reservation-service/src/test/java/study/locker/reservation/integration/ReservationBookIT.java#L17) | 2 | Stable command results under replay · Exclusive insertion and row locking during concurrent operations. |
| [`successfulReplayReturnsOriginalResultAndDifferentInputConflicts()`](../../reservation-service/src/test/java/study/locker/reservation/integration/ReservationBookIT.java#L24) | 1 | Stable command results under replay. |
| [`concurrentReservationsHaveExactlyOneWinner()`](../../reservation-service/src/test/java/study/locker/reservation/integration/ReservationBookIT.java#L36) | 1 | Exclusive insertion and row locking during concurrent operations. |
| [`concurrentRetriesCommitOnlyOneBookingAndOneOutboxEvent()`](../../reservation-service/src/test/java/study/locker/reservation/integration/ReservationBookIT.java#L60) | 2 | Stable command results under replay · Exclusive insertion and row locking during concurrent operations. **Suggested change.** Keep the concurrent replay regression and factor its database uniqueness assertions into a focused helper. |
| [`lockingAReservationAllowsReadsButSerializesUpdates()`](../../reservation-service/src/test/java/study/locker/reservation/integration/ReservationBookIT.java#L78) | 1 | Exclusive insertion and row locking during concurrent operations. |

**Refactor to consider.** Keep replay semantics and persistence contention independently exercisable with the same booking fixture.

## ReservationCommitIT

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class ReservationCommitIT`](../../reservation-service/src/test/java/study/locker/reservation/integration/ReservationCommitIT.java#L11) | 1 | Atomic rollback and suppression of success telemetry until the command actually commits. |
| [`commitFailureRollsBackJournalBookingAndOutboxBeforeSuccessIsLogged()`](../../reservation-service/src/test/java/study/locker/reservation/integration/ReservationCommitIT.java#L13) | 1 | Atomic rollback and suppression of success telemetry until the command actually commits. Forces commit-time failure and checks database rows and emitted success events. |

## ReservationFixture

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class ReservationFixture`](../../reservation-service/src/test/java/study/locker/reservation/integration/ReservationFixture.java#L20) | 3 | Lifecycle of the authenticated gRPC fixture · Creation of scenario bookings and pickup codes · Database reset and assertions on persisted side effects. |
| [`reset()`](../../reservation-service/src/test/java/study/locker/reservation/integration/ReservationFixture.java#L30) | 2 | Lifecycle of the authenticated gRPC fixture · Database reset and assertions on persisted side effects. Resets persistence and time and opens a fresh client channel for the test. **Suggested change.** Separate database/time reset from opening the per-test channel. |
| [`close()`](../../reservation-service/src/test/java/study/locker/reservation/integration/ReservationFixture.java#L37) | 1 | Lifecycle of the authenticated gRPC fixture. |
| [`client(Jwt)`](../../reservation-service/src/test/java/study/locker/reservation/integration/ReservationFixture.java#L42) | 1 | Lifecycle of the authenticated gRPC fixture. |
| [`client(ManagedChannel, String)`](../../reservation-service/src/test/java/study/locker/reservation/integration/ReservationFixture.java#L46) | 1 | Lifecycle of the authenticated gRPC fixture. |
| [`execute(Jwt, Action, Command)`](../../reservation-service/src/test/java/study/locker/reservation/integration/ReservationFixture.java#L53) | 1 | Lifecycle of the authenticated gRPC fixture. |
| [`cmd(String)`](../../reservation-service/src/test/java/study/locker/reservation/integration/ReservationFixture.java#L64) | 1 | Creation of scenario bookings and pickup codes. |
| [`reserve()`](../../reservation-service/src/test/java/study/locker/reservation/integration/ReservationFixture.java#L68) | 1 | Creation of scenario bookings and pickup codes. |
| [`deposit(String)`](../../reservation-service/src/test/java/study/locker/reservation/integration/ReservationFixture.java#L72) | 1 | Creation of scenario bookings and pickup codes. |
| [`lastCode()`](../../reservation-service/src/test/java/study/locker/reservation/integration/ReservationFixture.java#L77) | 1 | Creation of scenario bookings and pickup codes. |
| [`assertRowCounts(int, int, int)`](../../reservation-service/src/test/java/study/locker/reservation/integration/ReservationFixture.java#L84) | 1 | Database reset and assertions on persisted side effects. |

**Refactor to consider.** Separate transport setup, scenario builders and database assertions into test fixtures.

## ReservationPickupIT

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class ReservationPickupIT`](../../reservation-service/src/test/java/study/locker/reservation/integration/ReservationPickupIT.java#L14) | 2 | Code expiry, reissue and attempt accounting · Compatibility with previously persisted bookings and replayed results. |
| [`expiredCodeCanBeReissuedAndCollectedExactlyOnce()`](../../reservation-service/src/test/java/study/locker/reservation/integration/ReservationPickupIT.java#L18) | 1 | Code expiry, reissue and attempt accounting. |
| [`repeatedBadRequestCountsOnceAndConfiguredAttemptsLockTheCode()`](../../reservation-service/src/test/java/study/locker/reservation/integration/ReservationPickupIT.java#L40) | 1 | Code expiry, reissue and attempt accounting. |
| [`canReplayAndCollectAPreJpaReservation()`](../../reservation-service/src/test/java/study/locker/reservation/integration/ReservationPickupIT.java#L55) | 2 | Code expiry, reissue and attempt accounting · Compatibility with previously persisted bookings and replayed results. **Suggested change.** Have a legacy fixture verify stored-result compatibility and reuse the existing pickup behavior assertions. |

**Refactor to consider.** Keep the legacy database fixture case separate from current pickup policy examples.

## ReservationQueryIT

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class ReservationQueryIT`](../../reservation-service/src/test/java/study/locker/reservation/integration/ReservationQueryIT.java#L13) | 1 | PostgreSQL execution plan and buffer-use evidence for the active-compartment lookup. |
| [`scopedAvailabilityUsesFewerBuffersWithTheActiveCompartmentIndex()`](../../reservation-service/src/test/java/study/locker/reservation/integration/ReservationQueryIT.java#L29) | 1 | PostgreSQL execution plan and buffer-use evidence for the active-compartment lookup. |
| [`plan(String, List<String>)`](../../reservation-service/src/test/java/study/locker/reservation/integration/ReservationQueryIT.java#L57) | 1 | PostgreSQL execution plan and buffer-use evidence for the active-compartment lookup. |
| [`buffers(tools.jackson.databind.JsonNode)`](../../reservation-service/src/test/java/study/locker/reservation/integration/ReservationQueryIT.java#L63) | 1 | PostgreSQL execution plan and buffer-use evidence for the active-compartment lookup. |

## ReservationQueryIT.SqlCapture

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class SqlCapture`](../../reservation-service/src/test/java/study/locker/reservation/integration/ReservationQueryIT.java#L19) | 1 | Capture of the actual SQL generated inside a scoped test invocation. |
| [`inspect(String)`](../../reservation-service/src/test/java/study/locker/reservation/integration/ReservationQueryIT.java#L22) | 1 | Capture of the actual SQL generated inside a scoped test invocation. |

## ReservationTestBeans

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class ReservationTestBeans`](../../reservation-service/src/test/java/study/locker/reservation/integration/ReservationTestBeans.java#L16) | 4 | Deterministic pickup secret for encrypted test messages · Test identity claims and scopes · Stubbed compartment catalog · Controllable time for expiry scenarios. |
| [`testSecrets()`](../../reservation-service/src/test/java/study/locker/reservation/integration/ReservationTestBeans.java#L35) | 1 | Deterministic pickup secret for encrypted test messages. |
| [`testTime()`](../../reservation-service/src/test/java/study/locker/reservation/integration/ReservationTestBeans.java#L39) | 1 | Controllable time for expiry scenarios. |
| [`testIdentity()`](../../reservation-service/src/test/java/study/locker/reservation/integration/ReservationTestBeans.java#L55) | 1 | Test identity claims and scopes. |
| [`testDirectory()`](../../reservation-service/src/test/java/study/locker/reservation/integration/ReservationTestBeans.java#L66) | 1 | Stubbed compartment catalog. |
| [`actor(String, String)`](../../reservation-service/src/test/java/study/locker/reservation/integration/ReservationTestBeans.java#L77) | 1 | Test identity claims and scopes. |

**Refactor to consider.** Give time, secret material, identity and catalog behavior separate fixtures so scenario setup is explicit.

## ReservationTestBeans.anonymous@40

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class anonymous@40`](../../reservation-service/src/test/java/study/locker/reservation/integration/ReservationTestBeans.java#L40) | 1 | Advancing the fixture instant while preserving Clock zone behavior. |
| [`getZone()`](../../reservation-service/src/test/java/study/locker/reservation/integration/ReservationTestBeans.java#L41) | 1 | Advancing the fixture instant while preserving Clock zone behavior. |
| [`withZone(ZoneId)`](../../reservation-service/src/test/java/study/locker/reservation/integration/ReservationTestBeans.java#L45) | 1 | Advancing the fixture instant while preserving Clock zone behavior. |
| [`instant()`](../../reservation-service/src/test/java/study/locker/reservation/integration/ReservationTestBeans.java#L49) | 1 | Advancing the fixture instant while preserving Clock zone behavior. |

## ReservationTestBeans.anonymous@67

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class anonymous@67`](../../reservation-service/src/test/java/study/locker/reservation/integration/ReservationTestBeans.java#L67) | 1 | Synthetic compartment listing and existence behavior for reservation tests. |
| [`search(String, String)`](../../reservation-service/src/test/java/study/locker/reservation/integration/ReservationTestBeans.java#L68) | 1 | Synthetic compartment listing and existence behavior for reservation tests. |
| [`requireCompartment(String)`](../../reservation-service/src/test/java/study/locker/reservation/integration/ReservationTestBeans.java#L72) | 1 | Synthetic compartment listing and existence behavior for reservation tests. |

## ReservationTestDatabase

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class ReservationTestDatabase`](../../reservation-service/src/test/java/study/locker/reservation/integration/ReservationTestDatabase.java#L10) | 2 | Spring-owned PostgreSQL container lifecycle and datasource property registration · Independent transaction propagation for contention tests. |
| [`postgres()`](../../reservation-service/src/test/java/study/locker/reservation/integration/ReservationTestDatabase.java#L12) | 1 | Spring-owned PostgreSQL container lifecycle and datasource property registration. |
| [`postgresProperties(PostgreSQLContainer)`](../../reservation-service/src/test/java/study/locker/reservation/integration/ReservationTestDatabase.java#L17) | 1 | Spring-owned PostgreSQL container lifecycle and datasource property registration. |
| [`independentTransaction(TransactionTemplate)`](../../reservation-service/src/test/java/study/locker/reservation/integration/ReservationTestDatabase.java#L26) | 1 | Independent transaction propagation for contention tests. |

**Refactor to consider.** Move the independent-transaction factory alongside the contention-test fixtures.
