<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ChatMarkdown from '../../components/ChatMarkdown.vue'
import { getPythonProblemDetail, streamPythonAssist } from '../../api/pythonProblem'
import {
  CODE_DRAFT_PREFIX,
  DIFFICULTY_LABELS,
  PROGRESS_STORAGE_KEY,
  executeCodeOnServer,
  makeResult,
} from '../../utils/pythonOnlineIde'

const AI_QUICK_LABELS = {
  hint: '给我一点提示',
  solution: '帮我讲讲思路',
  explain: '解释我的代码',
  debug: '帮我分析报错',
  optimize: '帮我优化代码',
}

const route = useRoute()
const router = useRouter()

const activeTab = ref('code')
const code = ref('')
const problemId = ref(0)
const problem = ref(null)
const loadError = ref(false)
const judgeResult = ref(null)
const isSubmit = ref(false)
const showResultModal = ref(false)
const isExecuting = ref(false)
const executingLabel = ref('')
const toastMessage = ref('')

const aiPanelVisible = ref(false)
const aiMessages = ref([])
const aiInput = ref('')
const aiStreaming = ref(false)
const aiScrollRef = ref(null)
const lastJudgeResult = ref(null)

const descParagraphs = computed(() => {
  if (!problem.value || !problem.value.description) return []
  return problem.value.description.split('\n').filter((p) => p.trim() !== '')
})

const judgeable = computed(() =>
  !!(problem.value && problem.value.funcName && problem.value.testcases && problem.value.testcases.length > 0),
)

const resultTitle = computed(() => {
  const st = judgeResult.value && judgeResult.value.status
  if (st === 'err') return isSubmit.value ? '提交失败' : '运行失败'
  if (st === 're') return '运行错误'
  if (st === 'ce') return '编译错误'
  if (st === 'tle') return '执行超时'
  return isSubmit.value ? '提交未通过' : '解答错误'
})

const aiDebugAvailable = computed(() => {
  const jr = lastJudgeResult.value
  return !!(jr && jr.status && jr.status !== 'ac' && jr.status !== 'err' && jr.status !== 'unsupported')
})

const commonActual = computed(() => {
  const tcs = (judgeResult.value && judgeResult.value.testcases) || []
  const failed = []
  for (let i = 0; i < tcs.length; i++) {
    if (tcs[i].status !== 'pass' && tcs[i].actual) failed.push(tcs[i].actual)
  }
  if (!failed.length) return null
  const first = failed[0]
  for (let j = 1; j < failed.length; j++) {
    if (failed[j] !== first) return null
  }
  return first
})

const commonActualCount = computed(() => {
  const tcs = (judgeResult.value && judgeResult.value.testcases) || []
  let count = 0
  for (let i = 0; i < tcs.length; i++) {
    if (tcs[i].status !== 'pass' && tcs[i].actual) count++
  }
  return count
})

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
  try {
    const res = await getPythonProblemDetail(problemId.value)
    if (!res || !res.data) {
      loadError.value = true
      return
    }
    problem.value = res.data
    const draft = localStorage.getItem(CODE_DRAFT_PREFIX + problemId.value)
    code.value = draft || problem.value.defaultCode || '# 你的代码\n'
  } catch (e) {
    console.error('加载题目失败:', e)
    loadError.value = true
  }
}

function goBack() {
  router.back()
}

function switchTab(tab) {
  activeTab.value = tab
}

function clearCode() {
  if (!code.value) return
  if (window.confirm('确定要清空当前代码吗？此操作不可恢复。')) {
    code.value = ''
  }
}

function resetCode() {
  if (window.confirm('确定要恢复为默认模板代码吗？当前修改将丢失。')) {
    code.value = problem.value?.defaultCode || '# 你的代码\n'
  }
}

function buildRunTestcases() {
  const fromExamples = (problem.value.examples || []).map((ex) => {
    if (!ex || !ex.input || !ex.input.length || !ex.output || !ex.output.length) return null
    return { input: ex.input.join(', '), expected: ex.output.join('\n') }
  }).filter(Boolean)
  if (fromExamples.length > 0) return fromExamples
  return (problem.value.testcases || []).slice(0, 2)
}

function showResult(result) {
  judgeResult.value = result
  lastJudgeResult.value = result
  showResultModal.value = true
}

function closeResultModal() {
  showResultModal.value = false
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
  executingLabel.value = '执行中...'

  try {
    const result = await executeCodeOnServer(code.value, problem.value, buildRunTestcases())
    showResult(result)
  } catch (e) {
    console.error('运行出错:', e)
    showResult(makeResult('err', 0, 0, []))
  }
  isExecuting.value = false
  executingLabel.value = ''
}

async function submitCode() {
  if (isExecuting.value || !problem.value) return
  closeResultModal()
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
  executingLabel.value = '提交中...'

  try {
    const result = await executeCodeOnServer(code.value, problem.value)
    if (result.status === 'ac') {
      markSolved(problemId.value)
    }
    showResult(result)
  } catch (e) {
    console.error('提交出错:', e)
    showResult(makeResult('err', 0, 0, []))
  }
  isExecuting.value = false
  executingLabel.value = ''
}

function openAiPanel() {
  aiPanelVisible.value = true
  scrollAiToBottom()
}

function closeAiPanel() {
  aiPanelVisible.value = false
}

function pushAiMessage(role, content) {
  aiMessages.value.push({ role, content: content || '' })
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
  const failed = (jr.testcases || []).filter((tc) => tc.status !== 'pass').slice(0, 3)
  return {
    status: jr.status,
    statusLabel: jr.statusLabel,
    passed: jr.passed,
    total: jr.total,
    runtime: jr.runtime,
    memory: jr.memory,
    testcases: failed,
  }
}

function aiAnalyzeLastResult() {
  if (!aiDebugAvailable.value) {
    showToast('没有可分析的判题结果')
    return
  }
  closeResultModal()
  openAiPanel()
  sendAiRequest('debug', null, null)
}

function sendAiFollowUp() {
  const text = (aiInput.value || '').trim()
  if (!text) {
    showToast('请输入要追问的内容')
    return
  }
  aiInput.value = ''
  sendAiRequest('free', text, null)
}

async function sendAiRequest(questionType, followUp, options) {
  if (aiStreaming.value || !problem.value) return
  const userText = followUp || AI_QUICK_LABELS[questionType] || (options?.omitCode ? '解释一下题目' : questionType)
  const history = aiMessages.value.slice(-6).map((m) => ({ role: m.role, content: m.content }))
  const needJudge = questionType === 'debug' || questionType === 'optimize'
  const data = {
    questionType,
    problem: buildAiProblemContext(),
    userCode: options?.omitCode ? '' : code.value,
    judgeResult: needJudge ? buildAiDebugResult() : null,
    followUp: followUp || null,
    history,
  }
  pushAiMessage('user', userText)
  pushAiMessage('assistant', '')
  aiStreaming.value = true
  scrollAiToBottom()

  const assistantIndex = aiMessages.value.length - 1
  try {
    const stream = streamPythonAssist(data, {
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
        const detail = payload?.message ? payload.message : '请稍后重试'
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

watch(code, (val) => {
  if (problemId.value) {
    localStorage.setItem(CODE_DRAFT_PREFIX + problemId.value, val)
  }
}, { flush: 'sync' })

onMounted(() => {
  problemId.value = Number(route.params.id) || 0
  loadProblem()
})

onBeforeUnmount(() => {
  if (problemId.value && code.value) {
    localStorage.setItem(CODE_DRAFT_PREFIX + problemId.value, code.value)
  }
})
</script>

<template>
  <div class="practice-page">
    <div class="top-bar">
      <div class="back-btn" @click="goBack">
        <div class="back-arrow" />
      </div>
      <span class="top-bar-title">Python 在线编程</span>
      <div class="top-bar-right">
        <div class="tab-btn" :class="{ 'tab-btn--active': activeTab === 'detail' }" @click="switchTab('detail')">详情</div>
        <div class="tab-btn" :class="{ 'tab-btn--active': activeTab === 'code' }" @click="switchTab('code')">代码</div>
      </div>
    </div>

    <div v-if="executingLabel" class="loading-overlay">
      <div class="loading-box">{{ executingLabel }}</div>
    </div>

    <div v-if="toastMessage" class="toast">{{ toastMessage }}</div>

    <div v-if="!problem && !loadError" class="load-state">
      <span class="load-state-text">加载中...</span>
    </div>
    <div v-else-if="loadError" class="load-state">
      <span class="load-state-text">题目加载失败</span>
      <span class="load-state-hint">请检查网络连接后重试</span>
      <div class="retry-btn" @click="loadProblem">
        <span class="retry-btn-text">重新加载</span>
      </div>
    </div>

    <div v-if="activeTab === 'detail' && problem" class="content-area">
      <div class="problem-card">
        <div class="problem-header">
          <span class="problem-id">{{ problem.number }}.</span>
          <span class="problem-name">{{ problem.title }}</span>
          <div class="difficulty-tag" :class="'difficulty-tag--' + problem.difficulty">{{ difficultyLabel(problem.difficulty) }}</div>
        </div>
        <div class="problem-stats">
          <span class="stat">通过率 {{ problem.passRate }}%</span>
          <span class="stat">提交数 {{ problem.submissions }}</span>
        </div>
      </div>

      <div class="problem-description">
        <span v-for="(p, pi) in descParagraphs" :key="pi" class="desc-text">{{ p }}</span>
        <div v-for="(ex, ei) in problem.examples" :key="ei" class="example">
          <span class="example-label">示例 {{ ei + 1 }}：</span>
          <span v-for="(line, li) in ex.input" :key="'in' + li" class="example-code">输入：{{ line }}</span>
          <span v-for="(line, li) in ex.output" :key="'out' + li" class="example-code">输出：{{ line }}</span>
          <span v-if="ex.explain" class="example-explain">{{ ex.explain }}</span>
        </div>
      </div>

      <div class="tags">
        <span class="tag-label">相关标签</span>
        <div class="tag-list">
          <div v-for="t in problem.tags" :key="t" class="tag-item">{{ t }}</div>
        </div>
      </div>
    </div>

    <div v-else-if="activeTab === 'code' && problem" class="content-area code-area">
      <div class="editor-container">
        <div class="editor-header">
          <span class="editor-title">Python 3</span>
          <div class="editor-actions">
            <div class="action-btn" @click="clearCode">
              <div class="clear-icon" />
              <span class="action-text">清空</span>
            </div>
            <div class="action-btn" @click="resetCode">
              <div class="reset-icon" />
              <span class="action-text">重置</span>
            </div>
          </div>
        </div>
        <textarea
          v-model="code"
          class="code-editor"
          placeholder="在此输入你的代码..."
          spellcheck="false"
        />
      </div>

      <div class="run-actions">
        <button class="run-btn" type="button" :disabled="isExecuting" @click="runCode">
          <div class="run-icon-css" />
          <span class="run-text">{{ isExecuting ? '执行中...' : '运行' }}</span>
        </button>
        <button class="submit-btn" type="button" :disabled="isExecuting" @click="submitCode">
          <div class="submit-icon-css" />
          <span class="submit-text">{{ isExecuting ? '执行中...' : '提交' }}</span>
        </button>
      </div>
    </div>

    <div v-if="showResultModal" class="result-modal-overlay">
      <div class="result-modal-fullscreen" @click.stop>
        <div class="modal-back-btn" @click="closeResultModal">
          <div class="modal-back-arrow" />
        </div>

        <div v-if="judgeResult && judgeResult.status === 'unsupported'" class="result-full-scroll">
          <div class="result-error-header">
            <div class="error-circle">!</div>
            <span class="error-title">暂不支持在线判题</span>
            <span class="error-desc">{{ judgeResult.statusLabel }}</span>
          </div>
          <div class="testcase-section">
            <div class="testcase-card">
              <span class="tc-label">「{{ problem.title }}」涉及链表或类设计等数据结构，当前在线判题环境暂不支持自动判定。</span>
              <span class="tc-label tc-label--block">你仍然可以在编辑器中编写代码，并在本地 Python 环境中自行验证。</span>
            </div>
          </div>
          <div class="modal-actions">
            <button class="btn-secondary" type="button" @click="closeResultModal">返回</button>
          </div>
        </div>

        <div v-else-if="judgeResult && judgeResult.status === 'ac'" class="result-full-scroll">
          <div class="result-success-header">
            <div class="success-checkmark">✓</div>
            <span class="success-title">{{ isSubmit ? '提交成功' : '运行通过' }}</span>
            <span class="success-subtitle">{{ judgeResult.passed }}/{{ judgeResult.total }} {{ isSubmit ? '个用例通过' : '个示例用例通过' }}</span>
          </div>
          <div class="result-metrics">
            <div class="metric-box">
              <span class="metric-label">执行用时</span>
              <span class="metric-value">{{ judgeResult.runtime }}ms</span>
            </div>
            <div class="metric-box">
              <span class="metric-label">内存消耗</span>
              <span class="metric-value">{{ judgeResult.memory }}MB</span>
            </div>
          </div>
          <span class="details-title">详情</span>
          <div class="testcase-section">
            <div v-for="(tc, idx) in judgeResult.testcases" :key="idx" class="testcase-card">
              <div class="tc-header"><span class="tc-label">输入</span></div>
              <div class="tc-code-block">{{ tc.input }}</div>
              <div class="tc-header tc-header--spaced">
                <span class="tc-label">输出</span>
                <span class="tc-pass-badge">✓</span>
              </div>
              <div class="tc-code-block">{{ tc.actual || tc.expected }}</div>
              <div class="tc-header tc-header--spaced">
                <span class="tc-label">预期结果</span>
              </div>
              <div class="tc-code-block">{{ tc.expected }}</div>
            </div>
          </div>
          <div class="modal-actions">
            <button class="btn-secondary" type="button" @click="closeResultModal">{{ isSubmit ? '继续刷题' : '修改代码' }}</button>
            <button class="btn-primary" type="button" @click="retryAction">{{ isSubmit ? '再次提交' : '再次运行' }}</button>
          </div>
        </div>

        <div v-else-if="judgeResult" class="result-full-scroll">
          <div class="result-error-header">
            <div class="error-circle">!</div>
            <span class="error-title">{{ resultTitle }}</span>
            <span v-if="judgeResult.status === 'err'" class="error-desc">网络异常或判题服务不可用，请稍后重试</span>
            <span v-else-if="judgeResult.status === 'ce'" class="error-desc">代码存在语法错误，请查看下方用例中的报错信息</span>
            <span v-else-if="judgeResult.status === 're'" class="error-desc">代码执行时抛出异常，请查看下方用例中的报错信息</span>
            <span v-else-if="judgeResult.status === 'tle'" class="error-desc">代码执行超时（单用例限时 10 秒），请检查是否存在死循环或复杂度过高</span>
            <span v-else class="error-desc">{{ judgeResult.passed }}/{{ judgeResult.total }} {{ isSubmit ? '个用例通过' : '个示例用例通过' }}</span>
          </div>
          <div v-if="commonActual" class="testcase-section">
            <div class="testcase-card testcase-card--fail">
              <div class="tc-header">
                <span class="tc-label">实际输出（{{ commonActualCount }} 个失败用例相同）</span>
                <span class="tc-fail-badge">✗</span>
              </div>
              <div class="tc-code-block tc-code-error">{{ commonActual }}</div>
            </div>
          </div>
          <div v-if="judgeResult.testcases && judgeResult.testcases.length > 0" class="testcase-section">
            <div
              v-for="(tc, idx) in judgeResult.testcases"
              :key="idx"
              class="testcase-card"
              :class="{ 'testcase-card--fail': tc.status !== 'pass' }"
            >
              <div class="tc-header">
                <span class="tc-label">用例 {{ idx + 1 }}</span>
                <span v-if="tc.status === 'pass'" class="tc-pass-badge">✓</span>
                <span v-else class="tc-fail-badge">✗</span>
              </div>
              <div class="tc-code-block">{{ tc.input }}</div>
              <div class="tc-header tc-header--spaced">
                <span class="tc-label">预期输出</span>
              </div>
              <div class="tc-code-block">{{ tc.expected }}</div>
              <template v-if="tc.actual && !commonActual">
                <div class="tc-header tc-header--spaced">
                  <span class="tc-label">实际输出</span>
                </div>
                <div class="tc-code-block tc-code-error">{{ tc.actual }}</div>
              </template>
            </div>
          </div>
          <div v-if="judgeResult.status !== 'unsupported'" class="modal-actions">
            <button class="btn-ai" type="button" @click="aiAnalyzeLastResult">AI 分析</button>
            <button class="btn-secondary" type="button" @click="closeResultModal">修改代码</button>
            <button class="btn-primary" type="button" @click="retryAction">{{ isSubmit ? '再次提交' : '再次运行' }}</button>
          </div>
        </div>
      </div>
    </div>

    <div v-if="activeTab === 'code' && problem" class="ai-float-btn" @click="openAiPanel">
      <span class="ai-float-btn-text">AI</span>
    </div>

    <div v-if="aiPanelVisible" class="ai-panel-overlay" @click="closeAiPanel">
      <div class="ai-panel" @click.stop>
        <div class="ai-panel-header">
          <span class="ai-panel-title">AI 助手</span>
          <div class="ai-panel-close" @click="closeAiPanel">
            <span class="ai-panel-close-x">×</span>
          </div>
        </div>
        <div v-if="problem" class="ai-context-tip">
          <span class="ai-context-tip-text">{{ problem.number }}. {{ problem.title }} · {{ difficultyLabel(problem.difficulty) }}</span>
        </div>
        <div ref="aiScrollRef" class="ai-messages">
          <div v-if="!aiMessages.length" class="ai-empty">
            <span class="ai-empty-text">可以让我给你提示、讲思路、解释代码或分析报错</span>
          </div>
          <div
            v-for="(msg, idx) in aiMessages"
            :key="idx"
            class="ai-msg-row"
            :class="msg.role === 'user' ? 'ai-msg-row--user' : 'ai-msg-row--assistant'"
          >
            <div class="ai-bubble" :class="msg.role === 'user' ? 'ai-bubble--user' : 'ai-bubble--assistant'">
              <ChatMarkdown
                v-if="msg.role === 'assistant' && msg.content"
                :content="msg.content"
                :streaming="aiStreaming && idx === aiMessages.length - 1"
              />
              <span v-else-if="msg.role === 'user'">{{ msg.content }}</span>
              <span v-else-if="msg.role === 'assistant' && !msg.content" class="ai-thinking">思考中...</span>
            </div>
          </div>
        </div>
        <div class="ai-input-row">
          <input
            v-model="aiInput"
            class="ai-input"
            placeholder="追问...（如：再给一点提示）"
            :disabled="aiStreaming"
            @keydown.enter.prevent="sendAiFollowUp"
          />
          <div class="ai-send-btn" :class="{ 'ai-send-btn--disabled': aiStreaming }" @click="sendAiFollowUp">
            <span class="ai-send-text">{{ aiStreaming ? '回复中' : '发送' }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.practice-page {
  min-height: 100vh;
  background: #f5f6fa;
  color: #1a1a1a;
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', sans-serif;
}

.loading-overlay {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.25);
}

.loading-box {
  padding: 14px 24px;
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.75);
  color: #fff;
  font-size: 14px;
}

.toast {
  position: fixed;
  top: 72px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 1500;
  padding: 10px 18px;
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.78);
  color: #fff;
  font-size: 13px;
}

.top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  background: #fff;
  border-bottom: 1px solid #eee;
  position: sticky;
  top: 0;
  z-index: 100;
}

.back-btn {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.back-arrow {
  width: 8px;
  height: 8px;
  border-left: 1.5px solid #1a1a1a;
  border-bottom: 1.5px solid #1a1a1a;
  transform: rotate(45deg);
}

.top-bar-title {
  font-size: 16px;
  font-weight: 600;
  color: #1a1a1a;
}

.top-bar-right {
  display: flex;
  gap: 8px;
}

.tab-btn {
  padding: 6px 12px;
  font-size: 13px;
  color: #666;
  border-radius: 4px;
  cursor: pointer;
}

.tab-btn--active {
  background: #f0f0ff;
  color: #4f46e5;
}

.content-area {
  padding: 12px;
  padding-bottom: 80px;
}

.load-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 14px;
}

.load-state-text {
  font-size: 14px;
  color: #9ca3af;
}

.load-state-hint {
  font-size: 12px;
  color: #d1d5db;
  margin-top: 4px;
}

.retry-btn {
  margin-top: 14px;
  padding: 8px 22px;
  border-radius: 999px;
  background: #ffffff;
  border: 1px solid #dfe3ea;
  cursor: pointer;
}

.retry-btn-text {
  font-size: 13px;
  color: #4b5563;
}

.problem-card {
  background: #fff;
  border-radius: 8px;
  padding: 14px;
  margin-bottom: 12px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}

.problem-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.problem-id {
  font-size: 14px;
  font-weight: 600;
  color: #4f46e5;
}

.problem-name {
  font-size: 18px;
  font-weight: 700;
  color: #1a1a1a;
  flex: 1;
}

.difficulty-tag {
  padding: 3px 8px;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 500;
}

.difficulty-tag--easy { background: #e8f5e9; color: #2e7d32; }
.difficulty-tag--medium { background: #fff3e0; color: #ef6c00; }
.difficulty-tag--hard { background: #ffebee; color: #c62828; }

.problem-stats {
  display: flex;
  gap: 16px;
  padding-top: 8px;
  border-top: 1px solid #f0f0f0;
}

.stat { font-size: 12px; color: #888; }

.problem-description {
  background: #fff;
  border-radius: 8px;
  padding: 14px;
  margin-bottom: 12px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}

.desc-text {
  display: block;
  font-size: 14px;
  line-height: 1.8;
  color: #333;
  margin-bottom: 8px;
}

.example {
  background: #f8f9fa;
  border-radius: 6px;
  padding: 10px;
  margin: 8px 0;
}

.example-label {
  font-size: 12px;
  font-weight: 600;
  color: #4f46e5;
  margin-bottom: 4px;
  display: block;
}

.example-code,
.example-explain {
  font-size: 12px;
  color: #555;
  font-family: Menlo, monospace;
  line-height: 1.6;
  display: block;
}

.tags {
  background: #fff;
  border-radius: 8px;
  padding: 12px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}

.tag-label {
  font-size: 13px;
  color: #666;
  margin-bottom: 8px;
  display: block;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.tag-item {
  padding: 4px 10px;
  background: #f0f0ff;
  color: #4f46e5;
  border-radius: 10px;
  font-size: 12px;
}

.code-area {
  display: flex;
  flex-direction: column;
}

.editor-container {
  background: #1e1e1e;
  border-radius: 8px;
  overflow: hidden;
  margin-bottom: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.editor-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: #2d2d2d;
  border-bottom: 1px solid #3d3d3d;
}

.editor-title { font-size: 13px; color: #888; }

.editor-actions { display: flex; gap: 8px; }

.action-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 4px;
  cursor: pointer;
}

.action-text { font-size: 12px; color: #aaa; }

.code-editor {
  width: 100%;
  height: 250px;
  padding: 12px;
  font-size: 13px;
  line-height: 1.7;
  color: #d4d4d4;
  background: #1e1e1e;
  font-family: Menlo, Consolas, monospace;
  box-sizing: border-box;
  border: none;
  outline: none;
  resize: vertical;
}

.run-actions {
  display: flex;
  gap: 10px;
  margin-top: 12px;
  padding: 0 2px;
}

.run-btn,
.submit-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  height: 48px;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}

.run-btn {
  background: #fff;
  border: 1px solid #e5e7eb;
  color: #374151;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}

.run-btn:disabled,
.submit-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.run-icon-css {
  width: 0;
  height: 0;
  border-left: 8px solid #4f46e5;
  border-top: 5px solid transparent;
  border-bottom: 5px solid transparent;
}

.submit-btn {
  background: #4f46e5;
  border: none;
  color: #fff;
  box-shadow: 0 1px 4px rgba(79, 70, 229, 0.2);
}

.submit-icon-css {
  width: 11px;
  height: 11px;
  position: relative;
}

.submit-icon-css::before {
  content: '';
  position: absolute;
  top: 50%;
  left: 50%;
  width: 6px;
  height: 3px;
  border-left: 1.5px solid #fff;
  border-bottom: 1.5px solid #fff;
  transform: translate(-50%, -70%) rotate(-45deg);
}

.result-modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  z-index: 1000;
}

.result-modal-fullscreen {
  position: fixed;
  inset: 0;
  background: #fff;
  z-index: 1001;
  display: flex;
  flex-direction: column;
  animation: modalSlideUp 0.38s cubic-bezier(0.22, 0.61, 0.36, 1);
}

@keyframes modalSlideUp {
  from { opacity: 0; transform: translateY(15px) scale(0.97); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}

.modal-back-btn {
  position: absolute;
  top: 12px;
  left: 12px;
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1010;
  border-radius: 50%;
  cursor: pointer;
}

.modal-back-arrow {
  width: 9px;
  height: 9px;
  border-left: 1.5px solid #374151;
  border-bottom: 1.5px solid #374151;
  transform: rotate(45deg);
}

.result-full-scroll {
  flex: 1;
  overflow-y: auto;
  height: 100%;
  padding-top: 44px;
}

.result-success-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 20px 16px;
  background: linear-gradient(135deg, #e8f5e9, #c8e6c9);
  border-bottom: 1px solid #a5d6a7;
}

.success-checkmark {
  width: 32px;
  height: 32px;
  background: #4caf50;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  color: #fff;
  font-weight: bold;
}

.success-title {
  font-size: 18px;
  font-weight: 700;
  color: #2e7d32;
}

.success-subtitle {
  font-size: 12px;
  color: #666;
  margin-left: auto;
}

.result-metrics {
  display: flex;
  padding: 12px 16px;
  background: #fafafa;
  border-bottom: 1px solid #f0f0f0;
}

.metric-box {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.metric-label {
  font-size: 11px;
  color: #888;
}

.metric-value {
  font-size: 14px;
  font-weight: 600;
  color: #1a1a1a;
}

.details-title {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: #333;
  padding: 12px 16px 8px;
}

.testcase-section {
  padding: 0 16px 12px;
}

.testcase-card {
  background: #f8f9fa;
  border-radius: 6px;
  padding: 10px;
  margin-bottom: 8px;
}

.tc-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.tc-header--spaced {
  margin-top: 6px;
}

.tc-label {
  font-size: 12px;
  color: #666;
  font-weight: 500;
}

.tc-label--block {
  display: block;
  margin-top: 6px;
}

.tc-pass-badge { color: #4caf50; font-size: 12px; }
.tc-fail-badge { color: #e53935; font-size: 12px; }

.tc-code-block {
  background: #fff;
  border: 1px solid #e0e0e0;
  border-radius: 4px;
  padding: 8px;
  font-size: 12px;
  color: #333;
  font-family: Menlo, monospace;
  margin-top: 4px;
  word-break: break-all;
  white-space: pre-wrap;
}

.modal-actions {
  display: flex;
  gap: 10px;
  padding: 12px 16px 20px;
  border-top: 1px solid #f0f0f0;
}

.btn-secondary,
.btn-primary,
.btn-ai {
  flex: 1;
  padding: 12px;
  border-radius: 6px;
  border: none;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
}

.btn-ai {
  background: #fff;
  border: 1px solid #4f46e5;
  color: #4f46e5;
}

.btn-secondary {
  background: #f0f0f0;
  color: #333;
}

.btn-primary {
  background: #4f46e5;
  color: #fff;
}

.result-error-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 24px 16px;
  background: linear-gradient(135deg, #ffebee, #ffcdd2);
  border-bottom: 1px solid #ef9a9a;
}

.error-circle {
  width: 40px;
  height: 40px;
  background: #e53935;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  color: #fff;
  font-weight: bold;
}

.error-title {
  font-size: 18px;
  font-weight: 700;
  color: #c62828;
}

.error-desc {
  font-size: 12px;
  color: #666;
}

.testcase-card--fail {
  border: 1px solid #ffcdd2;
  background: #fff5f5;
}

.tc-code-error {
  color: #e53935;
  border-color: #ffcdd2;
}

.ai-float-btn {
  position: fixed;
  right: 14px;
  bottom: 110px;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: #4f46e5;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 7px rgba(79, 70, 229, 0.28);
  z-index: 90;
  cursor: pointer;
}

.ai-float-btn-text {
  font-size: 16px;
  font-weight: 700;
  color: #fff;
}

.ai-panel-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  z-index: 950;
  display: flex;
  align-items: flex-end;
}

.ai-panel {
  width: 100%;
  height: 78vh;
  background: #f5f6fa;
  border-radius: 14px 14px 0 0;
  display: flex;
  flex-direction: column;
  animation: aiPanelSlideUp 0.32s cubic-bezier(0.22, 0.61, 0.36, 1);
}

@keyframes aiPanelSlideUp {
  from { transform: translateY(20px); opacity: 0.6; }
  to { transform: translateY(0); opacity: 1; }
}

.ai-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px 10px;
  background: #fff;
  border-radius: 14px 14px 0 0;
  border-bottom: 1px solid #eee;
}

.ai-panel-title {
  font-size: 16px;
  font-weight: 700;
  color: #1a1a1a;
}

.ai-panel-close {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #f0f0f0;
  cursor: pointer;
}

.ai-panel-close-x {
  font-size: 18px;
  color: #666;
  line-height: 1;
}

.ai-context-tip {
  padding: 8px 16px;
  background: #eef0ff;
}

.ai-context-tip-text {
  font-size: 12px;
  color: #4f46e5;
}

.ai-messages {
  flex: 1;
  overflow-y: auto;
  padding: 12px 14px;
  box-sizing: border-box;
}

.ai-empty {
  padding: 40px 20px;
  text-align: center;
}

.ai-empty-text {
  font-size: 13px;
  color: #9ca3af;
}

.ai-msg-row {
  display: flex;
  margin-bottom: 10px;
}

.ai-msg-row--user { justify-content: flex-end; }
.ai-msg-row--assistant { justify-content: flex-start; }

.ai-bubble {
  max-width: 84%;
  padding: 9px 12px;
  border-radius: 8px;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
}

.ai-bubble--user {
  background: #4f46e5;
  color: #fff;
  border-top-right-radius: 2px;
}

.ai-bubble--assistant {
  background: #fff;
  color: #1a1a1a;
  border-top-left-radius: 2px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}

.ai-thinking {
  font-size: 13px;
  color: #9ca3af;
}

.ai-input-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px 15px;
  background: #fff;
  border-top: 1px solid #f0f0f0;
}

.ai-input {
  flex: 1;
  height: 38px;
  padding: 0 12px;
  border-radius: 19px;
  background: #f5f6fa;
  font-size: 14px;
  color: #1a1a1a;
  border: none;
  outline: none;
}

.ai-send-btn {
  min-width: 66px;
  height: 38px;
  padding: 0 14px;
  border-radius: 19px;
  background: #4f46e5;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.ai-send-btn--disabled {
  background: #c7cbe0;
  cursor: not-allowed;
}

.ai-send-text {
  font-size: 14px;
  color: #fff;
}
</style>
