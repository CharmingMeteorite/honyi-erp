<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../../lib/api.js'
import { authState } from '../../lib/auth.js'
const rows = ref([]), total = ref(0), loading = ref(false), saving = ref(false), completingId = ref(null)
const loadError = ref('')
const warehouses = ref([]), products = ref([]), visible = ref(false), editingId = ref(null), readOnly = ref(false)
const canWrite = computed(() => authState.user?.dataScope !== 'SELF')
const filters = reactive({ warehouseId: null, status: '', page: 1, size: 20 })
const form = reactive({ clientRequestId: '', warehouseId: null, remark: '', items: [] })
const deviceDialog = ref(false), deviceRows = ref([]), deviceTotal = ref(0), deviceLoading = ref(false), deviceItem = ref(null)
const deviceSearch = reactive({ keyword: '', page: 1, size: 20 })
const selectedDevices = ref([])
async function searchWarehouses(keyword = '') {
  try { warehouses.value = (await api.inventoryWarehouses({ keyword, size: 100 })).list } catch (e) { ElMessage.error(e.message) }
}
async function searchProducts(keyword = '') {
  try { products.value = (await api.inventoryProducts({ keyword, size: 100 })).list } catch (e) { ElMessage.error(e.message) }
}
async function load() {
  loading.value = true
  loadError.value = ''
  try { const data = await api.openings(filters); rows.value = data.list; total.value = data.total }
  catch (e) { rows.value = []; total.value = 0; loadError.value = e.message; ElMessage.error(e.message) } finally { loading.value = false }
}
function newItem() { return { productId: null, productType: '', productName: '', quantity: '1', deviceIds: [] } }
async function open(row, detail = false) {
  try {
    const data = row ? await api.opening(row.id) : null
    editingId.value = row?.id || null; readOnly.value = detail || data?.status === 'COMPLETED'
    Object.assign(form, data ? { clientRequestId: data.clientRequestId, warehouseId: data.warehouseId, remark: data.remark, items: data.items } : { clientRequestId: crypto.randomUUID(), warehouseId: null, remark: '', items: [newItem()] })
    if (data && !warehouses.value.some(w => w.id === data.warehouseId)) warehouses.value.push({ id: data.warehouseId, warehouseName: data.warehouseName })
    visible.value = true
    if (!readOnly.value) await searchProducts()
  } catch (e) { ElMessage.error(e.message) }
}
function productChanged(item) {
  const product = products.value.find(p => p.id === item.productId)
  Object.assign(item, { productType: product?.productType || '', productName: product?.productName || '', deviceIds: [], quantity: product?.productType === 'DEVICE' ? '0' : '1' })
}
async function loadDevices() {
  deviceLoading.value = true
  try { const data = await api.openingDevices({ productId: deviceItem.value.productId, ...deviceSearch }); deviceRows.value = data.list; deviceTotal.value = data.total }
  catch (e) { ElMessage.error(e.message) } finally { deviceLoading.value = false }
}
async function chooseDevices(item) {
  deviceItem.value = item; selectedDevices.value = [...item.deviceIds]
  Object.assign(deviceSearch, { keyword: '', page: 1 }); deviceDialog.value = true; await loadDevices()
}
function toggleDevice(id, checked) {
  selectedDevices.value = checked ? [...new Set([...selectedDevices.value, id])] : selectedDevices.value.filter(value => value !== id)
}
function applyDevices() {
  deviceItem.value.deviceIds = [...selectedDevices.value]; deviceItem.value.quantity = String(selectedDevices.value.length)
  deviceDialog.value = false
}
async function save() {
  if (saving.value) return
  if (!form.warehouseId || !form.items.length || form.items.some(i => !i.productId || !/^\d+(\.\d{1,4})?$/.test(String(i.quantity)) || Number(i.quantity) <= 0)) return ElMessage.warning('请选择仓库、物资，并填写正数数量（最多4位小数）')
  if (new Set(form.items.map(i => i.productId)).size !== form.items.length) return ElMessage.warning('物资不能重复')
  if (form.items.some(i => i.productType === 'DEVICE' && i.deviceIds.length !== Number(i.quantity))) return ElMessage.warning('设备数量必须与期初数量相同')
  saving.value = true
  const data = { clientRequestId: form.clientRequestId, warehouseId: form.warehouseId, remark: form.remark, items: form.items.map(i => ({ productId: i.productId, quantity: String(i.quantity), deviceIds: i.deviceIds })) }
  try {
    if (editingId.value) await api.updateOpening(editingId.value, data)
    else await api.createOpening(data)
    visible.value = false; ElMessage.success('期初单已保存，完成后才会增加库存'); await load()
  } catch (e) { ElMessage.error(e.message) } finally { saving.value = false }
}
async function complete(row) {
  try {
    await ElMessageBox.confirm(`确认完成期初单 ${row.openingNo}？完成后将增加库存、绑定设备仓库并记录流水，单据不能再修改。`, '完成期初库存', { type: 'warning' })
    completingId.value = row.id
    await api.completeOpening(row.id); ElMessage.success('期初库存已建立'); await load()
  } catch (e) { if (e !== 'cancel' && e !== 'close') ElMessage.error(e.message) } finally { completingId.value = null }
}
onMounted(() => { load(); searchWarehouses() })
</script>
<template>
  <div class="page">
    <div class="page-head"><div><h2>期初库存</h2><p class="page-subtitle">创建和确认仓库初始库存，完成后记录库存流水</p></div><el-button v-if="canWrite" type="primary" @click="open()">新增期初单</el-button></div>
    <el-alert title="用于登记系统启用时的已有库存。数量型物资填写数量，设备型物资选择具体设备。请核对已有期初单，避免把同一批数量型库存录入不同单据。" :closable="false" />
    <div class="filter-bar business-filters"><el-select v-model="filters.warehouseId" filterable remote :remote-method="searchWarehouses" clearable placeholder="全部仓库"><el-option v-for="w in warehouses" :key="w.id" :value="w.id" :label="w.warehouseName" /></el-select><el-select v-model="filters.status" clearable placeholder="全部状态"><el-option value="DRAFT" label="待完成" /><el-option value="COMPLETED" label="已完成" /></el-select><div class="filter-actions"><el-button type="primary" :loading="loading" @click="filters.page = 1; load()">查询期初单</el-button><el-button @click="Object.assign(filters, { warehouseId: null, status: '', page: 1 }); load()">重置</el-button></div></div>
    <el-alert v-if="loadError" type="error" :title="loadError" :closable="false" show-icon />
    <div class="result-heading"><strong>期初单列表</strong><span v-if="!loading && !loadError">共 {{ total }} 张单据</span><span v-else-if="loading">正在查询期初单…</span></div>
    <el-table v-loading="loading" :data="rows" :empty-text="loadError ? '查询失败，请重试' : '当前条件下暂无期初单'"><el-table-column prop="openingNo" label="期初单号" min-width="210" show-overflow-tooltip><template #default="{ row }"><span class="record-code">{{ row.openingNo }}</span></template></el-table-column><el-table-column prop="warehouseName" label="仓库" min-width="130" show-overflow-tooltip /><el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="row.status === 'COMPLETED' ? 'success' : 'warning'">{{ row.status === 'COMPLETED' ? '已完成' : '待完成' }}</el-tag></template></el-table-column><el-table-column prop="createdAt" label="创建时间" min-width="170" show-overflow-tooltip /><el-table-column prop="completedAt" label="完成时间" min-width="170" show-overflow-tooltip /><el-table-column label="操作" min-width="190" fixed="right"><template #default="{ row }"><el-button link @click="open(row, true)">详情</el-button><template v-if="row.status === 'DRAFT' && canWrite"><el-button link @click="open(row)">编辑</el-button><el-button type="primary" link :disabled="completingId !== null" :loading="completingId === row.id" @click="complete(row)">完成</el-button></template></template></el-table-column></el-table>
    <el-pagination v-model:current-page="filters.page" :page-size="filters.size" :total="total" layout="total, prev, pager, next" @current-change="load" />
    <el-dialog v-model="visible" :title="readOnly ? '期初单详情' : editingId ? '编辑期初单' : '新增期初单'" width="min(1000px, 95vw)" :close-on-click-modal="false">
      <h3 class="dialog-section-title">基本信息</h3><el-form label-width="80px" class="business-form"><el-form-item label="仓库" required><el-select v-model="form.warehouseId" :disabled="readOnly" filterable remote :remote-method="searchWarehouses"><el-option v-for="w in warehouses" :key="w.id" :value="w.id" :label="w.warehouseName" :disabled="w.status === false" /></el-select></el-form-item><el-form-item label="备注"><el-input v-model="form.remark" :disabled="readOnly" maxlength="500" /></el-form-item></el-form>
      <h3 class="dialog-section-title">期初明细 <small>完成后才会产生库存及流水</small></h3>
      <el-table :data="form.items"><el-table-column label="物资" min-width="220"><template #default="{ row }"><span v-if="readOnly">{{ row.productName }}</span><el-select v-else v-model="row.productId" filterable remote :remote-method="searchProducts" placeholder="搜索物资名称" @change="productChanged(row)"><el-option v-if="row.productId && !products.some(p => p.id === row.productId)" :value="row.productId" :label="row.productName" /><el-option v-for="p in products" :key="p.id" :value="p.id" :label="`${p.productCode} · ${p.productName}`" /></el-select></template></el-table-column><el-table-column label="管理方式"><template #default="{ row }">{{ row.productType === 'DEVICE' ? '设备' : row.productType === 'QUANTITY' ? '数量' : '—' }}</template></el-table-column><el-table-column label="期初数量" min-width="130"><template #default="{ row }"><el-input v-model="row.quantity" :disabled="readOnly || row.productType === 'DEVICE'" inputmode="decimal" /></template></el-table-column><el-table-column label="具体设备" min-width="160"><template #default="{ row }"><template v-if="row.productType === 'DEVICE'"><el-button v-if="!readOnly" @click="chooseDevices(row)">选择（{{ row.deviceIds.length }}）</el-button><span v-else>设备 ID：{{ row.deviceIds.join('、') }}</span></template><span v-else>无需关联设备</span></template></el-table-column><el-table-column v-if="!readOnly" label="操作" width="70"><template #default="{ $index }"><el-button link type="danger" @click="form.items.splice($index, 1)">移除</el-button></template></el-table-column></el-table>
      <el-button v-if="!readOnly" class="dialog-add-row" :disabled="form.items.length >= 100" @click="form.items.push(newItem())">添加明细</el-button>
      <template #footer><el-button @click="visible = false">关闭</el-button><el-button v-if="!readOnly" type="primary" :loading="saving" @click="save">保存待完成单据</el-button></template>
    </el-dialog>
    <el-dialog v-model="deviceDialog" title="选择未归属仓库的在库设备" width="min(780px, 95vw)">
      <div class="filter-bar"><el-input v-model="deviceSearch.keyword" placeholder="设备编号 / SN / 资产编号" @keyup.enter="deviceSearch.page = 1; loadDevices()" /><el-button @click="deviceSearch.page = 1; loadDevices()">搜索</el-button></div>
      <p>已选择 {{ selectedDevices.length }} 台 <el-button link @click="selectedDevices = []">清空选择</el-button></p>
      <el-table v-loading="deviceLoading" :data="deviceRows"><el-table-column width="60"><template #default="{ row }"><el-checkbox :model-value="selectedDevices.includes(row.id)" @change="checked => toggleDevice(row.id, checked)" /></template></el-table-column><el-table-column prop="deviceCode" label="设备编号" /><el-table-column prop="serialNumber" label="SN" /><el-table-column prop="assetNo" label="资产编号" /></el-table>
      <el-pagination v-model:current-page="deviceSearch.page" :page-size="deviceSearch.size" :total="deviceTotal" layout="total, prev, pager, next" @current-change="loadDevices" />
      <template #footer><el-button @click="deviceDialog = false">取消</el-button><el-button type="primary" @click="applyDevices">确认选择</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-subtitle { margin: 7px 0 0; color: var(--text-secondary); font-size: 13px; }
.filter-bar .el-input, .filter-bar .el-select { width: 220px; }
.el-pagination { margin-top: 16px; }
</style>
