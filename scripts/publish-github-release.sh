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
upload_error="$(mktemp)"
publish_stage="startup"
publish_detail="none"
cleanup() {
  status=$?; trap - EXIT
  if ((status != 0)) && [[ "${GITHUB_ACTIONS:-false}" == true ]]; then
    printf '::error title=GitHub Release failed::stage=%s; detail=%s; exit=%s\n' \
      "$publish_stage" "$publish_detail" "$status" >&2
  fi
  rm -f "$release_json" "$release_error" || true
  [[ -z "$upload_error" ]] || rm -f "$upload_error" || true
  rm -rf "$remote_dir" || true
  exit "$status"
}
trap cleanup EXIT

load_release() {
  local view_json
  view_json="$(mktemp)"
  if ! gh release view "$tag" --repo "$repo" \
    --json databaseId,tagName,name,body,isDraft,isPrerelease,uploadUrl,assets \
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
    upload_url: .uploadUrl,
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

load_release_by_id() {
  local id="$1"
  if ! gh api "repos/$repo/releases/$id" >"$release_json"; then
    echo "failed to query release id $id" >&2
    return 12
  fi
}

validate_metadata() {
  [[ "$(jq -r .prerelease "$release_json")" == false ]]
  [[ "$(jq -r .name "$release_json")" == "$title" ]]
  [[ "$(jq -r .body "$release_json")" == "$(cat "$notes_file")" ]]
  local id
  id="$(jq -er .id "$release_json")"
  [[ "$(jq -r .upload_url "$release_json")" == \
    "https://uploads.github.com/repos/$repo/releases/$id/assets{?name,label}" ]]
}

validate_release_identity() {
  local expected_id="${1:-}"
  publish_detail="identity-tag"
  local actual_tag draft_state
  actual_tag="$(jq -r '.tag_name // ""' "$release_json")"
  draft_state="$(jq -r '.draft // false' "$release_json")"
  if [[ -z "$expected_id" || "$draft_state" != true ]]; then
    [[ "$actual_tag" == "$tag" ]]
  else
    [[ -z "$actual_tag" || "$actual_tag" == "$tag" ]]
  fi
  publish_detail="identity-id-type"
  jq -e '.id | type == "number" and . > 0 and floor == .' "$release_json" >/dev/null
  local id
  id="$(jq -r .id "$release_json")"
  publish_detail="identity-id-continuity"
  [[ -z "$expected_id" || "$id" == "$expected_id" ]]
  publish_detail="identity-upload-url"
  [[ "$(jq -r .upload_url "$release_json")" == \
    "https://uploads.github.com/repos/$repo/releases/$id/assets{?name,label}" ]]
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

publish_stage="validate-release-identity"
validate_release_identity
release_id="$(jq -r .id "$release_json")"
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
  publish_detail="asset-CHANGELOG.md-starting"
  upload_url="https://uploads.github.com/repos/$repo/releases/$release_id/assets"
  for name in "${assets[@]}"; do
    publish_detail="asset-$name-starting"
    set +e
    gh api --method POST --silent \
      -H 'Content-Type: application/octet-stream' \
      --input "$asset_dir/$name" "$upload_url?name=$name" 2>"$upload_error"
    upload_status=$?
    set -e
    if ((upload_status != 0)); then
      upload_reason=unknown
      for reason in HTTP-401 HTTP-403 HTTP-404 HTTP-422 already-exists release-not-found; do
        case "$reason" in
          HTTP-*) pattern="${reason/-/ }" ;;
          already-exists) pattern='already exists' ;;
          release-not-found) pattern='release not found' ;;
        esac
        if grep -Fqi "$pattern" "$upload_error"; then upload_reason="$reason"; break; fi
      done
      publish_detail="asset-$name-$upload_reason"
      exit "$upload_status"
    fi
  done
  publish_stage="validate-uploaded-release"
  publish_detail="load-by-id"
  load_release_by_id "$release_id"
  publish_detail="identity"
  validate_release_identity "$release_id"
  publish_detail="metadata"
  validate_metadata
  publish_detail="draft-state"
  [[ "$(jq -r .draft "$release_json")" == true ]]
fi

publish_detail="metadata"
validate_metadata
publish_stage="verify-draft-assets"
publish_detail="asset-set"
validate_assets
publish_detail="asset-downloads"
download_and_verify_assets

if [[ "$(jq -r .draft "$release_json")" == true ]]; then
  publish_stage="publish-release"
  release_id="$(jq -er .id "$release_json")"
  gh api --method PATCH "repos/$repo/releases/$release_id" -F draft=false -F prerelease=false >/dev/null
  publish_stage="validate-public-release"
  publish_detail="load-by-id"
  load_release_by_id "$release_id"
  publish_detail="identity"
  validate_release_identity "$release_id"
fi

publish_detail="metadata"
validate_metadata
publish_detail="published-state"
[[ "$(jq -r .draft "$release_json")" == false ]]
publish_detail="asset-set"
validate_assets
publish_stage="verify-public-assets"
publish_detail="asset-downloads"
download_and_verify_assets
publish_stage="complete"
echo "Published and remotely verified $tag"
