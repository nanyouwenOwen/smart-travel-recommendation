# Round 08：Compose Smoke 确定性与失败诊断独立审核

## 结论

**PASS（实现与本地可执行门；完整成功容器路径待准确提交的 CI 证明）**

当前实现已经改善阶段定位、失败诊断、数据库恢复登录语义和退出码保留。首次审核发现的可执行位问题与首次复审发现的 OR-list/`errexit` 控制流问题均已修复，并由同一 reviewer 定向实跑通过。没有剩余实现阻断；成功容器路径仍以推送后该准确提交的 GitHub Actions `container-smoke` 为准。

## 审核范围

- 计划：`docs/plans/round-08-compose-smoke-reliability.md`
- 实现：`scripts/compose-smoke.sh` 当前工作树差异
- 相关约束：`.github/workflows/ci.yml`、`scripts/perf.sh`、`docs/openapi.yaml` 中的行程状态枚举
- 本轮 reviewer 未修改实现文件。

## 已通过的审查项

1. `set -euo pipefail` 保留。`EXIT` trap 先保存 `$?`，非零时采集诊断，随后删除两个临时文件并执行 `compose down -v`，最终以原状态退出；诊断与清理中的 best-effort 命令不会覆盖根因。
2. `INT`/`TERM` 分别转换为 130/143，再进入统一 `EXIT` cleanup。通过无 Docker 副作用的函数级定向测试，实际得到 `INT=130`、`TERM=143`、普通 `exit 7` 仍为 7。
3. 诊断只输出 `compose ps -a`、容器 status/health 和三个服务各 40 行末尾日志；没有输出 Compose 环境、容器 inspect 全量配置、Authorization header、token 或认证响应全文。日志仍属 CI 内部运行日志，应继续遵循现有日志访问权限与保留策略。
4. 启动、后端重启健康和 MySQL 恢复均为有上限轮询；连接错误和非 2xx 只在这些明确恢复窗口内被接住。注册、地点、创建行程、SSE、备份数据、gzip、非 root 等一次性业务断言没有增加 `|| true` 或降低条件。
5. 最终未达到 `READY` 会非零退出；合法的 `DRAFT`/`GENERATING` 与畸形/未知状态在 `case` 中显式分支。`FAILED`、解析失败、缺失与未知状态都显式结束外层函数，不依赖 OR-list 中的 `errexit`。
6. 数据库恢复登录只有同时满足 HTTP 2xx 和可由 `jq -e` 读取的非空 `data.accessToken` 才成功；401、5xx、空体和错误页只能在 60 次窗口内重试，不能最终误判通过。
7. SSE 仍在 `pipefail` 下要求流请求和 `grep` 终态断言共同成功；备份仍执行 gzip 校验和三个恢复库数据阈值；backend/frontend 非 root 断言未放宽。
8. 性能前置健康检查独立命名；`scripts/perf.sh` 非零会立即触发 `fail`，后续重启恢复阶段不会执行。k6 脚本、阈值和网络模式未更改。

## 首次审核阻断项及关闭证据

### 1. `FAILED`、畸形或未知行程状态被错误地重试（已修复）

位置：`scripts/compose-smoke.sh` 的 `wait_for_trip`。

修正后的 `case` 在 `FAILED`、缺失/畸形及未知状态分支调用 `fail`，但 `fail` 只是一个执行 `return 1` 的 helper。实际调用点是 `wait_for_trip || fail ...`，Bash 会在这个 OR-list 条件上下文中关闭 `errexit` 对整个函数体的自动退出效果；因此分支中的 `fail` 返回后，函数继续执行 `sleep` 和下一次循环，而不是立即返回。

定向 mock 实跑证实 `FAILED` 响应连续打印了 30 次 `Trip entered FAILED state`，最后才超时返回。这仍把确定性的终态/契约错误纳入重试窗口。

实施方已在 `FAILED` 和兜底分支的 `fail` 后显式 `return 1`。同一 mock 复跑结果：`FAILED`、非 JSON、未知状态均只输出一次错误并立即返回 1；`READY` 返回 0。此项关闭。

### 2. `scripts/compose-smoke.sh` 丢失可执行位（已修复）

首次审核时 diff 包含 `mode change 100755 => 100644`。实施方已恢复 executable bit；最新 `git diff --summary` 不再包含 mode change，工作树权限可执行。此项关闭。

## 已执行证据

- `bash -n scripts/compose-smoke.sh scripts/perf.sh`：PASS。
- `git diff --check`：PASS。
- 使用占位 AI 环境值执行 `docker compose config >/dev/null`：PASS。
- 信号/普通退出函数级测试：PASS，得到 130、143、7，确认统一 cleanup 保留状态。
- 对照 `docs/openapi.yaml`：行程状态契约为 `DRAFT`、`GENERATING`、`READY`、`FAILED`；最终分支集合与契约一致，错误分支显式结束外层函数。
- 定向代码审查确认性能失败传播、SSE pipeline、备份恢复阈值、非 root 和登录 token 条件均未弱化。
- 首次修正复审：`bash -n`、`git diff --check`、可执行位均 PASS；mock `FAILED` 响应却得到 30 次错误日志，证明错误分支没有立即退出，因此总体结论仍为 FAIL。
- 第二次修正复审：相同 mock 下 `FAILED`、非 JSON、未知状态分别返回 1 且各只有一次错误，`READY` 返回 0；`bash -n`、`git diff --check` 和 executable bit 再次 PASS。实现审核最终结论更新为 PASS。

本机 Docker daemon 无权限，因此 reviewer 未把本地成功容器 smoke 作为证据，也不因该环境限制单独判定失败。实施方已在失败路径实跑并确认诊断与清理；完整成功路径必须由推送后、准确提交对应的 GitHub Actions `container-smoke` 证明。这里的 PASS 允许进入提交和 CI 阶段，不替代计划完成定义中的全部 required/dependent jobs 绿色证据；若 CI 失败，必须回到同一修正—复审循环。

## 远程最终复核

**最终结论：PASS**

同一 reviewer 于证据补录阶段核验了公开的 [GitHub Actions run `29165356764`](https://github.com/nanyouwenOwen/smart-travel-recommendation/actions/runs/29165356764)：

- 页面标题、提交链接和完整 SHA 对应 `40167645d58fc9ee2ce1bc84f7c3c2ce96230e88`（短 SHA `4016764`，`ci: harden compose smoke diagnostics`），没有用旧绿色 run 替代本轮提交。
- run 总状态为 completed successfully；`openapi`、`frontend`、`backend`、`e2e`、`container-smoke`、`security`、`release-candidate` 七个 job 均显示完成且成功。
- 页面列出 artifact `smart-travel-assistant-0.1.0-rc`，digest 为 `sha256:0d18f4cf11ff673b4869f71a85bebf899bf17594b8ddae9149cb334a8dd05b64`。
- `docs/ai-governance/PROJECT_HANDOFF.md` 与 `docs/ai-governance/AI_CHANGE_LOG.md` 补录的提交、run、七 job 全绿及 artifact digest 与公开页面一致；未发现把 tag、GitHub Release 或部署误写为已完成。

因此计划要求的准确提交成功容器路径与全部 required/dependent jobs 证据已经补齐，Round 08 审核测试最终全部通过。证据文档补录本身仍应按仓库流程提交并等待对应 CI；若该纯文档提交出现新的 required job 失败，应重新进入修正—复审循环。
