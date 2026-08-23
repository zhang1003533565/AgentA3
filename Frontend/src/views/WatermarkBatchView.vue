<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppTabBar from '../components/AppTabBar.vue'
import { addWatermarkHistory, createHistoryThumbnail } from '../utils/watermarkHistory'

const router = useRouter()
const route = useRoute()
const fileInput = ref(null)
const previewCanvas = ref(null)
const batchMode = ref('add')
const files = ref([])
const selectedFileIndex = ref(-1)
const batchWatermarkText = ref('团队素材')
const batchOpacity = ref(80)
const batchTile = ref(false)
const precision = ref('hd')
const compressIndex = ref(0)
const formatIndex = ref(0)
const processing = ref(false)
const processedFiles = ref([])
const toastMessage = ref('')
let toastTimer

const compressList = ['中压缩', '低压缩', '高压缩']
const formatList = ['JPG', 'PNG', 'WebP']
const fileCountText = computed(() => files.value.length ? `已导入 ${files.value.length} 张图片` : '支持 JPG、PNG、WebP')
const selectedFile = computed(() => files.value[selectedFileIndex.value] || null)
const canExport = computed(() => processedFiles.value.length > 0 && !processing.value)

let renderId = 0

const returnPath = computed(() => route.query.returnTo === '/ai-original/add' ? '/ai-original/add' : '/ai-original')

function showToast(message) {
  toastMessage.value = message
  clearTimeout(toastTimer)
  toastTimer = setTimeout(() => { toastMessage.value = '' }, 2400)
}

function chooseFiles() {
  fileInput.value?.click()
}

function handleFiles(event) {
  const selected = Array.from(event.target.files || []).filter((file) => file.type.startsWith('image/'))
  selected.forEach((file) => {
    files.value.push({ file, url: URL.createObjectURL(file), name: file.name })
  })
  if (selectedFileIndex.value === -1 && files.value.length) selectedFileIndex.value = 0
  processedFiles.value = []
  event.target.value = ''
}

function selectFile(index) {
  selectedFileIndex.value = index
}

function renderBatchPreview() {
  const canvas = previewCanvas.value
  const currentFile = selectedFile.value
  const currentRenderId = ++renderId
  if (!canvas || !currentFile) return

  const image = new Image()
  image.onload = () => {
    if (currentRenderId !== renderId) return
    const ratio = image.naturalWidth / image.naturalHeight || 1
    const maxWidth = 1000
    const maxHeight = 650
    let width = maxWidth
    let height = width / ratio
    if (height > maxHeight) {
      height = maxHeight
      width = height * ratio
    }

    canvas.width = Math.max(1, Math.round(width))
    canvas.height = Math.max(1, Math.round(height))
    const context = canvas.getContext('2d')
    if (!context) return
    context.clearRect(0, 0, canvas.width, canvas.height)
    context.drawImage(image, 0, 0, canvas.width, canvas.height)

    if (batchMode.value !== 'add' || !batchWatermarkText.value) return
    context.save()
    context.globalAlpha = batchOpacity.value / 100
    context.fillStyle = '#1e293b'
    context.font = `${Math.max(18, canvas.width / 34)}px Arial, "Microsoft YaHei", sans-serif`
    context.textBaseline = 'alphabetic'
    const offsetX = canvas.width / 2
    const offsetY = canvas.height / 2
    if (batchTile.value) {
      const stepX = Math.max(150, canvas.width / 4)
      const stepY = Math.max(100, canvas.height / 4)
      for (let x = -canvas.width; x < canvas.width * 2; x += stepX) {
        for (let y = -canvas.height; y < canvas.height * 2; y += stepY) {
          context.fillText(batchWatermarkText.value, x, y)
        }
      }
    } else {
      context.fillText(batchWatermarkText.value, offsetX, offsetY)
    }
    context.restore()
  }
  image.src = currentFile.url
}

function removeFile(index) {
  const [file] = files.value.splice(index, 1)
  if (file) URL.revokeObjectURL(file.url)
  if (!files.value.length) {
    selectedFileIndex.value = -1
  } else if (index < selectedFileIndex.value) {
    selectedFileIndex.value -= 1
  } else if (index === selectedFileIndex.value) {
    selectedFileIndex.value = Math.min(index, files.value.length - 1)
  }
  processedFiles.value = []
}

function getExportSettings() {
  const format = formatList[formatIndex.value]
  return {
    mimeType: { JPG: 'image/jpeg', PNG: 'image/png', WebP: 'image/webp' }[format],
    extension: format.toLowerCase(),
    quality: [0.92, 0.78, 0.58][compressIndex.value],
  }
}

function drawBatchWatermark(context, canvas) {
  if (batchMode.value !== 'add' || !batchWatermarkText.value) return
  context.save()
  context.globalAlpha = batchOpacity.value / 100
  context.fillStyle = '#1e293b'
  context.font = `${Math.max(18, canvas.width / 34)}px Arial, "Microsoft YaHei", sans-serif`
  context.textBaseline = 'alphabetic'
  if (batchTile.value) {
    const stepX = Math.max(150, canvas.width / 4)
    const stepY = Math.max(100, canvas.height / 4)
    for (let x = -canvas.width; x < canvas.width * 2; x += stepX) {
      for (let y = -canvas.height; y < canvas.height * 2; y += stepY) {
        context.fillText(batchWatermarkText.value, x, y)
      }
    }
  } else {
    context.fillText(batchWatermarkText.value, canvas.width / 2, canvas.height / 2)
  }
  context.restore()
}

function loadImage(file) {
  return new Promise((resolve, reject) => {
    const image = new Image()
    image.onload = () => resolve(image)
    image.onerror = () => reject(new Error(`无法读取图片：${file.name}`))
    image.src = file.url
  })
}

function getCanvasSize(image) {
  const ratio = image.naturalWidth / image.naturalHeight || 1
  const maxWidth = 1000
  const maxHeight = 650
  let width = maxWidth
  let height = width / ratio
  if (height > maxHeight) {
    height = maxHeight
    width = height * ratio
  }
  return { width: Math.max(1, Math.round(width)), height: Math.max(1, Math.round(height)) }
}

async function renderFileToBlob(file) {
  const image = await loadImage(file)
  const size = getCanvasSize(image)
  const canvas = document.createElement('canvas')
  canvas.width = size.width
  canvas.height = size.height
  const context = canvas.getContext('2d')
  if (!context) throw new Error('无法创建图片画布')
  context.drawImage(image, 0, 0, canvas.width, canvas.height)
  drawBatchWatermark(context, canvas)
  const { mimeType, quality } = getExportSettings()
  return new Promise((resolve, reject) => {
    canvas.toBlob((blob) => {
      if (blob) resolve(blob)
      else reject(new Error(`无法导出图片：${file.name}`))
    }, mimeType, quality)
  })
}

async function startBatch() {
  if (!files.value.length) {
    showToast('请先导入图片')
    return
  }
  if (processing.value) return
  processing.value = true
  processedFiles.value = []
  try {
    const blobs = await Promise.all(files.value.map((file) => renderFileToBlob(file)))
    processedFiles.value = files.value.map((file, index) => ({ file, blob: blobs[index] }))
    showToast('批量处理完成，可以导出')
  } catch (error) {
    showToast(error.message || '批量处理失败，请重试')
  } finally {
    processing.value = false
  }
}

async function downloadProcessed() {
  if (!canExport.value) {
    showToast('请先点击开始批量处理')
    return
  }
  const { extension } = getExportSettings()
  const format = formatList[formatIndex.value]
  processedFiles.value.forEach(({ file, blob }, index) => {
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `watermark-${index + 1}-${file.name.replace(/\.[^/.]+$/, '')}.${extension}`
    link.click()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  })
  const historyRecords = await Promise.all(
    processedFiles.value.map(async ({ blob }) => ({
      previewUrl: await createHistoryThumbnail(blob),
      format,
    })),
  )
  historyRecords.forEach((record) => addWatermarkHistory({ title: '图片加水印', ...record }))
  showToast(`已导出 ${processedFiles.value.length} 张图片`)
}

onBeforeUnmount(() => {
  clearTimeout(toastTimer)
  files.value.forEach((file) => URL.revokeObjectURL(file.url))
})

watch([selectedFileIndex, batchMode, batchWatermarkText, batchOpacity, batchTile, compressIndex, formatIndex], () => {
  processedFiles.value = []
  nextTick(renderBatchPreview)
})

onMounted(() => {
  nextTick(renderBatchPreview)
})
</script>

<template>
  <div class="feature-page batch-page">
    <AppTabBar />
    <main class="batch-content">
      <header class="batch-heading">
        <button class="back-button" type="button" aria-label="返回上一个页面" @click="router.push(returnPath)">
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m15 5-7 7 7 7" /></svg>
        </button>
        <div><h1>批量处理</h1></div>
      </header>

      <div class="batch-layout">
        <section class="feature-card batch-preview">
          <div class="preview-heading">
            <h2>图片预览</h2>
            <span v-if="selectedFile">{{ selectedFile.name }}</span>
          </div>
          <div class="preview-box">
            <canvas v-if="selectedFile" ref="previewCanvas" class="preview-canvas" aria-label="批量水印预览"></canvas>
            <div v-else class="preview-empty">
              <svg viewBox="0 0 24 24" aria-hidden="true"><rect x="3.5" y="4" width="17" height="16" rx="2" /><circle cx="8.5" cy="9" r="1.5" /><path d="m5.5 17 4.5-4 3 2.5 2-2 3.5 3.5" /></svg>
              <span>选择右侧缩略图预览</span>
            </div>
          </div>
        </section>

        <section class="feature-card batch-card">
          <div class="feature-tabs">
            <button type="button" :class="{ active: batchMode === 'add' }" @click="batchMode = 'add'">批量加水印</button>
            <button type="button" :class="{ active: batchMode === 'remove' }" @click="batchMode = 'remove'">批量去水印</button>
          </div>

        <input ref="fileInput" class="hidden-input" type="file" accept="image/*" multiple @change="handleFiles" />
        <button class="upload-box" type="button" @click="chooseFiles">
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 16V4m0 0L7 9m5-5 5 5" /><path d="M5 14v5h14v-5" /></svg>
          <strong>导入多张图片</strong><span>{{ fileCountText }}</span>
        </button>

        <div v-if="files.length" class="image-grid">
          <div v-for="(file, index) in files" :key="file.url" class="image-item" :class="{ selected: selectedFileIndex === index }" role="button" tabindex="0" @click="selectFile(index)" @keydown.enter="selectFile(index)">
            <img :src="file.url" :alt="file.name" />
            <button type="button" aria-label="移除图片" @click.stop="removeFile(index)">×</button>
          </div>
          <button class="image-add" type="button" aria-label="继续添加图片" @click="chooseFiles">+</button>
        </div>

        <section class="setting-panel">
          <h2>统一参数</h2>
          <template v-if="batchMode === 'add'">
            <label class="form-field"><span>水印文字</span><input v-model="batchWatermarkText" class="feature-input" placeholder="请输入水印文字" /></label>
            <label class="form-field"><span class="field-heading"><span>透明度</span><b>{{ batchOpacity }}%</b></span><input v-model.number="batchOpacity" class="range-input" type="range" min="0" max="100" /></label>
            <label class="checkbox-field"><span>重复平铺</span><input v-model="batchTile" type="checkbox" /></label>
          </template>
          <template v-else>
            <span class="form-label">修复精度</span>
            <div class="precision-row"><button type="button" :class="{ active: precision === 'fast' }" @click="precision = 'fast'">快速</button><button type="button" :class="{ active: precision === 'hd' }" @click="precision = 'hd'">高清</button></div>
          </template>
        </section>

        <button class="start-button" type="button" :disabled="processing" @click="startBatch">{{ processing ? '处理中…' : '开始批量处理' }}</button>

        <section class="export-section">
          <h2>处理完成后导出</h2>
          <div class="export-row"><select v-model.number="compressIndex" class="feature-select" aria-label="压缩设置"><option v-for="(item, index) in compressList" :key="item" :value="index">{{ item }}</option></select><select v-model.number="formatIndex" class="feature-select" aria-label="图片格式"><option v-for="(item, index) in formatList" :key="item" :value="index">{{ item }}</option></select><button class="export-button" type="button" :disabled="!canExport" @click="downloadProcessed">导出保存</button></div>
        </section>
        </section>
      </div>
    </main>
    <transition name="toast"><div v-if="toastMessage" class="editor-toast" role="status">{{ toastMessage }}</div></transition>
  </div>
</template>

<style scoped>
.batch-page { min-height: 100vh; padding-top: 60px; background: #f4f7fb; }
.batch-content { width: min(1440px, calc(100% - 40px)); margin: 0 auto; padding: 26px 0 48px; }
.batch-heading { display: flex; align-items: center; gap: 12px; margin-bottom: 22px; }
.batch-heading h1, .setting-panel h2, .export-section h2 { margin: 0; }
.batch-heading h1 { color: #17233a; font-size: 27px; font-weight: 800; }
.back-button { display: grid; width: 40px; height: 40px; flex: 0 0 40px; place-items: center; border: 1px solid #dbe3eb; border-radius: 8px; color: #334155; background: #fff; }
.back-button svg { width: 21px; height: 21px; fill: none; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 2; }
.batch-layout { display: grid; grid-template-columns: minmax(0, 1.25fr) minmax(420px, 0.75fr); align-items: stretch; gap: 20px; }
.batch-preview, .batch-card { height: calc(100vh - 170px); min-height: 0; padding: 22px; }
.batch-preview { display: flex; flex-direction: column; }
.preview-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 18px; }
.preview-heading h2 { margin: 0; color: #23344a; font-size: 19px; font-weight: 800; }
.preview-heading span { max-width: 52%; overflow: hidden; color: #718096; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.preview-box { position: relative; display: grid; flex: 1; min-height: 0; place-items: center; overflow: hidden; border: 1px solid #dde5ec; border-radius: 9px; background: #e2e8f0; }
.preview-canvas { display: block; max-width: 100%; max-height: 100%; object-fit: contain; }
.preview-empty { display: grid; place-items: center; gap: 12px; color: #8191a4; font-size: 14px; }
.preview-empty svg { width: 48px; height: 48px; fill: none; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 1.5; }
.batch-card { overflow-y: auto; scrollbar-width: thin; }
.feature-tabs { display: flex; gap: 4px; padding: 4px; border: 1px solid #e0e6ec; border-radius: 9px; background: #f4f7fa; }
.feature-tabs button { flex: 1; min-height: 36px; border-radius: 6px; color: #65758a; background: transparent; font-weight: 700; }
.feature-tabs button.active { color: #294966; background: #fff; box-shadow: 0 2px 8px rgba(30,43,76,.08); }
.hidden-input { display: none; }
.upload-box { display: grid; width: 100%; min-height: 150px; place-content: center; justify-items: center; gap: 8px; margin-top: 20px; border: 1px dashed #cbd5e1; border-radius: 10px; color: #64748b; background: #fafbfd; }
.upload-box svg { width: 34px; height: 34px; fill: none; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 1.7; }
.upload-box strong { color: #42566b; font-size: 15px; }.upload-box span { color: #94a3b8; font-size: 12px; }
.image-grid { display: grid; grid-template-columns: repeat(5, 1fr); gap: 10px; margin-top: 16px; }
.image-item, .image-add { position: relative; display: grid; aspect-ratio: 1; place-items: center; overflow: hidden; border-radius: 8px; background: #f1f5f9; }
.image-item { cursor: pointer; outline: 2px solid transparent; outline-offset: 2px; }.image-item.selected { outline-color: #315f8c; }.image-item img { width: 100%; height: 100%; object-fit: cover; }.image-item button { position: absolute; top: 4px; right: 4px; width: 24px; height: 24px; border-radius: 50%; color: #fff; background: rgba(15,23,42,.65); }
.image-add { border: 1px dashed #cbd5e1; color: #94a3b8; background: #fff; font-size: 25px; }
.setting-panel { display: grid; gap: 14px; margin-top: 24px; padding: 20px; border-radius: 10px; background: #f8fafc; }.setting-panel h2, .export-section h2 { color: #23344a; font-size: 16px; font-weight: 800; }
.form-field { display: grid; gap: 7px; color: #42566b; font-size: 13px; font-weight: 700; }.field-heading, .checkbox-field { display: flex; align-items: center; justify-content: space-between; gap: 12px; }.field-heading b { color: #26384d; font-size: 12px; }
.feature-input, .feature-select { width: 100%; height: 40px; border: 1px solid #d7e0e8; border-radius: 8px; outline: 0; color: #26384d; background: #fff; }.feature-input { padding: 0 12px; }.feature-select { padding: 0 10px; }.range-input { width: 100%; height: 18px; accent-color: #315f8c; }.checkbox-field { min-height: 32px; color: #42566b; font-size: 13px; font-weight: 700; }.checkbox-field input { width: 17px; height: 17px; accent-color: #315f8c; }.form-label { color: #42566b; font-size: 13px; font-weight: 700; }
.precision-row { display: flex; gap: 8px; }.precision-row button { flex: 1; min-height: 40px; border: 1px solid #e2e8f0; border-radius: 8px; color: #64748b; background: #fff; font-weight: 700; }.precision-row button.active { border-color: #1e293b; color: #fff; background: #1e293b; }
.start-button { width: 100%; min-height: 42px; margin-top: 20px; border-radius: 8px; color: #fff; background: #1e293b; font-weight: 800; }.start-button:disabled { cursor: wait; opacity: .65; }.export-section { margin-top: 24px; padding-top: 20px; border-top: 1px solid #e2e8f0; }.export-row { display: grid; grid-template-columns: 1fr 1fr 1.1fr; gap: 10px; margin-top: 12px; }.export-button { min-height: 40px; border-radius: 8px; color: #fff; background: #315f8c; font-weight: 800; }.export-button:disabled { cursor: not-allowed; opacity: .45; }
.editor-toast { position: fixed; left: 50%; bottom: 28px; z-index: 1200; padding: 10px 16px; border-radius: 8px; color: #fff; background: rgba(15,23,42,.9); font-size: 13px; transform: translateX(-50%); }.toast-enter-active, .toast-leave-active { transition: opacity .18s ease, transform .18s ease; }.toast-enter-from, .toast-leave-to { opacity: 0; transform: translate(-50%, 8px); }
@media (max-width: 980px) { .batch-layout { grid-template-columns: 1fr; }.batch-preview, .batch-card { height: auto; }.batch-preview { min-height: 520px; }.batch-card { overflow: visible; } }
@media (max-width: 620px) { .batch-content { width: min(100% - 24px, 560px); padding-top: 18px; }.batch-preview, .batch-card { padding: 16px; }.batch-preview { min-height: 420px; }.image-grid { grid-template-columns: repeat(4, 1fr); }.export-row { grid-template-columns: 1fr 1fr; }.export-button { grid-column: 1 / -1; } }
</style>
