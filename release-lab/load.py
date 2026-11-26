"""Run inside the cluster with Python 3. Fresh connections exercise the Service."""
from collections import Counter
from concurrent.futures import ThreadPoolExecutor
import json
import math
import sys
import time
import urllib.request

endpoint = sys.argv[1] if len(sys.argv) > 1 else "track"
seconds = min(300, max(1, int(sys.argv[2]))) if len(sys.argv) > 2 else 5
concurrency = min(8, max(1, int(sys.argv[3]))) if len(sys.argv) > 3 else 1
assert endpoint in {"track", "work"}
deadline = time.monotonic() + seconds


def run(_):
    samples = []
    while time.monotonic() < deadline:
        started = time.monotonic()
        try:
            with urllib.request.urlopen("http://parcel/" + endpoint, timeout=4) as res:
                outcome = json.load(res)["replica"]
        except (OSError, ValueError) as error:
            outcome = "ERROR " + type(error).__name__
        samples.append((outcome, 1000 * (time.monotonic() - started)))
    return samples


with ThreadPoolExecutor(max_workers=concurrency) as pool:
    samples = [entry for group in pool.map(run, range(concurrency)) for entry in group]
times = sorted(ms for _, ms in samples)
distribution = Counter(name for name, _ in samples)
errors = sum(n for name, n in distribution.items() if name.startswith("ERROR "))
print(json.dumps({"requests": len(times), "errors": errors, "distribution": dict(distribution),
                  "p95_ms": round(times[math.ceil(0.95 * len(times)) - 1], 2)}))
