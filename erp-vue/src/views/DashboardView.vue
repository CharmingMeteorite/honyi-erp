<script setup>
import { onMounted, ref } from 'vue'
import { api } from '../lib/api.js'
import { authState } from '../lib/auth.js'
const health = ref('检查中')
onMounted(async () => { try { const data = await api.health(); health.value = data.status === 'UP' ? '运行正常' : '异常' } catch { health.value = '不可用' } })
</script>
<template><div class="page-heading"><div><h1>首页</h1><p class="muted">欢迎回来，{{ authState.user?.realName || authState.user?.username }}。</p></div></div><el-row :gutter="16"><el-col :xs="24" :sm="12" :md="8"><el-card><div class="metric-label">系统状态</div><div class="metric-value">{{ health }}</div></el-card></el-col><el-col :xs="24" :sm="12" :md="8"><el-card><div class="metric-label">当前账号</div><div class="metric-value">{{ authState.user?.username }}</div></el-card></el-col></el-row></template>
