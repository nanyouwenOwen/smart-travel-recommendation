#!/usr/bin/env bash
set -euo pipefail
base="${BASE_URL:-http://127.0.0.1:5173}"
docker run --rm --network host -i -e BASE_URL="$base" grafana/k6:0.57.0 run - < "$(dirname "$0")/../tests/performance/smoke.js"
