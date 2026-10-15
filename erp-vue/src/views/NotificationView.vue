<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api, request } from '../lib/api.js'
import { hasPermission } from '../lib/auth.js'

const router = useRouter()
const rows = ref([]), total = ref(0), unread = ref(0), loading = ref(false), error = ref('')
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
  try { await api.readAllNotifications(); await load() } catch (e) { ElMessage.error(e.message) }
}
onMounted(load)
</script>
<template>
  <div class="page-heading"><h1>站内通知</h1><el-button :disabled="!unread" @click="readAll">全部标记已读</el-button></div>
  <el-card v-loading="loading">
    <p>未读 {{ unread }} 条</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-empty v-else-if="!rows.length" description="暂无通知" />
    <el-table v-else :data="rows" row-key="id">
      <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="row.readAt ? 'info' : 'primary'">{{ row.readAt ? '已读' : '未读' }}</el-tag></template></el-table-column>
      <el-table-column prop="title" label="标题" min-width="150" />
      <el-table-column prop="summary" label="摘要" min-width="220" />
      <el-table-column prop="createdAt" label="时间" width="190" />
      <el-table-column label="操作" width="130"><template #default="{ row }"><el-button link @click="read(row)">查看业务</el-button></template></el-table-column>
    </el-table>
    <el-pagination v-model:current-page="page.page" v-model:page-size="page.size" :total="total" @current-change="load" />
  </el-card>
</template>
