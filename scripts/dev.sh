#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "$ROOT_DIR"
docker compose up -d mysql

cleanup() {
  jobs -pr | xargs -r kill
}
trap cleanup EXIT INT TERM

mvn -f backend/pom.xml spring-boot:run &
npm --prefix frontend run dev -- --host 0.0.0.0 &

wait
