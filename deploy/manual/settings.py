"""Runtime settings for the local lab. Only PUBLIC_SETTINGS is sent to browsers."""

import os
from urllib.parse import urlsplit


def integer(name, default, minimum=1, maximum=None):
    value = int(os.getenv(name, str(default)))
    if value < minimum or (maximum is not None and value > maximum):
        raise ValueError(f"{name} is outside its supported range")
    return value


def tool_url(name, default):
    value = os.getenv(name, default).rstrip("/")
    parts = urlsplit(value)
    if (
        parts.scheme not in {"http", "https"}
        or not parts.hostname
        or parts.username
        or parts.password
        or parts.query
        or parts.fragment
    ):
        raise ValueError(
            f"{name} must be an HTTP(S) base URL without credentials, query or fragment"
        )
    return value


LAB_PORT = integer("LAB_PORT", 18080, maximum=65535)
PAGE_SIZE = integer("LAB_PAGE_SIZE", 20)
INSPECT_TIMEOUT = integer("LAB_INSPECT_TIMEOUT_SECONDS", 2)
GATEWAY_TIMEOUT = integer("LAB_GATEWAY_TIMEOUT_SECONDS", 8)
PUBLIC_SETTINGS = {
    "refreshMs": integer("LAB_REFRESH_MS", 1000),
    "requestTimeoutMs": integer("LAB_BROWSER_TIMEOUT_MS", 4000),
    "historyLimit": integer("LAB_HISTORY_LIMIT", 50),
    "retentionHours": integer("LAB_RETENTION_HOURS", 72, minimum=24),
    "grafanaUrl": tool_url("LAB_GRAFANA_URL", "http://localhost:13000"),
    "mailpitUrl": tool_url("LAB_MAILPIT_URL", "http://localhost:18025"),
}
