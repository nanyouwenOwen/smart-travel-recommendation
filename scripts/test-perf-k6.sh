#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
k6="${K6_BIN:-$(command -v k6 || true)}"
[[ -x "$k6" ]] || { echo 'K6_BIN pointing to k6 0.57.0 is required' >&2; exit 2; }
"$k6" version | grep -q 'v0.57.0'
tmp="$(mktemp -d)"
server_pid=''
cleanup() { [[ -z "$server_pid" ]] || kill -TERM "$server_pid" 2>/dev/null || true; rm -rf "$tmp"; }
trap cleanup EXIT

run_case() {
  local scenario="$1" expected_phase="$2" port=$((19000 + RANDOM % 1000))
  SCENARIO="$scenario" PORT="$port" node "$root/scripts/perf-fixture-server.mjs" &
  server_pid=$!
  for _ in $(seq 1 30); do curl -fsS "http://127.0.0.1:$port/ready" >/dev/null 2>&1 && break; sleep 0.1; done
  summary="$tmp/$scenario.json"; : >"$summary"
  set +e
  BASE_URL="http://127.0.0.1:$port" SUMMARY_PATH="$summary" "$k6" run --quiet "$root/tests/performance/smoke.js" >/dev/null 2>&1
  status=$?
  set -e
  counts="$(curl -fsS "http://127.0.0.1:$port/counts")"
  kill -TERM "$server_pid"; wait "$server_pid" || true; server_pid=''
  [[ "$status" != 0 && -s "$summary" ]]
  if [[ "$expected_phase" == warmup ]]; then
    jq -e '.backend <= 1 and .frontend <= 1' <<<"$counts" >/dev/null
    jq -e '(.metrics["http_reqs{phase:measurement}"].values.count // 0) == 0' "$summary" >/dev/null
  else
    jq -e '.backend > 5 and .frontend > 5' <<<"$counts" >/dev/null
    jq -e '(.metrics["http_reqs{phase:measurement}"].values.count // 0) > 0' "$summary" >/dev/null
  fi
  echo "$scenario: PASS"
}

run_case warmup-backend-status warmup
run_case warmup-frontend-status warmup
run_case warmup-frontend-content warmup
run_case measurement-transport measurement
run_case measurement-http measurement
run_case measurement-content measurement
run_case measurement-p95 measurement
echo 'real k6 performance failure matrix: PASS'
