#!/usr/bin/env bash
set -euo pipefail
base="${BASE_URL:-http://127.0.0.1:5173}"
docker compose restart backend
for _ in $(seq 1 60); do curl -sf "$base/api/v1/health" >/dev/null && { echo recovered; exit 0; }; sleep 2; done
echo 'backend did not recover' >&2; exit 1
