<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, RefreshRight, Search } from '@element-plus/icons-vue'
import { api } from '../lib/api.js'
import { authState, hasPermission } from '../lib/auth.js'
import { queryTypeNames, queryStatusName } from '../lib/query.js'
import { statusTagType } from '../lib/status.js'

const router = useRouter()
const loading = ref(false)
const error = ref('')
const summary = ref(null)
const metricDefinitions = {
  lowStockProducts: ['低于安全库存', '/inventory/stocks', 'risk'],
  pendingInbounds: ['待审批入库', '/inventory/inbounds', 'pending'],
  pendingOutbounds: ['待审批出库', '/inventory/outbounds', 'pending'],
  pendingRequisitions: ['待审批领用', '/apply/requisitions', 'pending'],
  pendingStocktakes: ['待确认盘点', '/inventory/stocktakes', 'pending'],
  activeRepairs: ['维修处理中', '/assets/repairs', 'pending'],
  activeWarranties: ['保修处理中', '/assets/warranties', 'pending'],
  pendingExchanges: ['待处理换货', '/assets/exchanges', 'pending'],
  products: ['物资档案', '/master-data/products', 'overview'],
  activeProducts: ['启用物资', '/master-data/products', 'overview'],
  devices: ['设备档案', '/assets/devices', 'overview'],
  activeWarehouses: ['启用仓库', '/system/warehouses', 'overview'],
  stockPositions: ['有库存的仓库物资', '/inventory/stocks', 'overview'],
  stockProducts: ['有库存的物资', '/inventory/stocks', 'overview'],
  stockWarehouses: ['有库存的仓库', '/inventory/stocks', 'overview'],
}
const deviceLabels = {
  IN_STOCK: '在库', IN_USE: '使用中', BORROWED: '借用中', REPAIRING: '维修中',
  PENDING_EXCHANGE: '待换货', SHIPPED: '已出库', SCRAPPED: '已报废', MISSING: '盘亏',
}
const metrics = computed(() => Object.entries(summary.value?.metrics || {}).map(([key, value]) => ({
  key, value, label: metricDefinitions[key]?.[0] || key,
  route: metricDefinitions[key]?.[1], group: metricDefinitions[key]?.[2] || 'overview',
})))
const riskMetrics = computed(() => metrics.value.filter(item => (item.group === 'risk' || item.group === 'pending') && Number(item.value) > 0))
const overviewMetrics = computed(() => metrics.value.filter(item => item.group === 'overview'))
const deviceStatuses = computed(() => Object.entries(summary.value?.deviceStatuses || {})
  .filter(([, value]) => Number(value) > 0).map(([key, value]) => ({ key, label: deviceLabels[key] || key, value: Number(value) })))
const deviceTotal = computed(() => Number(summary.value?.metrics?.devices || 0))
const quickLinks = computed(() => [
  ['库存查询', '/inventory/stocks', 'inventory.stock.view'],
  ['入库单', '/inventory/inbounds', 'inventory.inbound.view'],
  ['领用申请', '/apply/requisitions', 'apply.requisition.view'],
  ['设备档案', '/assets/devices', 'system.device.view'],
  ['业务报表', '/reports', null],
].filter(([, , permission]) => permission ? hasPermission(permission) : metrics.value.length > 0))
async function load() {
  loading.value = true; error.value = ''
  try { summary.value = await api.dashboardSummary() }
  catch (e) { summary.value = null; error.value = e.message }
  finally { loading.value = false }
}
function open(row) {
  if (row.route) router.push({ path: row.route, query: ['PRODUCT', 'DEVICE'].includes(row.type) ? { id: row.id } : {} })
}
onMounted(load)
</script>

<template>
  <div class="page-heading dashboard-heading">
    <div><h1>运营概览</h1><p>{{ authState.user?.realName || authState.user?.username }}，以下数据来自当前权限范围内的实时业务记录。</p></div>
    <div class="heading-actions"><el-button :icon="Search" @click="router.push('/query')">综合查询</el-button><el-button :icon="RefreshRight" :loading="loading" @click="load">刷新</el-button></div>
  </div>
  <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
  <div v-loading="loading" class="dashboard">
    <template v-if="summary">
      <div class="dashboard-top">
        <section class="dashboard-priority">
          <div class="section-kicker">待关注事项</div>
          <div class="section-heading"><div><h2>业务待办与库存风险</h2><p>按当前可查看的数据范围统计</p></div></div>
          <div v-if="riskMetrics.length" class="priority-grid">
            <button v-for="item in riskMetrics" :key="item.key" type="button" class="priority-item" :class="item.group" @click="router.push(item.route)">
              <span>{{ item.label }}</span><strong>{{ item.value }}</strong><el-icon><ArrowRight /></el-icon>
            </button>
          </div>
          <div v-else class="all-clear"><strong>暂无待关注事项</strong><span>当前账号可见的待办与库存风险指标没有非零项。</span></div>
        </section>
        <section class="dashboard-shortcuts">
          <div class="section-kicker">常用入口</div>
          <h2>快速进入业务</h2>
          <button v-for="[label, path] in quickLinks" :key="path" type="button" class="shortcut" @click="router.push(path)">
            <span>{{ label }}</span><el-icon><ArrowRight /></el-icon>
          </button>
          <p v-if="!quickLinks.length" class="muted">当前没有可用的快捷入口。</p>
        </section>
      </div>
      <section v-if="overviewMetrics.length" class="dashboard-section">
        <div class="section-heading"><div><span class="section-kicker">业务规模</span><h2>基础业务概况</h2></div><small>不同计量单位的库存数量不合并计算</small></div>
        <div class="overview-grid">
          <button v-for="item in overviewMetrics" :key="item.key" type="button" class="overview-item" @click="router.push(item.route)">
            <span>{{ item.label }}</span><strong>{{ item.value }}</strong><el-icon><ArrowRight /></el-icon>
          </button>
        </div>
      </section>
      <div class="dashboard-bottom">
        <section v-if="deviceTotal && deviceStatuses.length" class="dashboard-section status-section">
          <div class="section-heading"><div><span class="section-kicker">设备结构</span><h2>设备状态分布</h2></div><small>设备总数 {{ deviceTotal }}</small></div>
          <div v-for="item in deviceStatuses" :key="item.key" class="status-row">
            <div class="status-row-heading"><span>{{ item.label }}</span><strong>{{ item.value }}</strong></div>
            <div class="status-track"><span :style="{ width: Math.min(100, item.value / deviceTotal * 100) + '%' }" /></div>
          </div>
          <p class="metric-note">条形长度按各状态设备数与当前可见设备总数计算。</p>
        </section>
        <section class="dashboard-section activity-section">
          <div class="section-heading"><div><span class="section-kicker">最近记录</span><h2>近期业务动态</h2></div><el-button link @click="router.push('/query')">综合查询 <el-icon class="el-icon--right"><ArrowRight /></el-icon></el-button></div>
          <el-table :data="summary.recent || []" empty-text="当前权限范围内暂无动态">
            <el-table-column label="类型" width="100"><template #default="{ row }"><el-tag effect="plain" type="info">{{ queryTypeNames[row.type] || row.type }}</el-tag></template></el-table-column>
            <el-table-column prop="title" label="业务编号 / 名称" min-width="190" show-overflow-tooltip />
            <el-table-column prop="subtitle" label="相关信息" min-width="170" show-overflow-tooltip />
            <el-table-column label="状态" width="120"><template #default="{ row }"><el-tag size="small" effect="plain" :type="statusTagType(row.status)">{{ queryStatusName(row.type, row.status) }}</el-tag></template></el-table-column>
            <el-table-column prop="createdAt" label="时间" min-width="170" />
            <el-table-column label="操作" width="80"><template #default="{ row }"><el-button link type="primary" @click="open(row)">查看</el-button></template></el-table-column>
          </el-table>
        </section>
      </div>
    </template>
    <el-empty v-else-if="!loading && !error" description="暂无可查看的业务数据" />
  </div>
</template>

<style scoped>
.dashboard-heading { align-items: center; flex-wrap: wrap; }
.heading-actions { display: flex; gap: 8px; }
.dashboard { min-height: 280px; display: grid; gap: 22px; }
.dashboard-top { display: grid; grid-template-columns: minmax(0, 1fr) 260px; gap: 18px; }
.dashboard-priority, .dashboard-shortcuts, .dashboard-section { min-width: 0; padding: 22px; border: 1px solid var(--line); border-radius: var(--radius); background: var(--surface); box-shadow: var(--shadow); }
.dashboard-priority { border-top: 3px solid var(--brand); }
.section-kicker { display: block; margin-bottom: 7px; color: var(--brand); font-size: 11px; font-weight: 750; letter-spacing: .09em; }
.section-heading { display: flex; align-items: start; justify-content: space-between; gap: 12px; margin-bottom: 18px; }
h2 { margin: 0; font-size: 17px; font-weight: 720; letter-spacing: -.02em; }
.section-heading p, .section-heading small, .metric-note { margin: 6px 0 0; color: var(--text-muted); font-size: 12px; }
.priority-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(145px, 1fr)); gap: 10px; }
.all-clear { display: grid; gap: 7px; min-height: 98px; align-content: center; padding: 16px; border: 1px solid #d7e9df; border-radius: 8px; background: #f6fbf8; }
.all-clear strong { color: var(--success); font-size: 14px; }.all-clear span { color: var(--text-secondary); font-size: 12px; }
.priority-item, .overview-item, .shortcut { font: inherit; cursor: pointer; }
.priority-item { position: relative; min-height: 100px; display: flex; flex-direction: column; justify-content: space-between; align-items: start; padding: 14px; text-align: left; background: var(--surface-subtle); border: 1px solid var(--line); border-radius: 8px; color: var(--text-secondary); }
.priority-item:hover, .overview-item:hover, .shortcut:hover { border-color: #a6c1ff; background: #f6f9ff; }
.priority-item span { font-size: 12px; }
.priority-item strong { color: var(--text); font-size: 29px; line-height: 1; font-variant-numeric: tabular-nums; }
.priority-item.risk strong { color: var(--danger); }
.priority-item .el-icon { position: absolute; right: 13px; bottom: 15px; color: #9aa8bb; }
.dashboard-shortcuts h2 { margin-bottom: 14px; }
.shortcut { display: flex; align-items: center; justify-content: space-between; width: 100%; padding: 11px 2px; border: 0; border-bottom: 1px solid var(--line); color: var(--text-secondary); background: transparent; text-align: left; font-size: 13px; }
.shortcut:last-child { border-bottom: 0; }
.shortcut .el-icon { color: var(--brand); }
.overview-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(145px, 1fr)); }
.overview-item { position: relative; min-height: 81px; display: grid; align-content: space-between; padding: 6px 18px; border: 0; border-right: 1px solid var(--line); background: transparent; text-align: left; }
.overview-item:first-child { padding-left: 0; }
.overview-item:last-child { border-right: 0; }
.overview-item span { color: var(--text-muted); font-size: 12px; }
.overview-item strong { font-size: 25px; font-weight: 700; font-variant-numeric: tabular-nums; }
.overview-item .el-icon { position: absolute; right: 15px; bottom: 10px; color: #a5afbd; }
.dashboard-bottom { display: grid; grid-template-columns: minmax(250px, 1fr) minmax(0, 2fr); gap: 18px; align-items: start; }
.dashboard-bottom:has(.activity-section:only-child) { grid-template-columns: 1fr; }
.status-row + .status-row { margin-top: 14px; }
.status-row-heading { display: flex; justify-content: space-between; font-size: 12px; color: var(--text-secondary); margin-bottom: 6px; }
.status-row-heading strong { color: var(--text); font-variant-numeric: tabular-nums; }
.status-track { height: 6px; overflow: hidden; border-radius: 5px; background: #edf1f6; }
.status-track span { display: block; height: 100%; border-radius: 5px; background: var(--brand); }
.metric-note { margin-top: 18px; line-height: 1.5; }
.activity-section { overflow: hidden; }
.activity-section :deep(.el-table) { border: 0; }
@media (max-width: 1120px) { .dashboard-bottom { grid-template-columns: 1fr; } }
@media (max-width: 850px) { .dashboard-top { grid-template-columns: 1fr; } .dashboard-shortcuts { display: none; } }
@media (max-width: 600px) { .dashboard-priority, .dashboard-section { padding: 16px; } .overview-grid { grid-template-columns: repeat(2, 1fr); gap: 12px; } .overview-item { border: 1px solid var(--line); border-radius: 7px; padding: 12px; } .overview-item:first-child { padding-left: 12px; } }
</style>
