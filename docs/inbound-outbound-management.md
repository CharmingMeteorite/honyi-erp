# 入库与出库管理（Phase 5）

2026-09-20 接管状态：Phase 5 已完成。V10 已在当前真实 MySQL 8.0.46 执行并通过结构核验；真实回归 15 项、完整测试 70 项和 Maven package 均通过。详细证据见 [phase5-verification.md](phase5-verification.md)。

## 单据流程

入库与出库共用 `InventoryOrderService`，分别由 `InboundOrderController`、`OutboundOrderController` 暴露 `/api/inventory/inbounds`、`/api/inventory/outbounds`。返回沿用 `ApiResponse`、`PageResult`，错误沿用 `BusinessException`。

| 当前状态 | 源码允许的动作 | 结果 |
| --- | --- | --- |
| DRAFT | 编辑、提交、取消 | DRAFT、PENDING_APPROVAL、CANCELLED |
| PENDING_APPROVAL | 批准、拒绝 | APPROVED、REJECTED；拒绝需要原因 |
| REJECTED | 编辑、取消 | DRAFT、CANCELLED；编辑后重新提交 |
| APPROVED | 执行 | COMPLETED |
| COMPLETED / CANCELLED | 查询 | 不允许再次执行或修改 |

草稿、提交和批准不改变库存；设备在有效单据中通过 claim 占用。拒绝、取消或完成释放占用。创建使用不可修改的 `clientRequestId`；后续动作提交 `version`，Service 检查版本与状态。真实 MySQL 回归已验证唯一键、版本控制、重复执行和并发效果。

## 库存和设备

QUANTITY 明细仅操作仓库/产品库存，不能关联设备；出库检查 `quantity - locked_quantity`。DEVICE 明细必须选择具体设备，数量与台数一致，并校验产品、部门范围、设备身份快照及占用归属。

DEVICE 入库要求 `IN_STOCK` 且尚无仓库，执行后设置 warehouse；出库要求 `IN_STOCK` 且属于单据仓库，执行后设置 `SHIPPED` 并清空 warehouse。执行前后检查同仓库同产品的 Device 台数与 Stock 数量。一致性失败报错，不自动补库存或流水。

`execute` 标注方法级 `@Transactional`；调用链内处理库存、设备、设备历史、库存流水、claim 释放、单据状态与操作日志。源码在最终保存单据时记录执行审计，历史和流水带业务类型、业务单号及 request_id。真实 MySQL 回归通过末端审计故障注入和多连接并发测试，确认了事务边界和整体回滚。

## 数据库与权限

`V10__create_inbound_outbound_orders.sql` 定义：

- `inv_inbound_orders/items/devices`、`inv_outbound_orders/items/devices` 六张单据表，以及 `inv_order_device_claims`。
- 单号和客户端请求唯一键、同单产品和设备唯一键、明细复合外键、Device claim 主键及归属 CHECK。
- 状态、非负版本、审批资料、执行资料、正数数量和产品类型 CHECK。
- `asset_device_history` 新增 `from_warehouse_id`、`to_warehouse_id`、`business_type`、`business_no`，以及两个仓库外键。
- 入库和出库各六个权限：view、create、update、submit、approve、execute；取消复用 update，拒绝复用 approve。

脚本已在当前库生效：SYSTEM_ADMIN、WAREHOUSE_ADMIN 拥有上述全部权限，MANAGEMENT、DEPARTMENT_MANAGER 拥有查看权限，按钮已挂到各自查看菜单。真实数据库已核对权限、约束、索引、外键和 Device history 扩展。

仓库按既有 RBAC 数据范围过滤：ALL 可访问全部，DEPARTMENT 可访问本部门及子部门仓库；SELF 或无部门的部门范围不能访问整仓库存。Device 另校验部门范围，列表/count、详情、选项和写操作均须通过范围检查。权限与数据范围同时生效。

## 接口和页面

以下路径分别以 `/api/inventory/inbounds`、`/api/inventory/outbounds` 为前缀。

| 方法/路径 | 用途 | 权限后缀 |
| --- | --- | --- |
| GET /、GET /{id} | 分页及单据详情（明细、设备快照、操作日志、执行流水） | view |
| POST / | 创建草稿 | create |
| PUT /{id} | 编辑草稿或修订被拒绝单据 | update |
| POST /{id}/submit | 提交审批 | submit |
| POST /{id}/approve、/{id}/reject | 批准/拒绝 | approve |
| POST /{id}/execute | 执行入出库 | execute |
| POST /{id}/cancel | 取消草稿或被拒绝单据 | update |
| GET /options/warehouses | 范围内仓库 | view/create/update 任一 |
| GET /options/products、/options/devices | 选物资、选具体设备 | create/update 任一 |

创建/更新使用 `clientRequestId`、`warehouseId`、`remark`、`items`；每项含 `productId`、`quantity`、`deviceIds`。更新和动作请求携带 `version`；拒绝还需 `reason`，最多 200 字。列表支持仓库、状态、单号、创建日期和 page/size。单据没有物理删除 API，过期版本和重复执行返回冲突。

Vue 的 `InventoryOrderView.vue` 由 `/inventory/inbounds`、`/inventory/outbounds` 传入 kind 复用，调用既有 `lib/api.js` fetch 封装。页面提供查询、草稿编辑、设备分页选择、审批原因、执行确认、详情/操作日志/流水以及按权限和状态显示的动作。前端 JavaScript 构建已通过；浏览器 E2E 未执行。

## 当前边界

此次接管未修改生产代码或 Migration；仅对 `Phase5MySqlRegressionTests.java` 做了两行 lambda 变量最小修复。数据库恢复时缺失的测试数据未被补造；测试 fixture 由回归清理，未改变业务设计。领用、借还、调拨、盘点、维修等 Phase 6 及以后功能不在本次范围。
