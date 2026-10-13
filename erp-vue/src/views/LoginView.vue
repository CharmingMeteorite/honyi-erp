<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api } from '../lib/api.js'
import { setSession } from '../lib/auth.js'
const router = useRouter(); const loading = ref(false); const form = reactive({ username: '', password: '' })
async function submit() { loading.value = true; try { setSession(await api.login(form)); router.push('/') } catch (error) { ElMessage.error(error.message) } finally { loading.value = false } }
</script>
<template><main class="login-page"><el-card class="login-card"><h1>ERP 管理平台</h1><p class="muted">公司内部物资、库存与设备管理系统</p><el-form :model="form" @submit.prevent="submit"><el-form-item><el-input v-model="form.username" placeholder="用户名" autocomplete="username" /></el-form-item><el-form-item><el-input v-model="form.password" type="password" show-password placeholder="密码" autocomplete="current-password" /></el-form-item><el-button type="primary" native-type="submit" :loading="loading" class="full-width">登录</el-button></el-form></el-card></main></template>
