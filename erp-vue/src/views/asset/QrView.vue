<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Camera, Printer, Search, Close } from '@element-plus/icons-vue'
import { api } from '../../lib/api.js'
import { hasPermission } from '../../lib/auth.js'

const route = useRoute()
const router = useRouter()
const mode = ref('scan')
const input = ref('')
const scanned = ref(null)
const feedback = ref('')
const loading = ref(false)
const cameraOn = ref(false)
const cameraStarting = ref(false)
const video = ref(null)
const labels = ref([])
const candidates = ref([])
const selected = ref([])
const itemType = ref('DEVICE')
const keyword = ref('')
const settings = reactive({ width: 60, height: 35, qr: 25, margin: 3 })
let stream = null
let frame = null
let detector = null
let lastRead = ''
let cameraRequest = 0
let detecting = false
const cameraSupported = typeof window !== 'undefined' && 'BarcodeDetector' in window && !!navigator.mediaDevices?.getUserMedia
const canPrint = computed(() => hasPermission('asset.qr.print') && hasPermission('asset.qr.generate'))
const canGenerate = computed(() => hasPermission('asset.qr.generate'))
const maxQrSize = computed(() => Math.min(32, settings.height - settings.margin * 2, settings.width / 2 - settings.margin * 2))
watch(maxQrSize, max => { if (settings.qr > max) settings.qr = max })
const labelStyle = computed(() => ({
  '--label-width': `${settings.width}mm`, '--label-height': `${settings.height}mm`,
  '--qr-size': `${settings.qr}mm`, '--label-margin': `${settings.margin}mm`,
}))

async function scan(value = input.value) {
  if (loading.value || !value?.trim()) return
  loading.value = true
  try {
    scanned.value = await api.qrScan(value.trim())
    input.value = value.trim()
    feedback.value = '识别成功'
    lastRead = value.trim()
    ElMessage.success('已找到对应档案')
  } catch (e) {
    scanned.value = null
    feedback.value = e.message
    lastRead = value.trim()
    ElMessage.error(e.message)
  } finally { loading.value = false }
}

async function startCamera() {
  if (cameraOn.value || cameraStarting.value) return
  if (!cameraSupported) { feedback.value = '当前浏览器不支持摄像头扫码，请使用输入框'; return }
  const request = ++cameraRequest
  cameraStarting.value = true
  try {
    detector = new window.BarcodeDetector({ formats: ['qr_code'] })
    const acquired = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' }, audio: false })
    if (request !== cameraRequest) { acquired.getTracks().forEach(track => track.stop()); return }
    stream = acquired
    cameraOn.value = true
    await nextTick()
    if (request !== cameraRequest || !video.value) { stopCamera(); return }
    video.value.srcObject = stream
    await video.value.play()
    if (request !== cameraRequest) return
    frame = window.setInterval(async () => {
      if (!video.value || loading.value || detecting || video.value.readyState < 2) return
      detecting = true
      try {
        const results = await detector.detect(video.value)
        if (request !== cameraRequest) return
        const value = results[0]?.rawValue
        if (value && value === lastRead) feedback.value = scanned.value ? '已识别该二维码' : '重复内容已忽略'
        else if (value) await scan(value)
      } catch { if (request === cameraRequest) feedback.value = '摄像头识别失败，请使用输入框' }
      finally { detecting = false }
    }, 350)
  } catch (e) {
    if (request === cameraRequest) {
      stopCamera()
      feedback.value = e.name === 'NotAllowedError' ? '未获得摄像头权限，请使用输入框' : '摄像头不可用，请使用输入框'
    }
  } finally { if (request === cameraRequest) cameraStarting.value = false }
}

function stopCamera() {
  cameraRequest++
  if (frame) window.clearInterval(frame)
  frame = null
  stream?.getTracks().forEach(track => track.stop())
  stream = null
  if (video.value) video.value.srcObject = null
  cameraOn.value = false
  cameraStarting.value = false
  lastRead = ''
}

async function searchCandidates() {
  try {
    const result = itemType.value === 'DEVICE'
      ? await api.devices({ keyword: keyword.value, page: 1, size: 100 })
      : await api.products({ code: keyword.value, page: 1, size: 100 })
    candidates.value = result.list
  } catch (e) { ElMessage.error(e.message) }
}

function changeType() { selected.value = []; labels.value = []; candidates.value = []; keyword.value = ''; searchCandidates() }
function choose(rows) { selected.value = rows.map(row => row.id) }
async function preview() {
  if (!canPrint.value) return
  if (!selected.value.length || selected.value.length > 30) { ElMessage.warning('请选择1至30个资源'); return }
  loading.value = true
  try { labels.value = await api.qrLabels(itemType.value, selected.value); mode.value = 'labels' }
  catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}
async function openOne(type, id) {
  if (!canGenerate.value) { ElMessage.error('没有生成二维码的权限'); return }
  loading.value = true
  try {
    itemType.value = type
    labels.value = [await api.qrLabel(type, id, true)]
    mode.value = 'labels'
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}
function printLabels() { if (labels.value.length && canPrint.value) window.print() }
function detailLink() {
  const data = scanned.value
  if (!data) return
  router.push(data.resourceType === 'DEVICE'
    ? { path: '/assets/devices', query: { id: data.device.id } }
    : { path: '/master-data/products', query: { id: data.product.id } })
}
watch(mode, value => { if (value !== 'scan') stopCamera() })
watch(() => route.fullPath, (value, old) => { if (value !== old) stopCamera() })
onMounted(() => {
  const ids = String(route.query.ids || '').split(',').map(Number).filter(id => Number.isSafeInteger(id) && id > 0)
  if (route.query.deviceId) openOne('DEVICE', Number(route.query.deviceId))
  else if (route.query.productId) openOne('PRODUCT', Number(route.query.productId))
  else if (ids.length && ids.length <= 30) {
    itemType.value = 'DEVICE'; selected.value = ids; preview()
  }
  if (route.query.content) { input.value = String(route.query.content).slice(0, 256); scan() }
})
onBeforeUnmount(stopCamera)
</script>

<template>
  <div class="qr-page" :style="labelStyle">
    <div class="page-heading screen-only"><div><h1>二维码与标签</h1><p>扫描并核对真实业务档案，或按现有尺寸规则预览打印标签。</p></div></div>
    <el-tabs v-model="mode" class="screen-only"><el-tab-pane label="扫码查询" name="scan" /><el-tab-pane label="标签预览" name="labels" /></el-tabs>

    <section v-if="mode === 'scan'" class="scan-panel screen-only">
      <div class="scan-intro"><strong>扫描查询</strong><span>支持二维码内容输入；摄像头识别取决于浏览器权限与支持情况。</span></div>
      <div class="scan-tools">
        <el-input v-model="input" maxlength="256" clearable placeholder="扫描二维码或输入 ERPQR1 内容后回车" @keyup.enter="scan()" />
        <el-button type="primary" :icon="Search" :loading="loading" @click="scan()">查询</el-button>
        <el-button v-if="cameraSupported && !cameraOn" :icon="Camera" :loading="cameraStarting" @click="startCamera">摄像头</el-button>
        <el-button v-if="cameraOn" :icon="Close" @click="stopCamera">停止</el-button>
      </div>
      <video v-if="cameraOn" ref="video" class="camera" autoplay muted playsinline />
      <el-alert v-if="feedback" :title="feedback" :type="scanned ? 'success' : 'warning'" :closable="false" />
      <template v-if="scanned">
        <div class="scan-result-heading"><h2>{{ scanned.resourceType === 'DEVICE' ? '设备' : '物资' }}档案</h2><el-button @click="detailLink">查看详情</el-button></div>
        <el-descriptions v-if="scanned.device" :column="2" border>
          <el-descriptions-item label="设备编号">{{ scanned.device.deviceCode }}</el-descriptions-item>
          <el-descriptions-item label="物资">{{ scanned.device.productName }}</el-descriptions-item>
          <el-descriptions-item label="SN">{{ scanned.device.serialNumber || '-' }}</el-descriptions-item>
          <el-descriptions-item label="MAC">{{ scanned.device.macAddress || '-' }}</el-descriptions-item>
          <el-descriptions-item label="资产号">{{ scanned.device.assetNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ scanned.device.status }}</el-descriptions-item>
          <el-descriptions-item label="使用人">{{ scanned.device.userName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="部门">{{ scanned.device.departmentName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="仓库">{{ scanned.device.warehouseName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="保修起止">{{ scanned.device.warrantyStartAt || '-' }} / {{ scanned.device.warrantyEndAt || '-' }}</el-descriptions-item>
        </el-descriptions>
        <template v-if="scanned.product">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="编码">{{ scanned.product.productCode }}</el-descriptions-item>
            <el-descriptions-item label="名称">{{ scanned.product.productName }}</el-descriptions-item>
            <el-descriptions-item label="分类">{{ scanned.product.categoryName }}</el-descriptions-item>
            <el-descriptions-item label="品牌">{{ scanned.product.brandName }}</el-descriptions-item>
            <el-descriptions-item label="类型">{{ scanned.product.productType }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ scanned.product.status ? '启用' : '停用' }}</el-descriptions-item>
          </el-descriptions>
          <el-table v-if="scanned.stocks?.list?.length" :data="scanned.stocks.list" class="stock-list">
            <el-table-column prop="warehouseName" label="仓库" /><el-table-column prop="quantity" label="库存" /><el-table-column prop="availableQuantity" label="可用" />
          </el-table>
        </template>
      </template>
    </section>

    <section v-if="mode === 'labels'" class="label-workspace">
      <div class="label-controls screen-only">
        <div class="scan-intro"><strong>标签生成</strong><span>选取现有设备或物资后生成预览；打印内容与尺寸规则保持不变。</span></div>
        <el-segmented v-model="itemType" :options="[{ label: '设备标签', value: 'DEVICE' }, { label: '物资标签', value: 'PRODUCT' }]" @change="changeType" />
        <div class="select-tools"><el-input v-model="keyword" clearable :placeholder="itemType === 'PRODUCT' ? '搜索物资编号' : '搜索设备编号或名称'" @keyup.enter="searchCandidates" /><el-button :icon="Search" @click="searchCandidates">查询</el-button><el-button type="primary" :disabled="!canPrint || !selected.length" :loading="loading" @click="preview">生成预览</el-button></div>
        <el-table :data="candidates" height="220" @selection-change="choose"><el-table-column type="selection" width="48" /><el-table-column :prop="itemType === 'DEVICE' ? 'deviceCode' : 'productCode'" label="编号" /><el-table-column prop="productName" label="名称" /></el-table>
        <div class="size-tools"><span>宽</span><el-input-number v-model="settings.width" :min="60" :max="100" :step="5" /><span>高</span><el-input-number v-model="settings.height" :min="35" :max="70" :step="5" /><span>二维码</span><el-input-number v-model="settings.qr" :min="18" :max="maxQrSize" :step="2" /><span>边距</span><el-input-number v-model="settings.margin" :min="2" :max="5" /><span>mm</span><el-button :icon="Printer" :disabled="!labels.length || !canPrint" @click="printLabels">打印</el-button></div>
      </div>
      <div v-if="labels.length" class="label-sheet">
        <article v-for="label in labels" :key="`${label.resourceType}-${label.resourceId}`" class="print-label">
          <img class="label-qr" :src="`data:image/svg+xml,${encodeURIComponent(label.svg)}`" alt="二维码" />
          <div class="label-copy"><strong>ERP 资产管理</strong><b>{{ label.code }}</b><span>{{ label.name }}</span><small>{{ label.detail || '' }}</small></div>
        </article>
      </div>
      <el-empty v-else class="screen-only" description="暂无标签预览" />
    </section>
  </div>
</template>

<style>
.qr-page .scan-panel { max-width: 940px; }
.qr-page .scan-panel, .qr-page .label-controls { padding: 20px; border: 1px solid var(--line); border-radius: var(--radius); background: #fff; }
.qr-page .scan-intro { display: grid; gap: 5px; margin-bottom: 16px; }
.qr-page .scan-intro strong { font-size: 15px; color: var(--text); }
.qr-page .scan-intro span { color: var(--text-muted); font-size: 12px; }
.qr-page .scan-tools, .qr-page .select-tools, .qr-page .size-tools { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin: 12px 0; }
.qr-page .scan-tools .el-input, .qr-page .select-tools .el-input { width: min(440px, 100%); }
.qr-page .camera { width: min(400px, 100%); aspect-ratio: 4 / 3; object-fit: cover; background: #16252b; }
.qr-page .scan-result-heading { display: flex; align-items: center; justify-content: space-between; margin: 20px 0 12px; }
.qr-page .scan-result-heading h2 { font-size: 18px; margin: 0; }
.qr-page .stock-list { margin-top: 16px; }
.qr-page .label-controls { max-width: 1000px; }
.qr-page .size-tools .el-input-number { width: 100px; }
.qr-page .label-sheet { display: flex; flex-wrap: wrap; align-items: flex-start; gap: 8mm; margin-top: 20px; }
.qr-page .print-label { box-sizing: border-box; display: flex; align-items: center; gap: 2mm; width: var(--label-width); height: var(--label-height); padding: var(--label-margin); border: 1px solid #ccd5d9; background: #fff; color: #172c35; overflow: hidden; break-inside: avoid; page-break-inside: avoid; }
.qr-page .label-qr { display: block; flex: none; width: var(--qr-size); height: var(--qr-size); image-rendering: crisp-edges; }
.qr-page .label-copy { display: flex; flex: 1; flex-direction: column; min-width: 0; overflow: hidden; line-height: 1.2; }
.qr-page .label-copy strong { font-size: 7pt; color: #124e78; }
.qr-page .label-copy b { font-size: 9pt; overflow-wrap: anywhere; }
.qr-page .label-copy span, .qr-page .label-copy small { font-size: 7pt; overflow-wrap: anywhere; }
@media print {
  @page { margin: 5mm; }
  body, #app, .app-shell, .app-main { background: #fff !important; margin: 0 !important; padding: 0 !important; min-height: 0 !important; }
  .app-aside, .app-header, .screen-only { display: none !important; }
  .app-shell, .app-shell > .el-container, .app-main { display: block !important; width: auto !important; height: auto !important; overflow: visible !important; }
  .qr-page .label-sheet { margin: 0; gap: 2mm; display: flex; flex-wrap: wrap; }
  .qr-page .print-label { border: none; break-inside: avoid; page-break-inside: avoid; box-shadow: none; }
}
</style>
