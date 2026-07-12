#!/usr/bin/env bash
set -euo pipefail

repo="${1:?usage: publish-github-release.sh REPO TAG EXPECTED_SHA ASSET_DIR NOTES_FILE}"
tag="${2:?usage: publish-github-release.sh REPO TAG EXPECTED_SHA ASSET_DIR NOTES_FILE}"
expected_sha="${3:?usage: publish-github-release.sh REPO TAG EXPECTED_SHA ASSET_DIR NOTES_FILE}"
asset_dir="${4:?usage: publish-github-release.sh REPO TAG EXPECTED_SHA ASSET_DIR NOTES_FILE}"
notes_file="${5:?usage: publish-github-release.sh REPO TAG EXPECTED_SHA ASSET_DIR NOTES_FILE}"
title='Smart Travel Assistant v0.1.0'

[[ "$repo" == */* && "$tag" == v0.1.0 && "$expected_sha" =~ ^[0-9a-f]{40}$ ]]
[[ -d "$asset_dir" && -s "$notes_file" ]]
command -v gh >/dev/null
command -v jq >/dev/null

assets=(
  CHANGELOG.md
  GIT_SHA
  SHA256SUMS
  backend-sbom.cdx.json
  frontend-0.1.0.tar.gz
  frontend-sbom.cdx.json
  openapi.yaml
  travel-assistant-api-0.1.0.jar
)
mapfile -t expected_assets < <(printf '%s\n' "${assets[@]}" | LC_ALL=C sort)
release_json="$(mktemp)"
release_error="$(mktemp)"
remote_dir="$(mktemp -d)"
publish_stage="startup"
cleanup() {
  status=$?; trap - EXIT
  if ((status != 0)) && [[ "${GITHUB_ACTIONS:-false}" == true ]]; then
    printf '::error title=GitHub Release failed::stage=%s; exit=%s\n' "$publish_stage" "$status" >&2
  fi
  rm -f "$release_json" "$release_error" || true
  rm -rf "$remote_dir" || true
  exit "$status"
}
trap cleanup EXIT

load_release() {
  local view_json
  view_json="$(mktemp)"
  if ! gh release view "$tag" --repo "$repo" \
    --json databaseId,tagName,name,body,isDraft,isPrerelease,assets \
    >"$view_json" 2>"$release_error"; then
    rm -f "$view_json"
    if [[ "$(<"$release_error")" == 'release not found' ]]; then return 1; fi
    echo "failed to query release for $tag" >&2
    return 10
  fi
  jq --arg asset_prefix "https://api.github.com/repos/$repo/releases/assets/" '{
    id: .databaseId,
    tag_name: .tagName,
    name,
    body,
    draft: .isDraft,
    prerelease: .isPrerelease,
    assets: [.assets[] | {
      id: (.apiUrl
        | if startswith($asset_prefix) and (ltrimstr($asset_prefix) | test("^[0-9]+$"))
          then ltrimstr($asset_prefix) | tonumber
          else error("unexpected release asset API URL") end),
      name,
      size
    }]
  }' "$view_json" >"$release_json"
  rm -f "$view_json"
}

validate_metadata() {
  [[ "$(jq -r .tag_name "$release_json")" == "$tag" ]]
  [[ "$(jq -r .prerelease "$release_json")" == false ]]
  [[ "$(jq -r .name "$release_json")" == "$title" ]]
  [[ "$(jq -r .body "$release_json")" == "$(cat "$notes_file")" ]]
}

validate_assets() {
  mapfile -t actual < <(jq -r '.assets[].name' "$release_json" | LC_ALL=C sort)
  [[ "${actual[*]}" == "${expected_assets[*]}" ]]
  jq -e '.assets | length == 8 and all(.size > 0)' "$release_json" >/dev/null
}

download_and_verify_assets() {
  rm -rf "$remote_dir"
  mkdir -p "$remote_dir"
  local name asset_id
  for name in "${assets[@]}"; do
    asset_id="$(jq -er --arg name "$name" '.assets[] | select(.name == $name) | .id' "$release_json")"
    gh api -H 'Accept: application/octet-stream' "repos/$repo/releases/assets/$asset_id" >"$remote_dir/$name"
  done
  scripts/verify-release-candidate.sh "$remote_dir" "$expected_sha"
}

set +e
publish_stage="list-release"
load_release
load_status=$?
set -e

if [[ "$load_status" == 1 ]]; then
  publish_stage="create-draft"
  gh api --method POST "repos/$repo/releases" \
    -f tag_name="$tag" -f name="$title" -f body="$(cat "$notes_file")" \
    -F draft=true -F prerelease=false >"$release_json"
  load_release
elif [[ "$load_status" != 0 ]]; then
  exit "$load_status"
fi

release_id="$(jq -er .id "$release_json")"
draft="$(jq -r .draft "$release_json")"

if [[ "$draft" == true ]]; then
  publish_stage="prepare-draft"
  gh api --method PATCH "repos/$repo/releases/$release_id" \
    -f name="$title" -f body="$(cat "$notes_file")" \
    -F draft=true -F prerelease=false >/dev/null
  while IFS= read -r asset_id; do
    gh api --method DELETE "repos/$repo/releases/assets/$asset_id"
  done < <(jq -r '.assets[].id' "$release_json")
  publish_stage="upload-assets"
  upload_paths=()
  for name in "${assets[@]}"; do upload_paths+=("$asset_dir/$name"); done
  gh release upload "$tag" "${upload_paths[@]}" --repo "$repo"
  load_release
  validate_metadata
  [[ "$(jq -r .draft "$release_json")" == true ]]
fi

validate_metadata
publish_stage="verify-draft-assets"
validate_assets
download_and_verify_assets

if [[ "$(jq -r .draft "$release_json")" == true ]]; then
  publish_stage="publish-release"
  release_id="$(jq -er .id "$release_json")"
  gh api --method PATCH "repos/$repo/releases/$release_id" -F draft=false -F prerelease=false >/dev/null
  load_release
fi

validate_metadata
[[ "$(jq -r .draft "$release_json")" == false ]]
validate_assets
publish_stage="verify-public-assets"
download_and_verify_assets
publish_stage="complete"
echo "Published and remotely verified $tag"
