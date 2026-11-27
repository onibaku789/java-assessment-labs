"""Exercise outages in the local manual Compose stack and restore every affected service."""

import contextlib
import json
import os
import signal
import subprocess
import time
import uuid
from urllib.error import HTTPError
from urllib.parse import quote, urlencode
from urllib.request import Request, urlopen
from xml.etree import ElementTree


def compose(*args):
    return subprocess.check_output(["./scripts/compose", *args], text=True).strip()


def fetch(url, headers=None, data=None):
    request = Request(url, headers=headers or {}, data=data)
    with urlopen(request, timeout=8) as response:
        return response.read()


def wait(description, check, seconds=90):
    until = time.monotonic() + seconds
    while time.monotonic() < until:
        try:
            if check():
                print(description, flush=True)
                return
        except (OSError, TimeoutError):
            pass
        time.sleep(0.4)
    raise RuntimeError("Timed out: " + description)


@contextlib.contextmanager
def outage(service, pause):
    cid = compose("ps", "-q", service)
    if (
        not cid
        or subprocess.check_output(
            ["docker", "inspect", "-f", "{{.State.Status}}", cid], text=True
        ).strip()
        != "running"
    ):
        raise RuntimeError("Start the manual lab before running the drill: " + service)
    try:
        cmd = ["pause", cid] if pause else ["stop", "-t", "2", cid]
        subprocess.run(["docker", *cmd], check=True, stdout=subprocess.DEVNULL)
        yield
    finally:
        subprocess.run(
            ["docker", "unpause" if pause else "start", cid],
            check=True,
            stdout=subprocess.DEVNULL,
        )


def main():
    rest = "http://" + compose("port", "rest", "8080")
    grafana = "http://" + compose("port", "grafana", "3000")
    storage = (
        os.environ["LOCKER_S3_ENDPOINT"].rstrip("/")
        + "/"
        + quote(os.environ["LOCKER_RECEIPTS_BUCKET"])
    )
    actor = "drill-" + uuid.uuid4().hex
    token = subprocess.check_output(
        ["./scripts/token.py", "customer", actor], text=True
    ).strip()

    def api(path, body=None, key=None):
        headers = {"Authorization": "Bearer " + token}
        if key:
            headers.update({"Content-Type": "application/json", "Idempotency-Key": key})
        data = None if key is None else json.dumps(body or {}).encode()
        return json.loads(fetch(rest + "/v1/" + path, headers, data))

    def sample(expression):
        url = (
            grafana
            + "/api/datasources/proxy/uid/locker-prom/api/v1/query?"
            + urlencode({"query": expression})
        )
        values = json.loads(fetch(url))["data"]["result"]
        return float(values[0]["value"][1]) if values else 0

    def receipt(reservation, state):
        listing = ElementTree.fromstring(
            fetch(
                storage
                + "?"
                + urlencode(
                    {"list-type": "2", "prefix": "receipts/" + reservation + "/"}
                )
            )
        )
        return any(
            json.loads(fetch(storage + "/" + quote(node.text)))["type"] == state
            for node in listing.findall(".//{*}Key")
        )

    def lookup_fails():
        try:
            api("compartments")
        except HTTPError as failure:
            if failure.code not in (503, 504):
                raise
            return True
        return False

    slots = api("compartments")
    if not slots:
        raise RuntimeError("The drill needs a free compartment.")
    key = str(uuid.uuid4())
    payload = {"compartmentId": slots[0]["id"]}
    # Discard the first successful reply, then recover its identity using the same command key.
    api("reservations", payload, key)
    booking = api("reservations", payload, key)["id"]
    path = "reservations/" + booking
    try:
        wait(
            "Committed reserve replayed; receipt stored",
            lambda: receipt(booking, "RESERVED"),
        )
        with outage("directory", False):
            wait(
                "Directory breaker opened",
                lambda: (
                    lookup_fails()
                    and sample(
                        'max(locker_rpc_breaker_state{service="reservations",dependency="directory"})'
                    )
                    == 2
                ),
            )
            if api(path)["state"] != "RESERVED":
                raise RuntimeError("Independent reservation lookup failed")
            print("Existing booking remained readable", flush=True)
        wait(
            "Directory recovered and breaker closed",
            lambda: (
                api("compartments") is not None
                and sample(
                    'max(locker_rpc_breaker_state{service="reservations",dependency="directory"})'
                )
                == 0
            ),
        )
        parked = 'sum(locker_worker_deliveries_total{service="receipts",outcome="waiting_for_dependency"})'
        initial = sample(parked)
        with outage("objects", True):
            api(path + "/cancel", key=str(uuid.uuid4()))
            wait(
                "Valid receipt parked during S3 outage",
                lambda: sample(parked) > initial,
            )
        wait(
            "Worker resumed and stored the pending cancellation",
            lambda: receipt(booking, "CANCELLED"),
        )
        fallback = 'sum(locker_quota_events_total{service="rest",outcome="fallback"})'
        initial = sample(fallback)
        with outage("valkey", True):
            if api(path)["state"] != "CANCELLED":
                raise RuntimeError("Local quota fallback failed")
            wait(
                "Local quota fallback visible in Grafana",
                lambda: sample(fallback) > initial,
            )
        wait(
            "Shared quota checks recovered", lambda: shared_quota_works(rest, api, path)
        )
        print("Grafana: " + grafana + "/d/locker-overview/locker-overview", flush=True)
    finally:
        if api(path)["state"] == "RESERVED":
            api(path + "/cancel", key=str(uuid.uuid4()))


def shared_quota_works(rest, api, path):
    def fallback_count():
        for line in fetch(rest + "/actuator/prometheus").decode().splitlines():
            if (
                line.startswith("locker_quota_events_total{")
                and 'outcome="fallback"' in line
            ):
                return float(line.split()[-1])
        return 0

    before = fallback_count()
    api(path)
    return fallback_count() == before


if __name__ == "__main__":
    signal.signal(signal.SIGTERM, lambda *_: exit(143))
    main()
