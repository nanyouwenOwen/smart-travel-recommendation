# Round 04：完整前端应用实施计划

## 1. 目标与范围

本轮完成 `TODO.md` 第 5 阶段的全部八项任务，将当前单页占位骨架升级为可在 GitHub Codespaces 中直接演示的 Vue 单页应用。前端必须完整覆盖注册/登录、异步行程规划、行程详情与预算、自然语言调整与版本恢复、AI 咨询流式对话，并以真实后端和 Stub AI 完成浏览器端到端验证。

本轮以 `docs/openapi.yaml` 为字段和状态码的唯一事实来源，以 `docs/api-contract.md` 为幂等、认证、游标和 SSE 行为约束。外部地图、天气、景点和交通数据属于第 6 阶段，不在本轮伪造；界面需明确展示“AI 估算，非实时信息”。

## 2. 现状与关键设计决定

- 当前 `frontend/src` 只有 `App.vue`、首页占位视图、单路由和少量全局 CSS；尚无 API 客户端、领域类型、状态仓库和测试框架。
- 使用 Vue 3 Composition API、TypeScript、Pinia、Vue Router，避免本轮引入重量级 UI 框架；建立项目自己的设计令牌和可访问组件。
- 新增 Vitest、Vue Test Utils、MSW（单元/组件边界测试）及 Playwright（真实端到端测试）。测试脚本必须进入 CI，而不只是存在于仓库。
- Access Token 仅保存在 Pinia 内存中；Refresh Token 放入 `sessionStorage` 以支持同一浏览器标签页刷新后恢复，但不跨浏览器会话长期保存。任何日志、错误详情或持久化状态不得包含 Token。后端当前不支持 HttpOnly Cookie，因此文档明确该折中；未来若增加 Cookie 模式再移除浏览器可读 Refresh Token。
- API 收到 401 时只允许一次共享刷新：并发请求复用同一个 refresh Promise；刷新成功后轮换并重试原请求一次，失败则清理认证状态并跳转登录页，禁止刷新死循环。
- SSE 使用 `fetch`，因为原生 `EventSource` 不支持 POST body 和 Authorization header。解析器需正确处理任意 chunk 边界、`\n`/`\r\n`、多行 `data:`、注释心跳以及 `id/event/data` 字段。
- 创建/重新规划/调整是异步 `202`：详情页按有限退避轮询 `GET /trips/{id}`，仅在页面可见且状态为 `GENERATING` 时运行；`READY`/`FAILED`/离开页面/组件卸载立即停止。
- 服务端金额保持十进制字符串；显示时只做字符串安全格式化，不把金额转换为二进制浮点后参与合计。预算图形采用 CSS 条形图和文本表格，避免引入图表库。

## 3. 页面、路由和导航

建立以下路由，并使用路由元数据统一执行登录保护和访客重定向：

| 路由 | 页面 | 主要职责 |
| --- | --- | --- |
| `/` | `HomeView` | 产品说明、能力入口、登录状态对应 CTA |
| `/login` | `LoginView` | 登录表单、原目标页回跳 |
| `/register` | `RegisterView` | 注册及密码字节边界提示 |
| `/trips` | `TripListView` | 游标分页、状态筛选展示、空状态、新建入口 |
| `/trips/new` | `TripCreateView` | 规划需求表单、客户端校验、幂等提交 |
| `/trips/:tripId` | `TripDetailView` | 生成进度、逐日安排、预算、调整、版本、删除 |
| `/conversations` | `ConversationListView` | 会话列表、创建会话、可选绑定 READY 行程 |
| `/conversations/:conversationId` | `ConversationView` | 历史消息、流式问答、重连与取消 |
| `/:pathMatch(.*)*` | `NotFoundView` | 404 和安全返回入口 |

`App.vue` 承载应用壳；桌面使用侧栏/顶栏，窄屏使用可关闭导航。跳转后移动焦点到主标题，并为当前导航项设置 `aria-current="page"`。认证初始化期间显示整页骨架，避免先闪现受保护页面再跳转。

## 4. 文件级实施步骤

### 4.1 工程、类型和测试基础

1. 修改 `frontend/package.json`、`package-lock.json`：添加 Vitest、Vue Test Utils、jsdom、MSW、Playwright、覆盖率插件，以及 `test`、`test:unit`、`test:coverage`、`test:e2e` 脚本。
2. 修改 `frontend/vite.config.ts` 并新增 `frontend/vitest.setup.ts`：配置 `@` 别名、jsdom、全局清理、MSW 生命周期和覆盖率目录。
3. 新增 `frontend/src/api/types.ts`：逐项表达 OpenAPI 中的通用包裹、错误、认证、用户、Money、行程/版本/预算、会话/消息和 SSE payload。金额类型保持 string，状态和类别使用字面量联合类型。
4. 新增 `frontend/src/api/client.ts`：实现 JSON 请求、Bearer、`X-Request-Id`、超时 AbortSignal、204、统一 envelope 解包、结构化 `ApiError`、一次性共享 Token 刷新及重试。
5. 新增 `frontend/src/api/auth.ts`、`trips.ts`、`conversations.ts`：按领域封装所有已实现接口；写请求由调用方传入稳定 `Idempotency-Key`。
6. 新增 `frontend/src/api/sse.ts`：实现无副作用增量解析器、POST 建流、GET 重放、事件类型守卫、AbortController 和协议错误分类。未知事件向前兼容地忽略并记录非敏感诊断。
7. 新增 `frontend/src/utils/id.ts`、`money.ts`、`date.ts`、`validation.ts`：UUID 幂等键、金额显示、时区日期显示、表单规则。使用 Web Crypto，不使用时间戳作为幂等键。

### 4.2 状态管理与认证生命周期

1. 新增 `frontend/src/stores/auth.ts`：`initialize/register/login/logout/refresh/clearSession`；初始化读取 session refresh token并调用刷新，再请求 `/users/me`。刷新 Token 每次成功立即原子替换。
2. 新增 `frontend/src/stores/trips.ts`：列表游标、选中行程、创建、详情、轮询、调整、版本读取/恢复、删除。用请求序号或 AbortController 防止旧响应覆盖新路由状态。
3. 新增 `frontend/src/stores/conversations.ts`：会话列表、当前会话、历史消息分页、乐观用户消息、流状态、流重连/取消。消息按 ID 去重，delta 只追加到对应 assistant message；`done` 后重新读取消息详情作为最终真相。
4. 修改 `frontend/src/main.ts`、`router/index.ts`：在挂载前执行认证初始化；路由守卫区分 `requiresAuth` 和 `guestOnly`，保存经过校验的站内 redirect，禁止开放重定向。

### 4.3 应用壳、主题与复用组件

1. 重构 `frontend/src/styles/main.css`，新增 `tokens.css`、`utilities.css`：颜色、排版、间距、圆角、阴影、焦点环、动效；覆盖 320px 到宽屏，支持 `prefers-reduced-motion`、高对比焦点及深浅背景的 AA 对比度。
2. 新增 `layouts/AppShell.vue`、`components/navigation/AppHeader.vue`、`AppSidebar.vue`、`MobileNav.vue`。
3. 新增通用组件 `BaseButton.vue`、`BaseInput.vue`、`BaseSelect.vue`、`BaseTextarea.vue`、`FormField.vue`、`StatusBadge.vue`、`LoadingState.vue`、`EmptyState.vue`、`ErrorState.vue`、`ToastRegion.vue`、`ConfirmDialog.vue`、`SkeletonBlock.vue`。
4. 表单控件必须有显式 label、描述和关联错误；Toast 使用合适的 `aria-live`，破坏性操作使用可聚焦且能归还焦点的确认对话框。不可只用颜色传达状态。

### 4.4 首页、登录和注册

1. 重构 `HomeView.vue`：产品价值、三步流程、AI 估算提示，CTA 指向登录或新建行程。
2. 新增 `LoginView.vue`、`RegisterView.vue` 和 `components/auth/AuthFormShell.vue`。
3. 客户端校验邮箱、必填、密码 8～72 字符且 UTF-8 不超过 72 字节、显示名 1～50；服务端 `details[]` 映射回字段，认证错误显示为表单级错误。
4. 成功登录/注册后跳转安全 redirect 或 `/trips`；退出即使服务端失败也清除本地凭据，并展示可理解提示。

### 4.5 行程创建、列表、进度和详情

1. 新增 `TripListView.vue`、`TripCreateView.vue`、`TripDetailView.vue`。
2. 新增 `components/trips/TripRequestForm.vue`：目的地、开始日期（不得早于当前业务日期）、1～30 天、预算金额/币种、1～50 人、偏好标签、IANA 时区、附加要求；提交前聚焦首个错误。
3. 创建提交时生成一次幂等键，在未知网络结果时保留同一键供“重试本次提交”；明确收到非幂等冲突或用户修改表单后才换键。`202` 后跳转详情。
4. 新增 `TripGenerationStatus.vue`：展示 `GENERATING` 轮询、`FAILED` 的稳定错误码和重试入口。轮询采用 1s、2s、3s、5s（封顶）并加轻微抖动，网络暂时失败不把行程误判 FAILED。
5. 新增 `ItineraryTimeline.vue`、`ItineraryDayCard.vue`、`ActivityCard.vue`：语义化日期标题、时间、地点、费用、交通建议；没有 description/transport 时不渲染空标签。
6. 新增 `BudgetSummary.vue`、`BudgetCategoryList.vue`、`TripWarnings.vue`：总预算、估算总额、类别拆分、超支金额和警告；图形均有等价文本，显著展示“估算且非实时”。
7. 列表实现不透明 cursor 的“加载更多”，不得推导 cursor；重复加载按 ID 去重。删除成功回列表并移除缓存，409/404 等按业务语义处理。

### 4.6 自然语言调整与版本恢复

1. 新增 `components/trips/TripAdjustmentPanel.vue`：2～2000 字指令、提交锁、防重复操作和调整中的状态说明。
2. 调整 `202` 后保留当前 READY 版本展示，同时启动状态轮询；失败必须继续显示原版本并说明未覆盖现有方案。
3. 新增 `TripVersionPanel.vue`、`TripVersionPreview.vue`：列出版本号、时间和估算总额；按需读取历史详情，不把历史版本误标成当前版本。
4. 恢复前显示版本号和影响确认；成功后用返回的 `TripResponse` 原子刷新详情、预算和当前版本。处理 409（生成中或状态冲突）并允许重新获取。

### 4.7 AI 咨询与可靠 SSE

1. 新增 `ConversationListView.vue`、`ConversationView.vue`；创建会话时可从 READY 行程中选择上下文，也可创建无行程会话。
2. 新增 `components/chat/MessageList.vue`、`MessageBubble.vue`、`ChatComposer.vue`、`StreamingIndicator.vue`、`ConversationHeader.vue`；消息区使用日志语义，新增 assistant 内容采用克制的 live region，避免每个 token 打断屏幕阅读器。
3. POST 流建立后处理 `ack` 并持久记录当前 `streamId`、最新十进制事件 ID和 message IDs；`delta` 按事件 ID 去重并增量渲染；`done` 标记完成并重新拉取消息；`error` 按 `retryable/final` 区分重连建议与终态。
4. 网络断开但未收到终态时，在服务端 30 秒宽限内通过 `GET .../streams/{streamId}` 和 `Last-Event-ID` 重放；使用 0.5s、1s、2s、4s 封顶退避，总时长不超过宽限，页面隐藏时暂停倒计时但不能无限延长。重放也必须使用 Authorization。
5. 用户点击取消时先 abort 当前 fetch，再幂等调用 DELETE；取消结果随后刷新消息，防止 UI 虚假显示仍在生成。离开路由不等于显式取消：仅断开本地连接，让用户可在宽限内返回重连。
6. 刷新页面时从会话消息恢复已有终态；MVP 后端没有“按会话查询活跃 stream”接口，因此尚未拿到 streamId 的刷新无法恢复原流，界面应刷新消息状态并给出明确重试提示，不伪造成功。

### 4.8 统一反馈与异常策略

1. 所有页面统一四态：初始加载、空、成功、错误；刷新失败时保留已有数据并显示非阻塞错误，而不是清空页面。
2. `ApiError` 保留 `status/code/message/details/requestId`，UI 可展示 requestId 供排障；401 交给认证层，404 跳资源不存在状态，409 提示刷新，429 提示稍后重试，502/503/504 提示 AI 暂不可用。
3. 全局 Toast 只用于跨页面操作结果；字段错误就近显示，页面加载错误使用 `ErrorState`，不可将所有错误都变成短暂 Toast。
4. 使用 `<button>`、`<a>`、标题层级和列表等原生语义；所有异步按钮包含文本状态并保持布局稳定，触摸目标至少 44px。

### 4.9 Codespaces、CI 和文档收尾

1. 修改 `.devcontainer/devcontainer.json`、`scripts/dev.sh`（仅在实际验证发现需要时）：保证前端监听 `0.0.0.0`、`5173` 公开转发，Vite `/api` 代理正确指向 `8080`，后端 Stub AI 无密钥即可完成演示。
2. 新增 `scripts/e2e.sh`：启动/检查 MySQL、使用 dev/stub 后端、启动 Vite，等待 health 后运行 Playwright，并用 trap 清理本脚本启动的进程。不得清理用户已有容器或服务。
3. 修改 `.github/workflows/ci.yml`：保留 build/type-check，加入 unit/coverage；增加带 MySQL service、真实 Spring Boot 和浏览器的 E2E job，上传失败 trace/screenshot。
4. 更新 `README.md`：页面能力、运行/测试命令、Codespaces 演示路径、session token 安全说明和 Stub/真实 AI 切换。
5. 所有验收通过后才勾选 `TODO.md` 第 5 阶段八项，并记录独立审核结果到 `docs/reviews/round-04-frontend-application-review.md`。

## 5. 测试矩阵

### 5.1 单元与组件测试（Vitest + Vue Test Utils + MSW）

| 区域 | 必测行为 |
| --- | --- |
| API 客户端 | envelope/204、业务错误详情、requestId、超时、一次共享 refresh、轮换、刷新失败清理、请求只重试一次 |
| SSE 解析器 | 拆分字段/chunk、CRLF、多行 data、UTF-8 跨 chunk、注释心跳、未知事件、畸形 JSON、ack/delta/done/error、事件 ID 去重 |
| 认证仓库/守卫 | 初次登录、刷新恢复、无 token、过期刷新、guestOnly、requiresAuth、安全 redirect、退出 |
| 规划表单 | 所有边界、金额字符串、日期、人数、偏好、UTF-8 长度、服务端字段错误、幂等键复用/更新 |
| 行程状态 | 轮询退避、READY/FAILED 停止、卸载取消、旧响应不覆盖、网络瞬断、隐藏页面 |
| 行程详情 | 活动顺序、可选字段、预算分类、超支、警告、估算声明、空 itinerary |
| 调整/版本 | 保留当前版本、生成失败不覆盖、历史预览、确认恢复、409 后刷新 |
| 会话 | 历史分页去重、乐观消息、delta 累加、done 回源、重连 Last-Event-ID、error、取消、卸载断开 |
| 通用 UI | loading/empty/error、Toast live region、Dialog 焦点、键盘导航、reduced motion |

建议对 `src/api`、`src/stores`、`src/utils` 设置至少 85% lines/functions 和 80% branches 的覆盖率门槛；视图覆盖率不作为替代行为断言的指标。

### 5.2 真正端到端测试（Playwright + MySQL + Spring Boot + Vite）

E2E 禁止拦截业务 API，必须通过浏览器访问 Vite，再由 `/api` 代理调用真实 Spring Boot、真实 Flyway/MySQL 和 Stub AI。每次测试注册唯一用户，彼此隔离：

1. 注册 → 自动登录 → 刷新页面恢复登录态 → 退出 → 受保护路由跳登录。
2. 创建完整规划 → 观察 GENERATING → READY → 查看逐日活动、预算分类与估算提示 → 列表可见。
3. 对 READY 行程提交自然语言调整 → 新版本 READY → 查看旧版本 → 恢复旧版本并确认当前版本改变。
4. 新建绑定行程的会话 → 发送流式问题 → 观察增量和 done → 刷新后消息持久存在。
5. 流式生成中制造浏览器侧断连并重连，验证基于 Last-Event-ID 的内容无重复；另测显式取消后终止生成。若 Stub 太快，提供仅限 `e2e` profile 的可配置 token 延迟，不能在前端伪造流。
6. 验证 320px 移动视口的导航和核心操作，以及键盘完成登录、创建规划、发送消息的主路径。
7. 对无数据用户验证行程和会话空状态；对不存在/越权 ID 验证不泄露资源的 404 页面。

### 5.3 构建与手工检查

- `npm run type-check`
- `npm run test:coverage`
- `npm run build`
- `npm run test:e2e`
- 使用 Chromium 检查控制台无错误、网络请求无 Token 日志、SSE 响应没有重复消息。
- 在 Codespaces 公网转发 URL 以桌面与手机尺寸完成一次注册、规划、版本恢复和咨询 smoke test。

## 6. 验收标准

1. TODO 第 5 阶段八项均有对应实现和自动化证据，不因只有页面外观而勾选业务项。
2. 未登录用户无法访问业务路由；页面刷新可通过一次 refresh 轮换恢复，失效凭据可靠清理，并发 401 不触发刷新风暴。
3. 规划表单遵守 OpenAPI 全部边界；创建使用稳定幂等键；用户能看到异步进度、成功详情或真实失败状态。
4. READY 行程完整展示逐日活动、预算分类、超支和警告；金额不经浮点计算；AI 信息明确标为估算且非实时。
5. 调整生成新版本且失败不覆盖旧版本；历史版本可预览，恢复后当前版本、行程和预算同步更新。
6. 咨询界面能消费真实 POST SSE，正确累加、重放、去重、完成和取消；断线恢复遵守 30 秒服务端宽限与 Last-Event-ID 约束。
7. 每个页面都有一致的加载、空、错误和成功反馈；320px 起可操作，键盘主流程可用，焦点和 live region 行为合理。
8. Vitest/覆盖率、类型检查、生产构建和真实栈 Playwright E2E 全绿；CI 执行这些门禁。
9. Codespaces 无 AI 密钥时可借助 Stub 完成核心演示，端口转发和 `/api` 代理在远程 URL 下工作。
10. 独立审核无阻塞项，README、TODO 和审核文档与实际状态一致后才提交并推送。

## 7. 风险与缓解

| 风险 | 影响 | 缓解措施 |
| --- | --- | --- |
| 浏览器可读 Refresh Token 受 XSS 威胁 | 会话被窃取 | 仅 sessionStorage、不持久化 access token、禁止 `v-html`、不引入远程脚本；后续推动 HttpOnly Cookie 契约 |
| SSE chunk/重放处理错误 | 内容乱码、重复或丢失 | 独立字节流解析器、TextDecoder streaming、事件 ID 去重、协议级单测和真实断线 E2E |
| 页面刷新时尚未收到 ack/streamId | 无法定位活跃流 | 明确产品限制，刷新会话消息并提示重试；后续契约增加活跃流查询，不在前端猜测 ID |
| Stub 生成太快导致轮询/SSE 场景不稳定 | E2E 假阳性或波动 | e2e profile 使用服务端可配置延迟，按最终业务状态等待，不使用固定长 sleep |
| 并发刷新、轮询和路由切换竞态 | 旧数据覆盖、重复请求 | 单飞 refresh、AbortController、请求世代号、组件卸载清理与 fake timer 测试 |
| 依赖 OpenAPI 手写类型漂移 | 前后端字段不一致 | 类型逐项对应契约，MSW fixture 复用类型，CI OpenAPI lint；后续可评估生成器但本轮不引入未验证代码生成链 |
| Playwright 真栈启动复杂 | CI/Codespaces 不稳定 | 统一 `scripts/e2e.sh`、health readiness、动态清理、保留 trace；不以 Mock E2E 降低验收标准 |
| 调整期间后端仍返回现有 READY 版本 | UI 误认为任务已结束 | 以接口实际状态和版本号变化共同判断，显示旧版本可用与后台生成两种状态，针对该行为加集成测试 |

## 8. 推荐实施顺序与审核检查点

1. API 类型、JSON 客户端、认证恢复及其单测。
2. 应用壳、基础组件、登录/注册和路由保护。
3. 行程表单、列表、轮询、详情与预算。
4. 调整、版本查看与恢复。
5. SSE 解析器、会话状态和聊天界面。
6. 统一状态、响应式、可访问性收敛。
7. 真实栈 E2E、CI、Codespaces 与文档。

每个检查点由主代理实施并跑相应测试；Round 04 全部实施后交给独立审核终端，审核必须按 OpenAPI、TODO、测试矩阵及真实运行行为逐项举证。阻塞项修复后由同一审核终端复审，直至通过。
