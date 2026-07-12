# 部署指南

## Compose 部署

复制 `.env.example` 为 `.env`，替换数据库密码和至少 32 字节的随机 `JWT_SECRET`，然后运行 `docker compose up --build -d`。生产式覆盖使用 `docker compose -f docker-compose.yml -f docker-compose.prod.yml up --build -d`，仅应由 TLS 反向代理公开前端 5173；MySQL 与后端保持内网可见。

升级前备份数据库；先构建镜像，再启动 MySQL、执行后端 Flyway，健康后切换前端。回滚应用镜像不能回滚已执行的数据库迁移，涉及破坏性迁移必须单独设计兼容窗口。`/api/v1/health` 与前端 `/healthz` 用于验收。

公共 Nominatim、Overpass、Open-Meteo 只适合低流量演示。生产须配置获批/自托管供应商、有效联系信息和监控。密钥仅通过环境或 secret manager 注入，不写入镜像、日志或仓库。

## Xiaomi MiMo Token Plan

通过 Secret 注入 `XIAOMI_MIMO_API_KEY`，并设置
`XIAOMI_MIMO_BASE_URL=https://token-plan-cn.xiaomimimo.com/anthropic`、
`XIAOMI_MIMO_MODEL=mimo-v2.5`，以及按需将行程和咨询 provider 设置为
`xiaomi-mimo-anthropic`。Base URL 不包含 `/v1/messages`；Compose 只在运行时透传，
镜像不保存 key。上线前须在非生产环境以最小额度验证三条链路，并在官方平台确认用途、
配额和费用。缺 key 或上游错误会显式失败，不会静默降级。回滚时由运维显式把两个
provider 改为 `stub` 并重启后端。
