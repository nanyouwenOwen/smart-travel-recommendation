# Round 02：智能行程规划后端实施计划

## 目标

完成 TODO 第 3 阶段的后端闭环：认证用户提交结构化旅游需求后，系统异步调用可替换的 AI 供应商，校验结构化输出并持久化为不可变行程版本；用户可分页查看、修改基础需求、删除行程、用自然语言生成新版本、查看版本历史并回退，同时获得分类预算统计和超预算提示。

本轮必须同时提供 OpenAI 兼容实现和确定性的 Stub 实现。没有真实 AI Key 不影响本地开发及自动化验收，但生产功能边界不因此缩减：生产环境仍以真实供应商、真实超时/重试/限流及严格输出 Schema 为准。

## 范围

### 本轮包含

- 创建规划请求的字段、交叉字段和业务时区校验。
- 创建请求幂等控制，以及生成中的状态轮询。
- 版本化、可复用的系统提示词、用户提示词和 JSON Schema。
- AI 供应商端口、OpenAI 兼容适配器、确定性 Stub 适配器。
- 客户端连接/读取/总超时、有限重试、应用级用户限流和供应商限流映射。
- 初次生成、自然语言调整、失败状态及结构化结果校验。
- 行程、版本、每日安排、活动和预算统计的事务化持久化。
- 用户自己的行程列表、详情、基础需求修改、软删除。
- 版本历史、版本详情与回退；READY 版本不可原地覆盖。
- 预算分类统计、总额校验和超预算警告。
- 服务单元测试、Repository 测试、MockMvc 接口测试和 MySQL 迁移验证。
- 实现通过后同步 README、数据模型、接口约束和 TODO。

### 本轮不包含

- 前端规划表单及行程展示（TODO 第 5 阶段）。
- 地图、天气、景点营业时间和交通实时报价（TODO 第 6 阶段）。AI 产生的相关信息必须标识为建议，不伪装成实时数据。
- AI 咨询会话和 SSE（TODO 第 4 阶段）。
- 分布式消息队列和多节点分布式限流。MVP 先使用有界应用执行器与可替换限流端口，但接口、状态和失败恢复语义须保持将来可迁移。
- 自动货币换算；一个行程及其全部活动只允许一种币种。

## 关键业务规则

### 创建、校验与幂等

1. `destination` 去除首尾空白后长度为 1～200；`days` 为 1～30；`travelers` 为 1～50；预算大于 0、小数最多两位，币种必须为大写 ISO 4217 三字母代码。
2. `startDate` 不早于按请求 `timezone` 计算的当地当天，且最后一天不得超出应用支持的日期范围；`timezone` 必须能由 `ZoneId` 解析。测试通过注入 `Clock` 固定边界。
3. `preferences` 最多 20 项；每项去除空白后 1～50 字符、按大小写不敏感去重并保持首次出现顺序。`additionalRequirements` 去除首尾空白后最多 2000 字符，空字符串归一为缺省。
4. `Idempotency-Key` 对创建接口必填。按 `(user_id, operation, key)` 唯一；保存规范化请求体的 SHA-256。24 小时内同 Key 同请求返回同一个行程及相同语义状态，不重复调用 AI；同 Key 不同请求返回 `409 IDEMPOTENCY_KEY_CONFLICT`。
5. 创建成功先在短事务中写入 `GENERATING` 行程和幂等记录，再提交有界后台任务，返回 `202`。执行器饱和时不得留下永久 `GENERATING`：将行程置为 `FAILED/GENERATION_QUEUE_FULL` 并返回 `503`，或在创建事务提交前可靠拒绝。
6. GET 详情是状态轮询入口：`GENERATING` 返回需求和空 itinerary；`READY` 返回当前版本；`FAILED` 返回稳定的 `failureCode`，但不泄露供应商原始响应或密钥。

### AI 提示与输出 Schema

1. 提示词由版本化模板组成（例如 `trip-planner/v1`）：系统提示定义角色、安全边界、单币种、日期/天数、时间顺序、费用类别及“不把推测描述为实时事实”；用户数据以序列化 JSON 放入独立消息，不进行字符串拼接式指令覆盖。
2. 初次生成输入只包含规范化规划参数；调整输入还包含当前 READY 版本的结构化快照和用户调整指令。用户内容始终视为数据，不能覆盖系统约束。
3. 供应商输出必须符合应用自有 JSON Schema：根对象包含 `days`、`warnings`；每天包含连续的 `dayNumber`、匹配起始日期的 `date`、可选摘要及活动；活动包含顺序、`HH:mm` 起止、标题、地点、描述、交通建议、非负费用和预算类别。
4. 预算类别使用稳定枚举 `TRANSPORTATION`、`ACCOMMODATION`、`FOOD`、`ATTRACTION`、`SHOPPING`、`OTHER`。显示文案由客户端本地化，不把模型自由文本作为类别。
5. 供应商的 structured-output/schema 能力只是第一道约束；应用必须再次反序列化并做语义校验：天数/日期准确、每日编号连续、活动顺序唯一且时间不重叠、结束晚于开始、币种一致、金额精度合法、文本长度受限。
6. 模型返回未知字段应拒绝或由显式 Schema 策略处理，不得静默持久化不受控内容。Schema 不合格在一次可配置的“修复重试”仍失败后置为 `FAILED/AI_OUTPUT_INVALID`；接口读取失败状态，调整失败不得改变当前版本。
7. `estimatedTotal` 由服务端对活动费用求和，不信任模型总计。分类统计也由服务端计算。总额超过预算时追加机器可判断的 `BUDGET_EXCEEDED` 警告，并携带超出金额；模型文字警告与系统警告分开建模或带稳定 code。

### 供应商、超时、重试与限流

1. 定义 `TripPlanningProvider` 端口，输入为供应商无关的规划上下文，输出为供应商无关 DTO；领域/控制器不得依赖 OpenAI JSON 类型。
2. `OpenAiCompatibleTripPlanningProvider` 通过配置读取 `base-url`、`api-key`、`model`，调用兼容 Chat/Responses structured-output 的明确端点和字段。请求日志只记录 provider、model、耗时、request ID，不记录 Key、完整提示或完整用户隐私数据。
3. 分别配置连接超时、单次读取超时和整个生成任务截止时间。超时映射为 `AI_TIMEOUT`；DNS/连接失败、供应商 5xx 映射为 `AI_UNAVAILABLE`；供应商 429 映射为 `AI_RATE_LIMITED`。
4. 仅对连接异常、超时、429 和可重试 5xx 进行最多两次、带指数退避和抖动的重试；400/401/403、Schema 业务错误和应用取消不重试。调整与创建依靠版本提交的幂等边界，重试不能生成多个版本。
5. 应用级限流至少按认证用户限制“创建/调整”频率和并发数，并设置全局供应商并发上限。超限在调用供应商前返回/记录 `429 RATE_LIMITED`；限制器通过接口抽象，单节点实现的限制和重启行为写入 README。
6. `stub` profile 生成与输入天数、日期、币种一致的固定合法结果，可通过测试夹具触发 timeout、429、5xx、非法 JSON/Schema 等故障。`test` 默认 Stub；`dev` 可显式选择 Stub；`prod` 默认 OpenAI 兼容实现且缺少 Key 时启动失败，禁止静默回退 Stub。

### 行程、修改、删除与归属

1. 所有 Repository 查询都包含当前 `userId` 和 `deletedAt is null`；不存在与越权统一返回 `404 TRIP_NOT_FOUND`。
2. 列表按 `(created_at DESC, id DESC)` 稳定排序，游标编码最后一项的时间与 ID并签名或做不可伪造校验；响应不得泄露其他用户数据。
3. `PATCH /trips/{tripId}` 修改 destination、startDate、days、travelers、budget、preferences、timezone、additionalRequirements。对 READY 行程，需求变化会创建新的生成任务/版本，原当前版本保持可读直到新版本成功；不得让详情短暂丢失。状态冲突返回 `409 TRIP_STATE_CONFLICT`。
4. 删除为幂等软删除；首次及重复删除均可返回 204，但其他用户访问仍返回 404。进行中的后台任务在提交前再次检查有效性，已删除行程不得发布新版本。
5. READY 版本不可更新。初次生成成功创建版本 1；每次成功调整或重新规划使用该行程下单调递增版本号，并在数据库唯一约束及事务/锁下防止并发重复。

### 调整与版本回退

1. 调整指令去除首尾空白后为 2～2000 字符，仅允许当前行程存在 READY 当前版本时提交；同一行程同时最多一个生成/调整任务，否则返回 409。
2. 调整生成完整的新版本，不只保存 diff。新版本成功后在同一事务内写入全部子项并切换 `current_version_id`；失败保留原当前版本并记录任务失败原因。
3. 提供版本列表和版本详情。版本详情只允许行程所有者读取；历史版本也返回预算统计和警告。
4. 回退 `POST /trips/{tripId}/versions/{versionNumber}:restore` 不调用 AI、不修改历史版本。它原子切换 `current_version_id` 到指定 READY 历史版本，并记录恢复事件/审计字段；行程摘要中的 `version` 随当前指针变化。若要求“回退也形成新版本”，必须在契约阶段明确选择；本轮依据现有数据模型采用切换指针语义。

## 接口契约变更

实现必须先更新 `docs/openapi.yaml` 和 `docs/api-contract.md`。建议最终接口如下：

| 方法与路径 | 成功 | 说明 | 主要错误 |
| --- | --- | --- | --- |
| `POST /trips` | `202 TripResponse` | 带 `Idempotency-Key`，创建并异步生成 | 400, 409, 429, 503 |
| `GET /trips` | `200 TripListResponse` | 稳定游标分页 | 400, 401 |
| `GET /trips/{tripId}` | `200 TripResponse` | 查询状态、当前版本及预算 | 404 |
| `PATCH /trips/{tripId}` | `202 TripResponse` | 修改需求并重新生成；无生成需要的元数据修改可为 200，但契约只能选定一种明确语义 | 400, 404, 409, 429 |
| `DELETE /trips/{tripId}` | `204` | 幂等软删除 | 404（其他用户） |
| `POST /trips/{tripId}/adjustments` | `202 TripResponse` | 自然语言调整，建议也要求 `Idempotency-Key` | 400, 404, 409, 429 |
| `GET /trips/{tripId}/versions` | `200 TripVersionListResponse` | 版本号倒序，无活动明细 | 404 |
| `GET /trips/{tripId}/versions/{versionNumber}` | `200 TripVersionResponse` | 历史完整快照 | 404 |
| `POST /trips/{tripId}/versions/{versionNumber}:restore` | `200 TripResponse` | 原子切换当前版本 | 404, 409 |

契约需要补齐：

- 为所有 Trips 路由显式列出 `401`，并补齐 `400/409/422/429/502/503/504` 的复用响应。
- 新增 `UpdateTripRequest`、`AdjustTripRequest`、`TripVersionSummary/Response`、`BudgetBreakdown`、`BudgetCategoryAmount`、结构化 `TripWarning`。
- `TripSummary` 明确 `currentVersion` 可在生成失败前缺省；当前 `version` required 与 `GENERATING` 无版本的矛盾必须修正。
- `Trip` 增加基础需求、`budgetBreakdown`、结构化 warnings、失败状态信息；`itinerary` 在未 READY 时为空或缺省，二者择一并固定。
- `Activity` 增加 `sequenceNumber`、`category`；每日增加 `summary`。费用币种继承行程还是逐项返回需统一，推荐 API 逐项仍返回 `Money` 以自描述。
- 定义稳定错误码：`TRIP_NOT_FOUND`、`TRIP_STATE_CONFLICT`、`IDEMPOTENCY_KEY_CONFLICT`、`RATE_LIMITED`、`AI_OUTPUT_INVALID`、`AI_RATE_LIMITED`、`AI_UNAVAILABLE`、`AI_TIMEOUT`。
- 调整当前“202 直接返回 TripResponse”的轮询语义；如响应时版本尚未生成，Schema 不得强制 READY 专属字段。

## 数据迁移变更

新增 `V3__support_trip_generation.sql`，在不改写 V1/V2 的前提下演进：

1. `activities` 增加 `budget_category VARCHAR(32) NOT NULL` 及类别 CHECK。
2. `trip_versions` 增加 `generation_type`（`INITIAL/REPLAN/ADJUSTMENT`）、`prompt_version`、`provider`、`model`、可选 `restored_from_version_id` 或另建恢复审计表。Token/耗时若需要成本观测，可增加非负 `input_tokens`、`output_tokens`、`generation_duration_ms`。
3. 新建 `trip_generation_jobs`：`id`、`trip_id`、可选 base/current version、类型、状态（`QUEUED/RUNNING/SUCCEEDED/FAILED`）、调整指令/请求快照、attempt、failure_code、创建/开始/完成时间。该表解决 READY 当前版本与新版本生成状态并存，避免只用 `trips.status` 丢失旧版本。
4. 新建 `idempotency_records`：用户、操作、Key、请求摘要、资源 ID、响应/状态引用、`expires_at`，唯一键 `(user_id, operation, idempotency_key)`，并建立过期清理索引。
5. 如版本切换需审计，新建 `trip_version_restores`，记录 trip、from/to version、用户和时间；不要篡改不可变版本。
6. 为列表稳定游标确认 `(user_id, deleted_at, created_at DESC, id DESC)` 复合索引；根据 MySQL 执行计划调整，不能只依赖 V1 的三列索引。
7. H2 的 `ddl-auto=create-drop` 实体结构必须与 V3 一致；最终仍要在 MySQL 8.4 空库执行 V1→V3，并以 `ddl-auto=validate` 启动。

## 文件级实施步骤

### 1. 契约与配置

1. 修改 `docs/openapi.yaml`，先解决异步状态与 `version` 的矛盾，再加入 PATCH、版本和回退接口、预算结构及错误响应。
2. 修改 `docs/api-contract.md`：写明生成状态、幂等 24 小时、重试边界、版本不变性、回退、限流和非实时数据声明。
3. 修改 `docs/data-model.md`，加入生成任务、幂等、预算类别和恢复审计关系。
4. 在 `application*.yml` 增加 provider 类型、URL、模型、Key、模板版本、超时、重试、执行器容量、用户/全局限流配置；通过 `@ConfigurationProperties` 做启动校验。
5. 根据选定 HTTP 客户端与重试/限流库修改 `pom.xml`。优先使用 Spring `RestClient`/HTTP client、受控重试器和成熟限流实现，避免在业务方法内散落 sleep/计数器。

### 2. 迁移与领域持久化

1. 创建 V3 迁移及对应实体：`Trip`、`TripVersion`、`ItineraryDay`、`Activity`、`TripGenerationJob`、`IdempotencyRecord`、恢复审计实体。
2. 建立双向关系时避免将大对象图直接 JSON 序列化；控制器只返回 DTO。对子集合使用级联保存，但显式控制批量和事务边界。
3. Repository 提供所有者限定查询、带锁的版本号分配/状态切换、稳定游标列表、幂等键原子获取方法。
4. 建立领域枚举及转换，数据库 CHECK、Java enum、OpenAPI enum 保持一致。

### 3. AI 端口、提示及输出校验

1. 在 `com.travelassistant.trip.ai` 定义 provider 端口、请求/响应 DTO、统一异常分类和调用元数据。
2. 在资源目录保存版本化系统提示和 JSON Schema；应用启动测试验证 Schema 可解析，提示版本被持久化。
3. 实现纯函数式 prompt assembler，初次生成与调整模板分离；加入长度上限和结构化快照裁剪规则，但不得丢失日期、预算和已有活动。
4. 实现 OpenAI 兼容适配器，严格解析 structured output；实现脱敏观测、HTTP 错误分类、超时和重试。
5. 实现 Stub provider 和故障注入测试夹具；Stub 不通过网络且结果确定，可覆盖 1、30 天与各币种。
6. 实现第二层 `TripPlanValidator` 和 `BudgetCalculator`，只让验证成功的数据进入持久化。

### 4. 应用服务与异步生成

1. `TripCommandService` 处理规范化、业务校验、幂等、所有权及生成任务创建。
2. 使用 Spring 事务事件的 `AFTER_COMMIT` 或明确任务派发器，保证异步线程看得到已提交行程；后台线程不得依赖 Web SecurityContext，需传递已验证的 user/trip/job ID。
3. `TripGenerationService` 领取任务、调用限流/provider、校验结果，并在独立事务中原子保存完整版本、预算及 current pointer。异常分类写入 job/trip failure code。
4. 用数据库状态条件更新防止同一 job 被重复执行；进程崩溃后的超时 `RUNNING` 任务需有启动恢复/定时恢复策略，至少安全置回队列或 FAILED，避免永久卡住。
5. 调整和 PATCH 复用同一生成管线。调整失败不改变 current version；初次生成失败使无版本行程为 FAILED。
6. 回退服务加锁并校验目标版本属于同一行程且 READY，切换指针并写审计记录。

### 5. 查询、控制器与映射

1. 新增 Trip Controller、请求/响应 DTO 及显式 mapper，不从 JPA 实体直接响应。
2. 实现列表游标编码/解码与非法游标 400；limit 遵守 1～100。
3. 实现详情及版本详情的批量获取策略，避免 itinerary day/activity 的 N+1 查询。
4. 统一映射状态、失败码、预算分类、警告和版本号；金额固定两位的十进制字符串由 Jackson 配置或 DTO mapper 保证。
5. 对所有入口使用 Jakarta Validation，交叉字段规则在专用 validator/service 中完成；错误字段路径需与 OpenAPI 一致。

### 6. 验证与收尾

1. 完成下述测试矩阵并执行 `mvn verify`。
2. 在 MySQL 8.4 空库运行 V1→V3，随后以 `ddl-auto=validate` 启动；验证 CHECK、JSON、时间精度和索引。
3. 对 OpenAPI 进行语义校验，并用 MockMvc 响应验证关键字段/状态与契约一致。
4. 只在实现和验证均完成后更新 README 配置/运行方式、`TODO.md` 第 3 阶段八项状态。

## 测试矩阵

### 参数与契约

- destination/requirements 空白归一化；字段最小/最大边界；缺字段、非法 JSON、未知/非法币种格式。
- 预算 0、负数、超过两位小数；days 0/31；travelers 0/51；偏好超过 20、空项、重复项。
- 合法/非法 IANA timezone；当地今天允许、过去日期拒绝；跨月/跨年/夏令时日期序列正确。
- 未认证 401；资源不存在和跨用户的详情、修改、删除、版本、回退全部为同样的 404。

### 幂等、并发与状态

- 同用户同 Key 同请求并发提交只创建一个 Trip、一个 Job、一次 provider 调用。
- 同 Key 不同请求返回 409；不同用户可复用同 Key；过期记录按既定规则允许新请求。
- 执行器满、进程恢复、重复领取任务不会留下永久 GENERATING 或产生重复版本。
- 同行程并发调整/修改只有一个被接受；版本号唯一且 current pointer 总是指向完整版本。
- 调整失败保留旧 current；删除与生成竞争时，已删除 Trip 不发布版本。

### AI 适配与韧性

- Stub 对 1 天、30 天、多活动、各种合法币种产生确定性合法 Schema。
- OpenAI 请求包含模型、模板版本对应消息及 strict JSON Schema；Key/完整提示不出现在日志和异常响应。
- timeout、429、502/503、连接失败按策略重试且次数/退避可验证；400/401/Schema 失败不进行传输重试。
- 总截止时间生效；重试后成功只持久化一次；耗尽后稳定映射 failure code。
- 非 JSON、截断 JSON、额外字段、缺字段、未知类别、错日期、错天数、时间重叠、负费用、币种不一致均被应用校验拒绝。
- test/dev 可无 Key 使用 Stub；prod/OpenAI 模式无 Key 启动失败，绝不自动降级为 Stub。

### 持久化与预算

- 初次成功产生版本 1、正确天数/活动顺序及当前指针；READY 版本没有 update 路径。
- 调整产生完整版本 2，版本 1 内容未变化；查看两个历史版本数据正确。
- 回退到版本 1 不调用 AI，current pointer 和审计正确；非法/其他 Trip 版本不能回退。
- 分类合计之和严格等于 estimatedTotal；金额舍入规则固定；等于预算不警告，超过预算产生 code 和精确差额。
- 数据库约束拒绝重复版本、重复 day/sequence、负费用和非法类别。

### 查询与生命周期

- 空列表、limit 边界、多页无重复/遗漏；同 createdAt 时用 ID 稳定排序；非法/篡改游标 400。
- GENERATING、READY、FAILED 详情分别符合 Schema；READY 返回当前版本预算和 itinerary。
- PATCH 生成成功切换版本；失败保持旧版本可见；状态冲突 409。
- 删除幂等、列表不可见、详情 404；其他用户数据始终不可见。
- 查询明细执行固定数量查询或有 N+1 防护测试。

### 回归与真实数据库

- Round 01 注册、登录、刷新、资料及安全测试全部继续通过。
- H2 快速测试与 MySQL 8.4 Flyway V1→V3/JPA validate 均通过。
- OpenAPI 3.1 校验通过，CI 不跳过测试。

## 验收标准

- TODO 第 3 阶段八项均有对应实现和自动化证据，不用 Stub 替代生产适配器。
- 注册/登录后可完成：创建（202）→轮询 READY→列表/详情→PATCH 重新规划→自然语言调整→查看历史→回退→删除。
- 无真实 Key 时 `test` 全量测试和 `dev stub` 演示可重复运行；配置测试服务器后只需环境变量即可切换 OpenAI 兼容供应商，无需修改业务代码。
- AI 输出同时经过供应商 strict Schema 与应用语义验证，非法结果不会产生半成品版本。
- 重试只覆盖明确的瞬态失败，超时和限流有稳定 HTTP/业务错误语义，日志不泄露 Key 或完整隐私输入。
- 每次成功生成只发布一个不可变完整版本；失败调整不破坏现有 READY 行程；回退可审计。
- 预算分类总和、总预算、超预算 code/差额在详情与历史版本中一致。
- 所有资源接口实施 owner-scoped 查询，跨用户自动化测试证明无信息泄露。
- `mvn verify`、OpenAPI 语义检查、MySQL 8.4 迁移和 `ddl-auto=validate` 全部通过。
- README、API/Data Model 文档与实现一致，完成后才勾选 TODO 第 3 阶段。

## 风险与应对

| 风险 | 影响 | 应对 |
| --- | --- | --- |
| 当前 OpenAPI 强制 `version`，但 202 时尚无版本 | 实现与契约必然冲突 | 契约优先拆分生成中/就绪响应字段，增加状态判别测试 |
| 只用 `trips.status` 表示重规划 | READY 旧版本在新任务期间不可读或失败后丢失 | 引入 generation job 状态，将当前已发布版本与在途任务分离 |
| AI 返回“合法 JSON”但业务日期/金额错误 | 脏数据进入正式行程 | strict Schema 后再做应用语义校验，总额由服务端重算 |
| 异步任务在事务提交前启动 | 查不到 Trip 或产生偶发失败 | AFTER_COMMIT 派发或 outbox/job 领取模式，传递 ID 而非实体 |
| 进程崩溃导致 RUNNING 永久卡住 | 用户无法继续调整 | job lease/超时恢复与条件更新，重复执行保持幂等 |
| 同行程并发生成导致版本号冲突 | 重复版本或 current 指针竞态 | 数据库唯一键、悲观/乐观锁和单活动任务约束 |
| OpenAI 兼容服务的 structured-output 方言不同 | 切换供应商失败 | 端口隔离供应商请求格式，契约测试和能力配置；领域 Schema 不随供应商变化 |
| 重试放大成本或重复结果 | 费用增加、多个版本 | 只重试瞬态错误，限制次数/总时限，持久化提交以 job ID 幂等 |
| 内存限流在多节点不一致 | 生产限流可绕过 | 限流端口抽象并记录单节点边界；扩容前替换 Redis/网关实现 |
| Stub 与真实模型差异 | 测试绿但线上解析失败 | Stub 覆盖领域流程；另加 HTTP 合约测试录制典型成功/错误响应，真实沙箱作为非阻塞集成门禁 |
| H2/MySQL 对 JSON、CHECK、排序不同 | 部署才暴露迁移问题 | MySQL 8.4 迁移+validate 为强制验收，H2 仅作快速反馈 |
| 用户提示词注入 | 模型违反预算/结构或泄露上下文 | 系统/数据消息分离、严格 Schema、语义校验、最小上下文和日志脱敏 |

## 实施顺序与审核门

1. **契约门**：先评审异步状态、PATCH 语义、版本回退语义和预算 Schema；未定稿前不写 Controller。
2. **数据门**：V3 在 MySQL 8.4 通过，实体与迁移一致后再实现生成服务。
3. **AI 边界门**：Stub 与 OpenAI 兼容适配器通过端口合约测试，生产缺 Key 启动失败。
4. **领域门**：输出语义验证、预算计算和不可变版本服务单测通过。
5. **接口门**：完整认证后端流程和跨用户测试通过，实际响应与 OpenAPI 一致。
6. **独立审核**：审核重点为异步竞态、所有权、幂等、重试副作用、Schema 绕过、金额正确性及文档一致性；发现问题修正后复审。

