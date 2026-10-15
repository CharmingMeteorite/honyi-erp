<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Search, RefreshRight } from '@element-plus/icons-vue'
import { api } from '../lib/api.js'
import { hasPermission } from '../lib/auth.js'
import { queryTypeNames, queryStatusName } from '../lib/query.js'

const router = useRouter()
const types = [
  ['PRODUCT', '物资', 'system.product.view'], ['DEVICE', '设备', 'system.device.view'],
  ['WAREHOUSE', '仓库', 'system.warehouse.view'], ['INBOUND', '入库', 'inventory.inbound.view'],
  ['OUTBOUND', '出库', 'inventory.outbound.view'], ['REQUISITION', '领用', 'apply.requisition.view'],
  ['STOCKTAKE', '盘点', 'inventory.stocktake.view'], ['REPAIR', '维修', 'asset.repair.view'],
  ['WARRANTY', '保修', 'asset.warranty.view'], ['EXCHANGE', '换货', 'asset.exchange.view'],
]
const availableTypes = computed(() => types.filter(([, , permission]) => hasPermission(permission)))
const statusGroups = {
  PRODUCT: ['ENABLED', 'DISABLED'], WAREHOUSE: ['ENABLED', 'DISABLED'],
  DEVICE: ['IN_STOCK', 'IN_USE', 'BORROWED', 'REPAIRING', 'PENDING_EXCHANGE', 'SHIPPED', 'SCRAPPED', 'MISSING'],
  INBOUND: ['DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'COMPLETED', 'CANCELLED'],
  OUTBOUND: ['DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'COMPLETED', 'CANCELLED'],
  REQUISITION: ['DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'COMPLETED', 'CANCELLED'],
  STOCKTAKE: ['DRAFT', 'COUNTING', 'COUNTED', 'CONFIRMED', 'CANCELLED'],
  REPAIR: ['DRAFT', 'SUBMITTED', 'ACCEPTED', 'REPAIRING', 'COMPLETED', 'UNREPAIRABLE', 'CANCELLED', 'CLOSED'],
  WARRANTY: ['DRAFT', 'SUBMITTED', 'ACCEPTED', 'APPROVED', 'REJECTED', 'COMPLETED', 'CANCELLED'],
  EXCHANGE: ['DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'COMPLETED', 'CANCELLED'],
}
const statusOptions = computed(() => filters.type ? statusGroups[filters.type] : [...new Set(Object.values(statusGroups).flat())])
const filters = reactive({ keyword: '', type: '', status: '', dates: [], page: 1, size: 20 })
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const searched = ref(false)
const error = ref('')
async function search() {
  loading.value = true
  error.value = ''
  try {
    const data = await api.globalQuery({ keyword: filters.keyword, type: filters.type, status: filters.status,
      from: filters.dates?.[0], to: filters.dates?.[1], page: filters.page, size: filters.size })
    rows.value = data.list; total.value = data.total; searched.value = true
  } catch (e) { rows.value = []; total.value = 0; error.value = e.message }
  finally { loading.value = false }
}
function reset() { Object.assign(filters, { keyword: '', type: '', status: '', dates: [], page: 1, size: 20 }); search() }
function open(row) { router.push({ path: row.route, query: ['PRODUCT', 'DEVICE'].includes(row.type) ? { id: row.id } : {} }) }
</script>

<template>
  <div class="page-heading"><h1>综合查询</h1></div>
  <div class="query-controls">
    <el-input v-model="filters.keyword" maxlength="100" clearable placeholder="编号、名称、SN、MAC、单号" class="keyword" @keyup.enter="filters.page=1;search()" />
    <el-select v-model="filters.type" clearable placeholder="全部类型" class="type-select" @change="filters.status=''"><el-option v-for="[value, label] in availableTypes" :key="value" :value="value" :label="label" /></el-select>
    <el-select v-model="filters.status" clearable filterable placeholder="状态" class="status-input"><el-option v-for="state in statusOptions" :key="state" :value="state" :label="queryStatusName(filters.type, state)" /></el-select>
    <el-date-picker v-model="filters.dates" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" class="date-range" />
    <el-button type="primary" :icon="Search" :loading="loading" @click="filters.page=1;search()">查询</el-button>
    <el-button :icon="RefreshRight" @click="reset">重置</el-button>
  </div>
  <el-alert v-if="error" :title="error" type="error" show-icon class="query-error" />
  <el-table v-loading="loading" :data="rows" empty-text="暂无结果" class="results">
    <el-table-column label="类型" width="100"><template #default="{ row }"><el-tag effect="plain">{{ queryTypeNames[row.type] || row.type }}</el-tag></template></el-table-column>
    <el-table-column prop="title" label="编号 / 单号" min-width="220" />
    <el-table-column prop="subtitle" label="信息" min-width="210" show-overflow-tooltip />
    <el-table-column label="状态" width="130"><template #default="{ row }">{{ queryStatusName(row.type, row.status) }}</template></el-table-column>
    <el-table-column prop="createdAt" label="创建时间" width="175" />
    <el-table-column label="入口" width="90"><template #default="{ row }"><el-button link type="primary" @click="open(row)">查看</el-button></template></el-table-column>
  </el-table>
  <el-pagination v-if="searched && total" v-model:current-page="filters.page" v-model:page-size="filters.size" :total="total" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next" class="query-pagination" @current-change="search" @size-change="filters.page=1;search()" />
</template>

<style scoped>
.query-controls { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; margin-bottom: 18px; }
.keyword { width: min(300px, 100%); }.type-select { width: 140px; }.status-input { width: 130px; }.date-range { width: 250px; }
.query-error { margin-bottom: 16px; }.results { width: 100%; }.query-pagination { margin-top: 16px; justify-content: flex-end; }
</style>
