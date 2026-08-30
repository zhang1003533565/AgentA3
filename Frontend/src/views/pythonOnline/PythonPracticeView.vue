<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppTabBar from '../../components/AppTabBar.vue'
import ChatMarkdown from '../../components/ChatMarkdown.vue'
import { getPythonProblemDetail, streamPythonAssist } from '../../api/pythonProblem'
import { usePythonProblemBank } from '../../composables/usePythonProblemBank'
import {
  CODE_DRAFT_PREFIX,
  DIFFICULTY_LABELS,
  PROGRESS_STORAGE_KEY,
  executeCodeOnServer,
  makeResult,
} from '../../utils/pythonOnlineIde'

const AI_QUICK_ACTIONS = [
  { key: 'hint', label: '给我一点提示' },
  { key: 'solution', label: '讲讲思路' },
  { key: 'explain', label: '解释我的代码' },
  { key: 'optimize', label: '优化代码' },
]

const AI_QUICK_LABELS = {
  hint: '给我一点提示',
  solution: '帮我讲讲思路',
  explain: '解释我的代码',
  debug: '帮我分析报错',
  optimize: '帮我优化代码',
}

const route = useRoute()
const router = useRouter()
const bank = usePythonProblemBank()

const code = ref('')
const problemId = ref(0)
const problem = ref(null)
const loadError = ref(false)
const judgeResult = ref(null)
const isSubmit = ref(false)
const resultPanelOpen = ref(false)
const isExecuting = ref(false)
const executingLabel = ref('')
const toastMessage = ref('')

const listOpen = ref(false)
const aiPanelVisible = ref(false)
const aiMessages = ref([])
const aiInput = ref('')
const aiStreaming = ref(false)
const aiScrollRef = ref(null)
const lastJudgeResult = ref(null)

const descParagraphs = computed(() => {
  if (!problem.value?.description) return []
  return problem.value.description.split('\n').filter((p) => p.trim() !== '')
})

const judgeable = computed(() =>
  !!(problem.value?.funcName && problem.value?.testcases?.length),
)

const resultTitle = computed(() => {
  const st = judgeResult.value?.status
  if (st === 'err') return isSubmit.value ? '提交失败' : '运行失败'
  if (st === 're') return '运行错误'
  if (st === 'ce') return '编译错误'
  if (st === 'tle') return '执行超时'
  if (st === 'ac') return isSubmit.value ? '提交成功' : '运行通过'
  if (st === 'unsupported') return '暂不支持在线判题'
  return isSubmit.value ? '提交未通过' : '解答错误'
})

const resultTone = computed(() => {
  const st = judgeResult.value?.status
  if (st === 'ac') return 'success'
  if (st === 'unsupported') return 'neutral'
  return 'error'
})

const prevId = computed(() => bank.findAdjacentId(problemId.value, -1))
const nextId = computed(() => bank.findAdjacentId(problemId.value, 1))

const aiDebugAvailable = computed(() => {
  const jr = lastJudgeResult.value
  return !!(jr?.status && jr.status !== 'ac' && jr.status !== 'err' && jr.status !== 'unsupported')
})

const commonActual = computed(() => {
  const tcs = judgeResult.value?.testcases || []
  const failed = tcs.filter((tc) => tc.status !== 'pass' && tc.actual).map((tc) => tc.actual)
  if (!failed.length) return null
  const first = failed[0]
  return failed.every((v) => v === first) ? first : null
})

const commonActualCount = computed(() =>
  (judgeResult.value?.testcases || []).filter((tc) => tc.status !== 'pass' && tc.actual).length,
)

function showToast(message) {
  toastMessage.value = message
  window.setTimeout(() => {
    if (toastMessage.value === message) toastMessage.value = ''
  }, 2200)
}

function difficultyLabel(d) {
  return DIFFICULTY_LABELS[d] || d
}

async function loadProblem() {
  loadError.value = false
  problem.value = null
  try {
    const res = await getPythonProblemDetail(problemId.value)
    if (!res?.data) {
      loadError.value = true
      return
    }
    problem.value = res.data
    const draft = localStorage.getItem(CODE_DRAFT_PREFIX + problemId.value)
    code.value = draft || problem.value.defaultCode || '# 你的代码\n'
    aiMessages.value = []
  } catch (e) {
    console.error('加载题目失败:', e)
    loadError.value = true
  }
}

function goToBank() {
  router.push('/learning')
}

function goToProblem(id) {
  if (!id || id === problemId.value) return
  router.push(`/learning/practice/${id}`)
}

function clearCode() {
  if (!code.value) return
  if (window.confirm('确定要清空当前代码吗？')) code.value = ''
}

function resetCode() {
  if (window.confirm('确定要恢复为默认模板代码吗？')) {
    code.value = problem.value?.defaultCode || '# 你的代码\n'
  }
}

function buildRunTestcases() {
  const fromExamples = (problem.value.examples || []).map((ex) => {
    if (!ex?.input?.length || !ex?.output?.length) return null
    return { input: ex.input.join(', '), expected: ex.output.join('\n') }
  }).filter(Boolean)
  if (fromExamples.length) return fromExamples
  return (problem.value.testcases || []).slice(0, 2)
}

function showResult(result) {
  judgeResult.value = result
  lastJudgeResult.value = result
  resultPanelOpen.value = true
}

function closeResultPanel() {
  resultPanelOpen.value = false
}

function retryAction() {
  if (isSubmit.value) submitCode()
  else runCode()
}

function markSolved(id) {
  if (!id) return
  let solved = []
  try {
    solved = JSON.parse(localStorage.getItem(PROGRESS_STORAGE_KEY) || '[]')
  } catch {
    solved = []
  }
  if (!solved.includes(id)) {
    solved.push(id)
    localStorage.setItem(PROGRESS_STORAGE_KEY, JSON.stringify(solved))
    const item = bank.questions.value.find((q) => q.id === id)
    if (item) item.done = true
  }
}

async function runCode() {
  if (isExecuting.value || !problem.value) return
  isSubmit.value = false
  if (!judgeable.value) {
    showResult(makeResult('unsupported', 0, 0, []))
    return
  }
  if (!code.value.trim()) {
    showToast('请先编写代码')
    return
  }
  isExecuting.value = true
  executingLabel.value = '运行中…'
  try {
    showResult(await executeCodeOnServer(code.value, problem.value, buildRunTestcases()))
  } catch (e) {
    console.error('运行出错:', e)
    showResult(makeResult('err', 0, 0, []))
  }
  isExecuting.value = false
  executingLabel.value = ''
}

async function submitCode() {
  if (isExecuting.value || !problem.value) return
  isSubmit.value = true
  if (!judgeable.value) {
    showResult(makeResult('unsupported', 0, 0, []))
    return
  }
  if (!code.value.trim()) {
    showToast('请先编写代码')
    return
  }
  isExecuting.value = true
  executingLabel.value = '提交中…'
  try {
    const result = await executeCodeOnServer(code.value, problem.value)
    if (result.status === 'ac') markSolved(problemId.value)
    showResult(result)
  } catch (e) {
    console.error('提交出错:', e)
    showResult(makeResult('err', 0, 0, []))
  }
  isExecuting.value = false
  executingLabel.value = ''
}

function toggleAiPanel() {
  aiPanelVisible.value = !aiPanelVisible.value
  if (aiPanelVisible.value) scrollAiToBottom()
}

function scrollAiToBottom() {
  nextTick(() => {
    const el = aiScrollRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

function buildAiProblemContext() {
  const p = problem.value || {}
  return {
    id: p.id,
    number: p.number,
    title: p.title,
    difficulty: p.difficulty,
    description: p.description,
    examples: p.examples,
    tags: p.tags,
    funcName: p.funcName,
  }
}

function buildAiDebugResult() {
  const jr = lastJudgeResult.value
  if (!jr) return null
  return {
    status: jr.status,
    statusLabel: jr.statusLabel,
    passed: jr.passed,
    total: jr.total,
    runtime: jr.runtime,
    memory: jr.memory,
    testcases: (jr.testcases || []).filter((tc) => tc.status !== 'pass').slice(0, 3),
  }
}

function aiAnalyzeLastResult() {
  if (!aiDebugAvailable.value) {
    showToast('没有可分析的判题结果')
    return
  }
  aiPanelVisible.value = true
  sendAiRequest('debug', null, null)
}

function sendAiFollowUp() {
  const text = aiInput.value.trim()
  if (!text) {
    showToast('请输入要追问的内容')
    return
  }
  aiInput.value = ''
  sendAiRequest('free', text, null)
}

async function sendAiRequest(questionType, followUp, options) {
  if (aiStreaming.value || !problem.value) return
  const userText = followUp || AI_QUICK_LABELS[questionType] || questionType
  const history = aiMessages.value.slice(-6).map((m) => ({ role: m.role, content: m.content }))
  const needJudge = questionType === 'debug' || questionType === 'optimize'
  pushAiMessage('user', userText)
  pushAiMessage('assistant', '')
  aiStreaming.value = true
  aiPanelVisible.value = true
  scrollAiToBottom()
  const assistantIndex = aiMessages.value.length - 1
  try {
    const stream = streamPythonAssist({
      questionType,
      problem: buildAiProblemContext(),
      userCode: options?.omitCode ? '' : code.value,
      judgeResult: needJudge ? buildAiDebugResult() : null,
      followUp: followUp || null,
      history,
    }, {
      onDelta(content) {
        aiMessages.value[assistantIndex].content += content || ''
        scrollAiToBottom()
      },
      onDone(payload) {
        if (payload?.answer && !aiMessages.value[assistantIndex].content) {
          aiMessages.value[assistantIndex].content = payload.answer
        }
        aiStreaming.value = false
        scrollAiToBottom()
      },
      onError(payload) {
        const detail = payload?.message || '请稍后重试'
        aiMessages.value[assistantIndex].content = `AI 服务暂时不可用：${detail}`
        aiStreaming.value = false
        scrollAiToBottom()
      },
    })
    await stream.done
  } catch (err) {
    console.error('AI 辅助请求失败:', err)
    aiMessages.value[assistantIndex].content = 'AI 服务调用失败，请稍后重试'
    aiStreaming.value = false
    scrollAiToBottom()
  }
}

function pushAiMessage(role, content) {
  aiMessages.value.push({ role, content: content || '' })
}

watch(code, (val) => {
  if (problemId.value) localStorage.setItem(CODE_DRAFT_PREFIX + problemId.value, val)
}, { flush: 'sync' })

watch(() => route.params.id, (id) => {
  problemId.value = Number(id) || 0
  resultPanelOpen.value = false
  loadProblem()
})

onMounted(async () => {
  problemId.value = Number(route.params.id) || 0
  await bank.loadProblems()
  await loadProblem()
})

onBeforeUnmount(() => {
  if (problemId.value && code.value) {
    localStorage.setItem(CODE_DRAFT_PREFIX + problemId.value, code.value)
  }
})
</script>

<template>
  <div class="py-workspace-page">
    <AppTabBar />

    <header class="py-workspace-toolbar">
      <div class="py-workspace-toolbar__left">
        <button class="py-toolbar-btn py-toolbar-btn--ghost" type="button" @click="goToBank">← 题库</button>
        <button
          class="py-toolbar-btn py-toolbar-btn--ghost"
          type="button"
          :class="{ 'py-toolbar-btn--active': listOpen }"
          @click="listOpen = !listOpen"
        >
          题目列表
        </button>
        <div v-if="problem" class="py-workspace-title">
          <span class="py-workspace-no">{{ problem.number }}.</span>
          <span class="py-workspace-name">{{ problem.title }}</span>
          <span class="py-diff" :class="'py-diff--' + problem.difficulty">{{ difficultyLabel(problem.difficulty) }}</span>
        </div>
      </div>
      <div class="py-workspace-toolbar__right">
        <button class="py-toolbar-btn py-toolbar-btn--ghost" type="button" :disabled="!prevId" @click="goToProblem(prevId)">上一题</button>
        <button class="py-toolbar-btn py-toolbar-btn--ghost" type="button" :disabled="!nextId" @click="goToProblem(nextId)">下一题</button>
        <button class="py-toolbar-btn py-toolbar-btn--ghost" type="button" :class="{ 'py-toolbar-btn--active': aiPanelVisible }" @click="toggleAiPanel">AI 助手</button>
        <button class="py-toolbar-btn" type="button" :disabled="isExecuting" @click="runCode">运行</button>
        <button class="py-toolbar-btn py-toolbar-btn--primary" type="button" :disabled="isExecuting" @click="submitCode">提交</button>
      </div>
    </header>

    <div v-if="executingLabel" class="py-workspace-loading">{{ executingLabel }}</div>
    <div v-if="toastMessage" class="py-workspace-toast">{{ toastMessage }}</div>

    <div v-if="!problem && !loadError" class="py-workspace-state">正在加载题目…</div>
    <div v-else-if="loadError" class="py-workspace-state">
      <p>题目加载失败</p>
      <button class="feature-button feature-button--primary" type="button" @click="loadProblem">重新加载</button>
    </div>

    <div
      v-else
      class="py-workspace-body"
      :class="{
        'py-workspace-body--with-list': listOpen,
        'py-workspace-body--with-ai': aiPanelVisible,
      }"
    >
      <aside v-if="listOpen" class="py-problem-list">
        <div class="py-problem-list__head">全部题目</div>
        <button
          v-for="q in bank.questions.value"
          :key="q.id"
          type="button"
          class="py-problem-list__item"
          :class="{ 'py-problem-list__item--active': q.id === problemId, 'py-problem-list__item--done': q.done }"
          @click="goToProblem(q.id)"
        >
          <span class="py-problem-list__status" />
          <span class="py-problem-list__no">{{ q.number }}</span>
          <span class="py-problem-list__title">{{ q.title }}</span>
        </button>
      </aside>

      <section class="py-desc-panel">
        <div class="py-desc-panel__meta">
          <span>通过率 {{ problem.passRate }}%</span>
          <span>提交 {{ problem.submissions }}</span>
        </div>
        <div class="py-desc-panel__body">
          <p v-for="(p, pi) in descParagraphs" :key="pi">{{ p }}</p>
          <div v-for="(ex, ei) in problem.examples" :key="ei" class="py-example">
            <strong>示例 {{ ei + 1 }}</strong>
            <pre v-for="(line, li) in ex.input" :key="'in' + li">输入：{{ line }}</pre>
            <pre v-for="(line, li) in ex.output" :key="'out' + li">输出：{{ line }}</pre>
            <p v-if="ex.explain" class="py-example__explain">{{ ex.explain }}</p>
          </div>
        </div>
        <div v-if="problem.tags?.length" class="py-desc-panel__tags">
          <span v-for="t in problem.tags" :key="t">{{ t }}</span>
        </div>
      </section>

      <section class="py-code-panel" :class="{ 'py-code-panel--with-ai': aiPanelVisible }">
        <div class="py-editor-wrap">
          <div class="py-editor-head">
            <span>Python 3</span>
            <div class="py-editor-actions">
              <button type="button" @click="clearCode">清空</button>
              <button type="button" @click="resetCode">重置模板</button>
            </div>
          </div>
          <textarea
            v-model="code"
            class="py-editor"
            spellcheck="false"
            placeholder="在此输入你的代码…"
          />
        </div>

        <div v-if="resultPanelOpen && judgeResult" class="py-result-panel" :class="'py-result-panel--' + resultTone">
          <div class="py-result-panel__head">
            <div>
              <strong>{{ resultTitle }}</strong>
              <span v-if="judgeResult.status !== 'unsupported' && judgeResult.status !== 'err'">
                {{ judgeResult.passed }}/{{ judgeResult.total }} 用例通过
              </span>
              <span v-if="judgeResult.status === 'ac'">
                · {{ judgeResult.runtime }}ms · {{ judgeResult.memory }}MB
              </span>
            </div>
            <div class="py-result-panel__actions">
              <button v-if="aiDebugAvailable" type="button" @click="aiAnalyzeLastResult">AI 分析</button>
              <button type="button" @click="retryAction">再次{{ isSubmit ? '提交' : '运行' }}</button>
              <button type="button" aria-label="关闭结果" @click="closeResultPanel">×</button>
            </div>
          </div>
          <div class="py-result-panel__body">
            <p v-if="judgeResult.status === 'unsupported'" class="py-result-note">
              该题涉及链表或类设计等结构，当前环境暂不支持自动判题，可在本地 Python 环境验证。
            </p>
            <p v-else-if="judgeResult.status === 'err'" class="py-result-note">网络异常或判题服务不可用，请稍后重试。</p>
            <div v-if="commonActual" class="py-tc py-tc--fail">
              <strong>实际输出（{{ commonActualCount }} 个失败用例相同）</strong>
              <pre>{{ commonActual }}</pre>
            </div>
            <div
              v-for="(tc, idx) in judgeResult.testcases"
              :key="idx"
              class="py-tc"
              :class="{ 'py-tc--fail': tc.status !== 'pass' }"
            >
              <div class="py-tc__head">
                <strong>用例 {{ idx + 1 }}</strong>
                <span>{{ tc.status === 'pass' ? '通过' : '失败' }}</span>
              </div>
              <pre>{{ tc.input }}</pre>
              <small>预期</small>
              <pre>{{ tc.expected }}</pre>
              <template v-if="tc.actual && tc.status !== 'pass' && !commonActual">
                <small>实际</small>
                <pre class="py-tc__actual">{{ tc.actual }}</pre>
              </template>
            </div>
          </div>
        </div>
      </section>

      <aside v-if="aiPanelVisible" class="py-ai-panel">
        <div class="py-ai-panel__head">
          <strong>AI 助手</strong>
          <button type="button" aria-label="关闭 AI 面板" @click="aiPanelVisible = false">×</button>
        </div>
        <div class="py-ai-quick">
          <button
            v-for="action in AI_QUICK_ACTIONS"
            :key="action.key"
            type="button"
            :disabled="aiStreaming"
            @click="sendAiRequest(action.key, null, null)"
          >
            {{ action.label }}
          </button>
        </div>
        <div ref="aiScrollRef" class="py-ai-messages">
          <p v-if="!aiMessages.length" class="py-ai-empty">可以让我给你提示、讲思路、解释代码或分析报错</p>
          <div
            v-for="(msg, idx) in aiMessages"
            :key="idx"
            class="py-ai-msg"
            :class="msg.role === 'user' ? 'py-ai-msg--user' : 'py-ai-msg--assistant'"
          >
            <ChatMarkdown
              v-if="msg.role === 'assistant' && msg.content"
              :content="msg.content"
              :streaming="aiStreaming && idx === aiMessages.length - 1"
            />
            <span v-else-if="msg.role === 'user'">{{ msg.content }}</span>
            <span v-else class="py-ai-thinking">思考中…</span>
          </div>
        </div>
        <div class="py-ai-input">
          <input
            v-model="aiInput"
            type="text"
            placeholder="追问…"
            :disabled="aiStreaming"
            @keydown.enter.prevent="sendAiFollowUp"
          />
          <button type="button" :disabled="aiStreaming" @click="sendAiFollowUp">
            {{ aiStreaming ? '回复中' : '发送' }}
          </button>
        </div>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.py-workspace-page {
  min-height: 100vh;
  padding-top: 60px;
  background: #eef1f5;
  color: #17233a;
}

.py-workspace-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  height: 52px;
  padding: 0 16px;
  border-bottom: 1px solid #dde3ea;
  background: #fff;
}

.py-workspace-toolbar__left,
.py-workspace-toolbar__right {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.py-workspace-toolbar__right {
  flex-shrink: 0;
}

.py-toolbar-btn {
  height: 34px;
  padding: 0 12px;
  border: 1px solid #d0d5dd;
  border-radius: 6px;
  color: #344054;
  background: #fff;
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
}

.py-toolbar-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.py-toolbar-btn--ghost {
  border-color: transparent;
  background: transparent;
}

.py-toolbar-btn--ghost:hover:not(:disabled),
.py-toolbar-btn--active {
  background: #f4f7fb;
}

.py-toolbar-btn--primary {
  border-color: #2f76bd;
  color: #fff;
  background: #2f76bd;
}

.py-workspace-title {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  margin-left: 4px;
}

.py-workspace-no {
  color: #667085;
  font-size: 13px;
  font-weight: 600;
}

.py-workspace-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
  font-weight: 700;
}

.py-diff {
  flex-shrink: 0;
  padding: 2px 7px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 700;
}

.py-diff--easy { color: #027a48; background: #ecfdf3; }
.py-diff--medium { color: #6941c6; background: #f4f3ff; }
.py-diff--hard { color: #b42318; background: #fef3f2; }

.py-workspace-body {
  display: grid;
  grid-template-columns: minmax(300px, 2fr) minmax(380px, 3fr);
  height: calc(100vh - 112px);
}

.py-workspace-body--with-list {
  grid-template-columns: 240px minmax(280px, 2fr) minmax(360px, 3fr);
}

.py-workspace-body--with-ai {
  grid-template-columns: minmax(300px, 2fr) minmax(360px, 3fr) 340px;
}

.py-workspace-body--with-list.py-workspace-body--with-ai {
  grid-template-columns: 240px minmax(260px, 2fr) minmax(340px, 3fr) 340px;
}

.py-problem-list {
  width: 240px;
  border-right: 1px solid #dde3ea;
  background: #fff;
  overflow-y: auto;
}

.py-problem-list__head {
  position: sticky;
  top: 0;
  padding: 12px 14px;
  border-bottom: 1px solid #eef2f6;
  color: #667085;
  background: #fafbfc;
  font-size: 12px;
  font-weight: 700;
}

.py-problem-list__item {
  display: grid;
  grid-template-columns: 14px 28px 1fr;
  gap: 8px;
  align-items: center;
  width: 100%;
  padding: 10px 12px;
  border: 0;
  border-bottom: 1px solid #f1f3f6;
  color: #344054;
  background: #fff;
  text-align: left;
  font-size: 13px;
}

.py-problem-list__item:hover {
  background: #f8fbff;
}

.py-problem-list__item--active {
  background: #eaf4fd;
}

.py-problem-list__status {
  width: 10px;
  height: 10px;
  border: 1.5px solid #d0d5dd;
  border-radius: 50%;
}

.py-problem-list__item--done .py-problem-list__status {
  border-color: #12b76a;
  background: #12b76a;
}

.py-problem-list__no {
  color: #98a2b3;
}

.py-problem-list__title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.py-desc-panel {
  overflow-y: auto;
  padding: 20px 22px;
  border-right: 1px solid #dde3ea;
  background: #fff;
}

.py-desc-panel__meta {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
  color: #667085;
  font-size: 13px;
}

.py-desc-panel__body p {
  margin: 0 0 12px;
  color: #344054;
  line-height: 1.75;
  font-size: 14px;
}

.py-example {
  margin: 16px 0;
  padding: 14px;
  border-left: 3px solid #2f76bd;
  border-radius: 0 8px 8px 0;
  background: #f8fafc;
}

.py-example strong {
  display: block;
  margin-bottom: 8px;
  font-size: 13px;
}

.py-example pre {
  margin: 0 0 6px;
  color: #1d2939;
  font-family: ui-monospace, 'Cascadia Code', Consolas, monospace;
  font-size: 13px;
  white-space: pre-wrap;
}

.py-example__explain {
  margin: 8px 0 0;
  color: #667085;
  font-size: 13px;
}

.py-desc-panel__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid #eef2f6;
}

.py-desc-panel__tags span {
  padding: 4px 10px;
  border-radius: 999px;
  color: #667085;
  background: #f2f4f7;
  font-size: 12px;
}

.py-code-panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: #1e1e1e;
}

.py-code-panel--with-ai {
  border-right: 1px solid #333;
}

.py-editor-wrap {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.py-editor-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 14px;
  border-bottom: 1px solid #333;
  color: #aaa;
  background: #252526;
  font-size: 12px;
}

.py-editor-actions {
  display: flex;
  gap: 8px;
}

.py-editor-actions button {
  padding: 4px 8px;
  border-radius: 4px;
  color: #ccc;
  background: #333;
  font-size: 12px;
}

.py-editor {
  flex: 1;
  width: 100%;
  min-height: 200px;
  padding: 16px;
  border: 0;
  outline: none;
  resize: none;
  color: #d4d4d4;
  background: #1e1e1e;
  font-family: ui-monospace, 'Cascadia Code', Consolas, monospace;
  font-size: 14px;
  line-height: 1.6;
  tab-size: 4;
}

.py-result-panel {
  max-height: 42%;
  border-top: 1px solid #333;
  background: #252526;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.py-result-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  border-bottom: 1px solid #333;
  color: #ddd;
  font-size: 13px;
}

.py-result-panel__head strong {
  margin-right: 8px;
}

.py-result-panel__head span {
  color: #aaa;
}

.py-result-panel__actions {
  display: flex;
  gap: 6px;
}

.py-result-panel__actions button {
  padding: 4px 10px;
  border-radius: 4px;
  color: #ddd;
  background: #333;
  font-size: 12px;
}

.py-result-panel--success .py-result-panel__head strong { color: #6ee7a0; }
.py-result-panel--error .py-result-panel__head strong { color: #fca5a5; }

.py-result-panel__body {
  overflow-y: auto;
  padding: 12px 14px;
}

.py-result-note {
  margin: 0 0 10px;
  color: #aaa;
  font-size: 13px;
  line-height: 1.5;
}

.py-tc {
  margin-bottom: 10px;
  padding: 10px;
  border-radius: 6px;
  background: #1e1e1e;
}

.py-tc--fail {
  border: 1px solid #7f1d1d;
}

.py-tc__head {
  display: flex;
  justify-content: space-between;
  margin-bottom: 6px;
  color: #ccc;
  font-size: 12px;
}

.py-tc pre {
  margin: 0 0 6px;
  color: #d4d4d4;
  font-family: ui-monospace, Consolas, monospace;
  font-size: 12px;
  white-space: pre-wrap;
}

.py-tc small {
  display: block;
  margin-bottom: 4px;
  color: #888;
  font-size: 11px;
}

.py-tc__actual {
  color: #fca5a5;
}

.py-ai-panel {
  display: flex;
  flex-direction: column;
  width: 340px;
  border-left: 1px solid #dde3ea;
  background: #fff;
}

.py-ai-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  border-bottom: 1px solid #eef2f6;
}

.py-ai-panel__head button {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  color: #667085;
  background: #f2f4f7;
  font-size: 18px;
}

.py-ai-quick {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 10px 12px;
  border-bottom: 1px solid #eef2f6;
}

.py-ai-quick button {
  padding: 6px 10px;
  border: 1px solid #e1e7ed;
  border-radius: 999px;
  color: #344054;
  background: #fff;
  font-size: 12px;
}

.py-ai-messages {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

.py-ai-empty {
  margin: 0;
  color: #98a2b3;
  font-size: 13px;
  line-height: 1.6;
}

.py-ai-msg {
  margin-bottom: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  font-size: 13px;
  line-height: 1.6;
}

.py-ai-msg--user {
  margin-left: 24px;
  color: #17233a;
  background: #eaf4fd;
}

.py-ai-msg--assistant {
  margin-right: 12px;
  color: #344054;
  background: #f8fafc;
}

.py-ai-thinking {
  color: #98a2b3;
}

.py-ai-input {
  display: flex;
  gap: 8px;
  padding: 12px;
  border-top: 1px solid #eef2f6;
}

.py-ai-input input {
  flex: 1;
  height: 36px;
  padding: 0 12px;
  border: 1px solid #e1e7ed;
  border-radius: 8px;
  outline: none;
}

.py-ai-input button {
  height: 36px;
  padding: 0 14px;
  border-radius: 8px;
  color: #fff;
  background: #2f76bd;
  font-size: 13px;
  font-weight: 600;
}

.py-workspace-loading {
  position: fixed;
  inset: 0;
  z-index: 200;
  display: grid;
  place-items: center;
  background: rgba(15, 23, 42, 0.25);
  color: #fff;
  font-size: 14px;
}

.py-workspace-toast {
  position: fixed;
  top: 76px;
  left: 50%;
  z-index: 150;
  transform: translateX(-50%);
  padding: 10px 16px;
  border-radius: 8px;
  color: #fff;
  background: rgba(15, 23, 42, 0.85);
  font-size: 13px;
}

.py-workspace-state {
  padding: 80px 24px;
  text-align: center;
  color: #667085;
}

@media (max-width: 1100px) {
  .py-workspace-name {
    max-width: 120px;
  }
}

@media (max-width: 900px) {
  .py-workspace-toolbar {
    flex-wrap: wrap;
    height: auto;
    padding: 10px 12px;
  }

  .py-workspace-body {
    grid-template-columns: 1fr;
    height: auto;
    min-height: calc(100vh - 140px);
  }

  .py-problem-list {
    width: 100%;
    max-height: 220px;
  }

  .py-desc-panel {
    max-height: 360px;
    border-right: 0;
    border-bottom: 1px solid #dde3ea;
  }

  .py-code-panel {
    min-height: 420px;
  }

  .py-ai-panel {
    width: 100%;
    min-height: 320px;
    border-left: 0;
    border-top: 1px solid #dde3ea;
  }
}
</style>
