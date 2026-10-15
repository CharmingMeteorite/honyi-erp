export const queryTypeNames = {
  PRODUCT: '物资', DEVICE: '设备', WAREHOUSE: '仓库', INBOUND: '入库', OUTBOUND: '出库',
  REQUISITION: '领用', STOCKTAKE: '盘点', REPAIR: '维修', WARRANTY: '保修', EXCHANGE: '换货',
}

const statusNames = {
  ENABLED: '启用', DISABLED: '停用', IN_STOCK: '在库', IN_USE: '使用中', BORROWED: '借用中',
  REPAIRING: '维修中', PENDING_EXCHANGE: '待换货', SHIPPED: '已出库', SCRAPPED: '已报废', MISSING: '盘亏',
  DRAFT: '草稿', PENDING_APPROVAL: '待审批', APPROVED: '已批准', REJECTED: '已拒绝',
  COMPLETED: '已完成', CANCELLED: '已取消', COUNTING: '盘点中', COUNTED: '待确认', CONFIRMED: '已确认',
  SUBMITTED: '已提交', ACCEPTED: '已受理', UNREPAIRABLE: '无法修复', CLOSED: '已关闭',
}

export function queryStatusName(type, status) {
  return type === 'REQUISITION' && status === 'COMPLETED' ? '已发放' : statusNames[status] || status
}
