<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

// ─── 周次与学期状态 ────────────────────────────────────────────────
const currentWeek = ref(12)
const weekOptions = Array.from({ length: 20 }, (_, i) => i + 1)
const showWeekDropdown = ref(false)

const SEMESTER_OPTIONS = [
  { id: '2025-2026-2', label: '2025-2026 第二学期' },
  { id: '2025-2026-1', label: '2025-2026 第一学期' },
]

const currentSemester = ref('2025-2026 第二学期')
const currentSemesterId = ref('2025-2026-2')
const showSemesterSheet = ref(false)

const today = new Date()
const todayLabel = computed(() => {
  const y = today.getFullYear()
  const m = String(today.getMonth() + 1).padStart(2, '0')
  const d = String(today.getDate()).padStart(2, '0')
  return `${y}年${m}月${d}日`
})

// 今天是周几 (1=周一 … 7=周日)
const todayDow = computed(() => {
  const d = today.getDay()
  return d === 0 ? 7 : d
})

// 学期第一周周一（所有周次以此为基准推算）
const SEMESTER_START_MONDAY = new Date(2026, 1, 23) // 2026-02-23

// 当前选中周的周一
const currentWeekMonday = computed(() => {
  const d = new Date(SEMESTER_START_MONDAY)
  d.setDate(d.getDate() + (currentWeek.value - 1) * 7)
  return d
})

// 选中日期索引（0=周一 … 6=周日），同一时间只有一个
const selectedDayIndex = ref(null)

// 课表背景图（从 localStorage 读取）
const BG_STORAGE_KEY = 'schedule_bg_image'
const scheduleBg = ref(null)

onMounted(() => {
  selectedDayIndex.value = todayDow.value - 1
  scheduleBg.value = localStorage.getItem(BG_STORAGE_KEY) || null
})

const shellStyle = computed(() => {
  if (!scheduleBg.value) return {}
  return {
    backgroundImage: `url(${scheduleBg.value})`,
    backgroundSize: 'cover',
    backgroundPosition: 'center',
    backgroundAttachment: 'fixed',
  }
})

// 每天对应的日期数字
function dayDateNum(colIndex) {
  const d = new Date(currentWeekMonday.value)
  d.setDate(currentWeekMonday.value.getDate() + colIndex)
  return d.getDate()
}

// 每天对应的完整日期（M/D 格式）
function dayDateLabel(colIndex) {
  const d = new Date(currentWeekMonday.value)
  d.setDate(currentWeekMonday.value.getDate() + colIndex)
  return `${d.getMonth() + 1}/${d.getDate()}`
}

// 点击表头日期选中
function selectDay(ci) {
  selectedDayIndex.value = ci
}

function toggleWeekDropdown() {
  showWeekDropdown.value = !showWeekDropdown.value
}

function selectWeek(w) {
  currentWeek.value = w
  showWeekDropdown.value = false
  // 切换周后重置选中日：当前周则选中今天，否则默认选中周一
  const isCurrentWeek = currentWeek.value === 12 // TODO: 动态计算当前真实周次
  selectedDayIndex.value = isCurrentWeek ? todayDow.value - 1 : 0
}

// ─── 课表数据（预留后端接口） ─────────────────────────────────────────
// TODO: 调用后端 API 获取真实课表数据
// async function fetchSchedule() {
//   const res = await scheduleApi.getMySchedule({
//     week: currentWeek.value,
//     semesterId: currentSemesterId.value,
//   })
//   scheduleData.value = res.data
// }

// TODO: 切换学期时调用后端接口刷新课表
// async function switchSemester(semesterId) {
//   currentSemesterId.value = semesterId
//   currentWeek.value = 1
//   await fetchSchedule()
// }

const scheduleData = ref([
  // { day: 1, period: 1, name: '高等数学', room: 'A301', color: '#3b82f6' },
  // { day: 2, period: 3, name: '大学物理', room: 'B205', color: '#10b981' },
  // { day: 4, period: 2, name: '程序设计', room: 'C102', color: '#f59e0b' },
])

const WEEKDAYS = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']

const PERIODS = [
  { label: '1', time: '08:00 – 08:45' },
  { label: '2', time: '08:55 – 09:40' },
  { label: '3', time: '10:00 – 10:45' },
  { label: '4', time: '10:55 – 11:40' },
  { label: '5', time: '14:00 – 14:45' },
  { label: '6', time: '14:55 – 15:40' },
  { label: '7', time: '16:00 – 16:45' },
  { label: '8', time: '16:55 – 17:40' },
]

function getCourse(day, period) {
  return scheduleData.value.find(c => c.day === day + 1 && c.period === period + 1)
}

// ─── 导航 ────────────────────────────────────────────────────────────
function goBack() {
  router.back()
}

function onSemesterClick() {
  router.push('/mine/semester')
}

function selectSemester(opt) {
  if (opt.id !== currentSemesterId.value) {
    currentSemesterId.value = opt.id
    currentSemester.value = opt.label
    currentWeek.value = 1
    // TODO: 调用后端接口刷新课表数据
    // switchSemester(opt.id)
  }
  showSemesterSheet.value = false
}

function closeSemesterSheet() {
  showSemesterSheet.value = false
}

function onSettingsClick() {
  router.push('/mine/schedule-settings')
}

// ─── 分享弹窗 ─────────────────────────────────────────────────────────
const showShareModal = ref(false)
const shareCode = ref('')

// TODO: 调用后端接口生成分享码
// async function generateShareCodeAPI(semesterId) {
//   const res = await shareApi.generate({ semesterId })
//   return res.data.code
// }

function generateLocalCode() {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'
  let code = ''
  for (let i = 0; i < 8; i++) code += chars[Math.floor(Math.random() * chars.length)]
  return code
}

async function onShareClick() {
  // TODO: 调用后端接口获取分享码
  // shareCode.value = await generateShareCodeAPI(currentSemesterId.value)
  shareCode.value = generateLocalCode()

  try {
    await navigator.clipboard.writeText(shareCode.value)
  } catch {
    // 兜底：旧版浏览器
    const ta = document.createElement('textarea')
    ta.value = shareCode.value
    document.body.appendChild(ta)
    ta.select()
    document.execCommand('copy')
    document.body.removeChild(ta)
  }

  showShareModal.value = true
}

function closeShareModal() {
  showShareModal.value = false
}

function onAddClick() {
  // TODO: 添加课程
}

// ─── 加号下拉菜单 ───────────────────────────────────────────────────
const showAddMenu = ref(false)
const addMenuRef = ref(null)

function toggleAddMenu(e) {
  e.stopPropagation()
  showAddMenu.value = !showAddMenu.value
}

function closeAddMenu() {
  showAddMenu.value = false
}

function onDocClick() {
  if (showAddMenu.value) showAddMenu.value = false
}

onMounted(() => document.addEventListener('click', onDocClick))
onBeforeUnmount(() => document.removeEventListener('click', onDocClick))

// ─── 教务账号状态 ─────────────────────────────────────────────────────
const eduAccountSet = ref(false) // TODO: 从后端接口获取真实状态

// ─── 未登录提示弹窗 ──────────────────────────────────────────────────
const showNotLoggedModal = ref(false)

function goEduAccountSetup() {
  showNotLoggedModal.value = false
  closeAddMenu()
  router.push('/mine/edu-account')
}

function closeNotLoggedModal() {
  showNotLoggedModal.value = false
}

// ─── 导入课表 ──────────────────────────────────────────────────────────
const showImportModal = ref(false)
const importTarget = ref('') // 'current' | 'other'

const IMPORT_STEPS = [
  { key: 'connect',  label: '连接教务系统' },
  { key: 'login',    label: '登录教务账号' },
  { key: 'query',    label: '进入课表查询' },
  { key: 'read',     label: '读取对应学期' },
  { key: 'save',     label: '保存课程数据' },
]

const stepStatuses = ref({}) // { connect: 'done', login: 'running', ... }
const importDone = ref(false)
const importFailed = ref(false)

// TODO: 调用后端接口执行导入
// async function importScheduleAPI(semesterId) {
//   const res = await scheduleApi.importFromEdu({ semesterId })
//   return res.data
// }

function startImport(target) {
  closeAddMenu()
  if (!eduAccountSet.value) {
    showNotLoggedModal.value = true
    return
  }
  importTarget.value = target
  importDone.value = false
  importFailed.value = false
  stepStatuses.value = {}
  showImportModal.value = true
  runImportSteps()
}

async function runImportSteps() {
  for (let i = 0; i < IMPORT_STEPS.length; i++) {
    const step = IMPORT_STEPS[i]
    stepStatuses.value[step.key] = 'running'
    // TODO: 替换为真实接口调用，根据接口进度更新状态
    await new Promise(r => setTimeout(r, 900))
    stepStatuses.value[step.key] = 'done'
  }
  importDone.value = true
}

function closeImportModal() {
  showImportModal.value = false
}

function getStepIcon(key) {
  const s = stepStatuses.value[key]
  if (s === 'done') return 'done'
  if (s === 'running') return 'running'
  return 'pending'
}

// ─── 分享码导入 ────────────────────────────────────────────────────────
const showShareCodeImportModal = ref(false)
const shareCodeInput = ref('')
const shareCodeImporting = ref(false)
const shareCodeResult = ref(null) // null | 'success' | 'fail'

// TODO: 调用后端接口通过分享码导入课表
// async function importByShareCodeAPI(code) {
//   const res = await scheduleApi.importByShareCode({ code })
//   return res.data
// }

function openShareCodeImport() {
  closeAddMenu()
  shareCodeInput.value = ''
  shareCodeResult.value = null
  showShareCodeImportModal.value = true
}

async function doShareCodeImport() {
  if (!shareCodeInput.value.trim()) return
  shareCodeImporting.value = true
  shareCodeResult.value = null
  try {
    // TODO: 调用后端接口
    await new Promise(r => setTimeout(r, 1200))
    shareCodeResult.value = 'success'
  } catch {
    shareCodeResult.value = 'fail'
  } finally {
    shareCodeImporting.value = false
  }
}

function closeShareCodeImportModal() {
  showShareCodeImportModal.value = false
}
</script>

<template>
  <div class="schedule-shell" :class="{ 'has-bg': !!scheduleBg }" :style="shellStyle">

    <!-- ── 顶部导航栏 ── -->
    <header class="navbar">
      <button class="nav-back" type="button" @click="goBack">
        <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
          <polyline points="15 18 9 12 15 6" />
        </svg>
      </button>
      <h1 class="nav-title">我的课表</h1>
      <div class="nav-actions">
        <button class="nav-btn" type="button" @click="onSemesterClick">学期</button>
        <button class="nav-btn" type="button" @click="onSettingsClick">设置</button>
        <button class="nav-btn" type="button" @click="onShareClick">分享</button>
        <div class="nav-add-wrap" ref="addMenuRef">
          <button class="nav-add" type="button" @click="toggleAddMenu">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="#fff" stroke-width="2.4" stroke-linecap="round">
              <line x1="12" y1="5" x2="12" y2="19" />
              <line x1="5" y1="12" x2="19" y2="12" />
            </svg>
          </button>
          <Transition name="menu">
            <ul v-if="showAddMenu" class="add-dropdown">
              <li @click="startImport('current')">
                <svg viewBox="0 0 20 20" width="15" height="15" fill="currentColor"><path d="M3 17a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1zm3.293-7.707a1 1 0 011.414 0L9 10.586V3a1 1 0 112 0v7.586l1.293-1.293a1 1 0 111.414 1.414l-3 3a1 1 0 01-1.414 0l-3-3a1 1 0 010-1.414z"/></svg>
                导入本学期
              </li>
              <li @click="startImport('other')">
                <svg viewBox="0 0 20 20" width="15" height="15" fill="currentColor"><path d="M3 17a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1zm3.293-7.707a1 1 0 011.414 0L9 10.586V3a1 1 0 112 0v7.586l1.293-1.293a1 1 0 111.414 1.414l-3 3a1 1 0 01-1.414 0l-3-3a1 1 0 010-1.414z"/></svg>
                导入其他学期
              </li>
              <li @click="openShareCodeImport">
                <svg viewBox="0 0 20 20" width="15" height="15" fill="currentColor"><path fill-rule="evenodd" d="M12.586 4.586a2 2 0 112.828 2.828l-3 3a2 2 0 01-2.828 0 1 1 0 00-1.414 1.414 4 4 0 005.656 0l3-3a4 4 0 00-5.656-5.656l-1.5 1.5a1 1 0 101.414 1.414l1.5-1.5zm-5 5a2 2 0 012.828 0 1 1 0 101.414-1.414 4 4 0 00-5.656 0l-3 3a4 4 0 105.656 5.656l1.5-1.5a1 1 0 10-1.414-1.414l-1.5 1.5a2 2 0 11-2.828-2.828l3-3z" clip-rule="evenodd"/></svg>
                分享码导入
              </li>
            </ul>
          </Transition>
        </div>
      </div>
    </header>

    <!-- ── 周次 / 学期 / 日期区 ── -->
    <section class="info-panel">
      <!-- 第XX周下拉 -->
      <div class="week-row">
        <div class="week-selector" @click="toggleWeekDropdown">
          <span class="week-label">第{{ currentWeek }}周</span>
          <svg class="week-caret" :class="{ open: showWeekDropdown }" viewBox="0 0 12 12" width="12" height="12" fill="currentColor">
            <path d="M2 4l4 4 4-4" />
          </svg>
        </div>
        <ul v-if="showWeekDropdown" class="week-dropdown">
          <li
            v-for="w in weekOptions"
            :key="w"
            :class="{ active: w === currentWeek }"
            @click="selectWeek(w)"
          >第{{ w }}周</li>
        </ul>
      </div>

      <!-- 学期 -->
      <div class="semester-row">
        <span class="semester-text">{{ currentSemester }}</span>
        <span class="semester-switch" @click="onSemesterClick">切换</span>
      </div>

      <!-- 今天日期 -->
      <div class="date-row">{{ todayLabel }}</div>
    </section>

    <!-- ── 课表网格 ── -->
    <section class="grid-wrapper">
      <div class="schedule-grid">

        <!-- 表头行 -->
        <div class="grid-head-corner">节</div>
        <div
          v-for="(d, ci) in WEEKDAYS"
          :key="d"
          class="grid-head-cell"
          @click="selectDay(ci)"
        >
          <span class="head-day">{{ d }}</span>
          <span class="head-date" :class="{ selected: ci === selectedDayIndex }">{{ dayDateLabel(ci) }}</span>
        </div>

        <!-- 课程行 -->
        <template v-for="(p, pi) in PERIODS" :key="pi">
          <!-- 左侧节次列 -->
          <div class="grid-period-cell">
            <span class="period-num">{{ p.label }}</span>
            <span class="period-time">{{ p.time }}</span>
          </div>

          <!-- 7个课程格子 -->
          <div
            v-for="(d, ci) in WEEKDAYS"
            :key="`${pi}-${ci}`"
            class="grid-course-cell"
          >
            <div
              v-if="getCourse(ci, pi)"
              class="course-chip"
              :style="{ background: getCourse(ci, pi).color + '22', borderLeft: `3px solid ${getCourse(ci, pi).color}` }"
            >
              <span class="chip-name">{{ getCourse(ci, pi).name }}</span>
              <span class="chip-room">{{ getCourse(ci, pi).room }}</span>
            </div>
          </div>
        </template>

      </div>
    </section>

    <!-- ── 分享弹窗 ── -->
    <Teleport to="body">
      <Transition name="modal">
        <div v-if="showShareModal" class="modal-overlay" @click.self="closeShareModal">
          <div class="modal-panel">

            <!-- 顶部图标 -->
            <div class="modal-icon">
              <svg viewBox="0 0 48 48" width="44" height="44" fill="none">
                <circle cx="24" cy="24" r="22" fill="#dbeafe" stroke="#2563eb" stroke-width="2" />
                <path d="M16 24h16M24 16v16" stroke="#2563eb" stroke-width="2.4" stroke-linecap="round" />
                <path d="M18 30l-4 4 4 4" stroke="#2563eb" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" fill="none" transform="translate(14,-10)" />
              </svg>
            </div>

            <!-- 标题 -->
            <div class="modal-title">本学期分享码已复制</div>

            <!-- 学期名称 -->
            <div class="modal-field">
              <span class="field-label">分享学期</span>
              <span class="field-value">{{ currentSemester }}</span>
            </div>

            <!-- 分享码 -->
            <div class="modal-field code-field">
              <span class="field-label">分享码</span>
              <span class="code-value">{{ shareCode }}</span>
            </div>

            <!-- 提示文字 -->
            <p class="modal-hint">好友粘贴后只会导入这个学期的课表。</p>

            <!-- 操作按钮 -->
            <button class="modal-btn" type="button" @click="closeShareModal">我知道了</button>

          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- ── 未登录教务系统提示弹窗 ── -->
    <Teleport to="body">
      <Transition name="modal">
        <div v-if="showNotLoggedModal" class="modal-overlay" @click.self="closeNotLoggedModal">
          <div class="modal-panel">
            <div class="modal-icon">
              <svg viewBox="0 0 48 48" width="44" height="44" fill="none">
                <circle cx="24" cy="24" r="22" fill="#fef2f2" stroke="#dc2626" stroke-width="2" />
                <path d="M24 16v8" stroke="#dc2626" stroke-width="3" stroke-linecap="round" />
                <circle cx="24" cy="31" r="2" fill="#dc2626" />
              </svg>
            </div>
            <div class="modal-title">您未登录教务系统</div>
            <p class="modal-hint">请先绑定教务账号，才能导入课表数据。</p>
            <button class="modal-btn" type="button" @click="goEduAccountSetup">去设置教务账号</button>
            <button class="modal-cancel-btn" type="button" @click="closeNotLoggedModal">取消</button>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- ── 导入进度弹窗 ── -->
    <Teleport to="body">
      <Transition name="modal">
        <div v-if="showImportModal" class="modal-overlay" @click.self="importDone ? closeImportModal() : null">
          <div class="modal-panel import-panel">
            <div class="import-title">正在导入课表</div>
            <div class="import-subtitle">{{ importTarget === 'current' ? currentSemester : '其他学期' }}</div>

            <ul class="import-steps">
              <li v-for="step in IMPORT_STEPS" :key="step.key" class="import-step">
                <!-- 状态图标 -->
                <span class="step-icon" :class="getStepIcon(step.key)">
                  <!-- done: 勾选 -->
                  <svg v-if="getStepIcon(step.key) === 'done'" viewBox="0 0 16 16" width="16" height="16" fill="currentColor">
                    <path d="M13.78 4.22a.75.75 0 010 1.06l-7.25 7.25a.75.75 0 01-1.06 0L2.22 9.28a.75.75 0 011.06-1.06L6 10.94l6.72-6.72a.75.75 0 011.06 0z"/>
                  </svg>
                  <!-- running: 旋转圆环 -->
                  <svg v-else-if="getStepIcon(step.key) === 'running'" class="spin" viewBox="0 0 16 16" width="16" height="16" fill="none">
                    <circle cx="8" cy="8" r="6" stroke="#dbeafe" stroke-width="2.5" />
                    <path d="M14 8a6 6 0 00-6-6" stroke="#2563eb" stroke-width="2.5" stroke-linecap="round" />
                  </svg>
                  <!-- pending: 灰色圆点 -->
                  <span v-else class="dot"></span>
                </span>
                <span class="step-label" :class="getStepIcon(step.key)">{{ step.label }}</span>
              </li>
            </ul>

            <button v-if="importDone" class="modal-btn" type="button" @click="closeImportModal">导入完成</button>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- ── 分享码导入弹窗 ── -->
    <Teleport to="body">
      <Transition name="modal">
        <div v-if="showShareCodeImportModal" class="modal-overlay" @click.self="closeShareCodeImportModal">
          <div class="modal-panel">
            <div class="modal-icon">
              <svg viewBox="0 0 48 48" width="44" height="44" fill="none">
                <circle cx="24" cy="24" r="22" fill="#f0f5ff" stroke="#2563eb" stroke-width="2" />
                <path d="M16 20h16M16 26h10" stroke="#2563eb" stroke-width="2.4" stroke-linecap="round" />
                <path d="M30 28l4-4-4-4" stroke="#2563eb" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" fill="none" />
              </svg>
            </div>
            <div class="modal-title">分享码导入</div>
            <p class="modal-hint">请输入好友分享的 8 位课表码</p>

            <div class="share-input-wrap">
              <input
                v-model="shareCodeInput"
                class="share-code-input"
                type="text"
                maxlength="8"
                placeholder="请输入分享码"
                :disabled="shareCodeImporting"
              />
            </div>

            <div v-if="shareCodeResult === 'success'" class="result-msg success">导入成功！课表数据已更新。</div>
            <div v-if="shareCodeResult === 'fail'" class="result-msg fail">分享码无效，请确认后重试。</div>

            <button
              class="modal-btn"
              type="button"
              :disabled="shareCodeImporting || !shareCodeInput.trim()"
              @click="doShareCodeImport"
            >{{ shareCodeImporting ? '导入中…' : '导入课表' }}</button>
            <button class="modal-cancel-btn" type="button" @click="closeShareCodeImportModal">取消</button>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- ── 学期切换底部弹窗 ── -->
    <Teleport to="body">
      <Transition name="sheet">
        <div v-if="showSemesterSheet" class="sheet-overlay" @click.self="closeSemesterSheet">
          <div class="sheet-panel">
            <div class="sheet-title">选择学期</div>
            <ul class="sheet-list">
              <li
                v-for="opt in SEMESTER_OPTIONS"
                :key="opt.id"
                class="sheet-item"
                :class="{ active: opt.id === currentSemesterId }"
                @click="selectSemester(opt)"
              >
                <span>{{ opt.label }}</span>
                <svg v-if="opt.id === currentSemesterId" class="check-icon" viewBox="0 0 20 20" width="16" height="16" fill="currentColor">
                  <path fill-rule="evenodd" d="M16.704 5.29a1 1 0 010 1.42l-7.5 7.5a1 1 0 01-1.42 0l-3.5-3.5a1 1 0 011.42-1.42l2.79 2.79 6.79-6.79a1 1 0 011.42 0z" clip-rule="evenodd" />
                </svg>
              </li>
            </ul>
            <button class="sheet-cancel" type="button" @click="closeSemesterSheet">取消</button>
          </div>
        </div>
      </Transition>
    </Teleport>

  </div>
</template>

<style scoped>
/* ─── 整体外壳 ─── */
.schedule-shell {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: #f4f7fb;
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Helvetica Neue', sans-serif;
  color: #1f2937;
  user-select: none;
}

/* 背景图生效时：外壳透明，各板块半透明保证可读性 */
.schedule-shell.has-bg {
  background: transparent;
}

.schedule-shell.has-bg .navbar {
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(8px);
}

.schedule-shell.has-bg .info-panel {
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(8px);
}

.schedule-shell.has-bg .schedule-grid {
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(6px);
}

/* ─── 顶部导航 ─── */
.navbar {
  position: sticky;
  top: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  height: 52px;
  background: #ffffff;
  border-bottom: 1px solid #eef2f7;
  flex-shrink: 0;
}

.nav-back {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 8px;
  background: #f1f5f9;
  color: #374151;
  cursor: pointer;
}

.nav-title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: #111827;
}

.nav-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.nav-btn {
  padding: 5px 10px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  background: #fff;
  color: #475569;
  font-size: 13px;
  cursor: pointer;
  transition: background 0.15s;
}

.nav-btn:hover {
  background: #f8fafc;
}

.nav-add {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 50%;
  background: #111827;
  cursor: pointer;
  transition: opacity 0.15s;
}

.nav-add:hover {
  opacity: 0.85;
}

/* ─── 信息面板 ─── */
.info-panel {
  padding: 16px 20px 10px;
  background: #ffffff;
  border-bottom: 1px solid #eef2f7;
  flex-shrink: 0;
}

.week-row {
  position: relative;
  margin-bottom: 6px;
}

.week-selector {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
}

.week-label {
  font-size: 24px;
  font-weight: 800;
  color: #dc2626;
  letter-spacing: -0.5px;
}

.week-caret {
  color: #dc2626;
  transition: transform 0.2s;
}

.week-caret.open {
  transform: rotate(180deg);
}

.week-dropdown {
  position: absolute;
  top: 36px;
  left: 0;
  z-index: 200;
  width: 140px;
  max-height: 220px;
  overflow-y: auto;
  margin: 0;
  padding: 6px 0;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 6px 24px rgba(0,0,0,0.12);
  list-style: none;
}

.week-dropdown li {
  padding: 7px 16px;
  font-size: 14px;
  color: #374151;
  cursor: pointer;
  transition: background 0.12s;
}

.week-dropdown li:hover {
  background: #f1f5f9;
}

.week-dropdown li.active {
  color: #dc2626;
  font-weight: 700;
  background: #fef2f2;
}

.semester-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.semester-text {
  font-size: 13px;
  color: #6b7280;
}

.semester-switch {
  font-size: 13px;
  color: #2563eb;
  cursor: pointer;
  font-weight: 600;
}

.date-row {
  font-size: 12px;
  color: #9ca3af;
  margin-bottom: 4px;
}

/* ─── 课表网格 ─── */
.grid-wrapper {
  flex: 1;
  overflow-x: auto;
  padding: 12px 8px 24px;
}

.schedule-grid {
  display: grid;
  /* 节次列 72px + 7天各 1fr */
  grid-template-columns: 72px repeat(7, minmax(90px, 1fr));
  gap: 0;
  min-width: 760px;
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 1px 4px rgba(0,0,0,0.06);
}

/* 表头 */
.grid-head-corner {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 60px;
  background: #f8fafc;
  font-size: 12px;
  color: #94a3b8;
  border-bottom: 1px solid #eef2f7;
  border-right: 1px solid #eef2f7;
}

.grid-head-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  height: 60px;
  background: #f8fafc;
  border-bottom: 1px solid #eef2f7;
  border-right: 1px solid #f1f5f9;
  cursor: pointer;
  transition: background 0.12s;
}

.grid-head-cell:hover {
  background: #f1f5f9;
}

.grid-head-cell:last-child {
  border-right: none;
}

.head-day {
  font-size: 11px;
  font-weight: 700;
  color: #475569;
  line-height: 1;
  letter-spacing: 0.5px;
}

.head-date {
  font-size: 13px;
  color: #64748b;
  line-height: 1;
  padding: 3px 6px;
  border-radius: 4px;
  font-weight: 500;
}

.head-date.selected {
  color: #dc2626;
  font-weight: 800;
  background: #fef2f2;
}

/* 节次列 */
.grid-period-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 58px;
  padding: 4px 0;
  background: #f8fafc;
  border-bottom: 1px solid #eef2f7;
  border-right: 1px solid #eef2f7;
}

.period-num {
  font-size: 15px;
  font-weight: 700;
  color: #374151;
  line-height: 1;
}

.period-time {
  font-size: 9px;
  color: #94a3b8;
  margin-top: 3px;
  white-space: nowrap;
}

/* 课程单元格 */
.grid-course-cell {
  min-height: 58px;
  padding: 4px 3px;
  border-bottom: 1px solid #f1f5f9;
  border-right: 1px solid #f1f5f9;
}

.grid-course-cell:last-child {
  border-right: none;
}

.course-chip {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 6px 8px;
  border-radius: 6px;
  height: 100%;
}

.chip-name {
  font-size: 12px;
  font-weight: 600;
  color: #1f2937;
  line-height: 1.3;
}

.chip-room {
  font-size: 10px;
  color: #6b7280;
}

/* ─── 底部弹窗 ─── */
.sheet-overlay {
  position: fixed;
  inset: 0;
  z-index: 999;
  background: rgba(0, 0, 0, 0.35);
  display: flex;
  align-items: flex-end;
  justify-content: center;
}

.sheet-panel {
  width: 100%;
  max-width: 480px;
  background: #fff;
  border-radius: 16px 16px 0 0;
  padding: 24px 20px 28px;
  box-shadow: 0 -4px 24px rgba(0, 0, 0, 0.1);
}

.sheet-title {
  font-size: 15px;
  font-weight: 700;
  color: #111827;
  text-align: center;
  margin-bottom: 16px;
}

.sheet-list {
  list-style: none;
  margin: 0;
  padding: 0;
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid #eef2f7;
}

.sheet-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  font-size: 14px;
  color: #374151;
  background: #fff;
  cursor: pointer;
  border-bottom: 1px solid #f1f5f9;
  transition: background 0.12s;
}

.sheet-item:last-child {
  border-bottom: none;
}

.sheet-item:hover {
  background: #f8fafc;
}

.sheet-item.active {
  color: #2563eb;
  font-weight: 600;
  background: #f0f5ff;
}

.check-icon {
  flex-shrink: 0;
  color: #2563eb;
}

.sheet-cancel {
  display: block;
  width: 100%;
  margin-top: 14px;
  padding: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #fff;
  color: #64748b;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.12s;
}

.sheet-cancel:hover {
  background: #f8fafc;
}

/* 弹窗进出动画 */
.sheet-enter-active,
.sheet-leave-active {
  transition: opacity 0.25s ease;
}

.sheet-enter-active .sheet-panel,
.sheet-leave-active .sheet-panel {
  transition: transform 0.25s ease;
}

.sheet-enter-from,
.sheet-leave-to {
  opacity: 0;
}

.sheet-enter-from .sheet-panel,
.sheet-leave-to .sheet-panel {
  transform: translateY(100%);
}

/* ─── 分享弹窗 ─── */
.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 9999;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
}

.modal-panel {
  background: #fff;
  border-radius: 16px;
  padding: 30px 26px 22px;
  width: 320px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.18);
}

.modal-icon {
  margin-bottom: 4px;
}

.modal-title {
  font-size: 16px;
  font-weight: 800;
  color: #111827;
  text-align: center;
}

.modal-field {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 10px 14px;
  border-radius: 8px;
  background: #f8fafc;
  border: 1px solid #eef2f7;
  gap: 12px;
}

.field-label {
  font-size: 12px;
  color: #9ca3af;
  flex-shrink: 0;
}

.field-value {
  font-size: 13px;
  font-weight: 600;
  color: #374151;
  text-align: right;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.code-field {
  background: #f0f5ff;
  border-color: #dbeafe;
}

.code-value {
  font-size: 20px;
  font-weight: 800;
  color: #2563eb;
  letter-spacing: 3px;
  text-align: right;
  flex: 1;
  font-family: 'Courier New', Courier, monospace;
}

.modal-hint {
  font-size: 12px;
  color: #9ca3af;
  text-align: center;
  line-height: 1.6;
  margin: 4px 0 0;
}

.modal-btn {
  width: 100%;
  height: 42px;
  margin-top: 6px;
  border: none;
  border-radius: 10px;
  background: linear-gradient(135deg, #2563eb 0%, #7c3aed 100%);
  color: #fff;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  transition: opacity 0.15s;
}

.modal-btn:hover {
  opacity: 0.92;
}

/* 分享弹窗动画 */
.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.2s ease;
}

.modal-enter-active .modal-panel,
.modal-leave-active .modal-panel {
  transition: transform 0.2s ease, opacity 0.2s ease;
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}

.modal-enter-from .modal-panel,
.modal-leave-to .modal-panel {
  transform: scale(0.9);
  opacity: 0;
}

/* ─── 加号下拉菜单 ─── */
.nav-add-wrap {
  position: relative;
}

.add-dropdown {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  z-index: 300;
  width: 170px;
  list-style: none;
  margin: 0;
  padding: 6px 0;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.14);
}

.add-dropdown li {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  font-size: 13px;
  font-weight: 500;
  color: #374151;
  cursor: pointer;
  transition: background 0.12s;
}

.add-dropdown li:hover {
  background: #f1f5f9;
}

.add-dropdown li svg {
  flex-shrink: 0;
  color: #94a3b8;
}

/* 下拉菜单动画 */
.menu-enter-active,
.menu-leave-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
}

.menu-enter-from,
.menu-leave-to {
  opacity: 0;
  transform: translateY(-6px) scale(0.96);
}

/* ─── 弹窗内取消按钮 ─── */
.modal-cancel-btn {
  width: 100%;
  height: 40px;
  margin-top: 8px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #fff;
  color: #64748b;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.12s;
}

.modal-cancel-btn:hover {
  background: #f8fafc;
}

/* ─── 导入进度弹窗 ─── */
.import-panel {
  width: 340px;
  align-items: stretch;
  gap: 0;
}

.import-title {
  font-size: 16px;
  font-weight: 800;
  color: #111827;
  text-align: center;
  margin-bottom: 4px;
}

.import-subtitle {
  font-size: 13px;
  color: #9ca3af;
  text-align: center;
  margin-bottom: 20px;
}

.import-steps {
  list-style: none;
  margin: 0 0 20px;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.import-step {
  display: flex;
  align-items: center;
  gap: 12px;
}

.step-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  flex-shrink: 0;
}

.step-icon.done {
  color: #16a34a;
}

.step-icon.running {
  color: #2563eb;
}

.step-icon.pending {
  color: #d1d5db;
}

.dot {
  display: block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #d1d5db;
}

.step-label {
  font-size: 14px;
  color: #9ca3af;
}

.step-label.done {
  color: #16a34a;
  font-weight: 600;
}

.step-label.running {
  color: #2563eb;
  font-weight: 600;
}

.spin {
  animation: spin-anim 0.8s linear infinite;
}

@keyframes spin-anim {
  from { transform: rotate(0deg); }
  to   { transform: rotate(360deg); }
}

/* ─── 分享码导入弹窗 ─── */
.share-input-wrap {
  width: 100%;
  margin: 8px 0;
}

.share-code-input {
  width: 100%;
  height: 48px;
  padding: 0 14px;
  border: 2px solid #e2e8f0;
  border-radius: 10px;
  background: #f8fafc;
  color: #1f2937;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 6px;
  text-align: center;
  outline: none;
  font-family: 'Courier New', Courier, monospace;
  text-transform: uppercase;
  box-sizing: border-box;
  transition: border-color 0.15s, background 0.15s;
}

.share-code-input:focus {
  border-color: #2563eb;
  background: #fff;
}

.share-code-input:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.result-msg {
  font-size: 13px;
  text-align: center;
  padding: 6px 12px;
  border-radius: 6px;
  margin-bottom: 6px;
}

.result-msg.success {
  background: #f0fdf4;
  color: #16a34a;
  font-weight: 600;
}

.result-msg.fail {
  background: #fef2f2;
  color: #dc2626;
  font-weight: 600;
}
</style>
