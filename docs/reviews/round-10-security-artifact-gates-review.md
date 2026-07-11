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

## 准确提交 CI 最终复核

**最终结论：PASS**

Reviewer 于 2026-07-12 通过 GitHub Actions REST API和公开 job 页面独立复核了实现提交 [`0f1930b513d8d0038e51eb07e5435a3c624fce7c`](https://github.com/nanyouwenOwen/smart-travel-recommendation/commit/0f1930b513d8d0038e51eb07e5435a3c624fce7c) 的 [run `29166218083`](https://github.com/nanyouwenOwen/smart-travel-recommendation/actions/runs/29166218083)：

- run 为首次执行的 `push`，`head_sha` 精确等于上述提交，状态为 `completed/success`；执行时间为 `2026-07-11T19:59:21Z` 至 `20:03:32Z`。
- `openapi`、`frontend`、`backend`、`e2e`、`container-smoke`、`security`、`release-candidate` 七个 job 均为 `completed/success`。这不是用历史绿色 run 或其他提交替代本轮证据。
- `security` job `86579661949` 中 “Build the backend release JAR from this checkout”、“Record the fixed scanner version”、“Scan the release JAR for every High or Critical vulnerability” 和 “Scan only tracked source files for secrets” 四个关键 step 均为 `completed/success`。结合该准确提交中已经审计的固定 `aquasec/trivy:0.58.2`、无 `--ignore-unfixed`、`HIGH,CRITICAL --exit-code 1` 和实际 JAR 路径，可证明当次当前 JAR 漏洞门及仅 tracked source 的 secret 门均未发现阻断结果。
- `release-candidate` job `86579941834` 的 “Verify release candidate before upload” 为 step 9、`completed/success`；上传为后续 step 10、同样 `completed/success`。因此固定白名单、准确 `GIT_SHA`、完整且不自含的 `SHA256SUMS`、双 CycloneDX SBOM 和双归档验证均在上传前执行并通过。
- 同一 run 产生未过期 artifact `smart-travel-assistant-0.1.0-rc`，artifact id `8252248407`，大小 `56,319,725` bytes，digest 为 `sha256:77ba570b13e6c8a7bb2f65f300907d1c744c9a1e41c451354fbe2f79e4506cda`；artifact 元数据内的 workflow run id 与 `head_sha` 也精确匹配本轮 run/commit。

未认证 REST job-log 下载端点返回 HTTP 403，因此 reviewer 没有声称直接下载了完整控制台日志或 artifact ZIP；最终判断来自 GitHub 返回的准确 run/job/step/artifact 状态、已审核的同提交工作流与脚本控制流，以及本轮独立正向和负向行为测试。公开 job 页面也独立显示上述关键 step 名称及成功结论。该限制不会把成功 step 误写成 artifact 内容的人工解压，但不削弱“同一 run 在上传前执行强制自校验”的 CI 证据链。

Round 09 的两项实现级阻断现已关闭，可进入后续证据同步：发布清单第二、第五项只能在记录本次准确 SHA/run/artifact 后重新勾选，并且该证据提交仍需轻量独立复核及自身 CI 全绿。此最终 PASS 仍不授权或证明 annotated tag、GitHub Release、部署或 `TODO.md` 的“发布 MVP”完成。

## 证据同步轻量复核

**结论：PASS（证据提交自身 CI 仍待推送后核验）**

实施终端依据上述最终复核同步修改了 `docs/release-checklist.md`、`docs/reports/mvp-verification.md`、`docs/ai-governance/PROJECT_HANDOFF.md` 和 `docs/ai-governance/AI_CHANGE_LOG.md`。Reviewer 对实际 diff 逐项核对如下：

- 发布清单只重新勾选第 2 项“依赖审计无未处置 High/Critical”和第 5 项“候选产物绑定同一 Git SHA”；第 6 项 tag/GitHub Release 授权仍为 `[ ]`，`TODO.md` 未出现在本次 diff 中且“发布 MVP”仍为 `[ ]`。
- 四份证据文档均准确引用实现提交 `0f1930b513d8d0038e51eb07e5435a3c624fce7c`（允许展示短 SHA `0f1930b`）及 GitHub Actions run `29166218083`，没有用 Round 08 的历史 run 替代本轮证据。
- 安全声明与实际门一致：当前 checkout 构建实际 JAR、固定 Trivy 0.58.2、`HIGH,CRITICAL --exit-code 1`、不使用 `--ignore-unfixed`，并独立扫描 tracked source secrets。文档没有虚构漏洞 allowlist、人工风险接受或运行时镜像扫描。
- 候选声明与实际门一致：`scripts/verify-release-candidate.sh` 在上传前验证固定文件集、完整 `GIT_SHA`、完整且不自含并实际复验的 `SHA256SUMS`、双 CycloneDX SBOM 和 JAR/tar 结构。Artifact 名称、大小 `56,319,725` bytes 和 digest `sha256:77ba570b13e6c8a7bb2f65f300907d1c744c9a1e41c451354fbe2f79e4506cda` 与独立 GitHub API 核验完全一致。
- `PROJECT_HANDOFF.md` 与 MVP 报告仍明确记录真实 DeepSeek/Xiaomi MiMo、全新 Codespaces、正式 tag/Release 等人工边界；“自动化前置条件完整”和“可发布”没有被写成“已经发布”。AI 日志准确记录了本轮 AI 实施、独立审核和实际验证结果，没有写入凭据或私密提示词。
- `git diff --check`：PASS；定向扫描未发现 TODO/授权越权勾选、旧缺口声明残留在当前结论中、错误 SHA/run/digest 或疑似凭据。

证据同步内容通过轻量复核，可以提交并推送普通文档。按 Round 10 计划，该证据提交自己的七个 CI job 仍须全部成功后，才能把本轮闭环视为完成；本结论不授权创建 tag、GitHub Release 或部署。
