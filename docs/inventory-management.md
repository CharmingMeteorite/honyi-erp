# Phase 4 仓库与期初库存

Phase 4 建立仓库、库存、库存流水和期初单据。当前实际业务为单仓库，无库区、货架或库位体系；页面允许维护仓库主数据，但不提供仓库间库存移动。本文描述 V8/V9 和当前代码。

## 模型与表

| 对象 / 表 | 含义与关键约束 |
| --- | --- |
| Product / `md_products` | 是什么物资，`QUANTITY` 或 `DEVICE`；有库存或期初明细后不能改变类型和单位；已有设备的身份管理属性保护沿用 Phase 3 |
| Device / `asset_devices` | 具体哪台设备；`warehouse_id` 现在有真实仓库外键，`location_id` 保持 nullable 预留；普通创建/编辑不能设置仓库 |
| Warehouse / `wh_warehouses` | 仓库编码、名称分别唯一，所属部门、启用状态、备注、时间；部门用于现有数据范围，创建后固定，无删除接口 |
| Stock / `inv_stocks` | 唯一维度 `warehouse_id + product_id`；`DECIMAL(18,4)` 数量，不含 `device_id`；CHECK 保证 `0 <= locked_quantity <= quantity` |
| StockLedger / `inv_stock_ledgers` | 每个实际变化一条：仓库、物资、业务类型/单号、IN/OUT、数量、前后数量、操作人、操作时间、request_id；唯一业务键防重复写流水 |
| OpeningBalance / `inv_opening_balances` | `OB-UUID` 单号、全局唯一 `client_request_id`、仓库、DRAFT/COMPLETED、备注、完成时间、操作人和 request_id |
| OpeningBalanceItem / `inv_opening_balance_items` | 同单同物资唯一，正数数量；一单最多 100 行 |
| OpeningBalanceDevice / `inv_opening_balance_devices` | 设备明细连接表；同单设备唯一，完成时填充 `initialized_device_id`，该字段唯一以保证同一设备只能被期初初始化一次 |

可用库存由 SQL 计算 `quantity - locked_quantity`。本阶段锁定数量始终为默认 0，没有锁库、预占或任意修改库存数量的接口。数量输入最多 14 位整数、4 位小数，并校验物资单位的 `precision_scale`；DEVICE 数量必须等于选中设备的整数台数，每行最多 1000 台。零数量明细无需建立，负数拒绝。

流水本阶段只写 `OPENING_BALANCE`。`business_type` 使用可扩展 VARCHAR，为 `INBOUND`、`OUTBOUND`、`TRANSFER`、`LOAN`、`RETURN`、`STOCKTAKE`、`ADJUSTMENT`、`EXCHANGE`、`REPAIR_RETURN` 留出表达空间；这些类型没有执行 API，也不生成展示用假流水。CHECK 校验数量为正、前后余额非负，以及 IN/OUT 对应的算术关系。

## 期初单流程

1. 创建 DRAFT：选仓库、物资、数量；DEVICE 必须同时选择具体设备。保存只登记单据，不改变库存，不占用设备。
2. DRAFT 可编辑仓库、明细和备注，替换明细在事务内执行；创建请求标识不可修改。不提供删除或取消动作。
3. 显式完成：重新检查仓库和物资启用、设备类型/归属/状态、数据范围和既有库存一致性。成功后为 COMPLETED，不再允许编辑或重复完成。

QUANTITY 明细不得绑定任何设备，完成时还会检查该物资本身不存在非法设备实例。DEVICE 的每台设备必须属于对应 Product，处于 `IN_STOCK`，尚未归属任何仓库，且位于操作人的设备数据范围内。设备在草稿保存后可能变化，因此完成时必须再次验证。

完成一个单据在同一数据库事务内执行全部明细：锁定/创建 Stock、增加数量、写设备仓库归属、标记设备已初始化、写 `asset_device_history`（OPENING_BALANCE 事件）、写真实库存流水、完成单据并写 `sys_operation_log`。任何一步（包括最后审计日志）失败时，整张单据全部回滚。设备保持 IN_STOCK，不伪造采购入库日期或状态变化。

允许分批建立期初库存，所以同仓库同产品的不同有效期初单会累加；数量型物资的现实批次没有独立批次标识，系统不能识别用户用**不同请求标识和不同单据**人为重复登记同一批物资，应在完成前核对现有单据。

## 幂等、并发与锁

- 前端新建表单时生成 `clientRequestId`，同一表单重试沿用该值。数据库唯一键拒绝重复创建，返回 409；请求重试不会产生第二张可执行单据。客户端应查询原单据确认结果。
- 完成使用单据 ID 和 `SELECT ... FOR UPDATE`。并发或串行重复完成只会有一次效果；后续请求返回 409，而非再次增加 QUANTITY 库存。
- 仓库状态修改和所有期初写操作锁同一仓库行。停用提交后不会有新的期初动作接受该仓库。当前单仓库采用较粗的串行锁，优先保证正确性。
- 完成锁顺序：单据 → 仓库 → 按 Product ID 升序 → 对应 Stock → 按 Device ID 升序。Product 行锁与已有 Product 编辑/停用和 Device 创建共用；所有明细按同一顺序处理，避免不同单据反向获取产品锁。
- Stock 使用 `(warehouse_id, product_id)` 唯一键、创建时 upsert、行锁、原数量条件更新和 CHECK。并发增加无丢失更新，不产生负库存。
- Device 行锁与现有设备状态/编辑共用，归属写入还带 `warehouse_id IS NULL AND status='IN_STOCK'` 条件。两个仓库同时初始化同一设备只有一个成功；完成标记的唯一索引作为持久化防重约束。
- 设备数在事务中使用锁定读取，避免 MySQL REPEATABLE READ 等待锁后读到旧快照。完成前、后都校验 Stock.quantity 与仓库内同 Product 的 IN_STOCK 设备数相等；已有不一致时拒绝继续初始化，不自动修账。

## 保留的设备规则

普通 Device PUT 不接收仓库变更，Mapper 普通更新也不再写 warehouse_id/location_id，因此空输入不能清除已建立的仓库归属。详情展示仓库名称。

未建立仓库库存的设备继续使用 Phase 3 基础状态登记规则。已经归属仓库的设备，其普通状态入口拒绝变化，`allowedStatuses` 为空，避免在库存数量不变时把设备手工改成 IN_USE 或 SCRAPPED。正式出库、领用、调拨、借用等必须由后续单据原子更新状态、归属和库存。

Product、device_code 不可普通编辑，身份字段的首次状态历史保护、部门和人员引用校验仍沿用 Phase 3。IN_STOCK 仍是生命周期状态，未归属仓库的设备不自动计入任何 Stock。

## RBAC 与数据范围

| 权限 | 默认角色 |
| --- | --- |
| system.warehouse.view | SYSTEM_ADMIN、WAREHOUSE_ADMIN、MANAGEMENT、DEPARTMENT_MANAGER |
| system.warehouse.create / update / disable | SYSTEM_ADMIN、WAREHOUSE_ADMIN |
| inventory.stock.view / ledger | SYSTEM_ADMIN、WAREHOUSE_ADMIN、MANAGEMENT、DEPARTMENT_MANAGER |
| inventory.stock.opening | SYSTEM_ADMIN、WAREHOUSE_ADMIN |

全部入口使用 RequirePermission，普通员工没有新增权限。仓库启停是独立动作，不允许 PUT 携带 status 绕过 disable 权限。

- ALL：查看、操作其功能权限允许的全部仓库，包括无所属部门的全局仓库。
- DEPARTMENT：仓库所属部门在本部门及子树内；无部门用户不会扩大成 ALL；创建仓库时必须选择可访问且启用的部门。
- SELF：没有可归属到个人的仓库余额，返回空列表，拒绝仓库/期初写入。不能按个人设备数伪装成仓库整体库存。

仓库、Stock、流水、期初单和一致性结果的列表/count/详情/写入都沿用仓库范围，显式查询越权仓库返回 403。DEVICE 期初创建和完成还校验设备本身的部门范围；库存余额按仓库整体显示。产品是共用主数据，选项不依赖额外产品管理权限。期初设备选项按设备范围、Product、IN_STOCK 和无仓库条件过滤，支持关键词及分页。

## API

JSON 均为 ApiResponse，分页均为 PageResult（默认 20、最大 100）。无普通 Stock CRUD，也无流水编辑入口。

| 方法 / 路径 | 作用 / 权限 |
| --- | --- |
| GET `/api/system/warehouses`、`/{id}` | 分页/详情；system.warehouse.view |
| POST `/api/system/warehouses` | 创建启用仓库；system.warehouse.create |
| PUT `/api/system/warehouses/{id}` | 编码/名称/备注；system.warehouse.update，所属部门不可更改 |
| POST `/api/system/warehouses/{id}/enable`、`/disable` | system.warehouse.disable |
| GET `/api/system/warehouses/options/departments` | create/update 权限下的范围内部门选项 |
| GET `/api/inventory/stocks` | warehouseId/productId/keyword/page/size；inventory.stock.view |
| GET `/api/inventory/stocks/consistency` | warehouseId 可选；inventory.stock.view |
| GET `/api/inventory/ledgers` | warehouseId/productId/businessType/page/size；inventory.stock.ledger |
| GET `/api/inventory/openings`、`/{id}` | 分页（warehouseId/status/page/size）/含明细详情；inventory.stock.opening |
| POST `/api/inventory/openings` | 创建 DRAFT，必填 clientRequestId、warehouseId、items；inventory.stock.opening |
| PUT `/api/inventory/openings/{id}` | 修改 DRAFT，沿用原 clientRequestId；inventory.stock.opening |
| POST `/api/inventory/openings/{id}/complete` | 完成；inventory.stock.opening |
| GET `/api/inventory/options/warehouses` | 三个库存权限任一，分页和范围内仓库 |
| GET `/api/inventory/options/products` | opening 权限，启用产品，keyword/page/size |
| GET `/api/inventory/options/devices` | opening 权限，必填 productId，keyword/page/size |

期初请求示例（ID 仅说明 JSON 形状）：

```json
{
  "clientRequestId": "客户端生成的稳定UUID",
  "warehouseId": 1,
  "remark": "系统启用时的已有库存",
  "items": [
    { "productId": 10, "quantity": "12.5", "deviceIds": [] },
    { "productId": 20, "quantity": "2", "deviceIds": [101, 102] }
  ]
}
```

operation_log、设备历史和库存流水使用 `OperationLogService.currentRequestId()` 读取同一 RequestIdFilter 上下文。完成请求的 request_id 和操作人也写入单据。直接 Service 调用没有 HTTP 上下文时 request_id 可为 NULL；`clientRequestId` 是独立、必填的业务创建去重键，不用追踪 ID 代替业务幂等键。

## 一致性检查

只执行一条查询，在一致快照内检查：DEVICE 余额不匹配、有仓库 IN_STOCK 设备但缺少 Stock、QUANTITY 关联设备、无效设备仓库，以及非法余额/锁定量。外键和 CHECK 已阻止常规非法写入，检查仍保留这些问题类型以供排查历史/人工绕过约束的数据。结果只报告仓库、物资、问题、账面数量和设备数量，不修复、不补流水。未分配仓库的合法 DEVICE 不视为差错。

## 页面与范围终点

页面为 `/system/warehouses`、`/inventory/stocks`、`/inventory/ledgers`、`/inventory/openings`，仍为 Vue JavaScript + Element Plus + 原生 fetch。包含筛选、分页、详情、草稿编辑、设备搜索选择、完成确认、结果和错误提示。新 `views/inventory` 目录仅承载库存页面。

Phase 5 需另行明确入/出库及单据执行规则，复用此阶段库存/流水/锁/审计基础。当前不实现入库、出库、领用、发货、借用、归还、调拨、盘点、维修、换货、二维码、报表、审批或库位体系。
