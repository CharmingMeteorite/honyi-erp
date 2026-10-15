# MySQL 备份与安全恢复

当前开发目标只允许 `192.168.1.247:3307/erp`，实际 MySQL 8.0.46、Flyway 最高 V18。`scripts/backup-mysql.ps1` 固定该主机、端口和库；从进程环境或 Git 忽略的根 `.env` 读取 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`，并拒绝不匹配的 JDBC URL。脚本需要本机 MySQL 8 `mysqldump.exe`，使用临时客户端配置文件传递凭据，避免密码进入命令行；结束删除临时凭据文件。备份默认写入 Git 忽略的 `.local/backups/`，不允许将备份写到工作区其他目录。

```powershell
.\scripts\backup-mysql.ps1
```

脚本使用 `--single-transaction --quick`、`utf8mb4`、触发器/存储程序/事件、二进制字段安全导出和显式 `--databases erp`；只在退出码为 0、文件达到合理大小、含 Flyway 历史及完成标记时把 `.partial` 改成 `.sql`，并输出 SHA-256。执行前确认账号具有读取全部目标对象及导出所需权限，确认磁盘和备份目录 ACL。对于非 InnoDB 表或导出期间 DDL，单事务一致性不保证；上线前要核查真实表类型和维护窗口。脚本不负责自动定时、远程副本或告警，失败时需人工处理且不能把 `.partial` 视为有效备份。

建议每日备份并另存至少一份加密的离机副本，按业务要求确定保留期；建议 RPO 24 小时、RTO 4 小时只是规划值，未经过恢复计时验证。备份文件含真实业务数据及认证摘要，限制访问、加密传输并定期核对 SHA-256、完成标记和 `flyway_schema_history`。Git 忽略不是访问控制。

## 恢复 Runbook

1. 由负责人确认事件、目标环境、备份时间、SHA-256、对应 MinIO 对象备份时间点及可能损失的数据。保留现状证据和当前备份。
2. **只能先在独立 MySQL 服务器或完全隔离的实例恢复。** 该 dump 含 `CREATE DATABASE/USE erp`，指定不同命令行 schema 并不能阻止它写入 `erp`。绝不能将恢复命令指向当前开发库 `192.168.1.247:3307`，也不能在未知实例执行 DROP、TRUNCATE 或覆盖。
3. 在隔离实例准备受限账户及临时凭据文件，确认连接目标后，由管理员执行 `mysql --defaults-extra-file=<isolated-client-file> < <verified-backup.sql>`。尖括号路径必须替换；PowerShell 应使用适当的文件输入方式，不要误把这条 POSIX shell 示例直接粘贴进 PowerShell。
4. 在隔离实例只读核查库名、Flyway V1～V18 的版本/成功状态与 checksum、核心用户/产品/设备/库存/单据数量、`sys_attachments` 和 `sys_notifications` 数量。使用同一发布 JAR 的 Flyway validate 核验；严禁通过 repair 改写 history。
5. 按 [minio-backup-restore.md](minio-backup-restore.md) 恢复匹配时间点的 ERP bucket，抽查已授权附件的元数据、对象大小和 SHA-256。记录数据库与对象时间差造成的缺失/孤儿风险。
6. 隔离验收通过后才提出正式恢复变更，在获批准的维护窗口确认目标、备份现状并暂停写入。正式恢复由有权限的管理员按审批执行；若失败，依据保留的现状和原备份决定继续修复或重新恢复，而非自动重试覆盖。
7. 恢复后再核对 Flyway、健康检查、登录/RBAC、库存与单据、附件、通知、报表和 Excel。记录真实恢复耗时与数据缺口。

本阶段没有独立恢复实例，**未执行恢复演练**；备份文件校验不能替代恢复成功验证。生产账号权限、加密副本与自动调度必须由实际部署环境落实。
