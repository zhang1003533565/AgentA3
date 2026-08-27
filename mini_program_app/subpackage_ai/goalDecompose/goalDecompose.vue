<template>
  <view class="page">
    <nav-bar
      title="学习计划拆解"
      :showBack="true"
      :border="false"
    />

    <scroll-view class="content" scroll-y :show-scrollbar="false">
      <view class="content-inner">
        <!-- 第一步：输入学习计划 -->
        <view v-if="phase === 'input'" class="section-card input-card">
          <view class="card-head">
            <text class="card-title">选择学习计划来源</text>
            <view class="head-right">
              <text class="my-plans-link" @tap="goMyPlans">我的计划</text>
              <text class="char-count">{{ planText.length }}/8000</text>
            </view>
          </view>

          <textarea
            class="plan-input"
            v-model="planText"
            @input="onPlanTextInput"
            placeholder="粘贴你的学习计划或目标描述，例如：30天学会Python爬虫，从零基础到完成一个简单爬虫项目..."
            placeholder-class="plan-placeholder"
            :maxlength="8000"
          />

          <view class="upload-row" @tap="chooseFile">
            <view class="upload-icon-wrap">
              <view class="upload-icon-arrow"></view>
            </view>
            <view class="upload-info">
              <text class="upload-main">{{ chosenFile ? '重新选择文件' : '点击上传数据表' }}</text>
              <text class="upload-sub">支持 .xlsx / .csv，也可直接粘贴文本</text>
            </view>
          </view>

          <view v-if="chosenFile" class="chosen-file">
            <view class="file-chip">
              <view class="file-chip__badge">{{ fileSuffixLabel }}</view>
              <view class="file-chip__info">
                <text class="file-chip__name">{{ chosenFile.name }}</text>
                <text class="file-chip__size">{{ fileSizeText }}</text>
              </view>
              <view class="file-chip__remove" @tap.stop="removeFile"><text>移除</text></view>
            </view>
            <text class="chosen-file__tip">已选择文件时优先使用文件内容进行拆解</text>
          </view>

          <view
            class="primary-btn"
            :class="{ 'primary-btn--disabled': decomposing || !canDecompose }"
            @tap="startDecompose"
          >
            <text>{{ decomposing ? 'AI 正在拆解中...' : '开始拆解' }}</text>
          </view>
        </view>

        <!-- 第二步：拆解预览（未入库） -->
        <template v-if="phase === 'preview'">
          <view class="section-card goal-card">
            <view class="goal-head">
              <text class="goal-badge">预览</text>
              <input class="goal-title-input" v-model="previewGoal.title" maxlength="120" />
            </view>
            <textarea class="goal-desc-input" v-model="previewGoal.description" maxlength="500" placeholder="补充目标说明（可选）" />
            <view class="date-settings">
              <view class="date-setting">
                <text class="date-label">开始日期</text>
                <picker mode="date" :value="previewGoal.startDate" @change="changePreviewDate('startDate', $event)">
                  <text class="date-value">{{ formatPlanDate(previewGoal.startDate) }}</text>
                </picker>
              </view>
              <view class="date-setting">
                <text class="date-label">目标日期</text>
                <picker mode="date" :value="previewGoal.targetDate" @change="changePreviewDate('targetDate', $event)">
                  <text class="date-value">{{ previewGoal.targetDate ? formatPlanDate(previewGoal.targetDate) : '不设置' }}</text>
                </picker>
              </view>
              <view class="date-setting capacity-setting">
                <text class="date-label">每日学习容量</text>
                <view class="capacity-line">
                  <input class="capacity-input" type="number" v-model="previewGoal.dailyStudyMinutes" />
                  <text class="date-value">分钟</text>
                </view>
              </view>
            </view>
            <view class="goal-meta-row">
              <text class="goal-meta">共 {{ previewTasks.length }} 个任务</text>
              <text class="goal-meta-split">·</text>
              <text class="goal-meta">预计 {{ totalEstimatedDays }} 天</text>
              <text class="goal-meta-split">·</text>
              <text class="goal-meta">排至 {{ formatPlanDate(previewTasks[previewTasks.length - 1]?.plannedEndDate) }}</text>
            </view>
          </view>

          <view class="section-card task-table">
            <view class="table-header task-row">
              <text class="col-check">#</text>
              <text class="col-name">任务（可编辑）</text>
              <text class="col-stage">阶段</text>
              <text class="col-days">天数</text>
              <text class="col-priority">优先级</text>
            </view>
            <view class="task-row task-row--editable" v-for="(task, index) in previewTasks" :key="task.orderNum">
              <text class="col-check">{{ task.orderNum }}</text>
              <view class="col-name">
                <input class="task-edit-input" v-model="task.taskName" maxlength="120" placeholder="任务名称" />
                <input class="task-edit-desc" v-model="task.description" maxlength="500" placeholder="任务说明（可选）" />
              </view>
              <input class="stage-edit-input" v-model="task.stage" maxlength="60" placeholder="阶段" />
              <input class="days-edit-input" type="number" v-model="task.estimatedDays" @input="reschedulePreview" />
              <view class="col-priority">
                <view class="priority-tag" :class="`priority-tag--${priorityLevel(task.priority)}`" @tap="cyclePriority(task)">
                  <text>{{ task.priority }}</text>
                </view>
              </view>
              <view class="row-actions">
                <text @tap="movePreviewTask(index, -1)">上移</text>
                <text @tap="movePreviewTask(index, 1)">下移</text>
                <text class="row-action--danger" @tap="removePreviewTask(index)">删除</text>
              </view>
            </view>
            <view class="add-task-row" @tap="addPreviewTask"><text>＋ 添加任务</text></view>
          </view>

          <view class="action-bar">
            <view class="ghost-btn" @tap="backToInput"><text>返回修改</text></view>
            <view class="primary-btn primary-btn--inline" :class="{ 'primary-btn--disabled': saving }" @tap="confirmSave">
              <text>{{ saving ? '创建中...' : '确认创建任务' }}</text>
            </view>
          </view>
        </template>

        <!-- 第三步：任务执行清单（可勾选） -->
        <template v-if="phase === 'saved' && goalDetail">
          <view class="section-card goal-card">
            <view class="goal-head">
              <text class="goal-badge" :class="`goal-badge--${goalDetail.goal.status}`">{{ statusLabel }}</text>
              <text class="goal-title">{{ goalDetail.goal.title }}</text>
            </view>
            <text v-if="goalDetail.goal.description" class="goal-desc">{{ goalDetail.goal.description }}</text>
            <view class="progress-line">
              <view class="progress-track">
                <view class="progress-fill" :style="{ width: `${goalDetail.goal.progress}%` }"></view>
              </view>
              <text class="progress-num">{{ progressPercent }}%</text>
            </view>
            <view class="goal-stats">
              <text class="goal-stat">已完成 {{ completedCount }} 项</text>
              <text class="goal-meta-split">·</text>
              <text class="goal-stat goal-stat--remain">剩余 {{ remainingCount }} 项</text>
              <text class="goal-status-text">{{ statusLabel }}</text>
            </view>
            <view class="schedule-summary">
              <text>计划开始 {{ formatPlanDate(goalDetail.goal.startDate) }}</text>
              <text v-if="goalDetail.goal.targetDate">目标 {{ formatPlanDate(goalDetail.goal.targetDate) }}</text>
              <text>每天 {{ goalDetail.goal.dailyStudyMinutes || 60 }} 分钟</text>
            </view>
          </view>

          <view class="section-card filter-card">
            <view
              class="filter-tab"
              :class="{ 'filter-tab--active': activeFilter === 'today' }"
              @tap="activeFilter = 'today'"
            >
              <text>今日 {{ todayCount }}</text>
            </view>
            <view
              class="filter-tab"
              :class="{ 'filter-tab--active': activeFilter === 'all' }"
              @tap="activeFilter = 'all'"
            >
              <text>全部 {{ goalDetail.tasks.length }}</text>
            </view>
            <view
              class="filter-tab"
              :class="{ 'filter-tab--active': activeFilter === 'pending' }"
              @tap="activeFilter = 'pending'"
            >
              <text>剩余任务 {{ remainingCount }}</text>
            </view>
            <view
              class="filter-tab"
              :class="{ 'filter-tab--active': activeFilter === 'completed' }"
              @tap="activeFilter = 'completed'"
            >
              <text>已完成 {{ completedCount }}</text>
            </view>
          </view>

          <view class="section-card task-table">
            <view v-if="filteredTasks.length === 0" class="empty-state">
              <text class="empty-main">{{ emptyText }}</text>
              <text class="empty-sub">{{ emptySubText }}</text>
            </view>
            <view
              class="task-item"
              v-for="task in filteredTasks"
              :key="task.id"
              :class="{ 'task-item--completed': task.isCompleted }"
              @tap="toggleTask(task)"
            >
              <view class="checkbox" :class="{ 'checkbox--checked': task.isCompleted, 'checkbox--disabled': togglingIds.includes(task.id) }">
                <view v-if="task.isCompleted" class="checkbox-mark"></view>
              </view>
              <view class="task-body">
                <text class="task-name" :class="{ 'task-name--done': task.isCompleted }">{{ task.taskName }}</text>
                <view class="task-meta">
                  <text v-if="task.stage" class="meta-text">{{ task.stage }}</text>
                  <text v-if="task.estimatedDays" class="meta-text">预计{{ task.estimatedDays }}天</text>
                  <view class="priority-tag priority-tag--small" :class="`priority-tag--${priorityLevel(task.priority)}`">
                    <text>{{ task.priority }}</text>
                  </view>
                  <text class="meta-text">{{ formatPlanDate(task.plannedStartDate) }}-{{ formatPlanDate(task.plannedEndDate) }}</text>
                </view>
                <slider
                  class="task-progress-slider"
                  :value="task.progressPercent"
                  min="0"
                  max="100"
                  step="5"
                  activeColor="#5C7A99"
                  backgroundColor="#EEF1F7"
                  block-size="16"
                  @changing.stop="previewTaskProgress(task, $event)"
                  @change.stop="changeTaskProgress(task, $event)"
                />
                <view class="task-controls">
                  <picker :range="statusOptions" :value="statusIndex(task.status)" @change.stop="changeTaskStatus(task, $event)">
                    <text class="status-picker">状态：{{ statusText(task.status) }}</text>
                  </picker>
                  <text class="postpone-link" @tap.stop="postponeTask(task)">延期 1 天</text>
                </view>
              </view>
            </view>
          </view>

          <view class="action-bar action-bar--bottom">
            <view class="ghost-btn ghost-btn--wide" @tap="backToInput"><text>再拆解一份计划</text></view>
          </view>
        </template>
      </view>
    </scroll-view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import NavBar from '@/components/nav-bar/nav-bar.vue'
import {
  decomposeStudyFile,
  decomposeStudyText,
  saveStudyGoal,
  updateStudyTaskCompletion,
  updateStudyTaskProgress,
  updateStudyTaskStatus,
  postponeStudyTask,
  getStudyGoalDetail
} from '@/api/studyGoal.js'
import {
  buildStudyGoalPayload,
  formatPlanDate,
  isPlanTaskToday,
  normalizePlanTask,
  schedulePlanTasks,
  statusText,
  todayDate
} from '@/utils/studyPlan.js'

const phase = ref('input') // input -> preview -> saved
const planText = ref('')
const chosenFile = ref(null)
const decomposing = ref(false)
const saving = ref(false)
const previewGoal = ref(null)
const previewTasks = ref([])
const goalDetail = ref(null)
const activeFilter = ref('all')
const togglingIds = ref([])
const statusOptions = ['未开始', '进行中', '受阻', '已跳过', '已完成']

const canDecompose = computed(() => Boolean(planText.value.trim()) || Boolean(chosenFile.value))

/** 从「我的计划」带 goalId 进入：直接载入该目标详情回到可勾选的执行清单 */
onLoad((options) => {
  const goalId = options && options.goalId
  if (goalId) {
    loadGoalDetail(goalId)
  }
})

function loadGoalDetail(goalId) {
  getStudyGoalDetail(goalId, 'all')
    .then((response) => {
      applyGoalDetail(response?.data)
      activeFilter.value = 'all'
      phase.value = 'saved'
    })
    .catch(() => {})
}

function goMyPlans() {
  uni.navigateTo({ url: '/subpackage_ai/goalPlanList/goalPlanList' })
}

const fileSuffixLabel = computed(() => {
  const name = chosenFile.value?.name || ''
  return name.toLowerCase().endsWith('.csv') ? 'CSV' : 'XLSX'
})

const fileSizeText = computed(() => {
  const size = chosenFile.value?.size
  if (!size) return ''
  return size > 1024 * 1024 ? `${(size / (1024 * 1024)).toFixed(1)}MB` : `${Math.max(1, Math.round(size / 1024))}KB`
})

const totalEstimatedDays = computed(
  () => previewTasks.value.reduce((sum, task) => sum + (Number(task.estimatedDays) || 0), 0)
)

const filteredTasks = computed(() => {
  if (!goalDetail.value) return []
  if (activeFilter.value === 'today') return goalDetail.value.tasks.filter((task) => isPlanTaskToday(task))
  if (activeFilter.value === 'pending') return goalDetail.value.tasks.filter((task) => !task.isCompleted)
  if (activeFilter.value === 'completed') return goalDetail.value.tasks.filter((task) => task.isCompleted)
  return goalDetail.value.tasks
})

const completedCount = computed(() =>
  goalDetail.value ? goalDetail.value.tasks.filter((task) => task.isCompleted).length : 0
)

const remainingCount = computed(() =>
  goalDetail.value ? goalDetail.value.tasks.filter((task) => !task.isCompleted).length : 0
)

const todayCount = computed(() =>
  goalDetail.value ? goalDetail.value.tasks.filter((task) => isPlanTaskToday(task)).length : 0
)

const progressPercent = computed(() => Math.max(0, Math.min(100, Number(goalDetail.value?.goal.progress) || 0)))

const statusLabel = computed(() => {
  return statusText(goalDetail.value?.goal.status)
})

const emptyText = computed(() =>
  activeFilter.value === 'completed' ? '还没有完成的任务' : activeFilter.value === 'pending' ? '没有剩余任务了' : activeFilter.value === 'today' ? '今天没有排期任务' : '暂无任务'
)

const emptySubText = computed(() =>
  activeFilter.value === 'pending' ? '恭喜，该目标下的任务已全部完成' : activeFilter.value === 'today' ? '可以查看全部任务或调整排期' : '换个筛选条件看看'
)

function onPlanTextInput(event) {
  if (event?.detail?.value?.trim()) chosenFile.value = null
}

function chooseFile() {
  const choose = uni.chooseMessageFile || uni.chooseFile
  choose({
    count: 1,
    type: 'file',
    extension: ['xlsx', 'csv'],
    success: (res) => {
      const file = res.tempFiles && res.tempFiles[0]
      if (!file) return
      const name = file.name || ''
      if (!name.toLowerCase().match(/\.(xlsx|csv)$/)) {
        uni.showToast({ title: '仅支持 .xlsx 或 .csv 文件', icon: 'none' })
        return
      }
      chosenFile.value = { path: file.path, name, size: file.size }
      planText.value = ''
    }
  })
}

function removeFile() {
  chosenFile.value = null
}

function startDecompose() {
  if (decomposing.value || !canDecompose.value) return
  decomposing.value = true
  const requestTask = chosenFile.value
    ? decomposeStudyFile(chosenFile.value.path)
    : decomposeStudyText(planText.value.trim())

  Promise.resolve(requestTask)
    .then((response) => {
      const data = response?.data
      if (!data || !data.goal || !Array.isArray(data.tasks) || data.tasks.length === 0) {
        throw new Error('智能体没有返回有效任务')
      }
      previewGoal.value = {
        title: data.goal.title || '',
        description: data.goal.description || '',
        dailyStudyMinutes: 60
      }
      previewGoal.value.startDate = todayDate()
      previewGoal.value.targetDate = ''
      previewTasks.value = schedulePlanTasks(data.tasks.map((task, index) => normalizePlanTask(task, index)), todayDate())
      phase.value = 'preview'
    })
    .catch(() => {})
    .finally(() => {
      decomposing.value = false
    })
}

function backToInput() {
  if (phase.value === 'saved') {
    planText.value = ''
    chosenFile.value = null
    previewGoal.value = null
    previewTasks.value = []
    goalDetail.value = null
  }
  phase.value = 'input'
}

function confirmSave() {
  if (saving.value) return
  if (!previewGoal.value?.title?.trim() || !previewTasks.value.length || previewTasks.value.some((task) => !task.taskName.trim())) {
    uni.showToast({ title: '请补全目标标题和任务名称', icon: 'none' })
    return
  }
  const scheduledTasks = schedulePlanTasks(previewTasks.value, previewGoal.value.startDate)
  const lastEnd = scheduledTasks[scheduledTasks.length - 1].plannedEndDate
  if (previewGoal.value.targetDate && lastEnd > previewGoal.value.targetDate) {
    uni.showToast({ title: '预计排期超过目标日期，请调整任务天数', icon: 'none' })
    return
  }
  previewTasks.value = scheduledTasks
  saving.value = true
  saveStudyGoal(buildStudyGoalPayload(previewGoal.value, scheduledTasks))
    .then((response) => {
      applyGoalDetail(response?.data)
      activeFilter.value = 'all'
      phase.value = 'saved'
      uni.showToast({ title: '任务清单已创建', icon: 'success' })
    })
    .catch(() => {})
    .finally(() => {
      saving.value = false
    })
}

function toggleTask(task) {
  if (!task.id || togglingIds.value.includes(task.id)) return
  const nextValue = !task.isCompleted
  const previousProgress = task.progressPercent
  // 乐观更新：勾选立即生效，失败回滚
  task.isCompleted = nextValue
  task.progressPercent = nextValue ? 100 : 0
  task.status = nextValue ? 'completed' : 'pending'
  togglingIds.value.push(task.id)

  updateStudyTaskCompletion(task.id, nextValue)
    .then((response) => {
      if (response?.data && goalDetail.value) {
        goalDetail.value.goal.progress = response.data.progress
        goalDetail.value.goal.status = response.data.status
      }
    })
    .catch(() => {
      task.isCompleted = !nextValue
      task.progressPercent = previousProgress
      task.status = nextValue ? 'pending' : 'completed'
    })
    .finally(() => {
      togglingIds.value = togglingIds.value.filter((id) => id !== task.id)
    })
}

function previewTaskProgress(task, event) {
  task.progressPercent = Number(event?.detail?.value ?? event?.target?.value ?? task.progressPercent)
}

function changeTaskProgress(task, event) {
  if (!task.id || togglingIds.value.includes(task.id)) return
  const previous = Number(task.progressPercent) || 0
  const next = Math.max(0, Math.min(100, Number(event?.detail?.value ?? previous)))
  task.progressPercent = next
  task.isCompleted = next >= 100
  task.status = next >= 100 ? 'completed' : next > 0 ? 'in_progress' : 'pending'
  togglingIds.value.push(task.id)
  updateStudyTaskProgress(task.id, next)
    .then((response) => applyGoalProgress(response?.data))
    .catch(() => {
      task.progressPercent = previous
      task.isCompleted = previous >= 100
      task.status = previous >= 100 ? 'completed' : previous > 0 ? 'in_progress' : 'pending'
    })
    .finally(() => {
      togglingIds.value = togglingIds.value.filter((id) => id !== task.id)
    })
}

function statusIndex(status) {
  const index = ['pending', 'in_progress', 'blocked', 'skipped', 'completed'].indexOf(status)
  return index < 0 ? 0 : index
}

function changeTaskStatus(task, event) {
  if (!task.id || togglingIds.value.includes(task.id)) return
  const statuses = ['pending', 'in_progress', 'blocked', 'skipped', 'completed']
  const nextStatus = statuses[Number(event?.detail?.value) || 0]
  const previous = { status: task.status, progressPercent: task.progressPercent, isCompleted: task.isCompleted }
  task.status = nextStatus
  if (nextStatus === 'completed') {
    task.progressPercent = 100
    task.isCompleted = true
  } else {
    task.isCompleted = false
    if (nextStatus === 'pending' && task.progressPercent >= 100) task.progressPercent = 0
  }
  togglingIds.value.push(task.id)
  updateStudyTaskStatus(task.id, nextStatus)
    .then((response) => applyGoalProgress(response?.data))
    .catch(() => Object.assign(task, previous))
    .finally(() => {
      togglingIds.value = togglingIds.value.filter((id) => id !== task.id)
    })
}

function postponeTask(task) {
  if (!task.id || togglingIds.value.includes(task.id) || task.isCompleted) return
  togglingIds.value.push(task.id)
  postponeStudyTask(task.id, 1)
    .then((response) => applyGoalDetail(response?.data))
    .catch(() => {})
    .finally(() => {
      togglingIds.value = togglingIds.value.filter((id) => id !== task.id)
    })
}

function applyGoalProgress(goal) {
  if (!goalDetail.value || !goal) return
  goalDetail.value.goal.progress = goal.progress
  goalDetail.value.goal.status = goal.status
}

function changePreviewDate(field, event) {
  const value = event?.detail?.value
  if (!value || !previewGoal.value) return
  previewGoal.value[field] = value
  if (field === 'startDate') {
    previewTasks.value = schedulePlanTasks(previewTasks.value, value)
  }
}

function reschedulePreview() {
  if (previewGoal.value) previewTasks.value = schedulePlanTasks(previewTasks.value, previewGoal.value.startDate)
}

function addPreviewTask() {
  previewTasks.value.push({
    orderNum: previewTasks.value.length + 1,
    taskName: '',
    stage: '',
    estimatedDays: 1,
    priority: '中',
    description: '',
    progressPercent: 0,
    isCompleted: false
  })
  reschedulePreview()
}

function removePreviewTask(index) {
  if (previewTasks.value.length <= 1) {
    uni.showToast({ title: '至少保留一个任务', icon: 'none' })
    return
  }
  previewTasks.value.splice(index, 1)
  reschedulePreview()
}

function movePreviewTask(index, offset) {
  const nextIndex = index + offset
  if (nextIndex < 0 || nextIndex >= previewTasks.value.length) return
  const tasks = [...previewTasks.value]
  const [current] = tasks.splice(index, 1)
  tasks.splice(nextIndex, 0, current)
  previewTasks.value = schedulePlanTasks(tasks, previewGoal.value?.startDate)
}

function cyclePriority(task) {
  const priorities = ['高', '中', '低']
  task.priority = priorities[(priorities.indexOf(task.priority) + 1) % priorities.length]
}

function applyGoalDetail(data) {
  goalDetail.value = {
    goal: {
      id: data?.goal?.id,
      title: data?.goal?.title || '',
      description: data?.goal?.description || '',
      progress: Number(data?.goal?.progress) || 0,
      status: data?.goal?.status || 'pending',
      startDate: data?.goal?.startDate || '',
      targetDate: data?.goal?.targetDate || '',
      dailyStudyMinutes: Number(data?.goal?.dailyStudyMinutes) || 60
    },
    tasks: Array.isArray(data?.tasks)
      ? data.tasks.map((task, index) => ({
          id: task.id,
          ...normalizePlanTask(task, index)
        }))
      : []
  }
}

function priorityLevel(priority) {
  if (priority === '高') return 'high'
  if (priority === '低') return 'low'
  return 'mid'
}
</script>

<style scoped>
.page {
  height: 100vh;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #F6F7FB;
  color: #172033;
}

.content {
  flex: 1;
  min-height: 0;
  box-sizing: border-box;
}

.content-inner {
  width: 100%;
  box-sizing: border-box;
  padding: 24rpx 30rpx calc(120rpx + env(safe-area-inset-bottom));
}

.section-card {
  border: 1rpx solid rgba(229, 226, 240, 0.82);
  border-radius: 22rpx;
  background: #FFFFFF;
  box-shadow: 0 12rpx 34rpx rgba(31, 35, 68, 0.045);
  box-sizing: border-box;
}

.section-card + .section-card {
  margin-top: 22rpx;
}

.input-card {
  padding: 26rpx 30rpx 28rpx;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20rpx;
}

.head-right {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.my-plans-link {
  font-size: 24rpx;
  color: #3D5789;
  font-weight: 600;
  padding: 6rpx 14rpx;
  border-radius: 999rpx;
  background: #EDF2FA;
}

.card-title {
  font-size: 29rpx;
  font-weight: 600;
  color: #182033;
}

.char-count {
  font-size: 22rpx;
  color: #A7ADBB;
}

.plan-input {
  width: 100%;
  height: 220rpx;
  padding: 22rpx 24rpx;
  border: 1rpx solid #DADDE8;
  border-radius: 14rpx;
  background: #FFFFFF;
  color: #2C364A;
  font-size: 25rpx;
  line-height: 1.55;
  box-sizing: border-box;
}

.plan-placeholder {
  color: #A7ADBB;
}

.upload-row {
  margin-top: 20rpx;
  padding: 24rpx;
  border: 1rpx dashed #C6CCDC;
  border-radius: 14rpx;
  background: #FAFBFE;
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.upload-icon-wrap {
  width: 64rpx;
  height: 64rpx;
  border-radius: 16rpx;
  background: rgba(92, 122, 153, 0.12);
  position: relative;
}

.upload-icon-arrow {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 3rpx;
  height: 26rpx;
  background: #5C7A99;
  transform: translate(-50%, -50%);
}

.upload-icon-arrow::before {
  content: '';
  position: absolute;
  left: -6rpx;
  top: 2rpx;
  width: 15rpx;
  height: 15rpx;
  border-top: 3rpx solid #5C7A99;
  border-left: 3rpx solid #5C7A99;
  transform: rotate(45deg);
}

.upload-info {
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.upload-main {
  font-size: 26rpx;
  font-weight: 500;
  color: #233047;
}

.upload-sub {
  font-size: 22rpx;
  color: #8B93A6;
}

.chosen-file {
  margin-top: 18rpx;
}

.file-chip {
  display: flex;
  align-items: center;
  gap: 18rpx;
  padding: 16rpx 20rpx;
  border-radius: 14rpx;
  background: #F4F7FB;
}

.file-chip__badge {
  min-width: 74rpx;
  text-align: center;
  padding: 8rpx 0;
  border-radius: 8rpx;
  background: #EAF0F9;
  color: #3D5773;
  font-size: 21rpx;
  font-weight: 600;
}

.file-chip__info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
  overflow: hidden;
}

.file-chip__name {
  font-size: 25rpx;
  color: #233047;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.file-chip__size {
  font-size: 21rpx;
  color: #8B93A6;
}

.file-chip__remove {
  padding: 10rpx 18rpx;
  border-radius: 999rpx;
  background: rgba(92, 122, 153, 0.1);
  color: #5C7A99;
  font-size: 22rpx;
}

.chosen-file__tip {
  display: block;
  margin-top: 12rpx;
  font-size: 21rpx;
  color: #98A0B0;
}

.primary-btn {
  margin-top: 28rpx;
  height: 88rpx;
  border-radius: 16rpx;
  background: #3D5789;
  color: #FFFFFF;
  font-size: 29rpx;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: opacity 0.18s ease;
}

.primary-btn--inline {
  flex: 1;
  margin-top: 0;
}

.primary-btn--disabled {
  opacity: 0.45;
}

/* 预览 */
.goal-card {
  padding: 26rpx 30rpx;
}

.goal-head {
  display: flex;
  align-items: center;
  gap: 14rpx;
}

.goal-badge {
  flex-shrink: 0;
  padding: 6rpx 16rpx;
  border-radius: 8rpx;
  font-size: 21rpx;
  font-weight: 600;
  background: #FFF3DE;
  color: #B97D24;
}

.goal-badge--done {
  background: #E8F3EC;
  color: #3D7A52;
}

.goal-badge--in_progress,
.goal-badge--completed {
  background: #E8F3EC;
  color: #3D7A52;
}

.goal-badge--pending {
  background: #EEF1F6;
  color: #64748B;
}

.goal-title {
  font-size: 31rpx;
  font-weight: 600;
  color: #172033;
  flex: 1;
}

.goal-title-input {
  flex: 1;
  min-width: 0;
  height: 54rpx;
  padding: 0 14rpx;
  border: 1rpx solid #DADDE8;
  border-radius: 10rpx;
  color: #172033;
  font-size: 29rpx;
  font-weight: 600;
}

.goal-desc-input {
  width: 100%;
  height: 86rpx;
  margin-top: 16rpx;
  padding: 14rpx 16rpx;
  border: 1rpx solid #E2E5ED;
  border-radius: 10rpx;
  box-sizing: border-box;
  color: #5C667A;
  font-size: 23rpx;
}

.date-settings {
  display: flex;
  gap: 16rpx;
  margin-top: 18rpx;
}

.date-setting {
  flex: 1;
  min-width: 0;
  padding: 14rpx 16rpx;
  border-radius: 12rpx;
  background: #F6F8FC;
}

.capacity-setting {
  flex: 1.15;
}

.capacity-line {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.capacity-input {
  width: 86rpx;
  height: 40rpx;
  padding: 0 8rpx;
  border: 1rpx solid #DADDE8;
  border-radius: 8rpx;
  color: #3D5773;
  font-size: 23rpx;
  text-align: right;
}

.date-label {
  display: block;
  margin-bottom: 6rpx;
  color: #8B93A6;
  font-size: 21rpx;
}

.date-value {
  color: #3D5773;
  font-size: 23rpx;
}

.goal-desc {
  display: block;
  margin-top: 12rpx;
  font-size: 24rpx;
  color: #5C667A;
  line-height: 1.55;
}

.goal-meta-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-top: 16rpx;
}

.goal-meta {
  font-size: 23rpx;
  color: #8B93A6;
}

.goal-meta-split {
  color: #C9CFDB;
}

/* 任务表 */
.task-table {
  padding: 8rpx 30rpx;
}

.table-header {
  border-bottom: 1rpx solid #EDF0F6;
}

.table-header .col-name,
.table-header .col-stage,
.table-header .col-days,
.table-header .col-priority,
.table-header .col-check {
  color: #98A0B0;
  font-weight: 500;
}

.task-row {
  display: flex;
  align-items: center;
  gap: 14rpx;
  padding: 22rpx 0;
}

.task-row--editable {
  flex-wrap: wrap;
  align-items: flex-start;
}

.task-edit-input,
.stage-edit-input,
.days-edit-input {
  width: 100%;
  height: 48rpx;
  padding: 0 10rpx;
  border: 1rpx solid #E0E4EC;
  border-radius: 8rpx;
  box-sizing: border-box;
  color: #233047;
  font-size: 23rpx;
}

.task-edit-desc {
  width: 100%;
  color: #8B93A6;
  font-size: 20rpx;
}

.stage-edit-input {
  width: 120rpx;
}

.days-edit-input {
  width: 70rpx;
}

.row-actions {
  width: calc(100% - 62rpx);
  margin-left: 62rpx;
  display: flex;
  gap: 22rpx;
  color: #5C7A99;
  font-size: 21rpx;
}

.row-action--danger {
  color: #B04A44;
}

.add-task-row {
  padding: 22rpx 0 14rpx;
  border-top: 1rpx dashed #E4E7EF;
  color: #5C7A99;
  text-align: center;
  font-size: 23rpx;
}

.col-check {
  width: 48rpx;
  flex-shrink: 0;
  font-size: 23rpx;
  color: #8B93A6;
}

.col-name {
  flex: 1.5;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.col-stage {
  width: 120rpx;
  flex-shrink: 0;
  font-size: 23rpx;
  color: #3D5773;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.col-days {
  width: 76rpx;
  flex-shrink: 0;
  font-size: 23rpx;
  color: #3D5773;
}

.col-priority {
  width: 88rpx;
  flex-shrink: 0;
  display: flex;
  justify-content: center;
}

.task-name {
  font-size: 26rpx;
  color: #233047;
  line-height: 1.4;
}

.task-desc {
  font-size: 21rpx;
  color: #98A0B0;
  line-height: 1.4;
}

.priority-tag {
  padding: 6rpx 14rpx;
  border-radius: 999rpx;
  font-size: 21rpx;
}

.priority-tag--high {
  background: #FBEAE9;
  color: #B04A44;
}

.priority-tag--mid {
  background: #FCF3E4;
  color: #A87A2A;
}

.priority-tag--low {
  background: #EBF1F6;
  color: #55708C;
}

.priority-tag--small {
  padding: 2rpx 12rpx;
  font-size: 19rpx;
}

.action-bar {
  margin-top: 26rpx;
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.ghost-btn {
  height: 88rpx;
  padding: 0 40rpx;
  border-radius: 16rpx;
  border: 1rpx solid #CBD3E0;
  background: #FFFFFF;
  color: #47536A;
  font-size: 27rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.ghost-btn--wide {
  flex: 1;
}

/* 执行清单 */
.progress-line {
  display: flex;
  align-items: center;
  gap: 18rpx;
  margin-top: 24rpx;
}

.progress-track {
  flex: 1;
  height: 14rpx;
  border-radius: 999rpx;
  background: #EEF1F7;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  border-radius: 999rpx;
  background: linear-gradient(90deg, #5E86C7, #3D5789);
  transition: width 0.35s ease;
}

.progress-num {
  width: 84rpx;
  text-align: right;
  font-size: 26rpx;
  font-weight: 600;
  color: #3D5789;
}

.goal-stats {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-top: 18rpx;
}

.goal-stat {
  font-size: 23rpx;
  color: #3D7A52;
}

.goal-stat--remain {
  color: #A87A2A;
}

.goal-status-text {
  margin-left: auto;
  font-size: 22rpx;
  color: #98A0B0;
}

.filter-card {
  padding: 14rpx 18rpx;
  display: flex;
  gap: 12rpx;
}

.filter-tab {
  flex: 1;
  height: 62rpx;
  border-radius: 12rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 23rpx;
  color: #5C667A;
  background: transparent;
  transition: background-color 0.18s ease;
}

.filter-tab--active {
  background: #EDF2FA;
  color: #2F4468;
  font-weight: 600;
}

.task-item {
  display: flex;
  align-items: flex-start;
  gap: 20rpx;
  padding: 24rpx 0;
  border-bottom: 1rpx solid #F1F3F8;
}

.task-item:last-child {
  border-bottom: none;
}

.checkbox {
  margin-top: 4rpx;
  width: 38rpx;
  height: 38rpx;
  border-radius: 10rpx;
  border: 2rpx solid #C4CBDA;
  background: #FFFFFF;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.checkbox--checked {
  border-color: #3D5789;
  background: #3D5789;
}

.checkbox-mark {
  width: 14rpx;
  height: 8rpx;
  border-left: 3rpx solid #FFFFFF;
  border-bottom: 3rpx solid #FFFFFF;
  transform: rotate(-45deg) translateY(-2rpx);
}

.task-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 10rpx;
}

.task-name--done {
  color: #9AA3B4;
  text-decoration: line-through;
}

.task-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 14rpx;
}

.meta-text {
  font-size: 21rpx;
  color: #8B93A6;
}

.schedule-summary {
  display: flex;
  gap: 24rpx;
  margin-top: 18rpx;
  color: #8B93A6;
  font-size: 21rpx;
}

.task-progress-slider {
  margin: 2rpx 0 0;
  padding: 0;
}

.task-controls {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #8B93A6;
  font-size: 21rpx;
}

.status-picker,
.postpone-link {
  padding: 6rpx 12rpx;
  border-radius: 999rpx;
  background: #F2F5F9;
}

.postpone-link {
  color: #5C7A99;
}

.empty-state {
  padding: 70rpx 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
}

.empty-main {
  font-size: 26rpx;
  color: #5C667A;
}

.empty-sub {
  font-size: 22rpx;
  color: #98A0B0;
}
</style>
