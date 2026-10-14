# Phase 8 实现与验证记录

日期：2026-10-14（Asia/Shanghai）。项目：`F:\codexerp`，分支：`feature/phase8-repair-warranty-exchange`。此文记录本次真实执行与结果；旧 target/surefire-reports 不作为本次验收证据。

## 数据库与迁移

只连接指定 MySQL `192.168.1.247:3307/erp`、用户 root。密码从 Git 忽略的本地 `.env` 加载到命令进程；文档与日志不含密码。开始前只读检查 Flyway history 为 V1～V13 全部 Success，独立 `flyway:validate` 核对 13 个 Migration checksum。迁移前对真实数据库使用 `mysqldump --single-transaction` 备份到 Git 忽略的 `.local/backups/`，备份非空。随后按顺序应用 V14、V15、V16；每版均独立执行 Flyway validate/info。V14 增加维修、保修、换货、换货设备关系及动作幂等表，扩展既有设备占用；V15 加入设备版本快照；V16 修正保修菜单路径。V1～V13 未改动，已应用的 V14/V15 也未改写。V16 后的 Flyway schema version 为 16。

V14 实际结构核对包含 5 张新表、字段、外键、唯一/CHECK 约束、索引和 5 个默认角色的 224 项元数据断言，全部通过。`asset_devices.version` 与共享 `inv_order_device_claims` 的新增列和外键亦已核对。定向测试进一步查询 Phase 8 权限、外键与 CHECK。

## 真实回归与验收

`Phase8MySqlRegressionTests` 使用独立 `P8_` UUID 前缀、生成主键精确清理，不执行 TRUNCATE 或全表删除。先读取 Phase 1～7 测试源码，确认它们通过事务回滚或仅清理各自生成的前缀/主键。测试覆盖维修草稿、状态、备注/注意事项、库存送修与返库、使用中归属恢复、无法修复、换货身份独立/原因/库存、保修日期快照与过保拒绝/自费、保修转维修、权限与 ALL/DEPARTMENT/SELF 范围、服务端设备选项、并发与幂等、库存一致性、库存与审计失败回滚、request_id、设备历史及数据库约束。

| 验证 | 结果 |
| --- | --- |
| 指定 MySQL 基线 V13 checksum | PASS，独立 Flyway validate 13 项 |
| V14～V16 实际迁移、validate/info | PASS，当前 schema version 16 |
| 新结构与权限元数据核对 | PASS，224 项断言 |
| Phase 8 定向真实 MySQL 回归 | PASS，36 项，0 失败/错误/跳过 |
| 完整 Maven/JVM 测试 | PASS，133 项，0 失败/错误/跳过，含 Phase 1～8 的真实 MySQL 回归 |
| 未跳过测试的 Maven package | PASS，再次执行 133 项测试，0 失败/错误/跳过；已生成 Spring Boot jar |
| 前端 npm run build | PASS，Vite 8.2.2 构建成功；主 JS 包超过 500 kB 提示 |
| lint | NOT CONFIGURED，`package.json` 无 lint 脚本 |
| 浏览器 E2E | NOT RUN |

测试后对设备、库存、流水、历史、审计及 `P8_` 主数据执行只读残留复核，均为 0；DEVICE 库存一致性问题为 0。完整 Maven test 和未跳过测试的 package 分别执行过，结果均为 133 项通过。浏览器 E2E 未执行，不能视为浏览器验收。
