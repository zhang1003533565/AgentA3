<template>
  <view class="learning-page">
    <nav-bar title="Python 精准推荐" :showBack="true" fixed placeholder />

    <view v-if="pageState !== 'ready'" class="state-card">
      <text class="state-card__title">{{ currentStateCopy.title }}</text>
      <text class="state-card__desc">{{ currentStateCopy.description }}</text>
      <button v-if="pageState !== 'loading'" @tap="retryLoad">{{ currentStateCopy.action || '重新加载' }}</button>
    </view>

    <template v-else>
      <view class="recommendation-intro">
        <text class="recommendation-intro__eyebrow">WHY THIS, WHY NOW</text>
        <text class="recommendation-intro__title">每条推荐都说明依据</text>
        <text class="recommendation-intro__desc">推荐来自当前画像、掌握度、路径优先级和已审核学习资源。</text>
      </view>

      <view v-for="(item, index) in recommendations" :key="item.itemId || item.id || item.itemKey || index" class="recommendation-card">
        <view class="recommendation-card__head">
          <text class="recommendation-card__priority">优先级 {{ item.priority ?? index + 1 }}</text>
          <text class="recommendation-card__status">{{ item.status || '待学习' }}</text>
        </view>
        <text class="recommendation-card__topic">{{ item.knowledgePoint || 'Python 学习任务' }}</text>
        <text class="recommendation-card__objective">{{ item.objective || '完成推荐知识点的理解与练习' }}</text>
        <view class="recommendation-card__reason"><text class="recommendation-card__reason-label">推荐理由</text><text>{{ item.rationale || '根据你的当前画像与学习进度推荐' }}</text></view>
        <view v-if="evidenceSummary(item)" class="recommendation-card__evidence">依据：{{ evidenceSummary(item) }}</view>
        <view class="recommendation-card__actions">
          <button class="recommendation-card__action recommendation-card__action--ghost" :disabled="workingId === itemId(item)" @tap="interact(item, 'dismiss')">暂不需要</button>
          <button class="recommendation-card__action recommendation-card__action--secondary" :disabled="workingId === itemId(item)" @tap="interact(item, 'complete')">已掌握</button>
          <button class="recommendation-card__action" :disabled="workingId === itemId(item)" @tap="interact(item, 'open')">开始学习</button>
        </view>
      </view>
    </template>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { getPythonRecommendations, recordRecommendationInteraction } from '@/api/learning.js'
import { asList, buildQueryString, classifyLearningError, learningErrorMessage, responseData, stateCopy } from '@/subpackage_learning/learningView.js'

export default {
  components: { NavBar },
  data() { return { pageState: 'loading', errorMessage: '', recommendations: [], workingId: '', viewedIds: {} } },
  computed: { currentStateCopy() { return stateCopy(this.pageState, this.errorMessage) } },
  onLoad() { this.loadRecommendations() },
  onPullDownRefresh() { this.loadRecommendations(true) },
  methods: {
    itemId(item) { return String(item?.itemId || item?.id || '').trim() },
    async loadRecommendations(fromRefresh = false) {
      this.pageState = 'loading'
      this.errorMessage = ''
      try {
        const data = responseData(await getPythonRecommendations())
        this.recommendations = asList(data, ['items', 'recommendations'])
        this.pageState = this.recommendations.length ? 'ready' : 'empty'
        if (this.recommendations.length) this.reportViews()
      } catch (error) {
        this.pageState = classifyLearningError(error)
        this.errorMessage = learningErrorMessage(error)
      } finally {
        if (fromRefresh) uni.stopPullDownRefresh?.()
      }
    },
    retryLoad() { return this.loadRecommendations() },
    reportViews() {
      for (const item of this.recommendations) {
        const id = this.itemId(item)
        if (!id || this.viewedIds[id]) continue
        this.viewedIds = { ...this.viewedIds, [id]: true }
        recordRecommendationInteraction(id, { action: 'view' }).catch(() => {
          const next = { ...this.viewedIds }; delete next[id]; this.viewedIds = next
        })
      }
    },
    async interact(item, action) {
      const id = this.itemId(item)
      if (!id || this.workingId) return
      this.workingId = id
      try {
        await recordRecommendationInteraction(id, { action })
        if (action === 'open') {
          const query = buildQueryString({ pathItemId: id, topic: item.knowledgePoint || item.objective || '' })
          uni.navigateTo({ url: `/subpackage_learning/resourceGenerate/resourceGenerate${query ? `?${query}` : ''}` })
        } else {
          await this.loadRecommendations()
        }
      } catch (error) {
        uni.showToast({ title: learningErrorMessage(error, '推荐状态更新失败'), icon: 'none' })
      } finally { this.workingId = '' }
    },
    evidenceSummary(item) {
      const evidence = item.evidenceSummary || item.evidenceStatus || item.evidenceChain?.status
      if (evidence) return evidence
      const ids = item.evidenceIds || []
      return Array.isArray(ids) && ids.length ? `${ids.length} 条课程依据` : ''
    }
  }
}
</script>

<style scoped>
.learning-page{min-height:100vh;padding:24rpx 24rpx 80rpx;background:#f5f7fb;box-sizing:border-box;color:#172033}.state-card{padding:80rpx 34rpx;border-radius:24rpx;background:#fff;text-align:center}.state-card__title,.state-card__desc{display:block}.state-card__title{font-size:32rpx;font-weight:760}.state-card__desc{margin-top:14rpx;color:#64748b;font-size:25rpx;line-height:1.6}.state-card button{margin-top:26rpx;border:0;background:#4f46e5;color:#fff}.recommendation-intro{padding:34rpx;border-radius:26rpx;background:linear-gradient(135deg,#172554,#3730a3);color:#fff}.recommendation-intro__eyebrow,.recommendation-intro__title,.recommendation-intro__desc{display:block}.recommendation-intro__eyebrow{font-size:20rpx;font-weight:750;letter-spacing:3rpx;opacity:.68}.recommendation-intro__title{margin-top:15rpx;font-size:38rpx;font-weight:800}.recommendation-intro__desc{margin-top:12rpx;font-size:24rpx;line-height:1.6;opacity:.82}.recommendation-card{margin-top:22rpx;padding:28rpx;border-radius:24rpx;background:#fff;box-shadow:0 10rpx 32rpx rgba(15,23,42,.045)}.recommendation-card__head{display:flex;justify-content:space-between}.recommendation-card__priority{color:#4f46e5;font-size:21rpx;font-weight:700}.recommendation-card__status{color:#64748b;font-size:21rpx}.recommendation-card__topic,.recommendation-card__objective{display:block}.recommendation-card__topic{margin-top:16rpx;font-size:32rpx;font-weight:760}.recommendation-card__objective{margin-top:10rpx;color:#475569;font-size:24rpx;line-height:1.6}.recommendation-card__reason{display:flex;flex-direction:column;gap:8rpx;margin-top:20rpx;padding:18rpx;border-radius:15rpx;background:#f8fafc;color:#64748b;font-size:23rpx;line-height:1.55}.recommendation-card__reason-label{color:#334155;font-weight:700}.recommendation-card__evidence{margin-top:14rpx;color:#047857;font-size:21rpx}.recommendation-card__actions{display:flex;gap:10rpx;margin-top:22rpx}.recommendation-card__action{flex:1;margin:0;padding:0 10rpx;border:0;border-radius:13rpx;background:#4f46e5;color:#fff;font-size:22rpx}.recommendation-card__action--secondary{background:#eef2ff;color:#4338ca}.recommendation-card__action--ghost{background:#f8fafc;color:#64748b}
</style>
