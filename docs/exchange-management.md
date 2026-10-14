# Phase 8 正式换货

`asset_exchange_orders` 保存旧 Device、新 Device、二者独立身份快照、产品、申请/审批/执行人员、换货原因、时间、原使用归属、状态、版本和防重标识；`asset_exchange_devices` 保存 `OLD`/`NEW` 关系并承担设备占用外键。严禁原地覆盖旧 Device 的编号、SN、MAC 或资产编号。

只有维修单确认无法修复后处于 `PENDING_EXCHANGE` 的旧设备可发起换货。新设备必须是同一 DEVICE 产品、不同 Device、状态 `IN_STOCK`、归属有效仓库、库存可用且未被库存单据/维修/换货占用。选项接口在服务端应用这些条件。状态为 `DRAFT → SUBMITTED → APPROVED → COMPLETED`，提交可批准或拒绝，草稿和已提交可取消；拒绝/取消释放两台设备占用。执行前锁定单据、仓库、产品、库存及按 ID 排序的新旧设备，复核版本、身份、归属、状态、占用与库存一致性。

执行事务将新设备从库存扣减，写 `EXCHANGE` OUT 流水，清空其 warehouse_id。有原使用人和部门的新设备转 `IN_USE` 并继承原使用归属；原为库存设备的新设备按现有出库语义进入 `SHIPPED`，不伪造使用人。旧设备进入 `SHIPPED`，保持原设备身份和完整历史。新旧设备分别写 DeviceHistory，操作写 OperationLog/request_id；事务失败全部回滚。列表、详情、过滤选项和动作接口为 `/api/assets/exchanges`，前端为 `/assets/exchanges`。

换货限同产品单台对单台；不支持不同产品替代、自动报废、跨仓调拨或二维码。已换出的旧设备仍保留历史与正式换货关系。
