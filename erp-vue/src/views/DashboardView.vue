<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, CircleCheck, RefreshRight, Search } from '@element-plus/icons-vue'
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
const metricDescriptions = {
  products: '当前可见的物资档案', activeProducts: '状态为启用的物资',
  devices: '当前范围内的设备', activeWarehouses: '状态为启用的仓库',
  stockPositions: '正库存的仓库物资组合', stockProducts: '有正库存的不同物资',
  stockWarehouses: '有正库存的不同仓库',
}
const reportPermissions = [
  'inventory.stock.view', 'inventory.inbound.view', 'inventory.outbound.view',
  'apply.requisition.view', 'system.device.view', 'inventory.stocktake.view',
  'asset.repair.view', 'asset.warranty.view', 'asset.exchange.view',
]
const deviceLabels = {
  IN_STOCK: '在库', IN_USE: '使用中', BORROWED: '借用中', REPAIRING: '维修中',
  PENDING_EXCHANGE: '待换货', SHIPPED: '已出库', SCRAPPED: '已报废', MISSING: '盘亏',
}
const metrics = computed(() => Object.entries(summary.value?.metrics || {}).map(([key, value]) => ({
  key, value: value == null || !Number.isFinite(Number(value)) ? null : value,
  label: metricDefinitions[key]?.[0] || key,
  route: metricDefinitions[key]?.[1], group: metricDefinitions[key]?.[2] || 'other',
  description: metricDescriptions[key] || '查看对应业务列表',
})))
const availableRiskMetrics = computed(() => metrics.value.filter(item => item.group === 'risk' || item.group === 'pending'))
const riskMetrics = computed(() => availableRiskMetrics.value.filter(item => item.value !== null && Number(item.value) > 0))
const riskDataIncomplete = computed(() => availableRiskMetrics.value.some(item => item.value === null))
const overviewMetrics = computed(() => metrics.value.filter(item => item.group === 'overview'))
const deviceStatuses = computed(() => Object.entries(summary.value?.deviceStatuses || {})
  .filter(([, value]) => Number(value) > 0).map(([key, value]) => ({ key, label: deviceLabels[key] || key, value: Number(value) })))
const deviceTotal = computed(() => Number(summary.value?.metrics?.devices || 0))
const recent = computed(() => Array.isArray(summary.value?.recent) ? summary.value.recent : null)
const quickLinks = computed(() => [
  ['库存查询', '/inventory/stocks', 'inventory.stock.view'],
  ['入库单', '/inventory/inbounds', 'inventory.inbound.view'],
  ['领用申请', '/apply/requisitions', 'apply.requisition.view'],
  ['设备档案', '/assets/devices', 'system.device.view'],
  ['业务报表', '/reports', null],
].filter(([, , permission]) => permission ? hasPermission(permission) : reportPermissions.some(hasPermission)))
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
  <div v-if="error" class="dashboard-error" role="alert">
    <div><strong>概览数据暂时无法加载</strong><p>{{ error }}</p></div>
    <el-button :icon="RefreshRight" @click="load">重试</el-button>
  </div>
  <div v-loading="loading && !!summary" class="dashboard">
    <template v-if="summary">
      <div class="dashboard-top">
        <section class="dashboard-priority">
          <div class="section-heading"><div><span class="section-kicker">待关注事项</span><h2>业务待办与库存风险</h2></div><small>当前可查看范围</small></div>
          <div v-if="riskMetrics.length" class="priority-grid">
            <button v-for="item in riskMetrics" :key="item.key" type="button" class="priority-item" :class="item.group" @click="router.push(item.route)">
              <span>{{ item.label }}</span><strong>{{ item.value }}</strong><el-icon><ArrowRight /></el-icon>
            </button>
          </div>
          <div v-else-if="availableRiskMetrics.length && !riskDataIncomplete" class="all-clear"><el-icon><CircleCheck /></el-icon><div><strong>暂无待关注事项</strong><span>可见范围内的待办与库存风险指标均为零。</span></div></div>
          <p v-else class="compact-empty">{{ riskDataIncomplete ? '部分待办或风险指标暂不可用。' : '当前权限范围内没有可查看的待办或风险指标。' }}</p>
          <p v-if="riskMetrics.length && riskDataIncomplete" class="metric-note">部分待办或风险指标暂不可用。</p>
        </section>
        <section class="dashboard-shortcuts">
          <div class="section-heading"><div><span class="section-kicker">常用入口</span><h2>快速进入业务</h2></div></div>
          <div class="shortcut-grid">
            <button v-for="[label, path] in quickLinks" :key="path" type="button" class="shortcut" @click="router.push(path)">
              <span>{{ label }}</span><el-icon><ArrowRight /></el-icon>
            </button>
          </div>
          <p v-if="!quickLinks.length" class="muted">当前没有可用的快捷入口。</p>
        </section>
      </div>
      <section class="dashboard-section overview-section">
        <div class="section-heading"><div><span class="section-kicker">业务规模</span><h2>基础业务概况</h2></div><small>按当前权限统计 · 不合并不同单位的库存数量</small></div>
        <div v-if="overviewMetrics.length" class="overview-grid">
          <button v-for="item in overviewMetrics" :key="item.key" type="button" class="overview-item" @click="router.push(item.route)">
            <span class="overview-label">{{ item.label }}</span><strong>{{ item.value ?? '—' }}</strong><span class="overview-foot">{{ item.value === null ? '数据暂不可用' : item.description }}<el-icon><ArrowRight /></el-icon></span>
          </button>
        </div>
        <p v-else class="compact-empty">当前权限范围内没有可显示的基础指标。</p>
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
          <el-table v-if="recent?.length" :data="recent">
            <el-table-column label="类型" width="100"><template #default="{ row }"><el-tag effect="plain" type="info">{{ queryTypeNames[row.type] || row.type }}</el-tag></template></el-table-column>
            <el-table-column prop="title" label="业务编号 / 名称" min-width="190" show-overflow-tooltip />
            <el-table-column prop="subtitle" label="相关信息" min-width="170" show-overflow-tooltip />
            <el-table-column label="状态" width="120"><template #default="{ row }"><el-tag size="small" effect="plain" :type="statusTagType(row.status)">{{ queryStatusName(row.type, row.status) }}</el-tag></template></el-table-column>
            <el-table-column prop="createdAt" label="时间" min-width="170" />
            <el-table-column label="操作" width="80"><template #default="{ row }"><el-button link type="primary" @click="open(row)">查看</el-button></template></el-table-column>
          </el-table>
          <p v-else class="compact-empty">{{ recent === null ? '近期记录暂不可用，请刷新后重试。' : '当前权限范围内暂无业务动态。' }}</p>
        </section>
      </div>
    </template>
    <div v-else-if="loading" class="dashboard-loading"><el-skeleton :rows="3" animated /></div>
  </div>
</template>

<style scoped>
.dashboard-heading { align-items: center; flex-wrap: wrap; }
.heading-actions { display: flex; gap: 8px; }
.dashboard { display: grid; gap: 14px; }
.dashboard-top { display: grid; grid-template-columns: minmax(0, 1fr) clamp(262px, 32%, 380px); gap: 14px; align-items: start; }
.dashboard-priority, .dashboard-shortcuts, .dashboard-section { min-width: 0; padding: 17px 18px; border: 1px solid var(--line); border-radius: var(--radius); background: var(--surface); box-shadow: var(--shadow); }
.section-kicker { display: block; margin-bottom: 5px; color: var(--brand); font-size: 11px; font-weight: 750; letter-spacing: .06em; }
.section-heading { display: flex; align-items: end; justify-content: space-between; gap: 12px; margin-bottom: 13px; }
h2 { margin: 0; font-size: 16px; font-weight: 720; letter-spacing: -.02em; }
.section-heading small, .metric-note { margin: 0; color: var(--text-muted); font-size: 12px; }
.priority-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(145px, 1fr)); gap: 8px; }
.all-clear { display: flex; align-items: center; gap: 10px; padding: 10px 0 2px; }
.all-clear > .el-icon { flex: none; color: var(--success); font-size: 19px; }
.all-clear div { display: flex; flex-wrap: wrap; align-items: baseline; gap: 4px 10px; min-width: 0; }
.all-clear strong { color: var(--text); font-size: 13px; }.all-clear span { color: var(--text-secondary); font-size: 12px; }
.priority-item, .overview-item, .shortcut { font: inherit; cursor: pointer; }
.priority-item { position: relative; min-height: 76px; display: flex; flex-direction: column; justify-content: space-between; align-items: start; padding: 11px 13px; text-align: left; background: var(--surface-subtle); border: 1px solid var(--line); border-radius: 8px; color: var(--text-secondary); }
.priority-item:hover, .overview-item:hover, .shortcut:hover { border-color: #a6c1ff; background: #f6f9ff; }
.priority-item span { font-size: 12px; }
.priority-item strong { color: var(--text); font-size: 24px; line-height: 1; font-variant-numeric: tabular-nums; }
.priority-item.risk strong { color: var(--danger); }
.priority-item .el-icon { position: absolute; right: 12px; bottom: 12px; color: #9aa8bb; }
.shortcut-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 7px; }
.shortcut { display: flex; align-items: center; justify-content: space-between; gap: 5px; width: 100%; min-height: 32px; padding: 5px 9px; border: 1px solid var(--line); border-radius: 6px; color: var(--text-secondary); background: var(--surface); text-align: left; font-size: 12px; }
.shortcut .el-icon { flex: none; color: var(--brand); font-size: 12px; }
.overview-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 8px; }
.overview-item { min-width: 0; min-height: 84px; display: grid; align-content: space-between; gap: 3px; padding: 11px 12px; border: 1px solid var(--line); border-radius: 7px; background: var(--surface); text-align: left; }
.overview-label { color: var(--text-secondary); font-size: 12px; font-weight: 600; }
.overview-item strong { color: var(--text); font-size: 24px; line-height: 1; font-weight: 730; font-variant-numeric: tabular-nums; }
.overview-foot { display: flex; align-items: center; justify-content: space-between; gap: 6px; min-width: 0; color: var(--text-muted); font-size: 11px; }
.overview-foot .el-icon { flex: none; color: var(--brand); }
.dashboard-bottom { display: grid; grid-template-columns: minmax(250px, 1fr) minmax(0, 2fr); gap: 14px; align-items: start; }
.dashboard-bottom:has(.activity-section:only-child) { grid-template-columns: 1fr; }
.status-row + .status-row { margin-top: 11px; }
.status-row-heading { display: flex; justify-content: space-between; font-size: 12px; color: var(--text-secondary); margin-bottom: 6px; }
.status-row-heading strong { color: var(--text); font-variant-numeric: tabular-nums; }
.status-track { height: 6px; overflow: hidden; border-radius: 5px; background: #edf1f6; }
.status-track span { display: block; height: 100%; border-radius: 5px; background: var(--brand); }
.metric-note { margin-top: 13px; line-height: 1.5; }
.activity-section { overflow: hidden; }
.activity-section :deep(.el-table) { border: 0; }
.activity-section :deep(.el-table__cell) { padding-block: 8px; }
.compact-empty { margin: 0; padding: 13px 0 4px; color: var(--text-muted); font-size: 12px; text-align: center; }
.dashboard-loading { min-height: 180px; padding: 18px; border: 1px solid var(--line); border-radius: var(--radius); background: var(--surface); }
.dashboard-error { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 14px 18px; border: 1px solid #f1d9dd; border-radius: var(--radius); background: #fff8f8; }
.dashboard-error strong { color: var(--danger); font-size: 13px; }
.dashboard-error p { margin: 4px 0 0; color: var(--text-secondary); font-size: 12px; }
@media (max-width: 1120px) { .dashboard-bottom { grid-template-columns: 1fr; } }
@media (min-width: 1700px) { .overview-grid { grid-template-columns: repeat(7, minmax(0, 1fr)); } }
@media (max-width: 1150px) { .overview-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@media (max-width: 1100px) { .shortcut-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 700px) { .overview-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } .section-heading { align-items: start; flex-wrap: wrap; } }
@media (max-width: 700px) { .dashboard-top { grid-template-columns: 1fr; } }
@media (max-width: 600px) { .dashboard-priority, .dashboard-shortcuts, .dashboard-section { padding: 15px; } .dashboard-error { align-items: start; flex-direction: column; } }
</style>
