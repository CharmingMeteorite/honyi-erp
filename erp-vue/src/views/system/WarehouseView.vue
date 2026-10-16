<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../../lib/api.js'
import { authState, hasPermission } from '../../lib/auth.js'
const loading = ref(false), saving = ref(false), visible = ref(false), editingId = ref(null)
const rows = ref([]), total = ref(0), departments = ref([])
const filters = reactive({ keyword: '', status: null, page: 1, size: 20 })
const form = reactive({ warehouseCode: '', warehouseName: '', departmentId: null, remark: '' })
async function load() {
  loading.value = true
  try { const data = await api.warehouses(filters); rows.value = data.list; total.value = data.total }
  catch (error) { ElMessage.error(error.message) } finally { loading.value = false }
}
async function open(row) {
  editingId.value = row?.id || null
  Object.assign(form, row ? { warehouseCode: row.warehouseCode, warehouseName: row.warehouseName, departmentId: row.departmentId, remark: row.remark } : { warehouseCode: '', warehouseName: '', departmentId: authState.user?.department?.id || null, remark: '' })
  try { departments.value = await api.warehouseDepartments(); visible.value = true } catch (error) { ElMessage.error(error.message) }
}
async function save() {
  if (!form.warehouseCode.trim() || !form.warehouseName.trim()) return ElMessage.warning('请填写仓库编码和名称')
  saving.value = true
  try {
    if (editingId.value) await api.updateWarehouse(editingId.value, form)
    else await api.createWarehouse(form)
    visible.value = false; ElMessage.success('仓库已保存'); await load()
  } catch (error) { ElMessage.error(error.message) } finally { saving.value = false }
}
async function changeStatus(row) {
  try {
    await ElMessageBox.confirm(row.status ? '停用后该仓库不能创建或完成期初库存，确认停用？' : '确认启用该仓库？', '仓库状态')
    await api.warehouseStatus(row.id, !row.status); ElMessage.success('状态已更新'); await load()
  } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(error.message) }
}
onMounted(load)
</script>
<template>
  <div class="page">
    <div class="page-head"><div><h2>仓库管理</h2><p class="page-subtitle">维护仓库编码、所属部门与启用状态</p></div><el-button v-if="hasPermission('system.warehouse.create') && authState.user?.dataScope !== 'SELF'" type="primary" @click="open()">新增仓库</el-button></div>
    <div class="filter-bar"><el-input v-model="filters.keyword" placeholder="编码 / 名称" clearable @keyup.enter="filters.page = 1; load()" /><el-select v-model="filters.status" placeholder="全部状态" clearable><el-option label="启用" :value="true" /><el-option label="停用" :value="false" /></el-select><el-button @click="filters.page = 1; load()">查询</el-button><el-button @click="Object.assign(filters, { keyword: '', status: null, page: 1 }); load()">重置</el-button></div>
    <el-table v-loading="loading" :data="rows"><el-table-column prop="warehouseCode" label="编码" /><el-table-column prop="warehouseName" label="名称" /><el-table-column label="所属部门"><template #default="{ row }">{{ row.departmentName || '全局' }}</template></el-table-column><el-table-column label="状态"><template #default="{ row }"><el-tag :type="row.status ? 'success' : 'info'">{{ row.status ? '启用' : '停用' }}</el-tag></template></el-table-column><el-table-column prop="remark" label="备注" show-overflow-tooltip /><el-table-column label="操作"><template #default="{ row }"><el-button v-if="hasPermission('system.warehouse.update')" link @click="open(row)">编辑</el-button><el-button v-if="hasPermission('system.warehouse.disable')" link @click="changeStatus(row)">{{ row.status ? '停用' : '启用' }}</el-button></template></el-table-column></el-table>
    <el-pagination v-model:current-page="filters.page" :page-size="filters.size" :total="total" layout="total, prev, pager, next" @current-change="load" />
    <el-dialog v-model="visible" :title="editingId ? '编辑仓库' : '新增仓库'" width="520px">
      <el-form label-width="100px"><el-form-item label="编码" required><el-input v-model="form.warehouseCode" maxlength="64" /></el-form-item><el-form-item label="名称" required><el-input v-model="form.warehouseName" maxlength="100" /></el-form-item><el-form-item label="所属部门"><el-select v-model="form.departmentId" :disabled="!!editingId" clearable filterable placeholder="全局仓库可留空"><el-option v-for="d in departments" :key="d.id" :value="d.id" :label="d.departmentName" /></el-select></el-form-item><el-alert title="所属部门用于数据范围控制，创建后固定。部门范围用户只能创建本部门及下级部门仓库。" :closable="false" /><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" maxlength="500" /></el-form-item></el-form>
      <template #footer><el-button @click="visible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-subtitle { margin: 7px 0 0; color: var(--text-secondary); font-size: 13px; }
.filter-bar .el-input, .filter-bar .el-select { width: 220px; }
.el-pagination { margin-top: 16px; }
</style>
