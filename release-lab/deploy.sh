#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
source ./tools.sh
[[ -f .lab/cluster-owned ]]
docker build -t parcel-study-release:1 .
kind load docker-image parcel-study-release:1 --name parcel-study
helm upgrade --install parcel ./chart --wait --timeout 3m
kubectl run parcel-client --image=python:3.15.2-alpine --image-pull-policy=IfNotPresent \
  --restart=Never --dry-run=client -o yaml -- sleep infinity | kubectl apply -f -
kubectl wait --for=condition=Ready pod/parcel-client --timeout=90s
