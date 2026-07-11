# Round 09：发布就绪证据同步独立审核

## 结论

**PASS（首审修正复审；发布清单仍有两项未满足）**

第一、三、四项的现有证据足以勾选；第二项“依赖审计无未处置 High/Critical”和第五项“候选产物绑定同一 Git SHA”尚未达到本轮计划自己规定的正向证明标准。实施方已按首审要求撤销第二、五项勾选并删除过度声明，当前文档准确反映“3 项已证实、2 项待下一实现轮次、发布授权未取得”的状态。因此本次修正复审通过，但这不是发布清单全部完成或 MVP 已发布的结论。

Reviewer 只创建本报告，未修改计划或实施文件。

## 独立核验结果

### GitHub Actions 与候选产物元数据

通过 GitHub Actions REST API 独立核验：

- run `29165356764` 的 `head_sha` 为 `40167645d58fc9ee2ce1bc84f7c3c2ce96230e88`，总状态为 `completed/success`；`openapi`、`frontend`、`backend`、`e2e`、`container-smoke`、`security`、`release-candidate` 七个 job 均为 `completed/success`。artifact 名称为 `smart-travel-assistant-0.1.0-rc`，digest 为 `sha256:0d18f4cf11ff673b4869f71a85bebf899bf17594b8ddae9149cb334a8dd05b64`，未过期。
- run `29165523778` 的 `head_sha` 为 `2eb538b710c18518ee3d8763bfb0ed8adacba118`，总状态为 `completed/success`；相同七个 job 均为 `completed/success`。artifact 名称为 `smart-travel-assistant-0.1.0-rc`，digest 为 `sha256:de642becadd443090be4121d874e90c68fa8d6cf4a3f092200afc5810092fedd`，未过期。
- 当前 workflow 的 `release-candidate` 明确 `needs` 上述六个质量 job；执行步骤会构建 JAR、前端 tar、OpenAPI、CHANGELOG、前后端 CycloneDX SBOM，随后生成 `SHA256SUMS` 和 `GIT_SHA` 并上传单一 artifact。

这些事实证明准确提交的 job 成功和 artifact 元数据存在，但不单独证明已下载 artifact 内每个文件并实际执行校验。

### 清单逐项结论

1. **质量、E2E 与容器 smoke：满足。** Reviewer 独立运行 `./scripts/check.sh`，退出码为 0；两个准确 run 的 MySQL 8.4 `e2e` 与 `container-smoke` 均成功。OpenAPI lint 仍有非阻断 warning，但 gate 按当前配置通过。
2. **无未处置 High/Critical：不满足证明标准。** 前端独立执行 `npm audit --audit-level=high --omit=dev` 得到 `0 vulnerabilities`，两个 run 的 `frontend` 和 `security` 也成功；但后端 `mvn verify` 只绑定 Spotless/SpotBugs，并不是依赖漏洞审计。仓库级 Trivy 命令包含 `--ignore-unfixed`，因此成功只能证明扫描时没有被该配置阻断的 High/Critical，不能支持报告中的“没有未处置例外”或清单中的更强断言。仓库也没有列出被忽略的 unfixed High/Critical 及其处置结论。该项要么改成准确的门禁表述，要么增加不忽略这些等级的审计/显式例外记录后再勾选。
3. **备份恢复与回滚路径：满足。** `compose-smoke.sh` 对唯一用户、行程及 `flyway_schema_history` 做全新库恢复断言，并在 MySQL 重启后要求数据库用户重新登录取得非空 token；两个准确 run 的 `container-smoke` 成功。报告正确保留了本地 Docker 权限边界和“不是生产 RTO/RPO 保证”。
4. **契约、README、CHANGELOG 与限制同步：满足。** `README.md`、`CHANGELOG.md`、发布说明、MVP 报告和交接文档均把 `0.1.0` 写为候选，说明默认 Stub、真实 DeepSeek/MiMo 未验收、全新 Codespace 未人工验收、公共数据无 SLA，以及交通路由/预订/实时票价/签证边界。旧 `63e1d2d`/`29163802316` 未再出现在当前发布报告中。
5. **artifact/GIT SHA/SHA256/SBOM 证据链：不满足本轮计划的直接证据要求。** Reviewer 尝试通过准确 artifact API 下载 run `29165523778` 的 ZIP，GitHub 返回 HTTP 401；当前环境没有 `gh` 或 GitHub API 凭据。Round 08 审核只核验了 run、artifact 名称和 digest，没有记录解压后的 `GIT_SHA`、文件清单或 `sha256sum -c`。当前报告用“workflow 成功，因此同一 artifact 绑定同一提交”替代了计划明确要求的 artifact 内部核验，正是计划第 5.3 条禁止的间接推断。需要由有 Actions artifact 下载权限的终端对同一 run 解压，核对 `GIT_SHA`、所需七类文件，并运行 `sha256sum -c SHA256SUMS`，把结果写入可追溯报告后才能勾选。
6. **授权与发布：保持未完成。** 本地与远端均没有 tag，公开 Releases API 无 Release；没有发现部署或发布声明。发布清单第六项和 `TODO.md` 的“发布 MVP”均保持 `[ ]`，没有越权。

## 其他检查

- `git diff --check`：PASS。
- 本轮计划、发布、报告和治理共 8 个 Markdown 文件的本地相对链接存在性检查：PASS。
- 变更 diff 的疑似长 API key/token/password/secret 模式检查：未发现凭据。
- `TODO.md` 未被本轮修改，且“发布 MVP”未勾选；没有把 Codespaces、真实模型、tag、Release 或部署虚构为完成。
- 文档分区符合约束：工程验证位于 `docs/reports/`，AI 决策记录位于 `docs/ai-governance/`。

## 必须修正后复审

1. 撤销第二项勾选，或提供真正覆盖后端/仓库依赖且不静默忽略 High/Critical 的门禁与可审计例外记录，并相应修正 `mvp-verification.md` 的“没有未处置例外”。
2. 撤销第五项勾选，或使用有权限的 GitHub 终端下载准确 run 的 artifact，记录 artifact digest、内部 `GIT_SHA`、必需文件清单和 `sha256sum -c SHA256SUMS` 的实际结果。
3. 修正后由同一 reviewer 复审；在最终 PASS 前不得提交本轮发布清单为“前五项全部就绪”。

## 同一 Reviewer 修正复审

**复审结论：PASS（对首审修正的准确性）**

实施方选择了首审允许的保守修正路径，而不是用间接证据继续维持勾选：

1. `docs/release-checklist.md` 已将第二项和第五项恢复为 `[ ]`，并在清单下明确说明 Trivy 会忽略无修复版本漏洞、artifact 尚未在 CI 内完成内部自校验。第一、三、四项仍由本报告已核验的本地门、两个准确 run、备份恢复断言和文档一致性支持。
2. `docs/reports/mvp-verification.md` 不再声称“没有未处置例外”，而是准确区分前端 audit、普通 Maven verify 与带 `--ignore-unfixed` 的 Trivy 门；也不再从成功 workflow 直接推导 artifact 内部绑定已验证，明确记录下载 HTTP 401、未解压及需要新增 CI 自校验。
3. `PROJECT_HANDOFF.md` 明确撤回“自动化发布证据完整”的结论，列出相同两项缺口；`AI_CHANGE_LOG.md` 如实记录首审 `FAIL`、修正选择和仍待后续实现的证据，没有把文档修正写成安全门或 artifact 验证已经完成。
4. `TODO.md` 未被修改，“发布 MVP”仍为 `[ ]`；发布清单授权项也仍为 `[ ]`。没有 tag、GitHub Release、Codespaces 人工验收或真实模型验收的虚构声明。
5. 复审执行 `git diff --check`：PASS；关键声明与勾选状态定向扫描：PASS。

本轮文档可以作为准确的缺口快照交付，但没有满足原计划“前五项全部勾选”的发布完成定义。下一轮必须实现不静默忽略 High/Critical 的可审计安全门，以及在上传前验证必需文件、准确 `GIT_SHA` 和 `SHA256SUMS` 的候选产物自校验；完成后仍需独立审核和准确提交 CI。第六项与 `TODO.md` 只能在用户明确授权并实际创建 tag/Release 后勾选。
