# Round 17 独立审核：发布收尾、恢复清理与最终证据

## 首审结论（2026-07-12）：FAIL

审核身份：独立 reviewer，仅审查与测试；未修改产品代码、workflow、测试实现或状态文档。审核基线为本地 `HEAD`/`origin/main` `197904ab7a101ab6baa86173c3180d53f6e4cc91` 加 Round 17 未提交 diff。

### 已验证的远端事实

- 本地 annotated tag 对象类型为 `tag`；本地及 `git ls-remote --tags origin 'refs/tags/v0.1.0*'` 均证明 `v0.1.0` peeled commit 为 `52864b1aa72f56081abfc0bd146415d2a5f1ccb8`，tag 对象为 `70d8c799d6f01f0c81972f6eae3bd8d8b4d3c098`。
- GitHub 公共 Actions API 证明 run `29195654260` 为 attempt `1`、`push/main`、完整 head SHA `197904ab7a101ab6baa86173c3180d53f6e4cc91`、结论 `success`。`openapi`、`frontend`、`backend`、`e2e`、`container-smoke`、`security`、`release-candidate`、`release-recovery-v0-1-0` 均为 `success`；tag-only `release` 在该 main run 中为 `skipped`。
- 同一 run 的公共 artifact 元数据为：候选 `smart-travel-assistant-0.1.0-rc`，56,333,935 bytes，digest `sha256:2322dc6f6ee0816b0d66363c2fdd84935169ba9428164d44887a3a6a89e95449`；性能 summary 为 1,332 bytes，digest `sha256:7565b9d932ff0e7b524b328a46e5a4af85644f01784df6141ee72b80ff7b2a3e`。对应 `container-smoke` 与 `release-candidate` job 均为 `success`。
- GitHub 公共 Release API 证明 ID `352766714` 的 tag 为 `v0.1.0`、target 为 `main`、标题为 `Smart Travel Assistant v0.1.0`、`draft=false`、`prerelease=false`，正文身份与 `docs/releases/v0.1.0.md` 的范围/限制相符。
- 公共 Release 附件集合精确为 `CHANGELOG.md`、`GIT_SHA`、`SHA256SUMS`、`backend-sbom.cdx.json`、`frontend-0.1.0.tar.gz`、`frontend-sbom.cdx.json`、`openapi.yaml`、`travel-assistant-api-0.1.0.jar`；八项均非零，无额外附件。全部下载至仓库外 `/tmp/round17-release-assets` 后运行 `scripts/verify-release-candidate.sh /tmp/round17-release-assets 52864b1aa72f56081abfc0bd146415d2a5f1ccb8`，文件集、SHA256、`GIT_SHA`、双 CycloneDX SBOM、JAR 与前端归档全部通过。

### workflow 与文档审查

- 实际 diff 只从 `.github/workflows/ci.yml` 删除 `release-recovery-v0-1-0`；七个普通质量/候选 job 和精确 tag-only `release` job 没有改动。删除内容覆盖 recovery 的 `contents: write`、固定 run/artifact/tag/SHA、跨 run 下载和发布脚本调用。
- 当前 workflow 顶层仍为 `contents: read`。唯一 `contents: write` 及 `publish-github-release.sh` 调用位于条件严格等于 `github.event_name == 'push' && github.ref == 'refs/tags/v0.1.0'` 的 `release` job；普通 main/pull request 的七个 job 没有 Release 写路径。
- `HUMAN_ACTIONS.md` 正确保留真实 Xiaomi MiMo/DeepSeek 凭据、全新 Codespace 人工验收与生产部署待办；没有把 mock 协议测试、开发环境配置或公开 Release 混同为这些人工验收。
- 产品/发布事实位于普通 `docs/`，AI 过程、人工操作和交接位于 `docs/ai-governance/`，分区正确；检查到的变更不含凭据、下载附件或生成物。

### 独立门禁

- `node scripts/test-release-workflow-boundary.mjs`：PASS。
- 使用仓库已安装 `yaml` 模块解析 `.github/workflows/ci.yml` 并检查 `jobs`：PASS。
- `git diff --check`：PASS。
- 对工作树执行 GitHub/OpenAI/Bearer 常见 token 形态定向扫描（排除依赖、target 与 `.git`）：无匹配。
- `scripts/check.sh`：PASS；OpenAPI minimal lint 只有既有 warning，前端 format/lint/type-check/43 tests/coverage/build 与后端 Spotless/compile/SpotBugs/52 tests/verify 均通过。

### Blockers

1. **B1 — 完成状态早于计划规定的准确收尾 CI。** `TODO.md` 已勾选“发布 MVP”，`PROJECT_HANDOFF.md`、发布清单和验证报告也无条件写成 recovery 已删除/MVP 已完成；但这些删除尚在未提交 diff，收尾 SHA 尚未推送，因而不可能已有该准确 SHA 的七 job 普通 CI 终验。计划“不可变边界”第 6 条明确要求：如果文档提交需要预先勾选，必须明确标记“等待本提交 CI 终验”。当前材料没有该限定，状态表述超前。修正后仍须在推送及准确 CI 后补录最终 SHA/run，并再次核验 Release 未变化。
2. **B2 — 新增静态测试没有证明关键门禁仍存在。** `scripts/test-release-workflow-boundary.mjs` 仅验证七个普通 job 的名称存在，并未按计划第 2 节要求断言“关键门禁”未弱化。至少应直接检查实际 workflow 中 `release-candidate.needs` 的六个质量依赖，以及性能 smoke/summary、前端 audit/coverage、后端 verify、安全漏洞与 secret 扫描、候选验证和 artifact 保留等关键约束。人工 diff 能证明本轮没有改动这些块，但不能替代计划明确要求的可重复静态回归检查。

修复 B1/B2 后，由同一 reviewer 重跑受影响检查并追加复审。本次 `FAIL` 不授权推送收尾提交，也不否定已经成立的公开 `v0.1.0` Release 事实。

## 同一审核端复审（2026-07-12）：PASS（授权推送，仍待准确 CI 终验）

实施端只针对 B1/B2 修正后，同一 reviewer 重新读取实际 diff 并复跑受影响门禁：

- B1 已关闭：`TODO.md` 明确 Release 已公开，但 recovery 清理仍须由本轮准确 SHA 的普通 CI 终验，失败则恢复为未完成；`docs/release-checklist.md`、`PROJECT_HANDOFF.md` 与 `AI_CHANGE_LOG.md` 同样明确当前是待提交工作树、不得在准确 CI 前引用为最终清理闭环。真实模型 key、Codespace 与生产部署边界仍保持未完成。
- B2 已关闭：`scripts/test-release-workflow-boundary.mjs` 直接解析实际 workflow，除 recovery/权限/Release 写路径负向断言外，现会断言 `release-candidate` 精确依赖六个质量 job，并锁定 frontend audit/coverage/build、backend verify、MySQL E2E、Compose smoke 与 `always()` 性能 summary、Trivy vuln/secret、候选自验及 artifact retention 等关键门禁。
- 复跑 `node scripts/test-release-workflow-boundary.mjs`：PASS。
- 复跑 workflow YAML 解析：PASS。
- 复跑 `git diff --check`：PASS。
- 人工复查 workflow diff：仍只有一次性 recovery job 被删除，七个普通 job 和 tag-only release job 的实现行没有增删；没有新增 Release 写入口、凭据、附件副本或产品代码改动。
- 首审完整 `scripts/check.sh` 的 PASS 仍适用于相同产品/workflow diff；复审新增变化仅为 Markdown 状态限定与独立 Node 静态断言，已由上述定向检查覆盖。

Round 17 本地实施/文档 diff 最终审核为 **PASS**，当前无 blocker，可以提交并推送。此 PASS 不替代计划要求的远端终验：必须等待收尾提交的准确完整 SHA 对应 run，确认 `openapi`、`frontend`、`backend`、`e2e`、`container-smoke`、`security`、`release-candidate` 七项单次全部成功、`release` 只被跳过且不再出现 recovery；随后再次确认 tag 与 Release ID `352766714` 的八附件未变化。只有完成该远端复核后才能把 Round 17/MVP 清理闭环作为最终完成。
