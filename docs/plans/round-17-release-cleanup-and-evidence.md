# Round 17：v0.1.0 发布收尾、一次性恢复清理与最终证据计划

## 背景与已成立证据

Round 16 的准确远端 run `29195654260` 对应提交 `197904a`，质量链、候选产物与一次性 `release-recovery-v0-1-0` 均已成功。公开 GitHub Release `v0.1.0` 的 ID 为 `352766714`，状态为 `draft=false`、`prerelease=false`；固定八项附件已从公开地址下载，并由 `scripts/verify-release-candidate.sh` 验证为绑定发布提交 `52864b1aa72f56081abfc0bd146415d2a5f1ccb8`。这些事实满足正式发布本身的远端条件，但仓库仍保留具有 Release 写权限和写入逻辑的一次性 recovery job，`TODO.md` 的“发布 MVP”尚未勾选，交接与 AI 审计也尚未形成最终闭环。

本轮只做发布后的清理和证据同步，不改变产品代码、API 契约、发布 tag、Release、附件或候选内容。实施终端必须在写文档前重新核验上述公开事实；若实际状态与本计划不一致，应停止勾选完成并报告差异，不用计划文本替代现场证据。

## 不可变边界

1. annotated tag `v0.1.0` 不得移动、删除或重建，其 peeled commit 必须继续为 `52864b1aa72f56081abfc0bd146415d2a5f1ccb8`。
2. 不 PATCH、重建、删除或重新上传 Release `352766714`，不修改其标题、正文、tag、target、draft/prerelease 状态或八项附件。
3. 从普通 CI 中完整删除一次性 `release-recovery-v0-1-0` job 及其专属 Release 写入权限、固定恢复 run/artifact 参数和调用路径；不得保留另一条自动发布、恢复、上传或覆盖 Release 的等价路径。
4. 收尾后的 `push`/`pull_request` CI 只能读取源码、构建、测试、扫描和生成本次提交自己的候选 artifact，不得写 GitHub Release、tag 或远端仓库内容。保持 workflow 顶层最小只读权限，job 如无写入需求不得请求 `contents: write`。
5. 不提交 token、认证响应、私有日志、临时下载目录、Release 附件副本、性能 summary 或其他生成物；审计记录只保存可复核的非敏感标识、校验结果和链接/命令摘要。
6. “发布 MVP”只能在一次性 recovery 删除、独立审核 PASS、收尾提交推送且该准确提交的普通 CI 全绿后作为最终完成状态；若文档提交需要先勾选，应明确其为“等待本提交 CI 终验”，最终交付前再复核。

## 实施范围

### 1. 重新核验发布与候选证据

实施终端在改动前记录完整本地 `HEAD`、`origin/main` 和工作树状态，并使用公开 GitHub API/下载地址重新核验：

- annotated tag 对象类型与 peeled commit；
- Release ID `352766714`、tag `v0.1.0`、`draft=false`、`prerelease=false`、target、标题以及预期正文身份；
- 附件集合精确为计划中的固定八项，名称无缺失或额外项且每项大小非零；
- 下载到仓库外的临时目录后，运行现有候选验证脚本并确认绑定 `52864b1aa72f56081abfc0bd146415d2a5f1ccb8`。

只在以上结果完全一致时进入清理。记录 run `29195654260` 的完整提交 SHA、attempt、八个 job 结论及 performance summary/候选验证结论；不能只写短 SHA、相邻绿色 run 或笼统的“CI 通过”。临时下载内容在验证后清理，不加入 Git。

### 2. 删除一次性 Release recovery 写路径

从 `.github/workflows/ci.yml` 完整删除 `release-recovery-v0-1-0` job，包括：

- `contents: write` 权限；
- 对固定 source run/artifact、tag 和 peeled SHA 的检查；
- 下载跨 run 候选、调用 `scripts/publish-github-release.sh` 以及所有 Release API 写操作；
- 只为该 job 服务的环境变量、条件和依赖。

保留七个普通质量/候选 job 的既有门禁和依赖，不因清理改变测试、性能阈值、安全扫描、candidate 内容或 artifact retention。发布脚本与状态机测试可以作为历史可复用工具保留；本轮删除的是默认 CI 的自动写调用入口，不应顺手删除已经审核的发布实现或降低其测试覆盖。

新增定向静态检查或扩展现有 workflow 测试，至少证明：普通 workflow 不再包含 recovery job 名称、`contents: write`、Release 发布脚本调用、跨 run 固定 artifact 下载或其他 `gh release`/Release REST 写命令；同时仍包含原七个 job 及其关键门禁。检查应扫描实际 workflow，而不是复制一份期望文本后自证。

### 3. 同步发布状态和分区文档

在发布事实和 workflow 清理都成立的前提下同步以下材料：

- `TODO.md`：将唯一剩余项“发布 MVP”勾选；不得新增虚构的生产部署、真实 MiMo/DeepSeek key 验收或 Codespaces 人工验收完成项。
- `docs/release-checklist.md` 与既有 MVP 验证/发布报告：记录 tag、Release ID、准确 run、八附件公开复验和 recovery 已移除；清楚区分发布目标提交、触发恢复的 main 提交和本轮收尾提交。
- `docs/ai-governance/PROJECT_HANDOFF.md`：把“尚未授权/尚未发布”等过时描述改为已公开发布，记录剩余人工边界（真实 Xiaomi MiMo/DeepSeek 凭据与线上部署仍未完成，如事实仍如此）和后续普通修改流程。
- `docs/ai-governance/AI_CHANGE_LOG.md`：按 `track-ai-usage` 规范记录用户授权、AI 的恢复/诊断/清理贡献、曾出现的失败与修正、准确验证命令、review 结论和最终 CI；不复制私密提示词或凭据。
- 如 `docs/ai-governance/HUMAN_ACTIONS.md` 或其他交接入口仍把 GitHub Release 列为待办，只更新已由本次证据关闭的项目，保留真实模型密钥、供应商条款、生产部署等未完成的人工作业。

产品/工程发布证据仍放普通 `docs/` 主题文件；AI 决策、过程和模型交接只放 `docs/ai-governance/`。不得把同一长篇流水账复制到多个文件，入口文件应链接到权威证据。

### 4. 本地验证

实施终端至少运行并记录：

1. workflow YAML 解析和针对 Release 写路径的定向测试；
2. `git diff --check`、必要的 Markdown/链接检查与 secret 扫描；
3. `scripts/check.sh` 完整项目门禁，确认清理未破坏七个普通 job 对应检查；
4. `git diff` 人工核对，确认没有 tag/Release 操作、生产代码变更、生成物或无关文件。

任何检查失败都先修正并重新运行，不以文档变更为理由跳过。环境局限必须准确记录，不能把未运行写成通过。

## 独立审核与修正循环

由未参与实施的 reviewer 创建 `docs/reviews/round-17-release-cleanup-and-evidence-review.md`，只审核和测试，不编辑实现。审核至少包括：

1. 独立重验 tag、公开 Release 元数据、固定八附件集合/非零大小和候选 SHA 绑定，确认记录的 run、commit、Release ID 均精确；
2. 对实际 workflow diff 做负向审计，证明 recovery job、Release 写权限和所有普通 CI Release 写调用均已删除，且七个普通 job 与质量门禁未被削弱；
3. 核对 `TODO.md`、release checklist/验证报告、handoff、human actions 和 AI log，确认状态一致、文档分区正确、未虚构线上部署或真实供应商验收；
4. 独立运行定向 workflow 测试、YAML 解析、`git diff --check`、secret scan 和比例相称门禁，给出明确 `PASS`/`FAIL` 与 blocker。

实施终端修复 blocker，同一 reviewer 追加复审并重跑受影响检查。reviewer 最终 PASS 仅授权推送收尾提交，不替代远端准确 SHA 的 CI 终验。

## 推送与最终远端闭环

1. reviewer PASS 后提交并推送普通 `main` 收尾变更，记录完整 SHA；推送前后再次确认 `v0.1.0` tag 未变化。
2. 等待该准确 SHA 的 GitHub Actions 完成。预期只出现 `openapi`、`frontend`、`backend`、`e2e`、`container-smoke`、`security`、`release-candidate` 七个普通 job，全部必须一次成功；不得出现 `release-recovery-v0-1-0`，不得发生 Release 写入。
3. CI 后再次通过公开 API 核验 Release ID `352766714`、tag、draft/prerelease 状态和八附件集合保持不变，以证明普通 CI 未改写已发布版本。
4. 若准确收尾 CI 失败，保留真实证据，进入实施修正—同一 reviewer 复审—新准确提交/CI 循环；不得 rerun 取优、推空提交碰运气或引用前一 run 代替。
5. 全部成立后核对工作树干净、`HEAD == origin/main`，在最终交接中报告发布 URL、tag/Release/run/commit 证据、检查结果和仍需人工完成的事项。

## 完成定义

Round 17 和 MVP 只有同时满足以下条件才完成：

1. `v0.1.0` annotated tag 仍绑定 `52864b1aa72f56081abfc0bd146415d2a5f1ccb8`，Release `352766714` 公开且固定八附件经公开下载复验；
2. 一次性 recovery job 及普通 CI 中全部 Release 写权限/调用已删除，七个质量/候选 job 和门禁未被削弱；
3. `TODO.md` 已勾选“发布 MVP”，发布清单、验证报告、handoff、human actions 与 AI log 准确一致且无秘密；
4. 独立 reviewer 对实际 diff 和远端证据最终 `PASS`；
5. 收尾提交已推送，其准确 SHA 的普通 CI 七个 job 单次全部成功，且运行后 Release 内容未变化；
6. 工作树干净、`HEAD == origin/main`，没有提交临时附件、日志或生成物，真实模型凭据验收与线上部署等未完成边界仍被明确保留。
