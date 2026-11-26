#!/usr/bin/env bash
set -eu
cd "$(dirname "$0")"
exec java -jar target/shipment-worker-1.0.0.jar "$@"
