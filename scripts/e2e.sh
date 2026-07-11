#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"
started_mysql=false
if [[ -z "$(docker compose ps --status running -q mysql)" ]]; then
  docker compose up -d mysql
  started_mysql=true
fi
cleanup() {
  if [[ "$started_mysql" == true ]]; then docker compose stop mysql >/dev/null; fi
}
trap cleanup EXIT INT TERM
ready=false
for _ in $(seq 1 60); do
  if docker compose exec -T mysql mysqladmin ping -h localhost -uroot -plocal-root-password --silent >/dev/null 2>&1; then ready=true; break; fi
  sleep 1
done
if [[ "$ready" != true ]]; then echo "MySQL did not become ready within 60 seconds" >&2; exit 1; fi
cd frontend
npx playwright install chromium
npm run test:e2e
