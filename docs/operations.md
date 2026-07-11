# 运维手册

- 日常检查：`docker compose ps`、前端 `/healthz`、API `/api/v1/health`、MySQL 磁盘和容器重启次数。
- 追踪故障：使用响应 `X-Request-Id` 查后端日志；不得记录 Token、消息正文、精确坐标或第三方密钥。
- 重点告警：5xx/429 比例、AI/实时供应商超时、线程池/连接池耗尽、生成任务积压、磁盘不足、备份失败。
- 优雅停机：先停止入口流量，再 `docker compose stop frontend backend`；数据库最后停止。恢复顺序相反并等待健康检查。
- 容量边界：当前应用/供应商限流主要是单实例。横向扩容前迁移至网关或 Redis，并配置共享任务协调。
