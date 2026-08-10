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

    <!-- 详情页 -->
    <view v-if="activeTab === 'detail'" class="content-area">
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
    <view v-else-if="activeTab === 'code'" class="content-area code-area">
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

        <!-- 运行成功 -->
        <scroll-view v-if="judgeResult && !isSubmit && judgeResult.status === 'ac'" class="result-full-scroll" scroll-y>
          <view class="result-success-header">
            <view class="success-checkmark">✓</view>
            <text class="success-title">运行通过</text>
            <text class="success-subtitle">{{ judgeResult.passed }}/{{ judgeResult.total }} 个用例通过</text>
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
            <button class="btn-secondary" @tap="closeResultModal">修改代码</button>
            <button class="btn-primary" @tap="runCode">再次运行</button>
          </view>
        </scroll-view>

        <!-- 运行失败 -->
        <scroll-view v-else-if="judgeResult && !isSubmit && judgeResult.status !== 'ac'" class="result-full-scroll" scroll-y>
          <view class="result-error-header">
            <view class="error-circle">!</view>
            <text class="error-title">解答错误</text>
            <text class="error-desc">{{ judgeResult.passed }}/{{ judgeResult.total }} 个用例通过</text>
          </view>
          <view class="testcase-section" v-if="judgeResult.testcases && judgeResult.testcases.length > 0">
            <view class="testcase-card" v-for="(tc, idx) in judgeResult.testcases" :key="idx" :class="{ 'testcase-card--fail': tc.status === 'fail' }">
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
              <view v-if="tc.actual" class="tc-header" style="margin-top:12rpx;">
                <text class="tc-label">实际输出</text>
              </view>
              <view v-if="tc.actual" class="tc-code-block tc-code-error">{{ tc.actual }}</view>
            </view>
          </view>
          <view class="modal-actions">
            <button class="btn-secondary" @tap="closeResultModal">修改代码</button>
            <button class="btn-primary" @tap="runCode">再次运行</button>
          </view>
        </scroll-view>

        <!-- 提交成功 -->
        <scroll-view v-else-if="judgeResult && isSubmit && judgeResult.status === 'ac'" class="result-full-scroll" scroll-y>
          <view class="result-success-header">
            <view class="success-checkmark">✓</view>
            <text class="success-title">提交成功</text>
            <text class="success-subtitle">{{ judgeResult.passed }}/{{ judgeResult.total }} 个用例通过</text>
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
            <button class="btn-secondary" @tap="closeResultModal">继续刷题</button>
            <button class="btn-primary" @tap="submitCode">再次提交</button>
          </view>
        </scroll-view>

        <!-- 提交失败 -->
        <scroll-view v-else-if="judgeResult && isSubmit && judgeResult.status !== 'ac'" class="result-full-scroll" scroll-y>
          <view class="result-error-header">
            <view class="error-circle">!</view>
            <text class="error-title">提交未通过</text>
            <text class="error-desc">{{ judgeResult.passed }}/{{ judgeResult.total }} 个用例通过</text>
          </view>
          <view class="testcase-section" v-if="judgeResult.testcases && judgeResult.testcases.length > 0">
            <view class="testcase-card" v-for="(tc, idx) in judgeResult.testcases" :key="idx" :class="{ 'testcase-card--fail': tc.status === 'fail' }">
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
              <view v-if="tc.actual" class="tc-header" style="margin-top:12rpx;">
                <text class="tc-label">实际输出</text>
              </view>
              <view v-if="tc.actual" class="tc-code-block tc-code-error">{{ tc.actual }}</view>
            </view>
          </view>
          <view class="modal-actions">
            <button class="btn-secondary" @tap="closeResultModal">修改代码</button>
            <button class="btn-primary" @tap="submitCode">再次提交</button>
          </view>
        </scroll-view>
      </view>
    </view>

  </view>
</template>

<script>
// ==================== 题目数据 ====================
var PROBLEMS = {
  1: {
    id: 1, number: 1, title: '两数之和', difficulty: 'easy', passRate: 45.2, submissions: '12.1M',
    description: '给定一个整数数组 nums 和一个目标值 target，请你在该数组中找出和为目标值的那两个整数，并返回他们的数组下标。\n\n你可以假设每种输入只会对应一个答案。但是，数组中同一个元素不能使用两遍。',
    examples: [
      { input: ['nums = [2, 7, 11, 15]', 'target = 9'], output: ['[0, 1]'], explain: '因为 nums[0] + nums[1] == 9，所以返回 [0, 1]。' },
      { input: ['nums = [3, 2, 4]', 'target = 6'], output: ['[1, 2]'] },
      { input: ['nums = [3, 3]', 'target = 6'], output: ['[0, 1]'] }
    ],
    tags: ['数组', '哈希表'],
    defaultCode: '# 两数之和\ndef twoSum(nums, target):\n    # 你的代码\n    pass\n',
    testcases: [
      { input: 'nums = [2,7,11,15], target = 9', expected: '[0,1]' },
      { input: 'nums = [3,2,4], target = 6', expected: '[1,2]' },
      { input: 'nums = [3,3], target = 6', expected: '[0,1]' },
      { input: 'nums = [0,4,3,0], target = 0', expected: '[0,3]' }
    ],
    funcName: 'twoSum'
  },
  15: {
    id: 15, number: 15, title: '三数之和', difficulty: 'medium', passRate: 30.8, submissions: '8.4M',
    description: '给定一个包含 n 个整数的数组 nums，判断 nums 中是否存在三个元素 a，b，c ，使得 a + b + c = 0 ？找出所有满足条件且不重复的三元组。\n\n注意：答案中不可以包含重复的三元组。',
    examples: [
      { input: ['nums = [-1, 0, 1, 2, -1, -4]'], output: [['-1, 0, 1'], ['-1, -1, 2']], explain: '满足要求的三元组集合为: [[-1, 0, 1], [-1, -1, 2]]。' }
    ],
    tags: ['数组', '双指针', '排序'],
    defaultCode: '# 三数之和\ndef threeSum(nums):\n    # 你的代码\n    pass\n',
    testcases: [
      { input: 'nums = [-1,0,1,2,-1,-4]', expected: '[[-1,-1,2],[-1,0,1]]' },
      { input: 'nums = [0,1,1]', expected: '[]' },
      { input: 'nums = [0,0,0]', expected: '[[0,0,0]]' },
      { input: 'nums = [-2,0,1,1,2]', expected: '[[-2,0,2],[-2,1,1]]' }
    ],
    funcName: 'threeSum'
  }
}

var DIFFICULTY_LABELS = { easy: '简单', medium: '中等', hard: '困难' }

function defaultProblem(id) {
  return {
    id: id, number: id, difficulty: 'easy', passRate: 40, submissions: '5M',
    title: '未知题目 #' + id,
    description: '请前往题库查看详情。',
    examples: [], tags: [],
    defaultCode: '# 你的代码\n',
    testcases: [], funcName: ''
  }
}

// ==================== 判题工具函数 ====================

function makeResult(status, runtime, memory, testcases) {
  var passed = 0
  for (var i = 0; i < testcases.length; i++) {
    if (testcases[i].status === 'pass') passed++
  }
  var labels = { ac: '通过', wa: '解答错误', err: '运行错误' }
  return {
    status: status,
    statusLabel: labels[status] || status,
    runtime: Math.round(runtime),
    memory: parseFloat(memory.toFixed(1)),
    passed: passed,
    total: testcases.length,
    testcases: testcases
  }
}

// ==================== 后端 API 判题 ====================

var API_BASE = 'http://localhost:8080'

async function executeCodeOnServer(code, problem) {
  var testcases = problem.testcases || []
  var funcName = problem.funcName || ''

  if (!funcName) {
    return makeResult('err', 0, 0, [])
  }

  if (!testcases.length) {
    return makeResult('err', 0, 0, [])
  }

  var requestBody = {
    code: code,
    funcName: funcName,
    testcases: testcases
  }

  try {
    var response = await new Promise(function (resolve, reject) {
      uni.request({
        url: API_BASE + '/api/code/execute',
        method: 'POST',
        data: requestBody,
        header: {
          'Content-Type': 'application/json'
        },
        success: resolve,
        fail: reject
      })
    })

    if (response.statusCode === 200 && response.data && response.data.code === 200) {
      var result = response.data.data
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

export default {
  data() {
    return {
      activeTab: 'code',
      code: '',
      problemId: null,
      judgeResult: null,
      isSubmit: false,
      showResultModal: false,
      isExecuting: false
    }
  },
  computed: {
    problem() {
      return PROBLEMS[this.problemId] || defaultProblem(this.problemId)
    },
    descParagraphs() {
      if (!this.problem.description) return []
      return this.problem.description.split('\n').filter(function (p) { return p.trim() !== '' })
    }
  },
  onLoad(options) {
    this.problemId = Number(options.id) || 1
    this.code = this.problem.defaultCode || '# 你的代码\n'
  },
  methods: {
    goBack() {
      uni.navigateBack()
    },
    switchTab(tab) {
      this.activeTab = tab
    },
    clearCode() {
      this.code = ''
    },
    resetCode() {
      this.code = this.problem.defaultCode || '# 你的代码\n'
    },
    difficultyLabel(d) {
      return DIFFICULTY_LABELS[d] || d
    },
    showResult(result) {
      this.judgeResult = result
      this.showResultModal = true
    },
    closeResultModal() {
      this.showResultModal = false
    },

    // ---- 运行代码 ----
    async runCode() {
      if (this.isExecuting) return
      this.isSubmit = false

      if (!this.code.trim()) {
        this.showResult(makeResult('err', 0, 0, []))
        return
      }

      this.isExecuting = true
      uni.showLoading({ title: '执行中...' })

      try {
        var result = await executeCodeOnServer(this.code, this.problem)
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
      if (this.isExecuting) return
      this.closeResultModal()
      this.isSubmit = true

      if (!this.code.trim()) {
        this.showResult(makeResult('err', 0, 0, []))
        return
      }

      this.isExecuting = true
      uni.showLoading({ title: '提交中...' })

      try {
        var result = await executeCodeOnServer(this.code, this.problem)
        uni.hideLoading()
        this.showResult(result)
      } catch (e) {
        console.error('提交出错:', e)
        uni.hideLoading()
        this.showResult(makeResult('err', 0, 0, []))
      }
      this.isExecuting = false
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
  border-radius: 48rpx;
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
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
  border: none;
  color: #fff;
  box-shadow: 0 4rpx 16rpx rgba(79, 70, 229, 0.3);
}

.submit-btn:active {
  box-shadow: 0 2rpx 8rpx rgba(79, 70, 229, 0.2);
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

.btn-secondary, .btn-primary {
  flex: 1;
  padding: 24rpx;
  border-radius: 12rpx;
  border: none;
  font-size: 28rpx;
  font-weight: 500;
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
</style>
