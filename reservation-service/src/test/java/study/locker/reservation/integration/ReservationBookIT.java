package study.locker.reservation.integration;

import static org.assertj.core.api.Assertions.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import io.grpc.*;
import io.grpc.stub.MetadataUtils;

import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

import study.locker.common.*;
import study.locker.reservation.ReservationApp;
import study.locker.reservation.adapter.grpcserver.ReservationEndpoint;
import study.locker.reservation.adapter.kafka.KafkaPublisher;
import study.locker.reservation.adapter.transaction.TransactionalOutbox;
import study.locker.reservation.application.*;
import study.locker.reservation.application.ReservationCommands.Action;
import study.locker.security.RpcIdentity;
import study.locker.wire.*;

import tools.jackson.databind.ObjectMapper;

import java.sql.SQLException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

@Testcontainers
@SpringBootTest(
        classes = ReservationApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "locker.grpc.port=0",
            "locker.pickup.ttl=45s",
            "locker.pickup.max-attempts=3",
            "locker.outbox.scheduling=false",
            "spring.jpa.properties.hibernate.session_factory.statement_inspector=study.locker.reservation.integration.ReservationBookIT$SqlCapture"
        })
class ReservationBookIT {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:19.0-alpine");

    @Autowired JdbcClient db;
    @Autowired TransactionTemplate tx;
    @Autowired PublishOutbox.Queue queue;
    @Autowired ReservationAccess access;
    @Autowired ReservationStore store;
    @Autowired Server server;
    ManagedChannel channel;
    final ObjectMapper json = new ObjectMapper();
    final Jwt customer = actor("alice", "locker.read locker.reserve");
    final Jwt courier = actor("courier", "locker.read locker.deposit");
    static final AtomicReference<Instant> now = new AtomicReference<>();

    @TestBean(methodName = "testSecrets")
    PickupSecrets secrets;

    @TestBean(methodName = "testTime")
    Clock clock;

    @TestBean(methodName = "testIdentity")
    JwtDecoder lockerDecoder;

    @TestBean(methodName = "testDirectory")
    Directory directory;

    static PickupSecrets testSecrets() {
        return new PickupSecrets(Base64.getEncoder().encodeToString(new byte[32]));
    }

    static Clock testTime() {
        return new Clock() {
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            public Clock withZone(ZoneId zone) {
                return Clock.fixed(instant(), zone);
            }

            public Instant instant() {
                return now.get();
            }
        };
    }

    static JwtDecoder testIdentity() {
        return token ->
                switch (token) {
                    case "courier" -> actor(token, "locker.read locker.deposit");
                    case "courier-no-read" -> actor(token, "locker.deposit");
                    case "alice-unverified" ->
                            Jwt.withTokenValue("fixture")
                                    .header("alg", "RS256")
                                    .subject("alice")
                                    .claim("scope", "locker.reserve")
                                    .claim("email_verified", false)
                                    .build();
                    case "alice-no-scope" -> actor("alice", "locker.read");
                    default -> actor(token, "locker.read locker.reserve");
                };
    }

    static Directory testDirectory() {
        return new Directory() {
            public List<Slot> search(String site, String size) {
                return List.of(new Slot("CENTRAL-S1", "Central", "SMALL"));
            }

            public void requireCompartment(String id) {}
        };
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", postgres::getJdbcUrl);
        p.add("spring.datasource.username", postgres::getUsername);
        p.add("spring.datasource.password", postgres::getPassword);
    }

    @BeforeEach
    void reset() {
        db.sql("truncate command_result,booking,outbox").update();
        now.set(Instant.parse("2026-01-01T00:00:00Z"));
        channel = Rpc.channel("127.0.0.1:" + server.getPort());
    }

    @AfterEach
    void close() {
        channel.shutdownNow();
    }

    ReservationsGrpc.ReservationsBlockingStub client(Jwt actor) {
        return client(channel, actor.getSubject());
    }

    private ReservationsGrpc.ReservationsBlockingStub client(ManagedChannel channel, String token) {
        var meta = new Metadata();
        meta.put(RpcIdentity.AUTH, "Bearer " + token);
        return ReservationsGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(meta));
    }

    Booking execute(Jwt actor, Action action, Command cmd) {
        var client = client(actor);
        return switch (action) {
            case RESERVE -> client.reserve(cmd);
            case CANCEL -> client.cancel(cmd);
            case DEPOSIT -> client.deposit(cmd);
            case REISSUE -> client.reissue(cmd);
            case COLLECT -> client.collect(cmd);
        };
    }

    @Test
    void validatesCommandTargetsAfterAuthorizationAndUuidParsing() {
        var malformed = cmd("").toBuilder().setRequestId("invalid").build();
        assertThatThrownBy(() -> client(courier).reserve(malformed))
                .hasMessageContaining("PERMISSION_DENIED: Missing scope locker.reserve");
        assertThatThrownBy(() -> client(customer).reserve(malformed))
                .hasMessageContaining("INVALID_ARGUMENT: Expected UUID");

        for (String invalid : List.of("", " \t", "x".repeat(101))) {
            assertThatThrownBy(() -> client(customer).reserve(cmd(invalid)))
                    .hasMessageContaining("INVALID_ARGUMENT: A valid ID is required");
        }
        assertRowCounts(0, 0, 0);
        String longest = "x".repeat(100);
        assertThat(client(customer).reserve(cmd(longest)).getCompartmentId()).isEqualTo(longest);
    }

    @Test
    void canReplayAndCollectAPreJpaReservation() {
        new ResourceDatabasePopulator(new ClassPathResource("legacy-reservation.sql"))
                .execute(dbSource);
        String id = "20260000-0000-4000-8000-000000000002";
        var replay =
                execute(
                        customer,
                        Action.RESERVE,
                        cmd("CENTRAL-S1").toBuilder()
                                .setRequestId("20260000-0000-4000-8000-000000000001")
                                .build());
        assertThat(replay.getState()).isEqualTo("RESERVED");
        assertThat(replay.getId()).isEqualTo(id);
        assertThat(db.sql("select pickup_max_attempts from booking").query(Integer.class).single())
                .isEqualTo(5);
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> execute(customer, Action.COLLECT, cmd(id)))
                    .hasMessageContaining("Invalid pickup code");
        }
        now.set(now.get().plusSeconds(899));
        assertThat(
                        execute(
                                        customer,
                                        Action.COLLECT,
                                        cmd(id).toBuilder().setCode("01234567").build())
                                .getState())
                .isEqualTo("COLLECTED");
    }

    @Autowired javax.sql.DataSource dbSource;

    static Jwt actor(String sub, String scopes) {
        return Jwt.withTokenValue("fixture")
                .header("alg", "RS256")
                .subject(sub)
                .claim("scope", scopes)
                .claim("email_verified", true)
                .claim("email", sub + "@example.test")
                .build();
    }

    static Command cmd(String id) {
        return Command.newBuilder().setId(id).setRequestId(UUID.randomUUID().toString()).build();
    }

    Booking reserve() {
        return execute(customer, Action.RESERVE, cmd("CENTRAL-S1"));
    }

    String deposit(String id) {
        execute(courier, Action.DEPOSIT, cmd(id));
        return lastCode();
    }

    String lastCode() {
        var mail =
                json.readTree(
                        db.sql(
                                        "select payload from outbox where"
                                                + " topic='locker.pickup-mail.v1' order by seq desc"
                                                + " limit 1")
                                .query(String.class)
                                .single());
        return secrets.open(mail.get("eventId").asString(), mail.get("envelope").asString());
    }

    @Test
    void concurrentReservationsHaveExactlyOneWinner() throws Exception {
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var start = new CountDownLatch(1);
            var futures = new ArrayList<Future<Status.Code>>();
            for (int i = 0; i < 8; i++)
                futures.add(
                        threads.submit(
                                () -> {
                                    start.await();
                                    try {
                                        reserve();
                                        return Status.Code.OK;
                                    } catch (StatusRuntimeException ex) {
                                        return ex.getStatus().getCode();
                                    }
                                }));
            start.countDown();
            var results = new ArrayList<Status.Code>();
            for (var future : futures) results.add(future.get(10, TimeUnit.SECONDS));
            assertThat(results).containsOnly(Status.Code.OK, Status.Code.ALREADY_EXISTS);
            assertThat(Collections.frequency(results, Status.Code.OK)).isOne();
            assertThat(db.sql("select count(*) from outbox").query(Integer.class).single()).isOne();
        }
    }

    @Test
    void successfulReplayReturnsOriginalResultAndDifferentInputConflicts() {
        Command command = cmd("CENTRAL-S1");
        Booking before = execute(customer, Action.RESERVE, command);
        execute(customer, Action.CANCEL, cmd(before.getId()));
        assertThat(execute(customer, Action.RESERVE, command)).isEqualTo(before);
        assertThatThrownBy(
                        () ->
                                execute(
                                        customer,
                                        Action.RESERVE,
                                        command.toBuilder().setId("CENTRAL-L1").build()))
                .isInstanceOf(StatusRuntimeException.class)
                .hasMessageContaining("ALREADY_EXISTS");
        assertThat(reserve().getId()).isNotEqualTo(before.getId());
    }

    @Test
    void expiredCodeCanBeReissuedAndCollectedExactlyOnce() {
        String id = reserve().getId(), old = deposit(id);
        assertThat(db.sql("select pickup_max_attempts from booking").query(Integer.class).single())
                .isEqualTo(3);
        assertThat(
                        db.sql("select expires_at from booking")
                                .query(OffsetDateTime.class)
                                .single()
                                .toInstant())
                .isEqualTo(now.get().plusSeconds(45));
        now.set(now.get().plusSeconds(45));
        Command expired = cmd(id).toBuilder().setCode(old).build();
        assertThatThrownBy(() -> execute(customer, Action.COLLECT, expired))
                .hasMessageContaining("expired");
        execute(customer, Action.REISSUE, cmd(id));
        String fresh = lastCode();
        assertThatThrownBy(
                        () ->
                                execute(
                                        customer,
                                        Action.COLLECT,
                                        cmd(id).toBuilder().setCode(old).build()))
                .hasMessageContaining("Invalid pickup code");
        var collect = cmd(id).toBuilder().setCode(fresh).build();
        assertThat(execute(customer, Action.COLLECT, collect).getState()).isEqualTo("COLLECTED");
        assertThat(execute(customer, Action.COLLECT, collect).getState()).isEqualTo("COLLECTED");
        assertThatThrownBy(
                        () ->
                                execute(
                                        customer,
                                        Action.COLLECT,
                                        cmd(id).toBuilder().setCode(fresh).build()))
                .hasMessageContaining("FAILED_PRECONDITION");
        assertThat(reserve().getState()).isEqualTo("RESERVED");
    }

    @Test
    void repeatedBadRequestCountsOnceAndConfiguredAttemptsLockTheCode() {
        String id = reserve().getId(), code = deposit(id);
        Command bad = cmd(id).toBuilder().setCode("wrong").build();
        for (int i = 0; i < 6; i++)
            assertThatThrownBy(() -> execute(customer, Action.COLLECT, bad))
                    .hasMessageContaining("Invalid pickup code");
        assertThat(db.sql("select attempts from booking").query(Integer.class).single()).isOne();
        for (int i = 1; i < 3; i++)
            assertThatThrownBy(() -> execute(customer, Action.COLLECT, cmd(id)))
                    .hasMessageContaining("Invalid pickup code");
        assertThatThrownBy(
                        () ->
                                execute(
                                        customer,
                                        Action.COLLECT,
                                        cmd(id).toBuilder().setCode(code).build()))
                .hasMessageContaining("locked");
    }

    @Test
    void customersCannotReadOrCollectAnotherCustomersReservation() {
        String id = reserve().getId();
        var bob = actor("bob", "locker.read locker.reserve");
        assertThatThrownBy(() -> client(bob).get(Key.newBuilder().setId(id).build()))
                .hasMessageContaining("NOT_FOUND");
        assertThatThrownBy(() -> execute(bob, Action.CANCEL, cmd(id)))
                .hasMessageContaining("NOT_FOUND");
        assertThatThrownBy(() -> execute(customer, Action.DEPOSIT, cmd(id)))
                .hasMessageContaining("PERMISSION_DENIED");
    }

    @Test
    void concurrentRetriesCommitOnlyOneBookingAndOneOutboxEvent() throws Exception {
        Command request = cmd("CENTRAL-S1");
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var start = new CyclicBarrier(4);
            List<Future<Booking>> results = new ArrayList<>();
            for (int i = 0; i < 4; i++)
                results.add(
                        pool.submit(
                                () -> {
                                    start.await(5, TimeUnit.SECONDS);
                                    return client(customer).reserve(request);
                                }));
            Booking original = results.getFirst().get(10, TimeUnit.SECONDS);
            for (var result : results)
                assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo(original);
        }
        assertRowCounts(1, 1, 1);
    }

    @Test
    void authorizeEveryRetryAndCheckVerifiedEmailOnlyForFreshReservations() {
        var request = cmd("CENTRAL-S1");
        Booking original = client(customer).reserve(request);
        assertThat(client(channel, "alice-unverified").reserve(request)).isEqualTo(original);
        assertThatThrownBy(() -> client(channel, "alice-no-scope").reserve(request))
                .hasMessageContaining("PERMISSION_DENIED: Missing scope locker.reserve");
        assertThatThrownBy(() -> client(channel, "alice-unverified").reserve(cmd("CENTRAL-S2")))
                .hasMessageContaining("Verified customer email required");
        assertThatThrownBy(
                        () ->
                                client(channel, "courier-no-read")
                                        .get(Key.newBuilder().setId(original.getId()).build()))
                .hasMessageContaining("PERMISSION_DENIED: Missing scope locker.read");
        assertThatThrownBy(
                        () ->
                                ReservationsGrpc.newBlockingStub(channel)
                                        .available(Search.getDefaultInstance()))
                .hasMessageContaining("UNAUTHENTICATED");
        assertRowCounts(1, 1, 1);
    }

    @Test
    void twoCallersOnOneGrpcThreadRestoreThePreviousIdentityEvenAfterDenial() throws Exception {
        try (var worker = Executors.newSingleThreadExecutor()) {
            var previous = new TestingAuthenticationToken("outer", "unused");
            worker.submit(() -> SecurityContextHolder.getContext().setAuthentication(previous))
                    .get();
            var localServer =
                    ServerBuilder.forPort(0)
                            .executor(worker)
                            .addService(
                                    ServerInterceptors.intercept(
                                            new ReservationEndpoint(access, secrets),
                                            new RpcIdentity(lockerDecoder)))
                            .build()
                            .start();
            var localChannel = Rpc.channel("127.0.0.1:" + localServer.getPort());
            try {
                var alice = client(localChannel, "alice");
                var driver = client(localChannel, "courier");
                var reservation = alice.reserve(cmd("CENTRAL-S1"));
                var key = Key.newBuilder().setId(reservation.getId()).build();
                assertThat(driver.get(key)).isEqualTo(reservation);
                assertThat(
                                worker.submit(
                                                () ->
                                                        SecurityContextHolder.getContext()
                                                                .getAuthentication())
                                        .get())
                        .isSameAs(previous);
                assertThatThrownBy(() -> alice.deposit(cmd(reservation.getId())))
                        .hasMessageContaining("PERMISSION_DENIED: Missing scope locker.deposit");
                assertThat(
                                worker.submit(
                                                () ->
                                                        SecurityContextHolder.getContext()
                                                                .getAuthentication())
                                        .get())
                        .isSameAs(previous);
                assertRowCounts(1, 1, 1);
            } finally {
                localChannel.shutdownNow();
                localServer.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void commitFailureRollsBackJournalBookingAndOutboxBeforeSuccessIsLogged() {
        var request = cmd("CENTRAL-S1");
        Logger logger = (Logger) LoggerFactory.getLogger("locker.events");
        var capture = new ListAppender<ILoggingEvent>();
        capture.start();
        logger.addAppender(capture);
        try {
            // The random request ID differs from the booking ID. PostgreSQL checks this FK at
            // commit.
            db.sql(
                            "alter table command_result add constraint test_commit_failure foreign"
                                + " key (request_id) references booking(id) deferrable initially"
                                + " deferred")
                    .update();
            try {
                assertThatThrownBy(() -> client(customer).reserve(request))
                        .hasMessageContaining("INTERNAL");
                assertRowCounts(0, 0, 0);
                assertThat(capture.list)
                        .filteredOn(e -> e.getMessage().equals("reservation_command"))
                        .isEmpty();
            } finally {
                db.sql("alter table command_result drop constraint test_commit_failure").update();
            }
            assertThat(client(customer).reserve(request).getState()).isEqualTo("RESERVED");
            assertRowCounts(1, 1, 1);
            assertThat(capture.list)
                    .filteredOn(e -> e.getMessage().equals("reservation_command"))
                    .singleElement()
                    .satisfies(
                            event ->
                                    assertThat(event.getKeyValuePairs())
                                            .anyMatch(
                                                    pair ->
                                                            pair.key.equals("outcome")
                                                                    && pair.value.equals(
                                                                            "committed")));
        } finally {
            logger.detachAppender(capture);
            capture.stop();
        }
    }

    private void assertRowCounts(int bookings, int journal, int outbox) {
        assertThat(db.sql("select count(*) from booking").query(Integer.class).single())
                .isEqualTo(bookings);
        assertThat(db.sql("select count(*) from command_result").query(Integer.class).single())
                .isEqualTo(journal);
        assertThat(db.sql("select count(*) from outbox").query(Integer.class).single())
                .isEqualTo(outbox);
    }

    @Test
    void lockingAReservationAllowsReadsButSerializesUpdates() {
        var id = UUID.fromString(reserve().getId());
        var otherTransaction = independentTransaction();

        tx.executeWithoutResult(
                holder -> {
                    assertThat(store.find(id, true)).isPresent();
                    otherTransaction.executeWithoutResult(
                            reader -> {
                                db.sql("set local lock_timeout = '500ms'").update();
                                assertThat(store.find(id, false)).isPresent();
                            });
                    assertThatThrownBy(
                                    () ->
                                            otherTransaction.executeWithoutResult(
                                                    writer -> {
                                                        db.sql("set local lock_timeout = '500ms'")
                                                                .update();
                                                        store.find(id, true);
                                                    }))
                            .rootCause()
                            .isInstanceOfSatisfying(
                                    SQLException.class,
                                    failure ->
                                            assertThat(failure.getSQLState()).isEqualTo("55P03"));
                });

        tx.executeWithoutResult(nextWriter -> assertThat(store.find(id, true)).isPresent());
    }

    @Test
    void scopedAvailabilityUsesFewerBuffersWithTheActiveCompartmentIndex() {
        reserve();
        db.sql(
                        """
                        insert into booking(id,compartment,owner_sub,email,state)
                        select gen_random_uuid(),'historical-' || n,'fixture','a@example.test','COLLECTED'
                        from generate_series(1,40000) n
                        """)
                .update();
        db.sql("analyze booking").update();
        tx.executeWithoutResult(
                transaction -> {
                    var ids = List.of("CENTRAL-S1", "CENTRAL-S2");
                    var statements = new ArrayList<String>();
                    ScopedValue.where(SqlCapture.statements, statements)
                            .run(
                                    () ->
                                            assertThat(store.occupied(ids))
                                                    .containsExactly("CENTRAL-S1"));
                    assertThat(statements).hasSize(1);
                    var sql = statements.getFirst();
                    var indexed = plan(sql, ids);
                    db.sql("set local enable_indexscan=off").update();
                    db.sql("set local enable_indexonlyscan=off").update();
                    db.sql("set local enable_bitmapscan=off").update();
                    var scanned = plan(sql, ids);
                    assertThat(store.occupied(ids)).containsExactly("CENTRAL-S1");
                    assertThat(indexed.toString()).contains("one_active_booking");
                    assertThat(scanned.toString()).contains("Seq Scan");
                    assertThat(buffers(indexed)).isLessThan(buffers(scanned));
                });
    }

    private tools.jackson.databind.JsonNode plan(String sql, List<String> ids) {
        return json.readTree(
                        db.sql("explain (analyze, buffers, format json) " + sql)
                                .params(ids)
                                .query(String.class)
                                .single())
                .get(0)
                .get("Plan");
    }

    public static class SqlCapture implements StatementInspector {
        private static final ScopedValue<List<String>> statements = ScopedValue.newInstance();

        @Override
        public String inspect(String sql) {
            if (statements.isBound()) statements.get().add(sql);
            return sql;
        }
    }

    private static int buffers(tools.jackson.databind.JsonNode plan) {
        return plan.path("Shared Hit Blocks").asInt() + plan.path("Shared Read Blocks").asInt();
    }

    private TransactionTemplate independentTransaction() {
        var independent = new TransactionTemplate(tx.getTransactionManager());
        independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return independent;
    }

    @Test
    void onlyOneRelayCanClaimTheOutboxUntilItsTransactionEnds() {
        tx.executeWithoutResult(
                holder -> {
                    assertThat(queue.claimBatch()).isTrue();
                    independentTransaction()
                            .executeWithoutResult(
                                    contender -> {
                                        db.sql("set local lock_timeout = '500ms'").update();
                                        assertThat(queue.claimBatch()).isFalse();
                                    });
                    holder.setRollbackOnly();
                });
        tx.executeWithoutResult(successor -> assertThat(queue.claimBatch()).isTrue());
    }

    @Test
    void relayPublishesTheOldestConfiguredNumberOfRowsAndLeavesTheRestForTheNextBatch() {
        db.sql(
                        """
                        insert into outbox (seq, event_id, reservation_id, topic, payload) overriding system value
                        select n, gen_random_uuid(), gen_random_uuid(), 'locker.lifecycle.v1', '{}'
                        from generate_series(51, 1, -1) n
                        """)
                .update();
        var sequences = java.util.stream.LongStream.rangeClosed(1, 51).boxed().toList();
        var published = new ArrayList<Long>();
        var relay =
                new TransactionalOutbox(
                        tx,
                        new PublishOutbox(
                                queue,
                                27,
                                message -> {
                                    published.add(message.sequence());
                                    return PublishOutbox.Publication.ACKNOWLEDGED;
                                }));

        relay.publish();
        assertThat(published).containsExactlyElementsOf(sequences.subList(0, 27));
        assertThat(queue.count()).isEqualTo(24);

        relay.publish();
        assertThat(published).containsExactlyElementsOf(sequences);
        assertThat(queue.count()).isZero();
    }

    @Test
    void outboxSurvivesBrokerFailureAndReplaysIdenticalEventsAfterLostAcknowledgement()
            throws Exception {
        var booking = reserve();
        String topic = "locker.lifecycle.v1";
        String payload = db.sql("select payload from outbox").query(String.class).single();
        relay("127.0.0.1:1", false);
        assertThat(db.sql("select payload from outbox").query(String.class).single())
                .isEqualTo(payload);
        try (var broker = new org.testcontainers.kafka.KafkaContainer("apache/kafka:4.2.2")) {
            broker.start();
            relay(broker.getBootstrapServers(), true);
            assertThat(db.sql("select payload from outbox").query(String.class).single())
                    .isEqualTo(payload);
            relay(broker.getBootstrapServers(), false);
            assertThat(db.sql("select count(*) from outbox").query(Integer.class).single())
                    .isZero();
            try (var consumer =
                    new org.apache.kafka.clients.consumer.KafkaConsumer<String, String>(
                            Map.of(
                                    "bootstrap.servers",
                                    broker.getBootstrapServers(),
                                    "group.id",
                                    "outbox-proof",
                                    "auto.offset.reset",
                                    "earliest"),
                            new org.apache.kafka.common.serialization.StringDeserializer(),
                            new org.apache.kafka.common.serialization.StringDeserializer())) {
                consumer.subscribe(List.of(topic));
                List<String> seen = new ArrayList<>();
                org.awaitility.Awaitility.await()
                        .atMost(Duration.ofSeconds(20))
                        .untilAsserted(
                                () -> {
                                    consumer.poll(Duration.ofMillis(350))
                                            .forEach(
                                                    event -> {
                                                        assertThat(event.key())
                                                                .isEqualTo(booking.getId());
                                                        seen.add(event.value());
                                                    });
                                    assertThat(seen).containsExactly(payload, payload);
                                });
            }
        }
    }

    private void relay(String bootstrap, boolean simulateLostCommit) {
        var producer =
                new org.springframework.kafka.core.DefaultKafkaProducerFactory<String, String>(
                        Map.of(
                                "bootstrap.servers",
                                bootstrap,
                                "max.block.ms",
                                bootstrap.endsWith(":1") ? 300 : 10000),
                        new org.apache.kafka.common.serialization.StringSerializer(),
                        new org.apache.kafka.common.serialization.StringSerializer());
        try {
            var relay =
                    new TransactionalOutbox(
                            tx,
                            new PublishOutbox(
                                    queue,
                                    50,
                                    new KafkaPublisher(
                                            new org.springframework.kafka.core.KafkaTemplate<>(
                                                    producer),
                                            Duration.ofSeconds(12))));
            tx.executeWithoutResult(
                    txStatus -> {
                        relay.publish();
                        if (simulateLostCommit) txStatus.setRollbackOnly();
                    });
        } finally {
            producer.destroy();
        }
    }
}
