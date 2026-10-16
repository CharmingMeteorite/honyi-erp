<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api, request } from '../lib/api.js'
import { hasPermission } from '../lib/auth.js'

const router = useRouter()
const rows = ref([]), total = ref(0), unread = ref(0), loading = ref(false), marking = ref(false), error = ref('')
const page = reactive({ page: 1, size: 20 })
const targets = {
  PRODUCT: ['system.product.view', '/master-data/products', '/api/master-data/products/'], DEVICE: ['system.device.view', '/assets/devices', '/api/assets/devices/'],
  INBOUND: ['inventory.inbound.view', '/inventory/inbounds', '/api/inventory/inbounds/'], OUTBOUND: ['inventory.outbound.view', '/inventory/outbounds', '/api/inventory/outbounds/'],
  REQUISITION: ['apply.requisition.view', '/apply/requisitions', '/api/apply/requisitions/'], STOCKTAKE: ['inventory.stocktake.view', '/inventory/stocktakes', '/api/inventory/stocktakes/'],
  REPAIR: ['asset.repair.view', '/assets/repairs', '/api/assets/repairs/'], WARRANTY: ['asset.warranty.view', '/assets/warranties', '/api/assets/warranties/'],
  EXCHANGE: ['asset.exchange.view', '/assets/exchanges', '/api/assets/exchanges/'],
}
async function load() {
  loading.value = true; error.value = ''
  try {
    const data = await api.notifications(page)
    rows.value = data.list; total.value = data.total; unread.value = await api.notificationCount()
    window.dispatchEvent(new Event('erp:notifications-changed'))
  } catch (e) { error.value = e.message } finally { loading.value = false }
}
async function read(row) {
  try {
    await api.readNotification(row.id); await load()
    const target = targets[row.businessType]
    if (!target || !hasPermission(target[0])) { ElMessage.warning('当前无权访问关联业务'); return }
    try { await request(`${target[2]}${row.businessId}`, { suppressForbiddenRedirect: true }) }
    catch { ElMessage.warning('关联业务已不存在或当前无权查看'); return }
    router.push({ path: target[1], query: { id: row.businessId } })
  } catch (e) { ElMessage.error(e.message) }
}
async function readAll() {
  marking.value = true
  try { await api.readAllNotifications(); await load(); ElMessage.success('通知已全部标记为已读') }
  catch (e) { ElMessage.error(e.message) }
  finally { marking.value = false }
}
onMounted(load)
</script>
<template>
  <div class="page-heading"><div><h1>站内通知</h1><p>业务状态更新与处理提醒，仅显示当前账号的通知。</p></div><el-button :disabled="!unread || marking" :loading="marking" @click="readAll">全部标记已读</el-button></div>
  <section v-loading="loading" class="notification-panel">
    <div class="notification-overview"><div><strong>消息中心</strong><span>共 {{ total }} 条通知</span></div><el-tag v-if="unread" type="primary" effect="plain">{{ unread }} 条未读</el-tag><span v-else class="muted">全部已读</span></div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-empty v-else-if="!loading && !rows.length" description="暂无通知" />
    <ul v-else class="notification-list">
      <li v-for="row in rows" :key="row.id" class="notification-item" :class="{ unread: !row.readAt }">
        <span class="notification-dot" aria-hidden="true" />
        <div class="notification-copy"><div class="notification-title"><strong>{{ row.title }}</strong><el-tag :type="row.readAt ? 'info' : 'primary'" size="small" effect="plain">{{ row.readAt ? '已读' : '未读' }}</el-tag></div><p>{{ row.summary }}</p><time>{{ row.createdAt }}</time></div>
        <el-button link type="primary" @click="read(row)">查看业务</el-button>
      </li>
    </ul>
    <el-pagination v-if="total" v-model:current-page="page.page" v-model:page-size="page.size" :total="total" layout="total, prev, pager, next" @current-change="load" />
  </section>
</template>
<style scoped>
.notification-panel { min-height: 220px; padding: 0 20px 20px; border: 1px solid var(--line); border-radius: var(--radius); background: #fff; }
.notification-overview { display: flex; align-items: center; justify-content: space-between; gap: 10px; min-height: 65px; border-bottom: 1px solid var(--line); }
.notification-overview > div { display: flex; align-items: baseline; gap: 12px; }
.notification-overview strong { font-size: 15px; }
.notification-overview span { color: var(--text-muted); font-size: 12px; }
.notification-list { margin: 0; padding: 0; list-style: none; }
.notification-item { display: flex; align-items: flex-start; gap: 13px; padding: 18px 4px; border-bottom: 1px solid var(--line); }
.notification-item:last-child { border: 0; }
.notification-item.unread { background: #f8fbff; }
.notification-dot { width: 7px; height: 7px; flex: none; margin-top: 7px; border-radius: 50%; background: #c4cbd6; }
.notification-item.unread .notification-dot { background: var(--brand); }
.notification-copy { min-width: 0; flex: 1; }
.notification-title { display: flex; align-items: center; gap: 8px; }
.notification-title strong { font-size: 14px; }
.notification-copy p { margin: 6px 0; color: var(--text-secondary); line-height: 1.5; overflow-wrap: anywhere; }
.notification-copy time { color: var(--text-muted); font-size: 12px; }
.notification-item .el-button { flex: none; }
@media (max-width: 600px) { .notification-panel { padding-inline: 12px; } .notification-item { flex-wrap: wrap; } .notification-item .el-button { margin-left: 20px; } }
</style>
