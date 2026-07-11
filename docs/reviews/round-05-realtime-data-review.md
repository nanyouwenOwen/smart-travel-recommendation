# Round 05 独立审核报告

审核日期：2026-07-12  
审核范围：`docs/plans/round-05-realtime-data.md`、`TODO.md` 第 6 节及当前工作区实现  
结论：**PASS**

## GitHub Actions 证据核验

[GitHub Actions run 29162926553](https://github.com/nanyouwenOwen/smart-travel-recommendation/actions/runs/29162926553) 已于 commit `7133c637` completed/success；`backend`、`openapi`、`frontend`、`e2e` 全部成功，e2e job 的 MySQL 8.4 容器初始化、Playwright 安装、`npm run test:e2e` 和容器清理均成功。

通过 `git show 7133c637:...` 复核确认：天气窗口裁剪、`DEMO_STUB` 诚实来源、地点 stale 映射、64 KiB Base64 JSON prompt 边界及单 provider warning 均已包含在该 commit；当前唯一工作区变更为本审核文档。因此该 run 完整验证了最终实现，可确认 Flyway V6 在 MySQL 8.4 执行成功且真实栈 Playwright 通过。

审核过程中曾误判上述关键修复尚未进入 `7133c637`，现已根据 Git 对象内容与 `git status --porcelain` 纠正。Round 05 最终结论为 **PASS**。

## 2026-07-12 第二次复审（最终结论）

Round 05 的低流量 MVP 实现 **PASS**。上次列出的 6 个代码阻断均已修复：

- 地点时区不再对所有非中国地点返回 UTC；常见国家使用 IANA zone，其余使用经度推导的合法 `Etc/GMT±N` 固定偏移。
- 天气查询裁剪到 `today..today+16`，完全超范围返回 `UNAVAILABLE`，部分覆盖明确返回 `unavailableDates` 和 warning。
- Stub 来源统一为 `DEMO_STUB / 演示数据（非实时）`，OpenAPI、后端集成断言和前端徽标一致；Stub AI 只说“带来源的数据”，不再宣称实时第三方数据。
- 地点 stale-if-error 会把每项来源映射为 `STALE`。
- AI 实时上下文改为最大 64 KiB 的 Base64 JSON 数据块，第三方文本不能闭合外层 `<realtime-data>` 边界；超限时明确不用实时数据且 sources 为空。
- weather 或 places 任一失败都会生成并渲染全局 warning，不再静默隐藏。
- 普通 AI 消息集成测试已直接证明来源持久化及带来源 Stub 回答；现有 SSE 客户端仍以 done 后 GET 回源为最终真相，E2E 已覆盖刷新后来源。

### 本次独立门禁

- `cd backend && mvn -q verify`：退出码 0。
- `cd frontend && npm run type-check && npm run test:coverage && npm run build`：退出码 0；17 个测试文件、41 个测试通过；lines 91.66%、branches 78.82%、functions 87.32%；生产构建成功。
- `npx --yes @redocly/cli lint docs/openapi.yaml --extends=minimal`：退出码 0，API description valid，37 条非阻断 warnings。
- 人工核对 `DEMO_STUB` 已进入 OpenAPI provider 枚举，前端 provider 类型保持开放字符串兼容；OpenAPI 不再有重复 `messages` 属性。

### MySQL 8.4 交付门禁

本地 Maven 测试使用 H2 schema，因此最终以 [GitHub Actions run 29162926553](https://github.com/nanyouwenOwen/smart-travel-recommendation/actions/runs/29162926553) 的干净环境为准。该 run 在 commit `7133c637` 上以 MySQL 8.4 service 启动应用并完成 Playwright，job 全绿，门禁已满足。

### 已知但不阻塞 MVP 的限制

- 经度兜底的 `Etc/GMT±N` 是固定偏移，不处理 DST，也无法精确覆盖美国、澳大利亚等多时区国家；生产 live 模式应改用可靠 timezone provider/坐标时区库，或使用 Open-Meteo `timezone=auto` 并持久化其规范 IANA zone。
- Base64 边界防止结构闭合，但模型级提示注入仍应继续通过恶意 fixture、输出事实校验和结构化 tool calling 加固。
- per-key single-flight、provider host allowlist、条件请求、熔断、独立配额、缓存清理和前端 AbortController 仍按第一次复审列入生产增强。

下方历史初审/第一次复审保留为修正轨迹，其 FAIL 结论已被本节取代。

## 2026-07-12 第一次复审（本节结论优先于下方初审明细）

复审结论：**FAIL，但阻断项已显著收敛**。

已确认修复：地点搜索现在有持久缓存、认证用户 10/min 限流、live Nominatim 全局调用起点间隔至少 1 秒；天气/景点/地点刷新由全局同步段阻止并发击穿；不再把 Open-Meteo `generationtime_ms` 冒充源更新时间；Stub 会根据上下文明确说明使用/未使用实时数据；远期 Stub 不再生成天气日；新增 AI 意图最小调用、失败无来源和 Stub 真实性测试；地图会响应异步 places 并监听 tile error；OpenAPI 已补地点搜索 429 和 language 2..35；E2E 已覆盖显式地点搜索、天气、景点、AI 来源及刷新后持久来源。

门禁复跑结果：后端 `mvn -q verify` 通过；前端 type-check、41 个 Vitest、coverage 和 build 通过；OpenAPI minimal lint 有效（37 warnings）。

### 仍属 MVP 必须修复

1. **live 地点时区仍然错误。** Nominatim 映射仍是“中国 → Asia/Shanghai，其他所有国家 → UTC”。东京、纽约、巴黎等目的地会按 UTC 请求/展示 daily 天气，本地日期、日出日落语义错误。这是核心天气功能的数据正确性问题。位置：`backend/src/main/java/com/travelassistant/realtime/provider/RealtimeProviderConfiguration.java:40`。

2. **live 天气没有裁剪供应商预报窗口。** 实现把 Trip 完整 start/end（最多 30 天）直接传给 Forecast API；行程部分超出 Open-Meteo 允许窗口时，供应商可能拒绝整段请求，无法返回“可覆盖日期 + unavailableDates”。应先按当前可预报范围裁剪请求，完全超范围直接返回空 days 和全部 unavailableDates。位置：`backend/src/main/java/com/travelassistant/realtime/RealtimeService.java:27`、`backend/src/main/java/com/travelassistant/realtime/provider/RealtimeProviderConfiguration.java:31`。

3. **Stub 仍被 UI 伪装成 Open-Meteo/OSM 的 FRESH 实时数据。** Stub 虽把 provider 内部 condition 改成“演示：晴间多云”，但 `WeatherPanel` 根据 WMO code 自己渲染“多云”，不会显示该 condition；来源仍是 `OPEN_METEO`、`FRESH`，景点仍显示 OpenStreetMap。默认 Codespaces 用户会看到“实时 / Open-Meteo”但数据并未来自供应商。MVP 至少必须在 snapshot warning、来源标签或统一演示徽标中明确“Stub 演示数据，未调用第三方”，且 AI 回答不能称其为“实时数据”。位置：`backend/src/main/java/com/travelassistant/realtime/provider/RealtimeProviderConfiguration.java:22,29,34`、`backend/src/main/java/com/travelassistant/realtime/RealtimeService.java:27-28,37`、`frontend/src/components/realtime/SourceList.vue:6`。

4. **地点 stale-if-error 被错误标为 FRESH。** `cached(...)` 会返回 `stale=true`，但 `search(...)` 直接返回缓存数组，没有把每条 `sources.freshness` 转为 STALE，也没有 warning 承载字段。供应商故障时 UI 会把 7 天后、30 天 stale window 内的地点缓存继续标成实时数据。位置：`backend/src/main/java/com/travelassistant/realtime/RealtimeService.java:24`。

5. **第三方提示注入隔离仍可被结构边界突破。** 外部 name/opening_hours 等内容直接 JSON 序列化后放入字面 `<realtime-data>...</realtime-data>`；第三方字段可包含 `</realtime-data>`、控制文本或超长指令，从而闭合约定边界。当前测试只断言 prompt 含“不可信结构化数据”，没有注入 payload 测试。应对字段做长度/控制字符限制，并使用不能被数据闭合的编码或结构化消息边界，再验证来源仍由服务端生成。位置：`backend/src/main/java/com/travelassistant/consultation/RealtimeConsultationContext.java:3`。

6. **单 provider 失败状态仍未显示。** `TripDetailView` 已计算 `weatherError`/`placesError`，但 template 没有渲染它们；天气失败而景点成功时天气面板静默消失，反之亦然。至少显示各自错误和可操作重试，满足五态的诚实降级。位置：`frontend/src/views/TripDetailView.vue:3-8`。

7. **需要一次 MySQL 8.4 + Playwright 最终证据。** 本次后端门禁仍使用 H2 测试 schema，没有执行 Flyway V6；新增 E2E 代码也尚未在本次独立复审中以 MySQL 8.4 真栈运行。修复上述问题后，最终验收必须提供 V6 迁移成功及完整 E2E 通过结果。

### 可列入后续生产增强，不阻塞低流量 MVP

- 将当前全局 `synchronized cached` 改为真正的 per-key single-flight，避免一个慢 Overpass 请求阻塞所有天气和地点 key；横向扩容时把用户/provider 限流迁移到 Redis/网关。
- 增加 ETag/Last-Modified 条件请求、缓存定时清理、坏缓存同请求回源恢复、provider 熔断、指标和更完整的 stale Clock 测试。
- 对 provider base URL 建立 scheme + host allowlist、流式响应体大小上限、有界重试和 `Retry-After`；生产 profile 强制有效联系 User-Agent 与显式 live 模式。
- 为 Overpass/Open-Meteo 增加独立 RPM/日预算；实现 Transitous opt-in smoke 前先确认用途许可。
- realtime 前端改为 Pinia + AbortController/request generation，优化快速切换行程时的竞态；当前路由每次创建新 view，风险低于上述真实性阻断。
- 消除 OpenAPI 既有 warnings、完善天气单位字段、README 配置表以及缓存清理运维说明。

以上 7 个 MVP 项修复并完成 MySQL 真栈验收后，可再次复审；下方初审中已被本节列为“已确认修复”的条目不再作为阻断。

当前实现可以在 `stub` 模式演示地点选择、天气、景点、地图和消息来源，但尚未达到计划中关于公共供应商政策、数据真实性、缓存可靠性、安全及 AI 来源一致性的完成定义。第 6 节不得勾选。

## 阻断项

### P0：Nominatim 公共端点会被不受控调用，违反已承诺的使用政策

- `RealtimeService.search` 每次请求都先调用 provider，之后才查 `location_references`；没有按规范化 query/language/limit 的搜索缓存。重复搜索仍会访问 Nominatim。
- 没有跨用户、全实例至少 1 秒间隔的队列，也没有用户级地点搜索限流、429/`Retry-After` 映射。并发用户可直接并发打向公共端点。
- `RealtimeProviderConfiguration.get` 只有一次裸 GET，没有计划要求的有界重试、供应商配额/并发保护或熔断。

位置：`backend/src/main/java/com/travelassistant/realtime/RealtimeService.java:24`、`backend/src/main/java/com/travelassistant/realtime/provider/RealtimeProviderConfiguration.java:22-24,36`。

### P0：live 天气的 `sourceUpdatedAt` 语义错误，并会使 AI 来源静默丢失

- Open-Meteo 的 `generationtime_ms` 是本次响应生成耗时（数值毫秒），不是源数据更新时间；实现把它写入 `sourceUpdatedAt`，违反“不得用非源更新时间冒充”的规则，也不符合 OpenAPI `date-time`。
- 咨询持久化随后对该值执行 `Instant.parse`，解析失败后被空 catch 吞掉，导致 live 天气虽已注入 prompt，消息 `sources`/`dataUpdatedAt` 却完全不落库。普通响应、SSE done 后回源因此不能体现实际来源。

位置：`backend/src/main/java/com/travelassistant/realtime/provider/RealtimeProviderConfiguration.java:29`、`backend/src/main/java/com/travelassistant/consultation/ConversationService.java:9`。

### P0：AI 来源并不等于回答实际使用的来源

- 来源在调用 AI 前就写入 assistant message；并未在成功完成时确认回答实际使用了上下文，AI 失败时失败消息仍可能保留来源。
- 默认 Stub provider 永远只读取最后一条用户问题，完全忽略实时上下文，却仍会持久化 Open-Meteo/Overpass 来源；README 所称“消息携带结构化来源即表示实际使用”不真实。
- Stub 回答固定要求核验实时天气/营业时间，不能形成计划要求的确定性“带来源回答”；实时 provider 失败提示也只是 prompt 指令，Stub 不执行该指令，最终回答不会明确“本回答未使用实时数据”。
- 缺少普通请求、SSE、done、重放及 GET 最终回源一致性的测试，也缺少第三方提示注入不能改变模型行为/来源的验证。

位置：`backend/src/main/java/com/travelassistant/consultation/ConversationService.java:7-11`、`backend/src/main/java/com/travelassistant/consultation/RealtimeConsultationContext.java:3`、`backend/src/main/java/com/travelassistant/consultation/ai/StubConsultationProvider.java:12-13`、`README.md:173`。

### P1：缓存/降级实现缺少完成定义中的关键可靠性保证

- 天气/景点缓存没有 per-key single-flight；并发过期请求会同时访问供应商，并可能竞争插入同一 `cache_key`。
- 没有条件请求（ETag/Last-Modified）、缓存 payload schema/字段约束、坏缓存后的同次回源恢复、定时清理或 stale window 后的清理证据。
- 天气 stale 返回没有剔除已经过期的预报日期；缓存中的 `retrievedAt` 保留是合理的，但 `external_data_cache.source_updated_at` 始终写 null，与数据模型文档不一致。
- 测试只验证缓存表至少有两行，没有 fresh hit、过期刷新、stale-if-error、stale_until、并发 single-flight、重启读取、坏 payload 或可控 Clock 状态转换。

位置：`backend/src/main/java/com/travelassistant/realtime/RealtimeService.java:26-33`、`backend/src/test/java/com/travelassistant/realtime/RealtimeFlowIntegrationTest.java:15-32`。

### P1：live provider 安全、数据语义和模式约束未达到计划

- provider base URL 只检查 scheme，不校验允许 host；配置可指向任意 HTTP(S) 内网地址，缺少启动校验和重定向后 allowlist 机制。响应体使用 `BodyHandlers.ofString()` 整体读入后才检查长度，不能落实最大响应体保护。
- 只实现 `stub|disabled|其他值均视为 live`，无枚举校验，也没有计划声明的 `live-with-stale-fallback` 语义；生产 profile 不强制显式 live/合规 User-Agent，默认联系方式仍是 `admin@example.com`。
- Nominatim 地点时区被简化为“中国=Asia/Shanghai、其他=UTC”，会给东京、纽约等目的地生成错误本地天气日期；计划中的 Open-Meteo city fallback 未实现。
- Stub 对任意日期（包括远超预报窗口的 2030 年）生成并标记 `FRESH` 的虚构天气；这与“超出预报区间返回 unavailableDates、禁止伪装预报”冲突。
- Overpass 没有全局并发 1、用户限流、请求预算或供应商限流；交通只有 `enabled=false` 接口，没有可配置边界、来源目录或受控 probe。

位置：`backend/src/main/java/com/travelassistant/realtime/RealtimeProperties.java:12-37`、`backend/src/main/resources/application.yml:65-79`、`backend/src/main/java/com/travelassistant/realtime/provider/RealtimeProviderConfiguration.java:19-38`、`backend/src/main/java/com/travelassistant/realtime/provider/DisabledTransportProvider.java:1-3`。

### P1：前端没有实现计划要求的竞态、五态与地图更新语义

- 未新增 realtime Pinia store、request generation 或 AbortController。快速切换/卸载行程时旧请求仍可写入页面状态。
- weather 与 places 只有“两者都失败”才展示错误；单个 provider 失败被静默吞掉，对应面板直接消失，无法区分 loading/empty/stale/error/success，也无法单独重试。
- `DestinationMap` 只在 mounted 时读取一次 `places`。父组件先以空数组挂载地图、随后异步填充 places，因此景点 marker 不会出现；也没有 tile `error` 事件处理，网络瓦片失败时 `failed` fallback 通常不会触发。
- 聊天页固定显示“AI 回答不是实时信息”，即使消息明确带实时来源也仍显示，和来源区互相矛盾。

位置：`frontend/src/views/TripDetailView.vue:2-8`、`frontend/src/components/realtime/DestinationMap.vue:2-6`、`frontend/src/views/ConversationView.vue:2`。

### P1：缺少计划要求的 provider、迁移和真实栈验收证据

- 新增后端测试仅 2 个集成场景，全部走 stub；没有本地 HTTP provider contract tests，因此编码、User-Agent、恶意重定向、超时、429、5xx、畸形/超大 JSON、危险 URL、固定 Overpass 模板、天气日期裁剪等均未验证。
- 没有 AI 实时上下文/来源测试，也没有 realtime Playwright 的 stale、无缓存降级、范围外、320px 和键盘路径。
- 本次 `mvn verify` 走 H2 测试 schema，并未执行 Flyway V6；CI 虽配置 MySQL 8.4 E2E，但当前审核没有一次实际 MySQL 8.4 迁移成功证据。不能仅凭 SQL 文件存在宣称 MySQL 兼容。

位置：`backend/src/test/java/com/travelassistant/realtime/RealtimeFlowIntegrationTest.java`、`frontend/e2e/core.spec.ts`、`.github/workflows/ci.yml:45-75`。

### P2：接口与文档仍有不一致/不完整之处

- OpenAPI 地点搜索未声明计划要求的 429；语言参数 schema 缺少后端实际的 2..35 长度；places `category` 后端用自由字符串接收后手动校验，契约虽限制枚举但测试不足。
- OpenAPI 的 `Conversation.properties` 重复定义 `messages`，YAML 后定义覆盖前定义；minimal lint 没有报告，但应消除重复键。
- OpenAPI `WeatherDay` 没有单位字段，后端/前端也没有从供应商 `daily_units` 传递单位；前端硬编码摄氏度，无法满足单位契约。
- README 未列出三个实时用户接口，配置表也未完整披露实时端点、User-Agent、TTL/降级和生产限制；“默认实时数据 Stub”与真实数据来源视觉标签容易让演示数据被误解为实时第三方结果。

位置：`docs/openapi.yaml:124-162,770-795,920-948`、`README.md:125-173`、`frontend/src/components/realtime/WeatherPanel.vue:1-2`。

## 已通过的检查

- `cd backend && mvn -q verify`：退出码 0。
- `cd frontend && npm run type-check && npm run test:coverage && npm run build`：退出码 0；17 个测试文件、41 个测试通过；lines 91.66%、branches 78.82%、functions 87.32%；生产构建成功。
- `npx --yes @redocly/cli lint docs/openapi.yaml --extends=minimal`：退出码 0，API description valid，37 warnings。
- 已人工确认：新实时接口受认证保护；weather/places 做 Trip 归属校验且越权返回 404；地点搜索是按钮显式触发而非逐键 autocomplete；Overpass QL 的类别/半径来自服务端常量；website 仅保留 http/https；UI 保留 OSM 署名；普通与 SSE 均在 done 后以持久化消息回源的既有客户端机制仍存在。

## 复审条件

至少修复以上 P0/P1 项并补齐对应自动化证据后再申请复审。特别需要用可控 Clock 与本地 provider stub 证明地点政策、fresh/stale/single-flight、天气日期/时间语义、危险 URL、提示注入隔离和普通/SSE 最终来源一致；并提供一次 MySQL 8.4 Flyway V6 + 真实业务 E2E 成功记录。完成前 `TODO.md` 第 6 节应保持未勾选。
