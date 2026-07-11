#!/usr/bin/env bash
set -euo pipefail

npm --prefix frontend ci
mvn -f backend/pom.xml -q -DskipTests package

echo "Codespace 初始化完成。运行 bash scripts/dev.sh 启动前后端。"
