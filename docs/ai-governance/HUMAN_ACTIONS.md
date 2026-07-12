# 项目负责人后续人工操作清单

## 1. 把 Stub 切换为真实大模型 API

当前项目不能直接接入本次 Codex 会话里的“我”：会话身份、模型运行资源和凭据不可导出，也不能被项目长期调用。项目负责人目前没有 OpenAI API key，因此默认继续使用 Stub。后续可以从 DeepSeek 或 Xiaomi MiMo 中选择一个，但“兼容 Chat Completions”不等于与本项目全部功能兼容：咨询接口预期可用，行程规划还受结构化输出协议限制。

1. 在所选供应商控制台注册、实名/充值（如要求）、设置预算和用量告警，并创建仅供此后端使用的 API key。
2. 不要把 key 发到聊天、写入 `.env.example`、浏览器变量、Vue `VITE_*`、Dockerfile 或 GitHub 文件。所有请求只能从本项目后端发出；key 放在未提交的 `.env`、Codespaces Secret 或生产 secret manager。
3. 在本机未提交的 `.env`、Codespaces Secret 或部署平台 Secret 中设置：

   ```dotenv
   AI_API_KEY=<供应商密钥>
   AI_BASE_URL=<供应商基础 URL，不含 /chat/completions>
   AI_MODEL=<供应商当前可用模型 ID>
   TRIP_AI_PROVIDER=openai-compatible
   CONSULTATION_AI_PROVIDER=openai-compatible
   ```

4. 经 2026-07-12 核验，DeepSeek 当前官方基础 URL 是 `https://api.deepseek.com`，模型示例为 `deepseek-v4-flash` 或 `deepseek-v4-pro`；官方提示旧的 `deepseek-chat`/`deepseek-reasoner` 将弃用，所以配置当天再次查看[DeepSeek 官方 API 文档](https://api-docs.deepseek.com/)。示例：`AI_BASE_URL=https://api.deepseek.com`、`AI_MODEL=deepseek-v4-flash`。DeepSeek 当前 `response_format` 只接受 `text`/`json_object`，而本项目行程适配器固定发送 `json_schema`；因此只填 key 可用于咨询的非生产验证，**当前不能用于行程生成**。行程功能必须另开实现轮次，改用 `json_object` 并在本地继续做 Schema 校验，或实现其他受官方支持的严格结构化方案。
5. 项目已按负责人提供的 Token Plan Anthropic 配置新增独立适配器：`XIAOMI_MIMO_BASE_URL=https://token-plan-cn.xiaomimimo.com/anthropic`、`XIAOMI_MIMO_MODEL=mimo-v2.5`，并通过 `XIAOMI_MIMO_API_KEY` 注入专用 key。不得把 Token Plan key 放进 `AI_API_KEY`，也不得把真实 key 写入 `.env.example`、仓库或日志。公开平台页确认 Token Plan 包含 `mimo-v2.5`，但公开页面没有完整呈现自定义旅游后端的最新用途条款；负责人必须在购买或启用前通过 [Xiaomi MiMo 官方平台](https://platform.xiaomimimo.com)确认套餐允许本项目用途、配额和费用。
6. 先只启用一个供应商，在非生产环境执行注册 → 普通咨询 → SSE 咨询，检查非流式 JSON、SSE 分片、错误码、Token 用量、超时和账单。只有供应商的结构化输出协议与当前 `json_schema` 请求实际兼容，才可再验证行程生成；DeepSeek 已知不满足此条件。不能直接删掉输出校验，未完成相应适配与真实账号 smoke 前不得切换生产。

MiMo 验收时设置 `TRIP_AI_PROVIDER=xiaomi-mimo-anthropic` 和 `CONSULTATION_AI_PROVIDER=xiaomi-mimo-anthropic`。先以最小额度分别执行一次行程、普通咨询和 SSE 咨询；只记录脱敏状态、模型和 usage，不保存完整用户问题、模型回答或 key。自动化测试只证明 Anthropic Messages 协议适配，不等同于真实账号连通或用途授权。
7. 设置费用上限和告警；轮换 key 时先添加新 key、更新服务并验证，再撤销旧 key。泄露时立即撤销并检查 Usage。

项目仍保留 Stub 作为 CI、离线演示和故障切换路径。没有 `AI_API_KEY` 时不得把 provider 切到真实模式。

## 2. Codespaces 远程人工验收

从 GitHub 创建全新 Codespace，等待 post-create 完成，运行 `scripts/dev.sh`；确认 5173 端口公开、8080 私有，从转发 URL 完成注册、地点选择、行程、实时演示数据和 SSE 咨询。关闭/重启 Codespace 后再次验证。把日期、提交 SHA 和结果更新到 `docs/reports/mvp-verification.md`。

## 3. 正式发布授权（已完成）

2026-07-12，负责人明确授权的 `v0.1.0` annotated tag、GitHub Release 和候选附件已经完成并公开验证；本项不再需要人工操作。未来版本仍需单独授权，不能复用本次授权移动或覆盖 `v0.1.0`。

## 4. 生产部署前

配置真实数据库/JWT secret、TLS、备份位置、域名、供应商合规端点、监控告警与 secret manager；执行全新库恢复演练和回滚演练。公共 OSM/Open-Meteo/Overpass 不作为生产 SLA。
