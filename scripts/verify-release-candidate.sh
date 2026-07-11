#!/usr/bin/env bash
set -euo pipefail

release_dir="${1:?usage: verify-release-candidate.sh RELEASE_DIR EXPECTED_GIT_SHA}"
expected_sha="${2:?usage: verify-release-candidate.sh RELEASE_DIR EXPECTED_GIT_SHA}"
[[ "$expected_sha" =~ ^[0-9a-f]{40}$ ]] || { echo "expected Git SHA must be 40 lowercase hex characters" >&2; exit 1; }
[[ -d "$release_dir" ]] || { echo "release directory does not exist: $release_dir" >&2; exit 1; }
release_dir="$(cd "$release_dir" && pwd)"

payload=(
  CHANGELOG.md
  GIT_SHA
  backend-sbom.cdx.json
  frontend-0.1.0.tar.gz
  frontend-sbom.cdx.json
  openapi.yaml
  travel-assistant-api-0.1.0.jar
)
required=("${payload[@]}" SHA256SUMS)

mapfile -t actual < <(find "$release_dir" -mindepth 1 -maxdepth 1 -type f -printf '%f\n' | LC_ALL=C sort)
mapfile -t expected < <(printf '%s\n' "${required[@]}" | LC_ALL=C sort)
[[ "${actual[*]}" == "${expected[*]}" ]] || {
  echo "release file set does not match the fixed allowlist" >&2
  printf 'expected: %s\nactual: %s\n' "${expected[*]}" "${actual[*]}" >&2
  exit 1
}

for file in "${required[@]}"; do
  [[ -f "$release_dir/$file" && -s "$release_dir/$file" ]] || { echo "required file is missing or empty: $file" >&2; exit 1; }
done

actual_sha="$(tr -d '\r\n' <"$release_dir/GIT_SHA")"
[[ "$actual_sha" == "$expected_sha" ]] || { echo "GIT_SHA mismatch: expected=$expected_sha actual=$actual_sha" >&2; exit 1; }

mapfile -t manifest_files < <(awk '{print $2}' "$release_dir/SHA256SUMS" | sed 's/^\*//' | LC_ALL=C sort)
mapfile -t expected_payload < <(printf '%s\n' "${payload[@]}" | LC_ALL=C sort)
[[ "${manifest_files[*]}" == "${expected_payload[*]}" ]] || {
  echo "SHA256SUMS file set is incomplete, has extras, or is self-referential" >&2
  exit 1
}
[[ ${#manifest_files[@]} -eq ${#payload[@]} ]] || { echo "unexpected SHA256SUMS entry count" >&2; exit 1; }
(cd "$release_dir" && sha256sum -c SHA256SUMS)

for sbom in backend-sbom.cdx.json frontend-sbom.cdx.json; do
  jq -e '
    .bomFormat == "CycloneDX" and
    (.specVersion | type == "string" and length > 0) and
    (.components | type == "array")
  ' "$release_dir/$sbom" >/dev/null || { echo "invalid CycloneDX SBOM: $sbom" >&2; exit 1; }
done

jar_entries="$(jar tf "$release_dir/travel-assistant-api-0.1.0.jar" | awk 'NF{count++} /^BOOT-INF\//{boot=1} END{if(!count || !boot)exit 1; print count}')" || {
  echo "backend JAR is unreadable or lacks Spring Boot structure" >&2; exit 1;
}
tar_entries="$(tar -tzf "$release_dir/frontend-0.1.0.tar.gz" | awk 'NF{count++} /(^|\/)index\.html$/{has_index=1} END{if(!count || !has_index)exit 1; print count}')" || {
  echo "frontend archive is unreadable or lacks index.html" >&2; exit 1;
}

echo "Release candidate verification PASS"
echo "Git SHA: $actual_sha"
for file in "${required[@]}"; do printf '%s %s bytes\n' "$file" "$(stat -c %s "$release_dir/$file")"; done
for sbom in backend-sbom.cdx.json frontend-sbom.cdx.json; do
  printf '%s CycloneDX %s, %s components\n' "$sbom" \
    "$(jq -r .specVersion "$release_dir/$sbom")" "$(jq '.components | length' "$release_dir/$sbom")"
done
printf 'backend JAR entries: %s\nfrontend archive entries: %s\n' "$jar_entries" "$tar_entries"

if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  {
    echo '### Release candidate verification: PASS'
    echo
    echo "- Git SHA: \`$actual_sha\`"
    echo "- Fixed payload files: ${#payload[@]}"
    echo '- SHA256 manifest: complete, non-self-referential, all entries verified'
    echo "- Backend JAR entries: $jar_entries"
    echo "- Frontend archive entries: $tar_entries"
    for sbom in backend-sbom.cdx.json frontend-sbom.cdx.json; do
      echo "- $sbom: CycloneDX $(jq -r .specVersion "$release_dir/$sbom"), $(jq '.components | length' "$release_dir/$sbom") components"
    done
  } >>"$GITHUB_STEP_SUMMARY"
fi
