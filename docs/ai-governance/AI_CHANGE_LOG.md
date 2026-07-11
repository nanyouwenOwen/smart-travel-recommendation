# AI 变更索引

详细历史见 [`../ai-usage-log.md`](../ai-usage-log.md)。本文件从项目封板开始提供短索引，后续每轮追加一项。

## 2026-07-12 - 项目封板与治理

- 用户要求：固化“规划—实施—审核与测试”模式，区分工程文档与 AI 文档，说明真实模型接入和人工动作，并提供后续模型审查材料。
- AI 行为：新增根 `AGENTS.md` 和本目录；根据 DeepSeek 与 Xiaomi MiMo 官方文档核对 base URL、模型示例、结构化输出和套餐使用边界；没有索取、保存或生成任何 API key。
- 独立审核过程：Round 07 首审为 `FAIL`，阻断项为 DeepSeek `json_schema` 已知不兼容却表述过宽、MiMo Token Plan 非 Coding 用途限制缺失、工作流错误允许 `CONDITIONAL PASS`。实施方已修正 B1–B3；首次复审确认该三项有效，同时要求补齐候选产物与本轮日志证据。补齐后同一审核方最终给出 `PASS`，详见 `../reviews/round-07-ai-governance-handoff-review.md`。
- 人类决定待办：后续从 DeepSeek 或 Xiaomi MiMo 选择供应商并自行配置 key/计费、干净 Codespaces 验收、`v0.1.0` tag/GitHub Release 授权。
- 验证证据：Round 06 证据见 `PROJECT_HANDOFF.md`。Round 07 已实际通过 `git diff --check`、`bash -n scripts/*.sh`、`scripts/check.sh`（前端 43 项测试与后端 Maven verify）、Compose config 和 Markdown 本地链接检查；本机无 Trivy，固定模式 secret 补充扫描未发现真实凭据。提交 `82d0f89` 的 GitHub Actions run `29164801003` 七个 job 全绿，候选 artifact 及 digest 见 `PROJECT_HANDOFF.md`。

## 2026-07-12 - Compose smoke 可靠性

- 触发：纯文档证据提交 `3fe06a6` 的 CI run `29164981696` 中 `container-smoke` 非确定性失败，导致 `release-candidate` 跳过；紧邻且业务代码相同的 run `29164801003` 全绿。
- 实施：将 Compose smoke 拆为可定位阶段，保留原始/信号退出码，失败时输出有界容器诊断；仅对启动、异步行程、后端与数据库恢复做有限轮询，登录恢复必须同时满足 HTTP 2xx 和非空 token，性能、SSE、备份数据与非 root 断言未放宽。
- 审核修正：首审发现行程畸形/未知状态被错当可重试且脚本丢失可执行位；首次复审又通过 mock 发现 OR-list 会禁用函数内 `errexit`。实施方恢复 100755，对 FAILED/畸形/未知状态显式返回失败；同一 reviewer 定向 mock 复审最终 `PASS`。
- 验证证据：`bash -n`、`git diff --check`、Compose config 和 `scripts/check.sh` 通过；本机 Docker socket 无权限，该失败路径实际保留非零状态并输出诊断。提交 `4016764` 的 GitHub Actions run `29165356764` 七个 job 全绿，含完整 container smoke 与 release candidate；artifact digest 见 `PROJECT_HANDOFF.md`。

## 2026-07-12 - 发布就绪证据同步

- 用户目标：继续按独立规划、实施、审核与复审流程完成 MVP，所有测试通过并推送 GitHub。
- 实施：将发布清单前五项与已验证的 `4016764`/`29165356764` 和 `2eb538b`/`29165523778` 证据对齐，更新 MVP/备份恢复报告、CHANGELOG、发布说明和交接入口，不修改产品实现或门禁。
- 首审结论：`FAIL`。审核发现 Trivy `--ignore-unfixed` 和普通 `mvn verify` 不能证明“无未处置 High/Critical”；Actions artifact 下载需认证并返回 HTTP 401，而既有审核没有解压验证其 `GIT_SHA`/文件清单/校验和。实施方撤回这两项勾选和过度声明，留待新实现轮次建立正向证据。
- 人工边界：用户尚未授权 `v0.1.0` tag/GitHub Release，因此授权清单项和 `TODO.md` 的“发布 MVP”保持未完成；本轮也不冒充 Codespaces 或真实 DeepSeek/MiMo 验收。
