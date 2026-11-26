package study.parcel;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class LocalRelease {
    static final String NAMESPACE = "default";
    static final String CORE_API = "/api/v1/namespaces/" + NAMESPACE;
    private static final ObjectMapper JSON = new ObjectMapper();
    KubernetesClient cluster;

    @BeforeAll void connectToLab() {
        Path lab = Files.isDirectory(Path.of("release-lab")) ? Path.of("release-lab/.lab") : Path.of(".lab");
        assertThat(lab.resolve("cluster-owned")).as("Start the local lab with ./cluster.sh up and ./deploy.sh in release-lab.")
                .isRegularFile();
        var config = Config.fromKubeconfig("kind-parcel-study", lab.resolve("kubeconfig").toFile());
        assertThat(URI.create(config.getMasterUrl()).getHost()).as("Local kind API server").isEqualTo("127.0.0.1");
        cluster = new KubernetesClientBuilder().withConfig(config).build();
    }

    @AfterAll void closeClient() {
        if (cluster != null) cluster.close();
    }

    JsonNode get(String path) {
        return JSON.readTree(cluster.raw(path));
    }

    JsonNode track() {
        return get(CORE_API + "/services/parcel/proxy/track");
    }

    Map<String, Integer> sampleReplicas() {
        Map<String, Integer> counts = new TreeMap<>();
        for (int request = 0; request < 30; request++) {
            String replica = track().required("replica").asString();
            counts.merge(replica, 1, Integer::sum);
        }
        return counts;
    }

    record Revision(JsonNode release) {
        int number() { return release.required("version").asInt(); }
        JsonNode info() { return release.required("info"); }
        JsonNode value(String key) {
            JsonNode override = release.path("config").path(key);
            return override.isMissingNode() ? release.required("chart").required("values").required(key) : override;
        }
    }

    TreeMap<Integer, Revision> history() throws IOException {
        var history = new TreeMap<Integer, Revision>();
        var secrets = cluster.secrets().inNamespace(NAMESPACE)
                .withLabels(Map.of("owner", "helm", "name", "parcel")).list().getItems();
        // Kubernetes Secret.data wraps Helm's base64-encoded, gzipped release record.
        var decoder = Base64.getDecoder();
        for (var secret : secrets) {
            byte[] compressed = decoder.decode(decoder.decode(secret.getData().get("release")));
            try (var stream = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
                var revision = new Revision(JSON.readTree(stream));
                history.put(revision.number(), revision);
            }
        }
        assertThat(history).as("Deploy the parcel Helm release in the local lab.").isNotEmpty();
        return history;
    }
}
