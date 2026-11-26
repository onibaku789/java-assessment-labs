package study.parcel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.*;

@Disabled("Enable when starting Week 7")
@DisabledIfEnvironmentVariable(named = "GITHUB_ACTIONS", matches = "true", disabledReason = "Uses the learner's local kind cluster")
class Week07OperationsIT extends LocalRelease {
    @Test void hpaScaledAboveItsMinimumInResponseToCpuLoad() {
        var hpa = cluster.autoscaling().v2().horizontalPodAutoscalers().inNamespace(NAMESPACE).withName("parcel").get();
        assertNotNull(hpa, "Enable autoscale in the parcel chart.");
        assertTrue(hpa.getSpec().getMetrics().stream().anyMatch(metric ->
                "Resource".equals(metric.getType()) && "cpu".equals(metric.getResource().getName())),
                "Configure the HPA to scale on CPU.");
        var events = get(CORE_API + "/events?fieldSelector=involvedObject.kind%3DHorizontalPodAutoscaler%2CinvolvedObject.name%3Dparcel");
        var sizes = new ArrayList<Integer>();
        var newSize = Pattern.compile("New size:\\s*(\\d+)");
        for (var event : events.required("items")) {
            if ("SuccessfulRescale".equals(event.path("reason").asString())
                    && hpa.getMetadata().getUid().equals(event.path("involvedObject").path("uid").asString())) {
                String message = event.required("message").asString();
                var match = newSize.matcher(message);
                if (match.find() && message.toLowerCase(Locale.ROOT).contains("cpu")) {
                    sizes.add(Integer.parseInt(match.group(1)));
                }
            }
        }
        assertTrue(sizes.stream().anyMatch(size -> size > hpa.getSpec().getMinReplicas()),
                () -> "Generate sustained /work traffic with metrics enabled. Observed CPU scaling decisions " + sizes);
    }

    @Test void rollbackRestoredAHealthyReleaseAndTraffic() throws IOException {
        var history = history();
        var current = history.lastEntry().getValue();
        int target = current.info().path("rollback_revision").asInt(0);
        assertTrue(target > 0, "Release accepting=false, diagnose it, then run helm rollback.");
        assertTrue(history.containsKey(target) && target < current.number(), "Keep the healthy target revision in Helm history.");
        assertTrue(history.subMap(target, false, current.number(), false).values().stream()
                        .anyMatch(revision -> !revision.value("accepting").booleanValue()),
                "The intervening revision should have accepting=false.");
        assertTrue(history.get(target).value("accepting").booleanValue(), "Choose a healthy rollback target.");
        assertTrue(current.value("accepting").booleanValue(), "The current revision should accept traffic.");
        assertEquals("deployed", current.info().required("status").asString());
        var counts = sampleReplicas();
        assertTrue(counts.size() >= 2, () -> "Expect 30 successful requests across at least two replicas. Observed " + counts);
    }
}
