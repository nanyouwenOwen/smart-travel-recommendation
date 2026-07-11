# MVP 候选验证报告

- 候选版本：0.1.0（尚未创建 tag/GitHub Release）
- 实现基线：`40167645d58fc9ee2ce1bc84f7c3c2ce96230e88`（Compose smoke 可靠性为最后一项实现修改）。[GitHub Actions run `29165356764`](https://github.com/nanyouwenOwen/smart-travel-recommendation/actions/runs/29165356764) 的 `openapi`、`frontend`、`backend`、MySQL 8.4 `e2e`、`container-smoke`、`security` 和 `release-candidate` 七个 job 全部成功，Round 08 独立审核最终 `PASS`。
- 证据同步 HEAD：`2eb538b710c18518ee3d8763bfb0ed8adacba118`。[GitHub Actions run `29165523778`](https://github.com/nanyouwenOwen/smart-travel-recommendation/actions/runs/29165523778) 同样七个 job 全部成功，证明证据提交本身没有绕过当前门禁。
- 本地验证：Java 21 后端 43 项测试与 SpotBugs/Spotless 通过；Node 24 前端格式、ESLint、类型、43 项单测、覆盖率和生产构建通过；OpenAPI 3.1 有效。
- 安全与依赖：Round 10 提交 `0f1930b513d8d0038e51eb07e5435a3c624fce7c` 的 [GitHub Actions run `29166218083`](https://github.com/nanyouwenOwen/smart-travel-recommendation/actions/runs/29166218083) 中，security job 先用 Java 21/Maven 从当前 checkout 打包实际 JAR，再用固定 Trivy 0.58.2 以 `HIGH,CRITICAL --exit-code 1` 扫描，不使用 `--ignore-unfixed`；受版本控制源文件的 secret scan 也独立成功。前端 `npm audit --audit-level=high`、后端和 security job 均成功，当前没有漏洞例外或忽略清单。
- 产物：同一 run 的 `release-candidate` 在上传前成功执行 `scripts/verify-release-candidate.sh`，验证固定白名单文件非空、`GIT_SHA` 等于当前完整 SHA、`SHA256SUMS` 完整且不自包含并通过 `sha256sum -c`、双 SBOM 为有效 CycloneDX JSON、JAR/tar 可读且含关键结构。随后上传的 `smart-travel-assistant-0.1.0-rc` 为 56,319,725 bytes，artifact digest `sha256:77ba570b13e6c8a7bb2f65f300907d1c744c9a1e41c451354fbe2f79e4506cda`，workflow run 绑定上述 SHA。
- Codespaces：仓库已有幂等 post-create、5173 转发与 `scripts/dev.sh`；“全新 Codespace 远程打开”仍是人工验收项，没有冒充已验证。
- AI 边界：默认使用确定性 Stub，尚未使用真实 DeepSeek/Xiaomi MiMo 凭据验收。DeepSeek 当前不支持行程适配器使用的 `response_format=json_schema`，行程功能需专用适配后才能验证。
- 已知限制：公共实时端点无生产 SLA；交通路由、预订、实时票价和签证不在 MVP；正式 tag/Release 尚待用户明确授权。
