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

## 首次 recovery 失败后的修正审核

审核结论：**FAIL（禁止推送当前上传修正）**

准确 main run `29176419521` 的公共 API 证据显示：提交 `33e600b3034363c9d44f70ad57f89c17c9fc8860` 上七个既有 job 全部成功；`release-recovery-v0-1-0` 的身份/source run、固定跨 run artifact 下载和固定候选校验步骤成功，仅 `Recover and verify v0.1.0 Release` 失败。公开 Release API 仍为 `404`；无法认证枚举 draft，因此没有把猜测写成根因。`v0.1.0` 仍为 annotated tag且 peeled commit 未变。

实施修正把附件上传从 `gh api --hostname uploads.github.com` 改为 GitHub REST 示例形态的 `curl`：POST 到固定 uploads endpoint、Bearer `GH_TOKEN`、固定 API/Accept/Content-Type header 和 `--data-binary` 文件体；上传前后的严格八附件及远端下载复验没有移除。新增 EXIT trap 仅输出固定阶段名和退出码，不打印 token、header、附件内容或响应正文。设计方向成立，但当前测试不足以证明关键修正。

### R13-B3：上传 mock 没有验证本次修正的 HTTP 不变量

新 mock `curl` 对 `-H` 和 `--request` 只做 `shift 2`，既不记录也不断言。因此下列任一回归都仍会让三条正向测试通过：

- 删除或写错 `Authorization: Bearer ...`；
- 删除或写错 `Content-Type: application/octet-stream`、Accept 或 API version；
- 把 `POST` 改成其他 method；
- header 中使用错误 token 值。

首次 recovery 正是在发布写步骤立即失败，本次提交的核心就是纠正真实上传请求。测试必须 fail-closed 地断言 method、精确 uploads host/path、固定安全文件名、Bearer 使用注入的 mock token、API version、Accept、Content-Type 和 `--data-binary` 输入；还应有至少一条错误/缺失认证或上传请求失败的负向用例，证明不会继续公开 Release。mock 和测试日志不得回显真实或 mock token。

### R13-B4：新增发布阶段诊断没有回归测试

EXIT annotation 是本次为下一次远端失败提供可审计阶段证据的新行为，但当前测试所有负向输出都被 `/dev/null 2>&1` 丢弃，没有验证：

- `GITHUB_ACTIONS=true` 时准确输出固定 `stage` 和原始非零 exit；
- 本地模式不输出 Actions annotation；
- 输出不包含 `GH_TOKEN`、Authorization header、响应正文或附件内容；
- cleanup 不把原始非零状态改写为成功或其他状态。

请对 mock curl 注入固定非零退出，在 `upload-assets` 阶段捕获 stderr 与退出码，并覆盖上述边界。阶段值只能来自脚本内固定枚举，不得吸收外部响应或文件名。

### 本次已通过证据

- `bash -n`（发布、候选验证和测试脚本）：PASS。
- `scripts/test-publish-github-release.sh` 当前 3 条正向、4 条负向：PASS，但因 B3/B4 对新行为覆盖不足，不能作为本修正的充分证据。
- `git diff --check`：PASS。
- `./scripts/check.sh`：PASS；前端/后端既有门禁无退化。
- source run `29176419521`、jobs、远端 tag 与公开 Release 的只读事实和 AI log 一致；AI log 没有宣称未知根因或发布完成。

修正 B3/B4 后由同一 reviewer 复跑 shell syntax、状态机、上传请求正负向、annotation 安全性、diff/project gates，并再次确认 tag/Release 未改变。最终 PASS 前禁止推送该修正恢复提交。

## 上传修正第一次复审

复审结论：**FAIL（B3/B4 尚未完全关闭）**

新增测试已经有效改善以下证据：mock 现在要求 POST、Accept、Bearer mock token、API version、octet-stream 和非空 `@file`；错误 token 会在真实 upload 分支失败；Actions 模式只出现一条固定 `upload-assets` annotation，且输出排除 token、Authorization、Content-Type 和测试附件内容；本地模式无 annotation并与 Actions 模式返回相同非零状态。脚本语法、完整状态机测试和 `git diff --check` 均 PASS。

仍有两个必须修正的精确断言：

1. **B3 剩余：URL 未绑定官方 host/repository。** mock 当前只用 `[[ "$url" =~ /releases/.../assets... ]]`，没有断言 scheme、`uploads.github.com`、`/repos/owner/repo/` 和 URL 整体边界。把生产 URL 改为任意主机或错误 repository、但保留末尾 release/assets 路径，测试仍会通过。请精确匹配本测试固定的 `https://uploads.github.com/repos/owner/repo/releases/<id>/assets?name=<固定安全文件名>`，并至少证明错误 host 或 repo 会失败。
2. **B4 剩余：annotation 未证明保存原始退出码。** 测试只断言文本含 `stage=upload-assets; exit=`，没有从 annotation 取出 exit 数值并与 `annotation_status` 精确比较。当前只能证明“有某个 exit 字段”，不能证明 cleanup 未改写原状态。请断言完整尾部 `exit=$annotation_status`（或等价精确解析比较），并继续要求该状态非零及本地/Actions 一致。

上述是本轮既定安全不变量的测试缺口，不要求修改生产逻辑。修正后复跑同一测试与静态门禁；最终 PASS 前仍禁止推送。

## 上传修正最终复审

复审结论：**PASS（允许推送本次 recovery 修正提交）**

B3/B4 已完整关闭：

- mock upload 现在同时精确要求 POST、四个固定 header、Bearer 预期 mock token、非空 `@file`，以及 `https://uploads.github.com/repos/owner/repo/releases/<数字 id>/assets?name=<单段文件名>` 的完整 host/repository/path 结构；错误 host 与错误 repository 两条负例均按预期失败。生产上传文件名仍只来自脚本内八项固定白名单。
- upload 认证失败测试捕获真实非零 `annotation_status`，并精确要求 annotation 中 `stage=upload-assets; exit=$annotation_status`；同时证明仅一条 annotation、无 token/header/content-type/附件内容，本地模式无 Actions annotation 且保留相同退出状态。

最终复跑证据：

- 相关三个 shell 文件 `bash -n`：PASS。
- `scripts/test-publish-github-release.sh`：PASS；3 条正向状态路径、4 条既有冲突/API 负向，以及上传认证、Actions/local 诊断、错误 host、错误 repository 全部通过。
- `git diff --check`：PASS。
- 前次修正审核的 `./scripts/check.sh`：PASS；本次仅补测试精确断言，无产品或 workflow 变更。
- 最终只读远端复核：`v0.1.0` 仍为 annotated tag，peeled commit 仍为固定 `52864b1aa72f56081abfc0bd146415d2a5f1ccb8`；公开 Release API 仍返回 `404`。复审没有实施任何远端写操作。

允许提交并推送本次上传修正，等待其准确 main run。仍必须满足七个既有 job 全绿、recovery 身份/跨 run candidate 校验通过且发布步骤成功；若成功，应立即按计划删除一次性 recovery job、下载复验公开 Release 八项附件、补齐 TODO/清单/交接证据并由同一 reviewer 完成最终发布复审。

## 第二次 recovery 失败后的修正审核

审核结论：**PASS（允许推送 `gh release upload` 修正）**

### 真实失败边界

公共 Actions API 证明 run `29176701107` 对应 main 提交 `8492ec67b9a62583826450de01c3ce4841de931f`：六个质量 job 和 `release-candidate` 成功；recovery 的固定身份/source run、跨 run 下载及候选校验步骤成功，只有发布步骤失败。已有固定阶段诊断把失败定位到 `upload-assets` 且原退出码为 1。公开 Release API 仍为 `404`，但认证可见 draft 状态未知；审计日志没有将未知 HTTP 响应伪造成确定根因。tag 类型和 peeled commit 均未改变。

### 实现审核

- 上传改为 GitHub CLI 官方命令 `gh release upload "$tag" <八个固定路径> --repo "$repo"`。tag 和 repository 仍来自 workflow 已固定并在写前校验的常量；认证仍只使用 job 注入的短期 `GH_TOKEN`，未增加 secret、PAT、动态输入或权限。
- 当前 GitHub CLI 官方实现的 `FetchRelease` 明确同时查找公开 Release 和按 pending tag 查找 draft Release，再使用返回的 `upload_url` 并发上传。因此该命令与现有“先创建/恢复 draft、清空旧附件、上传验证后再公开”的状态机相容。参考：[官方 CLI upload 手册](https://cli.github.com/manual/gh_release_upload) 与 [GitHub CLI 当前 `FetchRelease` 源码](https://github.com/cli/cli/blob/trunk/pkg/cmd/release/shared/fetch.go)。
- 上传参数不是 glob 或目录扫描：`upload_paths` 只由脚本内八项白名单逐项构造。上传后重新认证枚举 Release，要求仍为 draft、metadata 精确匹配、资产集合恰为八项且全部非空，并逐项远端下载运行固定 SHA/checksum/SBOM/JAR/tar 验证；只有通过后才公开，公开后再次完整复验。
- 已公开且完全匹配的 Release 仍走只读幂等验证，不调用 upload；冲突公开 Release 仍失败。draft 恢复仍先删除其现有资产，再上传固定集合，未使用 `--clobber` 的非原子替换语义。
- `--repo` 显式约束仓库；mock 同时要求固定 tag、固定 repository、精确 `GH_TOKEN`、目标为该 tag 的 draft、所有路径为非空文件。后续固定资产校验使缺失、额外、重复或错误文件名均不能进入公开路径。
- 日志未开启 shell xtrace；`GH_TOKEN` 不作为命令参数。失败 annotation 仍只包含脚本内固定阶段和数字退出码，既有错误 token 测试证明不回显 token、Authorization、Content-Type 或附件内容。本轮没有引入响应正文输出或 debug API trace。

### 测试证据

- `bash -n scripts/publish-github-release.sh scripts/test-publish-github-release.sh scripts/verify-release-candidate.sh`：PASS。
- `scripts/test-publish-github-release.sh`：PASS；首次创建、draft 清理恢复、公开幂等三条正向路径，metadata/asset/API 冲突四条既有负向路径，以及错误认证、Actions/local 原退出码诊断、错误 repository 均符合预期。
- `git diff --check`：PASS。
- `./scripts/check.sh`：PASS；前端 43 tests/coverage/build、后端 Maven verify 与 OpenAPI 门禁无退化。
- 远端只读复核：run/job 事实与 AI log 一致；`v0.1.0` 仍为 annotated tag，peeled commit 为 `52864b1aa72f56081abfc0bd146415d2a5f1ccb8`；公开 Release 仍为 404。审核终端未执行远端写操作。

允许推送该修正并等待其准确 main run。只有七个既有 job 与 recovery 全部成功、公开 Release 八附件经真实下载复验后，才可进入删除一次性 recovery job、勾选 MVP TODO 和最终证据复审阶段；本 PASS 本身不代表 Release 已发布。

## 第三次 recovery 失败后的修正审核

审核结论：**FAIL（禁止推送当前 release discovery 修正）**

### 远端事实

公共 Actions API 显示 run `29176911435` 对应 main 提交 `f836ac87a177d65185d079b8cf4601293f9b447a`：六个质量 job 与 `release-candidate` 成功；recovery 仅发布步骤失败。根据实施终端提供的固定阶段证据，失败位于 `create-draft`，与“REST list 没有发现既有 pending-tag draft，继而尝试重复创建”的解释一致，但公共 API/日志不能独立读取认证 draft，故本报告不把它提升为完全证明的服务端根因。公开 Release API仍为 `404`，annotated tag 与固定 peeled commit 未改变。

改用 `gh release view <tag> --json ...` 的总体方向正确：当前 GitHub CLI `FetchRelease` 同时查 published Release 与 pending-tag draft；成功 JSON 映射回现有状态机后，draft 清理/上传、严格八资产、远端下载验证、公开后复验和公开 Release 幂等路径均保留。当前实现仍有下列阻断。

### R13-B5：not-found 分类是宽松子串，不是精确错误

要求是“仅精确 `release not found` 视为缺失，其他查询错误 fail-closed”。当前实现使用：

```bash
grep -Fqi 'release not found' "$release_error"
```

这会把包含该短语的更长错误、额外诊断或混合认证/网络错误误判为缺失，然后进入有写权限的 `create-draft`。请规范化单个末尾换行后要求 stderr 内容精确等于固定文本（大小写也应按官方固定错误匹配），其他任意空输出、多行输出、前后缀、HTTP/认证/GraphQL/网络错误全部返回查询失败。测试需至少覆盖精确 not-found 进入首次创建，以及“含 not-found 子串但还有其他内容”不得创建。

### R13-B6：asset API URL 只绑定前缀，未绑定完整规范路径

当前 `jq` 仅要求 `startsWith("https://api.github.com/repos/<repo>/releases/assets/")`，随后 `split("/") | last | tonumber`。例如此前缀后为 `unexpected/path/123` 仍会被接受为资产 ID 123。该 ID 随后可用于删除和下载 API，不能接受非规范 URL。

请要求完整 URL 恰为固定 scheme/host/repository/path 加一个十进制数字 ID，前缀后不得有额外斜杠、路径、query、fragment、符号或空值。可从固定前缀移除后验证 remainder 为纯数字再 `tonumber`。新增错误 host、错误 repo、额外路径、非数字 ID 的负向映射测试，并保留正常 draft/公开 JSON 映射正向证据。

### R13-B7：AI 审计日志尚未记录第三次真实失败与本轮决策

当前 worktree 只有发布脚本、测试和本 Round 13 报告修改；`docs/ai-governance/AI_CHANGE_LOG.md` 没有未提交更新，末尾停留在第二次 recovery。根据仓库工作流，第三次真实 run、已知 `create-draft` 阶段、认证日志不可见边界、改用官方 pending-draft discovery 的理由及“尚未发布”必须在实现提交中记录，不能等远端成功后回填并丢失失败决策链。

### 已通过测试与不变量

- `bash -n`（发布、测试、候选验证脚本）：PASS。
- 现有 `scripts/test-publish-github-release.sh`：PASS；首次、draft、公开幂等状态机及既有冲突、查询故障、上传认证/仓库/annotation 测试均通过，但尚未覆盖 B5/B6 的欺骗性输入。
- `git diff --check`：PASS。
- run `29176911435` 的七个既有 job 均成功；tag 不变且公开 Release API 仍为 404。审核未执行远端写操作。

实施终端修正 B5–B7 后，由同一 reviewer 复跑状态机、精确错误分类与 URL 映射正负向、shell/diff 门禁，并再次核验远端不变性。最终 PASS 前禁止推送。

## 第三次 recovery 修正最终复审

复审结论：**PASS（允许推送 release discovery 修正）**

B5–B7 已关闭：

- `gh release view` 失败时，stderr 经 shell 去除末尾换行后必须整体精确等于固定 `release not found` 才返回“缺失”；任意其他查询错误返回 10 并阻止创建。普通认证/API 故障及包含 not-found 短语但附带认证错误的混合文本负例均安全失败。
- asset `apiUrl` 必须以固定 `https://api.github.com/repos/<目标仓库>/releases/assets/` 开头，去掉该完整前缀后的全部剩余内容必须只含十进制数字，再转换为 ID。错误 host、错误 repository、额外路径和非数字 ID 四类负例均失败；正常 draft/公开 JSON 映射仍通过完整状态机。
- `AI_CHANGE_LOG.md` 已记录准确 run `29176911435`、`create-draft` 阶段、公开/认证信息边界、pending-tag draft discovery 决策及仍待审核/远端运行状态，没有提前宣称 Release 或 MVP 完成。

最终复跑：相关 shell `bash -n` PASS；完整 `scripts/test-publish-github-release.sh` PASS；`git diff --check` PASS。既有完整项目门禁已在本轮前一修正复审通过，本次只修改 discovery、JSON 映射及其测试/审计记录。远端 tag 与公开 Release 状态在初审时已只读复核且本次实施/复审没有远端写动作。

允许推送本修正并等待准确 main run。只有七个既有 job、固定 source candidate 校验和 recovery 发布步骤全部成功，才可核验公开 Release；随后仍必须删除一次性 recovery job并完成最终发布证据复审。

## draft discovery 提交的远端门禁失败审核

审核结论：**允许仅提交本次远端失败的审计记录，以触发一次新的完整重验；不允许把 run `29177109032` 视为通过，也不允许放宽性能门禁或修改产品。**

只读核验结果：

- run `29177109032` 精确对应提交 `6049e18631e5d515d5eed36d99f1e8583e4faecb`。backend、frontend、e2e、security、openapi 成功；`container-smoke` 失败；`release-candidate` 与 `release-recovery-v0-1-0` 均因依赖跳过。
- 固定阶段证据将失败定位为 `stage=7/10 performance thresholds`，k6 阈值返回 1。该 run 没有生成候选，也没有进入 recovery 的身份、draft discovery、上传或任何 Release 写步骤。因此它严格阻断了该次发布尝试，但不构成 draft discovery 逻辑失败的执行证据。
- `6049e18` 相比相邻已验证提交没有修改 `scripts/compose-smoke.sh`、`tests/performance/smoke.js`、Compose 文件或产品实现。相同 smoke/性能实现已在紧邻 run `29176701107` 与 `29176911435` 中通过；后两次的失败均发生在 recovery 发布步骤，说明它们已先通过同一 container 门禁。
- 当前拟提交差异只有 `docs/ai-governance/AI_CHANGE_LOG.md` 对上述准确事实的记录；它明确不把 container failure 写成发布代码失败、不宣称 Release 完成，也不调整阈值、超时、重试、断言或测试数据。

基于以上证据，允许一个纯审计文档提交触发新的 main run。这不是绕过失败：新 run 仍必须从头通过 openapi、frontend、backend、e2e、container-smoke、security、release-candidate，recovery 才可能获得写入机会。历史相邻 green 不能替代新 run 的 container 成功。

授权边界：只允许这一次审计记录触发的完整重验；不得手工重跑单个 recovery、不得使用历史 candidate 替代当前质量门禁、不得调高 k6 阈值或修改产品以掩盖波动。若新 run 再次在同一 performance stage 失败，应停止用文档提交反复重试，转入独立诊断轮次并保留原阈值，查明资源噪声或真实性能退化后再决定。

## 第四次上传诊断修正审核

审核结论：**FAIL（禁止推送当前逐项上传诊断修正）**

### 远端边界

实施终端提供的准确 run `29177210816` 证据显示：六个质量 job、`release-candidate`、recovery 的固定身份/source artifact/候选复验均成功；pending-tag draft 已被 discovery 找到，失败再次局限在 `upload-assets` 且原退出码为 1。公开 tag 页面仅有 GitHub 自动源码归档，没有固定八附件，不能当作公开 Release 完成。该事实证明本次诊断修正针对真实上传失败，不涉及放宽任何质量门禁。

### 已通过的实现审查

- 八项固定白名单改为逐项 `gh release upload`，消除 CLI 内部五并发变量；tag、repository、短期 `GH_TOKEN` 和资产路径边界未扩大。
- 每项上传只把 stderr 写入私有临时文件；原始响应、header、token 和附件内容均不回显。失败类别只能落入固定 `HTTP-401/403/404/422`、`already-exists`、`release-not-found`、`unknown` 枚举；annotation 的资产名只来自固定八项列表，因此不存在 workflow command 注入来源。
- `set +e` 只包围单次 CLI 调用，紧接着捕获 `$?` 并恢复 `set -e`；失败以捕获的原状态退出，EXIT trap 再输出固定字段并保持该状态。正常路径逐项删除错误临时文件，异常路径也由 EXIT cleanup 删除当前文件。
- draft 重试逻辑仍先认证枚举并删除所有现有附件，再从固定第一项开始上传。若中途失败，draft 不公开；下次运行重新清空部分附件，因此逻辑上可安全幂等恢复。上传完仍要求恰好八项、远端逐项下载候选验证，随后才公开并再次复验。

### R13-B8：新增分类诊断只有 `unknown` 测试，不能证明固定类别映射

当前错误 token 测试中的 mock 通过 shell 条件静默返回 1，没有输出 401/403/404/422、already exists 或 release not found 文本，因此只验证 `asset-CHANGELOG.md-unknown`。本次变更的核心交付是下一次真实失败能给出可靠而安全的固定分类；其余六类从未被执行验证，类别优先级和大小写匹配也没有证据。

请让 mock 可注入固定 stderr 与自定义非零状态，表驱动覆盖六个已知类别和 unknown。每例必须断言：恰一条 annotation、精确固定 asset/category、annotation 中 exit 等于 mock 原状态、原始响应及 token 不出现、本地模式无 annotation。还应加入包含 `%`、换行或 `::` 的恶意响应，证明输出仍只包含固定类别且没有 workflow command 注入。

### R13-B9：缺少中途部分上传失败后的真实重试测试

现有 draft recovery 测试只从预置 stale/extra 附件直接成功；错误 token 总在第一项 `CHANGELOG.md` 失败。没有证明第二至第七项失败后已上传的部分集合保持 draft、未执行 publish，并能在下一次调用先清空后完整重建八项。

请为 mock 增加“第 N 项上传后失败”的一次性注入（使用固定中间项），验证首次调用：原 exit/固定中间资产 annotation 正确、Release 仍 draft、没有公开、只留下预期部分附件；关闭注入后再次运行，验证旧部分被删除、八项无重复无额外、远端内容验证通过并最终公开。该测试同时应检查失败和成功后没有遗留脚本创建的 upload stderr 临时文件；可以给脚本使用受测临时目录或记录创建路径，不能依赖模糊的全局 `/tmp` 数量。

### 当前门禁证据

- 相关 shell `bash -n`：PASS。
- 现有发布状态机测试：PASS，但只覆盖 `unknown` 首项失败，不能关闭 B8/B9。
- `git diff --check`：PASS。
- 本次只读 tag fetch 仍证明 `v0.1.0` 为 annotated tag且 peeled commit 未变。公共 API 查询因匿名速率限制返回 403，本 reviewer 不据此声称当前公开 Release 状态；采用实施方已记录的 tag 页面事实，且审核未执行任何远端写操作。

实施终端修正 B8/B9 后由同一 reviewer 复跑分类、注入安全、中途失败/重试、临时文件清理、状态机及静态门禁。最终 PASS 前禁止推送。

## 第四次上传诊断修正最终复审

复审结论：**PASS（允许推送逐项上传诊断修正）**

B8/B9 已关闭：

- mock 可按固定附件注入 stderr 与任意固定非零状态。表驱动测试逐项执行 `HTTP-401`、`HTTP-403`、`HTTP-404`、`HTTP-422`、`already-exists`、`release-not-found` 和 `unknown`，每例均保留原 exit 23、只产生一条 annotation，并精确映射到固定 `asset-CHANGELOG.md-<category>`。
- unknown 用例注入包含 `::error::` 和伪 token 的恶意响应；最终输出不含该响应或 token，只含脚本生成的固定 annotation。这与静态审查共同证明原始 stderr 不进入日志，资产名和类别均无外部 workflow-command 注入来源。
- 中间第六项 `frontend-sbom.cdx.json` 注入 exit 29 后，Release 仍为 draft且只保留前五项已上传附件，没有进入 publish；受控 `TMPDIR` 为空，证明 release/view/upload error 与 remote 临时文件均由 EXIT cleanup 清理。
- 关闭注入后的同脚本重试先按 draft discovery 删除前五项，再完整上传，最终状态为 `draft=false`，资产恰八项且八个唯一名称。该用例实际证明部分上传后的清理、无重复重建、验证和公开状态机可恢复。

最终门禁：相关 shell `bash -n` PASS；完整发布状态机与新增分类/注入/重试测试 PASS；`git diff --check` PASS。生产逻辑仍保持单次 `set +e` 捕获、立即恢复 `set -e`、原状态退出、固定分类/附件 annotation 和原始响应不回显；没有扩大权限、tag/repository/artifact 或候选边界。

允许推送本修正并等待准确 main run。只有完整质量链、candidate、recovery 和真实远端八附件复验全部成功后，才可删除一次性 recovery job并进入最终 MVP 发布证据复审。

## 第五次上传修正审核

审核结论：**PASS（允许推送复用已发现 draft `uploadUrl` 的修正）**

### 真实失败与修正范围

实施终端记录的 run `29177426902` 已通过六个质量 job、candidate、固定 source artifact 与 recovery 只读校验；首项 `CHANGELOG.md` 精确分类为 `release-not-found`。这把失败限定为 `gh release upload` 内部再次执行 `FetchRelease`，而非候选内容、固定附件集或并发。修正不再二次按 tag 查 Release，而是复用已经由认证 `release view` 成功发现的同一 draft `uploadUrl`。

### 安全与状态机审核

- discovery JSON 新增官方 `uploadUrl` 字段；`validate_metadata` 要求其完整值精确等于 `https://uploads.github.com/repos/<固定仓库>/releases/<当前 databaseId>/assets{?name,label}`。因此 scheme、host、repository、Release 数字 ID、路径和模板必须同时匹配，不能由返回 JSON 注入另一目的地。
- 只有上述精确验证通过后才去掉固定 `{?name,label}` 模板。最终 absolute endpoint 只附加 `?name=<name>`；name 来自八项固定安全白名单，仅包含字母、数字、点和连字符，不接受用户输入、斜杠、`&`、`#`、`%` 或 workflow 内容。
- GitHub CLI 当前官方 `gh api` 实现明确在 endpoint 含 `://` 时直接将其作为 request URL，并用 `http.NewRequest` 发出请求；因此 absolute uploads URL 是实现支持的路径，而不是 mock 专属行为。参考：[GitHub CLI `httpRequest` 源码](https://github.com/cli/cli/blob/trunk/pkg/cmd/api/http.go)。请求仍使用同一 GitHub CLI 认证 HTTP client 和 job 短期 `GH_TOKEN`，未新增 header 拼接、secret 或权限。
- 每项请求固定为 POST、`Content-Type: application/octet-stream`、`--input` 对应非空候选文件和 `--silent`。响应正文不输出，stderr 仍进入临时文件并只映射到固定类别；既有原退出码、恶意响应隔离、临时文件 cleanup 证据保持有效。
- draft 仍先删除已存在附件，再逐项上传；中途失败不公开，下次运行重新清空。上传后必须重新 discovery，验证 metadata/upload URL、精确八项及非零大小，逐项下载候选复验后才公开，公开后再次复验。已公开完全匹配路径仍只读幂等，冲突路径仍失败。

### 负向与门禁证据

- upload URL 错误 host、错误 repository、错误 Release ID、缺失标准模板四类均在任何上传前失败。
- mock 对 absolute URL、octet-stream header、短期 token、非空 input 和固定目标 Release 做精确断言；错误 token、七类分类、恶意 stderr、部分上传第六项失败及重试完整八项测试继续全部通过。
- 相关 shell `bash -n`：PASS；完整发布状态机：PASS；`git diff --check`：PASS。
- 只读 tag fetch 再次确认 `v0.1.0` 仍为 annotated tag且 peeled commit 未变；审核未执行远端写操作。

允许推送并等待准确 main run。仍必须由该 run 自身通过完整质量链、candidate、recovery 及真实远端八附件下载复验；成功后立即删除一次性 recovery job并进入最终发布证据复审。

## 第六次最小上传修正审核

审核结论：**FAIL（禁止推送当前 upload URL 构造修正）**

### 已通过审查

- run `29177621742` 的既有证据把失败定位为 `stage=upload-assets; detail=none; exit=1`，且发生在任何固定附件调用前；实施没有把它误分为 HTTP 或附件错误。
- 移除第二次 `jq -er .upload_url` 后，生产请求不再依赖重新读取模板字段；scheme、uploads host 和 repository 由脚本固定，文件名仍来自八项白名单，上传/验证/公开状态机没有放宽。
- `publish_detail=resolve-upload-url` 在 URL 构造前设置，每项调用前设置固定 `asset-<name>-starting`，CLI 返回非零后仍由原有固定类别覆盖；所有 detail 字段均为脚本枚举或白名单值，不引入外部文本。
- 现有 `bash -n`、完整发布状态机测试和 `git diff --check` 均 PASS；AI log 准确记录 run、`detail=none` 边界、未推测 HTTP 根因和待审核状态。

### R13-B10：`release_id` 未被证明为数字，且 draft upload identity 未在写前校验

实现描述称使用“此前已确认的固定数字 `release_id`”，但实际只有：

```bash
release_id="$(jq -er .id "$release_json")"
```

`jq -e` 只要求结果不是 `false`/`null`；任意非空字符串都会通过。该值随后直接插入 absolute URL。请在任何 PATCH、附件删除或上传之前要求 `.id` 类型为 number、为正整数，并输出其十进制形式；错误字符串、零、负数、小数均 fail-closed。

此外，`validate_metadata` 中 upload URL 与同一 ID 的严格匹配目前只在上传完成后调用。对于 draft，错误 host/repository/ID/template 的 `uploadUrl` 会先经历 PATCH、删除和上传，再在事后失败；现有四条坏 upload URL 测试使用的是已公开 Release，未覆盖该时序。请拆出写前 immutable identity 校验（至少 tag、正整数 database ID、精确 upload URL/repository/ID/template），在 draft 的任何写操作前调用；title/body 等可恢复字段仍可随后规范化。新增 draft 坏 URL/非数字 ID 测试，断言零 PATCH、零删除、零上传并保持原 draft/附件状态。

### R13-B11：两个新增“首项前”诊断位置没有实际失败测试

当前测试没有让 URL resolve 阶段或首项 `mktemp`/调用准备阶段失败，因此无法证明未来首项前失败不再回到 `detail=none`。请在受控测试接口下分别注入：

- URL/ID 解析失败，精确得到固定 `resolve-upload-url`（或更精确安全类别）且保留原 exit；
- 首项调用前的临时文件创建失败，精确得到 `asset-CHANGELOG.md-starting`、保留原 exit、无 token/响应内容。

测试注入不得进入生产环境；可通过受控 `TMPDIR` 权限/无效路径触发真实 `mktemp` 失败。继续断言恰一条 annotation、无 workflow command 注入和 cleanup 不改写状态。

修正 B10/B11 后由同一 reviewer 复跑数字/URL identity 正负向、首项前诊断、状态机、shell 与 diff 门禁。最终 PASS 前禁止推送。

## 第六次修正第一次复审

复审结论：**FAIL（B10 尚有一个后续写入边界）**

已关闭部分：

- 初次 discovery/create 后立即进入固定 `validate-release-identity` 阶段；tag 必须匹配，ID 必须是正整数，upload URL 必须精确绑定固定 repository 与同一 ID。字符串、0、负数、小数 ID 及 draft 坏 upload URL 均在 PATCH/delete/upload 前失败，测试中的 Release 状态文件 hash 保持不变。
- `upload_error` 在 EXIT trap 安装前一次创建；进入 upload 阶段立即设置首项 `starting`，循环内每项 `starting` 后直接进入 `set +e` 的 `gh api`（stderr 重定向会截断同一文件），不再执行 `mktemp`、JSON 解析或文件删除。CLI/重定向失败均已有固定资产 detail；错误 token与七类注入继续证明不会出现 `detail=none` 且保留原 exit。
- 完整状态机、`bash -n`、`git diff --check` 均 PASS。

### R13-B12：重新 discovery 后、公开 PATCH 前没有复用正整数 identity 校验

八项上传完成后脚本执行：

```bash
load_release
validate_metadata
...
release_id="$(jq -er .id "$release_json")"
gh api --method PATCH "repos/$repo/releases/$release_id" ...
```

这里的 `validate_metadata` 虽检查 tag 和 upload URL 文本，但仍以 `jq -er .id` 读取 ID，不要求其为正整数。初次 identity 校验不能证明重新查询结果仍是同一规范 Release；随后该 ID 被用于第二个远端写操作（公开 PATCH）。这不满足“任何写操作前”的 B10 边界。

请让 `validate_metadata` 复用 `validate_release_identity`，或在每次 `load_release` 后、任何后续写操作前显式调用同一校验；还应要求重新 discovery 的 ID 与本轮最初固定 `release_id` 相同，而不只是它各自与返回的 upload URL 自洽。新增 mock 在上传完成后的第二次 view 返回不同/字符串 ID 的负例，断言不执行 publish PATCH、Release 保持 draft。公开后最终只读复验也应继续执行 identity 校验，以证明最终对象未切换。

修正 R13-B12 后复跑定向身份切换负例和完整状态机；最终 PASS 前仍禁止推送。

## 第六次最小修正最终复审

复审结论：**PASS（允许推送该最小修正）**

B10–B12 已全部关闭：

- `validate_release_identity [expected_id]` 要求固定 tag、正整数 database ID、精确 uploads host/repository/同 ID/template；首次 discovery 后在任何 draft PATCH/delete/upload 前执行。
- 八项上传后的 `validate-uploaded-release` 会重新 discovery，并要求新 ID 与本轮初始 ID 完全相同；只有通过 identity、metadata、draft 状态、八附件和远端内容复验后才允许 publish PATCH。
- publish PATCH 后的 `validate-public-release` 再次 discovery，并以同一 expected ID 做正整数、tag 和 upload URL 复验；最终 metadata、`draft=false`、八资产及远端下载候选验证仍继续执行。
- mock 可在第二次 view 返回不同数字 ID 或字符串 ID；两例均在上传后失败、保持 `draft=true` 和八项附件，没有执行公开 PATCH。关闭注入后重试会清空重建，并最终得到 `draft=false`、八个唯一附件，证明身份切换不会被误发布且仍可安全恢复。
- 初次 draft 对字符串、0、负数、小数 ID 及坏 upload URL 的状态 hash 不变证据继续通过。

B11 通过消除可失败点而关闭：upload stderr 文件在 trap 安装前一次创建；upload 阶段立即设置固定首项 `starting`，每项循环在 `starting` 后直接进入包含 stderr 重定向的 CLI 调用，不再有 JSON 读取、`mktemp` 或删除命令。错误 token和七类别测试实际证明调用失败会从 `starting` 转为固定类别、保留原 exit且不会泄露 stderr；因此先前 `detail=none` 路径已从 upload 阶段移除。

最终复跑：完整发布状态机及所有身份/分类/部分重试测试 PASS；相关 shell `bash -n` PASS；`git diff --check` PASS。AI log 保持准确的 run `29177621742`、首项前失败边界和未完成状态。审核没有执行远端写操作。

允许推送并等待准确 main run。只有完整质量链、candidate、recovery、公开 Release 和真实八附件下载复验全部成功后，才可删除一次性 recovery job并进入最终发布证据复审。

## 第七次确定性重载修正审核

审核结论：**PASS（允许推送 by-ID 重载修正）**

### 真实边界与设计

run `29177897897` 已通过六个质量 job、candidate、recovery 只读校验以及八次逐项上传；唯一失败移至 `validate-uploaded-release`。该证据证明固定 absolute upload URL 与八个二进制请求有效，剩余不稳定点是上传后再次用 pending tag 查询 draft。修正保留首次 `release view` 只用于发现并锁定正整数 ID，之后改用确定性的认证 REST `GET repos/<固定仓库>/releases/<固定 ID>`。

### REST 兼容性与安全审核

- GitHub REST by-ID Release 响应使用脚本现有内部字段：`id`、`tag_name`、`name`、`body`、`draft`、`prerelease`、`upload_url`、`assets[].id/name/size/url`。因此不需要 GraphQL/CLI JSON 的 camelCase 转换；后续 identity、metadata、资产和下载验证可直接复用。
- endpoint 只由已验证的固定 repository 和正整数初始 `release_id` 构造，无 tag、响应 URL、用户输入或动态 latest 选择。`gh api` 仍使用同一短期 job token；查询失败返回固定 12 并由 `set -e` 阻断，不进入 identity、publish 或后续写操作。
- 上传后 by-ID 响应必须继续通过 expected ID、正整数、tag、upload URL、metadata、`draft=true`、八项资产与远端下载候选复验，才允许公开 PATCH。
- 公开 PATCH 后再次按同一 ID 查询，并要求 identity 连续、`draft=false`、精确八项及远端下载复验。整个后半段不再执行 pending-tag draft lookup。

### 测试证据

- mock 实现固定 by-ID GET，并返回 REST snake_case 结构。响应 ID 可注入不同数字或字符串；两例均在上传后 fail-closed，保持 draft、不公开，关闭注入后可清空并安全重试至八项唯一公开状态。
- 首次创建、既有 draft 恢复、公开幂等、metadata/asset 冲突、错误 discovery、错误 ID/URL、七类上传诊断、恶意 stderr、部分上传恢复等完整状态机继续 PASS。
- 相关 shell `bash -n` PASS；`git diff --check` PASS。AI log 准确记录 run `29177897897`、八次上传成功、validate-uploaded-release 失败及 by-ID 决策，没有提前宣称发布成功。

允许推送并等待准确 main run。只有完整 CI、recovery、公开 Release 和八项远端附件下载复验均成功后，才可移除一次性 recovery job并进入最终证据复审。

## 官方 GitHub CLI 上传恢复修正审核

审核结论：**PASS（允许推送以 `gh release upload` 替代 curl 的恢复修正）**

准确 main run `29176701107` 已证明只读身份和固定候选验证通过、失败局限在 `upload-assets`。本次未提交修正只替换该上传实现和对应 mock：生产脚本不再自行拼接 uploads host、Authorization header 或二进制请求，而是一次调用 GitHub 官方 `gh release upload <tag> <files>... --repo <owner/repo>`；发布前后的状态机、远端八资产精确验证和下载复验均未改变。

定向审核结果：

- **draft 兼容性成立。** GitHub CLI 官方 `upload` 实现先调用共享 `FetchRelease`。当前官方源码明确说明并实现“查找已发布 Release，或按 pending tag 查找 draft Release”：published 与 draft 两条查询并行，draft 通过 GraphQL 定位后再以 REST 获取详情，随后使用返回的 `upload_url` 上传。因此固定 tag 的已有 draft 是该官方命令的受支持路径，不依赖公开 `/releases/tags/...` 对 draft 的可见性。
- **状态机继续 fail-closed。** 脚本只在认证枚举确认不存在或恰有一个 draft 时进入上传；上传前先把 draft 保持为 draft 并清理旧附件。`set -euo pipefail` 使 `gh release upload` 任一非零立即退出，后续 metadata 验证和公开 PATCH 不会执行；即使官方 CLI 并发上传发生部分成功，Release 仍是 draft，下一次恢复会先重新枚举并清理全部残留附件。已有公开 Release 路径不会执行上传，只接受完整 metadata、八资产和远端候选复验全部匹配的幂等成功。
- **八资产仍为固定集合。** 生产 `assets` 数组仍精确包含 `CHANGELOG.md`、`GIT_SHA`、`SHA256SUMS`、双 SBOM、前端 tar、OpenAPI 和后端 JAR 八项。`upload_paths` 只能由该数组和固定 `asset_dir` 逐项构造，没有 glob、目录递归、workflow input 或动态远端名称。上传后 `validate_assets` 要求远端名称集合精确匹配且恰为 8 个非空附件，再下载每项并运行严格候选验证；缺失、额外、重复、空白或损坏均不能进入公开步骤。
- **严格 mock 与真实调用边界匹配。** mock 只接受 `gh release upload`，要求固定 `v0.1.0`、精确 `--repo owner/repo`、`GH_TOKEN` 等于注入的 mock token，并把所有其余参数当作必须存在且非空的附件路径；未知 flag 会被当作不存在文件而失败。随后同一生产逻辑重新枚举并验证远端精确八项和内容。首次创建、带陈旧/额外附件的 draft 恢复、已公开幂等，以及标题、正文、额外附件、API 故障、错误 token、错误 repository 均有正负向覆盖。draft recovery 的陈旧附件用例也覆盖了“先前部分上传后重试必须清理”的等价状态。
- **凭据边界改善且未泄露。** `GH_TOKEN` 只通过环境传给官方 `gh`，不再插值进命令参数、URL、header 或 curl 输出。固定 Actions annotation 仍只含脚本内 stage 与退出码；错误 token 测试确认 Actions/local 模式均保留同一非零状态，annotation 不含 token、Authorization、Content-Type 或附件内容。本轮 diff 的常见 token 模式扫描未发现凭据。

Reviewer 实际执行：

- `scripts/test-publish-github-release.sh`：PASS；首次创建、draft 恢复、公开幂等、四类既有冲突/API 失败、认证失败 annotation/local 和错误 repository 均符合预期。
- `bash -n scripts/publish-github-release.sh scripts/test-publish-github-release.sh scripts/verify-release-candidate.sh`：PASS。
- `git diff --check`：PASS。
- `./scripts/check.sh`：PASS；前后端和 OpenAPI 既有门禁无回归。
- GitHub CLI 官方 manual 确认命令签名为 `gh release upload <tag> <files>... [flags]` 且继承 `--repo`；官方 `cli/cli` 源码确认 draft 查询与官方 upload URL 路径。当前本机未安装真实 `gh`，因此没有用本地网络写操作冒充远端验证。
- 再次只读核验：`v0.1.0` 仍为 annotated tag，peeled commit 仍为 `52864b1aa72f56081abfc0bd146415d2a5f1ccb8`；公开 Release API 仍返回 `404`。审核没有创建、修改或发布 Release。

本 PASS 只授权推送当前两文件修正并等待其准确 main run。只有 recovery job 的固定身份、候选校验、官方上传、远端八附件下载复验和最终发布全部成功后，才能进入一次性 recovery job 删除与最终发布证据阶段。
