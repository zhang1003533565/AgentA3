<template>
  <view class="settings-page">
    <nav-bar title="学期管理" :showBack="true" fixed placeholder />

    <view class="settings-shell">
      <view class="account-status-card">
        <view class="account-status-card__main">
          <text class="account-status-card__title">教务账号</text>
          <text class="account-status-card__desc">
            {{ accountConfigured ? '已配置，可直接导入课表' : '未配置，导入前需要先设置账号' }}
          </text>
        </view>
        <view class="account-status-card__side">
          <text class="account-status-badge" :class="{ 'account-status-badge--ok': accountConfigured }">
            {{ accountConfigured ? '已设置' : '未设置' }}
          </text>
          <button class="account-status-btn" @click="openScheduleAccountSettings">
            {{ accountConfigured ? '修改' : '去设置' }}
          </button>
        </view>
      </view>

      <view v-if="entryMode === 'import'" class="import-guide-card">
        <text class="import-guide-card__title">导入其他学期</text>
        <text class="import-guide-card__desc">选择下方对应学期，点击“导入此学期”。</text>
      </view>

      <view class="settings-card">
        <view class="settings-card__head settings-card__head--row">
          <view>
            <text class="settings-card__title">添加学期</text>
            <text class="settings-card__desc">先建学期，再选择要导入的学期</text>
          </view>
        </view>

        <view class="form-item">
          <text class="form-item__label">学年</text>
          <view class="academic-year-composer">
            <input
              v-model.trim="newSemesterYearStart"
              class="academic-year-input"
              type="number"
              maxlength="4"
              placeholder="2025"
              @input="handleNewAcademicYearStartInput"
              @blur="handleNewAcademicYearBlur"
            />
            <text class="academic-year-separator">-</text>
            <view class="academic-year-end">
              <text>{{ newSemesterYearEnd || '自动' }}</text>
            </view>
          </view>
        </view>

        <view class="form-grid">
          <view class="form-item form-item--grid">
            <text class="form-item__label">学期</text>
            <picker :range="semesterOptions" :value="newSemesterIndex" @change="handleNewTermChange">
              <view class="form-item__picker">
                <text class="form-item__value">{{ termLabel(newSemester.semesterTerm) }}</text>
              </view>
            </picker>
          </view>

          <view class="form-item form-item--grid">
            <text class="form-item__label">开学日期</text>
            <picker mode="date" :value="newSemester.semesterStart" @change="handleNewDateChange">
              <view class="form-item__picker">
                <text :class="newSemester.semesterStart ? 'form-item__value' : 'form-item__placeholder'">
                  {{ newSemester.semesterStart || '请选择' }}
                </text>
              </view>
            </picker>
          </view>
        </view>

        <button class="secondary-btn" @click="addSemester">添加学期</button>
      </view>

      <view class="semester-section">
        <view class="semester-section__head">
          <text class="semester-section__title">学期列表</text>
          <text class="semester-section__count">{{ semesterList.length }} 个学期</text>
        </view>

        <view v-if="!semesterList.length" class="empty-card">
          <text class="empty-card__title">还没有学期</text>
          <text class="empty-card__desc">添加一个学期后，就能选择它来导入课表。</text>
        </view>

        <view
          v-for="(item, index) in semesterList"
          :key="semesterKey(item)"
          class="semester-card"
          :class="{ 'semester-card--selected': item.selected }"
        >
          <view class="semester-card__top">
            <view class="semester-card__main">
              <text class="semester-card__title">{{ semesterLabel(item) }}</text>
              <text class="semester-card__meta">
                {{ Number(item.courseCount || 0) }} 门课 · 第 {{ Number(item.currentWeek || 1) }} 周
              </text>
            </view>
            <text v-if="item.selected" class="semester-badge">当前</text>
          </view>

          <picker mode="date" :value="item.semesterStart" @change="handleSemesterDateChange($event, index)">
            <view class="semester-date-row">
              <text class="semester-date-row__label">开学日期</text>
              <text :class="item.semesterStart ? 'semester-date-row__value' : 'semester-date-row__placeholder'">
                {{ item.semesterStart || '请选择开学日期' }}
              </text>
            </view>
          </picker>

          <view class="semester-actions">
            <button
              class="action-btn action-btn--ghost"
              :disabled="actionLocked"
              @click="setCurrentSemester(item)"
            >
              设为当前
            </button>
            <button
              class="action-btn action-btn--primary"
              :loading="importingKey === semesterKey(item)"
              :disabled="actionLocked"
              @click="importSemester(item)"
            >
              导入此学期
            </button>
            <button
              class="action-btn action-btn--warning"
              :loading="clearingKey === semesterKey(item)"
              :disabled="actionLocked"
              @click="confirmClearSemester(item)"
            >
              清空课表
            </button>
            <button
              class="action-btn action-btn--danger"
              :loading="deletingKey === semesterKey(item)"
              :disabled="actionLocked"
              @click="confirmDeleteSemester(item)"
            >
              删除学期
            </button>
          </view>
        </view>
      </view>

      <view class="tips-card">
        <text class="tips-card__title">说明</text>
        <text class="tips-card__line">导入只会覆盖所选学期，不会清空其他学期。</text>
        <text class="tips-card__line">重新导入后，如果教务系统调整了课程，会提示新增、删除和变更内容。</text>
        <text class="tips-card__line">清空课表会保留学期；删除学期会同时删除该学期课程。</text>
        <text class="tips-card__line">课表页顶部“切换”用于切换当前显示学期。</text>
      </view>

      <button class="save-btn" :loading="saving" :disabled="actionLocked" @click="saveSettings()">
        保存学期管理
      </button>
    </view>

    <import-progress
      :visible="showImportProgress"
      :title="importProgressTitle"
      :message="importProgressMessage"
      :status="importProgressStatus"
      :steps="importProgressSteps"
    />
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import ImportProgress from '@/components/import-progress/import-progress.vue'
import {
  checkJwxBind,
  clearSemesterSchedule,
  deleteScheduleSemester,
  getScheduleImportProgress,
  getScheduleSettings,
  importScheduleAuto,
  updateScheduleSettings
} from '@/api/schedule.js'

const semesterCodeForTerm = (term) => (Number(term) === 2 ? '12' : '3')

const normalizeAcademicYear = (value) => {
  const text = String(value || '').trim()
  if (/^\d{4}-\d{4}$/.test(text)) return text
  if (/^\d{4}$/.test(text)) return `${text}-${Number(text) + 1}`

  const now = new Date()
  const year = now.getFullYear()
  const startYear = now.getMonth() + 1 >= 8 ? year : year - 1
  return `${startYear}-${startYear + 1}`
}

const defaultSemesterStarts = (academicYear) => {
  const startYear = Number(normalizeAcademicYear(academicYear).slice(0, 4))
  return {
    1: `${startYear}-09-01`,
    2: `${startYear + 1}-03-01`
  }
}

const semesterKey = (item) => `${normalizeAcademicYear(item?.academicYear)}-${Number(item?.semesterTerm || 1)}`

export default {
  components: { NavBar, ImportProgress },
  data() {
    const academicYear = normalizeAcademicYear()
    return {
      saving: false,
      importing: false,
      importingKey: '',
      clearingKey: '',
      deletingKey: '',
      showImportProgress: false,
      importProgressTitle: '正在导入课表',
      importProgressMessage: '正在准备导入',
      importProgressStatus: 'running',
      importProgressSteps: [],
      importProgressPollTimer: null,
      importProgressStartedAt: 0,
      accountConfigured: false,
      entryMode: '',
      newSemesterYearStart: academicYear.slice(0, 4),
      semesterOptions: ['第 1 学期', '第 2 学期'],
      newSemester: {
        academicYear,
        semesterTerm: 1,
        semesterStart: defaultSemesterStarts(academicYear)[1]
      },
      semesterList: []
    }
  },
  onLoad(options = {}) {
    this.entryMode = options.mode || ''
  },
  computed: {
    newSemesterIndex() {
      return Math.max(0, Number(this.newSemester.semesterTerm || 1) - 1)
    },
    newSemesterYearEnd() {
      const startYear = Number(this.newSemesterYearStart)
      return Number.isInteger(startYear) && String(this.newSemesterYearStart).length === 4
        ? String(startYear + 1)
        : ''
    },
    selectedSemester() {
      return this.semesterList.find((item) => item.selected) || this.semesterList[0] || null
    },
    actionLocked() {
      return this.saving || this.importing || Boolean(this.clearingKey) || Boolean(this.deletingKey)
    }
  },
  onShow() {
    this.loadSettings()
  },
  onUnload() {
    this.stopImportProgressPolling()
  },
  beforeDestroy() {
    this.stopImportProgressPolling()
  },
  methods: {
    semesterKey,
    async loadSettings(options = {}) {
      const silent = Boolean(options.silent)
      try {
        if (!silent) {
          uni.showLoading({ title: '加载中...' })
        }
        const res = await getScheduleSettings()
        const data = res.data || {}
        this.accountConfigured = Boolean(data.jwxStudentId && data.jwxPassword)
        this.refreshAccountStatus()
        const academicYear = normalizeAcademicYear(data.academicYear)
        const semesterTerm = Number(data.semesterTerm || 1)
        this.semesterList = this.normalizeSemesterList(
          data.semesters || [],
          academicYear,
          semesterTerm,
          data.semesterStart
        )
        const nextStart = defaultSemesterStarts(academicYear)[semesterTerm]
        this.newSemesterYearStart = academicYear.slice(0, 4)
        this.newSemester = {
          academicYear,
          semesterTerm,
          semesterStart: data.semesterStart || nextStart
        }
      } catch (error) {
        uni.showToast({ title: error?.msg || error?.message || '加载失败', icon: 'none' })
      } finally {
        if (!silent) {
          uni.hideLoading()
        }
      }
    },
    async refreshAccountStatus() {
      try {
        const res = await checkJwxBind()
        this.accountConfigured = Boolean(res.data?.binded)
      } catch (error) {
        // 保留设置接口返回的状态，避免状态检查失败影响学期管理。
      }
    },
    normalizeSemesterList(rawList, selectedAcademicYear, selectedTerm, selectedStart) {
      const byKey = new Map()
      const addItem = (source = {}) => {
        const academicYear = normalizeAcademicYear(source.academicYear || selectedAcademicYear)
        const semesterTerm = Number(source.semesterTerm || selectedTerm || 1)
        if (semesterTerm !== 1 && semesterTerm !== 2) return
        const defaults = defaultSemesterStarts(academicYear)
        const key = `${academicYear}-${semesterTerm}`
        const existing = byKey.get(key) || {}
        byKey.set(key, {
          academicYear,
          semesterTerm,
          semesterCode: source.semesterCode || semesterCodeForTerm(semesterTerm),
          semesterStart: source.semesterStart || existing.semesterStart || defaults[semesterTerm],
          selected: Boolean(source.selected) || (
            academicYear === selectedAcademicYear && semesterTerm === Number(selectedTerm || 1)
          ),
          currentWeek: Number(source.currentWeek || existing.currentWeek || 1),
          courseCount: Number(source.courseCount || existing.courseCount || 0)
        })
      }

      ;(Array.isArray(rawList) ? rawList : []).forEach(addItem)
      const sorted = this.sortSemesterList(Array.from(byKey.values()))
      if (sorted.length && !sorted.some((item) => item.selected)) {
        sorted[0] = { ...sorted[0], selected: true }
      }
      return sorted
    },
    sortSemesterList(list) {
      return [...list].sort((a, b) => {
        if (a.academicYear !== b.academicYear) return b.academicYear.localeCompare(a.academicYear)
        return Number(b.semesterTerm) - Number(a.semesterTerm)
      })
    },
    buildAcademicYearFromStartYear(value) {
      const digits = String(value || '').replace(/\D/g, '').slice(0, 4)
      if (digits.length !== 4) return ''
      return `${digits}-${Number(digits) + 1}`
    },
    syncNewAcademicYearFromStartYear() {
      const digits = String(this.newSemesterYearStart || '').replace(/\D/g, '').slice(0, 4)
      this.newSemesterYearStart = digits
      const academicYear = this.buildAcademicYearFromStartYear(digits)
      if (!academicYear) return ''
      const term = Number(this.newSemester.semesterTerm || 1)
      this.newSemester = {
        ...this.newSemester,
        academicYear,
        semesterStart: this.newSemester.semesterStart || defaultSemesterStarts(academicYear)[term]
      }
      return academicYear
    },
    handleNewAcademicYearStartInput(e) {
      this.newSemesterYearStart = String(e.detail.value || '').replace(/\D/g, '').slice(0, 4)
      if (this.newSemesterYearStart.length === 4) {
        this.syncNewAcademicYearFromStartYear()
      }
    },
    handleNewAcademicYearBlur() {
      const academicYear = this.syncNewAcademicYearFromStartYear() || normalizeAcademicYear(this.newSemester.academicYear)
      this.newSemesterYearStart = academicYear.slice(0, 4)
      const term = Number(this.newSemester.semesterTerm || 1)
      this.newSemester = {
        ...this.newSemester,
        academicYear,
        semesterStart: this.newSemester.semesterStart || defaultSemesterStarts(academicYear)[term]
      }
    },
    handleNewTermChange(e) {
      const semesterTerm = Number(e.detail.value) + 1
      const academicYear = this.syncNewAcademicYearFromStartYear() || normalizeAcademicYear(this.newSemester.academicYear)
      this.newSemester = {
        ...this.newSemester,
        academicYear,
        semesterTerm,
        semesterStart: defaultSemesterStarts(academicYear)[semesterTerm]
      }
    },
    handleNewDateChange(e) {
      this.newSemester = {
        ...this.newSemester,
        semesterStart: e.detail.value
      }
    },
    handleSemesterDateChange(e, index) {
      const list = [...this.semesterList]
      list[index] = {
        ...list[index],
        semesterStart: e.detail.value
      }
      this.semesterList = list
    },
    addSemester() {
      const academicYear = this.syncNewAcademicYearFromStartYear() || normalizeAcademicYear(this.newSemester.academicYear)
      const semesterTerm = Number(this.newSemester.semesterTerm || 1)
      const semesterStart = this.newSemester.semesterStart || defaultSemesterStarts(academicYear)[semesterTerm]
      if (!semesterStart) {
        uni.showToast({ title: '请选择开学日期', icon: 'none' })
        return
      }

      const key = `${academicYear}-${semesterTerm}`
      const existing = this.semesterList.find((item) => semesterKey(item) === key)
      const item = {
        ...(existing || {}),
        academicYear,
        semesterTerm,
        semesterCode: semesterCodeForTerm(semesterTerm),
        semesterStart,
        selected: existing ? existing.selected : this.semesterList.length === 0,
        currentWeek: existing?.currentWeek || 1,
        courseCount: existing?.courseCount || 0
      }
      this.semesterList = this.sortSemesterList([
        item,
        ...this.semesterList.filter((semester) => semesterKey(semester) !== key)
      ])
      uni.showToast({ title: existing ? '学期已更新' : '学期已添加', icon: 'success' })
    },
    termLabel(term) {
      return Number(term) === 2 ? '第 2 学期' : '第 1 学期'
    },
    semesterLabel(item) {
      return `${item.academicYear} ${this.termLabel(item.semesterTerm)}`
    },
    markSelected(item) {
      const selectedKey = semesterKey(item)
      this.semesterList = this.semesterList.map((semester) => ({
        ...semester,
        selected: semesterKey(semester) === selectedKey
      }))
    },
    validateSemesterList() {
      if (!this.semesterList.length) {
        uni.showToast({ title: '请先添加学期', icon: 'none' })
        return false
      }
      const invalid = this.semesterList.find((item) => !item.semesterStart)
      if (invalid) {
        uni.showToast({ title: `${this.semesterLabel(invalid)} 缺少开学日期`, icon: 'none' })
        return false
      }
      return true
    },
    buildSettingsPayload(selectedItem) {
      const selected = selectedItem || this.selectedSemester || this.semesterList[0]
      return {
        academicYear: selected.academicYear,
        semesterTerm: selected.semesterTerm,
        semesterStart: selected.semesterStart,
        selected: true,
        semesters: this.semesterList.map((item) => ({
          academicYear: normalizeAcademicYear(item.academicYear),
          semesterTerm: Number(item.semesterTerm || 1),
          semesterCode: semesterCodeForTerm(item.semesterTerm),
          semesterStart: item.semesterStart
        }))
      }
    },
    async saveSettings(options = {}) {
      const selectedItem = options.selectedItem || this.selectedSemester
      const silent = Boolean(options.silent)
      if (!this.validateSemesterList()) return false
      this.saving = true
      try {
        const selected = selectedItem || this.selectedSemester
        this.markSelected(selected)
        await updateScheduleSettings(this.buildSettingsPayload(selected))
        if (!silent) {
          uni.showToast({ title: '保存成功', icon: 'success' })
        }
        return true
      } catch (error) {
        if (!silent) {
          uni.showToast({ title: error?.msg || error?.message || '保存失败', icon: 'none' })
        }
        return false
      } finally {
        this.saving = false
      }
    },
    async setCurrentSemester(item) {
      const saved = await this.saveSettings({ selectedItem: item })
      if (saved) {
        this.markSelected(item)
      }
    },
    confirmClearSemester(item) {
      if (!item) return
      uni.showModal({
        title: '清空课表',
        content: `确定清空 ${this.semesterLabel(item)} 的课程吗？学期会保留，可以之后重新导入。`,
        confirmText: '清空',
        confirmColor: '#d95b37',
        success: (res) => {
          if (res.confirm) {
            this.clearSemester(item)
          }
        }
      })
    },
    async clearSemester(item) {
      const key = semesterKey(item)
      this.clearingKey = key
      try {
        const res = await clearSemesterSchedule({
          academicYear: normalizeAcademicYear(item.academicYear),
          semesterTerm: Number(item.semesterTerm || 1)
        })
        const removedCount = Number(res.data?.removedCount || 0)
        uni.showToast({ title: `已清空 ${removedCount} 门课`, icon: 'success' })
        await this.loadSettings({ silent: true })
      } catch (error) {
        uni.showToast({ title: error?.msg || error?.message || '清空失败', icon: 'none' })
      } finally {
        this.clearingKey = ''
      }
    },
    confirmDeleteSemester(item) {
      if (!item) return
      uni.showModal({
        title: '删除学期',
        content: `确定删除 ${this.semesterLabel(item)} 吗？该学期的课程也会一起删除。`,
        confirmText: '删除',
        confirmColor: '#e5484d',
        success: (res) => {
          if (res.confirm) {
            this.deleteSemester(item)
          }
        }
      })
    },
    async deleteSemester(item) {
      const key = semesterKey(item)
      this.deletingKey = key
      try {
        const res = await deleteScheduleSemester({
          academicYear: normalizeAcademicYear(item.academicYear),
          semesterTerm: Number(item.semesterTerm || 1)
        })
        const removedCount = Number(res.data?.removedCount || 0)
        uni.showToast({ title: removedCount ? `已删除 ${removedCount} 门课` : '学期已删除', icon: 'success' })
        await this.loadSettings({ silent: true })
      } catch (error) {
        uni.showToast({ title: error?.msg || error?.message || '删除失败', icon: 'none' })
      } finally {
        this.deletingKey = ''
      }
    },
    openScheduleAccountSettings() {
      uni.navigateTo({
        url: '/subpackage_schedule/scheduleAccountSettings/scheduleAccountSettings'
      })
    },
    createImportProgressSteps(semesterTerm) {
      return [
        { key: 'connect', title: '连接教务系统', desc: '正在打开教务系统登录页', status: 'active' },
        { key: 'login', title: '登录教务账号', desc: '等待账号验证完成', status: 'waiting' },
        { key: 'query', title: '进入课表查询', desc: '打开个人课表查询页面', status: 'waiting' },
        { key: 'read', title: `读取第 ${semesterTerm} 学期`, desc: '获取课程、时间和教室信息', status: 'waiting' },
        { key: 'save', title: '保存课程数据', desc: '同步到我的课表', status: 'waiting' }
      ]
    },
    startImportProgress(semesterTerm) {
      this.stopImportProgressPolling()
      this.importProgressTitle = `导入第 ${semesterTerm} 学期课表`
      this.importProgressMessage = '正在准备导入'
      this.importProgressStatus = 'running'
      this.importProgressSteps = this.createImportProgressSteps(semesterTerm)
      this.importProgressStartedAt = Date.now()
      this.showImportProgress = true
      this.importProgressPollTimer = setInterval(() => {
        this.pollImportProgress()
      }, 900)
      this.pollImportProgress()
    },
    stopImportProgressPolling() {
      if (this.importProgressPollTimer) {
        clearInterval(this.importProgressPollTimer)
        this.importProgressPollTimer = null
      }
    },
    async pollImportProgress() {
      try {
        const res = await getScheduleImportProgress()
        this.applyImportProgress(res.data || {})
      } catch (error) {
        // 轮询失败不打断导入请求，保留当前展示。
      }
    },
    applyImportProgress(progress) {
      const stepOrder = ['connect', 'login', 'query', 'read', 'save']
      const step = progress.step || 'connect'
      const status = progress.status || 'running'
      if (progress.updatedAt && progress.updatedAt < this.importProgressStartedAt - 1000) {
        return
      }
      const activeIndex = stepOrder.includes(step) ? stepOrder.indexOf(step) : 0
      this.importProgressMessage = progress.message || this.importProgressMessage
      this.importProgressStatus = status === 'failed' ? 'failed' : (status === 'done' ? 'done' : 'running')
      this.importProgressSteps = this.importProgressSteps.map((item, index) => {
        if (status === 'done') {
          return { ...item, status: 'done' }
        }
        if (status === 'failed') {
          return {
            ...item,
            status: index < activeIndex ? 'done' : (index === activeIndex ? 'failed' : 'waiting')
          }
        }
        return {
          ...item,
          status: index < activeIndex ? 'done' : (index === activeIndex ? 'active' : 'waiting'),
          desc: item.key === step && progress.message ? progress.message : item.desc
        }
      })
    },
    finishImportProgress(message) {
      this.stopImportProgressPolling()
      this.importProgressStatus = 'done'
      this.importProgressMessage = message
      this.importProgressSteps = this.importProgressSteps.map((item) => ({ ...item, status: 'done' }))
      setTimeout(() => {
        this.showImportProgress = false
      }, 900)
    },
    failImportProgress(message) {
      this.stopImportProgressPolling()
      this.importProgressStatus = 'failed'
      this.importProgressMessage = message || '导入失败，请稍后重试'
      if (!this.importProgressSteps.some((item) => item.status === 'failed')) {
        this.importProgressSteps = this.importProgressSteps.map((item, index) => ({
          ...item,
          status: index === 0 ? 'failed' : 'waiting'
        }))
      }
      setTimeout(() => {
        this.showImportProgress = false
      }, 1800)
    },
    async importSemester(item) {
      if (!item) return
      const saved = await this.saveSettings({ selectedItem: item, silent: true })
      if (!saved) return

      const key = semesterKey(item)
      this.importing = true
      this.importingKey = key
      try {
        const bindRes = await checkJwxBind()
        if (!bindRes.data?.binded) {
          this.accountConfigured = false
          uni.showModal({
            title: '需要先设置账号',
            content: '导入课表前需要先设置教务系统账号。设置完成后回来点“导入此学期”。',
            confirmText: '去设置',
            success: (modalRes) => {
              if (modalRes.confirm) {
                this.openScheduleAccountSettings()
              }
            }
          })
          return
        }

        this.startImportProgress(Number(item.semesterTerm || 1))
        const res = await importScheduleAuto({
          academicYear: normalizeAcademicYear(item.academicYear),
          selectedSemesterTerm: Number(item.semesterTerm || 1),
          importBothTerms: false,
          semesterStarts: {
            [String(item.semesterTerm)]: item.semesterStart
          }
        })
        const count = res.data?.count || 0
        this.finishImportProgress(`导入完成，共 ${count} 门课`)
        uni.showToast({ title: `成功导入 ${count} 门课`, icon: 'success' })
        this.showImportChangeSummary(res.data?.changes)
        await this.loadSettings({ silent: true })
      } catch (error) {
        this.failImportProgress(error?.msg || error?.message || '导入失败')
        uni.showToast({ title: error?.msg || error?.message || '导入失败', icon: 'none' })
      } finally {
        this.importing = false
        this.importingKey = ''
      }
    },
    showImportChangeSummary(changes) {
      if (!changes || !changes.hasChanges || Number(changes.oldCount || 0) === 0) {
        return
      }

      const lines = ['教务系统课表有更新：']
      this.appendChangeLines(lines, '新增', changes.addedCount, changes.added)
      this.appendChangeLines(lines, '删除', changes.removedCount, changes.removed)
      this.appendUpdatedChangeLines(lines, changes.updatedCount, changes.updated)

      uni.showModal({
        title: '课表有更新',
        content: lines.join('\n'),
        showCancel: false,
        confirmText: '知道了'
      })
    },
    appendChangeLines(lines, label, count, items = []) {
      const total = Number(count || 0)
      if (!total) return
      lines.push(`${label} ${total} 门`)
      items.slice(0, 4).forEach((item) => {
        lines.push(`- ${item.summary || item.courseName}`)
      })
      if (total > 4) {
        lines.push(`- 还有 ${total - 4} 门`)
      }
    },
    appendUpdatedChangeLines(lines, count, items = []) {
      const total = Number(count || 0)
      if (!total) return
      lines.push(`变更 ${total} 门`)
      items.slice(0, 4).forEach((item) => {
        const fields = Array.isArray(item.changedFields) && item.changedFields.length
          ? item.changedFields.join('、')
          : '课程信息'
        lines.push(`- ${item.summary || item.courseName}：${fields}`)
      })
      if (total > 4) {
        lines.push(`- 还有 ${total - 4} 门`)
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.settings-page {
  min-height: 100vh;
  background: linear-gradient(180deg, #eef7ff 0%, #f8fbff 52%, #f7f9fb 100%);
}

.settings-shell {
  padding: 30rpx 24rpx 44rpx;
  box-sizing: border-box;
}

.settings-card,
.tips-card,
.empty-card,
.semester-card,
.account-status-card,
.import-guide-card {
  background: rgba(255, 255, 255, 0.96);
  border-radius: 24rpx;
  box-shadow: 0 14rpx 32rpx rgba(65, 102, 153, 0.08);
}

.account-status-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
  padding: 24rpx 28rpx;
  margin-bottom: 22rpx;
  border: 2rpx solid rgba(220, 233, 251, 0.78);
  box-sizing: border-box;
}

.account-status-card__main {
  flex: 1;
  min-width: 0;
}

.account-status-card__title {
  display: block;
  font-size: 30rpx;
  font-weight: 700;
  color: #21334d;
}

.account-status-card__desc {
  display: block;
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #7890ad;
}

.account-status-card__side {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.account-status-badge {
  flex-shrink: 0;
  padding: 9rpx 16rpx;
  border-radius: 999rpx;
  background: #fff2eb;
  color: #d95b37;
  font-size: 22rpx;
  font-weight: 700;
  white-space: nowrap;
}

.account-status-badge--ok {
  background: #eaf8f3;
  color: #168b6a;
}

.account-status-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 104rpx;
  height: 60rpx;
  margin: 0;
  padding: 0 20rpx;
  border: none;
  border-radius: 16rpx;
  background: #1d1d1f;
  color: #fff;
  font-size: 24rpx;
  font-weight: 700;
  line-height: normal;
  white-space: nowrap;
}

.account-status-btn::after {
  border: none;
}

.import-guide-card {
  padding: 22rpx 28rpx;
  margin-bottom: 22rpx;
  border: 2rpx solid rgba(63, 125, 242, 0.24);
  background: linear-gradient(135deg, #f5f9ff, #ffffff);
  box-sizing: border-box;
}

.import-guide-card__title {
  display: block;
  font-size: 28rpx;
  font-weight: 700;
  color: #21334d;
}

.import-guide-card__desc {
  display: block;
  margin-top: 8rpx;
  font-size: 23rpx;
  color: #5f7898;
}

.settings-card {
  padding: 28rpx 28rpx 30rpx;
  margin-bottom: 26rpx;
}

.settings-card__head {
  margin-bottom: 18rpx;
}

.settings-card__head--row,
.semester-section__head,
.semester-card__top,
.semester-actions,
.semester-date-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.settings-card__title,
.tips-card__title,
.semester-section__title,
.empty-card__title {
  display: block;
  font-size: 30rpx;
  font-weight: 700;
  color: #21334d;
}

.settings-card__desc,
.empty-card__desc {
  display: block;
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #7890ad;
}

.form-item + .form-item {
  margin-top: 24rpx;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20rpx;
  margin-top: 24rpx;
}

.form-item--grid {
  margin-top: 0 !important;
}

.form-item__label {
  display: block;
  margin-bottom: 12rpx;
  font-size: 24rpx;
  color: #48627f;
}

.form-item__input,
.form-item__picker,
.academic-year-input,
.academic-year-end {
  width: 100%;
  min-height: 92rpx;
  padding: 0 24rpx;
  box-sizing: border-box;
  border-radius: 20rpx;
  background: #f7fbff;
  border: 2rpx solid #dce9fb;
  display: flex;
  align-items: center;
  font-size: 28rpx;
  color: #21334d;
}

.academic-year-composer {
  display: flex;
  align-items: center;
  gap: 18rpx;
}

.academic-year-input,
.academic-year-end {
  flex: 1;
  min-width: 0;
  text-align: center;
}

.academic-year-input {
  font-weight: 700;
}

.academic-year-end {
  justify-content: center;
  background: #f2f6fb;
  color: #48627f;
  font-weight: 700;
}

.academic-year-separator {
  flex-shrink: 0;
  font-size: 30rpx;
  font-weight: 700;
  color: #7d8fa5;
}

.form-item__value {
  color: #21334d;
}

.form-item__placeholder {
  color: #96a7bb;
}

.secondary-btn,
.save-btn,
.action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  padding: 0;
  box-sizing: border-box;
  line-height: normal;
  white-space: nowrap;
}

.secondary-btn::after,
.save-btn::after,
.action-btn::after {
  border: none;
}

.secondary-btn {
  margin-top: 28rpx;
  height: 84rpx;
  border-radius: 18rpx;
  background: #fff2eb;
  color: #d95b37;
  font-size: 28rpx;
  font-weight: 700;
}

.semester-section {
  margin-top: 10rpx;
}

.semester-section__head {
  padding: 4rpx 4rpx 18rpx;
}

.semester-section__count {
  font-size: 22rpx;
  color: #8b98a8;
}

.empty-card {
  padding: 34rpx 28rpx;
}

.semester-card {
  padding: 26rpx 26rpx 24rpx;
  margin-bottom: 20rpx;
  border: 2rpx solid rgba(220, 233, 251, 0.78);
}

.semester-card--selected {
  border-color: rgba(63, 125, 242, 0.58);
  box-shadow: 0 16rpx 34rpx rgba(63, 125, 242, 0.12);
}

.semester-card__main {
  flex: 1;
  min-width: 0;
}

.semester-card__title {
  display: block;
  font-size: 30rpx;
  font-weight: 700;
  color: #20324c;
}

.semester-card__meta {
  display: block;
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #7d8fa5;
}

.semester-badge {
  flex-shrink: 0;
  padding: 10rpx 18rpx;
  border-radius: 999rpx;
  background: #eaf2ff;
  color: #3f7df2;
  font-size: 22rpx;
  font-weight: 700;
}

.semester-date-row {
  min-height: 82rpx;
  margin-top: 24rpx;
  padding: 0 22rpx;
  border-radius: 18rpx;
  background: #f7fbff;
  border: 2rpx solid #e3edf8;
  box-sizing: border-box;
}

.semester-date-row__label {
  font-size: 24rpx;
  color: #5c7088;
}

.semester-date-row__value,
.semester-date-row__placeholder {
  font-size: 24rpx;
}

.semester-date-row__value {
  color: #21334d;
}

.semester-date-row__placeholder {
  color: #9aa9ba;
}

.semester-actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18rpx;
  margin-top: 24rpx;
}

.action-btn {
  width: 100%;
  height: 74rpx;
  min-width: 0;
  margin: 0;
  padding: 0 12rpx;
  border-radius: 16rpx;
  box-sizing: border-box;
  font-size: 24rpx;
  font-weight: 700;
  line-height: normal;
  text-align: center;
  overflow: visible;
}

.action-btn--ghost {
  background: #f2f6fb;
  color: #536b87;
}

.action-btn--primary {
  background: #1d1d1f;
  color: #fff;
}

.action-btn--warning {
  background: #fff2eb;
  color: #d95b37;
}

.action-btn--danger {
  background: #fff0f0;
  color: #e5484d;
}

.tips-card {
  margin-top: 20rpx;
  padding: 26rpx 28rpx;
}

.tips-card__line {
  display: block;
  margin-top: 12rpx;
  font-size: 24rpx;
  line-height: 1.7;
  color: #5d738c;
}

.save-btn {
  margin-top: 30rpx;
  height: 92rpx;
  border-radius: 999rpx;
  background: linear-gradient(135deg, #3f7df2, #23b08a);
  color: #fff;
  font-size: 30rpx;
  font-weight: 700;
  box-shadow: 0 18rpx 32rpx rgba(63, 125, 242, 0.18);
}
</style>
