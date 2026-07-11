# Round 09：发布就绪证据同步计划

## 目标与边界

在不改变产品实现、不创建 tag/GitHub Release、也不冒充人工验收的前提下，把当前发布候选的真实证据同步到发布清单和交付文档，使 `docs/release-checklist.md` 的自动化前置项能够逐项追溯到准确提交与 CI run。

本轮基线为当前 `main` HEAD `2eb538b710c18518ee3d8763bfb0ed8adacba118`。最后一个实现变更提交是 `40167645d58fc9ee2ce1bc84f7c3c2ce96230e88`：对应 GitHub Actions run `29165356764` 的七个 job 全部成功，Round 08 独立审核最终为 `PASS`；随后证据同步提交 `2eb538b` 对应 run `29165523778`，七个 job 也全部成功。实施者必须重新核验这些事实，不得仅复制本计划中的已知值。

本轮只做发布证据和文档一致性维护：

- 不修改业务代码、测试语义、CI 门槛或发布产物构建方式。
- 不勾选 `TODO.md` 的“发布 MVP”；它只有在用户明确授权、`v0.1.0` tag 与 GitHub Release 实际创建并核验后才能完成。
- 不勾选发布清单中的授权项，不创建或推送 tag，不创建 GitHub Release，不上传 Release 附件，不部署线上环境。
- 不把仓库中已有 Codespaces 配置写成“干净 Codespace 人工验收已通过”，也不把 Stub 测试写成 DeepSeek/Xiaomi MiMo 真实密钥或真实供应商验收。
- 不下载或提交潜在敏感运行日志、凭据、Token、备份数据或私密提示词。

## 事实来源与验收映射

实施前逐项对照以下权威来源；证据不足时保留未勾选并明确原因，不能以“未发现问题”替代正向证明。

| 发布清单项 | 必须核验的证据 | 允许勾选的条件 |
| --- | --- | --- |
| `scripts/check.sh`、MySQL 8.4 E2E 和容器 smoke 全绿 | `scripts/check.sh` 本轮本地结果；run `29165356764` 与 `29165523778` 的提交关联、总状态及 `openapi`、`frontend`、`backend`、`e2e`、`container-smoke`、`security`、`release-candidate` 七 job 状态 | 本地门通过，两个指定 run 均属于准确提交且七 job 全绿，尤其 E2E 使用 MySQL 8.4、容器 smoke 成功 |
| 依赖审计无未处置 High/Critical | `.github/workflows/ci.yml` 中前端 `npm audit --audit-level=high`、后端 Maven verify、固定版本 Trivy `HIGH,CRITICAL --exit-code 1`；上述准确提交的 `frontend`、`backend`、`security` job 成功 | 阻断门确实覆盖候选仓库且成功；若发现 High/Critical 例外，必须先形成含 CVE、影响、补偿措施与到期日的处置记录，不能直接勾选 |
| 备份恢复演练与回滚路径已核验 | `scripts/compose-smoke.sh` 的备份/全新库恢复断言、`docs/backup-restore.md` 和恢复脚本；run `29165356764`/`29165523778` 的 `container-smoke` 成功 | 演练和文档化回滚路径均可追溯且无被弱化；报告不得把 CI 演练写成生产 RTO/RPO 保证 |
| OpenAPI、README、CHANGELOG、已知限制同步 | `docs/openapi.yaml`、`README.md`、`CHANGELOG.md`、`docs/releases/v0.1.0.md`、`docs/reports/mvp-verification.md` 逐项交叉检查 | 版本、功能范围、默认 Stub、真实模型/Codespaces/正式发布边界和已知限制一致，OpenAPI lint job 成功 |
| JAR、前端静态包、SBOM 与校验和绑定同一 Git SHA | `.github/workflows/ci.yml` 的 `release-candidate` 内容与依赖关系；指定 run 的 artifact 名称和 Actions artifact digest；artifact 内 `GIT_SHA`、`SHA256SUMS`、JAR、前端 tar、OpenAPI、CHANGELOG、前后端 SBOM | `release-candidate` 依赖六个质量 job 并成功；能证明 artifact 内文件和校验清单绑定到同一准确 SHA。若当前环境无法直接检查 artifact 内容，则必须引用已独立核验的同一 run 证据，不能臆造文件摘要 |
| 用户授权创建 tag/Release | 用户明确书面授权及随后实际发布结果 | 本轮始终不满足，保持未勾选 |

## 实施步骤

### 1. 建立当前状态快照

1. 确认工作树没有被本轮覆盖的用户改动，记录完整 HEAD、最近实现提交、远端 `main` 状态和 `TODO.md`/发布清单现状。
2. 打开 Round 08 审核、当前 CI workflow、发布产物构建步骤和现有验证报告，确认没有用旧 run 替代当前候选。
3. 通过 GitHub Actions 页面或 API 核验 run `29165356764` 对应 `4016764`、run `29165523778` 对应 `2eb538b`，并记录七个 job 的实际 conclusion。核验 artifact `smart-travel-assistant-0.1.0-rc` 的准确 digest；不得从不同 run 拼接 SHA、job 或 digest。

### 2. 同步工程发布证据

1. 更新 `docs/reports/mvp-verification.md`，移除 `63e1d2d` / `29163802316` 作为“当前候选”的过期表述，至少分别说明：
   - `4016764` 是最后实现变更及其 run `29165356764`；
   - `2eb538b` 是随后证据同步 HEAD 及其 run `29165523778`；
   - 当前 artifact 名称、准确 digest、七 job 结果和 Round 08 `PASS`；
   - Codespaces、真实 DeepSeek/MiMo 与正式发布仍是未完成的人工作业。
2. 更新 `docs/reports/backup-restore-verification.md`，用上述当前成功 run 替换旧 run，准确描述容器 smoke 中的 MySQL 备份、gzip/校验、全新目标库恢复及恢复后断言；保留“不是生产 RTO/RPO 保证”和本地 Docker 能力边界。
3. 交叉核对 `README.md`、`CHANGELOG.md` 与 `docs/releases/v0.1.0.md`：版本、已实现能力、默认 Stub、公共实时数据 SLA、交通/预订/票价/签证范围、真实模型适配约束、Codespaces 人工验收、tag/Release 未授权等事实必须一致。只做必要的事实修正，不能扩大 MVP 声明。
4. 将本轮材料决策、改动范围、验证与人工边界追加到 `docs/ai-governance/AI_CHANGE_LOG.md`；如交接入口仍把 `2eb538b` 的 CI 写成待验证，则同步 `PROJECT_HANDOFF.md`。工程报告放在 `docs/reports/`，AI 过程记录只放在 `docs/ai-governance/`。

### 3. 更新发布清单但保留发布边界

1. 仅当“事实来源与验收映射”的证据逐项成立后，将 `docs/release-checklist.md` 前五项从 `[ ]` 更新为 `[x]`；在清单附近记录准确 SHA/run 或链接到承载这些值的验证报告，避免勾选成为无来源断言。
2. 第六项“用户明确授权创建 `v0.1.0` tag 和 GitHub Release”保持 `[ ]`。
3. `TODO.md` 的“发布 MVP”保持 `[ ]`。本轮结论只能是“发布前自动化证据就绪，等待授权”，不能是“已发布 MVP”。

### 4. 本地验证

至少执行并记录实际结果：

1. `git diff --check`。
2. Markdown 本地链接检查，覆盖本轮修改的发布、报告和治理文档。
3. `scripts/check.sh`，验证 OpenAPI、前后端格式/静态检查、类型、测试、覆盖率和构建；若因明确环境限制无法完成，不能勾第一项，必须记录缺失证据并继续修正或等待可执行环境。
4. 检查发布文档中不存在仍作为当前事实的 `63e1d2d` / `29163802316`，也不存在把 Codespaces、真实模型、tag、Release 或部署写成已经完成的语句。
5. 检查仓库改动中没有疑似 API key、Token、密码、备份内容或其他凭据。

### 5. 独立审核与修正复审

由未参与实施的独立 reviewer 创建 `docs/reviews/round-09-release-readiness-evidence-review.md`，且不编辑实施文件。审核必须逐项：

1. 对照本计划、实际 diff、`TODO.md` 和发布清单，确认没有越权发布或过早勾选。
2. 独立核验两个指定 run 的准确 commit、七 job 状态、artifact 名称与 digest，重点确认 `security` 成功可证明当前固定 High/Critical 阻断门运行，`release-candidate` 成功且依赖其余质量 job。
3. 核验 artifact/GIT SHA/SHA256/SBOM 的证据链，不能仅因 workflow 文件“看起来会生成”就宣告现有 artifact 内容已验证。
4. 核验 MVP、备份、README、CHANGELOG、发布说明、AI 交接材料互相一致，旧 SHA/run 不再被当作当前证据。
5. 运行比例相称的本地门并给出明确 `PASS` 或 `FAIL`；发现阻断后由主实施终端修正，再由同一 reviewer 复审，直至没有阻断项。

### 6. 交付与当前提交 CI

1. 只有在独立审核 `PASS` 后，主实施终端才可提交并推送本轮普通文档变更；提交前再次确认没有 tag 或 Release 操作。
2. 等待该准确文档提交触发的 GitHub Actions，核验七个 job 全绿。不得以 `2eb538b` 的历史绿色 run 替代新提交 CI。
3. 若新提交任一 required/dependent job 失败，回到实施—同一 reviewer 复审循环；不因为“只是文档”而忽略失败。
4. CI 全绿后，把准确提交 SHA、run ID、七 job 与 artifact digest 补录到交接/变更记录，并按流程对这次证据补录进行轻量独立复核和 CI 验证。
5. 最终只向用户报告前五项发布前置条件已满足，并请求是否明确授权创建 annotated tag `v0.1.0`、GitHub Release 和附加候选产物。在取得明确授权并实际完成发布之前，`TODO.md` 和授权清单项不得勾选。

## 完成定义

本轮只有同时满足以下条件才算完成：

1. 发布清单前五项均有准确提交、run、job、审核或 artifact 证据支持并已勾选；授权项保持未勾选。
2. `mvp-verification.md` 与 `backup-restore-verification.md` 不再把旧 `63e1d2d` / `29163802316` 表述为当前候选，且当前候选与证据同步 HEAD 的角色区分清楚。
3. README、CHANGELOG、发布说明、验证报告、已知限制与 AI 交接一致，没有虚构 Codespaces、真实供应商或正式发布验收。
4. 本地门通过、独立审核最终 `PASS`，本轮准确提交的 GitHub Actions 七个 job 全绿，产物 digest/同 SHA 证据可追溯。
5. 没有创建 tag、GitHub Release 或部署，没有提交秘密；`TODO.md` 的“发布 MVP”仍保持未完成，等待用户明确授权。
