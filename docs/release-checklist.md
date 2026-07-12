# MVP 发布清单

- [x] `scripts/check.sh`、MySQL 8.4 E2E 和容器 smoke 全绿
- [x] 依赖审计无未处置 High/Critical
- [x] 备份恢复演练与回滚路径已核验
- [x] OpenAPI、README、CHANGELOG、已知限制同步
- [x] JAR、前端静态包、SBOM 与校验和绑定同一 Git SHA
- [x] 用户明确授权创建 `v0.1.0` tag 和 GitHub Release

自动化前置条件的提交、CI、安全门、备份恢复与候选产物证据见 [`reports/mvp-verification.md`](reports/mvp-verification.md) 和 [`reports/backup-restore-verification.md`](reports/backup-restore-verification.md)。Round 10 提交 `0f1930b` 的 [GitHub Actions run `29166218083`](https://github.com/nanyouwenOwen/smart-travel-recommendation/actions/runs/29166218083) 已扫描当前 JAR 的所有 High/Critical 且不忽略 unfixed，并在上传前验证候选文件集、Git SHA、校验和、SBOM 与归档。

2026-07-12 已按用户授权完成 [GitHub Release v0.1.0](https://github.com/nanyouwenOwen/smart-travel-recommendation/releases/tag/v0.1.0)。Annotated tag peeled commit 为 `52864b1aa72f56081abfc0bd146415d2a5f1ccb8`；准确恢复 run [`29195654260`](https://github.com/nanyouwenOwen/smart-travel-recommendation/actions/runs/29195654260) 的质量链、候选和恢复任务全部成功。Release ID `352766714` 为公开、非 prerelease，固定八项附件均非空；从公开 URL 下载后由 `scripts/verify-release-candidate.sh` 完整复验并绑定上述 SHA。一次性 recovery job 已从普通 CI 删除。

收尾状态：上述“已删除”描述的是当前待提交工作树；仍须在独立审核 PASS 后推送，并由该准确 SHA 的七项普通 CI 单次全绿终验。终验前不得把仓库清理闭环当作已经完成；失败时恢复 `TODO.md` 为未完成并进入修正循环。
