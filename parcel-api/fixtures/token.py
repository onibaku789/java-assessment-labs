"""Issue a short-lived, synthetic RS256 token for the loopback resource server."""
import base64
import json
import os
from pathlib import Path
import subprocess
import sys
import time

scope = {"reader": "parcel.read", "observer": "parcel.observe"}[sys.argv[1]]
keys = Path(__file__).resolve().parents[1] / ".lab" / "keys"
keys.mkdir(parents=True, exist_ok=True)
private = keys / "private.pem"
public = keys / "public.pem"
if not private.exists():
    os.umask(0o077)
    subprocess.run(["openssl", "genrsa", "-out", str(private), "2048"], check=True)
subprocess.run(["openssl", "rsa", "-in", str(private), "-pubout", "-out", str(public)],
               check=True, stderr=subprocess.DEVNULL)


def encode(data):
    return base64.urlsafe_b64encode(data).rstrip(b"=")


now = int(time.time())
claims = {"iss": "urn:parcel:study", "sub": "test-user", "aud": "parcel-api",
          "iat": now, "exp": now + 300, "scope": scope}
parts = [encode(json.dumps(obj, separators=(",", ":")).encode())
         for obj in ({"alg": "RS256", "typ": "JWT"}, claims)]
payload = b".".join(parts)
sig = subprocess.run(["openssl", "dgst", "-sha256", "-sign", str(private)],
                     input=payload, capture_output=True, check=True).stdout
print((payload + b"." + encode(sig)).decode())
