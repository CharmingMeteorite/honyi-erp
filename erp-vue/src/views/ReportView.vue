<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Download, Search, RefreshRight } from '@element-plus/icons-vue'
import { api, downloadReport } from '../lib/api.js'
import { hasPermission } from '../lib/auth.js'
import { queryStatusName } from '../lib/query.js'

const router = useRouter()
const definitions = [
  ['STOCK', '库存', 'inventory.stock.view', '/inventory/stocks'],
  ['INBOUND', '入库', 'inventory.inbound.view', '/inventory/inbounds'],
  ['OUTBOUND', '出库', 'inventory.outbound.view', '/inventory/outbounds'],
  ['REQUISITION', '领用', 'apply.requisition.view', '/apply/requisitions'],
  ['DEVICE', '设备资产', 'system.device.view', '/assets/devices'],
  ['STOCKTAKE', '盘点差异', 'inventory.stocktake.view', '/inventory/stocktakes'],
  ['SERVICE', '维修 / 保修 / 换货', null, '/assets/repairs'],
]
const serviceOptions = [
  ['REPAIR', '维修', 'asset.repair.view', '/assets/repairs'],
  ['WARRANTY', '保修', 'asset.warranty.view', '/assets/warranties'],
  ['EXCHANGE', '换货', 'asset.exchange.view', '/assets/exchanges'],
]
const availableServices = computed(() => serviceOptions.filter(([, , permission]) => hasPermission(permission)))
const availableReports = computed(() => definitions.filter(([, , permission]) => permission ? hasPermission(permission) : availableServices.value.length))
const selected = ref(availableReports.value[0]?.[0] || '')
const filter = reactive({ keyword: '', status: '', productType: '', serviceKind: '', warrantyStatus: '', warehouseId: '', productId: '', categoryId: '', brandId: '', departmentId: '', userId: '', lowStock: '', dates: [], page: 1, size: 20 })
const result = ref(null)
const loading = ref(false)
const exporting = ref(false)
const error = ref('')
const displayRows = computed(() => result.value?.page?.list || [])
const total = computed(() => result.value?.page?.total || 0)
const summary = computed(() => result.value?.summary || {})
const stateOptions = computed(() => {
  if (selected.value === 'STOCK') return ['ENABLED', 'DISABLED']
  if (selected.value === 'DEVICE') return ['IN_STOCK', 'IN_USE', 'BORROWED', 'REPAIRING', 'PENDING_EXCHANGE', 'SHIPPED', 'SCRAPPED', 'MISSING']
  if (selected.value === 'STOCKTAKE') return ['DRAFT', 'COUNTING', 'COUNTED', 'CONFIRMED', 'CANCELLED']
  if (selected.value === 'SERVICE') return ['DRAFT', 'SUBMITTED', 'ACCEPTED', 'REPAIRING', 'APPROVED', 'REJECTED', 'COMPLETED', 'UNREPAIRABLE', 'CANCELLED', 'CLOSED']
  return ['DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'COMPLETED', 'CANCELLED']
})
const columns = computed(() => {
  if (selected.value === 'STOCK') return [['productCode', '产品编码'], ['productName', '产品名称'], ['warehouseName', '仓库'], ['productType', '类型'], ['quantity', '库存'], ['lockedQuantity', '锁定'], ['availableQuantity', '可用'], ['safetyStock', '安全库存'], ['lowStock', '低库存']]
  if (selected.value === 'DEVICE') return [['number', '设备编号'], ['productName', '产品'], ['status', '状态'], ['departmentName', '部门'], ['userName', '使用人'], ['warehouseName', '仓库'], ['warrantyStatus', '保修状态']]
  if (selected.value === 'STOCKTAKE') return [['number', '盘点单号'], ['warehouseName', '仓库'], ['productName', '产品'], ['status', '状态'], ['snapshotQuantity', '账面快照'], ['countedQuantity', '实盘'], ['differenceQuantity', '待确认差异'], ['differenceDirection', '差异方向'], ['finalDifferenceQuantity', '最终差异'], ['differenceCode', '设备异常代码'], ['completedAt', '确认时间']]
  if (selected.value === 'SERVICE') return [['serviceKind', '业务类型'], ['number', '业务编号'], ['deviceCode', '旧设备'], ['newDeviceCode', '新设备'], ['productName', '产品'], ['status', '状态'], ['userName', '申请人'], ['submittedAt', '申请时间'], ['reason', '原因'], ['result', '结果'], ['remark', '备注'], ['notes', '维修注意事项'], ['completedAt', '完成时间']]
  return [['number', '单号'], ['productCode', '产品编码'], ['productName', '产品'], ['warehouseName', '仓库'], ['userName', '创建人'], ['status', '状态'], ['quantity', '申请数量'], ['executedQuantity', '执行数量'], ['deviceCount', '设备明细数'], ['approvedAt', '审批时间'], ['executedAt', '执行时间']]
})
function params() {
  return { ...filter, serviceKind: selected.value === 'SERVICE' ? (filter.serviceKind || (availableServices.value.length < 3 ? availableServices.value[0]?.[0] : '')) : '', lowStock: selected.value === 'STOCK' ? filter.lowStock : '', from: filter.dates?.[0], to: filter.dates?.[1], dates: undefined }
}
function selectReport(type) {
  selected.value = type
  Object.assign(filter, { keyword: '', status: '', productType: '', serviceKind: '', warrantyStatus: '', warehouseId: '', productId: '', categoryId: '', brandId: '', departmentId: '', userId: '', lowStock: '', dates: [], page: 1, size: 20 })
  result.value = null
  error.value = ''
}
async function search() {
  if (!selected.value) return
  loading.value = true
  error.value = ''
  try { result.value = await api.report(selected.value, params()) }
  catch (e) { result.value = null; error.value = e.message }
  finally { loading.value = false }
}
function reset() { selectReport(selected.value); search() }
async function exportAll() {
  exporting.value = true
  try { await downloadReport(selected.value, params()); ElMessage.success('报表已开始下载') }
  catch (e) { ElMessage.error(e.message) }
  finally { exporting.value = false }
}
function open(row) {
  const path = selected.value === 'SERVICE' ? serviceOptions.find(([kind]) => kind === row.serviceKind)?.[3] : definitions.find(([type]) => type === selected.value)?.[3]
  if (path) router.push({ path, query: selected.value === 'DEVICE' ? { id: row.id } : {} })
}
onMounted(() => { if (!selected.value) selected.value = availableReports.value[0]?.[0] || ''; search() })
function value(row, field) {
  if (field === 'lowStock') return row.lowStock ? '是' : '否'
  if (field === 'status') return queryStatusName(selected.value === 'SERVICE' ? row.serviceKind : selected.value, row.status)
  if (field === 'serviceKind') return serviceOptions.find(([kind]) => kind === row.serviceKind)?.[1] || row.serviceKind
  if (field === 'differenceDirection') return { SURPLUS: '盘盈', SHORTAGE: '盘亏', MATCH: '相符' }[row[field]] || '—'
  return row[field] ?? '—'
}
</script>

<template>
  <div class="page-heading"><h1>业务报表</h1><p class="muted">基于当前可见业务数据，按同一条件查询和导出全部结果</p></div>
  <el-empty v-if="!availableReports.length" description="暂无可查看的报表" />
  <template v-else>
    <el-tabs :model-value="selected" @tab-change="selectReport"><el-tab-pane v-for="[type, label] in availableReports" :key="type" :name="type" :label="label" /></el-tabs>
    <div class="report-filters">
      <el-input v-model="filter.keyword" maxlength="100" clearable placeholder="编号、产品、仓库或设备" class="wide" @keyup.enter="filter.page=1;search()" />
      <el-select v-if="selected==='SERVICE'" v-model="filter.serviceKind" clearable placeholder="业务类型"><el-option v-for="[kind, label] in availableServices" :key="kind" :value="kind" :label="label" /></el-select>
      <el-select v-model="filter.status" clearable placeholder="全部状态"><el-option v-for="state in stateOptions" :key="state" :value="state" :label="queryStatusName(selected, state)" /></el-select>
      <el-select v-if="['STOCK', 'INBOUND', 'OUTBOUND', 'REQUISITION', 'STOCKTAKE', 'DEVICE'].includes(selected)" v-model="filter.productType" clearable placeholder="产品类型"><el-option value="QUANTITY" label="数量" /><el-option value="DEVICE" label="设备" /></el-select>
      <el-select v-if="selected==='STOCK'" v-model="filter.lowStock" clearable placeholder="低库存"><el-option :value="true" label="是" /><el-option :value="false" label="否" /></el-select>
      <el-select v-if="selected==='DEVICE'" v-model="filter.warrantyStatus" clearable placeholder="保修状态"><el-option v-for="s in ['VALID','EXPIRED','NOT_STARTED','UNKNOWN']" :key="s" :label="s" :value="s" /></el-select>
      <el-input v-if="['STOCK','INBOUND','OUTBOUND','REQUISITION','STOCKTAKE','DEVICE'].includes(selected)" v-model="filter.warehouseId" clearable placeholder="仓库 ID" />
      <el-input v-if="selected==='STOCK'" v-model="filter.categoryId" clearable placeholder="分类 ID" />
      <el-input v-if="selected==='STOCK'" v-model="filter.brandId" clearable placeholder="品牌 ID" />
      <el-input v-if="['DEVICE','STOCK'].includes(selected)" v-model="filter.productId" clearable placeholder="产品 ID" />
      <el-input v-if="selected==='DEVICE'" v-model="filter.departmentId" clearable placeholder="部门 ID" />
      <el-input v-if="selected==='DEVICE'" v-model="filter.userId" clearable placeholder="使用人 ID" />
      <el-date-picker v-model="filter.dates" type="daterange" value-format="YYYY-MM-DD" start-placeholder="创建起始日" end-placeholder="创建结束日" />
      <el-button type="primary" :icon="Search" :loading="loading" @click="filter.page=1;search()">查询</el-button>
      <el-button :icon="RefreshRight" @click="reset">重置</el-button>
      <el-button :icon="Download" :loading="exporting" @click="exportAll">导出全部筛选结果</el-button>
    </div>
    <el-alert v-if="error" :title="error" type="error" show-icon class="report-error" />
    <div v-if="result" class="report-summary"><div><span>明细行数</span><strong>{{ summary.rowCount }}</strong></div><div v-if="['INBOUND','OUTBOUND','REQUISITION','STOCKTAKE','SERVICE'].includes(selected)"><span>单据数</span><strong>{{ summary.documentCount }}</strong></div><div v-if="['INBOUND','OUTBOUND','REQUISITION'].includes(selected)"><span>申请数量 / 已执行数量</span><strong>{{ summary.requestedQuantity }} / {{ summary.executedQuantity }}</strong></div><div v-if="selected==='STOCKTAKE'"><span>已确认差异合计</span><strong>{{ summary.finalDifferenceQuantity }}</strong></div><div v-if="selected==='STOCK'"><span>低库存行数</span><strong>{{ summary.lowStockCount }}</strong></div></div>
    <p v-if="result" class="report-note">数量按产品原单位展示；不同单位的合计不宜直接比较。草稿和待审批单据的执行数量为 0，未确认盘点的最终差异为空。</p>
    <el-table v-loading="loading" :data="displayRows" empty-text="暂无结果" class="report-table"><el-table-column v-for="[field, label] in columns" :key="field" :label="label" min-width="140" show-overflow-tooltip><template #default="{ row }">{{ value(row, field) }}</template></el-table-column><el-table-column label="入口" fixed="right" width="80"><template #default="{ row }"><el-button link type="primary" @click="open(row)">查看</el-button></template></el-table-column></el-table>
    <el-pagination v-if="result && total" v-model:current-page="filter.page" v-model:page-size="filter.size" :total="total" :page-sizes="[10,20,50,100]" layout="total, sizes, prev, pager, next" class="report-pages" @current-change="search" @size-change="filter.page=1;search()" />
  </template>
</template>

<style scoped>
.muted,.report-note { color: #64748b; }.report-filters { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; margin: 12px 0 18px; }.report-filters .el-input,.report-filters .el-select { width: 145px; }.report-filters .wide { width: 230px; }.report-filters .el-date-editor { width: 260px; }.report-error { margin-bottom: 16px; }.report-summary { display: flex; flex-wrap: wrap; gap: 12px; margin: 14px 0; }.report-summary div { min-width: 150px; padding: 12px 18px; border: 1px solid #e4ecf8; background: #f7faff; border-radius: 6px; display: grid; gap: 5px; }.report-summary span { color: #64748b; }.report-summary strong { color: #1d4ed8; font-size: 22px; }.report-table { width: 100%; }.report-pages { margin-top: 16px; justify-content: flex-end; }
</style>
