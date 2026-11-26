package study.parcel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled("Enable when starting Week 7")
@DisabledIfEnvironmentVariable(named = "GITHUB_ACTIONS", matches = "true", disabledReason = "Uses the learner's local kind cluster")
class Week07OperationsIT extends LocalRelease {
    @Test void hpaScaledAboveItsMinimumInResponseToCpuLoad() {
        var hpa = cluster.autoscaling().v2().horizontalPodAutoscalers().inNamespace(NAMESPACE).withName("parcel").get();
        assertThat(hpa).as("Enable autoscale in the parcel chart.").isNotNull();
        assertThat(hpa.getSpec().getMetrics()).as("Configure the HPA to scale on CPU.")
                .anyMatch(metric -> "Resource".equals(metric.getType()) && "cpu".equals(metric.getResource().getName()));
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
        assertThat(sizes).as("CPU scaling decisions after sustained /work traffic")
                .anyMatch(size -> size > hpa.getSpec().getMinReplicas());
    }

    @Test void rollbackRestoredAHealthyReleaseAndTraffic() throws IOException {
        var history = history();
        var current = history.lastEntry().getValue();
        int target = current.info().path("rollback_revision").asInt(0);
        assertThat(target).as("Release accepting=false, diagnose it, then run helm rollback.").isPositive();
        assertThat(history).as("Keep the healthy target revision in Helm history.").containsKey(target);
        assertThat(target).as("Rollback target revision").isLessThan(current.number());
        assertThat(history.subMap(target, false, current.number(), false).values())
                .as("The intervening revision should have accepting=false.")
                .anyMatch(revision -> !revision.value("accepting").booleanValue());
        assertThat(history.get(target).value("accepting").booleanValue()).as("Choose a healthy rollback target.").isTrue();
        assertThat(current.value("accepting").booleanValue()).as("The current revision should accept traffic.").isTrue();
        assertThat(current.info().required("status").asString()).isEqualTo("deployed");
        var counts = sampleReplicas();
        assertThat(counts).as("Replicas serving the 30 requests").hasSizeGreaterThanOrEqualTo(2);
    }
}
