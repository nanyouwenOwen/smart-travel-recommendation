#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"
tmp="$(mktemp -d)"
cleanup() { rm -rf "$tmp"; }
trap cleanup EXIT

sha='0123456789abcdef0123456789abcdef01234567'
notes="$tmp/notes.md"
printf '# Smart Travel Assistant v0.1.0\n\nMock release notes.\n' >"$notes"
assets="$tmp/local-assets"
mkdir -p "$assets" "$tmp/jar/BOOT-INF" "$tmp/frontend"
printf 'changelog\n' >"$assets/CHANGELOG.md"
printf '%s\n' "$sha" >"$assets/GIT_SHA"
printf 'openapi: 3.1.0\n' >"$assets/openapi.yaml"
printf '{"bomFormat":"CycloneDX","specVersion":"1.6","components":[]}' >"$assets/backend-sbom.cdx.json"
printf '{"bomFormat":"CycloneDX","specVersion":"1.5","components":[]}' >"$assets/frontend-sbom.cdx.json"
printf 'class' >"$tmp/jar/BOOT-INF/mock.class"
jar cf "$assets/travel-assistant-api-0.1.0.jar" -C "$tmp/jar" .
printf '<!doctype html>\n' >"$tmp/frontend/index.html"
tar -C "$tmp/frontend" -czf "$assets/frontend-0.1.0.tar.gz" .
(cd "$assets" && sha256sum CHANGELOG.md GIT_SHA backend-sbom.cdx.json frontend-0.1.0.tar.gz frontend-sbom.cdx.json openapi.yaml travel-assistant-api-0.1.0.jar >SHA256SUMS)
scripts/verify-release-candidate.sh "$assets" "$sha" >/dev/null

mkdir -p "$tmp/bin"
cat >"$tmp/bin/gh" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
if [[ "$1" == release && "$2" == upload ]]; then
  shift 2; tag="$1"; shift; files=(); repo=''
  while (($#)); do
    case "$1" in
      --repo) repo="$2"; shift 2 ;;
      *) files+=("$1"); shift ;;
    esac
  done
  [[ "$tag" == v0.1.0 && "$repo" == "${MOCK_EXPECT_REPO:-owner/repo}" ]]
  [[ "${GH_TOKEN:-}" == "${MOCK_EXPECT_TOKEN:-mock-token}" ]]
  state="$MOCK_STATE/releases.json"
  release_id="$(jq -er --arg tag "$tag" '.[] | select(.tag_name == $tag and .draft == true) | .id' "$state")"
  for input in "${files[@]}"; do
    [[ -f "$input" && -s "$input" ]]; name="$(basename "$input")"; id="$(cat "$MOCK_STATE/next-id")"
    echo $((id + 1)) >"$MOCK_STATE/next-id"; cp "$input" "$MOCK_STATE/assets/$id"; size="$(stat -c %s "$input")"
    jq --argjson release_id "$release_id" --argjson id "$id" --arg name "$name" --argjson size "$size" \
      'map(if .id == $release_id then .assets += [{id:$id,name:$name,size:$size}] else . end)' \
      "$state" >"$state.new"; mv "$state.new" "$state"
  done
  exit 0
fi
[[ "$1" == api ]]; shift
method=GET; input=''; endpoint=''
declare -A fields=()
while (($#)); do
  case "$1" in
    --method) method="$2"; shift 2 ;;
    --input) input="$2"; shift 2 ;;
    -f|-F) pair="$2"; fields["${pair%%=*}"]="${pair#*=}"; shift 2 ;;
    -H|--hostname) shift 2 ;;
    --paginate) shift ;;
    *) endpoint="$1"; shift ;;
  esac
done
state="$MOCK_STATE/releases.json"
[[ "${MOCK_API_FAIL:-0}" != 1 ]] || { echo 'mock API failure' >&2; exit 42; }

if [[ "$method" == GET && "$endpoint" == *'/releases?per_page=100' ]]; then cat "$state"; exit 0; fi
if [[ "$method" == POST && "$endpoint" == */releases ]]; then
  release="$(jq -n --arg tag "${fields[tag_name]}" --arg name "${fields[name]}" --arg body "${fields[body]}" \
    '{id:1,tag_name:$tag,name:$name,body:$body,draft:true,prerelease:false,assets:[]}')"
  jq --argjson release "$release" '. + [$release]' "$state" >"$state.new"; mv "$state.new" "$state"
  printf '%s\n' "$release"; exit 0
fi
if [[ "$method" == PATCH && "$endpoint" =~ /releases/([0-9]+)$ ]]; then
  id="${BASH_REMATCH[1]}"; draft="${fields[draft]:-}"
  jq --argjson id "$id" --arg name "${fields[name]:-}" --arg body "${fields[body]:-}" --arg draft "$draft" '
    map(if .id == $id then
      (if $name != "" then .name=$name else . end) |
      (if $body != "" then .body=$body else . end) |
      (if $draft != "" then .draft=($draft == "true") else . end) |
      .prerelease=false
    else . end)' "$state" >"$state.new"; mv "$state.new" "$state"; echo '{}'; exit 0
fi
if [[ "$method" == DELETE && "$endpoint" =~ /assets/([0-9]+)$ ]]; then
  id="${BASH_REMATCH[1]}"; jq --argjson id "$id" 'map(.assets |= map(select(.id != $id)))' "$state" >"$state.new"; mv "$state.new" "$state"; exit 0
fi
if [[ "$method" == POST && "$endpoint" =~ /releases/([0-9]+)/assets\?name=(.+)$ ]]; then
  release_id="${BASH_REMATCH[1]}"; name="${BASH_REMATCH[2]}"; id="$(cat "$MOCK_STATE/next-id")"
  echo $((id + 1)) >"$MOCK_STATE/next-id"; cp "$input" "$MOCK_STATE/assets/$id"
  size="$(stat -c %s "$input")"
  jq --argjson release_id "$release_id" --argjson id "$id" --arg name "$name" --argjson size "$size" '
    map(if .id == $release_id then .assets += [{id:$id,name:$name,size:$size}] else . end)' "$state" >"$state.new"; mv "$state.new" "$state"
  echo '{}'; exit 0
fi
if [[ "$method" == GET && "$endpoint" =~ /assets/([0-9]+)$ ]]; then cat "$MOCK_STATE/assets/${BASH_REMATCH[1]}"; exit 0; fi
echo "unsupported mock gh call: $method $endpoint" >&2; exit 90
MOCK
chmod +x "$tmp/bin/gh"

seed_state() {
  local draft="$1" title="$2" body="$3" asset_mode="$4"
  rm -rf "$tmp/state"; mkdir -p "$tmp/state/assets"; echo 100 >"$tmp/state/next-id"
  jq -n --arg tag v0.1.0 --arg name "$title" --arg body "$body" --argjson draft "$draft" \
    '[{id:1,tag_name:$tag,name:$name,body:$body,draft:$draft,prerelease:false,assets:[]}]' >"$tmp/state/releases.json"
  if [[ "$asset_mode" != none ]]; then
    local name id size
    for name in CHANGELOG.md GIT_SHA SHA256SUMS backend-sbom.cdx.json frontend-0.1.0.tar.gz frontend-sbom.cdx.json openapi.yaml travel-assistant-api-0.1.0.jar; do
      id="$(cat "$tmp/state/next-id")"; echo $((id + 1)) >"$tmp/state/next-id"; cp "$assets/$name" "$tmp/state/assets/$id"; size="$(stat -c %s "$assets/$name")"
      jq --argjson id "$id" --arg name "$name" --argjson size "$size" '.[0].assets += [{id:$id,name:$name,size:$size}]' \
        "$tmp/state/releases.json" >"$tmp/state/new"; mv "$tmp/state/new" "$tmp/state/releases.json"
    done
    if [[ "$asset_mode" == extra ]]; then
      id="$(cat "$tmp/state/next-id")"; echo junk >"$tmp/state/assets/$id"
      jq --argjson id "$id" '.[0].assets += [{id:$id,name:"unexpected.txt",size:5}]' "$tmp/state/releases.json" >"$tmp/state/new"; mv "$tmp/state/new" "$tmp/state/releases.json"
    fi
  fi
}

run_publish_with() {
  local token="$1" github_actions="$2"
  PATH="$tmp/bin:$PATH" MOCK_STATE="$tmp/state" MOCK_EXPECT_TOKEN=mock-token GH_TOKEN="$token" GITHUB_ACTIONS="$github_actions" \
    scripts/publish-github-release.sh owner/repo v0.1.0 "$sha" "$assets" "$notes"
}
run_publish() { run_publish_with mock-token false; }
expect_failure() { local name="$1"; shift; if "$@" >/dev/null 2>&1; then echo "$name: unexpected PASS" >&2; exit 1; else echo "$name: expected failure"; fi; }

rm -rf "$tmp/state"; mkdir -p "$tmp/state/assets"; echo 100 >"$tmp/state/next-id"; echo '[]' >"$tmp/state/releases.json"
run_publish >/dev/null; echo 'first create: PASS'
seed_state true 'stale title' 'stale body' extra
run_publish >/dev/null; echo 'draft recovery: PASS'
seed_state false 'Smart Travel Assistant v0.1.0' "$(cat "$notes")" complete
run_publish >/dev/null; echo 'published idempotency: PASS'
seed_state false 'wrong title' "$(cat "$notes")" complete
expect_failure 'published title conflict' run_publish
seed_state false 'Smart Travel Assistant v0.1.0' 'wrong body' complete
expect_failure 'published body conflict' run_publish
seed_state false 'Smart Travel Assistant v0.1.0' "$(cat "$notes")" extra
expect_failure 'published asset conflict' run_publish
seed_state false 'Smart Travel Assistant v0.1.0' "$(cat "$notes")" complete
expect_failure 'release API failure' env PATH="$tmp/bin:$PATH" MOCK_STATE="$tmp/state" MOCK_API_FAIL=1 \
  GH_TOKEN=mock-token scripts/publish-github-release.sh owner/repo v0.1.0 "$sha" "$assets" "$notes"

rm -rf "$tmp/state"; mkdir -p "$tmp/state/assets"; echo 100 >"$tmp/state/next-id"; echo '[]' >"$tmp/state/releases.json"
annotation_output="$tmp/upload-annotation.txt"
set +e; run_publish_with wrong-token true >"$annotation_output" 2>&1; annotation_status=$?; set -e
[[ "$annotation_status" != 0 ]]
annotation="$(cat "$annotation_output")"
[[ "$(grep -c '^::error title=GitHub Release failed::' "$annotation_output")" == 1 ]]
[[ "$annotation" == *"stage=upload-assets; exit=$annotation_status"* ]]
for forbidden in wrong-token 'Authorization:' 'application/octet-stream' changelog; do
  [[ "$annotation" != *"$forbidden"* ]] || { echo "annotation leaked $forbidden" >&2; exit 1; }
done
echo 'upload auth failure annotation: PASS'

rm -rf "$tmp/state"; mkdir -p "$tmp/state/assets"; echo 100 >"$tmp/state/next-id"; echo '[]' >"$tmp/state/releases.json"
local_output="$tmp/upload-local.txt"
set +e; run_publish_with wrong-token false >"$local_output" 2>&1; local_status=$?; set -e
[[ "$local_status" == "$annotation_status" ]]
! grep -q '::error' "$local_output"
echo 'upload auth failure local mode: PASS'

seed_state true 'Smart Travel Assistant v0.1.0' "$(cat "$notes")" none
expect_failure 'upload wrong repository' env PATH="$tmp/bin:$PATH" MOCK_STATE="$tmp/state" MOCK_EXPECT_TOKEN=mock-token \
  GH_TOKEN=mock-token gh release upload v0.1.0 "$assets/CHANGELOG.md" --repo other/repo
echo 'publish release state-machine tests: PASS'
