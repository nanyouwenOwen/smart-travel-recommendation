# AI 使用记录

本文记录 AI 在项目开发中的实际贡献、独立复审、修正和验证证据；不记录密钥、Token 或用户隐私数据。

## 项目背景

- 项目：智能旅游助手
- 目标：交付可在 GitHub Codespaces 运行的前后端分离 AI 行程规划与旅游咨询 MVP
- 协作方式：独立规划终端 → 主开发终端实施 → 独立审核终端复审 → 修正复审
- 人类负责人：GitHub 仓库所有者 nanyouwenOwen

## 活动记录

### 2026-07-11 - 智能行程规划后端

- AI 贡献：制定 Round 02 计划，实施结构化行程生成、版本调整/恢复、预算统计、供应商适配与自动化测试。
- 任务摘要：根据目的地、预算、日期、人数和偏好生成可持久化的逐日行程，并满足幂等、归属和失败恢复约束。
- 产物：行程领域与 AI 适配代码、数据库迁移、OpenAPI/业务文档及测试。
- 审核与决定：独立审核终端提出问题后由主开发终端修正，最终通过审核。
- 验证：后端测试、OpenAPI 校验及 MySQL 迁移/实体校验通过。
- 证据：提交 `84c56a9 feat: implement intelligent trip planning`，计划和审核文档位于 `docs/plans/`、`docs/reviews/`。

### 2026-07-11 - AI 旅游咨询后端

- AI 贡献：制定 Round 03 计划，实施会话、普通问答、SSE 流式问答、短期事件重放、取消/恢复、行程上下文、安全策略与 OpenAI 兼容适配器。
- 任务摘要：完成 TODO 第 4 阶段，并以独立复审确认接口契约、幂等竞态、资源释放和隐私边界。
- 产物：`backend/.../consultation/`、V5 迁移、咨询提示词、OpenAPI/接口/数据模型文档及咨询测试。
- 审核与决定：三轮复审发现并推动修正流事件竞态、输出缓冲、错误分类、真实上游协议、超时资源取消、恢复事件字段和两事务幂等窗口。
- 问题与修正：最初首字节/空闲超时仅取消令牌，现同时取消执行 Future；恢复 error 缺少 `streamId`，现补齐并断言；消息 turn 与 stream 原为两事务加轮询，现合并为单事务并增加同时并发 POST 测试。
- 验证：`mvn verify` 通过，38 个测试、0 失败、0 错误；OpenAPI 0 error；第三轮最终独立复审 PASS。
- 证据：`docs/plans/round-03-ai-consultation.md` 及后续 `docs/reviews/round-03-ai-consultation-review.md`。

### 2026-07-11 - 完整前端应用

- AI 贡献：独立制定 Round 04 计划，实施 Vue 页面、Pinia 状态、认证刷新、行程与版本交互、SSE 聊天、响应式样式、Vitest 和 Playwright 真栈验收。
- 任务摘要：把占位首页升级为可在 Codespaces 远程演示的完整 MVP 前端，并消费现有 OpenAPI/真实后端。
- 审核与决定：第一轮独立审核判定 FAIL，提出调整版本轮询、幂等键、SSE 取消竞态、API 超时、路由竞态、表单、移动可访问性、E2E/覆盖率和文档等 12 项阻断；主开发终端接受并逐项修正。
- 问题与修正：调整轮询改为等待版本号超过基线；未知网络结果复用同一幂等键；SSE 使用 29 秒可中断/可见性感知重放并校验事件归属；建流响应增加 `X-Stream-Id` 以支持 ack 前取消；加入请求世代、超时、移动菜单、焦点对话框和覆盖率门禁。
- 验证：type-check、37 项 Vitest、覆盖率门禁和生产构建通过；7 项真实浏览器测试覆盖行程/版本/咨询重放与取消/键盘/移动/错误路径；第五轮独立复审 PASS。
- 证据：`docs/plans/round-04-frontend-application.md`、`frontend/e2e/`、`.github/workflows/ci.yml`。

## 结果摘要

### 2026-07-12 - 质量加固与 0.1.0 发布候选

- AI 贡献：独立制定 Round 06 计划，实施 Prettier/ESLint、Spotless/SpotBugs、本地 Hook、非 root 前后端镜像、完整 Compose、性能/恢复/备份 smoke、运维文档、安全扫描与可追溯候选产物流水线。
- 审核与修正：首轮独立审核判定 FAIL，指出生产 Compose 默认值、危险恢复目标、smoke 场景不足、缺少扫描和 SBOM/artifact；主开发终端接受并修正为生产环境必填 secret、全新库恢复、业务/SSE/备份/性能/重启链路及 release-candidate job。
- 本地验证：`scripts/check.sh --quick`、后端 43 项测试、前端 43 项测试、覆盖率 86.57%、格式/静态/类型/构建和依赖审计通过；Docker socket 无权限，容器与候选产物等待 GitHub CI 形成证据。
- 发布决定：普通代码推送沿用用户授权；尚未获得创建 `v0.1.0` tag 和 GitHub Release 的明确授权，因此不得宣称正式发布。

### 2026-07-12 - 外部实时旅游数据

- AI 贡献：独立核实供应商官方政策并制定 Round 05 计划；实施地点引用、天气、景点营业信息、持久缓存降级、地图与来源界面，以及按问题意图注入 AI 的最小实时上下文。
- 任务摘要：无密钥 Codespaces/CI 可确定性演示，同时为 Nominatim、Open-Meteo、Overpass 和后续 Transitous 保留可替换适配器与诚实的数据新鲜度语义。
- 产物：`backend/.../realtime/`、V6 迁移、三个受保护 API、`frontend/src/components/realtime/`、OpenAPI/接口/数据模型与供应商边界文档。
- 人类决定：用户要求保持“规划—实施—独立审核”循环直到项目结束；实现采用公共服务低流量演示、生产须替换或获批的边界。
- 问题与修正：后端初版仅在实时接口返回来源，未满足 AI 回答标注；主开发终端随后把实际使用的来源和更新时间持久化到消息，并将第三方内容作为不可信结构化数据注入提示词。
- 最终验证：后端 43 项测试与前端 41 项测试、类型检查、覆盖率 91.66%、生产构建和 OpenAPI 校验通过；GitHub Actions run `29162926553` 的 backend/openapi/frontend/e2e 全部成功，MySQL 8.4 完成 Flyway V6 和 Playwright 真栈验收；第三次独立复审最终 PASS。

- 主要 AI 贡献：需求拆解、契约设计、后端实现、测试、故障诊断、独立代码审核和文档同步。
- 人类决定：用户确认 GitHub/Codespaces 交付方式，并要求持续采用三终端循环直至项目结束。
- 已测结果：Round 02–04 已推送；当前后端 40 项、前端 41 项自动化测试通过。
- 当前限制：公共社区端点不提供生产 SLA，交通路由默认关闭；经度固定偏移兜底不处理夏令时或跨时区国家，生产需接入权威 timezone provider；最终交付加固仍待下一轮。
