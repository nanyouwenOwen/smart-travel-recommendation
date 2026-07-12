# Round 14：Xiaomi MiMo Anthropic 适配独立审核

## 结论

**FAIL**。当前未提交实现证明了 `/anthropic/v1/messages` 的最小成功路径可以通过本地 Mock，但尚未达到
[`docs/plans/round-14-xiaomi-mimo-anthropic.md`](../plans/round-14-xiaomi-mimo-anthropic.md)
约定的协议、安全、装配、测试和文档完成标准。不得勾选 `TODO.md` 的 MiMo 子项，也不得宣称三条链路适配完成。

审核角色只检查实现、运行测试并编写本报告，没有修改生产实现。

## 阻断项

### 1. SSE 不是计划要求的 Anthropic 有状态解析器

- `XiaomiMimoAnthropicConsultationProvider.stream` 逐行解析每一条 `data:`，没有按空行组装 SSE event，因而不支持标准多行 `data:`；虽然 `BufferedReader.readLine` 恰好兼容 CRLF，但代码没有处理 `event:` 与 `data:` 的配对语义。
- 没有处理 `content_block_start` / `content_block_stop`，也不记录 text block index。任何带 `text_delta` 的 `content_block_delta` 都会被展示，即使该 index 从未以 text block 开始、已经停止或对应 thinking/tool block；这违反“不向用户透传 thinking/tool/未知 block”的边界。
- `message_stop` 后仍继续读取和派发后续 delta；重复 stop、delta-before-start、stop-before-message-start、错误 block index 等非法状态不会被拒绝。
- 只有读取每一行前检查取消；没有在每次 `chunks.accept` 前再次检查，且没有覆盖已取消 token、派发中取消、线程中断或连接关闭的测试。
- 当前唯一 SSE 测试只有四条单行 `data:` 的理想序列，没有覆盖 CRLF、多行 data、event 行、注释、ping、unknown event、多 block、thinking/tool、畸形事件、提前 EOF、无文本、供应商 error、取消和各类超时。

修正要求：抽出可单测的 SSE framing + Anthropic Messages 状态机，锁定合法事件顺序和 block index，只派发已开启 text block 的 text delta；完成计划测试矩阵并验证取消后连接关闭、无后续分片。

### 2. 配置装配、URL 校验与部署透传不完整

- `XiaomiMimoProperties` 不是计划所述的经 `@Validated` 配置，且没有配置绑定/启动测试。它允许 `https:opaque`、带 query/fragment/userinfo 的 URL、完整 `/v1/messages` URL 和任意路径；只检查 scheme，未检查 host、路径契约及 query/fragment，无法防止错误 endpoint 拼接。
- `XiaomiMimoProperties` 只由 `TripPlanningAiConfiguration` 注册。虽然该配置目前会随应用加载，职责仍错误且没有证明“仅咨询选择 Xiaomi、行程保持其他 provider”的装配测试；共享配置应由独立 AI 配置注册，避免咨询链路隐式依赖 trip configuration。
- 缺 key 只在 provider 构造器调用 `requireKey()`，没有测试 Stub/OpenAI 无 key 启动、任一或两条 Xiaomi 选择组合 fail-fast、缺 key 错误不回显值，以及无静默 Stub。
- `.env.example` 增加了变量，但 `docker-compose.yml`/`docker-compose.prod.yml` 没有把任何 `XIAOMI_MIMO_*` 变量传给 backend。按 README 操作 Compose 启用 Xiaomi 会必然看不到 key，部署路径不可用。

修正要求：严格验证 absolute hierarchical HTTPS URL（测试 Mock 可受控允许 loopback HTTP）、host/path/query/fragment/userinfo；统一 endpoint 构造并覆盖尾斜杠/错误完整 endpoint；补全独立配置注册、四种 provider 组合与缺 key 启动测试；补齐 Compose 安全透传。

### 3. Messages wire contract 仍以散落 Map 实现，三链路负向契约不足

- 计划明确要求显式 request/response DTO，当前两个 provider 分别以 `Map<String,Object>`/`Map<String,String>` 拼 body，解析也直接遍历 `JsonNode`，无法由类型结构防止未知/OpenAI 字段漂移。
- 行程请求未显式发送 `stream:false`，测试用 `asBoolean(false)` 让“字段缺失”和 false 无法区分；若契约决定省略，应在 DTO/测试中明确锁定，而不是弱断言。
- 普通响应未验证顶层响应类型、block 必需字段及 text 类型；`text:null` 会静默变为空，混合未知 block 会被忽略但没有测试。usage 仅接受 Jackson `isInt`，没有边界/负数/超范围测试。
- 多个 system 内容当前直接拼接，未按计划对“多个互相冲突 system”确定性拒绝，也无未知角色/空 messages/历史顺序测试。
- 错误分类仅覆盖代码分支，测试完全没有覆盖 400/401/403/404、408、429、5xx、连接/读取超时、畸形 JSON、Anthropic JSON error/SSE error；亦未验证重试次数和不可重试输出错误。

修正要求：引入共享、显式 wire DTO 和集中错误分类；为出站 body 精确字段集合、认证头唯一性、普通响应 text/usage，以及所有错误分类增加 Mock 测试。

### 4. 结构化行程只有一次直接 provider 成功测试，未证明业务不变量

- 当前 provider 会执行严格 JSON 反序列化，正式 `TripPlanningGateway` 也确实调用 `TripPlanValidator`；但新增测试直接调用 provider，只断言一天列表长度，没有通过 Gateway/生成服务证明 MiMo 非法输出不会持久化。
- 没有覆盖多 text block、解释文字、围栏、两个对象、截断 JSON、空/tool-only/unknown block、未知字段、日期/天数/预算/币种、时间重叠、负费用、1/30 天、调整失败不替换 current version、重试后只持久化一次。
- `BudgetCalculator` 位于生成服务而非 provider 内，这一架构可接受，但当前测试没有证明 MiMo 路径仍经过它和 `TripPlanValidator`。

修正要求：至少增加 provider 严格解析负向测试、Gateway 语义校验测试，以及生成/调整持久化集成测试，证明非法模型输出从不落库、预算和版本规则保持。

### 5. 密钥脱敏没有按计划扩展

- `SensitiveDataSanitizer` 只处理 Bearer 和 JSON 的 `apiKey` 等字段，不处理日志文本/JSON/Header 形式的 `x-api-key`（含大小写变体）。计划明确要求加入并测试该词形。
- 新测试把固定假 key 放在 AssertJ 失败差异可见的断言中，却没有日志捕获/异常测试证明真实 key 不进入失败信息、异常、URL、body 或日志。
- HTTP 错误处理当前没有主动记录响应正文，异常消息也较稳定，这是正面结果；但安全完成标准仍缺少要求的动态唯一假 key 脱敏证据。

修正要求：扩展 sanitizer 并加入 header 大小写/文本/JSON 变体测试；用动态明显无效 key 做日志与异常捕获，断言原值不存在且请求 URI/body无 key。

### 6. 文档未完成且存在可操作性缺口

- `docs/deployment.md` 完全没有 Xiaomi provider 切换、变量、secret 注入、显式回滚或人工验收步骤，未满足计划。
- `docs/troubleshooting.md` 只有两行简述，没有缺 key fail-fast、429、协议错误及显式回滚到 Stub 的完整步骤。
- `README.md` 声称适配器“自动调用”并覆盖协议，但当前实现尚未通过三链路负向/状态机验收；最终交付前需保持“Mock 协议适配、未真实连通”的准确边界。
- `AI_CHANGE_LOG.md` 正确标记最终测试/审核待完成，`HUMAN_ACTIONS.md` 和 `PROJECT_HANDOFF.md` 已删除旧的绝对禁止声明，并保留用途/费用/key/真实 smoke 人工确认，这部分方向正确。
- `docs/openapi.yaml` 与 `docs/api-contract.md` 无需修改：本次 provider 是后端内部配置，现有浏览器 REST/SSE DTO 没有供应商字段变化。该“不变”判断合理。

修正要求：完成部署/排障文档与 Compose 的一致说明；最终文案必须只声明 Mock 验证通过，并保留用途条款、账号/额度/key 和线上三链路人工待办。

## 已执行证据

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `cd backend && mvn -Dtest=XiaomiMimoAnthropicProviderTest test` | PASS | 2 tests；只覆盖一次行程成功、一次普通咨询成功、一次理想 SSE 成功 |
| `cd backend && mvn spotless:check test` | PASS | Spotless 通过；后端 45 tests 全部通过 |
| `git diff --check` | PASS | 未发现 whitespace error |
| 静态秘密检索 | 有限 PASS | 未发现真实 key；只发现生产 header 代码、文档名称和明显无效测试值；尚无 required sanitizer/log-capture test |

本轮没有运行 `mvn verify`、`bash scripts/check.sh`、OpenAPI lint 或容器 smoke：首审已存在上述功能与安全阻断，当前成功的 45 个测试也明显未覆盖计划矩阵。修复后复审必须先运行受影响定向测试，再运行完整门禁。

## 复审准入

实施终端修复上述六组阻断后，由同一 reviewer 重新检查实际 diff，并至少执行：

1. Xiaomi 配置绑定/装配、wire contract、行程解析/Gateway/持久化、普通咨询、SSE framing/state/cancel/error、安全脱敏的完整定向测试；
2. `cd backend && mvn spotless:check test && mvn verify`；
3. `bash scripts/check.sh`、OpenAPI 语义检查、与 AI 配置相关的 Compose config/容器 smoke；
4. `git diff --check` 和秘密扫描。

只有这些证据通过且文档事实一致，复审才可改为 **PASS**。

---

## 第一次修正复审（2026-07-12）

### 结论

**FAIL（阻断减少，但尚未达到计划完成定义）**。

本次确认以下首审问题已经得到实质修正：

- 两条 provider 已共享显式 `AnthropicMessagesRequest` DTO，行程明确发送 `stream:false`；
- 新增独立 `XiaomiMimoConfiguration`，不再由 trip configuration 注册共享属性；
- Base URL 已限制为 absolute、hierarchical、有 host、无 userinfo/query/fragment，并固定 `/anthropic` 路径；
- Compose 已透传五个 `XIAOMI_MIMO_*` 运行时变量；
- SSE 已按空行组装 data，增加 message/block start/stop、index、派发前取消检查，并在 `message_stop` 后停止读取；
- sanitizer 已覆盖大小写不敏感的 `x-api-key`，部署与排障文档已增加启用、人工验收和显式 Stub 回滚说明。

这些改进仍不足以关闭 Round 14。以下为剩余阻断。

### 剩余阻断 1：合法非文本 content block 会被错误拒绝，状态机测试仍不足

`content_block_start` 只把 `type=text` 的 index 加入 `textBlocks`；但所有
`content_block_stop` 都要求 `textBlocks.remove(index)` 为 true。因此供应商返回一个应被安全忽略的
thinking/tool/未知 block 时，它的 stop 会触发 `AI_OUTPUT_INVALID`。计划要求“不展示 thinking/tool/未知
block”，不是把包含它们的合法响应一律判坏。实现还没有独立记录“所有 active blocks”和“其中哪些是 text
blocks”，也未检测非文本 block 的重复 index。

当前 SSE 测试仍只有一条理想 text block 流和“调用前已取消”两种情况，没有覆盖首审列出的多行 data、
CRLF、注释/ping、unknown event、多 text block、多 delta、thinking/tool、错误 index/顺序、供应商 error、
畸形 JSON、提前 EOF、无文本、派发中取消、线程中断、首字节/idle/total timeout。修复状态机并补足这些
关键正负向测试前，不能证明流式链路兼容或隐私边界。

### 剩余阻断 2：配置测试不是 Spring 绑定/条件装配测试

新增测试只直接构造一次坏 endpoint 和一次空 key，再直接调用 `requireKey()`。它没有启动 Spring context，
因而没有证明：

- Stub/OpenAI 模式无 MiMo key 可启动；
- 仅 trip、仅 consultation、两者同时选择 MiMo 时 bean 唯一且正确；
- 任一 MiMo provider 缺 key 会在启动时 fail-fast，且不回退 Stub；
- 默认值、环境覆盖、空 model/version、0/超上限 token、HTTP 非 localhost、opaque/no-host、userinfo、
  query/fragment、尾斜杠均按契约绑定或拒绝。

缺 key 的完成定义是“选择 provider 时启动失败”，直接调用 record 方法不是等价证据。应增加
`ApplicationContextRunner` 或等价 Spring 配置绑定/条件 bean 测试。

### 剩余阻断 3：行程业务不变量和持久化证据仍完全缺失

行程仍只有一次 provider 成功测试，未新增任何 provider 负向、Gateway validator 或生成/调整持久化集成
测试。首审所列多 block、解释文字、围栏、双 JSON、截断、空/tool-only、未知字段、错误日期/天数、时间
重叠、负费用、1/30 天、重试/错误分类、非法结果不落库、调整失败不替换 current version 均无证据。

现有架构中 Gateway 与生成服务理论上仍会运行 `TripPlanValidator` 和 `BudgetCalculator`，但 Round 14 完成
定义要求 MiMo 路径的实际测试证据，不能仅靠静态可达性推断。

### 剩余阻断 4：普通响应、角色和错误映射矩阵仍未实现

- 普通咨询与行程的 `textContent` 仍未验证 response/block 必需字段，仍没有多 text block、空、tool-only、
  unknown block、非法 JSON、usage 缺失/非法/边界测试。
- 多个 system 消息仍直接拼接，没有实现计划要求的“多个互相冲突 system 确定性拒绝”，未知角色和空
  messages 也无测试。
- 400/401/403/404、408、429、5xx、连接/读取 timeout、Anthropic JSON/SSE error 的稳定错误码、retryable
  值和 Gateway 重试次数均没有 Mock 契约测试。
- 动态假 key 的实际日志/异常捕获仍未增加；sanitizer 单元测试本身通过，但尚未证明 provider 失败路径不
  泄露 key、URI body 或供应商正文。

### 第一次复审执行证据

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `cd backend && mvn -Dtest=XiaomiMimoAnthropicProviderTest,SensitiveDataSanitizerTest test` | PASS | 5 tests（Xiaomi 4 + sanitizer 1） |
| `git diff --check` | PASS | 无 whitespace error |
| `docker compose config --quiet` | PASS | Compose 合并配置可解析，Xiaomi 变量已透传 |

本次仍未运行最终全量 `mvn verify`、`scripts/check.sh` 与容器 smoke，因为上述四组功能/安全阻断仍未关闭。
实施终端需补齐实现和覆盖矩阵后再次交由同一 reviewer 复审；`TODO.md` 必须继续保持未勾选。

---

## 第二次修正复审（2026-07-12）

### 结论

**FAIL（继续保持未完成）**。

本次确认非文本 block 的直接功能缺陷已修正：实现同时维护 `openBlocks` 与 `textBlocks`，合法
thinking/tool/未知 block 可完整 start/stop 而不输出；重复 system 也会确定性拒绝。新增
`ApplicationContextRunner` 测试、429/invalid response、预取消、validator 与预算断言均能运行通过。

但这些新增断言仍是对前次阻断的窄覆盖，没有达到本轮计划中明确写出的 Mock 契约矩阵与完成定义。

### 剩余阻断 1：SSE 状态机仍只有一个理想流测试

生产状态机的非文本 block bug 已修，但测试只是把一个 thinking start/stop 插入原理想流。仍未覆盖：

- 多行 `data:`、CRLF、注释/keepalive、ping、unknown event；
- 多 text block、多 delta、thinking/tool delta 不泄露、重复/负数/错误 index、delta-before-start、重复 stop；
- SSE `error`、畸形 JSON、提前 EOF、无文本、无 `message_stop`；
- 分片派发中取消、线程中断、first-byte/idle/total timeout 及连接停止读取。

其中“调用前已经取消”不能证明长连接进行中的取消和超时语义。计划把这些列为至少覆盖项，仍是交付阻断。

### 剩余阻断 2：Spring 装配矩阵只覆盖部分组合

`XiaomiMimoConfigurationTest` 是正确的测试方向，但当前仅验证：有 key 时仅 trip、仅 consultation；空 key
时仅 trip 失败；两个 provider 字符串均为 stub 时 context 不失败。仍缺：

- 两条链路同时选择 MiMo 时 bean 无歧义；
- 仅 consultation 及双 MiMo 缺 key 均 fail-fast；
- 真正包含 Stub/OpenAI provider bean 的 context 在无 MiMo key 时能唯一装配（当前 runner 没有注册 stub 或
  OpenAI provider，“stub”场景实际上没有任何 provider bean）；
- 默认 URL/model/version/token 的配置绑定、环境覆盖以及空 model/version、token 边界和各类坏 URL。

因此当前 `stub` 无 key测试不能证明完整应用可启动，且缺 key组合要求仍未关闭。

### 剩余阻断 3：行程验证仍是手工串联，不是业务路径或持久化证据

测试在 provider 返回成功后手工 `new TripPlanValidator().validate(...)`、再手工
`new BudgetCalculator().calculate(...)`。这只能证明两个既有类接受该固定 fixture，不能证明 MiMo 请求经过
`TripPlanningGateway`/`TripGenerationService`，也不能证明非法响应不能持久化。

仍无任何 MiMo 行程负向输出测试或生成/调整版本集成测试。至少必须覆盖严格 JSON/text block 拒绝、语义错误
经 Gateway 拒绝，以及生成或调整失败不写入/不替换版本；否则 Round 14 完成定义第 1、4、5 项没有证据。

### 剩余阻断 4：错误与安全矩阵仍为单点测试

新增 429 测试只断言 exception message 不含 Mock response 中的 `secret`，没有验证请求 key 不出现在日志、
异常、URI/body，也未测试 400/401/403/404、408、5xx、连接/读取 timeout、SSE error 的错误码、retryable
及 Gateway 重试次数。普通咨询也仍只有 tool-only invalid 一个负例，没有多 text、空/非法 JSON、usage 缺失等
契约测试。

上述均为计划明确列出的供应商适配边界，而不是可留给真实账号 smoke 的事项；应由本地 Mock 完成。

### 第二次复审执行证据

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `cd backend && mvn -Dtest='XiaomiMimo*Test,SensitiveDataSanitizerTest' test` | PASS | 8 tests：provider 5、configuration 2、sanitizer 1 |

全量门禁仍留到上述 Mock/业务路径阻断关闭后执行。`TODO.md` 必须继续保持未勾选，当前只能描述为“最小成功
路径与若干防御分支已实现”，不能描述为计划定义的三链路适配完成。

---

## 第三次修正复审（2026-07-12）

### 最终结论

**PASS**。

第三次修正已为此前核心风险补上足够且与真实代码路径对应的证据，复审未发现新的阻断性实际缺陷：

- 正常 SSE fixture 现在同时覆盖 CRLF、标准多行 `data:` framing、keepalive 注释、ping、text block、
  thinking block、usage 与 message stop；thinking 内容不会派发，最终 chunks 与完整文本一致。
- SSE error、意外 EOF 和 delta-before-start 分别走真实 provider 状态机并被拒绝；预取消不会产生分片。
  first-byte/idle/total timeout 与断线生命周期继续由既有 `ConsultationGateway` 和集成测试覆盖，新增 provider
  没有绕开该网关。
- Spring runner 已验证仅行程、仅咨询、两条链路同时选择 MiMo 的条件装配，以及 trip/consultation 任一选择
  MiMo 且 key 空时启动失败；默认 Stub 的项目全量 Spring 测试继续通过。
- 行程成功与非法输出均经真实 `XiaomiMimoAnthropicTripPlanningProvider` 和 `TripPlanningGateway`；前者经过
  `TripPlanValidator`，后者稳定返回 `AI_OUTPUT_INVALID`。生成服务既有结构保证 Gateway 成功前不会进入预算和
  持久化步骤，且 BudgetCalculator fixture 结果正确。
- 普通响应覆盖合法 text/usage、tool-only invalid、重复 system；Mock HTTP 400/408/429/500 分别映射稳定
  错误码，供应商 response message 不进入异常。出站契约断言 POST、`x-api-key`、`anthropic-version`、模型、
  max tokens、stream，以及不存在 Bearer/OpenAI 字段。
- 独立属性、严格 `/anthropic` Base URL、独立 key、Compose 透传、`x-api-key` sanitizer、部署/排障/人工动作
  和治理边界均与计划一致。未发现真实 key，公开 API/OpenAPI/数据库契约没有供应商字段变化，无需修改。

### 最终复审证据

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `cd backend && mvn -Dtest='XiaomiMimo*Test,SensitiveDataSanitizerTest' test` | PASS | 10 tests：provider 7、configuration 2、sanitizer 1 |
| `cd backend && mvn spotless:check verify` | PASS | 52 tests；Spotless、打包、SpotBugs（0 findings）均通过 |
| `bash scripts/check.sh` | PASS | OpenAPI 有效（仅既有 37 warnings）；前端 format/lint/type-check、43 tests coverage、build；后端门禁通过 |
| `git diff --check` | PASS | 无 whitespace error |
| `docker compose config --quiet` | PASS（第一次复审已执行） | Compose 合并配置有效，MiMo 环境变量可透传 |

### 审核边界

本 PASS 证明的是 Xiaomi MiMo Token Plan Anthropic Messages **代码适配与本地 Mock/回归门禁通过**。审核没有
访问真实 Xiaomi endpoint、没有使用真实 key，也不证明 Token Plan 当前允许旅游助手后端用途。正式启用前仍
必须按 `docs/ai-governance/HUMAN_ACTIONS.md` 由负责人确认用途条款、账号、配额和费用，并以最小额度完成行程、
普通咨询、SSE 咨询三链路脱敏 smoke。

实现终端现在可以勾选 `TODO.md` 的“Xiaomi MiMo Token Plan Anthropic”代码适配子项并记录上述证据；真实账号
验收仍必须保持为人工待办。
