# Round 02 智能行程规划审核记录

## 首轮结论

首轮审核未通过。主要阻塞问题包括：异步队列拒绝和进程中断可能造成任务永久卡住；历史版本预算依赖当前行程预算；并发幂等语义不完整；AI 总截止、异常分类和结构化输出校验不足；跨用户测试及游标防篡改缺失。

## 修复摘要

- 使用事务级容量预留，在持久化 Trip/Job 前可靠拒绝满队列并返回 `503 GENERATION_QUEUE_FULL`；事务回滚或任务结束后释放容量。
- 增加 stale QUEUED/RUNNING 任务扫描和失败恢复，并阻止被恢复任务的迟到结果发布。
- V4 为不可变版本保存预算基线，历史预算结论不再随 PATCH 改变。
- 按用户悲观锁串行创建；幂等命中发生在准入限流之前，同 Key 同请求并发或重试复用同一行程。
- 补齐跨用户 GET/PATCH/DELETE/adjust/version/restore 的统一 404 行为。
- 分页游标增加 HMAC-SHA256 签名及常量时间校验。
- AI Provider 调用增加严格总截止时间、超时/限流/服务错误分类、配置化 prompt 版本，并强化 JSON Schema 和应用语义校验。
- 对创建、调整和重新规划增加用户 RPM 准入限制；AI 生成结果继续经过服务端预算重算。

## 最终复审结论

最终复审通过，无 Round 02 阻塞项。

验证证据：

- `mvn -q -f backend/pom.xml verify`：30 项测试通过，0 failures/errors/skips。
- Redocly minimal lint：OpenAPI 3.1 契约有效；23 项文档风格警告不影响语义。
- MySQL 8.4：Flyway V1→V4 成功，Hibernate `ddl-auto=validate` 及完整 Web 应用启动通过。
- 完整流程已覆盖：创建 202 → READY → 列表/详情 → 调整/PATCH → 历史版本 → 恢复 → 删除。

后续非阻塞优化包括：OpenAI 异常响应形状合约测试、结构化 warning、历史详情查询数优化、执行器真实满载接口测试及 OpenAPI 风格警告清理。
