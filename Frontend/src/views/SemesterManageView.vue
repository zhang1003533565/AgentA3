<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

// ─── 教务账号 ──────────────────────────────────────────────────────────
const eduAccount = ref('')
const eduAccountSet = computed(() => !!eduAccount.value)

function goSetAccount() {
  router.push('/mine/edu-account')
}

// ─── 添加学期表单 ─────────────────────────────────────────────────────
const currentYear = new Date().getFullYear()
const startYear = ref(currentYear)
const endYear = ref(currentYear + 1)
const semesterNum = ref('1')
const startDate = ref('')

const SEMESTER_NUM_OPTIONS = [
  { value: '1', label: '第一学期' },
  { value: '2', label: '第二学期' },
]

// ─── 学期列表 ──────────────────────────────────────────────────────────
// TODO: 调用后端接口获取学期列表
// async function fetchSemesters() {
//   const res = await semesterApi.getList()
//   semesters.value = res.data
// }

const semesters = ref([
  // { id: '1', label: '2025-2026 第二学期', startDate: '2026-02-24' },
  // { id: '2', label: '2025-2026 第一学期', startDate: '2025-09-01' },
])

const currentSemesterId = ref(null) // 当前展示学期

const hasSemesters = computed(() => semesters.value.length > 0)

// ─── 新增学期 ──────────────────────────────────────────────────────────
// TODO: 调用后端接口新增学期
// async function addSemesterAPI(payload) {
//   const res = await semesterApi.add(payload)
//   return res.data
// }

function addSemester() {
  const semesterLabel = `${startYear.value}-${endYear.value} 第${semesterNum.value === '1' ? '一' : '二'}学期`

  // 校验
  if (!startDate.value) {
    alert('请选择开学日期')
    return
  }
  if (Number(endYear.value) !== Number(startYear.value) + 1) {
    alert('结束年份应比起始年份大 1')
    return
  }

  const newItem = {
    id: String(Date.now()),
    label: semesterLabel,
    startDate: startDate.value,
  }

  // TODO: 调用后端接口，成功后再 push
  // addSemesterAPI(newItem).then(() => { semesters.value.unshift(newItem) })

  semesters.value.unshift(newItem)

  // 重置表单
  startDate.value = ''
  semesterNum.value = '1'
}

// ─── 删除学期 ──────────────────────────────────────────────────────────
// TODO: 调用后端接口删除学期
// async function deleteSemesterAPI(id) {
//   await semesterApi.remove(id)
// }

function deleteSemester(id) {
  if (!confirm('删除学期将同时删除该学期所有课表记录，确认删除吗？')) return
  semesters.value = semesters.value.filter(s => s.id !== id)
  if (currentSemesterId.value === id) currentSemesterId.value = null
}

// ─── 设为当前学期 ───────────────────────────────────────────────────
// TODO: 调用后端接口设为当前学期
// async function setCurrentSemesterAPI(id) {
//   await semesterApi.setCurrent(id)
// }

function setAsCurrent(id) {
  currentSemesterId.value = id
}

// ─── 清空课表 ──────────────────────────────────────────────────────────
// TODO: 调用后端接口清空学期课表
// async function clearScheduleAPI(id) {
//   await semesterApi.clearSchedule(id)
// }

function clearSchedule(id) {
  const item = semesters.value.find(s => s.id === id)
  if (!item) return
  if (!confirm(`确认清空「${item.label}」的所有课程数据？学期将保留。`)) return
  // TODO: 调用后端接口清空课表
  alert(`「${item.label}」课表已清空`)
}

// ─── 保存 ──────────────────────────────────────────────────────────────
// TODO: 调用后端接口批量保存学期管理数据
// async function saveSemestersAPI() {
//   await semesterApi.batchSave(semesters.value)
// }

function saveSemesters() {
  // TODO: 调用后端接口保存
  alert('学期管理已保存')
}

// ─── 导入学期课表 ──────────────────────────────────────────────────
const showNotLoggedModal = ref(false)
const showImportModal = ref(false)
const importingItem = ref(null)

const IMPORT_STEPS = [
  { key: 'connect', label: '连接教务系统' },
  { key: 'login',   label: '登录教务账号' },
  { key: 'query',   label: '进入课表查询' },
  { key: 'read',    label: '读取对应学期' },
  { key: 'save',    label: '保存课程数据' },
]

const stepStatuses = ref({})
const importDone = ref(false)

// TODO: 调用后端接口执行导入
// async function importScheduleAPI(semesterId) {
//   const res = await scheduleApi.importFromEdu({ semesterId })
//   return res.data
// }

function importSemester(item) {
  if (!eduAccountSet.value) {
    showNotLoggedModal.value = true
    return
  }
  importingItem.value = item
  importDone.value = false
  stepStatuses.value = {}
  showImportModal.value = true
  runImportSteps()
}

async function runImportSteps() {
  for (const step of IMPORT_STEPS) {
    stepStatuses.value[step.key] = 'running'
    // TODO: 替换为真实接口调用
    await new Promise(r => setTimeout(r, 800))
    stepStatuses.value[step.key] = 'done'
  }
  importDone.value = true
}

function getStepIcon(key) {
  const s = stepStatuses.value[key]
  if (s === 'done') return 'done'
  if (s === 'running') return 'running'
  return 'pending'
}

function closeImportModal() {
  showImportModal.value = false
}

function closeNotLoggedModal() {
  showNotLoggedModal.value = false
}

function goEduAccountSetup() {
  showNotLoggedModal.value = false
  router.push('/mine/edu-account')
}

// ─── 导航 ──────────────────────────────────────────────────────────────
function goBack() {
  router.back()
}
</script>

<template>
  <div class="page-shell">

    <!-- 顶部导航 -->
    <header class="navbar">
      <button class="nav-back" type="button" @click="goBack">
        <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
          <polyline points="15 18 9 12 15 6" />
        </svg>
      </button>
      <h1 class="nav-title">学期管理</h1>
      <div class="nav-placeholder"></div>
    </header>

    <main class="page-body">

      <!-- ① 教务账号 -->
      <section class="card">
        <div class="section-label">教务账号</div>
        <div class="account-row">
          <span class="account-text">{{ eduAccount || '教务账号' }}</span>
          <div class="account-right">
            <span class="status-badge" :class="{ set: eduAccountSet }">{{ eduAccountSet ? '已设置' : '未设置' }}</span>
            <button class="link-btn" type="button" @click="goSetAccount">去设置</button>
          </div>
        </div>
      </section>

      <!-- ② 添加学期 -->
      <section class="card">
        <div class="section-label">添加学期</div>

        <!-- 学年 -->
        <div class="form-group">
          <label class="form-label">学年</label>
          <div class="year-row">
            <input v-model.number="startYear" type="number" class="year-input" placeholder="起始年份" />
            <span class="year-sep">—</span>
            <input v-model.number="endYear" type="number" class="year-input" placeholder="结束年份" />
          </div>
        </div>

        <!-- 学期 -->
        <div class="form-group">
          <label class="form-label">学期</label>
          <select v-model="semesterNum" class="form-select">
            <option v-for="opt in SEMESTER_NUM_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
          </select>
        </div>

        <!-- 开学日期 -->
        <div class="form-group">
          <label class="form-label">开学日期</label>
          <input v-model="startDate" type="date" class="form-input" />
        </div>

        <button class="add-btn" type="button" @click="addSemester">添加学期</button>
      </section>

      <!-- ③ 学期列表 -->
      <section class="card">
        <div class="section-label">学期列表</div>

        <div v-if="!hasSemesters" class="empty-tip">暂无学期，请先添加学期</div>

        <ul v-else class="semester-list">
          <li v-for="item in semesters" :key="item.id" class="semester-item">
            <div class="sem-head">
              <div class="sem-info">
                <span class="sem-label">
                  {{ item.label }}
                  <span v-if="currentSemesterId === item.id" class="current-badge">当前</span>
                </span>
                <span class="sem-date">开学 {{ item.startDate }}</span>
              </div>
            </div>
            <div class="sem-actions">
              <button
                class="action-btn primary"
                type="button"
                :disabled="currentSemesterId === item.id"
                @click="setAsCurrent(item.id)"
              >设为当前</button>
              <button class="action-btn blue" type="button" @click="importSemester(item)">导入此学期</button>
              <button class="action-btn warn" type="button" @click="clearSchedule(item.id)">清空课表</button>
              <button class="action-btn danger" type="button" @click="deleteSemester(item.id)">删除学期</button>
            </div>
          </li>
        </ul>
      </section>

      <!-- ④ 说明 -->
      <section class="card tips-card">
        <div class="section-label">说明</div>
        <ul class="tips-list">
          <li>导入课表前，请先确认教务账号已正确设置。</li>
          <li>清空课表将移除当前学期下所有课程数据，操作不可撤销。</li>
          <li>删除学期会同时删除该学期关联的所有课表记录，请谨慎操作。</li>
        </ul>
      </section>

    </main>

    <!-- 底部保存按钮 -->
    <footer class="bottom-bar">
      <button class="save-btn" type="button" @click="saveSemesters">保存学期管理</button>
    </footer>

    <!-- 未登录教务系统弹窗 -->
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

    <!-- 导入进度弹窗 -->
    <Teleport to="body">
      <Transition name="modal">
        <div v-if="showImportModal" class="modal-overlay" @click.self="importDone ? closeImportModal() : null">
          <div class="modal-panel import-panel">
            <div class="import-title">正在导入课表</div>
            <div class="import-subtitle">{{ importingItem ? importingItem.label : '' }}</div>
            <ul class="import-steps">
              <li v-for="step in IMPORT_STEPS" :key="step.key" class="import-step">
                <span class="step-icon" :class="getStepIcon(step.key)">
                  <svg v-if="getStepIcon(step.key) === 'done'" viewBox="0 0 16 16" width="16" height="16" fill="currentColor">
                    <path d="M13.78 4.22a.75.75 0 010 1.06l-7.25 7.25a.75.75 0 01-1.06 0L2.22 9.28a.75.75 0 011.06-1.06L6 10.94l6.72-6.72a.75.75 0 011.06 0z"/>
                  </svg>
                  <svg v-else-if="getStepIcon(step.key) === 'running'" class="spin" viewBox="0 0 16 16" width="16" height="16" fill="none">
                    <circle cx="8" cy="8" r="6" stroke="#dbeafe" stroke-width="2.5" />
                    <path d="M14 8a6 6 0 00-6-6" stroke="#2563eb" stroke-width="2.5" stroke-linecap="round" />
                  </svg>
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

  </div>
</template>

<style scoped>
/* ─── 整体外壳 ─── */
.page-shell {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: #f4f7fb;
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Helvetica Neue', sans-serif;
  color: #1f2937;
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
  background: #fff;
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

.nav-placeholder {
  width: 36px;
}

/* ─── 主体 ─── */
.page-body {
  flex: 1;
  padding: 14px 16px 100px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

/* ─── 通用卡片 ─── */
.card {
  background: #fff;
  border-radius: 12px;
  padding: 18px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
}

.section-label {
  font-size: 13px;
  font-weight: 700;
  color: #94a3b8;
  letter-spacing: 0.5px;
  text-transform: uppercase;
  margin-bottom: 14px;
}

/* ─── ① 教务账号 ─── */
.account-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.account-text {
  font-size: 14px;
  color: #374151;
}

.account-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.status-badge {
  font-size: 12px;
  font-weight: 600;
  padding: 3px 8px;
  border-radius: 20px;
  background: #fef2f2;
  color: #dc2626;
}

.status-badge.set {
  background: #f0fdf4;
  color: #16a34a;
}

.link-btn {
  border: none;
  background: none;
  font-size: 13px;
  font-weight: 600;
  color: #2563eb;
  cursor: pointer;
  padding: 0;
}

/* ─── ② 添加学期 ─── */
.form-group {
  margin-bottom: 14px;
}

.form-label {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: #6b7280;
  margin-bottom: 6px;
}

.year-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.year-input,
.form-select,
.form-input {
  flex: 1;
  height: 40px;
  padding: 0 12px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
  color: #1f2937;
  font-size: 14px;
  outline: none;
  transition: border-color 0.15s;
  appearance: none;
  -webkit-appearance: none;
}

.year-input:focus,
.form-select:focus,
.form-input:focus {
  border-color: #2563eb;
  background: #fff;
}

.year-sep {
  color: #94a3b8;
  font-size: 14px;
  flex-shrink: 0;
}

.form-select {
  background-image: url("data:image/svg+xml,%3Csvg viewBox='0 0 12 12' xmlns='http://www.w3.org/2000/svg' fill='%2394a3b8'%3E%3Cpath d='M2 4l4 4 4-4'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 12px center;
  background-size: 12px;
  padding-right: 32px;
}

.add-btn {
  width: 100%;
  height: 44px;
  border: none;
  border-radius: 10px;
  background: #f97316;
  color: #fff;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  transition: opacity 0.15s;
  margin-top: 4px;
}

.add-btn:hover {
  opacity: 0.9;
}

/* ─── ③ 学期列表 ─── */
.empty-tip {
  font-size: 13px;
  color: #9ca3af;
  text-align: center;
  padding: 18px 0;
}

.semester-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0;
}

.semester-item {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px 0;
  border-bottom: 1px solid #f1f5f9;
}

.semester-item:last-child {
  border-bottom: none;
}

.sem-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.sem-info {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.sem-label {
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.current-badge {
  font-size: 10px;
  font-weight: 700;
  padding: 2px 7px;
  border-radius: 10px;
  background: #eff6ff;
  color: #2563eb;
  letter-spacing: 0.3px;
}

.sem-date {
  font-size: 12px;
  color: #9ca3af;
}

.sem-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.action-btn {
  flex: 1 1 calc(50% - 3px);
  min-width: 0;
  height: 32px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  border: 1px solid transparent;
  transition: background 0.12s, opacity 0.12s;
  padding: 0 6px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.action-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.action-btn.primary {
  background: #f0fdf4;
  color: #16a34a;
  border-color: #bbf7d0;
}
.action-btn.primary:hover:not(:disabled) { background: #dcfce7; }

.action-btn.blue {
  background: #eff6ff;
  color: #2563eb;
  border-color: #bfdbfe;
}
.action-btn.blue:hover { background: #dbeafe; }

.action-btn.warn {
  background: #fffbeb;
  color: #d97706;
  border-color: #fde68a;
}
.action-btn.warn:hover { background: #fef3c7; }

.action-btn.danger {
  background: #fef2f2;
  color: #dc2626;
  border-color: #fecaca;
}
.action-btn.danger:hover { background: #fee2e2; }

/* ─── ④ 说明 ─── */
.tips-card {
  background: #fffbeb;
  border: 1px solid #fde68a;
}

.tips-card .section-label {
  color: #b45309;
}

.tips-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.tips-list li {
  position: relative;
  padding-left: 14px;
  font-size: 12px;
  color: #92400e;
  line-height: 1.6;
}

.tips-list li::before {
  content: '·';
  position: absolute;
  left: 0;
  color: #d97706;
  font-weight: 900;
}

/* ─── 底部保存按钮 ─── */
.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 14px 16px 22px;
  background: rgba(244, 247, 251, 0.92);
  backdrop-filter: blur(8px);
  border-top: 1px solid #eef2f7;
  z-index: 50;
}

.save-btn {
  width: 100%;
  height: 48px;
  border: none;
  border-radius: 12px;
  background: linear-gradient(135deg, #2563eb 0%, #7c3aed 100%);
  color: #fff;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
  transition: opacity 0.15s;
  box-shadow: 0 4px 16px rgba(37, 99, 235, 0.25);
}

.save-btn:hover {
  opacity: 0.92;
}

/* ─── 弹窗 ─── */
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

.modal-icon { margin-bottom: 4px; }

.modal-title {
  font-size: 16px;
  font-weight: 800;
  color: #111827;
  text-align: center;
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
.modal-btn:hover { opacity: 0.92; }

.modal-cancel-btn {
  width: 100%;
  height: 40px;
  margin-top: 6px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #fff;
  color: #64748b;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.12s;
}
.modal-cancel-btn:hover { background: #f8fafc; }

/* 导入进度 */
.import-panel { width: 340px; align-items: stretch; gap: 0; }

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
.step-icon.done  { color: #16a34a; }
.step-icon.running { color: #2563eb; }
.step-icon.pending { color: #d1d5db; }

.dot {
  display: block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #d1d5db;
}

.step-label { font-size: 14px; color: #9ca3af; }
.step-label.done    { color: #16a34a; font-weight: 600; }
.step-label.running { color: #2563eb; font-weight: 600; }

.spin { animation: spin-anim 0.8s linear infinite; }
@keyframes spin-anim {
  from { transform: rotate(0deg); }
  to   { transform: rotate(360deg); }
}

/* 弹窗动画 */
.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.2s ease;
}
.modal-enter-active .modal-panel,
.modal-leave-active .modal-panel {
  transition: transform 0.2s ease, opacity 0.2s ease;
}
.modal-enter-from,
.modal-leave-to { opacity: 0; }
.modal-enter-from .modal-panel,
.modal-leave-to .modal-panel {
  transform: scale(0.9);
  opacity: 0;
}
</style>
