# Round 06：质量加固与 MVP 交付独立审核

## 审核结论

**FAIL（MVP 候选尚不可交付）**。

代码级质量门已经通过，格式化、静态检查、前后端测试和生产构建本身没有阻断；但生产 Compose 仍会暴露数据库并继承开发密钥，恢复脚本默认指向活动库，性能/恢复验证和候选产物流水线也未达到本轮计划中对“已完成”的最低证据要求。`TODO.md` 第 7 节相关事项保持未勾选是正确的；在以下 MVP 阻断项修正并复审前，不应把 Round 06 或 0.1.0 发布候选声明为完成。

## 本次实际验证

审核环境：Java 21.0.11、Node 24、Docker CLI/Compose 可用但当前用户无 `/var/run/docker.sock` 权限。

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| `bash scripts/check.sh` | PASS | Redocly 2.12.3 判定 OpenAPI 有效（37 warnings）；Prettier、ESLint、Vue TypeScript 均通过；Vitest 18 个文件、43 项测试通过；Vite build 通过；Maven verify、Spotless、SpotBugs 通过 |
| 后端测试汇总 | PASS | Surefire 共 43 项，0 failure、0 error、0 skipped |
| `git diff --check` | PASS | 无空白错误 |
| `bash -n scripts/*.sh .githooks/pre-commit .devcontainer/post-create.sh` | PASS | Shell 语法有效 |
| Docker 真栈 | **未执行（CI 条件）** | `docker info` 返回 socket permission denied；不得用静态审查冒充运行通过 |

质量入口和 Hook 的基本链路成立：`scripts/check.sh` 固定 Redocly 版本并串联前后端检查，`.githooks/pre-commit` 调用 quick 门，`scripts/install-hooks.sh` 只修改仓库内 `core.hooksPath`；CI 也包含前端格式/lint 和绑定 Spotless/SpotBugs 的后端 verify。

## MVP 阻断项

### B1. 生产 Compose 没有做到数据库不暴露和配置 fail-closed

- `docker-compose.yml:10` 发布 MySQL `3306`；`docker-compose.prod.yml:2-3` 使用普通 `ports: []`，Compose 合并语义不会可靠重置基础文件的端口列表，合并后的生产配置仍可能发布 3306。
- `docker-compose.prod.yml` 没有设置 `SPRING_PROFILES_ACTIVE=prod`，也没有覆盖基础文件 `docker-compose.yml:21-27` 的开发数据库口令、固定本地 JWT secret 和 Stub 默认值。按当前部署文档启动时，生产 Profile 不会启用，且缺少 secret 时不会失败。
- `.github/workflows/ci.yml:91-96` 只验证基础 Compose，未展开并断言生产合并配置，因此不能发现上述问题。

这是安全与部署阻断。生产覆盖应明确重置 MySQL/后端宿主端口，启用 `prod` Profile，对数据库和 JWT 等必需 secret 使用 `${VAR:?message}`，并在 CI 中检查最终合并配置不含开发默认值且仅发布前端端口。

### B2. 恢复工具默认可能覆盖活动数据库

`scripts/restore.sh:3-6` 虽要求确认和校验和，但直接向默认 Compose 服务的 `$MYSQL_DATABASE` 导入；它没有要求显式目标 project/目标库，也没有验证目标为空或是全新实例。`docs/backup-restore.md` 推荐该命令会把用户引向当前活动库。这违反计划“要求显式目标与确认，默认不覆盖现有库”的数据安全边界。

恢复命令必须默认拒绝已有目标，显式指定隔离 project/新数据库，并在导入前做目标存在性、空库和连接预检；覆盖恢复应是单独且更强确认的操作。

### B3. 容器、备份恢复和业务 smoke 仍没有可引用的 Round 06 通过证据

- `docs/reports/backup-restore-verification.md:3` 明确写明实际容器验证尚待候选 CI；`docs/reports/mvp-verification.md:6` 同样写明 Round 06 Compose 门待 CI。因此这些报告当前是测试计划，不是通过记录。
- `scripts/compose-smoke.sh:9-21` 只验证首页健康、健康 API、注册、Flyway 表存在和 backend 重启。它没有按计划经过同源代理生成 Stub 行程或咨询/SSE，也没有核对备份恢复后的唯一用户/行程、记录数量和 schema 版本一致，更没有制造 MySQL 中断后验证数据保持。
- 脚本只等待 frontend healthy，未显式确认三个服务均 healthy；随机 project 仍固定占用宿主 5173，CI 并行或已有服务时可能冲突。

必须补全 smoke 的最小业务链路与恢复数据断言，并由 `container-smoke` 在候选提交上真实成功后填写 run URL、提交 SHA、时间及结果。由于当前环境没有 Docker 权限，这一项允许依赖 CI，但不能省略 CI 证据。

### B4. “性能、限流、安全和故障恢复测试完成”证据不足

- `tests/performance/smoke.js:1-3` 只压测健康接口 30 秒；没有计划要求的登录后行程列表/详情、实时地点和低并发 Stub 咨询，也没有创建隔离测试数据。它只能证明健康端点基线，不能支撑 TODO 中“性能完成”。
- `scripts/recovery-smoke.sh:3-6` 只重启 backend；没有短暂 MySQL 不可用、供应商不可达、健康先失败后恢复、已有数据不丢失和日志不泄密等故障演练。
- `.github/workflows/ci.yml` 没有 performance/recovery/security job，也没有将这些报告保存为 artifact。`docs/reports/security-performance.md` 主要是对已有单元/集成测试的概述，未记录 k6 实际结果、运行环境、提交 SHA 或恢复演练结果。
- CI 有 `npm audit --audit-level=high`，但没有 Maven 依赖漏洞门或容器镜像扫描；报告只把镜像扫描列为生产前建议，未对候选镜像 High/Critical 风险给出扫描证据或例外清单。

不要求把所有生产级混沌测试都变成普通 PR 阻断，但 MVP 候选至少需要：扩展可重复性能场景、完整恢复 smoke、候选提交成功运行一次并归档结果，以及依赖/镜像 High/Critical 扫描结论。慢任务可以仅在 main、手动触发或发布候选工作流运行。

### B5. 0.1.0 候选产物尚未形成可追溯交付链

- `.github/workflows/ci.yml` 没有 `workflow_dispatch`/版本 tag 的 release-candidate job，也没有构建并上传 JAR、前端静态包、OpenAPI、SBOM 和 SHA256 文件。
- `docs/release-checklist.md:3-8` 的全部发布项仍未完成；`docs/reports/mvp-verification.md:4` 的基线提交仍是占位文本。
- `docs/releases/v0.1.0.md` 只有简短能力说明，没有候选提交、产物校验、迁移/回滚和实际验证证据。

因此版本号统一为 0.1.0 只是准备完成，尚不能称为“发布候选产物就绪”。需要增加只上传 Actions artifact、不推 registry/不发布 Release 的候选工作流，并让报告和校验文件绑定同一 Git SHA。

## 需 CI 或人工环境确认的条件项

以下不是要求当前审核机强行完成，但必须如实保留为条件：

1. Docker build、三服务健康、同源 API/SSE、非 root、read-only、backend/MySQL 重启和全新库恢复，需要修正上述脚本后由 GitHub Actions 真栈运行成功。
2. 干净 GitHub Codespace 的公开 5173 远程演示当前无法在本机创建。`docs/reports/mvp-verification.md:7` 如实标为人工验收，处理正确；正式候选前应记录 Codespace 提交 SHA、时间、公开前端演示结果，API 继续保持 private。
3. tag、GitHub Release、registry 推送和端口可见性变更均未获用户明确授权。当前不创建这些外部状态是正确的；即使所有候选门通过，`TODO.md` 的“发布 MVP”也只能等用户授权并实际发布后勾选。

## 非阻塞生产增强

这些改进有价值，但不单独阻断 MVP：

- 为生产 Compose 增加 CPU/内存限制，并把基础镜像/Actions 从 tag 或 major 进一步固定到 digest/commit SHA。
- Nginx 补传 `X-Forwarded-For`、`X-Request-Id`；CSP 可在验证 Leaflet 与前端资源策略后加入，HSTS 应由真实 TLS 入口设置。
- `backup.sh` 采用临时文件后原子改名，失败时不留下部分备份；增加磁盘空间和数据库连通性预检。
- 为 OpenAPI 当前 37 个 warning 建立已知基线，避免后续 warning 无意增长；这些 warning 不影响本次语义有效结论。

## 复审通过条件

1. 修复 B1、B2，并新增能自动断言生产合并配置与安全恢复目标的检查。
2. 补全 Compose/备份恢复/业务 smoke，候选提交的 CI 真栈运行成功且报告记录可追溯证据。
3. 扩展性能与恢复场景，增加候选依赖/镜像扫描，并至少成功执行一次、保存报告。
4. 增加 release-candidate 产物工作流，生成 JAR、前端静态包、OpenAPI、前后端 SBOM 与 SHA256，全部绑定同一提交 SHA。
5. 本地 `scripts/check.sh` 继续通过；干净 Codespace 保持明确待人工验收，直到真实执行。

