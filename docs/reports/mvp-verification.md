# MVP 候选验证报告

- 候选版本：0.1.0
- 基线提交：由最终审核提交填写
- 本地验证：Java 21 后端测试与 SpotBugs/Spotless通过；Node 24 前端格式、ESLint、类型、42 项单测、覆盖率和构建通过；OpenAPI 有效。
- 真栈证据：Round 05 GitHub Actions run 29162926553 已在 MySQL 8.4 上完成 Flyway V6 与 7 项 Playwright。Round 06 镜像/Compose 门由候选提交 CI 再次验证。
- Codespaces：仓库已有幂等 post-create、5173 转发与 `scripts/dev.sh`；当前执行环境不能新建全新 Codespace，因此“干净 Codespace 远程打开”保留为人工验收项，不冒充已验证。
- 已知限制：默认使用演示 Stub；公共实时端点无生产 SLA；交通路由、预订、实时票价、签证不在 MVP；创建 tag/Release 尚待用户授权。
