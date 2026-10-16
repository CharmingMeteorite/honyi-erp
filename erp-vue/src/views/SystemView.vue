<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { User, OfficeBuilding, Key, Lock, ArrowRight } from '@element-plus/icons-vue'
import { hasPermission } from '../lib/auth.js'
const router = useRouter()
const links = computed(() => [
  { label: '用户管理', description: '账号、部门与角色分配', path: '/system/users', permission: 'system.user.view', icon: User },
  { label: '部门管理', description: '组织结构和负责人', path: '/system/departments', permission: 'system.department.view', icon: OfficeBuilding },
  { label: '角色管理', description: '角色权限与数据范围', path: '/system/roles', permission: 'system.role.view', icon: Key },
  { label: '权限管理', description: '查看菜单和操作权限', path: '/system/permissions', permission: 'system.permission.view', icon: Lock },
].filter(item => hasPermission(item.permission)))
</script>
<template>
  <div class="page-heading"><div><h1>系统管理</h1><p>管理账号、组织和访问权限。可见入口随当前账号权限变化。</p></div></div>
  <div v-if="links.length" class="system-links">
    <button v-for="item in links" :key="item.path" type="button" class="system-link" @click="router.push(item.path)">
      <el-icon :size="21"><component :is="item.icon" /></el-icon>
      <span><strong>{{ item.label }}</strong><small>{{ item.description }}</small></span>
      <el-icon><ArrowRight /></el-icon>
    </button>
  </div>
  <el-empty v-else description="当前账号没有可查看的系统管理模块" />
</template>
<style scoped>
.system-links { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 14px; }
.system-link { display: flex; align-items: center; gap: 14px; min-height: 95px; padding: 18px; border: 1px solid var(--line); border-radius: var(--radius); background: #fff; color: var(--text); text-align: left; cursor: pointer; }
.system-link:hover { border-color: #a6c1ff; background: #f8fbff; }
.system-link > .el-icon:first-child { color: var(--brand); }
.system-link span { display: grid; flex: 1; gap: 5px; }
.system-link small { color: var(--text-muted); }
</style>
