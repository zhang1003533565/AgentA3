<template>
  <view class="page">
    <nav-bar title="题库生成" subtitle="从资料快速生成练习题" :showBack="true" />

    <view
      class="content"
      :class="{
        'content--footer': step === 1
          || (step === 2 && (generating || generateError))
          || (step === 3 && hasPreviewResult)
      }"
    >
      <view class="stepper">
        <view
          v-for="item in steps"
          :key="item.value"
          class="step"
          :class="{
            'step--active': step === item.value,
            'step--done': step > item.value
          }"
          @tap="goStep(item.value)"
        >{{ item.label }}</view>
      </view>

      <!-- 1 设置 -->
      <block v-if="step === 1">
        <view class="card">
          <text class="card-title">资料来源</text>
          <view class="seg-row">
            <view
              class="seg"
              :class="{ 'seg--active': sourceRoute === 'direct' }"
              @tap="sourceRoute = 'direct'"
            >直接导入资料</view>
            <view
              class="seg"
              :class="{ 'seg--active': sourceRoute === 'knowledge' }"
              @tap="onSelectKnowledgeRoute"
            >知识库选课</view>
          </view>

          <view v-if="sourceRoute === 'direct'" class="route-box">
            <view class="route-title">
              <text>路径一：直接导入资料</text>
              <text class="route-tag">当前选择</text>
            </view>
            <view class="seg-equal seg-equal--2">
              <view
                v-for="item in inputModes"
                :key="item.value"
                class="seg-equal__item"
                :class="{ 'seg-equal__item--active': inputMode === item.value }"
                @tap="switchInputMode(item.value)"
              >{{ item.label }}</view>
            </view>

            <textarea
              v-if="inputMode === 'text'"
              class="textarea"
              v-model="materialText"
              maxlength="20000"
              placeholder="请粘贴老师讲义、复习提纲或知识点摘要…"
            />
            <text v-if="inputMode === 'text'" class="muted">当前输入 {{ materialText.length }} 字，建议保留章节标题与关键术语。</text>

            <view
              v-else
              class="dropzone"
              :class="{ 'dropzone--ready': Boolean(fileName) }"
              @tap="chooseFile"
              @dragover.prevent
              @drop.prevent="onDropFile"
            >
              <view class="dropzone__icon" aria-hidden="true">
                <view class="dropzone__tray"></view>
              </view>
              <text class="dropzone__title">{{ fileName || '点击或拖拽一个 TXT / DOCX 文件' }}</text>
              <text class="dropzone__desc">{{ fileName ? `已选择 · ${fileSourceType.toUpperCase()}，选择后不会自动上传` : '选择后不会自动上传；DOCX 提取标题、正文和表格，不识别图片' }}</text>
              <text v-if="fileName" class="dropzone__replace">点击可重新选择</text>
            </view>
            <text v-if="fieldError.source" class="field-error">{{ fieldError.source }}</text>
          </view>

          <view v-else class="route-box">
            <view class="route-title">
              <text>路径二：从知识库选择课程与章节</text>
              <text class="route-tag">第二导入路</text>
            </view>
            <text class="muted">{{ knowledgeHint }}</text>
            <view class="selector" @tap="pickKnowledgeAccount">
              <text>知识库账号</text>
              <text class="selector-value">{{ selectedAccountLabel }}</text>
              <text class="arrow">›</text>
            </view>
            <view class="selector" @tap="pickKnowledgeBase">
              <text>对应课程</text>
              <text class="selector-value">{{ selectedKnowledgeLabel }}</text>
              <text class="arrow">›</text>
            </view>
            <view class="chip-row" style="margin-top: 16rpx;">
              <view
                v-for="doc in knowledgeDocuments"
                :key="doc.id"
                class="chip"
                :class="{ 'chip--active': selectedDocumentIds.includes(String(doc.id)) }"
                @tap="toggleDocument(doc)"
              >{{ doc.name || doc.title || '未命名文档' }}</view>
            </view>
            <text v-if="!knowledgeDocuments.length && selectedKnowledgeId" class="muted">该课程下暂无文档。</text>
            <text v-if="fieldError.source" class="field-error">{{ fieldError.source }}</text>
          </view>
        </view>

        <view class="card">
          <text class="card-title">出题规则</text>

          <view class="field-block">
            <text class="field-label">来源标题</text>
            <input
              class="input input--flush"
              v-model="sourceTitle"
              maxlength="160"
              placeholder="可留空，自动用文件名或「粘贴文本生成」"
            />
          </view>

          <view class="field-block">
            <view class="field-label-row">
              <text class="field-label">题型</text>
              <text class="field-hint">单次仅一种</text>
            </view>
            <view class="type-list">
              <view
                v-for="item in questionTypeOptions"
                :key="item.type"
                class="type-row"
                :class="{
                  'type-row--active': questionType === item.type,
                  'type-row--disabled': item.available === false
                }"
                @tap="selectQuestionType(item)"
              >
                <view class="type-row__main">
                  <view class="type-radio" :class="{ 'type-radio--on': questionType === item.type }"></view>
                  <text class="type-row__name">{{ typeLabel(item.type) }}</text>
                </view>
                <text
                  class="type-row__status"
                  :class="item.available === false ? 'type-row__status--off' : 'type-row__status--on'"
                >{{ item.available === false ? '不可用' : '可用' }}</text>
              </view>
            </view>
          </view>

          <view class="field-block">
            <text class="field-label">难度</text>
            <view class="seg-equal">
              <view
                v-for="item in difficulties"
                :key="item.value"
                class="seg-equal__item"
                :class="{ 'seg-equal__item--active': difficulty === item.value }"
                @tap="difficulty = item.value"
              >{{ item.label }}</view>
            </view>
          </view>

          <view class="field-block field-block--last">
            <text class="field-label">题量上限</text>
            <view class="seg-equal seg-equal--4">
              <view
                v-for="n in maxQuestionChoices"
                :key="n"
                class="seg-equal__item"
                :class="{ 'seg-equal__item--active': maxQuestions === n }"
                @tap="maxQuestions = n"
              >{{ n }} 题</view>
            </view>
          </view>

          <text v-if="fieldError.rules" class="field-error">{{ fieldError.rules }}</text>
          <text v-if="optionsError" class="field-tip">{{ optionsError }}</text>
        </view>

        <view class="card">
          <text class="card-title">本次生成摘要</text>
          <view class="summary-grid">
            <view class="summary-item">
              <text class="summary-label">输入方式</text>
              <text class="summary-value">{{ summaryInputMode }}</text>
            </view>
            <view class="summary-item">
              <text class="summary-label">题型</text>
              <text class="summary-value">{{ typeLabel(questionType) || '未选' }}</text>
            </view>
            <view class="summary-item">
              <text class="summary-label">难度</text>
              <text class="summary-value">{{ difficultyLabel }}</text>
            </view>
            <view class="summary-item">
              <text class="summary-label">题量上限</text>
              <text class="summary-value">{{ maxQuestions }} 题</text>
            </view>
          </view>
        </view>
      </block>

      <!-- 2 生成中 -->
      <block v-if="step === 2">
        <view v-if="!generating && !generateError" class="empty-card">
          <view class="empty-icon" aria-hidden="true"></view>
          <text class="empty-title">尚未开始生成</text>
          <text class="empty-desc">先在「设置」里填好资料与出题规则，再点开始生成。生成过程会显示进度与材料摘要。</text>
          <view class="empty-actions">
            <view class="primary empty-btn" @tap="step = 1">去设置</view>
          </view>
        </view>

        <block v-else>
          <view class="card">
            <text class="card-title">当前进度</text>
            <view class="progress-box">
              <view class="progress-top">
                <text>{{ progressText }}</text>
                <text>{{ progressPercent }}%</text>
              </view>
              <view class="progress-bar">
                <view class="progress-value" :style="{ width: progressPercent + '%' }"></view>
              </view>
              <view class="timeline">
                <view class="timeline-item" v-for="(item, index) in timeline" :key="index">
                  <view class="dot" :class="{ 'dot--active': item.active }"></view>
                  <text>{{ item.text }}</text>
                </view>
              </view>
            </view>
            <text v-if="generateError" class="field-error">{{ generateError }}</text>
          </view>

          <view class="card">
            <text class="card-title">材料摘要</text>
            <view class="summary-grid">
              <view class="summary-item">
                <text class="summary-label">来源</text>
                <text class="summary-value">{{ summaryInputMode }}</text>
              </view>
              <view class="summary-item">
                <text class="summary-label">智能体</text>
                <text class="summary-value">题库生成智能体</text>
              </view>
              <view class="summary-item">
                <text class="summary-label">题型</text>
                <text class="summary-value">{{ typeLabel(questionType) }}</text>
              </view>
              <view class="summary-item">
                <text class="summary-label">题量上限</text>
                <text class="summary-value">{{ maxQuestions }} 题</text>
              </view>
            </view>
          </view>

          <view class="card">
            <text class="card-title">说明</text>
            <view class="issue-list">
              <view class="issue">生成可能需要几秒到几十秒，请稍候。</view>
              <view class="issue">如果失败，系统会给出明确原因，不会自动导入题库。</view>
              <view class="issue">完成后将自动进入结果预览，可再编辑后导入。</view>
            </view>
          </view>
        </block>
      </block>

      <!-- 3 预览 -->
      <block v-if="step === 3">
        <view v-if="!hasPreviewResult" class="empty-card">
          <view class="empty-icon empty-icon--preview" aria-hidden="true"></view>
          <text class="empty-title">暂无预览结果</text>
          <text class="empty-desc">生成成功后，题目会在这里展示，可逐题编辑、删除，校验通过后再导入题库。</text>
          <view class="empty-actions">
            <view class="ghost empty-btn" @tap="step = 1">返回设置</view>
            <view class="primary empty-btn" @tap="step = 2">查看生成</view>
          </view>
        </view>

        <block v-else>
          <view class="card">
            <view class="banner" :class="bannerClass">{{ bannerText }}</view>
            <view v-if="(review.warnings || []).length" class="banner banner--warn">
              发现 {{ review.warnings.length }} 条提醒：{{ review.warnings.slice(0, 2).join('；') }}
            </view>
          </view>

          <view class="card">
            <text class="card-title">结果摘要</text>
            <view class="summary-grid">
              <view class="summary-item">
                <text class="summary-label">生成题数</text>
                <text class="summary-value">{{ questions.length }} 题</text>
              </view>
              <view class="summary-item">
                <text class="summary-label">警告数</text>
                <text class="summary-value">{{ (review.warnings || []).length }} 条</text>
              </view>
              <view class="summary-item">
                <text class="summary-label">缺失信息</text>
                <text class="summary-value">{{ (draft.missingInfo || []).length }} 条</text>
              </view>
              <view class="summary-item">
                <text class="summary-label">来源标题</text>
                <text class="summary-value">{{ draft.sourceTitle || draft.originalFilename || '—' }}</text>
              </view>
            </view>
          </view>

          <view v-if="(review.issues || []).length || (draft.missingInfo || []).length" class="card">
            <text class="card-title">问题提示</text>
            <view class="issue-list">
              <view class="issue" v-for="(item, index) in (review.issues || [])" :key="'i-' + index">{{ item }}</view>
              <view class="issue" v-for="(item, index) in (draft.missingInfo || [])" :key="'m-' + index">{{ item }}</view>
            </view>
          </view>

          <view class="card">
            <text class="card-title">题目预览</text>
            <view v-if="!questions.length" class="empty-inline">
              <text class="empty-inline__title">未生成有效题目</text>
              <text class="empty-inline__desc">可返回设置调整材料或题型后重新生成。</text>
            </view>
            <view v-for="(q, index) in questions" :key="q.id || index" class="question-card">
              <view class="question-head">
                <text class="question-no">{{ index + 1 }}. {{ typeLabel(q.type || draft.questionType) }}</text>
                <text class="badge">{{ questionBadge(q, index) }}</text>
              </view>

              <block v-if="editingIndex !== index">
                <text class="stem">{{ questionStem(q) }}</text>
                <text v-for="(opt, oi) in (q.body?.options || [])" :key="oi" class="option">{{ opt.key }}. {{ opt.text }}</text>
                <text class="answer">答案：{{ formatAnswer(q) }}　解析：{{ q.analysis || '—' }}</text>
                <view class="ops">
                  <view class="ghost" @tap="startEdit(index)">编辑</view>
                  <view class="ghost" @tap="confirmDelete(index)">删除</view>
                </view>
              </block>

              <block v-else>
                <textarea class="textarea textarea--edit" v-model="editDraft.stem" placeholder="题干" />
                <textarea class="textarea textarea--edit" v-model="editDraft.analysis" placeholder="解析" />
                <input class="input" v-model="editDraft.answerText" placeholder="答案摘要（保存时写回参考答案字段）" />
                <view class="ops">
                  <view class="ghost" @tap="cancelEdit">取消</view>
                  <view class="primary-mini" @tap="saveEdit">保存</view>
                </view>
              </block>
            </view>
          </view>

          <view v-if="importResult" class="card">
            <view class="banner banner--success">
              已导入 {{ importResult.importedCount ?? questions.length }} 道题。
              <text v-if="syncedFolderName">并已放入收藏夹「{{ syncedFolderName }}」。</text>
              <text v-else-if="syncFolderError">题目已入库，但加入收藏夹失败：{{ syncFolderError }}</text>
            </view>
            <view class="ops" style="margin-top: 16rpx;">
              <view class="primary-mini" @tap="goMyQuestionBank">去我的题库查看</view>
            </view>
          </view>
        </block>
      </block>
    </view>

    <view v-if="step === 1" class="footer-bar">
      <text class="tiny">生成前会先进行结构校验，未通过不会导入题库。</text>
      <view class="ops">
        <view class="primary" :class="{ 'primary--disabled': optionsLoading || generating }" @tap="startGenerate">开始生成</view>
      </view>
    </view>

    <view v-if="step === 2 && (generating || generateError)" class="footer-bar">
      <view class="ops">
        <view class="ghost" @tap="step = 1">返回设置</view>
        <view v-if="generateError" class="primary" @tap="startGenerate">重试</view>
        <view v-else class="ghost" @tap="leaveForLater">稍后查看</view>
      </view>
    </view>

    <view v-if="step === 3 && hasPreviewResult" class="footer-bar">
      <text v-if="!canImport && !importResult" class="tiny">当前结果不可导入，请先修正问题。</text>
      <view class="ops">
        <view class="ghost" @tap="continueGenerate">继续生成</view>
        <view
          class="primary"
          :class="{ 'primary--disabled': !canImport || importing || Boolean(importResult) }"
          @tap="handleImport"
        >{{ importResult ? '已导入' : (importing ? '导入中…' : '导入当前题库') }}</view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import NavBar from '@/components/nav-bar/nav-bar.vue'
import {
  getQuestionGenerationOptions,
  generateQuestions,
  importGeneratedQuestions,
  reviewGeneratedQuestions
} from '@/api/questionGeneration.js'
import {
  addQuestionToFolder,
  createQuestionFolder,
  listQuestionFolders
} from '@/api/questionFolder.js'
import { getUserInfo } from '@/utils/storage.js'
import { request } from '@/utils/request.js'

const TYPE_LABELS = {
  single_choice: '单选题',
  multiple_choice: '多选题',
  true_false: '判断题',
  fill_blank: '填空题',
  short_answer: '简答题',
  calculation: '计算题',
  programming: '编程题'
}

const steps = [
  { value: 1, label: '1 设置' },
  { value: 2, label: '2 生成' },
  { value: 3, label: '3 预览' }
]

const inputModes = [
  { value: 'text', label: '粘贴文本' },
  { value: 'file', label: '上传文件' }
]

const difficulties = [
  { value: 'easy', label: '简单' },
  { value: 'medium', label: '中等' },
  { value: 'hard', label: '困难' }
]

const maxQuestionChoices = [5, 10, 15, 20]

const step = ref(1)
const sourceRoute = ref('direct')
const inputMode = ref('text')
const materialText = ref('')
const filePath = ref('')
const fileName = ref('')
const fileSourceType = ref('txt')
const sourceTitle = ref('')
const questionType = ref('single_choice')
const difficulty = ref('medium')
const maxQuestions = ref(10)
const questionTypeOptions = ref([])
const optionsLoading = ref(false)
const optionsError = ref('')
const fieldError = reactive({ source: '', rules: '' })

const generating = ref(false)
const generateError = ref('')
const progressPercent = ref(12)
const progressText = ref('正在解析资料')
let progressTimer = null

const draft = ref({})
const questions = ref([])
const review = ref({ valid: false, issues: [], warnings: [] })
const reviewing = ref(false)
const importing = ref(false)
const importResult = ref(null)
const syncedFolderName = ref('')
const syncFolderError = ref('')
const editingIndex = ref(-1)
const editDraft = reactive({ stem: '', analysis: '', answerText: '' })

const knowledgeHint = ref('按课程与章节自动提取知识库内容，作为生成材料的第二条导入路径。')
const knowledgeAccounts = ref([])
const knowledgeBases = ref([])
const knowledgeDocuments = ref([])
const selectedAccountId = ref('')
const selectedKnowledgeId = ref('')
const selectedDocumentIds = ref([])
const selectedAccountLabel = computed(() => {
  const item = knowledgeAccounts.value.find((a) => String(a.id) === selectedAccountId.value)
  return item?.accountName || item?.name || '请选择'
})
const selectedKnowledgeLabel = computed(() => {
  const item = knowledgeBases.value.find((a) => String(a.id ?? a.knowledge_id) === selectedKnowledgeId.value)
  return item?.name || item?.knowledge_name || item?.title || '请选择'
})

const summaryInputMode = computed(() => {
  if (sourceRoute.value === 'knowledge') return '知识库章节'
  if (inputMode.value === 'text') return '粘贴文本'
  return fileName.value ? `上传 ${fileSourceType.value.toUpperCase()}` : '上传文件'
})

const difficultyLabel = computed(() => difficulties.find((d) => d.value === difficulty.value)?.label || '中等')

const timeline = computed(() => [
  { text: '材料解析：识别标题、正文和关键知识点。', active: progressPercent.value >= 20 },
  { text: `正在生成题目：根据「${typeLabel(questionType.value)} / ${difficultyLabel.value} / 最多 ${maxQuestions.value} 题」输出候选结果。`, active: progressPercent.value >= 45 && progressPercent.value < 92 },
  { text: '下一步将自动进行结构校验与可导入检查。', active: progressPercent.value >= 92 }
])

const bannerClass = computed(() => {
  if (review.value?.valid && !(review.value.issues || []).length) return 'banner--success'
  if ((review.value.issues || []).length) return 'banner--error'
  return 'banner--warn'
})

const bannerText = computed(() => {
  if (review.value?.valid && !(review.value.issues || []).length) return '已通过校验，可导入题库。'
  if ((review.value.issues || []).length) return '当前结果不可导入，请先修正。'
  return '可导入，但建议先检查提醒内容。'
})

const canImport = computed(() => (
  !importResult.value
  && !reviewing.value
  && Boolean(review.value?.valid)
  && !(review.value.issues || []).length
  && questions.value.length > 0
  && Boolean(draft.value?.proof)
))

const hasPreviewResult = computed(() => Boolean(draft.value?.proof) || questions.value.length > 0 || Boolean(importResult.value))

function goStep(target) {
  const next = Number(target)
  if (!next || next === step.value) return
  if (generating.value && next !== 2) {
    uni.showToast({ title: '生成进行中，请稍候', icon: 'none' })
    return
  }
  step.value = next
}

function leaveForLater() {
  uni.navigateBack({
    fail: () => {
      uni.switchTab({ url: '/pages/index/index' })
    }
  })
}

function typeLabel(type) {
  return TYPE_LABELS[type] || type || ''
}

function questionStem(q) {
  return q.stem || q.body?.statement || q.body?.text || ''
}

function formatAnswer(q) {
  const answer = q.answer || {}
  if (q.type === 'single_choice' || draft.value.questionType === 'single_choice') return answer.correctOption || '—'
  if (q.type === 'multiple_choice' || draft.value.questionType === 'multiple_choice') {
    return (answer.correctOptions || []).join('/') || '—'
  }
  if (q.type === 'true_false' || draft.value.questionType === 'true_false') {
    return answer.correct === true ? '正确' : (answer.correct === false ? '错误' : '—')
  }
  if (q.type === 'fill_blank' || draft.value.questionType === 'fill_blank') {
    return (answer.blanks || []).map((b) => (b.answers || []).join('/')).filter(Boolean).join('；') || '—'
  }
  return answer.referenceAnswer || '—'
}

function questionBadge(q, index) {
  const issues = review.value.issues || []
  const warnings = review.value.warnings || []
  const hitIssue = issues.some((text) => String(text).includes(String(index + 1)))
  const hitWarn = warnings.some((text) => String(text).includes(String(index + 1)))
  if (hitIssue) return '需修正'
  if (hitWarn) return '有提醒'
  return '可导入'
}

async function loadOptions() {
  optionsLoading.value = true
  optionsError.value = ''
  try {
    const res = await getQuestionGenerationOptions()
    const list = res?.data?.questionTypes || []
    questionTypeOptions.value = list
    const firstAvailable = list.find((item) => item.available)
    if (firstAvailable) {
      questionType.value = firstAvailable.type
      optionsError.value = ''
    } else if (list.length) {
      questionType.value = list[0].type
      optionsError.value = '题型智能体尚未配置或不可用，请先到 Web 端「智能体设置」完成题库生成映射后再试。'
    } else {
      optionsError.value = '未返回可用题型配置'
    }
  } catch (error) {
    optionsError.value = error?.msg || error?.message || '题型配置加载失败'
  } finally {
    optionsLoading.value = false
  }
}

function clearFileSelection() {
  filePath.value = ''
  fileName.value = ''
  fileSourceType.value = 'txt'
}

function switchInputMode(mode) {
  if (mode === inputMode.value) return
  if ((inputMode.value === 'text' && materialText.value) || filePath.value) {
    uni.showModal({
      title: '切换输入方式',
      content: '切换后将清空当前输入，是否继续？',
      success: (res) => {
        if (!res.confirm) return
        inputMode.value = mode
        materialText.value = ''
        clearFileSelection()
        fieldError.source = ''
      }
    })
    return
  }
  inputMode.value = mode
  fieldError.source = ''
}

function detectFileSourceType(name) {
  const lower = String(name || '').toLowerCase()
  if (lower.endsWith('.docx')) return 'docx'
  if (lower.endsWith('.txt')) return 'txt'
  return ''
}

function chooseFile() {
  const choose = uni.chooseMessageFile || uni.chooseFile
  if (!choose) {
    uni.showToast({ title: '当前端不支持文件选择', icon: 'none' })
    return
  }
  choose({
    count: 1,
    type: 'file',
    extension: ['txt', 'docx'],
    success: applyChosenFile
  })
}

function onDropFile(event) {
  const file = event?.dataTransfer?.files?.[0]
  if (!file) return
  const type = detectFileSourceType(file.name)
  if (!type) {
    uni.showToast({ title: '仅支持 TXT 或 DOCX', icon: 'none' })
    return
  }
  const url = typeof URL !== 'undefined' && URL.createObjectURL
    ? URL.createObjectURL(file)
    : ''
  if (!url) {
    uni.showToast({ title: '当前环境不支持拖拽上传', icon: 'none' })
    return
  }
  filePath.value = url
  fileName.value = file.name
  fileSourceType.value = type
  fieldError.source = ''
}

function applyChosenFile(res) {
  const file = (res.tempFiles || [])[0]
  const path = file?.path || res.tempFilePaths?.[0] || ''
  const name = file?.name || path.split(/[\\/]/).pop() || 'material.txt'
  if (!path) return
  const type = detectFileSourceType(name)
  if (!type) {
    uni.showToast({ title: '仅支持 TXT 或 DOCX', icon: 'none' })
    return
  }
  filePath.value = path
  fileName.value = name
  fileSourceType.value = type
  fieldError.source = ''
}

function selectQuestionType(item) {
  if (item.available === false) {
    uni.showToast({ title: item.unavailableReason || '该题型当前不可用', icon: 'none' })
    return
  }
  questionType.value = item.type
  fieldError.rules = ''
}

function onSelectKnowledgeRoute() {
  sourceRoute.value = 'knowledge'
  loadKnowledgeAccounts()
}

async function loadKnowledgeAccounts() {
  try {
    const res = await request({
      url: '/api/knowledge/maxkb/accounts',
      method: 'GET',
      data: { current: 1, size: 50, status: 1 },
      showError: false
    })
    const records = res?.data?.records || res?.data?.list || res?.data || []
    knowledgeAccounts.value = Array.isArray(records) ? records : []
    if (!knowledgeAccounts.value.length) {
      knowledgeHint.value = '当前没有可用知识库账号，请改用直接导入资料。'
    }
  } catch (error) {
    knowledgeAccounts.value = []
    knowledgeHint.value = '知识库暂不可用（可能需要管理员权限），请改用直接导入资料。'
  }
}

function pickKnowledgeAccount() {
  if (!knowledgeAccounts.value.length) {
    uni.showToast({ title: '暂无知识库账号', icon: 'none' })
    return
  }
  uni.showActionSheet({
    itemList: knowledgeAccounts.value.map((a) => a.accountName || a.name || String(a.id)),
    success: async (res) => {
      const account = knowledgeAccounts.value[res.tapIndex]
      selectedAccountId.value = String(account.id)
      selectedKnowledgeId.value = ''
      selectedDocumentIds.value = []
      knowledgeDocuments.value = []
      await loadKnowledgeBases(selectedAccountId.value)
    }
  })
}

async function loadKnowledgeBases(accountId) {
  try {
    const res = await request({
      url: `/api/knowledge/maxkb/accounts/${accountId}/knowledges`,
      method: 'GET',
      data: { page: 1, page_size: 100 },
      showError: false
    })
    const payload = res?.data
    const records = payload?.records || payload?.list || payload?.items || (Array.isArray(payload) ? payload : [])
    knowledgeBases.value = records
  } catch (error) {
    knowledgeBases.value = []
    uni.showToast({ title: '知识库列表加载失败', icon: 'none' })
  }
}

function pickKnowledgeBase() {
  if (!selectedAccountId.value) {
    uni.showToast({ title: '请先选择账号', icon: 'none' })
    return
  }
  if (!knowledgeBases.value.length) {
    uni.showToast({ title: '暂无课程知识库', icon: 'none' })
    return
  }
  uni.showActionSheet({
    itemList: knowledgeBases.value.map((k) => k.name || k.knowledge_name || k.title || String(k.id)),
    success: async (res) => {
      const item = knowledgeBases.value[res.tapIndex]
      selectedKnowledgeId.value = String(item.id ?? item.knowledge_id)
      selectedDocumentIds.value = []
      await loadDocuments()
    }
  })
}

async function loadDocuments() {
  try {
    const res = await request({
      url: `/api/knowledge/maxkb/accounts/${selectedAccountId.value}/knowledges/${selectedKnowledgeId.value}/documents`,
      method: 'GET',
      data: { page: 1, page_size: 200, task_type: 1 },
      showError: false
    })
    const payload = res?.data
    const records = payload?.records || payload?.list || payload?.items || (Array.isArray(payload) ? payload : [])
    knowledgeDocuments.value = records.map((doc) => ({
      ...doc,
      id: String(doc.id ?? doc.document_id)
    }))
  } catch (error) {
    knowledgeDocuments.value = []
  }
}

function toggleDocument(doc) {
  const id = String(doc.id)
  const set = new Set(selectedDocumentIds.value)
  if (set.has(id)) set.delete(id)
  else set.add(id)
  selectedDocumentIds.value = [...set]
  fieldError.source = ''
}

function validateBeforeGenerate() {
  fieldError.source = ''
  fieldError.rules = ''
  if (!questionType.value) {
    fieldError.rules = '请选择题型'
    return false
  }
  if (sourceRoute.value === 'direct') {
    if (inputMode.value === 'text' && !materialText.value.trim()) {
      fieldError.source = '请粘贴资料文本'
      return false
    }
    if (inputMode.value === 'file' && !filePath.value) {
      fieldError.source = '请先选择 TXT 或 DOCX 文件'
      return false
    }
  } else if (!selectedDocumentIds.value.length) {
    fieldError.source = '请至少选择一个知识库章节文档'
    return false
  }
  return true
}

async function buildKnowledgeText() {
  const sections = []
  for (const docId of selectedDocumentIds.value) {
    const doc = knowledgeDocuments.value.find((d) => String(d.id) === docId)
    const res = await request({
      url: `/api/knowledge/maxkb/accounts/${selectedAccountId.value}/knowledges/${selectedKnowledgeId.value}/documents/${docId}/paragraphs`,
      method: 'GET',
      data: { page: 1, page_size: 200 },
      showError: false
    })
    const payload = res?.data
    const records = payload?.records || payload?.list || payload?.items || (Array.isArray(payload) ? payload : [])
    const content = records.map((p) => p.content || p.text || '').filter(Boolean).join('\n\n')
    if (content) sections.push(`## ${doc?.name || doc?.title || docId}\n\n${content}`)
  }
  const title = selectedKnowledgeLabel.value || '知识库'
  return {
    text: [`# ${title}`, ...sections].join('\n\n').trim(),
    sourceTitle: sourceTitle.value.trim() || title
  }
}

function startProgressAnimation() {
  stopProgressAnimation()
  progressPercent.value = 12
  progressText.value = '正在解析资料'
  progressTimer = setInterval(() => {
    if (progressPercent.value < 35) {
      progressPercent.value += 3
      progressText.value = '正在解析资料'
    } else if (progressPercent.value < 78) {
      progressPercent.value += 2
      progressText.value = '正在调用题库生成智能体'
    } else if (progressPercent.value < 92) {
      progressPercent.value += 1
      progressText.value = '正在校验结果'
    }
  }, 400)
}

function stopProgressAnimation() {
  if (progressTimer) {
    clearInterval(progressTimer)
    progressTimer = null
  }
}

async function startGenerate() {
  if (!validateBeforeGenerate()) return
  if (optionsError.value) {
    uni.showToast({ title: optionsError.value, icon: 'none' })
    return
  }

  step.value = 2
  generating.value = true
  generateError.value = ''
  importResult.value = null
  syncedFolderName.value = ''
  syncFolderError.value = ''
  startProgressAnimation()

  try {
    let payload = {
      questionType: questionType.value,
      maxQuestions: maxQuestions.value,
      difficulty: difficulty.value,
      sourceTitle: sourceTitle.value.trim() || undefined
    }

    if (sourceRoute.value === 'knowledge') {
      const material = await buildKnowledgeText()
      if (!material.text) throw new Error('所选知识库文档没有可用于生成的文本')
      payload = {
        ...payload,
        sourceType: 'text',
        text: material.text,
        sourceTitle: material.sourceTitle
      }
    } else if (inputMode.value === 'text') {
      payload = {
        ...payload,
        sourceType: 'text',
        text: materialText.value.trim(),
        sourceTitle: payload.sourceTitle || '粘贴文本生成'
      }
    } else {
      payload = {
        ...payload,
        sourceType: fileSourceType.value || detectFileSourceType(fileName.value) || 'txt',
        filePath: filePath.value,
        fileName: fileName.value,
        sourceTitle: payload.sourceTitle || fileName.value
      }
    }

    const res = await generateQuestions(payload)
    const nextDraft = res?.data || {}
    draft.value = nextDraft
    questions.value = (nextDraft.questions || []).map((q, index) => ({
      ...q,
      displayNumber: index + 1
    }))
    review.value = {
      valid: nextDraft.valid,
      issues: nextDraft.issues || [],
      warnings: nextDraft.warnings || []
    }
    progressPercent.value = 100
    progressText.value = '生成完成'
    stopProgressAnimation()
    step.value = 3
  } catch (error) {
    stopProgressAnimation()
    generateError.value = error?.msg || error?.message || '题库生成失败'
    progressText.value = '生成失败'
  } finally {
    generating.value = false
  }
}

function continueGenerate() {
  importResult.value = null
  syncedFolderName.value = ''
  syncFolderError.value = ''
  draft.value = {}
  questions.value = []
  review.value = { valid: false, issues: [], warnings: [] }
  editingIndex.value = -1
  step.value = 1
}

function startEdit(index) {
  const q = questions.value[index]
  editingIndex.value = index
  editDraft.stem = questionStem(q)
  editDraft.analysis = q.analysis || ''
  editDraft.answerText = formatAnswer(q)
}

function cancelEdit() {
  editingIndex.value = -1
}

async function saveEdit() {
  const index = editingIndex.value
  if (index < 0) return
  const current = { ...questions.value[index] }
  current.stem = editDraft.stem
  current.analysis = editDraft.analysis
  const type = current.type || draft.value.questionType
  if (type === 'short_answer' || type === 'calculation' || type === 'programming') {
    current.answer = { ...(current.answer || {}), referenceAnswer: editDraft.answerText }
  } else if (type === 'single_choice') {
    current.answer = { ...(current.answer || {}), correctOption: editDraft.answerText.trim() }
  } else if (type === 'multiple_choice') {
    current.answer = {
      ...(current.answer || {}),
      correctOptions: editDraft.answerText.split(/[/、,，\s]+/).map((s) => s.trim()).filter(Boolean)
    }
  } else if (type === 'true_false') {
    const text = editDraft.answerText.trim()
    current.answer = {
      ...(current.answer || {}),
      correct: text === '正确' || text === 'true' || text === 'True'
    }
  }
  questions.value = questions.value.map((q, i) => (i === index ? current : q))
  editingIndex.value = -1
  await reReview()
}

async function reReview() {
  if (!draft.value?.proof) return
  reviewing.value = true
  try {
    const res = await reviewGeneratedQuestions({
      questions: questions.value,
      missingInfo: draft.value.missingInfo || [],
      sourceTitle: draft.value.sourceTitle,
      sourceScene: 'question_generation'
    }, draft.value.questionType)
    review.value = res?.data || { valid: false, issues: ['复审失败'], warnings: [] }
  } catch (error) {
    review.value = { valid: false, issues: ['复审请求失败'], warnings: [] }
  } finally {
    reviewing.value = false
  }
}

function confirmDelete(index) {
  uni.showModal({
    title: '删除题目',
    content: `确认删除第 ${index + 1} 题？`,
    success: async (res) => {
      if (!res.confirm) return
      questions.value = questions.value
        .filter((_, i) => i !== index)
        .map((q, i) => ({ ...q, displayNumber: i + 1 }))
      await reReview()
    }
  })
}

async function syncImportedToFolder(questionIds) {
  syncedFolderName.value = ''
  syncFolderError.value = ''
  const ids = (questionIds || []).filter((id) => id != null)
  if (!ids.length) {
    syncFolderError.value = '未返回题目 ID'
    return
  }
  const info = getUserInfo() || {}
  const isAdmin = String(info.role || '').toUpperCase() === 'ADMIN'
  const visibility = isAdmin ? 'PUBLIC' : 'PRIVATE'
  const folderName = '题库生成'
  try {
    const listRes = await listQuestionFolders({ visibility })
    const folders = listRes?.data || []
    let folder = folders.find((item) => item.name === folderName && item.ownedByCurrentUser)
    if (!folder) {
      const created = await createQuestionFolder({ name: folderName, visibility })
      folder = created?.data
    }
    if (!folder?.id) {
      syncFolderError.value = '无法创建收藏夹'
      return
    }
    for (const questionId of ids) {
      await addQuestionToFolder(folder.id, questionId)
    }
    syncedFolderName.value = folder.name || folderName
  } catch (error) {
    syncFolderError.value = error?.msg || error?.message || '请稍后在收藏夹中手动添加'
  }
}

function goMyQuestionBank() {
  const info = getUserInfo() || {}
  const isAdmin = String(info.role || '').toUpperCase() === 'ADMIN'
  const visibility = isAdmin ? 'PUBLIC' : 'PRIVATE'
  uni.navigateTo({
    url: `/subpackage_exam/myQuestionBank/myQuestionBank?visibility=${visibility}`
  })
}

async function handleImport() {
  if (!canImport.value || importing.value || importResult.value) return
  importing.value = true
  try {
    const res = await importGeneratedQuestions({
      proof: draft.value.proof,
      questions: questions.value,
      missingInfo: draft.value.missingInfo || []
    })
    importResult.value = res?.data || { importedCount: questions.value.length }
    await syncImportedToFolder(importResult.value.questionIds || [])
    uni.showToast({
      title: syncedFolderName.value ? '已导入并加入收藏夹' : '导入成功',
      icon: 'success'
    })
  } catch (error) {
    // toast already from request
  } finally {
    importing.value = false
  }
}

onMounted(() => {
  loadOptions()
})
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #f6f8fb;
  box-sizing: border-box;
}

.content {
  padding: 24rpx 28rpx 40rpx;
}

.content--footer {
  padding-bottom: 220rpx;
}

.stepper {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16rpx;
  margin-bottom: 24rpx;
}

.step {
  padding: 18rpx 8rpx;
  border-radius: 16rpx;
  border: 1px solid #d9e2ea;
  background: #ffffff;
  text-align: center;
  font-size: 22rpx;
  font-weight: 700;
  color: #6b7280;
}

.step--active {
  border-color: #8ba1b4;
  background: #e9eff4;
  color: #324354;
}

.step--done {
  border-color: #c8d5df;
  background: #f5f8fb;
  color: #516274;
}

.empty-card {
  margin-bottom: 20rpx;
  padding: 72rpx 36rpx 56rpx;
  border: 1px solid #e4ebf1;
  border-radius: 24rpx;
  background: #ffffff;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.empty-icon {
  width: 96rpx;
  height: 96rpx;
  margin-bottom: 28rpx;
  border-radius: 28rpx;
  background:
    linear-gradient(180deg, #e8eef3 0%, #d9e3ec 100%);
  position: relative;
}

.empty-icon::after {
  content: '';
  position: absolute;
  left: 28rpx;
  right: 28rpx;
  top: 34rpx;
  height: 10rpx;
  border-radius: 999rpx;
  background: #8ba1b4;
  box-shadow: 0 18rpx 0 #a8b8c7, 0 36rpx 0 #c2ced9;
}

.empty-icon--preview::after {
  left: 22rpx;
  right: 22rpx;
  top: 26rpx;
  height: 44rpx;
  border-radius: 12rpx;
  background: transparent;
  border: 4rpx solid #8ba1b4;
  box-shadow: none;
}

.empty-title {
  display: block;
  font-size: 30rpx;
  font-weight: 800;
  color: #18222f;
}

.empty-desc {
  display: block;
  margin-top: 14rpx;
  max-width: 520rpx;
  font-size: 24rpx;
  line-height: 1.6;
  color: #7b8794;
}

.empty-actions {
  margin-top: 32rpx;
  width: 100%;
  display: flex;
  gap: 16rpx;
}

.empty-btn {
  flex: 1;
  height: 76rpx;
}

.empty-inline {
  padding: 36rpx 20rpx;
  border-radius: 18rpx;
  background: #f6f8fb;
  text-align: center;
}

.empty-inline__title {
  display: block;
  font-size: 26rpx;
  font-weight: 700;
  color: #314253;
}

.empty-inline__desc {
  display: block;
  margin-top: 10rpx;
  font-size: 22rpx;
  color: #7b8794;
}

.card {
  margin-bottom: 20rpx;
  padding: 28rpx;
  border: 1px solid #e4ebf1;
  border-radius: 24rpx;
  background: #ffffff;
}

.card-title {
  display: block;
  margin-bottom: 20rpx;
  font-size: 28rpx;
  font-weight: 700;
  color: #18222f;
}

.field-block {
  margin-bottom: 28rpx;
}

.field-block--last {
  margin-bottom: 8rpx;
}

.field-label-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 12rpx;
}

.field-label {
  display: block;
  margin-bottom: 12rpx;
  font-size: 24rpx;
  font-weight: 700;
  color: #516274;
}

.field-label-row .field-label {
  margin-bottom: 0;
}

.field-hint {
  font-size: 20rpx;
  color: #91a0af;
}

.input--flush {
  margin-top: 0;
}

.type-list {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.type-row {
  min-height: 84rpx;
  padding: 0 22rpx;
  border: 1px solid #dfe7ee;
  border-radius: 18rpx;
  background: #f8fafc;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  box-sizing: border-box;
}

.type-row--active {
  border-color: #8ea4b8;
  background: #e8eef3;
}

.type-row--disabled {
  opacity: 0.72;
}

.type-row__main {
  display: flex;
  align-items: center;
  gap: 16rpx;
  min-width: 0;
  flex: 1;
}

.type-radio {
  width: 28rpx;
  height: 28rpx;
  border-radius: 50%;
  border: 2rpx solid #a8b6c4;
  box-sizing: border-box;
  flex-shrink: 0;
}

.type-radio--on {
  border-color: #5e7387;
  background: radial-gradient(circle at center, #5e7387 0 40%, transparent 42%);
}

.type-row__name {
  font-size: 26rpx;
  font-weight: 700;
  color: #233243;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.type-row__status {
  flex-shrink: 0;
  padding: 6rpx 14rpx;
  border-radius: 999rpx;
  font-size: 20rpx;
  font-weight: 700;
}

.type-row__status--on {
  background: #e6eef2;
  color: #3d5266;
}

.type-row__status--off {
  background: #eef1f4;
  color: #8a96a3;
}

.seg-equal {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12rpx;
}

.seg-equal--2 {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin-top: 8rpx;
  margin-bottom: 8rpx;
}

.seg-equal--4 {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.dropzone {
  margin-top: 16rpx;
  min-height: 240rpx;
  padding: 36rpx 28rpx;
  border: 2rpx dashed #c5d0db;
  border-radius: 20rpx;
  background: #f7f9fb;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
}

.dropzone--ready {
  border-color: #8ea4b8;
  background: #eef3f7;
}

.dropzone__icon {
  width: 72rpx;
  height: 64rpx;
  margin-bottom: 18rpx;
  display: flex;
  align-items: flex-end;
  justify-content: center;
}

.dropzone__tray {
  width: 56rpx;
  height: 40rpx;
  border: 4rpx solid #6b8cae;
  border-top: none;
  border-radius: 0 0 10rpx 10rpx;
  position: relative;
  box-sizing: border-box;
}

.dropzone__tray::before {
  content: '';
  position: absolute;
  left: 10rpx;
  right: 10rpx;
  top: -12rpx;
  height: 12rpx;
  border: 4rpx solid #6b8cae;
  border-bottom: none;
  border-radius: 8rpx 8rpx 0 0;
  box-sizing: border-box;
}

.dropzone__title {
  display: block;
  max-width: 100%;
  text-align: center;
  font-size: 26rpx;
  font-weight: 700;
  color: #3d4f63;
  word-break: break-all;
}

.dropzone__desc {
  display: block;
  margin-top: 12rpx;
  max-width: 100%;
  text-align: center;
  font-size: 22rpx;
  line-height: 1.55;
  color: #7b8794;
}

.dropzone__replace {
  display: block;
  margin-top: 14rpx;
  font-size: 22rpx;
  font-weight: 700;
  color: #5e7387;
}

.seg-equal__item {
  min-height: 72rpx;
  padding: 0 8rpx;
  border: 1px solid #d9e2ea;
  border-radius: 14rpx;
  background: #f8fafc;
  display: flex;
  align-items: center;
  justify-content: center;
  text-align: center;
  font-size: 24rpx;
  font-weight: 700;
  color: #617184;
  box-sizing: border-box;
}

.seg-equal__item--active {
  border-color: #8ea4b8;
  background: #e8eef3;
  color: #304152;
}

.field-tip {
  display: block;
  margin-top: 16rpx;
  padding: 16rpx 18rpx;
  border-radius: 14rpx;
  background: #f9f1dd;
  font-size: 22rpx;
  line-height: 1.55;
  color: #725a22;
}

.seg-row,
.chip-row {
  display: flex;
  flex-wrap: wrap;
  gap: 14rpx;
}

.seg,
.chip {
  padding: 14rpx 20rpx;
  border: 1px solid #d9e2ea;
  border-radius: 14rpx;
  background: #f8fafc;
  font-size: 22rpx;
  font-weight: 700;
  color: #617184;
}

.seg--active,
.chip--active {
  border-color: #8ea4b8;
  background: #e8eef3;
  color: #304152;
}

.chip--disabled {
  opacity: 0.45;
}

.route-box {
  margin-top: 20rpx;
  padding: 20rpx;
  border: 1px solid #e0e8ef;
  border-radius: 20rpx;
  background: #fbfcfd;
}

.route-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16rpx;
  font-size: 22rpx;
  font-weight: 700;
  color: #314253;
}

.route-tag {
  padding: 6rpx 12rpx;
  border-radius: 999rpx;
  background: #e8eef3;
  font-size: 20rpx;
  color: #607086;
}

.textarea,
.input,
.upload {
  width: 100%;
  margin-top: 16rpx;
  border: 1px solid #dfe7ee;
  border-radius: 18rpx;
  background: #f8fafc;
  color: #4b5d71;
  font-size: 24rpx;
  box-sizing: border-box;
}

.textarea {
  min-height: 220rpx;
  padding: 22rpx;
}

.textarea--edit {
  min-height: 140rpx;
}

.input {
  height: 80rpx;
  padding: 0 22rpx;
}

.upload {
  min-height: 88rpx;
  padding: 0 22rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.muted {
  display: block;
  margin-top: 12rpx;
  font-size: 22rpx;
  line-height: 1.5;
  color: #7b8794;
}

.field-error {
  display: block;
  margin-top: 12rpx;
  font-size: 22rpx;
  color: #b42318;
}

.selector {
  height: 80rpx;
  margin-top: 14rpx;
  padding: 0 20rpx;
  border: 1px solid #dfe7ee;
  border-radius: 16rpx;
  background: #f8fafc;
  display: flex;
  align-items: center;
  gap: 12rpx;
  font-size: 22rpx;
  color: #4b5d71;
}

.selector-value {
  flex: 1;
  text-align: right;
  color: #233243;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.arrow {
  color: #91a0af;
  font-weight: 700;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16rpx;
}

.summary-item {
  padding: 20rpx;
  border-radius: 16rpx;
  background: #f6f8fb;
}

.summary-label {
  display: block;
  font-size: 20rpx;
  color: #7b8794;
}

.summary-value {
  display: block;
  margin-top: 10rpx;
  font-size: 26rpx;
  font-weight: 700;
  color: #233243;
}

.progress-box {
  padding: 22rpx;
  border-radius: 20rpx;
  background: #f6f8fb;
}

.progress-top {
  display: flex;
  justify-content: space-between;
  margin-bottom: 16rpx;
  font-size: 24rpx;
  font-weight: 700;
  color: #334155;
}

.progress-bar {
  height: 12rpx;
  border-radius: 999rpx;
  background: #dfe7ee;
  overflow: hidden;
}

.progress-value {
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #8ba1b4 0%, #5d7387 100%);
  transition: width 0.3s ease;
}

.timeline {
  margin-top: 20rpx;
  display: grid;
  gap: 16rpx;
}

.timeline-item {
  display: flex;
  gap: 14rpx;
  align-items: flex-start;
  font-size: 22rpx;
  color: #536375;
}

.dot {
  width: 16rpx;
  height: 16rpx;
  margin-top: 8rpx;
  border-radius: 50%;
  background: #becbd7;
  flex-shrink: 0;
}

.dot--active {
  background: #5d7387;
  box-shadow: 0 0 0 8rpx rgba(141, 163, 181, 0.18);
}

.banner {
  padding: 20rpx 22rpx;
  border-radius: 18rpx;
  font-size: 24rpx;
  line-height: 1.5;
  font-weight: 700;
}

.banner--success {
  background: #e6eef2;
  color: #324354;
}

.banner--warn {
  margin-top: 14rpx;
  background: #f9f1dd;
  color: #725a22;
}

.banner--error {
  background: #fce4e4;
  color: #8a1f1f;
}

.issue-list {
  display: grid;
  gap: 12rpx;
}

.issue {
  padding: 18rpx 20rpx;
  border-radius: 16rpx;
  background: #f8fafc;
  font-size: 22rpx;
  color: #556476;
}

.question-card {
  margin-top: 20rpx;
  padding: 22rpx;
  border-radius: 20rpx;
  background: #f8fafc;
}

.question-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14rpx;
}

.question-no {
  font-size: 26rpx;
  font-weight: 700;
  color: #223244;
}

.badge {
  padding: 8rpx 14rpx;
  border-radius: 999rpx;
  background: #e8eef3;
  font-size: 20rpx;
  font-weight: 700;
  color: #516274;
}

.stem,
.option,
.answer {
  display: block;
  font-size: 22rpx;
  line-height: 1.65;
  color: #4b5d71;
}

.option {
  margin-top: 8rpx;
}

.answer {
  margin-top: 16rpx;
  padding-top: 16rpx;
  border-top: 1px solid #dfe7ee;
}

.ops {
  display: flex;
  gap: 16rpx;
  margin-top: 18rpx;
}

.ghost,
.primary,
.primary-mini {
  height: 76rpx;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26rpx;
  font-weight: 700;
}

.ghost {
  flex: 1;
  border: 1px solid #d6e0e8;
  background: #ffffff;
  color: #58687a;
}

.primary {
  flex: 1.4;
  background: #5e7387;
  color: #ffffff;
}

.primary-mini {
  flex: 1.4;
  background: #5e7387;
  color: #ffffff;
}

.primary--disabled {
  opacity: 0.45;
}

.footer-bar {
  position: fixed;
  left: 24rpx;
  right: 24rpx;
  bottom: calc(24rpx + env(safe-area-inset-bottom));
  padding: 20rpx;
  border: 1px solid #dbe4ec;
  border-radius: 28rpx;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 18rpx 34rpx rgba(103, 120, 142, 0.12);
  z-index: 20;
}

.tiny {
  display: block;
  margin-bottom: 12rpx;
  font-size: 20rpx;
  color: #7b8794;
  line-height: 1.5;
}
</style>
