# Round 13：v0.1.0 一次性发布恢复计划

## 背景与不可变边界

用户已授权创建并推送 annotated tag `v0.1.0`、创建 GitHub Release 并附加候选产物。远端 tag 已不可变地指向发布提交 `52864b1aa72f56081abfc0bd146415d2a5f1ccb8`；其 tag workflow run `29175974787` 中六个质量 job 和 `release-candidate` 已成功，`release` job 在只读步骤 “Verify annotated tag and downloaded candidate” 约 5 秒后失败，发布脚本未执行。公开 Release API 当前返回 `404`，但因没有认证日志和 draft 对公开 API 不可见，不能据此断言远端绝对不存在 draft。

本地已确认 `v0.1.0` 是 annotated tag、剥离目标为上述提交且该提交可从 `main` 到达。失败发生在只读校验中，最可能但尚未被日志证明的原因，是 annotated-tag push 的 `GITHUB_SHA` 可能表示 tag object 而校验把它当成 peeled commit，或下载目录布局与假设不一致。本轮不得把该推测写成已证实根因；恢复实现必须同时消除这两类歧义。

以下对象构成本轮不可修改的恢复身份：

- tag：`v0.1.0`
- peeled release commit：`52864b1aa72f56081abfc0bd146415d2a5f1ccb8`
- 已通过 tag run：`29175974787`
- artifact：`smart-travel-assistant-0.1.0-rc`
- repository：当前 `github.repository`，且必须等于预期仓库 `nanyouwenOwen/smart-travel-recommendation`

禁止删除、重建、移动或强推 `v0.1.0`；禁止重新构建并冒充 tag run 产物；禁止使用其他 run、同名未来 artifact 或当前 `main` 构建物；禁止把 Token、Release draft 内容或二进制产物提交进仓库。本轮授权不包括部署、真实模型密钥或仓库策略修改。

## 目标与恢复策略

在 `main` 上增加一个可审计的一次性 recovery job。它只在普通 `main` push 中运行，并且必须 `needs: [release-candidate]`，从而让恢复实现所在的准确新提交先通过 `openapi`、`frontend`、`backend`、`e2e`、`container-smoke`、`security` 和当前 run 的 `release-candidate`。该当前 artifact 仅作为完整质量链门禁，不得用于发布；待发布附件必须通过跨 run 下载固定 tag run `29175974787` 的固定 artifact。

不选择 `workflow_dispatch`，因为当前本地没有可触发 workflow 的 API Token；不选择依赖未知提交 SHA 的自引用 condition；不修改已推送 tag。恢复 job 在首个实现提交上满足完整 CI 后执行，发布成功后以紧接的证据提交删除整个一次性恢复路径。即使证据提交前出现重复运行，发布脚本仍必须以严格幂等方式只接受完全匹配的 Release；任何冲突都安全失败。

## 实施设计

### 1. 增加明确的一次性恢复 job

修改 `.github/workflows/ci.yml`，新增例如 `release-recovery-v0-1-0` 的 job，并满足以下全部条件：

1. job 只允许 `github.event_name == 'push' && github.ref == 'refs/heads/main'`，不得在 PR、tag 或其他分支运行；保留原 `release` job，不改变 tag 历史或让它在 branch push 获得写权限。
2. `needs: [release-candidate]`。由于现有 candidate 已依赖六个质量 job，恢复写操作只能在本次 `main` 提交整条质量链成功后开始；任何 skipped、cancelled 或 failed 均阻断恢复。
3. job 级权限仅为 `contents: write` 与 `actions: read`；顶层仍保持 `contents: read`，其他 job 不扩大权限。使用 Actions 自动注入的短期 `github.token`，不新增 repository secret，不把 token 输出或作为普通参数记录。
4. 在 job `env` 中把 tag、完整 peeled SHA、source run ID、artifact name、预期 repository 固定为上述常量；所有后续命令只使用这些常量并在开始时逐项断言，不接受 workflow input、branch 文件内容、PR 内容或动态“latest successful run”。
5. checkout 当前 `main` 的恢复实现，使用足够历史；随后以精确 refspec 显式 fetch `refs/tags/v0.1.0:refs/tags/v0.1.0` 和 `main`。不得用 `--tags` 后选最新 tag。

恢复 job 的存在期必须保持最短：它只服务一次 `v0.1.0` 恢复，不能泛化成未来版本的常驻自动发布入口。

### 2. 在任何写操作前验证 tag、当前提交和来源 run

恢复 job 在调用发布脚本前必须失败即停并验证：

1. `github.repository` 精确等于固定仓库，event/ref 精确等于普通 `main` push；当前 checkout 的 `HEAD` 等于 `GITHUB_SHA`，且固定 release commit 是 `HEAD` 的祖先。
2. `git cat-file -t refs/tags/v0.1.0` 必须为 `tag`；`git rev-parse refs/tags/v0.1.0^{commit}` 必须精确等于固定 release commit。记录 tag object SHA 只作审计，不拿它替代 peeled commit。
3. 通过 GitHub API 查询 run `29175974787`，确认 repository、event/ref/tag、head SHA、conclusion 与预期一致；必须确认该 run 的 `release-candidate` job 成功，不只相信 artifact 存在。若 API 对 annotated tag 的 `head_sha` 语义表现为 tag object，则同时依赖本地精确 tag fetch/peel 验证，不以推测放宽固定 peeled SHA。
4. 只用 `actions/download-artifact@v4` 的 `run-id: 29175974787`、`github-token: ${{ github.token }}`、固定 `name` 和固定 `path` 下载。若 action 的跨 run 语法需要 `repository`，显式设为当前固定仓库。不得省略 run-id，也不得下载所有 artifacts。
5. 下载后先规范化和检查布局：允许 action 对单个命名 artifact 的 documented 直接内容布局；若实际出现唯一同名嵌套目录，只能由明确、受测的 staging 步骤复制到新的空 `release/`，不能递归搜索或选择第一个匹配文件。最终 `release/` 顶层必须由现有候选验证脚本证明恰好包含固定八项，无额外文件。
6. 运行 `scripts/verify-release-candidate.sh release 52864b1aa72f56081abfc0bd146415d2a5f1ccb8`；校验 `GIT_SHA`、`SHA256SUMS`、七项 payload、JAR、前端归档和双 SBOM。绝不能使用当前恢复提交的 SHA作为候选 expected SHA。

以上所有验证均为只读。任何身份、run 结论、tag 类型、peeled SHA、布局或候选校验不一致时立即失败，不创建、修改或公开 Release。

### 3. 严格处理现有 Release/draft 并发布

在候选验证成功后才调用现有 `scripts/publish-github-release.sh`，参数固定为当前仓库、`v0.1.0`、固定 release commit、已规范化的 `release/` 和 tag 中版本化的 `docs/releases/v0.1.0.md`。

调用前先以认证 API 枚举该 tag 的 Release 状态，不能依赖公开 `404`：

- 不存在时，允许现有脚本创建 draft、上传、远端下载复验后再公开。
- 恰有一个 draft 时，只允许同 tag 恢复；脚本按固定白名单清理其旧附件、重新上传并在 draft 状态验证，最后公开。
- 恰有一个非 draft Release 时，只有标题、notes、tag、prerelease 状态、固定八项附件、非零大小及远端下载候选校验全部匹配才算幂等成功；不匹配必须失败，不覆盖公开 Release。
- 多个匹配或 API 查询异常必须失败。不得删除 Release、修改 tag、接受额外附件或因为公开 API `404` 绕过认证检查。

现有发布脚本已经实现 draft 组装、白名单、远端下载复验及严格幂等语义；实施时应优先复用它，只补充跨 run 获取与绑定验证。若审核发现脚本无法证明某项不变量，修改必须保持首次/恢复/已发布三条路径都安全，并补相应纯逻辑测试。

### 4. 文档与审计状态

实现提交中：

1. 在 `docs/ai-governance/AI_CHANGE_LOG.md` 记录 tag run 的已知事实、未知日志边界、为何采用固定 run artifact 和一次性 main recovery，以及 recovery 尚未执行；不能提前宣称 Release 完成。
2. 如交接或发布清单需要同步，只记录 `v0.1.0` 已推送、tag run 质量链/candidate 成功但 release 失败、恢复待执行。`TODO.md` 的“发布 MVP”继续为 `[ ]`。
3. 不把一次性 recovery 说明混入产品/API 文档；发布工程事实可记录在 release checklist/reports，AI 决策留在 `docs/ai-governance/`。

## 独立审核与修正复审

由未参与实施的 reviewer 创建 `docs/reviews/round-13-v0.1.0-release-recovery-review.md`，只审核和测试，不编辑实现。首次推送 recovery 提交前至少完成：

1. 对照本计划、实际 diff、Round 11/12 计划审核、tag/run 的可公开事实，确认 tag 未移动，四项固定身份和 repository guard 无动态替代路径。
2. 审核触发与权限：只有普通 `main` push、且当前完整质量链成功后才有写操作；PR/tag 不运行 recovery；`actions: read`/`contents: write` 只授予该 job，没有长期 token、`pull_request_target` 或外部输入。
3. 审核跨 run artifact 下载明确绑定 run ID、repository 和 name，且当前 run artifact 不能被误发布；目录规范化不递归猜测，最终固定八项及 fixed `GIT_SHA` 由脚本实际验证。
4. 审核 tag object 与 peeled commit 的区分、固定 release commit 在当前 main 的祖先关系、source run/job 成功检查，以及 Release 不存在/draft/已公开冲突三条路径。
5. 运行 workflow YAML 解析、`git diff --check`、相关 shell `bash -n`、候选验证正负向测试及发布脚本状态机测试；重点覆盖错误 run/tag/SHA、错误布局、缺失/额外/损坏附件、draft 恢复、完全匹配的公开 Release和冲突公开 Release。
6. 按比例运行 `./scripts/check.sh`，给出明确 `PASS`/`FAIL`。阻断由实施终端修正，再由同一 reviewer 复跑受影响门禁并追加复审；reviewer 不直接修改实现。

只有 reviewer 最终 `PASS` 才可提交并推送 recovery 变更。准确 `main` run 中七个既有质量/候选 job 与 recovery job 必须全部成功；历史 tag run 的质量成功不能替代 recovery 实现提交自身的 CI。

## 执行、远端核验与一次性路径移除

1. 推送经审核的 recovery 实现提交，记录完整 SHA，并等待其准确 Actions run。确认七个既有 job 全绿后，recovery job 才开始；若先前门禁失败，不得手工绕过发布。
2. recovery job 成功后独立核验：远端 annotated tag 仍剥离到固定 release commit；GitHub Release 为公开、非 draft、非 prerelease并绑定 `v0.1.0`；标题和 notes 匹配；资产名精确为固定八项且均非空；下载远端附件到新目录并通过 checksum 和候选验证；记录准确 recovery run/job URL。
3. 立即启动同一轮的收尾实施，删除 `.github/workflows/ci.yml` 中整个一次性 recovery job及相关常量，避免未来 `main` push 持有无必要的 Release 写路径。保留原始精确-tag `release` job作为版本化历史机制，不移动或重跑 tag。
4. 在该发布后证据提交中将 `TODO.md` 的“发布 MVP”改为 `[x]`，更新 release checklist、MVP verification、handoff 和 AI change log，明确区分 tag/release commit、tag run、recovery run与发布后证据提交。
5. 同一独立 reviewer 对真实远端 Release、tag 不变性、附件下载复验、recovery 路径已完整移除和文档 diff 追加最终复审。只有最终 `PASS` 后才推送证据提交。
6. 等待证据提交自身的准确 `main` CI 七个既有 job 全绿；由于 recovery 已删除，该 run 不应有 recovery job。最终再次核验 Release/附件未变、`origin/main` 包含证据提交、工作树干净且 `v0.1.0` 仍指向固定 release commit。

若 recovery job 在任何写操作前失败，可依据准确错误新建修正提交并继续同一 reviewer 复审；若创建了 draft 后失败，只能用相同固定身份安全恢复。若发现 tag 目标错误、source artifact 不可信、已有公开 Release 冲突或必须改变已发布源码，立即停止并请求用户决定新版本号，禁止移动 `v0.1.0`。

## 验证证据清单

- recovery 实现提交与其准确 Actions run；七个既有 job 及 recovery job 的逐项结论。
- `v0.1.0` tag object 类型、tag object SHA、peeled commit、tagger/message及 main 可达性；前后核验一致。
- source run `29175974787`、其 tag/ref/head 事实、成功的 `release-candidate` job、固定 artifact name和跨 run 下载证据。
- 下载 artifact 与 Release 八项附件的精确文件集合、非零大小、固定 `GIT_SHA`、`SHA256SUMS`、双 SBOM、JAR与前端归档校验。
- Release URL/id、tag、标题、notes、draft/prerelease状态，以及认证 API 对不存在/draft/公开状态的核验。
- reviewer 初审/复审结论、workflow/shell/项目门禁输出、一次性 recovery 删除 diff。
- 发布后证据提交及其准确 CI，`TODO.md` 完成、`origin/main` 同步、干净工作树和 tag/Release最终一致性。

任何审计记录不得包含 token、认证头、私有响应正文或附件二进制内容。

## 完成定义

Round 13 和 MVP 发布只有同时满足以下条件才完成：

1. 一次性 recovery 实现通过独立审核，并在其准确 `main` run 完整质量链成功后才执行写操作。
2. 发布附件唯一来自固定 tag run `29175974787` 的固定 artifact，且其内容精确绑定 `52864b1aa72f56081abfc0bd146415d2a5f1ccb8`；没有用恢复提交或其他 run 重新构建物替代。
3. `v0.1.0` 始终是 annotated tag且未被移动；GitHub Release 已公开、非 draft、非 prerelease，固定八项远端附件全部通过下载复验。
4. 一次性 recovery job 已从后续证据提交删除，未来普通 `main` push 不再拥有该恢复写路径。
5. 真实远端事实经独立 reviewer 最终 `PASS`；`TODO.md` 的“发布 MVP”已勾选，清单、报告、handoff 和 AI 日志准确记录证据。
6. 发布后证据提交已推送且其准确 `main` CI 七个既有 job 全绿；`origin/main`、本地工作树、不可变 tag和 Release/附件最终状态一致。

真实 DeepSeek/Xiaomi MiMo 密钥验收、全新 Codespaces 人工访问和生产部署仍是明确的人工作业边界，不阻断已定义的 MVP GitHub Release，但不得被本轮虚假声明为完成。
