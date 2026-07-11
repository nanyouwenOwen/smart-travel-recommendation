#!/usr/bin/env bash
set -euo pipefail
input="${1:?usage: CONFIRM_RESTORE=yes restore.sh BACKUP.sql.gz NEW_DATABASE}"
target="${2:?a separate target database name is required}"
[[ "$target" =~ ^[A-Za-z0-9_]+$ ]] || { echo 'Invalid target database name.' >&2; exit 2; }
[[ "$target" != "${MYSQL_DATABASE:-travel_assistant}" ]] || { echo 'Refusing to overwrite the active database.' >&2; exit 2; }
[[ "${CONFIRM_RESTORE:-}" == yes ]] || { echo 'Set CONFIRM_RESTORE=yes; restore creates the target database.' >&2; exit 2; }
sha256sum -c "$input.sha256"
if docker compose exec -T mysql sh -c "mysql -N -uroot -p\"\$MYSQL_ROOT_PASSWORD\" -e \"SELECT SCHEMA_NAME FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME='$target'\"" | grep -qx "$target"; then
  echo 'Refusing to restore into an existing database.' >&2
  exit 2
fi
docker compose exec -T mysql sh -c "mysql -uroot -p\"\$MYSQL_ROOT_PASSWORD\" -e 'CREATE DATABASE \`$target\`'"
gzip -dc "$input" | docker compose exec -T mysql sh -c "exec mysql -uroot -p\"\$MYSQL_ROOT_PASSWORD\" '$target'"
