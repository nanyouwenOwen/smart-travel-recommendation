# Round 06：质量加固与 MVP 交付独立审核

## 最终复审结论

**FAIL（仅余 2 个最小 MVP 阻断项）**。

首轮提出的格式/静态门、生产端口重置、必填 secret、完整业务容器 smoke、备份恢复演练、性能基线、安全扫描和 release-candidate 产物链已基本落实，并取得真实 GitHub Actions 成功证据。但生产 Compose 仍未启用文档要求的 `prod` Profile，恢复脚本也没有真正保证目标数据库是新库。二者分别属于部署配置一致性和数据恢复安全边界，修复前不应把 Round 06 判为完成。

## 已核验通过的内容

### 本地质量门

- `bash scripts/check.sh`：PASS。
- OpenAPI 3.1：有效，Redocly 2.12.3 返回 37 个非阻断 warning。
- 前端：Prettier、ESLint、类型检查、生产构建通过；18 个 Vitest 文件、43 项测试通过。
- 后端：Maven verify、Spotless、SpotBugs 通过；Surefire 43 项测试，0 failure、0 error、0 skipped。
- `git diff --check` 与所有 Shell 脚本语法检查通过。
- 本机 Docker socket 无权限，因此本地未冒充容器运行证据；容器结论使用下述 GitHub Actions 真栈结果。

### GitHub Actions 真栈与候选产物

[GitHub Actions run 29163802316](https://github.com/nanyouwenOwen/smart-travel-recommendation/actions/runs/29163802316) 已直接核验：

- 提交：`63e1d2d`；整体状态 `Success`，耗时约 3 分 30 秒。
- `openapi`、`frontend`、`backend`、`e2e`、`container-smoke`、`security`、`release-candidate` 七个 job 全部成功。
- Playwright：7 项通过。
- 候选 artifact：`smart-travel-assistant-0.1.0-rc`，53.7 MB，Actions artifact digest 为 `sha256:bc611abd641aa1b7ac08a5b1129d3385b8b3f01c4af3f6301e8d0a7bafd59669`。

工作流的 `release-candidate` 在全部质量 job 成功后生成 JAR、前端 tar、OpenAPI、前后端 CycloneDX SBOM、`SHA256SUMS` 与 `GIT_SHA`，并上传同一 artifact；这满足“候选产物绑定同一提交”的实现要求，且没有越权创建 tag、GitHub Release 或推送 registry。

### 容器、业务、恢复与安全

- `docker-compose.prod.yml` 已使用 `!reset []` 清除 MySQL 宿主端口；合并配置实测只发布 frontend 5173，不发布 MySQL 或 backend。
- 数据库密码、root 密码、JWT secret、行程/咨询/实时 provider 均在生产覆盖中使用 `${VAR:?}` fail-closed。
- `scripts/compose-smoke.sh` 等待 MySQL/backend/frontend 三服务 healthy，检查前后端非 root，经同源代理完成注册、地点检索、Stub 行程到 READY、会话 SSE 到 done。
- 同一 smoke 创建唯一用户和行程，备份并恢复到 `restore_verify`，核对唯一用户、Trip 和 Flyway history；随后执行 k6 10 VU/30 秒基线、backend 重启和 MySQL stop/start 后重新登录。上述链路已由 `container-smoke` job 真实通过。
- 安全 job 使用固定 Trivy 0.58.2 对仓库文件系统执行 vulnerability/secret 的 HIGH/CRITICAL 阻断扫描；前端另有 `npm audit --audit-level=high`。对 MVP 候选而言该证据可接受，容器镜像/registry 持续扫描保留为生产增强。
- 部署、运维、备份恢复、排障和发布清单文档均已存在并互相链接；Codespaces 幂等初始化、端口转发与开发启动入口已配置。

## 剩余 MVP 阻断项

### B1. 生产 Compose 未启用 `prod` Profile

`docker-compose.prod.yml` 没有为 backend 设置 `SPRING_PROFILES_ACTIVE=prod`，合并后的环境也没有该变量。README 明确规定生产环境应启用此 Profile，而 `application-prod.yml` 承载生产数据源强制配置、forward headers、graceful shutdown 和生产日志策略。

尽管必填变量已经显著降低开发默认值风险，当前“生产覆盖”仍没有实际加载生产配置文件，和部署文档不一致。最小修复是在生产 override 的 backend environment 中加入：

```yaml
SPRING_PROFILES_ACTIVE: prod
```

并让 CI 对最终合并配置断言该值为 `prod`。

### B2. `restore.sh` 没有拒绝已存在的非活动目标库

`scripts/restore.sh` 已拒绝把活动库名作为目标，但随后执行 `CREATE DATABASE IF NOT EXISTS`，若用户传入另一个已经存在且有数据的库，脚本会继续把 dump 导入其中。它因此没有实现使用说明所称的“新数据库”，也不满足计划的“默认不覆盖现有库”。

最小修复是在导入前查询 `information_schema.schemata`：只要目标库已经存在就非零退出；随后使用不带 `IF NOT EXISTS` 的 `CREATE DATABASE`。应补一个无需破坏数据的测试，至少验证活动库和任意已存在目标库都会被拒绝。

## 非阻塞条件与增强

- 干净 GitHub Codespace 的公开 5173 远程演示仍是明确的人工验收项。当前报告没有用本地结果冒充 Codespaces，通过边界处理正确；正式对外演示前应记录提交 SHA、时间和结果。
- GitHub Actions 页面显示的 Node.js 20 Actions runtime 弃用警告来自 `actions/checkout@v4`、`setup-* @v4` 等 Action 自身，当前 runner 强制使用 Node 24，属于后续升级 Action major 的维护项，不阻断 MVP。
- OpenAPI 37 个 warning、生产资源限制、镜像 digest/Action commit 固定、registry 镜像持续扫描、Nginx 转发 request ID/CSP、备份原子写入属于生产增强，不机械阻断本次候选。
- `docs/reports/mvp-verification.md` 应把“42 项单测”校正为本次实际的 43 项，并在最终修复提交重新跑 CI 后把基线 SHA 更新为新的候选提交。

## 授权边界与最终通过条件

修复 B1、B2 后，重新运行本地 `scripts/check.sh` 与至少包含 production config assertion、restore safety test、container-smoke、security、release-candidate 的 GitHub Actions；全部成功并更新候选 SHA 后，Round 06 实现可判 PASS。

即使 Round 06 PASS，`TODO.md` 的“发布 MVP”仍须保持未勾选，直到用户明确授权并实际创建、推送 `v0.1.0` tag，创建可访问的 GitHub Release，并核验发布产物。Codespaces 人工验收不应被误写为已经完成。

