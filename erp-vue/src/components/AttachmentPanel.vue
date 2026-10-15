<script setup>
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, downloadAttachment } from '../lib/api.js'
import { hasPermission } from '../lib/auth.js'

const props = defineProps({ businessType: String, businessId: Number })
const rows = ref([]), busy = ref(false), error = ref(''), input = ref(null)
const limit = 20 * 1024 * 1024
async function load() {
  if (!props.businessType || !props.businessId || !hasPermission('attachment.view')) return
  busy.value = true; error.value = ''
  try { rows.value = await api.attachments(props.businessType, props.businessId) }
  catch (e) { error.value = e.message; rows.value = [] }
  finally { busy.value = false }
}
watch(() => [props.businessType, props.businessId], load, { immediate: true })
async function upload(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  if (file.size === 0 || file.size > limit) { ElMessage.error('文件须非空且不超过 20 MiB'); return }
  busy.value = true
  try {
    await api.uploadAttachment(props.businessType, props.businessId, file, crypto.randomUUID())
    ElMessage.success('上传完成'); await load()
  } catch (e) { ElMessage.error(e.message) } finally { busy.value = false }
}
async function download(row, preview = false) {
  try {
    const blob = await downloadAttachment(row.id, preview)
    const url = URL.createObjectURL(blob)
    if (preview && row.contentType.startsWith('image/')) window.open(url, '_blank', 'noopener,noreferrer')
    else { const link = document.createElement('a'); link.href = url; link.download = row.displayName; link.click() }
    setTimeout(() => URL.revokeObjectURL(url), 60000)
  } catch (e) { ElMessage.error(e.message) }
}
async function remove(row) {
  try {
    await ElMessageBox.confirm(`删除附件“${row.displayName}”？`, '确认删除', { type: 'warning' })
    await api.deleteAttachment(row.id); ElMessage.success('删除已受理'); await load()
  } catch (e) { if (e !== 'cancel') ElMessage.error(e.message) }
}
</script>
<template>
  <section v-if="businessId && hasPermission('attachment.view')" class="attachment-panel">
    <div class="attachment-heading"><strong>业务附件</strong><div>
      <el-button v-if="hasPermission('attachment.upload')" size="small" :disabled="busy || rows.length >= 20" @click="input?.click()">上传文件</el-button>
      <input ref="input" type="file" hidden accept=".pdf,.png,.jpg,.jpeg,.webp,.docx,.xlsx,.txt" @change="upload" />
      <el-button size="small" :loading="busy" @click="load">刷新</el-button>
    </div></div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-empty v-else-if="!busy && !rows.length" description="暂无附件" :image-size="70" />
    <el-table v-else v-loading="busy" :data="rows" size="small">
      <el-table-column prop="displayName" label="文件名" min-width="180" />
      <el-table-column label="大小" width="110"><template #default="{ row }">{{ (row.sizeBytes / 1024).toFixed(1) }} KiB</template></el-table-column>
      <el-table-column label="状态" width="110"><template #default="{ row }">{{ row.status === 'DELETE_PENDING' ? '删除待重试' : '可用' }}</template></el-table-column>
      <el-table-column prop="createdAt" label="上传时间" width="180" />
      <el-table-column label="操作" width="190"><template #default="{ row }">
        <el-button v-if="row.status === 'READY'" link @click="download(row)">下载</el-button>
        <el-button v-if="row.status === 'READY' && row.contentType.startsWith('image/')" link @click="download(row, true)">预览</el-button>
        <el-button v-if="hasPermission('attachment.delete')" link type="danger" @click="remove(row)">{{ row.status === 'DELETE_PENDING' ? '重试删除' : '删除' }}</el-button>
      </template></el-table-column>
    </el-table>
  </section>
</template>
<style scoped>
.attachment-panel { margin-top: 24px; padding-top: 16px; border-top: 1px solid var(--el-border-color-lighter); }
.attachment-heading { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
</style>
