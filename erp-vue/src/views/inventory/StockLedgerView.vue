<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../../lib/api.js'
const filters = reactive({ warehouseId: null, productId: null, businessType: '', page: 1, size: 20 })
const rows = ref([]), total = ref(0), warehouses = ref([]), loading = ref(false)
async function searchWarehouses(keyword = '') { try { warehouses.value = (await api.inventoryWarehouses({ keyword, size: 100 })).list } catch (e) { ElMessage.error(e.message) } }
async function load() {
  loading.value = true
  try { const data = await api.stockLedgers(filters); rows.value = data.list; total.value = data.total }
  catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}
onMounted(() => { load(); searchWarehouses() })
</script>
<template>
  <div class="page"><div class="page-head"><div><h2>库存流水</h2><p class="page-subtitle">按业务单据追溯库存变动与操作记录</p></div></div>
    <div class="filter-bar"><el-select v-model="filters.warehouseId" filterable remote :remote-method="searchWarehouses" clearable placeholder="全部仓库"><el-option v-for="w in warehouses" :key="w.id" :value="w.id" :label="w.warehouseName" /></el-select><el-input-number v-model="filters.productId" :min="1" :precision="0" placeholder="物资 ID" /><el-select v-model="filters.businessType" clearable placeholder="全部业务类型"><el-option value="OPENING_BALANCE" label="期初库存" /><el-option value="REQUISITION" label="领用发放" /></el-select><el-button @click="filters.page = 1; load()">查询</el-button><el-button @click="Object.assign(filters, { warehouseId: null, productId: null, businessType: '', page: 1 }); load()">重置</el-button></div>
    <el-table v-loading="loading" :data="rows"><el-table-column prop="warehouseName" label="仓库" min-width="100" /><el-table-column prop="productName" label="物资" min-width="120" /><el-table-column prop="businessNo" label="业务单号" min-width="250" /><el-table-column label="业务类型"><template #default="{ row }">{{ row.businessType === 'OPENING_BALANCE' ? '期初库存' : row.businessType === 'REQUISITION' ? '领用发放' : row.businessType }}</template></el-table-column><el-table-column label="方向"><template #default="{ row }">{{ row.direction === 'IN' ? '增加' : '减少' }}</template></el-table-column><el-table-column prop="quantity" label="变化数量" /><el-table-column prop="beforeQuantity" label="变更前" /><el-table-column prop="afterQuantity" label="变更后" /><el-table-column prop="operatorName" label="操作人" /><el-table-column prop="operatedAt" label="操作时间" min-width="180" /><el-table-column prop="requestId" label="请求标识" min-width="230" /></el-table>
    <el-pagination v-model:current-page="filters.page" :page-size="filters.size" :total="total" layout="total, prev, pager, next" @current-change="load" />
  </div>
</template>

<style scoped>
.page-subtitle { margin: 7px 0 0; color: var(--text-secondary); font-size: 13px; }
.filter-bar .el-input, .filter-bar .el-select { width: 220px; }
.el-pagination { margin-top: 16px; }
</style>
