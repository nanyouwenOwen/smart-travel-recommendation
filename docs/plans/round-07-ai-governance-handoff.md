# Round 07：AI 治理、真实模型人工接入与项目交接封板计划

## 1. 目标与边界

本轮只做治理、文档一致性、真实模型接入说明和交接证据封板，不新增旅游业务能力，也不把当前会话、任何密钥或未验证的供应商能力写入运行时。

目标：

1. 把“独立规划 → 实施 → 独立审核与测试 → 修正复审 → 交付”的工作模式固化为仓库级约束，保证今后所有非简单修改都沿用相同循环。
2. 严格区分产品/工程资料与 AI 协作资料：业务契约、部署、运维继续留在常规 `docs/` 主题文档；AI 流程、审计、人工动作和后续模型交接只放在 `docs/ai-governance/`。
3. 如实说明当前 Codex 会话不能作为可导出的运行时模型嵌入本项目；项目继续保留 Stub，真实 AI 由负责人日后用自己的 DeepSeek 或 Xiaomi MiMo 凭据，通过现有 OpenAI-compatible 后端适配器启用。
4. 提供一份不依赖当前对话上下文的完整交接入口，让后续工程师或大模型能够定位权威资料、复现测试、理解风险与授权边界。
5. 核对现有草稿、README、配置、实际适配器和 Round 06/CI 证据，修正文档漂移；不以文档修改掩盖尚未执行的人工验收或正式发布。

非目标：

- 不申请、生成、接收、验证或提交任何 API key、Token、密码和个人账号资料。
- 不将本次 Codex 会话身份、内部能力或访问凭据包装成项目 API。
- 不替用户注册、充值或启用 DeepSeek/MiMo，不对真实供应商产生请求或费用。
- 不改业务接口、数据库、前后端功能、Stub 行为或供应商适配器；若审计发现代码兼容性缺陷，应记录为新一轮计划，而不是在本轮顺手扩展。
- 不创建 tag、GitHub Release、生产部署或勾选 `TODO.md` 的“发布 MVP”；这些动作需要负责人明确授权及实际证据。

## 2. 已核验事实与风险

### 2.1 当前实现边界

- 后端由 `AI_BASE_URL`、`AI_API_KEY`、`AI_MODEL` 配置共享的 OpenAI-compatible Chat Completions 客户端；行程与咨询分别由 `TRIP_AI_PROVIDER`、`CONSULTATION_AI_PROVIDER` 切换。
- 两个真实 provider 都从后端向相对路径 `/chat/completions` 发请求，使用 `Authorization: Bearer`；浏览器不应接触供应商密钥。
- 本地/CI 使用确定性 Stub；生产 profile 选择真实 provider 且缺少 key 时应启动失败，不允许真实请求失败后静默伪装成 Stub 回答。
- 当前会话无法被项目长期调用：会话运行资源、身份和凭据不是项目资产，也没有可安全导出的服务端 API key。

### 2.2 供应商资料基线

实施阶段必须在提交当天再次打开官方文档核验，并把“核验日期”与官方链接写入人工操作文档；模型 ID、价格、限流和退役时间均视为时效信息，不应作为永久保证。

- DeepSeek 官方 OpenAI-compatible base URL 当前为 `https://api.deepseek.com`，Chat Completions 模型示例为 `deepseek-v4-flash` / `deepseek-v4-pro`；官方首页同时给出旧名称退役提示：[DeepSeek API 官方文档](https://api-docs.deepseek.com/)。
- Xiaomi MiMo 官方 OpenAI-compatible pay-as-you-go base URL 当前为 `https://api.xiaomimimo.com/v1`，Chat Completions 地址为 `/v1/chat/completions`，当前官方示例模型为 `mimo-v2.5-pro`；Token Plan 使用不同 base URL 和凭据，不能混用：[MiMo 首次 API 调用](https://mimo.mi.com/docs/quick-start/first-api-call)、[MiMo Chat Completions](https://mimo.mi.com/docs/en-US/api/chat/openai-api)。
- MiMo 官方同时接受 `Authorization: Bearer`，因此与当前适配器认证方式表面兼容；结构化输出、流式分片、错误码和字段容忍度仍必须由负责人用真实账号在非生产环境验收后才能宣布兼容。

主要风险：URL 是否应包含 `/v1` 的供应商差异、模型 ID 漂移、结构化 JSON 与 SSE 分片差异、推理模式默认行为、429/超时与费用、将密钥误放进前端变量或 Git 历史，以及把未执行的人工验收写成已通过。

## 3. 文档分区与权威性约束

### 3.1 工程与产品区

下列资料继续放在根目录和普通 `docs/` 主题文件中，描述项目本身而不是 AI 协作过程：

- `README.md`：目标、版本、核心规则、启动、配置和当前能力。
- `TODO.md`：实际通过验证后的功能/交付状态。
- `docs/openapi.yaml`、`docs/api-contract.md`、`docs/data-model.md`：机器契约、人类契约和数据事实。
- `docs/deployment.md`、`docs/operations.md`、`docs/backup-restore.md`、`docs/troubleshooting.md`、`docs/release-checklist.md`：工程交付资料。
- `docs/plans/`、`docs/reviews/`、`docs/reports/`：每轮范围、独立结论与可重复验证证据。

### 3.2 AI 治理区

`docs/ai-governance/` 只保存：

- `README.md`：本目录入口、范围和禁止项。
- `WORKFLOW.md`：强制循环、角色隔离、最低测试矩阵与交付权限。
- `HUMAN_ACTIONS.md`：真实模型、Codespaces、Release 和生产准备中必须人工完成的动作。
- `PROJECT_HANDOFF.md`：后续模型/工程师审查入口、当前状态、已知限制和接手步骤。
- `AI_CHANGE_LOG.md`：每轮 AI 参与、关键判断、失败修正和证据索引；历史 `docs/ai-usage-log.md` 只能作为旧记录，宜在交接文档中明确其兼容位置，不继续制造两个并行的当前事实源。

两区不得复制形成互相冲突的配置或状态。工程事实以代码、OpenAPI 和工程文档为准；AI 治理文档引用这些事实并记录核验提交，不成为第二份接口契约。任何目录都禁止保存密钥、完整私密提示词、个人数据或原始生产日志。

## 4. 实施步骤

### 4.1 固化仓库级工作模式

1. 审核根 `AGENTS.md`，确保每个非简单变更都强制执行五阶段循环，并明确：规划终端不能实现；独立审核终端不能修改实现；失败由实施终端修正，再由同一审核者复审。
2. 在 `docs/ai-governance/WORKFLOW.md` 补全角色职责、状态枚举、证据要求、TODO 勾选时机、CI 等待规则和外部副作用授权边界。
3. 规定每轮产物命名和最小内容：`docs/plans/round-XX-<topic>.md`、`docs/reviews/round-XX-<topic>-review.md`、必要报告和 AI change-log 条目。
4. 明确小改动可以缩短篇幅但不可取消独立审核；纯文字勘误也至少记录范围、检查链接/格式和独立结论。

### 4.2 整理治理目录且保持分区

1. 审核 `docs/ai-governance/README.md` 的目录索引与禁止项，确保所有链接有效。
2. 统一 `AI_CHANGE_LOG.md` 与历史 `docs/ai-usage-log.md` 的关系：前者作为后续短索引，后者保持历史证据，不在常规产品文档中混入过程细节。
3. README 只提供简短治理入口，不复制完整人工步骤；人工操作和模型交接详情仅留在治理目录。
4. 更新目录树/链接时清晰标出 `docs/ai-governance/`，避免读者把治理记录误认为运行时模块。

### 4.3 完善真实模型人工接入清单

1. 在 `HUMAN_ACTIONS.md` 首先解释“为何不能直接嵌入当前 Codex”，再给出 DeepSeek/MiMo 二选一流程；不要求用户提供密钥给代理。
2. 对每个供应商分别写明：官方控制台/文档入口、提交当天核验过的 base URL、模型 ID 获取方式、凭据放置位置、现有五个环境变量以及 Stub 回退开关。
3. 明确 URL 拼接规则：`AI_BASE_URL` 必须使当前客户端追加 `/chat/completions` 后得到供应商官方地址；DeepSeek 与 MiMo 示例分别单独列出，Token Plan 另列且不得与按量付费 URL/key 混用。
4. 密钥只能进入未提交的本地 `.env`、Codespaces Secret 或部署平台 secret manager；禁止进入 Git、`.env.example` 的值、`VITE_*`、浏览器、Dockerfile、聊天和日志。
5. 提供人工 smoke 清单：先在非生产环境启用一个供应商；验证启动失败保护、注册、结构化行程、调整、普通咨询、SSE 增量/终态、中文输出、超时、429、日志脱敏和账单；记录供应商、模型、日期、commit SHA 和脱敏结果，不记录 key。
6. 明确只有上述真实账号测试通过，文档才能从“预期兼容”改为“已验证兼容”；Stub 必须永久保留给 CI/离线演示，但线上真实 provider 故障不得静默降级 Stub。

### 4.4 形成完整审查与交接记录

1. `PROJECT_HANDOFF.md` 必须能独立回答：项目做什么、技术栈、当前完成度、权威契约、架构信任边界、配置入口、测试命令、最近独立审核/CI、已知限制、人工阻断和下一轮开始步骤。
2. 引用最近实际存在的 commit、CI run、artifact/校验和及 Round 06 结论；如果治理提交尚未经过新 CI，清楚标成“待本轮推送后验证”，不得沿用旧 run 冒充新提交证据。
3. 核对 `docs/reviews/round-06-quality-delivery-review.md` 与 `docs/reports/` 是否存在已知证据漂移；本轮只同步可由仓库或 GitHub 实证确认的值，并保留 Codespaces 人工验收、真实模型验收和正式 Release 的未完成状态。
4. `AI_CHANGE_LOG.md` 追加 Round 07：用户要求、规划/实施/审核角色、实际修改、失败与修正、验证命令、commit/CI 和未完成的人工动作。

### 4.5 一致性审计但不扩展业务

实施者逐项比对：

- README 的 AI 默认值/生产值是否与 `application.yml`、`application-prod.yml`、`.env.example` 和 Compose 一致。
- `HUMAN_ACTIONS.md` 的变量名是否在代码中真实存在；base URL 与 `uri("/chat/completions")` 拼接是否正确。
- Stub、live、prod、Codespaces 的描述是否与实际 profile/脚本一致。
- TODO 只保留“发布 MVP”未勾选；治理工作不伪装成新的产品完成项。
- 所有相对路径、Markdown 链接、官方外链、commit/run 引用和文件名有效。

如发现需要修改代码、接口或测试才能成立的配置声明，停止扩大本轮范围：在审核报告列出 blocker，并为下一轮创建独立计划。

## 5. 安全与准确性验收

必须满足：

1. `git diff` 中不存在形似真实 key、Bearer Token、JWT、密码、个人邮箱/手机号或生产日志；示例只使用明显占位符。
2. `.gitignore` 仍覆盖 `.env` 等本地 secret 文件；文档没有引导把密钥放入 `VITE_*`、客户端存储、镜像层或 GitHub 普通变量。
3. DeepSeek/MiMo 的 base URL、认证方式和模型示例附官方直链与核验日期；文案使用“当前官方示例/配置当天复核”，不承诺永久有效。
4. “Codex 不能直接嵌入”“当前仍为 Stub”“真实兼容性尚待账号验收”“MVP 尚未正式 Release”四个状态在 README、人工清单、交接和 TODO 中不矛盾。
5. 产品/技术文档与 AI 治理文档边界明确，不把 API 契约搬入治理目录，也不把 AI 私密过程写进 README/OpenAPI。
6. 后续接手者仅凭仓库即可知道从何开始、运行哪些门禁、哪些动作需要用户授权；不存在依赖当前聊天记录才能理解的关键结论。

## 6. 验证与独立审核

### 6.1 实施者验证

本轮不改生产代码，但仍执行与仓库封板状态相称的验证：

```bash
git diff --check
bash -n scripts/*.sh
bash scripts/check.sh
docker compose config >/dev/null
docker compose -f docker-compose.yml -f docker-compose.prod.yml config >/dev/null
```

另执行只读审计：

- 使用仓库搜索核对所有 AI 环境变量、provider 名、`/chat/completions` 路径与文档示例。
- 检查治理目录内外的 Markdown 相对链接均指向存在文件；抽查官方供应商外链可访问。
- 对本轮 diff 运行 secret 扫描（沿用 CI 固定版本/策略），确认示例占位符不被误识别为真实凭据且没有泄漏。
- 核对 `git status`，确保没有测试产物、`.env`、密钥或无关用户文件进入候选提交。

### 6.2 独立审核要求

独立审核终端不得编辑实现或治理草稿，只创建 `docs/reviews/round-07-ai-governance-handoff-review.md`。审核至少包括：

1. 逐条对照本计划、用户要求、AGENTS、TODO 和实际 diff，给出 `PASS` 或 `FAIL`；不使用模糊的“基本通过”掩盖 blocker。
2. 复核文档分区、链接、配置变量与代码、URL 拼接、供应商官方资料和时效措辞。
3. 执行 secret/隐私审计以及本节门禁，确认没有把未运行的真实模型、Codespaces 或 Release 写成已通过。
4. 检查交接资料能否让不了解对话的后续模型独立开展下一轮。
5. FAIL 时列出具体文件/位置、风险和最小修正条件；由实施终端修正后，同一审核者重新运行受影响检查并更新最终结论。

### 6.3 CI 与交付证据

1. 独立本地审核 PASS 后才可提交并推送普通文档/约束变更。
2. 推送后必须等待该提交对应的 GitHub Actions 全部 required jobs 结束；旧提交的绿色 run 不能替代本轮结果。
3. 若 CI 失败，回到实施/复审循环；不得仅在文档里解释后宣布完成。
4. CI 全绿后，将 commit SHA、run URL/ID、job 状态和审核结论写入交接/变更索引；若这需要提交后再写一次，使用同样的轻量规划—实施—独立复核闭环。
5. 不创建 tag/Release；“发布 MVP”保持未勾选，直到负责人明确授权并能记录真实 Release 证据。

## 7. 完成定义

仅当以下条件全部成立，本轮可结束：

- 根 `AGENTS.md` 与 `docs/ai-governance/WORKFLOW.md` 一致固化强制循环。
- 治理目录结构、README 入口、人工动作、交接和 AI change-log 内容完整且职责不重叠。
- DeepSeek/MiMo 接入说明与现有代码配置准确，官方链接和核验日期齐全，真实兼容性被正确标为待人工验收。
- Codex 不可直接嵌入、Stub 保留、secret 边界、Codespaces/Release 人工动作都被明确记录。
- 工程资料与 AI 治理资料分区清晰，仓库没有 secret、无关产物或业务范围扩张。
- 实施门禁通过，独立审核最终 PASS，本轮提交对应 CI 全绿，交接记录包含可追溯证据。
- `TODO.md` 的“发布 MVP”仍未勾选；项目实现封板与正式对外发布的区别清楚。
