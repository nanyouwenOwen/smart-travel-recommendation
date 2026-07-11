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
5. 经 2026-07-12 核验，Xiaomi MiMo 按量付费 API 的兼容接口是 `https://api.xiaomimimo.com/v1/chat/completions`，所以本项目只推荐使用按量付费 API，填 `AI_BASE_URL=https://api.xiaomimimo.com/v1` 及其对应 API key。官方当前示例为 `mimo-v2.5-pro`，并已提示 V2 系列弃用，请以配置当天的[MiMo Chat 官方文档](https://mimo.mi.com/docs/en-US/api/chat/openai-api)为准。MiMo Token Plan 是独立订阅产品，使用其专用 key/官方指定的独立调用配置，不得与按量付费 URL/key 混用。它当前只允许 AI 编程工具场景，明确禁止自定义应用后端等非 Coding API 调用，**不适用于本旅游助手**；不得购买或配置它用于本项目，并在接入当天复核 [MiMo Token Plan 条款](https://mimo.mi.com/docs/en-US/price/token-plan)。
6. 先只启用一个供应商，在非生产环境执行注册 → 普通咨询 → SSE 咨询，检查非流式 JSON、SSE 分片、错误码、Token 用量、超时和账单。只有供应商的结构化输出协议与当前 `json_schema` 请求实际兼容，才可再验证行程生成；DeepSeek 已知不满足此条件。不能直接删掉输出校验，未完成相应适配与真实账号 smoke 前不得切换生产。
7. 设置费用上限和告警；轮换 key 时先添加新 key、更新服务并验证，再撤销旧 key。泄露时立即撤销并检查 Usage。

项目仍保留 Stub 作为 CI、离线演示和故障切换路径。没有 `AI_API_KEY` 时不得把 provider 切到真实模式。

## 2. Codespaces 远程人工验收

从 GitHub 创建全新 Codespace，等待 post-create 完成，运行 `scripts/dev.sh`；确认 5173 端口公开、8080 私有，从转发 URL 完成注册、地点选择、行程、实时演示数据和 SSE 咨询。关闭/重启 Codespace 后再次验证。把日期、提交 SHA 和结果更新到 `docs/reports/mvp-verification.md`。

## 3. 正式发布授权

实现与候选已完成，但自动化代理没有被授权创建 tag/Release。负责人审阅 `docs/release-checklist.md` 后，可明确回复授权以下动作：创建并推送 `v0.1.0` annotated tag、创建 GitHub Release、附加候选产物。未授权前 `TODO.md` 的“发布 MVP”保持未勾选。

## 4. 生产部署前

配置真实数据库/JWT secret、TLS、备份位置、域名、供应商合规端点、监控告警与 secret manager；执行全新库恢复演练和回滚演练。公共 OSM/Open-Meteo/Overpass 不作为生产 SLA。
