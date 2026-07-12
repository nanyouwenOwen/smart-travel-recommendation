# Round 14：Xiaomi MiMo Token Plan Anthropic 协议适配计划

## 背景与已确认输入

用户要求后端支持 Xiaomi MiMo，并明确给出：

- 官方平台：`https://platform.xiaomimimo.com`
- Token Plan Anthropic Base URL：`https://token-plan-cn.xiaomimimo.com/anthropic`
- 模型：`mimo-v2.5`

现有后端的行程生成与旅游咨询虽然已经抽象为 `TripPlanningProvider` 和 `ConsultationProvider`，但唯一真实实现固定调用 OpenAI Chat Completions：向共享 `AI_BASE_URL` 追加 `/chat/completions`、使用 `Authorization: Bearer`，行程还固定发送 OpenAI `response_format.json_schema`，咨询流固定解析 OpenAI `data: {choices[].delta.content}`。Xiaomi 给出的地址是 Anthropic 协议入口，不能通过替换现有三个 `AI_*` 环境变量可靠兼容；本轮应新增独立的 Anthropic Messages 适配器，并保持 OpenAI-compatible 与 Stub 路径不回归。

截至 2026-07-12，公开可见的平台页面确认 Token Plan 包含 `mimo-v2.5`，并把产品定位为 Claude Code、OpenCode 等编程工具的订阅接入；公开页面没有完整展示自定义后端调用的授权范围和全部 Messages 请求细节。用户提供的 URL/模型作为本轮技术契约输入，但实现和 Mock 测试不得被表述为真实账号或商业用途条款验收。启用真实 key 前，项目负责人仍须在控制台核对该 key 可用于本旅游助手后端、费用/配额和最新协议；若条款不允许，不得实际启用。该人工确认不妨碍完成无密钥的协议适配与自动化测试。

## 目标

1. 新增可显式选择的 `xiaomi-mimo-anthropic` provider，同时覆盖结构化行程生成、普通咨询和 SSE 咨询三条业务链路。
2. 按 Anthropic Messages 协议构造 `/v1/messages` 请求、认证头、非流响应与 SSE 事件解析；默认 Xiaomi 配置精确使用用户给出的 Base URL 和模型，但允许通过独立环境变量覆盖。
3. 保持领域层、前端 REST/OpenAPI 业务接口不感知供应商协议；切换供应商不改变现有客户端接口、数据库 Schema 或 SSE 对浏览器的契约。
4. 密钥只从运行时环境/secret manager 注入，配置、日志、异常、测试夹具和文档均不包含真实密钥。
5. 用本地 Mock HTTP server 完整验证出站协议和入站解析，不对 Xiaomi 真实端点发请求、不消耗用户额度；真实连通性作为有 key 且确认条款后的人工验收。

## 范围边界

### 本轮包含

- Xiaomi/Anthropic 专用配置、启动校验和两个 Provider 实现（或共享的低层 Messages 客户端加两个领域适配器）。
- 行程 JSON 输出提示、提取、反序列化和既有 `TripPlanValidator` 二次语义校验。
- 咨询普通响应与供应商 SSE 到现有 `Consumer<String>`/浏览器 SSE 生命周期的转换。
- HTTP 错误、连接/读取超时、取消和 usage 字段到既有稳定异常/结果类型的映射。
- Mock server 契约测试、现有 Stub/OpenAI 回归、配置/部署/人工操作/交接文档更新。

### 本轮不包含

- 创建 Xiaomi 账号、购买 Token Plan、充值、生成/读取 key，或向真实端点发收费请求。
- 把密钥提交到仓库、GitHub Actions 普通变量、日志、截图、测试报告或 AI 治理文档。
- 修改前端业务 API、OpenAPI 公共请求/响应、数据库迁移或默认无密钥 CI 行为。
- 运行时自动从 Xiaomi 降级到 Stub；真实 provider 配置错误或调用失败必须显式失败。
- 宣称 Token Plan 的非编程自定义后端用途已经获得 Xiaomi 授权。真实启用前必须由负责人核对当前控制台条款。

## 协议与配置设计

### 1. Provider 选择和独立命名空间

保留现有 `stub`、`openai-compatible`，新增稳定值 `xiaomi-mimo-anthropic`。行程与咨询仍可分别选择，但两者选中 Xiaomi 时读取同一个独立配置对象，例如：

| 配置/环境变量 | 默认值 | 约束 |
| --- | --- | --- |
| `app.ai.xiaomi-mimo.base-url` / `XIAOMI_MIMO_BASE_URL` | `https://token-plan-cn.xiaomimimo.com/anthropic` | HTTPS；去除结尾 `/` 后由客户端只追加 `/v1/messages` |
| `app.ai.xiaomi-mimo.api-key` / `XIAOMI_MIMO_API_KEY` | 空 | 仅选中 Xiaomi provider 时必须非空；不得回退读取 `AI_API_KEY`，避免两类套餐凭据混用 |
| `app.ai.xiaomi-mimo.model` / `XIAOMI_MIMO_MODEL` | `mimo-v2.5` | 非空，持久化/响应中记录实际配置模型 |
| `app.ai.xiaomi-mimo.anthropic-version` / `XIAOMI_MIMO_ANTHROPIC_VERSION` | 实施时按 Xiaomi 控制台当前示例锁定 | 非空，作为 `anthropic-version` 头；不能静默省略 |
| `app.ai.xiaomi-mimo.max-output-tokens` / `XIAOMI_MIMO_MAX_OUTPUT_TOKENS` | 有界正整数 | Anthropic Messages 的 `max_tokens` 必填值；设置合理上限以覆盖 30 天行程 |

`XIAOMI_MIMO_BASE_URL` 的契约必须写清：它是用户给出的 `/anthropic` Base URL，不是完整 `/v1/messages`，也不是 OpenAI `/v1` 地址。URL 拼接应由单一函数完成并用测试覆盖有/无末尾斜杠，禁止得到重复 `/anthropic/anthropic` 或 `/v1/messages/v1/messages`。

认证按 Xiaomi Token Plan 控制台针对 Anthropic-compatible 客户端给出的当前配置实现。实施前必须再次读取可访问的官方配置指南；若指南确认 Anthropic 标准，则请求至少发送 `x-api-key: <key>`、`anthropic-version: <version>` 和 `Content-Type: application/json`。不得同时发送 Bearer key，除非 Xiaomi 官方明确要求；协议测试应断言密钥只出现在预期认证头中。若公开文档仍不足，代码保持明确可配置的认证策略但不接受任意用户提供的 header 名，真实验收等待负责人从控制台确认，不能猜测后宣称兼容。

### 2. Anthropic Messages 请求映射

共同请求端点为 `POST {base-url}/v1/messages`，基本字段为：

- `model`: `mimo-v2.5`（或显式配置值）；
- `max_tokens`: 独立配置的正整数；
- `system`: 顶层系统提示，不把 `system` 塞进 `messages`；
- `messages`: 仅允许 `user`/`assistant` 历史消息，内容采用 Anthropic 支持的文本 content block 或明确验证过的字符串形式；
- `stream`: 普通请求为 `false` 或省略，流式咨询为 `true`。

不得把 OpenAI 专属 `response_format`、`stream_options` 或 `choices` 假设复用到此请求。构造 DTO 应使用显式 record/class，而不是在多个 Provider 中散落未经约束的 `Map<String,Object>`；这样 Mock 测试可检查未知字段不会被意外发送。

### 3. 结构化行程生成

Anthropic Messages 协议不能假定支持现有 OpenAI `response_format.type=json_schema`。行程适配按以下层次实现：

1. 继续读取版本化 `trip-planner/v1/system.txt` 与 `schema.json`，保持业务 Schema 的单一来源。
2. 在顶层 `system` 中追加供应商专用、固定且受测试的输出约束：只返回一个符合所附 JSON Schema 的 JSON 对象，不使用 Markdown 代码围栏或解释文字；用户的 `TripPlanningRequest` 始终作为独立 JSON 数据块发送，不能拼接成可覆盖系统约束的指令。
3. 若 Xiaomi 官方 Messages 文档明确支持 JSON Schema/tool input，可在实施审核中选择受官方支持的结构化机制，但必须把工具调用/文本两种响应类型的具体契约锁入测试；不得臆造 Anthropic 字段。没有明确官方支持时采用纯 JSON 文本路径。
4. 非流响应只接受 `content` 数组中明确的 `type=text` block；拼接所有文本块后做严格 JSON 提取。首选整个去空白文本必须是单个 JSON 对象；如决定容忍 ```json 围栏，只能剥离唯一、完整的一层围栏并有正负测试，禁止从任意说明文字中搜索第一个 `{...}`。
5. 反序列化为既有 `TripPlan`，随后必须继续经过 `TripPlanValidator` 和 `BudgetCalculator`；模型输出缺字段、额外字段、错日期/天数、时间重叠、负费用等仍按 `AI_OUTPUT_INVALID` 拒绝，不能持久化半成品。
6. 解析 Anthropic `usage.input_tokens` / `usage.output_tokens`（如行程元数据当前可保存则记录），`providerName()` 返回稳定 `xiaomi-mimo-anthropic`，`modelName()` 返回实际模型。

### 4. 普通咨询响应

将 `ConsultationPrompt` 中的系统消息提取为顶层 `system`，其余 user/assistant 消息保持顺序。必须定义异常历史角色的处理：未知角色或多个互相冲突的 system 内容在调用前确定性拒绝，不能原样发给供应商。

响应必须验证：

- HTTP 成功且 JSON 可解析；
- `content` 是数组，至少有一个非空 `type=text` block；
- 多个文本 block 按顺序拼接，不把 `tool_use`、thinking 或未知 block 的内部内容展示给用户；
- `usage.input_tokens`、`usage.output_tokens` 映射到 `ConsultationResult`，缺失时为 `null`；
- 空内容、仅未知 block、截断/非法 JSON 均为 `AI_OUTPUT_INVALID`。

### 5. 流式咨询与取消

供应商 SSE 由 Provider 内部消费，浏览器仍接收项目现有的 `token/done/error` 事件，不能把供应商原始事件直接透传。解析状态机至少覆盖 Anthropic Messages 事件：

- `message_start`：读取初始 `usage.input_tokens`；
- `content_block_start`：只跟踪 `type=text` 的 block index；
- `content_block_delta`：只接受对应文本 block 的 `delta.type=text_delta` 和 `delta.text`，逐片调用现有 `chunks.accept(text)`；
- `content_block_stop`：关闭对应 block；
- `message_delta`：读取最终 `usage.output_tokens`（兼容官方可能返回的累计/增量语义，以 Xiaomi 文档为准）；
- `message_stop`：成功结束；
- `ping`：忽略；
- `error`：解析供应商错误类型后映射稳定业务错误，不向客户端泄露原始响应或请求。

解析器应同时识别 SSE 的 `event:` 与 `data:` 行、空行分隔、CRLF、注释/keepalive、多行 `data:` 的标准组合，不依赖每行都恰好是完整 JSON。未知事件可安全忽略并受测试；已知事件字段畸形、无 `message_stop` 的意外 EOF、完整流无文本应失败为 `AI_OUTPUT_INVALID`/`AI_UNAVAILABLE` 的明确一种，计划实施时固定并写入测试。

每次读取和派发分片前检查线程中断与 `CancellationToken`。用户取消、客户端断线、first-byte/idle/total timeout 的现有上层语义必须保留；Provider 不得吞掉取消后继续计费。流结束返回拼接后的完整文本、模型和 usage，且 chunks 与最终文本逐字一致。

### 6. 错误、重试和隐私边界

复用既有稳定业务错误码，但针对两条 Provider 端口分别保持现有 exception 类型：

- HTTP `429` → `AI_RATE_LIMITED`，可重试；
- HTTP `408`、连接/读取超时 → `AI_TIMEOUT`，是否可重试保持既有网关策略；
- HTTP `5xx` → `AI_UNAVAILABLE`，可重试；
- HTTP `400/401/403/404` → `AI_REQUEST_REJECTED`，不可重试，其中日志只留 status/provider/request ID，不输出响应正文；
- `2xx` 协议畸形/无有效文本/非法行程 JSON → `AI_OUTPUT_INVALID`，不可作传输重试。

如果 Xiaomi 返回 Anthropic 风格 JSON error 或 SSE `error`，只映射白名单字段；供应商 message 不返回前端、不写完整日志。任何日志、异常和测试失败 diff 都不能包含 `x-api-key`、完整提示、完整用户隐私、供应商响应正文。将 Xiaomi key 加入现有脱敏测试词形（包括大小写不同的 header 名），并确保 Spring 启动异常只说缺少哪个环境变量，不回显值。

## 文件级实施步骤

### 1. 配置和共享协议组件

1. 在 AI 包中新增经 `@Validated` 的 Xiaomi MiMo 配置 record，并通过配置类启用；校验 URL、model、version、max token，且只有选择该 provider 时才要求 key。
2. 新增 Anthropic Messages 的请求/响应 DTO、HTTP client factory 和 SSE parser。行程、咨询可共享认证、端点、错误分类与 wire DTO，但领域结果解析保持在各自适配器内，避免两个 bounded context 互相依赖。
3. 在 `application.yml` 添加独立 `XIAOMI_MIMO_*` 映射；`application-prod.yml` 不强制把默认 provider 改为 Xiaomi。`.env.example` 只放空 key 与非秘密示例，Compose 只透传环境变量，禁止镜像构建参数承载 key。

### 2. 行程 Provider

1. 新增 `XiaomiMimoAnthropicTripPlanningProvider`，条件值严格为 `xiaomi-mimo-anthropic`。
2. 组装顶层 system、用户 JSON、Schema 约束，调用 `/v1/messages`，严格提取 text blocks 与 JSON。
3. 保持 Gateway 的超时、并发、重试、总截止和二次语义校验；成功时现有生成/调整/版本持久化路径无需分叉。
4. Provider 名称和模型持久化为可审计值，不保存 base URL、key 或完整提示。

### 3. 咨询 Provider

1. 新增 `XiaomiMimoAnthropicConsultationProvider`，普通问答按 Messages 非流响应解析。
2. 流式问答使用独立 SSE 状态机，只向上层发文本 delta，并正确回收连接、响应取消。
3. 保持现有隐私/内容安全、会话持久化、断线重放和显式取消都位于 Provider 之外；不得为 Xiaomi 绕开这些规则。

### 4. 文档和治理

1. 更新 `README.md` 配置表和启用示例，明确 Base URL 是 `/anthropic` 根、endpoint 自动追加、模型默认 `mimo-v2.5`，真实 key 不入库。
2. 更新 `docs/deployment.md`、`docs/troubleshooting.md`：给出 provider 切换、缺 key/401/429/协议错误排查和回滚到 Stub 的显式部署步骤；回滚是运维配置变更，不是请求级静默降级。
3. 更新 `docs/ai-governance/HUMAN_ACTIONS.md` 与 `PROJECT_HANDOFF.md`：用本轮独立适配替代“只支持 OpenAI compatible”的旧描述；同时保留 Token Plan 用途条款、账号、额度、key 和真实连通性必须人工确认。
4. 在 `docs/ai-governance/AI_CHANGE_LOG.md` 记录用户选择、协议决策、无真实调用、规划/实施/审核角色与验证证据；不记录 key 或私密提示。
5. `docs/openapi.yaml` 和 `docs/api-contract.md` 的浏览器业务契约预计不变；实施时必须显式审查并在 review 中记录“无需变更”的理由。如发现 provider-specific 字段泄漏到 API，先修改契约再实现。
6. 在 `TODO.md` 新增可审计子项“Xiaomi MiMo Anthropic 行程/咨询适配”，只有 Mock 契约测试、全量回归和独立复审 PASS 后勾选；真实账号验收必须单列且在实际执行前保持未完成。

## 自动化测试矩阵

### 配置与启动

- Stub/OpenAI 模式无 `XIAOMI_MIMO_API_KEY` 仍可启动；任一业务选择 Xiaomi 且 key 空白时启动失败且不回退 Stub。
- 默认 URL/model 精确为用户给出的值；覆盖值生效；HTTP/畸形 URL、空 model/version、非正或过大 max tokens 被拒绝。
- 两个业务都选 Xiaomi 时共享协议配置但不创建歧义 Provider；只选其中一个时另一条链路保持原 Provider。

### 出站请求契约

- Mock server 断言精确路径 `/anthropic/v1/messages`、POST、JSON Content-Type、官方要求的认证/version 头、无 Bearer 或 OpenAI 专属字段。
- key 不出现在 JSON body、URI、日志或异常；base URL 有/无尾斜杠结果一致。
- system 位于顶层，messages 只含 user/assistant；model、max_tokens、stream 值准确。
- 行程请求包含版本化系统规则、Schema 和独立的规范化输入 JSON；咨询历史顺序不变。

### 行程解析与业务保持

- 单/multiple text block 的合法完整 JSON 可生成初次行程和调整版本，provider/model 元数据准确。
- JSON 围栏策略按最终契约正向/负向覆盖；解释文字、两个 JSON 对象、截断 JSON、空 block、tool-only/unknown block 均拒绝。
- 1 天/30 天、日期、预算、币种、时间重叠、费用与未知字段继续由既有 Schema/validator 验证；非法输出不持久化版本，调整失败不替换 current version。
- 429/5xx/timeout/4xx/无效输出映射和重试次数符合既有 Gateway 规则，只成功持久化一次。

### 普通与 SSE 咨询

- 普通响应单/多 text block、usage 有/无字段、未知非文本 block、空响应、非法 JSON。
- SSE 覆盖标准事件完整顺序、多 text block、多个 delta、CRLF、多行 data、ping/注释、usage、unknown event、供应商 error、畸形 delta、提前 EOF、无文本和正常 `message_stop`。
- 每个文本 delta 恰好派发一次，最终拼接文本一致；不得把 thinking/tool/错误正文发给浏览器。
- 用户显式取消、线程中断、客户端断线、首字节/空闲/总超时停止读取并关闭连接；现有重放/取消集成测试继续通过。

### 安全与回归

- 使用唯一假 key 的日志捕获测试确认 header/key 被脱敏；错误响应没有供应商正文、URL 查询中的秘密或完整 prompt。
- `StubTripPlanningProvider`、`StubConsultationProvider`、两个 OpenAI-compatible provider 的既有测试全绿。
- 行程完整集成、咨询普通/SSE 生命周期、鉴权、E2E 和容器 smoke 不依赖公网或真实 Xiaomi key。

## 验证门禁

实施终端完成后至少执行：

1. 新增 Xiaomi 配置、Messages wire、行程、普通咨询、SSE parser 的定向单元/Mock server 测试。
2. `cd backend && mvn spotless:check test`，随后 `mvn verify`（项目现有门禁若已封装则优先执行 `scripts/check.sh`）。
3. OpenAPI 语义校验，确认公共 API 无意外变化。
4. `bash scripts/check.sh`、与 AI 配置相关的容器启动/健康 smoke；全部使用 Stub 或本地 Mock，不访问 Xiaomi。
5. `git diff --check`、秘密扫描，确认没有真实 key、Authorization/x-api-key 值或 Mock 固定秘密泄漏到产物（测试只能用明显无效占位值）。

真实连通验收另列人工步骤：负责人先确认 Token Plan 允许本项目用途并在本地/Codespaces Secret 配置 key，再用最小额度分别执行一次行程、一次普通咨询、一次 SSE 咨询，观察 usage/429/取消行为；记录脱敏结果，不保存响应全文或 key。真实验收未执行时只能声明“协议适配和 Mock 契约测试通过”，不能声明“Xiaomi 线上调用通过”。

## 独立审核与修正循环

由未参与实现的 reviewer 创建 `docs/reviews/round-14-xiaomi-mimo-anthropic-review.md`，只审核和测试，不编辑实现。审核至少检查：

1. 实际 diff 与本计划一致，三个链路均使用 Anthropic Messages 而非伪装成 OpenAI Base URL 替换。
2. URL 拼接、认证头、`anthropic-version`、`max_tokens`、system/messages/content/usage 与 Xiaomi 当前官方配置一致；若官方细节无法公开确认，真实兼容声明仍被限制且没有猜测性字段。
3. 行程仍经过应用 Schema/语义校验，咨询 SSE parser 是有状态、可取消、不会转发 thinking/tool/error 私有内容。
4. Provider 条件装配无冲突，缺 key fail-fast，无运行时静默 Stub，OpenAI/Stub 行为没有回归。
5. Mock server 不访问真实域名，测试覆盖正负向协议、错误分类、超时取消和秘密脱敏。
6. README、部署、故障排查、人工操作、交接、TODO 与 AI 变更日志事实一致，产品文档和 AI 治理文档没有混放。
7. 运行上述定向测试与全量质量门，报告准确命令、结果和任何未执行边界，给出明确 `PASS`/`FAIL`。

若首审 `FAIL`，仅实施终端修复阻断；同一 reviewer 复跑受影响测试并追加复审。只有最终 `PASS` 后才更新 TODO 完成状态和交付证据。普通代码推送沿用用户已有授权范围；真实 secret、收费调用、部署、tag 或新 Release 仍需要对应明确授权。

## 完成定义

Round 14 只有同时满足以下条件才可完成：

1. `TRIP_AI_PROVIDER=xiaomi-mimo-anthropic` 可经本地 Mock 完成合法结构化行程的生成和调整，非法模型输出不能写入版本。
2. `CONSULTATION_AI_PROVIDER=xiaomi-mimo-anthropic` 可经本地 Mock 完成普通问答和 Anthropic SSE 流式问答，usage、取消、超时和错误映射符合现有业务语义。
3. 默认 Base URL 精确为 `https://token-plan-cn.xiaomimimo.com/anthropic`、默认模型为 `mimo-v2.5`；请求使用经官方确认的 Anthropic Messages endpoint/headers/body，不携带 OpenAI 专属字段。
4. Stub 和 OpenAI-compatible 回归、后端全量测试、项目质量门、契约校验与秘密扫描全部通过，独立 reviewer 最终 `PASS`。
5. 配置、部署、排障、治理与人工验收文档完整；仓库、日志和测试产物不含真实 key。
6. 未进行真实账号调用时，所有交付说明准确限定为“适配完成、Mock 验证通过”；真实用途条款、账号/额度/key 和线上三链路验收仍清楚标为人工待办。
