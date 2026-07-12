# Round 11：v0.1.0 正式发布计划

## 目标、授权与边界

用户已明确授权创建并推送 annotated tag `v0.1.0`、创建 GitHub Release，并附加候选产物。本轮在不改变 MVP 业务范围和 API 契约的前提下，把已通过质量门的 `0.1.0` 候选正式发布，并在发布后以可独立核验的证据完成 `TODO.md` 的“发布 MVP”。

本轮授权只覆盖上述版本的 tag、GitHub Release 和附件，不覆盖生产部署、仓库可见性/保护规则修改、真实 DeepSeek/Xiaomi MiMo 凭据、Codespaces 人工验收或其他版本。任何凭据都不得写入仓库、日志、Release notes 或附件。

当前基线为 `15e8eb57b077b86fa70194262e76ebe5a244df02`，其 GitHub Actions run `29166573782` 七个 job 全绿；发布清单前五项已满足，授权项和 `TODO.md` 的“发布 MVP”尚未勾选。实施前必须重新核对本地 `HEAD`、`origin/main`、工作树、远端 tag 和公开 Release，不能只依赖本计划中的快照。

## 发布原子性与提交模型

发布必须按以下状态机推进，不能先 tag 再补发布能力：

1. **发布机制提交**：先在 `main` 增加受限的 tag 发布 workflow、最终版发布说明及“授权已收到、尚未执行”的记录。授权清单项可以据真实授权勾选，但 `TODO.md` 仍保持未完成。
2. **准确提交审核与 CI**：独立 reviewer 对实际 diff 给出 `PASS`；推送普通提交后，该准确提交的 `openapi`、`frontend`、`backend`、`e2e`、`container-smoke`、`security`、`release-candidate` 必须全部成功。以这个完整 SHA 作为唯一发布提交。
3. **不可变 tag**：只在上述 CI 全绿后，对该准确发布提交创建 annotated `v0.1.0` tag 并推送。不得用 lightweight tag，不得把 tag 指向尚未通过 CI 的提交，也不得在失败后移动或强推同名 tag。
4. **tag run 原地构建并发布**：tag 触发的 workflow 从 tag 指向的 checkout 重新运行全部质量门、重新构建候选、在同一 run 内下载该 run 的 artifact、自校验后以 draft 方式组装 Release，附件全部验证成功后才转为非 draft。普通 `main` push 和 pull request 绝不能创建 Release。
5. **发布后证据提交**：Release 实际可访问且远端 tag/目标/附件/校验和均核验通过后，才在 `main` 的后续证据提交中勾选 `TODO.md` 和记录 Release 证据。该提交自然会使 `main` 领先 `v0.1.0`，这是预期状态；tag 永远保留在被发布的源代码快照，不能为了把发布后的记录纳入版本而移动 tag。

这样可以保证正式版本源码、tag run 构建产物和 `GIT_SHA` 指向同一提交，同时避免在尚无发布 workflow 或 CI 未通过时暴露半成品 Release。发布后的状态记录属于仓库运维历史，不改变已发布版本内容。

## 当前认证约束与解决方案

- 当前终端可通过已有 Git SSH 权限推送普通提交和 annotated tag，但没有可供本地 `gh`/REST API 使用的 GitHub Token；无认证下载 Actions artifact 会返回 `401`，本地也不能可靠创建 Release。
- 不要求用户提供或粘贴 Token。新增的 tag 发布 job 使用 GitHub Actions 自动注入的短期 `GITHUB_TOKEN`，并只在该 job 声明 `permissions: contents: write`；其余 workflow/job 保持 `contents: read`。
- 发布 job 使用同一 tag run 内的 `actions/download-artifact@v4` 获取 `release-candidate` job 刚上传的命名 artifact，避免依赖旧 run、公开 API 下载或长期凭据。
- 创建 Release 优先使用 GitHub-hosted runner 自带的 `gh` CLI 和 `GH_TOKEN: ${{ github.token }}`，避免引入新的第三方发布 Action。不得把 token 作为命令参数、输出到日志或传入产物。

## 实施设计

### 1. 扩展 CI 触发条件但隔离写权限

修改 `.github/workflows/ci.yml`：

1. 保留 `push.branches: [main]` 和 `pull_request`，另增加仅匹配精确 tag `v0.1.0` 的 `push.tags`。不用宽泛的 `v*` 自动发布未来未知版本。
2. 现有七个 job 在 tag run 中继续完整执行；不得因 tag 已创建而跳过 OpenAPI、前后端、E2E、容器 smoke、安全或候选自校验。
3. 新增 `release` job，条件必须同时要求 `github.event_name == 'push'`、`github.ref == 'refs/tags/v0.1.0'`，并 `needs` 已通过的 `release-candidate` 以及它的质量链。job 级别仅授予 `contents: write`；顶层仍为 `contents: read`。
4. checkout 使用完整 tag 历史（`fetch-depth: 0`），下载 artifact 时固定名称 `smart-travel-assistant-0.1.0-rc` 和固定目标目录，不使用“下载所有 artifact”或 glob 选择旧产物。

### 2. 发布前验证 tag、提交和产物

release job 在创建任何公开 Release 前必须失败即停，并验证：

1. `GITHUB_REF_NAME` 精确等于 `v0.1.0`，本地 `refs/tags/v0.1.0` 的对象类型为 `tag`，从而证明它是 annotated tag；`refs/tags/v0.1.0^{commit}` 精确等于 `GITHUB_SHA`。
2. 获取 `origin/main` 后确认 tag 目标是 `origin/main` 可达提交，防止发布游离/未合入代码。发布前的人工门另外要求它就是已记录的绿色发布提交；workflow 的 tag run 还会再次执行全部门禁，因此不以历史绿色 run 替代当前验证。
3. 对下载目录再次运行 `scripts/verify-release-candidate.sh <dir> "$GITHUB_SHA"`，要求固定八文件集合：`CHANGELOG.md`、`GIT_SHA`、`backend-sbom.cdx.json`、`frontend-0.1.0.tar.gz`、`frontend-sbom.cdx.json`、`openapi.yaml`、`travel-assistant-api-0.1.0.jar`、`SHA256SUMS`，无缺失、空文件或额外文件。
4. `SHA256SUMS` 必须覆盖七个 payload、不自包含，并由 `sha256sum -c` 实际复验；双 SBOM、JAR、前端归档和 `GIT_SHA` 继续满足现有自校验脚本约束。
5. Release notes 固定读取 tag 中的 `docs/releases/v0.1.0.md`；实施提交需将其从“发布候选/创建前提示”改为适合正式 Release 的准确说明，同时保留默认 Stub、真实模型未验收、公共数据 SLA、Codespaces 与 MVP 范围限制。

### 3. 以 draft 组装并原子公开 Release

为降低“Release 已公开但附件不全”的风险，发布 job 按以下顺序执行：

1. 查询 `v0.1.0` Release。首次执行时创建 `draft` Release，标题固定为 `Smart Travel Assistant v0.1.0`，正文使用版本化 notes 文件，关闭自动生成说明，且不得标记 prerelease。
2. 只上传上述固定八项白名单，不把整个工作区、临时目录、日志、`.env` 或任何凭据打包。上传命令显式列出每个路径。
3. 在 draft 状态通过 GitHub API/`gh` 核对 `tag_name`、draft/prerelease 状态和资产名称集合；每个资产大小必须大于零，资产集合必须无缺失、无额外项。下载 draft 附件到新的临时目录，再以下载到的 `SHA256SUMS` 执行 `sha256sum -c`，并运行候选验证脚本确认远端副本仍绑定 `GITHUB_SHA`。
4. 全部验证通过后才执行 `gh release edit v0.1.0 --draft=false`。随后再次核验 Release 非 draft、非 prerelease、tag 正确、八个资产仍完整且可下载。
5. 若上传或校验失败，job 失败并保留不公开的 draft 供诊断，绝不能转为公开 Release。重跑时只能复用目标 tag 相同的 draft，并对固定白名单执行可审计的覆盖上传；若发现同名 Release 已公开但内容不匹配、目标异常或存在额外附件，立即失败并进入人工审查，不删除、覆盖或移动 tag。若已公开内容完全匹配，重跑可以将其视为幂等成功，但仍要完整复验。

实现时可将上述发布校验和幂等逻辑提取到严格模式 shell 脚本并添加本地可测的纯校验部分；不得以复杂的内联 YAML 掩盖错误处理。若使用 `gh api`，只允许读取/更新当前仓库的 `v0.1.0` Release，所有 JSON 解析必须检查字段而非依赖输出文本位置。

### 4. 发布前文档同步

在打 tag 前的发布机制提交中：

1. `docs/release-checklist.md` 将“用户明确授权”勾选，并记录授权日期与“tag/Release 尚待 workflow 实际执行”；前五项引用更新为准确的当前绿色证据，但不能提前宣称已发布。
2. `docs/releases/v0.1.0.md` 改为正式 Release notes，功能、限制、运行/升级入口与校验方法和仓库事实一致。
3. `docs/ai-governance/AI_CHANGE_LOG.md` 记录用户授权、为何使用 tag workflow/GITHUB_TOKEN、原子发布设计及仍待执行的外部状态；如 `HUMAN_ACTIONS.md`、`PROJECT_HANDOFF.md` 或 README 仍称“未授权”，同步改成“已授权，待发布”。
4. `TODO.md` 的“发布 MVP”仍为 `[ ]`。在 tag 和 Release 被真实核验前，不得通过措辞把授权等同于完成。

## 独立审核、修正与复审

由未参与实施的 reviewer 创建 `docs/reviews/round-11-v0.1.0-release-review.md`，只审核/测试，不编辑实现。首次 tag 推送前至少检查：

1. 对照本计划、用户授权、实际 diff、现有候选验证脚本和 workflow，确认普通 push/PR 不具备发布路径，只有精确 `v0.1.0` tag 能运行写权限 job。
2. 确认 `contents: write` 仅存在于 release job；没有 `pull_request_target`、外部可控脚本、长期 token、secret 输出或把未经验证 artifact 上传到 Release 的路径。
3. 确认 release job 依赖完整质量链、artifact 来自同一 tag run，并独立检查 annotated tag/目标 SHA、main 可达性、固定白名单、远端附件下载复验和 draft 后公开顺序。
4. 对发布脚本的纯逻辑执行正向和关键负向测试：lightweight tag、错误 tag 名/目标、缺失/空白/额外附件、错误 `GIT_SHA`、损坏 checksum、错误/已公开冲突 Release 均必须安全失败；重跑 draft 的幂等路径不能产生重复或意外附件。
5. 核对 Release notes、清单、README/交接/AI 日志与真实状态一致，`TODO.md` 尚未勾选。
6. 运行 `git diff --check`、workflow YAML 解析、相关 shell `bash -n`、候选校验正负向测试及比例相称的仓库门。给出明确 `PASS`/`FAIL`；`FAIL` 时由实施终端修正，同一 reviewer 复审，reviewer 不直接修改实现。

只有 reviewer 最终 `PASS`，才允许提交并推送发布机制。该准确提交自身 CI 七个 job 全绿后，reviewer/实施方再核对 SHA、run 和远端 `main`，方可进入 tag 阶段。

## 正式执行顺序

1. 确认发布机制提交的完整 SHA 等于 `origin/main` 预期发布提交、工作树干净、对应普通 `main` CI 七个 job 全绿、远端不存在 `v0.1.0` tag 和 Release。
2. 创建 `git tag -a v0.1.0 <release-sha> -m "Smart Travel Assistant MVP v0.1.0"`；本地用 `git cat-file -t` 和 `git rev-parse v0.1.0^{commit}` 验证类型和目标后，通过 SSH 推送该单一 tag。
3. 等待由该 tag 触发的准确 Actions run。七个质量/候选 job 以及新增 release job 必须全部成功；任何失败都不能用历史 run 替代，也不能移动 tag。若失败，修复只能形成后续代码提交；由于公开版本 tag 不应移动，若失败发生在公开前，需要先判断是否能安全重跑同一 tag run（例如瞬时网络/draft 可恢复）。若源代码/发布机制本身有缺陷而必须改变 tag 目标，则停止并请求用户决定新版本号，禁止强推 `v0.1.0`。
4. 成功后独立核验远端：fetch tag 后对象类型为 `tag`；剥离目标等于发布 SHA；GitHub Release 为非 draft、非 prerelease且绑定 `v0.1.0`；资产名精确等于固定八项、均非空；重新下载附件并通过候选脚本及 checksum；Release 页面可公开访问。

## 发布后证据闭环

正式发布核验通过后，实施终端创建一个仅记录事实的后续提交：

1. 将 `TODO.md` 的“发布 MVP”改为 `[x]`。
2. 在 `docs/release-checklist.md` 增加实际 annotated tag、目标完整 SHA、Release URL、tag workflow run ID、release job 和资产/checksum 核验结果；不要把授权清单项重复解释为发布结果。
3. 更新 `docs/reports/mvp-verification.md`、`docs/ai-governance/PROJECT_HANDOFF.md` 和 `AI_CHANGE_LOG.md`，明确区分发布提交/tag SHA与发布后证据提交 SHA，并保留真实模型和全新 Codespaces 尚未人工验收的边界。
4. 由同一独立 reviewer 对远端事实和该文档 diff 做最终复审，在 review 文档追加最终 `PASS`。本地门通过后推送证据提交，并等待该提交自身 GitHub Actions 全绿。
5. 最终核验 `origin/main` 包含证据提交、工作树干净、`TODO.md` 已完成，同时 `v0.1.0` 仍指向原发布提交且 Release/附件仍完整。只有此时才能宣布 MVP 发布目标完成。

## 验证清单

实施及 reviewer 至少保留以下证据：

- `git diff --check`，workflow YAML 解析，新增/修改 shell 的 `bash -n` 与行为测试。
- `./scripts/check.sh` 以及发布机制准确提交的七 job 全绿 run。
- 普通 branch push/PR 上 `release` job 不运行；精确 tag run 上质量链、candidate 和 release 全绿。
- annotated tag 的 tag object、tagger/message、剥离目标完整 SHA及其在 `origin/main` 的可达性。
- tag run artifact 与 Release 八项附件的文件名、非零大小、`GIT_SHA`、`SHA256SUMS`、双 SBOM和归档校验；不记录或提交二进制附件本身。
- GitHub Release URL、tag、target、draft/prerelease 状态和准确 tag run ID。
- 发布后证据提交的独立复审、CI 全绿、干净工作树和远端同步状态。

## 完成定义

Round 11 和整个 MVP 只有同时满足以下条件才完成：

1. 发布机制经过独立审核，准确发布提交已推送且普通 `main` CI 七个 job 全绿。
2. 远端 `v0.1.0` 是 annotated tag，且不可变地指向该准确发布提交。
3. tag run 重新通过全部质量门；GitHub Release 已由最小写权限 job 创建，为非 draft、非 prerelease并绑定该 tag。
4. Release 只包含固定八项候选附件；远端下载副本的 Git SHA、checksum、SBOM 和归档均复验通过。
5. 发布后证据提交将 `TODO.md` 的“发布 MVP”勾选，记录准确 tag/Release/run/asset 证据，经过同一 reviewer 最终 `PASS` 并在 `main` CI 全绿后推送。
6. `origin/main`、本地工作树、tag 和 Release 状态一致；没有移动 tag、泄漏 secret、伪造真实模型/Codespaces/生产部署验收或遗留未公开但冲突的 draft。
