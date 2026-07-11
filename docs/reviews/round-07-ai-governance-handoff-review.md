# Round 07：AI 治理、真实模型接入与交接独立审核

## 首轮结论

**FAIL（3 个文档准确性/治理阻断项）**。

本结论不把真实供应商账号测试、干净 Codespaces 验收或正式 tag/Release 当作本轮实现失败：三者均属于已正确保留的人工授权边界。阻断项来自当前文档对既有代码和官方供应商约束的表述尚不够准确，以及强制审核状态与计划不一致。

## 已通过项

- 根 `AGENTS.md` 已建立“计划 → 实施 → 独立审核/测试 → 修正复审 → 交付”仓库级约束，且明确规划者不得实现、审核者不得修改实现、TODO 只能在证据通过后更新。
- `README.md` 只保留治理入口；AI 流程、人工动作、变更索引和模型交接集中在 `docs/ai-governance/`，工程契约与部署资料仍位于普通 `docs/`，分区符合用户要求。
- “当前 Codex 会话不可导出为项目运行时”“默认仍为 Stub”“真实供应商尚待人工账号验收”“MVP 尚未 tag/Release”四个边界基本一致，没有把未执行的外部动作写成已通过。
- 代码核对通过：行程和咨询 provider 均向 `AI_BASE_URL` 追加 `/chat/completions`；共享 `AI_API_KEY` 以 Bearer 发送；`.env.example`、基础 Compose 与 Spring 配置使用相同的五个 AI 变量，Compose 已透传 `AI_BASE_URL`、`AI_API_KEY`、`AI_MODEL`。
- secret 边界正确：密钥被要求仅放未提交 `.env`、Codespaces Secret 或部署 secret manager，明确禁止 `VITE_*`、浏览器、镜像层、聊天和 Git；仓库扫描只命中后端安全测试中的故意伪造 `sk-abcdefghijklmnop` 样例，没有发现候选变更中的真实凭据。
- `PROJECT_HANDOFF.md` 已提供项目状态、权威契约、信任边界、测试入口、已知限制和新 round 起步步骤；Round 06 审核、Codespaces 人工验收及 Release 授权边界没有混淆。

## 阻断项

### B1. DeepSeek 行程适配兼容性已知不成立，却仍被概括成与当前适配器匹配

`docs/ai-governance/HUMAN_ACTIONS.md` 开头称 DeepSeek 与 MiMo “都有与当前后端适配器匹配”的接口，随后只用条件句表示供应商“如果”仅支持 `json_object` 才会失败。实际代码 `OpenAiCompatibleTripPlanningProvider` 固定发送 `response_format.type=json_schema`；2026-07-12 核验的 DeepSeek 官方 Chat Completions 参考只允许 `text`、`json_object`。因此 DeepSeek 的咨询接口可预期兼容，但当前行程生成是**已知协议不兼容**，不是仅待账号确认的未知项。

最小修正：在人工清单和交接中明确区分“咨询预期兼容”与“行程当前已知需要供应商专用适配/改用 `json_object` 后在本地继续做 Schema 校验”，不要指导负责人只填 DeepSeek key 就期望全部功能工作；仍应保留真实账号 smoke 作为最终验收。

官方依据：[DeepSeek Chat Completions](https://api-docs.deepseek.com/api/create-chat-completion/)（`response_format` 仅列 `text` / `json_object`，核验日期 2026-07-12）。

### B2. Xiaomi MiMo Token Plan 的用途限制与独立配置缺失

计划要求 MiMo Token Plan 使用不同 base URL/key 时单列且不得混用，但 `HUMAN_ACTIONS.md` 只描述按量付费 URL，没有说明 Token Plan。更重要的是，MiMo 官方 Token Plan 当前限定 AI 编程工具场景，明确禁止自定义应用后端等非 Coding API 调用；本旅游助手不应被引导使用该套餐。仅写“注册/充值并创建 key”不足以让负责人避开错误计费产品。

最小修正：明确推荐本项目使用按量付费 `https://api.xiaomimimo.com/v1`；列出 Token Plan 的独立 base URL/key 类型只用于辨识，明确其当前条款不适用于本旅游后端并要求配置当天复核，不指导实际接入。MiMo 官方 Chat 文档已确认按量接口支持 `Authorization: Bearer`，所以当前认证头表面兼容；`json_schema`、SSE 分片仍保留人工验收状态。

官方依据：[MiMo 首次 API 调用](https://mimo.mi.com/docs/quick-start/first-api-call)、[MiMo Chat Completions](https://mimo.mi.com/docs/en-US/api/chat/openai-api)、[MiMo Token Plan](https://mimo.mi.com/docs/en-US/price/token-plan)（核验日期 2026-07-12）。

### B3. 强制审核状态枚举不一致

`AGENTS.md` 和本轮计划要求独立审核明确给出 `PASS` 或 `FAIL`，但 `docs/ai-governance/WORKFLOW.md` 又允许 `CONDITIONAL PASS`。这会让后续未知上下文模型用模糊状态绕过 blocker，与用户要求及当前仓库级约束冲突。

最小修正：WORKFLOW 只允许 `PASS` / `FAIL`；存在任何阻断就是 FAIL，非阻塞观察项可在 PASS 报告中单列。并将“必要 CI”写成该提交对应 required jobs，避免旧 run 被当作新修改证据。

## 验证证据

- `git diff --check`：PASS。
- `bash -n scripts/*.sh`：PASS。
- `bash scripts/check.sh`：PASS；OpenAPI 3.1 有效（37 个既有非阻断 warning），前端 18 个文件/43 项 Vitest 通过、覆盖率门通过、构建通过，后端 Maven verify/格式/静态分析通过。
- `docker compose config`：PASS。
- 带明显测试占位值展开 production merged Compose：PASS；`SPRING_PROFILES_ACTIVE=prod` 存在，AI 三个共享配置和两个 provider 均透传。
- 全仓库 Markdown 本地链接存在性检查：PASS。
- 本地 Trivy CLI 不可用；以固定模式补充扫描候选工作树，没有发现真实 key、Bearer token、私钥或 GitHub token。CI 固定版本 Trivy 仍须在本轮提交推送后给出最终 secret gate。

## 复审条件

由实施终端仅修正文档 B1–B3 后，原审核终端重新核对官方链接、代码字段、Markdown 链接、secret 扫描、`git diff --check` 和 `scripts/check.sh`。复审 PASS 后才可提交推送；本轮提交对应 GitHub Actions 全绿后再追加 commit/run 证据。不得创建 tag、Release，不得勾选 `TODO.md` 的“发布 MVP”。

## 第一次复审（2026-07-12）

**FAIL（B1–B3 已关闭，剩余 2 个交接证据阻断项）**。

- B1 已关闭：人工清单和交接已明确 DeepSeek 咨询仅为预期兼容，当前 `json_schema` 行程请求已知不兼容，并给出 `json_object` 加本地 Schema 校验的后续适配边界。
- B2 已关闭：MiMo 已限定推荐按量付费接口；Token Plan 的独立凭据/配置、不可混用和不适用于本旅游后端的官方用途限制均已写明。
- B3 已关闭：WORKFLOW 现在仅允许 PASS/FAIL，并要求本次提交对应的 required jobs，旧 run 不可替代。
- 复核 `git diff --check`、全仓库 Markdown 本地链接和固定模式 secret 扫描继续通过；仅命中既有安全测试中的伪 key 以及本审核报告对该伪 key 的说明。

### B4. PROJECT_HANDOFF 缺少计划要求的完整候选追溯字段

`PROJECT_HANDOFF.md` 仍只写 GitHub Actions run `29164101865`，未写计划 4.4.2 要求的候选 commit、artifact 名称和 digest/校验信息，也未明确本轮治理提交及对应 CI 尚待推送后验证。未知上下文模型无法仅凭交接入口区分旧候选证据与本轮待产生证据。

最小修正：补充候选 commit `b978503`、artifact `smart-travel-assistant-0.1.0-rc`、Actions digest `sha256:c64e81a442cc716cae8008d69c77c8ff2f8e4d36ff899b029bf889e0644c1640`，并明确这些属于 Round 06；Round 07 commit/run 当前待提交推送后填写，不能用旧 run 替代。

### B5. AI_CHANGE_LOG 尚未形成实际 Round 07 审计链

`AI_CHANGE_LOG.md` 目前没有记录独立首审 FAIL、B1–B3 的修正以及本轮实际运行的门禁；“依据 OpenAI 官方资料核对”也与本轮实际核验的 DeepSeek/MiMo 官方资料不符。这不满足计划要求的角色、失败修正和验证证据索引。

最小修正：追加规划/实施/独立审核角色，记录首审三项失败与修正、实际本地门禁结果和仍待本轮 CI 的状态；把供应商资料来源表述校正为 DeepSeek/MiMo 官方文档。不得预写本轮 commit/run 成功。

## 最终复审（2026-07-12）

**PASS（本地实现与治理交接文档）**。

- B4 已关闭：`PROJECT_HANDOFF.md` 已完整记录 Round 06 候选提交 `b978503`、run `29164101865`、artifact 名称及完整 digest，并明确 Round 07 的提交 SHA/CI 尚待推送后产生，旧证据不能替代。
- B5 已关闭：`AI_CHANGE_LOG.md` 已按真实过程记录用户要求、DeepSeek/MiMo 官方资料核验、首审 FAIL、B1–B3 修正、第一次复审新增要求、实际本地门禁及 Trivy 不可用边界，没有预写本轮提交或 CI 成功。
- 最终重新执行 `git diff --check`、Markdown 本地链接检查、production merged Compose config：PASS。
- 最终固定模式 secret 扫描仅命中后端安全测试中的故意伪 key，以及本报告对该测试值的说明；候选变更没有真实凭据。
- `scripts/check.sh` 的本轮完整结果沿用首审后同一工作树实跑证据：OpenAPI 有效（37 个既有 warning）、前端 18 文件/43 测试、构建及覆盖率门通过、后端 Maven verify/格式/静态分析通过。后续修正仅涉及 Markdown，没有改变生产代码或测试配置。

当前没有本地阻断项。实施方可以提交并推送普通文档/配置变更，然后必须等待**该 Round 07 提交对应**的 GitHub Actions required jobs 全绿并追加 commit/run 证据；在此之前不得把本轮交付描述为最终 CI 完成。干净 Codespaces、真实 DeepSeek/MiMo 账号验证和正式 tag/Release 继续是明确人工动作，不影响本次本地 PASS，也不得被标成已完成。
