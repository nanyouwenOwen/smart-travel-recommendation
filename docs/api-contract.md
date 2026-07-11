# 前后端接口契约

机器可读字段定义以 [`openapi.yaml`](openapi.yaml) 为准。本文说明双方必须共同遵守的行为约束。

## 基础约定

- API 前缀：`/api/v1`
- 数据格式：除 SSE 外均为 `application/json; charset=utf-8`
- 字段命名：JSON 使用 `camelCase`，数据库使用 `snake_case`
- 标识符：不透明字符串，当前采用 UUID；前端不得解析或依赖其格式
- 日期与时间：日期为 `YYYY-MM-DD`；瞬时时间为带时区的 ISO 8601；业务时区为 IANA 名称
- 金额：`"1234.50"` 形式的十进制字符串，并始终携带 ISO 4217 币种
- 可选字段：未提供时省略；有业务意义时才使用 `null`，不得混用空字符串
- 接口版本：不兼容修改发布新主版本；兼容性新增字段不升级主版本

## 认证与请求头

受保护接口使用：

```http
Authorization: Bearer <access-token>
X-Request-Id: <客户端生成的 UUID，可选>
```

服务端每次响应都返回 `X-Request-Id`。创建行程请求还应包含 `Idempotency-Key`；同一用户在 24 小时内使用相同 Key 和相同请求体，应得到同一业务结果。

Access Token 为 15 分钟有效的 Bearer JWT；Refresh Token 默认有效 30 天，只能使用一次并在刷新时轮换。服务端仅保存 Refresh Token 的 SHA-256 摘要。退出撤销 Refresh Token，但已签发的 Access Token 在短有效期内仍然有效。资源归属必须从 SecurityContext 获取用户 ID，不得信任请求参数中的 `userId`；越权读取单用户资源统一返回 404。

密码长度为 8～72 个字符；鉴于 BCrypt 的输入边界，UTF-8 编码后还必须不超过 72 字节。

## 成功响应

单对象统一包装：

```json
{
  "data": {},
  "meta": {
    "requestId": "c4af..."
  }
}
```

列表采用游标分页：

```json
{
  "data": [],
  "meta": {
    "requestId": "c4af...",
    "nextCursor": "opaque-value",
    "hasMore": false
  }
}
```

默认 `limit=20`，最大 `100`。游标是不透明值，客户端不得自行构造。

## 错误响应

错误状态必须使用合适的 HTTP 状态码，并返回稳定的业务错误码：

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "请求参数不合法",
    "details": [
      { "field": "budget.amount", "reason": "必须大于 0" }
    ]
  },
  "meta": {
    "requestId": "c4af..."
  }
}
```

约定状态码：

| 状态码 | 使用场景 |
| --- | --- |
| 400 | JSON 格式、业务参数或状态不合法 |
| 401 | 未登录、Token 无效或过期 |
| 403 | 已登录但无权操作资源 |
| 404 | 资源不存在；越权读取也返回此状态以避免泄露 |
| 409 | 幂等冲突、版本冲突或资源状态冲突 |
| 422 | 请求合法，但 AI 结果无法满足业务 Schema |
| 429 | 超出用户或供应商限流 |
| 500 | 未预期的服务端错误 |
| 502/503/504 | 外部 AI 或实时数据服务异常、不可用或超时 |

前端只依赖 `error.code` 做流程判断，`message` 用于展示，不通过解析文案判断错误类型。

## 行程生成状态

`DRAFT -> GENERATING -> READY` 为正常路径；生成失败进入 `FAILED`。只有 `READY` 行程可作为正式方案展示。重新生成或调整创建新的行程版本，不覆盖历史版本。

创建行程通过 `Idempotency-Key` 保证 24 小时幂等，成功接收后返回 202。生成期间 `currentVersion` 可缺省且 `itinerary` 固定为空数组；初次失败进入 `FAILED`。已有 READY 版本的重新规划或调整由独立生成任务表示，失败不得覆盖当前版本。版本内容不可修改，回退只原子切换当前版本指针并记录审计，不调用 AI。

所有金额由服务端按活动费用重新汇总，分类使用稳定枚举。AI 生成的地点、价格、交通和时间均为建议，不代表实时数据；接入外部实时数据前必须向用户标识这一限制。

## SSE 流式咨询

请求使用 `POST /conversations/{conversationId}/messages:stream`，响应类型为 `text/event-stream`。事件格式：

```text
event: delta
data: {"messageId":"...","content":"增量文本"}

event: done
data: {"messageId":"...","usage":{"inputTokens":120,"outputTokens":80}}
```

可能的事件：

- `ack`：请求已接受并包含用户消息 ID
- `delta`：模型文本增量，可出现多次
- `done`：生成成功结束
- `error`：流内错误；发出后连接结束

服务端每 15 秒发送注释心跳 `: ping`。客户端必须按事件累加内容，收到 `done` 后以服务端消息详情为最终状态。SSE 已开始后发生的错误用 `error` 事件表达，不再依赖 HTTP 状态码。

## 契约变更流程

1. 先修改 `openapi.yaml` 和本文档中的行为规则。
2. 评估是否为破坏性变更；禁止无版本升级地删除字段、收紧枚举或改变语义。
3. 更新后端实现及测试，再更新前端类型与交互。
4. 在 `TODO.md` 勾选完成项，并确保 CI 中 OpenAPI 校验通过。
