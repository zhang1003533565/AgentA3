<template>
  <view class="page">
    <!-- 通用导航栏 -->
    <nav-bar title="AI试卷生成" :showBack="true" />

    <view class="content">
      <!-- 考试类型 Tab -->
      <view class="section-card">
        <view class="exam-type-tabs">
          <view
            class="exam-type-tab"
            :class="{ 'exam-type-tab--active': examType === item.value }"
            v-for="item in examTypes"
            :key="item.value"
            @tap="examType = item.value"
          >
            <text class="exam-type-tab-text">{{ item.label }}</text>
          </view>
        </view>
      </view>

      <!-- 题库编排方式 -->
      <view class="section-card">
        <view class="field-label">题库编排方式</view>
        <view class="choice-row">
          <view
            v-for="item in assemblyModes"
            :key="item.value"
            class="choice-chip"
            :class="{ 'choice-chip--active': assemblyMode === item.value }"
            @tap="assemblyMode = item.value"
          >{{ item.label }}</view>
        </view>
        <text class="field-help">{{ assemblyModeHelp }}</text>
      </view>

      <!-- 生成依据 -->
      <view class="section-card" v-if="assemblyMode !== 'existing'">
        <view class="field-label">生成依据</view>
        <view class="choice-row">
          <view
            v-for="item in basisModes"
            :key="item.value"
            class="choice-chip"
            :class="{ 'choice-chip--active': basisMode === item.value }"
            @tap="selectBasisMode(item.value)"
          >{{ item.label }}</view>
        </view>
        <view class="file-row" v-if="basisMode === 'uploaded_question_bank'">
          <view class="file-select" @tap="chooseBasisFile">选择题库文件</view>
          <text class="file-name">{{ basisFileName || '支持 TXT、DOCX' }}</text>
        </view>
      </view>

      <!-- 知识点输入 -->
      <view class="section-card" v-if="assemblyMode !== 'existing' && basisMode !== 'uploaded_question_bank'">
        <view class="field-label">{{ basisMode === 'knowledge_agent' ? '知识点主题' : '生成材料' }}</view>
        <textarea
          class="knowledge-input"
          v-model="knowledgeText"
          :placeholder="basisMode === 'knowledge_agent' ? '输入主题，先由知识点智能体整理知识点，再交给题型智能体出题' : '请输入需要考察的知识点或材料内容'"
          :maxlength="500"
        />
        <view class="input-footer">
          <text class="char-count">{{ knowledgeText.length }}/500</text>
        </view>
      </view>

      <!-- 试卷难度 -->
      <view class="section-card">
        <view class="field-label-row">
          <text class="field-label">试卷难度</text>
          <text class="field-info-icon">ⓘ</text>
        </view>
        <view class="difficulty-row">
          <view class="stars">
            <text
              class="star"
              :class="{ 'star--active': idx < difficulty }"
              v-for="idx in 5"
              :key="idx"
              @tap="difficulty = idx"
            >★</text>
          </view>
          <text class="difficulty-label">{{ difficultyLabels[difficulty - 1] || '' }}</text>
        </view>
      </view>

      <!-- 题目数量 -->
      <view class="section-card">
        <view class="field-label">题目数量</view>
        <view class="stepper-row">
          <view class="stepper-btn" @tap="decreaseTotal">−</view>
          <text class="stepper-value">{{ totalQuestions }}</text>
          <text class="stepper-unit">题</text>
          <view class="stepper-btn" @tap="increaseTotal">+</view>
        </view>
      </view>

      <!-- 题型分布 -->
      <view class="section-card">
        <view class="field-label">题型分布</view>
        <view class="question-type-list">
          <view class="question-type-item" v-for="item in questionTypes" :key="item.key">
            <view class="qt-left">
              <view class="qt-checkbox" :class="{ 'qt-checkbox--checked': item.enabled }" @tap="toggleType(item)">
                <text class="qt-check-icon" v-if="item.enabled">✓</text>
              </view>
              <view class="qt-labels">
                <text class="qt-name">{{ item.name }}</text>
                <text class="qt-agent">{{ item.available ? item.agentName : '未配置生成智能体' }}</text>
              </view>
              <text class="qt-percent">({{ getPercent(item) }}%)</text>
            </view>
            <view class="qt-right">
              <view class="mini-stepper-btn" @tap="decreaseType(item)">−</view>
              <text class="mini-stepper-value">{{ item.count }}</text>
              <view class="mini-stepper-btn" @tap="increaseType(item)">+</view>
              <text class="mini-stepper-unit">题</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 生成试卷按钮 -->
      <view class="generate-btn" :class="{ 'generate-btn--disabled': isGenerating }" @tap="generateExam">
        <text class="generate-btn-text">{{ isGenerating ? '提交中...' : '✦ 后台生成题库' }}</text>
      </view>

      <view class="section-card task-card" v-if="currentTask.taskId">
        <view class="task-head">
          <text class="field-label">题库后台任务</text>
          <text class="task-status">{{ taskStatusLabel }}</text>
        </view>
        <text class="task-message">{{ currentTask.message }}</text>
        <view class="task-progress"><view class="task-progress-value" :style="{ width: `${currentTask.progress || 0}%` }"></view></view>
        <text class="field-help" v-if="!isTaskFinished">任务在后台运行，你可以离开本页继续聊天，完成后会收到题库消息。</text>
      </view>
      
      <!-- 试卷预览 -->
      <view class="section-card preview-card">
        <view class="preview-header">
          <text class="preview-title">试卷预览</text>
          <text class="preview-clear" @tap="clearPreview">清空预览</text>
        </view>
        <view class="assembly-summary" v-if="assemblyResult">
          <text>系统题库 {{ assemblyResult.existingCount || 0 }} 题 · 智能体生成 {{ assemblyResult.generatedCount || 0 }} 题</text>
          <text class="summary-warning" v-if="assemblyResult.missingCount">仍缺 {{ assemblyResult.missingCount }} 题</text>
        </view>
        <view class="assembly-issues" v-if="assemblyResult?.issues?.length">
          <text v-for="issue in assemblyResult.issues" :key="issue">{{ issue }}</text>
        </view>
        <view
          class="private-save-btn"
          :class="{ 'private-save-btn--disabled': isSavingPrivate || privateSaved }"
          v-if="assemblyResult && assemblyResult.generatedCount > 0"
          @tap="saveGeneratedToPrivateBank"
        >{{ privateSaved ? '已保存到我的私有题库' : (isSavingPrivate ? '保存中...' : '保存生成题到我的私有题库') }}</view>
      
        <!-- 题目列表 -->
        <view class="questions-list" v-if="previewQuestions.length > 0">
          <view class="question-item" v-for="(q, index) in previewQuestions" :key="index">
            <view class="question-header">
              <text class="question-number">{{ index + 1 }}.</text>
              <text class="question-type">[{{ q.type || '题目' }}]</text>
              <text class="question-origin">{{ q._origin === 'existing' ? '题库已有' : '智能体生成' }}</text>
            </view>
            <view class="question-stem">
              <text>{{ q.stem || q.question || '' }}</text>
            </view>
            <!-- 选择题选项 -->
            <view class="question-options" v-if="q.options && q.options.length > 0">
              <view class="option-item" v-for="opt in q.options" :key="opt.label || opt.key">
                <text class="option-label">{{ opt.label || opt.key }}.</text>
                <text class="option-text">{{ opt.text || opt.content || '' }}</text>
              </view>
            </view>
            <!-- 答案和解析 -->
            <view class="question-answer">
              <text class="answer-label">答案：</text>
              <text class="answer-value">{{ q._answerText }}</text>
            </view>
            <view class="question-explanation" v-if="q.explanation || q.analysis">
              <text class="explanation-label">解析：</text>
              <text class="explanation-value">{{ q.explanation || q.analysis || '' }}</text>
            </view>
          </view>
        </view>
      
        <!-- 原始文本预览（当无法解析为题目时） -->
        <view class="preview-body" v-else-if="previewContent">
          <text class="preview-text">{{ previewContent }}</text>
        </view>
      
        <!-- 空状态 -->
        <view class="preview-empty" v-else>
          <text class="preview-empty-text">点击“生成试卷”后在此预览</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import NavBar from '@/components/nav-bar/nav-bar.vue'
import {
  commitPrivateQuestionAssembly,
  getQuestionAssemblyTask,
  getQuestionAssemblyOptions,
  submitQuestionAssemblyFileTask,
  submitQuestionAssemblyTask
} from '@/api/questionAssembly.js'

const examTypes = [
  { label: '期末考试', value: 'final' },
  { label: '单元测试', value: 'unit' },
  { label: '模拟考试', value: 'mock' },
  { label: '竞赛考试', value: 'contest' }
]
const assemblyModes = [
  { label: '系统题库编排', value: 'existing' },
  { label: '智能体生成', value: 'generate' },
  { label: '题库不足时生成', value: 'hybrid' }
]
const basisModes = [
  { label: '输入材料', value: 'text' },
  { label: '上传题库', value: 'uploaded_question_bank' },
  { label: '知识点智能体', value: 'knowledge_agent' }
]
const typeNames = {
  single_choice: '单选题',
  multiple_choice: '多选题',
  true_false: '判断题',
  fill_blank: '填空题',
  short_answer: '简答题',
  calculation: '计算题',
  programming: '编程题'
}

const examType = ref('final')
const assemblyMode = ref('hybrid')
const basisMode = ref('text')
const knowledgeText = ref('')
const basisFilePath = ref('')
const basisFileName = ref('')
const difficulty = ref(3)
const difficultyLabels = ['简单', '较易', '中等', '较难', '困难']
const questionTypes = ref([])
const previewContent = ref('')
const previewQuestions = ref([])
const assemblyResult = ref(null)
const isGenerating = ref(false)
const isSavingPrivate = ref(false)
const privateSaved = ref(false)
const currentTask = ref({ taskId: '', status: '', progress: 0, message: '' })
let taskPollTimer = null

const assemblyModeHelp = computed(() => ({
  existing: '只从公共题库和你自己的私有题库中选题',
  generate: '按所选题型调用对应智能体生成',
  hybrid: '优先使用可见题库，不足的部分再调用题型智能体'
}[assemblyMode.value]))
const enabledTotal = computed(() => questionTypes.value
  .filter(item => item.enabled)
  .reduce((sum, item) => sum + item.count, 0))
const totalQuestions = computed(() => enabledTotal.value)
const isTaskFinished = computed(() => ['SUCCEEDED', 'FAILED'].includes(currentTask.value.status))
const taskStatusLabel = computed(() => ({
  QUEUED: '排队中',
  RUNNING: '处理中',
  SUCCEEDED: '已完成',
  FAILED: '失败'
}[currentTask.value.status] || '等待中'))
const getPercent = item => !item.enabled || !enabledTotal.value
  ? 0
  : Math.round(item.count / enabledTotal.value * 100)

const loadOptions = async () => {
  try {
    const res = await getQuestionAssemblyOptions()
    questionTypes.value = (res?.data?.questionTypes || [])
      .map(item => ({
        key: item.type,
        name: typeNames[item.type] || item.type,
        agentName: item.agentName,
        available: Boolean(item.available),
        enabled: Boolean(item.available),
        count: 4
      }))
  } catch (error) {
    previewContent.value = '暂时无法读取你可用的题型智能体，请稍后重试'
  }
}

onMounted(async () => {
  await loadOptions()
  const pages = typeof getCurrentPages === 'function' ? getCurrentPages() : []
  const pageOptions = pages[pages.length - 1]?.options || {}
  if (pageOptions.prefill && !knowledgeText.value) {
    try {
      knowledgeText.value = decodeURIComponent(pageOptions.prefill)
    } catch (error) {
      knowledgeText.value = String(pageOptions.prefill)
    }
  }
  const taskId = pageOptions.taskId
  if (taskId) {
    currentTask.value.taskId = taskId
    await refreshTask(taskId)
  }
})

onUnmounted(() => {
  if (taskPollTimer) clearTimeout(taskPollTimer)
})

const selectBasisMode = value => {
  basisMode.value = value
  if (value !== 'uploaded_question_bank') {
    basisFilePath.value = ''
    basisFileName.value = ''
  }
}
const chooseBasisFile = () => {
  const choose = uni.chooseMessageFile || uni.chooseFile
  if (!choose) {
    uni.showToast({ title: '当前端不支持文件选择', icon: 'none' })
    return
  }
  choose({
    count: 1,
    type: 'file',
    extension: ['txt', 'docx'],
    success: result => {
      const file = result.tempFiles?.[0]
      basisFilePath.value = file?.path || result.tempFilePaths?.[0] || ''
      basisFileName.value = file?.name || basisFilePath.value.split(/[\\/]/).pop() || '已选择文件'
    }
  })
}

const toggleType = item => {
  if (assemblyMode.value !== 'existing' && !item.available) {
    uni.showToast({ title: `${item.name}尚未配置生成智能体`, icon: 'none' })
    return
  }
  item.enabled = !item.enabled
}
const increaseType = item => { if (item.count < 50) item.count++ }
const decreaseType = item => { if (item.count > 0) item.count-- }
const increaseTotal = () => {
  const target = questionTypes.value.find(item => item.enabled)
  if (target && enabledTotal.value < 100) target.count++
}
const decreaseTotal = () => {
  const target = [...questionTypes.value].reverse().find(item => item.enabled && item.count > 1)
  if (target) target.count--
}

const clearPreview = () => {
  previewContent.value = ''
  previewQuestions.value = []
  assemblyResult.value = null
  privateSaved.value = false
}

const difficultyCode = () => difficulty.value <= 2 ? 'easy' : (difficulty.value >= 4 ? 'hard' : 'medium')
const answerText = question => {
  const answer = question.answer ?? question.correctAnswer
  if (answer == null) return ''
  if (typeof answer !== 'object') return String(answer)
  const value = answer.correctOptionKeys
    || answer.correctOptionKey
    || answer.acceptedAnswers
    || answer.points
  if (Array.isArray(value)) return value.join('、')
  if (value != null) return String(value)
  if (typeof answer.correct === 'boolean') return answer.correct ? '正确' : '错误'
  return JSON.stringify(answer)
}
const toPreviewQuestion = item => {
  const question = item.question || {}
  return {
    ...question,
    type: item.type || question.type,
    options: question.options || question.body?.options || [],
    _answerText: answerText(question),
    _origin: item.origin,
    _generatedBy: item.generatedBy
  }
}
const buildSpec = enabledTypes => ({
  mode: assemblyMode.value,
  basisMode: assemblyMode.value === 'existing' ? null : basisMode.value,
  sourceType: basisMode.value === 'uploaded_question_bank'
    ? (basisFileName.value.toLowerCase().endsWith('.docx') ? 'docx' : 'txt')
    : 'text',
  topic: knowledgeText.value.trim(),
  text: knowledgeText.value.trim(),
  sourceTitle: examTypes.find(item => item.value === examType.value)?.label || '个人题库编排',
  saveGeneratedToPrivate: true,
  rules: enabledTypes.map(item => ({
    type: item.key,
    quantity: item.count,
    difficulty: difficultyCode()
  }))
})

const generateExam = async () => {
  if (isGenerating.value) return
  const enabledTypes = questionTypes.value.filter(item => item.enabled && item.count > 0)
  if (!enabledTypes.length) {
    uni.showToast({ title: '请至少选择一种可用题型', icon: 'none' })
    return
  }
  if (assemblyMode.value !== 'existing' && basisMode.value === 'uploaded_question_bank' && !basisFilePath.value) {
    uni.showToast({ title: '请先选择题库文件', icon: 'none' })
    return
  }
  if (assemblyMode.value !== 'existing' && basisMode.value !== 'uploaded_question_bank' && !knowledgeText.value.trim()) {
    uni.showToast({ title: '请填写生成依据', icon: 'none' })
    return
  }

  isGenerating.value = true
  clearPreview()
  try {
    const spec = buildSpec(enabledTypes)
    const res = basisFilePath.value
      ? await submitQuestionAssemblyFileTask(spec, basisFilePath.value)
      : await submitQuestionAssemblyTask(spec)
    currentTask.value = {
      taskId: res?.data?.taskId || '',
      status: res?.data?.status || 'QUEUED',
      progress: 0,
      message: res?.data?.message || '题库任务已提交'
    }
    previewContent.value = '任务已转入后台处理，你可以继续聊天或使用其他功能。'
    scheduleTaskPoll()
    uni.showToast({ title: '已转入后台处理', icon: 'success' })
  } catch (error) {
    const message = error?.msg || error?.message || error?.data?.msg || '题库编排失败'
    previewContent.value = message
  } finally {
    isGenerating.value = false
  }
}

const applyTaskResult = task => {
  const result = task?.result || {}
  assemblyResult.value = result
  previewQuestions.value = (result.questions || []).map(toPreviewQuestion)
  previewContent.value = (result.issues || []).join('\n')
  privateSaved.value = Number(task?.importedCount || 0) > 0
}

const refreshTask = async taskId => {
  if (!taskId) return
  try {
    const res = await getQuestionAssemblyTask(taskId)
    const task = res?.data || {}
    currentTask.value = task
    if (task.status === 'SUCCEEDED') {
      applyTaskResult(task)
      return
    }
    if (task.status === 'FAILED') {
      previewContent.value = task.errorMessage || '题库任务处理失败'
      return
    }
    scheduleTaskPoll()
  } catch (error) {
    previewContent.value = error?.msg || error?.message || '题库任务状态读取失败'
  }
}

const scheduleTaskPoll = () => {
  if (taskPollTimer) clearTimeout(taskPollTimer)
  if (!currentTask.value.taskId || isTaskFinished.value) return
  taskPollTimer = setTimeout(() => refreshTask(currentTask.value.taskId), 3000)
}

const saveGeneratedToPrivateBank = async () => {
  if (isSavingPrivate.value || privateSaved.value || !assemblyResult.value?.draftId) return
  isSavingPrivate.value = true
  try {
    const res = await commitPrivateQuestionAssembly(assemblyResult.value.draftId)
    privateSaved.value = true
    uni.showToast({ title: `已保存 ${res?.data?.importedCount || 0} 道私有题`, icon: 'success' })
  } finally {
    isSavingPrivate.value = false
  }
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background-color: #F6F8FB;
  box-sizing: border-box;
}

.content {
  padding: 20rpx 24rpx 40rpx;
}

.section-card {
  background: #FFFFFF;
  border-radius: 16rpx;
  padding: 28rpx 24rpx;
  margin-bottom: 20rpx;
}

/* 考试类型 Tab */
.exam-type-tabs {
  display: flex;
  gap: 16rpx;
}

.exam-type-tab {
  flex: 1;
  padding: 18rpx 0;
  text-align: center;
  background: #F0F2F5;
  border-radius: 12rpx;
}

.exam-type-tab--active {
  background: #EEF0FF;
  border: 2rpx solid #4D6BFE;
}

.exam-type-tab-text {
  font-size: 26rpx;
  color: #666;
}

.exam-type-tab--active .exam-type-tab-text {
  color: #4D6BFE;
  font-weight: 600;
}

/* 字段标签 */
.field-label {
  font-size: 30rpx;
  font-weight: 700;
  color: #222;
  margin-bottom: 20rpx;
}

.field-label-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20rpx;
}

.field-info-icon {
  font-size: 28rpx;
  color: #AAA;
}

.choice-row {
  display: flex;
  flex-wrap: wrap;
  gap: 14rpx;
}

.choice-chip {
  padding: 14rpx 20rpx;
  border: 2rpx solid #E4E7EC;
  border-radius: 10rpx;
  color: #667085;
  font-size: 25rpx;
}

.choice-chip--active {
  color: #4D6BFE;
  border-color: #4D6BFE;
  background: #F2F4FF;
}

.field-help {
  display: block;
  margin-top: 16rpx;
  color: #98A2B3;
  font-size: 24rpx;
  line-height: 1.5;
}

.file-row {
  display: flex;
  align-items: center;
  gap: 18rpx;
  margin-top: 20rpx;
}

.file-select {
  padding: 14rpx 20rpx;
  border-radius: 10rpx;
  background: #EEF0FF;
  color: #4D6BFE;
  font-size: 25rpx;
}

.file-name {
  flex: 1;
  color: #667085;
  font-size: 24rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 知识点文本输入 */
.knowledge-input {
  width: 100%;
  min-height: 180rpx;
  padding: 20rpx;
  background: #F8F9FA;
  border-radius: 12rpx;
  font-size: 28rpx;
  color: #333;
  line-height: 1.6;
  box-sizing: border-box;
}

.input-footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 12rpx;
}

.char-count {
  font-size: 24rpx;
  color: #BBB;
}

/* 难度 */
.difficulty-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.stars {
  display: flex;
  gap: 8rpx;
}

.star {
  font-size: 44rpx;
  color: #DDD;
}

.star--active {
  color: #FFB800;
}

.difficulty-label {
  font-size: 26rpx;
  color: #888;
  margin-left: 8rpx;
}

/* 题目数量 Stepper */
.stepper-row {
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.stepper-btn {
  width: 60rpx;
  height: 60rpx;
  background: #F0F2F5;
  border-radius: 12rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32rpx;
  color: #333;
  font-weight: 600;
}

.stepper-value {
  font-size: 36rpx;
  font-weight: 700;
  color: #222;
  min-width: 60rpx;
  text-align: center;
}

.stepper-unit {
  font-size: 26rpx;
  color: #888;
}

/* 题型分布 */
.question-type-list {
  display: flex;
  flex-direction: column;
}

.question-type-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 18rpx 0;
  border-bottom: 1rpx solid #F5F5F5;
}

.question-type-item:last-child {
  border-bottom: none;
}

.qt-left {
  display: flex;
  align-items: center;
  gap: 14rpx;
}

.qt-checkbox {
  width: 40rpx;
  height: 40rpx;
  border-radius: 8rpx;
  border: 2rpx solid #DDD;
  display: flex;
  align-items: center;
  justify-content: center;
}

.qt-checkbox--checked {
  background: #4D6BFE;
  border-color: #4D6BFE;
}

.qt-check-icon {
  color: #FFF;
  font-size: 24rpx;
  font-weight: 700;
}

.qt-name {
  font-size: 28rpx;
  color: #333;
}

.qt-labels {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.qt-agent {
  max-width: 220rpx;
  color: #98A2B3;
  font-size: 20rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.qt-percent {
  font-size: 24rpx;
  color: #999;
}

.qt-right {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.mini-stepper-btn {
  width: 44rpx;
  height: 44rpx;
  background: #F0F2F5;
  border-radius: 8rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26rpx;
  color: #333;
  font-weight: 600;
}

.mini-stepper-value {
  font-size: 30rpx;
  font-weight: 700;
  color: #222;
  min-width: 36rpx;
  text-align: center;
}

.mini-stepper-unit {
  font-size: 24rpx;
  color: #888;
}

/* 知识点标签 */
.smart-recommend {
  font-size: 24rpx;
  color: #4D6BFE;
}

.knowledge-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 14rpx;
}

.knowledge-tag {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 12rpx 20rpx;
  background: #EEF0FF;
  border-radius: 999rpx;
}

.tag-text {
  font-size: 24rpx;
  color: #4D6BFE;
}

.tag-close {
  font-size: 20rpx;
  color: #4D6BFE;
}

/* 生成按钮 */
.generate-btn {
  background: linear-gradient(135deg, #6A8CFE 0%, #4D6BFE 100%);
  border-radius: 16rpx;
  padding: 30rpx 0;
  text-align: center;
  margin-bottom: 20rpx;
}

.generate-btn-text {
  color: #FFF;
  font-size: 32rpx;
  font-weight: 700;
}

.generate-btn--disabled {
  opacity: 0.6;
}

/* 试卷预览 */
.preview-card {
  margin-bottom: 40rpx;
}

.preview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20rpx;
}

.preview-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #222;
}

.preview-clear {
  font-size: 24rpx;
  color: #999;
}

.task-card {
  border: 2rpx solid #DDE3FF;
}

.task-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.task-head .field-label {
  margin-bottom: 0;
}

.task-status {
  color: #4D6BFE;
  font-size: 24rpx;
}

.task-message {
  display: block;
  margin-top: 16rpx;
  color: #475467;
  font-size: 25rpx;
}

.task-progress {
  height: 10rpx;
  margin-top: 18rpx;
  overflow: hidden;
  border-radius: 999rpx;
  background: #EAECF0;
}

.task-progress-value {
  height: 100%;
  border-radius: inherit;
  background: #4D6BFE;
  transition: width .25s ease;
}

.assembly-summary {
  display: flex;
  justify-content: space-between;
  margin-bottom: 18rpx;
  color: #667085;
  font-size: 24rpx;
}

.summary-warning {
  color: #D92D20;
}

.assembly-issues {
  display: flex;
  flex-direction: column;
  gap: 6rpx;
  margin-bottom: 18rpx;
  color: #D92D20;
  font-size: 23rpx;
}

.private-save-btn {
  padding: 18rpx;
  margin-bottom: 20rpx;
  border: 2rpx solid #4D6BFE;
  border-radius: 12rpx;
  text-align: center;
  color: #4D6BFE;
  font-size: 26rpx;
}

.private-save-btn--disabled {
  opacity: 0.55;
}

/* 题目列表 */
.questions-list {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.question-item {
  background: #F8F9FA;
  border-radius: 12rpx;
  padding: 24rpx;
}

.question-header {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 16rpx;
}

.question-number {
  font-size: 30rpx;
  font-weight: 700;
  color: #4D6BFE;
}

.question-type {
  font-size: 24rpx;
  color: #888;
  background: #EEF0FF;
  padding: 4rpx 12rpx;
  border-radius: 6rpx;
}

.question-origin {
  margin-left: auto;
  color: #667085;
  font-size: 22rpx;
}

.question-stem {
  font-size: 28rpx;
  color: #333;
  line-height: 1.6;
  margin-bottom: 16rpx;
}

.question-options {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  margin-bottom: 16rpx;
}

.option-item {
  display: flex;
  align-items: flex-start;
  gap: 12rpx;
}

.option-label {
  font-size: 28rpx;
  font-weight: 600;
  color: #555;
  min-width: 32rpx;
}

.option-text {
  font-size: 28rpx;
  color: #333;
  line-height: 1.5;
  flex: 1;
}

.question-answer {
  display: flex;
  align-items: flex-start;
  gap: 8rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid #E8E8E8;
}

.answer-label {
  font-size: 26rpx;
  color: #888;
}

.answer-value {
  font-size: 26rpx;
  color: #4D6BFE;
  font-weight: 600;
}

.question-explanation {
  display: flex;
  align-items: flex-start;
  gap: 8rpx;
  margin-top: 12rpx;
}

.explanation-label {
  font-size: 26rpx;
  color: #888;
}

.explanation-value {
  font-size: 26rpx;
  color: #666;
  line-height: 1.6;
  flex: 1;
}

.preview-body {
  background: #F8F9FA;
  border-radius: 12rpx;
  padding: 24rpx;
}

.preview-text {
  font-size: 26rpx;
  color: #333;
  line-height: 1.8;
  white-space: pre-wrap;
}

.preview-empty {
  padding: 40rpx 0;
  text-align: center;
}

.preview-empty-text {
  font-size: 26rpx;
  color: #BBB;
}
</style>
