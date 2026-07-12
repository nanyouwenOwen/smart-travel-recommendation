#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/bin" "$tmp/results"

cat >"$tmp/bin/docker" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
host_results=''
while (($#)); do
  if [[ "$1" == -v ]]; then host_results="${2%%:*}"; shift 2; else shift; fi
done
case "${MOCK_SUMMARY:-valid}" in
valid|missing-endpoint|missing-iterations|missing-distribution|zero-sample)
  mkdir -p "$host_results"
  cat >"$host_results/k6-summary.json" <<'JSON'
{"schemaVersion":1,"k6Version":"0.57.0","metrics":{"http_reqs{phase:measurement}":{"values":{"count":100}},"iterations{phase:measurement}":{"values":{"count":50}},"http_req_failed{phase:measurement}":{"values":{"rate":0}},"http_req_duration{phase:measurement}":{"values":{"avg":10,"med":10,"p(90)":15,"p(95)":20,"max":30}},"checks{phase:measurement}":{"values":{"rate":1}},"http_req_failed{phase:measurement,endpoint:backend}":{"values":{"rate":0}},"checks{phase:measurement,endpoint:backend}":{"values":{"rate":1}},"http_req_duration{phase:measurement,endpoint:backend}":{"values":{"p(95)":20}},"http_req_failed{phase:measurement,endpoint:frontend}":{"values":{"rate":0}},"checks{phase:measurement,endpoint:frontend}":{"values":{"rate":1}},"http_req_duration{phase:measurement,endpoint:frontend}":{"values":{"p(95)":20}}}}
JSON
  if [[ "${MOCK_SUMMARY:-}" == zero-sample ]]; then
    sed -i 's/"count":100/"count":0/' "$host_results/k6-summary.json"
  fi
  if [[ "${MOCK_SUMMARY:-}" == missing-endpoint ]]; then
    sed -i 's/"http_req_failed{phase:measurement,endpoint:frontend}"/"removed-frontend"/' "$host_results/k6-summary.json"
  elif [[ "${MOCK_SUMMARY:-}" == missing-iterations ]]; then
    sed -i 's/"iterations{phase:measurement}"/"removed-iterations"/' "$host_results/k6-summary.json"
  elif [[ "${MOCK_SUMMARY:-}" == missing-distribution ]]; then
    sed -i 's/"p(90)":15,//' "$host_results/k6-summary.json"
  fi
  ;;
malformed)
  echo '{' >"$host_results/k6-summary.json"
  ;;
esac
if [[ "${MOCK_SLEEP:-0}" == 1 ]]; then
  trap 'exit 130' INT
  trap 'exit 143' TERM
  while true; do sleep 1; done
fi
exit "${MOCK_K6_STATUS:-0}"
MOCK
chmod +x "$tmp/bin/docker"

run_perf() {
  PATH="$tmp/bin:$PATH" PERF_RESULTS_DIR="$tmp/results" scripts/perf.sh >/dev/null 2>&1
}
expect_status() {
  local expected="$1"; shift
  rm -f "$tmp/results/k6-summary.json"
  set +e; "$@" >/dev/null 2>&1; actual=$?; set -e
  [[ "$actual" == "$expected" ]] || { echo "expected $expected, got $actual" >&2; exit 1; }
}

run_perf
test -s "$tmp/results/k6-summary.json"
expect_status 70 env PATH="$tmp/bin:$PATH" PERF_RESULTS_DIR="$tmp/results" MOCK_SUMMARY=missing scripts/perf.sh
expect_status 70 env PATH="$tmp/bin:$PATH" PERF_RESULTS_DIR="$tmp/results" MOCK_SUMMARY=malformed scripts/perf.sh
expect_status 70 env PATH="$tmp/bin:$PATH" PERF_RESULTS_DIR="$tmp/results" MOCK_SUMMARY=zero-sample scripts/perf.sh
expect_status 70 env PATH="$tmp/bin:$PATH" PERF_RESULTS_DIR="$tmp/results" MOCK_SUMMARY=missing-endpoint scripts/perf.sh
expect_status 70 env PATH="$tmp/bin:$PATH" PERF_RESULTS_DIR="$tmp/results" MOCK_SUMMARY=missing-iterations scripts/perf.sh
expect_status 70 env PATH="$tmp/bin:$PATH" PERF_RESULTS_DIR="$tmp/results" MOCK_SUMMARY=missing-distribution scripts/perf.sh
expect_status 99 env PATH="$tmp/bin:$PATH" PERF_RESULTS_DIR="$tmp/results" MOCK_K6_STATUS=99 scripts/perf.sh
expect_status 99 env PATH="$tmp/bin:$PATH" PERF_RESULTS_DIR="$tmp/results" MOCK_K6_STATUS=99 MOCK_SUMMARY=missing scripts/perf.sh
expect_status 130 env PATH="$tmp/bin:$PATH" PERF_RESULTS_DIR="$tmp/results" MOCK_K6_STATUS=130 scripts/perf.sh
expect_status 143 env PATH="$tmp/bin:$PATH" PERF_RESULTS_DIR="$tmp/results" MOCK_K6_STATUS=143 scripts/perf.sh

rm -f "$tmp/results/k6-summary.json"
PATH="$tmp/bin:$PATH" PERF_RESULTS_DIR="$tmp/results" MOCK_SLEEP=1 scripts/perf.sh >/dev/null 2>&1 &
perf_pid=$!
sleep 0.2
kill -TERM "$perf_pid"
set +e; wait "$perf_pid"; signal_status=$?; set -e
[[ "$signal_status" == 143 && -s "$tmp/results/k6-summary.json" ]]

expect_status 64 env PATH="$tmp/bin:$PATH" PERF_RESULTS_DIR="$root" scripts/perf.sh
run_perf
[[ "$(stat -c %a "$tmp/results")" == 755 ]]
[[ "$(stat -c %a "$tmp/results/k6-summary.json")" == 666 ]]
chmod 0755 "$tmp"
[[ "$(( $(stat -c %a "$tmp/results/k6-summary.json") % 10 ))" -ge 2 ]]

secret_output="$tmp/secret-output"
PATH="$tmp/bin:$PATH" PERF_RESULTS_DIR="$tmp/results" TEST_SECRET_VALUE=must-not-appear scripts/perf.sh >"$secret_output" 2>&1
! grep -q 'must-not-appear' "$secret_output"

script="$(<tests/performance/smoke.js)"
[[ "$script" == *"vus: 10"* && "$script" == *"duration: '30s'"* ]]
[[ "$script" == *"'http_req_failed{phase:measurement}': ['rate<0.01']"* ]]
[[ "$script" == *"'http_req_duration{phase:measurement}': ['p(95)<2000']"* ]]
[[ "$script" == *"endpoint: 'backend'"* && "$script" == *"endpoint: 'frontend'"* ]]
[[ "$(grep -c "exec: 'measurement'" tests/performance/smoke.js)" == 1 ]]
[[ "$script" == *"export function setup()"* && "$script" == *"attempt <= 5"* ]]
[[ "$script" == *"fail(\`warmup failed"* ]]
[[ "$script" == *"value.status === 200"* && "$script" == *"value.body.includes('智能旅游助手')"* ]]
grep -q 'if: always()' .github/workflows/ci.yml
grep -q 'perf-results/k6-summary.json' .github/workflows/ci.yml
perf_line="$(grep -n 'scripts/perf.sh' scripts/compose-smoke.sh | cut -d: -f1)"
post_line="$(grep -n 'Post-measurement bounded' scripts/compose-smoke.sh | cut -d: -f1)"
failure_line="$(grep -n 'performance_status != 0' scripts/compose-smoke.sh | cut -d: -f1)"
((perf_line < post_line && post_line < failure_line))
echo 'performance gate contract tests: PASS'
