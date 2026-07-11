# 部署指南

## Compose 部署

复制 `.env.example` 为 `.env`，替换数据库密码和至少 32 字节的随机 `JWT_SECRET`，然后运行 `docker compose up --build -d`。生产式覆盖使用 `docker compose -f docker-compose.yml -f docker-compose.prod.yml up --build -d`，仅应由 TLS 反向代理公开前端 5173；MySQL 与后端保持内网可见。

升级前备份数据库；先构建镜像，再启动 MySQL、执行后端 Flyway，健康后切换前端。回滚应用镜像不能回滚已执行的数据库迁移，涉及破坏性迁移必须单独设计兼容窗口。`/api/v1/health` 与前端 `/healthz` 用于验收。

公共 Nominatim、Overpass、Open-Meteo 只适合低流量演示。生产须配置获批/自托管供应商、有效联系信息和监控。密钥仅通过环境或 secret manager 注入，不写入镜像、日志或仓库。
