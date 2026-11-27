"""Serve the local browser lab and attach an identity only to known locker gateways."""

import json
import re
from http.client import HTTPConnection, HTTPException
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit

from inspect_lab import inspect_request
from signing import token
from settings import LAB_PORT, GATEWAY_TIMEOUT

WEB = Path(__file__).parent / "web"
ASSETS = {
    "/": ("index.html", "text/html"),
    "/flow": ("index.html", "text/html"),
    "/flow.js": ("flow.js", "text/javascript"),
    "/diagram.svg": ("diagram.svg", "image/svg+xml"),
    "/flow.css": ("flow.css", "text/css"),
    "/app.js": ("app.js", "text/javascript"),
    "/config.js": ("config.js", "text/javascript"),
    "/wire.js": ("wire.js", "text/javascript"),
    "/style.css": ("style.css", "text/css"),
}
HOSTS = {f"localhost:{LAB_PORT}", f"127.0.0.1:{LAB_PORT}"}
if LAB_PORT == 80:
    HOSTS.update({"localhost", "127.0.0.1"})
ACTORS = {"customer": "lab-customer", "courier": "lab-courier"}
ROUTES = {
    "rest": {
        "GET": r"/v1/(?:compartments|reservations/[0-9a-fA-F-]{36})",
        "POST": r"/v1/reservations(?:/[0-9a-fA-F-]{36}/(?:deposit|cancel|reissue|collect))?",
    },
    "graphql": {"POST": r"/graphql"},
    "soap": {"POST": r"/soap/lockers"},
}


class Lab(BaseHTTPRequestHandler):
    def log_message(self, *args):
        # Upstream applications record the operation. Never print browser input or headers.
        pass

    def reply(self, status, content, media, location=None):
        self.send_response(status)
        self.send_header("Content-Type", media)
        self.send_header("Content-Length", str(len(content)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header(
            "Content-Security-Policy",
            "default-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'",
        )
        if location:
            self.send_header("Location", location)
        self.end_headers()
        self.wfile.write(content)

    def problem(self, status, message):
        self.reply(
            status, json.dumps({"message": message}).encode(), "application/json"
        )

    def do_GET(self):
        if self.headers.get("Host") not in HOSTS:
            return self.problem(403, "Unknown lab host")
        if self.path.startswith("/inspect/"):
            origin = self.headers.get("Origin")
            if origin is not None and origin != "http://" + self.headers["Host"]:
                return self.problem(403, "Local lab requests only")
            return inspect_request(self)
        if self.path in ASSETS:
            name, media = ASSETS[self.path]
            return self.reply(200, (WEB / name).read_bytes(), media + "; charset=utf-8")
        self.forward()

    def do_POST(self):
        self.forward()

    def forward(self):
        host = self.headers.get("Host")
        origin = self.headers.get("Origin")
        if host not in HOSTS or (origin is not None and origin != "http://" + host):
            return self.problem(403, "Local lab requests only")
        role = self.headers.get("X-Lab-Role", "")
        if role not in ACTORS:
            return self.problem(403, "Select a lab identity")
        match = re.fullmatch(r"/gateway/(rest|graphql|soap)(/[^#]*)", self.path)
        if not match:
            return self.problem(404, "Unknown gateway")
        service, path = match.groups()
        allowed = ROUTES[service].get(self.command)
        if not allowed or not re.fullmatch(allowed, urlsplit(path).path):
            return self.problem(404, "Unknown operation")
        try:
            size = int(self.headers.get("Content-Length", 0))
        except ValueError:
            return self.problem(400, "Invalid content length")
        if not 0 <= size <= 32768 or self.headers.get("Transfer-Encoding"):
            return self.problem(413, "Request too large")
        body = self.rfile.read(size) if size else None
        headers = {
            k: self.headers[k]
            for k in ("Content-Type", "SOAPAction", "Idempotency-Key")
            if self.headers.get(k)
        }
        parent = self.headers.get("traceparent", "")
        if re.fullmatch(r"00-[0-9a-f]{32}-[0-9a-f]{16}-01", parent):
            headers["traceparent"] = parent
        headers["Authorization"] = "Bearer " + token(
            role, ACTORS[role], Path("/run/lab/private.pem")
        )
        upstream = HTTPConnection(service, 8080, timeout=GATEWAY_TIMEOUT)
        try:
            upstream.request(self.command, path, body, headers)
            result = upstream.getresponse()
            self.reply(
                result.status,
                result.read(),
                result.getheader("Content-Type", "text/plain"),
                result.getheader("Location"),
            )
        except (OSError, HTTPException):
            self.problem(502, "Gateway unavailable")
        finally:
            upstream.close()


if __name__ == "__main__":
    ThreadingHTTPServer(("0.0.0.0", 8080), Lab).serve_forever()
