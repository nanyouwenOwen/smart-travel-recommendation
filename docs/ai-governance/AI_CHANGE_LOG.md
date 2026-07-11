# AI 变更索引

详细历史见 [`../ai-usage-log.md`](../ai-usage-log.md)。本文件从项目封板开始提供短索引，后续每轮追加一项。

## 2026-07-12 - 项目封板与治理

- 用户要求：固化“规划—实施—审核与测试”模式，区分工程文档与 AI 文档，说明真实模型接入和人工动作，并提供后续模型审查材料。
- AI 行为：新增根 `AGENTS.md` 和本目录；根据 DeepSeek 与 Xiaomi MiMo 官方文档核对 base URL、模型示例、结构化输出和套餐使用边界；没有索取、保存或生成任何 API key。
- 独立审核过程：Round 07 首审为 `FAIL`，阻断项为 DeepSeek `json_schema` 已知不兼容却表述过宽、MiMo Token Plan 非 Coding 用途限制缺失、工作流错误允许 `CONDITIONAL PASS`。实施方已修正 B1–B3；首次复审确认该三项有效，同时要求补齐候选产物与本轮日志证据。补齐后同一审核方最终给出 `PASS`，详见 `../reviews/round-07-ai-governance-handoff-review.md`。
- 人类决定待办：后续从 DeepSeek 或 Xiaomi MiMo 选择供应商并自行配置 key/计费、干净 Codespaces 验收、`v0.1.0` tag/GitHub Release 授权。
- 验证证据：Round 06 证据见 `PROJECT_HANDOFF.md`。Round 07 已实际通过 `git diff --check`、`bash -n scripts/*.sh`、`scripts/check.sh`（前端 43 项测试与后端 Maven verify）、Compose config 和 Markdown 本地链接检查；本机无 Trivy，固定模式 secret 补充扫描未发现真实凭据。提交 `82d0f89` 的 GitHub Actions run `29164801003` 七个 job 全绿，候选 artifact 及 digest 见 `PROJECT_HANDOFF.md`。
