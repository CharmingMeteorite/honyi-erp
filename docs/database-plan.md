# ERP 数据库蓝图

本文档规划完整 ERP 的数据领域。当前源码与指定开发库已推进至 Phase 8：V1～V3 为认证、组织权限和操作日志，V4 为物资主数据，V5～V7 为设备与历史，V8/V9 为仓库库存和期初库存，V10 为入出库，V11 为领用，V12/V13 为盘点，V14～V16 为维修、保修、换货及前向修正。2026-10-14 新建空 `erp` 库并按 Flyway 顺序迁移至 V13；实际验证见文末。调拨、借用、二维码等后续业务仍为规划。

## 领域规划

- 系统：用户、部门、角色、权限、操作日志
- 主数据：物资分类、品牌、单位、物资
- 仓储：仓库已实现；当前无库位/货架需求，不预建 location/bin 体系
- 库存：当前库存、库存流水
- 资产：设备、固定资产
- 单据：入库、出库、领用、发货、借用、归还、调拨
- 盘点：盘点单、盘点明细
- 服务：保修、维修、产品调换
- 审批：流程实例、任务
- 支撑：二维码、打印、附件、报表

## 规划表

```text
sys_user, sys_access_token, sys_department, sys_role
sys_permission, sys_user_role, sys_role_permission, sys_operation_log
md_categories, md_units, md_brands, md_products
wh_warehouses  # 已实现；当前不建立 wh_locations
inv_stocks, inv_stock_ledgers
inv_opening_balances, inv_opening_balance_items, inv_opening_balance_devices
asset_devices, asset_device_history
inv_inbound_orders, inv_inbound_items, inv_inbound_devices  # V10 已实现
inv_outbound_orders, inv_outbound_items, inv_outbound_devices
inv_order_device_claims
apply_requisitions, apply_requisition_items, apply_requisition_devices  # V11 已实现，最终验收受数据库连接故障阻塞
ship_orders, ship_order_items
loan_orders, loan_order_items
transfer_orders, transfer_order_items
stocktakes, stocktake_items
service_warranties, service_repairs
exchange_orders, exchange_relations
workflow_instances, workflow_tasks
files, qrcodes, print_jobs
```

## 核心模型

`Product` 表示“是什么物资”；`Device` 表示“具体是哪一台设备”；`Warehouse` 表示在哪个仓库；`Stock` 表示某产品在某仓库的当前库存；`StockLedger` 表示库存变化历史。Product 与 Device 分离，数量型物资按库存数量处理，设备型物资按设备实例处理。一个 Product 可以关联多个 Stock 和 Device。

入库、出库、调拨、借用、归还、盘点调整、调换以及维修完成重新入库等实际执行动作都必须产生库存流水。库存不能被页面直接编辑，正式业务单据不能随意物理删除；审批通过本身不等于库存变化，只有执行完成才改变库存。

SN 非空时必须唯一，MAC 非空时必须唯一，Device Code 必须唯一。设备生命周期必须可追溯，调换需要保存旧设备与新设备关系，借用必须有归还流程，盘点支持 SN 级设备盘点。当前按单仓库启动，Stock 只使用 warehouse_id + product_id 维度，不保存 device_id，也不预建库位维度。DEVICE 的多台设备合并成同仓库同产品的一条库存记录。

## Migration 原则

所有结构变更使用版本化 Migration，从空数据库可按顺序初始化。表使用 MySQL 8、InnoDB、utf8mb4；时间字段优先由 MySQL `CURRENT_TIMESTAMP` 和 `ON UPDATE` 维护；主键和唯一键规则在领域设计确定后统一执行。

## Phase 2 实际落地：物资基础资料

Phase 2 新增 Flyway `V4__create_master_data.sql`，未修改 V1/V2/V3。实际表为：

- `md_categories`：`id`、`parent_id`、`category_code`、`category_name`、`sort_order`、`status`、`created_at`、`updated_at`；分类编号和名称唯一，自引用外键支持无限层级。
- `md_units`：`id`、`unit_code`、`unit_name`、`precision_scale`、`status`、公共时间字段；编号和名称唯一。
- `md_brands`：`id`、`brand_code`、`brand_name`、`remark`、`status`、公共时间字段；编号和名称唯一。
- `md_products`：`product_code`、`product_name`、`category_id`、`brand_id`、`unit_id`、`model`、`specification`、`product_type`（`QUANTITY`/`DEVICE`）、`track_sn`、`track_mac`、`is_fixed_asset`、`enable_qrcode`、`safety_stock`、`min_stock`、`max_stock`、`remark`、`status`、公共时间字段。Product Code 唯一，并以外键关联分类、品牌、单位。

分类、单位、品牌停用后不能新建 Product；已被 Product 或下级分类引用的主数据不能物理删除。Product 只描述一种物资，不保存实际 SN、MAC、设备编号或库存数量；Phase 3 的 `Device` 保存具体设备实例及其 SN/MAC。当前库存字段仅为配置，不执行库存计算或预警。以上字段及 Product 唯一键、分类/品牌/单位外键已重新对照 MySQL 8.0.46 的 SHOW CREATE TABLE 核实，实际 ID 类型为 BIGINT。

## Phase 3 实际落地：具体设备

新增 `asset_devices` 和 `asset_device_history`。`asset_devices.product_id` 外键关联 `md_products`，设备编号、非空 SN、非空规范化 MAC、非空固定资产编号分别使用唯一键；MySQL 对可空唯一列允许多个 NULL，空白身份值先归一为 NULL。MAC 统一大写冒号格式，SN/资产号沿用 utf8mb4_unicode_ci 的比较规则。设备保存使用人、部门、保管人及其真实外键，采购/入库资料登记/首次使用时间、保修时间、备注和注意事项。

只有启用的 DEVICE Product 可以创建设备。已有设备的 Product 禁止改变类型、SN/MAC 管理和固定资产属性，普通 Product 维护继续可用。Device 与 Product 一对多分离；Device 绑定创建后不可更改。

Device 状态只表示实体生命周期状态，`IN_STOCK` 表示公司持有，不代表库存数量。Phase 3 基础状态允许 IN_STOCK 与 IN_USE 互转，或转 SCRAPPED；报废终态。Phase 4 对已归属仓库的设备关闭普通状态变更，保留后续正式库存单据入口。BORROWED/REPAIRING/PENDING_EXCHANGE/SHIPPED 保留到后续单据使用，当前不能手工进入。身份在首次状态变化后锁定；归属资料变化、身份及日期更正、建档和状态变化写入历史，所有成功写操作沿用现有 request_id 操作日志。

Phase 4 Stock 以 Product/仓库维度保存数量，StockLedger 记录执行产生的数量变化；设备型明细引用 Device，并将库存变化、状态变化、归属变化纳入一致事务。Repair/Loan 引用 Device，Exchange 明确保存旧、新 Device 关系；各单据应将来源类型/ID及业务时间扩展进设备历史。二维码以后以不透明标识关联 Device，不编码 SN/MAC，当前不创建二维码表。

V6 只补充设备权限到现有角色：仓库管理员拥有设备查看、创建、编辑和状态权限；普通员工拥有设备查看权限，服务层仍按 SELF 数据范围限制为本人作为使用人或保管人的设备。

V7 仅在 `asset_devices` 预留可空 `warehouse_id`、`location_id` 字段和索引，不创建仓库体系。Phase 3 时 DTO 和 Service 强制输入为 NULL。Phase 4 的 V8 为 warehouse_id 添加真实仓库外键，普通设备输入继续拒绝非空值，只有期初完成业务设置归属；location_id 仍为 nullable 预留，无库位表。基础建档不会自动回填或计算库存。

详细字段、状态机、编辑保护、数据权限和 API 见 [device-management.md](device-management.md)，实际验证证据见 [phase3-verification.md](phase3-verification.md)。Phase 3 的历史验证记录保持原样；Phase 4 的当前实现与验证见下文。


## Phase 4 实际落地：仓库、库存、流水、期初库存

2026-09-18 在迁移后的工作区 `/home/hc/codexerp`，真实连接当前 MySQL 8.0.46 的 erp 库，追加执行 V8、V9；V1～V7 文件未修改，Flyway V1～V9 全部 Success，validate 通过。凭据只在执行命令的进程环境中使用，不写入项目。

| 表 / 变更 | 实际字段和数据库约束 |
| --- | --- |
| wh_warehouses | 编码/名称各自唯一，department_id 真实外键用于既有 RBAC 数据范围；状态和备注，无删除/库位/货架 |
| inv_stocks | warehouse_id + product_id 唯一，两列真实外键，quantity/locked_quantity DECIMAL(18,4)；非负及 locked <= quantity 的 CHECK；可用数量实时相减 |
| inv_stock_ledgers | warehouse/product、business_type/business_no、direction、quantity、before/after、operator、operated_at、request_id；业务唯一键、外键和余额算术 CHECK |
| inv_opening_balances | opening_no 和 client_request_id 各自唯一；仓库、DRAFT/COMPLETED、remark、完成时间、操作人/request_id；状态 CHECK |
| inv_opening_balance_items | 单据、物资、正数数量；同单物资唯一，真实外键 |
| inv_opening_balance_devices | opening/item/device 连接表；同单设备唯一；完成时 initialized_device_id 唯一，防跨单重复初始化；真实外键 |
| asset_devices | warehouse_id 指向 wh_warehouses；新增 (warehouse_id,product_id,status) 计数索引；location_id 保持预留 |

V8 建立六张表、设备仓库外键和七个权限；V9 增加仓库部门范围、客户端请求唯一键、设备完成唯一标记、流水/单据 CHECK 和计数索引。数据库实际采用既有 BIGINT（非 UNSIGNED）、InnoDB、utf8mb4_unicode_ci，保持已有外键类型兼容。

期初保存不改变库存；完成事务统一更新 Stock、设备归属、设备历史、库存流水、单据状态及 operation_log。QUANTITY 不关联 Device；DEVICE 必须绑定具体 IN_STOCK 且尚无仓库的 Device，台数与数量一致。完成前后锁定计数保证设备数量等于 Stock.quantity。同一已完成单据和同一客户端创建请求不能重复执行或创建；同一 Device 不能重复初始化。

仓库停用与期初操作共享仓库行锁，产品/库存/设备按稳定顺序加锁；唯一键和非负 CHECK 为数据库保障。普通库存数量没有 CRUD，已建立库存的设备不能通过普通状态接口改变库存语义，Product 有库存/期初明细后不能改变类型和单位。

一致性检查只报告问题，不更新数据，不补造流水。当前支持分批期初，不具备识别不同单据手工重复录入同一现实数量批次的能力。

模型、权限、接口、并发和前端说明见 [inventory-management.md](inventory-management.md)，Phase 4 当时的真实验证和完整文件清单见 [phase4-verification.md](phase4-verification.md)。这些历史验证不能替代数据库恢复后的重新核验。Phase 5 入库、出库现有代码的接管状态见下文；领用、独立发货单、借还、调拨、盘点、维修、换货、二维码、报表等后续功能不在本次范围。

## Phase 5 实际落地：入库、出库与设备联动

2026-09-20 已确认工作区 V1～V10 文件及 Phase 5 代码仍在。当前真实 MySQL 8.0.46 的 Flyway history 为 V1～V10 Success，validate 通过；V1～V7 未修改，恢复库 Pending 的 V8、V9、V10 按原顺序应用。

所有 V1～V10 SQL 文件内容保持接管时原样。实际 migrate 一次从 V7 应用到 V10，没有在 V9 单独停下验收，且补执行 V8/V9 前未取得单独确认；这是相对于原接管顺序的偏差，详见 [phase5-verification.md](phase5-verification.md)。本节不声称本次仅迁移了 V10。

V10 定义七张表：入库/出库各自的 orders、items、devices，以及统一设备占用表 `inv_order_device_claims`。脚本包含单号/客户端请求唯一键、状态和审批/执行 CHECK、正数数量、产品类型 CHECK、真实及复合外键；新增 12 个权限及角色授权，扩展 Device history 的来源/目标仓库和业务类型/单号。

正式执行在同一事务中更新 Stock、Device 的状态与 warehouse、Device history、StockLedger、单据状态及 OperationLog。草稿/审批不改变库存；设备占用在拒绝/取消/完成时释放。出库只允许扣减可用余额，设备出库进入 SHIPPED 并清空仓库。Stock 维度仍为 warehouse_id + product_id，没有加入 device_id 或库位维度。

当前库已逐项核验表、字段、索引、外键、唯一键、CHECK、权限和历史扩展，275 项元数据断言通过。七张 V10 表、仓库、库存、流水、设备及 Device history 的最终 fixture 行数为 0；测试数据丢失未触发业务设计调整。

Phase 5 回归 15 项、完整 Maven 测试 70 项、Maven package 和前端 build 均通过。测试覆盖状态流转、草稿/审批/取消/执行、权限与数据范围、QUANTITY/DEVICE、库存不足、Device warehouse/status/history、StockLedger、OperationLog request_id、客户端唯一键、版本控制、重复及并发执行、并发库存/设备、事务回滚和库存一致性。证据见 [phase5-verification.md](phase5-verification.md)，源码行为见 [inbound-outbound-management.md](inbound-outbound-management.md)。

## Phase 6 实际落地：领用申请与仓库发放（最终验收待恢复数据库）

2026-09-20 在 Phase 5 的 V10 基础上追加应用 V11，Flyway migrate / validate / info 当时通过。2026-09-21 再次核对源码 SHA256，V1～V10 均未修改。V11 建立 `apply_requisitions`、`apply_requisition_items`、`apply_requisition_devices`，扩展共用的 `inv_order_device_claims` 并添加六个领用权限及角色授权。

申请人为登录用户，部门保存快照；单据支持草稿、提交审批、通过/拒绝、取消、整单发放。审批不改变库存；只有 APPROVED 单据发放时在同一事务内扣减 Stock，设置 DEVICE 为 IN_USE、清空仓库并更新申请人归属，写入 StockLedger、DeviceHistory、OperationLog，再完成单据。设备占用主键跨入库/出库/领用复用，配合版本控制、行锁、条件库存更新和数据库唯一键防止重复发放与并发超扣。库存维度仍为仓库 + 产品。

Phase 6 的 14 项真实 MySQL 回归及前端 build 在 2026-09-20 通过。2026-09-21 完整测试因 MySQL 不响应握手而失败：84 项中 19 项通过、65 项数据库上下文错误；Maven package 和最终数据库复核当时尚未完成。实现详见 [requisition-management.md](requisition-management.md)，历史结果和阻塞见 [phase6-verification.md](phase6-verification.md)。本次 Phase 7 在新建的指定开发库完成了 Phase 1～6 的重新回归，结果见下文。

## Phase 7 实际落地：库存盘点

2026-10-14 在指定的 MySQL `192.168.1.247:3307/erp` 新建空库，按 Flyway 正常顺序应用当前源码 V1～V11，并独立 validate 通过。随后 V12 新建 `inv_stocktakes`、`inv_stocktake_items`、`inv_stocktake_device_checks`，加入盘点权限；V13 将设备编号唯一约束限定在盘点明细内，允许同一设备在不同产品明细中形成“错产品”结构化差异。V1～V11 与已经应用的 V12 均未改写。

盘点单状态为 `DRAFT → COUNTING → COUNTED → CONFIRMED`，未确认状态可取消。提交时保存每个产品的账面数量、锁定数量和最后流水 ID，设备产品还保存逐台身份快照。录入实盘不修改库存；确认时持有单据、仓库、产品及库存锁，重查快照基准，任何后续库存或流水变化都拒绝确认。数量差异通过现有 `inv_stocks` 和 `inv_stock_ledgers` 调整；设备缺失经确认后状态变为 `MISSING`，保留原设备及其最后登记仓库，写 `asset_device_history` 和库存流水。账外、未登记、错产品、库外或状态异常设备只记录结构化差异，阻止自动调整。所有确认写入在同一事务，版本号和行锁阻止重复确认。

实际接口、页面、差异语义及限制见 [stocktake-management.md](stocktake-management.md)；当前真实 MySQL 验证见 [phase7-verification.md](phase7-verification.md)。Phase 9 以后仍未实现。

## Phase 8 实际落地：维修、保修与换货

2026-10-14 在指定 MySQL `192.168.1.247:3307/erp` 核对 V13 及全部 checksum 后，先备份数据库，再前向应用 V14～V16。V14 建立独立的维修、保修、换货单，换货新旧设备关系，统一客户端动作防重表；扩展已有设备占用表，使库存入出库、领用、维修和换货互斥。V15 增加设备版本快照，防止中途设备资料变化后恢复旧归属；V16 修正保修菜单的复数路径。已经应用的 Migration 均未改写。三版真实迁移、validate/info、约束与权限核验及测试结果见 [phase8-verification.md](phase8-verification.md)。

维修库存设备送修写 `REPAIR_OUT` 流水，完成返库写 `REPAIR_RETURN`；换货新设备扣库写 `EXCHANGE`。设备状态、仓库归属、库存、流水、设备历史、单据状态和操作日志处于同一事务。保修申请保留保修日期快照，过保、尚未开始或日期不完整者不能按保内批准；可以明确拒绝或转自费维修。旧、新设备是独立主键与身份，换货单永久记录两者关系。

接口、页面、状态与限制分别见 [repair-management.md](repair-management.md)、[warranty-management.md](warranty-management.md)、[exchange-management.md](exchange-management.md)。二维码、报表、附件和通知仍不在当前实现范围。
