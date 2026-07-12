# AI 变更索引

详细历史见 [`../ai-usage-log.md`](../ai-usage-log.md)。本文件从项目封板开始提供短索引，后续每轮追加一项。

## 2026-07-12 - 性能烟测稳定测量边界

- 触发：多个 main run 在相同 `container-smoke` 第 7/10 阶段偶发 k6 阈值失败，而相邻运行通过；旧 annotation 没有实际 p95、失败率或端点证据，因此没有把原因臆测为 runner 噪声。
- 实施决定：保留正式 10 VU/30 秒、错误率 `<1%`、p95 `<2000ms` 和前后端双请求，不做自动重试。新增五轮有界双端点预热并从正式指标中隔离；正式请求用 phase/endpoint 标签，成功或失败均生成结构化 summary，CI `always()` 保存，Compose 同时记录有界资源快照。
- 验证：Shell mock 覆盖有效/缺失/畸形 summary、k6 原退出码优先级和门禁常量；受控 HTTP fixture 使用官方 k6 v0.57.0 实际执行生产 smoke，3 类预热失败与 transport/HTTP/content/p95 4 类正式失败共 7/7 通过。Round 15 同一独立审核端复跑后结论 PASS；准确远端指标和 Release 结果待推送后补录。

## 2026-07-12 - Xiaomi MiMo Token Plan Anthropic 适配

- 用户决定：后端支持 Xiaomi MiMo，指定官方平台、`https://token-plan-cn.xiaomimimo.com/anthropic` 和模型 `mimo-v2.5`。
- AI 实施：独立规划 Round 14；新增 `xiaomi-mimo-anthropic` 行程与咨询 Provider、独立 `XIAOMI_MIMO_*` 配置、Anthropic Messages 普通/流式解析、本地 Mock 契约测试和配置文档。现有 Stub/OpenAI-compatible 未被替换，密钥不回退混用。
- 证据边界：官方公开 Token Plan 页面确认套餐列出 `mimo-v2.5`，但没有使用真实账号/key 发起收费调用，也没有代替负责人确认自定义旅游后端的当前用途条款。自动化结果只证明协议适配；真实用途、费用、key 与三链路 smoke 仍为人工动作。
- 安全：未索取或保存真实 key；Mock 使用明显无效占位值并断言无 Bearer/OpenAI 专属字段。首审与两次复审推动补齐 SSE block 状态机、条件装配、严格 URL、Compose 透传、显式 DTO、Gateway/预算、错误矩阵和 `x-api-key` 脱敏。最终独立审核 `PASS`：Xiaomi/脱敏定向 10 项、后端 `verify` 52 项、SpotBugs 0、前端 43 项、项目总门禁、Compose config 与 diff check 均通过。

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

## 2026-07-12 - v0.1.0 一次性发布恢复

- 触发：annotated `v0.1.0` 已不可变地指向 `52864b1`；tag run `29175974787` 的六个质量 job 和 `release-candidate` 成功，但 `release` 在只读验证步骤失败，发布脚本未执行，公开 Release API 返回 404。未移动 tag，也未勾选 TODO。
- 恢复设计：在 `main` 临时增加一次性 job，只在该恢复提交的完整质量链与候选 job 成功后获得 `actions: read`/`contents: write`。它固定绑定仓库、tag、peeled SHA、源 run ID 和 artifact 名，独立核对源 run 七个成功 job，不使用当前 main 产物；发布成功后将立即删除该一次性写路径。
- MiMo 提交 `7754b7c` 的 run `29188436060` 完整质量链、候选与八项上传均成功，最终在按数字 ID 复验 draft 的 `identity-tag` 阻断。GitHub 的 pending-tag draft 在该 REST 响应中可为空；修正仅对首次已严格按 tag 发现并锁定同一正整数 ID 的 draft 接受空 tag，公开后仍严格要求 `v0.1.0`。状态机测试与独立安全复审 PASS。
- 首次恢复结果：提交 `33e600b` 的 run `29176419521` 中七个现有 job 全绿，recovery 的身份、跨 run 下载和固定候选校验也成功，但发布写步骤立即失败。公开 Release API 仍为 404；具体日志不可读，因此未把推测写成根因。修正按 GitHub 官方 REST 示例使用 Bearer `curl` 调用 `uploads.github.com`，并增加不含 token/附件内容的发布阶段 error annotation；tag 保持不变。
- 第二次恢复结果：提交 `8492ec6` 的 run `29176701107` 中六个质量 job 与 `release-candidate` 全部成功，recovery 的固定身份、源 run、跨 run artifact 和候选校验也全部成功，但写步骤再次以 `stage=upload-assets; exit=1` 失败，公开 Release API 仍为 404。由于公共日志没有上传响应正文，仍不伪造具体 HTTP 根因；实施改用 GitHub CLI 官方的 `gh release upload` 命令处理认证与 upload endpoint，保留草稿清空、固定八附件、远端下载复验和发布后复验。对应 mock 覆盖草稿选择、固定仓库、注入 token、非空文件、错误认证、错误仓库和原退出码 annotation；最终结论等待独立 reviewer 与准确远端 run。
- 第三次恢复结果：提交 `f836ac8` 的 run `29176911435` 再次让六个质量 job、候选 job 和 recovery 的全部只读校验成功，但写步骤精确失败于 `stage=create-draft; exit=1`；结合前一轮已进入上传阶段及本轮公开 API 仍为 404，可确认恢复脚本没有发现既有 draft，继而重复创建发生失败，但不推断 GitHub 内部权限细节。修正让 discovery 使用 GitHub CLI 官方 `release view`/`FetchRelease` 的 pending-tag draft 查询路径；只有精确的 `release not found` 才进入创建，其他认证或 API 故障 fail-closed，并约束资产 API URL 必须属于目标仓库。状态机 mock 新增 draft discovery 与查询故障不可误判为缺失的覆盖；仍待独立审核和准确远端 run。
- draft discovery 修正提交 `6049e18` 的准确 run `29177109032` 未执行发布逻辑：后端、前端、E2E、安全和 OpenAPI 成功，但 `container-smoke` 在 `stage=7/10 performance thresholds` 的 k6 阈值检查以 1 失败，故候选与 recovery 均按依赖关系跳过。没有把该结果写成发布修正失败，也不放宽阈值；在同一 smoke 实现的相邻 run `29176701107`、`29176911435` 通过的背景下，由独立 reviewer 先判断是否允许用本审计记录提交触发一次完整重验。
- 审计重验提交 `3ca0b9c` 的 run `29177210816` 六个质量 job 与候选 job 全部成功，recovery 的固定身份、源产物下载和候选复验也成功；它已通过 `release view` 找到既有 draft，但仍以 `stage=upload-assets; exit=1` 失败。公开 tag 页面只显示源码归档两项，没有八个候选附件，故不把该页面误报为已发布 Release。公共日志没有命令响应正文；下一次修正把八项固定白名单改为逐项官方 CLI 上传以消除并发变量，并仅把固定附件名与 HTTP/已知错误类别写入 annotation，原始响应和 token 不进入日志。仍须独立审核后方可推送。
- 逐项上传提交 `65a7fa0` 的 run `29177426902` 六个质量 job、候选及 recovery 只读校验全部成功；首项 `CHANGELOG.md` 精确报告 `release-not-found`，证明失败来自 `gh release upload` 内部再次查询 Release，而不是附件内容或并发上传。修正复用已成功 discovery 的固定 draft `uploadUrl`，严格要求 uploads host、目标仓库、同一数字 Release ID 和标准模板，再用 `gh api --input` 直接上传，避免第二次 draft 查询；mock 对上传 URL 的错误 host/repo/ID/template、认证、内容类型、文件体、部分失败重试和脱敏分类均 fail-closed。仍待独立审核和准确远端验证。
- 直接 upload URL 提交 `4777efc` 的 run `29177621742` 六个质量 job、候选与 recovery 只读校验均成功，但在任何附件调用前以 `stage=upload-assets; detail=none; exit=1` 失败，因此没有把它误判为 GitHub HTTP 错误。该位置只剩从已严格验证的 Release JSON 再解析同一个 upload URL；修正移除这次冗余读取，使用此前已确认的固定数字 `release_id` 构造同一受限 URL，并在解析后及每项调用前预设固定 detail，使未来任何失败都有明确安全位置。上传、发布及远端复验规则不变；仍待独立审核与准确 run。
- 写前身份校验提交 `fd1f938` 的 run `29177897897` 六个质量 job、候选、recovery 只读校验及八次逐项附件上传均成功，失败已移动到 `stage=validate-uploaded-release`；这证明固定上传 URL 与二进制请求有效，剩余问题仍是上传后再次按 tag 查询 draft。修正保留首次 `release view` 发现与锁定正整数 ID，上传后和公开后改为确定性的认证 REST `releases/{id}` 查询，并继续要求返回 ID 与初始 ID 相同、metadata/八附件/下载候选全部通过。mock 对数字/字符串 ID 切换仍保持 draft、不公开并可安全重试。仍待独立审核和准确 run。
- 固定 ID 重载提交 `e0975b8` 的 run `29178048659` 六个质量 job、候选、只读校验与八项上传均成功，仍在复合的 `validate-uploaded-release` 阶段失败；当前公共 annotation 无法区分固定 ID 查询、身份、metadata、draft、附件集合或下载校验。下一次仅为这些既有 fail-closed 断言设置固定安全 detail，不调整重试、阈值、资产集合或公开条件；根据准确子阶段再决定修正，避免猜测。
- 诊断提交 `55ca6c3` 的 run `29178200671` 再次完成全部质量、候选与八项上传，并把失败定位为上传后固定 ID 响应的 `identity` 复验。下一次只把该既有断言拆为 tag、ID 类型、ID 连续性和 upload URL 四个固定子项，不改变断言本身，以确认 GitHub REST 字段差异。
