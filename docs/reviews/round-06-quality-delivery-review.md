# Round 06：质量加固与 MVP 交付独立审核

## 最终结论

**PASS（实现与 0.1.0 发布候选层面）**。

首轮和第二轮审核发现的生产配置、恢复安全、容器业务链路、性能/恢复证据、安全扫描与候选产物问题均已修复。Round 06 的实现完成条件已经满足，`TODO.md` 第 7 节除“发布 MVP”以外的项目可以保持已勾选。

正式发布不属于本次实现 PASS：在用户明确授权并实际创建 `v0.1.0` tag 与 GitHub Release 前，“发布 MVP”必须继续保持未勾选。

## 核验结果

### 代码质量与测试

- OpenAPI 3.1 语义有效；Redocly 2.12.3 的 37 个 warning 为已知非阻断项。
- 前端 Prettier、ESLint、TypeScript、Vitest 覆盖率及生产构建通过；18 个测试文件、43 项测试通过。
- 后端 Maven verify、Spotless 与 SpotBugs 通过；Surefire 43 项测试，0 failure、0 error、0 skipped。
- 仓库级 `scripts/check.sh`、提交前 Hook、Shell 语法检查及 `git diff --check` 均通过。

### 生产 Compose 与恢复边界

- `docker-compose.prod.yml` 使用 `ports: !reset []` 清除 MySQL 宿主端口。
- 实际展开 production merged config 已核验：仅发布 frontend 5173，不发布 MySQL 或 backend；backend 包含 `SPRING_PROFILES_ACTIVE=prod`。
- 数据库、root 密码、JWT secret、行程/咨询/实时 provider 均为 `${VAR:?}` 必填配置，不会静默继承开发默认值。
- `scripts/restore.sh` 要求显式目标与确认，拒绝活动库；导入前查询 `INFORMATION_SCHEMA.SCHEMATA`，任何已存在目标库都会非零退出；之后使用无 `IF NOT EXISTS` 的 `CREATE DATABASE` 创建全新目标。

### 容器、业务、性能与故障恢复

- Compose smoke 等待 MySQL、backend、frontend 三服务 healthy，并验证前后端容器非 root。
- 经前端同源代理完成注册、地点检索、Stub 行程到 READY、会话创建和 SSE 到 done。
- 创建唯一用户和行程后执行一致性备份，恢复至新库并核对唯一用户、Trip 与 Flyway history。
- k6 固定版本执行 10 VU/30 秒演示环境基线；backend 重启和 MySQL stop/start 后均验证恢复与已有用户重新登录。
- 后端集成测试继续覆盖限流、鉴权、AI 故障、SSE 重放/取消、缓存降级和任务恢复。

### 安全与候选产物

- CI 执行前端高危依赖审计，并使用固定 Trivy 0.58.2 对文件系统执行 vulnerability/secret 的 HIGH/CRITICAL 阻断扫描。
- `release-candidate` 依赖所有质量 job，生成后端 JAR、前端静态 tar、OpenAPI、前后端 CycloneDX SBOM、SHA256 清单和 `GIT_SHA`，作为单一 Actions artifact 上传。
- 流水线没有推送 registry、创建 tag 或 GitHub Release，符合用户授权边界。

## GitHub Actions 证据

[GitHub Actions run 29164101865](https://github.com/nanyouwenOwen/smart-travel-recommendation/actions/runs/29164101865) 已直接核验：

- 候选提交：`b978503`，包含此前边界修复提交 `21c335a`。
- 整体状态：`Success`，总耗时约 3 分 17 秒。
- `openapi`、`frontend`、`backend`、`e2e`、`container-smoke`、`security`、`release-candidate` 七个 job 全部成功。
- 候选 artifact：`smart-travel-assistant-0.1.0-rc`，53.7 MB；Actions artifact digest 为 `sha256:c64e81a442cc716cae8008d69c77c8ff2f8e4d36ff899b029bf889e0644c1640`。
- Playwright 汇总为 6 项直接通过、1 项首次等待“行程天气”超时后重试通过。由于 job 最终成功且同一核心流程此前已有成功真栈证据，此项不阻断候选，但应继续观察并减少 UI 时序抖动。

## 剩余非阻塞条件

1. 干净 GitHub Codespace 的公开 5173 远程演示仍需人工执行并记录提交、时间和结果；当前文档如实保留该条件，没有用本地结果冒充远程验收。
2. `docs/reports/mvp-verification.md` 和 `docs/reports/backup-restore-verification.md` 当前仍引用上一候选 `63e1d2d` / run `29163802316`。建议在交付提交中更新为 `b978503` / run `29164101865` 及新 artifact digest；这是证据同步事项，不推翻已直接核验的最新 CI 结果。
3. GitHub Actions 页面提示部分 `actions/*@v4` 自身使用 Node.js 20 runtime，当前 runner 强制使用 Node 24。后续升级 Action major 即可，不阻断 MVP。
4. OpenAPI warning 基线、生产资源限制、镜像 digest/Action commit 固定、registry 镜像持续扫描、Nginx CSP/request ID 与备份原子写入属于生产增强。

## 发布授权边界

Round 06 和 0.1.0 候选已经通过独立审核，但项目尚未正式发布。只有用户明确授权并完成以下外部动作后，才能勾选 `TODO.md` 的“发布 MVP”并宣布正式发布：

1. 创建并推送 `v0.1.0` annotated tag；
2. 创建可访问的 GitHub Release；
3. 核验 Release 绑定正确提交，且发布产物校验和与候选一致。

