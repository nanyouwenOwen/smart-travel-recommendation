# Round 16：pending-tag draft 固定 Release ID 身份诊断与恢复计划

## 背景与准确证据

已授权的 `v0.1.0` annotated tag 必须继续指向固定提交 `52864b1aa72f56081abfc0bd146415d2a5f1ccb8`，一次性 `release-recovery-v0-1-0` 仍只能使用固定 run `29175974787` 的候选产物。Round 13 已把恢复状态机收紧为：首次通过 `gh release view v0.1.0` 严格发现 Release，锁定正整数 ID，上传后和公开后只通过该 ID 重载，并在公开前后下载验证固定八项附件。

准确 main run `29194958320` 已通过本轮质量链、固定候选校验及八项附件上传，恢复步骤仍失败于 `stage=validate-uploaded-release; detail=identity-tag`。因此本轮只处理“上传后按已锁定 ID 获得的 pending-tag draft 身份响应为何没有通过现有 tag/draft 判定”；不得把失败归因于附件、候选内容、权限、网络或 GitHub 内部行为，除非后续有对应证据。公共 Release 尚未完成，`TODO.md` 的“发布 MVP”继续保持未勾选。

现有失败 detail 仍把两项条件合并在一起：`tag_name` 经过 `.tag_name // ""` 后的值，以及 `draft` 经过 `.draft // false` 后是否严格等于布尔 `true`。它不能区分字段缺失、`null`、空字符串、预期 tag、非预期 tag，或字段 JSON 类型异常；也不能证明当前 REST 响应仍为 draft。Round 13 对“空 tag 且布尔 draft=true”的兼容测试已通过，但准确 run 再次失败，说明继续放宽条件没有证据基础。

## 不可放宽的安全边界

1. 首次 discovery 必须仍按精确 tag `v0.1.0` 成功，并在任何写操作前要求 tag 严格相等、ID 为正整数、upload URL 为固定 uploads host/目标仓库/同一 ID/template。API、认证、网络或解析错误不得当作 Release 不存在。
2. 首次通过后立即锁定正整数 Release ID。prepare、删除旧附件、上传、上传后验证、publish PATCH 与公开后验证只能作用于该 ID；任何 ID 类型异常或不连续立即失败。
3. draft 兼容只能发生在“首次严格 discovery 已通过、调用者携带同一 expected ID、固定 ID 响应被证明仍为 draft”的上传后边界。非空异 tag 永远拒绝；不得把 tag 缺失兼容扩展到首次发现、已公开 Release或幂等公开路径。
4. upload URL 必须始终精确匹配 `https://uploads.github.com/repos/<固定仓库>/releases/<同一ID>/assets{?name,label}`。不得接受重定向 host、其他仓库、不同 ID、额外路径或任意服务端 URL。
5. 公开前仍必须验证 `draft=true`、`prerelease=false`、固定标题与 notes 正文、精确八项附件、每项非零，并把每项远端下载后绑定固定候选 SHA 验证。任一失败均保留 draft，禁止 PATCH 公开。
6. 公开 PATCH 后必须按同一 ID 重载，严格要求 `tag_name == v0.1.0`、`draft=false`、`prerelease=false`、metadata 和精确八项附件全部一致，并再次远端下载验证。公开态不允许 tag 缺失、空值或类型兼容。
7. 不修改 annotated tag、不重建 tag、不替换候选 run、不删除或隐匿 Release、不增加重试取优、不将失败步骤设为可忽略，也不输出 token、notes 正文、API 原始响应或附件内容。

## 实施范围

### 1. 先加入有界、脱敏的真实响应分类诊断

在 `scripts/publish-github-release.sh` 的身份验证入口，对实际使用的 `release_json` 只计算并输出/写入 GitHub annotation 以下固定枚举，不打印原始 JSON：

- 验证阶段：`initial-discovery`、`uploaded-draft` 或 `published-release`；
- `tag_name` 字段：JSON 类型（`missing|null|string|other`）与值分类（`missing|null|empty|expected|unexpected`）；
- `draft` 字段：JSON 类型（`missing|null|boolean|other`）与值分类（`true|false|missing|null|invalid`）；
- ID：只输出类型分类、是否正整数、是否与 expected ID 相同，不输出任意服务端正文；固定 ID 本身如需关联可输出十进制值，因为它不是凭据；
- upload URL：只输出 `exact|mismatch|missing|invalid-type`，不得输出不受信任 URL；
- 当前固定 `stage`/`detail` 和退出码。

诊断必须由 `jq` 对字段是否存在、JSON 类型和值分别分类，不能先用 `//` 抹平 `missing`、`null` 和空字符串。所有分类值来自脚本内固定白名单；即使服务端返回恶意字符串、换行或 workflow command，也只能输出 `unexpected`，不得回显、插值或写入 annotation。成功路径不必输出诊断；失败 trap 只输出最后一次已安全生成的分类。临时文件继续由 trap 清理。

先提交这一诊断变化给独立 reviewer。审核 PASS 后推送并等待准确 main run；只有新的 run 自身再次通过质量链、候选验证和八项上传，且给出上述分类，才能决定第二阶段修正。不得根据 mock、GitHub 文档或旧 run 猜测真实字段形态。

### 2. 依据准确分类选择且只选择一个最小修正

实施终端把新 run ID、准确提交 SHA、失败 stage/detail 和固定分类记录到 `docs/ai-governance/AI_CHANGE_LOG.md`，不复制原始响应。然后按证据进入以下互斥分支：

- 若 `uploaded-draft` 明确为布尔 `draft=true`，且 tag 为 `missing`、`null` 或 `empty`：允许把这三种 pending-tag 表示统一视为“未提供 tag”，但仅限携带首次锁定 expected ID 的 draft 分支；非空异 tag 仍失败。首次 discovery 与公开后仍严格要求字符串 `v0.1.0`。
- 若 tag 为字符串 `expected`，但 draft 为 `missing`、`null`、非布尔或 `false`：不得把该响应当 draft，也不得发布。应先核对是否读取了错误响应/字段或 GitHub 是否已经改变 Release 状态；需要新的只读证据或人工决策时停止，不通过默认 `false/true` 绕过。
- 若 tag 为 `unexpected` 或非字符串、ID 不连续/非正整数、upload URL 不精确：保持 fail-closed，不做兼容修正；将其报告为身份冲突并要求人工检查该固定 ID Release。
- 若分类无法由现有响应可靠得到：只修诊断/解析，不改发布判定，再走一轮准确 run。

不得同时实现多个推测性兼容分支。任何生产条件修改都必须引用触发它的准确 run 证据，并保留独立 reviewer 可验证的条件边界。

### 3. 扩充状态机测试而不复制实现结论

扩展 `scripts/test-publish-github-release.sh` 的 mock，使固定 ID REST 响应能够分别表达字段 missing、JSON `null`、空字符串、预期字符串、非预期字符串、错误 JSON 类型，以及 draft 的 missing/null/布尔/错误类型。测试至少覆盖：

1. 初始 discovery 的 tag missing/null/empty/unexpected/非字符串全部在任何 PATCH、DELETE、上传前失败；
2. 上传后只有准确证据允许的 pending-tag 表示，在布尔 `draft=true`、同一正整数 ID、精确 upload URL 时能继续；另一种未被证据支持的表示仍应失败，除非 GitHub 实际证据证明它们语义等价并经 reviewer 同意；
3. 上传后非空异 tag、非布尔 draft、`draft=false`、ID 类型异常/不连续、upload URL 不匹配均不执行 publish PATCH；
4. 即使 draft 兼容路径通过，metadata、附件缺失/额外/零大小和远端内容不匹配仍阻止公开；
5. publish PATCH 后 tag missing/null/empty/unexpected/非字符串全部失败，且严格字符串 `v0.1.0`、同一 ID、`draft=false` 才能完成；
6. 已公开幂等路径继续严格验证 tag、metadata、八附件及下载内容；
7. 诊断对恶意 tag/URL/正文值只输出固定分类，不泄漏原值、token、notes 正文或 workflow command；本地模式与 Actions annotation 行为、原退出码优先级保持正确；
8. 部分上传失败仍保留 draft，重试清理并重建精确八项附件，既有七类上传错误脱敏诊断继续通过。

测试应记录 mock 是否发生 PATCH/DELETE/upload/publish，以证明负例在正确写边界前停止，不能只断言最终非零。运行 `bash -n`、完整发布状态机测试、`git diff --check` 和项目比例相称门禁；不得用测试 fixture 的自我断言替代对生产条件的人工审阅。

### 4. 文档与治理

实施终端仅在 `docs/ai-governance/AI_CHANGE_LOG.md` 记录任务摘要、准确 run 分类、采用或拒绝的兼容分支、测试与 reviewer 结论；不得记录 token、原始认证响应、notes 正文或私有日志。若状态机事实影响交接，再同步 `docs/ai-governance/PROJECT_HANDOFF.md`。产品 Release notes、API 契约和业务文档不因本诊断改变。

`TODO.md` 的“发布 MVP”在 Release 公开、八项远端附件验证、一次性 recovery 清理和最终 CI 全绿之前不得勾选。诊断成功或 draft 上传成功都不等于发布完成。

## 规划—实施—独立审核循环

1. **规划终端（本文件）**：只检查代码、Round 13/15 审计与准确 run 证据，定义诊断、允许分支和安全边界，不修改生产实现。
2. **实施终端**：先只实施安全分类诊断、测试及 AI log；经独立审核后推送取得准确远端证据。再仅实施该证据支持的最小条件修正、对应负例和治理记录。
3. **独立审核终端**：创建 `docs/reviews/round-16-release-pending-tag-identity-review.md`，不编辑实现。第一阶段审核诊断是否固定枚举、无泄漏、无判定放宽；第二阶段对照准确 run 分类审核实际 diff，只运行和报告测试，明确 PASS/FAIL 与 blocker。
4. **修正与复审**：实施终端修复 blocker，同一 reviewer 重跑受影响检查并追加复审。未 PASS 不得推送下一次发布写操作。

独立 reviewer 必须逐项确认首次严格 tag discovery、正整数 ID/连续性、精确 upload URL、draft 状态、metadata、八资产、远端下载、公开后严格 tag 均未被削弱；检查生产脚本没有输出原始响应或不受信任字段；核对准确 run 与提交 SHA，不接受相邻绿色 run 或 rerun 取优。

## 远端恢复与完成条件

1. reviewer 对最小修正最终 PASS 后推送普通 main 提交，不移动 `v0.1.0` annotated tag。等待该准确 SHA 的一次完整 CI；所有质量 job 与候选 job必须成功，performance summary 也必须属于该 run 并实际通过。
2. recovery 必须继续使用固定 tag、固定 peeled SHA、固定 source run/artifact。脚本首次严格发现同一 Release，上传并验证 draft 八附件后才允许公开；任一身份分类异常立即停止。
3. 通过认证 API和公开 API核验 Release 为 `draft=false`、`prerelease=false`、tag/target/title/notes 正确；从公开 Release 下载固定八项非零附件，运行候选验证并绑定 `52864b1aa72f56081abfc0bd146415d2a5f1ccb8`。
4. 上述事实成立后立即另走规划—实施—审核循环删除一次性 `release-recovery-v0-1-0`，更新交接、发布证据和 `TODO.md`。收尾提交不得再次写 Release，并等待其普通 CI 全绿。

Round 16 只有在安全诊断获得真实分类、证据支持的最小修正通过独立审核、准确远端 run 完整成功且公开 Release 八附件得到独立复验后才完成。若真实分类显示身份冲突或非 draft 状态，本轮应 fail-closed 并请求人工决策，不以“完成发布”为目标强行兼容。
