<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../../lib/api.js'
import { hasPermission } from '../../lib/auth.js'

const rows = ref([]), flat = ref([]), loading = ref(false), saving = ref(false)
const dialog = ref(false), editing = ref(null)
const form = reactive({ parentId: null, categoryCode: '', categoryName: '', sortOrder: 0, status: true })
function flatten(items, result = []) { items.forEach(item => { result.push(item); flatten(item.children || [], result) }); return result }
async function load() {
  loading.value = true
  try { rows.value = await api.categories(); flat.value = flatten(rows.value) }
  catch (e) { ElMessage.error(e.message) }
  finally { loading.value = false }
}
function open(row = null) {
  editing.value = row?.id || null
  Object.assign(form, row
    ? { parentId: row.parentId, categoryCode: row.categoryCode, categoryName: row.categoryName, sortOrder: row.sortOrder, status: row.status }
    : { parentId: null, categoryCode: '', categoryName: '', sortOrder: 0, status: true })
  dialog.value = true
}
async function save() {
  if (!form.categoryCode?.trim() || !form.categoryName?.trim()) { ElMessage.warning('请填写分类编码和名称'); return }
  saving.value = true
  try {
    if (editing.value) await api.updateCategory(editing.value, form)
    else await api.createCategory(form)
    dialog.value = false; ElMessage.success('分类已保存'); await load()
  } catch (e) { ElMessage.error(e.message) }
  finally { saving.value = false }
}
async function toggle(row) {
  try {
    await ElMessageBox.confirm(`确认${row.status ? '停用' : '启用'}分类“${row.categoryName}”吗？`, '分类状态', { type: 'warning' })
    await api.categoryStatus(row.id, !row.status); ElMessage.success('状态已更新'); await load()
  } catch (e) { if (e !== 'cancel' && e !== 'close') ElMessage.error(e.message) }
}
onMounted(load)
</script>
<template>
  <div class="page-heading"><div><h1>物资分类</h1><p>按层级维护分类编码、名称与排序。</p></div><el-button v-if="hasPermission('system.category.create')" type="primary" @click="open()">新增分类</el-button></div>
  <el-card>
    <div class="list-caption"><strong>分类结构</strong><span>共 {{ flat.length }} 项，可展开查看下级分类</span></div>
    <el-table v-loading="loading" :data="rows" row-key="id" default-expand-all empty-text="暂无分类">
      <el-table-column prop="categoryName" label="分类名称" min-width="220" show-overflow-tooltip />
      <el-table-column prop="categoryCode" label="分类编码" min-width="160" show-overflow-tooltip />
      <el-table-column prop="sortOrder" label="排序" width="90" align="right" />
      <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="row.status ? 'success' : 'info'">{{ row.status ? '启用' : '停用' }}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="150"><template #default="{ row }"><el-button v-if="hasPermission('system.category.update')" link type="primary" @click="open(row)">编辑</el-button><el-button v-if="hasPermission('system.category.disable')" link @click="toggle(row)">{{ row.status ? '停用' : '启用' }}</el-button></template></el-table-column>
    </el-table>
  </el-card>
  <el-dialog v-model="dialog" :title="editing ? '编辑分类' : '新增分类'" width="520px">
    <el-form label-width="100px">
      <el-form-item label="上级分类"><el-select v-model="form.parentId" clearable filterable placeholder="顶级分类"><el-option v-for="item in flat.filter(item => item.id !== editing)" :key="item.id" :label="item.categoryName" :value="item.id" /></el-select></el-form-item>
      <el-form-item label="分类编码" required><el-input v-model="form.categoryCode" maxlength="64" /></el-form-item>
      <el-form-item label="分类名称" required><el-input v-model="form.categoryName" maxlength="100" /></el-form-item>
      <el-form-item label="排序"><el-input-number v-model="form.sortOrder" :min="0" /></el-form-item>
      <el-form-item label="状态"><el-switch v-model="form.status" active-text="启用" inactive-text="停用" /></el-form-item>
    </el-form>
    <template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
  </el-dialog>
</template>
<style scoped>
.list-caption { display: flex; align-items: baseline; gap: 12px; margin-bottom: 14px; }
.list-caption span { color: var(--text-muted); font-size: 12px; }
.el-form .el-select { width: 100%; }
</style>
