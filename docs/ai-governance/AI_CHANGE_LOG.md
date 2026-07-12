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

## 2026-07-12 - 安全与候选产物正向门禁

- 触发：Round 09 审核证明旧 security job 忽略 unfixed 漏洞，且无认证审核无法下载 artifact 核对内部绑定，发布清单第 2/5 项因此不成立。
- 实施：security job 先从当前 checkout 打包实际后端 JAR，再用固定 Trivy 0.58.2 对所有 High/Critical（包含无修复版本项）阻断；secret 扫描改为仅扫描 `git archive HEAD` 的受版本控制源文件。新增候选校验脚本，上传前验证固定文件集、完整 Git SHA、不自包含的 SHA-256 清单、双 CycloneDX SBOM 和 JAR/tar 可读性，并写入脱敏 Actions summary。
- 验证证据：完整 `scripts/check.sh`、Shell 语法和 diff check 通过；实施与独立 reviewer 均重建候选并正向通过，reviewer 对删除/置空/SHA 错误/篡改/自包含清单/坏 SBOM/坏 JAR/坏 tar/额外文件 9 类负向用例均确认非零退出。提交 `0f1930b` 的 GitHub Actions run `29166218083` 七个 job 全绿，security 四个关键步骤和候选上传前校验均成功，产物 digest 见 `PROJECT_HANDOFF.md`；Round 10 独立审核最终 `PASS`。

## 2026-07-12 - v0.1.0 正式发布授权与机制

- 用户决定：明确授权创建并推送 annotated tag `v0.1.0`，创建 GitHub Release 并附加候选产物。
- 认证边界：本地终端有 Git SSH 推送权限，但无 GitHub API token 且未安装 `gh`；不索取长期 token，而是使用精确 tag 触发的 Actions release job 及仅该 job 具有的短期 `contents: write` `GITHUB_TOKEN`。
- 原子性：tag run 重跑全部质量链并使用同 run artifact；只接受 annotated `v0.1.0` 且目标必须为 `origin/main` 可达提交。Release 先作为 draft 组装，八个固定附件远端下载并重做 SHA/校验和/SBOM/归档校验后才公开。在实际远端核验前 `TODO.md` 不勾选。

## 2026-07-12 - 发布前 Compose smoke 失败可观测性

- 触发：发布机制提交 `7fd4abc` 的 main CI run `29175449348` 中 `container-smoke` 以 1 退出，候选/发布 job 跳过；公开页面只显示通用退出码，不能定位阶段，因此未创建 tag。
- 实施：不猜测根因也不放宽任何断言、轮询、超时或性能阈值；为十个阶段维护固定安全名称，非零 EXIT 时发出经转义的单条 GitHub error annotation，包含阶段、最后脱敏观测和原退出码，然后保持原有有界诊断与清理。
- 定向证据：新增测试覆盖显式/直接/管道失败、自定义退出码、INT 130、TERM 143、GitHub/本地输出、workflow-command 注入转义及成功静默路径；对摘要、诊断和资源清理三个边界分别注入真实非零返回，均确认仍保留原始退出码 37。本轮准确 main CI 尚待推送后验证。
