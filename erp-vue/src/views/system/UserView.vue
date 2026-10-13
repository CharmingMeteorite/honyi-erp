<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../../lib/api.js'
import { hasPermission } from '../../lib/auth.js'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const roles = ref([])
const departments = ref([])
const dialog = ref(false)
const detailDialog = ref(false)
const detail = ref(null)
const editing = ref(null)
const filters = reactive({ keyword: '', status: undefined, page: 1, size: 20 })
const form = reactive({ username: '', realName: '', employeeNo: '', password: '', phone: '', email: '', departmentId: null, roleIds: [], status: true })

function flatten(items, result = []) { items.forEach(item => { result.push(item); flatten(item.children || [], result) }); return result }
function resetForm() { Object.assign(form, { username: '', realName: '', employeeNo: '', password: '', phone: '', email: '', departmentId: null, roleIds: [], status: true }) }
async function load() { loading.value = true; try { const data = await api.users(filters); rows.value = data.list || []; total.value = data.total || 0 } catch (e) { ElMessage.error(e.message) } finally { loading.value = false } }
async function loadOptions() {
  if (!roles.value.length && hasPermission('system.user.view')) {
    try { roles.value = await api.roleOptions() } catch (e) { ElMessage.error(e.message) }
  }
  if (!departments.value.length && hasPermission('system.department.view')) {
    try { departments.value = flatten(await api.departments()) } catch (e) { ElMessage.error(e.message) }
  }
}
async function openCreate() { editing.value = null; resetForm(); await loadOptions(); dialog.value = true }
async function openEdit(row) { editing.value = row.id; await loadOptions(); Object.assign(form, { username: row.username, realName: row.realName, employeeNo: row.employeeNo, password: '', phone: row.phone, email: row.email, departmentId: row.departmentId, roleIds: (row.roles || []).map(x => x.id), status: row.status }); dialog.value = true }
async function openDetail(row) { try { detail.value = await api.user(row.id); detailDialog.value = true } catch (e) { ElMessage.error(e.message) } }
async function save() { try { if (editing.value) await api.updateUser(editing.value, form); else await api.createUser(form); dialog.value = false; ElMessage.success('保存成功'); await load() } catch (e) { ElMessage.error(e.message) } }
async function toggle(row) { try { await ElMessageBox.confirm(`确认${row.status ? '停用' : '启用'}该用户吗？`); await api.userStatus(row.id, !row.status); await load() } catch (e) { if (e !== 'cancel') ElMessage.error(e.message) } }
async function resetPassword(row) { try { const { value } = await ElMessageBox.prompt('请输入新密码（至少 8 位）', '重置密码', { inputType: 'password', inputPattern: /.{8,}/, inputErrorMessage: '密码至少 8 位' }); await api.resetPassword(row.id, { newPassword: value }); ElMessage.success('密码已重置') } catch (e) { if (e !== 'cancel') ElMessage.error(e.message) } }
onMounted(load)
</script>

<template>
  <div class="page-heading"><div><h1>用户管理</h1><p class="muted">管理用户账号、部门和角色。</p></div><el-button v-if="hasPermission('system.user.create')" type="primary" @click="openCreate">新增用户</el-button></div>
  <el-card>
    <el-form inline><el-form-item><el-input v-model="filters.keyword" placeholder="用户名、姓名或工号" clearable @keyup.enter="filters.page = 1; load()" /></el-form-item><el-form-item><el-select v-model="filters.status" placeholder="状态" clearable><el-option label="启用" :value="true" /><el-option label="停用" :value="false" /></el-select></el-form-item><el-button @click="filters.page = 1; load()">查询</el-button></el-form>
    <el-table v-loading="loading" :data="rows" row-key="id"><el-table-column prop="employeeNo" label="工号" /><el-table-column prop="username" label="用户名" /><el-table-column prop="realName" label="姓名" /><el-table-column prop="departmentName" label="部门" /><el-table-column label="角色"><template #default="{ row }">{{ (row.roles || []).map(x => x.roleName).join('、') }}</template></el-table-column><el-table-column prop="phone" label="手机号" /><el-table-column label="状态"><template #default="{ row }"><el-tag :type="row.status ? 'success' : 'info'">{{ row.status ? '启用' : '停用' }}</el-tag></template></el-table-column><el-table-column label="操作" width="340"><template #default="{ row }"><el-button link @click="openDetail(row)">详情</el-button><el-button v-if="hasPermission('system.user.update')" link type="primary" @click="openEdit(row)">编辑</el-button><el-button v-if="hasPermission('system.user.reset-password')" link @click="resetPassword(row)">重置密码</el-button><el-button v-if="hasPermission('system.user.disable')" link @click="toggle(row)">{{ row.status ? '停用' : '启用' }}</el-button></template></el-table-column></el-table>
    <el-pagination v-model:current-page="filters.page" v-model:page-size="filters.size" :total="total" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="filters.page = 1; load()" />
  </el-card>
  <el-dialog v-model="dialog" :title="editing ? '编辑用户' : '新增用户'" width="560px"><el-form label-width="90px"><el-form-item label="用户名" required><el-input v-model="form.username" :disabled="!!editing" /></el-form-item><el-form-item label="姓名" required><el-input v-model="form.realName" /></el-form-item><el-form-item label="工号"><el-input v-model="form.employeeNo" /></el-form-item><el-form-item v-if="!editing" label="密码" required><el-input v-model="form.password" type="password" show-password /></el-form-item><el-form-item label="手机号"><el-input v-model="form.phone" /></el-form-item><el-form-item label="邮箱"><el-input v-model="form.email" /></el-form-item><el-form-item label="部门"><el-select v-model="form.departmentId" clearable style="width:100%"><el-option v-for="d in departments" :key="d.id" :label="d.departmentName" :value="d.id" /></el-select></el-form-item><el-form-item label="角色" required><el-select v-model="form.roleIds" multiple style="width:100%"><el-option v-for="r in roles" :key="r.id" :label="r.roleName" :value="r.id" /></el-select></el-form-item><el-form-item label="状态"><el-switch v-model="form.status" /></el-form-item></el-form><template #footer><el-button @click="dialog = false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template></el-dialog>
  <el-dialog v-model="detailDialog" title="用户详情" width="520px"><el-descriptions v-if="detail" :column="1" border><el-descriptions-item label="用户名">{{ detail.username }}</el-descriptions-item><el-descriptions-item label="姓名">{{ detail.realName }}</el-descriptions-item><el-descriptions-item label="工号">{{ detail.employeeNo || '-' }}</el-descriptions-item><el-descriptions-item label="部门">{{ detail.departmentName || '-' }}</el-descriptions-item><el-descriptions-item label="角色">{{ (detail.roles || []).map(x => x.roleName).join('、') || '-' }}</el-descriptions-item><el-descriptions-item label="权限摘要"><el-space wrap><el-tag v-for="permission in detail.permissions || []" :key="permission" size="small">{{ permission }}</el-tag><span v-if="!(detail.permissions || []).length">-</span></el-space></el-descriptions-item><el-descriptions-item label="数据范围">{{ detail.dataScope || '-' }}</el-descriptions-item><el-descriptions-item label="状态">{{ detail.status ? '启用' : '停用' }}</el-descriptions-item><el-descriptions-item label="最后登录">{{ detail.lastLoginAt || '-' }}</el-descriptions-item><el-descriptions-item label="创建时间">{{ detail.createdAt || '-' }}</el-descriptions-item></el-descriptions></el-dialog>
</template>
