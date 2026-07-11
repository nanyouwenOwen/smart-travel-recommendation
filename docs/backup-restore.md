# 备份与恢复

运行 `scripts/backup.sh backups/travel-YYYYMMDD.sql.gz` 创建一致性压缩备份和 SHA-256。备份应加密、限制权限、异地保存并按组织策略轮换。定期在隔离的新 MySQL 实例演练恢复：确认校验和后执行 `CONFIRM_RESTORE=yes scripts/restore.sh BACKUP.sql.gz NEW_DATABASE`，再核对 `flyway_schema_history`、表数量和抽样业务标记。脚本拒绝把当前活动库作为恢复目标。

恢复会写入目标数据库，禁止对未备份的生产库直接执行。建议目标为每日备份（RPO 24h）和经演练确认的恢复时间；这不是当前项目提供的生产 SLA。
