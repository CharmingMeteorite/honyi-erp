// A shared visual vocabulary for business statuses. Text still comes from each module.
const SUCCESS = new Set(['ENABLED', 'APPROVED', 'COMPLETED', 'CONFIRMED', 'CLOSED', 'IN_STOCK', 'VALID'])
const WARNING = new Set(['PENDING_APPROVAL', 'SUBMITTED', 'COUNTED', 'REPAIRING', 'PENDING_EXCHANGE', 'LOW_STOCK'])
const DANGER = new Set(['REJECTED', 'UNREPAIRABLE', 'MISSING', 'EXPIRED'])
const NEUTRAL = new Set(['DRAFT', 'DISABLED', 'CANCELLED', 'SCRAPPED', 'SHIPPED', 'DELETE_PENDING'])
export function statusTagType(status) {
  if (SUCCESS.has(status) || status === true) return 'success'
  if (WARNING.has(status)) return 'warning'
  if (DANGER.has(status)) return 'danger'
  if (NEUTRAL.has(status) || status === false) return 'info'
  return 'primary'
}
