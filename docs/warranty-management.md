# Phase 8 独立保修申请

`asset_warranty_orders` 独立于维修单，持久化具体 Device、故障、申请原因、申请/处理/审批人员、保修起止时间快照、`warranty_status`、处理决策和结果、版本、客户端请求标识和审计。保修申请本身不改 Device 状态或库存。

保修状态以**申请时的日期快照**识别：保修起止完整且当前处于区间为 `VALID`；超过结束为 `EXPIRED`，未到开始为 `NOT_STARTED`，日期缺失为 `UNKNOWN`。处理时再次以该快照和当前时间判断保内批准资格。后三种状态不能标记为保内批准；处理人可以明确拒绝，或明确转为 `PAID_REPAIR`。保内批准和自费维修都会创建一张独立、与保修申请一对一关联的维修草稿，随后须由维修流程处理设备/库存。保修单只能在关联维修到达完成或无法修复终态后完成。

状态：`DRAFT → SUBMITTED → ACCEPTED → APPROVED/REJECTED`，批准后可 `COMPLETED`；草稿、已提交、已受理可取消。服务端重查角色、ALL/DEPARTMENT/SELF 范围及活动维修/换货冲突。列表、详情、过滤后的设备选项、审批/拒绝/自费维修由 `/api/assets/warranties` 提供，前端为 `/assets/warranties`。`version` 与客户端请求标识防重复处理。
