#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
quick="${1:-}"
cd "$root"
npx --yes @redocly/cli@2.12.3 lint docs/openapi.yaml --extends=minimal
(cd frontend && npm run format:check && npm run lint && npm run type-check)
(cd backend && mvn -q -DskipTests spotless:check compile spotbugs:check)
if [[ "$quick" != "--quick" ]]; then
  (cd frontend && npm run test:coverage && npm run build)
  (cd backend && mvn -q verify)
fi
