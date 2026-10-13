<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../../lib/api.js'
import { authState, hasPermission } from '../../lib/auth.js'

const props = defineProps({ kind: { type: String, required: true } })
const title = computed(() => props.kind === 'inbound' ? '入库单' : '出库单')
const states = { DRAFT: '草稿', PENDING_APPROVAL: '待审批', APPROVED: '已批准', REJECTED: '已拒绝', COMPLETED: '已完成', CANCELLED: '已取消' }
const actions = { submit: '提交审批', approve: '审批通过', reject: '审批拒绝', execute: '执行', cancel: '取消单据' }
const logLabels = { CREATE: '创建', UPDATE: '编辑', SUBMIT: '提交审批', APPROVE: '审批通过', REJECT: '审批拒绝', EXECUTE: '执行', CANCEL: '取消' }
const rows = ref([]), total = ref(0), loading = ref(false), saving = ref(false), acting = ref(false)
const warehouses = ref([]), products = ref([]), visible = ref(false), editingId = ref(null), readOnly = ref(false)
const detail = ref(null), dates = ref([])
const filters = reactive({ warehouseId: null, status: '', keyword: '', page: 1, size: 20 })
const form = reactive({ clientRequestId: '', warehouseId: null, remark: '', version: null, items: [] })
const deviceDialog = ref(false), deviceRows = ref([]), deviceTotal = ref(0), deviceLoading = ref(false), deviceItem = ref(null)
const deviceSearch = reactive({ keyword: '', page: 1, size: 20 })
const selectedDevices = ref([])
const can = (action) => authState.user?.dataScope !== 'SELF' && hasPermission(`inventory.${props.kind}.${action}`)
const editable = (row) => ['DRAFT', 'REJECTED'].includes(row.status)
const identity = (d) => [d.deviceCode, d.serialNumber && `SN ${d.serialNumber}`, d.macAddress && `MAC ${d.macAddress}`, d.assetNo && `资产号 ${d.assetNo}`].filter(Boolean).join(' · ')
function newItem() { return { productId: null, productType: '', productName: '', quantity: '1', devices: [] } }
async function searchWarehouses(keyword = '') {
  try { warehouses.value = (await api.inventoryOrderOptions(props.kind, 'warehouses', { keyword, size: 100 })).list } catch (e) { ElMessage.error(e.message) }
}
async function searchProducts(keyword = '') {
  try { products.value = (await api.inventoryOrderOptions(props.kind, 'products', { keyword, size: 100 })).list } catch (e) { ElMessage.error(e.message) }
}
async function load() {
  loading.value = true
  const kind = props.kind
  try {
    const data = await api.inventoryOrders(kind, { ...filters, fromDate: dates.value?.[0], toDate: dates.value?.[1] })
    if (kind === props.kind) { rows.value = data.list; total.value = data.total }
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}
async function open(row, onlyDetail = false) {
  try {
    const data = row ? await api.inventoryOrder(props.kind, row.id) : null
    editingId.value = row?.id || null; readOnly.value = onlyDetail || (data && !editable(data)); detail.value = data
    Object.assign(form, data ? { clientRequestId: data.clientRequestId, version: data.version, warehouseId: data.warehouseId, remark: data.remark, items: data.items }
      : { clientRequestId: crypto.randomUUID(), version: null, warehouseId: null, remark: '', items: [newItem()] })
    if (data && !warehouses.value.some(w => w.id === data.warehouseId)) warehouses.value.push({ id: data.warehouseId, warehouseName: data.warehouseName })
    visible.value = true
    if (!readOnly.value) await searchProducts()
  } catch (e) { ElMessage.error(e.message) }
}
function productChanged(item) {
  const p = products.value.find(p => p.id === item.productId)
  Object.assign(item, { productType: p?.productType || '', productName: p?.productName || '', quantity: p?.productType === 'DEVICE' ? '0' : '1', devices: [] })
}
function warehouseChanged() { form.items.forEach(i => { if (i.productType === 'DEVICE') { i.devices = []; i.quantity = '0' } }) }
async function loadDevices() {
  deviceLoading.value = true
  try {
    const data = await api.inventoryOrderOptions(props.kind, 'devices', { warehouseId: form.warehouseId, productId: deviceItem.value.productId, ...deviceSearch })
    deviceRows.value = data.list; deviceTotal.value = data.total
  } catch (e) { ElMessage.error(e.message) } finally { deviceLoading.value = false }
}
async function chooseDevices(item) {
  if (!form.warehouseId) return ElMessage.warning('请先选择仓库')
  deviceItem.value = item; selectedDevices.value = [...item.devices]
  Object.assign(deviceSearch, { keyword: '', page: 1 }); deviceDialog.value = true; await loadDevices()
}
function toggleDevice(row, checked) {
  selectedDevices.value = checked ? [...selectedDevices.value.filter(d => d.deviceId !== row.deviceId), row] : selectedDevices.value.filter(d => d.deviceId !== row.deviceId)
}
function applyDevices() {
  deviceItem.value.devices = [...selectedDevices.value]; deviceItem.value.quantity = String(selectedDevices.value.length)
  deviceDialog.value = false
}
async function save() {
  if (!form.warehouseId || !form.items.length || form.items.some(i => !i.productId || !/^\d+(\.\d{1,4})?$/.test(String(i.quantity)) || Number(i.quantity) <= 0)) return ElMessage.warning('请选择仓库、物资并填写正数数量')
  if (new Set(form.items.map(i => i.productId)).size !== form.items.length) return ElMessage.warning('同一产品只能添加一行')
  saving.value = true
  const data = { clientRequestId: form.clientRequestId, version: form.version, warehouseId: form.warehouseId, remark: form.remark,
    items: form.items.map(i => ({ productId: i.productId, quantity: String(i.quantity), deviceIds: i.devices.map(d => d.deviceId) })) }
  try {
    if (editingId.value) await api.updateInventoryOrder(props.kind, editingId.value, data)
    else await api.createInventoryOrder(props.kind, data)
    visible.value = false; ElMessage.success('草稿已保存，提交审批后才能执行'); await load()
  } catch (e) { ElMessage.error(e.message) } finally { saving.value = false }
}
async function act(row, action) {
  try {
    let reason = ''
    if (action === 'reject' || action === 'approve') {
      const result = await ElMessageBox.prompt(`${actions[action]}：${row.orderNo}`, title.value, {
        inputPlaceholder: action === 'reject' ? '请填写拒绝原因（必填，最多200字）' : '审批说明（选填，最多200字）',
        inputValidator: value => (action !== 'reject' || !!value?.trim()) && (value?.length || 0) <= 200 || '请填写有效审批说明',
      })
      reason = result.value || ''
    } else {
      await ElMessageBox.confirm(action === 'execute' ? `确认执行 ${row.orderNo}？将同步${props.kind === 'inbound' ? '增加库存并登记设备仓库' : '扣减库存并将所选设备置为已出库'}，完成后单据永久冻结。` : `确认${actions[action]} ${row.orderNo}？`, title.value, { type: 'warning' })
    }
    acting.value = true
    await api.inventoryOrderAction(props.kind, row.id, action, { version: row.version, reason })
    ElMessage.success(`${actions[action]}成功`)
    if (visible.value) await open(row, true)
    await load()
  } catch (e) { if (e !== 'cancel' && e !== 'close') ElMessage.error(e.message) } finally { acting.value = false }
}
function reset() { Object.assign(filters, { warehouseId: null, status: '', keyword: '', page: 1 }); dates.value = []; load() }
watch(() => props.kind, () => { visible.value = false; deviceDialog.value = false; rows.value = []; reset(); searchWarehouses() }, { immediate: true })
</script>

<template>
  <div class="page">
    <div class="page-head"><h2>{{ title }}</h2><el-button v-if="can('create')" type="primary" @click="open()">新建{{ title }}</el-button></div>
    <el-alert :closable="false" :title="props.kind === 'inbound' ? '入库设备请先在设备档案中建档，再选择未归属仓库的设备。审批不会改变库存，正式执行才会入库。' : '出库须选择当前仓库的在库设备。审批不会扣减库存，正式执行后设备变为已出库。'" />
    <div class="filter-bar">
      <el-input v-model="filters.keyword" clearable placeholder="单号" @keyup.enter="filters.page = 1; load()" />
      <el-select v-model="filters.status" clearable placeholder="全部状态"><el-option v-for="(name, state) in states" :key="state" :value="state" :label="name" /></el-select>
      <el-select v-model="filters.warehouseId" clearable filterable remote :remote-method="searchWarehouses" placeholder="全部仓库"><el-option v-for="w in warehouses" :key="w.id" :value="w.id" :label="w.warehouseName" /></el-select>
      <el-date-picker v-model="dates" type="daterange" value-format="YYYY-MM-DD" start-placeholder="创建日期起" end-placeholder="创建日期止" />
      <el-button @click="filters.page = 1; load()">查询</el-button><el-button @click="reset">重置</el-button>
    </div>
    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="orderNo" label="单号" min-width="290" /><el-table-column prop="warehouseName" label="仓库" min-width="120" />
      <el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="row.status === 'COMPLETED' ? 'success' : row.status === 'REJECTED' ? 'danger' : 'info'">{{ states[row.status] }}</el-tag></template></el-table-column>
      <el-table-column prop="creatorName" label="制单人" /><el-table-column prop="createdAt" label="创建时间" min-width="180" />
      <el-table-column label="操作" min-width="330"><template #default="{ row }">
        <el-button link @click="open(row, true)">详情</el-button>
        <el-button v-if="editable(row) && can('update')" link @click="open(row)">{{ row.status === 'REJECTED' ? '修订草稿' : '编辑' }}</el-button>
        <el-button v-if="row.status === 'DRAFT' && can('submit')" link :disabled="acting" @click="act(row, 'submit')">提交审批</el-button>
        <template v-if="row.status === 'PENDING_APPROVAL' && can('approve')"><el-button link :disabled="acting" @click="act(row, 'approve')">通过</el-button><el-button link type="danger" :disabled="acting" @click="act(row, 'reject')">拒绝</el-button></template>
        <el-button v-if="row.status === 'APPROVED' && can('execute')" link type="primary" :disabled="acting" @click="act(row, 'execute')">执行</el-button>
        <el-button v-if="editable(row) && can('update')" link type="danger" :disabled="acting" @click="act(row, 'cancel')">取消</el-button>
      </template></el-table-column>
    </el-table>
    <el-pagination v-model:current-page="filters.page" :page-size="filters.size" :total="total" layout="total, prev, pager, next" @current-change="load" />
    <el-dialog v-model="visible" :title="`${readOnly ? '详情' : editingId ? '编辑' : '新建'} · ${title}`" width="min(1150px, 96vw)" :close-on-click-modal="false">
      <template v-if="detail && readOnly">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="单号">{{ detail.orderNo }}</el-descriptions-item><el-descriptions-item label="状态">{{ states[detail.status] }}</el-descriptions-item><el-descriptions-item label="制单人">{{ detail.creatorName }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ detail.createdAt }}</el-descriptions-item><el-descriptions-item label="提交时间">{{ detail.submittedAt || '—' }}</el-descriptions-item><el-descriptions-item label="审批">{{ detail.approverName || '—' }} {{ detail.approvedAt }}</el-descriptions-item>
          <el-descriptions-item label="执行">{{ detail.executorName || '—' }} {{ detail.executedAt }}</el-descriptions-item><el-descriptions-item label="请求追踪">{{ detail.requestId || '—' }}</el-descriptions-item><el-descriptions-item label="版本">{{ detail.version }}</el-descriptions-item>
        </el-descriptions>
      </template>
      <el-form label-width="80px">
        <el-form-item label="仓库" required><el-select v-model="form.warehouseId" :disabled="readOnly" filterable remote :remote-method="searchWarehouses" @change="warehouseChanged"><el-option v-for="w in warehouses" :key="w.id" :value="w.id" :label="w.warehouseName" :disabled="w.status === false" /></el-select></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" :disabled="readOnly" maxlength="500" /></el-form-item>
      </el-form>
      <el-table :data="form.items">
        <el-table-column label="物资" min-width="220"><template #default="{ row }"><span v-if="readOnly">{{ row.productCode }} · {{ row.productName }}</span><el-select v-else v-model="row.productId" filterable remote :remote-method="searchProducts" @change="productChanged(row)"><el-option v-if="row.productId && !products.some(p => p.id === row.productId)" :value="row.productId" :label="row.productName" /><el-option v-for="p in products" :key="p.id" :value="p.id" :label="`${p.productCode} · ${p.productName}`" /></el-select></template></el-table-column>
        <el-table-column label="数量" width="140"><template #default="{ row }"><el-input v-model="row.quantity" :disabled="readOnly || row.productType === 'DEVICE'" inputmode="decimal" /></template></el-table-column>
        <el-table-column label="具体设备" min-width="350"><template #default="{ row }"><template v-if="row.productType === 'DEVICE'"><el-button v-if="!readOnly" @click="chooseDevices(row)">选择设备（{{ row.devices.length }}）</el-button><div v-for="d in row.devices" :key="d.deviceId" class="device-identity">{{ identity(d) }}</div></template><span v-else>数量管理</span></template></el-table-column>
        <el-table-column v-if="!readOnly" label="操作" width="70"><template #default="{ $index }"><el-button link type="danger" @click="form.items.splice($index, 1)">移除</el-button></template></el-table-column>
      </el-table>
      <el-button v-if="!readOnly" :disabled="form.items.length >= 100" @click="form.items.push(newItem())">添加明细</el-button>
      <template v-if="readOnly && detail">
        <h3>审批与操作记录</h3><el-table :data="detail.logs"><el-table-column label="动作"><template #default="{ row }">{{ logLabels[row.operationType.split('_').slice(1).join('_')] || row.operationType }}</template></el-table-column><el-table-column prop="operatorName" label="操作人" /><el-table-column prop="detail" label="说明" min-width="200" /><el-table-column prop="createdAt" label="时间" min-width="180" /><el-table-column prop="requestId" label="请求追踪" min-width="200" /></el-table>
        <h3>执行结果</h3><el-empty v-if="!detail.ledgers.length" description="尚未执行，没有库存变化" :image-size="60" />
        <el-table v-else :data="detail.ledgers"><el-table-column prop="productId" label="产品ID" /><el-table-column prop="direction" label="方向" /><el-table-column prop="quantity" label="数量" /><el-table-column prop="beforeQuantity" label="变更前" /><el-table-column prop="afterQuantity" label="变更后" /><el-table-column prop="operatorName" label="执行人" /><el-table-column prop="requestId" label="请求追踪" min-width="200" /></el-table>
      </template>
      <template #footer>
        <el-button @click="visible = false">关闭</el-button><el-button v-if="!readOnly" type="primary" :loading="saving" @click="save">保存草稿</el-button>
        <template v-if="readOnly && detail"><el-button v-if="detail.status === 'DRAFT' && can('submit')" :loading="acting" @click="act(detail, 'submit')">提交审批</el-button><template v-if="detail.status === 'PENDING_APPROVAL' && can('approve')"><el-button :loading="acting" @click="act(detail, 'reject')">拒绝</el-button><el-button type="primary" :loading="acting" @click="act(detail, 'approve')">通过</el-button></template><el-button v-if="detail.status === 'APPROVED' && can('execute')" type="primary" :loading="acting" @click="act(detail, 'execute')">执行</el-button></template>
      </template>
    </el-dialog>
    <el-dialog v-model="deviceDialog" :title="props.kind === 'inbound' ? '选择未归属仓库的设备' : '选择当前仓库的在库设备'" width="min(950px, 96vw)">
      <div class="filter-bar"><el-input v-model="deviceSearch.keyword" placeholder="设备编号 / SN / MAC / 资产编号" @keyup.enter="deviceSearch.page = 1; loadDevices()" /><el-button @click="deviceSearch.page = 1; loadDevices()">搜索</el-button></div>
      <p>已选 {{ selectedDevices.length }} 台 <el-button link @click="selectedDevices = []">清空</el-button></p>
      <el-tag v-for="d in selectedDevices" :key="d.deviceId" closable @close="toggleDevice(d, false)">{{ identity(d) }}</el-tag>
      <el-table v-loading="deviceLoading" :data="deviceRows"><el-table-column width="55"><template #default="{ row }"><el-checkbox :model-value="selectedDevices.some(d => d.deviceId === row.deviceId)" @change="checked => toggleDevice(row, checked)" /></template></el-table-column><el-table-column prop="deviceCode" label="设备编号" /><el-table-column prop="serialNumber" label="SN" /><el-table-column prop="macAddress" label="MAC" /><el-table-column prop="assetNo" label="资产编号" /></el-table>
      <el-pagination v-model:current-page="deviceSearch.page" :page-size="deviceSearch.size" :total="deviceTotal" layout="total, prev, pager, next" @current-change="loadDevices" />
      <template #footer><el-button @click="deviceDialog = false">取消</el-button><el-button type="primary" @click="applyDevices">确认选择</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.page-head h2 { margin: 0; }
.filter-bar { display: flex; flex-wrap: wrap; gap: 12px; margin: 16px 0; }
.filter-bar .el-input, .filter-bar .el-select { width: 200px; }
.el-pagination, .el-form { margin-top: 16px; }
.device-identity { overflow-wrap: anywhere; padding: 4px 0; }
.el-tag { margin: 3px; white-space: normal; height: auto; }
</style>
