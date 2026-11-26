#!/usr/bin/env bash
set -eu
cd "$(dirname "$0")"
source ./tools.sh
[[ -f .lab/cluster-owned ]]
kubectl exec -i parcel-client -- python - "$@" < load.py
