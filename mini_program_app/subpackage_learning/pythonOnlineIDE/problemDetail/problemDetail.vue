<template>
  <view class="problem-detail-page">
    <nav-bar title="题目详情" :showBack="true" />

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
        <text class="example-text" v-for="(line, li) in ex.lines" :key="li">{{ line }}</text>
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
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'

const PROBLEMS = {
  1: {
    id: 1, number: 1, difficulty: 'easy', passRate: 45.2, submissions: '12.1M',
    title: '两数之和',
    description: '给定一个整数数组 nums 和一个整数目标值 target，请你在该数组中找出 和为目标值 target 的那 两个 整数，并返回它们的数组下标。\n\n你可以假设每种输入只会对应一个答案。但是，数组中同一个元素在答案里不能重复出现。\n\n你可以按任意顺序返回答案。',
    examples: [{ lines: ['输入：nums = [2,7,11,15], target = 9','输出：[0,1]'], explain: '解释：因为 nums[0] + nums[1] == 9 ，返回 [0, 1] 。' }],
    tags: ['数组','哈希表'],
    similarIds: [15, 167, 170]
  },
  15: {
    id: 15, number: 15, difficulty: 'medium', passRate: 30.8, submissions: '8.4M',
    title: '三数之和',
    description: '给你一个包含 n 个整数的数组 nums，判断 nums 中是否存在三个元素 a，b，c ，使得 a + b + c = 0 ？请你找出所有和为 0 且不重复的三元组。\n\n注意：答案中不可以包含重复的三元组。',
    examples: [{ lines: ['输入：nums = [-1,0,1,2,-1,-4]','输出：[[-1,-1,2],[-1,0,1]]'], explain: '解释：nums[0] + nums[1] + nums[2] = 0 。' }],
    tags: ['数组','双指针','排序'],
    similarIds: [1, 18]
  },
  3: {
    id: 3, number: 3, difficulty: 'medium', passRate: 28.5, submissions: '10.2M',
    title: '无重复字符的最长子串',
    description: '给定一个字符串 s ，请你找出其中不含有重复字符的 最长子串 的长度。',
    examples: [{ lines: ['输入：s = "abcabcbb"','输出：3'], explain: '解释：因为无重复字符的最长子串是 "abc"，所以其长度为 3。' }],
    tags: ['字符串','滑动窗口','哈希表'],
    similarIds: [5, 76, 159]
  },
  20: {
    id: 20, number: 20, difficulty: 'easy', passRate: 42.1, submissions: '9.8M',
    title: '有效括号',
    description: '给定一个只包括 (，)，{，}，[，] 的字符串 s ，判断字符串是否有效。\n\n有效字符串需满足：\n1. 左括号必须用相同类型的右括号闭合。\n2. 左括号必须以正确的顺序闭合。',
    examples: [{ lines: ['输入：s = "()"','输出：true'] },{ lines: ['输入：s = "()[]{}"','输出：true'] }],
    tags: ['栈','字符串'],
    similarIds: [155, 32, 84]
  },
  70: {
    id: 70, number: 70, difficulty: 'easy', passRate: 48.7, submissions: '6.5M',
    title: '爬楼梯',
    description: '假设你正在爬楼梯。需要 n 阶你才能到达楼顶。\n\n每次你可以爬 1 或 2 个台阶。你有多少种不同的方法可以爬到楼顶呢？',
    examples: [{ lines: ['输入：n = 2','输出：2'], explain: '解释：有两种方法可以爬到楼顶。\n1. 1 阶 + 1 阶\n2. 2 阶' }],
    tags: ['动态规划','数学'],
    similarIds: [121, 42, 53]
  },
  206: {
    id: 206, number: 206, difficulty: 'easy', passRate: 60.5, submissions: '7.1M',
    title: '反转链表',
    description: '给你单链表的头节点 head ，请你反转链表，并返回反转后的链表。',
    examples: [{ lines: ['输入：head = [1,2,3,4,5]','输出：[5,4,3,2,1]'] }],
    tags: ['链表','递归','迭代'],
    similarIds: [141, 146, 92]
  },
}

// Sample fallback for ids not in PROBLEMS
function defaultProblem(id) {
  id = Number(id) || 1
  return {
    id: id, number: id, difficulty: 'easy', passRate: 40, submissions: '5.0M',
    title: '未知题目 #' + id,
    description: '请前往编程页面查看题目详情。',
    examples: [],
    tags: [],
    similarIds: []
  }
}

const DIFFICULTY_LABELS = { easy: '简单', medium: '中等', hard: '困难' }

export default {
  components: { NavBar },
  data() {
    return {
      problemId: null,
    }
  },
  computed: {
    problem() {
      return PROBLEMS[this.problemId] || defaultProblem(this.problemId)
    },
    similarProblems() {
      var ids = this.problem.similarIds || []
      var self = this
      return ids.map(function(id) {
        return PROBLEMS[id] || { id: id, number: id, difficulty: 'easy', title: '未知 #' + id }
      })
    }
  },
  onLoad(options) {
    this.problemId = Number(options.id) || 1
  },
  methods: {
    difficultyLabel(d) {
      return DIFFICULTY_LABELS[d] || d
    },
    goToProblem(id) {
      uni.navigateTo({
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