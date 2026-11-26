package study.parcel;

import java.io.IOException;
import java.util.HashMap;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.*;

@Disabled("Enable when starting Week 6")
@DisabledIfEnvironmentVariable(named = "GITHUB_ACTIONS", matches = "true", disabledReason = "Uses the learner's local kind cluster")
class Week06ReleaseIT extends LocalRelease {
    @Test void regionChangeReachesTheRunningService() throws IOException {
        var region = history().lastEntry().getValue().value("region").asString();
        assertNotEquals("north-yard", region, "Change the Helm region and wait for the rollout.");
        assertEquals(region, track().required("region").asString(), "The Service should serve the new region.");
    }

    @Test void serviceRoutesAroundOneLiveButUnreadyReplica() {
        var pods = cluster.pods().inNamespace(NAMESPACE).withLabel("app", "parcel").list().getItems();
        var paused = pods.stream().filter(pod -> "Running".equals(pod.getStatus().getPhase()))
                .filter(pod -> pod.getStatus().getConditions().stream()
                        .anyMatch(c -> "Ready".equals(c.getType()) && "False".equals(c.getStatus())))
                .toList();
        assertEquals(1, paused.size(), "Pause readiness on one running pod and wait for Ready=False.");
        String name = paused.getFirst().getMetadata().getName();
        var liveness = get(CORE_API + "/pods/" + name + ":8080/proxy/actuator/health/liveness");
        assertEquals("UP", liveness.required("status").asString());

        var ready = new HashMap<String, Boolean>();
        var slices = cluster.discovery().v1().endpointSlices().inNamespace(NAMESPACE)
                .withLabel("kubernetes.io/service-name", "parcel").list().getItems();
        for (var slice : slices) {
            for (var endpoint : slice.getEndpoints()) {
                ready.put(endpoint.getTargetRef().getName(), endpoint.getConditions().getReady());
            }
        }
        assertEquals(Boolean.FALSE, ready.get(name), "Wait for the paused pod's endpoint to become unready.");
        var counts = sampleReplicas();
        assertTrue(counts.keySet().stream().allMatch(replica -> Boolean.TRUE.equals(ready.get(replica))),
                () -> "All 30 requests should reach ready endpoints. Observed " + counts);
    }
}
