# Round 13：v0.1.0 发布恢复独立审核

## 结论

**FAIL（禁止提交或推送恢复变更）**

一次性恢复 job 的总体策略正确：写权限只授予 `main` push 上、依赖当前 `release-candidate` 的单个 job；发布附件通过固定 repository/run ID/name 跨 run 下载，固定校验 tag、peeled commit、source run 结论、七个成功 job 和失败的原 `release` job；发布脚本严格限定八项附件并覆盖首次创建、draft 恢复、完全匹配公开 Release 的幂等路径。当前实现仍缺少计划要求的两项显式运行时身份断言，因此本轮尚不能 PASS。

## 审核范围

- 计划：`docs/plans/round-13-v0.1.0-release-recovery.md`
- 实现：`.github/workflows/ci.yml` 中 `release-recovery-v0-1-0`
- 状态机：`scripts/publish-github-release.sh`、`scripts/verify-release-candidate.sh`、`scripts/test-publish-github-release.sh`
- 审计：`docs/ai-governance/AI_CHANGE_LOG.md`
- 远端只读事实：tag、source run/jobs、公开 Release API

审核终端没有修改实现、tag、Release 或任何远端状态。本报告之外，进入审核前已存在的 `docs/reviews/round-11-v0.1.0-release-review.md` 修改保持不动。

## 已通过项目

1. **触发、依赖和权限边界正确**
   - job 级 `if` 只接受 `push` 且 `refs/heads/main`；PR、tag 和其他分支不进入恢复 job。
   - `needs: [release-candidate]`；而 candidate 明确依赖 `openapi`、`frontend`、`backend`、`e2e`、`container-smoke`、`security`，因此当前恢复提交的完整质量链失败、取消或跳过时不会获得写入机会。
   - 顶层保持 `contents: read`；只有恢复 job 获得最小的 `actions: read`、`contents: write`，没有长期 token、workflow input、`pull_request_target` 或外部可控身份参数。

2. **不可变发布身份与跨 run 下载基本正确**
   - repository、tag、peeled SHA、source run ID、artifact name 均为 job 常量：`nanyouwenOwen/smart-travel-recommendation`、`v0.1.0`、`52864b1aa72f56081abfc0bd146415d2a5f1ccb8`、`29175974787`、`smart-travel-assistant-0.1.0-rc`。
   - `actions/download-artifact@v4` 同时固定 `repository`、`run-id`、`name`、`path` 和当前短期 token；没有“latest run”、无 run ID 的同名选择或当前 main artifact 下载。当前 run 的 candidate 只充当质量门禁，不会被误发布。
   - 下载目录直接交给严格候选验证脚本；该脚本只接受顶层固定八项、拒绝缺失/额外/空文件，校验固定 `GIT_SHA`、完整非自指 checksum、双 SBOM、Spring Boot JAR 和包含 `index.html` 的前端归档。

3. **tag object 与 peeled commit 区分正确**
   - job 精确 fetch `refs/tags/v0.1.0`，要求对象类型为 `tag`，再以 `^{commit}` 与固定 release SHA 比较；固定 release commit 还必须是当前恢复提交祖先。
   - 只读远端复核：tag object SHA 为 `70d8c799d6f01f0c81972f6eae3bd8d8b4d3c098`，peeled commit 为 `52864b1aa72f56081abfc0bd146415d2a5f1ccb8`，且可从 `origin/main` 到达。审核期间未移动 tag。

4. **source run 和发布状态机主体正确**
   - 公共 Actions API 当前显示 run `29175974787`：`push`、`head_branch=v0.1.0`、`head_sha=52864b1...`、overall `failure`；七个质量/candidate job 各自 `success`，原 `release` job 为 `failure`。workflow 对这些结论逐项检查且要求每个 job 唯一。
   - 发布脚本只处理固定 `v0.1.0` 与八项白名单：不存在则建 draft；已有 draft 时清理并重传；已有公开 Release 只接受标题、notes、tag、非 prerelease、八项非空附件及远端重新下载验证全部匹配。冲突时安全失败，不删除 Release、不移动 tag。
   - 公开 Release API 在审核时仍返回 `404`；这不能证明不存在认证可见 draft，计划和实现均未错误地据此跳过认证枚举。

5. **审计描述没有提前宣称成功**
   - AI log 区分已知 tag/run 事实和待执行恢复；明确不移动 tag、不勾选 TODO，并承诺发布成功后移除一次性写路径。

## 阻断项

### R13-B1：source run API 响应未显式绑定预期仓库

计划要求“通过 GitHub API 查询 run 时确认 repository”并要求固定身份逐项断言。当前 `jq` 仅检查 `.head_sha`、`.head_branch`、`.event`、`.status`、`.conclusion`，没有检查 `.repository.full_name == $RECOVERY_REPOSITORY`。

虽然 API endpoint 已使用固定仓库，这降低了误取风险，但不能替代计划规定的响应对象身份断言。请把 repository 作为 `jq` 参数传入，并显式要求响应中的完整仓库名精确匹配。修正后需用正确仓库通过、错误仓库失败的定向逻辑检查证明该约束有效。

### R13-B2：写入前未在运行脚本内逐项断言 event 名称

job 级 condition 包含 `github.event_name == 'push'`，脚本又检查了 `GITHUB_REF`，但计划明确要求开始时逐项断言 event/ref，当前脚本没有 `test "$GITHUB_EVENT_NAME" = push`。恢复代码具有 `contents: write`，应让只读身份校验自身完整、可审计，而不只依赖调度表达式。

请在任何 API/下载/发布动作之前显式断言 `GITHUB_EVENT_NAME=push`。修正后定向检查应覆盖错误 event 安全失败。

## 测试证据

- `bash -n scripts/publish-github-release.sh scripts/verify-release-candidate.sh scripts/test-publish-github-release.sh`：PASS。
- `scripts/test-publish-github-release.sh`：PASS；覆盖首次创建、draft 恢复、公开 Release 幂等，以及标题、正文、额外附件和 API 异常冲突失败。
- `git diff --check`：PASS。
- `./scripts/check.sh`：PASS；OpenAPI 有既有 warning，前端格式/lint/type/43 tests/coverage/build 与后端测试门禁通过。
- `npm exec --yes prettier@3.6.2 -- --check .github/workflows/ci.yml`：FAIL，仅报告 workflow 排版差异；这不是现有 CI 的 workflow 语义门禁，本次不单独列为安全阻断，但建议实施终端统一格式后复审。
- 远端只读 API/tag 检查：PASS；source run/job 结论、annotated tag、peeled SHA、main 可达性及公开 Release `404` 与计划记录一致。

## 复审要求

实施终端只修正上述阻断项及必要格式/定向测试；不得移动 tag、创建 Release、下载后改造为当前 run 产物或扩大权限。修正后由本 reviewer 重新审核实际 diff，至少复跑 workflow 解析/格式、`git diff --check`、shell syntax、发布状态机测试、固定身份正负向检查，并确认远端 tag 未移动且公开 Release 仍未被本地修正动作改变。最终明确 `PASS` 前禁止提交或推送恢复变更。

## 修正复审

复审结论：**PASS（允许提交并推送本次一次性恢复变更）**

实施终端只修正了初审的两项阻断，没有扩大权限、改变固定发布身份或触碰远端：

- R13-B1 已关闭：source run 的同一 fail-closed `jq` 表达式新增固定 repository 参数，并要求 `.repository.full_name == $repo`；repository、SHA、tag、event、status 和 conclusion 现在必须同时匹配。
- R13-B2 已关闭：只读身份步骤在 API、artifact 下载及发布前显式执行 `test "$GITHUB_EVENT_NAME" = push`，并继续保留 job condition 与 `GITHUB_REF=refs/heads/main` 的双层边界。

复审证据：

- `npx --yes yaml-lint@1.7.0 .github/workflows/ci.yml`：PASS。
- `git diff --check`：PASS。
- 三个相关 shell 文件 `bash -n`：PASS。
- `scripts/test-publish-github-release.sh`：PASS；首次创建、draft 恢复、公开幂等及冲突失败路径仍全部成立。
- 以真实 source run JSON 执行固定身份表达式：正确 repository/SHA/tag 通过；错误 repository、错误 SHA、错误 tag 均按预期失败。event guard 对 `push` 通过、对 `pull_request` 失败。
- 初审已完成的 `./scripts/check.sh` 保持 PASS；修正仅涉及 workflow 两条只读断言，无需重复全量项目门禁。
- workflow 的 Prettier 风格检查仍报告排版差异，但 YAML 语义解析、diff whitespace 和既有项目门禁通过；该仓库 CI 不以 workflow Prettier 为门禁，此项不阻断恢复安全性。
- 再次只读 fetch/API 核验：`v0.1.0` 仍为 annotated tag，peeled commit 仍为 `52864b1aa72f56081abfc0bd146415d2a5f1ccb8` 且可从 `origin/main` 到达；公开 Release API 仍为 `404`。本轮审核未创建、修改或发布 Release。

本 PASS 只授权按计划推送一次性 recovery 实现提交。推送后的准确 main run 必须让七个既有 job 全部成功，且 recovery job 成功并远端复验 Release；随后必须立即删除一次性 recovery job、补齐发布证据并再次由同一独立 reviewer 审核。当前不得提前勾选 `TODO.md` 的“发布 MVP”，也不得把本次本地 PASS 当作 GitHub Release 已完成。
