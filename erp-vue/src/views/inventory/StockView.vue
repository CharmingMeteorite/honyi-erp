<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../../lib/api.js'
const filters = reactive({ warehouseId: null, keyword: '', page: 1, size: 20 })
const rows = ref([]), total = ref(0), warehouses = ref([]), loading = ref(false), checking = ref(false), checked = ref(false), issues = ref([])
async function searchWarehouses(keyword = '') { try { warehouses.value = (await api.inventoryWarehouses({ keyword, size: 100 })).list } catch (e) { ElMessage.error(e.message) } }
async function load() {
  loading.value = true
  try { const data = await api.stocks(filters); rows.value = data.list; total.value = data.total }
  catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}
async function check() {
  checking.value = true
  try { issues.value = await api.stockConsistency({ warehouseId: filters.warehouseId }); checked.value = true }
  catch (e) { ElMessage.error(e.message) } finally { checking.value = false }
}
onMounted(() => { load(); searchWarehouses() })
</script>
<template>
  <div class="page">
    <div class="page-head"><h2>库存查询</h2><el-button :loading="checking" @click="check">检查库存一致性</el-button></div>
    <el-alert title="库存按仓库和物资汇总，可用库存 = 当前数量 − 锁定数量。期初单完成后产生库存及流水。" :closable="false" />
    <div class="filter-bar"><el-select v-model="filters.warehouseId" filterable remote :remote-method="searchWarehouses" clearable placeholder="全部仓库"><el-option v-for="w in warehouses" :key="w.id" :value="w.id" :label="w.warehouseName" /></el-select><el-input v-model="filters.keyword" placeholder="物资编码 / 名称" clearable @keyup.enter="filters.page = 1; load()" /><el-button @click="filters.page = 1; load()">查询</el-button><el-button @click="Object.assign(filters, { warehouseId: null, keyword: '', page: 1 }); checked = false; load()">重置</el-button></div>
    <el-table v-loading="loading" :data="rows"><el-table-column prop="warehouseName" label="仓库" /><el-table-column prop="productCode" label="物资编码" /><el-table-column prop="productName" label="物资" /><el-table-column label="管理方式"><template #default="{ row }">{{ row.productType === 'DEVICE' ? '设备' : '数量' }}</template></el-table-column><el-table-column prop="quantity" label="当前数量" /><el-table-column prop="lockedQuantity" label="锁定数量" /><el-table-column prop="availableQuantity" label="可用数量" /></el-table>
    <el-pagination v-model:current-page="filters.page" :page-size="filters.size" :total="total" layout="total, prev, pager, next" @current-change="load" />
    <template v-if="checked"><h3>一致性检查结果</h3><el-alert :type="issues.length ? 'warning' : 'success'" :title="issues.length ? `发现 ${issues.length} 项问题，请人工核查。系统未修改任何数据。` : '当前可见范围内未发现库存一致性问题'" :closable="false" /><el-table v-if="issues.length" :data="issues"><el-table-column prop="warehouseId" label="仓库 ID" /><el-table-column prop="productId" label="物资 ID" /><el-table-column prop="message" label="问题" /><el-table-column prop="stockQuantity" label="账面数量" /><el-table-column prop="deviceQuantity" label="设备数量" /></el-table></template>
  </div>
</template>

<style scoped>
.page-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.page-head h2 { margin: 0; }
.filter-bar { display: flex; flex-wrap: wrap; gap: 12px; margin: 16px 0; }
.filter-bar .el-input, .filter-bar .el-select { width: 220px; }
.el-pagination { margin-top: 16px; }
</style>
