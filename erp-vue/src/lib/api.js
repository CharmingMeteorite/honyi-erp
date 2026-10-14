const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || ''
export async function request(path, options = {}) {
  const headers = new Headers(options.headers || {})
  headers.set('Accept', 'application/json')
  if (options.body && !(options.body instanceof FormData)) headers.set('Content-Type', 'application/json')
  const token = localStorage.getItem('erp_access_token')
  if (token) headers.set('Authorization', `Bearer ${token}`)
  const response = await fetch(`${API_BASE_URL}${path}`, { ...options, headers })
  let payload = null
  try { payload = await response.json() } catch { payload = null }
  if (response.status === 401) window.dispatchEvent(new CustomEvent('erp:unauthorized'))
  if (response.status === 403) window.dispatchEvent(new CustomEvent('erp:forbidden'))
  if (!response.ok || (payload && payload.code !== 0)) {
    const error = new Error(payload?.message || `请求失败（${response.status}）`); error.status = response.status; error.code = payload?.code; throw error
  }
  return payload?.data
}
function query(params) { const value = new URLSearchParams(); Object.entries(params).forEach(([key, item]) => { if (item !== undefined && item !== null && item !== '') value.set(key, item) }); return value.toString() }
export const api = {
  stocktakes: (params = {}) => request(`/api/inventory/stocktakes?${query(params)}`),
  stocktake: (id) => request(`/api/inventory/stocktakes/${id}`),
  createStocktake: (data) => request('/api/inventory/stocktakes', { method: 'POST', body: JSON.stringify(data) }),
  updateStocktake: (id, data) => request(`/api/inventory/stocktakes/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  stocktakeAction: (id, action, data) => request(`/api/inventory/stocktakes/${id}/${action}`, { method: 'POST', body: JSON.stringify(data) }),
  stocktakeCount: (id, itemId, data) => request(`/api/inventory/stocktakes/${id}/items/${itemId}/count`, { method: 'PUT', body: JSON.stringify(data) }),
  stocktakeOptions: (resource, params = {}) => request(`/api/inventory/stocktakes/options/${resource}?${query(params)}`),

  inventoryOrders: (kind, params = {}) => request(`/api/inventory/${kind}s?${query(params)}`),
  inventoryOrder: (kind, id) => request(`/api/inventory/${kind}s/${id}`),
  createInventoryOrder: (kind, data) => request(`/api/inventory/${kind}s`, { method: 'POST', body: JSON.stringify(data) }),
  updateInventoryOrder: (kind, id, data) => request(`/api/inventory/${kind}s/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  inventoryOrderAction: (kind, id, action, data) => request(`/api/inventory/${kind}s/${id}/${action}`, { method: 'POST', body: JSON.stringify(data) }),
  inventoryOrderOptions: (kind, resource, params = {}) => request(`/api/inventory/${kind}s/options/${resource}?${query(params)}`),

  requisitions: (params = {}) => request(`/api/apply/requisitions?${query(params)}`),
  requisition: (id) => request(`/api/apply/requisitions/${id}`),
  createRequisition: (data) => request('/api/apply/requisitions', { method: 'POST', body: JSON.stringify(data) }),
  updateRequisition: (id, data) => request(`/api/apply/requisitions/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  requisitionAction: (id, action, data) => request(`/api/apply/requisitions/${id}/${action}`, { method: 'POST', body: JSON.stringify(data) }),
  requisitionOptions: (resource, params = {}) => request(`/api/apply/requisitions/options/${resource}?${query(params)}`),

  login: (data) => request('/api/auth/login', { method: 'POST', body: JSON.stringify(data) }),
  logout: () => request('/api/auth/logout', { method: 'POST' }),
  me: () => request('/api/auth/me'),
  health: () => request('/health'),
  users: (params = {}) => request(`/api/system/users?${query(params)}`),
  user: (id) => request(`/api/system/users/${id}`),
  createUser: (data) => request('/api/system/users', { method: 'POST', body: JSON.stringify(data) }),
  updateUser: (id, data) => request(`/api/system/users/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  userStatus: (id, enabled) => request(`/api/system/users/${id}/${enabled ? 'enable' : 'disable'}`, { method: 'POST' }),
  resetPassword: (id, data) => request(`/api/system/users/${id}/reset-password`, { method: 'POST', body: JSON.stringify(data) }),
  roles: () => request('/api/system/roles'),
  roleOptions: () => request('/api/system/roles/options'),
  role: (id) => request(`/api/system/roles/${id}`),
  createRole: (data) => request('/api/system/roles', { method: 'POST', body: JSON.stringify(data) }),
  updateRole: (id, data) => request(`/api/system/roles/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  roleStatus: (id, enabled) => request(`/api/system/roles/${id}/${enabled ? 'enable' : 'disable'}`, { method: 'POST' }),
  deleteRole: (id) => request(`/api/system/roles/${id}`, { method: 'DELETE' }),
  rolePermissions: (id, data) => request(`/api/system/roles/${id}/permissions`, { method: 'PUT', body: JSON.stringify(data) }),
  permissionTree: () => request('/api/system/permissions/tree'),
  departments: () => request('/api/system/departments/tree'),
  createDepartment: (data) => request('/api/system/departments', { method: 'POST', body: JSON.stringify(data) }),
  updateDepartment: (id, data) => request(`/api/system/departments/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  departmentStatus: (id, enabled) => request(`/api/system/departments/${id}/${enabled ? 'enable' : 'disable'}`, { method: 'POST' }),
  categories: () => request('/api/master-data/categories/tree'), createCategory: (data) => request('/api/master-data/categories', { method: 'POST', body: JSON.stringify(data) }), updateCategory: (id, data) => request(`/api/master-data/categories/${id}`, { method: 'PUT', body: JSON.stringify(data) }), categoryStatus: (id, enabled) => request(`/api/master-data/categories/${id}/${enabled ? 'enable' : 'disable'}`, { method: 'POST' }),
  units: () => request('/api/master-data/units'), unitOptions: () => request('/api/master-data/units/options'), createUnit: (data) => request('/api/master-data/units', { method: 'POST', body: JSON.stringify(data) }), updateUnit: (id, data) => request(`/api/master-data/units/${id}`, { method: 'PUT', body: JSON.stringify(data) }), unitStatus: (id, enabled) => request(`/api/master-data/units/${id}/${enabled ? 'enable' : 'disable'}`, { method: 'POST' }),
  brands: () => request('/api/master-data/brands'), brandOptions: () => request('/api/master-data/brands/options'), createBrand: (data) => request('/api/master-data/brands', { method: 'POST', body: JSON.stringify(data) }), updateBrand: (id, data) => request(`/api/master-data/brands/${id}`, { method: 'PUT', body: JSON.stringify(data) }), brandStatus: (id, enabled) => request(`/api/master-data/brands/${id}/${enabled ? 'enable' : 'disable'}`, { method: 'POST' }),
  products: (params = {}) => request(`/api/master-data/products?${query(params)}`), product: (id) => request(`/api/master-data/products/${id}`), createProduct: (data) => request('/api/master-data/products', { method: 'POST', body: JSON.stringify(data) }), updateProduct: (id, data) => request(`/api/master-data/products/${id}`, { method: 'PUT', body: JSON.stringify(data) }), productStatus: (id, enabled) => request(`/api/master-data/products/${id}/${enabled ? 'enable' : 'disable'}`, { method: 'POST' }),
  productOptions: (params = {}) => request(`/api/assets/devices/options?${query(params)}`),
  deviceDepartmentOptions: (params = {}) => request(`/api/assets/devices/options/departments?${query(params)}`), deviceUserOptions: (params = {}) => request(`/api/assets/devices/options/users?${query(params)}`),
  devices: (params = {}) => request(`/api/assets/devices?${query(params)}`), device: (id) => request(`/api/assets/devices/${id}`), deviceHistory: (id) => request(`/api/assets/devices/${id}/history`), createDevice: (data) => request('/api/assets/devices', { method: 'POST', body: JSON.stringify(data) }), updateDevice: (id, data) => request(`/api/assets/devices/${id}`, { method: 'PUT', body: JSON.stringify(data) }), deviceStatus: (id, status, reason) => request(`/api/assets/devices/${id}/status`, { method: 'PATCH', body: JSON.stringify({ status, reason }) }),
  warehouses: (params = {}) => request(`/api/system/warehouses?${query(params)}`),
  createWarehouse: (data) => request('/api/system/warehouses', { method: 'POST', body: JSON.stringify(data) }),
  updateWarehouse: (id, data) => request(`/api/system/warehouses/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  warehouseStatus: (id, enabled) => request(`/api/system/warehouses/${id}/${enabled ? 'enable' : 'disable'}`, { method: 'POST' }),
  warehouseDepartments: () => request('/api/system/warehouses/options/departments'),
  inventoryWarehouses: (params = {}) => request(`/api/inventory/options/warehouses?${query(params)}`),
  inventoryProducts: (params = {}) => request(`/api/inventory/options/products?${query(params)}`),
  openingDevices: (params = {}) => request(`/api/inventory/options/devices?${query(params)}`),
  stocks: (params = {}) => request(`/api/inventory/stocks?${query(params)}`),
  stockConsistency: (params = {}) => request(`/api/inventory/stocks/consistency?${query(params)}`),
  stockLedgers: (params = {}) => request(`/api/inventory/ledgers?${query(params)}`),
  openings: (params = {}) => request(`/api/inventory/openings?${query(params)}`),
  opening: (id) => request(`/api/inventory/openings/${id}`),
  createOpening: (data) => request('/api/inventory/openings', { method: 'POST', body: JSON.stringify(data) }),
  updateOpening: (id, data) => request(`/api/inventory/openings/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  completeOpening: (id) => request(`/api/inventory/openings/${id}/complete`, { method: 'POST' }),
}
