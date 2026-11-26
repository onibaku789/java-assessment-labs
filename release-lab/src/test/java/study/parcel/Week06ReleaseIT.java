package study.parcel;

import java.io.IOException;
import java.util.HashMap;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled("Enable when starting Week 6")
@DisabledIfEnvironmentVariable(named = "GITHUB_ACTIONS", matches = "true", disabledReason = "Uses the learner's local kind cluster")
class Week06ReleaseIT extends LocalRelease {
    @Test void regionChangeReachesTheRunningService() throws IOException {
        var region = history().lastEntry().getValue().value("region").asString();
        assertThat(region).as("Change the Helm region and wait for the rollout.").isNotEqualTo("north-yard");
        assertThat(track().required("region").asString()).as("Region served after rollout").isEqualTo(region);
    }

    @Test void serviceRoutesAroundOneLiveButUnreadyReplica() {
        var pods = cluster.pods().inNamespace(NAMESPACE).withLabel("app", "parcel").list().getItems();
        var paused = pods.stream().filter(pod -> "Running".equals(pod.getStatus().getPhase()))
                .filter(pod -> pod.getStatus().getConditions().stream()
                        .anyMatch(c -> "Ready".equals(c.getType()) && "False".equals(c.getStatus())))
                .toList();
        assertThat(paused).as("Pause readiness on one running pod and wait for Ready=False.").hasSize(1);
        String name = paused.getFirst().getMetadata().getName();
        var liveness = get(CORE_API + "/pods/" + name + ":8080/proxy/actuator/health/liveness");
        assertThat(liveness.required("status").asString()).isEqualTo("UP");

        var ready = new HashMap<String, Boolean>();
        var slices = cluster.discovery().v1().endpointSlices().inNamespace(NAMESPACE)
                .withLabel("kubernetes.io/service-name", "parcel").list().getItems();
        for (var slice : slices) {
            for (var endpoint : slice.getEndpoints()) {
                ready.put(endpoint.getTargetRef().getName(), endpoint.getConditions().getReady());
            }
        }
        assertThat(ready).as("Wait for the paused pod's endpoint to become unready.").containsEntry(name, false);
        var counts = sampleReplicas();
        assertThat(counts.keySet()).as("Replicas serving the 30 requests: %s", counts)
                .allSatisfy(replica -> assertThat(ready).containsEntry(replica, true));
    }
}
