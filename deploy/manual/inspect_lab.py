"""Same-origin read models for the manual lab. Only projected data leaves this process."""

import json
import re
from http.client import HTTPConnection, HTTPException
from urllib.parse import parse_qs, urlsplit

import psycopg
from psycopg import sql
from psycopg.rows import dict_row

from settings import INSPECT_TIMEOUT, PAGE_SIZE, PUBLIC_SETTINGS

TABLES = {
    "compartment": ("id", "site", "size"),
    "booking": (
        "id",
        "compartment",
        "state",
        "expires_at",
        "attempts",
        "pickup_max_attempts",
    ),
    "command_result": ("request_id", "outcome"),
    "outbox": ("seq", "event_id", "reservation_id", "topic"),
}
TRACE = re.compile(r"[0-9a-f]{32}")
GATEWAYS = {"rest", "graphql", "soap"}
WORKERS = {"receipts", "notifications"}


def attrs(values):
    return {item["key"]: next(iter(item["value"].values()), "") for item in values}


DELIVERIES = {
    "sent": "Email sent",
    "stored": "Receipt stored",
    "replayed": "Receipt replayed",
    "expired": "Pickup notification expired",
    "retried": "Delivery retry",
    "failed": "Delivery failed",
    "retry_failed": "Retry failed",
    "dead_lettered": "Dead-lettered",
    "dead_letter_failed": "Dead-letter failed",
}


def observed_steps(service, span):
    """Expose operations and timings. Raw span attributes may contain application data."""
    fields = attrs(span.get("attributes", []))
    kind = span.get("kind")
    start = int(span["startTimeUnixNano"])
    end = int(span["endTimeUnixNano"])
    error = span.get("status", {}).get("code") in (2, "STATUS_CODE_ERROR")

    def step(source, target, operation, suffix="", at=start, failed=error):
        return {
            "id": span["spanId"] + suffix,
            "source": source,
            "target": target,
            "operation": operation,
            "at": at // 1000000,
            "ms": round((end - start) / 1e6, 1),
            "failed": failed,
        }

    if (
        service in GATEWAYS
        and kind in (2, "SPAN_KIND_SERVER")
        and ("http.request.method" in fields or "http.method" in fields)
    ):
        yield step("browser", service, "HTTP request")
    elif fields.get("rpc.system", fields.get("rpc.system.name")) == "grpc" and kind in (
        3,
        "SPAN_KIND_CLIENT",
    ):
        if service in GATEWAYS or service == "reservations":
            target = "directory" if service == "reservations" else "reservations"
            method = fields.get("rpc.method", span["name"]).split("/")[-1]
            yield step(service, target, "gRPC · " + method)
    elif fields.get(
        "db.system.name", fields.get("db.system")
    ) == "postgresql" and service in {"reservations", "directory"}:
        verb = fields.get("db.operation.name", fields.get("db.operation", "")).upper()
        yield step(
            service,
            "postgres",
            verb
            if verb in {"SELECT", "INSERT", "DELETE", "UPDATE", "COMMIT"}
            else "Database call",
        )
    elif span.get("name") == "outbox.publish":
        yield step("postgres", "relay", "Outbox event claimed", "-claim", failed=False)
        yield step(
            "relay",
            "kafka",
            "Publish failed" if error else "Event published",
            "-publish",
            at=end,
        )
    elif service in WORKERS and kind in (5, "SPAN_KIND_CONSUMER"):
        yield step("kafka", service, "Consume event")

    for i, event in enumerate(span.get("events", [])):
        outcome = attrs(event.get("attributes", [])).get("locker.outcome")
        at = int(event["timeUnixNano"])
        if (
            event["name"] == "worker_delivery"
            and service in WORKERS
            and outcome in DELIVERIES
        ):
            if outcome in {"stored", "sent", "replayed"}:
                target = "objects" if service == "receipts" else "mailpit"
                yield step(service, target, DELIVERIES[outcome], f"-{i}", at, False)
            else:
                yield step(
                    "kafka",
                    service,
                    DELIVERIES[outcome],
                    f"-{i}",
                    at,
                    outcome != "retried",
                )
        elif event["name"] == "reservation_command" and outcome in {
            "committed",
            "replayed",
            "rejected",
        }:
            yield step(
                "reservations",
                "reservations",
                "Command " + outcome,
                f"-{i}",
                at,
                outcome == "rejected",
            )


def trace_steps(trace):
    steps = []
    for batch in trace.get("batches", trace.get("resourceSpans", [])):
        service = attrs(batch["resource"].get("attributes", [])).get("service.name")
        for scope in batch.get(
            "scopeSpans", batch.get("instrumentationLibrarySpans", [])
        ):
            for span in scope.get("spans", []):
                steps.extend(observed_steps(service, span))
    return sorted(steps, key=lambda item: (item["at"], item["id"]))


def fetch_trace(trace_id):
    conn = HTTPConnection("tempo", 3200, timeout=INSPECT_TIMEOUT)
    try:
        conn.request(
            "GET", "/api/traces/" + trace_id, headers={"Accept": "application/json"}
        )
        response = conn.getresponse()
        if response.status == 404:
            return {"steps": [], "waiting": True}
        if response.status != 200:
            raise OSError("trace store unavailable")
        return {"steps": trace_steps(json.load(response)), "waiting": False}
    finally:
        conn.close()


def read_model(path):
    url = urlsplit(path)
    if url.path == "/inspect/config":
        return PUBLIC_SETTINGS
    args = parse_qs(url.query)
    table = url.path.removeprefix("/inspect/tables/")
    trace_id = args.get("trace", [""])[0]
    if trace_id and not TRACE.fullmatch(trace_id):
        raise ValueError
    if url.path.startswith("/inspect/traces/"):
        key = url.path.removeprefix("/inspect/traces/")
        if not TRACE.fullmatch(key):
            raise ValueError
        return fetch_trace(key)
    page = int(args.get("page", ["0"])[0])
    if not 0 <= page <= 1000000:
        raise ValueError
    params = []
    if url.path == "/inspect/changes":
        columns = (
            "id",
            "at",
            "trace_id",
            "table_name",
            "action",
            "before_row",
            "after_row",
        )
        query = sql.SQL("select * from lab_inspect.changes ")
        if trace_id:
            query += sql.SQL("where trace_id = %s ")
            params.append(trace_id)
        query += sql.SQL("order by at desc, id desc")
    elif table in TABLES and url.path.startswith("/inspect/tables/"):
        columns = TABLES[table]
        query = sql.SQL("select {} from lab_inspect.{} order by {}").format(
            sql.SQL(", ").join(map(sql.Identifier, columns)),
            sql.Identifier(table),
            sql.Identifier(columns[0]),
        )
    else:
        raise KeyError
    with psycopg.connect(
        connect_timeout=INSPECT_TIMEOUT,
        options=f"-c statement_timeout={INSPECT_TIMEOUT * 1000}",
        row_factory=dict_row,
    ) as db:
        rows = db.execute(
            query + sql.SQL(" limit %s offset %s"),
            [*params, PAGE_SIZE + 1, page * PAGE_SIZE],
        ).fetchall()
    return {"columns": columns, "rows": rows[:PAGE_SIZE], "more": len(rows) > PAGE_SIZE}


def inspect_request(handler):
    try:
        data = read_model(handler.path)
    except KeyError:
        return handler.problem(404, "Unknown inspection view")
    except ValueError:
        return handler.problem(400, "Invalid inspection request")
    except (OSError, HTTPException, psycopg.Error):
        return handler.problem(503, "Live data source unavailable")
    handler.reply(200, json.dumps(data, default=str).encode(), "application/json")
