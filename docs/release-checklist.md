# MVP 发布清单

- [x] `scripts/check.sh`、MySQL 8.4 E2E 和容器 smoke 全绿
- [x] 依赖审计无未处置 High/Critical
- [x] 备份恢复演练与回滚路径已核验
- [x] OpenAPI、README、CHANGELOG、已知限制同步
- [x] JAR、前端静态包、SBOM 与校验和绑定同一 Git SHA
- [ ] 用户明确授权创建 `v0.1.0` tag 和 GitHub Release

自动化前置条件的提交、CI、安全门、备份恢复与候选产物证据见 [`reports/mvp-verification.md`](reports/mvp-verification.md) 和 [`reports/backup-restore-verification.md`](reports/backup-restore-verification.md)。Round 10 提交 `0f1930b` 的 [GitHub Actions run `29166218083`](https://github.com/nanyouwenOwen/smart-travel-recommendation/actions/runs/29166218083) 已扫描当前 JAR 的所有 High/Critical 且不忽略 unfixed，并在上传前验证候选文件集、Git SHA、校验和、SBOM 与归档。前五项完成仅表示“可发布”，不表示 tag/Release 已创建。
