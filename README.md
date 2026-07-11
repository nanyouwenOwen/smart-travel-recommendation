# 智能旅游助手

基于 AI 的前后端分离旅游规划平台。系统根据用户的目的地、预算、出行天数及偏好生成可执行的逐日行程，并通过多轮对话提供实时旅游咨询和行程调整服务。

## 项目目标

- 将模糊的旅游需求转换为结构化、可保存、可调整的行程。
- 在预算、时间和用户偏好约束内给出合理建议，并清楚标识估算信息。
- 提供有上下文的实时 AI 咨询，支持流式响应。
- 通过统一接口契约保证前后端能够独立开发和测试。

## 技术栈与版本

| 层级 | 技术 | 版本基线 |
| --- | --- | --- |
| 前端 | Vue、TypeScript、Vite | Vue 3.5.x、TypeScript 5.9.x、Vite 7.3.x |
| 状态与路由 | Pinia、Vue Router | Pinia 3.x、Vue Router 5.x |
| 运行环境 | Node.js | 24 LTS |
| 后端 | Java、Spring Boot、Maven | Java 21 LTS、Spring Boot 4.1.x、Maven 3.6.3+ |
| 数据库 | MySQL | 8.4 LTS |
| 数据迁移 | Flyway | 由 Spring Boot 依赖管理 |
| 接口 | REST + JSON、SSE | OpenAPI 3.1 |
| AI | OpenAI 兼容接口 | 通过环境变量切换供应商和模型 |

除 Node.js 外，业务依赖锁定在各自清单文件中；补丁版本升级需通过测试后执行。

## 核心业务规则

1. 行程规划必须包含目的地、开始日期、天数、总预算、币种、出行人数；天数为 1～30 天，总预算必须大于 0。
2. 预算是整个出行团队的总预算，默认包含住宿、交通、餐饮、门票和其他费用；系统必须返回分类预算与总估算。
3. AI 生成内容属于建议。价格、营业时间、天气、签证和交通等易变化信息必须附带“估算”或数据更新时间，不能表述为保证。
4. 每日活动时间不得重叠；活动应包含时间、地点、预计费用和交通建议。生成失败或信息不足时，不保存不完整的正式行程。
5. 行程只能由创建者读取、修改和删除。所有受保护接口均使用 Bearer Token。
6. 用户可通过自然语言调整已有行程；调整应创建新版本，保留原版本以便回退。
7. AI 咨询按会话保存上下文；流式内容使用 SSE。客户端断开连接后服务端应停止无必要的生成任务。
8. 金额在接口中使用十进制字符串，禁止使用浮点数；时间采用 ISO 8601，日期使用 `YYYY-MM-DD`，时区使用 IANA 标识（如 `Asia/Shanghai`）。
9. 所有写接口应支持请求追踪；创建规划使用 `Idempotency-Key` 防止重复提交。
10. AI 提示词不得包含密钥、密码等敏感数据，日志不得记录 Token 或完整隐私信息。

## 目录结构

```text
.
├── backend/             # Spring Boot API 服务
├── frontend/            # Vue 单页应用
├── docs/
│   ├── api-contract.md  # 接口通用约束与协作规则
│   ├── data-model.md    # 核心数据模型说明
│   └── openapi.yaml     # 可被工具读取的接口契约
├── docker-compose.yml   # 本地 MySQL
├── README.md
└── TODO.md
```

## 本地启动

### GitHub Codespaces（推荐用于在线演示）

在 GitHub 仓库点击 `Code → Codespaces → Create codespace on main`。容器会自动安装 Java 21、Maven、Node.js 24 和 Docker，并安装项目依赖。

初始化完成后，在 Codespaces 终端运行：

```bash
bash scripts/dev.sh
```

端口 `5173`（前端）和 `8080`（API）会自动转发。前端端口配置为公开访问，适合临时演示；Codespace 停止后演示地址也会停止。AI 密钥应添加到 Codespaces Secrets，禁止写入仓库。

### 1. 启动数据库

```bash
docker compose up -d mysql
```

### 2. 启动后端

需要 Java 21 和 Maven 3.6.3+：

```bash
cd backend
mvn spring-boot:run
```

后端默认地址为 `http://localhost:8080`，健康检查为 `GET /api/v1/health`。

### 3. 启动前端

需要 Node.js 24 LTS：

```bash
cd frontend
npm install
cp .env.example .env.local
npm run dev
```

前端默认地址为 `http://localhost:5173`。开发服务器将 `/api` 代理到后端。

## 配置

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `DB_URL` | `jdbc:mysql://localhost:3306/travel_assistant` | JDBC 地址 |
| `DB_USERNAME` / `DB_PASSWORD` | `travel` / `travel` | 本地数据库凭据 |
| `AI_BASE_URL` | `https://api.openai.com/v1` | OpenAI 兼容服务地址 |
| `AI_API_KEY` | 无 | AI 服务密钥，不得提交到仓库 |
| `AI_MODEL` | `gpt-5-mini` | 默认模型，可按供应商修改 |
| `TRIP_AI_PROVIDER` | 本地 `stub`、生产 `openai-compatible` | 行程生成供应商；Stub 不访问网络 |
| `TRIP_AI_CONNECT_TIMEOUT` / `TRIP_AI_REQUEST_TIMEOUT` | `PT5S` / `PT45S` | AI 连接与单次请求超时 |
| `TRIP_AI_TOTAL_TIMEOUT` | `PT2M` | 包含重试和退避的单个生成任务总截止时间 |
| `TRIP_AI_MAX_ATTEMPTS` | `3` | 瞬态故障最大尝试次数 |
| `TRIP_AI_GLOBAL_CONCURRENCY` / `TRIP_AI_USER_RPM` | `4` / `5` | 单实例全局并发及每用户每分钟请求限制 |
| `TRIP_EXECUTOR_CORE_SIZE` / `TRIP_EXECUTOR_MAX_SIZE` | `2` / `4` | 行程生成线程池大小 |
| `TRIP_EXECUTOR_QUEUE_CAPACITY` | `50` | 行程生成等待队列容量 |
| `TRIP_JOB_STALE_AFTER` / `TRIP_RECOVERY_INTERVAL` | `PT3M` / `PT30S` | 中断任务判定和恢复扫描间隔；应大于总截止时间 |
| `CONSULTATION_AI_PROVIDER` | 本地 `stub`、生产 `openai-compatible` | 旅游咨询供应商 |
| `CONSULTATION_GLOBAL_CONCURRENCY` / `CONSULTATION_USER_RPM` | `4` / `20` | 单实例咨询并发及用户频率限制 |
| `CONSULTATION_USER_CONCURRENCY` | `1` | 单用户同时进行的咨询数 |
| `CONSULTATION_REQUEST_TIMEOUT` / `CONSULTATION_TOTAL_TIMEOUT` | `PT45S` / `PT60S` | 单次读取及完整咨询截止时间 |
| `CONSULTATION_DISCONNECT_GRACE` | `PT30S` | SSE 断线后的手动重连宽限期 |
| `CONSULTATION_EVENT_RETENTION` | `PT10M` | SSE 事件重放保留时间 |
| `JWT_SECRET` | 仅本地有开发默认值 | JWT HMAC 密钥，生产环境至少 32 字节且必须设置 |
| `JWT_ISSUER` | `smart-travel-assistant` | JWT 签发者 |
| `ACCESS_TOKEN_TTL` | `PT15M` | Access Token 有效期 |
| `REFRESH_TOKEN_TTL` | `P30D` | Refresh Token 有效期 |
| `VITE_API_BASE_URL` | `/api/v1` | 浏览器请求前缀 |

后端环境通过 Spring Profile 区分：`dev` 用于本地调试，`test` 使用内存数据库隔离测试，`prod` 强制从环境变量读取数据库凭据。生产环境启动时应设置 `SPRING_PROFILES_ACTIVE=prod`。

## 已实现接口

- `POST /api/v1/auth/register`：注册并签发 Token
- `POST /api/v1/auth/login`：登录
- `POST /api/v1/auth/refresh`：一次性轮换 Refresh Token
- `POST /api/v1/auth/logout`：幂等撤销 Refresh Token
- `GET /api/v1/users/me`：查询当前用户
- `PATCH /api/v1/users/me`：修改当前用户展示名称
- `POST /api/v1/trips`：幂等提交行程生成任务，返回 `202`
- `GET /api/v1/trips`、`GET /api/v1/trips/{tripId}`：分页查询及轮询生成状态
- `PATCH /api/v1/trips/{tripId}`、`DELETE /api/v1/trips/{tripId}`：重新规划及软删除
- `POST /api/v1/trips/{tripId}/adjustments`：通过自然语言生成完整新版本
- `GET /api/v1/trips/{tripId}/versions`：查询不可变版本历史
- `POST /api/v1/trips/{tripId}/versions/{versionNumber}:restore`：切换当前版本
- `POST/GET /api/v1/conversations`：创建和查看自己的咨询会话
- `POST /api/v1/conversations/{id}/messages`：普通 AI 旅游问答
- `POST /api/v1/conversations/{id}/messages:stream`：SSE 流式旅游问答
- `GET/DELETE /api/v1/conversations/{id}/streams/{streamId}`：事件重放和取消
- `GET /api/v1/health`：健康检查

行程生成默认使用确定性的 Stub，便于 Codespaces 和本地环境在没有密钥时完整演示。生产环境默认使用 OpenAI 兼容适配器，并要求 `AI_API_KEY`。超时、瞬态服务错误和供应商限流会有限重试；应用限流当前是单实例内存实现，横向扩容前需替换为网关或 Redis 共享限流。AI 行程中的价格、班次和营业信息均为建议，并不代表实时数据。

## 接口与开发约定

- 人类可读约束见 [`docs/api-contract.md`](docs/api-contract.md)。
- 机器可读契约见 [`docs/openapi.yaml`](docs/openapi.yaml)，它是接口字段的单一事实来源。
- 数据关系和持久化规则见 [`docs/data-model.md`](docs/data-model.md)。
- 新增或修改接口时，先更新 OpenAPI，再修改后端与前端类型，并在 `TODO.md` 更新进度。
- 提交前至少执行前端类型检查与构建、后端测试。
- GitHub Actions 会在推送和 Pull Request 时自动执行上述检查。

## 当前进度

详细任务和勾选状态见 [`TODO.md`](TODO.md)。目前已完成项目骨架、用户认证、智能行程规划后端和 AI 旅游咨询后端，下一阶段实现完整前端交互。
