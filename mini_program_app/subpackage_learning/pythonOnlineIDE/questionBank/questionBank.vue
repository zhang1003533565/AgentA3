<template>
  <view class="question-bank-page">
    <nav-bar title="题库" :showBack="true" />

    <view class="search-bar">
      <view class="search-bar-placeholder" @tap="focusSearch">
        <image class="search-icon" src="/static/icons/search.svg" mode="aspectFit"></image>
        <input
          class="search-input"
          v-model="searchKeyword"
          placeholder="搜索题目、标签或题号..."
          placeholder-class="search-placeholder"
          confirm-type="search"
          :focus="searchFocused"
          @confirm="onSearch"
          @blur="searchFocused = false"
        />
        <view v-if="searchKeyword" class="search-clear" @tap.stop="clearSearch">×</view>
      </view>
    </view>

    <view class="progress-card">
      <view class="progress-header">
        <text class="progress-label">当前进度</text>
        <text class="progress-sub">{{ doneCount }}/{{ totalCount }} 已解决</text>
      </view>
      <view class="progress-track">
        <view class="progress-fill" :style="{ width: progressPercent + '%' }"></view>
      </view>
      <view class="progress-stats">
        <view class="stat-item" @tap="selectDifficulty('all')">
          <view class="stat-dot stat-dot--easy"></view>
          <text class="stat-text">简单</text>
          <text class="stat-num">{{ easyTotal }}</text>
        </view>
        <view class="stat-item" @tap="selectDifficulty('all')">
          <view class="stat-dot stat-dot--medium"></view>
          <text class="stat-text">中等</text>
          <text class="stat-num">{{ mediumTotal }}</text>
        </view>
        <view class="stat-item" @tap="selectDifficulty('all')">
          <view class="stat-dot stat-dot--hard"></view>
          <text class="stat-text">困难</text>
          <text class="stat-num">{{ hardTotal }}</text>
        </view>
      </view>
    </view>

    <view class="filter-section">
      <view class="filter-label">难度</view>
      <scroll-view scroll-x class="filter-scroll" :show-scrollbar="false">
        <view class="filter-row">
          <view
            v-for="d in difficultyOptions"
            :key="d.key"
            class="filter-chip"
            :class="{ 'filter-chip--active': activeDifficulty === d.key }"
            @tap="selectDifficulty(d.key)"
          >
            <text>{{ d.label }}</text>
            <text v-if="d.count !== null" class="filter-chip-count">{{ d.count }}</text>
          </view>
        </view>
      </scroll-view>
    </view>

    <view class="filter-section">
      <view class="filter-label">状态</view>
      <scroll-view scroll-x class="filter-scroll" :show-scrollbar="false">
        <view class="filter-row">
          <view
            v-for="s in statusOptions"
            :key="s.key"
            class="filter-chip"
            :class="{ 'filter-chip--active': activeStatus === s.key }"
            @tap="selectStatus(s.key)"
          >
            <text>{{ s.label }}</text>
            <text v-if="s.count !== null" class="filter-chip-count">{{ s.count }}</text>
          </view>
        </view>
      </scroll-view>
    </view>

    <view class="filter-section">
      <view class="filter-label">标签</view>
      <view class="tag-grid">
        <view
          v-for="t in allTags"
          :key="t.name"
          class="tag-chip"
          :class="{ 'tag-chip--active': activeTags.indexOf(t.name) !== -1 }"
          @tap="toggleTag(t.name)"
        >
          <text>{{ t.name }}</text>
          <text class="tag-chip-count">{{ t.count }}</text>
        </view>
      </view>
    </view>

    <view class="result-header">
      <text class="result-title">题目列表</text>
      <text class="result-count">{{ filteredQuestions.length }} 道</text>
    </view>

    <view class="question-list">
      <view
        v-for="q in filteredQuestions"
        :key="q.id"
        class="question-item"
        @tap="goToDetail(q.id)"
      >
        <view class="question-status" :class="{ 'question-status--done': q.done }">
          <image v-if="q.done" class="status-icon" src="/static/icons/line-md/check-circle-twotone.png" mode="aspectFit"></image>
          <view v-else class="empty-circle"></view>
        </view>
        <view class="question-content">
          <view class="question-title-row">
            <text class="question-number">{{ q.number }}.</text>
            <text class="question-title">{{ q.title }}</text>
            <view class="difficulty-tag" :class="'difficulty-tag--' + q.difficulty">{{ difficultyLabel(q.difficulty) }}</view>
          </view>
          <view class="question-meta">
            <text class="meta-item">通过率 {{ q.passRate }}%</text>
            <view class="meta-tags-row">
              <text
                v-for="t in q.tags"
                :key="t"
                class="meta-tag"
                @tap.stop="toggleTag(t)"
              >{{ t }}</text>
            </view>
          </view>
          <view class="question-footer">
            <image class="star-icon" src="/static/icons/star.svg" mode="aspectFit"></image>
          </view>
        </view>
      </view>
      <view v-if="filteredQuestions.length === 0" class="empty-state">
        <text class="empty-state-text">未找到匹配的题目</text>
        <text class="empty-state-hint">试试调整筛选条件</text>
      </view>
    </view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'

const ALL_QUESTIONS = [
  { id: 1,   number: 1,   title: '两数之和',           difficulty: 'easy',   passRate: 45.2, tags: ['数组','哈希表'],                                          done: true  },
  { id: 15,  number: 15,  title: '三数之和',           difficulty: 'medium', passRate: 30.8, tags: ['数组','双指针','排序'],                              done: false },
  { id: 20,  number: 20,  title: '有效括号',           difficulty: 'easy',   passRate: 42.1, tags: ['栈','字符串'],                                                done: false },
  { id: 3,   number: 3,   title: '无重复字符的最长子串', difficulty: 'medium', passRate: 28.5, tags: ['字符串','滑动窗口','哈希表'],          done: true  },
  { id: 5,   number: 5,   title: '最长回文子串',        difficulty: 'medium', passRate: 32.4, tags: ['字符串','动态规划'],                              done: true  },
  { id: 10,  number: 10,  title: '正则表达式匹配',      difficulty: 'hard',   passRate: 18.2, tags: ['字符串','动态规划','递归'],                done: false },
  { id: 50,  number: 50,  title: 'Pow(x, n)',         difficulty: 'medium', passRate: 35.6, tags: ['数学','递归'],                                                  done: false },
  { id: 69,  number: 69,  title: 'x 的平方根',         difficulty: 'easy',   passRate: 40.3, tags: ['数学','二分查找'],                                          done: true  },
  { id: 70,  number: 70,  title: '爬楼梯',             difficulty: 'easy',   passRate: 48.7, tags: ['动态规划','数学'],                                        done: true  },
  { id: 121, number: 121, title: '买卖股票的最佳时机', difficulty: 'easy',   passRate: 52.3, tags: ['数组','贪心','动态规划'],                    done: false },
  { id: 42,  number: 42,  title: '接雨水',             difficulty: 'hard',   passRate: 22.1, tags: ['数组','动态规划','双指针','单调栈'],        done: false },
  { id: 84,  number: 84,  title: '柱状图中最大矩形',    difficulty: 'hard',   passRate: 19.8, tags: ['数组','单调栈','栈'],                                done: true  },
  { id: 155, number: 155, title: '最小栈',             difficulty: 'easy',   passRate: 40.9, tags: ['栈','设计'],                                                    done: false },
  { id: 206, number: 206, title: '反转链表',           difficulty: 'easy',   passRate: 60.5, tags: ['链表','递归','迭代'],                                        done: true  },
  { id: 146, number: 146, title: 'LRU 缓存',           difficulty: 'medium', passRate: 38.2, tags: ['设计','哈希表','链表'],                                        done: false },
  { id: 53,  number: 53,  title: '最大子数组和',       difficulty: 'medium', passRate: 50.4, tags: ['数组','动态规划','分治'],                        done: false },
  { id: 215, number: 215, title: '数组中的第K个最大元素', difficulty: 'medium', passRate: 45.8, tags: ['数组','堆','排序','分治'],                    done: false },
  { id: 200, number: 200, title: '岛屿数量',           difficulty: 'medium', passRate: 38.9, tags: ['深度优先搜索','广度优先搜索','并查集'],   done: false },
  { id: 46,  number: 46,  title: '全排列',             difficulty: 'medium', passRate: 52.1, tags: ['数组','回溯'],                                                  done: true  },
  { id: 141, number: 141, title: '环形链表',           difficulty: 'easy',   passRate: 48.3, tags: ['链表','双指针','哈希表'],                              done: true  },
]

const DIFFICULTY_LABELS = { easy: '简单', medium: '中等', hard: '困难' }

export default {
  components: { NavBar },
  data() {
    return {
      questions: ALL_QUESTIONS,
      searchKeyword: '',
      activeDifficulty: 'all',
      activeStatus: 'all',
      activeTags: [],
      searchFocused: false,
    }
  },
  computed: {
    allTags() {
      var map = {}
      var self = this
      this.questions.forEach(function(q) {
        q.tags.forEach(function(t) {
          map[t] = (map[t] || 0) + 1
        })
      })
      var result = Object.keys(map).map(function(k) {
        return { name: k, count: map[k] }
      })
      result.sort(function(a, b) { return b.count - a.count })
      return result
    },
    difficultyOptions() {
      return [
        { key: 'all',    label: '全部', count: this.totalCount },
        { key: 'easy',   label: '简单', count: this.easyTotal },
        { key: 'medium', label: '中等', count: this.mediumTotal },
        { key: 'hard',   label: '困难', count: this.hardTotal },
      ]
    },
    statusOptions() {
      return [
        { key: 'all',      label: '全部', count: this.totalCount },
        { key: 'done',     label: '已完成', count: this.doneCount },
        { key: 'undone',   label: '未完成', count: this.totalCount - this.doneCount },
      ]
    },
    filteredQuestions() {
      var list = this.questions
      if (this.activeDifficulty !== 'all') {
        list = list.filter(function(q) { return q.difficulty === this.activeDifficulty }.bind(this))
      }
      if (this.activeStatus === 'done') {
        list = list.filter(function(q) { return q.done })
      } else if (this.activeStatus === 'undone') {
        list = list.filter(function(q) { return !q.done })
      }
      if (this.activeTags.length > 0) {
        var tags = this.activeTags
        list = list.filter(function(q) {
          return tags.some(function(t) { return q.tags.indexOf(t) !== -1 })
        })
      }
      if (this.searchKeyword.trim()) {
        var kw = this.searchKeyword.trim().toLowerCase()
        list = list.filter(function(q) {
          return q.title.toLowerCase().indexOf(kw) !== -1 ||
                 q.tags.some(function(t) { return t.toLowerCase().indexOf(kw) !== -1 }) ||
                 String(q.number).indexOf(kw) !== -1
        })
      }
      return list
    },
    doneCount() {
      return this.questions.filter(function(q) { return q.done }).length
    },
    totalCount() {
      return this.questions.length
    },
    easyTotal() {
      return this.questions.filter(function(q) { return q.difficulty === 'easy' }).length
    },
    mediumTotal() {
      return this.questions.filter(function(q) { return q.difficulty === 'medium' }).length
    },
    hardTotal() {
      return this.questions.filter(function(q) { return q.difficulty === 'hard' }).length
    },
    easyCount() {
      return this.questions.filter(function(q) { return q.difficulty === 'easy' && q.done }).length
    },
    mediumCount() {
      return this.questions.filter(function(q) { return q.difficulty === 'medium' && q.done }).length
    },
    hardCount() {
      return this.questions.filter(function(q) { return q.difficulty === 'hard' && q.done }).length
    },
    progressPercent() {
      if (this.totalCount === 0) return 0
      return Math.round(this.doneCount / this.totalCount * 100)
    }
  },
  methods: {
    formatTags(tags) {
      return tags.map(function(t) { return '#' + t }).join(' ')
    },
    difficultyLabel(d) {
      return DIFFICULTY_LABELS[d] || d
    },
    focusSearch() {
      this.searchFocused = true
    },
    onSearch() {},
    clearSearch() {
      this.searchKeyword = ''
    },
    selectDifficulty(key) {
      this.activeDifficulty = key
    },
    selectStatus(key) {
      this.activeStatus = key
    },
    toggleTag(name) {
      var idx = this.activeTags.indexOf(name)
      if (idx === -1) {
        this.activeTags.push(name)
      } else {
        this.activeTags.splice(idx, 1)
      }
    },
    goToDetail(id) {
      uni.navigateTo({
        url: '/subpackage_learning/pythonOnlineIDE/problemDetail/problemDetail?id=' + id
      })
    }
  }
}
</script>

<style scoped>
.question-bank-page {
  min-height: 100vh;
  background: #f5f6f8;
  padding-bottom: 60rpx;
}

.search-bar {
  display: flex;
  align-items: center;
  padding: 20rpx 28rpx;
  background: #f5f6f8;
}

.search-bar-placeholder {
  display: flex;
  align-items: center;
  flex: 1;
  padding: 18rpx 24rpx;
  background: #ffffff;
  border-radius: 16rpx;
  border: 1rpx solid #eef1f4;
}

.search-icon {
  width: 32rpx;
  height: 32rpx;
  margin-right: 14rpx;
  opacity: 0.4;
  flex-shrink: 0;
}

.search-input {
  flex: 1;
  min-width: 0;
  font-size: 28rpx;
  color: #111827;
  height: 40rpx;
  line-height: 40rpx;
}

.search-placeholder {
  font-size: 28rpx;
  color: #9ca3af;
}

.search-clear {
  width: 40rpx;
  height: 40rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32rpx;
  color: #9ca3af;
  flex-shrink: 0;
}

.progress-card {
  margin: 12rpx 28rpx 8rpx;
  padding: 24rpx 28rpx;
  background: #ffffff;
  border-radius: 20rpx;
  border: 1rpx solid #eef1f4;
}

.progress-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 16rpx;
}

.progress-label {
  font-size: 26rpx;
  font-weight: 700;
  color: #111827;
}

.progress-sub {
  font-size: 23rpx;
  color: #6b7280;
}

.progress-track {
  height: 8rpx;
  background: #e5e7eb;
  border-radius: 999rpx;
  overflow: hidden;
  margin-bottom: 18rpx;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #4f46e5, #7c3aed);
  border-radius: 999rpx;
}

.progress-stats {
  display: flex;
  gap: 28rpx;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 6rpx;
}

.stat-dot {
  width: 14rpx;
  height: 14rpx;
  border-radius: 50%;
}

.stat-dot--easy {
  background: #10b981;
}

.stat-dot--medium {
  background: #8b5cf6;
}

.stat-dot--hard {
  background: #ef4444;
}

.stat-text {
  font-size: 24rpx;
  color: #6b7280;
}

.stat-num {
  font-size: 22rpx;
  color: #9ca3af;
  font-weight: 500;
}

/* Filter sections */
.filter-section {
  padding: 8rpx 28rpx 4rpx;
}

.filter-label {
  font-size: 24rpx;
  color: #9ca3af;
  margin-bottom: 12rpx;
  font-weight: 500;
}

.filter-scroll {
  white-space: nowrap;
}

.filter-row {
  display: inline-flex;
  gap: 12rpx;
  padding-bottom: 8rpx;
}

.filter-chip {
  display: inline-flex;
  align-items: center;
  gap: 6rpx;
  padding: 12rpx 22rpx;
  border-radius: 999rpx;
  font-size: 26rpx;
  color: #4b5563;
  background: #f3f4f6;
  flex-shrink: 0;
}

.filter-chip--active {
  background: #4f46e5;
  color: #ffffff;
}

.filter-chip-count {
  font-size: 20rpx;
  opacity: 0.7;
}

/* Tag grid */
.tag-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  padding-bottom: 8rpx;
}

.tag-chip {
  display: inline-flex;
  align-items: center;
  gap: 6rpx;
  padding: 10rpx 20rpx;
  border-radius: 999rpx;
  font-size: 24rpx;
  color: #4b5563;
  background: #f3f4f6;
}

.tag-chip--active {
  background: rgba(79, 70, 229, 0.1);
  color: #4f46e5;
  font-weight: 600;
}

.tag-chip-count {
  font-size: 20rpx;
  opacity: 0.55;
}

/* Result header */
.result-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 18rpx 28rpx 12rpx;
}

.result-title {
  font-size: 28rpx;
  font-weight: 700;
  color: #111827;
}

.result-count {
  font-size: 23rpx;
  color: #9ca3af;
}

/* Question list */
.question-list {
  padding: 0 28rpx;
}

.question-item {
  display: flex;
  align-items: flex-start;
  padding: 28rpx;
  margin-bottom: 14rpx;
  background: #ffffff;
  border-radius: 20rpx;
  border: 1rpx solid #eef1f4;
}

.question-status {
  width: 44rpx;
  height: 44rpx;
  border-radius: 50%;
  border: 3rpx solid #d1d5db;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 20rpx;
  flex-shrink: 0;
}

.question-status--done {
  border-color: #10b981;
  background: #10b981;
}

.status-icon {
  width: 24rpx;
  height: 24rpx;
}

.empty-circle {
  width: 20rpx;
  height: 20rpx;
  border-radius: 50%;
  border: 3rpx solid #d1d5db;
}

.question-content {
  flex: 1;
  min-width: 0;
}

.question-title-row {
  display: flex;
  align-items: center;
  gap: 10rpx;
  margin-bottom: 12rpx;
}

.question-number {
  font-size: 28rpx;
  font-weight: 600;
  color: #111827;
  flex-shrink: 0;
}

.question-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #111827;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.difficulty-tag {
  padding: 3rpx 14rpx;
  border-radius: 6rpx;
  font-size: 21rpx;
  font-weight: 600;
  margin-left: auto;
  flex-shrink: 0;
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

.question-meta {
  display: flex;
  flex-direction: column;
  gap: 10rpx;
  margin-bottom: 12rpx;
}

.meta-item {
  font-size: 24rpx;
  color: #6b7280;
}

.meta-tags-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10rpx;
}

.meta-tag {
  padding: 4rpx 14rpx;
  border-radius: 6rpx;
  font-size: 21rpx;
  color: #6b7280;
  background: #f3f4f6;
}

.question-footer {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.star-icon {
  width: 28rpx;
  height: 28rpx;
  opacity: 0.4;
}

/* Empty */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80rpx 28rpx;
}

.empty-state-text {
  font-size: 28rpx;
  color: #9ca3af;
  margin-bottom: 8rpx;
}

.empty-state-hint {
  font-size: 24rpx;
  color: #d1d5db;
}
</style>