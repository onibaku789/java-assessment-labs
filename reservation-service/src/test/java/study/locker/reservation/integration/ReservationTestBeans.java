package study.locker.reservation.integration;

import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.convention.TestBean;
import study.locker.common.*;
import study.locker.reservation.ReservationApp;
import study.locker.reservation.application.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@SpringBootTest(classes = ReservationApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "locker.grpc.port=0", "locker.pickup.ttl=45s", "locker.pickup.max-attempts=3", "locker.outbox.scheduling=false",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=study.locker.reservation.integration.ReservationQueryIT$SqlCapture"})
@Import(ReservationTestDatabase.class)
abstract class ReservationTestBeans {
    @TestBean(methodName = "testSecrets")
    PickupSecrets secrets;

    @TestBean(methodName = "testTime")
    Clock clock;

    @TestBean(methodName = "testIdentity")
    JwtDecoder lockerDecoder;

    @TestBean(methodName = "testDirectory")
    Directory directory;

    static final AtomicReference<Instant> now = new AtomicReference<>();

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
        return token -> switch (token) {
            case "courier" -> actor(token, "locker.read locker.deposit");
            case "courier-no-read" -> actor(token, "locker.deposit");
            case "alice-unverified" -> Jwt.withTokenValue("fixture").header("alg", "RS256").subject("alice")
                    .claim("scope", "locker.reserve").claim("email_verified", false).build();
            case "alice-no-scope" -> actor("alice", "locker.read");
            default -> actor(token, "locker.read locker.reserve");
        };
    }

    static Directory testDirectory() {
        return new Directory() {
            public List<Slot> search(String site, String size) {
                return List.of(new Slot("CENTRAL-S1", "Central", "SMALL"));
            }

            public void requireCompartment(String id) {
            }
        };
    }

    static Jwt actor(String sub, String scopes) {
        return Jwt.withTokenValue("fixture").header("alg", "RS256").subject(sub).claim("scope", scopes)
                .claim("email_verified", true).claim("email", sub + "@example.test").build();
    }
}
