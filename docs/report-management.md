# Phase 10B 业务报表

当前报表读取 V1～V17 已存在的业务表，不新增统计表、缓存、定时任务或 Migration。入口为前端 `/reports`；接口为 `GET /api/reports/{type}`，`type` 可取 `STOCK`、`INBOUND`、`OUTBOUND`、`REQUISITION`、`DEVICE`、`STOCKTAKE`、`SERVICE`。`SERVICE` 可按 `REPAIR`、`WARRANTY`、`EXCHANGE` 缩小范围。返回 `ApiResponse<ReportPageView>`，包含 `PageResult<ReportRowView>` 与对同一筛选结果计算的 `ReportSummaryView`。

## 口径

| 报表 | 明细粒度与字段 | 汇总口径 |
| --- | --- | --- |
| 库存 | `inv_stocks` 一行对应仓库＋Product；数量、锁定量、可用量、安全库存、低库存标志 | 行数、低库存行数。低库存为当前行数量 `< md_products.safety_stock` 且阈值大于零；未建库存行的组合不显示 |
| 入库 / 出库 | 一张单据的一个 `inv_*_items` 产品明细；单号、状态、仓库、创建人、产品、申请数量、设备快照明细数、审批与执行时间 | 去重单据数、申请数量、已执行数量；只有 `COMPLETED` 行计入已执行数量。DEVICE 的已执行数来自设备快照明细计数 |
| 领用 | `apply_requisition_items` 产品明细；申请人取单据创建人，部门取单据部门，发放时间取 `executed_at` | 同上，未发放单据的已执行数量为 0 |
| 设备资产 | `asset_devices` 一台设备一行，展示当前状态、所属部门、使用人、仓库及按当前保修日期计算的状态 | 设备总数含已离库、维修中、待换货及报废设备；按 `IN_STOCK` 筛选可看在库设备，不把总资产数当库存 |
| 盘点差异 | `inv_stocktake_items` 产品明细，展示快照、实盘、差异方向、待确认差异及结构化设备异常代码和数量 | `finalDifferenceQuantity` 仅对 `CONFIRMED` 盘点行有值；未确认盘点不进入最终差异合计 |
| 维修 / 保修 / 换货 | 三张服务单各一行；保留旧设备编号、换货新设备编号、原因、结果、备注、维修注意事项及保修决定 | 三种类型的单据数按业务类型＋主键去重，不合并旧新 Device 身份 |

不同 Product 的数量保留原单位，汇总数量只作概览，不应把不同单位解释成统一实物量。设备异常直接读取 `inv_stocktake_device_checks.difference_code`，不重新推断差异。报表不读 DeviceHistory 或 StockLedger 作为重复的主记录来源，因此一台设备多条历史、一个单据多明细不会放大主单据数。

## 筛选、排序和权限

共用 `ReportRequest` 作为页面与导出输入：`keyword`、`status`、`productType`、`serviceKind`、`warrantyStatus`、仓库/Product/分类/品牌/部门/用户 ID、`lowStock`、`from`、`to`、`page`、`size`。关键字按真实编号、Product、仓库、部门、用户、设备字段字面子串匹配，`%`、`_` 不作通配符。日期按主记录 `created_at`，包含结束日全天。关键词最多 100 字符，状态最多 32 字符；分页为第 1～1000 页、每页 1～100 行，默认 1/20。稳定排序：创建时间倒序、报表类型、服务类型、明细 ID 倒序。所有用户值均通过 MyBatis `#{}` 绑定；SQL 模板中的表名仅来自服务端固定报表类型。

后端要求登录，逐类型复用现有 `inventory.stock.view`、`inventory.inbound.view`、`inventory.outbound.view`、`apply.requisition.view`、`system.device.view`、`inventory.stocktake.view`、`asset.repair.view`、`asset.warranty.view`、`asset.exchange.view`。查看权限同时允许导出当前可见内容，本阶段未增单独导出权限或 V18。`SERVICE` 不指定类型时需三类查看权限；只有部分权限的账号需选具体类型。ALL 看全部；DEPARTMENT 使用现有部门树范围；SELF 的设备仅本人使用/保管、领用仅本人创建、服务单仅本人申请，整仓库存及入出库/盘点不可见。汇总、分页和 Excel 使用同一个范围过滤。

复杂七类 SQL 使用 MyBatis 注解 `@SelectProvider` 与内部固定 `ReportSqlProvider` 模板，这是相对 `docs/coding-conventions.md` 第 6 节“SQL 直接写在 `@Select`”的局部偏离。原因是明细、汇总和导出必须共用相同的七类固定投影、筛选及范围表达式，重复维护七套查询容易产生口径漂移。Provider 仅按服务端校验过的类型选择固定表名和列名，用户值仍全部通过 `#{}` 参数绑定；Controller/Service/Mapper、返回结构、异常和依赖边界未改变。真实 MySQL 的七类筛选、权限、特殊字符与 Excel 对照测试覆盖这一兼容点。

页面按当前权限显示报表类型，提供显式查询、重置、分页、汇总卡、明细和“导出全部筛选结果”。业务结果跳转已有列表；Device 可带 ID 参数进入原列表定位。其他业务列表没有统一详情路由时，跳转至原列表继续查找单号。浏览器只显示后端已过滤的数据，最终权限以服务端为准。报表无写业务数据、StockLedger、DeviceHistory 或 OperationLog；请求链路仍由现有 RequestIdFilter 提供 request_id。
