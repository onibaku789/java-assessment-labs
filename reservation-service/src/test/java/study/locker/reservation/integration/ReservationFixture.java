package study.locker.reservation.integration;

import static org.assertj.core.api.Assertions.*;

import io.grpc.*;
import io.grpc.stub.MetadataUtils;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.Jwt;
import study.locker.common.*;
import study.locker.wire.*;
import tools.jackson.databind.ObjectMapper;

import java.time.*;
import java.util.*;

abstract class ReservationFixture extends ReservationTestBeans {
    @Autowired
    JdbcClient db;
    @Autowired
    ReservationTestServer server;
    ManagedChannel channel;
    final ObjectMapper json = new ObjectMapper();
    final Jwt customer = ReservationTestBeans.actor("alice", "locker.read locker.reserve");
    final Jwt courier = ReservationTestBeans.actor("courier", "locker.read locker.deposit");

    @BeforeEach
    void reset() {
        db.sql("truncate command_result,booking,outbox").update();
        ReservationTestBeans.now.set(Instant.parse("2026-01-01T00:00:00Z"));
        channel = Rpc.channel("127.0.0.1:" + server.port());
    }

    @AfterEach
    void close() {
        channel.shutdownNow();
    }

    ReservationsGrpc.ReservationsBlockingStub client(Jwt actor) {
        return client(channel, actor.getSubject());
    }

    ReservationsGrpc.ReservationsBlockingStub client(ManagedChannel channel, String token) {
        var meta = new Metadata();
        meta.put(Rpc.AUTHORIZATION, "Bearer " + token);
        return ReservationsGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(meta));
    }

    static Command cmd(String id) {
        return Command.newBuilder().setId(id).setRequestId(UUID.randomUUID().toString()).build();
    }

    Booking reserve() {
        return client(customer).reserve(cmd("CENTRAL-S1"));
    }

    String deposit(String id) {
        client(courier).deposit(cmd(id));
        return lastCode();
    }

    String lastCode() {
        var mail = json.readTree(
                db.sql("select payload from outbox where" + " topic='locker.pickup-mail.v1' order by seq desc" +
                        " limit 1").query(String.class).single());
        return secrets.open(mail.get("eventId").asString(), mail.get("envelope").asString());
    }

    void assertRowCounts(int bookings, int journal, int outbox) {
        assertThat(db.sql("select count(*) from booking").query(Integer.class).single()).isEqualTo(bookings);
        assertThat(db.sql("select count(*) from command_result").query(Integer.class).single()).isEqualTo(journal);
        assertThat(db.sql("select count(*) from outbox").query(Integer.class).single()).isEqualTo(outbox);
    }
}
