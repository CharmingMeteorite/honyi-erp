# Phase 6 实现与验证记录

工作区：`/home/hc/codexerp`。记录日期：2026-09-21（Asia/Shanghai）。**当前结论：实现已落地，最终验收受 MySQL 连接故障阻塞，Phase 6 尚未完成全部验收。**

## 已完成实现

已读取 AGENTS.md、代码规范、参考索引、数据库计划及 Phase 5 库存单据实现。新增领用申请、提交审批、通过/拒绝、取消、仓库发放、QUANTITY 扣减、DEVICE 状态及归属变化、StockLedger、DeviceHistory 和操作日志。实现复用既有 RBAC、request_id、数据范围、事务、库存 Mapper 及正式单据设备占用机制；模型和接口见 [requisition-management.md](requisition-management.md)。

新增 `V11__create_requisitions.sql`、RequisitionController / RequisitionService / RequisitionMapper、ApplyRequisition、RequisitionRequest、RequisitionView、Vue 领用页面及 Phase6MySqlRegressionTests。配套修改仅涉及 StockMapper 产品引用检查、AssetDeviceMapper / AssetDeviceService / DeviceView 的发放与保护、前端 API/菜单/路由/流水类型。未新增依赖、TypeScript、ServiceImpl 或 Phase 7 功能。

2026-09-21 以 `erp/target/phase6-source-baseline.json` 的 SHA256 重新核对，V1～V10 全部与 Phase 6 开始时一致；Phase 1～5 测试源码未修改。工作区无 Git 元数据，因此不声称存在 Git 提交或 diff。

## 实际验证结果

| 项目 | 结果 | 日期与证据 |
| --- | --- | --- |
| Flyway migrate | PASS | 2026-09-20 16:54:30 从 V10 应用 V11；`erp/target/phase6-flyway.log` |
| Flyway validate / info | PASS（当时） | 同次命令验证 11 项迁移；V1～V11 Success |
| 编译及测试编译 | PASS | 已完成后端编译并执行以下真实回归 |
| Phase 6 真实 MySQL 回归 | PASS（当时） | 2026-09-20，14 tests，0 failures / errors / skipped；`erp/target/phase6-mysql-regression.log` |
| npm run build | PASS | 2026-09-20 工具输出，1626 modules；主 JS 约 1134.08 kB，有既有构建体积提示；未单独保存日志 |
| 完整 Maven test | FAILED：数据库连接错误 | 2026-09-21，84 tests，0 failures、65 errors、0 skipped；19 项通过；`erp/target/phase6-full-test.log` |
| 本次独立 Flyway info | FAILED：数据库连接错误 | 2026-09-21，同样无法从 MySQL 获取响应 |
| Maven package | NOT RUN | 数据库故障后停止，未跳过测试打包 |
| 最终数据库结构/残留数据/一致性复核 | BLOCKED | 数据库当前不可用；不把上一日结果视作当前现场结论 |
| 浏览器 E2E | NOT RUN | ERP 未配置浏览器 E2E |

成功与失败记录必须区分：当前 `target/surefire-reports/` 已被 2026-09-21 失败的全量运行覆盖；14 项通过的证据是保留的 `phase6-mysql-regression.log`，不能引用当前 Surefire 报告声称全部通过。`target/` 是构建目录，clean 会删除这些证据。

## 真实 MySQL 回归覆盖

14 项 Phase 6 测试覆盖单据流转、审批库存不变、发放状态与归属/历史/流水、跨领用及出库的设备占用、拒绝/取消释放、可用库存不足、后续明细失败回滚、末端审计故障整体回滚及重试、停用引用及设备身份变化、申请人部门变化、库存/设备不一致拒绝且不修复、SELF/部门范围、真实数据库约束及产品属性冻结。

并发测试使用真实连接和独立事务，覆盖同客户端请求创建、同单提交/审批/发放、发放与取消竞争、数量领用与出库竞争、同设备占用竞争和不同设备并发发放。HTTP 测试覆盖权限、参数校验、request_id 和 ThreadLocal 清理。完整 Phase 1～6 回归仍需在数据库恢复后通过。

## 当前阻塞

用户提供的 MySQL 地址为 `127.0.0.1:3306/erp`。2026-09-21 完整测试在 Flyway 初始化连接时出现 `Communications link failure`，底层为 `SocketTimeoutException: Read timed out`，65 项数据库测试未能建立 Spring 上下文；没有证明业务断言失败，也没有证明全量回归通过。

只读探测确认端口监听、mysqld 进程存在，但 TCP 建连后收不到 MySQL 初始握手包。独立 Flyway 也连接失败，因此未通过修改应用超时或跳过 MySQL 测试掩盖故障。没有执行容器重启、Flyway repair/baseline、旧迁移修改或数据库恢复。依用户“工具不可用，明确停止”的要求，在保存本记录后停止，等待数据库恢复。

凭据仅注入命令进程环境，没有写入项目。系统没有独立 mvn/mysql 命令，Maven 使用项目自带 `bash mvnw`，数据库验证通过 JDBC 和真实集成测试。恢复后需提供有效的 DB_URL / DB_USERNAME / DB_PASSWORD 环境变量，按以下步骤继续（不跳过测试）：

```bash
# 在 erp 目录，DB_* 由调用环境提供
bash mvnw flyway:validate flyway:info
ERP_MYSQL_TEST=true bash mvnw test
ERP_MYSQL_TEST=true bash mvnw package
# 在 erp-vue 目录
npm run build
```

最后重新检查真实数据库的迁移、权限、约束、测试数据清理及库存一致性。V11 已经成功应用，恢复后先检查 history；不得因连接故障修改 V11 或重置迁移记录。
