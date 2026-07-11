# Round 06：质量加固与 MVP 交付计划

## 1. 本轮目标与边界

本轮完成 `TODO.md` 第 7 节剩余事项，把当前可开发、可测试的应用收口为可重复构建、可在 GitHub Codespaces 远程演示、具备基本运维与恢复手册的 MVP。

本轮允许修改代码、构建配置、CI、容器编排、测试与文档，但不扩展业务功能。`tag`、GitHub Release、公开部署或修改仓库可见性属于外部状态变更：实施阶段只能准备并验证发布产物、发布说明和命令；除非用户明确授权，不创建或推送 tag，不创建 GitHub Release，也不改变端口可见性或仓库设置。Codespaces 只作为临时远程演示环境，不承诺持续在线。

## 2. 现状与主要缺口

- 前端已有类型检查、Vitest 覆盖率、生产构建、Playwright E2E 和 `npm audit`，但没有 ESLint/Prettier，也没有统一的本地质量入口和提交前检查。
- 后端 `mvn verify` 已覆盖集成测试，但没有可执行的格式检查和静态规则；CI 也未验证工作区格式。
- 当前 `docker-compose.yml` 只有 MySQL；前后端均无 Dockerfile，无法用一条 Compose 命令验证完整生产式链路。
- 已有 AI 并发/RPM 限流、任务恢复、SSE 重连和安全契约测试，但缺少明确的负载基线、限流边界矩阵、容器级依赖故障/重启恢复演练。
- README 适合开发启动，但缺少部署、监控、备份恢复、升级回滚和系统化排障文档。
- Codespaces 已配置公开前端端口，但尚无面向演示者的一键启动、健康检查和验收清单；也没有版本化发布清单与发布说明。

## 3. 实施步骤

### 3.1 统一格式化、静态检查与提交检查

1. 前端依赖与配置：
   - 在 `frontend/package.json` / `package-lock.json` 增加 ESLint（Vue + TypeScript 官方插件）、Prettier 与 Vue 格式支持，固定兼容版本。
   - 新增 `frontend/eslint.config.js`、`frontend/.prettierignore`，根目录新增 `.editorconfig`；规则优先发现未处理 Promise、无效 Vue 模板、未使用变量和危险类型逃逸，不在本轮进行纯风格争论。
   - 增加 `format`、`format:check`、`lint`、`check` 脚本；`check` 至少串联格式检查、lint、type-check、单测覆盖率和 build。
   - 先执行格式化，再人工检查差异，保证不改变行为；生成目录、依赖、覆盖率和 Playwright 产物必须忽略。
2. 后端 Maven 质量门：
   - 修改 `backend/pom.xml`，加入 Spotless Java 格式检查（固定格式版本）及 SpotBugs 或 PMD 静态检查；插件绑定到 `verify`，并只对项目源码生效。
   - 如静态工具对 Spring/JPA 产生合理误报，使用最小、带注释的规则排除文件，而不是全局跳过。
   - 提供 `mvn spotless:apply`（修复）与 `mvn verify`（只检查）约定；格式化后运行完整测试。
3. 仓库统一入口与提交检查：
   - 新增 `scripts/check.sh`，按确定顺序执行 OpenAPI lint、前端检查、后端 `verify`；支持 `--quick` 仅执行格式/静态/类型/后端快速校验，完整模式用于 CI 和交付。
   - 新增版本控制内的 `.githooks/pre-commit` 与 `scripts/install-hooks.sh`，安装脚本仅设置本仓库 `core.hooksPath=.githooks`，不得修改全局 Git 配置。Hook 执行 `scripts/check.sh --quick`，失败必须阻止提交并给出修复命令。
   - README 明确 Hook 是开发者显式安装步骤；CI 是不可绕过的最终质量门。
4. 更新 `.github/workflows/ci.yml`：前端加入格式与 lint，后端继续以绑定了静态检查的 `verify` 为准；OpenAPI 使用固定 CLI 版本，避免 `npx --yes` 浮动版本。

验收：格式错误、ESLint 错误或后端静态规则错误任一注入后，相应本地命令和 CI 命令均非零退出；恢复后 `scripts/check.sh` 完整通过，工作树没有格式化残留。

### 3.2 前后端镜像与完整编排

1. 后端镜像：
   - 新增 `backend/Dockerfile` 和 `.dockerignore`，使用 Java 21 多阶段构建；运行阶段只带 JRE/应用产物。
   - 创建非 root 用户，以只读应用文件运行，暴露 8080；通过 Actuator `health/readiness`（或已有健康端点）提供容器健康检查。
   - 不在镜像层写入数据库、JWT、AI 或第三方服务密钥；支持优雅停机，配置合理 JVM 容器内存参数。
2. 前端镜像：
   - 新增 `frontend/Dockerfile`、`.dockerignore` 与 `frontend/nginx.conf`，Node 24 阶段执行锁文件安装和生产构建，运行阶段由非 root Nginx（或等价最小 Web Server）提供静态文件。
   - 配置 SPA history fallback、静态资源缓存、安全响应头和 `/api/` 到 `backend:8080` 的同源反向代理；SSE 路径关闭代理缓冲并设置足够读取超时。
   - 增加不依赖业务认证的 HTTP 健康端点。
3. Compose 分层：
   - 将根 `docker-compose.yml` 扩展为默认完整本地栈 `mysql + backend + frontend`，保留只启动 `mysql` 的兼容能力；服务使用健康依赖而非固定 sleep。
   - 新增 `.env.example`（只含占位/开发值）及 `docker-compose.prod.yml` 生产式覆盖：不暴露 MySQL/后端到公网，只暴露前端；禁用源码挂载；设重启策略、资源限制、日志轮转、只读文件系统/临时目录（可行处）和命名持久卷。
   - JWT 与数据库密码在生产式启动时必须显式提供；Compose 配置展开输出不得包含真实密钥，仓库不得提交 `.env`。
4. 新增 `scripts/compose-smoke.sh`：构建并启动栈，等待三个服务 healthy，经过前端代理验证首页、健康 API、注册/登录和至少一个 Stub 行程或咨询请求，最后用 trap 清理容器；保留命名卷由参数控制，CI 默认使用独立 project name 并完整清理。
5. CI 新增 `container-smoke` job，执行 `docker compose config`、镜像构建、健康检查和 smoke；失败上传容器状态与脱敏后的末尾日志。

验收：`docker compose up --build -d` 后所有服务 healthy；浏览器只访问前端端口即可完成 API/SSE；`docker inspect` 显示前后端进程 UID 非 0；镜像历史和 Compose 展开配置无真实 secret；停止并重启 backend 后前端可恢复访问，停止再启动 MySQL 后持久数据仍存在。

### 3.3 性能、限流、安全与故障恢复验证

1. 性能基线：
   - 在 `tests/performance/` 增加可重复的 k6 脚本（通过固定 Docker 镜像运行，避免本机安装依赖）和 `scripts/perf.sh`。
   - 场景覆盖健康检查、登录后行程列表/详情、实时地点查询，以及低并发 Stub 咨询；测试数据由 setup 创建并在独立数据库运行。
   - MVP 门槛记录为小型演示环境基线而非生产 SLA：无意外 5xx、错误率 `<1%`，健康/只读核心 API 的 p95 `<500ms`（Stub、预热后、建议 10 VU/60 秒）。AI/外部数据接口单列成功率与上游延时，不套用 500ms 门槛。
2. 限流测试：
   - 补充后端集成测试，分别验证行程 RPM、咨询 RPM、单用户并发、全局并发达到阈值时返回契约规定的 429/业务错误、`Retry-After`（若契约要求），并验证不同用户互不串扰、窗口结束可恢复。
   - 使用可控时钟/阻塞 Stub，禁止依赖真实等待导致测试不稳定；在 OpenAPI 与 `docs/api-contract.md` 对齐限流响应。
3. 安全检查：
   - 扩充 `SecurityContractIntegrationTest` 或新增专项测试：匿名访问、越权读取/修改、无效和过期 Token、输入边界、提示词注入/隐私过滤、日志脱敏、CORS/安全头、SSE 归属与取消权限。
   - CI 保留 `npm audit --audit-level=high`，新增 Maven 依赖漏洞检查时固定工具/数据库缓存，并定义网络不可用策略：PR 中工具故障与发现高危漏洞必须可区分，已确认高危漏洞阻断发布。
   - 对容器运行 `docker scout`、Trivy 或等价固定版本扫描；High/Critical 有可利用修复版本时阻断 MVP，例外必须写明 CVE、影响分析、补偿措施和到期日。
4. 故障恢复：
   - 自动化测试 AI 超时/429/5xx/畸形响应、实时供应商超时与陈旧缓存降级、SSE 客户端断开重放、生成中服务重启后的 stale job 恢复，且验证不会保存半成品或重复版本。
   - `scripts/recovery-smoke.sh` 在 Compose 中制造 backend 重启、短暂 MySQL 不可用和外部 provider 不可达，验证健康状态先失败再恢复、已有数据不丢失、请求返回受控错误且日志不泄密。
   - 不使用破坏宿主机或共享环境的故障注入；所有容器和数据使用独立 Compose project name。

验收：上述自动化测试进入 CI（较慢的性能/恢复场景可放独立 job）；测试报告保存为 artifact；所有门槛与环境规格记录在报告中，失败可通过同一命令复现。

### 3.4 部署、运维、备份与排障文档

新增并互相链接以下文档：

- `docs/deployment.md`：前置条件、配置矩阵、secret 生成和注入、Compose 本地与生产式启动、域名/TLS 反代建议、Flyway 升级顺序、健康验证、升级和回滚；明确公共 Nominatim/Overpass/Open-Meteo 仅用于低流量演示。
- `docs/operations.md`：进程/容器状态、健康/就绪、关键日志与 request ID、容量和限流参数、AI/实时供应商指标、磁盘/数据库/线程池/错误率告警建议、日常检查和优雅启停。
- `docs/backup-restore.md`：MySQL 一致性备份、加密/保留/异地存储原则、校验和、恢复到全新卷、Flyway 版本核对；提供 `scripts/backup.sh`、`scripts/restore.sh`，要求显式目标与确认，默认不覆盖现有库。
- `docs/troubleshooting.md`：端口冲突、容器 unhealthy、Flyway 失败、MySQL 认证、JWT 配置、AI 429/超时、实时供应商降级、SSE/Nginx 缓冲、Codespaces 端口不可见、备份恢复失败的症状—诊断—处理步骤。
- `docs/release-checklist.md`：版本号、变更、全量质量门、镜像摘要、数据库兼容性、漏洞结果、备份/回滚、演示验收和授权步骤。

备份恢复必须实际演练：创建带唯一标记的用户/行程，执行备份，恢复到全新 MySQL 卷/临时实例，验证 Flyway schema history、记录数量与唯一标记，生成不含用户隐私和密钥的 `docs/reports/backup-restore-verification.md`。RPO/RTO 只记录本次演练测量值及建议目标，不虚构生产保证。

### 3.5 Codespaces 远程演示与 MVP 发布准备

1. 调整 `.devcontainer/devcontainer.json`、`.devcontainer/post-create.sh` 和 `scripts/dev.sh`：初始化幂等；一条命令启动完整演示栈或开发模式；启动后打印前端转发 URL 的获取方式、健康状态与 Stub/live 模式。默认使用 Stub，不要求任何 AI key。
2. 新增 `scripts/demo-smoke.sh`，从 Codespace 前端公开 URL（或本地 URL）验证首页、代理健康、注册、生成行程、实时数据来源展示和流式咨询；README 增加 5 分钟演示脚本及停机/隐私提醒。
3. Codespaces 验收：新建干净 Codespace，运行文档中的唯一启动命令；5173 前端可远程打开，API 不要求直接公开；完成桌面/移动核心流程；停止再启动 Codespace 后数据库卷状态和恢复行为符合文档。记录提交 SHA、时间和结果至 `docs/reports/mvp-verification.md`。若当前执行环境无法创建新 Codespace，标记为唯一待人工远程验证项，不能伪称通过。
4. 统一版本为 `0.1.0`（前端包、后端 artifact、镜像标签和发布说明），新增 `CHANGELOG.md` 与 `docs/releases/v0.1.0.md`，列出能力、限制、配置、迁移、验证证据和已知风险。
5. 准备可发布产物：后端可执行 JAR、前端静态包、OpenAPI、SBOM（前后端）、镜像构建命令及 SHA256 校验文件；CI `release-candidate` job 在手动触发或版本标签条件下构建并上传 Actions artifact，但不自动发布到公开 registry。
6. 实施代理仅可提交/推送普通代码提交（沿用用户此前对仓库开发的授权）。完成验证后向用户报告候选提交 SHA，并提供拟执行命令：
   - `git tag -a v0.1.0 <sha> -m "Smart Travel Assistant MVP v0.1.0"`
   - `git push origin v0.1.0`
   - `gh release create v0.1.0 ...`
   只有获得用户对 tag/Release 的明确确认后才能执行；若未确认，`TODO.md` 的“发布 MVP”只能标记为“发布候选已就绪”，不得勾选为已发布。

## 4. 测试与验收矩阵

| 层级 | 命令/场景 | 强制结果 |
| --- | --- | --- |
| OpenAPI | 固定版本 Redocly lint | 语义有效；新增 warning 不得无说明增加 |
| 前端静态 | `npm ci && npm run format:check && npm run lint && npm run type-check` | 全部零退出 |
| 前端测试 | `npm run test:coverage && npm run build` | 现有覆盖率阈值不下降；生产构建成功 |
| 后端 | `mvn --batch-mode verify` | 格式、静态检查及全部测试通过 |
| 浏览器 | `bash scripts/e2e.sh` | 桌面与移动核心流程、SSE 流程全部通过 |
| 容器 | `bash scripts/compose-smoke.sh` | 构建、healthy、同源 API/SSE、非 root、重启恢复通过 |
| 性能 | `bash scripts/perf.sh` | 在记录环境下满足错误率和 p95 基线 |
| 安全 | 依赖/镜像扫描 + 安全集成测试 | 无未处置的可修复 High/Critical；鉴权边界通过 |
| 恢复 | `bash scripts/recovery-smoke.sh` | 故障受控、健康可恢复、无数据/隐私泄漏 |
| 备份 | 备份到全新实例并核对数据/Flyway | 校验和、标记数据和 schema 版本一致 |
| Codespaces | 干净环境启动 + `scripts/demo-smoke.sh` | 公开前端可完成 Stub MVP 演示；证据记录真实 |
| 发布候选 | `scripts/check.sh` + artifact/SBOM/checksum | 候选产物可追溯到同一 Git SHA |

CI 建议按 `openapi`、`frontend`、`backend`、`e2e`、`container-smoke`、`security` 分 job 并行；`performance/recovery` 可在 main、手动触发或发布候选时运行，但 MVP 候选必须至少成功一次。所有 Action 固定 major 或完整版本，权限维持最小 `contents: read`；发布 job 需要写权限时另行设计环境审批，不能给普通 CI 常驻写权限。

## 5. 文档与任务同步

- 更新 `README.md`：完整栈启动、质量命令、Codespaces 演示入口、容器部署入口、运维/发布文档链接与 MVP 限制。
- 实施每个子项并通过对应验证后，才更新 `TODO.md` 第 7 节勾选状态；“发布 MVP”遵循第 3.5 节授权边界。
- 更新 `docs/ai-usage-log.md`，记录规划、实现、独立审核、失败修正、实际测试结果及用户对发布动作的决定。
- 最终由独立审核终端复查实现、运行强制门槛并写 `docs/reviews/round-06-quality-delivery-review.md`；所有阻断项修正后复审。

## 6. 完成定义（Definition of Done）

以下条件必须同时满足，Round 06 才可称为实现完成：

1. 前后端格式化、静态分析、本地提交检查和 CI 质量门均可重复运行且通过。
2. 前后端 Dockerfile 和完整 Compose 栈通过构建、健康、同源代理、非 root、secret 与持久化验收。
3. 性能、限流、安全、依赖/镜像漏洞和故障恢复测试有可执行脚本、明确门槛和真实通过记录。
4. 部署、运维、备份恢复、排障及发布清单完整；备份已恢复到全新实例并验证，不只停留在文档描述。
5. 干净 Codespace 可按文档远程演示，或明确记录由于缺少外部环境权限而待用户执行的单一验收项；不得用本地结果冒充远程结果。
6. `0.1.0` 发布候选产物、SBOM、校验和、变更日志和验证报告均绑定同一提交 SHA，独立审核无阻断项。
7. 普通实现可以在上述第 1～6 项满足后完成；只有用户明确授权且 tag 与 GitHub Release 实际创建、可访问、产物校验成功后，才能勾选 `TODO.md` 的“发布 MVP”，并宣布整个 MVP 正式发布。
