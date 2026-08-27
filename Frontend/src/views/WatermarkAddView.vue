<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import AppTabBar from '../components/AppTabBar.vue'
import { addWatermarkHistory, createHistoryThumbnail } from '../utils/watermarkHistory'

const router = useRouter()
const canvasRef = ref(null)
const fileInput = ref(null)
const image = ref(null)
const imageUrl = ref('')
const imageWidth = ref(0)
const imageHeight = ref(0)
const canvasWidth = ref(900)
const canvasHeight = ref(560)
const tabType = ref('text')
const targetColorType = ref('text')
const colorPopupShow = ref(false)
const textColor = ref('#1e293b')
const shapeColor = ref('#1e293b')
const tempColor = ref('#1e293b')
const fontIndex = ref(0)
const shapeIndex = ref(0)
const posIndex = ref(0)
const compressIndex = ref(1)
const formatIndex = ref(0)
const toastMessage = ref('')
let toastTimer

const fontList = ['黑体', '宋体', '楷体', '圆体']
const shapeList = ['矩形', '圆形', '斜线条纹']
const posList = ['居中', '左上', '右下']
const compressList = ['低压缩', '中压缩', '高压缩']
const formatList = ['JPG', 'PNG', 'WebP']
const presetColors = ['#1e293b', '#ef4444', '#fbbf24', '#10b981', '#3b82f6']
const fontFamilyList = [
  'Arial, "Microsoft YaHei", sans-serif',
  'SimSun, serif',
  'KaiTi, serif',
  'Microsoft YaHei, sans-serif',
]

const form = reactive({
  text: '仅供预览',
  fontSize: 18,
  opacity: 60,
  strokeWidth: 2,
  scale: 100,
  rotate: -25,
  repeat: false,
})

const hasImage = computed(() => Boolean(image.value))
const canvasStyle = computed(() => ({
  width: `${canvasWidth.value}px`,
  height: `${canvasHeight.value}px`,
}))

function showToast(message) {
  toastMessage.value = message
  clearTimeout(toastTimer)
  toastTimer = setTimeout(() => {
    toastMessage.value = ''
  }, 2400)
}

function triggerImagePicker() {
  fileInput.value?.click()
}

function handleFileChange(event) {
  const [file] = event.target.files || []
  if (!file) return
  if (!file.type.startsWith('image/')) {
    showToast('请选择图片文件')
    event.target.value = ''
    return
  }

  const nextUrl = URL.createObjectURL(file)
  const nextImage = new Image()
  nextImage.onload = () => {
    if (imageUrl.value) URL.revokeObjectURL(imageUrl.value)
    imageUrl.value = nextUrl
    image.value = nextImage
    imageWidth.value = nextImage.naturalWidth
    imageHeight.value = nextImage.naturalHeight
    resizeCanvas(nextImage.naturalWidth, nextImage.naturalHeight)
    event.target.value = ''
    nextTick(renderWatermark)
  }
  nextImage.onerror = () => {
    URL.revokeObjectURL(nextUrl)
    showToast('图片读取失败，请重试')
    event.target.value = ''
  }
  nextImage.src = nextUrl
}

function resizeCanvas(width, height) {
  const ratio = width / height || 1
  const maxWidth = 1000
  const maxHeight = 600
  let nextWidth = maxWidth
  let nextHeight = nextWidth / ratio
  if (nextHeight > maxHeight) {
    nextHeight = maxHeight
    nextWidth = nextHeight * ratio
  }
  canvasWidth.value = Math.max(1, Math.round(nextWidth))
  canvasHeight.value = Math.max(1, Math.round(nextHeight))
}

function renderWatermark() {
  const canvas = canvasRef.value
  const currentImage = image.value
  if (!canvas || !currentImage) return

  const width = canvasWidth.value
  const height = canvasHeight.value
  if (canvas.width !== width) canvas.width = width
  if (canvas.height !== height) canvas.height = height

  const ctx = canvas.getContext('2d')
  if (!ctx) return
  ctx.clearRect(0, 0, width, height)
  ctx.drawImage(currentImage, 0, 0, width, height)

  const scale = width / 750
  const alpha = form.opacity / 100

  ctx.save()
  ctx.globalAlpha = alpha

  if (tabType.value === 'text') {
    const fontSize = form.fontSize * scale
    ctx.fillStyle = textColor.value
    ctx.font = `${fontSize}px ${fontFamilyList[fontIndex.value]}`
    ctx.textBaseline = 'alphabetic'
    const stepX = Math.max(120 * scale, form.fontSize * 6 * scale)
    const stepY = Math.max(80 * scale, form.fontSize * 4 * scale)
    if (form.repeat) {
      const centerX = width / 2
      const centerY = height / 2
      let patternOffsetX = 0
      let patternOffsetY = 0
      if (posIndex.value === 1) {
        patternOffsetX = -width * 0.35
        patternOffsetY = -height * 0.35
      } else if (posIndex.value === 2) {
        patternOffsetX = width * 0.35
        patternOffsetY = height * 0.35
      }
      ctx.translate(centerX, centerY)
      ctx.rotate(form.rotate * Math.PI / 180)
      for (let x = -width; x < width; x += stepX) {
        for (let y = -height; y < height; y += stepY) {
          ctx.fillText(form.text, x + patternOffsetX, y + patternOffsetY)
        }
      }
    } else {
      const textWidth = ctx.measureText(form.text).width
      const padding = 24 * scale
      let anchorX = width / 2
      let anchorY = height / 2
      if (posIndex.value === 1) {
        anchorX = padding + textWidth / 2
        anchorY = padding + fontSize / 2
      } else if (posIndex.value === 2) {
        anchorX = width - padding - textWidth / 2
        anchorY = height - padding - fontSize / 2
      }
      ctx.translate(anchorX, anchorY)
      ctx.rotate(form.rotate * Math.PI / 180)
      ctx.fillText(form.text, -textWidth / 2, fontSize * 0.35)
    }
  } else {
    const size = 60 * (form.scale / 100) * scale
    const padding = 24 * scale
    let anchorX = width / 2
    let anchorY = height / 2
    if (posIndex.value === 1) {
      anchorX = padding + size / 2
      anchorY = padding + size / 2
    } else if (posIndex.value === 2) {
      anchorX = width - padding - size / 2
      anchorY = height - padding - size / 2
    }
    ctx.fillStyle = shapeColor.value
    ctx.strokeStyle = shapeColor.value
    ctx.lineWidth = form.strokeWidth * scale
    ctx.translate(anchorX, anchorY)
    ctx.rotate(form.rotate * Math.PI / 180)
    if (shapeIndex.value === 0) {
      ctx.fillRect(-size / 2, -size / 2, size, size)
    } else if (shapeIndex.value === 1) {
      ctx.beginPath()
      ctx.arc(0, 0, size / 2, 0, 2 * Math.PI)
      ctx.fill()
    } else {
      drawStripeShape(ctx, 0, 0, size)
    }
  }

  ctx.restore()
}

function drawStripeShape(ctx, x, y, size) {
  const half = size / 2
  ctx.save()
  ctx.beginPath()
  ctx.rect(x - half, y - half, size, size)
  ctx.clip()
  ctx.lineWidth = Math.max(1, form.strokeWidth * (canvasWidth.value / 750))
  for (let offset = -size; offset <= size * 2; offset += Math.max(8, size / 5)) {
    ctx.beginPath()
    ctx.moveTo(x - half + offset, y + half)
    ctx.lineTo(x + half + offset, y - half)
    ctx.stroke()
  }
  ctx.restore()
}

function switchTab(type) {
  tabType.value = type
}

function openColorPopup(type) {
  targetColorType.value = type
  tempColor.value = type === 'text' ? textColor.value : shapeColor.value
  colorPopupShow.value = true
}

function selectPreset(color) {
  tempColor.value = color
}

function confirmColor() {
  if (targetColorType.value === 'text') textColor.value = tempColor.value
  else shapeColor.value = tempColor.value
  colorPopupShow.value = false
}

function closeColorPopup() {
  colorPopupShow.value = false
}

function onRotateInput(event) {
  const value = event.target.value.replace(/[^0-9-]/g, '')
  if (value !== '' && !Number.isNaN(Number(value))) form.rotate = Number(value)
}

function handleDownload() {
  if (!image.value) {
    showToast('请先上传图片')
    return
  }

  renderWatermark()
  const mimeTypes = { JPG: 'image/jpeg', PNG: 'image/png', WebP: 'image/webp' }
  const extension = formatList[formatIndex.value].toLowerCase()
  const quality = [0.92, 0.78, 0.58][compressIndex.value]
  canvasRef.value.toBlob((blob) => {
    if (!blob) {
      showToast('图片导出失败，请重试')
      return
    }
    const downloadUrl = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = downloadUrl
    link.download = `watermark-${Date.now()}.${extension}`
    link.click()
    URL.revokeObjectURL(downloadUrl)
    createHistoryThumbnail(blob)
      .then((previewUrl) => addWatermarkHistory({ title: '图片加水印', format: formatList[formatIndex.value], previewUrl }))
      .catch(() => {})
    showToast('图片保存成功！')
  }, mimeTypes[formatList[formatIndex.value]], quality)
}

function goBatch() {
  router.push({ path: '/ai-original/batch', query: { returnTo: '/ai-original/add' } })
}

watch(
  [tabType, textColor, shapeColor, fontIndex, shapeIndex, posIndex, () => form.text, () => form.fontSize, () => form.opacity, () => form.strokeWidth, () => form.scale, () => form.rotate, () => form.repeat],
  renderWatermark,
)

onMounted(() => {
  nextTick(renderWatermark)
})

onBeforeUnmount(() => {
  clearTimeout(toastTimer)
  if (imageUrl.value) URL.revokeObjectURL(imageUrl.value)
})
</script>

<template>
  <div class="feature-page watermark-editor-page">
    <AppTabBar />

    <main class="watermark-editor">
      <header class="editor-heading">
        <button class="back-button" type="button" aria-label="返回水印工具" @click="router.push('/ai-original')">
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m15 5-7 7 7 7" /></svg>
        </button>
        <div>
          <h1>加水印编辑</h1>
        </div>
      </header>

      <div class="editor-layout">
        <section class="preview-panel feature-card">
          <div class="panel-heading">
            <div>
              <h2>图片预览</h2>
            </div>
          </div>
          <input ref="fileInput" class="hidden-input" type="file" accept="image/*" @change="handleFileChange" />
          <button class="canvas-shell" type="button" aria-label="点击上传图片" @click="triggerImagePicker">
            <canvas ref="canvasRef" class="watermark-canvas" :style="canvasStyle" :width="canvasWidth" :height="canvasHeight"></canvas>
            <span v-if="!hasImage" class="empty-tip">
              <svg viewBox="0 0 24 24" aria-hidden="true"><rect x="3.5" y="4" width="17" height="16" rx="2" /><circle cx="8.5" cy="9" r="1.5" /><path d="m5.5 17 4.5-4 3 2.5 2-2 3.5 3.5" /></svg>
              <span>点击此处上传图片</span>
            </span>
          </button>
          <p v-if="hasImage" class="image-meta">{{ imageWidth }} × {{ imageHeight }} px</p>
        </section>

        <aside class="settings-panel feature-card">
          <div class="panel-heading panel-heading--compact">
            <div>
              <h2>水印参数</h2>
            </div>
          </div>

          <div class="feature-tabs" role="tablist" aria-label="水印类型">
            <button type="button" :class="{ active: tabType === 'text' }" role="tab" :aria-selected="tabType === 'text'" @click="switchTab('text')">文字水印</button>
            <button type="button" :class="{ active: tabType === 'shape' }" role="tab" :aria-selected="tabType === 'shape'" @click="switchTab('shape')">形状水印</button>
          </div>

          <div v-if="tabType === 'text'" class="parameter-section">
            <label class="form-field">
              <span>水印文字</span>
              <input v-model="form.text" class="feature-input" placeholder="请输入水印文字" />
            </label>
            <label class="form-field">
              <span>字体</span>
              <select v-model.number="fontIndex" class="feature-select">
                <option v-for="(font, index) in fontList" :key="font" :value="index">{{ font }}</option>
              </select>
            </label>
            <label class="form-field">
              <span class="field-heading"><span>字号</span><b>{{ form.fontSize }} px</b></span>
              <input v-model.number="form.fontSize" class="range-input" type="range" min="12" max="48" />
            </label>
            <button class="color-field" type="button" @click="openColorPopup('text')">
              <span>字体颜色</span><i :style="{ backgroundColor: textColor }"></i>
            </button>
            <label class="form-field">
              <span>透明度</span>
              <input v-model.number="form.opacity" class="range-input" type="range" min="0" max="100" />
            </label>
          </div>

          <div v-else class="parameter-section">
            <label class="form-field">
              <span>形状选择</span>
              <select v-model.number="shapeIndex" class="feature-select">
                <option v-for="(shape, index) in shapeList" :key="shape" :value="index">{{ shape }}</option>
              </select>
            </label>
            <button class="color-field" type="button" @click="openColorPopup('shape')">
              <span>填充颜色</span><i :style="{ backgroundColor: shapeColor }"></i>
            </button>
            <label class="form-field">
              <span class="field-heading"><span>描边粗细</span><b>{{ form.strokeWidth }} px</b></span>
              <input v-model.number="form.strokeWidth" class="range-input" type="range" min="0" max="10" />
            </label>
            <label class="form-field">
              <span class="field-heading"><span>大小</span><b>{{ form.scale }}%</b></span>
              <input v-model.number="form.scale" class="range-input" type="range" min="20" max="200" />
            </label>
          </div>

          <section class="parameter-section common-parameters">
            <div class="two-col">
              <label class="form-field">
                <span>位置调节</span>
                <select v-model.number="posIndex" class="feature-select">
                  <option v-for="(position, index) in posList" :key="position" :value="index">{{ position }}</option>
                </select>
              </label>
              <label class="form-field">
                <span>旋转角度</span>
                <input class="feature-input" :value="`${form.rotate}°`" @input="onRotateInput" />
              </label>
            </div>
            <label class="checkbox-field">
              <span>重复平铺</span>
              <input v-model="form.repeat" type="checkbox" />
            </label>
          </section>

          <button class="batch-button" type="button" @click="goBatch">切换批量处理</button>

          <section class="export-section">
            <h3>导出设置</h3>
            <div class="export-row">
              <select v-model.number="compressIndex" class="feature-select" aria-label="压缩设置">
                <option v-for="(item, index) in compressList" :key="item" :value="index">{{ item }}</option>
              </select>
              <select v-model.number="formatIndex" class="feature-select" aria-label="图片格式">
                <option v-for="(item, index) in formatList" :key="item" :value="index">{{ item }}</option>
              </select>
              <button class="feature-button feature-button--primary" type="button" @click="handleDownload">下载保存</button>
            </div>
          </section>
        </aside>
      </div>
    </main>

    <div v-if="colorPopupShow" class="color-popup-mask" @click.self="closeColorPopup">
      <section class="color-popup-box" role="dialog" aria-modal="true" aria-label="选择颜色">
        <h2>选择颜色</h2>
        <div class="preset-color-wrap">
          <button v-for="color in presetColors" :key="color" class="preset-color-item" :class="{ selected: tempColor === color }" type="button" :aria-label="color" :style="{ backgroundColor: color }" @click="selectPreset(color)"></button>
        </div>
        <div class="popup-btn-row">
          <button class="popup-cancel" type="button" @click="closeColorPopup">取消</button>
          <button class="popup-ok" type="button" @click="confirmColor">完成</button>
        </div>
      </section>
    </div>

    <transition name="toast">
      <div v-if="toastMessage" class="editor-toast" role="status">{{ toastMessage }}</div>
    </transition>
  </div>
</template>

<style scoped>
.watermark-editor-page {
  min-height: 100vh;
  padding-top: 60px;
  background: #f4f7fb;
}

.watermark-editor {
  width: min(1440px, calc(100% - 40px));
  margin: 0 auto;
  padding: 26px 0 48px;
}

.editor-heading {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 22px;
}

.editor-heading h1,
.panel-heading h2,
.color-popup-box h2,
.export-section h3 {
  margin: 0;
}

.editor-heading h1 {
  color: #17233a;
  font-size: 27px;
  font-weight: 800;
}

.back-button {
  display: grid;
  width: 40px;
  height: 40px;
  flex: 0 0 40px;
  place-items: center;
  border: 1px solid #dbe3eb;
  border-radius: 8px;
  color: #334155;
  background: #ffffff;
}

.back-button:hover {
  background: #eef3f7;
}

.back-button svg {
  width: 21px;
  height: 21px;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 2;
}

.editor-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(420px, 0.75fr);
  align-items: start;
  gap: 20px;
}

.preview-panel,
.settings-panel {
  height: calc(100vh - 170px);
  min-height: 0;
  padding: 22px;
}

.preview-panel {
  display: flex;
  flex-direction: column;
}

.panel-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.panel-heading--compact {
  margin-bottom: 16px;
}

.panel-heading h2 {
  margin-top: 4px;
  color: #23344a;
  font-size: 19px;
  font-weight: 800;
}

.canvas-shell {
  position: relative;
  display: grid;
  width: 100%;
  min-height: 0;
  flex: 1;
  place-items: center;
  padding: 0;
  overflow: hidden;
  border: 1px solid #dde5ec;
  border-radius: 9px;
  color: #94a3b8;
  background: #e2e8f0;
}

.watermark-canvas {
  display: block;
  max-width: 100%;
  height: auto;
  object-fit: contain;
}

.empty-tip {
  position: absolute;
  inset: 0;
  display: grid;
  place-content: center;
  justify-items: center;
  gap: 12px;
  color: #7d8da0;
  font-size: 14px;
  pointer-events: none;
}

.empty-tip svg {
  width: 48px;
  height: 48px;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.5;
}

.hidden-input {
  display: none;
}

.image-meta {
  margin: 12px 0 0;
  color: #718096;
  font-size: 12px;
  text-align: right;
}

.settings-panel {
  position: sticky;
  top: 78px;
  overflow-y: auto;
  scrollbar-width: thin;
}

.feature-tabs {
  display: flex;
  gap: 4px;
  padding: 4px;
  border: 1px solid #e0e6ec;
  border-radius: 9px;
  background: #f4f7fa;
}

.feature-tabs button {
  flex: 1;
  min-height: 36px;
  padding: 0 12px;
  border-radius: 6px;
  color: #65758a;
  background: transparent;
  font-size: 13px;
  font-weight: 700;
}

.feature-tabs button.active {
  color: #294966;
  background: #ffffff;
  box-shadow: 0 2px 8px rgba(30, 43, 76, 0.08);
}

.parameter-section {
  display: grid;
  gap: 14px;
  margin-top: 18px;
  padding-top: 2px;
}

.form-field {
  display: grid;
  gap: 7px;
  color: #42566b;
  font-size: 13px;
  font-weight: 700;
}

.field-heading,
.color-field,
.checkbox-field {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.field-heading b {
  color: #26384d;
  font-size: 12px;
  font-weight: 700;
}

.feature-input,
.feature-select {
  width: 100%;
  height: 40px;
  border: 1px solid #d7e0e8;
  border-radius: 8px;
  outline: 0;
  color: #26384d;
  background: #ffffff;
}

.feature-input {
  padding: 0 12px;
}

.feature-select {
  padding: 0 10px;
}

.feature-input:focus,
.feature-select:focus {
  border-color: #6688a9;
  box-shadow: 0 0 0 3px rgba(82, 117, 151, 0.1);
}

.range-input {
  width: 100%;
  height: 18px;
  accent-color: #315f8c;
}

.color-field {
  width: 100%;
  min-height: 40px;
  padding: 0;
  color: #42566b;
  background: transparent;
  font-size: 13px;
  font-weight: 700;
  text-align: left;
}

.color-field i {
  width: 28px;
  height: 28px;
  border: 3px solid #ffffff;
  border-radius: 50%;
  box-shadow: 0 0 0 1px #cbd5e1;
}

.common-parameters {
  margin-top: 20px;
  padding-top: 18px;
  border-top: 1px solid #edf1f5;
}

.two-col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.checkbox-field {
  min-height: 34px;
  color: #42566b;
  font-size: 13px;
  font-weight: 700;
}

.checkbox-field input {
  width: 17px;
  height: 17px;
  accent-color: #315f8c;
}

.batch-button {
  width: 100%;
  min-height: 40px;
  margin-top: 20px;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  color: #34506c;
  background: #ffffff;
  font-size: 13px;
  font-weight: 700;
}

.batch-button:hover {
  background: #f6f9fb;
}

.export-section {
  margin-top: 20px;
  padding-top: 18px;
  border-top: 1px solid #e2e8f0;
}

.export-section h3 {
  color: #23344a;
  font-size: 15px;
  font-weight: 800;
}

.export-row {
  display: grid;
  grid-template-columns: 1fr 1fr 1.25fr;
  gap: 8px;
  margin-top: 12px;
}

.feature-button {
  min-height: 40px;
  padding: 0 15px;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  color: #34506c;
  background: #ffffff;
  font-size: 13px;
  font-weight: 700;
}

.feature-button--primary {
  border-color: #315f8c;
  color: #ffffff;
  background: #315f8c;
}

.feature-button:hover {
  filter: brightness(0.98);
}

.color-popup-mask {
  position: fixed;
  inset: 0;
  z-index: 1100;
  display: flex;
  align-items: flex-end;
  background: rgba(30, 41, 59, 0.4);
}

.color-popup-box {
  width: min(100%, 560px);
  margin: 0 auto;
  padding: 28px 30px 32px;
  border-radius: 16px 16px 0 0;
  background: #ffffff;
  box-shadow: 0 -8px 24px rgba(15, 23, 42, 0.12);
}

.color-popup-box h2 {
  color: #23344a;
  font-size: 17px;
  font-weight: 800;
  text-align: center;
}

.preset-color-wrap {
  display: flex;
  justify-content: center;
  gap: 20px;
  margin: 24px 0 28px;
}

.preset-color-item {
  width: 34px;
  height: 34px;
  padding: 0;
  border: 3px solid #ffffff;
  border-radius: 50%;
  box-shadow: 0 0 0 1px #cbd5e1;
}

.preset-color-item.selected {
  box-shadow: 0 0 0 2px #315f8c;
}

.popup-btn-row {
  display: flex;
  gap: 12px;
}

.popup-cancel,
.popup-ok {
  flex: 1;
  min-height: 42px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 700;
}

.popup-cancel {
  border: 1px solid #d7e0e8;
  color: #64748b;
  background: #ffffff;
}

.popup-ok {
  color: #ffffff;
  background: #1e293b;
}

.editor-toast {
  position: fixed;
  left: 50%;
  bottom: 28px;
  z-index: 1200;
  padding: 10px 16px;
  border-radius: 8px;
  color: #ffffff;
  background: rgba(15, 23, 42, 0.9);
  font-size: 13px;
  transform: translateX(-50%);
}

.toast-enter-active,
.toast-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translate(-50%, 8px);
}

@media (max-width: 980px) {
  .editor-layout {
    grid-template-columns: 1fr;
  }

  .settings-panel {
    position: static;
  }

  .preview-panel {
    height: auto;
  }
}

@media (max-width: 620px) {
  .watermark-editor {
    width: min(100% - 24px, 560px);
    padding-top: 18px;
  }

  .preview-panel,
  .settings-panel {
    padding: 16px;
  }

  .canvas-shell {
    min-height: 340px;
    flex: none;
  }

  .settings-panel {
    height: auto;
    overflow: visible;
  }

  .editor-heading h1 {
    font-size: 23px;
  }

  .export-row {
    grid-template-columns: 1fr 1fr;
  }

  .export-row .feature-button {
    grid-column: 1 / -1;
  }
}
</style>
