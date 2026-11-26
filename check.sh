#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

usage() {
  cat <<'HELP'
Usage: ./check.sh WEEK
  1   Create, retrieve and validate parcels
  2   JWT authorization and CORS
  3   SOAP carrier, unknown parcels and timeouts (Python 3)
  4   Concurrent duplicates, interruption and failed writes (Docker)
  5   Timed restores and data integrity (Docker)
  6   Configuration and readiness on your local kind release
  7   CPU scale-out and recovery on your local kind release
  8   PostgreSQL query results and buffer use (Docker)
Start with ./check.sh 1 and the Week 1 checkbox in README.md.
HELP
}
case "${1:-}" in
  ""|-h|--help) usage; exit 0;;
esac
[[ $# == 1 ]] || { usage >&2; exit 2; }
case "$1" in
  1) project=parcel-api; suite=Week01ParcelContract;;
  2) project=parcel-api; suite=Week02SecurityContract;;
  3) project=parcel-api; suite=Week03CarrierContract;;
  4) project=shipment-worker; suite=Week04DeliveryContract;;
  5) project=shipment-worker; suite=Week05RestoreContract;;
  6|7)
    cd release-lab
    if [[ ! -x .lab/bin/kubectl || ! -x .lab/bin/helm || ! -f .lab/cluster-owned ]]; then
      echo 'First run the release-lab quick start in README.md (tools, cluster, deploy).' >&2
      exit 2
    fi
    source ./tools.sh
    exec python3 check_release.py "$1";;
  8) project=parcel-api; suite=Week08SqlContract;;
  *) usage >&2; exit 2;;
esac
cd "$project"
echo "Week $1: $project/src/test/java/study/parcel/$suite.java"
exec ../toolchain.sh mvn -B -ntp "-Dtest=$suite" test
