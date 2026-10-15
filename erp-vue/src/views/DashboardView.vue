<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { RefreshRight, Search } from '@element-plus/icons-vue'
import { api } from '../lib/api.js'
import { authState } from '../lib/auth.js'
import { queryTypeNames, queryStatusName } from '../lib/query.js'

const router = useRouter()
const loading = ref(false)
const error = ref('')
const summary = ref(null)
const labels = {
  products: ['物资总数', '/master-data/products'], activeProducts: ['启用物资', '/master-data/products'],
  devices: ['设备总数', '/assets/devices'], activeWarehouses: ['启用仓库', '/system/warehouses'],
  stockPositions: ['有库存的仓库物资', '/inventory/stocks'], lowStockProducts: ['低于安全库存', '/inventory/stocks'],
  stockProducts: ['有库存的物资', '/inventory/stocks'], stockWarehouses: ['有库存的仓库', '/inventory/stocks'],
  pendingInbounds: ['待审批入库', '/inventory/inbounds'], pendingOutbounds: ['待审批出库', '/inventory/outbounds'],
  pendingRequisitions: ['待审批领用', '/apply/requisitions'], pendingStocktakes: ['待确认盘点', '/inventory/stocktakes'],
  activeRepairs: ['维修处理中', '/assets/repairs'], activeWarranties: ['保修处理中', '/assets/warranties'],
  pendingExchanges: ['待处理换货', '/assets/exchanges'],
}
const states = { IN_STOCK: '在库', IN_USE: '使用中', BORROWED: '借用中', REPAIRING: '维修中', PENDING_EXCHANGE: '待换货', SHIPPED: '已出库', SCRAPPED: '已报废', MISSING: '盘亏' }
const metrics = computed(() => Object.entries(summary.value?.metrics || {}).map(([key, value]) => ({ key, value, label: labels[key]?.[0], route: labels[key]?.[1] })))
async function load() {
  loading.value = true
  error.value = ''
  try { summary.value = await api.dashboardSummary() }
  catch (e) { summary.value = null; error.value = e.message }
  finally { loading.value = false }
}
function open(row) { if (row.route) router.push({ path: row.route, query: ['PRODUCT', 'DEVICE'].includes(row.type) ? { id: row.id } : {} }) }
onMounted(load)
</script>

<template>
  <div class="page-heading dashboard-heading">
    <div><h1>首页</h1><p class="muted">{{ authState.user?.realName || authState.user?.username }}，当前可见业务概况</p></div>
    <div class="heading-actions"><el-button :icon="Search" @click="router.push('/query')">综合查询</el-button><el-button :icon="RefreshRight" :loading="loading" title="刷新" @click="load" /></div>
  </div>
  <el-alert v-if="error" :title="error" type="error" show-icon class="dashboard-error" />
  <div v-loading="loading">
    <div v-if="metrics.length" class="dashboard-metrics">
      <button v-for="item in metrics" :key="item.key" class="dashboard-metric" type="button" @click="router.push(item.route)">
        <span>{{ item.label }}</span><strong>{{ item.value }}</strong>
      </button>
    </div>
    <el-empty v-else-if="!loading && !error" description="暂无可查看的业务数据" />
    <div v-if="summary" class="dashboard-sections">
      <section v-if="Object.keys(summary.deviceStatuses || {}).length" class="dashboard-section">
        <h2>设备状态</h2>
        <div class="state-grid"><div v-for="(value, key) in summary.deviceStatuses" :key="key" class="state-item"><span>{{ states[key] || key }}</span><strong>{{ value }}</strong></div></div>
      </section>
      <section v-if="summary.stockQuantity != null" class="dashboard-section">
        <h2>库存数量概览</h2><div class="stock-total">{{ summary.stockQuantity }}</div>
        <p class="stock-note">按各物资原单位合计；不同单位不能直接比较，明细请查看库存查询。</p>
      </section>
      <section class="dashboard-section dashboard-activity">
        <div class="section-heading"><h2>近期业务动态</h2><el-button link @click="router.push('/query')">查看全部</el-button></div>
        <el-table :data="summary.recent || []" empty-text="暂无动态" size="small">
          <el-table-column label="类型" width="100"><template #default="{ row }">{{ queryTypeNames[row.type] || row.type }}</template></el-table-column><el-table-column prop="title" label="单号" min-width="220" />
          <el-table-column prop="subtitle" label="相关信息" min-width="170" show-overflow-tooltip />
          <el-table-column label="状态" width="110"><template #default="{ row }">{{ queryStatusName(row.type, row.status) }}</template></el-table-column><el-table-column prop="createdAt" label="时间" min-width="170" />
          <el-table-column label="入口" width="80"><template #default="{ row }"><el-button link @click="open(row)">查看</el-button></template></el-table-column>
        </el-table>
      </section>
    </div>
  </div>
</template>

<style scoped>
.dashboard-heading { align-items: center; gap: 16px; flex-wrap: wrap; }
.dashboard-heading .muted { margin: 0; color: #64748b; }
.heading-actions { display: flex; gap: 8px; }
.dashboard-error { margin-bottom: 20px; }
.dashboard-metrics { display: grid; grid-template-columns: repeat(auto-fill, minmax(170px, 1fr)); gap: 12px; }
.dashboard-metric { min-height: 96px; padding: 16px; text-align: left; background: #fff; border: 1px solid #e5eaf0; border-radius: 6px; cursor: pointer; display: flex; flex-direction: column; justify-content: space-between; color: #475569; font: inherit; }
.dashboard-metric:hover { border-color: #3b82f6; }
.dashboard-metric strong { font-size: 26px; color: #1d4ed8; font-weight: 650; }
.dashboard-sections { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 26px; margin-top: 28px; }
.dashboard-section { min-width: 0; }
.dashboard-section h2 { font-size: 17px; margin: 0 0 14px; }
.dashboard-activity { grid-column: 1 / -1; }
.state-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(130px, 1fr)); gap: 8px; }
.state-item { display: flex; justify-content: space-between; padding: 10px 12px; background: #f5f8fc; border-radius: 4px; gap: 12px; }
.stock-total { font-size: 26px; font-weight: 650; color: #155e75; }
.stock-note { color: #64748b; font-size: 13px; line-height: 1.6; }
.section-heading { display: flex; justify-content: space-between; align-items: baseline; }
@media (max-width: 760px) { .dashboard-sections { grid-template-columns: 1fr; } .dashboard-activity { grid-column: auto; } }
</style>
