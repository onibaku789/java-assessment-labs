#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
source ./tools.sh
name=parcel-study
version=v1.37.3
case "${1:-}" in
  up)
    if [[ -f .lab/cluster-owned ]] && current=$(kubectl get node "$name-control-plane" \
        -o jsonpath='{.status.nodeInfo.kubeletVersion}' 2>/dev/null); then
      if [[ "$current" == "$version" ]]; then exit 0; fi
      echo "Existing lab uses $current. Save your evidence, then run ./cluster.sh down and ./cluster.sh up for $version." >&2
      exit 1
    fi
    if kind get clusters | grep -Fxq "$name"; then
      echo "Cluster name already in use. Check the original workspace." >&2
      exit 1
    fi
    image="parcel-study-node:$version"
    if ! docker image inspect "$image" >/dev/null 2>&1; then
      kind build node-image --type release "$version" --image "$image"
    fi
    kind create cluster --name "$name" --image "$image" \
      --kubeconfig "$KUBECONFIG" --wait 150s
    touch .lab/cluster-owned
    ;;
  metrics)
    [[ -f .lab/cluster-owned ]]
    curl -fsSL --retry 2 https://github.com/kubernetes-sigs/metrics-server/releases/download/v0.9.1/components.yaml -o .lab/metrics.yaml
    kubectl apply -f .lab/metrics.yaml
    # kind's development kubelets use locally issued serving certificates.
    if ! kubectl -n kube-system get deploy metrics-server -o yaml | grep -q -- --kubelet-insecure-tls; then
      kubectl -n kube-system patch deploy metrics-server --type=json \
        -p '[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-insecure-tls"}]'
    fi
    kubectl -n kube-system rollout status deploy/metrics-server --timeout=180s
    ;;
  down)
    [[ -f .lab/cluster-owned ]] || { echo "No owned cluster to remove"; exit 0; }
    kind delete cluster --name "$name" --kubeconfig "$KUBECONFIG"
    rm -f .lab/cluster-owned
    ;;
  *) echo "Use up, metrics or down" >&2; exit 2;;
esac
