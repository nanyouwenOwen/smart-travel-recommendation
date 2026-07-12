# 智能旅游助手：后续模型审查与开发交接

## 项目状态

这是 Java 21/Spring Boot 4.1 + Vue 3.5/TypeScript + MySQL 8.4 的前后端分离 MVP。已实现认证、结构化 AI 行程、预算、调整/版本恢复、普通与 SSE 咨询、地点/天气/景点/地图、来源与更新时间。默认 AI 和实时数据均为确定性 Stub；当前 Codex 会话不能嵌入运行时。后端另有 OpenAI-compatible 适配器，并在 Round 14 新增 Xiaomi MiMo Token Plan Anthropic Messages 适配器，覆盖行程、普通咨询和 SSE；无真实 key 的自动化 Mock 验证不代表真实账号、费用或用途条款已经验收，人工步骤见 `HUMAN_ACTIONS.md`。

## 权威资料与边界

- 产品目标、版本、规则：根 `README.md`。
- 任务状态：`TODO.md`；MVP 与 `v0.1.0` GitHub Release 已完成，真实模型凭据验收、全新 Codespace 人工验收和生产部署仍是独立的人工作业。
- 机器契约：`docs/openapi.yaml`；人类接口约束：`docs/api-contract.md`；数据模型：`docs/data-model.md`。
- 每轮计划/审核：`docs/plans/`、`docs/reviews/`。
- 部署/运维：`docs/deployment.md`、`operations.md`、`backup-restore.md`、`troubleshooting.md`。
- AI 过程：仅在 `docs/ai-governance/` 与 `docs/ai-usage-log.md`。

## 架构审查重点

后端是唯一持有 AI key 和调用供应商的信任边界。资源查询必须取 SecurityContext 用户并对越权返回 404。行程版本不可变；失败调整不能覆盖 READY 版本。咨询 SSE 有幂等 key、事件序列、重放、取消和最终消息回源。实时来源由服务端持久化，模型不能自行伪造；Stub 必须明确标为演示数据。

已知非阻塞限制：应用/供应商限流主要是单实例；实时 timezone 兜底不处理全部 DST/跨时区国家；公共数据端点无 SLA；交通路由默认关闭；Codespaces 干净环境仍需人工验收；生产需权威 timezone、共享限流/协调、监控与商业/自托管数据端点。

## 可重复门禁

运行 `scripts/check.sh`。浏览器和 MySQL 真栈由 GitHub CI 执行；Compose smoke 覆盖注册、地点、行程 READY、SSE、三服务健康、非 root、备份到新库恢复、k6、后端/MySQL 重启。安全 job 使用 Trivy；release-candidate 生成 JAR、前端 tar、OpenAPI、双 CycloneDX SBOM、SHA256 和 Git SHA。

当前已核验的 Round 06 候选证据：提交 `b978503`；GitHub Actions run `29164101865` 七个 job 全绿；Actions artifact `smart-travel-assistant-0.1.0-rc`；artifact digest `sha256:c64e81a442cc716cae8008d69c77c8ff2f8e4d36ff899b029bf889e0644c1640`；Round 06 独立审核最终 `PASS`。

Round 07 治理与交接候选证据：提交 `82d0f89`；[GitHub Actions run `29164801003`](https://github.com/nanyouwenOwen/smart-travel-recommendation/actions/runs/29164801003) 七个 job 全绿；Actions artifact `smart-travel-assistant-0.1.0-rc`；artifact digest `sha256:2d25af42044d10211c6cbc8c4a8859ee9ecb18ce440b86875168bfad3293ef67`；Round 07 独立审核最终 `PASS`。本段证据同步文档将作为后续轻量提交，其本身的 CI 仍需另行通过；后续模型不得只引用历史结论，每次修改都按 `AGENTS.md` 产生当前提交对应的计划、测试、审核与 CI 证据。

Round 08 Compose smoke 可靠性候选证据：提交 `4016764`；[GitHub Actions run `29165356764`](https://github.com/nanyouwenOwen/smart-travel-recommendation/actions/runs/29165356764) 七个 job 全绿；Actions artifact `smart-travel-assistant-0.1.0-rc`；artifact digest `sha256:0d18f4cf11ff673b4869f71a85bebf899bf17594b8ddae9149cb334a8dd05b64`；Round 08 独立审核最终 `PASS`。

Round 08 证据同步 HEAD `2eb538b` 的 [GitHub Actions run `29165523778`](https://github.com/nanyouwenOwen/smart-travel-recommendation/actions/runs/29165523778) 也已七个 job 全绿；artifact `smart-travel-assistant-0.1.0-rc` digest 为 `sha256:de642becadd443090be4121d874e90c68fa8d6cf4a3f092200afc5810092fedd`。Round 09 审核确认仍缺两项发布正向证据：安全门会忽略无修复版本漏洞，artifact 内部文件/GIT SHA/校验和未直接核对。在下一轮闭环前不得宣称自动化发布证据完整，tag/GitHub Release 也仍未获得授权。

Round 10 已关闭上述两项缺口：提交 `0f1930b513d8d0038e51eb07e5435a3c624fce7c` 的 [GitHub Actions run `29166218083`](https://github.com/nanyouwenOwen/smart-travel-recommendation/actions/runs/29166218083) 七个 job 全绿，安全门对当前 JAR 扫描所有 High/Critical 且不忽略 unfixed，候选产物在上传前完成文件/SHA/校验和/SBOM/归档自校验。Artifact `smart-travel-assistant-0.1.0-rc` 为 56,319,725 bytes，digest `sha256:77ba570b13e6c8a7bb2f65f300907d1c744c9a1e41c451354fbe2f79e4506cda`；Round 10 独立审核最终 `PASS`。

正式发布证据：Annotated tag `v0.1.0` peeled commit 固定为 `52864b1aa72f56081abfc0bd146415d2a5f1ccb8`；提交 `197904ab7a101ab6baa86173c3180d53f6e4cc91` 的 [run `29195654260`](https://github.com/nanyouwenOwen/smart-travel-recommendation/actions/runs/29195654260) 中六项质量任务、候选与 recovery 全部成功。公开 [Release v0.1.0](https://github.com/nanyouwenOwen/smart-travel-recommendation/releases/tag/v0.1.0) ID 为 `352766714`，非 draft、非 prerelease；八项公开附件下载后完整通过候选验证并绑定固定 SHA。一次性 recovery job 已删除，普通 main CI 不再持有 Release 写路径；仅保留已经审核、只响应精确 `v0.1.0` tag push 的发布机制。

本段 recovery 删除状态在本轮收尾提交推送前仅描述待提交工作树；必须以收尾准确 SHA 的七项普通 CI 全绿作为终验。终验失败时，本交接不得被引用为完成证据。

## 接手步骤

1. 阅读 `AGENTS.md` 与 `docs/ai-governance/WORKFLOW.md`。
2. 检查 `git status`、最近提交、TODO 和最新审核，不覆盖用户改动。
3. 创建新 round 计划；实施者和审核者职责分离。
4. 运行与风险相称的本地/CI 门；只记录实际结果。
5. 把新决策追加到 `AI_CHANGE_LOG.md`，敏感信息一律不落盘。
