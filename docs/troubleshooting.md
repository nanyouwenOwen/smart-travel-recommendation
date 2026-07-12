# 故障排查

| 症状 | 检查 | 处理 |
| --- | --- | --- |
| 容器 unhealthy | `docker compose ps`、末尾日志 | 校验 `.env`、数据库健康与端口冲突 |
| Flyway 启动失败 | `flyway_schema_history`、SQL 错误 | 不手改校验和；恢复备份后修复迁移 |
| 401 | JWT secret、Token 时钟/过期 | 重新登录；确认各实例 secret 一致 |
| AI 429/超时 | provider 模式、配额、request ID | 等待窗口、切 Stub 或提高获批配额 |
| MiMo 401/403 | `xiaomi-mimo-anthropic`、Token Plan 专用 key、套餐用途 | 核对 `XIAOMI_MIMO_API_KEY` 与官方控制台；不要改用 `AI_API_KEY` 或记录响应正文 |
| MiMo 协议错误 | Base URL、模型、Anthropic version、供应商 request ID | Base URL 使用 `/anthropic` 根且不含 `/v1/messages`；核对平台最新配置后再重试 |
| MiMo 启动失败 | provider、专用 Secret | 注入 `XIAOMI_MIMO_API_KEY`；不要写入 Compose 或回退读取 `AI_API_KEY` |
| MiMo 429 | Token Plan 额度、并发、request ID | 等待窗口或降低并发；不要请求级自动切换 Stub |
| 实时数据 stale | 供应商健康与缓存时间 | 保留警告，核验官方信息，勿清空有效缓存 |
| SSE 一次性出现/不流式 | Nginx buffering/read timeout | 使用仓库 nginx 配置并关闭代理缓冲 |
| Codespaces 无法访问 | Ports 面板可见性与 5173 | 启动 `scripts/dev.sh`，将前端端口按需设为公开 |
| 恢复失败 | gzip/sha256、目标库权限 | 停止写流量，在全新实例重试并保留原备份 |

应急停用 MiMo 时，显式设置两个 AI provider 为 `stub` 后重启并检查健康端点；这是部署配置回滚，不是运行时静默降级。
