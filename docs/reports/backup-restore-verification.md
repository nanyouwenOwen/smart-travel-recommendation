# 备份恢复验证

`scripts/compose-smoke.sh` 在隔离 Compose project 中注册唯一用户并创建行程、执行 single-transaction `mysqldump`、验证 gzip、恢复到全新 `restore_verify` 数据库，并核对唯一用户、行程和 `flyway_schema_history`。它还在演练后重启后端和 MySQL，只有后端健康且数据库用户能重新登录并获得有效 token 才通过。

最后实现变更 `40167645d58fc9ee2ce1bc84f7c3c2ce96230e88` 的 [GitHub Actions run `29165356764`](https://github.com/nanyouwenOwen/smart-travel-recommendation/actions/runs/29165356764) 与证据 HEAD `2eb538b710c18518ee3d8763bfb0ed8adacba118` 的 [run `29165523778`](https://github.com/nanyouwenOwen/smart-travel-recommendation/actions/runs/29165523778) 中，`container-smoke` 都已成功完成上述 MySQL 8.4 备份、全新库恢复、数据断言和重启恢复链路。本机 Docker socket 无权限，因此本地只验证脚本静态门与失败诊断路径，不冒充本地完成了容器成功演练。

脚本使用临时文件并在退出时清理，不包含备份内容、密码或用户隐私。生产恢复使用 `CONFIRM_RESTORE=yes scripts/restore.sh BACKUP.sql.gz NEW_DATABASE`，脚本拒绝已存在的目标数据库；实际生产操作仍必须先按 [`../backup-restore.md`](../backup-restore.md) 完成备份、隔离恢复和回切评审。测得 RTO/RPO 不代表生产保证。
