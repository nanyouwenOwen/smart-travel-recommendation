# 备份恢复验证

`scripts/compose-smoke.sh` 在隔离 Compose project 中注册唯一用户、执行 single-transaction `mysqldump`、验证 gzip、恢复到全新 `restore_verify` 数据库，并核对 `flyway_schema_history`。本机 Docker socket 无权限，实际容器验证由 GitHub Actions `container-smoke` job 执行；只有该 job 成功后本报告视为通过。

脚本使用临时文件并在退出时清理，不包含备份内容、密码或用户隐私。测得 RTO/RPO 不代表生产保证。
