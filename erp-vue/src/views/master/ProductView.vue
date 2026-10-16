<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, RefreshRight } from '@element-plus/icons-vue'
import { api } from '../../lib/api.js'
import { hasPermission } from '../../lib/auth.js'
import AttachmentPanel from '../../components/AttachmentPanel.vue'

const route = useRoute()
const router = useRouter()
const rows = ref([]), total = ref(0), loading = ref(false), saving = ref(false)
const dialog = ref(false), detailDialog = ref(false), editing = ref(null), detail = ref(null)
const categories = ref([]), brands = ref([]), units = ref([])
const query = reactive({ page: 1, size: 20, code: '', name: '', model: '', productType: '', status: null })
const defaults = () => ({
  productCode: '', productName: '', categoryId: null, brandId: null, model: '',
  specification: '', unitId: null, productType: 'QUANTITY', trackSn: false,
  trackMac: false, isFixedAsset: false, enableQrcode: false, safetyStock: 0,
  minStock: 0, maxStock: 0, remark: '', status: true,
})
const form = reactive(defaults())
async function loadOptions() {
  try {
    const categoriesTree = await api.categories()
    function flatten(items) { return items.reduce((all, item) => all.concat(item, flatten(item.children || [])), []) }
    categories.value = flatten(categoriesTree)
    brands.value = await api.brandOptions()
    units.value = await api.unitOptions()
  } catch (e) { ElMessage.error(e.message) }
}
async function load() {
  loading.value = true
  try { const data = await api.products(query); rows.value = data.list; total.value = data.total }
  catch (e) { ElMessage.error(e.message) }
  finally { loading.value = false }
}
function reset() {
  Object.assign(query, { page: 1, code: '', name: '', model: '', productType: '', status: null })
  load()
}
function open(row = null) {
  editing.value = row?.id || null
  Object.assign(form, defaults(), row || {})
  dialog.value = true
}
async function openDetail(row) {
  try { detail.value = await api.product(row.id); detailDialog.value = true }
  catch (e) { ElMessage.error(e.message) }
}
async function save() {
  if (!form.productName?.trim() || !form.productCode?.trim()) { ElMessage.warning('请填写物资编码和名称'); return }
  saving.value = true
  try {
    if (editing.value) await api.updateProduct(editing.value, form)
    else await api.createProduct(form)
    dialog.value = false
    ElMessage.success('物资档案已保存')
    await load()
  } catch (e) { ElMessage.error(e.message) }
  finally { saving.value = false }
}
onMounted(async () => {
  await loadOptions()
  if (route.query.id) {
    try {
      const product = await api.product(Number(route.query.id))
      query.code = product.productCode
      detail.value = product
      detailDialog.value = true
    } catch (e) { ElMessage.error(e.message) }
  }
  await load()
})
</script>

<template>
  <div class="page-heading"><div><h1>物资档案</h1><p>维护编码、分类、型号和库存管理属性。</p></div><el-button v-if="hasPermission('system.product.create')" type="primary" @click="open()">新增物资</el-button></div>
  <section class="product-panel">
    <div class="filter-bar">
      <el-input v-model="query.code" clearable placeholder="物资编码" @keyup.enter="query.page=1;load()" />
      <el-input v-model="query.name" clearable placeholder="物资名称" @keyup.enter="query.page=1;load()" />
      <el-input v-model="query.model" clearable placeholder="型号" @keyup.enter="query.page=1;load()" />
      <el-select v-model="query.productType" clearable placeholder="全部类型"><el-option label="数量型" value="QUANTITY" /><el-option label="设备型" value="DEVICE" /></el-select>
      <el-button type="primary" :icon="Search" :loading="loading" @click="query.page=1;load()">查询</el-button>
      <el-button :icon="RefreshRight" @click="reset">重置</el-button>
    </div>
    <el-table v-loading="loading" :data="rows" empty-text="当前条件下暂无物资">
      <el-table-column prop="productCode" label="物资编码" min-width="150" show-overflow-tooltip />
      <el-table-column prop="productName" label="名称" min-width="180" show-overflow-tooltip />
      <el-table-column prop="categoryName" label="分类" min-width="110" show-overflow-tooltip />
      <el-table-column prop="brandName" label="品牌" min-width="100" show-overflow-tooltip />
      <el-table-column prop="model" label="型号" min-width="120" show-overflow-tooltip />
      <el-table-column label="类型" width="100"><template #default="{ row }"><el-tag size="small" effect="plain" type="info">{{ row.productType === 'DEVICE' ? '设备型' : '数量型' }}</el-tag></template></el-table-column>
      <el-table-column label="状态" width="88"><template #default="{ row }"><el-tag size="small" :type="row.status ? 'success' : 'info'">{{ row.status ? '启用' : '停用' }}</el-tag></template></el-table-column>
      <el-table-column label="操作" fixed="right" min-width="205"><template #default="{ row }">
        <el-button link @click="openDetail(row)">详情</el-button>
        <el-button v-if="hasPermission('asset.qr.view') && hasPermission('asset.qr.generate')" link @click="router.push({ path: '/assets/qr', query: { productId: row.id } })">二维码</el-button>
        <el-button v-if="hasPermission('system.product.update')" link type="primary" @click="open(row)">编辑</el-button>
      </template></el-table-column>
    </el-table>
    <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total" :page-sizes="[10,20,50,100]" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="query.page=1;load()" />
  </section>

  <el-dialog v-model="dialog" :title="editing ? '编辑物资' : '新增物资'" width="700px" :close-on-click-modal="false">
    <el-form label-width="100px" class="product-form">
      <h3>基础信息</h3>
      <el-form-item label="物资编码" required><el-input v-model="form.productCode" maxlength="64" /></el-form-item>
      <el-form-item label="物资名称" required><el-input v-model="form.productName" maxlength="100" /></el-form-item>
      <el-form-item label="分类"><el-select v-model="form.categoryId" filterable><el-option v-for="item in categories.filter(item => item.status)" :key="item.id" :label="item.categoryName" :value="item.id" /></el-select></el-form-item>
      <el-form-item label="品牌"><el-select v-model="form.brandId" filterable><el-option v-for="item in brands" :key="item.id" :label="item.brandName" :value="item.id" /></el-select></el-form-item>
      <el-form-item label="型号"><el-input v-model="form.model" /></el-form-item>
      <el-form-item label="规格"><el-input v-model="form.specification" /></el-form-item>
      <h3>管理方式</h3>
      <el-form-item label="类型"><el-radio-group v-model="form.productType"><el-radio value="QUANTITY">数量型</el-radio><el-radio value="DEVICE">设备型</el-radio></el-radio-group></el-form-item>
      <el-form-item label="单位"><el-select v-model="form.unitId" filterable><el-option v-for="item in units" :key="item.id" :label="item.unitName" :value="item.id" /></el-select></el-form-item>
      <el-form-item label="管理属性"><div class="product-checks"><el-checkbox v-model="form.trackSn">SN 管理</el-checkbox><el-checkbox v-model="form.trackMac">MAC 管理</el-checkbox><el-checkbox v-model="form.isFixedAsset">固定资产</el-checkbox><el-checkbox v-model="form.enableQrcode">启用二维码</el-checkbox></div></el-form-item>
      <el-form-item label="库存配置"><div class="stock-config"><label>安全库存 <el-input-number v-model="form.safetyStock" :min="0" /></label><label>最低库存 <el-input-number v-model="form.minStock" :min="0" /></label><label>最高库存 <el-input-number v-model="form.maxStock" :min="0" /></label></div></el-form-item>
      <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="3" /></el-form-item>
    </el-form>
    <template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存档案</el-button></template>
  </el-dialog>

  <el-dialog v-model="detailDialog" title="物资详情" width="760px">
    <template v-if="detail">
      <div class="detail-identity"><div><small>物资编码</small><strong>{{ detail.productCode }}</strong><span>{{ detail.productName }}</span></div><el-tag :type="detail.status ? 'success' : 'info'">{{ detail.status ? '启用' : '停用' }}</el-tag></div>
      <h3>基础资料</h3>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="分类">{{ detail.categoryName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="品牌">{{ detail.brandName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="型号">{{ detail.model || '—' }}</el-descriptions-item>
        <el-descriptions-item label="规格">{{ detail.specification || '—' }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ detail.productType === 'DEVICE' ? '设备型' : '数量型' }}</el-descriptions-item>
        <el-descriptions-item label="单位">{{ detail.unitName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="安全库存">{{ detail.safetyStock ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detail.remark || '—' }}</el-descriptions-item>
      </el-descriptions>
      <AttachmentPanel business-type="PRODUCT" :business-id="detail.id" />
    </template>
  </el-dialog>
</template>

<style scoped>
.product-panel { min-width: 0; }
.product-form h3 { margin: 6px 0 18px; padding-bottom: 9px; border-bottom: 1px solid var(--line); }
.product-form .el-select { width: 100%; }
.product-checks, .stock-config { display: flex; align-items: center; flex-wrap: wrap; gap: 14px; }
.stock-config label { display: grid; gap: 5px; color: var(--text-secondary); font-size: 12px; }
.detail-identity { display: flex; align-items: start; justify-content: space-between; padding: 18px; background: var(--surface-subtle); border: 1px solid var(--line); border-radius: 8px; }
.detail-identity div { display: grid; gap: 5px; }
.detail-identity small { color: var(--text-muted); }
.detail-identity strong { font-size: 20px; }
.detail-identity span { color: var(--text-secondary); }
</style>
