# Phase 10A 综合查询

前端 `/query` 调用 `GET /api/query`，是十类已实现资源的统一只读入口，不替代业务列表。返回 `PageResult<QueryResultView>`，每行含 `type`、`id`、`title`、`subtitle`、`status`、`createdAt`、`route`。支持 `type`、`keyword`、`status`、`from`、`to`、`page`、`size`。不传 `type` 时只搜索当前用户有权查看的类型；明确指定无权查看的类型返回 403。

| 类型 | 关键词范围 |
| --- | --- |
| PRODUCT | 编码、名称、分类、品牌 |
| DEVICE | 设备编号、SN、MAC、资产编号、Product 编码和名称 |
| WAREHOUSE | 仓库编码、名称 |
| INBOUND、OUTBOUND、REQUISITION | 单号、仓库名称、明细 Product 编码和名称、关联 Device 快照编号/SN/MAC/资产编号 |
| STOCKTAKE | 盘点单号、仓库名称、明细 Product 编码和名称、设备核对记录编号/SN/MAC/资产编号 |
| REPAIR、WARRANTY、EXCHANGE | 单号、设备编号、Product 编码和名称；换货另匹配新设备编号 |

关键词不超过 100 字符，用 `LOCATE(#{keyword}, column)` 字面匹配，`%`、`_` 不是通配符；所有值经 MyBatis 参数绑定。状态长度最多 32、只接受大写字母与下划线，Product/Warehouse 使用 `ENABLED`、`DISABLED`，其他类型使用原业务状态。日期采用 `YYYY-MM-DD`，按各对象创建时间筛选，包括开始日期及结束日期全天，允许年份为 1000～9998；`page` 为 1～1000，`size` 为 1～50，默认 1/20。排序为创建时间倒序、类型、主键倒序；一次 SQL `UNION ALL` 后由数据库统一分页。业务明细及设备快照仅用 `EXISTS` 判断关键词是否命中，不参与结果行数，历史终态设备仍按原主键返回其当前状态。

接口通过 `@RequirePermission` 强制登录，并逐类型叠加现有 view 权限。Device、仓库单据、领用和维修/保修/换货分别使用与原业务一致的部门/SELF 条件，查询输入不能覆盖服务端范围。搜索不写 `sys_operation_log`，也不写任何业务表。结果的 `route` 指向现有页面；只有 Product、Device 页面支持 ID 参数定位，其余类型打开原有列表，用户可继续按单号筛选。目标 API 和路由继续独立鉴权。

此查询采用现有表与索引，没有新增 V18。关键词跨多列的字面子串匹配在大数据量下可能较慢；本阶段没有足够数据证明需要特定新索引，后续应先在目标数据量上用 `EXPLAIN` 检查实际计划。没有引入搜索服务或缓存。

列表和总数在同一个只读事务中查询，沿用当前 MySQL 快照隔离；规范例外的原因和影响见 [dashboard-management.md](dashboard-management.md)。前端通过搜索按钮或回车发起查询，输入字符本身不调用 API；页面支持状态、日期、分页、加载、无结果和错误反馈。
