# 备份恢复验证

`scripts/compose-smoke.sh` 在隔离 Compose project 中注册唯一用户并创建行程、执行 single-transaction `mysqldump`、验证 gzip、恢复到全新 `restore_verify` 数据库，并核对唯一用户、行程和 `flyway_schema_history`。本机 Docker socket 无权限；GitHub Actions run `29163802316` 的 `container-smoke` 已成功完成该演练。

脚本使用临时文件并在退出时清理，不包含备份内容、密码或用户隐私。测得 RTO/RPO 不代表生产保证。
