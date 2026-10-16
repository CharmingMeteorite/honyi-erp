<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../../lib/api.js'
const filters = reactive({ warehouseId: null, productId: null, businessType: '', page: 1, size: 20 })
const rows = ref([]), total = ref(0), warehouses = ref([]), loading = ref(false), error = ref('')
async function searchWarehouses(keyword = '') { try { warehouses.value = (await api.inventoryWarehouses({ keyword, size: 100 })).list } catch (e) { ElMessage.error(e.message) } }
async function load() {
  loading.value = true
  error.value = ''
  try { const data = await api.stockLedgers(filters); rows.value = data.list; total.value = data.total }
  catch (e) { rows.value = []; total.value = 0; error.value = e.message; ElMessage.error(e.message) } finally { loading.value = false }
}
function reset() { Object.assign(filters, { warehouseId: null, productId: null, businessType: '', page: 1 }); load() }
function movement(row) {
  if (row.quantity == null) return '—'
  const quantity = String(row.quantity).replace(/^[+-]/, '')
  return row.direction === 'IN' ? `+${quantity}` : row.direction === 'OUT' ? `−${quantity}` : quantity
}
onMounted(() => { load(); searchWarehouses() })
</script>
<template>
  <div class="page"><div class="page-head"><div><h2>库存流水</h2><p class="page-subtitle">按业务单据追溯库存变动与操作记录</p></div></div>
    <div class="filter-bar business-filters"><el-select v-model="filters.warehouseId" filterable remote :remote-method="searchWarehouses" clearable placeholder="全部仓库"><el-option v-for="w in warehouses" :key="w.id" :value="w.id" :label="w.warehouseName" /></el-select><el-input-number v-model="filters.productId" :min="1" :precision="0" placeholder="物资 ID" /><el-select v-model="filters.businessType" clearable placeholder="全部业务类型"><el-option value="OPENING_BALANCE" label="期初库存" /><el-option value="REQUISITION" label="领用发放" /></el-select><div class="filter-actions"><el-button type="primary" :loading="loading" @click="filters.page = 1; load()">查询流水</el-button><el-button @click="reset">重置</el-button></div></div>
    <el-alert v-if="error" type="error" :title="error" :closable="false" show-icon />
    <div class="result-heading"><strong>变动记录</strong><span v-if="!loading && !error">共 {{ total }} 条流水</span><span v-else-if="loading">正在查询流水…</span></div>
    <el-table v-loading="loading" :data="rows" :empty-text="error ? '查询失败，请重试' : '当前条件下暂无库存流水'"><el-table-column prop="operatedAt" label="操作时间" min-width="170" show-overflow-tooltip /><el-table-column prop="businessNo" label="业务单号" min-width="210" show-overflow-tooltip><template #default="{ row }"><span class="record-code">{{ row.businessNo || '—' }}</span></template></el-table-column><el-table-column label="业务类型" min-width="115"><template #default="{ row }">{{ row.businessType === 'OPENING_BALANCE' ? '期初库存' : row.businessType === 'REQUISITION' ? '领用发放' : row.businessType }}</template></el-table-column><el-table-column prop="productName" label="物资" min-width="160" show-overflow-tooltip><template #default="{ row }"><span class="record-name">{{ row.productName }}</span></template></el-table-column><el-table-column prop="warehouseName" label="仓库" min-width="130" show-overflow-tooltip /><el-table-column label="方向" width="82"><template #default="{ row }"><el-tag size="small" effect="plain" :type="row.direction === 'IN' ? 'success' : row.direction === 'OUT' ? 'danger' : 'info'">{{ row.direction === 'IN' ? '增加' : row.direction === 'OUT' ? '减少' : row.direction }}</el-tag></template></el-table-column><el-table-column label="变化数量" min-width="110" align="right"><template #default="{ row }"><span class="change-value" :class="{ 'is-in': row.direction === 'IN', 'is-out': row.direction === 'OUT' }">{{ movement(row) }}</span></template></el-table-column><el-table-column prop="beforeQuantity" label="变更前" min-width="90" align="right"><template #default="{ row }"><span class="number-cell">{{ row.beforeQuantity }}</span></template></el-table-column><el-table-column prop="afterQuantity" label="变更后" min-width="90" align="right"><template #default="{ row }"><span class="number-cell">{{ row.afterQuantity }}</span></template></el-table-column><el-table-column prop="operatorName" label="操作人" min-width="100" show-overflow-tooltip /><el-table-column prop="requestId" label="请求标识" min-width="190" show-overflow-tooltip /></el-table>
    <el-pagination v-model:current-page="filters.page" :page-size="filters.size" :total="total" layout="total, prev, pager, next" @current-change="load" />
  </div>
</template>

<style scoped>
.page-subtitle { margin: 7px 0 0; color: var(--text-secondary); font-size: 13px; }
.filter-bar .el-input, .filter-bar .el-select { width: 188px; }
</style>
