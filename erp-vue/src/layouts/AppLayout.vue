<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  House, Search, DataAnalysis, User, OfficeBuilding, Key, Lock, Monitor,
  Bell, Box, Collection, Goods, Coin, Tickets, Document, List, Tools,
  Printer, Expand, Fold, ArrowDown, Grid, Van, ShoppingCart, Files,
  RefreshRight, Setting, Warning, ArrowRight
} from '@element-plus/icons-vue'
import { authState, logout, hasPermission } from '../lib/auth.js'
import { api } from '../lib/api.js'
import brandLogo from '../assets/hongyi-logo.png'
import brandMark from '../assets/hongyi-mark.png'

const router = useRouter()
const route = useRoute()
const unreadCount = ref(0)
const collapsed = ref(localStorage.getItem('erp_nav_collapsed') === '1')
const mobileOpen = ref(false)
const groups = [
  { key: 'workspace', label: '工作台', items: [
    { path: '/', label: '运营概览', icon: House },
    { path: '/query', label: '综合查询', icon: Search },
    { path: '/notifications', label: '站内通知', icon: Bell, permission: 'notification.view' },
  ] },
  { key: 'master', label: '基础资料', icon: Goods, items: [
    { path: '/master-data/products', label: '物资档案', icon: Goods, permission: 'system.product.view' },
    { path: '/master-data/categories', label: '物资分类', icon: Grid, permission: 'system.category.view' },
    { path: '/master-data/brands', label: '品牌管理', icon: Collection, permission: 'system.brand.view' },
    { path: '/master-data/units', label: '计量单位', icon: Coin, permission: 'system.unit.view' },
    { path: '/system/warehouses', label: '仓库管理', icon: Box, permission: 'system.warehouse.view' },
  ] },
  { key: 'inventory', label: '仓储业务', icon: Box, items: [
    { path: '/inventory/stocks', label: '库存查询', icon: Collection, permission: 'inventory.stock.view' },
    { path: '/inventory/ledgers', label: '库存流水', icon: List, permission: 'inventory.stock.ledger' },
    { path: '/inventory/openings', label: '期初库存', icon: Document, permission: 'inventory.stock.opening' },
    { path: '/inventory/inbounds', label: '入库单', icon: Box, permission: 'inventory.inbound.view' },
    { path: '/inventory/outbounds', label: '出库单', icon: Van, permission: 'inventory.outbound.view' },
    { path: '/apply/requisitions', label: '领用申请', icon: ShoppingCart, permission: 'apply.requisition.view' },
    { path: '/inventory/stocktakes', label: '库存盘点', icon: Tickets, permission: 'inventory.stocktake.view' },
  ] },
  { key: 'assets', label: '设备业务', icon: Monitor, items: [
    { path: '/assets/devices', label: '设备档案', icon: Monitor, permission: 'system.device.view' },
    { path: '/assets/repairs', label: '维修管理', icon: Tools, permission: 'asset.repair.view' },
    { path: '/assets/warranties', label: '保修管理', icon: Warning, permission: 'asset.warranty.view' },
    { path: '/assets/exchanges', label: '换货管理', icon: RefreshRight, permission: 'asset.exchange.view' },
    { path: '/assets/qr', label: '二维码与标签', icon: Printer, permission: 'asset.qr.view' },
  ] },
  { key: 'reports', label: '报表分析', icon: DataAnalysis, items: [
    { path: '/reports', label: '业务报表', icon: DataAnalysis, permissions: [
      'inventory.stock.view', 'inventory.inbound.view', 'inventory.outbound.view',
      'apply.requisition.view', 'system.device.view', 'inventory.stocktake.view',
      'asset.repair.view', 'asset.warranty.view', 'asset.exchange.view'
    ] },
  ] },
  { key: 'system', label: '系统管理', icon: Setting, items: [
    { path: '/system/users', label: '用户管理', icon: User, permission: 'system.user.view' },
    { path: '/system/departments', label: '部门管理', icon: OfficeBuilding, permission: 'system.department.view' },
    { path: '/system/roles', label: '角色管理', icon: Key, permission: 'system.role.view' },
    { path: '/system/permissions', label: '权限管理', icon: Lock, permission: 'system.permission.view' },
  ] },
]
const visibleGroups = computed(() => groups.map(group => ({
  ...group,
  items: group.items.filter(item => !item.permission || hasPermission(item.permission))
    .filter(item => !item.permissions || item.permissions.some(hasPermission)),
})).filter(group => group.items.length))
const currentItem = computed(() => visibleGroups.value.flatMap(group => group.items).find(item => item.path === route.path))
const currentGroup = computed(() => visibleGroups.value.find(group => group.items.some(item => item.path === route.path))?.label || '工作台')
const expandedGroups = ref([])
const manuallyExpandedGroups = ref([])
function groupForPath(path) {
  return visibleGroups.value.find(group => group.key !== 'workspace' && group.items.some(item => item.path === path))?.key
}
function isGroupExpanded(key) { return expandedGroups.value.includes(key) }
function toggleGroup(key) {
  if (isGroupExpanded(key)) {
    expandedGroups.value = expandedGroups.value.filter(value => value !== key)
    manuallyExpandedGroups.value = manuallyExpandedGroups.value.filter(value => value !== key)
  } else {
    expandedGroups.value = [...expandedGroups.value, key]
    manuallyExpandedGroups.value = [...manuallyExpandedGroups.value, key]
  }
}
watch(() => route.path, (path, previousPath) => {
  const previousKey = groupForPath(previousPath)
  const currentKey = groupForPath(path)
  if (currentKey) manuallyExpandedGroups.value = manuallyExpandedGroups.value.filter(value => value !== currentKey)
  if (previousKey && previousKey !== currentKey && !manuallyExpandedGroups.value.includes(previousKey)) {
    expandedGroups.value = expandedGroups.value.filter(value => value !== previousKey)
  }
  if (currentKey && !isGroupExpanded(currentKey)) expandedGroups.value = [...expandedGroups.value, currentKey]
}, { immediate: true })
const displayName = computed(() => authState.user?.realName || authState.user?.username || '用户')
const initial = computed(() => displayName.value.slice(0, 1).toUpperCase())
async function refreshNotifications() {
  if (!hasPermission('notification.view')) return
  try { unreadCount.value = await api.notificationCount() } catch { unreadCount.value = 0 }
}
function toggleNav() {
  if (window.innerWidth <= 760) { mobileOpen.value = !mobileOpen.value; return }
  collapsed.value = !collapsed.value
  localStorage.setItem('erp_nav_collapsed', collapsed.value ? '1' : '0')
}
async function handleLogout() {
  try { await logout(); router.push('/login') }
  catch (error) { ElMessage.error(error.message) }
}
watch(() => route.fullPath, () => { mobileOpen.value = false; refreshNotifications() })
onMounted(() => { refreshNotifications(); window.addEventListener('erp:notifications-changed', refreshNotifications) })
onUnmounted(() => window.removeEventListener('erp:notifications-changed', refreshNotifications))
</script>

<template>
  <el-container class="app-shell">
    <button class="sidebar-shade" :class="{ 'is-visible': mobileOpen }" aria-label="关闭导航" @click="mobileOpen = false" />
    <el-aside :width="collapsed ? '72px' : '234px'" class="app-aside" :class="{ 'is-collapsed': collapsed, 'is-mobile-open': mobileOpen }">
      <div class="aside-inner">
        <RouterLink to="/" class="brand" aria-label="弘毅 ERP，返回运营概览">
          <img v-if="collapsed && !mobileOpen" :src="brandMark" class="brand-mark-image" alt="" />
          <span v-else class="brand-identity"><img :src="brandLogo" class="brand-wordmark" alt="弘毅人工智能" /><span class="brand-suffix">·<em>Erp</em></span></span>
        </RouterLink>
        <nav class="aside-scroll" aria-label="主导航">
          <div v-for="group in visibleGroups" :key="group.key" class="nav-group" :class="{ 'nav-workspace': group.key === 'workspace' }">
            <template v-if="group.key === 'workspace'">
              <span v-if="!collapsed || mobileOpen" class="nav-group-label">{{ group.label }}</span>
              <el-menu router :default-active="route.path" :collapse="collapsed && !mobileOpen" class="app-menu">
                <el-menu-item v-for="item in group.items" :key="item.path" :index="item.path" :title="item.label">
                  <el-icon><component :is="item.icon" /></el-icon><template #title>
                    {{ item.label }}<span v-if="item.path === '/notifications' && unreadCount" class="nav-unread">{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
                  </template>
                </el-menu-item>
              </el-menu>
            </template>
            <template v-else-if="!collapsed || mobileOpen">
              <button type="button" class="nav-group-toggle" :class="{ 'is-current': group.items.some(item => item.path === route.path) }"
                :aria-expanded="isGroupExpanded(group.key)" :aria-controls="`nav-items-${group.key}`" @click="toggleGroup(group.key)">
                <el-icon class="nav-group-icon"><component :is="group.icon" /></el-icon>
                <span>{{ group.label }}</span>
                <el-icon class="nav-group-arrow" :class="{ 'is-open': isGroupExpanded(group.key) }"><ArrowRight /></el-icon>
              </button>
              <el-menu v-show="isGroupExpanded(group.key)" :id="`nav-items-${group.key}`" router :default-active="route.path" class="app-menu nav-group-items">
                <el-menu-item v-for="item in group.items" :key="item.path" :index="item.path" :title="item.label">
                  <el-icon><component :is="item.icon" /></el-icon><template #title>{{ item.label }}</template>
                </el-menu-item>
              </el-menu>
            </template>
            <el-popover v-else placement="right-start" trigger="hover" :show-after="120" :hide-after="100" :width="196" popper-class="nav-group-popover">
              <template #reference>
                <button type="button" class="nav-group-compact" :class="{ 'is-current': group.items.some(item => item.path === route.path) }"
                  :title="group.label" :aria-label="group.label">
                  <el-icon><component :is="group.icon" /></el-icon>
                </button>
              </template>
              <strong class="nav-popover-title">{{ group.label }}</strong>
              <RouterLink v-for="item in group.items" :key="item.path" :to="item.path" class="nav-popover-link"
                :class="{ 'is-active': route.path === item.path }" :aria-current="route.path === item.path ? 'page' : undefined">
                <el-icon><component :is="item.icon" /></el-icon><span>{{ item.label }}</span>
              </RouterLink>
            </el-popover>
          </div>
        </nav>
        <div v-if="!collapsed || mobileOpen" class="aside-footer">ERP · 业务数据以当前权限为准</div>
      </div>
    </el-aside>
    <el-container class="app-content">
      <el-header class="app-header">
        <div class="header-context">
          <el-button class="header-icon" :aria-label="collapsed ? '展开导航' : '折叠导航'" @click="toggleNav">
            <el-icon :size="18"><Expand v-if="collapsed" /><Fold v-else /></el-icon>
          </el-button>
          <span class="header-divider" />
          <div class="header-title"><small>{{ currentGroup }}</small><strong>{{ currentItem?.label || 'ERP' }}</strong></div>
        </div>
        <div class="header-actions">
          <el-button class="header-icon header-search" aria-label="综合查询" title="综合查询" @click="router.push('/query')"><el-icon :size="18"><Search /></el-icon></el-button>
          <el-badge v-if="hasPermission('notification.view')" :value="unreadCount" :hidden="!unreadCount" :max="99">
            <el-button class="header-icon" aria-label="站内通知" title="站内通知" @click="router.push('/notifications')"><el-icon :size="18"><Bell /></el-icon></el-button>
          </el-badge>
          <span class="header-divider" />
          <el-dropdown trigger="click" @command="command => { if (command === 'logout') handleLogout() }">
            <button class="user-trigger" type="button" aria-label="用户菜单">
              <span class="user-avatar">{{ initial }}</span><span class="user-name">{{ displayName }}</span><el-icon><ArrowDown /></el-icon>
            </button>
            <template #dropdown><el-dropdown-menu><el-dropdown-item disabled>{{ displayName }}</el-dropdown-item><el-dropdown-item divided command="logout">退出登录</el-dropdown-item></el-dropdown-menu></template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="app-main"><RouterView /></el-main>
    </el-container>
  </el-container>
</template>
