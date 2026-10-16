<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../../lib/api.js'
import { hasPermission } from '../../lib/auth.js'

const rows = ref([]), loading = ref(false), saving = ref(false)
const dialog = ref(false), editing = ref(null)
const form = reactive({ brandCode: '', brandName: '', remark: '', status: true })
async function load() {
  loading.value = true
  try { rows.value = await api.brands() }
  catch (e) { ElMessage.error(e.message) }
  finally { loading.value = false }
}
function open(row = null) {
  editing.value = row?.id || null
  Object.assign(form, row
    ? { brandCode: row.brandCode, brandName: row.brandName, remark: row.remark, status: row.status }
    : { brandCode: '', brandName: '', remark: '', status: true })
  dialog.value = true
}
async function save() {
  if (!form.brandCode?.trim() || !form.brandName?.trim()) { ElMessage.warning('请填写品牌编码和名称'); return }
  saving.value = true
  try {
    if (editing.value) await api.updateBrand(editing.value, form)
    else await api.createBrand(form)
    dialog.value = false; ElMessage.success('品牌已保存'); await load()
  } catch (e) { ElMessage.error(e.message) }
  finally { saving.value = false }
}
async function toggle(row) {
  try {
    await ElMessageBox.confirm(`确认${row.status ? '停用' : '启用'}品牌“${row.brandName}”吗？`, '品牌状态', { type: 'warning' })
    await api.brandStatus(row.id, !row.status); ElMessage.success('状态已更新'); await load()
  } catch (e) { if (e !== 'cancel' && e !== 'close') ElMessage.error(e.message) }
}
onMounted(load)
</script>
<template>
  <div class="page-heading"><div><h1>品牌管理</h1><p>维护物资品牌编码、名称和启用状态。</p></div><el-button v-if="hasPermission('system.brand.create')" type="primary" @click="open()">新增品牌</el-button></div>
  <el-card>
    <div class="list-caption"><strong>品牌列表</strong><span>共 {{ rows.length }} 项</span></div>
    <el-table v-loading="loading" :data="rows" empty-text="暂无品牌">
      <el-table-column prop="brandCode" label="品牌编码" min-width="150" show-overflow-tooltip />
      <el-table-column prop="brandName" label="品牌名称" min-width="180" show-overflow-tooltip />
      <el-table-column prop="remark" label="备注" min-width="200" show-overflow-tooltip />
      <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="row.status ? 'success' : 'info'">{{ row.status ? '启用' : '停用' }}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="150"><template #default="{ row }"><el-button v-if="hasPermission('system.brand.update')" link type="primary" @click="open(row)">编辑</el-button><el-button v-if="hasPermission('system.brand.disable')" link @click="toggle(row)">{{ row.status ? '停用' : '启用' }}</el-button></template></el-table-column>
    </el-table>
  </el-card>
  <el-dialog v-model="dialog" :title="editing ? '编辑品牌' : '新增品牌'" width="520px">
    <el-form label-width="90px">
      <el-form-item label="品牌编码" required><el-input v-model="form.brandCode" maxlength="64" /></el-form-item>
      <el-form-item label="品牌名称" required><el-input v-model="form.brandName" maxlength="100" /></el-form-item>
      <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="3" /></el-form-item>
      <el-form-item label="状态"><el-switch v-model="form.status" active-text="启用" inactive-text="停用" /></el-form-item>
    </el-form>
    <template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
  </el-dialog>
</template>
<style scoped>
.list-caption { display: flex; align-items: baseline; gap: 12px; margin-bottom: 14px; }
.list-caption span { color: var(--text-muted); font-size: 12px; }
</style>
