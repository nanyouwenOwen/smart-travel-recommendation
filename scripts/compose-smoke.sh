#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; cd "$root"
project="travel-smoke-${RANDOM}"; port="${FRONTEND_PORT:-5173}"; base="http://127.0.0.1:$port"
backup="$(mktemp --suffix=.sql.gz)"
cleanup(){ rm -f "$backup"; docker compose -p "$project" down -v --remove-orphans >/dev/null 2>&1 || true; }
trap cleanup EXIT INT TERM
docker compose -p "$project" up --build -d
for _ in $(seq 1 90); do
  healthy=true; for service in mysql backend frontend; do [[ "$(docker inspect -f '{{.State.Health.Status}}' "${project}-${service}-1" 2>/dev/null || true)" == healthy ]] || healthy=false; done
  [[ "$healthy" == true ]] && break; sleep 2
done
for service in mysql backend frontend; do [[ "$(docker inspect -f '{{.State.Health.Status}}' "${project}-${service}-1")" == healthy ]]; done
curl -fsS "$base/healthz" >/dev/null; curl -fsS "$base/api/v1/health" >/dev/null
test "$(docker inspect -f '{{.Config.User}}' "${project}-backend-1")" != root
test "$(docker inspect -f '{{.Config.User}}' "${project}-frontend-1")" != root
email="compose-${project}@example.com"; password='secure-pass-123'
auth="$(curl -fsS -H 'Content-Type: application/json' -d "{\"email\":\"$email\",\"password\":\"$password\",\"displayName\":\"Smoke\"}" "$base/api/v1/auth/register")"
token="$(jq -r .data.accessToken <<<"$auth")"; header="Authorization: Bearer $token"
location="$(curl -fsS -H "$header" "$base/api/v1/locations/search?q=Shanghai" | jq -r .data[0].locationId)"
start="$(date -u -d tomorrow +%F)"
trip="$(curl -fsS -H "$header" -H 'Content-Type: application/json' -H 'Idempotency-Key: compose-trip-1' -d "{\"destination\":\"Shanghai\",\"destinationLocationId\":\"$location\",\"startDate\":\"$start\",\"days\":2,\"budget\":{\"amount\":\"1000.00\",\"currency\":\"USD\"},\"travelers\":1,\"preferences\":[\"culture\"],\"timezone\":\"Asia/Shanghai\"}" "$base/api/v1/trips" | jq -r .data.id)"
for _ in $(seq 1 30); do state="$(curl -fsS -H "$header" "$base/api/v1/trips/$trip" | jq -r .data.status)"; [[ "$state" == READY ]] && break; sleep 1; done; [[ "$state" == READY ]]
conversation="$(curl -fsS -H "$header" -H 'Content-Type: application/json' -d "{\"title\":\"Smoke\",\"tripId\":\"$trip\"}" "$base/api/v1/conversations" | jq -r .data.id)"
curl -fsSN -H "$header" -H 'Content-Type: application/json' -H 'Idempotency-Key: compose-stream-1' -d '{"content":"天气如何？"}' "$base/api/v1/conversations/$conversation/messages:stream" | grep -q 'event: done'
docker compose -p "$project" exec -T mysql sh -c 'exec mysqldump --single-transaction -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' | gzip > "$backup"; gzip -t "$backup"
docker compose -p "$project" exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "CREATE DATABASE restore_verify"'
gzip -dc "$backup" | docker compose -p "$project" exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" restore_verify'
docker compose -p "$project" exec -T mysql sh -c "mysql -N -uroot -p\"\$MYSQL_ROOT_PASSWORD\" restore_verify -e \"SELECT COUNT(*) FROM users WHERE email='$email'; SELECT COUNT(*) FROM trips; SELECT COUNT(*) FROM flyway_schema_history\"" | awk 'NR==1{if($1!=1)exit 1} NR==2{if($1<1)exit 1} NR==3{if($1<1)exit 1}'
BASE_URL="$base" scripts/perf.sh
docker compose -p "$project" restart backend >/dev/null
for _ in $(seq 1 60); do curl -sf "$base/api/v1/health" >/dev/null && break; sleep 2; done
docker compose -p "$project" stop mysql >/dev/null; sleep 2; docker compose -p "$project" start mysql >/dev/null
for _ in $(seq 1 60); do curl -sf -H 'Content-Type: application/json' -d "{\"email\":\"$email\",\"password\":\"$password\"}" "$base/api/v1/auth/login" >/dev/null && exit 0; sleep 2; done
exit 1
