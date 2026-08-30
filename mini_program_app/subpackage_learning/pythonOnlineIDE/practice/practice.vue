<template>
  <view class="practice-page">
    <!-- 顶部导航栏 -->
    <view class="top-bar">
      <view class="back-btn" @tap="goBack">
        <view class="back-arrow"></view>
      </view>
      <text class="top-bar-title">Python 在线编程</text>
      <view class="top-bar-right">
        <view class="tab-btn" :class="{ 'tab-btn--active': activeTab === 'detail' }" @tap="switchTab('detail')">详情</view>
        <view class="tab-btn" :class="{ 'tab-btn--active': activeTab === 'code' }" @tap="switchTab('code')">代码</view>
      </view>
    </view>

    <!-- 题目加载状态 -->
    <view v-if="!problem && !loadError" class="load-state">
      <text class="load-state-text">加载中...</text>
    </view>
    <view v-else-if="loadError" class="load-state">
      <text class="load-state-text">题目加载失败</text>
      <text class="load-state-hint">请检查网络连接后重试</text>
      <view class="retry-btn" @tap="loadProblem">
        <text class="retry-btn-text">重新加载</text>
      </view>
    </view>

    <!-- 详情页 -->
    <view v-if="activeTab === 'detail' && problem" class="content-area">
      <view class="problem-card">
        <view class="problem-header">
          <text class="problem-id">{{ problem.number }}.</text>
          <text class="problem-name">{{ problem.title }}</text>
          <view class="difficulty-tag" :class="'difficulty-tag--' + problem.difficulty">{{ difficultyLabel(problem.difficulty) }}</view>
        </view>
        <view class="problem-stats">
          <text class="stat">通过率 {{ problem.passRate }}%</text>
          <text class="stat">提交数 {{ problem.submissions }}</text>
        </view>
      </view>

      <view class="problem-description">
        <text class="desc-text" v-for="(p, pi) in descParagraphs" :key="pi">{{ p }}</text>
        <view class="example" v-for="(ex, ei) in problem.examples" :key="ei">
          <text class="example-label">示例 {{ ei + 1 }}：</text>
          <text class="example-code" v-for="(line, li) in ex.input" :key="'in'+li">输入：{{ line }}</text>
          <text class="example-code" v-for="(line, li) in ex.output" :key="'out'+li">输出：{{ line }}</text>
          <text class="example-explain" v-if="ex.explain">{{ ex.explain }}</text>
        </view>
      </view>

      <view class="tags">
        <text class="tag-label">相关标签</text>
        <view class="tag-list">
          <view class="tag-item" v-for="t in problem.tags" :key="t">{{ t }}</view>
        </view>
      </view>
    </view>

    <!-- 代码编辑页 -->
    <view v-else-if="activeTab === 'code' && problem" class="content-area code-area">
      <view class="editor-container">
        <view class="editor-header">
          <text class="editor-title">Python 3</text>
          <view class="editor-actions">
            <view class="action-btn" @tap="clearCode">
              <view class="clear-icon"></view>
              <text class="action-text">清空</text>
            </view>
            <view class="action-btn" @tap="resetCode">
              <view class="reset-icon"></view>
              <text class="action-text">重置</text>
            </view>
          </view>
        </view>
        <textarea
          class="code-editor"
          v-model="code"
          placeholder="在此输入你的代码..."
          :show-confirm-bar="false"
          :maxlength="-1"
          auto-focus
        ></textarea>
      </view>

      <!-- 运行和提交按钮 -->
      <view class="run-actions">
        <button class="run-btn" @tap="runCode" :disabled="isExecuting">
          <view class="run-icon-css"></view>
          <text class="run-text">{{ isExecuting ? '执行中...' : '运行' }}</text>
        </button>
        <button class="submit-btn" @tap="submitCode" :disabled="isExecuting">
          <view class="submit-icon-css"></view>
          <text class="submit-text">{{ isExecuting ? '执行中...' : '提交' }}</text>
        </button>
      </view>
    </view>

    <!-- 结果弹窗遮罩 -->
    <view v-if="showResultModal" class="result-modal-overlay">
      <view class="result-modal-fullscreen" @tap.stop>
        <!-- 关闭按钮 -->
        <view class="modal-back-btn" @tap="closeResultModal">
          <view class="modal-back-arrow"></view>
        </view>
        <!-- 暂不支持判题 -->
        <scroll-view v-if="judgeResult && judgeResult.status === 'unsupported'" class="result-full-scroll" scroll-y>
          <view class="result-error-header">
            <view class="error-circle">!</view>
            <text class="error-title">暂不支持在线判题</text>
            <text class="error-desc">{{ judgeResult.statusLabel }}</text>
          </view>
          <view class="testcase-section">
            <view class="testcase-card">
              <text class="tc-label">「{{ problem.title }}」涉及链表或类设计等数据结构，当前在线判题环境暂不支持自动判定。</text>
              <text class="tc-label" style="margin-top: 12rpx; display: block;">你仍然可以在编辑器中编写代码，并在本地 Python 环境中自行验证。</text>
            </view>
          </view>
          <view class="modal-actions">
            <button class="btn-secondary" @tap="closeResultModal">返回</button>
          </view>
        </scroll-view>

        <!-- 判题通过（运行/提交共用） -->
        <scroll-view v-else-if="judgeResult && judgeResult.status === 'ac'" class="result-full-scroll" scroll-y>
          <view class="result-success-header">
            <view class="success-checkmark">✓</view>
            <text class="success-title">{{ isSubmit ? '提交成功' : '运行通过' }}</text>
            <text class="success-subtitle">{{ judgeResult.passed }}/{{ judgeResult.total }} {{ isSubmit ? '个用例通过' : '个示例用例通过' }}</text>
          </view>
          <view class="result-metrics">
            <view class="metric-box">
              <text class="metric-label">执行用时</text>
              <text class="metric-value">{{ judgeResult.runtime }}ms</text>
            </view>
            <view class="metric-box">
              <text class="metric-label">内存消耗</text>
              <text class="metric-value">{{ judgeResult.memory }}MB</text>
            </view>
          </view>
          <text class="details-title">详情</text>
          <view class="testcase-section">
            <view class="testcase-card" v-for="(tc, idx) in judgeResult.testcases" :key="idx">
              <view class="tc-header"><text class="tc-label">输入</text></view>
              <view class="tc-code-block">{{ tc.input }}</view>
              <view class="tc-header" style="margin-top:12rpx;">
                <text class="tc-label">输出</text>
                <text class="tc-pass-badge">✓</text>
              </view>
              <view class="tc-code-block">{{ tc.actual || tc.expected }}</view>
              <view class="tc-header" style="margin-top:12rpx;">
                <text class="tc-label">预期结果</text>
              </view>
              <view class="tc-code-block">{{ tc.expected }}</view>
            </view>
          </view>
          <view class="modal-actions">
            <button class="btn-secondary" @tap="closeResultModal">{{ isSubmit ? '继续刷题' : '修改代码' }}</button>
            <button class="btn-primary" @tap="retryAction">{{ isSubmit ? '再次提交' : '再次运行' }}</button>
          </view>
        </scroll-view>

        <!-- 判题未通过 / 运行失败（共用） -->
        <scroll-view v-else-if="judgeResult" class="result-full-scroll" scroll-y>
          <view class="result-error-header">
            <view class="error-circle">!</view>
            <text class="error-title">{{ resultTitle }}</text>
            <text class="error-desc" v-if="judgeResult.status === 'err'">网络异常或判题服务不可用，请稍后重试</text>
            <text class="error-desc" v-else-if="judgeResult.status === 'ce'">代码存在语法错误，请查看下方用例中的报错信息</text>
            <text class="error-desc" v-else-if="judgeResult.status === 're'">代码执行时抛出异常，请查看下方用例中的报错信息</text>
            <text class="error-desc" v-else-if="judgeResult.status === 'tle'">代码执行超时（单用例限时 10 秒），请检查是否存在死循环或复杂度过高</text>
            <text class="error-desc" v-else>{{ judgeResult.passed }}/{{ judgeResult.total }} {{ isSubmit ? '个用例通过' : '个示例用例通过' }}</text>
          </view>
          <view class="testcase-section" v-if="commonActual">
            <view class="testcase-card testcase-card--fail">
              <view class="tc-header">
                <text class="tc-label">实际输出（{{ commonActualCount }} 个失败用例相同）</text>
                <text class="tc-fail-badge">✗</text>
              </view>
              <view class="tc-code-block tc-code-error">{{ commonActual }}</view>
            </view>
          </view>
          <view class="testcase-section" v-if="judgeResult.testcases && judgeResult.testcases.length > 0">
            <view class="testcase-card" v-for="(tc, idx) in judgeResult.testcases" :key="idx" :class="{ 'testcase-card--fail': tc.status !== 'pass' }">
              <view class="tc-header">
                <text class="tc-label">用例 {{ idx + 1 }}</text>
                <text v-if="tc.status === 'pass'" class="tc-pass-badge">✓</text>
                <text v-else class="tc-fail-badge">✗</text>
              </view>
              <view class="tc-code-block">{{ tc.input }}</view>
              <view class="tc-header" style="margin-top:12rpx;">
                <text class="tc-label">预期输出</text>
              </view>
              <view class="tc-code-block">{{ tc.expected }}</view>
              <view v-if="tc.actual && !commonActual" class="tc-header" style="margin-top:12rpx;">
                <text class="tc-label">实际输出</text>
              </view>
              <view v-if="tc.actual && !commonActual" class="tc-code-block tc-code-error">{{ tc.actual }}</view>
            </view>
          </view>
          <view class="modal-actions" v-if="judgeResult.status !== 'unsupported'">
            <button class="btn-ai" @tap="aiAnalyzeLastResult">AI 分析</button>
            <button class="btn-secondary" @tap="closeResultModal">修改代码</button>
            <button class="btn-primary" @tap="retryAction">{{ isSubmit ? '再次提交' : '再次运行' }}</button>
          </view>
          <view class="modal-actions" v-else>
            <button class="btn-secondary" @tap="closeResultModal">修改代码</button>
            <button class="btn-primary" @tap="retryAction">{{ isSubmit ? '再次提交' : '再次运行' }}</button>
          </view>
        </scroll-view>
      </view>
    </view>

    <!-- AI 助手悬浮按钮（代码 tab 展示） -->
    <view v-if="activeTab === 'code' && problem" class="ai-float-btn" @tap="openAiPanel">
      <text class="ai-float-btn-text">AI</text>
    </view>

    <!-- AI 助手抽屉 -->
    <view v-if="aiPanelVisible" class="ai-panel-overlay" @tap="closeAiPanel">
      <view class="ai-panel" @tap.stop>
        <view class="ai-panel-header">
          <text class="ai-panel-title">AI 助手</text>
          <view class="ai-panel-close" @tap="closeAiPanel">
            <text class="ai-panel-close-x">×</text>
          </view>
        </view>
        <view class="ai-context-tip" v-if="problem">
          <text class="ai-context-tip-text">{{ problem.number }}. {{ problem.title }} · {{ difficultyLabel(problem.difficulty) }}</text>
        </view>
        <scroll-view class="ai-messages" scroll-y :scroll-top="aiScrollTop">
          <view v-if="!aiMessages.length" class="ai-empty">
            <text class="ai-empty-text">可以让我给你提示、讲思路、解释代码或分析报错</text>
          </view>
          <view v-for="(msg, idx) in aiMessages" :key="idx" class="ai-msg-row" :class="msg.role === 'user' ? 'ai-msg-row--user' : 'ai-msg-row--assistant'">
            <view class="ai-bubble" :class="msg.role === 'user' ? 'ai-bubble--user' : 'ai-bubble--assistant'">
              <safe-markdown v-if="msg.role === 'assistant' && msg.content" :content="msg.content" show-code-copy />
              <text v-else-if="msg.role === 'user'">{{ msg.content }}</text>
              <text v-else-if="msg.role === 'assistant' && !msg.content" class="ai-thinking">思考中...</text>
            </view>
          </view>
        </scroll-view>
        <view class="ai-input-row">
          <input
            class="ai-input"
            v-model="aiInput"
            placeholder="追问...（如：再给一点提示）"
            confirm-type="send"
            :disabled="aiStreaming"
            @confirm="sendAiFollowUp"
          />
          <view class="ai-send-btn" :class="{ 'ai-send-btn--disabled': aiStreaming }" @tap="sendAiFollowUp">
            <text class="ai-send-text">{{ aiStreaming ? '回复中' : '发送' }}</text>
          </view>
        </view>
      </view>
    </view>

  </view>
</template>

<script>
import { request } from '@/utils/request.js'
import { getPythonProblemDetail, streamPythonAssist } from '@/api/pythonProblem.js'
import { PROGRESS_STORAGE_KEY, CODE_DRAFT_PREFIX } from '../problems.js'
import SafeMarkdown from '@/components/safe-markdown/safe-markdown.vue'

// ==================== 判题工具函数 ====================

function makeResult(status, runtime, memory, testcases) {
  var passed = 0
  for (var i = 0; i < testcases.length; i++) {
    if (testcases[i].status === 'pass') passed++
  }
  var labels = {
    ac: '通过',
    wa: '解答错误',
    re: '运行错误',
    ce: '编译错误',
    tle: '执行超时',
    err: '服务异常',
    unsupported: '该题型暂不支持在线判题'
  }
  return {
    status: status,
    statusLabel: labels[status] || status,
    runtime: Math.round(Number(runtime) || 0),
    memory: parseFloat((Number(memory) || 0).toFixed(1)),
    passed: passed,
    total: testcases.length,
    testcases: testcases
  }
}

// ==================== 后端 API 判题 ====================

async function executeCodeOnServer(code, problem, testcases) {
  var cases = (testcases && testcases.length) ? testcases : (problem.testcases || [])
  var funcName = problem.funcName || ''

  if (!funcName || !cases.length) {
    return makeResult('unsupported', 0, 0, [])
  }

  var requestBody = {
    code: code,
    funcName: funcName,
    testcases: cases
  }

  try {
    // 走项目统一请求封装：自动拼接 BASE_URL 与 token，统一错误处理
    var data = await request({
      url: '/api/code/execute',
      method: 'POST',
      data: requestBody,
      showError: false
    })

    if (data && data.data) {
      var result = data.data
      return makeResult(
        result.status,
        result.runtime,
        result.memory,
        result.testcases
      )
    } else {
      return makeResult('err', 0, 0, [])
    }
  } catch (err) {
    console.error('后端判题请求失败:', err)
    return makeResult('err', 0, 0, [])
  }
}

// ==================== 组件 ====================

var DIFFICULTY_LABELS = { easy: '简单', medium: '中等', hard: '困难' }

// AI 快捷指令的展示文案
var AI_QUICK_LABELS = {
  hint: '给我一点提示',
  solution: '帮我讲讲思路',
  explain: '解释我的代码',
  debug: '帮我分析报错',
  optimize: '帮我优化代码'
}

export default {
  components: {
    SafeMarkdown
  },
  data() {
    return {
      activeTab: 'code',
      code: '',
      problemId: null,
      problem: null,
      loadError: false,
      judgeResult: null,
      isSubmit: false,
      showResultModal: false,
      isExecuting: false,
      // AI 助手
      aiPanelVisible: false,
      aiMessages: [],
      aiInput: '',
      aiStreaming: false,
      aiScrollTop: 0,
      lastJudgeResult: null
    }
  },
  computed: {
    descParagraphs() {
      if (!this.problem || !this.problem.description) return []
      return this.problem.description.split('\n').filter(function (p) { return p.trim() !== '' })
    },
    // 该题型是否支持在线判题（需函数式求解且有测试用例）
    judgeable() {
      return !!(this.problem && this.problem.funcName && this.problem.testcases && this.problem.testcases.length > 0)
    },
    // 结果弹窗标题：按运行/提交与错误状态派生
    resultTitle() {
      var st = this.judgeResult && this.judgeResult.status
      if (st === 'err') return this.isSubmit ? '提交失败' : '运行失败'
      if (st === 're') return '运行错误'
      if (st === 'ce') return '编译错误'
      if (st === 'tle') return '执行超时'
      return this.isSubmit ? '提交未通过' : '解答错误'
    },
    // "分析报错"可用性：存在最近一次失败的判题结果
    aiDebugAvailable() {
      var jr = this.lastJudgeResult
      return !!(jr && jr.status && jr.status !== 'ac' && jr.status !== 'err' && jr.status !== 'unsupported')
    },
    // 所有失败用例的实际输出是否一致（一致则在结果弹窗顶部统一展示一次，避免逐条重复）
    commonActual() {
      var tcs = (this.judgeResult && this.judgeResult.testcases) || []
      var failed = []
      for (var i = 0; i < tcs.length; i++) {
        if (tcs[i].status !== 'pass' && tcs[i].actual) failed.push(tcs[i].actual)
      }
      if (!failed.length) return null
      var first = failed[0]
      for (var j = 1; j < failed.length; j++) {
        if (failed[j] !== first) return null
      }
      return first
    },
    commonActualCount() {
      var tcs = (this.judgeResult && this.judgeResult.testcases) || []
      var count = 0
      for (var i = 0; i < tcs.length; i++) {
        if (tcs[i].status !== 'pass' && tcs[i].actual) count++
      }
      return count
    }
  },
  onLoad(options) {
    this.problemId = Number(options.id) || 0
    this.loadProblem()
  },
  onUnload() {
    // 兜底保存当前代码草稿
    if (this.problemId && this.code) {
      uni.setStorageSync(CODE_DRAFT_PREFIX + this.problemId, this.code)
    }
  },
  watch: {
    // 代码变化时立即同步保存草稿，中途退出也不丢失
    code: {
      handler(val) {
        if (this.problemId) {
          uni.setStorageSync(CODE_DRAFT_PREFIX + this.problemId, val)
        }
      },
      flush: 'sync'
    }
  },
  methods: {
    // 从后端加载题目内容（含判题用例），完成后再恢复草稿，避免模板代码尚未就绪
    async loadProblem() {
      this.loadError = false
      try {
        var res = await getPythonProblemDetail(this.problemId)
        if (!res || !res.data) {
          this.loadError = true
          return
        }
        this.problem = res.data
        var draft = uni.getStorageSync(CODE_DRAFT_PREFIX + this.problemId)
        this.code = draft || this.problem.defaultCode || '# 你的代码\n'
      } catch (e) {
        console.error('加载题目失败:', e)
        this.loadError = true
      }
    },
    goBack() {
      uni.navigateBack()
    },
    switchTab(tab) {
      this.activeTab = tab
    },
    clearCode() {
      if (!this.code) return
      var self = this
      uni.showModal({
        title: '清空代码',
        content: '确定要清空当前代码吗？此操作不可恢复。',
        confirmColor: '#4f46e5',
        success: function (res) {
          if (res.confirm) {
            self.code = ''
          }
        }
      })
    },
    resetCode() {
      var self = this
      uni.showModal({
        title: '重置代码',
        content: '确定要恢复为默认模板代码吗？当前修改将丢失。',
        confirmColor: '#4f46e5',
        success: function (res) {
          if (res.confirm) {
            self.code = self.problem.defaultCode || '# 你的代码\n'
          }
        }
      })
    },
    difficultyLabel(d) {
      return DIFFICULTY_LABELS[d] || d
    },
    // 运行只验证示例用例：由 examples 转换为判题用例格式；无有效示例时回退取前两个测试用例
    buildRunTestcases() {
      var fromExamples = (this.problem.examples || []).map(function (ex) {
        if (!ex || !ex.input || !ex.input.length || !ex.output || !ex.output.length) return null
        return { input: ex.input.join(', '), expected: ex.output.join('\n') }
      }).filter(function (tc) { return !!tc })
      if (fromExamples.length > 0) return fromExamples
      return (this.problem.testcases || []).slice(0, 2)
    },
    showResult(result) {
      this.judgeResult = result
      // 记录最近一次判题结果，供 AI 助手"分析报错"使用
      this.lastJudgeResult = result
      this.showResultModal = true
    },
    closeResultModal() {
      this.showResultModal = false
    },
    // 结果弹窗"再次运行/再次提交"：按当前状态分发
    retryAction() {
      if (this.isSubmit) {
        this.submitCode()
      } else {
        this.runCode()
      }
    },
    // 记录已解决题目（进度持久化，供题库页展示）
    markSolved(problemId) {
      if (!problemId) return
      var solved = uni.getStorageSync(PROGRESS_STORAGE_KEY) || []
      if (solved.indexOf(problemId) === -1) {
        solved.push(problemId)
        uni.setStorageSync(PROGRESS_STORAGE_KEY, solved)
      }
    },

    // ---- 运行代码 ----
    async runCode() {
      if (this.isExecuting || !this.problem) return
      this.isSubmit = false

      if (!this.judgeable) {
        this.showResult(makeResult('unsupported', 0, 0, []))
        return
      }

      if (!this.code.trim()) {
        uni.showToast({ title: '请先编写代码', icon: 'none' })
        return
      }

      this.isExecuting = true
      uni.showLoading({ title: '执行中...' })

      try {
        var result = await executeCodeOnServer(this.code, this.problem, this.buildRunTestcases())
        uni.hideLoading()
        this.showResult(result)
      } catch (e) {
        console.error('运行出错:', e)
        uni.hideLoading()
        this.showResult(makeResult('err', 0, 0, []))
      }
      this.isExecuting = false
    },

    // ---- 提交代码 ----
    async submitCode() {
      if (this.isExecuting || !this.problem) return
      this.closeResultModal()
      this.isSubmit = true

      if (!this.judgeable) {
        this.showResult(makeResult('unsupported', 0, 0, []))
        return
      }

      if (!this.code.trim()) {
        uni.showToast({ title: '请先编写代码', icon: 'none' })
        return
      }

      this.isExecuting = true
      uni.showLoading({ title: '提交中...' })

      try {
        var result = await executeCodeOnServer(this.code, this.problem)
        uni.hideLoading()
        // 提交通过后记录做题进度（本地持久化）
        if (result.status === 'ac') {
          this.markSolved(this.problemId)
        }
        this.showResult(result)
      } catch (e) {
        console.error('提交出错:', e)
        uni.hideLoading()
        this.showResult(makeResult('err', 0, 0, []))
      }
      this.isExecuting = false
    },

    // ==================== AI 助手 ====================

    openAiPanel() {
      this.aiPanelVisible = true
      this.scrollAiToBottom()
    },
    closeAiPanel() {
      this.aiPanelVisible = false
    },
    pushAiMessage(role, content) {
      this.aiMessages.push({ role: role, content: content || '' })
    },
    scrollAiToBottom() {
      this.$nextTick(function () {
        this.aiScrollTop = Date.now()
      })
    },
    // 组装题目上下文（不含隐藏用例，防泄露）
    buildAiProblemContext() {
      var p = this.problem || {}
      return {
        id: p.id,
        number: p.number,
        title: p.title,
        difficulty: p.difficulty,
        description: p.description,
        examples: p.examples,
        tags: p.tags,
        funcName: p.funcName
      }
    },
    // 组装判题结果上下文：仅保留状态与最多 3 个失败用例，避免泄露全部用例
    buildAiDebugResult() {
      var jr = this.lastJudgeResult
      if (!jr) return null
      var failed = (jr.testcases || []).filter(function (tc) { return tc.status !== 'pass' }).slice(0, 3)
      return {
        status: jr.status,
        statusLabel: jr.statusLabel,
        passed: jr.passed,
        total: jr.total,
        runtime: jr.runtime,
        memory: jr.memory,
        testcases: failed
      }
    },
    // 失败结果弹窗"AI 分析"：关闭弹窗、打开 AI 面板并预填报错分析
    aiAnalyzeLastResult() {
      if (!this.aiDebugAvailable) {
        uni.showToast({ title: '没有可分析的判题结果', icon: 'none' })
        return
      }
      this.closeResultModal()
      this.openAiPanel()
      this.sendAiRequest('debug', null, null)
    },
    sendAiFollowUp() {
      var text = (this.aiInput || '').trim()
      if (!text) {
        uni.showToast({ title: '请输入要追问的内容', icon: 'none' })
        return
      }
      this.aiInput = ''
      this.sendAiRequest('free', text, null)
    },
    async sendAiRequest(questionType, followUp, options) {
      if (this.aiStreaming || !this.problem) return
      var userText = followUp || AI_QUICK_LABELS[questionType] || (options && options.omitCode ? '解释一下题目' : questionType)
      var history = this.aiMessages.slice(-6).map(function (m) {
        return { role: m.role, content: m.content }
      })
      var needJudge = questionType === 'debug' || questionType === 'optimize'
      var data = {
        questionType: questionType,
        problem: this.buildAiProblemContext(),
        userCode: (options && options.omitCode) ? '' : this.code,
        judgeResult: needJudge ? this.buildAiDebugResult() : null,
        followUp: followUp || null,
        history: history
      }
      this.pushAiMessage('user', userText)
      this.pushAiMessage('assistant', '')
      this.aiStreaming = true
      this.scrollAiToBottom()

      var assistantIndex = this.aiMessages.length - 1
      try {
        var stream = streamPythonAssist(data, {
          onDelta: (function (self) {
            return function (content) {
              self.aiMessages[assistantIndex].content += content || ''
              self.scrollAiToBottom()
            }
          })(this),
          onDone: (function (self) {
            return function (payload) {
              if (payload && payload.answer && !self.aiMessages[assistantIndex].content) {
                self.aiMessages[assistantIndex].content = payload.answer
              }
              self.aiStreaming = false
              self.scrollAiToBottom()
            }
          })(this),
          onError: (function (self) {
            return function (payload) {
              var detail = (payload && payload.message) ? payload.message : '请稍后重试'
              self.aiMessages[assistantIndex].content = 'AI 服务暂时不可用：' + detail
              self.aiStreaming = false
              self.scrollAiToBottom()
            }
          })(this)
        })
        await stream.done
      } catch (err) {
        console.error('AI 辅助请求失败:', err)
        this.aiMessages[assistantIndex].content = 'AI 服务调用失败，请稍后重试'
        this.aiStreaming = false
        this.scrollAiToBottom()
      }
    }
  }
}
</script>

<style scoped>
.practice-page {
  min-height: 100vh;
  background: #f5f6fa;
  color: #1a1a1a;
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', sans-serif;
}

/* 顶部导航 */
.top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20rpx 24rpx;
  background: #fff;
  border-bottom: 1rpx solid #eee;
  position: sticky;
  top: 0;
  z-index: 100;
}

.back-btn {
  width: 56rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.back-arrow {
  width: 16rpx;
  height: 16rpx;
  border-left: 3rpx solid #1a1a1a;
  border-bottom: 3rpx solid #1a1a1a;
  transform: rotate(45deg);
}

.top-bar-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #1a1a1a;
}

.top-bar-right {
  display: flex;
  gap: 16rpx;
}

.tab-btn {
  padding: 12rpx 24rpx;
  font-size: 26rpx;
  color: #666;
  border-radius: 8rpx;
}

.tab-btn--active {
  background: #f0f0ff;
  color: #4f46e5;
}

.content-area {
  padding: 24rpx;
  padding-bottom: 160rpx;
}

.load-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 160rpx 28rpx;
}

.load-state-text {
  font-size: 28rpx;
  color: #9ca3af;
}

.load-state-hint {
  font-size: 24rpx;
  color: #d1d5db;
  margin-top: 8rpx;
}

.retry-btn {
  margin-top: 28rpx;
  padding: 16rpx 44rpx;
  border-radius: 999rpx;
  background: #ffffff;
  border: 1rpx solid #dfe3ea;
}

.retry-btn-text {
  font-size: 26rpx;
  color: #4b5563;
}

/* 题目卡片 */
.problem-card {
  background: #fff;
  border-radius: 16rpx;
  padding: 28rpx;
  margin-bottom: 24rpx;
  box-shadow: 0 2rpx 8rpx rgba(0,0,0,0.04);
}

.problem-header {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 16rpx;
}

.problem-id {
  font-size: 28rpx;
  font-weight: 600;
  color: #4f46e5;
}

.problem-name {
  font-size: 36rpx;
  font-weight: 700;
  color: #1a1a1a;
  flex: 1;
}

.difficulty-tag {
  padding: 6rpx 16rpx;
  border-radius: 20rpx;
  font-size: 22rpx;
  font-weight: 500;
}

.difficulty-tag--easy { background: #e8f5e9; color: #2e7d32; }
.difficulty-tag--medium { background: #fff3e0; color: #ef6c00; }
.difficulty-tag--hard { background: #ffebee; color: #c62828; }

.problem-stats {
  display: flex;
  gap: 32rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid #f0f0f0;
}

.stat { font-size: 24rpx; color: #888; }

.problem-description {
  background: #fff;
  border-radius: 16rpx;
  padding: 28rpx;
  margin-bottom: 24rpx;
  box-shadow: 0 2rpx 8rpx rgba(0,0,0,0.04);
}

.desc-text {
  font-size: 28rpx;
  line-height: 1.8;
  color: #333;
  margin-bottom: 16rpx;
}

.example {
  background: #f8f9fa;
  border-radius: 12rpx;
  padding: 20rpx;
  margin: 16rpx 0;
}

.example-label {
  font-size: 24rpx;
  font-weight: 600;
  color: #4f46e5;
  margin-bottom: 8rpx;
  display: block;
}

.example-code {
  font-size: 24rpx;
  color: #555;
  font-family: 'Menlo', monospace;
  line-height: 1.6;
  display: block;
}

.tags {
  background: #fff;
  border-radius: 16rpx;
  padding: 24rpx;
  box-shadow: 0 2rpx 8rpx rgba(0,0,0,0.04);
}

.tag-label { font-size: 26rpx; color: #666; margin-bottom: 16rpx; display: block; }

.tag-list { display: flex; flex-wrap: wrap; gap: 12rpx; }

.tag-item {
  padding: 8rpx 20rpx;
  background: #f0f0ff;
  color: #4f46e5;
  border-radius: 20rpx;
  font-size: 24rpx;
}

/* 代码编辑器 */
.code-area {
  display: flex;
  flex-direction: column;
}

.editor-container {
  background: #1e1e1e;
  border-radius: 16rpx;
  overflow: hidden;
  margin-bottom: 24rpx;
  box-shadow: 0 4rpx 16rpx rgba(0,0,0,0.15);
}

.editor-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16rpx 24rpx;
  background: #2d2d2d;
  border-bottom: 1rpx solid #3d3d3d;
}

.editor-title { font-size: 26rpx; color: #888; }

.editor-actions { display: flex; gap: 16rpx; }

.action-btn {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 8rpx 16rpx;
  background: rgba(255,255,255,0.08);
  border-radius: 8rpx;
}

.action-text { font-size: 24rpx; color: #aaa; }

.code-editor {
  width: 100%;
  height: 500rpx;
  padding: 24rpx;
  font-size: 26rpx;
  line-height: 1.7;
  color: #d4d4d4;
  background: #1e1e1e;
  font-family: 'Menlo', 'Consolas', monospace;
  box-sizing: border-box;
}

/* 运行按钮 */
.run-actions {
  display: flex;
  gap: 20rpx;
  margin-top: 24rpx;
  padding: 0 4rpx;
}

.run-btn, .submit-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10rpx;
  height: 96rpx;
  border-radius: 12rpx;
  font-size: 28rpx;
  font-weight: 600;
  transition: all 0.2s ease;
}

.run-btn {
  background: #fff;
  border: 2rpx solid #e5e7eb;
  color: #374151;
  box-shadow: 0 2rpx 8rpx rgba(0,0,0,0.04);
}

.run-btn:active {
  background: #f9fafb;
  border-color: #d1d5db;
  box-shadow: 0 1rpx 4rpx rgba(0,0,0,0.06);
}

.run-btn[disabled] {
  opacity: 0.6;
}

.run-icon-css {
  width: 0;
  height: 0;
  border-left: 16rpx solid #4f46e5;
  border-top: 10rpx solid transparent;
  border-bottom: 10rpx solid transparent;
}

.run-text { color: #374151; }

.submit-btn {
  background: #4f46e5;
  border: none;
  color: #fff;
  box-shadow: 0 2rpx 8rpx rgba(79, 70, 229, 0.2);
}

.submit-btn:active {
  box-shadow: 0 1rpx 4rpx rgba(79, 70, 229, 0.15);
  transform: scale(0.98);
}

.submit-btn[disabled] {
  opacity: 0.6;
}

.submit-icon-css {
  width: 22rpx;
  height: 22rpx;
  position: relative;
}

.submit-icon-css::before {
  content: '';
  position: absolute;
  top: 50%;
  left: 50%;
  width: 12rpx;
  height: 6rpx;
  border-left: 3rpx solid #fff;
  border-bottom: 3rpx solid #fff;
  transform: translate(-50%, -70%) rotate(-45deg);
}

.submit-text { color: #fff; }

/* ========== 结果弹窗 ========== */
.result-modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  width: 100%;
  height: 100vh;
  background: rgba(0,0,0,0.6);
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
}

.result-modal-fullscreen {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  width: 100%;
  height: 100vh;
  background: #fff;
  z-index: 1001;
  display: flex;
  flex-direction: column;
  animation: modalSlideUp 0.38s cubic-bezier(0.22, 0.61, 0.36, 1);
}

.result-modal-overlay {
  animation: overlayFadeIn 0.3s ease-out;
}

@keyframes overlayFadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes modalSlideUp {
  from { opacity: 0; transform: translateY(30rpx) scale(0.97); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}

.modal-back-btn {
  position: absolute;
  top: 24rpx;
  left: 24rpx;
  width: 56rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1010;
  border-radius: 50%;
}

.modal-back-arrow {
  width: 18rpx;
  height: 18rpx;
  border-left: 3rpx solid #374151;
  border-bottom: 3rpx solid #374151;
  transform: rotate(45deg);
}

.result-full-scroll {
  flex: 1;
  height: 100%;
}

/* 成功状态 */
.result-success-header {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 40rpx 32rpx;
  background: linear-gradient(135deg, #e8f5e9, #c8e6c9);
  border-bottom: 1rpx solid #a5d6a7;
}

.success-checkmark {
  width: 64rpx;
  height: 64rpx;
  background: #4caf50;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 36rpx;
  color: #fff;
  font-weight: bold;
}

.success-title {
  font-size: 36rpx;
  font-weight: 700;
  color: #2e7d32;
}

.success-subtitle {
  font-size: 24rpx;
  color: #666;
  margin-left: auto;
}

.result-metrics {
  display: flex;
  padding: 24rpx 32rpx;
  background: #fafafa;
  border-bottom: 1rpx solid #f0f0f0;
}

.metric-box {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.metric-label {
  font-size: 22rpx;
  color: #888;
}

.metric-value {
  font-size: 28rpx;
  font-weight: 600;
  color: #1a1a1a;
}

.details-title {
  font-size: 28rpx;
  font-weight: 600;
  color: #333;
  padding: 24rpx 32rpx 16rpx;
}

.testcase-section {
  padding: 0 32rpx 24rpx;
}

.testcase-card {
  background: #f8f9fa;
  border-radius: 12rpx;
  padding: 20rpx;
  margin-bottom: 16rpx;
}

.tc-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.tc-label {
  font-size: 24rpx;
  color: #666;
  font-weight: 500;
}

.tc-pass-badge {
  color: #4caf50;
  font-size: 24rpx;
}

.tc-code-block {
  background: #fff;
  border: 1rpx solid #e0e0e0;
  border-radius: 8rpx;
  padding: 16rpx;
  font-size: 24rpx;
  color: #333;
  font-family: 'Menlo', monospace;
  margin-top: 8rpx;
  word-break: break-all;
}

.modal-actions {
  display: flex;
  gap: 20rpx;
  padding: 24rpx 32rpx 40rpx;
  border-top: 1rpx solid #f0f0f0;
}

.btn-secondary, .btn-primary, .btn-ai {
  flex: 1;
  padding: 24rpx;
  border-radius: 12rpx;
  border: none;
  font-size: 28rpx;
  font-weight: 500;
}

.btn-ai {
  background: #fff;
  border: 2rpx solid #4f46e5;
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

/* 失败状态 */
.result-error-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
  padding: 48rpx 32rpx;
  background: linear-gradient(135deg, #ffebee, #ffcdd2);
  border-bottom: 1rpx solid #ef9a9a;
}

.error-circle {
  width: 80rpx;
  height: 80rpx;
  background: #e53935;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 44rpx;
  color: #fff;
  font-weight: bold;
}

.error-title {
  font-size: 36rpx;
  font-weight: 700;
  color: #c62828;
}

.error-desc {
  font-size: 24rpx;
  color: #666;
}

.error-detail-section {
  padding: 24rpx 32rpx;
  border-bottom: 1rpx solid #f0f0f0;
}

.section-title {
  font-size: 26rpx;
  font-weight: 600;
  color: #333;
  margin-bottom: 16rpx;
  display: block;
}

.error-code-block {
  background: #1e1e1e;
  border-radius: 12rpx;
  padding: 20rpx;
}

.error-line {
  font-size: 24rpx;
  color: #f44336;
  font-family: 'Menlo', monospace;
  line-height: 1.6;
  display: block;
}

.tc-fail-badge {
  color: #e53935;
  font-size: 24rpx;
}

.testcase-card--fail {
  border: 1rpx solid #ffcdd2;
  background: #fff5f5;
}

.tc-code-error {
  color: #e53935;
  border-color: #ffcdd2;
}

/* ========== AI 助手 ========== */
.ai-float-btn {
  position: fixed;
  right: 28rpx;
  bottom: 220rpx;
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  background: #4f46e5;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4rpx 14rpx rgba(79, 70, 229, 0.28);
  z-index: 90;
}

.ai-float-btn-text {
  font-size: 32rpx;
  font-weight: 700;
  color: #fff;
}

.ai-panel-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  width: 100%;
  height: 100vh;
  background: rgba(0, 0, 0, 0.45);
  z-index: 950;
  display: flex;
  align-items: flex-end;
  animation: aiOverlayFadeIn 0.25s ease-out;
}

@keyframes aiOverlayFadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

.ai-panel {
  width: 100%;
  height: 78vh;
  background: #f5f6fa;
  border-radius: 28rpx 28rpx 0 0;
  display: flex;
  flex-direction: column;
  animation: aiPanelSlideUp 0.32s cubic-bezier(0.22, 0.61, 0.36, 1);
}

@keyframes aiPanelSlideUp {
  from { transform: translateY(40rpx); opacity: 0.6; }
  to { transform: translateY(0); opacity: 1; }
}

.ai-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 28rpx 32rpx 20rpx;
  background: #fff;
  border-radius: 28rpx 28rpx 0 0;
  border-bottom: 1rpx solid #eee;
}

.ai-panel-title {
  font-size: 32rpx;
  font-weight: 700;
  color: #1a1a1a;
}

.ai-panel-close {
  width: 56rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #f0f0f0;
}

.ai-panel-close-x {
  font-size: 36rpx;
  color: #666;
  line-height: 1;
}

.ai-context-tip {
  padding: 16rpx 32rpx;
  background: #eef0ff;
}

.ai-context-tip-text {
  font-size: 24rpx;
  color: #4f46e5;
}

.ai-messages {
  flex: 1;
  height: 0;
  padding: 24rpx 28rpx;
  box-sizing: border-box;
}

.ai-empty {
  padding: 80rpx 40rpx;
  text-align: center;
}

.ai-empty-text {
  font-size: 26rpx;
  color: #9ca3af;
}

.ai-msg-row {
  display: flex;
  margin-bottom: 20rpx;
}

.ai-msg-row--user {
  justify-content: flex-end;
}

.ai-msg-row--assistant {
  justify-content: flex-start;
}

.ai-bubble {
  max-width: 84%;
  padding: 18rpx 24rpx;
  border-radius: 16rpx;
  font-size: 28rpx;
  line-height: 1.6;
  word-break: break-all;
}

.ai-bubble--user {
  background: #4f46e5;
  color: #fff;
  border-top-right-radius: 4rpx;
}

.ai-bubble--assistant {
  background: #fff;
  color: #1a1a1a;
  border-top-left-radius: 4rpx;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
}

.ai-thinking {
  font-size: 26rpx;
  color: #9ca3af;
}

.ai-input-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 16rpx 28rpx 30rpx;
  background: #fff;
  border-top: 1rpx solid #f0f0f0;
}

.ai-input {
  flex: 1;
  height: 76rpx;
  padding: 0 24rpx;
  border-radius: 38rpx;
  background: #f5f6fa;
  font-size: 28rpx;
  color: #1a1a1a;
}

.ai-send-btn {
  min-width: 132rpx;
  height: 76rpx;
  padding: 0 28rpx;
  border-radius: 38rpx;
  background: #4f46e5;
  display: flex;
  align-items: center;
  justify-content: center;
}

.ai-send-btn--disabled {
  background: #c7cbe0;
}

.ai-send-text {
  font-size: 28rpx;
  color: #fff;
}
</style>
