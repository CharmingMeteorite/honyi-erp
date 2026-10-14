<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../../lib/api.js'
import { authState, hasPermission } from '../../lib/auth.js'

const props = defineProps({ kind: { type: String, required: true } })
const titles = { repair: '设备维修', warranty: '保修申请', exchange: '换货处理' }
const stateNames = { DRAFT: '草稿', SUBMITTED: '已提交', ACCEPTED: '已受理', REPAIRING: '维修中', APPROVED: '已批准', REJECTED: '已拒绝', COMPLETED: '已完成', UNREPAIRABLE: '无法修复', CANCELLED: '已取消', CLOSED: '已关闭' }
const warrantyNames = { VALID: '保修期内', EXPIRED: '已过保', UNKNOWN: '保修日期不完整', NOT_STARTED: '保修尚未开始' }
const decisionNames = { WARRANTY: '保内维修', PAID_REPAIR: '明确转自费维修', REJECTED: '拒绝' }
const states = computed(() => props.kind === 'repair' ? ['DRAFT', 'SUBMITTED', 'ACCEPTED', 'REPAIRING', 'COMPLETED', 'UNREPAIRABLE', 'CANCELLED', 'CLOSED'] : props.kind === 'warranty' ? ['DRAFT', 'SUBMITTED', 'ACCEPTED', 'APPROVED', 'REJECTED', 'COMPLETED', 'CANCELLED'] : ['DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'COMPLETED', 'CANCELLED'])
const filters = reactive({ keyword: '', status: '', deviceId: null, productId: null, applicantId: null, fromDate: '', toDate: '', page: 1, size: 20 })
const rows = ref([]), total = ref(0), loading = ref(false), saving = ref(false)
const detail = ref(null), detailOpen = ref(false), editOpen = ref(false), deviceOptions = ref([]), newOptions = ref([])
const devicePage = ref(1), newPage = ref(1), deviceTotal = ref(0), newTotal = ref(0), deviceKeyword = ref(''), newKeyword = ref('')
const form = reactive({}), editingId = ref(null), selectedProductId = ref(null)
const actionOpen = ref(false), actionTarget = ref(null), chosenAction = ref(''), actionForm = reactive({ clientRequestId: '', result: '', remark: '', notes: '' })
const actionLabels = { submit: '提交', accept: '受理', start: '送修 / 开始维修', complete: '完成', unrepairable: '确认无法修复', close: '关闭', cancel: '取消', approve: '批准', reject: '拒绝', execute: '执行换货', 'paid-repair': '转自费维修' }
const can = (action) => hasPermission(`asset.${props.kind}.${action}`)
const staff = computed(() => authState.user?.dataScope !== 'SELF')
function allowed(row) {
  const result = []
  if (row.status === 'DRAFT' && can('submit')) result.push('submit')
  if (['DRAFT', 'SUBMITTED', 'ACCEPTED'].includes(row.status) && can('cancel')) result.push('cancel')
  if (staff.value) {
    if (['repair', 'warranty'].includes(props.kind) && row.status === 'SUBMITTED' && can('process')) result.push('accept')
    if (props.kind === 'repair') {
      if (row.status === 'ACCEPTED' && can('process')) result.push('start')
      if (row.status === 'REPAIRING' && can('complete')) result.push('complete', 'unrepairable')
      if (['COMPLETED', 'UNREPAIRABLE'].includes(row.status) && can('complete')) result.push('close')
    } else if (props.kind === 'warranty') {
      if (row.status === 'ACCEPTED') { if (can('approve')) result.push('approve', 'paid-repair'); if (can('reject')) result.push('reject') }
      if (row.status === 'APPROVED' && can('complete')) result.push('complete')
    } else {
      if (row.status === 'SUBMITTED') { if (can('approve')) result.push('approve'); if (can('reject')) result.push('reject') }
      if (row.status === 'APPROVED' && can('execute')) result.push('execute')
    }
  }
  return result
}
async function load() {
  loading.value = true
  try { const data = await api.assetServiceOrders(props.kind, filters); rows.value = data.list; total.value = data.total }
  catch (error) { ElMessage.error(error.message) } finally { loading.value = false }
}
function search() { filters.page = 1; load() }
async function show(row) {
  try { detail.value = await api.assetServiceOrder(props.kind, row.id); detailOpen.value = true }
  catch (error) { ElMessage.error(error.message) }
}
function newForm() {
  Object.assign(form, { deviceId: null, newDeviceId: null, reason: '', faultPhenomenon: '', faultDescription: '', remark: '', notes: '', serviceProvider: '', serviceLocation: '', expectedCompleteAt: null, clientRequestId: crypto.randomUUID(), version: null })
  editingId.value = null; selectedProductId.value = null; devicePage.value = 1; newPage.value = 1; deviceOptions.value = []; newOptions.value = []; editOpen.value = true; findDevices('')
}
async function edit(row) {
  try {
    const data = await api.assetServiceOrder(props.kind, row.id)
    Object.assign(form, { deviceId: data.deviceId, newDeviceId: data.newDeviceId, reason: data.reason, faultPhenomenon: data.faultPhenomenon, faultDescription: data.faultDescription, remark: data.remark, notes: data.notes, serviceProvider: data.serviceProvider, serviceLocation: data.serviceLocation, expectedCompleteAt: data.expectedCompleteAt, clientRequestId: data.clientRequestId, version: data.version })
    editingId.value = data.id; selectedProductId.value = data.productId; deviceOptions.value = [{ id: data.deviceId, deviceCode: data.deviceCode, productName: data.productName }]; newOptions.value = data.newDeviceId ? [{ id: data.newDeviceId, deviceCode: data.newDeviceCode }] : []; editOpen.value = true
  } catch (error) { ElMessage.error(error.message) }
}
async function findDevices(keyword = deviceKeyword.value) {
  if (keyword !== deviceKeyword.value) devicePage.value = 1
  deviceKeyword.value = keyword
  try { const data = await api.assetServiceDeviceOptions(props.kind, { keyword, page: devicePage.value, size: 20 }); deviceOptions.value = data.list; deviceTotal.value = data.total }
  catch (error) { ElMessage.error(error.message) }
}
async function selectDevice(id) {
  const device = deviceOptions.value.find(d => d.id === id)
  selectedProductId.value = device?.productId; form.newDeviceId = null; newPage.value = 1
  if (props.kind === 'exchange') await findNewDevices('')
}
async function findNewDevices(keyword = newKeyword.value) {
  if (!selectedProductId.value) return
  if (keyword !== newKeyword.value) newPage.value = 1
  newKeyword.value = keyword
  try { const data = await api.assetServiceDeviceOptions(props.kind, { replacement: true, productId: selectedProductId.value, keyword, page: newPage.value, size: 20 }); newOptions.value = data.list; newTotal.value = data.total }
  catch (error) { ElMessage.error(error.message) }
}
async function save() {
  if (!form.deviceId || !form.reason?.trim()) { ElMessage.warning('请选择设备并填写原因'); return }
  if (props.kind === 'exchange' && !form.newDeviceId) { ElMessage.warning('请选择独立的新设备'); return }
  if (props.kind !== 'exchange' && !form.faultDescription?.trim()) { ElMessage.warning('请填写故障描述'); return }
  saving.value = true
  try { if (editingId.value) await api.updateAssetServiceOrder(props.kind, editingId.value, form); else await api.createAssetServiceOrder(props.kind, form); editOpen.value = false; ElMessage.success('已保存'); await load() }
  catch (error) { ElMessage.error(error.message) } finally { saving.value = false }
}
function prepareAction(row, action) {
  actionTarget.value = row; chosenAction.value = action
  Object.assign(actionForm, { clientRequestId: crypto.randomUUID(), result: '', remark: row.remark || '', notes: row.notes || '' }); actionOpen.value = true
}
async function executeAction() {
  const action = chosenAction.value
  if (['complete', 'unrepairable', 'approve', 'paid-repair', 'reject'].includes(action) && !actionForm.result.trim()) { ElMessage.warning('请填写明确的处理结果'); return }
  try { await ElMessageBox.confirm(`确认${actionLabels[action]}？设备和库存将在后端重新校验。`, '业务确认', { type: 'warning' }) } catch { return }
  saving.value = true
  try {
    const updated = await api.assetServiceAction(props.kind, actionTarget.value.id, action, { ...actionForm, version: actionTarget.value.version })
    actionOpen.value = false; ElMessage.success('处理成功'); if (detail.value?.id === updated.id) detail.value = updated; await load()
  } catch (error) { ElMessage.error(error.message) } finally { saving.value = false }
}
watch(() => props.kind, () => { detailOpen.value = false; editOpen.value = false; actionOpen.value = false; filters.page = 1; filters.status = ''; load() })
onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head"><h2>{{ titles[kind] }}</h2><el-button v-if="can('create')" type="primary" @click="newForm">新建申请</el-button></div>
    <div class="panel">
      <div class="filter-bar">
        <el-input v-model="filters.keyword" clearable placeholder="业务编号 / 设备编号 / 产品" @keyup.enter="search" />
        <el-select v-model="filters.status" clearable placeholder="全部状态"><el-option v-for="state in states" :key="state" :value="state" :label="stateNames[state]" /></el-select>
        <el-input-number v-model="filters.deviceId" :min="1" :precision="0" placeholder="Device ID" />
        <el-input-number v-model="filters.productId" :min="1" :precision="0" placeholder="Product ID" />
        <el-input-number v-model="filters.applicantId" :min="1" :precision="0" placeholder="申请人 ID" />
        <el-date-picker v-model="filters.fromDate" type="date" value-format="YYYY-MM-DD" placeholder="开始日期" />
        <el-date-picker v-model="filters.toDate" type="date" value-format="YYYY-MM-DD" placeholder="结束日期" />
        <el-button type="primary" @click="search">查询</el-button>
      </div>
      <el-table v-loading="loading" :data="rows" empty-text="暂无业务单据">
        <el-table-column prop="orderNo" label="业务编号" min-width="220" />
        <el-table-column prop="deviceCode" label="设备编号" min-width="140" />
        <el-table-column prop="productName" label="产品" min-width="140" />
        <el-table-column prop="applicantName" label="申请人" width="110" />
        <el-table-column label="状态" width="110"><template #default="{ row }"><el-tag>{{ stateNames[row.status] }}</el-tag></template></el-table-column>
        <el-table-column prop="createdAt" label="申请时间" min-width="170" />
        <el-table-column label="操作" min-width="270" fixed="right"><template #default="{ row }">
          <el-button link type="primary" @click="show(row)">详情</el-button>
          <el-button v-if="row.status === 'DRAFT' && can('update')" link type="primary" @click="edit(row)">编辑</el-button>
          <el-button v-for="action in allowed(row)" :key="action" link type="primary" @click="prepareAction(row, action)">{{ actionLabels[action] }}</el-button>
        </template></el-table-column>
      </el-table>
      <div class="table-footer"><el-pagination v-model:current-page="filters.page" v-model:page-size="filters.size" :total="total" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="search" /></div>
    </div>

    <el-dialog v-model="editOpen" :title="`${editingId ? '编辑草稿' : '新建'} · ${titles[kind]}`" width="720px" :close-on-click-modal="false">
      <el-form label-width="110px">
        <el-form-item :label="kind === 'exchange' ? '旧设备' : '具体设备'" required>
          <el-select v-model="form.deviceId" filterable remote :remote-method="findDevices" :disabled="!!editingId" placeholder="搜索合法设备" @change="selectDevice">
            <el-option v-for="device in deviceOptions" :key="device.id" :value="device.id" :label="`${device.deviceCode} · ${device.productName || ''}`" />
          </el-select>
          <el-pagination v-if="!editingId" v-model:current-page="devicePage" :total="deviceTotal" :page-size="20" layout="prev, pager, next" @current-change="findDevices()" />
        </el-form-item>
        <el-form-item v-if="kind === 'exchange'" label="新设备" required>
          <el-select v-model="form.newDeviceId" filterable remote :remote-method="findNewDevices" :disabled="!!editingId || !form.deviceId" placeholder="同产品可用库存设备"><el-option v-for="device in newOptions" :key="device.id" :value="device.id" :label="`${device.deviceCode} · ${device.warehouseName || ''}`" /></el-select>
          <el-pagination v-if="!editingId" v-model:current-page="newPage" :total="newTotal" :page-size="20" layout="prev, pager, next" @current-change="findNewDevices()" />
        </el-form-item>
        <el-form-item :label="kind === 'exchange' ? '换货原因' : '申请原因'" required><el-input v-model="form.reason" type="textarea" maxlength="500" show-word-limit /></el-form-item>
        <el-form-item v-if="kind === 'repair'" label="故障现象" required><el-input v-model="form.faultPhenomenon" maxlength="200" /></el-form-item>
        <el-form-item v-if="kind !== 'exchange'" label="故障描述" required><el-input v-model="form.faultDescription" type="textarea" maxlength="1000" show-word-limit /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" maxlength="500" /></el-form-item>
        <template v-if="kind === 'repair'">
          <el-form-item label="注意事项"><el-input v-model="form.notes" type="textarea" maxlength="1000" /></el-form-item>
          <el-form-item label="服务商"><el-input v-model="form.serviceProvider" maxlength="200" /></el-form-item>
          <el-form-item label="送修地点"><el-input v-model="form.serviceLocation" maxlength="200" /></el-form-item>
          <el-form-item label="预计完成"><el-date-picker v-model="form.expectedCompleteAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
        </template>
        <el-alert v-if="kind === 'warranty'" title="系统记录设备当前保修日期快照；过保或日期不完整申请须明确拒绝或转自费维修。" type="info" :closable="false" />
      </el-form>
      <template #footer><el-button @click="editOpen = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存草稿</el-button></template>
    </el-dialog>

    <el-dialog v-model="actionOpen" :title="actionLabels[chosenAction]" width="560px" :close-on-click-modal="false">
      <el-form label-width="100px">
        <el-form-item label="处理结果"><el-input v-model="actionForm.result" type="textarea" maxlength="1000" show-word-limit /></el-form-item>
        <el-form-item label="备注"><el-input v-model="actionForm.remark" type="textarea" maxlength="500" /></el-form-item>
        <el-form-item v-if="kind === 'repair'" label="注意事项"><el-input v-model="actionForm.notes" type="textarea" maxlength="1000" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="actionOpen = false">返回</el-button><el-button type="primary" :loading="saving" @click="executeAction">确认处理</el-button></template>
    </el-dialog>

    <el-dialog v-model="detailOpen" :title="titles[kind] + '详情'" width="1000px">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="业务编号">{{ detail.orderNo }}</el-descriptions-item><el-descriptions-item label="状态">{{ stateNames[detail.status] }} · v{{ detail.version }}</el-descriptions-item>
          <el-descriptions-item label="设备 / 产品">{{ detail.deviceCode }} · {{ detail.productName }}</el-descriptions-item><el-descriptions-item label="申请人">{{ detail.applicantName }}</el-descriptions-item>
          <el-descriptions-item label="原因" :span="2">{{ detail.reason }}</el-descriptions-item>
          <el-descriptions-item v-if="kind !== 'exchange'" label="故障描述" :span="2">{{ detail.faultDescription }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detail.remark || '—' }}</el-descriptions-item>
          <el-descriptions-item v-if="kind === 'repair'" label="注意事项" :span="2">{{ detail.notes || '—' }}</el-descriptions-item>
          <el-descriptions-item label="处理结果" :span="2">{{ detail.result || '—' }}</el-descriptions-item>
          <el-descriptions-item label="处理人">{{ detail.handlerName || detail.executorName || '—' }}</el-descriptions-item><el-descriptions-item label="审批人">{{ detail.approverName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ detail.submittedAt || '—' }}</el-descriptions-item><el-descriptions-item label="完成时间">{{ detail.completedAt || '—' }}</el-descriptions-item>
          <template v-if="kind === 'repair'">
            <el-descriptions-item label="故障现象">{{ detail.faultPhenomenon }}</el-descriptions-item><el-descriptions-item label="送修前状态">{{ detail.originalStatus }}</el-descriptions-item>
            <el-descriptions-item label="服务商 / 地点">{{ detail.serviceProvider || '—' }} / {{ detail.serviceLocation || '—' }}</el-descriptions-item><el-descriptions-item label="送修 / 预计完成">{{ detail.sentAt || '—' }} / {{ detail.expectedCompleteAt || '—' }}</el-descriptions-item>
          </template>
          <template v-if="kind === 'warranty'">
            <el-descriptions-item label="保修快照">{{ detail.warrantyStartAt || '—' }} → {{ detail.warrantyEndAt || '—' }}</el-descriptions-item><el-descriptions-item label="保修识别 / 决策">{{ warrantyNames[detail.warrantyStatus] }} / {{ decisionNames[detail.decision] || '待处理' }}</el-descriptions-item>
            <el-descriptions-item label="关联维修" :span="2">{{ detail.linkedRepairNo || '—' }} · {{ stateNames[detail.linkedRepairStatus] || '' }}</el-descriptions-item>
          </template>
          <el-descriptions-item v-if="kind === 'exchange'" label="旧 → 新设备" :span="2">{{ detail.deviceCode }} → {{ detail.newDeviceCode }}；{{ detail.reason }}</el-descriptions-item>
        </el-descriptions>
        <h3>库存流水</h3><el-table :data="detail.ledgers" empty-text="无库存变化"><el-table-column prop="businessType" label="业务类型" /><el-table-column prop="direction" label="方向" /><el-table-column prop="quantity" label="数量" /><el-table-column prop="beforeQuantity" label="变动前" /><el-table-column prop="afterQuantity" label="变动后" /></el-table>
        <h3>操作记录</h3><el-table :data="detail.logs"><el-table-column prop="operationType" label="动作" /><el-table-column prop="detail" label="说明" min-width="240" /><el-table-column prop="operatorName" label="操作人" /><el-table-column prop="requestId" label="request_id" /><el-table-column prop="createdAt" label="时间" /></el-table>
        <h3>设备历史</h3><el-table :data="detail.deviceHistory"><el-table-column prop="fromStatus" label="原状态" /><el-table-column prop="toStatus" label="新状态" /><el-table-column prop="businessNo" label="业务编号" min-width="220" /><el-table-column prop="detail" label="说明" min-width="200" /><el-table-column prop="createdAt" label="时间" /></el-table>
        <template v-if="kind === 'exchange'"><h3>新设备历史</h3><el-table :data="detail.newDeviceHistory"><el-table-column prop="fromStatus" label="原状态" /><el-table-column prop="toStatus" label="新状态" /><el-table-column prop="detail" label="说明" /><el-table-column prop="createdAt" label="时间" /></el-table></template>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.filter-bar { display: flex; gap: 10px; flex-wrap: wrap; padding: 16px; }
.filter-bar :deep(.el-input), .filter-bar :deep(.el-select) { width: 190px; }
.el-form :deep(.el-select) { width: 100%; }
.el-descriptions { white-space: pre-wrap; }
h3 { margin: 24px 0 12px; color: var(--ink); }
</style>
