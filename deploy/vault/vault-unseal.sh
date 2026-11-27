#!/bin/sh
# Single-share automatic unlock is specific to the local lab.
set -eu
while :; do
    if [ -s /run/unseal/share ] && ! vault status >/dev/null 2>&1; then
        vault write sys/unseal key=@/run/unseal/share >/dev/null 2>&1 || true
    fi
    sleep 3
done
