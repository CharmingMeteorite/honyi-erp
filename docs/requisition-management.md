# Phase 6：领用申请与仓库发放

Phase 6 复用既有 Controller / Service / MyBatis Mapper / Entity / DTO / VO、ApiResponse、PageResult、RBAC 和操作日志。前端继续 Vue 3 `<script setup>`、JavaScript、Element Plus 和 `lib/api.js`。没有新增依赖或 ServiceImpl，没有实现 Phase 7 及以后的业务。当前验收状态见 [phase6-verification.md](phase6-verification.md)。

## 单据与状态

申请人为当前登录用户，同时是设备发放后的使用人和保管人；部门在创建时保存快照，不支持代他人申请。申请人必须启用且归属启用部门；提交及发放时再次校验，部门变化后需取消并重新申请。

```text
DRAFT → PENDING_APPROVAL → APPROVED → COMPLETED
                        → REJECTED → 编辑为 DRAFT → 重新提交
DRAFT / PENDING_APPROVAL / APPROVED / REJECTED → CANCELLED
```

只有申请人可以编辑、提交或取消。只有草稿和已拒绝单据可编辑；提交后冻结明细。拒绝必须填写原因。审批权限与发放权限分开；SELF 数据范围不能审批或发放。沿用现有单阶段审批方式，没有新增通用流程引擎，也没有新增禁止申请人审批本人单据的规则。

审批只更新单据和操作日志，不改变 Stock、Device 或 StockLedger。发放一次完成整单，不支持部分发放。完成、取消的单据不可继续修改，无物理删除接口。

## V11 数据库变更

V1～V10 保持原样，追加 `V11__create_requisitions.sql`：

| 对象 | 用途和约束 |
| --- | --- |
| apply_requisitions | 单号、客户端请求唯一键；仓库、申请人、部门快照、状态、version、提交/审批/执行记录、备注和 request_id；外键及状态/执行 CHECK |
| apply_requisition_items | 单据/物资、产品类型快照、正数数量；同单同物资唯一，真实外键 |
| apply_requisition_devices | 明细/设备关联及设备编号、SN、MAC、资产号快照；同单同设备唯一，复合外键保证明细属于本单 |
| inv_order_device_claims | 扩展 requisition_device_id；继续以 device_id 主键保证设备只由一个正式单据占用，CHECK 保证入库/出库/领用三类归属恰有一个 |
| sys_permission / sys_role_permission | 新增六个领用权限及既有角色授权 |

DEVICE 在草稿保存时选择具体设备并占用；拒绝、取消、发放完成时释放。拒绝后重新编辑会重新校验和占用。领用与既有入库/出库共用占用表，也排除期初草稿引用的设备。占用不改变库存数量；QUANTITY 不预留数量，发放时以可用库存决定是否允许执行。

## 库存、设备和事务

所有写入口使用 `@Transactional`。发放锁定单据并检查 version 和 APPROVED 状态，对申请人及其部门做共享锁读取，锁定仓库，按产品 ID 升序取得产品和 Stock 锁，再按设备 ID 锁定设备。沿用既有库存单据的仓库/产品/库存/设备锁定顺序。

QUANTITY 扣减 `quantity - locked_quantity` 范围内的数量，校验产品类型、单位精度和数量上限。Stock 条件更新同时检查原余额及非负/锁定数量，数据库约束兜底。DEVICE 数量必须等于所选台数，执行前后均核对同仓库同产品的在库设备数与 Stock 数量；发现不一致拒绝执行，不自动修复。

DEVICE 发放时校验占用归属、身份快照、产品、IN_STOCK 状态和仓库；更新为 IN_USE、清空 warehouse_id、使用人和保管人改为申请人、部门改为申请部门。首次使用时间仅在原值为空时设置。DeviceHistory 保存状态、仓库和归属变化，以及 REQUISITION、业务单号、操作人、request_id。

StockLedger 每项记录 REQUISITION / OUT、扣减数量及前后余额。库存、设备、历史、流水、占用释放、单据 COMPLETED 和 `REQUISITION_ISSUE` 操作日志全部在同一事务提交；任一步失败整体回滚。单据行锁/version、客户端唯一键、设备占用主键和库存流水业务唯一键共同防止重复创建、占用和发放。

有领用历史的设备不能通过普通设备编辑改变使用人/部门/保管人，也不能通过普通状态接口回到库存或改变状态；归还等动作留待后续阶段。产品引用检查包含领用明细，禁止已有单据引用的 Product 改变管理类型和单位。

## 权限与数据范围

权限前缀为 `apply.requisition.`，后缀为 view、create、update、submit、approve、issue。update 同时控制取消入口。

| 既有角色 | 新增授权 |
| --- | --- |
| SYSTEM_ADMIN | 六项全部 |
| EMPLOYEE | 查看、创建、编辑/取消、提交 |
| DEPARTMENT_MANAGER | 查看、创建、编辑/取消、提交、审批 |
| WAREHOUSE_ADMIN | 查看、创建、编辑/取消、提交、发放 |
| MANAGEMENT | 查看 |

ALL 可访问全部单据；DEPARTMENT 沿用部门子树并按申请部门限制；SELF 仅能访问本人申请。SELF 的仓库/设备选择限于当前本人部门，部门范围用户的选项和实际发放也校验仓库及设备所属部门。实际权限取决于既有角色配置，不以角色名称替代权限校验。

本人可查看申请内的发放流水；没有对应仓库库存数据范围时，详情隐藏流水前后余额，避免通过领用详情暴露整仓库存。通用库存和流水接口继续沿用既有数据范围。

## API 与页面

基础路径 `/api/apply/requisitions`；JSON 统一使用 `ApiResponse`，分页使用 `PageResult`。

| 方法/路径 | 行为 |
| --- | --- |
| GET / | 分页；支持 warehouseId、status、keyword（单号）、fromDate、toDate |
| GET /{id} | 单据、明细、具体设备、操作记录、发放流水 |
| POST / | 创建草稿；clientRequestId、warehouseId、remark、items |
| PUT /{id} | 编辑草稿/已拒绝单据；必须带当前 version，clientRequestId 不可更改 |
| POST /{id}/submit | 提交审批 |
| POST /{id}/approve 或 /reject | 审批通过/拒绝；拒绝必须带 reason |
| POST /{id}/issue | 仓库发放 |
| POST /{id}/cancel | 申请人取消 |
| GET /options/warehouses、/options/products、/options/devices | 权限和范围约束下的分页选项 |

动作请求复用 `InventoryOrderActionRequest`（version、reason）；明细复用 `InventoryOrderItemRequest`（productId、quantity、deviceIds）。旧版本操作返回冲突，需刷新后重试。

页面 `/apply/requisitions` 提供列表、草稿编辑、设备选择、详情、审批/发放/取消操作，并按权限、申请人和状态显示按钮；菜单和路由复用既有鉴权。库存流水页新增领用类型筛选。当前未配置浏览器 E2E，前端构建不等同于浏览器交互验收。
