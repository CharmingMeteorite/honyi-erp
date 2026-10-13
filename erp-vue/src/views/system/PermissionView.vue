<script setup>
import { onMounted, ref } from 'vue'; import { ElMessage } from 'element-plus'; import { api } from '../../lib/api.js'
const rows=ref([]); onMounted(async()=>{try{rows.value=await api.permissionTree()}catch(e){ElMessage.error(e.message)}})
</script>
<template><div class="page-heading"><div><h1>权限管理</h1><p class="muted">查看系统菜单和按钮权限。</p></div></div><el-card><el-tree :data="rows" node-key="id" default-expand-all><template #default="{data}"><span>{{data.permissionName}} <el-tag size="small" effect="plain">{{data.permissionType}}</el-tag> <span class="muted">{{data.permissionCode}}</span></span></template></el-tree></el-card></template>
