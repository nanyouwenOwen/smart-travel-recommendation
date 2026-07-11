# Round 05：外部实时旅游数据集成实施计划

## 1. 目标、范围与完成定义

本轮完成 `TODO.md` 第 6 节全部五项：确定地图、天气、景点及交通供应商；建立缓存、降级与更新时间策略；接入地点检索/地理编码、天气和景点营业信息；在行程页面和 AI 咨询回答中展示可审计的数据来源与更新时间。

本轮必须保持无密钥的 GitHub Codespaces/CI 演示路径，同时把第三方调用封装在后端适配器之后。公共社区端点仅用于低流量开发和开源演示，不承诺生产 SLA；生产部署须能仅通过配置切换为付费、自托管或经批准的端点。外部数据不可改变现有行程版本、预算或 AI 输出的事实边界：它是带来源的辅助信息，失败时核心规划和咨询仍可使用，并清楚降级为“非实时/请核验”。

不在本轮实现机票、酒店价格、签证政策、购票、导航或完整公共交通路径规划；现有活动 `transportAdvice` 仍是 AI 建议。交通供应商只完成选型、适配器边界和受控探测，不在未获得公共服务运营者同意时把高成本路由请求接入用户主路径。

## 2. 已核实的供应商选择与使用边界

以下结论于 2026-07-12 根据供应商/基金会官方资料核实，实施时仍需把端点、User-Agent、联系地址和开关做成配置，不得硬编码商业承诺。

| 能力 | MVP 选择 | 无密钥演示 | 关键边界与生产替换 |
| --- | --- | --- | --- |
| 地图渲染 | Leaflet 前端渲染器 + OpenStreetMap 标准栅格瓦片 | 是 | 仅用户主动查看的交互地图；使用 HTTPS、保留浏览器 Referer、尊重瓦片缓存头、禁止预取/离线下载，地图角落常驻 OSM/ODbL 署名。瓦片 URL配置化；生产替换为合规商业瓦片或自托管，不经本项目后端代理公共瓦片。OSMF 明确标准瓦片为 best-effort、无 SLA，并建议不要硬编码 URL：[Tile Usage Policy](https://operations.osmfoundation.org/policies/tiles/)、[Attribution Guidelines](https://osmfoundation.org/wiki/Licence/Attribution_Guidelines)。 |
| 地点检索/地理编码 | OSMF 公共 Nominatim，后端调用 | 是（低流量） | 必须发送可识别的应用版本和联系地址 User-Agent；全实例单队列且最多 1 req/s；只允许用户显式提交搜索，禁止按键自动补全、系统性/批量查询；结果必须缓存并展示 OSM 署名。公共端点可随时撤回，生产切换自托管 Nominatim 或兼容商业地理编码器。[Nominatim Usage Policy](https://operations.osmfoundation.org/policies/nominatim/)。Open-Meteo Geocoding 只作为 Nominatim 不可用时的“城市/邮编级”备用，不冒充 POI 搜索；其结果包含坐标和 IANA 时区，[官方文档](https://open-meteo.com/en/docs/geocoding-api)。 |
| 天气 | Open-Meteo Forecast API `best_match` | 是（非商业演示） | 免费端点无密钥、无 SLA，官方当前标明非商业免费层 10,000 次/日且需署名；商业使用切到 customer endpoint/API key 或自托管，接口语义保持一致。只请求行程所需的有限 daily/hourly 字段；超出可预报区间返回不可用，禁止用历史/气候数据伪装预报。[Forecast Docs](https://open-meteo.com/en/docs)、[Pricing/limits](https://open-meteo.com/en/pricing)。 |
| 景点及营业时间 | OpenStreetMap 数据，通过公共 Overpass API 做有限半径 POI 查询 | 是（低流量） | 单次按目的地中心点合并查询，限制半径、类别、返回数、超时及 payload；禁止网格抓取或逐元素请求。公共实例建议远低于其约 10,000 请求/日和 1 GB/日安全边界，高流量须自托管/更换供应商：[Overpass public-instance guidance](https://dev.overpass-api.de/overpass-doc/en/preface/commons.html)。营业时间使用 OSM `opening_hours` 原始表达式，字段可能缺失或过期，UI 必须提示向景点官方渠道核验；该标签规范见 [OSM opening_hours](https://wiki.openstreetmap.org/wiki/Opening_hours)。 |
| 公共交通 | Transitous/MOTIS 2 作为候选适配器，MVP 默认关闭远程路由 | 无密钥，但不默认压测/接主路径 | Transitous 面向自由开源、非营利使用，best-effort；高频或计算昂贵的 routing/isochrone 要先联系项目方，并要求识别 User-Agent、来源署名和遵守底层 feed 条款。因此本轮只定义 `TransportDataProvider`、配置、来源目录链接与可选 smoke probe；取得同意后再启用路线接口。商业场景换 Google/HERE/本地 GTFS/MOTIS 等合规实现。[Transitous API policy](https://transitous.org/api/)。 |

OpenStreetMap 衍生的地图、地理编码和 POI 均在相邻界面展示“OpenStreetMap”及许可链接；Open-Meteo 天气展示“Open-Meteo”及其数据来源链接；Transitous 若启用则展示其 sources 链接和具体 feed 署名。不得把第三方 URL 中可能含有的查询、坐标或密钥写入普通日志。

## 3. 统一领域模型与真实性规则

### 3.1 数据来源元数据

新增统一、机器可读的 `DataSourceReference`：

```json
{
  "provider": "OPEN_METEO",
  "label": "Open-Meteo",
  "sourceUrl": "https://open-meteo.com/",
  "license": "CC BY 4.0",
  "retrievedAt": "2026-07-12T08:30:00Z",
  "sourceUpdatedAt": "2026-07-12T06:00:00Z",
  "freshness": "FRESH"
}
```

- `provider` 为稳定枚举：`OPENSTREETMAP_NOMINATIM`、`OPENSTREETMAP_OVERPASS`、`OPEN_METEO`、`TRANSITOUS`；前端只依赖枚举和字段，不解析 label。
- `retrievedAt` 是本系统成功取得/缓存数据的时间，始终存在；`sourceUpdatedAt` 只有供应商明确给出模型运行时间或元素时间戳时才返回，禁止用抓取时间冒充源更新时间。
- `freshness` 为 `FRESH | STALE | UNAVAILABLE`；使用 stale-if-error 时必须为 `STALE` 并带 `warning`。日期按用户行程时区展示，API 始终传带 offset 的 ISO 8601 instant。
- 所有天气、地点、景点响应都携带自己的 sources；聚合响应去重但不丢失具体来源。实时数据不能只有自然语言脚注。

### 3.2 地点身份与坐标

- 新增内部 `LocationReference`（UUID、不透明），保存规范名称、WGS84 纬经度、IANA 时区、国家代码、供应商及供应商对象 ID。前端不得把第三方 ID 当成本系统主键。
- 地点搜索结果返回 `locationId`；创建行程新增可选 `destinationLocationId`。后端只接受已缓存且未失效的内部地点引用，不直接信任浏览器提交的经纬度。旧客户端仍可只提交 `destination`，保持兼容；后台可按目的地显式解析，歧义时不猜选并仅返回“实时信息需要选择具体地点”。
- 坐标只用于目的地级天气、地图和附近景点，不发送用户家庭地址、自由文本额外要求、消息正文或个人身份数据给供应商。

### 3.3 天气和营业信息语义

- 天气响应按本地日期返回 `weatherCode`、最高/最低温、降水概率、降水量、最大风速、日出/日落及单位；保留 Open-Meteo 原始 WMO code，由后端映射稳定展示枚举/文案。仅返回供应商实际覆盖的行程日期，并列出 `unavailableDates`。
- 景点返回 OSM 名称、类别、坐标、`openingHours` 原始字符串、website（仅允许 `http/https`）、供应商元素链接、元素 `sourceUpdatedAt`。不根据复杂 `opening_hours` 自行声称“现在营业”；本轮展示原始规则和“请向官方核验”。字段缺失显示“暂无营业信息”，不能显示“全天开放”。
- 任何外部服务故障均不得覆盖既有 READY 行程、阻止版本恢复或让咨询消息永久卡住；降级文案必须区分“无数据”“超出预报范围”和“供应商暂不可用”。

## 4. API 契约优先变更

先修改 `docs/openapi.yaml`，再同步 `docs/api-contract.md`、后端 DTO 和 `frontend/src/api/types.ts`。所有新接口受 Bearer 认证保护，以便执行用户限流和防止公共代理滥用；均返回统一 envelope、`X-Request-Id`，不暴露第三方原始错误正文。

### 4.1 新增接口

1. `GET /locations/search?q={2..120}&language={BCP47}&limit={1..10}`
   - 只在用户提交时调用；返回 `LocationSearchResult[]`：`locationId/name/displayName/countryCode/latitude/longitude/timezone/type/sources`。
   - 空结果为 `200 []`；限流为 `429 REALTIME_RATE_LIMITED`；供应商不可用且无缓存为 `503 LOCATION_DATA_UNAVAILABLE`。
2. `GET /trips/{tripId}/realtime/weather`
   - 读取归属校验后的行程坐标与日期，不接受任意坐标；返回 `WeatherSnapshot { timezone, days, unavailableDates, sources, freshness, warning? }`。
   - 未选择/无法唯一解析地点为 `409 TRIP_LOCATION_REQUIRED`；全部超出预报范围仍返回 `200` 和空 days/unavailableDates，不伪造成服务故障。
3. `GET /trips/{tripId}/realtime/places?category=ATTRACTION&limit={1..20}`
   - 服务端固定允许的类别和最大半径（默认 5 km，上限不由浏览器扩大）；返回 `PlaceSnapshot { places, sources, freshness, warning? }`。
   - 查询模板由代码构造，不能把客户端字符串拼入 Overpass QL。
4. 地图不新增瓦片代理 API；前端从配置化模板直接加载瓦片，避免本服务成为不合规缓存代理。
5. 不在本轮公开交通路由 API。仅建立后端 provider SPI 和默认 `disabled` 实现；若配置启用，可由集成测试执行一个有界 smoke probe，但不进入 OpenAPI 用户契约。

### 4.2 兼容扩展

- `CreateTripRequest` 新增可选 `destinationLocationId`；`Trip`/`TripResponse` 新增可选 `destinationLocation`（内部 ID、坐标、时区与 sources），均为兼容性新增。
- `ConversationMessage` 新增 `sources: DataSourceReference[]`、可选 `dataUpdatedAt` 和 `freshness`。普通消息响应与 GET 消息详情都返回；SSE `done` 兼容新增 `sources`/`dataUpdatedAt`，前端仍在 done 后回源，以持久化消息为最终真相。
- 现有 AI 文本不强制模型生成 URL。来源由服务端根据本 turn 实际注入的 `RealtimeContext` 追加并持久化，避免模型伪造引用。没有实际调用外部数据时 sources 为空，并继续标为一般 AI 建议。
- OpenAPI 增加稳定错误码说明：`TRIP_LOCATION_REQUIRED`、`REALTIME_RATE_LIMITED`、`LOCATION_DATA_UNAVAILABLE`、`WEATHER_DATA_UNAVAILABLE`、`PLACE_DATA_UNAVAILABLE`、`REALTIME_DATA_STALE`（后者通常作为 warning 而非 HTTP 错误）。502/503/504 行为继续遵守现有通用契约。

## 5. 后端实施设计

### 5.1 包结构与适配器

新增 `com.travelassistant.realtime`，按 `location`、`weather`、`place`、`transport`、`cache`、`api` 分包：

- 端口：`LocationDataProvider`、`WeatherDataProvider`、`PlaceDataProvider`、`TransportDataProvider`；业务服务仅依赖端口和领域 DTO。
- 实现：`NominatimLocationProvider`、`OpenMeteoCityFallbackProvider`、`OpenMeteoWeatherProvider`、`OverpassPlaceProvider`、`DisabledTransportProvider`，以及 feature flag 后的 `TransitousTransportProvider` 探测实现。
- HTTP：使用项目统一配置的 `RestClient`/JDK HTTP client，设置连接/读取/总超时、最大响应体、有限重试（仅 GET 的 429/5xx/网络瞬态，指数退避并遵守 `Retry-After`），禁止跟随到非 `http/https` 或非允许域名；不把用户输入放进日志。
- Provider properties 在启动时校验：base URL、超时、User-Agent、contact URL/email、enabled、并发、每分钟配额。生产 profile 不允许使用空的可识别 User-Agent；公共端点可通过单独开关整体关闭。
- Nominatim 使用应用级单线程/单飞调度器，跨用户全局间隔至少 1 秒；Overpass 全局并发 1、短查询 timeout；Open-Meteo 按实例并发和 RPM/日预算保护；Transitous 默认并发 0（disabled）。当前限流为单实例，文档标明横向扩容须移到网关/Redis。

### 5.2 持久化缓存与迁移

新增 Flyway `V6__support_realtime_data.sql`：

- `location_references`：UUID、provider、provider_ref、规范名称/显示名、country_code、latitude/longitude（DECIMAL，非浮点持久化）、timezone、source_updated_at、fetched_at、expires_at；`provider + provider_ref` 唯一。
- `external_data_cache`：`cache_key`（provider+能力+规范化参数的 SHA-256，不保存原始用户查询）、provider、data_type、payload JSON、source_updated_at、fetched_at、expires_at、stale_until、etag/last_modified、last_failure_code、updated_at；不存 Token、密钥或第三方完整错误。
- `trips` 增加 nullable `destination_location_id` 外键；旧行程保持可读。
- `messages` 增加 nullable JSON `source_references` 与 `data_updated_at`；只保存该 turn 实际使用的最小来源元数据，历史消息不随缓存刷新而改变。

默认 TTL/降级窗口（均可配置且有上下界校验）：

| 数据 | fresh TTL | stale-if-error | 说明 |
| --- | --- | --- | --- |
| 地点检索/引用 | 7 天 | 30 天 | 满足 Nominatim 重复查询必须缓存；缓存命中不触发 1 req/s 调度。 |
| 天气 | 30 分钟 | 6 小时 | 仅供应商故障时返回 stale；过期预报日期不因 stale window 继续展示。 |
| 附近景点/营业字段 | 6 小时 | 7 天 | 返回 OSM 元素时间戳；营业字段仍提示核验。 |
| Transitous 探测（若启用） | 2 分钟 | 15 分钟 | 不落用户主路径，本轮不作为“实时交通已接入”宣传。 |

使用数据库缓存保证重启后可降级，进程内只做短时 single-flight 防止 cache stampede；同 key 一个刷新者，其余读取 fresh/stale 或等待有界时间。成功响应可保存供应商 ETag/Last-Modified 并条件请求；缓存 payload 在反序列化时重新校验 schema 和最大大小。定时删除超过 `stale_until` 的条目，不能无限保存搜索词派生数据。

### 5.3 行程与咨询集成

- 新建行程页面先显式搜索并选择地点；后端将 `destinationLocationId` 绑定到 Trip。对旧 Trip，详情页提供“确认地点”操作，不静默选第一个同名城市。
- `RealtimeContextService` 根据已绑定行程、问题意图和日期取得最小数据：天气类问题取天气，景点/营业类问题取 places；一般问题不调用所有供应商。服务端将结构化 JSON 作为不可信数据块放入 consultation prompt，使用固定标签与长度上限，第三方文本不能成为 system 指令。
- Prompt 明确：只陈述提供的数据；区分抓取时间和源更新时间；缺失时说不知道；不得虚构营业状态、票价、班次或 URL。实际使用的来源在 provider 返回值中收集，由服务端写入 assistant Message，而不是相信模型自报。
- Stub consultation 根据固定 fixtures 生成确定性、带来源的回答，CI/Codespaces 即使暂时无外网也可演示。开发环境可设置 `REALTIME_PROVIDER_MODE=live|stub|disabled`；默认 Codespaces `live-with-stale-fallback`，测试固定 `stub`，生产必须显式选 live provider 和合规端点。
- 外部实时上下文失败时，咨询仍生成一般建议，但回答开头或结尾必须标注“实时数据暂不可用，本回答未使用实时数据”；持久化 sources 为空/仅含成功来源，不声称失败来源已使用。

## 6. 前端实施设计

1. 新增 `api/realtime.ts`、对应类型与 Pinia store；请求世代号/AbortController 防止快速切换行程时旧响应覆盖，缓存仅用于当前 UI 会话，服务端数据为最终真相。
2. `TripCreateView` 的目的地改为“文本 + 搜索按钮 + 明确结果列表”；至少 2 字符、无按键自动请求，键盘可选择，结果显示城市/地区/国家和 OSM 署名。修改目的地文本后清除旧 `locationId`，提交前要求重新选择；若地点服务降级，可继续以纯文本创建行程，但明确实时卡片不可用。
3. 行程详情新增：
   - 可访问的 Leaflet 地图和目的地/景点 marker；瓦片失败时保留地点文字列表，不让地图成为唯一入口；常驻可读 OSM attribution，禁止隐藏控件或预加载。
   - `WeatherPanel`：按行程日期显示天气卡、更新时间、fresh/stale badge、缺失日期及安全提示；超出范围不显示伪预测。
   - `NearbyPlacesPanel`：类别、距离、原始营业时间、来源元素链接和更新时间；无 `openingHours` 显示“暂无数据”，website 用安全新窗口属性。
4. `MessageBubble` 增加来源区：回答完成后列出 provider、更新时间、freshness、来源链接；流中可显示“正在核对实时数据”，done 后按回源消息替换。旧消息或未用实时数据时继续展示现有“AI 建议”说明。
5. 所有实时组件有 loading/empty/stale/error/success 五态；stale 数据保留同时显示非阻塞 warning。页面不得把第三方原始错误、坐标请求 URL或缓存 key 暴露给用户。
6. 地图 JS/CSS 作为锁定版本的 npm 依赖本地打包，不加载第三方远程脚本；配置 `VITE_MAP_TILE_URL`/署名，默认值符合 OSM policy。

## 7. 安全、隐私、滥用与可靠性

- SSRF：base URL 仅从受控配置读取并限制 scheme/host；客户端不能传任意 URL、Overpass QL、字段名或半径。重定向后重新校验 allowlist。
- 注入：Nominatim 参数 URI 编码；Overpass 查询仅用枚举和服务器常量构造；第三方名称、营业时间和 website 作为数据转义显示，禁止 `v-html`。第三方文本进入 AI 前做长度/控制字符限制并标记为不可信。
- 隐私：只发送行程目的地级坐标和必要日期；不发送用户 ID、邮箱、消息全文、IP、预算或偏好。文档披露 Transitous URL 可能包含起终点且其官方称日志最多保留 2 天，因此本轮默认禁用其用户请求路径。
- 限流：应用用户级 + provider 全局两层；认证用户地点搜索建议 10/min、天气/景点各 20/min，缓存命中也保留较宽松的防刷限制。供应商 429 映射为稳定错误或 stale，透传合理 `Retry-After`，不将所有 429 重试成风暴。
- 熔断：连续供应商故障短时 open，优先 stale；half-open 单探针。监控 provider、能力、结果（fresh/stale/error）、延迟和缓存命中率，不含查询文本/经纬度；健康端点只报告聚合状态，不泄露配置/密钥。
- 许可证：界面与 README 保留 OSM/Open-Meteo/Transitous 署名；CI 加测试防止 attribution 组件被条件隐藏。上线前由负责人复核实际用途是否仍满足非商业/开源条件。

## 8. 测试与验证矩阵

### 8.1 后端自动化

- Provider contract tests（MockWebServer/WireMock 或本地 HTTP stub）：正常、空字段、畸形 JSON、超大响应、超时、DNS/连接失败、429 `Retry-After`、5xx、重定向到非允许 host、单位/时区和 source timestamp 映射。
- Nominatim：显式搜索、编码、User-Agent、全局 1 req/s、无 autocomplete、缓存重复请求；Open-Meteo 城市 fallback 不返回 POI；歧义不自动绑定。
- Weather：行程日期裁剪、预报窗口外、WMO 映射、单位、stale 不覆盖已过日期、供应商模型时间与 retrievedAt 区分。
- Overpass：固定模板/半径/limit、复杂和缺失 opening_hours、危险 website scheme 丢弃、元素时间戳、timeout/504 stale 降级、不能注入 QL。
- 缓存：fresh hit、过期刷新、single-flight、stale-if-error、stale_until 后拒绝、条件请求、重启后读取、坏 payload 淘汰、定时清理；使用可注入 `Clock`，禁止依赖 sleep。
- 鉴权/归属：所有新接口 401、其他用户 Trip 404、地点 ID 不可伪造、请求限流；日志脱敏测试覆盖 query/coordinate/API key。
- 咨询：只按意图取最小上下文、第三方 prompt injection 仍作为数据、来源由服务端而非模型生成、普通/SSE/done/重放来源一致、实时失败仍完成并标注降级、历史消息来源不可变。
- Flyway 在 H2 测试兼容路径及真实 MySQL 8.4 E2E 均验证；OpenAPI 校验继续在 CI 通过。

### 8.2 前端与 E2E

- Vitest：地点显式提交/结果键盘操作/文本变化清除 ID；天气日期/单位/freshness；地点营业缺失；来源链接安全；地图 fallback 与 attribution；请求竞态、abort 和五态；消息流 done 后来源回源。
- Playwright 真实业务 API，第三方边界由后端确定性 stub 服务提供，不访问互联网，以消除 CI 波动：注册 → 搜索并选地点 → 创建/READY → 地图/天气/景点/更新时间 → 绑定会话提问 → SSE 回答来源；再覆盖 provider 故障返回 stale、无缓存降级、预报范围外、320px 和键盘路径。
- 独立的 opt-in live smoke（不进入每次 CI）：在 Codespaces 手动以低频执行 Nominatim、Open-Meteo、Overpass 各一次，检查 User-Agent/署名/配额；失败不阻塞确定性测试，但必须记录实际时间和结果。Transitous 只有取得许可或确认低成本用途后才执行。

### 8.3 门禁命令与观测验收

- 后端 `mvn verify`；前端 `npm run type-check && npm run test:coverage && npm run build`；Playwright 真栈 E2E；OpenAPI lint。
- 测试期间检查日志无消息正文、坐标 URL、Token/API key；第三方 stub 统计调用次数，证明缓存命中、意图最小调用和 single-flight。
- 用可控 Clock 验证页面展示的 retrieved/source updated 时间、fresh→stale→unavailable 转换，而非只断言存在字符串。

## 9. 文件级实施顺序与审核检查点

1. **契约与模型**：先更新 OpenAPI/API contract/data model，定义 sources、地点及三个新 GET 契约；独立检查兼容性、错误码和时间语义。
2. **迁移与缓存核心**：V6、repository、Clock、TTL/stale/single-flight；先完成缓存和 MySQL 测试再接公网 provider。
3. **地点能力**：Nominatim + Open-Meteo city fallback、显式搜索 UI、Trip 绑定；检查 1 req/s、User-Agent、无 autocomplete 和 OSM attribution。
4. **天气与景点**：Open-Meteo/Overpass 适配、Trip API、详情卡和地图；检查日期范围、原始营业时间、地图失败 fallback、缓存降级。
5. **咨询来源**：RealtimeContext、prompt 数据隔离、Message 持久化/SSE、来源 UI；检查模型不能伪造 sources 且失败时不声称实时。
6. **交通边界与文档**：提交 disabled Transitous adapter、生产替换说明和许可检查，不宣传尚未接入的路线能力。
7. **测试、Codespaces 与收尾**：确定性第三方 stub E2E、低频 live smoke、README 配置/署名/限制、AI 使用日志和独立审核。所有证据通过后才勾选 TODO 第 6 节。

每个检查点由实施终端完成后交独立审核终端。审核发现阻塞项时回到实施终端修复，再由同一审核终端复审；不得以公共 API 一次成功替代缓存、限流、降级和确定性测试。

## 10. 验收标准

1. 地图、天气、景点和交通四类供应商均有经官方资料核实的选择、许可证/配额边界、可替换配置和明确的 MVP 启用状态；Transitous 默认关闭且不虚假宣称已提供实时路线。
2. 用户只能通过显式提交搜索地点，无逐键 autocomplete；Nominatim 调用满足识别 User-Agent、全局 ≤1 req/s、缓存和署名，创建 Trip 可绑定不透明内部地点引用，旧 Trip 歧义时不猜选。
3. Trip 详情能显示带来源的目的地地图、行程日期内天气及附近景点营业字段；地图或任一 provider 失败不影响核心行程，天气范围外和营业信息缺失都有诚实状态。
4. 数据响应和 UI 同时区分 `sourceUpdatedAt`、`retrievedAt` 与 fresh/stale/unavailable；缓存 TTL、stale-if-error、single-flight 和重启后降级有自动化证据。
5. 普通及 SSE AI 咨询只标注该 turn 实际使用的实时来源；来源由服务端生成并持久化，历史消息不漂移，实时失败时回答明确未使用实时数据。
6. 新接口鉴权、Trip 归属、SSRF/查询注入/第三方内容注入、危险 URL、日志隐私、provider/user 限流、超时/重试/熔断均有测试；外部服务不可成为开放代理。
7. Codespaces 无密钥可运行；CI 不依赖公网并能通过固定 provider stub 验证完整路径；可选 live smoke 低频且遵守政策。
8. OpenAPI、API 文档、数据模型、README、前后端类型、配置表和实际实现一致；地图/数据署名在相关 UI 可见。
9. 后端、前端、OpenAPI、真实栈 E2E 全绿，独立审核无阻塞项后，才更新 `TODO.md` 第 6 节和 `docs/ai-usage-log.md` 并提交。

## 11. 主要风险与缓解

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| 公共社区服务限流、政策变化或撤回 | 演示不稳定、生产违规 | 后端适配器、配置化端点、持久 stale 缓存、熔断；CI 固定 stub；生产切付费/自托管并复核条款。 |
| 同名地点误解析 | 错误天气/景点进入行程与 AI | 用户显式选择内部 LocationReference；歧义不自动取第一项；旧数据要求确认。 |
| “更新时间”被误解为源数据时间 | 用户过度信任 | 分开 sourceUpdatedAt/retrievedAt，缺失不伪造，所有卡片显示 freshness 和核验提示。 |
| OSM opening_hours 复杂或过期 | 错报营业状态 | 保存/展示原始表达式和元素时间；不自行声称开门，提示访问官方 website 核验。 |
| 第三方数据含提示注入或恶意 URL | AI 越权、XSS/钓鱼 | 不可信结构化数据边界、字段/长度白名单、HTML 转义、URL scheme allowlist、来源由服务端生成。 |
| 缓存击穿与重试风暴 | 供应商封禁、线程耗尽 | per-key single-flight、全局并发/RPM、Retry-After、有界退避、熔断、stale-first。 |
| Transitous 计算负载和许可不匹配 | 社区资源滥用 | MVP 默认 disabled；先联系维护者，再启用路线；保留商业/自托管 adapter 路径。 |
