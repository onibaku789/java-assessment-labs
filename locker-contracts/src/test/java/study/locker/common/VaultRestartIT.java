package study.locker.common;

import com.github.dockerjava.api.model.ExposedPort;
import org.junit.jupiter.api.Test;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultInitializationRequest;
import org.springframework.vault.support.VaultMount;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.MountableFile;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class VaultRestartIT {
    private static final String CONFIG = """
            disable_mlock = true
            api_addr = "http://127.0.0.1:8200"
            storage "file" { path = "/vault/file" }
            listener "tcp" {
                address = "0.0.0.0:8200"
                tls_disable = true
            }
            """;

    @Test
    void existingEnvelopeOpensAfterServerRestartAndAutomaticUnseal() {
        try (var server = persistentServer()) {
            server.start();
            var endpoint = endpoint(server);
            var bootstrap = new VaultTemplate(endpoint).opsForSys();
            var init = bootstrap.initialize(VaultInitializationRequest.create(1, 1));
            server.copyFileToContainer(Transferable.of(init.getKeys().getFirst(), 0444), "/run/unseal/share");
            await().atMost(Duration.ofSeconds(20)).until(() -> !bootstrap.getUnsealStatus().isSealed());

            var admin = new VaultTemplate(endpoint, new TokenAuthentication(init.getRootToken()));
            admin.opsForSys().mount("transit", VaultMount.create("transit"));
            admin.opsForTransit().createKey("pickup-mail");
            var pickup = new PickupTransit(admin);
            String envelope = pickup.seal("event", "01873456");

            server.getDockerClient().restartContainerCmd(server.getContainerId()).exec();
            var restored = new VaultTemplate(endpoint(server), new TokenAuthentication(init.getRootToken()));
            await().atMost(Duration.ofSeconds(30)).ignoreExceptions().until(() -> !restored.opsForSys().getUnsealStatus().isSealed());
            assertThat(new PickupTransit(restored).open("event", envelope)).isEqualTo("01873456");
        }
    }

    private static VaultEndpoint endpoint(GenericContainer<?> server) {
        var ports = server.getCurrentContainerInfo().getNetworkSettings().getPorts().getBindings();
        String port = ports.get(ExposedPort.tcp(8200))[0].getHostPortSpec();
        return VaultEndpoint.from(URI.create("http://" + server.getHost() + ":" + port));
    }

    private static GenericContainer<?> persistentServer() {
        return new GenericContainer<>("hashicorp/vault:2.2.2")
                .withExposedPorts(8200)
                .withEnv("VAULT_ADDR", "http://127.0.0.1:8200")
                .withCopyToContainer(Transferable.of(CONFIG), "/tmp/server.hcl")
                .withCopyToContainer(Transferable.of(""), "/run/unseal/share")
                .withCopyFileToContainer(MountableFile.forClasspathResource("vault-unseal.sh"), "/tmp/unseal.sh")
                .withCommand("sh", "-ec", "sh /tmp/unseal.sh & exec vault server -config=/tmp/server.hcl")
                .waitingFor(Wait.forHttp("/v1/sys/health").forStatusCode(501));
    }
}
