# Round 03：AI 旅游咨询后端实施计划

## 目标

完成 TODO 第 4 阶段的后端闭环：认证用户能够创建、分页查看和读取自己的咨询会话，以普通 JSON 或 SSE 流式方式提问，并可选择将自己已有的行程作为受控上下文。系统需要持久化用户消息与助手消息的状态，执行所有权、限流、超时、取消、恢复和安全防护，并保证接口行为可被下一阶段前端稳定消费。

本轮必须提供供应商无关的咨询端口、OpenAI 兼容实现和确定性 Stub。没有真实 AI Key 时，开发、CI 和完整验收使用 Stub，结果可重复且不访问网络；生产默认真实 provider，缺失 Key 必须启动失败，运行失败也不得静默降级为 Stub。

## 现状与差距

- V1 已创建 `conversations` 和 `messages`，具备用户/行程关系、消息角色、生成状态、模型、Token、错误码和完成时间，但尚无实体、Repository 或业务实现。
- 当前 OpenAPI 仅有创建会话和不完整的 `POST .../messages:stream`；缺少会话列表/详情、普通问答、消息响应模型、分页、所有错误、SSE payload Schema、幂等与重连约束。
- `docs/api-contract.md` 已约定 `ack/delta/done/error` 和 15 秒心跳，但没有定义事件 ID、断线取消、续传窗口、重复提交和最终消息读取语义。
- 行程 AI 层已有配置、OpenAI 兼容适配器、Stub、超时/重试/限流范式。咨询是增量文本流，须建立独立的 `ConsultationProvider` 边界；可复用公共 HTTP/异常/脱敏思想，不把 `TripPlanningProvider` 或结构化行程 Schema 强行复用。
- 行程采用不可变版本。会话绑定行程后，咨询上下文必须明确引用提问时的具体 `tripVersionId/versionNumber`，不能在后台因当前版本切换而改变已发生问答的依据。

## 范围

### 本轮包含

- 会话创建、稳定游标分页列表、会话详情及消息分页。
- 普通问答：请求返回完整助手消息，持久化用户/助手消息及 usage。
- SSE 流式问答：ack、delta、done、error、心跳、客户端断开取消和有限窗口重连/补发。
- 可选行程上下文，严格校验行程归属、软删除状态和可用版本。
- 咨询 provider 抽象、OpenAI 兼容流式适配器、确定性 Stub、错误分类、限流、并发、超时和取消。
- 提示词注入、隐私、敏感内容和输出安全的分层防护。
- Flyway 演进、领域实体、Repository、服务、控制器、配置与可观测性。
- 单元、持久化、接口、SSE、并发、安全和 MySQL 迁移测试。
- 验证通过后同步 README、接口文档、数据模型和 TODO 第 4 阶段。

### 本轮不包含

- 前端聊天界面和流式渲染（TODO 第 5 阶段）。
- 地图、天气、景点营业信息或交通实时报价（TODO 第 6 阶段）。回答必须声明尚未接入实时数据，不得把模型推测包装为实时事实。
- 多节点共享流连接、Kafka/Redis 事件总线、跨节点恢复。本轮采用单实例有界执行资源和数据库短期事件日志；文档必须说明水平扩展前需要粘性路由或共享流基础设施。
- 医疗、法律、签证、边境政策的权威决策。此类问题只给一般信息、风险提示和核验渠道建议。
- 附件上传、语音、图片、多模态或主动联网检索。

## 核心业务规则

### 会话与归属

1. 所有接口从认证上下文取得 `userId`，不得接受客户端 `userId`。所有 Conversation/Message 查询均限定 `user_id`、`deleted_at is null`；不存在和越权统一 `404 CONVERSATION_NOT_FOUND`。
2. 创建会话时 `title` 去除首尾空白，空串归一为缺省，最大 100 字符。缺省标题可在第一条成功用户消息后由服务端截取安全纯文本生成，不调用额外 AI。
3. 可选 `tripId` 必须属于当前用户、未软删除且已有 READY 当前版本，否则分别统一为 `404 TRIP_NOT_FOUND` 或 `409 TRIP_NOT_READY`。禁止通过错误差异探测其他用户行程。
4. 会话绑定的 `tripId` 在本轮创建后不可修改。每次问答额外保存实际使用的 `trip_version_id`，因此行程后来调整或回退不会改写历史消息上下文。
5. 会话列表按 `(updated_at DESC, id DESC)` 稳定排序，游标必须不透明且带 HMAC 校验；消息按 `(created_at ASC, id ASC)` 展示，详情使用独立消息游标并固定方向，避免大会话一次加载。
6. 列表摘要返回最后一条已持久化消息的安全预览、消息数和生成中标记；不得返回完整提示、系统消息、供应商原始响应或其他用户数据。

### 消息、幂等和状态机

1. 输入 `content` 去除首尾空白后 1～5000 字符，按 Unicode 字符而非字节计数；拒绝仅空白、控制字符滥用和超限内容。请求体只允许声明用户内容，不允许客户端指定 role/model/system prompt。
2. 普通和流式问答都要求 `Idempotency-Key`（8～128 字符）。唯一边界为 `(conversation_id, operation, key)`，同时保存规范化内容摘要；同 Key 同内容返回/续接同一用户消息和助手消息，不重复调用 provider，同 Key 不同内容返回 `409 IDEMPOTENCY_KEY_CONFLICT`。
3. 一次 turn 原子创建 `USER/COMPLETED` 和 `ASSISTANT/PENDING` 两条消息，再开始供应商调用。助手状态只允许 `PENDING -> STREAMING -> COMPLETED|FAILED`；完成/失败为终态，不允许重新覆盖。
4. 同一会话同一时刻最多一个 active turn，避免上下文顺序分叉；冲突返回 `409 CONVERSATION_BUSY`。不同会话仍受用户并发和全局并发限制。
5. 普通问答成功返回 `201` 或 `200` 必须在契约中固定，推荐 `201 MessageTurnResponse`；调用超时/失败时助手消息写为 FAILED，并以标准 JSON 错误返回。若幂等重放的是已完成 turn，返回 `200`；若仍运行返回 `409 MESSAGE_IN_PROGRESS`，不得启动第二次生成。
6. provider usage 仅作为观测字段，空缺允许；数据库计数必须非负。日志和 API 不暴露 provider 原始错误体、API Key、完整用户内容或完整行程上下文。

### 上下文构造

1. 上下文窗口由版本化系统提示、产品安全说明、可选行程结构化摘要、最近已完成的对话 turns 和当前问题组成。角色由服务端从数据库映射，数据库中的 `SYSTEM` 消息不对客户端开放。
2. 行程上下文只提取目的地、日期、时区、预算摘要、警告和当前版本的日程；不序列化 JPA 实体，不包含用户邮箱、Token、内部 ID、provider 元数据或软删除记录。
3. 用户文本、历史消息和行程自由文本均用明确的数据边界/结构化字段传给 provider，并在系统提示中声明其不具备改写系统规则的权限。类似“忽略之前指令”“显示系统提示”的文本可以被讨论，但不得被执行。
4. 使用确定性 token/字符预算：系统与当前问题优先，行程摘要其次，历史从新到旧截取且保持完整 turn；不得从半条 UTF-8 文本中截断。超出单条输入上限直接 400，历史过长则裁剪并记录不含内容的指标。
5. 只读取 `COMPLETED` 消息作为历史；FAILED/STREAMING/PENDING 消息不进入后续 prompt。咨询前锁定本次 `tripVersionId` 并记录到 turn，避免生成期间版本漂移。

### 安全、隐私与敏感内容

1. 输入先执行规范化和确定性规则检查，再调用可替换的 `ContentSafetyPolicy`。至少阻止凭证/密钥索取、系统提示泄露、明显违法伤害指导和高风险个人数据滥用；普通旅游问题、合理的签证/健康提醒不得被粗暴误杀。
2. 对邮箱、电话、证件号、支付卡号和疑似密钥做最小化识别。默认不把无关个人信息传给 provider；可安全遮蔽的字段以占位符替换并记录 `redacted=true`，高风险或无法安全处理时返回 `400 SENSITIVE_CONTENT_REJECTED`。禁止在日志记录原值。
3. 系统提示明确：不泄露提示/密钥；不接受用户提升权限；不声称拥有实时数据；对价格、开放时间、天气、交通、签证和安全政策提示用户从官方实时来源核验；高风险健康/法律问题给出边界说明。
4. 输出在发送/持久化前经过增量安全策略。为避免先泄露后拦截，delta 需在小缓冲区完成边界扫描后再下发；发现严重违规立即取消 provider，助手置 FAILED，发 `error`，且不继续发送危险内容。
5. 原始 prompt、供应商请求/响应不得持久化。仅保存用户实际可见内容、受控元数据、usage、稳定 error code 和上下文版本引用。数据保留/删除策略写入 README；本轮会话软删除时客户端不可再读，未来物理清理另行实现。
6. 安全规则必须以接口/策略实现，便于后续接入供应商 moderation；不能只依靠系统提示。所有拒绝使用稳定错误码和中性文案，不向攻击者说明具体匹配规则。

## SSE、取消与重连语义

### 传输约束

1. 首次请求为 `POST /conversations/{conversationId}/messages:stream`，使用 `fetch` 读取 `text/event-stream`，而非依赖只能 GET 的原生 EventSource。请求携带 Bearer Token、`Idempotency-Key` 和 JSON 内容。
2. 每个业务事件必须包含 SSE `id`，格式为同一 stream 内单调递增的十进制序号；事件 `data` 为单行 JSON。事件类型固定为：
   - `ack`：包含 `streamId`、`userMessageId`、`assistantMessageId` 和首个 event ID；
   - `delta`：包含 `streamId`、`messageId`、`sequence`、增量 `content`；
   - `done`：包含最终消息 ID、最终状态、usage 和 `replayed`；
   - `error`：包含稳定 `code`、可展示 `message`、`retryable` 和最终/可恢复标志。
3. 每 15 秒输出 `: ping` 注释心跳，心跳不分配事件 ID、不进入重放日志。响应设置禁止代理缓冲/缓存的头；响应一旦提交，错误只用 `error` 事件表达并正常关闭流。
4. delta 必须按序追加；客户端只能以 `done` 后的会话详情/消息详情为最终事实。服务端不得依赖客户端收齐所有 delta 才持久化最终消息。

### 客户端断开与取消

1. `SseEmitter`/异步响应的 completion、timeout 和 error 回调连接同一 `CancellationToken`。检测到客户端断开后立即请求取消 provider、停止产生事件并释放并发许可。
2. 单纯网络断开不立即把助手消息标记 FAILED：进入可重连的 `STREAMING/DISCONNECTED` 运行状态并保留短暂宽限期（建议 30 秒）。宽限期内 provider 可继续到有界缓冲上限；无人重连或缓冲达到上限则取消并置 `FAILED/CLIENT_DISCONNECTED`。
3. 用户显式取消使用 `DELETE /conversations/{conversationId}/streams/{streamId}`（或契约选定的 `POST ...:cancel`），必须校验所有权，幂等返回 204；取消后置 `FAILED/CLIENT_CANCELLED`，不自动重试。
4. 服务端总截止时间（建议 60 秒）、首字节超时（建议 15 秒）和空闲 delta 超时（建议 20 秒）独立配置。超时取消上游并映射 `AI_TIMEOUT`；中断/取消不进入传输重试。

### 重连与重放

1. 首次 `ack` 返回不可猜测 `streamId`。手动重连使用 `GET /conversations/{conversationId}/streams/{streamId}`，携带 `Last-Event-ID`；服务端校验用户、会话和 stream 归属后，从下一序号补发。
2. 为支持跨 HTTP 连接重放，业务事件写入短期 `conversation_stream_events`。先持久化事件，再下发；唯一键 `(stream_id, sequence)` 保证不重复。事件保留时间建议 10 分钟，可配置并定时清理。
3. `Last-Event-ID` 缺失表示从可用最早事件重放；非法、属于其他 stream、晚于最新序号返回 400；请求早于保留窗口返回 `409 STREAM_REPLAY_EXPIRED`，客户端随后读取会话详情获取最终消息，绝不重新提交同一问题。
4. 已完成/失败的 stream 在保留期内重连时重放剩余事件并关闭；仍运行则先重放，再订阅实时事件。单实例内用受控 registry 连接生产者与订阅者；数据库事件是恢复事实，不能只放内存。
5. 应用进程重启后，无法恢复已中断的供应商 socket。启动恢复任务把遗留 PENDING/STREAMING 消息标记 `FAILED/SERVICE_RESTARTED`，补一条终态 error 事件；客户端可重连看到终态，但系统不得自动再次调用 provider，以免重复计费和产生不同回答。

## Provider、限流与失败恢复

1. 定义 `ConsultationProvider`，输入为供应商无关消息列表与取消信号；普通模式返回完整结果，流式模式通过受背压控制的 chunk callback/publisher 产生增量和 usage。领域、Controller 和数据库不得依赖 OpenAI DTO。
2. `OpenAiCompatibleConsultationProvider` 显式配置 base URL、Key、model 和 endpoint，正确解析 `data:` 分片与 `[DONE]`；关闭下游连接时必须关闭上游响应体。HTTP 原始错误只在适配器内转换为稳定异常。
3. Stub 对相同上下文产生相同中文回答，并按固定 chunk 大小/顺序流出；测试夹具可触发首字节超时、流中超时、429、5xx、非法 UTF-8/事件、敏感输出和取消。Stub 不读取 AI Key、不访问网络。
4. 调用前执行按用户 RPM、用户并发（建议 1）和全局并发限制。普通/流式共享配额，许可在 done/error/cancel/断开终止时必定释放。单节点限流限制写入 README，并保留可替换端口。
5. 普通非流式请求只对连接失败、429 和可重试 5xx做有限、带抖动的重试；流式请求仅在尚未向客户端发送任何 provider delta 时允许最多一次安全重试。一旦下发 delta，禁止从头重试，避免重复文本。
6. 错误映射：供应商 429 为 `AI_RATE_LIMITED`，连接/5xx 为 `AI_UNAVAILABLE`，截止时间为 `AI_TIMEOUT`，输出协议错误为 `AI_OUTPUT_INVALID`，安全拒绝为 `CONTENT_REJECTED`，用户取消和断线分别为 `CLIENT_CANCELLED/CLIENT_DISCONNECTED`。
7. 观测仅记录 request ID、用户/会话/消息的不可逆或内部 ID、provider、model、耗时、首字节时间、chunk 数、token 数、终态和 error code；禁止记录 Bearer Token、Key、完整消息或 prompt。

## 接口契约先行

实现前先修改 `docs/openapi.yaml` 和 `docs/api-contract.md`。建议最终接口：

| 方法与路径 | 成功 | 说明 | 主要错误 |
| --- | --- | --- | --- |
| `POST /conversations` | `201 ConversationResponse` | 创建会话，可选绑定自己的 READY 行程 | 400, 401, 404, 409 |
| `GET /conversations` | `200 ConversationListResponse` | 更新时间倒序的签名游标分页 | 400, 401 |
| `GET /conversations/{id}` | `200 ConversationDetailResponse` | 会话元数据及消息分页；或把消息拆为子资源，二者择一固定 | 400, 401, 404 |
| `POST /conversations/{id}/messages` | `201/200 MessageTurnResponse` | 普通完整问答，要求 Idempotency-Key | 400, 401, 404, 409, 429, 502/503/504 |
| `POST /conversations/{id}/messages:stream` | `200 text/event-stream` | 首次流式问答，要求 Idempotency-Key | 流建立前同上；建立后使用 error 事件 |
| `GET /conversations/{id}/streams/{streamId}` | `200 text/event-stream` | 携带 Last-Event-ID 手动续传/重放 | 400, 401, 404, 409 |
| `DELETE /conversations/{id}/streams/{streamId}` | `204` | 显式幂等取消 | 401, 404 |

契约必须补齐：

- `ConversationSummary`、`ConversationDetail`、`Message`、`MessageTurn`、分页 meta，以及消息 role/status 的稳定枚举。
- `CreateConversationRequest`、`SendMessageRequest`，两个问答入口共享相同校验；普通和流式均明确 Idempotency-Key。
- SSE 四类事件的 JSON Schema、`id`/sequence 语义、Last-Event-ID、心跳、终态、重放过期与连接关闭规则。OpenAPI 对流内容表达有限，详细行为以 api-contract 配套约束并通过测试锁定。
- 每条消息的 `tripVersionNumber/contextUsed`、`createdAt/completedAt`、可选 usage/errorCode；客户端不可见 SYSTEM 内容。
- 所有路由完整列出 401、404、409、429、502、503、504；新增复用响应和稳定错误码。
- 明确列表/详情分页方向、默认/最大 limit、签名游标不可构造；跨用户资源统一 404。

## 数据迁移

新增后续 Flyway 迁移（按仓库当前最大版本顺延，预计 `V5__support_ai_consultation.sql`，不得修改 V1）：

1. `messages` 增加 `user_id` 不需要重复存储，但增加可选 `trip_version_id`、`turn_id`、`client_request_key/hash` 或建立独立 `conversation_message_requests` 幂等表；唯一约束必须能原子阻止同一会话重复 turn。
2. 增加流相关元数据：`stream_id`、`last_event_sequence`、`disconnected_at`，或建立 `conversation_streams` 表保存 conversation、assistant message、状态、最后序号、过期时间、创建/完成时间。推荐独立表，避免把传输状态混入消息领域状态。
3. 建立 `conversation_stream_events`：stream、sequence、event_type、受控 payload JSON、created_at、expires_at；唯一 `(stream_id, sequence)`，并为过期清理建立索引。只保存客户端可见安全内容。
4. 如需清晰区分消息终态和连接状态，为 messages 状态保留 `PENDING/STREAMING/COMPLETED/FAILED`，在 stream 表使用 `ACTIVE/DISCONNECTED/COMPLETED/FAILED/CANCELLED`，同步数据库 CHECK 与 Java enum。
5. 为所有权和分页补索引：`conversations(user_id, deleted_at, updated_at DESC, id DESC)`、`messages(conversation_id, created_at, id)`；确认 MySQL 8.4 执行计划。
6. 外键：message 的 trip version 必须属于会话绑定行程，应用层校验且数据库至少保证引用存在；stream/event 随 conversation/message 生命周期级联清理。金额或个人隐私不得写进流元数据之外的冗余字段。

## 文件级实施步骤

### 1. 契约与配置

1. 先扩展 `docs/openapi.yaml` 与 `docs/api-contract.md`，确定普通返回码、消息分页、流重连路径、SSE payload、幂等、取消及错误码。
2. 更新 `docs/data-model.md`，描述 conversation/message/turn/stream/event、行程版本快照关系和状态机。
3. 在 `application.yml`、`application-test.yml`、`application-prod.yml` 增加 consultation provider、prompt version、上下文预算、超时、事件保留、心跳、并发/RPM、断线宽限及缓冲上限。使用 `@ConfigurationProperties` 校验正数和时间关系。
4. 如 Spring MVC 的异步/SSE 能力足够，继续使用当前 web starter；只在确有必要时增加成熟的流式 HTTP 客户端依赖，避免引入整套 WebFlux 与 MVC 运行模型冲突。

### 2. 持久化与领域

1. 新增迁移、`Conversation`、`Message`、`ConversationStream`、`ConversationStreamEvent`、消息幂等实体/Repository 和状态枚举。
2. Repository 所有用户入口提供 owner-scoped 查询；会话 active turn 通过条件更新、唯一键或悲观锁原子串行化，不能只靠 JVM `synchronized`。
3. 为列表与消息分页实现 HMAC 游标编解码；非法/篡改游标统一 400。显式 DTO mapper 防止实体关系递归和 SYSTEM 消息泄露。
4. 实现启动恢复与过期事件清理；测试中注入 `Clock`，不依赖真实等待。

### 3. 上下文与安全

1. 在 `consultation/context` 实现 `ConversationContextAssembler`、字符/token 预算、完整 turn 裁剪和 `TripContextSnapshotMapper`。
2. 保存版本化资源 `prompts/consultation/v1/system.txt`；用户、历史和行程数据以结构化 provider message 传入。增加测试防止用户文本进入 system role。
3. 定义 `ContentSafetyPolicy`、`PrivacyRedactor` 和安全判定结果；实现可解释但不泄露规则的本地基线策略，并为未来 moderation adapter 留端口。
4. 对输入、行程自由文本和增量输出分别测试注入、隐私和敏感内容。所有日志通过现有 `SensitiveDataSanitizer`，必要时扩展但不得削弱认证日志脱敏。

### 4. Provider 与网关

1. 在 `consultation/ai` 定义 provider 请求/结果/chunk、取消令牌、usage 和统一异常分类。
2. 实现确定性 Stub 及故障触发器；实现 OpenAI 兼容的普通和流式调用，确保关闭响应体、传播取消和正确处理分片边界。
3. `ConsultationGateway` 集中执行 admission、并发许可、首字节/空闲/总超时、有限重试、错误转换和指标；所有退出路径用 finally 释放许可。
4. prod 默认 openai-compatible 且 Key 为空启动失败；test 默认 stub。不得运行时捕获真实 provider 异常后改用 Stub。

### 5. 应用服务与 HTTP/SSE

1. `ConversationCommandService` 创建会话并校验行程；`ConversationQueryService` 实现列表、详情和消息分页。
2. `MessageTurnService` 原子创建幂等 turn、锁定行程版本、组装上下文，普通模式完成后一次性写助手终态，失败可靠落库。
3. `ConversationStreamService` 管理 stream/event 持久化、订阅、重放、断开宽限、显式取消和终态；provider 工作不得占用 servlet 请求线程。
4. `ConversationController` 返回 JSON 接口；`ConversationStreamController` 使用 Spring MVC async/SSE，设置 UTF-8、no-cache/no-buffering、心跳和生命周期回调。不得在 Controller 中直接调用 provider 或拼接 prompt。
5. done 前确保助手完整内容与 usage 已提交；事件先持久化后发送。客户端断开、发送失败、provider 终止和事务失败的竞态通过幂等终态条件更新解决。

### 6. 验证和收尾

1. 执行下面测试矩阵及 `mvn verify`，并运行 OpenAPI minimal lint。
2. MySQL 8.4 从 V1 顺序迁移到最新版本，用 `ddl-auto=validate` 启动；验证 CHECK、JSON、外键、唯一键和分页索引。
3. 使用无 AI Key 的 Stub 完成注册→创建行程→创建会话→普通问答→流式问答→断线重连→详情核对的全流程验收。
4. 使用受控 mock HTTP server 验证 OpenAI 兼容普通/流式协议、超时、429/5xx、分片和取消；CI 不调用真实供应商。
5. 验证后更新 README 配置/限制/运行说明。本轮全部验收通过并经独立审核后，才勾选 TODO 第 4 阶段六项。

## 测试矩阵

### 会话、归属和分页

- title 缺省、空白、最大长度和超限；合法 READY trip、未 READY trip、已删除 trip、其他用户 trip。
- 未认证 401；会话不存在与跨用户的列表外详情、问答、重连、取消均表现为一致 404。
- 空列表、同 updatedAt 的稳定排序、多页无重复/遗漏、非法及篡改游标 400。
- 消息正序分页、边界 limit、SYSTEM 不返回、FAILED 状态可见但供应商原始错误不可见。
- 绑定版本在行程调整/回退前后保持历史可追溯；后续新 turn 使用提问时的新当前版本。

### 普通问答与幂等

- content 1/5000 字符、空白、控制字符、超限及 Unicode 边界。
- 同 Key 同内容串行/并发只产生一个 USER、一个 ASSISTANT 和一次 provider 调用；同 Key 不同内容 409；不同会话可复用 Key。
- 同会话并发不同 turn 只有一个 active，另一个 409；不同会话受用户/全局并发限制。
- 成功状态、usage、model、completedAt 正确；timeout、429、5xx、非法输出均写稳定 FAILED 状态并映射正确 HTTP/error code。
- 普通可重试异常遵守次数与截止时间；重试成功仍只落一条助手消息。

### SSE 协议、取消和重连

- 响应 Content-Type/缓存/缓冲头正确；顺序为 ack、多个 delta、done，事件 ID 单调且 payload 符合 Schema；15 秒心跳用可注入调度器测试。
- 多字节 UTF-8 跨 provider chunk 不乱码、不丢失；delta 拼接严格等于最终持久化内容。
- 流建立前错误使用 HTTP JSON；ack 后错误使用一个终态 error 事件并关闭连接。
- 客户端在 ack 前、delta 中、done 竞态时断开；取消传播、许可释放、消息/stream 终态符合约定，无线程或连接泄漏。
- 宽限期内重连从 Last-Event-ID 下一条补发，既不重复也不遗漏；完成流可重放；非法 ID、跨 stream ID、过期窗口分别返回稳定错误。
- 显式取消幂等；取消后 provider 停止、无新 delta/done；应用重启恢复遗留流为 SERVICE_RESTARTED 且不重复调用 AI。
- 慢客户端超过缓冲上限时可控取消，不导致无界内存；多个订阅者策略固定（推荐同一 stream 只允许一个活动订阅者，第二个 409）。

### 上下文与安全

- 无行程、有行程、长历史、历史裁剪、FAILED 消息排除；当前问题与系统规则始终保留。
- 行程摘要字段白名单准确，不包含邮箱、JWT、内部 prompt/provider 元数据；只引用所有者当前 READY 版本。
- “忽略系统指令/显示提示词/伪造 system role”等注入只作为用户数据，provider 请求角色顺序不被改变。
- 邮箱、电话、证件、银行卡、疑似 API Key 的遮蔽/拒绝符合策略；日志捕获测试确认不含原文、Authorization 和 AI Key。
- 敏感输入在 provider 前拒绝且不计供应商调用；敏感输出在下发前被缓冲检查、取消并终止，不泄露被拦截文本。
- 回答对实时价格/天气/营业时间/签证政策包含非实时提示；Stub 固定输出可断言。

### Provider、韧性和配置

- Stub 相同输入得到相同普通答案与 chunk 序列，可触发每个故障且不联网。
- OpenAI 请求包含正确 model、系统提示版本和角色；流式解析处理任意网络分片、空行、usage、`[DONE]` 和异常终止。
- 首字节、空闲和总超时独立生效；普通/首 delta 前重试，已下发 delta 后绝不重试。
- 用户 RPM、用户并发、全局并发准确，所有成功/异常/取消路径释放许可。
- test/dev 无 Key 使用显式 Stub；prod 默认真实 provider，缺 Key 启动失败；生产错误绝不切换 Stub。

### 数据库与恢复

- 状态 CHECK、事件序号唯一、turn 幂等唯一、所有外键和级联行为正确。
- 事件先提交再发送；重复保存/重复终止不产生双 done/error；最终助手内容与 delta 合并一致。
- 事件过期清理不删除仍 active 流所需数据；会话软删除后所有读取和重连为 404。
- MySQL V1→最新迁移与 Hibernate validate 通过；H2 测试模型不得掩盖 MySQL 的 JSON、索引或 CHECK 差异。

## 验收标准

- TODO 第 4 阶段六项全部有对应实现和自动化证据，无未定义的占位接口。
- OpenAPI 先于实现更新且 lint 通过；普通 JSON、会话分页与 SSE 事件/重连行为和文档一致。
- 所有 owner-scoped 接口通过跨用户测试，未发现会话、消息、stream 或行程上下文泄露。
- Stub 在没有 `AI_API_KEY` 时完成全部本地/CI验收；生产配置缺 Key 启动失败，任何真实 provider 故障不降级 Stub。
- 普通与流式问答都持久化完整状态；断线、取消、超时、应用重启、重放过期均有确定的终态和恢复路径。
- 注入、隐私和敏感内容防护在 provider 前后都有可执行策略及测试，不仅是提示词声明。
- `mvn verify`、OpenAPI lint、MySQL 8.4 全量迁移和 `ddl-auto=validate` 全部通过，README/接口/数据模型同步。

## 风险与控制

| 风险 | 控制措施 |
| --- | --- |
| Spring MVC SSE 生命周期与 provider 阻塞调用造成线程/连接泄漏 | 使用专用有界执行器、取消令牌、所有回调幂等终止和并发压力测试；不占 servlet 线程等待模型 |
| POST SSE 无浏览器自动重连 | 明确使用 fetch 首次 POST、`streamId + GET + Last-Event-ID` 手动续传，并在前端契约中固定 |
| 先发 delta 后发现危险内容 | 小窗口缓冲并在发送前执行增量安全扫描；严重违规立即取消且不发送命中内容 |
| 数据库事件日志放大写入 | 控制 chunk 合并粒度、payload/缓冲上限、10 分钟 TTL 与批量清理；用压力测试确定默认值 |
| 断线、done、cancel、timeout 竞态产生双终态 | 数据库条件更新和唯一事件序号，所有终止路径共享单一 finalize 服务 |
| 多实例无法把重连订阅到原 provider 流 | MVP 明确单实例/粘性会话约束；数据库可重放终态，水平扩展前引入共享 pub/sub |
| 行程版本在问答中变化导致上下文不可追溯 | turn 创建时锁定并保存具体 tripVersionId/versionNumber |
| 本地规则误伤正常旅游问题或漏过新型攻击 | 策略端口、分级处置、稳定测试语料与审计指标；后续可接 moderation，不将安全只交给 prompt |
| 供应商兼容接口的流协议差异 | provider 适配器隔离、mock server 契约测试；领域层只依赖统一 chunk 模型 |

## 独立审核门

实现完成后必须由独立审核终端审查，至少覆盖以下审核门；任何高/中风险项修复后重新审核：

1. **契约门**：OpenAPI、api-contract、Controller、DTO、状态码、SSE 事件和重连语义逐项一致。
2. **所有权门**：会话、消息、stream、事件及行程版本的每条查询路径均 owner-scoped，越权统一 404。
3. **事务/竞态门**：turn 幂等、单会话 active、事件先存后发、单一终态、断线/取消/done 竞态和启动恢复无重复调用或永久 STREAMING。
4. **安全门**：注入边界、隐私最小化、日志脱敏、敏感输入/输出策略和非实时声明均有测试证据。
5. **资源门**：SSE executor、provider socket、定时器、订阅 registry 和并发许可在所有异常路径释放；缓冲和事件保留有明确上限。
6. **生产配置门**：真实 provider 配置和缺 Key 失败已验证，Stub 只在显式 dev/test 配置启用，绝无静默降级。
7. **数据门**：MySQL 8.4 顺序迁移、Hibernate validate、约束/索引/清理策略通过；不依赖 H2 特性假象。
8. **回归门**：认证和行程规划既有测试继续通过，完整 `mvn verify` 与 OpenAPI lint 通过。

只有审核结论为通过、阻断问题为零且验收证据记录完整，主实施终端才能更新 TODO 第 4 阶段并进入前端 Round 04。
