#!/usr/bin/env python3
"""Issue a short-lived JWT for a synthetic locker customer or courier."""

import argparse
import base64
import json
import os
from pathlib import Path
import subprocess
import time


def encode(data):
    return base64.urlsafe_b64encode(data).rstrip(b"=")


def token(role, subject, key):
    now = int(time.time())
    lifetime = int(os.environ.get("LAB_TOKEN_TTL_SECONDS", 600))
    if lifetime < 1:
        raise ValueError("LAB_TOKEN_TTL_SECONDS must be positive")
    claims = dict(
        iss=os.getenv("LOCKER_JWT_ISSUER", "urn:locker:lab"),
        aud=os.getenv("LOCKER_JWT_AUDIENCE", "locker-api"),
        sub=subject,
        iat=now,
        exp=now + lifetime,
        scope={
            "customer": "locker.read locker.reserve",
            "courier": "locker.read locker.deposit",
        }[role],
        email=subject + "@example.test",
        email_verified=True,
    )
    payload = b".".join(
        encode(json.dumps(obj).encode())
        for obj in ({"alg": "RS256", "typ": "JWT"}, claims)
    )
    signature = subprocess.run(
        ["openssl", "dgst", "-sha256", "-sign", str(key)],
        input=payload,
        capture_output=True,
        check=True,
    ).stdout
    return (payload + b"." + encode(signature)).decode()


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("role", choices=["customer", "courier"])
    parser.add_argument("subject")
    args = parser.parse_args()
    print(
        token(
            args.role,
            args.subject,
            Path(__file__).resolve().parents[1] / ".lab/keys/private.pem",
        )
    )
