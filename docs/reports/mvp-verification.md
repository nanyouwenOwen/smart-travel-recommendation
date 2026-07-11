# MVP 候选验证报告

- 候选版本：0.1.0（尚未创建 tag/GitHub Release）
- 实现基线：`40167645d58fc9ee2ce1bc84f7c3c2ce96230e88`（Compose smoke 可靠性为最后一项实现修改）。[GitHub Actions run `29165356764`](https://github.com/nanyouwenOwen/smart-travel-recommendation/actions/runs/29165356764) 的 `openapi`、`frontend`、`backend`、MySQL 8.4 `e2e`、`container-smoke`、`security` 和 `release-candidate` 七个 job 全部成功，Round 08 独立审核最终 `PASS`。
- 证据同步 HEAD：`2eb538b710c18518ee3d8763bfb0ed8adacba118`。[GitHub Actions run `29165523778`](https://github.com/nanyouwenOwen/smart-travel-recommendation/actions/runs/29165523778) 同样七个 job 全部成功，证明证据提交本身没有绕过当前门禁。
- 本地验证：Java 21 后端 43 项测试与 SpotBugs/Spotless 通过；Node 24 前端格式、ESLint、类型、43 项单测、覆盖率和生产构建通过；OpenAPI 3.1 有效。
- 安全与依赖：前端 `npm audit --audit-level=high`、后端 `mvn verify` 和固定 Trivy 0.58.2 均位于门禁中，但 Trivy 当前带 `--ignore-unfixed`，而 `mvn verify` 不是独立的后端漏洞审计。因此成功 job 只能证明当前门禁通过，不能证明“无未处置 High/Critical”；该发布条件尚待下一轮增强。
- 产物：HEAD run 上传 `smart-travel-assistant-0.1.0-rc`（Actions artifact digest `sha256:de642becadd443090be4121d874e90c68fa8d6cf4a3f092200afc5810092fedd`）。workflow 声明会生成 JAR、前端 tar、OpenAPI、CHANGELOG、前后端 CycloneDX SBOM、`SHA256SUMS` 和 `GIT_SHA`，但当前环境下载 artifact 需要 GitHub 认证并返回 HTTP 401，既有审核也未解压核对。在 CI 内增加存在性、`GIT_SHA` 和 `sha256sum -c` 自校验前，不宣称 artifact 内部绑定已被证明。
- Codespaces：仓库已有幂等 post-create、5173 转发与 `scripts/dev.sh`；“全新 Codespace 远程打开”仍是人工验收项，没有冒充已验证。
- AI 边界：默认使用确定性 Stub，尚未使用真实 DeepSeek/Xiaomi MiMo 凭据验收。DeepSeek 当前不支持行程适配器使用的 `response_format=json_schema`，行程功能需专用适配后才能验证。
- 已知限制：公共实时端点无生产 SLA；交通路由、预订、实时票价和签证不在 MVP；正式 tag/Release 尚待用户明确授权。
