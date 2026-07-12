#!/usr/bin/env bash

smoke_annotation_escape() {
  local value="${1-}"
  value="${value//'%'/'%25'}"
  value="${value//$'\r'/'%0D'}"
  value="${value//$'\n'/'%0A'}"
  value="${value//':'/'%3A'}"
  printf '%s' "$value"
}

smoke_failure_summary() {
  local status="${1:?status required}" stage="${2-unknown}" observation="${3-none}"
  if [[ "${GITHUB_ACTIONS:-false}" == true ]]; then
    printf '::error title=Compose smoke failed::stage=%s; last=%s; exit=%s\n' \
      "$(smoke_annotation_escape "$stage")" "$(smoke_annotation_escape "$observation")" "$status" >&2
  else
    printf 'Compose smoke failed: stage=%s; last=%s; exit=%s\n' "$stage" "$observation" "$status" >&2
  fi
}

smoke_best_effort() {
  "$@" || return 0
}
