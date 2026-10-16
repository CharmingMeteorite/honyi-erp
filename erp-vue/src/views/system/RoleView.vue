<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../../lib/api.js'
import { hasPermission } from '../../lib/auth.js'
const rows = ref([]); const permissions = ref([]); const loading = ref(false); const dialog = ref(false); const permissionDialog = ref(false); const editing = ref(null); const checked = ref([])
const form = reactive({ roleCode: '', roleName: '', description: '', dataScope: 'SELF', status: true })
const empty = () => ({ roleCode: '', roleName: '', description: '', dataScope: 'SELF', status: true })
async function load() { loading.value = true; try { rows.value = await api.roles(); permissions.value = await api.permissionTree() } catch (e) { ElMessage.error(e.message) } finally { loading.value = false } }
function open(row = null) { editing.value = row?.id || null; Object.assign(form, row ? { roleCode: row.roleCode, roleName: row.roleName, description: row.description, dataScope: row.dataScope, status: row.status } : empty()); dialog.value = true }
async function save() { try { if (editing.value) await api.updateRole(editing.value, form); else await api.createRole(form); dialog.value = false; ElMessage.success('保存成功'); await load() } catch (e) { ElMessage.error(e.message) } }
async function toggle(row) { try { await ElMessageBox.confirm(`确认${row.status ? '停用' : '启用'}该角色吗？`); await api.roleStatus(row.id, !row.status); await load() } catch (e) { if (e !== 'cancel') ElMessage.error(e.message) } }
async function remove(row) { try { await ElMessageBox.confirm('删除后无法恢复，确认删除该角色吗？'); await api.deleteRole(row.id); ElMessage.success('角色已删除'); await load() } catch (e) { if (e !== 'cancel') ElMessage.error(e.message) } }
function openPermissions(row) { editing.value = row.id; checked.value = row.permissionIds || []; permissionDialog.value = true }
async function savePermissions() { try { await api.rolePermissions(editing.value, { permissionIds: checked.value }); permissionDialog.value = false; ElMessage.success('权限已更新'); await load() } catch (e) { ElMessage.error(e.message) } }
onMounted(load)
</script>
<template>
<div class="page-heading"><div><h1>角色管理</h1><p class="muted">管理角色权限和 ALL / DEPARTMENT / SELF 数据范围。</p></div><el-button v-if="hasPermission('system.role.create')" type="primary" @click="open()">新增角色</el-button></div>
<el-card>
  <div class="list-caption"><strong>角色列表</strong><span>共 {{ rows.length }} 个角色</span></div>
  <el-table v-loading="loading" :data="rows" empty-text="暂无角色">
    <el-table-column prop="roleName" label="角色名称" min-width="150" show-overflow-tooltip />
    <el-table-column prop="roleCode" label="角色编码" min-width="150" show-overflow-tooltip />
    <el-table-column prop="description" label="描述" min-width="190" show-overflow-tooltip />
    <el-table-column label="数据范围" min-width="140"><template #default="{ row }"><el-tag type="info" effect="plain">{{ { ALL: '全部数据', DEPARTMENT: '本部门及下级', SELF: '本人数据' }[row.dataScope] || row.dataScope }}</el-tag></template></el-table-column>
    <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="row.status ? 'success' : 'info'">{{ row.status ? '启用' : '停用' }}</el-tag></template></el-table-column>
    <el-table-column label="操作" width="300" fixed="right"><template #default="{ row }"><el-button v-if="hasPermission('system.role.update')" link @click="open(row)">编辑</el-button><el-button v-if="hasPermission('system.role.permissions')" link type="primary" @click="openPermissions(row)">配置权限</el-button><el-button v-if="hasPermission('system.role.disable')" link @click="toggle(row)">{{ row.status ? '停用' : '启用' }}</el-button><el-button v-if="hasPermission('system.role.delete')" link type="danger" @click="remove(row)">删除</el-button></template></el-table-column>
  </el-table>
</el-card>
<el-dialog v-model="dialog" :title="editing ? '编辑角色' : '新增角色'" width="480px"><el-form label-width="90px"><el-form-item label="编码" required><el-input v-model="form.roleCode" :disabled="!!editing" /></el-form-item><el-form-item label="名称" required><el-input v-model="form.roleName" /></el-form-item><el-form-item label="描述"><el-input v-model="form.description" /></el-form-item><el-form-item label="数据范围"><el-select v-model="form.dataScope"><el-option label="全部" value="ALL" /><el-option label="部门" value="DEPARTMENT" /><el-option label="本人" value="SELF" /></el-select></el-form-item><el-form-item label="状态"><el-switch v-model="form.status" /></el-form-item></el-form><template #footer><el-button @click="dialog = false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template></el-dialog>
<el-dialog v-model="permissionDialog" title="配置角色权限" width="500px"><el-tree :data="permissions" node-key="id" show-checkbox default-expand-all :default-checked-keys="checked" @check="(_, state) => checked = state.checkedKeys" /><template #footer><el-button @click="permissionDialog = false">取消</el-button><el-button type="primary" @click="savePermissions">保存</el-button></template></el-dialog>
</template>
<style scoped>
.list-caption { display: flex; align-items: baseline; gap: 12px; margin-bottom: 14px; }
.list-caption span { color: var(--text-muted); font-size: 12px; }
.el-dialog .el-select { width: 100%; }
</style>
