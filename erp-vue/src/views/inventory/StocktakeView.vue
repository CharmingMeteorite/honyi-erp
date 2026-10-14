<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Refresh, View, Edit, Check, Close } from '@element-plus/icons-vue'
import { api } from '../../lib/api.js'
import { authState, hasPermission } from '../../lib/auth.js'

const states = { DRAFT: '草稿', COUNTING: '盘点中', COUNTED: '待确认', CONFIRMED: '已确认', CANCELLED: '已取消' }
const differences = { MATCH: '相符', MISSING: '缺失', EXTRA: '账外', UNREGISTERED: '未登记', WRONG_PRODUCT: '错产品', OUTSIDE_WAREHOUSE: '库外', STATUS_ABNORMAL: '状态异常' }
const rows = ref([]), total = ref(0), loading = ref(false), saving = ref(false), acting = ref(false)
const warehouses = ref([]), products = ref([]), detail = ref(null), detailVisible = ref(false)
const editorVisible = ref(false), editingId = ref(null), countVisible = ref(false), countingItem = ref(null)
const filters = reactive({ warehouseId: null, status: '', keyword: '', page: 1, size: 20 })
const form = reactive({ clientRequestId: '', warehouseId: null, version: null, remark: '', productIds: [] })
const countForm = reactive({ quantity: '0', deviceCodes: '' })
const can = (action) => authState.user?.dataScope !== 'SELF' && hasPermission(`inventory.stocktake.${action}`)
const blocking = computed(() => detail.value?.items.some(item => item.devices.some(device => device.differenceCode && !['MATCH', 'MISSING'].includes(device.differenceCode))) || false)
const uncounted = computed(() => detail.value?.items.some(item => item.countedQuantity == null) || false)
const displayDifference = (value) => value == null ? '—' : Number(value) > 0 ? `+${value}` : String(value)

async function load() {
  loading.value = true
  try { const data = await api.stocktakes(filters); rows.value = data.list; total.value = data.total }
  catch (error) { ElMessage.error(error.message) } finally { loading.value = false }
}
async function searchWarehouses(keyword = '') {
  try { warehouses.value = (await api.stocktakeOptions('warehouses', { keyword, size: 100 })).list }
  catch (error) { ElMessage.error(error.message) }
}
async function searchProducts(keyword = '') {
  try { products.value = (await api.stocktakeOptions('products', { keyword, size: 100 })).list }
  catch (error) { ElMessage.error(error.message) }
}
async function openEditor(row) {
  try {
    const data = row ? await api.stocktake(row.id) : null
    editingId.value = data?.id || null
    Object.assign(form, data ? {
      clientRequestId: data.clientRequestId, warehouseId: data.warehouseId, version: data.version,
      remark: data.remark || '', productIds: data.items.map(item => item.productId),
    } : { clientRequestId: crypto.randomUUID(), warehouseId: null, version: null, remark: '', productIds: [] })
    await Promise.all([searchWarehouses(), searchProducts()])
    if (data && !warehouses.value.some(w => w.id === data.warehouseId)) warehouses.value.push({ id: data.warehouseId, warehouseName: data.warehouseName })
    if (data) data.items.forEach(item => {
      if (!products.value.some(p => p.id === item.productId)) products.value.push({ id: item.productId, productCode: item.productCode, productName: item.productName })
    })
    editorVisible.value = true
  } catch (error) { ElMessage.error(error.message) }
}
async function saveDraft() {
  if (!form.warehouseId || !form.productIds.length) return ElMessage.warning('请选择仓库和盘点物资')
  saving.value = true
  try {
    const payload = { ...form, productIds: [...form.productIds] }
    if (editingId.value) await api.updateStocktake(editingId.value, payload)
    else await api.createStocktake(payload)
    editorVisible.value = false
    ElMessage.success('草稿已保存')
    await load()
  } catch (error) { ElMessage.error(error.message) } finally { saving.value = false }
}
async function openDetail(row) {
  try { detail.value = await api.stocktake(row.id); detailVisible.value = true }
  catch (error) { ElMessage.error(error.message) }
}
async function action(row, name) {
  const labels = { submit: '提交盘点', confirm: '确认差异', cancel: '取消盘点' }
  try {
    await ElMessageBox.confirm(`确认${labels[name]} ${row.stocktakeNo}？`, '库存盘点', { type: 'warning' })
    acting.value = true
    const result = await api.stocktakeAction(row.id, name, { version: row.version })
    if (detailVisible.value && detail.value?.id === row.id) detail.value = result
    ElMessage.success(`${labels[name]}成功`)
    await load()
  } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(error.message) }
  finally { acting.value = false }
}
function openCount(item) {
  countingItem.value = item
  countForm.quantity = item.countedQuantity == null ? '0' : String(item.countedQuantity)
  countForm.deviceCodes = item.devices.filter(device => device.observed).map(device => device.deviceCode).join('\n')
  countVisible.value = true
}
async function saveCount() {
  if (!detail.value || !countingItem.value) return
  const device = countingItem.value.productType === 'DEVICE'
  const payload = { version: detail.value.version, quantity: device ? null : countForm.quantity,
    deviceCodes: device ? countForm.deviceCodes.split(/\r?\n/).map(code => code.trim()).filter(Boolean) : null }
  saving.value = true
  try {
    detail.value = await api.stocktakeCount(detail.value.id, countingItem.value.id, payload)
    countVisible.value = false
    ElMessage.success('实盘已记录')
    await load()
  } catch (error) { ElMessage.error(error.message) } finally { saving.value = false }
}
function reset() { Object.assign(filters, { warehouseId: null, status: '', keyword: '', page: 1 }); load() }
onMounted(() => { load(); searchWarehouses() })
</script>

<template>
  <div class="stocktake-page">
    <div class="page-head"><h2>库存盘点</h2><el-button v-if="can('create')" type="primary" :icon="Plus" @click="openEditor()">新建盘点</el-button></div>
    <div class="filter-bar">
      <el-input v-model="filters.keyword" clearable placeholder="盘点单号" @keyup.enter="filters.page = 1; load()" />
      <el-select v-model="filters.status" clearable placeholder="全部状态"><el-option v-for="(label, state) in states" :key="state" :label="label" :value="state" /></el-select>
      <el-select v-model="filters.warehouseId" clearable filterable remote :remote-method="searchWarehouses" placeholder="全部仓库"><el-option v-for="w in warehouses" :key="w.id" :label="w.warehouseName" :value="w.id" /></el-select>
      <el-button :icon="Search" @click="filters.page = 1; load()">查询</el-button><el-button :icon="Refresh" @click="reset">重置</el-button>
    </div>
    <el-table v-loading="loading" :data="rows" row-key="id">
      <el-table-column prop="stocktakeNo" label="盘点单号" min-width="280" />
      <el-table-column prop="warehouseName" label="仓库" min-width="130" />
      <el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="row.status === 'CONFIRMED' ? 'success' : row.status === 'CANCELLED' ? 'info' : 'primary'">{{ states[row.status] }}</el-tag></template></el-table-column>
      <el-table-column prop="creatorName" label="制单人" min-width="110" />
      <el-table-column prop="createdAt" label="创建时间" min-width="180" />
      <el-table-column label="操作" min-width="340"><template #default="{ row }">
        <el-button link :icon="View" @click="openDetail(row)">详情</el-button>
        <el-button v-if="row.status === 'DRAFT' && can('update')" link :icon="Edit" @click="openEditor(row)">编辑</el-button>
        <el-button v-if="row.status === 'DRAFT' && can('submit')" link :icon="Check" :disabled="acting" @click="action(row, 'submit')">提交</el-button>
        <el-button v-if="row.status === 'COUNTED' && can('confirm')" link type="primary" :icon="Check" :disabled="acting" @click="action(row, 'confirm')">确认</el-button>
        <el-button v-if="!['CONFIRMED','CANCELLED'].includes(row.status) && can('update')" link type="danger" :icon="Close" :disabled="acting" @click="action(row, 'cancel')">取消</el-button>
      </template></el-table-column>
    </el-table>
    <el-pagination v-model:current-page="filters.page" :page-size="filters.size" :total="total" layout="total, prev, pager, next" @current-change="load" />

    <el-dialog v-model="editorVisible" :title="editingId ? '编辑盘点草稿' : '新建盘点单'" width="min(720px, 96vw)" :close-on-click-modal="false">
      <el-form label-width="90px">
        <el-form-item label="仓库" required><el-select v-model="form.warehouseId" filterable remote :remote-method="searchWarehouses" class="wide"><el-option v-for="w in warehouses" :key="w.id" :label="w.warehouseName" :value="w.id" :disabled="w.status === false" /></el-select></el-form-item>
        <el-form-item label="盘点物资" required><el-select v-model="form.productIds" multiple filterable remote :remote-method="searchProducts" collapse-tags collapse-tags-tooltip class="wide"><el-option v-for="p in products" :key="p.id" :label="`${p.productCode} · ${p.productName}`" :value="p.id" /></el-select></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" maxlength="500" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="editorVisible = false">关闭</el-button><el-button type="primary" :loading="saving" @click="saveDraft">保存草稿</el-button></template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="盘点详情" size="min(1050px, 100vw)" class="stocktake-drawer">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="单号">{{ detail.stocktakeNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ states[detail.status] }}</el-descriptions-item>
          <el-descriptions-item label="仓库">{{ detail.warehouseName }}</el-descriptions-item>
          <el-descriptions-item label="制单人">{{ detail.creatorName }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ detail.submittedAt || '—' }}</el-descriptions-item>
          <el-descriptions-item label="确认时间">{{ detail.confirmedAt || '—' }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detail.remark || '—' }}</el-descriptions-item>
        </el-descriptions>
        <h3>盘点明细</h3>
        <el-table :data="detail.items" row-key="id">
          <el-table-column label="物资" min-width="190"><template #default="{ row }">{{ row.productCode }} · {{ row.productName }}</template></el-table-column>
          <el-table-column label="类型" width="95"><template #default="{ row }">{{ row.productType === 'DEVICE' ? '设备' : '数量' }}</template></el-table-column>
          <el-table-column prop="snapshotQuantity" label="账面" width="100" />
          <el-table-column label="实盘" width="100"><template #default="{ row }">{{ row.countedQuantity ?? '未录入' }}</template></el-table-column>
          <el-table-column label="差异" width="100"><template #default="{ row }">{{ displayDifference(row.differenceQuantity) }}</template></el-table-column>
          <el-table-column label="操作" width="110"><template #default="{ row }"><el-button v-if="['COUNTING','COUNTED'].includes(detail.status) && can('count')" link :icon="Edit" @click="openCount(row)">录入</el-button></template></el-table-column>
        </el-table>
        <template v-for="item in detail.items.filter(i => i.productType === 'DEVICE' && i.devices.length)" :key="item.id">
          <h3>{{ item.productName }} · 设备核对</h3>
          <el-table :data="item.devices" size="small" max-height="320">
            <el-table-column prop="deviceCode" label="设备编号" min-width="150" />
            <el-table-column prop="serialNumber" label="SN" min-width="130" />
            <el-table-column label="账面" width="75"><template #default="{ row }">{{ row.expected ? '在库' : '无' }}</template></el-table-column>
            <el-table-column label="实盘" width="75"><template #default="{ row }">{{ row.observed ? '发现' : '未见' }}</template></el-table-column>
            <el-table-column label="差异类型" min-width="120"><template #default="{ row }"><el-tag v-if="row.differenceCode" :type="row.differenceCode === 'MATCH' ? 'success' : row.differenceCode === 'MISSING' ? 'warning' : 'danger'">{{ differences[row.differenceCode] }}</el-tag><span v-else>待盘点</span></template></el-table-column>
            <el-table-column prop="actualStatus" label="登记状态" min-width="105" />
          </el-table>
        </template>
        <h3>库存调整</h3>
        <el-table :data="detail.ledgers" empty-text="暂无调整流水"><el-table-column prop="productId" label="物资ID" /><el-table-column prop="direction" label="方向" /><el-table-column prop="quantity" label="数量" /><el-table-column prop="beforeQuantity" label="调整前" /><el-table-column prop="afterQuantity" label="调整后" /><el-table-column prop="requestId" label="请求追踪" min-width="180" /></el-table>
        <h3>操作历史</h3>
        <el-table :data="detail.logs"><el-table-column prop="operationType" label="动作" min-width="130" /><el-table-column prop="operatorName" label="操作人" /><el-table-column prop="createdAt" label="时间" min-width="180" /><el-table-column prop="requestId" label="请求追踪" min-width="180" /></el-table>
        <div class="detail-actions">
          <el-button v-if="detail.status === 'DRAFT' && can('submit')" :loading="acting" @click="action(detail, 'submit')">提交盘点</el-button>
          <el-button v-if="detail.status === 'COUNTED' && can('confirm')" type="primary" :icon="Check" :disabled="blocking || uncounted" :loading="acting" @click="action(detail, 'confirm')">确认差异</el-button>
          <el-button v-if="!['CONFIRMED','CANCELLED'].includes(detail.status) && can('update')" type="danger" plain :loading="acting" @click="action(detail, 'cancel')">取消单据</el-button>
        </div>
      </template>
    </el-drawer>

    <el-dialog v-model="countVisible" :title="`录入实盘 · ${countingItem?.productName || ''}`" width="min(620px, 96vw)" :close-on-click-modal="false">
      <el-form label-width="90px">
        <el-form-item v-if="countingItem?.productType === 'QUANTITY'" label="实盘数量"><el-input v-model="countForm.quantity" inputmode="decimal" class="wide" /></el-form-item>
        <el-form-item v-else label="设备编号"><el-input v-model="countForm.deviceCodes" type="textarea" :rows="10" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="countVisible = false">关闭</el-button><el-button type="primary" :loading="saving" @click="saveCount">保存实盘</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 16px; }
.page-head h2 { margin: 0; font-size: 22px; }
.filter-bar { display: flex; flex-wrap: wrap; gap: 10px; margin-bottom: 16px; }
.filter-bar .el-input, .filter-bar .el-select { width: 185px; }
.wide { width: 100%; }
.el-pagination { margin-top: 16px; }
h3 { margin: 24px 0 10px; font-size: 16px; }
.detail-actions { display: flex; justify-content: flex-end; flex-wrap: wrap; gap: 8px; margin: 22px 0; }
@media (max-width: 640px) { .filter-bar .el-input, .filter-bar .el-select { width: 100%; } .page-head h2 { font-size: 20px; } }
</style>
