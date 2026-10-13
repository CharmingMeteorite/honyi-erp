import { createRouter, createWebHistory } from 'vue-router'
import { authState, clearSession, refreshUser, hasPermission } from '../lib/auth.js'
import LoginView from '../views/LoginView.vue'
import AppLayout from '../layouts/AppLayout.vue'
import DashboardView from '../views/DashboardView.vue'
import SystemView from '../views/SystemView.vue'
import ForbiddenView from '../views/ForbiddenView.vue'
import NotFoundView from '../views/NotFoundView.vue'
import UserView from '../views/system/UserView.vue'
import DepartmentView from '../views/system/DepartmentView.vue'
import RoleView from '../views/system/RoleView.vue'
import PermissionView from '../views/system/PermissionView.vue'
import CategoryView from '../views/master/CategoryView.vue'
import UnitView from '../views/master/UnitView.vue'
import BrandView from '../views/master/BrandView.vue'
import ProductView from '../views/master/ProductView.vue'
import DeviceView from '../views/asset/DeviceView.vue'
import WarehouseView from '../views/system/WarehouseView.vue'
import StockView from '../views/inventory/StockView.vue'
import StockLedgerView from '../views/inventory/StockLedgerView.vue'
import OpeningBalanceView from '../views/inventory/OpeningBalanceView.vue'
import InventoryOrderView from '../views/inventory/InventoryOrderView.vue'
import RequisitionView from '../views/inventory/RequisitionView.vue'
const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: LoginView, meta: { guest: true } },
    { path: '/', component: AppLayout, children: [
      { path: '', component: DashboardView }, { path: 'system', component: SystemView },
      { path: 'system/users', component: UserView, meta: { permission: 'system.user.view' } },
      { path: 'system/departments', component: DepartmentView, meta: { permission: 'system.department.view' } },
      { path: 'system/roles', component: RoleView, meta: { permission: 'system.role.view' } },
      { path: 'system/permissions', component: PermissionView, meta: { permission: 'system.permission.view' } },
      { path: 'master-data/categories', component: CategoryView, meta: { permission: 'system.category.view' } },
      { path: 'master-data/units', component: UnitView, meta: { permission: 'system.unit.view' } },
      { path: 'master-data/brands', component: BrandView, meta: { permission: 'system.brand.view' } },
      { path: 'master-data/products', component: ProductView, meta: { permission: 'system.product.view' } },
      { path: 'system/warehouses', component: WarehouseView, meta: { permission: 'system.warehouse.view' } },
      { path: 'inventory/stocks', component: StockView, meta: { permission: 'inventory.stock.view' } },
      { path: 'inventory/ledgers', component: StockLedgerView, meta: { permission: 'inventory.stock.ledger' } },
      { path: 'inventory/inbounds', component: InventoryOrderView, props: { kind: 'inbound' }, meta: { permission: 'inventory.inbound.view' } },
      { path: 'inventory/outbounds', component: InventoryOrderView, props: { kind: 'outbound' }, meta: { permission: 'inventory.outbound.view' } },
      { path: 'apply/requisitions', component: RequisitionView, meta: { permission: 'apply.requisition.view' } },
      { path: 'inventory/openings', component: OpeningBalanceView, meta: { permission: 'inventory.stock.opening' } },
      { path: 'assets/devices', component: DeviceView, meta: { permission: 'system.device.view' } },
    ] },
    { path: '/403', component: ForbiddenView }, { path: '/404', component: NotFoundView }, { path: '/:pathMatch(.*)*', redirect: '/404' },
  ],
})
router.beforeEach(async (to) => {
  if (authState.token && !authState.user) { try { await refreshUser() } catch { clearSession(); return '/login' } }
  if (to.meta.guest && authState.token) return '/'
  if (!to.meta.guest && !['/403', '/404'].includes(to.path) && !authState.token) return '/login'
  if (to.meta.permission && !hasPermission(to.meta.permission)) return '/403'
  return true
})
window.addEventListener('erp:unauthorized', () => { clearSession(); if (router.currentRoute.value.path !== '/login') router.push('/login') })
window.addEventListener('erp:forbidden', () => { if (router.currentRoute.value.path !== '/403') router.push('/403') })
export default router
