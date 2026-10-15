# Phase 10A 首页 Dashboard

首页 `/` 读取 `GET /api/dashboard/summary`，返回 `metrics`、`deviceStatuses`、`stockQuantity` 与最多 8 条 `recent`。所有值从当前业务表实时读取；接口只读，不写操作日志、库存流水或设备历史，不执行统计任务。页面有加载、空数据和错误状态。

## 统计口径

| 字段 | 口径与权限 |
| --- | --- |
| `products`、`activeProducts` | `md_products` 主记录总数、`status=1` 数；需 `system.product.view` |
| `devices`、`deviceStatuses` | `asset_devices` 主记录总数和现有八类状态分布；需 `system.device.view`，叠加设备部门/本人范围 |
| `activeWarehouses` | `wh_warehouses.status=1`，仅计可见仓库；需 `system.warehouse.view` |
| `stockPositions`、`stockQuantity` | 可见仓库的 `inv_stocks.quantity>0` 行数，以及可见 Stock 全部行的数量合计；需 `inventory.stock.view`，SELF 不显示整仓库存 |
| `stockProducts`、`stockWarehouses` | 可见正库存行中的 Product、Warehouse 去重数量；需 `inventory.stock.view` |
| `lowStockProducts` | 启用且安全库存大于零的 Product，在当前可见的启用仓库中合计库存低于 `safety_stock`；需同时有 `inventory.stock.view` 与 `system.product.view` |
| `pendingInbounds`、`pendingOutbounds`、`pendingRequisitions` | 分别计状态 `PENDING_APPROVAL` 的主单据；需对应单据查看权限 |
| `pendingStocktakes` | 状态 `COUNTED`、待差异确认的盘点主单据；需盘点查看权限 |
| `activeRepairs`、`activeWarranties`、`pendingExchanges` | 维修 `SUBMITTED/ACCEPTED/REPAIRING`、保修 `SUBMITTED/ACCEPTED/APPROVED`、换货 `SUBMITTED/APPROVED` 的主单据；需对应查看权限 |

没有对应权限的字段不出现在 `metrics` 中。库存行数不是设备数；设备状态分布也不等于在库设备数。库存总量是各 Product 原单位的数量合计，只作概览，不作为可直接比较的统一计量值。安全库存基于 Product 阈值，当前可见范围内零库存的 Product 也属于低库存。单据待办只计单据，不提前计算库存变更。

`recent` 从有权限的入库、出库、领用、盘点、维修、保修、换货主单据按创建时间倒序取 8 条；不读完整操作日志。前端入口跳转现有业务列表，Product/Device 可按现有页面支持的 ID 参数定位。该列表跳转不代替目标页面再次进行权限检查。

数据范围与原业务一致：Product 为已有全局查看权限；Device 的 DEPARTMENT 为设备所属部门树，SELF 为本人使用或保管；仓库及相关库存单据按仓库所属部门树，SELF 无整仓可见行；领用按申请部门/本人创建；维修、保修、换货按单据部门/本人申请。服务端每一类独立检查已有 view 权限，登录本身不授予业务统计数据。

低库存只在用户有可见启用仓库时统计，库存恰好等于安全库存不报警；停用 Product 和零阈值不报警。不同部门用户看到的库存合计和低库存计数可以不同，Product 总数仍遵循原有全局产品查看边界。

## 只读一致性与规范例外

`DashboardQueryService.summary()` 与 `query()` 使用方法级 `@Transactional(readOnly=true)`。这是对 `docs/coding-conventions.md` 第 5 节“纯查询不加事务”的局部偏离：Dashboard 需要多次独立聚合，综合查询需要列表和总数，两者应在同一读取事务中完成，减少并发业务写入造成的口径不一致。指定 MySQL 的 REPEATABLE-READ 提供事务快照；没有行锁、业务写入、缓存、额外依赖或返回结构变化。测试比较查询前后库存、设备状态、历史、流水及审计，确认只读结果。
