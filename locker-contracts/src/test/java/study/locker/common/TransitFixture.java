package study.locker.common;

import com.github.dockerjava.api.command.InspectContainerResponse;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.net.URI;

public final class TransitFixture extends GenericContainer<TransitFixture> {
    public TransitFixture() {
        super("hashicorp/vault:2.2.2");
        withEnv("VAULT_DEV_ROOT_TOKEN_ID", "fixture");
        withEnv("VAULT_ADDR", "http://127.0.0.1:8200");
        withEnv("VAULT_TOKEN", "fixture");
        withExposedPorts(8200);
        withCopyFileToContainer(MountableFile.forClasspathResource("vault-configure.sh"), "/tmp/configure.sh");
        waitingFor(Wait.forHttp("/v1/sys/health").forPort(8200));
    }

    @Override
    protected void containerIsStarted(InspectContainerResponse info) {
        cli("sh", "/tmp/configure.sh", "approle");
        // Fixed synthetic keys used to generate the pre-Vault database and mail fixtures.
        cli("vault", "transit", "import", "transit/keys/legacy-mac",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", "type=hmac");
        cli("vault", "transit", "import", "transit/keys/legacy-mail",
                "bzz1Fnp7D/PmbzInFavAjoQTxHXHEJdofs4bnS/oHYA=", "type=aes256-gcm96");
    }

    public String uri() {
        return "http://" + getHost() + ":" + getMappedPort(8200);
    }

    public String token(String role) {
        return cli("vault", "token", "create", "-field=token", "-policy=" + role).trim();
    }

    public PickupTransit transit(String role) {
        return new PickupTransit(new VaultTemplate(VaultEndpoint.from(URI.create(uri())),
                new TokenAuthentication(token(role))));
    }

    public void rotate(String key) {
        cli("vault", "write", "-f", "transit/keys/" + key + "/rotate");
    }

    private String cli(String... args) {
        try {
            var result = execInContainer(args);
            if (result.getExitCode() != 0) throw new IllegalStateException("Vault fixture setup failed: " + result.getStderr());
            return result.getStdout();
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(failure);
        }
    }
}
