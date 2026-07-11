# 核心数据模型

## 实时数据实体（V6）

- `location_references`：内部 UUID、provider/provider_ref 唯一键、规范名称、WGS84 经纬度（DECIMAL）、IANA 时区、抓取/源更新时间及过期时间。Trip 通过 nullable 外键绑定。
- `external_data_cache`：只保存 SHA-256 cache key、provider、能力类型、受限 JSON payload、fresh/stale 时间边界和稳定失败码；不保存 Token、密钥或第三方原始错误。
- `trips.destination_location_id`：兼容性 nullable 外键；历史行程不会静默绑定同名地点。
- `messages.source_references/data_updated_at`：为咨询来源持久化预留；来源只记录该 turn 实际使用的数据，不随缓存刷新漂移。

默认缓存策略：地点 7 天；天气 fresh 30 分钟、stale-if-error 6 小时；景点 fresh 6 小时、stale-if-error 7 天。超过 stale window 不再展示。

数据库结构以 Flyway 迁移文件为事实来源。当前模型围绕“行程版本不可覆盖”和“咨询上下文可追溯”设计。

## 主要关系

```text
users 1 ── N trips 1 ── N trip_versions 1 ── N itinerary_days 1 ── N activities
  │              │
  │              └── N conversations 1 ── N messages
  └────────────────── N conversations
```

每个行程还关联 `trip_generation_jobs`（异步生成状态）、`idempotency_records`（24 小时创建幂等）和 `trip_version_restores`（版本指针恢复审计）。生成任务与已发布版本分离，因此重新规划失败不会破坏当前 READY 版本。活动预算类别为稳定枚举，预算汇总由服务端计算。

咨询问答通过 `conversation_message_requests` 保证 turn 幂等；每组 USER/ASSISTANT 消息共享 `turn_id` 并记录实际使用的 `trip_version_id`。`conversation_streams` 保存传输终态，`conversation_stream_events` 保存短期可重放的 ack/delta/done/error 事件。

## 设计规则

- 主键使用 UUID 字符串，由应用生成，接口不得依赖其具体格式。
- `trips` 保存行程身份及当前状态；生成或调整后的实际内容写入新的 `trip_versions`。
- `trip_versions` 保存生成时的预算基线，一旦发布不允许原地修改；回退通过切换 `trips.current_version_id` 完成，后续 PATCH 不会改变历史版本的预算结论。
- 金额使用 `DECIMAL(14,2)`，币种使用三位 ISO 4217 代码。
- `preferences_json` 和 `warnings_json` 保存结构简单且无需独立检索的数组；可查询实体使用关系表。
- 用户、行程和会话支持软删除；行程内部版本数据跟随行程生命周期管理。
- 所有时间戳按 UTC 写入，由 API 根据业务时区转换展示。
- 消息内容保留生成状态和 Token 用量，便于流式失败恢复与成本统计。
