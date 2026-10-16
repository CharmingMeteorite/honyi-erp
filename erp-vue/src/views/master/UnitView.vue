<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../../lib/api.js'
import { hasPermission } from '../../lib/auth.js'

const rows = ref([]), loading = ref(false), saving = ref(false)
const dialog = ref(false), editing = ref(null)
const form = reactive({ unitCode: '', unitName: '', precisionScale: 0, status: true })
async function load() {
  loading.value = true
  try { rows.value = await api.units() }
  catch (e) { ElMessage.error(e.message) }
  finally { loading.value = false }
}
function open(row = null) {
  editing.value = row?.id || null
  Object.assign(form, row
    ? { unitCode: row.unitCode, unitName: row.unitName, precisionScale: row.precisionScale, status: row.status }
    : { unitCode: '', unitName: '', precisionScale: 0, status: true })
  dialog.value = true
}
async function save() {
  if (!form.unitCode?.trim() || !form.unitName?.trim()) { ElMessage.warning('请填写单位编码和名称'); return }
  saving.value = true
  try {
    if (editing.value) await api.updateUnit(editing.value, form)
    else await api.createUnit(form)
    dialog.value = false; ElMessage.success('单位已保存'); await load()
  } catch (e) { ElMessage.error(e.message) }
  finally { saving.value = false }
}
async function toggle(row) {
  try {
    await ElMessageBox.confirm(`确认${row.status ? '停用' : '启用'}单位“${row.unitName}”吗？`, '单位状态', { type: 'warning' })
    await api.unitStatus(row.id, !row.status); ElMessage.success('状态已更新'); await load()
  } catch (e) { if (e !== 'cancel' && e !== 'close') ElMessage.error(e.message) }
}
onMounted(load)
</script>
<template>
  <div class="page-heading"><div><h1>计量单位</h1><p>管理物资数量的单位和小数精度。</p></div><el-button v-if="hasPermission('system.unit.create')" type="primary" @click="open()">新增单位</el-button></div>
  <el-card>
    <div class="list-caption"><strong>单位列表</strong><span>共 {{ rows.length }} 项</span></div>
    <el-table v-loading="loading" :data="rows" empty-text="暂无计量单位">
      <el-table-column prop="unitCode" label="单位编码" min-width="150" show-overflow-tooltip />
      <el-table-column prop="unitName" label="单位名称" min-width="180" show-overflow-tooltip />
      <el-table-column prop="precisionScale" label="小数精度" width="120" align="right" />
      <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="row.status ? 'success' : 'info'">{{ row.status ? '启用' : '停用' }}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="150"><template #default="{ row }"><el-button v-if="hasPermission('system.unit.update')" link type="primary" @click="open(row)">编辑</el-button><el-button v-if="hasPermission('system.unit.disable')" link @click="toggle(row)">{{ row.status ? '停用' : '启用' }}</el-button></template></el-table-column>
    </el-table>
  </el-card>
  <el-dialog v-model="dialog" :title="editing ? '编辑单位' : '新增单位'" width="520px">
    <el-form label-width="90px">
      <el-form-item label="单位编码" required><el-input v-model="form.unitCode" maxlength="64" /></el-form-item>
      <el-form-item label="单位名称" required><el-input v-model="form.unitName" maxlength="100" /></el-form-item>
      <el-form-item label="小数精度"><el-input-number v-model="form.precisionScale" :min="0" :max="8" /></el-form-item>
      <el-form-item label="状态"><el-switch v-model="form.status" active-text="启用" inactive-text="停用" /></el-form-item>
    </el-form>
    <template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
  </el-dialog>
</template>
<style scoped>
.list-caption { display: flex; align-items: baseline; gap: 12px; margin-bottom: 14px; }
.list-caption span { color: var(--text-muted); font-size: 12px; }
</style>
