#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
base="${BASE_URL:-http://127.0.0.1:5173}"
results="${PERF_RESULTS_DIR:-$(mktemp -d)}"
mkdir -p "$results"
results="$(realpath "$results")"
case "$results" in
  "$root/perf-results"|/tmp/*) ;;
  *) printf 'unsafe performance results directory\n' >&2; exit 64 ;;
esac
chmod 0755 "$results"
summary="$results/k6-summary.json"
rm -f "$summary"
install -m 0666 /dev/null "$summary"

docker_pid=''
signal_status=0
forward_signal() {
  local signal="$1" status="$2"
  signal_status="$status"
  [[ -z "$docker_pid" ]] || kill -"$signal" "$docker_pid" 2>/dev/null || true
}
trap 'forward_signal INT 130' INT
trap 'forward_signal TERM 143' TERM

set +e
docker run --rm --network host -i \
  -e BASE_URL="$base" -e SUMMARY_PATH=/results/k6-summary.json \
  -v "$results:/results" \
  grafana/k6:0.57.0 run - <"$root/tests/performance/smoke.js" &
docker_pid=$!
wait "$docker_pid"
k6_status=$?
set -e
trap - INT TERM
if ((signal_status != 0)); then k6_status="$signal_status"; fi

summary_status=0
if [[ ! -s "$summary" ]] || ! jq -e '
  . as $root |
  $root.schemaVersion == 1 and $root.k6Version == "0.57.0" and
  ($root.metrics["http_reqs{phase:measurement}"].values.count // 0) > 0 and
  ($root.metrics["iterations{phase:measurement}"].values.count // 0) > 0 and
  ($root.metrics["http_req_failed{phase:measurement}"].values.rate | type) == "number" and
  ($root.metrics["checks{phase:measurement}"].values.rate | type) == "number" and
  (["avg","med","p(90)","p(95)","max"] | all(. as $key |
    ($root.metrics["http_req_duration{phase:measurement}"].values[$key] | type) == "number")) and
  (["backend","frontend"] | all(. as $endpoint |
    ($root.metrics["http_req_failed{phase:measurement,endpoint:" + $endpoint + "}"].values.rate | type) == "number" and
    ($root.metrics["checks{phase:measurement,endpoint:" + $endpoint + "}"].values.rate | type) == "number" and
    ($root.metrics["http_req_duration{phase:measurement,endpoint:" + $endpoint + "}"].values["p(95)"] | type) == "number"))
' "$summary" >/dev/null 2>&1; then
  summary_status=70
  printf 'performance summary invalid or missing: %s\n' "$summary" >&2
else
  jq -r '
    "performance summary: requests=\(.metrics["http_reqs{phase:measurement}"].values.count) " +
    "failed_rate=\(.metrics["http_req_failed{phase:measurement}"].values.rate) " +
    "p95_ms=\(.metrics["http_req_duration{phase:measurement}"].values["p(95)"]) " +
    "checks_rate=\(.metrics["checks{phase:measurement}"].values.rate)"
  ' "$summary"
fi

if ((k6_status != 0)); then exit "$k6_status"; fi
exit "$summary_status"
