disable_mlock = true
api_addr = "https://vault:8200"
storage "file" {
  path = "/vault/file"
}
listener "tcp" {
  address = "0.0.0.0:8200"
  tls_cert_file = "/run/vault-tls/server.crt"
  tls_key_file = "/run/vault-tls/server.key"
}
