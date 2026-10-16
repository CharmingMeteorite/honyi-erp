<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../../lib/api.js'
const rows = ref([]), loading = ref(false)
onMounted(async () => {
  loading.value = true
  try { rows.value = await api.permissionTree() }
  catch (e) { ElMessage.error(e.message) }
  finally { loading.value = false }
})
</script>
<template>
  <div class="page-heading"><div><h1>权限管理</h1><p>查看系统菜单与操作权限编码。</p></div></div>
  <el-card v-loading="loading">
    <div class="list-caption"><strong>权限结构</strong><span>按模块展开查看</span></div>
    <el-empty v-if="!loading && !rows.length" description="暂无可查看的权限" />
    <el-tree v-else :data="rows" node-key="id" default-expand-all><template #default="{ data }"><span class="permission-line"><strong>{{ data.permissionName }}</strong><el-tag size="small" effect="plain" type="info">{{ data.permissionType }}</el-tag><code>{{ data.permissionCode }}</code></span></template></el-tree>
  </el-card>
</template>
<style scoped>
.list-caption { display: flex; align-items: baseline; gap: 12px; margin-bottom: 14px; }
.list-caption span { color: var(--text-muted); font-size: 12px; }
.permission-line { display: inline-flex; align-items: center; gap: 9px; min-width: 0; }
.permission-line strong { font-weight: 600; }
.permission-line code { overflow: hidden; color: var(--text-muted); font-size: 11px; text-overflow: ellipsis; }
</style>
