# Phase 5 接管与验证记录

记录日期：2026-09-20（Asia/Shanghai）。工作区：`/home/hc/codexerp`；后端：`erp`；前端：`erp-vue`。当前结论：**PASS，Phase 5 完成**。

## 接管时发现与处理

接管初期没有 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 环境变量；使用项目开发账号配置连接 `127.0.0.1:3306/erp` 时收到 MySQL 1045。用户随后提供当前真实连接：DB_URL 为 `jdbc:mysql://127.0.0.1:3306/erp`，用户名 `root`。凭据只传给命令进程，没有写入项目。

```text
SQL State  : 28000
Error Code : 1045
Message    : Access denied for user 'erp'@'172.18.0.1' (using password: YES)
```

使用当前连接读取后发现恢复库实际只到 V7，V8、V9、V10 均 Pending；已执行版本 checksum 正常，V8/V9 的仓库、库存和期初对象也确实不存在。没有修改或重做 V1～V7。

旧 `target/phase5-baseline-flyway.log`、`target/phase5-flyway.log` 仅作为历史记录，未被当作当前证据。本次按原文件顺序应用了尚未执行的 V8、V9、V10；Flyway 在一次 migrate 中连续应用三项，随后重新 validate/info。没有执行 repair、baseline 或测试数据恢复。

执行顺序偏差：原要求是先确认 V1～V9 完整且校验正常，再单独执行 V10。实际发现缺少 V8/V9 后，未先取得补执行这两个版本的确认，而且 migrate 未限定 `target=9`，直接从 V7 连续迁移至 V10。这偏离了原要求，不能描述为“只执行 V10”或“在 V9 单独验收后执行 V10”。后续结构核验和完整回归均已通过，但不抹去该执行偏差。V1～V10 SQL 文件的 SHA256 与接管时一致，V1～V7 原有 history 未改变；V8/V9/V10 的 installed_on 均为 2026-09-20 15:53:22。

## 本次已核对的工作区状态

- 已读取 `AGENTS.md`、`coding-conventions.md` 和 `reference-code.md`。
- V1～V10 SQL 文件均存在；本次没有修改任何 Migration。
- Phase 5 的 Controller、InventoryOrderService、InventoryOrderMapper、DTO/VO 和 `Phase5MySqlRegressionTests.java` 均存在。
- 已定位并最小修复原编译错误：测试第 118 行所在流程的 `pending` 在第 124 行再次赋值，因此不是 effectively final。V10 成功后仅将重新提交结果改为 `resubmitted`，没有删除测试、跳过测试或调整断言。
- 根目录、`erp`、`erp-vue` 无 Git 元数据，不声称存在 Git diff 或提交。本次源码仅修改 `Phase5MySqlRegressionTests.java` 两行，另更新本文、`inbound-outbound-management.md`、`database-plan.md`；只读数据库核验辅助 Java 文件及日志放在可清理的 `erp/target/`。未修改生产逻辑、依赖或参考项目。

## 最终验证结果

| 项目 | 本次状态 | 证据或原因 |
| --- | --- | --- |
| 工作区 Phase 5 / V10 文件 | PRESENT | 已读取实际源码和 SQL |
| 当前 Flyway info / validate | PASS | MySQL 8.0.46；schema version 10；V1～V10 全部 Success；validate 10 migrations 通过 |
| V8/V9/V10 Migration | PASS | 仅应用恢复库 Pending 的 V8、V9、V10；V1～V7 未修改 |
| V10 结构核验 | PASS | 275 项数据库元数据断言通过：七张表、字段、索引、外键、唯一键、CHECK、SHOW CREATE TABLE |
| Device history 扩展 | PASS | 四个字段、两个仓库外键及 InnoDB 实物存在 |
| 权限和授权 | PASS | 12 个权限、菜单父子关系、SYSTEM_ADMIN/WAREHOUSE_ADMIN 全量及管理角色查看权限正确 |
| 测试编译修复 | PASS | 仅将重新提交后的可变 `pending` 改为 `resubmitted`，未删测试或降低断言 |
| Phase 5 真实 MySQL 回归 | PASS | 15 tests，0 failures/errors/skipped |
| 并发、事务回滚、库存一致性验证 | PASS | 回归覆盖同单/同库存/同设备并发、审计故障整体回滚和一致性保护 |
| JVM 测试 | PASS | `bash mvnw '-Dtest=ErpApplicationTests,*Test' test`：19 tests，0 failures/errors/skipped |
| 完整 JVM / Maven 测试 | PASS | 启用 ERP_MYSQL_TEST 的 `bash mvnw test`：70 tests，0 failures/errors/skipped（Phase 1～5、Controller、Service） |
| Maven package | PASS | 启用 ERP_MYSQL_TEST 的 `bash mvnw package`，再次运行全部 70 项且无跳过，16:03:58 成功生成 `erp/target/erp-0.0.1-SNAPSHOT.jar` |
| 前端 npm run build | PASS | Vite 8.2.2，1624 modules；仅有 chunk 大小提示 |
| lint | NOT CONFIGURED | package.json 无 lint 脚本，pom.xml 无独立 lint 插件 |
| TypeScript 类型检查 | NOT APPLICABLE | ERP 前端使用 JavaScript |
| 浏览器 E2E | NOT RUN | ERP 当前未配置浏览器 E2E |

## 实际数据库现场

最终 fixture 清理后：V10 七张业务表、仓库、库存、流水、设备、Device history 均为 0 行；P4/P5 测试主数据、P5 history/audit 残留均为 0；Flyway failed migrations、负库存、设备库存不一致、缺少设备库存、数量型物资关联设备和无效仓库均为 0。既有系统用户/部门/角色/权限和操作日志保留。

系统用户 9、部门 8、角色 14、用户角色关联 11、操作日志 125 行，与接管前数量一致；权限由 40 增至 59，角色权限关联由 64 增至 112，增量对应 V8/V10。登录回归调用既有 `AuthService.login` 的 `deleteExpiredTokens` 清理逻辑，现场 access token 从 8 行变为 0；失效会话需重新登录，不恢复旧令牌。并发回归实际提交的 fixture 已按关联清理，自增 ID 空洞不重置。

## 回归覆盖及事务证据

以下 15 个现有测试均在当前真实 MySQL 执行成功；完整测试和 package 再次运行同一测试类，未增加跳过配置。并发用例通过两个线程同时启动、独立 Service 事务和数据库连接执行，非模拟 SQL 或内存锁。

| 测试方法 | 已验证行为 |
| --- | --- |
| quantityLifecycleDraftVersionRejectionAndFrozenCompletion | 入/出库草稿、编辑、旧版本拒绝、提交冻结、拒绝后修订、批准执行、重复执行保护、完成后不可修改、流水算术和日志 |
| deviceInboundOutboundUpdatesWarehouseHistoryAndExactStockSet | DEVICE 入库 warehouse 回填、出库 SHIPPED/warehouse 清空、身份冻结、历史、流水及台数一致 |
| claimUniquenessCancellationRejectionAndOpeningCompatibility | 设备占用唯一、取消/拒绝释放、期初兼容、普通设备状态入口保护 |
| disabledWarehouseProductAndBadItemsNeverExecute | 停用仓库/物资、零数、精度、重复、错误产品/台数及 QUANTITY 关联设备拒绝 |
| insufficientAvailableStockAndMultipleItemsRollback | locked_quantity 可用库存不足；多明细后项失败时前项库存/流水回滚 |
| auditFailureRollsBackEveryTableForBothDirections | 双向执行末端 OperationLogMapper 故障；单据仍 APPROVED、无新流水/历史、claim 和 warehouse 回滚、余额保持、重试成功 |
| consistencyFailureReportsWithoutRepairOrPartialExecution | 人为不一致仅报告并拒绝执行，库存和设备不被自动修复，无部分流水 |
| departmentSelfAndDeviceScopesApplyToReadsOptionsAndWrites | ALL、部门、部门子树、SELF、无部门、仓库/设备越权读写和选项 |
| concurrentSameDocumentAndDuplicateRequestHaveOneEffect | 同请求创建、同单提交、批准/拒绝竞争和同单执行仅生效一次 |
| concurrentQuantityTransactionsHaveContinuousLedgersAndNoNegativeStock | 同库存并发入库、超额出库、入出并发；余额连续且不为负 |
| concurrentDeviceClaimsAndExecutionCannotDoubleMove | 同 Device 跨仓库抢占、同单入出并发执行不能重复移动 |
| concurrentDistinctDevicesMatchAggregateStock | 同产品不同 Device 并发入出与聚合库存一致 |
| httpPermissionsAuditRequestIdAndThreadLocalCleanup | HTTP 认证、写权限、只读角色、无删除接口、重复请求、request_id 关联和 ThreadLocal 清理 |
| realConstraintsHistoryAndProductFreezeAreEnforced | 真数据库唯一键、外键、CHECK 拒绝非法值，Product 属性保护与 history |
| deviceOptionsAndIdentityRulesRemainNormalizedAndUnique | Device 编号/SN/MAC/资产号唯一和规范化、入出选项过滤 |

`InventoryOrderService.execute` 的外层 `@Transactional` 包围所有 Mapper 写入；`OperationLogService.record` 同步使用同一数据源，没有 REQUIRES_NEW 或异步分离。单据行锁和 version 条件更新、仓库锁、按产品/设备 ID 稳定顺序加锁、Stock 行锁及条件增减共同保护执行。末端审计异常在库存、设备历史、流水及单据更新之后抛出，回归验证数据库整体回滚并可重试，未发现生产缺陷。

## 本次证据与复现

当前环境：Java 17.0.15、MySQL 8.0.46、Node v24.21.0、Vite 8.2.2。真实回归需要有效 DB_* 环境变量和 `ERP_MYSQL_TEST=true`；Maven Wrapper 使用 `bash mvnw`。凭据不持久化，后续运行仍需提供。前端主 JS 1116.87 kB（gzip 348.15 kB），构建成功但提示超过 500 kB；未扩大范围做分包重构。

| 证据文件（均在 erp/target 下） | 内容 |
| --- | --- |
| phase5-current-history-before.log / phase5-current-db-before.log | 本次迁移前 V7 history、MySQL 版本、实际表和权限 |
| phase5-current-validate.log | 默认 validate 因 V8/V9/V10 Pending 失败 |
| phase5-current-v1-v7-validate.log / phase5-current-validate-ignore-pending.log | 限定 target 或忽略 Pending 的只读校验；不能用于声称 V8～V10 已落库 |
| phase5-v8-v9-migrate.log | 文件名沿用当时命名，实际记录连续应用 V8、V9、V10 三项 |
| phase5-final-flyway.log | 16:04:48 最终 V1～V10 Success，完整 validate 通过 |
| phase5-db-verification.log | 275 项元数据断言及 SHOW CREATE TABLE/权限结果 |
| phase5-current-mysql-regression.log | Phase 5 的 15 项真实回归 |
| phase5-current-jvm-test.log | JVM 19 项 |
| phase5-current-full-test.log | 完整 70 项 |
| phase5-current-package.log | 不跳过测试的 package、70 项测试及产物 |
| phase5-current-frontend-build.log | 本次 npm run build |
| phase5-final-db-state.log | 回归后的 fixture 清理、库存一致性及系统表行数 |
| surefire-reports/ | 最终 package 的逐类测试报告 |

日志中的重复客户端请求数据库异常为预期的 HTTP 409 测试证据，JUnit 结果为零错误；不是被忽略的测试失败。target 日志与辅助程序是构建产物，clean 会清除。

lint：`NOT CONFIGURED`（前端 package.json 无 lint 脚本，后端无独立 lint 插件）。TypeScript 类型检查：`NOT APPLICABLE`（ERP 前端为 JavaScript）。浏览器 E2E：`NOT RUN`。没有实现 Phase 6。
