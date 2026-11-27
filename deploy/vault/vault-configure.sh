#!/bin/sh
# Run with a provisioning identity. Application roles can use existing Transit keys only.
set -eu
mode=$1
if ! vault secrets list | grep -q '^transit/ '; then vault secrets enable transit; fi
for key in pickup-mail pickup-verifier command-fingerprint; do
    if vault read "transit/keys/$key" >/dev/null 2>&1; then continue; fi
    if [ "$key" = pickup-mail ]; then
        vault write "transit/keys/$key" type=aes256-gcm96
    else
        vault write "transit/keys/$key" type=hmac key_size=32
    fi
done
vault policy write reservations - <<'POLICY'
path "transit/encrypt/pickup-mail" { capabilities = ["update"] }
path "transit/hmac/pickup-verifier" { capabilities = ["update"] }
path "transit/hmac/command-fingerprint" { capabilities = ["update"] }
path "transit/verify/pickup-verifier" { capabilities = ["update"] }
path "transit/verify/command-fingerprint" { capabilities = ["update"] }
path "transit/verify/legacy-mac" { capabilities = ["update"] }
POLICY
vault policy write notifications - <<'POLICY'
path "transit/decrypt/pickup-mail" { capabilities = ["update"] }
path "transit/decrypt/legacy-mail" { capabilities = ["update"] }
POLICY
if ! vault auth list | grep -q "^$mode/ "; then vault auth enable "$mode"; fi
if [ "$mode" = kubernetes ]; then
    vault write auth/kubernetes/config kubernetes_host=https://kubernetes.default.svc:443
fi
for role in reservations notifications; do
    if [ "$mode" = kubernetes ]; then
        vault write "auth/kubernetes/role/$role" bound_service_account_names="$role" \
            bound_service_account_namespaces=lockers audience=vault token_policies="$role" token_ttl=1h
    else
        vault write "auth/approle/role/$role" token_policies="$role" token_ttl=1h token_max_ttl=4h
    fi
done
