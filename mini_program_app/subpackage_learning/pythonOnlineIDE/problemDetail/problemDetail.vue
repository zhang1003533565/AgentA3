<template>
  <view class="problem-detail-page">
    <nav-bar title="题目详情" :showBack="true" />

    <view v-if="!problem && !loadError" class="detail-state">
      <text class="detail-state-text">加载中...</text>
    </view>
    <view v-else-if="loadError" class="detail-state">
      <text class="detail-state-text">题目详情加载失败</text>
      <text class="detail-state-hint">题目可能不存在，或网络连接异常</text>
      <view class="retry-btn" @tap="loadProblem">
        <text class="retry-btn-text">重新加载</text>
      </view>
    </view>
    <view v-else-if="problem">
    <view class="problem-header">
      <view class="problem-title-row">
        <text class="problem-number">{{ problem.number }}.</text>
        <text class="problem-title">{{ problem.title }}</text>
        <view class="difficulty-tag" :class="'difficulty-tag--' + problem.difficulty">{{ difficultyLabel(problem.difficulty) }}</view>
      </view>
      <view class="problem-stats">
        <view class="stat-item">
          <image class="stat-icon" src="/static/icons/chart.svg" mode="aspectFit"></image>
          <text class="stat-text">通过率 {{ problem.passRate }}%</text>
        </view>
        <view class="stat-item">
          <image class="stat-icon" src="/static/icons/edit-3.svg" mode="aspectFit"></image>
          <text class="stat-text">提交数 {{ problem.submissions }}</text>
        </view>
      </view>
    </view>

    <view class="problem-content">
      <text class="problem-desc">{{ problem.description }}</text>

      <view class="example-block" v-for="(ex, ei) in problem.examples" :key="ei">
        <text class="example-title">示例 {{ ei + 1 }}：</text>
        <text class="example-text" v-for="(line, li) in ex.input" :key="'in' + li">输入：{{ line }}</text>
        <text class="example-text" v-for="(line, li) in ex.output" :key="'out' + li">输出：{{ line }}</text>
        <text class="example-explain" v-if="ex.explain">{{ ex.explain }}</text>
      </view>
    </view>

    <view class="tags-section">
      <text class="tags-label">相关标签</text>
      <view class="tags-list">
        <view class="tag" v-for="t in problem.tags" :key="t">{{ t }}</view>
      </view>
    </view>

    <view class="similar-section" v-if="similarProblems.length > 0">
      <text class="similar-title">相似题目</text>
      <view class="similar-list">
        <view
          v-for="s in similarProblems"
          :key="s.id"
          class="similar-item"
          @tap="goToProblem(s.id)"
        >
          <text class="similar-name">{{ s.number }}. {{ s.title }}</text>
          <view class="difficulty-tag" :class="'difficulty-tag--' + s.difficulty">{{ difficultyLabel(s.difficulty) }}</view>
        </view>
      </view>
    </view>

    <view class="bottom-action">
      <button class="practice-btn" @tap="goToPractice">
        <image class="btn-icon" src="/static/icons/edit-3.svg" mode="aspectFit"></image>
        <text class="btn-text">去编程</text>
      </button>
    </view>
    </view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { getPythonProblemDetail, getPythonProblemList } from '@/api/pythonProblem.js'

const DIFFICULTY_LABELS = { easy: '简单', medium: '中等', hard: '困难' }

export default {
  components: { NavBar },
  data() {
    return {
      problemId: null,
      problem: null,
      problemMap: {},
      loadError: false,
    }
  },
  computed: {
    similarProblems() {
      if (!this.problem) return []
      var ids = this.problem.similarIds || []
      var map = this.problemMap
      var result = []
      ids.forEach(function (id) {
        if (map[id]) result.push(map[id])
      })
      return result
    }
  },
  onLoad(options) {
    this.problemId = Number(options.id) || 0
    this.loadProblem()
  },
  methods: {
    async loadProblem() {
      this.loadError = false
      try {
        var res = await getPythonProblemDetail(this.problemId)
        if (!res || !res.data) {
          this.loadError = true
          return
        }
        this.problem = res.data
        // 拉取一次列表用于解析相似题标题，避免逐题请求详情
        var listRes = await getPythonProblemList()
        var map = {}
        ;((listRes && listRes.data) || []).forEach(function (p) {
          map[p.id] = p
        })
        this.problemMap = map
      } catch (e) {
        console.error('加载题目详情失败:', e)
        this.loadError = true
      }
    },
    difficultyLabel(d) {
      return DIFFICULTY_LABELS[d] || d
    },
    goToProblem(id) {
      // 同类页面跳转用 redirectTo 替换当前详情页，避免页面栈累积
      uni.redirectTo({
        url: '/subpackage_learning/pythonOnlineIDE/problemDetail/problemDetail?id=' + id
      })
    },
    goToPractice() {
      uni.navigateTo({
        url: '/subpackage_learning/pythonOnlineIDE/practice/practice?id=' + this.problemId
      })
    }
  }
}
</script>

<style scoped>
.problem-detail-page {
  min-height: 100vh;
  background: #ffffff;
  padding-bottom: 140rpx;
}

.detail-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 160rpx 28rpx;
}

.detail-state-text {
  font-size: 28rpx;
  color: #9ca3af;
}

.detail-state-hint {
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

.problem-header {
  padding: 32rpx 28rpx;
  border-bottom: 1rpx solid #f3f4f6;
}

.problem-title-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 20rpx;
}

.problem-number {
  font-size: 32rpx;
  font-weight: 600;
  color: #111827;
}

.problem-title {
  font-size: 36rpx;
  font-weight: 700;
  color: #111827;
}

.difficulty-tag {
  padding: 6rpx 20rpx;
  border-radius: 8rpx;
  font-size: 24rpx;
  font-weight: 500;
  margin-left: auto;
}

.difficulty-tag--easy {
  background: #d1fae5;
  color: #065f46;
}

.difficulty-tag--medium {
  background: #ede9fe;
  color: #5b21b6;
}

.difficulty-tag--hard {
  background: #fecaca;
  color: #991b1b;
}

.problem-stats {
  display: flex;
  gap: 32rpx;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.stat-icon {
  width: 32rpx;
  height: 32rpx;
  opacity: 0.6;
}

.stat-text {
  font-size: 26rpx;
  color: #6b7280;
}

.problem-content {
  padding: 32rpx 28rpx;
}

.problem-desc {
  display: block;
  font-size: 28rpx;
  color: #374151;
  line-height: 1.8;
  white-space: pre-line;
}

.example-block {
  margin-top: 28rpx;
  padding: 24rpx;
  background: #f9fafb;
  border-radius: 16rpx;
  border-left: 6rpx solid #4f46e5;
}

.example-title {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
  color: #111827;
  margin-bottom: 12rpx;
}

.example-text {
  display: block;
  font-size: 26rpx;
  color: #374151;
  font-family: 'Courier New', monospace;
  line-height: 1.6;
}

.example-explain {
  display: block;
  font-size: 26rpx;
  color: #6b7280;
  margin-top: 12rpx;
}

.tags-section {
  padding: 0 28rpx 24rpx;
}

.tags-label {
  display: block;
  font-size: 26rpx;
  color: #6b7280;
  margin-bottom: 16rpx;
}

.tags-list {
  display: flex;
  gap: 16rpx;
  flex-wrap: wrap;
}

.tag {
  padding: 8rpx 20rpx;
  background: #f3f4f6;
  border-radius: 999rpx;
  font-size: 24rpx;
  color: #4b5563;
}

.similar-section {
  padding: 24rpx 28rpx;
  border-top: 1rpx solid #f3f4f6;
}

.similar-title {
  font-size: 28rpx;
  font-weight: 600;
  color: #111827;
  margin-bottom: 20rpx;
}

.similar-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.similar-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20rpx 24rpx;
  background: #f9fafb;
  border-radius: 16rpx;
}

.similar-item:active {
  background: #f3f4f6;
}

.similar-name {
  font-size: 26rpx;
  color: #374151;
}

.bottom-action {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 20rpx 28rpx;
  padding-bottom: calc(20rpx + env(safe-area-inset-bottom));
  background: #ffffff;
  box-shadow: 0 -4rpx 20rpx rgba(0, 0, 0, 0.05);
}

.practice-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
  height: 96rpx;
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
  border-radius: 48rpx;
  border: none;
  color: #ffffff;
  font-size: 30rpx;
  font-weight: 600;
}

.btn-icon {
  width: 36rpx;
  height: 36rpx;
}

.btn-text {
  color: #ffffff;
}
</style>