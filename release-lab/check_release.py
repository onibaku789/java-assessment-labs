"""Check the current learning cluster. Run via ../check.sh 6 or 7."""
import json
import re
import subprocess
import sys
import unittest


def run(*args):
    result = subprocess.run(args, capture_output=True, text=True, timeout=45)
    if result.returncode:
        raise AssertionError(f"{' '.join(args)} failed: {result.stderr.strip()}")
    return result.stdout


def resource(*args):
    return json.loads(run("kubectl", "get", *args, "-o", "json"))


def values(revision=None):
    command = ["helm", "get", "values", "parcel", "--all", "--output", "json"]
    if revision is not None:
        command.extend(["--revision", str(revision)])
    return json.loads(run(*command))


def tracking():
    code = ('import urllib.request; '
            'print(urllib.request.urlopen("http://parcel/track", timeout=5).read().decode())')
    return json.loads(run("kubectl", "exec", "parcel-client", "--", "python", "-c", code))


def sample_tracking():
    # Probe new connections at a modest rate after the learner's load test.
    code = """
import collections, json, time, urllib.request
counts = collections.Counter()
for _ in range(30):
    with urllib.request.urlopen("http://parcel/track", timeout=3) as response:
        counts[json.load(response)["replica"]] += 1
    time.sleep(0.05)
print(json.dumps(counts))
"""
    return json.loads(run("kubectl", "exec", "parcel-client", "--", "python", "-c", code))


class Week06Release(unittest.TestCase):
    def test_helm_region_change_reaches_the_running_service(self):
        region = values()["region"]
        self.assertNotEqual("north-yard", region, "Change the Helm region and wait for the rollout.")
        self.assertEqual(region, tracking()["region"], "The service must read the new ConfigMap value.")

    def test_service_routes_around_one_live_but_unready_replica(self):
        pods = resource("pods", "-l", "app=parcel")["items"]
        unready = [pod for pod in pods if pod["status"]["phase"] == "Running" and any(
            c["type"] == "Ready" and c["status"] == "False" for c in pod["status"]["conditions"])]
        self.assertEqual(1, len(unready), "Pause readiness on one running pod and wait for Ready=False.")
        paused = unready[0]
        paused_name = paused["metadata"]["name"]
        probe = ('import urllib.request; print(urllib.request.urlopen('
                 f'"http://{paused["status"]["podIP"]}:8080/actuator/health/liveness", timeout=5)'
                 '.status)')
        self.assertEqual("200", run("kubectl", "exec", "parcel-client", "--", "python", "-c", probe).strip())
        slices = resource("endpointslices", "-l", "kubernetes.io/service-name=parcel")["items"]
        endpoints = {e["targetRef"]["name"]: e["conditions"]["ready"]
                     for item in slices for e in item["endpoints"]}
        self.assertIs(endpoints.get(paused_name), False, "Wait for the paused pod's endpoint to become unready.")
        counts = sample_tracking()
        self.assertEqual(30, sum(counts.values()))
        self.assertTrue(all(endpoints.get(name) for name in counts),
                        f"Traffic should reach only ready endpoints: {counts}")


class Week07Operations(unittest.TestCase):
    def test_hpa_observed_cpu_load_and_scaled_above_its_minimum(self):
        hpa = resource("hpa", "parcel")
        metrics = hpa["spec"]["metrics"]
        self.assertTrue(any(m["type"] == "Resource" and m["resource"]["name"] == "cpu" for m in metrics))
        events = resource("events", "--field-selector",
                          "involvedObject.kind=HorizontalPodAutoscaler,involvedObject.name=parcel")["items"]
        sizes = []
        for event in events:
            if event.get("reason") == "SuccessfulRescale" and event["involvedObject"]["uid"] == hpa["metadata"]["uid"]:
                match = re.search(r"New size:\s*(\d+)", event["message"])
                if match and "cpu" in event["message"].lower():
                    sizes.append(int(match.group(1)))
        self.assertTrue(any(size > hpa["spec"]["minReplicas"] for size in sizes),
                        f"Run sustained /work traffic with metrics enabled. Recorded scaling decisions: {sizes}")

    def test_bad_helm_release_was_rolled_back_and_traffic_is_healthy(self):
        history = json.loads(run("helm", "history", "parcel", "-o", "json"))
        current = history[-1]
        self.assertIn("rollback_revision", current, "Release accepting=false, diagnose it, then run helm rollback.")
        target = int(current["rollback_revision"])
        intervening = [row["revision"] for row in history if target < int(row["revision"]) < int(current["revision"])]
        self.assertTrue(any(values(rev)["accepting"] is False for rev in intervening),
                        "The rollback should recover from a revision with accepting=false.")
        self.assertIs(values(target)["accepting"], True)
        self.assertIs(values()["accepting"], True)
        self.assertEqual("deployed", current["status"])
        counts = sample_tracking()
        self.assertEqual(30, sum(counts.values()))
        self.assertGreaterEqual(len(counts), 2, f"Sample traffic across ready replicas: {counts}")
        print(f"Successful requests per replica after rollback: {counts}")


if __name__ == "__main__":
    suite = Week06Release if sys.argv[1] == "6" else Week07Operations
    tests = unittest.defaultTestLoader.loadTestsFromTestCase(suite)
    sys.exit(0 if unittest.TextTestRunner(verbosity=2).run(tests).wasSuccessful() else 1)
