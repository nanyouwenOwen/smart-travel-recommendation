#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$root/scripts/lib/smoke-diagnostics.sh"

assert_contains() { [[ "$1" == *"$2"* ]] || { printf 'missing %q in %q\n' "$2" "$1" >&2; exit 1; }; }

github_output="$(GITHUB_ACTIONS=true smoke_failure_summary 1 '3/10 trip' $'bad%\r\n::value' 2>&1)"
[[ "$(wc -l <<<"$github_output")" == 1 ]]
assert_contains "$github_output" '::error title=Compose smoke failed::'
assert_contains "$github_output" 'stage=3/10 trip'
assert_contains "$github_output" 'bad%25%0D%0A%3A%3Avalue'
assert_contains "$github_output" 'exit=1'

local_output="$(GITHUB_ACTIONS=false smoke_failure_summary 7 'direct command' 'failed' 2>&1)"
assert_contains "$local_output" 'Compose smoke failed: stage=direct command; last=failed; exit=7'
[[ "$local_output" != *'::error'* ]]

run_harness() {
  local command="$1" injected_failure="${2:-none}"
  GITHUB_ACTIONS=true bash -c '
    set -euo pipefail
    source "$1"
    current_stage="test stage"; last_observation="safe observation"; injected_failure="$3"
    summary_test() { [[ "$injected_failure" != summary ]] || return 91; smoke_failure_summary "$@"; }
    diagnostics_test() { [[ "$injected_failure" != diagnostics ]] || return 92; return 0; }
    cleanup_resources_test() { [[ "$injected_failure" != cleanup ]] || return 93; return 0; }
    cleanup_test() {
      status=$?; trap - EXIT
      if ((status != 0)); then
        smoke_best_effort summary_test "$status" "$current_stage" "$last_observation"
        smoke_best_effort diagnostics_test
      fi
      smoke_best_effort cleanup_resources_test
      exit "$status"
    }
    trap cleanup_test EXIT
    trap "exit 130" INT
    trap "exit 143" TERM
    eval "$2"
  ' bash "$root/scripts/lib/smoke-diagnostics.sh" "$command" "$injected_failure"
}

for spec in 'false:1' 'printf x | false:1' 'exit 23:23' 'kill -INT $$:130' 'kill -TERM $$:143'; do
  command="${spec%:*}"; expected="${spec##*:}"; output="$(mktemp)"
  set +e; run_harness "$command" >"$output" 2>&1; status=$?; set -e
  [[ "$status" == "$expected" ]] || { echo "$command returned $status, expected $expected" >&2; rm -f "$output"; exit 1; }
  content="$(cat "$output")"; rm -f "$output"
  assert_contains "$content" "exit=$expected"
done

success_output="$(run_harness true 2>&1)"
[[ -z "$success_output" ]]
for injected in summary diagnostics cleanup; do
  set +e; run_harness 'exit 37' "$injected" >/dev/null 2>&1; status=$?; set -e
  [[ "$status" == 37 ]] || { echo "$injected failure replaced original status with $status" >&2; exit 1; }
done
echo 'smoke diagnostics tests: PASS'
