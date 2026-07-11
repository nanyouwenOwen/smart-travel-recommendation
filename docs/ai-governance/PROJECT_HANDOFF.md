# 智能旅游助手：后续模型审查与开发交接

## 项目状态

这是 Java 21/Spring Boot 4.1 + Vue 3.5/TypeScript + MySQL 8.4 的前后端分离 MVP。已实现认证、结构化 AI 行程、预算、调整/版本恢复、普通与 SSE 咨询、地点/天气/景点/地图、来源与更新时间。默认 AI 和实时数据均为确定性 Stub；当前 Codex 会话不能嵌入运行时。后续可人工配置 DeepSeek 或 Xiaomi MiMo，但不应概括为“全功能兼容”：DeepSeek 咨询预期兼容，其官方 `response_format` 不支持当前行程适配器固定使用的 `json_schema`，因此行程接入需先实现专用适配；MiMo 仅推荐按量付费 API，Token Plan 当前条款禁止本项目这类非 Coding 自定义应用后端。详见 `HUMAN_ACTIONS.md`。

## 权威资料与边界

- 产品目标、版本、规则：根 `README.md`。
- 任务状态：`TODO.md`；“发布 MVP”未勾选表示 tag/Release 尚未获得授权，不表示实现未完成。
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

## 接手步骤

1. 阅读 `AGENTS.md` 与 `docs/ai-governance/WORKFLOW.md`。
2. 检查 `git status`、最近提交、TODO 和最新审核，不覆盖用户改动。
3. 创建新 round 计划；实施者和审核者职责分离。
4. 运行与风险相称的本地/CI 门；只记录实际结果。
5. 把新决策追加到 `AI_CHANGE_LOG.md`，敏感信息一律不落盘。
