# Round 01 认证实现独立审核

## 审核结论

**不通过。** 当前 `mvn verify` 虽然成功（6 个测试，0 失败），但实现没有满足 Round 01 计划中的安全语义和验收矩阵。以下高严重度问题会使 Refresh Token 重放防护失效；测试与 MySQL 验证也明显不足，因此不得将本轮视为完成。

## 发现（按严重度排序）

### 高：重放检测后的整族撤销会随异常一起回滚

- 证据：`backend/src/main/java/com/travelassistant/auth/TokenService.java:40-49`。
- `rotate` 在 `@Transactional` 事务中修改后继 Token 的 `revokedAt`，紧接着抛出 `BusinessException`。该异常继承 `RuntimeException`，Spring 默认将整个事务回滚，所以第 46-47 行所做的整族撤销不会提交。
- 现有测试 `AuthFlowIntegrationTest:64-70` 看似验证了后继失效，但测试类本身带 `@Transactional`（第 27 行）；在同一个测试事务/持久化上下文里，已被修改的托管实体仍可能表现为撤销，不能证明生产环境异常回滚后数据库状态已持久化。
- 影响：窃取者重放旧 Token 后，合法或攻击者持有的后继 Token 仍可继续刷新，直接违背计划的核心防盗链规则。
- 必须修复：将“原子提交整族撤销”和“向客户端返回重放错误”设计为不会互相回滚的边界（例如显式事务模板/独立事务完成撤销后再抛错，或条件更新并在事务成功返回后映射结果），并用跨请求、跨事务的集成测试查询数据库确认后继记录确实已撤销。

### 高：幂等退出会清除已经记录的轮换关系，绕过重放检测

- 证据：`TokenService.java:59-62` 调用 `token.revoke(now, null)`；`RefreshToken.java:52-55` 无条件执行 `replacedByTokenId = replacementId`。
- 已经轮换的旧 Token 原本带 `replacedByTokenId`。客户端若对该旧 Token 调用公开且无需认证的 `/auth/logout`，会把该字段清空。随后再次刷新旧 Token，只会进入 `INVALID_REFRESH_TOKEN` 分支，而不会触发 `REFRESH_TOKEN_REUSED` 或撤销后继链。
- 影响：任何持有旧 Token 的一方均可主动抹除重放证据，使整族撤销防护失效。
- 必须修复：普通撤销不得覆盖已有 replacement；轮换关系只能在首次轮换时设置且不可被 null 覆盖。增加“轮换旧 Token → 用旧 Token 退出 → 重放旧 Token → 返回 `REFRESH_TOKEN_REUSED` 且后继链持久撤销”的测试。

### 中：已软删除用户的 Refresh Token 路径可能产生 500，而非契约化 401

- 证据：`User.java:11` 对用户实体应用 `@SQLRestriction("deleted_at IS NULL")`；`RefreshToken.java:21-23` 使用懒加载必需关联；`TokenService.java:52` 解引用 `current.getUser().isDeleted()`。
- 软删除行会被 Hibernate 限制条件过滤，关联代理解引用时可能得到不可用实体/抛出实体未找到异常，而 `isDeleted()` 本身在能加载的实体上实际上总为 false。代码没有稳定地把这一情况映射为 `INVALID_REFRESH_TOKEN`。
- 必须修复：通过明确的有效用户查询或包含用户有效性条件的 Token 查询判断，不依赖受 `@SQLRestriction` 过滤的懒关联；增加软删除用户使用旧 Access Token 和 Refresh Token 均返回统一 401 的集成测试。

### 中：密码的“72 个字符”约束与 BCrypt 的 72 字节边界不一致

- 证据：`RegisterRequest.java:4-6` 仅按 Java 字符数限制为 72；`AuthService.java:27` 直接调用 BCrypt。
- 多字节 UTF-8 密码可以满足 72 字符却超过 BCrypt 可处理的 72 字节边界，可能被截断或由实现拒绝并落入 500；这也可能导致不同密码在有效前缀后碰撞。
- 必须修复：明确并统一契约语义；若仍直接 BCrypt，应按 UTF-8 字节安全限制并返回 400，或采用经过审查的预哈希方案。增加多字节边界测试，确保不出现 500 和静默截断。

### 中：计划要求的关键自动化测试和 MySQL 验收缺失

- 证据：当前 `mvn verify` 只运行 6 个测试；认证测试只有 `AuthFlowIntegrationTest` 的 2 个方法。没有 Refresh Token repository 测试、并发刷新测试、并发重复邮箱测试、Token 过期/错误签名/错误 issuer 测试、非法 JSON/密码边界测试、软删除用户测试、403 测试或资源归属测试。
- `application-test.yml:6-10` 禁用 Flyway 并使用 H2 `create-drop`，无法证明 V1+V2 在 MySQL 8.4 执行成功，也无法证明 `ddl-auto=validate` 与迁移一致。
- 必须修复：补齐计划测试矩阵；至少增加真正并行且独立事务的同 Token 刷新测试，确保只产生一个后继 Token。用 MySQL 8.4 执行 V1+V2，并以 `ddl-auto=validate` 启动应用，保留可复现的命令/CI 或测试证据。

### 中：TODO 状态与实际验收不符，且资源归属公共实现缺失

- 证据：`TODO.md` 已将注册、登录/Token、资料查询修改标为完成，但上述核心安全规则和验收尚未通过；“资源归属校验和鉴权测试”仍未完成。当前代码中也没有计划所述 owner-aware 公共约定的可执行测试控制器/仓库查询测试。
- 必须修复：在修复与完整验证前撤回本轮完成勾选，或至少不要提交当前勾选状态；实现并测试资源归属从 SecurityContext 获取、越权按 404 的约束后再完成本阶段。

### 低：实现与 OpenAPI 的邮箱长度和退出错误响应不一致

- 证据：OpenAPI `RegisterRequest`/`LoginRequest` 的邮箱 `maxLength` 为 254；Java DTO 的 `RegisterRequest.java:4` 和 `LoginRequest.java:4` 为 256。`AuthService` 最终会拒绝超过 254 的邮箱，但实际校验错误详情与契约约束不一致。
- OpenAPI `/auth/logout` 只声明 204，没有声明请求结构非法时实际产生的 400；计划明确要求该 400。
- 必须修复：DTO 上限改为 254，并在 OpenAPI 为 logout 补充 400；对实际路由、状态码、字段做契约测试或语义校验。

### 低：认证配置只校验非空/长度，没有校验有效期为正

- 证据：`AuthProperties.java:12-16` 的两个 Duration 只有 `@NotNull`；`TokenService.java:66,72-73` 直接使用。
- 零或负有效期会使服务成功启动但签发立即过期的 Token，`expiresIn` 甚至可能为负数，与 OpenAPI 预期不符。
- 必须修复：对 Access/Refresh TTL 增加正值校验和启动失败测试，并约束 Refresh TTL 不低于合理下限（若业务决定需要）。

## 已确认的正向证据

- Access JWT 包含 issuer、subject、issuedAt、expiresAt、jti，并显式使用 HS256（`TokenService.java:64-70`）；decoder 配置 issuer 校验（`SecurityConfiguration.java:39-42`）。
- Refresh Token 使用 `SecureRandom` 生成 48 字节随机值，数据库只保存 SHA-256 摘要（`TokenService.java:71-78`，`V2__create_refresh_tokens.sql:1-16`）。
- 同一 Token 的普通并发轮换使用悲观写锁（`RefreshTokenRepository.java:11-12`），设计方向正确，但仍需真实独立事务并发测试。
- Security 配置为无状态、关闭 CSRF，认证错误使用统一 JSON writer；请求 ID filter 具有最高优先级。
- 当前后端 `mvn verify` 成功：6 tests，0 failures/errors。该结果只能证明现有窄测试集通过，不能覆盖上述验收要求。

## 复审准入条件

1. 修复两个 Refresh Token 高严重度问题，并以独立事务测试证明数据库最终状态。
2. 修复软删除 Refresh 路径和 BCrypt 多字节边界。
3. 补齐 Token 过期/篡改/issuer、并发刷新、并发邮箱、软删除、请求契约、401/403/request-id 和资源归属测试。
4. 在 MySQL 8.4 验证 V1+V2 迁移及 JPA validate。
5. 同步 OpenAPI、README、TODO，使完成声明与证据一致。
6. 重新执行 `mvn verify`，全部通过后再请求独立复审。

---

## 修复后复审（2026-07-11）

### 复审结论

**不通过，需再次修正。** 两个原高严重度漏洞及软删除、BCrypt 边界、OpenAPI 差异已修复，真实 MySQL 迁移/validate 证据也满足本轮数据库门槛；但计划明确要求的 Refresh Token 过期、TTL 启动校验、受保护资源归属/SecurityContext、403/request-id 契约及 Refresh Token repository 覆盖仍缺少自动化证据。当前不能把 Round 01 视为完整验收通过。

### 原报告逐项复审

#### 已修复：重放撤销事务持久化

- `TokenService.java:40` 已使用 `@Transactional(noRollbackFor = BusinessException.class)`，因此重放分支第 46-48 行撤销后抛出的业务异常不会回滚撤销。
- `AuthFlowIntegrationTest.java:36` 已移除测试类事务；第 76-82 行通过后续独立 HTTP 请求确认旧 Token 返回 `REFRESH_TOKEN_REUSED` 后，新 Token 也无法刷新。这比原测试能有效证明跨事务最终状态。
- `mvn clean verify` 实际运行通过该流程。

#### 已修复：logout 不再清除 replacement

- `RefreshToken.java:52-55` 只在 replacement 非 null 且原字段为空时写入，不再被 `revoke(now, null)` 清空。
- `AuthFlowIntegrationTest.java:66-82` 覆盖“轮换 → 使用旧 Token logout → 重放旧 Token → 后继失效”。

#### 已修复：软删除认证路径

- `RefreshTokenRepository.java:13-15` 使用 `join fetch token.user`；受 `User` 的软删除限制影响，已删除用户的 Token 查询不会返回有效关联，从而稳定进入 `INVALID_REFRESH_TOKEN`。
- `AuthFlowIntegrationTest.java:105-122` 验证已软删除用户的 Access Token 与 Refresh Token 都返回 401，且 Refresh 错误码正确。

#### 已修复：BCrypt UTF-8 字节边界

- `AuthService.java:26,56-59` 在注册前按 UTF-8 字节数拒绝超过 72 字节的密码；登录也在匹配前拒绝超界输入。
- `AuthFlowIntegrationTest.java:124-127` 使用 90 UTF-8 字节的中文密码验证返回 400，而非 500。

#### 部分完成：TTL 校验实现存在，但缺少验收测试

- `AuthProperties.java:18-22` 已增加 Access/Refresh TTL 必须为正的 Bean Validation 约束，功能实现方向正确。
- 仍没有配置绑定/应用启动测试证明 `PT0S` 或负 Duration 会导致启动失败。原报告的“启动失败测试”要求未满足。
- 必须修复：增加配置属性验证测试，分别覆盖零/负 Access TTL 与零/负 Refresh TTL。

#### 部分完成：并发刷新与并发邮箱已有测试，但持久化断言仍可加强

- `AuthFlowIntegrationTest.java:130-149` 使用两个线程发出独立请求，验证同 Token 并发刷新只出现一个 200，另一个 401；悲观写锁路径得到执行。
- `AuthFlowIntegrationTest.java:169-182` 验证并发同邮箱注册结果为 201/409。
- 测试没有直接断言并发刷新后数据库只有一个 replacement，且 401 的错误码未断言为 `REFRESH_TOKEN_REUSED`。目前状态码加锁实现足以消除原高危，但建议补上数据库/错误码断言，防止错误原因漂移。

#### 部分完成：Access Token issuer/过期已覆盖，Refresh Token 过期未覆盖

- `AuthFlowIntegrationTest.java:151-160` 覆盖过期 Access JWT 和错误 issuer JWT；篡改 Token 也在第 121-122 行覆盖。
- 没有测试 Refresh Token 的 `expiresAt` 边界或过期后的 `INVALID_REFRESH_TOKEN`，也没有可注入 Clock 的过期单元测试。计划测试矩阵明确要求 Refresh Token 过期。
- 必须修复：通过可控 Clock 或数据库构造覆盖到期前/到期时/到期后行为。

#### 未充分完成：资源归属与鉴权测试

- 已新增 `OwnershipGuard` 及 `OwnershipGuardTest`，能证明纯函数比较 owner 不同会抛 `RESOURCE_NOT_FOUND`。
- 该测试没有经过 Spring Security、SecurityContext、Authentication 或受保护 HTTP 控制器，也没有证明业务代码忽略请求中的 `userId`。计划明确要求“受保护测试控制器验证当前用户只能读取自己的模拟资源，越权读取返回 404”。
- 仍没有 403 JSON 响应测试，也没有验证 Security 401/403 的 `X-Request-Id` 响应头与 `meta.requestId` 完全一致。
- 必须修复：增加受保护的测试端点/测试配置，以真实 JWT 身份访问自有与他人资源；断言越权 404，并增加 403 统一响应与 header/body request ID 一致性测试。

#### 已修复：OpenAPI 差异

- Java 注册/登录邮箱上限均已改为 254（`RegisterRequest.java:4`、`LoginRequest.java:4`），与 OpenAPI 一致。
- `/auth/logout` 已声明 400，公开认证接口继续显式 `security: []`。
- CI 已加入 Redocly OpenAPI lint job；尚需以该命令实际通过作为最终提交证据。

#### 已满足：真实 MySQL 8.4 数据库门槛

- 采纳本轮主实施日志作为外部运行证据：MySQL 8.4 空库成功执行 Flyway V1、V2 共 2 个 migration；应用以 Hibernate `ddl-auto=validate` 成功启动；健康接口返回 `UP`。
- V2 的实体字段、索引和外键未在复审中发现新的映射矛盾。

### 本次复审命令结果

- `cd backend && mvn clean verify`：**BUILD SUCCESS**。
- 共运行 11 个测试，0 failures，0 errors，0 skipped。
- 该结果证明现有测试均通过，但不覆盖上述剩余验收缺口。

### 再次复审的必须项

1. 增加 Refresh Token 过期自动化测试。
2. 增加零/负 Access 与 Refresh TTL 导致配置验证失败的测试。
3. 增加基于真实 SecurityContext/JWT 的 owner/非 owner HTTP 鉴权测试，并证明请求参数中的用户 ID 不受信任。
4. 增加 403 统一 JSON 测试，以及 401/403 的响应头、响应体 request ID 一致性断言。
5. 增加 Refresh Token repository 的摘要唯一性、查询/撤销持久化测试；并发刷新至少补充稳定业务错误码或数据库 replacement 数量断言。
6. 实际执行 OpenAPI lint 并保留通过证据，再运行 `mvn clean verify` 后申请复审。

---

## 最终复审（2026-07-11）

### 最终结论

**通过。** Round 01 计划范围内的认证闭环、安全关键规则、统一响应契约、资源归属约定、数据库迁移及自动化测试均已有足够证据。前两次审核提出的必须修复项已全部关闭，未发现新的阻断问题。

### 剩余项关闭证据

- Refresh Token 到期边界：`TokenServiceExpirationTest.java:18-32` 使用固定 Clock，证明 `expiresAt == now` 返回 `INVALID_REFRESH_TOKEN`，与 `RefreshToken.isExpired` 的边界定义一致。
- TTL 配置：`AuthPropertiesValidationTest.java:16-28` 分别覆盖 Access/Refresh 的零值和负值，共 4 个 context 启动失败场景。
- 资源归属：`SecurityContractIntegrationTest.java:34-50` 使用真实注册得到的 JWT，经资源服务器填充 SecurityContext；自有资源成功、外部资源返回 404，并通过攻击者控制的 `userId` 查询参数证明身份只取自认证上下文。
- 401/403 契约：`SecurityContractIntegrationTest.java:52-67` 同时断言状态码、稳定错误码及 `X-Request-Id == meta.requestId`。方法级 `@PreAuthorize` 的 `AccessDeniedException` 已由 `GlobalExceptionHandler.java:47-50` 映射为统一 403，不再落入 500。
- Refresh Token repository：`RefreshTokenRepositoryTest.java:21-40` 覆盖摘要存储/查询、撤销 flush 与 token hash 唯一约束；数据库唯一冲突稳定表现为 `DataIntegrityViolationException`。
- 并发：`AuthFlowIntegrationTest.java:130-149,169-182` 使用两个同步启动的线程验证同 Token 只允许一个刷新成功，以及同邮箱只允许一个注册成功。结合 repository 悲观写锁与轮换重放测试，足以支撑本轮并发规则。
- Access Token 安全：过期、错误 issuer、篡改签名和软删除用户均有集成测试；JWT claims/issuer/HS256 校验实现保持正确。
- OpenAPI：邮箱上限、logout 400、公开接口安全声明与实现一致；CI 使用相同 Redocly 命令。
- MySQL 8.4：沿用已核验的运行证据，Flyway V1/V2 共 2 个 migration 成功，Hibernate `ddl-auto=validate` 启动成功，health 返回 `UP`。

### 最终独立验证结果

- `cd backend && mvn clean verify`：**BUILD SUCCESS**。
- 测试结果：20 tests，0 failures，0 errors，0 skipped。
- `npx --yes @redocly/cli lint docs/openapi.yaml --extends=minimal`：退出码 0，API description valid。
- Redocly 报告 19 条非阻断 warning，内容为 localhost server、缺少 tag description 和 operation summary；不影响 OpenAPI 语义有效性或 Round 01 接口契约，可在后续文档质量任务中清理。

### 非阻断建议

- Repository 撤销测试后续可通过 `EntityManager.clear()` 再查询，进一步证明不是仅观察同一持久化上下文中的托管实体。
- 并发刷新测试可进一步断言 401 的业务错误码以及 family 内 replacement 数量，增强回归诊断能力。
- 清理 Redocly 的 19 条 warning，提高公开 API 文档完整度。

Round 01 可以提交并进入下一轮规划、实施与独立审核循环。
