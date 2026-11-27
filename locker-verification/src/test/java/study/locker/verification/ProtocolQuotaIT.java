package study.locker.verification;

import static org.assertj.core.api.Assertions.*;

import static study.locker.verification.StackClient.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.*;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.lifecycle.Startables;

import java.security.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

@EnabledIfSystemProperty(named = "locker.protocolQuotas", matches = "true")
class ProtocolQuotaIT {
    @Test
    void restGraphqlSoapAndAnotherReplicaEnforceOneBudget() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var keys = generator.generateKeyPair();
        var pem =
                "-----BEGIN PUBLIC KEY-----\n"
                        + Base64.getMimeEncoder().encodeToString(keys.getPublic().getEncoded())
                        + "\n-----END PUBLIC KEY-----\n";
        try (var network = Network.newNetwork();
                var store =
                        new GenericContainer<>("valkey/valkey:8.1.12-alpine")
                                .withNetwork(network)
                                .withNetworkAliases("valkey")
                                .withExposedPorts(6379);
                var rest = gateway("rest", network, pem);
                var graphql = gateway("graphql", network, pem);
                var soap = gateway("soap", network, pem);
                var replica = gateway("rest", network, pem)) {
            store.start();
            Startables.deepStart(Stream.of(rest, graphql, soap, replica)).join();
            String actor = signed("alice", keys);
            // The deliberately absent upstream returns UNAVAILABLE for admitted operations.
            assertThat(
                            send("GET", url(rest) + "/v1/compartments", actor, null, null, Map.of())
                                    .statusCode())
                    .isEqualTo(503);
            var graph =
                    send(
                            "POST",
                            url(graphql) + "/graphql",
                            actor,
                            "application/json",
                            "{\"query\":\"{ a: available { id } b: available { id } }\"}",
                            Map.of());
            assertThat(JSON.readTree(graph.body()).path("errors").size()).isEqualTo(2);
            assertThat(graph.body()).contains("UNAVAILABLE").doesNotContain("RESOURCE_EXHAUSTED");
            String envelope =
                    "<s:Envelope"
                        + " xmlns:s='http://schemas.xmlsoap.org/soap/envelope/'><s:Body><available"
                        + " xmlns='urn:lockers:v1'/></s:Body></s:Envelope>";
            assertThat(
                            send(
                                            "POST",
                                            url(soap) + "/soap/lockers",
                                            actor,
                                            "text/xml",
                                            envelope,
                                            Map.of("SOAPAction", "\"urn:lockers:available\""))
                                    .body())
                    .contains("RESOURCE_EXHAUSTED");
            var denied =
                    send("GET", url(replica) + "/v1/compartments", actor, null, null, Map.of());
            assertThat(denied.statusCode()).isEqualTo(429);
            assertThat(Long.parseLong(denied.headers().firstValue("Retry-After").orElseThrow()))
                    .isPositive();
            assertThat(
                            send(
                                            "GET",
                                            url(replica) + "/v1/compartments",
                                            signed("bob", keys),
                                            null,
                                            null,
                                            Map.of())
                                    .statusCode())
                    .isEqualTo(503);
        }
    }

    private static GenericContainer<?> gateway(String name, Network network, String pem) {
        return new GenericContainer<>("locker/locker-" + name + "-api:lab")
                .withNetwork(network)
                .withCopyToContainer(Transferable.of(pem, 0444), "/run/locker/public.pem")
                .withEnv("JAVA_TOOL_OPTIONS", "-Xmx176m -XX:ActiveProcessorCount=2")
                .withEnv("LOCKER_RESERVATIONS", "127.0.0.1:1")
                .withEnv("LOCKER_RPC_RETRY", "false")
                .withEnv("LOCKER_QUOTA_RATE", "1")
                .withEnv("LOCKER_QUOTA_BURST", "3")
                .withExposedPorts(8080)
                .waitingFor(Wait.forHttp("/actuator/health/readiness"));
    }

    private static String url(GenericContainer<?> service) {
        return "http://" + service.getHost() + ":" + service.getMappedPort(8080);
    }

    private static String signed(String subject, KeyPair keys) throws Exception {
        return sign(
                Map.of(
                        "iss",
                        "urn:locker:lab",
                        "aud",
                        "locker-api",
                        "sub",
                        subject,
                        "scope",
                        "locker.read",
                        "exp",
                        Instant.now().plusSeconds(300).getEpochSecond()),
                keys.getPrivate());
    }
}
