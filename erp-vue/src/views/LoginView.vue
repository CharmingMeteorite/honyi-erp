<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api } from '../lib/api.js'
import { setSession } from '../lib/auth.js'
import brandLogo from '../assets/hongyi-logo.png'

const router = useRouter()
const loading = ref(false)
const form = reactive({ username: '', password: '' })
async function submit() {
  if (!form.username.trim() || !form.password) { ElMessage.warning('请输入用户名和密码'); return }
  loading.value = true
  try { setSession(await api.login(form)); router.push('/') }
  catch (error) { ElMessage.error(error.message) }
  finally { loading.value = false }
}
</script>
<template>
  <main class="login-page">
    <div class="login-layout">
      <section class="login-intro">
        <div class="login-brand"><img :src="brandLogo" alt="弘毅人工智能" /><span>·<em>Erp</em></span></div>
        <div class="login-intro-copy"><span>企业运营工作台</span><h1>让物资、库存与设备<br />管理更加清晰。</h1><p>统一管理业务资料、仓储流程、设备资产与运营报表。</p></div>
        <small>内部业务系统 · 请使用已分配的账号登录</small>
      </section>
      <section class="login-form-area">
        <div class="login-form-wrap">
          <div class="login-eyebrow">WELCOME BACK</div>
          <h2>登录工作台</h2>
          <p>输入账号信息，继续处理你的业务。</p>
          <el-form :model="form" label-position="top" @submit.prevent="submit">
            <el-form-item label="用户名"><el-input v-model="form.username" size="large" placeholder="请输入用户名" autocomplete="username" /></el-form-item>
            <el-form-item label="密码"><el-input v-model="form.password" size="large" type="password" show-password placeholder="请输入密码" autocomplete="current-password" /></el-form-item>
            <el-button type="primary" native-type="submit" size="large" :loading="loading" class="login-submit">登录</el-button>
          </el-form>
          <span class="login-help">登录遇到问题，请联系系统管理员。</span>
        </div>
      </section>
    </div>
  </main>
</template>
<style scoped>
.login-page { min-height: 100vh; display: grid; place-items: center; padding: 28px; background: #f1f4f9; }
.login-layout { display: grid; grid-template-columns: minmax(380px, 1fr) minmax(380px, .9fr); width: min(980px, 100%); min-height: 570px; overflow: hidden; background: #fff; border: 1px solid var(--line); border-radius: 16px; box-shadow: 0 20px 60px rgba(29, 49, 85, .09); }
.login-intro { display: flex; flex-direction: column; justify-content: space-between; padding: 45px 50px; color: #fff; background: #113e9d; }
.login-brand { width: max-content; max-width: calc(100% + 50px); display: flex; align-items: center; gap: 6px; margin-left: -50px; padding: 8px 14px 8px 50px; border-radius: 0 8px 8px 0; background: #fff; }
.login-brand img { display: block; width: 226px; max-width: calc(100% - 42px); height: auto; }
.login-brand span { flex: none; color: #8995a7; font-size: 20px; font-weight: 650; }
.login-brand em { font-style: italic; }
.login-intro-copy > span { color: #b7ceff; font-size: 12px; letter-spacing: .12em; }
.login-intro-copy h1 { margin: 17px 0; font-size: clamp(26px, 3vw, 37px); line-height: 1.36; letter-spacing: -.035em; }
.login-intro-copy p { max-width: 350px; color: #d4e0ff; font-size: 14px; line-height: 1.8; }
.login-intro small { color: #adc6fc; }
.login-form-area { display: grid; place-items: center; padding: 48px; }
.login-form-wrap { width: min(330px, 100%); }
.login-eyebrow { color: var(--brand); font-size: 11px; font-weight: 750; letter-spacing: .13em; }
.login-form-wrap h2 { margin: 10px 0 7px; font-size: 27px; letter-spacing: -.03em; }
.login-form-wrap > p { margin: 0 0 35px; color: var(--text-secondary); font-size: 13px; }
.login-form-wrap :deep(.el-form-item) { margin-bottom: 22px; }
.login-form-wrap :deep(.el-form-item__label) { font-weight: 650; }
.login-submit { width: 100%; margin-top: 10px; }
.login-help { display: block; margin-top: 27px; color: var(--text-muted); font-size: 12px; text-align: center; }
@media (max-width: 760px) { .login-page { padding: 14px; } .login-layout { grid-template-columns: 1fr; min-height: 0; } .login-intro { padding: 24px; min-height: 120px; } .login-brand { margin-left: -24px; padding-left: 24px; } .login-brand img { width: min(226px, calc(100vw - 120px)); } .login-intro-copy, .login-intro small { display: none; } .login-form-area { padding: 45px 24px; } }
</style>
