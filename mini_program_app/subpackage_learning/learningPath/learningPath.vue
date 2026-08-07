<template>
  <view class="learning-page">
    <nav-bar title="Python 学习路径" :showBack="true" fixed placeholder />

    <view v-if="pageState !== 'ready'" class="state-card">
      <text class="state-card__title">{{ currentStateCopy.title }}</text>
      <text class="state-card__desc">{{ currentStateCopy.description }}</text>
      <button v-if="pageState !== 'loading'" @tap="retryLoad">{{ pageState === 'empty' ? '生成个性化资源包' : currentStateCopy.action }}</button>
    </view>

    <template v-else>
      <view class="path-summary">
        <view><text class="path-summary__eyebrow">PATH VERSION {{ path.version || 1 }}</text><text class="path-summary__title">{{ path.goal || 'Python 个性化学习路径' }}</text></view>
        <text class="path-summary__status">{{ statusLabel(path.status) }}</text>
        <text class="path-summary__desc">{{ path.profileDigest || path.masteryDigest || '路径依据当前画像、掌握度与课程知识库生成。' }}</text>
        <button class="path-summary__replan" :disabled="working" @tap="replanPath">{{ working ? '正在同步…' : '根据最新画像重新规划' }}</button>
      </view>

      <view class="timeline">
        <view v-for="(item, index) in pathItems" :key="item.id || item.itemKey || index" class="path-item" :class="`path-item--${normalizedStatus(item.status)}`">
          <view class="path-item__rail"><text class="path-item__index">{{ index + 1 }}</text><view v-if="index < pathItems.length - 1" class="path-item__line"></view></view>
          <view class="path-item__card">
            <view class="path-item__head"><text class="path-item__topic">{{ item.knowledgePoint || `学习节点 ${index + 1}` }}</text><text class="path-item__status">{{ statusLabel(item.status) }}</text></view>
            <text class="path-item__objective">{{ item.objective || item.rationale || '完成本节点对应学习资源' }}</text>
            <view v-if="item.resourceKinds?.length" class="path-item__tags"><text v-for="kind in item.resourceKinds" :key="kind">{{ kindLabel(kind) }}</text></view>
            <text v-if="item.rationale" class="path-item__reason">推荐理由：{{ item.rationale }}</text>
            <view class="path-item__actions">
              <button class="path-item__action path-item__action--secondary" @tap="openResources(item)">查看资源</button>
              <button v-if="canStart(item)" class="path-item__action" :disabled="workingItemId === item.id" @tap="startItem(item)">开始学习</button>
              <button v-if="canComplete(item)" class="path-item__action" :disabled="workingItemId === item.id" @tap="completeItem(item)">完成本节</button>
            </view>
          </view>
        </view>
      </view>
    </template>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { completePathItem, getPythonPath, replanPythonPath, startPathItem } from '@/api/learning.js'
import { asList, buildQueryString, classifyLearningError, learningErrorMessage, responseData, stateCopy } from '@/subpackage_learning/learningView.js'

export default {
  components: { NavBar },
  data() { return { pageState: 'loading', errorMessage: '', path: {}, working: false, workingItemId: '' } },
  computed: {
    currentStateCopy() { return stateCopy(this.pageState, this.errorMessage) },
    pathItems() { return asList(this.path.items) }
  },
  onLoad() { this.loadPath() },
  onPullDownRefresh() { this.loadPath(true) },
  methods: {
    async loadPath(fromRefresh = false) {
      this.pageState = 'loading'
      this.errorMessage = ''
      try {
        const data = responseData(await getPythonPath()) || {}
        this.path = data.path || data
        this.pageState = this.path.id || asList(this.path.items).length ? 'ready' : 'empty'
      } catch (error) {
        this.pageState = classifyLearningError(error)
        this.errorMessage = learningErrorMessage(error)
      } finally {
        if (fromRefresh) uni.stopPullDownRefresh?.()
      }
    },
    retryLoad() {
      if (this.pageState === 'empty') {
        uni.navigateTo({ url: '/subpackage_learning/resourceGenerate/resourceGenerate' })
        return
      }
      return this.loadPath()
    },
    normalizedStatus(status) { return String(status || 'pending').toLowerCase() },
    statusLabel(status) {
      const value = String(status || '').toUpperCase()
      return { ACTIVE: '进行中', PENDING: '待开始', NOT_STARTED: '待开始', RECOMMENDED: '待开始', IN_PROGRESS: '学习中', STARTED: '学习中', COMPLETED: '已完成', DONE: '已完成', PAUSED: '已暂停', SUPERSEDED: '已更新' }[value] || status || '待开始'
    },
    kindLabel(kind) {
      return { knowledge_note: '讲义', mind_map: '思维导图', practice_set: '练习', code_lab: '代码实验', presentation: 'PPT', extended_reading: '阅读' }[kind] || kind
    },
    canStart(item) { return ['PENDING', 'NOT_STARTED', 'RECOMMENDED'].includes(String(item.status || '').toUpperCase()) },
    canComplete(item) { return ['IN_PROGRESS', 'STARTED', 'ACTIVE'].includes(String(item.status || '').toUpperCase()) },
    async startItem(item) { return this.performItemAction(item, startPathItem) },
    async completeItem(item) { return this.performItemAction(item, completePathItem) },
    async performItemAction(item, action) {
      if (!item?.id || this.workingItemId) return
      this.workingItemId = item.id
      try {
        await action(item.id)
        await this.loadPath()
      } catch (error) {
        uni.showToast({ title: learningErrorMessage(error, '学习状态更新失败'), icon: 'none' })
      } finally {
        this.workingItemId = ''
      }
    },
    async replanPath() {
      if (this.working) return
      this.working = true
      try {
        await replanPythonPath()
        await this.loadPath()
      } catch (error) {
        this.pageState = classifyLearningError(error, 'generation_failed')
        this.errorMessage = learningErrorMessage(error)
      } finally { this.working = false }
    },
    openResources(item) {
      const query = buildQueryString({ pathItemId: item.id, topic: item.knowledgePoint || item.objective || '' })
      uni.navigateTo({ url: `/subpackage_learning/resourceGenerate/resourceGenerate${query ? `?${query}` : ''}` })
    }
  }
}
</script>

<style scoped>
.learning-page{min-height:100vh;padding:24rpx 24rpx 80rpx;background:#f5f7fa;box-sizing:border-box;color:#1d1f2c}.state-card{padding:80rpx 34rpx;border-radius:20rpx;background:#fff;text-align:center;box-shadow:0 4rpx 20rpx rgba(0,0,0,.04)}.state-card__title,.state-card__desc{display:block}.state-card__title{font-size:32rpx;font-weight:760}.state-card__desc{margin-top:14rpx;color:#64748b;font-size:25rpx;line-height:1.6}.state-card button{margin-top:26rpx;border:0;background:linear-gradient(135deg,#4a90d9,#5b9fe0);color:#fff;box-shadow:0 6rpx 20rpx rgba(74,144,217,.28)}.path-summary{padding:32rpx;border-radius:22rpx;background:linear-gradient(145deg,#fff,#e8f2fd);box-shadow:0 4rpx 20rpx rgba(74,144,217,.08)}.path-summary__eyebrow,.path-summary__title,.path-summary__status,.path-summary__desc{display:block}.path-summary__eyebrow{color:#4a90d9;font-size:20rpx;font-weight:750;letter-spacing:2rpx}.path-summary__title{margin-top:12rpx;font-size:36rpx;font-weight:800}.path-summary__status{display:inline-block;margin-top:14rpx;padding:8rpx 14rpx;border-radius:999rpx;background:#dcfce7;color:#166534;font-size:21rpx}.path-summary__desc{margin-top:18rpx;color:#64748b;font-size:24rpx;line-height:1.65}.path-summary__replan{margin-top:24rpx;border:0;border-radius:15rpx;background:linear-gradient(135deg,#4a90d9,#5b9fe0);color:#fff;font-size:25rpx;box-shadow:0 6rpx 20rpx rgba(74,144,217,.28)}.timeline{margin-top:28rpx}.path-item{display:flex;align-items:stretch;gap:18rpx}.path-item__rail{display:flex;width:56rpx;flex-direction:column;align-items:center}.path-item__index{width:52rpx;height:52rpx;border-radius:18rpx;background:#e8f2fd;color:#4a90d9;text-align:center;line-height:52rpx;font-weight:750}.path-item--completed .path-item__index,.path-item--done .path-item__index{background:#dcfce7;color:#166534}.path-item__line{flex:1;width:4rpx;min-height:36rpx;background:#dbe2ea}.path-item__card{flex:1;margin-bottom:20rpx;padding:26rpx;border-radius:20rpx;background:#fff;box-shadow:0 4rpx 16rpx rgba(0,0,0,.04)}.path-item__head{display:flex;justify-content:space-between;gap:16rpx}.path-item__topic{font-size:29rpx;font-weight:740}.path-item__status{flex-shrink:0;color:#4a90d9;font-size:21rpx}.path-item__objective,.path-item__reason{display:block;margin-top:12rpx;color:#64748b;font-size:23rpx;line-height:1.6}.path-item__reason{padding:14rpx;border-radius:12rpx;background:#f8fafc}.path-item__tags{display:flex;flex-wrap:wrap;gap:9rpx;margin-top:14rpx}.path-item__tags text{padding:7rpx 12rpx;border-radius:999rpx;background:#e8f2fd;color:#4a90d9;font-size:20rpx}.path-item__actions{display:flex;gap:12rpx;margin-top:20rpx}.path-item__action{flex:1;margin:0;border:0;border-radius:13rpx;background:linear-gradient(135deg,#4a90d9,#5b9fe0);color:#fff;font-size:23rpx}.path-item__action--secondary{background:#e8f2fd;color:#4a90d9}
</style>
