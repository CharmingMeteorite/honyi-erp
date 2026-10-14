# Device 管理（Phase 3，含 Phase 4 兼容规则）

本文件保留早期设备建档与生命周期的历史设计记录。Phase 9 已新增二维码生成、扫码和标签打印；当前实现见 [qr-management.md](qr-management.md)，以下“二维码未实现”等表述仅指 Phase 3 当时状态。

Phase 3 档案管理规则继续有效。Phase 4 已建立仓库/库存/期初单：仓库归属只能由期初完成或后续正式库存业务控制，已归属仓库的设备不能使用普通状态动作；详见 [inventory-management.md](inventory-management.md)。

## 边界与实际模型

`md_products` 描述“是什么产品/物资”，`asset_devices` 描述“具体是哪一台设备”。例如同一个交换机 Product 可以对应多个 DEV 编号。Device 模块只管理设备档案、归属资料和基础状态，不自行产生库存数量；Phase 4 的独立期初库存业务负责数量与仓库归属。

在 MySQL 8.0.46 的 `erp` 库核对了 Phase 2 实际结构：Product 已有 `product_type`、`track_sn`、`track_mac`、`is_fixed_asset`、`enable_qrcode`、启用状态，以及分类/品牌/单位外键；没有实际 SN、MAC、设备编号和库存数量。`safety_stock/min_stock/max_stock` 是配置字段。新增设备沿用实际表的 BIGINT 主键、InnoDB、utf8mb4_unicode_ci 和数据库维护时间的方式。

## 设备字段

| 字段 | 规则 |
| --- | --- |
| `id` | BIGINT 自增主键，后续业务关联的稳定标识 |
| `device_code` | VARCHAR(64)，必填、唯一；创建后不可改 |
| `product_id` | 必填，真实外键关联 `md_products.id`；创建后不可改 |
| `serial_number` | VARCHAR(128)，可空、非空唯一；按 Product 决定必填 |
| `mac_address` | VARCHAR(17)，可空、非空唯一；保存规范化 MAC |
| `asset_no` | VARCHAR(64)，可空、非空唯一；仅固定资产标识，无财务折旧 |
| `status` | 默认 `IN_STOCK`，只由状态动作修改 |
| `department_id` | 可空，外键关联 `sys_department`，表示归属组织 |
| `user_id` | 可空，外键关联 `sys_user`，表示实际使用人 |
| `custodian_id` | 可空，外键关联 `sys_user`，表示保管责任人 |
| `warehouse_id` | 可空，Phase 4 起真实外键关联 wh_warehouses；普通创建/编辑不能设置或清空，期初完成设置 |
| `location_id` | 继续为 nullable 预留，普通输入必须 NULL，当前没有库位体系 |
| `purchase_at/inbound_at/first_use_at` | 可空 DATETIME，购入、入库资料登记、首次使用时间 |
| `warranty_start_at/warranty_end_at` | 可空 DATETIME，结束不得早于开始，不触发维修或提醒 |
| `remark/notes` | 可空，分别最多 500/1000 字符；注意事项为长期维护资料 |
| `created_at/updated_at` | MySQL DEFAULT CURRENT_TIMESTAMP / ON UPDATE CURRENT_TIMESTAMP |

设备唯一键覆盖编号、SN、MAC、资产编号；普通索引覆盖 Product、状态、部门、使用人、保管人及预留仓库/库位。唯一键已是 SN/MAC/资产编号索引，不再重复建索引。历史表以 `(device_id, created_at)` 索引支持设备时间轴。关键词使用 LIKE 搜索，普通 B-tree 不保证加速任意中间匹配。

## 编号、SN 和 MAC

未传设备编号时，同一事务内先插入 `TMP-UUID`，取得 MySQL 自增 ID 后写入 `DEV%06d`。例如 ID=1 对应 DEV000001，超过六位继续增长；事务回滚可能留编号空洞，不承诺连续。并发分配依赖自增主键及唯一约束，没有额外编号表。

也可在创建时传自定义编号（英文、数字、点、短横线、下划线，1～64 字符）；`DEV` 和 `TMP-` 前缀由系统保留，避免占用自动编号。普通编辑不接收编号。

所有身份字符串先去首尾空格，空字符串保存为 NULL。MySQL 可空唯一索引允许多行 NULL，但相同非空值只能有一行。沿用 `utf8mb4_unicode_ci`，SN/资产编号/设备编号比较不区分大小写，数据库还遵循该排序规则的字符比较语义。

MAC 接受四种完整格式：`AA:BB:CC:DD:EE:FF`、`AA-BB-CC-DD-EE-FF`、`aabb.ccdd.eeff`、`aabbccddeeff`，一律保存成大写冒号格式 `AA:BB:CC:DD:EE:FF`。拒绝不满 12 位十六进制、混乱或多余分隔符。Service 在唯一性检查和写入之前规范化；完整 MAC 关键词也采用同一规范化。不要绕过 Service 直接写入未规范化值。

`track_sn=true` 要求 SN，`track_mac=true` 要求 MAC；关闭时允许为空，填写后仍唯一。`is_fixed_asset=true` 不自动强制填写资产编号。本阶段由 Service 实施这些规则，前端必填提示只是辅助。

## Product 与人员约束

创建设备时 Product 必须存在、启用且为 DEVICE；QUANTITY 拒绝创建。设备创建与 Product 修改/停用共用 Product 行锁，避免校验和保存之间核心属性被并发改写。Product 已有设备后，不得变更 `product_type/track_sn/track_mac/is_fixed_asset`；原有名称、型号、规格等普通维护仍可使用。停用 Product 会阻止新建 Device，已存在设备仍可维护。

部门、使用人、保管人可全部为空。设置任意人员时必须设置部门；部门必须存在并启用，使用人与保管人都必须存在、启用，且属于该目标部门。两个人员角色独立，可以是同一人，也可以是同部门不同的人。当前选择同部门约束，跨部门保管需后续明确业务授权后再扩展。

本阶段归属编辑是档案更正，没有“领用完成”或“调拨完成”的含义；归属变化记录历史。后续单据上线后，应把正式归属变化收敛到对应业务动作。已有人员/部门停用后，保存设备资料仍须满足引用启用校验；进入使用中时也重新校验归属。

## 状态机与编辑保护

| 当前状态 | Phase 3 允许的目标 |
| --- | --- |
| `IN_STOCK`（公司持有） | `IN_USE`、`SCRAPPED` |
| `IN_USE`（使用中） | `IN_STOCK`、`SCRAPPED` |
| `SCRAPPED`（已报废） | 无，终态 |
| `BORROWED/REPAIRING/PENDING_EXCHANGE/SHIPPED` | 预留，当前无手工进入/退出动作 |

Phase 4 补充：上述基础转换仅对 warehouse_id 为 NULL 的设备开放，已归属仓库的设备返回空 allowedStatuses，状态变化须等待后续库存单据；普通编辑 Mapper 不写 warehouse_id/location_id，避免清空账内归属。

新增固定为 IN_STOCK。“公司持有”不表示仓库账面可用数，`Device.status` 与未来 `Stock.quantity` 是两个概念。IN_USE 前必须已经设置有效使用人与部门。状态变更必须填写不超过 200 字符的原因；原状态到同一状态也拒绝。状态更新锁定设备行，并校验原状态，不能通过普通 PUT 任意赋值。

`validateIdentityEdit` 是身份保护入口：仅在 IN_STOCK 且从未有 STATUS 历史时允许更正 SN、MAC、资产编号。首次状态变更后永久锁定，返回 IN_STOCK 也不会解锁。Product 和设备编号始终不可通过编辑修改。

IN_USE 设备不得清空使用人/部门；SCRAPPED 不能修改归属。备注、注意事项、保修和日期资料保留维护入口，但仍进行引用、日期及身份校验。无物理删除接口，也没有额外 enabled 状态位；当前以生命周期状态满足状态管理需求。

## 历史与操作日志

`asset_device_history` 独立于统一操作日志，用于设备时间轴。保存事件类型、设备 ID、状态/部门/使用人/保管人前后 ID、说明、操作人、request_id 和数据库创建时间；关联设备、人员、部门的列使用真实外键。

| 变化 | 设备历史 | 统一操作日志 |
| --- | --- | --- |
| 建档 | CREATE 初始状态/归属；填写的日期另记资料事件 | DEVICE_CREATE |
| 归属更正 | UPDATE，记录三类归属前后值 | DEVICE_UPDATE |
| 身份或购入/入库登记/首次使用/保修日期更正 | 每个变化字段一条 UPDATE，记录前后值 | DEVICE_UPDATE |
| 状态变更 | STATUS，原状态、目标状态及原因 | DEVICE_STATUS |
| 仅备注/注意事项修改 | 不增加时间轴事件 | DEVICE_UPDATE |

设备主表、历史和操作日志处于同一事务。request_id 沿用 Phase 1 `RequestIdFilter` 的请求属性，通过 `OperationLogService.currentRequestId()` 读取；HTTP 请求带入的合法 ID 或过滤器生成的 ID 同时进入两表，不另建审计链。不记录 Token、密码或完整请求对象。非 HTTP 的直接 Service 调用没有请求上下文，request_id 可为 NULL。

历史记录时间表示登记发生时间；`inbound_at` 等用户填写的实际时间保存在设备字段及历史说明中，不伪装成已执行单据。时间轴按记录 ID 倒序返回。人员/部门历史保存稳定 ID，展示名称通过实时 JOIN 获得，重命名会显示新名称；当前不保存姓名快照。历史当前一次返回全部，后续业务量增长时再分页。

## API 与权限

统一前缀 `/api/assets/devices`，返回 ApiResponse；列表返回 PageResult，默认 1 页/20 条，最多 100 条。

| 方法/路径（相对前缀） | 内容 | 权限 |
| --- | --- | --- |
| GET 空路径 | keyword/status/productId/departmentId/userId/page/size 分页查询 | system.device.view |
| GET `/{id}` | 完整详情、历史、identityEditable、allowedStatuses | system.device.view |
| GET `/{id}/history` | 设备历史 | system.device.view |
| POST 空路径 | 创建设备 | system.device.create |
| PUT `/{id}` | 编辑安全基础资料，无 Product/code/status 字段 | system.device.update |
| PATCH `/{id}/status` | `{status, reason}` 状态动作 | system.device.status |
| GET `/options` | DEVICE 产品选项，keyword，activeOnly 默认 true，最多 100 条 | system.device.view |
| GET `/options/departments` | 当前数据范围内的部门简要选项，activeOnly 默认 true | system.device.view |
| GET `/options/users` | 当前数据范围内的人员分页选项，keyword/departmentId/activeOnly/page/size | system.device.view |

关键词匹配设备编号、产品名称、型号、SN、MAC、资产编号、部门名、使用人和保管人姓名。userId 筛选匹配使用人或保管人。选项复用现有 ProductMapper、DepartmentService、UserService，无需额外开放用户管理权限；人员选项只含 id/realName/username/departmentId/status。

| 默认角色 | 设备权限 | 数据范围 |
| --- | --- | --- |
| SYSTEM_ADMIN | 查看/创建/编辑/状态 | ALL |
| WAREHOUSE_ADMIN | 查看/创建/编辑/状态 | DEPARTMENT |
| DEPARTMENT_MANAGER | 查看 | DEPARTMENT |
| MANAGEMENT | 查看 | ALL |
| EMPLOYEE | 查看 | SELF |

SELF 只看自己作为使用人或保管人的设备，且禁止写入；DEPARTMENT 为本部门和子部门，没有部门时拒绝扩大范围；ALL 可看全部。筛选与范围取交集。详情、历史、修改、状态动作均校验范围，部门范围写入还校验修改前后所属部门。部门范围管理员不能创建无部门设备；ALL 可以建档未分配设备。

## 页面

`/assets/devices` 使用 `views/asset/DeviceView.vue`（asset 目录专门承载设备/资产页面），沿用 JavaScript、`<script setup>`、Element Plus 和 `lib/api.js` 原生 fetch。包含列表、筛选/重置/分页、新增对话框、编辑对话框、详情对话框和状态操作对话框，与现有页面组织一致。

Product 远程选择只提供启用 DEVICE 产品，动态提示 SN/MAC 必填。部门选定后搜索使用人/保管人；身份锁定后禁用相关输入。筛选允许已停用 Product/人员，仍受数据范围限制。详情展示产品、品牌、型号、规格、身份、三类归属、五类日期、备注、注意事项和历史时间轴；不展示虚构库存或未实施单据。

## Migration 与后续关系

- V5 建立两张设备表、外键/唯一键/索引及设备权限节点。
- V6 补齐仓库管理员写权限和普通员工查看权限，INSERT IGNORE 避免已有授权重复。
- V7 仅增加仓库/库位可空预留列和索引；DTO `@Null` 与 Service 双重拒绝非空输入。没有 Warehouse 表，不能把任意数字当成真实归属。
- V1～V4 未改动；已成功执行的版本不再修改。

Phase 4 Stock 按 Product、仓库维护数量；设备型单据通过 Device 明细绑定具体实例，并一致地更新库存、状态和归属历史。Repair/Loan 以 Device 为对象，Exchange 同时关联旧/新 Device；归还、维修完成、调换等动作由后续单据控制预留状态，不通过当前普通编辑绕过流程。届时应在历史中增加来源单据类型/ID、业务发生时间和仓库归属快照。

二维码当前不建表、不生成、不扫描、不打印；Device.id/code 提供未来关联基础。后续 qrcodes 用不透明标识关联 Device，二维码内容不包含 SN/MAC。Product.enable_qrcode 继续只是配置。Phase 3 未建立这些表；Phase 4 已建立仓库、库存、流水和期初单据，仍无库位、维修、借用、调换、领用或二维码功能。
