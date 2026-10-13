# Phase 4 实施与验证记录

验证日期：2026-09-18（Asia/Shanghai）。当前工作区 `/home/hc/codexerp`，后端 `erp`，前端 `erp-vue`。根目录、erp、erp-vue 没有 Git 元数据，因此本文列实际文件变化，不声称存在 Git diff、提交或分支。

## 当前环境与迁移前提

已读取 AGENTS.md、coding-conventions.md、reference-code.md、database-plan.md、device-management.md、phase3-verification.md，核实 Phase 0～3 的认证/RBAC、物资、设备及 V1～V7 文件均存在。未复制 ems/ems-vue 业务，没有重做既有功能。

| 项目 | 当前机器的实际情况 |
| --- | --- |
| Java | OpenJDK 17.0.20，javac 同版本，Linux amd64 |
| Maven | PATH 无 mvn；项目 Wrapper 下载/使用 Maven 3.9.16，命令为 `bash mvnw`（迁移来的脚本无执行位） |
| Node / npm | v24.21.0 / 12.0.2，满足现有 engines |
| MySQL | 实际连接 127.0.0.1:3306/erp，`SELECT VERSION()` 为 8.0.46 |
| 连接配置 | 初始无 DB_* 环境变量；用户随后确认当前连接，实际凭据仅传给命令进程环境，没有写入源码、文档或日志 |
| Docker / MySQL CLI | Docker CLI 29.8.1 存在，但当前用户无 socket 权限；无 mysql CLI。使用已存在的本机 MySQL 和项目 MySQL JDBC/Flyway 验证，没有假定 Docker 已启动 |
| 前端依赖 | 原 node_modules 来自迁移环境，vite 无执行权限。使用现有锁文件 `npm ci --registry=https://registry.npmmirror.com` 恢复 Linux 依赖；package.json、package-lock.json 均未修改 |

第一次 npm ci 使用本机默认 registry 时，npm 12 将锁文件中 npmmirror URL 识别为禁用的 remote 包；改用锁文件原有 registry 后成功。没有改变全局 npm 配置、依赖版本或构建体系。构建实际使用锁定的 Vite 8.2.2。

## 最终结果

| 验证 | 结果 | 实际证据 |
| --- | --- | --- |
| JVM / Maven 测试 | PASS | `bash mvnw '-Dtest=ErpApplicationTests,*Test' test`：19 tests，0 failures/errors/skipped |
| 全量测试（包含真实 MySQL） | PASS | 设置当前 DB_* 和 ERP_MYSQL_TEST=true 后 `bash mvnw test`：55 tests，0 failures/errors/skipped |
| Phase 4 MySQL regression | PASS | 19 个测试方法，独立事务及多连接并发实际提交后清理 |
| Phase 1～3 回归 | PASS | Phase 1 2、Phase 2 2、Phase 3 13 个方法均通过 |
| Flyway | PASS | V1～V9 全部 Success，schema version 9，validate 9 migrations；没有失败记录 |
| Schema / 索引 / 外键 | PASS | JDBC 执行 SHOW CREATE TABLE 和 information_schema 查询，核对六张新表、外键、唯一键、CHECK、设备计数索引 |
| Maven package | PASS | `bash mvnw package -DskipTests`，生成 `erp/target/erp-0.0.1-SNAPSHOT.jar`，测试已在此前全量通过 |
| npm build | PASS | `npm run build`，1622 modules，生成 dist；主 JS 1099.56 kB，gzip 344.63 kB |
| lint | NOT CONFIGURED | package.json 无 lint 脚本，后端无独立 lint 插件，没有新增 lint 工具 |
| TypeScript 类型检查 | NOT APPLICABLE | 项目为 JavaScript，未增加 .ts/.tsx、TS 语法或 vue-tsc |
| 浏览器 E2E | NOT RUN | 未配置/执行浏览器自动化；前端验证为构建和代码核对，HTTP 验证使用 MockMvc + 真实 MySQL |

完整 55 项组成：Phase4MySqlRegressionTests 19，Phase3MySqlRegressionTests 13，Phase2MySqlRegressionTests 2，Phase1MySqlRegressionTests 2，UserControllerTest 9，ErpApplicationTests 5，InventoryScopeServiceTest 3，AuthServiceTest 1，UserServiceTest 1。

最终日志（target 下为可清理构建产物）：

- `erp/target/phase4-jvm-test.log`：最终 JVM 测试。
- `erp/target/phase4-full-test.log`：最终 55 项全量测试。
- `erp/target/phase4-package.log`：最终后端打包。
- `erp/target/phase4-frontend-build.log`：最终前端构建。
- `erp/target/phase4-flyway.log`：V9 migrate、V1～V9 validate/info。
- `erp/target/phase4-db-verification.log`：数据库版本、history、约束/索引/外键、行数、SHOW CREATE TABLE。
- `erp/target/phase4-npm-ci.log`：当前环境依赖恢复。
- `erp/target/surefire-reports`：JUnit 逐类报告。

`phase4-mysql-regression.log` 保留的是首轮局部回归失败诊断，最终结论以 full-test.log 为准。首次未配置 DB 的普通 `mvn test` 因既有 Phase 2 测试没有环境开关而失败；没有改写该测试，之后先明确筛选 JVM 测试，再带真实 DB 配置执行全量。不得省略 DB 配置后把这些 MySQL 测试当成已通过。

## Migration 与实际结构

| 新增版本 | 内容 | 结果 |
| --- | --- | --- |
| V8__create_inventory_schema.sql | 六张仓库/库存/流水/期初相关表，设备 warehouse_id 外键，七个 RBAC 权限 | Success |
| V9__enforce_inventory_scope_and_opening_guards.sql | 仓库 department_id 外键、client_request_id 唯一键、完成设备唯一标记、单据/流水 CHECK、设备库存计数索引、页面权限路由 | Success |

V8 成功后所有追加完善均进入 V9，没有改写已执行的 Migration。V1～V7 SHA256 与本次读取时的文件一致，且 Flyway checksum validate 通过。结构核对包括：

- `inv_stocks` 的 `(warehouse_id, product_id)` 唯一键、两列外键和非负/锁定余额 CHECK；没有 device_id 列。
- 仓库名称/编码唯一键和部门外键，asset_devices 的真实 warehouse 外键。
- 期初单号/client_request_id 唯一键、同单物资唯一键、同单设备唯一键、initialized_device_id 全局唯一键及真实外键。
- 流水业务唯一键、仓库/物资/操作人外键、request_id 索引和 IN/OUT 算术 CHECK。
- `(warehouse_id,product_id,status)` 设备计数索引。

最终现场六张新增业务表各 0 行，asset_devices / asset_device_history 各 0 行；P4 fixture 产品、用户、部门剩余 0。没有创建展示仓库、虚构初始库存或伪造流水。回归提交的并发数据按 fixture 关联清理；自增 ID 空洞为预期，不重置现场自增值。

## Phase 4 回归覆盖

| 测试方法 | 主要覆盖 |
| --- | --- |
| warehouseCrudUniqueStatusAndPermissionSeparation | 创建、读取、更新、启停、编码/名称重复、分页、参数、权限、无删除入口 |
| quantityOpeningCreatesRealLedgerAndRejectsRepeatExecutionAndRequest | 数量期初、保存不动库存、客户端请求去重、完成去重、真实前后流水、完成后不可改 |
| deviceOpeningUpdatesWarehouseHistoryAndProtectsOrdinaryDeviceEndpoints | 设备期初、仓库回填、历史、不改生命周期状态、普通编辑保留仓库、状态入口保护、跨单设备防重 |
| validatesDeviceMembershipStatusQuantityPrecisionAndDuplicates | 类型/产品归属、设备数、状态、重复设备/物资、负数/零/小数精度拒绝 |
| scopesWarehouseStockLedgerOpeningOptionsAndEveryWrite | ALL/部门子树/SELF/无部门，分页与 count，越权详情/写入/检查，设备选项范围 |
| disabledWarehouseAndProductRejectCreateEditAndCompletion | 停用仓库创建、修改、完成拒绝；停用物资完成拒绝，启用后恢复 |
| draftEditReplacesItemsAndCompletedDocumentIsImmutable | 草稿替换明细/仓库，完成后冻结，无多余库存 |
| auditFailureRollsBackStockDeviceHistoryLedgerAndDocumentTogether | 最后操作日志处注入故障，跨表全部回滚，设备完成标记回滚，随后重试成功 |
| consistencyReportsTamperingAndMissingStocksWithoutRepair | 人工篡改、缺失 Stock、QUANTITY 非法设备报告；检查不修复、不补库存；不一致时阻止继续初始化 |
| databaseConstraintsProtectNegativeLockedForeignKeysAndInitializedDevices | 真实 CHECK 拒绝负库存/过量锁定/错误流水算术，唯一键/外键/初始化设备唯一键实测 |
| concurrentQuantityCompletionsAccumulateWithoutLostUpdates | 同仓库同产品两单并发累加，流水前后连续，无丢失更新 |
| concurrentSameDocumentAndCreateRequestHaveSingleEffect | 同 clientRequestId 并发创建只成功一次，同单并发完成只成功一次 |
| concurrentSameDeviceAcrossWarehousesCanOnlyInitializeOnce | 两仓库同时初始化同台 Device，仅一单成功，库存总量为 1 |
| concurrentDistinctDevicesMaintainExactStockCount | 同产品不同设备并发初始化，台数与账面数量精确一致 |
| httpPermissionsRequestIdAuditAndThreadLocalCleanup | HTTP 创建/完成、401/403、重复409、三类审计同 request_id、成功和拒绝请求上下文清理 |
| httpRejectsStockCrudAndWarehouseStatusBypassAndEnforcesReadOnlyRole | Stock 无普通写 API，PUT status 不能绕过启停，负数/分页参数400，管理层只读权限 |
| deviceChangedAfterDraftIsRevalidatedAndEntireCompletionRollsBack | 草稿不预占设备，设备后续状态变化后完成拒绝，多行数量操作一起回滚 |
| stockAndOpeningReferencesFreezeProductTypeAndUnit | 期初/库存引用锁定物资类型和单位的服务规则，普通物资维护仍成功 |
| realSchemaIndexesForeignKeysAndFlywayAreApplied | MySQL 版本、Flyway history、新表、设备外键及关键唯一索引实际存在 |

MySQL CHECK 错误码为 3819，当前 JDBC 翻译为 UncategorizedSQLException，测试针对真实错误码断言，未把它误报成外键/唯一性异常。库存业务层先校验输入，数据库 CHECK 为最终保护。

## 文件清单

以下为本阶段全部生产代码、测试、迁移、前端和文档变化；不包括 target/dist/node_modules 产物。

后端新增，路径基于 `erp/src/main/java/com/haucheng/erp/`：

```text
controller/WarehouseController.java
controller/StockController.java
dto/WarehouseRequest.java
dto/OpeningBalanceRequest.java
dto/OpeningBalanceItemRequest.java
entity/WhWarehouse.java
entity/InvStock.java
entity/InvStockLedger.java
entity/InvOpeningBalance.java
entity/InvOpeningBalanceItem.java
entity/InvOpeningBalanceDevice.java
mapper/WarehouseMapper.java
mapper/StockMapper.java
mapper/OpeningBalanceMapper.java
mapper/InventoryOptionMapper.java
service/WarehouseService.java
service/StockService.java
service/OpeningBalanceService.java
service/InventoryScopeService.java
service/InventoryOptionService.java
vo/WarehouseView.java
vo/StockView.java
vo/StockLedgerView.java
vo/OpeningBalanceView.java
vo/OpeningDeviceOptionView.java
vo/StockConsistencyIssueView.java
```

后端修改（同一根路径）：

```text
auth/AuthInterceptor.java                 # 在拒绝鉴权前清除遗留 ThreadLocal，完成后原清理保留
exception/GlobalExceptionHandler.java      # 不支持的方法统一返回40500/405，避免无库存CRUD时误报500
dto/DeviceCreateRequest.java               # 仓库归属输入限制文案
dto/DeviceUpdateRequest.java               # 同上
mapper/AssetDeviceMapper.java              # 专用初始归属条件更新/锁定计数，普通PUT不写仓库，显示仓库名称
service/AssetDeviceService.java            # 已入账设备不能手工变更状态，普通档案维护保留
service/ProductService.java                # 有库存/期初引用时冻结类型和单位
vo/DeviceView.java                         # 仓库名称
```

Migration 与测试新增：

```text
erp/src/main/resources/db/migration/V8__create_inventory_schema.sql
erp/src/main/resources/db/migration/V9__enforce_inventory_scope_and_opening_guards.sql
erp/src/test/java/com/haucheng/erp/Phase4MySqlRegressionTests.java
erp/src/test/java/com/haucheng/erp/service/InventoryScopeServiceTest.java
```

前端新增：

```text
erp-vue/src/views/system/WarehouseView.vue
erp-vue/src/views/inventory/StockView.vue
erp-vue/src/views/inventory/StockLedgerView.vue
erp-vue/src/views/inventory/OpeningBalanceView.vue
```

前端修改：`erp-vue/src/lib/api.js`、`src/router/index.js`、`src/layouts/AppLayout.vue`、`src/views/asset/DeviceView.vue`。

文档新增：`docs/inventory-management.md`、`docs/phase4-verification.md`。文档修改：`docs/database-plan.md`、`docs/device-management.md`。Phase 3 验证文档保留为当时环境的历史记录，当前运行环境以本文为准。

最终检查确认提供的数据库密码未出现在源码、文档、配置或 Phase 4 日志中；前端 src 下没有 .ts/.tsx 文件。

本阶段没有新增第三方依赖，没有修改 pom.xml、package.json、package-lock.json、V1～V7 或参考项目。仍为 Java 17 / Spring Boot 4.1.1 / MyBatis 注解 / Lombok / Jakarta Validation / Flyway / MySQL 和 Vue 3 / JavaScript / Element Plus / native fetch。没有规范偏离；新增 inventory 页面目录有独立库存业务职责。

## 可重复执行

在执行进程中安全设置当前正确的 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`，不要将密码作为测试代码、文档或本地提交文件。DB_URL 使用当前数据库的 JDBC URL。Java 保持 17，真实回归设置 `ERP_MYSQL_TEST=true`。

```bash
# erp 目录：先 JVM，再真实 MySQL 回归
bash mvnw '-Dtest=ErpApplicationTests,*Test' test
# 仅在存在待执行版本时执行 migrate，禁止修改已成功版本
bash mvnw flyway:migrate flyway:validate flyway:info
ERP_MYSQL_TEST=true bash mvnw test
bash mvnw package -DskipTests

# erp-vue 目录：当前环境需要恢复依赖时使用已有锁文件
npm ci --registry=https://registry.npmmirror.com
npm run build
```

## 已知限制与 Phase 5 边界

- 当前仓库采用部门范围；全局仓库仅 ALL 可见，仓库所属部门创建后不支持普通修改。SELF 不展示仓库总体余额。
- 同仓库期初写入使用仓库行锁串行化，适合当前规模；后续高吞吐要求出现时再评估更细粒度方案。
- 支持多批期初；不同单据/不同 clientRequestId 手工重复录入同一现实数量批次无法自动识别。当前没有批次、成本、金额或库存预占功能。
- 没有草稿取消/删除、完成撤销或自动修复。检查只报告，异常数据需人工核查并通过另行设计的受控更正流程处理。
- Vite 提示单个 chunk 超过 500 kB，构建成功；未为此改架构或引入依赖。Lint 未配置，浏览器 E2E 未执行。
- 运行时仍需提供当前环境 DB_*；未持久化用户密码，也没有启动常驻后端/前端服务。
- Phase 5 开始前需明确正式单据的业务状态、执行权限、设备绑定、库存变化方向和审计要求；在同一事务内复用库存与设备一致性规则。入库、出库、领用、发货、借用、归还、调拨、盘点、维修、换货、二维码、报表和库位均未在 Phase 4 实现。
