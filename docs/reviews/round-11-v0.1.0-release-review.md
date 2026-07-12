# Round 11：v0.1.0 正式发布独立审核

## 结论

**首审 FAIL；修正后复审 PASS（最终结论）**。

产品测试和候选白名单本身通过现有门禁，但 Release draft 的发现逻辑与 GitHub API 契约不符，会使首次运行在创建 draft 后失败，并使后续重跑无法恢复；此外，已公开 Release 的幂等分支没有按计划验证标题和正文。两项均属于正式发布阻断。

## 审核范围与状态

- 计划：`docs/plans/round-11-v0.1.0-release.md`
- 实施 diff：`.github/workflows/ci.yml`、正式 Release notes、发布清单及 AI 治理/交接记录
- 基线：`15e8eb57b077b86fa70194262e76ebe5a244df02`
- 用户授权：文档已准确记录 2026-07-12 对 annotated `v0.1.0`、GitHub Release 和候选附件的明确授权
- 当前状态：`TODO.md` 的“发布 MVP”仍为 `[ ]`，符合“远端发布核验前不勾选”的约束
- reviewer 未实施代码、未修改远端、未创建 tag 或 Release；本文件是本轮唯一 reviewer 变更

## 阻断项

### R11-B1：draft Release 无法由当前查询路径发现，首次运行与重跑状态机必然断裂

严重度：**阻断**

workflow 用以下请求判断 Release 是否存在，并在创建 draft 后再次用同一路径取回：

```bash
gh api "repos/$repo/releases/tags/$tag"
```

GitHub 官方 REST 契约将 “Get a release by tag name” 定义为获取指定 tag 的 **published release**，并不提供按 tag 获取 draft 的保证。当前首次路径执行 `gh release create ... --draft` 后，紧接着调用该 endpoint；draft 尚未公开，因此会得到 404，job 在任何附件远端复验和公开动作之前失败。重跑仍无法从该 endpoint 找到遗留 draft，继而再次 `gh release create`，会因同 tag 已存在 Release 而冲突失败。这样既不满足“失败保留 draft 后可恢复”，也不满足“同一 tag 重跑幂等”。

修正要求：通过能列出认证用户可见 draft 的 API/CLI 路径（例如列举 releases 后按 `tag_name` 严格筛选且要求唯一），或保存创建结果中的 release ID 并始终通过 release-ID endpoint 操作；必须区分不存在、draft、published 和 API/权限/网络错误，不能把所有非零查询都当作“不存在”。修正后用 mock `gh` 覆盖至少首次创建、已有 draft 恢复、已公开一致、已公开冲突、查询故障五条控制流。

### R11-B2：已公开 Release 的“一致则幂等成功，否则安全失败”检查不完整

严重度：**阻断**

已公开分支只核对 `tag_name`、`prerelease`、八个资产名称/大小以及下载后的候选内容，没有核对计划固定的标题 `Smart Travel Assistant v0.1.0` 和版本化 notes 正文。一个附件完全正确但标题或正文被改错的公开 Release 会被报告为 `Published and remotely verified`，与计划中“若已公开内容不匹配立即失败”和 Release notes 准确性要求冲突。

修正要求：以精确、可复现的方式比较 `name` 与期望标题、`body` 与 `docs/releases/v0.1.0.md`（明确处理 API 尾随换行表现），并在公开前、公开后及已公开幂等路径统一执行。不得自动覆盖已经公开且不匹配的 Release。

## 已通过的设计检查

- 触发范围精确：仅 `push.tags: [v0.1.0]`；release job 另同时限定 push 事件与 `refs/tags/v0.1.0`。
- 顶层权限保持 `contents: read`，`contents: write` 只出现在 release job；未引入 `pull_request_target`、长期 token 或 secret 输出。
- release job `needs: [release-candidate]`，而 candidate 传递依赖六个质量 job；下载 artifact 名称和目录固定，来自同一 workflow run。
- tag 前置验证检查 tag object 类型、剥离 commit 等于 `GITHUB_SHA`，并检查该提交为 `origin/main` 可达。
- Bash 数组前缀展开已实测得到八个正确的 `release/<name>` 路径，不存在 `releaseNAME` 拼接错误。
- 固定资产集合为八项，和 `scripts/verify-release-candidate.sh` 的七项 payload 加 `SHA256SUMS` 一致；远端下载后调用同一验证器，会检查完整 SHA、无额外文件、非空、checksum、双 SBOM、JAR 与前端 tar。
- draft 验证成功后才转为非 draft，公开顺序的意图正确；但受 R11-B1 影响当前不可执行。
- Release notes 保留默认 Stub、真实 DeepSeek/Xiaomi MiMo 未验收、公共数据无生产 SLA、Codespaces 未人工验收以及 MVP 范围限制，未虚假宣称外部验收完成。
- 授权文档、发布清单、交接记录与当前“已授权、执行中、尚未发布”的状态一致。

## 验证记录

| 检查 | 结果 | 证据 |
|---|---|---|
| `git diff --check` | PASS | 无输出，退出 0 |
| workflow YAML 解析 | PASS | Python `yaml.safe_load` 成功 |
| `bash -n scripts/*.sh` | PASS | 退出 0 |
| `./scripts/check.sh` | PASS | OpenAPI 有既有 warning 但有效；前端 18 files / 43 tests 通过、构建通过；后端 Maven verify 退出 0 |
| 权限/触发静态扫描 | PASS | 精确 tag 触发；仅一处 `contents: write`；无 `pull_request_target` |
| 资产数组路径展开 | PASS | 八项均展开为 `release/<固定文件名>` |
| 本地候选复验 | 未运行 | 当前工作树无 `release/` 目录；验证器已由 Round 10 正负向覆盖，本轮 diff 未修改该脚本 |
| 远端发布/写操作 | 未运行 | reviewer 角色禁止远端修改；当前结论为 FAIL |

说明：环境没有 Ruby，因此最初的 Ruby YAML 命令不可用；随后使用已安装的 Python YAML 解析器完成等价语法检查。`./scripts/check.sh` 的实际最终退出码为 0。

## 复审入口

实施终端修复 R11-B1、R11-B2 后，应由同一 reviewer 复审实际 diff，并在本文件追加复审结果。复审至少要求：

1. draft 通过可靠、唯一且可认证访问的 ID/列表路径发现；查询故障不得误判为不存在；
2. mock `gh` 对首次创建、draft 重跑、published 一致、published 标题/正文/资产冲突、API 故障均给出预期结果；
3. 公开前后都核对 tag、draft/prerelease、标题、正文、固定资产和远端下载内容；
4. `git diff --check`、YAML 解析、Shell 语法、发布控制流测试及 `scripts/check.sh` 继续通过；
5. reviewer 最终 `PASS` 前继续保持 `TODO.md` 未勾选，且不得推送 tag/创建 Release。

## 修正后复审（2026-07-12）

### 最终结论

**PASS**。R11-B1、R11-B2 均已关闭。当前发布机制可以进入“普通提交 → `main` 准确提交 CI 全绿 → 核对远端无冲突 tag/Release → 创建并推送 annotated `v0.1.0` tag”的执行阶段。

本结论只批准已经审核的发布机制进入计划规定的后续门禁，不代表 tag、GitHub Release 或附件已经存在；在远端 tag run、公开 Release 和八项附件完成实际复验前，`TODO.md` 必须继续保持未勾选。

### 阻断关闭证据

#### R11-B1：已关闭

- `scripts/publish-github-release.sh` 使用认证的 `GET /repos/{owner}/{repo}/releases?per_page=100` 配合 `--paginate` 获取包括 push 权限调用方可见 draft 在内的 Release 列表，再用 `jq` 对 `tag_name` 做唯一筛选。
- 列表 API/管道失败返回独立错误码；零匹配才进入创建；多匹配安全失败，不会把权限、网络、JSON 或分页错误误判为不存在。
- 创建后重新从列表加载唯一 Release；之后 draft 更新、资产删除、上传和公开全部使用 release ID。重跑可发现并重建遗留 draft，不再依赖只保证 published Release 的 tag endpoint。
- draft 资产先按 ID 全部删除，再上传固定八项；任一步失败都会在公开动作前退出，下一次重跑可重新收敛。

#### R11-B2：已关闭

- `validate_metadata` 统一比较 `tag_name`、`prerelease`、固定标题和版本化 notes 正文；公开前、公开后及已公开幂等路径均调用。
- `validate_assets` 精确比较排序后的八项资产名并要求全部非空；额外资产、缺失资产和空资产均失败。
- 已公开 Release 不执行 PATCH、DELETE 或覆盖上传，只执行一致性与下载复验；标题、正文或资产冲突会安全失败，符合不可擅自修改公开 Release 的约束。
- draft 公开前下载八项远端副本并执行完整候选验证；公开后重新加载 Release，再次下载八项并执行 `scripts/verify-release-candidate.sh`，证明公开状态下资产仍完整、可下载且绑定准确 SHA。

### 发布状态机实测

reviewer 独立运行 `scripts/test-publish-github-release.sh`，测试在临时目录生成合法的最小八项候选和 mock `gh` API 状态，不访问或修改 GitHub 远端。实际结果：

| 路径 | 结果 |
|---|---|
| 无 Release：创建 draft、上传、两阶段复验、公开 | PASS |
| 已有 stale draft：修正标题/正文、删除 extra asset、重建并公开 | PASS |
| 已公开且完全一致：只读幂等复验 | PASS |
| 已公开标题冲突 | 预期失败 |
| 已公开正文冲突 | 预期失败 |
| 已公开额外资产冲突 | 预期失败 |
| Release 列表 API 故障 | 预期失败 |

测试同时使用真实 `scripts/verify-release-candidate.sh` 验证 mock 上传后及下载后的 SHA256、`GIT_SHA`、双 SBOM、JAR 和前端 tar，而不是仅检查命令调用次数。

### 最终门禁结果

| 检查 | 结果 |
|---|---|
| `git diff --check` | PASS |
| Python YAML 解析 `.github/workflows/ci.yml` | PASS |
| `bash -n scripts/*.sh` | PASS |
| 两个发布脚本 executable bit | PASS |
| `scripts/test-publish-github-release.sh` | PASS，3 条正向及 4 条安全失败路径符合预期 |
| `./scripts/check.sh` | PASS；OpenAPI 有既有 warning 但契约有效，前端 18 个测试文件/43 项测试通过并构建成功，后端 Maven verify 退出 0 |
| `TODO.md` 发布状态 | PASS，仍为 `[ ] 发布 MVP` |

### 后续执行约束

1. 先提交并推送当前准确 diff，等待该 `main` 提交的七个既有 CI job 全绿；普通 branch run 的 release job 必须不运行。
2. 在创建 tag 前核对本地/`origin/main` SHA、工作树、远端 tag 和 Release 冲突状态；tag 必须为 annotated 且指向该准确绿色提交。
3. 推送 tag 后只接受该 tag run 自身的全部质量链、candidate 和 release job 成功，不得以历史 run 替代。
4. 发布后按计划独立核验远端 tag object/目标、公开 Release 状态和八项下载副本，再提交 `TODO.md` 与证据文档；由同一 reviewer 做发布事实终审。
5. 如果 tag run 暴露必须修改源码的缺陷，不得移动或强推 `v0.1.0`；停止并按计划请求新的版本决策。

## Tag 推送前放行核对（2026-07-12）

### 结论

**PASS — 放行创建并推送 annotated `v0.1.0` tag。**

准确发布提交现为 `52864b1aa72f56081abfc0bd146415d2a5f1ccb8`。该提交包含已审核的 Round 11 发布机制，以及已经独立审核通过、只增强 container smoke 失败诊断而不改变发布状态机的 Round 12 变更。

### 核对证据

- 核对前 `HEAD`、本地 `main` 和 `origin/main` 均为完整 SHA `52864b1aa72f56081abfc0bd146415d2a5f1ccb8`；工作树干净。
- GitHub Actions run [`29175831503`](https://github.com/nanyouwenOwen/smart-travel-recommendation/actions/runs/29175831503) 为该准确 SHA 的 `push` run，整体 `completed/success`。
- GitHub Actions REST jobs 证据显示 `openapi`、`frontend`、`backend`、`e2e`、`container-smoke`、`security`、`release-candidate` 七项均为 `completed/success`；`release` 为 `completed/skipped`，证明普通 `main` push 未进入写发布路径。
- `git ls-remote --tags origin refs/tags/v0.1.0 refs/tags/v0.1.0^{}` 无输出；GitHub Git refs API 对 `refs/tags/v0.1.0` 返回 404，远端不存在冲突 tag。
- GitHub Releases tag API 对 `v0.1.0` 返回 404，当前不存在冲突的公开 Release。认证 workflow 在真正执行时仍会通过 releases 列表检查可能的 draft，并对零/一/多匹配采取已审核的安全分支。
- 从准确提交读取的 workflow 仍仅由精确 `v0.1.0` tag 触发 release job；job 仍传递依赖完整质量链、只在该 job 授予 `contents: write`，并继续验证 annotated tag、目标 SHA、`origin/main` 可达性、同 run artifact 和固定八项附件。
- `git diff --check` 通过；本次 reviewer 只追加本审核记录，没有修改发布实现或远端状态。

### 放行边界

实施终端现在可以对准确提交 `52864b1aa72f56081abfc0bd146415d2a5f1ccb8` 创建 annotated tag，注释信息按计划固定，并只推送该 tag。放行不允许移动/强推 tag，也不允许跳过 tag run。tag run 的七项质量 job、`release-candidate` 和 `release` 必须全部成功；之后仍需核验 tag object/剥离目标、公开 Release 状态以及下载后的八项附件，才能勾选 `TODO.md`。
