<template>
  <view class="page study-plan-page">
    <nav-bar
      title="学习计划拆解"
      :showBack="true"
      :border="false"
    />

    <scroll-view class="content study-plan-content" scroll-y :show-scrollbar="false">
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
          <view class="preview-workflow-stepper">
            <view class="workflow-step workflow-step--done">
              <view class="workflow-step-marker"><text>✓</text></view>
              <text class="workflow-step-label">输入资料</text>
            </view>
            <view class="workflow-step-line workflow-step-line--done"></view>
            <view class="workflow-step workflow-step--done">
              <view class="workflow-step-marker"><text>✓</text></view>
              <text class="workflow-step-label">AI拆解</text>
            </view>
            <view class="workflow-step-line"></view>
            <view class="workflow-step workflow-step--active">
              <view class="workflow-step-marker"><text>3</text></view>
              <text class="workflow-step-label">预览调整</text>
            </view>
          </view>

          <view class="section-card goal-card preview-goal-card">
            <view class="goal-head preview-goal-title-row">
              <input class="goal-title-input" v-model="previewGoal.title" maxlength="120" />
              <image class="preview-goal-edit" src="@/static/icons/line/edit-3.svg" mode="aspectFit" />
            </view>
            <textarea class="goal-desc-input" v-model="previewGoal.description" maxlength="500" placeholder="补充目标说明（可选）" />
            <view class="date-settings">
              <view class="date-setting">
                <view class="date-setting-head">
                  <image class="date-setting-icon" src="@/static/icons/line/calendar.svg" mode="aspectFit" />
                  <text class="date-label">开始日期</text>
                </view>
                <picker mode="date" :value="previewGoal.startDate" @change="changePreviewDate('startDate', $event)">
                  <text class="date-value">{{ formatPlanDate(previewGoal.startDate) }}</text>
                </picker>
              </view>
              <view class="date-setting">
                <view class="date-setting-head">
                  <image class="date-setting-icon" src="@/static/icons/line/calendar.svg" mode="aspectFit" />
                  <text class="date-label">目标日期</text>
                </view>
                <picker mode="date" :value="previewGoal.targetDate" @change="changePreviewDate('targetDate', $event)">
                  <text class="date-value">{{ previewGoal.targetDate ? formatPlanDate(previewGoal.targetDate) : '不设置' }}</text>
                </picker>
              </view>
              <view class="date-setting capacity-setting">
                <view class="date-setting-head">
                  <image class="date-setting-icon" src="@/static/icons/line/clock.svg" mode="aspectFit" />
                  <text class="date-label">每日学习时长</text>
                </view>
                <view class="capacity-line">
                  <input class="capacity-input" type="number" v-model="previewGoal.dailyStudyMinutes" />
                  <text class="date-value">分钟</text>
                </view>
              </view>
            </view>
            <view class="goal-meta-row preview-summary">
              <text class="goal-meta">{{ previewTasks.length }} 个阶段</text>
              <text class="goal-meta-split">·</text>
              <text class="goal-meta">{{ totalSubtaskCount }} 个执行步骤</text>
              <text class="goal-meta-split">·</text>
              <text class="goal-meta">预计 {{ totalEstimatedDays }} 天</text>
            </view>
          </view>

          <view class="section-card task-table preview-task-list">
            <template v-for="(task, index) in previewTasks" :key="task.orderNum">
              <view class="preview-stage-group">
                <view class="preview-stage">
                  <view class="preview-stage-head">
                    <view class="preview-stage-number">{{ task.orderNum }}</view>
                    <view class="preview-stage-title">
                      <text class="preview-stage-kicker">学习阶段</text>
                      <input class="task-edit-input" v-model="task.taskName" maxlength="120" placeholder="阶段名称" />
                    </view>
                    <view class="priority-tag" :class="`priority-tag--${priorityLevel(task.priority)}`" @tap="cyclePriority(task)">
                      <text>难度：{{ task.priority }}</text>
                    </view>
                  </view>
                  <textarea class="task-edit-desc preview-stage-desc" v-model="task.description" maxlength="500" placeholder="阶段说明（可选）" auto-height />
                  <view class="preview-stage-stats">
                    <view class="preview-stage-stat">
                      <image class="preview-stage-stat-icon" src="@/static/icons/line/clock.svg" mode="aspectFit" />
                      <view class="preview-stage-stat-copy">
                        <text class="preview-stage-stat-label">预计天数</text>
                        <view class="preview-days-input preview-stage-stat-value-line">
                          <input class="days-edit-input preview-stage-stat-number" type="number" v-model="task.estimatedDays" @input="reschedulePreview" />
                          <text class="preview-stage-stat-unit">天</text>
                        </view>
                      </view>
                    </view>
                    <view class="preview-stage-stat">
                      <image class="preview-stage-stat-icon" src="@/static/icons/line/clipboard.svg" mode="aspectFit" />
                      <view class="preview-stage-stat-copy">
                        <text class="preview-stage-stat-label">执行步骤</text>
                        <view class="preview-stage-stat-value-line">
                          <text class="preview-stage-stat-number">{{ task.subtasks.length }}</text>
                          <text class="preview-stage-stat-unit">个</text>
                        </view>
                      </view>
                    </view>
                  </view>
                  <view class="preview-stage-type-row">
                    <text class="preview-field-label">阶段类型</text>
                    <input class="stage-edit-input" v-model="task.stage" maxlength="60" placeholder="例如：基础阶段" />
                  </view>
                  <view class="row-actions">
                    <text @tap="movePreviewTask(index, -1)">上移</text>
                    <text @tap="movePreviewTask(index, 1)">下移</text>
                    <text class="row-action--danger" @tap="removePreviewTask(index)">删除阶段</text>
                  </view>
                </view>
                <view v-if="task.subtasks.length" class="subtask-editor">
                  <view class="subtask-editor__head preview-subtask-head">
                    <view class="subtask-editor__heading">
                      <text class="subtask-editor__title">执行步骤（{{ task.subtasks.length }}）</text>
                    </view>
                    <view class="subtask-collapse-trigger" @tap="togglePreviewSubtasks(task)">
                      <text>{{ isPreviewSubtasksExpanded(task) ? '收起步骤' : '展开步骤' }}</text>
                      <view
                        class="subtask-collapse-chevron"
                        :class="{ 'subtask-collapse-chevron--expanded': isPreviewSubtasksExpanded(task) }"
                      ></view>
                    </view>
                  </view>
                  <template v-if="isPreviewSubtasksExpanded(task)">
                  <view v-for="(subtask, subtaskIndex) in task.subtasks" :key="`${task.orderNum}-${subtask.orderNum}`" class="subtask-row">
                    <text class="subtask-order">{{ subtask.orderNum }}</text>
                    <view class="subtask-name-col">
                      <input class="task-edit-input" v-model="subtask.taskName" maxlength="120" placeholder="执行步骤名称" />
                      <textarea class="task-edit-desc subtask-desc-input" v-model="subtask.description" maxlength="500" placeholder="完成标准（可选）" auto-height />
                    </view>
                    <view class="subtask-days-input">
                      <input class="days-edit-input" type="number" v-model="subtask.estimatedDays" @input="reschedulePreview" />
                      <text>天</text>
                    </view>
                    <view class="subtask-actions">
                      <text @tap="movePreviewSubtask(task, subtaskIndex, -1)">上移</text>
                      <text @tap="movePreviewSubtask(task, subtaskIndex, 1)">下移</text>
                      <text class="row-action--danger" @tap="removePreviewSubtask(task, subtaskIndex)">删除</text>
                    </view>
                  </view>
                  <view class="add-subtask-row" @tap="addPreviewSubtask(task)"><text>＋ 添加执行步骤</text></view>
                  </template>
                </view>
              </view>
            </template>
            <view class="add-task-row" @tap="addPreviewTask"><text>＋ 添加学习阶段</text></view>
          </view>

          <view class="action-bar preview-action-bar">
            <view class="ghost-btn" @tap="backToInput"><text>返回修改资料</text></view>
            <view class="primary-btn primary-btn--inline" :class="{ 'primary-btn--disabled': saving }" @tap="confirmSave">
              <text>{{ saving ? '创建中...' : '确认并创建计划' }}</text>
            </view>
          </view>
        </template>

        <!-- 第三步：任务执行清单（可勾选） -->
        <template v-if="phase === 'saved' && goalDetail">
          <view class="section-card goal-card goal-summary-card">
            <view class="goal-head">
              <text class="goal-badge" :class="`goal-badge--${goalDetail.goal.status}`">{{ statusLabel }}</text>
              <text class="goal-title">{{ goalDetail.goal.title }}</text>
            </view>
            <text v-if="goalDetail.goal.description" class="goal-desc">{{ goalDetail.goal.description }}</text>
            <view class="progress-line progress-summary">
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
            <view v-if="hasLegacyTasks" class="legacy-plan-notice">
              <view class="legacy-plan-copy">
                <text class="legacy-plan-title">这份计划还没有细分执行步骤</text>
                <text class="legacy-plan-sub">AI 将保留现有阶段和进度，只补充可勾选的学习行动</text>
              </view>
              <view class="legacy-plan-action" @tap="expandLegacySubtasks">
                <text>{{ expandingSubtasks ? '补全中...' : 'AI 补全' }}</text>
              </view>
            </view>
          </view>

          <view class="section-card filter-card filter-card--compact">
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
              <text>全部 {{ totalWorkItemCount }}</text>
            </view>
            <view
              class="filter-tab"
              :class="{ 'filter-tab--active': activeFilter === 'pending' }"
              @tap="activeFilter = 'pending'"
            >
              <text>剩余 {{ remainingCount }}</text>
            </view>
            <view
              class="filter-tab"
              :class="{ 'filter-tab--active': activeFilter === 'completed' }"
              @tap="activeFilter = 'completed'"
            >
              <text>已完成 {{ completedCount }}</text>
            </view>
          </view>

          <view class="section-card task-table task-list-card">
            <view v-if="filteredTasks.length === 0" class="empty-state">
              <text class="empty-main">{{ emptyText }}</text>
              <text class="empty-sub">{{ emptySubText }}</text>
            </view>
            <view v-for="task in filteredTasks" :key="task.id" class="task-group task-group--rail">
              <template v-if="task.subtasks.length">
                <view class="task-group-head">
                  <view class="task-group-title-wrap">
                    <text class="task-group-kicker">学习阶段</text>
                    <text class="task-group-title">{{ task.taskName }}</text>
                    <text class="task-group-meta">{{ task.stage || '学习阶段' }} · {{ taskSubtaskMeta(task) }} · {{ task.progressPercent }}%</text>
                  </view>
                  <view class="task-group-actions">
                    <view class="task-group-toggle" @tap.stop="toggleTaskGroup(task)">
                      <text>{{ isTaskGroupExpanded(task) ? '收起' : '展开' }}</text>
                      <view
                        class="subtask-collapse-chevron"
                        :class="{ 'subtask-collapse-chevron--expanded': isTaskGroupExpanded(task) }"
                      ></view>
                    </view>
                    <text class="task-group-status">{{ statusText(task.status) }}</text>
                  </view>
                </view>
                <template v-if="isTaskGroupExpanded(task)">
                <view
                  class="task-item task-item--subtask"
                  v-for="subtask in visibleSubtasks(task)"
                  :key="`subtask-${subtask.id}`"
                  :class="{
                    'task-item--completed': subtask.isCompleted,
                    'task-item--today': isTodaySubtask(subtask)
                  }"
                  @tap="toggleSubtask(subtask)"
                >
                  <view class="checkbox" :class="{ 'checkbox--checked': subtask.isCompleted, 'checkbox--disabled': togglingIds.includes(subtaskBusyKey(subtask)) }">
                    <view v-if="subtask.isCompleted" class="checkbox-mark"></view>
                  </view>
                  <view class="task-body">
                    <view class="task-step-label">
                      <text>执行步骤 {{ subtask.orderNum }}</text>
                      <text v-if="isTodaySubtask(subtask)" class="task-today-label">今日</text>
                    </view>
                    <text class="task-name" :class="{ 'task-name--done': subtask.isCompleted }">{{ subtask.taskName }}</text>
                    <text v-if="subtask.description" class="task-desc">{{ subtask.description }}</text>
                    <view class="task-meta">
                      <text v-if="subtask.estimatedDays" class="meta-text">预计{{ subtask.estimatedDays }}天</text>
                      <text class="meta-text">{{ formatPlanDate(subtask.plannedStartDate) }}-{{ formatPlanDate(subtask.plannedEndDate) }}</text>
                    </view>
                    <view class="task-controls">
                      <picker :range="statusOptions" :value="statusIndex(subtask.status)" @change.stop="changeSubtaskStatus(subtask, $event)">
                        <text class="status-picker">状态：{{ statusText(subtask.status) }}</text>
                      </picker>
                      <text class="postpone-link" @tap.stop="postponeSubtask(subtask)">延期 1 天</text>
                    </view>
                  </view>
                </view>
                </template>
              </template>
              <view
                v-else
                class="task-item"
                :class="{ 'task-item--completed': task.isCompleted }"
                @tap="toggleTask(task)"
              >
                <view class="checkbox" :class="{ 'checkbox--checked': task.isCompleted, 'checkbox--disabled': togglingIds.includes(task.id) }">
                  <view v-if="task.isCompleted" class="checkbox-mark"></view>
                </view>
                <view class="task-body">
                  <text class="task-step-label">学习阶段</text>
                  <text class="task-name" :class="{ 'task-name--done': task.isCompleted }">{{ task.taskName }}</text>
                  <view class="task-meta">
                    <text v-if="task.stage" class="meta-text">{{ task.stage }}</text>
                    <text v-if="task.estimatedDays" class="meta-text">预计{{ task.estimatedDays }}天</text>
                    <view class="priority-tag priority-tag--small" :class="`priority-tag--${priorityLevel(task.priority)}`">
                      <text>{{ task.priority }}</text>
                    </view>
                    <text class="meta-text">{{ formatPlanDate(task.plannedStartDate) }}-{{ formatPlanDate(task.plannedEndDate) }}</text>
                  </view>
                  <view class="task-controls">
                    <picker :range="statusOptions" :value="statusIndex(task.status)" @change.stop="changeTaskStatus(task, $event)">
                      <text class="status-picker">状态：{{ statusText(task.status) }}</text>
                    </picker>
                    <text class="postpone-link" @tap.stop="postponeTask(task)">延期 1 天</text>
                  </view>
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
  updateStudyTaskStatus,
  postponeStudyTask,
  updateStudySubtaskCompletion,
  updateStudySubtaskStatus,
  postponeStudySubtask,
  getStudyGoalDetail,
  expandStudyGoalSubtasks
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
const expandingSubtasks = ref(false)
const SUBTASK_AUTO_EXPAND_LIMIT = 3
const previewSubtaskExpansion = ref({})
const executionSubtaskExpansion = ref({})

const canDecompose = computed(() => Boolean(planText.value.trim()) || Boolean(chosenFile.value))

/** 从「我的计划」带 goalId 进入：直接载入该目标详情回到可勾选的执行清单 */
onLoad((options) => {
  const goalId = options && options.goalId
  if (goalId) {
    loadGoalDetail(goalId)
  }
})

function loadGoalDetail(goalId) {
  clearSubtaskExpansion()
  getStudyGoalDetail(goalId, 'all')
    .then((response) => {
      applyGoalDetail(response?.data)
      executionSubtaskExpansion.value = {}
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

const totalSubtaskCount = computed(
  () => previewTasks.value.reduce((sum, task) => sum + (Array.isArray(task.subtasks) ? task.subtasks.length : 0), 0)
)

const filteredTasks = computed(() => {
  if (!goalDetail.value) return []
  return goalDetail.value.tasks.filter((task) => {
    const subtasks = Array.isArray(task.subtasks) ? task.subtasks : []
    if (!subtasks.length) {
      if (activeFilter.value === 'today') return isPlanTaskToday(task)
      if (activeFilter.value === 'pending') return !task.isCompleted
      if (activeFilter.value === 'completed') return task.isCompleted
      return true
    }
    if (activeFilter.value === 'today') return subtasks.some((subtask) => isPlanTaskToday(subtask))
    if (activeFilter.value === 'pending') return subtasks.some((subtask) => !subtask.isCompleted)
    if (activeFilter.value === 'completed') return subtasks.some((subtask) => subtask.isCompleted)
    return true
  })
})

const completedCount = computed(() =>
  goalDetail.value ? leafTasks(goalDetail.value.tasks).filter((task) => task.isCompleted).length : 0
)

const remainingCount = computed(() =>
  goalDetail.value ? leafTasks(goalDetail.value.tasks).filter((task) => !task.isCompleted).length : 0
)

const todayCount = computed(() =>
  goalDetail.value ? leafTasks(goalDetail.value.tasks).filter((task) => isPlanTaskToday(task)).length : 0
)

const totalWorkItemCount = computed(() =>
  goalDetail.value ? leafTasks(goalDetail.value.tasks).length : 0
)

const hasLegacyTasks = computed(() =>
  Boolean(goalDetail.value?.tasks?.some((task) => !Array.isArray(task.subtasks) || task.subtasks.length === 0))
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

function leafTasks(tasks) {
  return (Array.isArray(tasks) ? tasks : []).flatMap((task) => {
    const subtasks = Array.isArray(task.subtasks) ? task.subtasks : []
    return subtasks.length ? subtasks : [task]
  })
}

function visibleSubtasks(task) {
  const subtasks = Array.isArray(task?.subtasks) ? task.subtasks : []
  if (activeFilter.value === 'today') {
    return subtasks.filter((subtask) => isPlanTaskToday(subtask))
  }
  if (activeFilter.value === 'pending') return subtasks.filter((subtask) => !subtask.isCompleted)
  if (activeFilter.value === 'completed') return subtasks.filter((subtask) => subtask.isCompleted)
  return subtasks
}

function todaySubtaskCount(task) {
  const subtasks = Array.isArray(task?.subtasks) ? task.subtasks : []
  return subtasks.filter((subtask) => isPlanTaskToday(subtask)).length
}

function isTodaySubtask(subtask) {
  return activeFilter.value === 'today' && isPlanTaskToday(subtask)
}

function subtaskExpansionKey(scope, task) {
  const taskIdentity = task?.id || task?.orderNum || task?.taskName || 'unknown'
  const filter = scope === 'execution' ? activeFilter.value : 'preview'
  return [scope, filter, taskIdentity].join(':')
}

function defaultSubtasksExpanded(count) {
  return count > 0 && count <= SUBTASK_AUTO_EXPAND_LIMIT
}

function hasExpansionOverride(state, key) {
  return Object.prototype.hasOwnProperty.call(state, key)
}

function isPreviewSubtasksExpanded(task) {
  const key = subtaskExpansionKey('preview', task)
  const count = Array.isArray(task?.subtasks) ? task.subtasks.length : 0
  return hasExpansionOverride(previewSubtaskExpansion.value, key)
    ? Boolean(previewSubtaskExpansion.value[key])
    : defaultSubtasksExpanded(count)
}

function togglePreviewSubtasks(task) {
  const key = subtaskExpansionKey('preview', task)
  const nextValue = !isPreviewSubtasksExpanded(task)
  previewSubtaskExpansion.value = {
    ...previewSubtaskExpansion.value,
    [key]: nextValue
  }
}

function isTaskGroupExpanded(task) {
  const key = subtaskExpansionKey('execution', task)
  const count = activeFilter.value === 'today' ? todaySubtaskCount(task) : visibleSubtasks(task).length
  return hasExpansionOverride(executionSubtaskExpansion.value, key)
    ? Boolean(executionSubtaskExpansion.value[key])
    : defaultSubtasksExpanded(count)
}

function toggleTaskGroup(task) {
  const key = subtaskExpansionKey('execution', task)
  const nextValue = !isTaskGroupExpanded(task)
  executionSubtaskExpansion.value = {
    ...executionSubtaskExpansion.value,
    [key]: nextValue
  }
}

function taskSubtaskMeta(task) {
  const total = Array.isArray(task?.subtasks) ? task.subtasks.length : 0
  if (activeFilter.value === 'today') {
    return '今日 ' + todaySubtaskCount(task) + ' 项 · 阶段共 ' + total + ' 个执行步骤'
  }
  const visible = visibleSubtasks(task).length
  return activeFilter.value === 'all'
    ? total + ' 个执行步骤'
    : '当前显示 ' + visible + '/' + total + ' 个执行步骤'
}

function clearSubtaskExpansion() {
  previewSubtaskExpansion.value = {}
  executionSubtaskExpansion.value = {}
}

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
      clearSubtaskExpansion()
      phase.value = 'preview'
    })
    .catch(() => {})
    .finally(() => {
      decomposing.value = false
    })
}

function backToInput() {
  clearSubtaskExpansion()
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
  if (previewTasks.value.some((task) => task.subtasks.some((subtask) => !subtask.taskName.trim()))) {
    uni.showToast({ title: '请补全细分任务名称', icon: 'none' })
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

function subtaskBusyKey(subtask) {
  return `subtask:${subtask?.id || ''}`
}

function parentForSubtask(subtask) {
  return goalDetail.value?.tasks.find((task) => Array.isArray(task.subtasks)
    && task.subtasks.some((item) => item.id === subtask.id))
}

function recomputeParent(parent) {
  if (!parent || !parent.subtasks?.length) return
  const totalDays = parent.subtasks.reduce((sum, subtask) => sum + (Number(subtask.estimatedDays) || 1), 0)
  const weighted = parent.subtasks.reduce((sum, subtask) => sum + (Number(subtask.estimatedDays) || 1) * (Number(subtask.progressPercent) || 0), 0)
  parent.progressPercent = totalDays ? Math.round(weighted / totalDays) : 0
  parent.isCompleted = parent.subtasks.every((subtask) => subtask.isCompleted)
  parent.status = parent.isCompleted
    ? 'completed'
    : parent.subtasks.some((subtask) => subtask.status !== 'pending') || parent.progressPercent > 0
      ? 'in_progress'
      : 'pending'
}

function recomputeParents() {
  goalDetail.value?.tasks.forEach((task) => recomputeParent(task))
}

function toggleSubtask(subtask) {
  if (!subtask.id || togglingIds.value.includes(subtaskBusyKey(subtask))) return
  const nextValue = !subtask.isCompleted
  const previous = {
    isCompleted: subtask.isCompleted,
    progressPercent: subtask.progressPercent,
    status: subtask.status
  }
  subtask.isCompleted = nextValue
  subtask.progressPercent = nextValue ? 100 : 0
  subtask.status = nextValue ? 'completed' : 'pending'
  const busyKey = subtaskBusyKey(subtask)
  togglingIds.value.push(busyKey)
  updateStudySubtaskCompletion(subtask.id, nextValue)
    .then((response) => {
      applyGoalProgress(response?.data)
      recomputeParents()
    })
    .catch(() => Object.assign(subtask, previous))
    .finally(() => {
      togglingIds.value = togglingIds.value.filter((id) => id !== busyKey)
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

function changeSubtaskStatus(subtask, event) {
  if (!subtask.id || togglingIds.value.includes(subtaskBusyKey(subtask))) return
  const statuses = ['pending', 'in_progress', 'blocked', 'skipped', 'completed']
  const nextStatus = statuses[Number(event?.detail?.value) || 0]
  const previous = {
    status: subtask.status,
    progressPercent: subtask.progressPercent,
    isCompleted: subtask.isCompleted
  }
  subtask.status = nextStatus
  if (nextStatus === 'completed') {
    subtask.progressPercent = 100
    subtask.isCompleted = true
  } else {
    subtask.isCompleted = false
    if (nextStatus === 'pending' && subtask.progressPercent >= 100) subtask.progressPercent = 0
  }
  const busyKey = subtaskBusyKey(subtask)
  togglingIds.value.push(busyKey)
  updateStudySubtaskStatus(subtask.id, nextStatus)
    .then((response) => {
      applyGoalProgress(response?.data)
      recomputeParents()
    })
    .catch(() => Object.assign(subtask, previous))
    .finally(() => {
      togglingIds.value = togglingIds.value.filter((id) => id !== busyKey)
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

function postponeSubtask(subtask) {
  if (!subtask.id || togglingIds.value.includes(subtaskBusyKey(subtask)) || subtask.isCompleted) return
  const busyKey = subtaskBusyKey(subtask)
  togglingIds.value.push(busyKey)
  postponeStudySubtask(subtask.id, 1)
    .then((response) => applyGoalDetail(response?.data))
    .catch(() => {})
    .finally(() => {
      togglingIds.value = togglingIds.value.filter((id) => id !== busyKey)
    })
}

function expandLegacySubtasks() {
  if (!goalDetail.value?.goal?.id || expandingSubtasks.value || !hasLegacyTasks.value) return
  uni.showModal({
    title: '补全细分任务',
    content: 'AI 会根据当前计划补充可执行步骤，并重新计算阶段排期。已有细分任务和进度不会被覆盖。',
    confirmText: '开始补全',
    success: (result) => {
      if (!result.confirm) return
      expandingSubtasks.value = true
      expandStudyGoalSubtasks(goalDetail.value.goal.id)
        .then((response) => {
          applyGoalDetail(response?.data)
          uni.showToast({ title: '细分任务已补全', icon: 'success' })
        })
        .catch(() => {})
        .finally(() => {
          expandingSubtasks.value = false
        })
    }
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
    isCompleted: false,
    status: 'pending',
    subtasks: []
  })
  reschedulePreview()
}

function addPreviewSubtask(task) {
  task.subtasks.push({
    orderNum: task.subtasks.length + 1,
    taskName: '',
    description: '',
    estimatedDays: 1,
    progressPercent: 0,
    isCompleted: false,
    status: 'pending'
  })
  reschedulePreview()
}

function removePreviewSubtask(task, index) {
  task.subtasks.splice(index, 1)
  reschedulePreview()
}

function movePreviewSubtask(task, index, offset) {
  const nextIndex = index + offset
  if (nextIndex < 0 || nextIndex >= task.subtasks.length) return
  const subtasks = [...task.subtasks]
  const [current] = subtasks.splice(index, 1)
  subtasks.splice(nextIndex, 0, current)
  task.subtasks = subtasks
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

.subtask-editor {
  margin: 0 0 18rpx 62rpx;
  padding: 18rpx 18rpx 4rpx;
  border-left: 4rpx solid #DCE5F2;
  background: #F8FAFD;
  border-radius: 0 12rpx 12rpx 0;
}

.subtask-editor__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12rpx;
  margin-bottom: 10rpx;
}

.subtask-editor__title {
  min-width: 0;
  color: #3D5773;
  font-size: 22rpx;
  font-weight: 600;
}

.subtask-editor__hint {
  flex-shrink: 0;
  color: #98A0B0;
  font-size: 19rpx;
}

.subtask-row {
  display: flex;
  align-items: flex-start;
  gap: 12rpx;
  padding: 12rpx 0;
  border-top: 1rpx solid #E9EDF4;
  flex-wrap: wrap;
}

.subtask-order {
  width: 28rpx;
  padding-top: 12rpx;
  color: #8B93A6;
  font-size: 21rpx;
  text-align: center;
}

.subtask-name-col {
  flex: 1;
  min-width: 220rpx;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.subtask-actions {
  width: 100%;
  margin-left: 40rpx;
  display: flex;
  gap: 20rpx;
  color: #5C7A99;
  font-size: 20rpx;
}

.add-subtask-row {
  padding: 14rpx 0;
  color: #5C7A99;
  font-size: 21rpx;
  text-align: center;
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

.task-group + .task-group {
  border-top: 1rpx solid #EDF0F6;
}

.task-group-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18rpx;
  padding: 24rpx 0 12rpx;
}

.task-group-title-wrap {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.task-group-title {
  color: #233047;
  font-size: 27rpx;
  font-weight: 600;
}

.task-group-meta,
.task-group-status {
  color: #8B93A6;
  font-size: 21rpx;
}

.task-group-status {
  flex-shrink: 0;
  padding: 6rpx 12rpx;
  border-radius: 999rpx;
  background: #F2F5F9;
}

.task-item--subtask {
  padding-left: 20rpx;
  border-left: 4rpx solid #DCE5F2;
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

.legacy-plan-notice {
  display: flex;
  align-items: center;
  gap: 18rpx;
  margin-top: 20rpx;
  padding: 18rpx 20rpx;
  border: 1rpx solid #EAD9C7;
  border-radius: 14rpx;
  background: #FFF9F2;
}

.legacy-plan-copy {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 5rpx;
}

.legacy-plan-title {
  color: #8A5A25;
  font-size: 23rpx;
  font-weight: 600;
}

.legacy-plan-sub {
  color: #A0805A;
  font-size: 20rpx;
  line-height: 1.4;
}

.legacy-plan-action {
  flex-shrink: 0;
  padding: 12rpx 18rpx;
  border-radius: 999rpx;
  background: #A87A2A;
  color: #FFFFFF;
  font-size: 21rpx;
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
/* 学习计划页面视觉基线：减少卡片噪音，突出阶段和执行步骤 */
.study-plan-page {
  background: #F5F7FA;
}

.study-plan-page .content-inner {
  padding: 18rpx 28rpx calc(120rpx + env(safe-area-inset-bottom));
}

.study-plan-page .section-card {
  border-color: #E6EBF2;
  border-radius: 16rpx;
  box-shadow: none;
}

.study-plan-page .section-card + .section-card {
  margin-top: 16rpx;
}

.study-plan-page .goal-card {
  padding: 22rpx 24rpx;
}

.study-plan-page .goal-head {
  align-items: flex-start;
}

.study-plan-page .goal-title {
  line-height: 1.35;
}

.study-plan-page .goal-desc {
  margin-top: 10rpx;
  color: #657287;
}

.study-plan-page .date-settings {
  gap: 10rpx;
  margin-top: 14rpx;
}

.study-plan-page .date-setting {
  padding: 12rpx 14rpx;
  border-radius: 10rpx;
  background: #F6F8FB;
}

.study-plan-page .goal-meta-row {
  gap: 8rpx;
  margin-top: 14rpx;
  flex-wrap: wrap;
}

.study-plan-page .goal-meta {
  font-size: 21rpx;
}

.study-plan-page .preview-task-list,
.study-plan-page .task-list-card {
  padding: 0 24rpx;
}

.study-plan-page .preview-stage {
  padding: 22rpx 0 20rpx;
  border-bottom: 1rpx solid #EDF0F5;
}

.study-plan-page .preview-stage-head {
  display: flex;
  align-items: flex-start;
  gap: 12rpx;
}

.study-plan-page .preview-stage-number {
  width: 42rpx;
  height: 42rpx;
  flex-shrink: 0;
  border-radius: 12rpx;
  background: #EAF0F7;
  color: #55708C;
  font-size: 22rpx;
  font-weight: 600;
  line-height: 42rpx;
  text-align: center;
}

.study-plan-page .preview-stage-title {
  flex: 1;
  min-width: 0;
}

.study-plan-page .preview-stage-kicker,
.study-plan-page .task-group-kicker,
.study-plan-page .task-step-label {
  display: block;
  margin-bottom: 5rpx;
  color: #8B97A8;
  font-size: 19rpx;
  line-height: 1.3;
}

.study-plan-page .preview-stage-title .task-edit-input {
  height: 52rpx;
  padding: 0;
  border: 0;
  background: transparent;
  font-size: 27rpx;
  font-weight: 600;
}

.study-plan-page .preview-stage-head > .priority-tag {
  flex-shrink: 0;
  margin-top: 18rpx;
}

.study-plan-page .preview-stage-desc {
  display: block;
  width: calc(100% - 54rpx);
  height: 42rpx;
  margin: 10rpx 0 0 54rpx;
  padding: 0;
  border: 0;
  background: transparent;
}

.study-plan-page .preview-stage-fields {
  display: flex;
  gap: 10rpx;
  margin: 12rpx 0 0 54rpx;
}

.study-plan-page .preview-field {
  flex: 1;
  min-width: 0;
  padding: 10rpx 12rpx;
  border-radius: 10rpx;
  background: #F7F9FC;
}

.study-plan-page .preview-field--days {
  flex: 0 0 180rpx;
}

.study-plan-page .preview-field-label {
  display: block;
  margin-bottom: 5rpx;
  color: #8B97A8;
  font-size: 19rpx;
}

.study-plan-page .preview-field .stage-edit-input,
.study-plan-page .preview-field .days-edit-input {
  width: 100%;
  height: 40rpx;
  padding: 0;
  border: 0;
  background: transparent;
  font-size: 22rpx;
}

.study-plan-page .preview-days-input,
.study-plan-page .subtask-days-input {
  display: flex;
  align-items: center;
  gap: 6rpx;
  color: #64748B;
  font-size: 21rpx;
}

.study-plan-page .preview-days-input .days-edit-input,
.study-plan-page .subtask-days-input .days-edit-input {
  width: 60rpx;
}

.study-plan-page .preview-stage .row-actions {
  width: auto;
  margin: 14rpx 0 0 54rpx;
  justify-content: flex-start;
  gap: 26rpx;
}

.study-plan-page .subtask-editor {
  margin: 16rpx 0 0 54rpx;
  padding: 14rpx 16rpx 4rpx;
  border-left-width: 2rpx;
  border-left-color: #B8C9DC;
  border-radius: 0 10rpx 10rpx 0;
  background: #F7F9FC;
}

.study-plan-page .subtask-editor__head {
  margin-bottom: 8rpx;
}

.study-plan-page .subtask-row {
  flex-wrap: wrap;
  align-items: center;
  gap: 10rpx;
  padding: 12rpx 0;
}

.study-plan-page .subtask-name-col {
  flex: 1;
  min-width: 180rpx;
}

.study-plan-page .subtask-name-col .task-edit-input {
  height: 42rpx;
  padding: 0 8rpx;
  background: #FFFFFF;
}

.study-plan-page .subtask-name-col .task-edit-desc {
  padding-left: 8rpx;
}

.study-plan-page .subtask-days-input {
  flex-shrink: 0;
}

.study-plan-page .subtask-actions {
  margin-left: 40rpx;
  gap: 18rpx;
}

.study-plan-page .add-task-row,
.study-plan-page .add-subtask-row {
  color: #55708C;
}

.study-plan-page .goal-summary-card {
  padding-bottom: 20rpx;
}

.study-plan-page .progress-summary {
  margin-top: 20rpx;
}

.study-plan-page .goal-stats {
  margin-top: 14rpx;
}

.study-plan-page .schedule-summary {
  gap: 14rpx;
  margin-top: 14rpx;
  flex-wrap: wrap;
}

.study-plan-page .filter-card--compact {
  padding: 8rpx;
  gap: 4rpx;
}

.study-plan-page .filter-tab {
  height: 58rpx;
  border-radius: 10rpx;
  font-size: 21rpx;
}

.study-plan-page .task-group--rail {
  position: relative;
  padding-left: 14rpx;
}

.study-plan-page .task-group--rail::before {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  width: 2rpx;
  background: #DCE5F0;
  content: '';
}

.study-plan-page .task-group-head {
  align-items: flex-start;
  padding: 20rpx 0 10rpx;
}

.study-plan-page .task-group-title-wrap {
  gap: 3rpx;
}

.study-plan-page .task-group-title {
  font-size: 26rpx;
}

.study-plan-page .task-group-status {
  padding: 4rpx 10rpx;
  border-radius: 7rpx;
  background: #F3F6F9;
  font-size: 20rpx;
}

.study-plan-page .task-item,
.study-plan-page .task-item--subtask {
  padding: 18rpx 0 18rpx 14rpx;
  border-left: 0;
}

.study-plan-page .task-item--subtask {
  position: relative;
}

.study-plan-page .task-item--subtask::before {
  position: absolute;
  top: 35rpx;
  left: -1rpx;
  width: 10rpx;
  height: 2rpx;
  background: #B8C9DC;
  content: '';
}

.study-plan-page .task-step-label {
  margin-bottom: 0;
}

.study-plan-page .task-name {
  font-size: 25rpx;
}

.study-plan-page .task-body {
  gap: 7rpx;
}

.study-plan-page .task-controls {
  margin-top: 2rpx;
}

.study-plan-page .status-picker,
.study-plan-page .postpone-link {
  padding: 4rpx 0;
  background: transparent;
}

.study-plan-page .status-picker {
  color: #64748B;
}

.study-plan-page .postpone-link {
  color: #55708C;
}

.study-plan-page .legacy-plan-notice {
  margin-top: 16rpx;
  padding: 14rpx 16rpx;
  border-radius: 10rpx;
}

/* 预览编辑态：让长标题、说明和统计信息始终留在自己的容器内 */
.study-plan-page .preview-goal-card,
.study-plan-page .preview-task-list,
.study-plan-page .preview-stage,
.study-plan-page .preview-field,
.study-plan-page .subtask-editor {
  box-sizing: border-box;
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
}

.study-plan-page .preview-goal-title-row,
.study-plan-page .preview-goal-title-row .goal-title-input {
  min-width: 0;
  max-width: 100%;
}

.study-plan-page .preview-goal-title-row .goal-title-input {
  box-sizing: border-box;
  overflow: hidden;
}

.study-plan-page .preview-goal-card .goal-desc-input {
  display: block;
  box-sizing: border-box;
  min-width: 0;
  width: 100%;
  max-width: 100%;
  line-height: 1.5;
  white-space: normal;
  overflow-wrap: anywhere;
}

.study-plan-page .preview-goal-card .date-settings,
.study-plan-page .preview-goal-card .date-setting,
.study-plan-page .preview-goal-card .capacity-line {
  min-width: 0;
}

.study-plan-page .preview-goal-card .date-setting {
  overflow: hidden;
}

.study-plan-page .preview-goal-card .date-value,
.study-plan-page .preview-summary .goal-meta,
.study-plan-page .preview-summary .goal-meta-split {
  max-width: 100%;
  white-space: normal;
  overflow-wrap: anywhere;
}

.study-plan-page .preview-goal-card .capacity-input {
  box-sizing: border-box;
  max-width: 100%;
}

.study-plan-page .preview-summary {
  align-items: flex-start;
  row-gap: 6rpx;
}

.study-plan-page .preview-stage-head,
.study-plan-page .preview-stage-title {
  min-width: 0;
}

.study-plan-page .preview-stage-title .task-edit-input {
  box-sizing: border-box;
  width: 100%;
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
}

.study-plan-page .preview-stage-head > .priority-tag {
  max-width: 92rpx;
  overflow: hidden;
  white-space: nowrap;
}

.study-plan-page .preview-stage-desc {
  box-sizing: border-box;
  width: calc(100% - 54rpx);
  max-width: calc(100% - 54rpx);
  min-height: 42rpx;
  height: auto;
  line-height: 1.45;
  white-space: normal;
  overflow-wrap: anywhere;
}

.study-plan-page .preview-stage-fields {
  min-width: 0;
  max-width: calc(100% - 54rpx);
}

.study-plan-page .preview-field .stage-edit-input,
.study-plan-page .preview-field .days-edit-input {
  box-sizing: border-box;
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
}

.study-plan-page .preview-subtask-head {
  align-items: flex-start;
  flex-wrap: wrap;
}

.study-plan-page .preview-subtask-head .subtask-editor__title {
  flex: 1;
  min-width: 0;
  max-width: 100%;
  white-space: normal;
  overflow-wrap: anywhere;
}

.study-plan-page .preview-subtask-head .subtask-editor__hint {
  max-width: 100%;
  white-space: normal;
}

.study-plan-page .preview-task-list .subtask-row,
.study-plan-page .preview-task-list .subtask-name-col {
  min-width: 0;
}

.study-plan-page .preview-task-list .subtask-name-col {
  max-width: 100%;
}

.study-plan-page .preview-task-list .subtask-name-col .task-edit-input {
  box-sizing: border-box;
  width: 100%;
  min-width: 0;
  max-width: 100%;
}

.study-plan-page .preview-task-list .subtask-desc-input {
  display: block;
  box-sizing: border-box;
  width: 100%;
  max-width: 100%;
  min-height: 34rpx;
  height: auto;
  padding: 0 8rpx;
  line-height: 1.45;
  white-space: normal;
  overflow-wrap: anywhere;
}

.study-plan-page .preview-task-list .subtask-days-input {
  max-width: 92rpx;
}

.study-plan-page .preview-task-list .subtask-actions {
  display: flex;
  flex-wrap: wrap;
  width: 100%;
  max-width: 100%;
  margin-left: 40rpx;
  gap: 18rpx;
}

.study-plan-page .preview-task-list .row-actions {
  flex-wrap: wrap;
}

.study-plan-page .subtask-editor__heading {
  display: flex;
  align-items: baseline;
  gap: 8rpx;
  min-width: 0;
  flex: 1;
}

.study-plan-page .subtask-editor__count {
  flex-shrink: 0;
  color: #98A0B0;
  font-size: 19rpx;
}

.study-plan-page .subtask-collapse-trigger,
.study-plan-page .task-group-toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  gap: 6rpx;
  min-height: 44rpx;
  padding: 4rpx 10rpx;
  border-radius: 999rpx;
  background: #EDF2FA;
  color: #55708C;
  font-size: 19rpx;
}

.study-plan-page .subtask-collapse-chevron {
  width: 12rpx;
  height: 12rpx;
  flex-shrink: 0;
  box-sizing: border-box;
  border-right: 2rpx solid #7890A8;
  border-bottom: 2rpx solid #7890A8;
  transform: rotate(45deg);
  transform-origin: center;
}

.study-plan-page .subtask-collapse-chevron--expanded {
  transform: rotate(-135deg);
}

.study-plan-page .preview-subtask-head {
  gap: 10rpx;
}

.study-plan-page .task-group-actions {
  display: flex;
  flex-shrink: 0;
  flex-direction: column;
  align-items: flex-end;
  gap: 6rpx;
}

.study-plan-page .task-group-toggle {
  min-height: 38rpx;
  padding: 2rpx 9rpx;
  background: transparent;
  font-size: 19rpx;
}

.study-plan-page .task-step-label {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8rpx;
}

.study-plan-page .task-today-label {
  padding: 2rpx 8rpx;
  border-radius: 999rpx;
  background: #E8F3EC;
  color: #3D7A52;
  font-size: 18rpx;
  font-weight: 500;
}

.study-plan-page .task-item--today {
  background: #FBFDFB;
}

/* 预览调整态按“步骤—摘要—阶段—保存”重排，降低长页面的阅读压力 */
.study-plan-page .preview-workflow-stepper {
  display: flex;
  align-items: flex-start;
  padding: 8rpx 34rpx 20rpx;
}

.study-plan-page .workflow-step {
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
  color: #6C788A;
  font-size: 20rpx;
  white-space: nowrap;
}

.study-plan-page .workflow-step-marker {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44rpx;
  height: 44rpx;
  box-sizing: border-box;
  border: 2rpx solid #81909F;
  border-radius: 50%;
  color: #527677;
  font-size: 25rpx;
  font-weight: 600;
}

.study-plan-page .workflow-step--active {
  color: #3E7476;
  font-weight: 600;
}

.study-plan-page .workflow-step--active .workflow-step-marker {
  border-color: #5B7F80;
  background: #5B7F80;
  color: #FFFFFF;
}

.study-plan-page .workflow-step-line {
  flex: 1;
  height: 2rpx;
  margin: 21rpx 10rpx 0;
  background: #D5DCE3;
}

.study-plan-page .workflow-step-line--done {
  background: #829297;
}

.study-plan-page .preview-goal-card {
  padding: 26rpx 24rpx 0;
}

.study-plan-page .preview-goal-title-row {
  display: flex;
  align-items: center;
  gap: 14rpx;
}

.study-plan-page .preview-goal-title-row .goal-title-input {
  flex: 1;
  width: auto;
  height: 62rpx;
  padding: 0;
  border: 0;
  background: transparent;
  color: #172033;
  font-size: 30rpx;
  font-weight: 600;
}

.study-plan-page .preview-goal-edit {
  flex: 0 0 auto;
  width: 34rpx;
  height: 34rpx;
  opacity: 0.72;
}

.study-plan-page .preview-goal-card .goal-desc-input {
  min-height: 94rpx;
  margin-top: 14rpx;
  padding: 16rpx 18rpx;
  border: 1rpx solid #E3E8ED;
  border-radius: 12rpx;
  background: #FAFBFC;
  color: #435064;
  font-size: 23rpx;
  line-height: 1.55;
}

.study-plan-page .preview-goal-card .date-settings {
  gap: 10rpx;
  margin-top: 16rpx;
}

.study-plan-page .preview-goal-card .date-setting {
  padding: 14rpx 12rpx;
  border: 1rpx solid #E5EAEF;
  border-radius: 12rpx;
  background: #FFFFFF;
}

.study-plan-page .date-setting-head {
  display: flex;
  align-items: center;
  gap: 6rpx;
  min-width: 0;
}

.study-plan-page .date-setting-icon {
  flex: 0 0 auto;
  width: 24rpx;
  height: 24rpx;
  opacity: 0.72;
}

.study-plan-page .preview-goal-card .date-label {
  min-width: 0;
  margin-bottom: 6rpx;
  font-size: 19rpx;
}

.study-plan-page .preview-goal-card .date-value {
  display: block;
  overflow: hidden;
  color: #27364B;
  font-size: 22rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.study-plan-page .preview-goal-card .capacity-line {
  min-height: 32rpx;
}

.study-plan-page .preview-goal-card .capacity-input {
  width: 70rpx;
  height: 32rpx;
  padding: 0 6rpx;
  border-color: #D6DEE5;
  color: #27364B;
  font-size: 22rpx;
}

.study-plan-page .preview-summary {
  justify-content: center;
  margin: 20rpx -24rpx 0;
  padding: 18rpx 20rpx;
  border-top: 1rpx solid #EDF0F3;
}

.study-plan-page .preview-summary .goal-meta {
  color: #4E7A7B;
  font-size: 22rpx;
}

.study-plan-page .preview-summary .goal-meta-split {
  color: #8A969B;
}

.study-plan-page .preview-task-list {
  padding: 0;
  overflow: visible;
  background: transparent;
  border: 0;
}

.study-plan-page .preview-stage-group {
  margin-top: 16rpx;
  overflow: hidden;
  border: 1rpx solid #E4E9ED;
  border-radius: 16rpx;
  background: #FFFFFF;
}

.study-plan-page .preview-stage {
  padding: 24rpx 22rpx 18rpx;
  border-bottom: 0;
}

.study-plan-page .preview-stage-number {
  display: flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
}

.study-plan-page .preview-stage-title .task-edit-input {
  height: 48rpx;
  color: #172033;
  font-size: 27rpx;
}

.study-plan-page .preview-stage-head > .priority-tag {
  max-width: 126rpx;
  margin-top: 20rpx;
  padding: 7rpx 12rpx;
  border-radius: 8rpx;
  font-size: 19rpx;
  text-overflow: ellipsis;
}

.study-plan-page .preview-stage-desc {
  width: calc(100% - 54rpx);
  max-width: calc(100% - 54rpx);
  margin-top: 12rpx;
  color: #657287;
  font-size: 22rpx;
}

.study-plan-page .preview-stage-stats {
  display: flex;
  margin: 18rpx 0 0 54rpx;
  padding: 16rpx 0;
  border-top: 1rpx solid #EDF0F3;
  border-bottom: 1rpx solid #EDF0F3;
}

.study-plan-page .preview-stage-stat {
  display: flex;
  flex: 1;
  align-items: center;
  gap: 12rpx;
  min-width: 0;
}

.study-plan-page .preview-stage-stat + .preview-stage-stat {
  padding-left: 20rpx;
  border-left: 1rpx solid #E7EBEE;
}

.study-plan-page .preview-stage-stat-icon {
  flex: 0 0 auto;
  width: 30rpx;
  height: 30rpx;
  opacity: 0.72;
}

.study-plan-page .preview-stage-stat-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4rpx;
}

.study-plan-page .preview-stage-stat-label,
.study-plan-page .preview-stage-stat-value {
  color: #7C8998;
  font-size: 19rpx;
}

.study-plan-page .preview-stage-stat-value,
.study-plan-page .preview-days-input {
  color: #27364B;
  font-size: 23rpx;
}

.study-plan-page .preview-stage-stat-value-line {
  display: flex;
  align-items: baseline;
  min-width: 102rpx;
  gap: 10rpx;
  color: #27364B;
  font-size: 23rpx;
  line-height: 1.2;
}

.study-plan-page .preview-stage-stat-number {
  display: block;
  width: 56rpx;
  min-width: 56rpx;
  padding: 0;
  color: #27364B;
  font-size: 23rpx;
  line-height: 1.2;
  text-align: left;
}

.study-plan-page .preview-stage-stat-unit {
  display: block;
  width: 28rpx;
  min-width: 28rpx;
  color: #27364B;
  font-size: 21rpx;
  line-height: 1.2;
  text-align: left;
}

.study-plan-page .preview-stage-stat .preview-days-input {
  gap: 4rpx;
}

.study-plan-page .preview-stage-stat .days-edit-input {
  width: 56rpx;
  height: 34rpx;
  padding: 0;
  border: 0;
  color: #27364B;
  font-size: 23rpx;
}

.study-plan-page .preview-stage-type-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin: 14rpx 0 0 54rpx;
  color: #7C8998;
  font-size: 19rpx;
}

.study-plan-page .preview-stage-type-row .preview-field-label {
  flex: 0 0 auto;
  margin: 0;
}

.study-plan-page .preview-stage-type-row .stage-edit-input {
  flex: 1;
  width: auto;
  height: 38rpx;
  padding: 0 8rpx;
  border-color: #E0E6EB;
  background: #FAFBFC;
  font-size: 20rpx;
}

.study-plan-page .preview-stage .row-actions {
  margin: 14rpx 0 0 54rpx;
  color: #557779;
}

.study-plan-page .preview-stage .row-action--danger,
.study-plan-page .subtask-actions .row-action--danger {
  color: #B76760;
}

.study-plan-page .preview-stage-group .subtask-editor {
  margin: 0;
  padding: 0 16rpx 14rpx;
  border-top: 1rpx solid #E8EDEF;
  border-left: 0;
  border-radius: 0;
  background: #F8FAFB;
}

.study-plan-page .preview-stage-group .preview-subtask-head {
  margin: 0 -16rpx 8rpx;
  padding: 16rpx;
  border-bottom: 1rpx solid #E8EDEF;
}

.study-plan-page .preview-stage-group .subtask-editor__title {
  color: #263B3D;
  font-size: 23rpx;
}

.study-plan-page .preview-stage-group .subtask-collapse-trigger {
  min-height: 36rpx;
  padding: 2rpx 8rpx;
  background: transparent;
  color: #527677;
}

.study-plan-page .preview-stage-group .subtask-row {
  margin-top: 10rpx;
  padding: 14rpx 12rpx;
  border: 1rpx solid #E2E8EB;
  border-radius: 10rpx;
  background: #FFFFFF;
}

.study-plan-page .preview-stage-group .subtask-order {
  width: 30rpx;
  padding-top: 10rpx;
  color: #527677;
}

.study-plan-page .preview-stage-group .subtask-name-col {
  min-width: 160rpx;
}

.study-plan-page .preview-stage-group .subtask-name-col .task-edit-input {
  height: 42rpx;
  border: 0;
  background: transparent;
  font-size: 23rpx;
  font-weight: 500;
}

.study-plan-page .preview-stage-group .subtask-desc-input {
  color: #7C8998;
  font-size: 20rpx;
}

.study-plan-page .preview-stage-group .subtask-days-input {
  flex: 0 0 auto;
  max-width: 76rpx;
}

.study-plan-page .preview-stage-group .subtask-actions {
  margin-left: 40rpx;
  gap: 18rpx;
}

.study-plan-page .preview-stage-group .add-subtask-row {
  margin-top: 10rpx;
  padding: 14rpx 0 4rpx;
  border: 1rpx dashed #C9D6D8;
  border-radius: 10rpx;
  color: #397577;
}

.study-plan-page .preview-task-list > .add-task-row {
  margin-top: 16rpx;
  padding: 18rpx 0;
  border: 1rpx dashed #C9D6D8;
  border-radius: 14rpx;
  background: #FFFFFF;
  color: #397577;
}

.study-plan-page .preview-action-bar {
  position: sticky;
  bottom: 0;
  z-index: 10;
  margin: 24rpx -28rpx 0;
  padding: 14rpx 28rpx calc(14rpx + env(safe-area-inset-bottom));
  border-top: 1rpx solid #E4E9ED;
  background: rgba(255, 255, 255, 0.97);
}

.study-plan-page .preview-action-bar .ghost-btn {
  flex: 0 0 214rpx;
  height: 72rpx;
  padding: 0 14rpx;
  border-color: #D6DEE4;
  border-radius: 12rpx;
  color: #657287;
  font-size: 23rpx;
}

.study-plan-page .preview-action-bar .primary-btn--inline {
  flex: 1;
  width: auto;
  height: 72rpx;
  margin-top: 0;
  border-radius: 12rpx;
  background: #397577;
  font-size: 24rpx;
}
</style>
