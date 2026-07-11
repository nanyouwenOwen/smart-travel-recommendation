# Round 01：用户认证与当前用户资料实施计划

## 目标

完成可供后续行程、咨询业务复用的认证闭环：用户注册、密码安全存储、登录、短期 Access Token、可轮换 Refresh Token、退出登录、当前用户资料查询与修改，以及统一的受保护接口鉴权行为。

本轮结束后，后端应能可靠回答“请求是谁发起的”，且不会在数据库或日志中保存明文密码、Refresh Token 或 JWT。

## 范围

### 本轮包含

- 注册并返回一组 Access Token 与 Refresh Token。
- 使用邮箱和密码登录。
- 使用 Refresh Token 轮换获取新 Token；旧 Refresh Token 立即失效。
- 退出登录并撤销本次 Refresh Token。
- 查询及修改当前登录用户的展示名称。
- Spring Security 无状态鉴权、统一 `401/403` JSON 错误响应和当前用户解析。
- Refresh Token 服务端持久化、过期与撤销管理。
- 用户资源归属校验的公共实现约定及鉴权测试；具体行程/会话归属查询留到对应业务轮次。
- OpenAPI、通用接口约束、配置说明和 TODO 状态同步（仅在实现及验证完成后同步）。

### 本轮不包含

- 邮箱验证、找回或重置密码、修改邮箱、修改密码。
- OAuth/OIDC 第三方登录、多因素认证、管理员或角色权限体系。
- 多设备会话管理页面、主动踢出全部设备。
- 前端注册/登录页面与浏览器 Token 存储策略。
- 行程和咨询业务实体的具体归属校验。
- 分布式 Token 黑名单；Access Token 签发后在其短有效期内保持有效。

## 关键业务规则

### 用户与密码

1. 邮箱在进入业务层时去除首尾空白并转换为小写，数据库保存规范化值；唯一性按大小写不敏感语义判断。
2. 注册邮箱必须符合邮箱格式且不超过 254 字符；重复邮箱返回 `409 EMAIL_ALREADY_REGISTERED`，不得披露已删除账户的额外信息。
3. 展示名称去除首尾空白后长度为 1～50；空白名称视为非法。
4. 密码长度为 8～72 个字符。密码仅用于请求处理，使用 BCrypt 编码后保存；不得记录、回显或进入异常消息。
5. 登录失败统一返回 `401 INVALID_CREDENTIALS`，邮箱不存在与密码错误使用相同响应，避免用户枚举。

### Token

1. Access Token 使用 JWT，至少包含 `sub`（用户 UUID）、`iat`、`exp`、`jti`；签发者固定且校验一致。不在 JWT 中放邮箱、展示名称等可变个人资料。
2. Access Token 默认有效期 15 分钟；Refresh Token 默认有效期 30 天。有效期均通过配置注入，测试环境使用更短时长覆盖过期场景。
3. JWT 使用至少 256 bit 的独立密钥。生产环境 `JWT_SECRET` 必填且不得使用仓库默认值；应用启动时校验密钥强度。
4. Refresh Token 是密码学安全的随机不透明字符串。客户端只收到明文一次；数据库只保存 SHA-256 摘要、用户、过期时间、撤销时间、创建时间及轮换关系。
5. 刷新采用一次性轮换：成功刷新时在同一事务内撤销旧 Token 并创建新 Token。已过期、已撤销、格式非法或用户已删除均返回 `401 INVALID_REFRESH_TOKEN`。
6. 检测到已被轮换 Token 再次使用时，撤销该轮换链中仍有效的后续 Token，并返回 `401 REFRESH_TOKEN_REUSED`，降低 Refresh Token 被窃取后的影响。
7. 退出接口撤销提交的 Refresh Token。对已经无效的 Token 仍返回成功，保证幂等且不泄露 Token 状态。
8. Access Token 只通过 `Authorization: Bearer <token>` 接收。缺失、格式错误、签名错误、过期、用户不存在或已软删除均返回统一 `401 UNAUTHORIZED`。

### 当前用户与资源归属

1. `GET /users/me` 仅返回 `id`、`email`、`displayName`、`createdAt`；绝不返回密码摘要或 Token 数据。
2. `PATCH /users/me` 本轮仅允许修改 `displayName`，成功返回更新后的用户资料。
3. 业务代码从已验证的 SecurityContext 获取用户 ID，禁止信任请求体或查询参数中的 `userId`。
4. 后续资源仓库查询统一携带 `ownerId`，例如 `findByIdAndUserIdAndDeletedAtIsNull`；资源不存在和越权读取均返回 `404 RESOURCE_NOT_FOUND`，避免泄露资源存在性。
5. 已认证但确实无权限执行某类操作时返回 `403 FORBIDDEN`；单用户资源越权读取仍按通用契约返回 404。

## 接口契约变更

实现前先修改 `docs/openapi.yaml`，并按需补充 `docs/api-contract.md`：

| 方法与路径 | 认证 | 请求 | 成功响应 | 主要错误 |
| --- | --- | --- | --- | --- |
| `POST /auth/register` | 否 | `email`, `password`, `displayName` | `201 AuthResponse` | 400, `409 EMAIL_ALREADY_REGISTERED` |
| `POST /auth/login` | 否 | `email`, `password` | `200 AuthResponse` | `401 INVALID_CREDENTIALS` |
| `POST /auth/refresh` | 否 | `refreshToken` | `200 AuthResponse` | `401 INVALID_REFRESH_TOKEN`, `401 REFRESH_TOKEN_REUSED` |
| `POST /auth/logout` | 否 | `refreshToken` | `204` | 400（请求结构非法） |
| `GET /users/me` | Bearer | 无 | `200 UserResponse` | `401 UNAUTHORIZED` |
| `PATCH /users/me` | Bearer | `displayName` | `200 UserResponse` | 400, `401 UNAUTHORIZED` |

契约细节：

- `AuthResponse.data` 增加 `tokenType: "Bearer"`，保留 `accessToken`、`refreshToken`、`expiresIn`；明确 `expiresIn` 单位为秒。
- 新增 `RefreshTokenRequest`、`LogoutRequest`、`UpdateCurrentUserRequest`、`UserProfile`、`UserResponse` Schema。
- `RegisterRequest` 和 `LoginRequest` 的密码上限统一为 72，登录邮箱上限为 254。
- `/auth/*` 显式 `security: []`；`/users/me` 使用全局 Bearer 安全要求。
- 所有有响应体的成功与失败响应继续携带请求追踪元数据；`204` 不包含响应体。

## 数据迁移变更

新增 `V2__create_refresh_tokens.sql`，创建 `refresh_tokens`：

- `id CHAR(36)` 主键。
- `user_id CHAR(36) NOT NULL`，外键引用 `users(id)`。
- `token_hash CHAR(64) NOT NULL`，唯一索引，只保存 SHA-256 十六进制摘要。
- `family_id CHAR(36) NOT NULL`，标识同一登录/轮换链。
- `replaced_by_token_id CHAR(36) NULL`，自引用轮换后的 Token。
- `expires_at TIMESTAMP(6) NOT NULL`。
- `revoked_at TIMESTAMP(6) NULL`。
- `created_at TIMESTAMP(6) NOT NULL`。
- 建立 `(user_id, revoked_at, expires_at)` 与 `family_id` 索引，支持撤销和清理。

迁移应同时在 MySQL 8.4 实际执行验证。H2 测试继续由 JPA 建表时，也必须保证实体字段和约束与迁移一致。Refresh Token 过期记录的定期物理清理不在本轮，可作为后续运维任务。

## 文件级实施步骤

### 1. 契约与依赖

1. 修改 `docs/openapi.yaml`：补齐刷新、退出、当前用户接口及 Schema、状态码和安全声明。
2. 修改 `docs/api-contract.md`：记录 Token 轮换、错误语义、资源归属查询规则。
3. 修改 `backend/pom.xml`：加入 `spring-boot-starter-security`、JWT 编解码所需的 Spring Security JOSE 依赖；测试沿用 Spring Security 测试支持，必要时显式加入 `spring-security-test`。
4. 修改 `backend/src/main/resources/application.yml`、`application-dev.yml`、`application-prod.yml` 与测试配置：加入 issuer、Access/Refresh 有效期和密钥来源；生产配置禁止弱默认密钥。

### 2. 持久化层

1. 新增 `backend/src/main/resources/db/migration/V2__create_refresh_tokens.sql`。
2. 在 `com.travelassistant.auth` 新增 `RefreshToken` 实体与 `RefreshTokenRepository`，提供按摘要查询、轮换链查询/撤销所需方法。
3. 按需要增强 `User`：保持受控修改方法，不暴露密码摘要；规范化逻辑放在服务层或专用值规范化工具中。
4. 扩展 `UserRepository` 的所有者/有效用户精确查询能力；继续尊重软删除过滤。

### 3. 安全基础设施

1. 新增 `SecurityConfiguration`：关闭基于 Session 的认证和 CSRF，允许健康检查与 `/auth/**`，其余 API 默认要求认证。
2. 提供 `PasswordEncoder`、`JwtEncoder`、`JwtDecoder`、`Clock` Bean；使用 `Clock` 让过期测试可重复。
3. 新增 JWT 签发服务，集中构造和校验 claims、issuer、有效期与密钥。
4. 新增认证主体/当前用户访问器，将 JWT `sub` 转换为用户 ID，并在需要时确认用户仍存在且未软删除。
5. 新增 JSON 格式的 `AuthenticationEntryPoint` 与 `AccessDeniedHandler`，复用 `ApiErrorResponse` 并读取 `RequestIdFilter` 的请求 ID。
6. 明确过滤器顺序：请求追踪先执行，确保 Spring Security 提前拒绝的响应也含 `X-Request-Id` 和 `meta.requestId`。

### 4. 认证与用户服务

1. 新增注册、登录、刷新、退出 DTO，并使用 Jakarta Validation 做结构校验。
2. 新增 `AuthService`：事务化注册、密码匹配、Token 对签发、刷新轮换与重用处置。
3. 新增 `AuthController` 映射 `/api/v1/auth/*`，严格遵守 OpenAPI 状态码。
4. 新增用户资料 DTO、`CurrentUserService` 与 `CurrentUserController`，实现 `GET/PATCH /api/v1/users/me`。
5. 邮箱/名称规范化使用单一实现；数据库唯一约束冲突应转换为稳定的 `EMAIL_ALREADY_REGISTERED`，避免并发检查竞态落入 500。
6. 确认敏感字段日志脱敏器覆盖 `Authorization`、`password`、`accessToken`、`refreshToken` 和 JWT/Refresh Token 值。

### 5. 测试与文档收尾

1. 添加服务单元测试覆盖密码散列、规范化、签发、轮换、过期及重用。
2. 添加 MockMvc 集成测试覆盖完整认证链与错误响应。
3. 添加 Repository 测试覆盖 Refresh Token 摘要唯一性、查询和撤销。
4. 用 MySQL 容器或本地 Compose 执行 Flyway V1+V2，确认迁移和 JPA `validate` 通过。
5. 执行 `mvn verify`；确认已有健康检查、异常处理、软删除测试无回归。
6. 实施与验证全部通过后，更新 `README.md` 的认证配置/接口说明，并勾选 `TODO.md` 第 2 阶段对应任务。

## 测试矩阵

### 正常流程

- 注册成功，邮箱被规范化，密码摘要不是明文且 BCrypt 可验证。
- 登录成功，Access Token claims/签名/有效期正确，Refresh Token 数据库中只有摘要。
- Access Token 可访问 `/users/me`；资料修改后重新查询一致。
- Refresh Token 成功轮换，新旧 Token 不同，旧记录撤销且指向新记录。
- 退出后该 Refresh Token 无法刷新；重复退出仍返回 204。

### 校验与冲突

- 邮箱格式错误、密码过短/过长、纯空白名称、缺字段和非法 JSON 均返回契约化 400。
- 同邮箱不同大小写或并发注册只允许一个成功，失败方返回 409。
- 用户不存在与密码错误的状态码、错误码和文案完全一致。

### 鉴权与安全

- 缺失 Bearer、错误 scheme、篡改签名、错误 issuer、过期 Token 返回 `401 UNAUTHORIZED`。
- 被软删除用户的旧 Access Token 不可继续访问受保护接口。
- 已轮换 Token 重放返回 `REFRESH_TOKEN_REUSED`，并使轮换链后续 Token 失效。
- Spring Security 产生的 401/403 与业务异常格式一致，响应头和响应体 request ID 一致。
- 响应与日志不包含密码、密码摘要、JWT 密钥或完整 Token。
- 受保护测试控制器验证：当前用户只能读取自己的模拟资源，越权读取返回 404。

## 验收标准

- `docs/openapi.yaml` 能被 OpenAPI 3.1 解析器通过，文档与实际路由、字段和状态码一致。
- Flyway V1+V2 能在 MySQL 8.4 空库上成功执行，Spring Boot 以 `ddl-auto=validate` 启动成功。
- `mvn verify` 全部通过且不跳过测试。
- 注册→访问资料→刷新→再次访问→退出的端到端后端流程通过。
- Access Token 过期、Refresh Token 过期/撤销/重用、并发重复邮箱均有自动化测试。
- 数据库无明文密码和明文 Refresh Token，接口不返回 `passwordHash`。
- 所有认证错误符合统一错误结构并带请求 ID。
- README 配置项与 TODO 第 2 阶段状态只在上述验证完成后更新。

## 风险与应对

| 风险 | 影响 | 应对 |
| --- | --- | --- |
| JWT 密钥弱或误提交 | 可伪造所有 Access Token | 生产强制环境变量、启动校验至少 256 bit、示例仅放占位符 |
| Refresh Token 并发刷新 | 同一 Token 可能签发多组后继 Token | 数据库事务与行锁/原子条件更新，确保只有一次轮换成功 |
| 重放处置不完整 | 被盗 Token 可长期维持会话 | 使用 `family_id`，检测已轮换 Token 后撤销整条仍有效后继链 |
| 邮箱唯一性竞态 | 并发注册出现 500 | 保留数据库唯一约束并映射约束异常为 409 |
| Spring Security 提前返回破坏响应契约 | 前端无法按统一错误码处理 | 自定义 EntryPoint/DeniedHandler，并对 request ID 和 JSON 结构做集成测试 |
| H2 与 MySQL 行为差异 | 测试通过但部署迁移失败 | 保留快速 H2 测试，同时必须在 MySQL 8.4 执行 Flyway/JPA validate 验收 |
| Access Token 无服务端即时撤销 | 退出后短期 Token 仍有效 | 采用 15 分钟短寿命并明确语义；高风险即时黑名单留作后续增强 |
| 用户软删除后 JWT 仍可解析 | 已删除用户可能继续访问 | 当前用户解析时检查有效用户，而非只信任 JWT 签名 |

