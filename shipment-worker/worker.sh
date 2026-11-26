#!/usr/bin/env bash
set -eu
cd "$(dirname "$0")"
exec ../toolchain.sh java -jar target/shipment-worker-1.0.0.jar "$@"
