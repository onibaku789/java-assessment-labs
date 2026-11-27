#!/bin/sh
set -eu
# Both services run Liquibase on startup, including when an existing volume is upgraded.
attempts=0
until [ "$(psql -Atc "select to_regclass('directory.compartment') is not null and exists (select from information_schema.columns where table_schema='reservations' and table_name='booking' and column_name='pickup_max_attempts')")" = t ]; do
  attempts=$((attempts + 1))
  if [ "$attempts" -ge 180 ]; then
    echo "Timed out waiting for the Directory and Reservations migrations." >&2
    exit 1
  fi
  sleep 1
done
psql -X -v ON_ERROR_STOP=1 -f /audit/install.sql
