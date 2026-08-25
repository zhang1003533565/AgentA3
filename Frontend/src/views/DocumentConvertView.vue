<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppTabBar from '../components/AppTabBar.vue'
import {
  createTask,
  deleteConvertTasks,
  downloadResult,
  getHistory,
  getTask,
} from '../api/documentConvert'

const route = useRoute()
const router = useRouter()

// 6 种转换类型元数据（从 app 端 documentConvertDetail.vue 迁移）
const TYPE_META = {
  pdf_to_docx: {
    label: 'PDF → Word',
    desc: 'PDF 转 Word 文档',
    iconText: 'Word',
    color: '#5C7A99',
    accept: ['.pdf'],
    convertMode: 'reflow',
    modes: [
      { value: 'reflow', label: '智能编辑模式', description: '提取PDF文字生成可编辑Word，适合内容修改。' },
      { value: 'image', label: '高清还原模式', description: '完整保留PDF页面效果，适合展示和打印，但文字不可单独编辑。' },
    ],
  },
  ppt_to_docx: {
    label: 'PPT → Word',
    desc: 'PPT 转 Word 文档',
    iconText: 'Word',
    color: '#6B9B7A',
    accept: ['.pptx'],
    convertMode: 'reflow',
    modes: [
      { value: 'reflow', label: '智能编辑模式', description: '保留文字、图片、表格可编辑，适合内容修改。' },
      { value: 'image', label: '高清还原模式', description: '通过PDF中转保留PPT页面效果，适合展示。' },
    ],
  },
  pdf_to_ppt: {
    label: 'PDF → PPT',
    desc: 'PDF 转 PPT 演示文稿',
    iconText: 'PPT',
    color: '#8B7AB8',
    accept: ['.pdf'],
    convertMode: 'image',
    modes: [
      { value: 'image', label: '高清还原模式', description: '页面完整还原为PPT图片，适合展示，但文字不可单独编辑。' },
      { value: 'editable', label: '智能编辑模式', description: 'PDF文字和图片会解析成PPT元素，支持文字修改和图片移动，复杂排版可能存在差异。' },
    ],
  },
  ppt_to_pdf: {
    label: 'PPT → PDF',
    desc: 'PPT 转 PDF 文档',
    iconText: 'PDF',
    color: '#7A9BB8',
    accept: ['.ppt', '.pptx'],
    convertMode: '',
    modes: [],
  },
  docx_to_pdf: {
    label: 'Word → PDF',
    desc: 'Word 转 PDF 文档',
    iconText: 'PDF',
    color: '#B89B7A',
    accept: ['.docx'],
    convertMode: '',
    modes: [],
  },
  docx_to_ppt: {
    label: 'Word → PPT',
    desc: 'Word 转 PPT 演示文稿',
    iconText: 'PPT',
    color: '#A67B7B',
    accept: ['.docx'],
    convertMode: 'smart',
    modes: [
      { value: 'smart', label: '智能生成模式', badge: '推荐', description: '解析标题、段落、图片和表格，生成可编辑的汇报型 PPT，适合做演示。' },
      { value: 'image', label: '高清还原模式', description: '保持 Word 原页面效果，每页一张图片，文字不可单独编辑，适合不改内容直接展示。' },
    ],
  },
}

const typeKeys = Object.keys(TYPE_META)

const STATUS_TEXT = {
  QUEUED: '等待处理',
  RUNNING: '正在转换',
  SUCCEEDED: '转换完成',
  FAILED: '转换失败',
}

const activeType = ref('docx_to_pdf')
const selectedFile = ref(null)
const selectedMode = ref('')
const taskId = ref('')
const taskStatus = ref('')
const progress = ref(0)
const progressMessage = ref('')
const errorMessage = ref('')
const result = ref({ fileName: '', fileSize: 0 })
const historyList = ref([])
const selectedIds = ref([])
const processing = ref(false)

let pollTimer = null
let submitLocked = false
let pollingErrorCount = 0

const currentMeta = computed(() => TYPE_META[activeType.value] || TYPE_META.docx_to_pdf)
const acceptAttr = computed(() => currentMeta.value.accept.join(','))
const acceptText = computed(() =>
  currentMeta.value.accept.map((item) => String(item).replace(/^\./, '').toUpperCase()).join(' / '),
)
const canStart = computed(() => !!selectedFile.value && !processing.value)
const showProgress = computed(() => processing.value || ['SUCCEEDED', 'FAILED'].includes(taskStatus.value))
const statusText = computed(() => STATUS_TEXT[taskStatus.value] || '等待处理')
const activeModeDesc = computed(() => {
  const mode = currentMeta.value.modes.find((item) => item.value === selectedMode.value)
  return mode ? mode.description : ''
})

const friendlyError = (raw) => {
  const text = String(raw || '').trim()
  if (!text) return '转换失败，请稍后重试'
  const lower = text.toLowerCase()
  if (/(databufferlimitexception|exceeded limit|max bytes|maximum allowed|文件过大|超过.*大小|too large|size limit)/.test(lower)) {
    return '文件过大，暂时无法处理，请尝试压缩文件或减少页数'
  }
  if (/(unsupported|not supported|invalid format|格式不支持|不支持.*格式|仅支持转换|无法识别.*格式|无效的转换类型)/.test(lower)) {
    return '暂不支持该文件格式，请选择支持的文件'
  }
  if (/(timeout|connection|ai服务调用失败|exception|服务异常|超时|暂时异常)/.test(lower)) {
    return '转换服务暂时异常，请稍后重试'
  }
  return text.length > 60 ? `${text.slice(0, 60)}...` : text
}

const statusClass = (status) => {
  const map = { SUCCEEDED: 'feature-status--completed', FAILED: 'feature-status--failed', RUNNING: 'feature-status--running', QUEUED: 'feature-status--pending' }
  return map[status] || 'feature-status--pending'
}

const switchType = (key) => {
  if (!TYPE_META[key] || key === activeType.value) return
  activeType.value = key
  selectedMode.value = TYPE_META[key].convertMode || ''
  resetState()
  router.replace({ query: { type: key } })
  loadHistory()
}

const resetState = () => {
  stopPolling()
  selectedFile.value = null
  taskId.value = ''
  taskStatus.value = ''
  progress.value = 0
  progressMessage.value = ''
  errorMessage.value = ''
  result.value = { fileName: '', fileSize: 0 }
  selectedIds.value = []
  processing.value = false
}

const onFileChange = (event) => {
  const file = event.target.files?.[0]
  if (!file) return
  selectedFile.value = file
  errorMessage.value = ''
  taskStatus.value = ''
  progress.value = 0
  progressMessage.value = ''
  result.value = { fileName: '', fileSize: 0 }
}

const startConvert = async () => {
  if (!selectedFile.value) return
  if (processing.value || submitLocked) return
  submitLocked = true

  taskId.value = ''
  taskStatus.value = 'QUEUED'
  progress.value = 0
  progressMessage.value = '等待处理'
  errorMessage.value = ''
  result.value = { fileName: '', fileSize: 0 }
  processing.value = true

  try {
    const res = await createTask(selectedFile.value, activeType.value, selectedMode.value)
    const data = res.data || {}
    if (!data.taskId) throw new Error('创建任务失败')
    taskId.value = data.taskId
    taskStatus.value = data.status || 'QUEUED'
    progress.value = data.progress || 0
    progressMessage.value = data.message || '等待处理'
    startPolling(data.taskId)
  } catch (error) {
    processing.value = false
    taskStatus.value = 'FAILED'
    errorMessage.value = friendlyError(error?.message || '创建任务失败')
  } finally {
    submitLocked = false
  }
}

const startPolling = (id) => {
  stopPolling()
  pollTimer = setInterval(async () => {
    try {
      const res = await getTask(id)
      pollingErrorCount = 0
      const data = res.data || {}
      taskStatus.value = data.status || taskStatus.value
      progress.value = data.progress || 0
      progressMessage.value = data.message || STATUS_TEXT[data.status] || ''

      if (data.status === 'SUCCEEDED') {
        stopPolling()
        processing.value = false
        progress.value = 100
        result.value = { fileName: data.resultFileName || '', fileSize: data.resultFileSize || 0 }
        loadHistory()
      } else if (data.status === 'FAILED') {
        stopPolling()
        processing.value = false
        errorMessage.value = friendlyError(data.errorMessage || '转换失败')
      }
    } catch {
      pollingErrorCount += 1
      if (pollingErrorCount >= 5) {
        stopPolling()
        processing.value = false
        errorMessage.value = '网络暂时异常，请稍后刷新查看任务状态'
      }
    }
  }, 1500)
}

const stopPolling = () => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

const loadHistory = async () => {
  try {
    const res = await getHistory(activeType.value, 1, 20)
    historyList.value = (res.data && res.data.records) || []
    selectedIds.value = []
  } catch {
    historyList.value = []
    selectedIds.value = []
  }
}

const isSelected = (id) => selectedIds.value.includes(id)

const toggleSelect = (id) => {
  const index = selectedIds.value.indexOf(id)
  if (index >= 0) selectedIds.value.splice(index, 1)
  else selectedIds.value.push(id)
}

const batchDelete = async () => {
  if (!selectedIds.value.length) return
  if (!window.confirm('确定删除选中的转换记录吗？删除后无法恢复')) return
  try {
    await deleteConvertTasks(selectedIds.value)
    selectedIds.value = []
    loadHistory()
  } catch (error) {
    errorMessage.value = friendlyError(error?.message || '删除失败')
  }
}

const openFile = async (id, fileName) => {
  if (!id) return
  try {
    const url = await downloadResult(id)
    window.open(url, '_blank')
  } catch (error) {
    errorMessage.value = friendlyError(error?.message || '打开失败')
  }
}

const saveFile = async (id, fileName) => {
  if (!id) return
  try {
    const url = await downloadResult(id)
    const link = document.createElement('a')
    link.href = url
    link.download = fileName || 'convert-result'
    document.body.appendChild(link)
    link.click()
    link.remove()
  } catch (error) {
    errorMessage.value = friendlyError(error?.message || '下载失败')
  }
}

const resetAndRetry = () => {
  resetState()
}

const formatSize = (bytes) => {
  if (bytes === null || bytes === undefined) return ''
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`
}

const formatTime = (time) => {
  if (!time) return ''
  return String(time).replace('T', ' ').slice(0, 19)
}

onMounted(() => {
  const type = String(route.query.type || '').trim()
  if (TYPE_META[type]) activeType.value = type
  selectedMode.value = currentMeta.value.convertMode || ''
  loadHistory()
})

onBeforeUnmount(() => {
  stopPolling()
})
</script>

<template>
  <div class="feature-page">
    <AppTabBar />

    <main class="convert">
      <!-- 左侧：类型导航 -->
      <aside class="feature-card convert-nav">
        <h2>格式转换</h2>
        <button
          v-for="key in typeKeys"
          :key="key"
          type="button"
          :class="{ active: activeType === key }"
          @click="switchType(key)"
        >
          <span class="convert-nav__badge" :style="{ color: TYPE_META[key].color, background: `${TYPE_META[key].color}1f` }">
            {{ TYPE_META[key].iconText }}
          </span>
          <span class="convert-nav__copy">
            <strong>{{ TYPE_META[key].label }}</strong>
            <small>{{ TYPE_META[key].desc }}</small>
          </span>
        </button>
      </aside>

      <!-- 中间：转换操作 -->
      <section class="feature-card convert-config">
        <header class="convert-config__head">
          <span>DOCUMENT CONVERT</span>
          <h1>{{ currentMeta.label }}</h1>
          <p>{{ currentMeta.desc }}</p>
        </header>

        <div v-if="currentMeta.modes.length" class="convert-field">
          <label>转换模式</label>
          <div class="feature-chip-row">
            <button
              v-for="mode in currentMeta.modes"
              :key="mode.value"
              type="button"
              class="feature-chip"
              :class="{ 'feature-chip--active': selectedMode === mode.value }"
              @click="selectedMode = mode.value"
            >
              {{ mode.label }}{{ mode.badge ? ` · ${mode.badge}` : '' }}
            </button>
          </div>
          <p class="convert-hint">{{ activeModeDesc }}</p>
        </div>

        <div class="convert-field">
          <label>选择文件</label>
          <label class="file-picker">
            <input type="file" :accept="acceptAttr" hidden @change="onFileChange" />
            <template v-if="!selectedFile">
              <span class="file-picker__plus">+</span>
              <span class="file-picker__text">点击选择文件</span>
              <span class="file-picker__hint">支持 {{ acceptText }} 格式，不超过 25MB</span>
            </template>
            <template v-else>
              <span class="file-picker__name">{{ selectedFile.name }}</span>
              <span class="file-picker__size">{{ formatSize(selectedFile.size) }}</span>
              <span class="file-picker__change">重新选择</span>
            </template>
          </label>
        </div>

        <button class="feature-button feature-button--primary convert-submit" :disabled="!canStart" @click="startConvert">
          {{ processing ? (STATUS_TEXT[taskStatus] || '处理中') : '开始转换' }}
        </button>

        <div v-if="showProgress" class="progress-box">
          <div class="progress-box__head">
            <span>{{ statusText }}</span>
            <span>{{ progress }}%</span>
          </div>
          <div class="progress-box__track">
            <div class="progress-box__bar" :style="{ width: `${progress}%` }"></div>
          </div>
          <p class="convert-hint">{{ progressMessage }}</p>

          <div v-if="taskStatus === 'SUCCEEDED'" class="result-box">
            <span class="result-box__check">✓</span>
            <div class="result-box__info">
              <strong>{{ result.fileName }}</strong>
              <small>{{ formatSize(result.fileSize) }}</small>
            </div>
            <div class="result-box__actions">
              <button class="feature-button" @click="openFile(taskId, result.fileName)">打开</button>
              <button class="feature-button feature-button--primary" @click="saveFile(taskId, result.fileName)">下载</button>
            </div>
          </div>

          <div v-if="taskStatus === 'FAILED'" class="feature-error">
            <span>{{ errorMessage }}</span>
            <button class="feature-button" @click="resetAndRetry">重新转换</button>
          </div>
        </div>
      </section>

      <!-- 右侧：历史记录 -->
      <section class="feature-card convert-history">
        <div class="feature-section__head">
          <h2>历史记录</h2>
          <button
            v-if="selectedIds.length"
            class="feature-button feature-button--danger"
            @click="batchDelete"
          >
            批量删除（{{ selectedIds.length }}）
          </button>
        </div>

        <div v-if="!historyList.length" class="feature-empty">暂无转换记录</div>
        <div v-else class="history-list">
          <div v-for="item in historyList" :key="item.taskId" class="history-item">
            <input type="checkbox" :checked="isSelected(item.taskId)" @change="toggleSelect(item.taskId)" />
            <div class="history-item__main">
              <strong>{{ item.resultFileName || item.sourceFileName }}</strong>
              <small>{{ formatTime(item.createTime) }}</small>
            </div>
            <span v-if="item.status !== 'SUCCEEDED'" class="feature-status" :class="statusClass(item.status)">
              {{ STATUS_TEXT[item.status] || item.status }}
            </span>
            <template v-else>
              <button class="feature-button" @click="openFile(item.taskId, item.resultFileName)">打开</button>
              <button class="feature-button feature-button--primary" @click="saveFile(item.taskId, item.resultFileName)">下载</button>
            </template>
          </div>
        </div>
      </section>
    </main>
  </div>
</template>

<style scoped>
.convert {
  display: grid;
  grid-template-columns: 250px minmax(320px, 380px) minmax(360px, 1fr);
  gap: 18px;
  width: min(1440px, calc(100% - 40px));
  margin: 0 auto;
  padding: 24px 0 45px;
  align-items: start;
}

.convert-nav,
.convert-config,
.convert-history {
  padding: 22px;
}

.convert-nav h2 {
  margin: 0 0 16px;
  color: #23344a;
  font-size: 19px;
}

.convert-nav > button {
  display: flex;
  align-items: center;
  gap: 11px;
  width: 100%;
  padding: 12px;
  border-radius: 8px;
  color: #5d7083;
  background: transparent;
  text-align: left;
}

.convert-nav > button.active {
  color: #294b67;
  background: #eaf1f6;
}

.convert-nav__badge {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 34px;
  height: 34px;
  border-radius: 8px;
  font-size: 11px;
  font-weight: 800;
}

.convert-nav__copy,
.convert-nav__copy strong,
.convert-nav__copy small {
  display: block;
}

.convert-nav__copy strong {
  color: #26384d;
  font-size: 14px;
}

.convert-nav__copy small {
  margin-top: 4px;
  color: #83909d;
  font-size: 11px;
}

.convert-config__head > span {
  color: #6f8398;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 1.2px;
}

.convert-config__head h1 {
  margin: 8px 0 5px;
  color: #17233a;
  font-size: 22px;
}

.convert-config__head p {
  margin: 0 0 22px;
  color: #718096;
  font-size: 14px;
}

.convert-field {
  display: grid;
  gap: 8px;
  margin-bottom: 16px;
}

.convert-field > label {
  color: #42566b;
  font-size: 13px;
  font-weight: 700;
}

.convert-hint {
  margin: 6px 0 0;
  color: #83909d;
  font-size: 12px;
  line-height: 1.6;
}

.file-picker {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 150px;
  padding: 20px;
  border: 2px dashed #d7e0e8;
  border-radius: 10px;
  background: #fafbfd;
  cursor: pointer;
  text-align: center;
}

.file-picker__plus {
  color: #315f8c;
  font-size: 34px;
  line-height: 1;
}

.file-picker__text {
  color: #26384d;
  font-size: 14px;
}

.file-picker__hint,
.file-picker__size {
  color: #83909d;
  font-size: 12px;
}

.file-picker__name {
  color: #26384d;
  font-size: 14px;
  font-weight: 700;
  word-break: break-all;
}

.file-picker__change {
  color: #315f8c;
  font-size: 12px;
  font-weight: 700;
}

.convert-submit {
  width: 100%;
  margin-top: 4px;
}

.progress-box {
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid #eef1f4;
}

.progress-box__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  color: #26384d;
  font-size: 13px;
  font-weight: 700;
}

.progress-box__track {
  height: 8px;
  border-radius: 999px;
  background: #eef0f3;
  overflow: hidden;
}

.progress-box__bar {
  height: 100%;
  border-radius: 999px;
  background: #315f8c;
  transition: width 0.3s ease;
}

.result-box {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 14px;
  padding: 14px;
  border-radius: 10px;
  background: #edf8f3;
}

.result-box__check {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  color: #fff;
  background: #36705c;
  font-weight: 700;
}

.result-box__info {
  flex: 1;
  min-width: 0;
}

.result-box__info strong,
.result-box__info small {
  display: block;
}

.result-box__info strong {
  color: #26384d;
  font-size: 13px;
  word-break: break-all;
}

.result-box__info small {
  margin-top: 3px;
  color: #718096;
  font-size: 12px;
}

.result-box__actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.convert-history {
  min-height: 300px;
}

.history-list {
  display: grid;
  gap: 8px;
}

.history-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid #e5eaf0;
  border-radius: 8px;
}

.history-item > input[type='checkbox'] {
  flex-shrink: 0;
  width: 15px;
  height: 15px;
  accent-color: #315f8c;
}

.history-item__main {
  flex: 1;
  min-width: 0;
}

.history-item__main strong,
.history-item__main small {
  display: block;
}

.history-item__main strong {
  color: #26384d;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history-item__main small {
  margin-top: 3px;
  color: #83909d;
  font-size: 11px;
}

.history-item .feature-button {
  min-height: 30px;
  padding: 0 12px;
  font-size: 12px;
}

@media (max-width: 1100px) {
  .convert {
    grid-template-columns: 220px 1fr;
  }

  .convert-history {
    grid-column: 1 / -1;
  }
}

@media (max-width: 720px) {
  .convert {
    grid-template-columns: 1fr;
  }

  .convert-nav {
    display: flex;
    gap: 5px;
    overflow-x: auto;
  }

  .convert-nav h2 {
    display: none;
  }

  .convert-nav > button {
    min-width: 150px;
  }

  .convert-history {
    grid-column: 1;
  }
}
</style>
