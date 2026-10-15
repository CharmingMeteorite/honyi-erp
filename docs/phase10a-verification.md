# Phase 10A 实现与验证记录

日期：2026-10-14（Asia/Shanghai）。项目：`F:\codexerp`，分支：`feature/phase10a-dashboard-query`。本记录描述本轮重新执行的验证，历史阶段文档及旧 `target/surefire-reports` 不作为当前通过证据。

## 基线与数据库

开始时工作区干净，当前及远程 Phase 9 分支包含提交 `deff43db102d03e1f2b18ccbf9ab10224f7bf54f`。Phase 10A 从该提交创建独立分支，完整继承 Phase 7～9；远程 main 仍为恢复基线，本阶段未合并或改写 main。

仅使用指定 MySQL `192.168.1.247:3307/erp`、用户 root。密码从 Git 忽略的本地 `.env` 加载到命令进程，未写入源码、文档、测试或 Git。开始前及最终独立执行 `flyway:validate flyway:info`：17 个 checksum 校验通过，V1～V17 全部 Success，最高 V17，失败记录 0。本阶段没有新增 V18，没有执行新 Migration、repair、重建或清库，V1～V17 文件未修改；因没有结构迁移，没有创建新的迁移备份。

## 实现文件和接口

后端新增 `DashboardQueryController`、`DashboardQueryService`、`DashboardQueryMapper`、`QueryFilter`、`DashboardSummaryView`、`QueryResultView`，以及 `Phase10AMySqlRegressionTests`。

前端更新 `DashboardView.vue`、`layouts/AppLayout.vue`、`router/index.js`、`lib/api.js`；新增 `GlobalQueryView.vue` 与中文类型/状态显示辅助 `lib/query.js`。同步 README、database-plan，并新增 dashboard-management、global-query 和本文件。AGENTS 现有规则仍适用，无需修改；没有新增依赖或更改原业务页面。

- `GET /api/dashboard/summary`：按资源权限返回 Product、Device 状态、启用仓库、正库存行/产品/仓库数量、库存合计、安全库存报警计数、七类单据待办及最多 8 条近期业务主记录。
- `GET /api/query`：Product、Device、Warehouse、Inbound、Outbound、Requisition、Stocktake、Repair、Warranty、Exchange 十类只读查询，支持关键词、类型、状态、创建日期及数据库分页。关联明细和 Device 快照用 `EXISTS` 匹配，一张主单据只返回一行。

接口沿用 `ApiResponse`、`PageResult`、业务异常、RequestIdFilter 和 `@RequirePermission`。不新增 dashboard/query 专用权限；每类结果复用真实库已有 view 权限，明确指定无权限类型返回 403。部门树、SELF 及仓库范围沿用原业务边界，无权限指标隐藏。两个方法使用只读事务，当前 MySQL 隔离级别已实查为 REPEATABLE-READ；对编码规范纯查询事务条款的局部偏离及原因见 [dashboard-management.md](dashboard-management.md)。查询不写 OperationLog、StockLedger、DeviceHistory 或业务数据。

## 测试数据安全

执行前检查 Phase 1～9 测试源码：事务回滚或按测试生成主键、UUID 前缀及其关联资源删除；未见 TRUNCATE 或无条件业务全表清空。原 Phase 4～9 回归未修改。

Phase 10A 使用 `P10A_` 加 UUID 前缀，在 Spring 测试事务中创建夹具并回滚。真实 RBAC/HTTP 用例只为本次用户插入短期 Token 哈希，不调用会清理过期 Token 的登录流程。`@AfterTransaction` 验证本次主数据、单据、库存、历史、用户、Token 和审计记录为零残留。测试写入 JDBC 后清除当前 MyBatis 测试会话缓存，保证断言读取最新夹具。

## 本轮测试与构建结果

所有 Maven 命令使用 Java 17.0.19，数据库回归启用 `ERP_MYSQL_TEST=true`。下面结果来自本轮命令退出码及重新生成的 Surefire XML。

| 验证 | 最终结果 |
| --- | --- |
| Phase 10A 真实 MySQL 测试 | PASS，11 项，0 失败/错误/跳过 |
| Phase 4～9 真实 MySQL 完整回归 | PASS，19 + 15 + 14 + 13 + 36 + 14 = 111 项 |
| 完整 `mvnw.cmd -q test` | PASS，158 项，0 失败/错误/跳过 |
| 未跳过测试的 `mvnw.cmd -q package` | PASS，再次执行 158 项，0 失败/错误/跳过；生成本轮 Spring Boot jar |
| `npm run build` | PASS，Vite 8.2.2；既有主 JS 包超过 500 kB 的提示仍存在 |
| 最终独立 Flyway validate/info | PASS，17 个 Migration checksum 一致，全部 Success，版本 V17 |
| 测试夹具残留与库存一致性 | PASS，只读复核残留为 0，设备库存差异为 0 |
| lint | NOT CONFIGURED，前端 package.json 无 lint 脚本 |
| 浏览器 E2E | NOT RUN；不能视为浏览器实机验收 |

11 项新用例覆盖：主表统计、设备八状态和可见库存、低库存严格边界/零库存/停用 Product/零阈值、七类待办与近期动态、十类查询、关联 Product/Device 快照、特殊字符字面匹配、状态及包含结束日全天的日期过滤、稳定排序与分页、关键词/状态/日期/分页限制、历史离库和报废设备、无结果、空权限/缺失部门、不同用户的 ALL/DEPARTMENT/SELF 范围、真实持久 RBAC、HTTP 401/403/400、request_id 及不写审计或业务表。

真实一对多夹具包含一个 Product 多台 Device、一个单据多个 Item/Device 核对记录、一个 Device 多条 History 和多条 StockLedger；查询与统计断言确认 Product、Device、Stock 和单据数不会重复膨胀。

补充低库存用例后的首轮定向测试有 2 项失败，原因是 JDBC 夹具更新绕过 MyBatis，读取了测试事务内旧的会话缓存；已修正夹具 helper 清理会话缓存。最后补充 Device 快照匹配时，一轮全量测试出现 1 失败和 7 错误，原因是新查询错误使用了不存在的 `inv_stocktake_devices`，而当前 V12/V13 实际表为 `inv_stocktake_device_checks`；已按真实表结构修正生产查询和测试必填字段。随后全量 test、未跳过测试的 package 均重新通过，没有删测试、跳过测试或降低断言。

最终项目外只读 JDBC 核查确认：V17、失败 Migration 0；`P3_`～`P9_` 与 `P10A_` 主数据/单据夹具 0；当前 Product、Device、Warehouse、Stock、StockLedger、DeviceHistory、二维码令牌、七类单据、各明细及设备占用关系均为 0 行。DEVICE Stock 与同仓库 IN_STOCK Device 的数量差异、缺失库存行、非法仓库归属状态均为 0。以上零行状态是回归夹具正常清理后的实查结果，没有执行清库。

## 已知限制

- 综合查询 Product/Device 可使用现有 ID 参数定位，其他业务结果跳转原有列表，再按单号查询；没有新增单据详情路由。
- 近期动态按单据创建时间展示主记录，不是完整动作时间轴；未实现趋势图。
- 库存合计保留各 Product 原单位，页面明确提示不能将不同单位当统一计量值比较。
- 子串跨字段搜索未进行大数据量性能专项验收；没有证据需要新增索引，未引入缓存或搜索服务。
- 浏览器 E2E 未执行，前端仅通过构建；lint 未配置。Phase 10B 及后续阶段没有实现。
