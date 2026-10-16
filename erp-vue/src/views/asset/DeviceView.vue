<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../../lib/api.js'
import { statusTagType } from '../../lib/status.js'
import { hasPermission } from '../../lib/auth.js'
import AttachmentPanel from '../../components/AttachmentPanel.vue'

const rows = ref([])
const route = useRoute()
const router = useRouter()
const selectedDeviceIds = ref([])
function openBatchLabels() {
  if (selectedDeviceIds.value.length > 30) { ElMessage.warning('每次最多打印30张标签'); return }
  router.push({ path: '/assets/qr', query: { ids: selectedDeviceIds.value.join(',') } })
}
const total = ref(0)
const loading = ref(false)
const loadError = ref(''), advancedOpen = ref(false)
const saving = ref(false)
const products = ref([])
const filterProducts = ref([])
const departments = ref([])
const filterDepartments = ref([])
const users = ref([])
const filterUsers = ref([])
const dialog = ref(false)
const detailDialog = ref(false)
const statusDialog = ref(false)
const editing = ref(null)
const detail = ref(null)
const statusDevice = ref(null)
const formRef = ref(null)
const filters = reactive({ keyword: '', status: '', productId: null, departmentId: null, userId: null, page: 1, size: 20 })
const advancedCount = computed(() => [filters.productId, filters.departmentId, filters.userId].filter(Boolean).length)
const empty = () => ({
  deviceCode: '', productId: null, serialNumber: '', macAddress: '', assetNo: '',
  departmentId: null, userId: null, custodianId: null, purchaseAt: null, inboundAt: null,
  firstUseAt: null, warrantyStartAt: null, warrantyEndAt: null, remark: '', notes: '',
})
const form = reactive(empty())
const stateForm = reactive({ status: '', reason: '' })
const statusOptions = [
  { label: '公司持有', value: 'IN_STOCK' }, { label: '使用中', value: 'IN_USE' },
  { label: '借用中', value: 'BORROWED' }, { label: '维修中', value: 'REPAIRING' },
  { label: '待调换', value: 'PENDING_EXCHANGE' }, { label: '已发货', value: 'SHIPPED' },
  { label: '已报废', value: 'SCRAPPED' }, { label: '盘点缺失', value: 'MISSING' },
]
const dateFields = [
  { key: 'purchaseAt', label: '购入时间' }, { key: 'inboundAt', label: '入库登记时间' },
  { key: 'firstUseAt', label: '首次使用时间' }, { key: 'warrantyStartAt', label: '保修开始' },
  { key: 'warrantyEndAt', label: '保修结束' },
]
const selectedProduct = computed(() => editing.value || products.value.find(x => x.id === form.productId))
const identityLocked = computed(() => editing.value && !editing.value.identityEditable)
const statusChoices = computed(() => statusOptions.filter(x => statusDevice.value?.allowedStatuses?.includes(x.value)))
const rules = computed(() => ({
  productId: [{ required: true, message: '请选择设备型产品', trigger: 'change' }],
  serialNumber: [{ required: !!selectedProduct.value?.trackSn, whitespace: true, message: '该产品要求填写SN', trigger: 'blur' }],
  macAddress: [{ required: !!selectedProduct.value?.trackMac, whitespace: true, message: '该产品要求填写MAC', trigger: 'blur' }],
}))
const statusLabel = value => statusOptions.find(x => x.value === value)?.label || value || '未设置'
const timeLabel = value => value?.replace('T', ' ') || '未设置'
const personLabel = person => person.realName + '（' + person.username + '）'

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const data = await api.devices(filters)
    rows.value = data.list
    total.value = data.total
  } catch (e) { rows.value = []; total.value = 0; loadError.value = e.message; ElMessage.error(e.message) } finally { loading.value = false }
}
function search() { filters.page = 1; load() }
function resetFilters() {
  Object.assign(filters, { keyword: '', status: '', productId: null, departmentId: null, userId: null, page: 1 })
  load()
}
async function searchProducts(keyword = '', forForm = false) {
  try {
    const result = await api.productOptions({ keyword, activeOnly: forForm })
    if (forForm) products.value = result
    else filterProducts.value = result
  } catch (e) { ElMessage.error(e.message) }
}
async function searchUsers(keyword = '', forForm = false) {
  try {
    const result = await api.deviceUserOptions({
      keyword, departmentId: forForm ? form.departmentId : undefined,
      activeOnly: forForm, page: 1, size: 100,
    })
    if (forForm) users.value = result.list
    else filterUsers.value = result.list
  } catch (e) { ElMessage.error(e.message) }
}
async function loadOptions(forForm = false) {
  try {
    const result = await api.deviceDepartmentOptions({ activeOnly: forForm })
    if (forForm) departments.value = result
    else filterDepartments.value = result
    await Promise.all([searchProducts('', forForm), searchUsers('', forForm)])
  } catch (e) { ElMessage.error(e.message) }
}
async function changeDepartment() {
  form.userId = null
  form.custodianId = null
  await searchUsers('', true)
}
async function openCreate() {
  editing.value = null
  Object.assign(form, empty())
  await loadOptions(true)
  dialog.value = true
}
async function openEdit(row) {
  try {
    const latest = await api.device(row.id)
    editing.value = latest
    Object.assign(form, empty())
    Object.keys(form).forEach(key => { form[key] = latest[key] ?? empty()[key] })
    await loadOptions(true)
    for (const [id, name] of [[latest.userId, latest.userName], [latest.custodianId, latest.custodianName]]) {
      if (id && !users.value.some(x => x.id === id)) users.value.push({ id, realName: name, username: '当前归属', status: false })
    }
    if (latest.departmentId && !departments.value.some(x => x.id === latest.departmentId)) {
      departments.value.push({ id: latest.departmentId, departmentName: latest.departmentName, disabled: true })
    }
    dialog.value = true
  } catch (e) { ElMessage.error(e.message) }
}
async function openDetail(row) {
  try { detail.value = await api.device(row.id); detailDialog.value = true } catch (e) { ElMessage.error(e.message) }
}
async function save() {
  if (saving.value || !await formRef.value.validate().catch(() => false)) return
  saving.value = true
  try {
    const payload = { ...form }
    if (editing.value) {
      delete payload.productId
      delete payload.deviceCode
      await api.updateDevice(editing.value.id, payload)
    } else await api.createDevice(payload)
    dialog.value = false
    ElMessage.success('保存成功')
    await load()
  } catch (e) { ElMessage.error(e.message) } finally { saving.value = false }
}
async function openStatus(row) {
  try {
    statusDevice.value = await api.device(row.id)
    Object.assign(stateForm, { status: '', reason: '' })
    statusDialog.value = true
  } catch (e) { ElMessage.error(e.message) }
}
async function saveStatus() {
  if (saving.value) return
  if (!stateForm.status || !stateForm.reason.trim()) { ElMessage.warning('请选择状态并填写变更原因'); return }
  try {
    await ElMessageBox.confirm('确认登记为“' + statusLabel(stateForm.status) + '”？' +
      (stateForm.status === 'SCRAPPED' ? '报废后不可恢复。' : ''), '确认状态变更')
    saving.value = true
    await api.deviceStatus(statusDevice.value.id, stateForm.status, stateForm.reason)
    statusDialog.value = false
    ElMessage.success('状态已更新')
    await load()
  } catch (e) { if (e !== 'cancel' && e !== 'close') ElMessage.error(e.message) } finally { saving.value = false }
}
onMounted(async () => {
  if (route.query.id) {
    try { const device = await api.device(Number(route.query.id)); filters.keyword = device.deviceCode; await openDetail(device) }
    catch (e) { ElMessage.error(e.message) }
  }
  load(); loadOptions()
})
</script>

<template>
  <div class="page-heading">
    <div><h1>设备管理</h1><p class="muted">按独立设备编号管理资产资料、归属和状态历史。</p></div>
    <div><el-button v-if="hasPermission('asset.qr.print') && selectedDeviceIds.length" @click="openBatchLabels">批量标签</el-button><el-button v-if="hasPermission('system.device.create')" type="primary" @click="openCreate">新增设备</el-button></div>
  </div>
  <section class="device-list-panel">
    <el-form inline class="device-filters business-filters" @submit.prevent="search">
      <el-form-item><el-input v-model="filters.keyword" class="keyword" placeholder="编号、产品、型号、SN、MAC、资产号、人员、部门" clearable @keyup.enter="search" /></el-form-item>
      <el-form-item><el-select v-model="filters.status" clearable placeholder="设备状态"><el-option v-for="x in statusOptions" :key="x.value" :label="x.label" :value="x.value" /></el-select></el-form-item>
      <div class="filter-actions"><el-button type="primary" :loading="loading" @click="search">查询设备</el-button><el-button @click="resetFilters">重置</el-button><el-button text @click="advancedOpen = !advancedOpen">{{ advancedOpen ? '收起筛选' : '更多筛选' }}{{ advancedCount ? ` · ${advancedCount}` : '' }}</el-button></div>
      <div v-show="advancedOpen" class="filter-extra"><el-form-item><el-select v-model="filters.productId" clearable filterable remote :remote-method="searchProducts" placeholder="搜索产品名称"><el-option v-for="x in filterProducts" :key="x.id" :label="x.productName + ' / ' + (x.model || x.productCode)" :value="x.id" /></el-select></el-form-item><el-form-item><el-select v-model="filters.departmentId" clearable filterable placeholder="归属部门"><el-option v-for="x in filterDepartments" :key="x.id" :label="x.departmentName" :value="x.id" /></el-select></el-form-item><el-form-item><el-select v-model="filters.userId" clearable filterable remote :remote-method="searchUsers" placeholder="使用人或保管人"><el-option v-for="x in filterUsers" :key="x.id" :label="personLabel(x)" :value="x.id" /></el-select></el-form-item></div>
    </el-form>
    <el-alert v-if="loadError" type="error" :title="loadError" :closable="false" show-icon />
    <div class="result-heading"><strong>设备列表</strong><span v-if="!loading && !loadError">共 {{ total }} 台 · 当前页 {{ rows.length }} 台</span><span v-else-if="loading">正在查询设备…</span></div>
    <el-table v-loading="loading" :data="rows" row-key="id" :empty-text="loadError ? '查询失败，请重试' : '当前范围内暂无设备'" @selection-change="items => selectedDeviceIds = items.map(item => item.id)">
      <el-table-column v-if="hasPermission('asset.qr.print')" type="selection" width="48" />
      <el-table-column label="设备编号" min-width="165" show-overflow-tooltip><template #default="{ row }"><strong class="record-code">{{ row.deviceCode }}</strong></template></el-table-column>
      <el-table-column prop="productName" label="产品" min-width="160" show-overflow-tooltip><template #default="{ row }"><span class="record-name">{{ row.productName }}</span></template></el-table-column>
      <el-table-column prop="model" label="型号" min-width="110" show-overflow-tooltip />
      <el-table-column prop="serialNumber" label="SN" min-width="140" show-overflow-tooltip />
      <el-table-column prop="macAddress" label="MAC" min-width="165" show-overflow-tooltip />
      <el-table-column prop="assetNo" label="固定资产编号" min-width="150" show-overflow-tooltip />
      <el-table-column prop="departmentName" label="部门" min-width="120" show-overflow-tooltip />
      <el-table-column prop="userName" label="使用人" min-width="110" show-overflow-tooltip />
      <el-table-column label="状态" min-width="100"><template #default="{ row }"><el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="220" fixed="right"><template #default="{ row }">
        <el-button link @click="openDetail(row)">详情</el-button>
        <el-button v-if="hasPermission('asset.qr.generate')" link @click="router.push({ path: '/assets/qr', query: { deviceId: row.id } })">二维码</el-button>
        <el-button v-if="hasPermission('system.device.update')" link type="primary" @click="openEdit(row)">编辑</el-button>
        <el-button v-if="hasPermission('system.device.status') && row.allowedStatuses?.length" link @click="openStatus(row)">状态</el-button>
      </template></el-table-column>
    </el-table>
    <el-pagination v-model:current-page="filters.page" v-model:page-size="filters.size" :total="total" :page-sizes="[10, 20, 50, 100]" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="search" />
  </section>

  <el-dialog v-model="dialog" :title="editing ? '编辑设备' : '新增设备'" width="760px" destroy-on-close class="device-dialog">
    <el-alert v-if="identityLocked" title="该设备已有状态历史，SN、MAC和固定资产编号已锁定。" type="info" :closable="false" />
    <el-form ref="formRef" :model="form" :rules="rules" label-width="115px" class="device-form business-form" @submit.prevent="save">
      <h3 class="dialog-section-title">设备标识与产品</h3>
      <el-form-item label="设备编号"><el-input v-model="form.deviceCode" :disabled="!!editing" maxlength="64" placeholder="留空自动生成 DEV 编号" /></el-form-item>
      <el-form-item label="产品" prop="productId">
        <el-input v-if="editing" :model-value="editing.productName + ' / ' + (editing.model || editing.productCode)" disabled />
        <el-select v-else v-model="form.productId" filterable remote :remote-method="value => searchProducts(value, true)" placeholder="搜索启用的设备型产品">
          <el-option v-for="x in products" :key="x.id" :label="x.productName + ' / ' + (x.model || x.productCode)" :value="x.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="SN" prop="serialNumber"><el-input v-model="form.serialNumber" :disabled="!!identityLocked" maxlength="128" :placeholder="selectedProduct?.trackSn ? '该产品要求必填' : '选填'" /></el-form-item>
      <el-form-item label="MAC" prop="macAddress"><el-input v-model="form.macAddress" :disabled="!!identityLocked" maxlength="32" :placeholder="selectedProduct?.trackMac ? '必填，例如 AA:BB:CC:DD:EE:FF' : '选填，例如 AA:BB:CC:DD:EE:FF'" /></el-form-item>
      <el-form-item label="固定资产编号"><el-input v-model="form.assetNo" :disabled="!!identityLocked" maxlength="64" /></el-form-item>
      <h3 class="dialog-section-title">归属信息</h3>
      <el-form-item label="归属部门"><el-select v-model="form.departmentId" clearable filterable @change="changeDepartment"><el-option v-for="x in departments" :key="x.id" :label="x.departmentName" :value="x.id" :disabled="x.disabled" /></el-select></el-form-item>
      <el-form-item label="使用人"><el-select v-model="form.userId" clearable filterable remote :remote-method="value => searchUsers(value, true)" :disabled="!form.departmentId" placeholder="先选部门，再搜索实际使用人"><el-option v-for="x in users" :key="x.id" :label="personLabel(x)" :value="x.id" :disabled="!x.status" /></el-select></el-form-item>
      <el-form-item label="保管人"><el-select v-model="form.custodianId" clearable filterable remote :remote-method="value => searchUsers(value, true)" :disabled="!form.departmentId" placeholder="负责保管的人，可与使用人不同"><el-option v-for="x in users" :key="x.id" :label="personLabel(x)" :value="x.id" :disabled="!x.status" /></el-select></el-form-item>
      <h3 class="dialog-section-title">日期与备注</h3>
      <el-form-item v-for="field in dateFields" :key="field.key" :label="field.label"><el-date-picker v-model="form[field.key]" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
      <el-form-item class="form-wide" label="备注"><el-input v-model="form.remark" type="textarea" maxlength="500" show-word-limit :rows="2" /></el-form-item>
      <el-form-item class="form-wide" label="注意事项"><el-input v-model="form.notes" type="textarea" maxlength="1000" show-word-limit :rows="3" /></el-form-item>
      <p class="muted form-wide">新设备默认处于公司持有状态。入库登记时间仅用于资料留档，不产生库存数量。</p>
    </el-form>
    <template #footer><el-button @click="dialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
  </el-dialog>

  <el-dialog v-model="statusDialog" title="登记基础状态" width="480px" class="device-dialog">
    <p>{{ statusDevice?.deviceCode }} · 当前：{{ statusLabel(statusDevice?.status) }}</p>
    <el-form label-width="90px">
      <el-form-item label="目标状态" required><el-select v-model="stateForm.status"><el-option v-for="x in statusChoices" :key="x.value" :label="x.label" :value="x.value" /></el-select></el-form-item>
      <el-form-item label="变更原因" required><el-input v-model="stateForm.reason" type="textarea" maxlength="200" show-word-limit /></el-form-item>
    </el-form>
    <p class="muted">此操作登记设备现状。借用、维修、调换和发货由后续业务流程处理。</p>
    <template #footer><el-button @click="statusDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveStatus">确认变更</el-button></template>
  </el-dialog>

  <el-dialog v-model="detailDialog" title="设备详情" width="850px" class="device-dialog">
    <template v-if="detail">
      <div class="device-detail-identity"><div><span>设备编号</span><strong>{{ detail.deviceCode }}</strong><small>{{ detail.productName }} · {{ detail.model || '未填写型号' }}</small></div><el-tag :type="statusTagType(detail.status)">{{ statusLabel(detail.status) }}</el-tag></div>
      <h3>归属与状态</h3>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="仓库归属">{{ detail.warehouseName || '尚未建立仓库库存' }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusLabel(detail.status) }}</el-descriptions-item>
        <el-descriptions-item label="归属部门">{{ detail.departmentName || '未分配' }}</el-descriptions-item>
        <el-descriptions-item label="使用人">{{ detail.userName || '未分配' }}</el-descriptions-item>
        <el-descriptions-item label="保管人">{{ detail.custodianName || '未分配' }}</el-descriptions-item>
      </el-descriptions>
      <h3>设备标识与产品</h3>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="设备编号">{{ detail.deviceCode }}</el-descriptions-item>
        <el-descriptions-item label="产品">{{ detail.productName }}</el-descriptions-item>
        <el-descriptions-item label="品牌">{{ detail.brandName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="型号">{{ detail.model || '-' }}</el-descriptions-item>
        <el-descriptions-item label="规格">{{ detail.specification || '-' }}</el-descriptions-item>
        <el-descriptions-item label="SN">{{ detail.serialNumber || '-' }}</el-descriptions-item>
        <el-descriptions-item label="MAC">{{ detail.macAddress || '-' }}</el-descriptions-item>
        <el-descriptions-item label="固定资产编号">{{ detail.assetNo || '-' }}</el-descriptions-item>
      </el-descriptions>
      <h3>日期与备注</h3>
      <el-descriptions :column="2" border>
        <el-descriptions-item v-for="field in dateFields" :key="field.key" :label="field.label">{{ timeLabel(detail[field.key]) }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ timeLabel(detail.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ timeLabel(detail.updatedAt) }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2"><span class="multiline">{{ detail.remark || '-' }}</span></el-descriptions-item>
        <el-descriptions-item label="注意事项" :span="2"><span class="multiline">{{ detail.notes || '-' }}</span></el-descriptions-item>
      </el-descriptions>
      <h3>基础历史</h3>
      <el-empty v-if="!detail.history?.length" description="暂无历史记录" />
      <el-timeline v-else>
        <el-timeline-item v-for="item in detail.history" :key="item.id" :timestamp="timeLabel(item.createdAt)">
          <div class="multiline">{{ item.detail }}</div>
          <div v-if="item.fromStatus !== item.toStatus" class="muted">状态：{{ statusLabel(item.fromStatus) }} → {{ statusLabel(item.toStatus) }}</div>
          <div v-if="item.fromDepartmentId !== item.toDepartmentId" class="muted">部门：{{ item.fromDepartmentName || '未分配' }} → {{ item.toDepartmentName || '未分配' }}</div>
          <div v-if="item.fromUserId !== item.toUserId" class="muted">使用人：{{ item.fromUserName || '未分配' }} → {{ item.toUserName || '未分配' }}</div>
          <div v-if="item.fromCustodianId !== item.toCustodianId" class="muted">保管人：{{ item.fromCustodianName || '未分配' }} → {{ item.toCustodianName || '未分配' }}</div>
          <div class="muted">操作人：{{ item.operatorName || '-' }}</div>
        </el-timeline-item>
      </el-timeline>
      <AttachmentPanel business-type="DEVICE" :business-id="detail.id" />
    </template>
  </el-dialog>
</template>

<style scoped>
.device-filters .el-select { width: 190px; }
.device-filters .keyword { width: min(390px, 100%); }
.device-filters .filter-extra :deep(.el-form-item) { margin: 0; }
.device-filters .filter-extra :deep(.el-select) { width: 190px; }
.device-list-panel { min-width: 0; }
.device-detail-identity { display: flex; align-items: start; justify-content: space-between; gap: 12px; margin-bottom: 20px; padding: 17px; border: 1px solid var(--line); border-radius: 8px; background: var(--surface-subtle); }
.device-detail-identity > div { display: grid; gap: 4px; }.device-detail-identity span, .device-detail-identity small { color: var(--text-muted); }.device-detail-identity strong { font-size: 20px; }
.device-form { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); column-gap: 14px; margin-top: 16px; }
.device-form .dialog-section-title, .device-form .form-wide { grid-column: 1 / -1; }
.device-form .form-wide { margin-top: 0; }
.device-form .el-select, .device-form :deep(.el-date-editor) { width: 100%; }
.multiline { white-space: pre-wrap; overflow-wrap: anywhere; }
:global(.device-dialog) { max-width: calc(100vw - 24px); }
@media (max-width: 760px) { .device-form { display: block; } .device-filters .keyword, .device-filters .el-select, .device-filters .filter-extra :deep(.el-select) { width: 100%; } }
</style>
