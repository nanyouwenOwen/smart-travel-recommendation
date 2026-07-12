# Round 12：发布前 Compose smoke 诊断独立审核

## 结论

**FAIL（首次审核；不得提交、推送或创建 `v0.1.0`）**

变更保持了原有十阶段业务断言、顺序、等待窗口、超时、性能阈值和重试条件，GitHub workflow-command 的单行转义及普通失败、管道失败、信号退出和成功静默路径也通过了现有定向测试。但是，“annotation、diagnostics 或清理自身失败时仍保留最初退出码”这一计划要求尚未由实现和测试可靠满足。当前测试对所谓二次失败只执行 `false || true`，没有让任何真实摘要、诊断或清理步骤失败；实际 `cleanup` 仍存在未隔离的 `diagnostics` 和 `rm -f`。这是发布前失败诊断的核心不变量，属于阻断项。

本 reviewer 只创建本审核文档，没有修改实现文件。

## 审核范围

- 计划：`docs/plans/round-12-release-ci-smoke-diagnostics.md`
- 实现：`scripts/compose-smoke.sh`、`scripts/lib/smoke-diagnostics.sh`
- 测试：`scripts/test-smoke-diagnostics.sh`
- 审计记录：`docs/ai-governance/AI_CHANGE_LOG.md`
- 基线：当前 `HEAD` 的 `scripts/compose-smoke.sh`，以及 Round 08 的 Compose smoke 审核结论

## 已确认通过

1. `compose-smoke.sh` 仍使用 `set -euo pipefail`；Stage 1–10 的原命令顺序未改变，只在各阶段日志前增加固定的 `current_stage` 和安全 `last_observation`。对基线逐行 diff 后，未发现断言、等待次数、sleep、curl 超时、k6 调用、性能阈值、恢复条件或重试分支发生变化。
2. EXIT trap 第一条语句仍保存 `$?`，随后移除 EXIT/INT/TERM trap，避免递归；INT/TERM 仍分别转换为 130/143。定向实跑得到普通 `false=1`、pipeline `=1`、自定义 `exit 23=23`、INT `=130`、TERM `=143`。
3. GitHub annotation 只包含固定字段 `stage`、`last`、`exit`。`%`、CR、LF 和 `:` 被编码；异常观察值 `$'bad%\r\n::value'` 只产生一条 annotation，不能注入第二条 workflow command。字段来源没有 token、Authorization header、密码、响应正文或容器日志。
4. 非 GitHub 环境输出普通摘要且不伪造 `::error`；成功 harness 没有 error annotation 或其他输出。
5. 原有 diagnostics 仍有界为 Compose `ps`、三个容器的 status/health 和各服务末尾 40 行日志；没有扩大日志范围或上传新 artifact。
6. `scripts/test-smoke-diagnostics.sh`、`bash -n scripts/*.sh scripts/lib/*.sh`、`git diff --check` 均通过。
7. `./scripts/check.sh` 通过：OpenAPI 有既有 warning 但描述有效，前端格式、lint、类型、43 项测试/覆盖率和构建通过，后端格式、静态检查、43 项测试及覆盖率通过。

## 阻断项

### 1. 二次失败不覆盖根因的实现隔离不完整

`cleanup` 中摘要调用已有 `|| true`，Compose down 也已有 `|| true`，但 `diagnostics` 函数调用和 `rm -f "$backup" "$response"` 没有在 cleanup 边界显式 best-effort 隔离。该 trap 在严格 shell 模式下运行；如果诊断输出本身失败（例如日志/标准错误写入失败），或临时文件删除返回非零，trap 可以在最后的 `exit "$status"` 前中断，因而不能证明最终进程始终返回最初状态。

计划明确要求 annotation/diagnostics 或容器清理自身失败不得覆盖原始退出码。请在 cleanup 边界对所有次级诊断和清理操作做明确隔离，同时继续保留最先捕获的状态；不得通过关闭主流程严格模式或弱化 smoke 断言实现。

### 2. “二次失败”测试没有触发被声明的失败面

`cleanup_test` 内的 `false || true` 是一个与 `smoke_failure_summary`、diagnostics、临时文件清理和 Compose 清理均无关的固定成功 OR-list。它只能证明显式忽略的 `false` 不改变状态，不能证明任何实际 cleanup 子步骤失败时保留原始状态。因此 `AI_CHANGE_LOG.md` 中“二次失败不覆盖原状态”的证据描述目前强于实际测试。

请加入真实可执行的注入/替身测试，至少让摘要失败、diagnostics 失败和清理失败分别发生，并断言最终状态仍是首次失败码；同时保留成功静默、INT/TERM、直接命令、pipeline 和注入转义覆盖。测试开关或 helper 必须默认不影响生产 CI。

## 复审要求

实施终端修复上述两项后，由同一 reviewer：

1. 审核 cleanup 的每个二级动作及其返回码隔离；
2. 亲自运行扩展后的 `scripts/test-smoke-diagnostics.sh`，确认实际触发摘要、诊断和清理失败；
3. 重跑 `bash -n scripts/*.sh scripts/lib/*.sh` 与 `git diff --check`；
4. 对受影响 diff 再确认十阶段顺序、断言、阈值、等待和重试均未改变；
5. 在本文件追加复审结论。

在复审 PASS、普通提交推送且该准确 `main` GitHub Actions 七个质量/候选 job 全绿之前，Round 11 tag 与 GitHub Release 继续禁止创建。

## 第一次修正复审

**结论：PASS（实现与本地门禁；准确 `main` CI 仍待推送后验证）**

实施终端已关闭两项首审阻断：

1. `scripts/lib/smoke-diagnostics.sh` 新增通用 `smoke_best_effort`，以 OR-list 明确吸收被调用动作的非零状态；production `cleanup` 现在分别在摘要、diagnostics、临时文件删除和 Compose down 四个边界调用它，最后仍以 trap 入口捕获的 `status` 显式退出。没有关闭主流程的 `set -euo pipefail`，也没有改变主 smoke 的失败传播。
2. 定向 harness 不再使用无关的 `false || true` 冒充二次失败。它分别让真实摘要 wrapper、diagnostics wrapper 和资源清理 wrapper 返回 91、92、93，并在首次 `exit 37` 下逐一实跑；三个场景最终状态均为 37。`AI_CHANGE_LOG.md` 已相应收窄并准确描述这三类注入证据。

同一 reviewer 亲自复跑并确认：

- `scripts/test-smoke-diagnostics.sh`：PASS；普通直接失败、pipeline、自定义 23、INT 130、TERM 143、workflow-command 转义、非 GitHub 摘要、成功静默及三类二次失败均通过。
- `bash -n scripts/*.sh scripts/lib/*.sh`：PASS。
- `git diff --check`：PASS。
- 修正后再次对照基线 diff：Stage 1–10 顺序、业务断言、curl 超时、轮询次数、sleep、k6 阈值调用、恢复条件及重试分支均未改变；修正只影响失败后的 best-effort 诊断与资源回收边界。
- 首审已执行的 `./scripts/check.sh` 全项目门禁不受本次纯 shell cleanup 修正影响，结果仍有效。

因此 Round 12 实现与本地测试审核最终通过，可以提交并推送普通 `main` 变更。此 PASS 不替代计划要求的准确远端运行证据：必须等待该提交对应的 GitHub Actions 中 `openapi`、`frontend`、`backend`、`e2e`、`container-smoke`、`security`、`release-candidate` 七个 job 全绿；若失败，必须依据新 annotation 继续新一轮修正。该准确 run 全绿之前仍不得创建或推送 `v0.1.0`，也不得创建 GitHub Release。
