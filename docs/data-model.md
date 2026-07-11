# 核心数据模型

数据库结构以 Flyway 迁移文件为事实来源。当前模型围绕“行程版本不可覆盖”和“咨询上下文可追溯”设计。

## 主要关系

```text
users 1 ── N trips 1 ── N trip_versions 1 ── N itinerary_days 1 ── N activities
  │              │
  │              └── N conversations 1 ── N messages
  └────────────────── N conversations
```

## 设计规则

- 主键使用 UUID 字符串，由应用生成，接口不得依赖其具体格式。
- `trips` 保存行程身份及当前状态；生成或调整后的实际内容写入新的 `trip_versions`。
- `trip_versions` 一旦进入 `READY` 不允许原地修改；回退通过切换 `trips.current_version_id` 完成。
- 金额使用 `DECIMAL(14,2)`，币种使用三位 ISO 4217 代码。
- `preferences_json` 和 `warnings_json` 保存结构简单且无需独立检索的数组；可查询实体使用关系表。
- 用户、行程和会话支持软删除；行程内部版本数据跟随行程生命周期管理。
- 所有时间戳按 UTC 写入，由 API 根据业务时区转换展示。
- 消息内容保留生成状态和 Token 用量，便于流式失败恢复与成本统计。

