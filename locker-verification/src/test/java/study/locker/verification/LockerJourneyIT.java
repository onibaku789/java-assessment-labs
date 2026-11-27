package study.locker.verification;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import static study.locker.verification.StackClient.*;
import static study.locker.verification.StackClient.Protocol.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;

@EnabledIfSystemProperty(named = "locker.stack", matches = "true")
class LockerJourneyIT {
    static String customer, courier, observer;
    static S3Client objects;
    static final String BUCKET =
            System.getenv().getOrDefault("LOCKER_RECEIPTS_BUCKET", "locker-audit");

    static String key() {
        return UUID.randomUUID().toString();
    }

    @BeforeAll
    static void setup() throws Exception {
        customer = token("alex", "locker.read locker.reserve");
        courier = token("driver", "locker.read locker.deposit");
        observer = token("guest", "observe");
        objects =
                S3Client.builder()
                        .endpointOverride(
                                URI.create(
                                        System.getenv()
                                                .getOrDefault("LOCKER_S3_ENDPOINT", local(19090))))
                        .forcePathStyle(true)
                        .region(Region.of(System.getenv().getOrDefault("AWS_REGION", "us-east-1")))
                        .credentialsProvider(
                                StaticCredentialsProvider.create(
                                        AwsBasicCredentials.create("lab", "lab")))
                        .build();
        for (Protocol protocol : Protocol.values()) {
            await().ignoreExceptions()
                    .atMost(Duration.ofSeconds(150))
                    .until(
                            () ->
                                    call(protocol, "available", customer, "", "", "")
                                            .code()
                                            .equals("OK"));
        }
    }

    @AfterAll
    static void close() {
        if (objects != null) objects.close();
    }

    private static String availableSlot(Protocol api) throws Exception {
        String ids = call(api, "available", customer, "", "", "").id();
        assertThat(ids).as("a free small compartment at Central").isNotBlank();
        return ids.split(",")[0];
    }

    @ParameterizedTest
    @EnumSource(Protocol.class)
    void equivalentOperationsAndFaults(Protocol api) throws Exception {
        assertThat(call(api, "available", null, "", "", "").code()).isEqualTo("UNAUTHENTICATED");
        assertThat(call(api, "available", observer, "", "", "").code())
                .isEqualTo("PERMISSION_DENIED");
        assertThat(call(api, "get", customer, key(), "", "").code()).isEqualTo("NOT_FOUND");
        assertThat(call(api, "get", customer, "invalid", "", "").code())
                .isEqualTo("INVALID_ARGUMENT");
        String slot = availableSlot(api);
        String request = key();
        var reserved = call(api, "reserve", customer, slot, request, "");
        assertThat(reserved.state()).isEqualTo("RESERVED");
        assertThat(call(api, "reserve", customer, slot, request, "")).isEqualTo(reserved);
        assertThat(call(api, "reserve", customer, slot, key(), "").code())
                .isEqualTo("ALREADY_EXISTS");
        assertThat(call(api, "get", customer, reserved.id(), "", "")).isEqualTo(reserved);
        assertThat(call(api, "cancel", customer, reserved.id(), key(), "").state())
                .isEqualTo("CANCELLED");
        var next = call(api, "reserve", customer, slot, key(), "");
        assertThat(call(api, "deposit", courier, next.id(), key(), "").state())
                .isEqualTo("DEPOSITED");
        String first = pickupMail(next.id(), null);
        assertThat(call(api, "reissue", customer, next.id(), key(), "").state())
                .isEqualTo("DEPOSITED");
        String second = pickupMail(next.id(), first);
        assertThat(call(api, "collect", customer, next.id(), key(), first).code())
                .isEqualTo("FAILED_PRECONDITION");
        assertThat(call(api, "collect", customer, next.id(), key(), second).state())
                .isEqualTo("COLLECTED");
    }

    @ParameterizedTest
    @EnumSource(Protocol.class)
    void commandValidationHasTheSamePublicFaultsAtEveryGateway(Protocol api) throws Exception {
        assertThat(call(api, "reserve", observer, " ", "invalid", "").code())
                .isEqualTo("PERMISSION_DENIED");
        for (String invalid : List.of("", "   ", "x".repeat(101))) {
            assertThat(call(api, "reserve", customer, invalid, key(), "").code())
                    .isEqualTo("INVALID_ARGUMENT");
        }
        // The directory looks up this well-formed target and reports that it does not exist.
        assertThat(call(api, "reserve", customer, "x".repeat(100), key(), "").code())
                .isEqualTo("NOT_FOUND");
    }

    @ParameterizedTest
    @EnumSource(Protocol.class)
    void rejectsInvalidBearerClaimsAtEveryGateway(Protocol api) throws Exception {
        var claims =
                new HashMap<String, Object>(
                        Map.of(
                                "sub",
                                "tester",
                                "iss",
                                System.getenv().getOrDefault("LOCKER_JWT_ISSUER", "urn:locker:lab"),
                                "aud",
                                System.getenv().getOrDefault("LOCKER_JWT_AUDIENCE", "locker-api"),
                                "exp",
                                java.time.Instant.now().plusSeconds(600).getEpochSecond(),
                                "scope",
                                "locker.read"));
        for (var override :
                Map.<String, Object>of("iss", "urn:other", "aud", "other-api", "exp", 1)
                        .entrySet()) {
            var invalid = new HashMap<>(claims);
            invalid.put(override.getKey(), override.getValue());
            assertThat(call(api, "available", sign(invalid), "", "", "").code())
                    .isEqualTo("UNAUTHENTICATED");
        }
        assertThat(call(api, "available", "invalid.token.signature", "", "", "").code())
                .isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void browserPreflightAllowsOnlyConfiguredOrigins() throws Exception {
        String allowedOrigin =
                System.getenv()
                        .getOrDefault("LOCKER_CORS_ORIGINS", "http://localhost:18081")
                        .split(",")[0]
                        .trim();
        String endpoint = local(18081) + "/v1/reservations";
        var permitted =
                send(
                        "OPTIONS",
                        endpoint,
                        null,
                        null,
                        null,
                        Map.of(
                                "Origin",
                                allowedOrigin,
                                "Access-Control-Request-Method",
                                "POST",
                                "Access-Control-Request-Headers",
                                "Authorization, Idempotency-Key"));
        assertThat(permitted.statusCode()).isEqualTo(200);
        assertThat(permitted.headers().firstValue("Access-Control-Allow-Origin"))
                .contains(allowedOrigin);
        var denied =
                send(
                        "OPTIONS",
                        endpoint,
                        null,
                        null,
                        null,
                        Map.of(
                                "Origin",
                                "https://other.example",
                                "Access-Control-Request-Method",
                                "POST"));
        assertThat(denied.statusCode()).isEqualTo(403);
        assertThat(denied.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
    }

    @Test
    void reserveViaRestDepositViaSoapCollectViaGraphqlAndRestoreReceipt(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path temp) throws Exception {
        String slot = availableSlot(REST);
        var b = call(REST, "reserve", customer, slot, key(), "");
        assertThat(b.state()).isEqualTo("RESERVED");
        assertThat(call(SOAP, "deposit", courier, b.id(), key(), "").state())
                .isEqualTo("DEPOSITED");
        String code = pickupMail(b.id(), null);
        assertThat(call(GRAPHQL, "collect", customer, b.id(), key(), code).state())
                .isEqualTo("COLLECTED");
        assertThat(call(GRAPHQL, "collect", customer, b.id(), key(), code).code())
                .isEqualTo("FAILED_PRECONDITION");
        assertThat(call(REST, "available", customer, "", "", "").id()).contains(slot);
        var receipt =
                await().atMost(Duration.ofSeconds(30))
                        .until(
                                () ->
                                        objects
                                                .listObjectsV2Paginator(
                                                        r ->
                                                                r.bucket(BUCKET)
                                                                        .prefix(
                                                                                "receipts/"
                                                                                        + b.id()
                                                                                        + "/"))
                                                .contents()
                                                .stream()
                                                .map(
                                                        o ->
                                                                objects.getObjectAsBytes(
                                                                                r ->
                                                                                        r.bucket(
                                                                                                        BUCKET)
                                                                                                .key(
                                                                                                        o
                                                                                                                .key()))
                                                                        .asByteArray())
                                                .filter(
                                                        bytes ->
                                                                JSON.readTree(bytes)
                                                                        .path("type")
                                                                        .asString()
                                                                        .equals("COLLECTED"))
                                                .findFirst(),
                                Optional::isPresent)
                        .orElseThrow();
        assertThat(JSON.readTree(receipt).propertyNames())
                .doesNotContain("code", "email", "envelope");
        String sandbox = "restore-drills/" + key() + "/";
        var folder = temp.resolve("receipts");
        ReceiptArchive.backup(objects, "receipts/" + b.id() + "/", folder);
        try {
            ReceiptArchive.restore(objects, sandbox, folder);
            ReceiptArchive.restore(objects, sandbox, folder);
            var restored = objects.listObjectsV2(r -> r.bucket(BUCKET).prefix(sandbox)).contents();
            assertThat(restored).hasSize(3);
            for (var obj : restored) {
                var saved =
                        objects.getObjectAsBytes(r -> r.bucket(BUCKET).key(obj.key()))
                                .asByteArray();
                assertThat(saved)
                        .isEqualTo(
                                java.nio.file.Files.readAllBytes(
                                        folder.resolve(obj.key().substring(sandbox.length()))));
            }
            var file = folder.resolve(restored.getFirst().key().substring(sandbox.length()));
            java.nio.file.Files.writeString(file, "damaged copy");
            assertThatIOException()
                    .isThrownBy(() -> ReceiptArchive.restore(objects, sandbox, folder))
                    .withMessageContaining("checksum");
        } finally {
            for (var obj : objects.listObjectsV2(r -> r.bucket(BUCKET).prefix(sandbox)).contents())
                objects.deleteObject(r -> r.bucket(BUCKET).key(obj.key()));
        }
    }

    static String pickupMail(String id, String previous) {
        return await().atMost(Duration.ofSeconds(30))
                .until(
                        () -> {
                            var list =
                                    JSON.readTree(
                                            send(
                                                            "GET",
                                                            local(
                                                                            Integer.parseInt(
                                                                                    System.getenv()
                                                                                            .getOrDefault(
                                                                                                    "MAILPIT_PORT",
                                                                                                    "18025")))
                                                                    + "/api/v1/messages?limit=100",
                                                            null,
                                                            null,
                                                            null,
                                                            Map.of())
                                                    .body());
                            for (var message : list.path("messages")) {
                                if (!message.path("Subject")
                                        .asString()
                                        .equals("Pickup ready for " + id)) continue;
                                var detail =
                                        JSON.readTree(
                                                send(
                                                                "GET",
                                                                local(
                                                                                Integer.parseInt(
                                                                                        System
                                                                                                .getenv()
                                                                                                .getOrDefault(
                                                                                                        "MAILPIT_PORT",
                                                                                                        "18025")))
                                                                        + "/api/v1/message/"
                                                                        + message.path("ID")
                                                                                .asString(),
                                                                null,
                                                                null,
                                                                null,
                                                                Map.of())
                                                        .body());
                                var match =
                                        Pattern.compile("Pickup code: (\\d{8})")
                                                .matcher(detail.path("Text").asString());
                                if (match.find() && !match.group(1).equals(previous))
                                    return match.group(1);
                            }
                            return null;
                        },
                        Objects::nonNull);
    }
}
