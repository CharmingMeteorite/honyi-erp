import { reactive } from 'vue'
import { api } from './api.js'
const storedUser = localStorage.getItem('erp_current_user')
export const authState = reactive({ token: localStorage.getItem('erp_access_token') || '', user: storedUser ? JSON.parse(storedUser) : null })
export function setSession(data) { authState.token = data.accessToken; authState.user = data.user; localStorage.setItem('erp_access_token', data.accessToken); localStorage.setItem('erp_current_user', JSON.stringify(data.user)) }
export function clearSession() { authState.token = ''; authState.user = null; localStorage.removeItem('erp_access_token'); localStorage.removeItem('erp_current_user') }
export async function refreshUser() { const user = await api.me(); authState.user = user; localStorage.setItem('erp_current_user', JSON.stringify(user)); return user }
export function hasPermission(permission) { return authState.user?.permissions?.includes(permission) || authState.user?.roles?.includes('SYSTEM_ADMIN') }
export async function logout() { try { await api.logout() } finally { clearSession() } }
