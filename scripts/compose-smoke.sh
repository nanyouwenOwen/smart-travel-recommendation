#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"
source "$root/scripts/lib/smoke-diagnostics.sh"
project="travel-smoke-${RANDOM}"
port="${FRONTEND_PORT:-5173}"
base="http://127.0.0.1:$port"
backup="$(mktemp --suffix=.sql.gz)"
response="$(mktemp)"
last_observation="not started"
current_stage="startup"

log() { printf '[%s] %s\n' "$(date -u +%FT%TZ)" "$*"; }
fail() { log "ERROR: $*" >&2; return 1; }

diagnostics() {
  log "Collecting bounded container diagnostics"
  docker compose -p "$project" ps -a 2>&1 || true
  local service
  for service in mysql backend frontend; do
    printf '%s: ' "$service"
    docker inspect -f 'status={{.State.Status}} health={{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' \
      "${project}-${service}-1" 2>&1 || true
    docker compose -p "$project" logs --tail=40 --no-color "$service" 2>&1 || true
  done
}

cleanup() {
  local status=$?
  trap - EXIT INT TERM
  if (( status != 0 )); then
    smoke_best_effort smoke_failure_summary "$status" "$current_stage" "$last_observation"
    smoke_best_effort diagnostics
  fi
  smoke_best_effort rm -f "$backup" "$response"
  smoke_best_effort docker compose -p "$project" down -v --remove-orphans >/dev/null 2>&1
  exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

wait_for_services() {
  local attempt service healthy
  for attempt in $(seq 1 90); do
    healthy=true
    for service in mysql backend frontend; do
      if [[ "$(docker inspect -f '{{.State.Health.Status}}' "${project}-${service}-1" 2>/dev/null || true)" != healthy ]]; then
        healthy=false
      fi
    done
    if [[ "$healthy" == true ]]; then return 0; fi
    last_observation="services not all healthy (attempt $attempt/90)"
    sleep 2
  done
  fail "Timed out waiting for services: $last_observation"
}

wait_for_trip() {
  local attempt body state
  for attempt in $(seq 1 30); do
    if body="$(curl -fsS --connect-timeout 3 --max-time 10 -H "$header" "$base/api/v1/trips/$trip" 2>/dev/null)"; then
      state="$(jq -er '.data.status' <<<"$body" 2>/dev/null || true)"
      last_observation="trip status=${state:-invalid-response}"
      case "$state" in
        READY) return 0 ;;
        DRAFT|GENERATING) ;;
        FAILED) fail "Trip entered FAILED state"; return 1 ;;
        *) fail "Trip returned malformed or unknown status: ${state:-missing}"; return 1 ;;
      esac
    else
      last_observation="trip request unavailable (attempt $attempt/30)"
    fi
    sleep 1
  done
  fail "Trip did not become READY: $last_observation"
}

wait_for_health() {
  local attempt status
  for attempt in $(seq 1 60); do
    status="$(curl -sS -o "$response" -w '%{http_code}' --connect-timeout 3 --max-time 10 "$base/api/v1/health" 2>/dev/null || printf 'transport-error')"
    last_observation="health HTTP $status"
    [[ "$status" == 2* ]] && return 0
    sleep 2
  done
  fail "Backend did not recover: $last_observation"
}

wait_for_login() {
  local attempt status access_token
  for attempt in $(seq 1 60); do
    : >"$response"
    status="$(curl -sS -o "$response" -w '%{http_code}' --connect-timeout 3 --max-time 10 \
      -H 'Content-Type: application/json' -d "{\"email\":\"$email\",\"password\":\"$password\"}" \
      "$base/api/v1/auth/login" 2>/dev/null || printf 'transport-error')"
    access_token=""
    if [[ "$status" == 2* ]]; then access_token="$(jq -er '.data.accessToken' "$response" 2>/dev/null || true)"; fi
    last_observation="login HTTP $status, token=$([[ -n "$access_token" ]] && printf present || printf absent)"
    [[ "$status" == 2* && -n "$access_token" ]] && return 0
    sleep 2
  done
  fail "Database-backed login did not recover: $last_observation"
}

current_stage="1/10 build and health"
last_observation="starting Compose services"
log "Stage 1/10: build, start, and wait for healthy services"
docker compose -p "$project" up --build -d
wait_for_services
for service in mysql backend frontend; do
  [[ "$(docker inspect -f '{{.State.Health.Status}}' "${project}-${service}-1")" == healthy ]] || fail "$service is not healthy"
done
log "Stage 1/10 passed"

current_stage="2/10 proxy and non-root"
last_observation="checking proxy health and container users"
log "Stage 2/10: proxy health and non-root containers"
curl -fsS --connect-timeout 3 --max-time 10 "$base/healthz" >/dev/null
curl -fsS --connect-timeout 3 --max-time 10 "$base/api/v1/health" >/dev/null
[[ "$(docker inspect -f '{{.Config.User}}' "${project}-backend-1")" != root ]] || fail "backend runs as root"
[[ "$(docker inspect -f '{{.Config.User}}' "${project}-frontend-1")" != root ]] || fail "frontend runs as root"
log "Stage 2/10 passed"

current_stage="3/10 trip generation"
last_observation="registering smoke user"
log "Stage 3/10: registration, location, and trip generation"
email="compose-${project}@example.com"
password='secure-pass-123'
auth="$(curl -fsS --connect-timeout 3 --max-time 15 -H 'Content-Type: application/json' \
  -d "{\"email\":\"$email\",\"password\":\"$password\",\"displayName\":\"Smoke\"}" "$base/api/v1/auth/register")"
token="$(jq -er '.data.accessToken | select(length > 0)' <<<"$auth")"
header="Authorization: Bearer $token"
location="$(curl -fsS --connect-timeout 3 --max-time 15 -H "$header" "$base/api/v1/locations/search?q=Shanghai" | jq -er '.data[0].locationId | select(length > 0)')"
start="$(date -u -d tomorrow +%F)"
trip="$(curl -fsS --connect-timeout 3 --max-time 15 -H "$header" -H 'Content-Type: application/json' -H 'Idempotency-Key: compose-trip-1' \
  -d "{\"destination\":\"Shanghai\",\"destinationLocationId\":\"$location\",\"startDate\":\"$start\",\"days\":2,\"budget\":{\"amount\":\"1000.00\",\"currency\":\"USD\"},\"travelers\":1,\"preferences\":[\"culture\"],\"timezone\":\"Asia/Shanghai\"}" \
  "$base/api/v1/trips" | jq -er '.data.id | select(length > 0)')"
wait_for_trip || fail "Trip generation failed: $last_observation"
log "Stage 3/10 passed"

current_stage="4/10 consultation SSE"
last_observation="creating consultation and waiting for done event"
log "Stage 4/10: consultation SSE terminal event"
conversation="$(curl -fsS --connect-timeout 3 --max-time 15 -H "$header" -H 'Content-Type: application/json' \
  -d "{\"title\":\"Smoke\",\"tripId\":\"$trip\"}" "$base/api/v1/conversations" | jq -er '.data.id | select(length > 0)')"
curl -fsSN --connect-timeout 3 --max-time 30 -H "$header" -H 'Content-Type: application/json' -H 'Idempotency-Key: compose-stream-1' \
  -d '{"content":"天气如何？"}' "$base/api/v1/conversations/$conversation/messages:stream" | grep -Eq 'event: ?done'
log "Stage 4/10 passed"

current_stage="5/10 backup and restore"
last_observation="dumping and restoring into a new database"
log "Stage 5/10: backup and restore into a new database"
docker compose -p "$project" exec -T mysql sh -c 'exec mysqldump --single-transaction -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' | gzip >"$backup"
gzip -t "$backup"
docker compose -p "$project" exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "CREATE DATABASE restore_verify"'
gzip -dc "$backup" | docker compose -p "$project" exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" restore_verify'
docker compose -p "$project" exec -T mysql sh -c "mysql -N -uroot -p\"\$MYSQL_ROOT_PASSWORD\" restore_verify -e \"SELECT COUNT(*) FROM users WHERE email='$email'; SELECT COUNT(*) FROM trips; SELECT COUNT(*) FROM flyway_schema_history\"" \
  | awk 'NR==1{if($1!=1)exit 1} NR==2{if($1<1)exit 1} NR==3{if($1<1)exit 1}'
log "Stage 5/10 passed"

current_stage="6/10 performance precondition"
last_observation="checking API health before performance test"
log "Stage 6/10: performance precondition"
curl -fsS --connect-timeout 3 --max-time 10 "$base/api/v1/health" >/dev/null || fail "Performance precondition health failed"
log "Stage 6/10 passed"

current_stage="7/10 performance thresholds"
last_observation="running k6 thresholds"
log "Stage 7/10: k6 performance thresholds"
performance_results="${PERF_RESULTS_DIR:-$root/perf-results}"
log "Pre-measurement bounded container resources"
docker stats --no-stream --format 'table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}' \
  "${project}-mysql-1" "${project}-backend-1" "${project}-frontend-1" || true
set +e
BASE_URL="$base" PERF_RESULTS_DIR="$performance_results" scripts/perf.sh
performance_status=$?
set -e
log "Post-measurement bounded container resources"
docker stats --no-stream --format 'table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}' \
  "${project}-mysql-1" "${project}-backend-1" "${project}-frontend-1" || true
if ((performance_status != 0)); then
  last_observation="k6 threshold failed; summary=perf-results/k6-summary.json"
  fail "Performance stage failed"
fi
log "Stage 7/10 passed"

current_stage="8/10 backend recovery"
last_observation="restarting backend"
log "Stage 8/10: backend restart recovery"
docker compose -p "$project" restart backend >/dev/null
wait_for_health
log "Stage 8/10 passed"

current_stage="9/10 MySQL restart"
last_observation="stopping and starting MySQL"
log "Stage 9/10: MySQL stop/start"
docker compose -p "$project" stop mysql >/dev/null
sleep 2
docker compose -p "$project" start mysql >/dev/null
log "Stage 9/10 passed"

current_stage="10/10 database login recovery"
last_observation="waiting for database-backed login"
log "Stage 10/10: database-backed login recovery"
wait_for_login
log "Stage 10/10 passed"
log "Compose smoke completed successfully"
