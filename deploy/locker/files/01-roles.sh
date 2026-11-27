#!/bin/sh
set -eu
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
    -v directory_password="$DIRECTORY_PASSWORD" -v reservations_password="$RESERVATIONS_PASSWORD" <<'SQL'
CREATE ROLE directory LOGIN PASSWORD :'directory_password';
CREATE ROLE reservations LOGIN PASSWORD :'reservations_password';
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
CREATE SCHEMA directory AUTHORIZATION directory;
CREATE SCHEMA reservations AUTHORIZATION reservations;
SQL
