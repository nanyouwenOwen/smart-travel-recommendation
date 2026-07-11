# Round 10：安全与候选产物门禁独立审核

## 结论

**PASS（实现与本地门；准确提交的 CI 正向证据仍待推送后核验）**

本轮实现直接关闭了 Round 09 的两项实现级阻断：`security` job 现在从当前 checkout 构建实际发布 JAR，并使用固定 Trivy `0.58.2` 对该 JAR执行不带 `--ignore-unfixed` 的 High/Critical 阻断扫描；候选产物在上传前由共用脚本完成固定白名单、提交绑定、校验和、双 SBOM 和双归档验证。没有发现降低安全等级、宽泛忽略、以文档声明替代门禁或 Bash/YAML 控制流阻断。

此 PASS 只授权实施终端进入普通提交、推送和 CI 核验阶段，不代表 Trivy 已在本机实际完成扫描，也不代表 Round 10 的准确提交 CI、发布清单勾选、tag、GitHub Release 或部署已经完成。Reviewer 只创建本报告，未修改实现文件。

## 对照计划与 Round 09 阻断项

### 当前发布 JAR 安全门

- `.github/workflows/ci.yml` 的 `security` job 先设置 Temurin Java 21，再运行 `mvn --batch-mode -f backend/pom.xml -DskipTests package`，并对固定路径 `backend/target/travel-assistant-api-0.1.0.jar` 执行非空断言。因此扫描目标来自本次 checkout，而不是历史 artifact 或其他 job 的未绑定产物。
- Trivy CLI 镜像固定为 `aquasec/trivy:0.58.2`；工作流先输出版本，实际漏洞扫描使用 `fs --scanners vuln --severity HIGH,CRITICAL --exit-code 1`。命令没有 `--ignore-unfixed`、allowlist、`--skip-db-update` 或降级退出码，任一被识别的 High/Critical 都会使 step 非零退出。
- 漏洞扫描只挂载并扫描本次构建 JAR，不用整个仓库缓存或 `target` 报告替代发布产物扫描。每次干净 GitHub-hosted runner 由 Trivy 正常更新数据库；未配置跨 run 漏洞库缓存或跳过更新。数据库元数据与实际零漏洞结论必须由后续准确提交的 job 日志确认。
- secret 扫描是独立 step：先用 `git archive HEAD` 解出受版本控制的内容，再对该临时目录运行固定 Trivy 的 `--scanners secret --exit-code 1`。仓库没有 `.gitattributes` 的 `export-ignore`，当前 `git archive HEAD` 与 `git ls-files` 的路径差集为空；因此不会把 `backend/target`、`frontend/node_modules`、`frontend/dist` 等未跟踪生成物混入，也没有通过排除规则漏掉受版本控制源码、配置、脚本或文档。
- 前端 `npm audit --audit-level=high` 保持在 `frontend` job，未被描述或实现成运行时镜像扫描。

本机当前用户无 Docker socket 权限，因此 reviewer 没有用跳过数据库更新、旧数据库或其他扫描器伪造 Trivy 正向结果。固定镜像能否拉取、数据库元数据、实际 JAR 扫描目标和 High/Critical 为零，必须在推送后的同一提交 CI 中复核；该环境限制按计划不单独构成实现 FAIL。

### 候选产物上传前自校验

- workflow 先写 `GIT_SHA`，再按显式七项 payload 数组生成 `SHA256SUMS`；清单包含 `GIT_SHA`，不包含自身，也没有无约束 glob。
- `scripts/verify-release-candidate.sh` 启用 `set -euo pipefail`，要求 40 位小写十六进制期望 SHA，并拒绝候选目录中任何缺失、空白或额外的普通文件。固定集合为七项 payload 加 `SHA256SUMS`。
- 脚本去除 `GIT_SHA` 的 CR/LF 后与期望 SHA 严格比较；清单文件名集合与条目数都必须精确等于 payload，随后在候选目录内运行 `sha256sum -c SHA256SUMS`。自包含、重复、缺失、额外或被篡改内容均不能通过。
- 两个 SBOM 都用 `jq -e` 验证有效 JSON、`bomFormat == "CycloneDX"`、非空 `specVersion` 和数组类型 `components`。后端 JAR 必须可由 `jar tf` 读取且存在 Spring Boot `BOOT-INF/` 结构；前端 tar 必须可由 `tar -tzf` 读取且存在 `index.html`。
- 验证 step 位于最终 `actions/upload-artifact@v4` 之前，且 `release-candidate.needs` 仍精确包含 `openapi`、`frontend`、`backend`、`e2e`、`container-smoke`、`security` 六个质量 job。
- 普通 job 日志只输出固定文件名、字节数、公开 commit SHA、SBOM 格式/版本/组件数、归档条目数和 PASS；step summary 输出相同性质的安全元数据及清单验证结论。没有输出文件内容、SBOM 依赖明细、归档列表、环境变量或凭据。summary 没有逐项重复文件大小，但同一步的普通日志提供完整固定名称与大小，二者共同满足无 artifact 下载权限 reviewer 的审计需要。

## 独立行为测试

Reviewer 用当前构建的后端 JAR、前端 `dist`、重新生成的前端 CycloneDX SBOM、后端 CycloneDX SBOM、OpenAPI 和 CHANGELOG 构造临时候选；所有测试副本位于临时目录，未修改真实产物。

正向用例结果：

- `scripts/verify-release-candidate.sh <release> "$(git rev-parse HEAD)"`：PASS。
- 固定 payload 为 7 项；后端 JAR 446 个条目，前端归档 22 个条目。
- 后端 SBOM 为 CycloneDX 1.6、104 components；前端 SBOM 为 CycloneDX 1.5、396 components。
- 设置临时 `GITHUB_STEP_SUMMARY` 后成功生成脱敏 PASS 摘要。

独立负向用例均得到预期的非零退出：

1. 删除必需文件；
2. 置空必需文件；
3. 将 `GIT_SHA` 改成另一合法格式 SHA；
4. 在不更新清单时篡改 payload；
5. 向清单加入 `SHA256SUMS` 自身；
6. 将 SBOM 改成有效 JSON 但错误的 `bomFormat`，并重新计算其 checksum；
7. 将 JAR 损坏并重新计算其 checksum；
8. 将前端 tar 损坏并重新计算其 checksum；
9. 添加额外文件。

第 6—8 项特意重算 checksum，证明结构门不是只依赖 SHA；第 3 项证明格式合法但提交不匹配仍会失败。

## 其他验证

- `bash -n scripts/verify-release-candidate.sh`：PASS。
- `git diff --check`：PASS。
- `./scripts/check.sh`：PASS；OpenAPI 仍有既有非阻断 warning，前端格式、lint、类型、43 项测试与覆盖率、构建，以及后端测试/格式/SpotBugs/构建门均通过。
- workflow 静态结构检查：PASS。当前环境没有预装 `actionlint`/YAML 解析 CLI，未为此临时引入依赖；多行 `run`、折叠命令、数组作用域、`needs` 和 verify-before-upload 顺序未发现语法或控制流问题。GitHub Actions 的实际 YAML 解析仍由推送触发验证。
- 本轮 diff 的常见 GitHub/OpenAI/AWS 长凭据模式扫描：未发现凭据；候选内容和构建产物未被加入 worktree diff。

## 推送后必须复核的证据

本地 PASS 后仍必须由同一 reviewer 核对准确提交的 CI，以下任一缺失或失败都应把本轮结论改为 FAIL 并回到修正—复审：

1. 七个 job 全部 `success`，且 run 的 `head_sha` 精确等于本轮实现提交。
2. `security` 日志显示 Trivy `0.58.2`、当次数据库更新/元数据、扫描目标为当前 JAR，并在无 `--ignore-unfixed` 的配置下 High/Critical 为零；secret step 同样成功。
3. `release-candidate` 日志显示固定八个文件元数据、准确 `GIT_SHA`、七项 checksum 全部 `OK`、双 CycloneDX 与双归档验证 PASS；step summary 安全且 upload action 随后成功并给出 artifact digest。
4. 只有上述证据成立后，才能在后续证据提交中重新勾选发布清单第二、第五项。该证据提交还需轻量独立复核和自身 CI 全绿。

`TODO.md` 的“发布 MVP”、tag、GitHub Release、部署、真实 DeepSeek/MiMo 验收和全新 Codespace 人工验收不属于本次 PASS；仍必须遵守各自授权和证据条件。
