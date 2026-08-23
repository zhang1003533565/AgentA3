<template>
  <view class="learning-page">
    <nav-bar title="Python 个性化学习" :showBack="true" fixed placeholder />

    <view class="hero">
      <text class="hero__eyebrow">PYTHON LEARNING COPILOT</text>
      <text class="hero__title">按你的基础，生成下一步</text>
      <text class="hero__desc">画像、资源、路径与答题反馈由同一条学习主线持续更新。</text>
      <view class="hero__progress">
        <view><text class="hero__metric">{{ profileCompleteness }}%</text><text class="hero__metric-label">画像完整度</text></view>
        <view class="hero__progress-track"><view class="hero__progress-value" :style="{ width: `${profileCompleteness}%` }"></view></view>
      </view>
    </view>

    <view v-if="pageState !== 'ready'" class="state-card">
      <text class="state-card__title">{{ currentStateCopy.title }}</text>
      <text class="state-card__desc">{{ currentStateCopy.description }}</text>
      <button v-if="pageState !== 'loading'" class="state-card__button" @tap="handleStateAction">
        {{ pageState === 'empty' ? '补充学习画像' : currentStateCopy.action }}
      </button>
    </view>

    <template v-else>
      <view class="action-grid">
        <view class="action-card action-card--primary" @tap="navigate('/subpackage_learning/resourceGenerate/resourceGenerate')">
          <text class="action-card__icon">✦</text><text class="action-card__title">生成资源包</text><text class="action-card__desc">讲义、练习、代码与课件协同生成</text>
        </view>
        <view class="action-card" @tap="profileVisible = true">
          <text class="action-card__icon">◎</text><text class="action-card__title">补充学习画像</text><text class="action-card__desc">用自然语言告诉系统你的变化</text>
        </view>
        <view class="action-card action-card--wide" @tap="navigate('/subpackage_learning/knowledgeGraph/knowledgeGraph')">
          <view class="action-card__graph-icon"><view></view><view></view><view></view></view>
          <text class="action-card__title">知识图谱</text><text class="action-card__desc">查看知识依赖、薄弱节点与掌握证据</text>
        </view>
      </view>

      <view class="section-card">
        <view class="section-head"><text class="section-title">今日学习任务</text><text class="section-link" @tap="navigate('/subpackage_learning/learningPath/learningPath')">完整路径 →</text></view>
        <view v-if="todayTasks.length" class="task-list">
          <view v-for="(item, index) in todayTasks" :key="item.id || item.itemKey || index" class="task-item" @tap="openPathItem(item)">
            <text class="task-item__sequence">{{ index + 1 }}</text>
            <view class="task-item__copy"><text class="task-item__title">{{ item.knowledgePoint || item.objective || 'Python 学习任务' }}</text><text class="task-item__desc">{{ item.objective || item.rationale || '按照路径完成本节学习' }}</text></view>
            <text class="task-item__status">{{ item.status || '待开始' }}</text>
          </view>
        </view>
        <text v-else class="section-empty">当前路径暂无待办任务，可重新规划生成下一步。</text>
      </view>

      <view class="section-card">
        <view class="section-head"><text class="section-title">知识掌握概览</text><text class="section-caption">来自学习与答题证据</text></view>
        <view v-if="masteryItems.length" class="mastery-list">
          <view v-for="item in masteryItems" :key="item.label" class="mastery-item">
            <view class="mastery-item__head"><text>{{ item.label }}</text><text>{{ item.value }}%</text></view>
            <view class="mastery-item__track"><view class="mastery-item__value" :style="{ width: `${item.value}%` }"></view></view>
          </view>
        </view>
        <text v-else class="section-empty">完成练习后，这里会显示有证据的掌握度变化。</text>
      </view>

      <view class="section-card">
        <view class="section-head"><text class="section-title">精准推荐</text><text class="section-link" @tap="navigate('/subpackage_learning/recommendations/recommendations')">查看全部 →</text></view>
        <view v-if="recommendationPreview.length" class="recommendation-list">
          <view v-for="item in recommendationPreview" :key="item.itemId || item.id || item.itemKey" class="recommendation-item" @tap="navigate('/subpackage_learning/recommendations/recommendations')">
            <text class="recommendation-item__topic">{{ item.knowledgePoint || item.objective || 'Python 学习建议' }}</text>
            <text class="recommendation-item__reason">{{ item.rationale || '根据你的当前画像与路径推荐' }}</text>
          </view>
        </view>
        <text v-else class="section-empty">暂无新推荐，先完成画像补问或生成资源包。</text>
      </view>
    </template>

    <learning-profile-dialog
      :visible="profileVisible"
      :answered-question-ids="answeredQuestionIds"
      @close="profileVisible = false"
      @answered="handleProfileAnswered"
      @complete="handleProfileComplete"
    />
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import LearningProfileDialog from '@/components/learning-profile-dialog/learning-profile-dialog.vue'
import { getPythonHome } from '@/api/learning.js'
import { asList, buildQueryString, classifyLearningError, displayPercent, learningErrorMessage, responseData, stateCopy } from '@/subpackage_learning/learningView.js'

export default {
  components: { NavBar, LearningProfileDialog },
  data() {
    return { pageState: 'loading', errorMessage: '', home: {}, profileVisible: false }
  },
  computed: {
    currentStateCopy() { return stateCopy(this.pageState, this.errorMessage) },
    profileCompleteness() {
      return displayPercent(this.home.profileCompleteness ?? this.home.profile?.completeness ?? this.home.profile?.completionRate)
    },
    answeredQuestionIds() {
      return asList(this.home.profile?.answeredQuestionIds || this.home.answeredQuestionIds)
    },
    activePath() { return this.home.activePath || this.home.path || null },
    todayTasks() {
      const items = asList(this.home.todayTasks).length ? asList(this.home.todayTasks) : asList(this.activePath?.items)
      return items.filter(item => !['COMPLETED', 'DONE'].includes(String(item.status || '').toUpperCase())).slice(0, 3)
    },
    recommendationPreview() {
      return asList(this.home.recommendations, ['items']).slice(0, 2)
    },
    masteryItems() {
      const mastery = this.home.mastery?.knowledgePoints || this.home.mastery?.items || this.home.mastery
      if (Array.isArray(mastery)) {
        return mastery.slice(0, 5).map(item => ({
          label: item.knowledgePointName || item.knowledgePointKey || item.knowledgePoint || item.name || item.label || 'Python 知识点',
          value: displayPercent(item.mastery ?? item.score ?? item.value)
        }))
      }
      if (!mastery || typeof mastery !== 'object') return []
      return Object.entries(mastery).filter(([, value]) => Number.isFinite(Number(value))).slice(0, 5)
        .map(([label, value]) => ({ label, value: displayPercent(value) }))
    }
  },
  onLoad() { this.loadHome() },
  onPullDownRefresh() { this.loadHome(true) },
  methods: {
    async loadHome(fromRefresh = false) {
      this.pageState = 'loading'
      this.errorMessage = ''
      try {
        const data = responseData(await getPythonHome()) || {}
        this.home = data
        const hasLearningData = Boolean(data.activePath || data.path || data.mastery || data.profile || data.profileCompleteness || asList(data.recommendations, ['items']).length)
        this.pageState = hasLearningData ? 'ready' : 'empty'
      } catch (error) {
        this.pageState = classifyLearningError(error)
        this.errorMessage = learningErrorMessage(error)
      } finally {
        if (fromRefresh) uni.stopPullDownRefresh?.()
      }
    },
    retryLoad() { return this.loadHome() },
    handleStateAction() {
      if (this.pageState === 'empty') this.profileVisible = true
      else this.retryLoad()
    },
    handleProfileAnswered(event) {
      const ids = new Set(this.answeredQuestionIds)
      if (event?.questionId) ids.add(event.questionId)
      this.home = { ...this.home, answeredQuestionIds: [...ids], ...(event?.profile ? { profile: event.profile } : {}) }
    },
    handleProfileComplete() {
      this.profileVisible = false
      this.loadHome()
    },
    openPathItem(item) {
      const query = buildQueryString({ pathItemId: item.id, topic: item.knowledgePoint || item.objective || '' })
      this.navigate(`/subpackage_learning/resourceGenerate/resourceGenerate${query ? `?${query}` : ''}`)
    },
    navigate(url) { uni.navigateTo({ url }) }
  }
}
</script>

<style scoped>
.learning-page{min-height:100vh;padding:24rpx 24rpx 70rpx;background:#f5f7fb;box-sizing:border-box;color:#172033}.hero{padding:38rpx 32rpx;border-radius:30rpx;background:linear-gradient(135deg,#312e81,#4f46e5 52%,#2563eb);color:#fff;box-shadow:0 18rpx 50rpx rgba(79,70,229,.22)}.hero__eyebrow,.hero__title,.hero__desc,.hero__metric,.hero__metric-label{display:block}.hero__eyebrow{font-size:20rpx;font-weight:700;letter-spacing:3rpx;opacity:.75}.hero__title{margin-top:18rpx;font-size:44rpx;font-weight:800}.hero__desc{margin-top:12rpx;font-size:25rpx;line-height:1.6;opacity:.88}.hero__progress{display:flex;align-items:center;gap:24rpx;margin-top:30rpx}.hero__metric{font-size:38rpx;font-weight:750}.hero__metric-label{font-size:20rpx;opacity:.72}.hero__progress-track{flex:1;height:12rpx;border-radius:999rpx;background:rgba(255,255,255,.2);overflow:hidden}.hero__progress-value{height:100%;border-radius:inherit;background:#fff}
.state-card,.section-card{margin-top:24rpx;padding:28rpx;border-radius:20rpx;background:#fff;box-shadow:0 10rpx 32rpx rgba(15,23,42,.045)}.state-card{text-align:center;padding:70rpx 34rpx}.state-card__title,.state-card__desc{display:block}.state-card__title{font-size:32rpx;font-weight:750}.state-card__desc{margin-top:14rpx;color:#64748b;font-size:25rpx;line-height:1.6}.state-card__button{margin-top:28rpx;border:0;border-radius:16rpx;background:#4f46e5;color:#fff;font-size:26rpx}
.action-grid{display:grid;grid-template-columns:1fr 1fr;gap:18rpx;margin-top:24rpx}.action-card{padding:26rpx;border-radius:20rpx;background:#fff}.action-card--primary{background:#eef2ff}.action-card__icon,.action-card__title,.action-card__desc{display:block}.action-card__icon,.action-card__title,.action-card__desc{display:block}.action-card__icon{color:#4f46e5;font-size:34rpx}.action-card__title{margin-top:12rpx;font-size:29rpx;font-weight:750}.action-card__desc{margin-top:8rpx;color:#64748b;font-size:22rpx;line-height:1.5}
.action-card--wide{grid-column:1/-1}.action-card__graph-icon{position:relative;width:52rpx;height:34rpx}.action-card__graph-icon view{position:absolute;width:12rpx;height:12rpx;border:3rpx solid #526f8d;border-radius:50%;background:#fff}.action-card__graph-icon view:nth-child(1){left:0;top:11rpx}.action-card__graph-icon view:nth-child(2){left:20rpx;top:0}.action-card__graph-icon view:nth-child(3){right:0;bottom:0}.action-card__graph-icon:before,.action-card__graph-icon:after{content:'';position:absolute;left:10rpx;width:30rpx;height:3rpx;background:#9babbc;transform-origin:left center}.action-card__graph-icon:before{top:16rpx;transform:rotate(-24deg)}.action-card__graph-icon:after{top:18rpx;transform:rotate(20deg)}
.section-head{display:flex;align-items:center;justify-content:space-between;gap:18rpx}.section-title{font-size:31rpx;font-weight:750}.section-link{color:#4f46e5;font-size:23rpx}.section-caption{color:#94a3b8;font-size:21rpx}.section-empty{display:block;margin-top:22rpx;color:#94a3b8;font-size:24rpx;line-height:1.6}.task-item{display:flex;align-items:center;gap:18rpx;padding:22rpx 0;border-bottom:1px solid #eef2f7}.task-item:last-child{border-bottom:0}.task-item__sequence{width:48rpx;height:48rpx;border-radius:14rpx;background:#eef2ff;color:#4338ca;text-align:center;line-height:48rpx;font-weight:700}.task-item__copy{flex:1}.task-item__title,.task-item__desc{display:block}.task-item__title{font-size:27rpx;font-weight:680}.task-item__desc{margin-top:6rpx;color:#64748b;font-size:22rpx}.task-item__status{color:#6366f1;font-size:21rpx}.mastery-item{margin-top:22rpx}.mastery-item__head{display:flex;justify-content:space-between;color:#475569;font-size:24rpx}.mastery-item__track{height:10rpx;margin-top:10rpx;border-radius:999rpx;background:#e7eaf0;overflow:hidden}.mastery-item__value{height:100%;border-radius:inherit;background:linear-gradient(90deg,#6366f1,#22c55e)}.recommendation-item{margin-top:18rpx;padding:20rpx;border-radius:16rpx;background:#f8fafc}.recommendation-item__topic,.recommendation-item__reason{display:block}.recommendation-item__topic{font-size:26rpx;font-weight:680}.recommendation-item__reason{margin-top:8rpx;color:#64748b;font-size:22rpx;line-height:1.5}
</style>
