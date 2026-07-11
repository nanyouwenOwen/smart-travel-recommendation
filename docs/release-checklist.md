# MVP 发布清单

- [x] `scripts/check.sh`、MySQL 8.4 E2E 和容器 smoke 全绿
- [ ] 依赖审计无未处置 High/Critical
- [x] 备份恢复演练与回滚路径已核验
- [x] OpenAPI、README、CHANGELOG、已知限制同步
- [ ] JAR、前端静态包、SBOM 与校验和绑定同一 Git SHA
- [ ] 用户明确授权创建 `v0.1.0` tag 和 GitHub Release

自动化前置条件的提交、CI、安全门、备份恢复与候选产物证据见 [`reports/mvp-verification.md`](reports/mvp-verification.md) 和 [`reports/backup-restore-verification.md`](reports/backup-restore-verification.md)。当前安全门忽略了无修复版本漏洞，且 artifact 内部文件/GIT SHA/校验和尚未在 CI 内自校验，因此相应两项保持未完成。
