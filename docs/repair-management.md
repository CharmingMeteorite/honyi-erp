# Phase 8 设备维修

`asset_repair_orders` 与具体 Device 关联，保存业务编号、产品/身份快照、故障现象、描述、申请人、处理人、预计与实际时间、结果、备注、注意事项、服务商/地点、送修前状态和归属、单据版本及客户端请求标识。设备身份不通过维修单改写。仅 DEVICE Product 可关联。普通设备资料编辑不能绕过有效维修占用。

状态：`DRAFT → SUBMITTED → ACCEPTED → REPAIRING → COMPLETED/UNREPAIRABLE → CLOSED`；草稿、已提交、已受理可取消。编辑只修改草稿描述资料，状态仅通过动作接口改变。送修时在事务内锁定仓库、产品、库存、设备和单据，检查快照与占用，将设备置为 `REPAIRING`；库存设备同时清空 warehouse_id、扣减库存并写 `REPAIR_OUT`。完成时按送修前快照恢复 `IN_STOCK` 或 `IN_USE`：库存设备返库写 `REPAIR_RETURN`，使用中设备保留原使用人/部门。设备版本或归属发生变化时拒绝恢复。无法修复则进入 `PENDING_EXCHANGE`，保留设备和历史，等待正式换货。

每次状态变化写 DeviceHistory、OperationLog/request_id。库存变化还写正数量、方向和前后余额的 StockLedger；审计失败会回滚整个动作。相同客户端请求重试返回同一处理结果；新请求在旧版本或错误状态下被拒绝。维修设备占用由既有 `inv_order_device_claims` 承载，与入出库、领用、换货互斥。列表/详情、服务端过滤设备选项、分页和历史经 `/api/assets/repairs` 提供，前端为 `/assets/repairs`。

现有状态不提供维修后跨仓调拨。已经失效的原使用人或部门、变化的库存/设备版本须人工核查，系统不会静默重派。
