#!/usr/bin/env bash
set -euo pipefail
out="${1:?usage: backup.sh OUTPUT.sql.gz}"
umask 077
docker compose exec -T mysql sh -c 'exec mysqldump --single-transaction --routines --triggers -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' | gzip -9 > "$out"
gzip -t "$out"
sha256sum "$out" > "$out.sha256"
