# Phase 3 实施与验证记录

最终验证日期：2026-09-18（Asia/Shanghai）。项目目录 `F:\codexerp`，后端 `erp`，前端 `erp-vue`。当前现场没有 Git 元数据，文件清单为实施记录，不声称有 Git diff 或提交。

## 实际结果

| 项目 | 状态 | 证据 |
| --- | --- | --- |
| test | PASS | Maven test：33 tests，0 failures，0 errors，0 skipped |
| MySQL regression | PASS | MySQL 8.0.46 / 127.0.0.1:3306/erp；Phase 3 13 个测试方法，Phase 1/2 各 2 个真实回归同时通过 |
| build（后端） | PASS | Maven package -DskipTests，生成 target/erp-0.0.1-SNAPSHOT.jar |
| build（前端） | PASS | npm run build，Vite 成功生成 dist |
| lint | NOT CONFIGURED | package.json 无 lint 脚本；npm run lint 返回 Missing script；后端也无独立 lint 插件 |
| migration | PASS | Flyway validate 验证 7 个版本；info 显示 V1～V7 全部 Success，schema version 7 |
| 浏览器端到端验证 | NOT RUN | 本次未配置或执行浏览器自动化；前端验证为构建和代码核对，后端 HTTP 用 MockMvc + 真实 MySQL |

后端最终以 Java 17.0.19、Maven 3.9.16 执行。系统默认 Java 为另一版本，命令进程显式设置 JAVA_HOME。PATH 无 mvn；本次 `mvnw.cmd` 在读取 Maven 缓存目录的 PowerShell Target 属性时报 `Cannot index into a null array`，因此使用已有 `.m2/wrapper/dists/apache-maven-3.9.16/.../bin/mvn.cmd` 完成同一 Maven test/package/Flyway 目标，没有修改 Wrapper 或依赖。

日志位于 `erp/target/phase3-regression.log`、`phase3-full-test.log`、`phase3-package.log`、`phase3-flyway.log`、`phase3-db-verification.log`；Surefire 明细位于 `erp/target/surefire-reports`。target 会在 clean 后删除。

## Migration

| 新增版本 | 内容 | 实际结果 |
| --- | --- | --- |
| V5__create_asset_devices.sql | 两张设备相关表、身份唯一键、Product/人员/部门外键、查询索引及权限节点 | Success |
| V6__grant_device_permissions.sql | 补齐仓库管理员设备写权限和普通员工查看权限 | Success |
| V7__reserve_device_location_columns.sql | 仓库/库位可空预留列与索引，无仓库表 | Success |

V6 首次尝试曾因 V5 已授予仓库管理员 view 权限而重复插入失败；在尚未成功应用 V6 时，将授权改为 INSERT IGNORE，清理 Flyway 失败记录后重新执行成功。没有改写任何已经成功执行的 Migration。最终 validate、数据库 history 和实际表结构一致，无失败或待执行版本。

V1～V4 未修改，复核 SHA256：

```text
V1 0E49AB5E89EB11A87EE3DDD021CC1908B1DEB694D71E864904B695E5A3CE1D85
V2 41C7FEFAD69CFCA797F805021941A74B7B5E2906ECF8FA5CF6E14A6F8F56C28A
V3 FB90AE07A3AA240EB6935CCB0FB4ED56FB333C750ACF3EDDA19D139FEB3DD904
V4 163F13200E843B502514D1E1F2C8BB7DC9AC36560D15AF3A118914F6DB03FE89
```

## MySQL 回归覆盖

测试文件：`erp/src/test/java/com/haucheng/erp/Phase3MySqlRegressionTests.java`。每个方法包含多条实际数据库断言，不将测试方法数等同于业务场景数。

| 测试方法 | 关键覆盖 |
| --- | --- |
| enforcesProductIdentityAndAssignmentRules | DEVICE 创建成功；QUANTITY、停用/不存在 Product 拒绝；SN/MAC 按属性必填；设备编号/SN/MAC/资产号重复拒绝；MAC 异格式重复拒绝；无效人员/部门及错配拒绝 |
| enforcesStatusHistoryFilteringAndDataScope | 基础状态及非法恢复、历史数量、分页、状态/关键词过滤、SELF/DEPARTMENT 范围 |
| persistsRequestIdAndRejectsMissingDevicePermission | HTTP 创建写入两表相同 request_id，员工创建 403 |
| validatesDisabledReferencesDatesAndReservedLocations | 停用部门/用户/保管人、人员无部门、仓库/库位占位值、保修倒序和非法 MAC 拒绝 |
| permitsNullIdentifiersAndEnforcesRealUniqueKeysAndForeignKeys | 多行 NULL SN/MAC/资产号；JDBC 直接违反四类唯一键及 Product/部门/使用人/保管人/历史设备外键均失败；引用阻止删除 |
| protectsProductAttributesWithoutBreakingNormalUpdatesOrDisabledProductDevices | 有 Device 后 Product 核心属性不可变，普通修改可用，停用 Product 后可维护已有 Device |
| recordsIdentityAssignmentAndDateHistoryAndLocksIdentityAfterLifecycleStarts | 身份/归属/入库时间记录前后值；仅备注/注意事项不增历史；首次状态变更后身份永久锁定 |
| reservesFutureStatusesAndMakesScrappingTerminal | 无归属不能使用；预留状态拒绝；报废终态且归属不可改 |
| intersectsAllFiltersWithScopeAndProtectsEveryWriteAndHistory | SELF 身份/筛选交集，SELF 写拒绝，部门子树、跨范围详情/历史/修改拒绝，无部门不会扩大查询 |
| verifiesPagingKeywordAndProductFiltersAgainstActualRows | 真实数据分页/数量/排序，产品名称、型号、设备编号、SN、MAC、资产号、部门、人员搜索和多条件过滤 |
| optionsWorkWithOnlyDevicePermissionAndExposeOnlyNecessaryUserFields | 仓库管理员无需用户管理权限即可选项查询；人员超过 100 条可搜索命中；不输出电话/密码/权限；越部门 403、未登录 401 |
| checksHttpUpdateStatusAuditAndReadOnlyIdentityBoundaries | HTTP 编辑/状态写审计，非法 request_id 自动替换；PUT 注入 status/productId 不能篡改；无权限及越权 403，非法状态 409，无原因/错误分页类型 400 |
| concurrentNumberAllocationAndDuplicateIdentityHaveDatabaseProtection | 多连接并发建档生成 8 个不同编号；重复身份竞争只有一个成功 |

大多数测试使用真实库事务回滚；并发测试使用独立连接提交，在 AfterEach 按本次精确 fixture ID 清理。最终设备表/历史表各 0 行，P3 测试 Product/用户/部门均为 0，没有保留虚构业务数据。自增编号会因测试/回滚产生空洞，这是预期行为。

全量测试明细：UserControllerTest 9，ErpApplicationTests 5，Phase1MySqlRegressionTests 2，Phase2MySqlRegressionTests 2，Phase3MySqlRegressionTests 13，AuthServiceTest 1，UserServiceTest 1，共 33。

复用 ProductMapper 的 active status + productType 组合查询曾在真实 MySQL 暴露动态 SQL `?AND` 缺空格问题，本阶段修复 selectPage/count 的条件分隔，设备选项和原 Phase 2 测试均已通过。MyBatis 与 JDBC 混用的测试在直接修改启用状态后显式清理一级缓存，不更改生产缓存配置。

## 文件清单

后端新增（以下路径位于 `erp/src/main/java/com/haucheng/erp`）：

- `entity/AssetDevice.java`、`entity/AssetDeviceHistory.java`
- `dto/DeviceCreateRequest.java`、`dto/DeviceUpdateRequest.java`、`dto/DeviceStatusRequest.java`
- `vo/DeviceView.java`、`vo/DeviceHistoryView.java`、`vo/UserOptionView.java`
- `mapper/AssetDeviceMapper.java`
- `service/AssetDeviceService.java`
- `controller/AssetDeviceController.java`

后端调整：`mapper/ProductMapper.java`（行锁、组合筛选 SQL），`mapper/UserMapper.java`（启用/部门引用校验），`service/ProductService.java`（Device 引用保护），`service/OperationLogService.java`（复用请求 ID 获取入口）。ProductController 最终保留原产品 API，没有新增重复的产品选项 API。

其他新增：上述 V5～V7 三个 SQL、`Phase3MySqlRegressionTests.java`、`erp-vue/src/views/asset/DeviceView.vue`、`docs/device-management.md`、本文档。

前端调整：`erp-vue/src/lib/api.js`、`src/router/index.js`、`src/layouts/AppLayout.vue`。文档调整：`docs/database-plan.md`。

技术栈、依赖和现有 API 包装均保留。没有引入 TypeScript、Axios、新 UI 或额外第三方库；新 asset 页面目录用于具体设备管理，无参考项目业务复制。

## 可重复执行

先在运行终端设置 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`；凭据不要写进脚本或仓库。DB_URL 指向 `jdbc:mysql://127.0.0.1:3306/erp?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true`。设置 Java 17，并设置 `ERP_MYSQL_TEST=true` 以启用真实回归。

```powershell
# 在 erp 目录；mvn 可使用已安装 Maven 的绝对路径
mvn test
mvn package -DskipTests
mvn flyway:validate flyway:info

# 在 erp-vue 目录
npm run build
```

Phase 2 既有真实数据库测试也依赖 DB 环境，因此不能省略连接配置并把 H2 结果称为完整回归。只有待执行的新库版本才运行 `mvn flyway:migrate`；当前 erp 已到 V7，无需重复修改 SQL 或 repair。

## 已知限制与 Phase 4 前置事项

- 前端构建成功但出现大于 500 kB 的 chunk 警告（当前主 JS 约 1,075.71 kB，gzip 339.59 kB）；本阶段不做无关打包架构调整。
- Lint 未配置，浏览器端到端测试未运行；不能把它们标为 PASS。Maven Wrapper 本机启动问题已有缓存 Maven 替代执行方式。
- 历史未分页，姓名/部门名为关联后的当前名称；大量业务事件上线前评估历史分页与名称快照。
- 仓库/库位预留字段当前只能为 NULL；身份状态约束由 Service 控制，数据库负责外键和唯一性。禁止业务侧绕过 Service 手工写未规范化 MAC 或状态。
- Phase 4 应先确定仓库/库位主数据及外键、设备期初归属与库存对账、设备型/数量型库存差异、执行单据与状态/历史的原子性及幂等性，再开放预留字段。未来借用/维修/调换/发货应由对应单据控制状态。
- 后续加入历史来源单据和业务时间、归属快照；二维码保持不透明标识，不携带 SN/MAC。

当前仅新增 asset_devices 和 asset_device_history 两张业务表，没有创建任何 Phase 4 库存或单据表。Phase 3 到此停止。
