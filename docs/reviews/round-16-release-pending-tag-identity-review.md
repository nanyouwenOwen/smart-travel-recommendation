# Round 16 第一阶段独立审核：pending-tag 身份诊断

## 结论

**FAIL**。生产脚本的本轮 diff 静态上保持 fail-closed，新增诊断也只拼接固定分类；但新增测试尚未真实覆盖计划要求的恶意 URL、notes 与 token 泄漏边界，不能据此批准推送并触发下一次 Release 写操作。

本审核仅检查并运行门禁，未修改实现代码。

## 审核范围

- 计划：`docs/plans/round-16-release-pending-tag-identity.md` 第一阶段。
- 实现 diff：`scripts/publish-github-release.sh`。
- 状态机测试 diff：`scripts/test-publish-github-release.sh`。
- 治理记录：`docs/ai-governance/AI_CHANGE_LOG.md`。
- 基线：当前 `HEAD` 与工作区未提交 diff。

## 静态审阅结果

### 通过项

1. `phase` 的生产调用仅为 `initial-discovery`、`uploaded-draft`、`published-release`；annotation 的 `stage`、`detail` 与退出码仍来自脚本内部状态。
2. tag、draft、ID 与 upload URL 的诊断均由 `jq` 分类后写入；tag 的恶意原值不会被插值，URL 只输出 `exact|mismatch|missing|invalid-type`。
3. tag 与 draft 分类在诊断阶段使用 `has`、显式 `null` 和 JSON `type`，没有先通过 `//` 抹平 missing/null/empty。用于既有发布判定的 `//` 未在本轮改变。
4. 本轮没有放宽首次 discovery、上传后 draft 或公开后 Release 的身份判定。正整数 ID、expected ID 连续性与精确 uploads URL 检查仍在原位置；metadata、八项附件、远端下载验证、公开后严格状态验证也未被删除或设为可忽略。
5. cleanup 保存进入 trap 时的原始退出码并显式 `exit "$status"`；本地模式仍不输出 Actions annotation。
6. `AI_CHANGE_LOG.md` 只记录脱敏事实与实施决定，没有原始 API 响应、token、notes 正文或附件内容。

### Blocker

**B1 — 恶意输入脱敏测试覆盖不完整。** 新用例只通过 `MOCK_ID_GET_TAG_JSON` 注入恶意 `tag_name`，并证明该 tag 被归为 `unexpected`。固定 ID GET mock 随后无条件根据 repo/ID 重建 `upload_url`，所以测试中的 `upload=exact` 不是恶意 URL 分类证据；当前 fixture 根本无法向该响应注入恶意字符串、换行或 workflow command URL。该用例也使用普通 notes 与 `mock-token`，没有以独特哨兵断言身份失败 annotation 不包含 notes 正文和 token。

修复要求：扩展 mock，使上传后固定 ID 响应能注入原始 JSON 类型和值的 `upload_url`，至少执行一个包含换行/workflow-command 哨兵的恶意 URL 负例，断言 annotation 只出现固定 `upload=mismatch`（错误类型则为 `invalid-type`）且不包含原值；同时用独特 token 与 notes 哨兵执行身份失败，断言二者均不出现在输出。负例还必须断言 Release 保持 draft、未发生 publish PATCH。测试不得通过先清洗 fixture 输入来证明生产脚本脱敏。

## 门禁证据

- `bash -n scripts/publish-github-release.sh scripts/test-publish-github-release.sh`：PASS。
- `scripts/test-publish-github-release.sh`：PASS；包含既有首次 discovery、ID/upload URL、metadata、附件、远端内容、公开后严格身份、上传错误脱敏和重试覆盖，以及新增恶意 tag 分类用例。
- `git diff --check`：PASS。
- 对生产 diff 的人工对照：未发现身份或发布条件被放宽。

状态机测试当前全绿不消除 B1，因为缺失的恶意 URL/notes/token 路径没有被执行。

## 复审条件

实施终端只需补充上述 fixture 与负例，不应改动生产发布判定。修复后由同一 reviewer 重跑 `bash -n`、完整 `scripts/test-publish-github-release.sh`、`git diff --check`，并重新人工确认诊断输出仍只含白名单分类。复审 PASS 前不得推送本阶段诊断或触发新的恢复 run。

## 2026-07-12 复审

**PASS**。B1 已关闭，第一阶段安全分类诊断可推送以取得准确远端分类证据。

### 修复核对

1. 固定 ID GET fixture 新增 `MOCK_ID_GET_UPLOAD_JSON`，以 `fromjson` 将调用者提供的原始 JSON 值直接放入响应；fixture 未先清洗恶意 URL。
2. 恶意字符串 URL `https://evil.invalid/%0A::warning::url-secret` 实际进入生产身份验证，annotation 只报告 `upload=mismatch`；数值 `42` 实际进入同一路径，annotation 只报告 `upload=invalid-type`。
3. 上传后恶意 tag 用例继续只报告 `tag-type=string,tag-value=unexpected`。恶意 tag、恶意 URL、workflow command、独特 notes 哨兵 `notes-secret-sentinel` 与 token 哨兵 `identity-token-secret` 均被断言不出现在捕获输出。
4. 每个负例均为非零退出并断言持久化 Release 仍为 `draft=true`。mock 的 publish PATCH 会把 draft 改为 false，因此该状态也证明负例未越过公开写边界。
5. 复审 diff 中生产脚本与首次审核时相同；没有为测试增加 tag、draft、ID、upload URL、metadata、附件或公开状态兼容条件。

### 复审门禁

- `bash -n scripts/publish-github-release.sh scripts/test-publish-github-release.sh`：PASS。
- `scripts/test-publish-github-release.sh`：PASS，包括新增 `identity upload URL diagnostics are classified and redacted: PASS`。
- `git diff --check`：PASS。
- 人工复核生产 diff：首次严格 tag、正整数 ID、ID 连续性、精确 upload URL、draft 状态、metadata、固定八附件、远端下载与公开后严格 tag 均未削弱。

本 PASS 仅批准推送第一阶段诊断并等待该准确提交的远端 run；它不等于 Release 已发布，也不授权在没有新 run 分类证据时实施第二阶段兼容。

## 2026-07-12 第二阶段独立审核

**PASS**。准确远端分类支持“在已严格发现并锁定的同一 draft ID 上显式规范化授权 tag，再严格重载验证”的单一修正；实现没有接受或吞掉 `unexpected`，也没有扩大 pending-tag 兼容范围。

### 准确远端证据核对

- GitHub 公共 Actions API 返回 run `29195389783` 的完整 SHA 为 `4f984ba24636db78c908896b5aee3e811acea35e`，attempt 1，状态 completed。`backend`、`frontend`、`security`、`openapi`、`e2e`、`container-smoke` 与 `release-candidate` 均为 success；仅一次性 `release-recovery-v0-1-0` 为 failure。
- 该 SHA 的 recovery check run `86657651842` 公共 annotation 精确为：`stage=validate-uploaded-release; detail=identity-tag; ... phase=uploaded-draft,tag-type=string,tag-value=unexpected,draft-type=boolean,draft-value=true,id-type=number,id-positive=yes,id-match=yes,upload=exact`。这不是相邻 run、rerun 或本地 mock 结论。
- failure 位于上传循环完成后的固定 ID 重载身份验证；结合 `release-candidate=success`，该证据支持计划中拒绝 `unexpected` 并显式写回授权 tag 的分支，不支持把异 tag 当空 tag。

### 安全边界审阅

1. 初始 discovery 仍调用 strict tag 验证，且发生在 prepare、DELETE 和上传之前；API/解析错误仍不被视为不存在。
2. Release ID 仍由首次严格 discovery 锁定，并继续要求正整数、按 ID 重载时连续；新增 PATCH 全部只使用该 `release_id`。
3. prepare PATCH、上传后规范化 PATCH 与 publish PATCH 均显式携带脚本固定授权值 `v0.1.0`。上传后立即按同一 ID 重载并以 strict 模式要求 tag 精确相等；GitHub 若拒绝 PATCH、返回 missing/null/empty/异 tag 或错误类型，流程均非零停止。
4. 实现没有将准确证据中的 `unexpected` 加入接受集合。原先仅限 draft/expected-ID 的空 tag 兼容代码仍存在于函数默认分支，但三个生产调用点现全部显式传入 strict；当前生产路径不存在该兼容调用。
5. upload URL 仍严格绑定 uploads.github.com、固定 repo 与同一 ID；draft、prerelease、标题、notes、精确八附件、非零大小与逐项远端下载验证顺序未削弱。公开 PATCH 之后仍按同一 ID 重载，strict tag、metadata、公开状态、附件和远端内容均再次验证。
6. 没有移动 annotated tag、替换固定候选、重试取优、忽略失败或扩大日志输出。治理记录只保存固定分类与决策，不包含原始响应或凭据。

### 测试与门禁

- mock 的所有 Release PATCH 现在硬性要求 `tag_name=v0.1.0` 并把该值写回状态，覆盖 prepare、上传后规范化和 publish 三类 PATCH。
- `MOCK_DRAFT_PENDING_TAG_EMPTY=1` 会在每次 draft by-ID GET 中继续返回空 tag；该负例在严格规范化后仍失败并保持 `draft=true`，证明空/缺失表示没有被默许。
- 既有恶意非空 tag 用例仍失败并保持 draft；ID 类型/连续性、URL 错配、metadata/附件/下载、公开后严格性和上传失败恢复覆盖继续通过。
- `bash -n scripts/publish-github-release.sh scripts/test-publish-github-release.sh`：PASS。
- `scripts/test-publish-github-release.sh`：PASS。
- `git diff --check`：PASS。

### 结论边界

本 PASS 批准推送第二阶段最小修正并等待该准确 SHA 的完整远端结果。它不代表 Release 已公开；只有同一远端 run 通过严格 draft/公开验证及八附件远端复验后，才能进入 recovery 清理与 MVP 完成流程。
