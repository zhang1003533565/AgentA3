<template>
  <view class="history-page">
    <nav-bar :title="title || '答题历史'" :showBack="true" fixed placeholder />
    <view class="list">
      <view v-for="item in attempts" :key="item.id" class="record" @click="openAttempt(item)">
        <view class="record-head"><text class="record-title">第 {{ item.attemptNo }} 次答题</text><text class="tag" :class="{ active: item.status === 'IN_PROGRESS' }">{{ statusText(item.status) }}</text></view>
        <view class="score"><text>客观题</text><text>{{ scoreText(item) }}</text></view>
        <view class="meta"><text>开始：{{ formatTime(item.startedAt) }}</text><text v-if="item.submittedAt">交卷：{{ formatTime(item.submittedAt) }}</text></view>
        <text class="link">{{ item.status === 'IN_PROGRESS' ? '继续答题' : '查看结果' }} ›</text>
      </view>
      <view v-if="errorMessage" class="state"><text>加载失败</text><text>{{ errorMessage }}</text><button @click="loadHistory">重新加载</button></view>
      <view v-else-if="!loading && attempts.length === 0" class="state"><text>暂无答题记录</text><text>完成一次答题后可在这里查看结果</text></view>
      <view v-else-if="loading" class="state">加载中...</view>
    </view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { getExamAttemptHistory } from '@/api/exam.js'

export default {
  components: { NavBar },
  data() { return { paperId: '', title: '', attempts: [], loading: false, errorMessage: '' } },
  onLoad(options) { this.paperId = options?.paperId || ''; this.title = options?.title ? decodeURIComponent(options.title) : ''; this.loadHistory() },
  onShow() { if (this.attempts.length) this.loadHistory() },
  methods: {
    async loadHistory() {
      if (!this.paperId) { this.errorMessage = '缺少试卷编号'; return }
      this.loading = true; this.errorMessage = ''
      try { const res = await getExamAttemptHistory(this.paperId); this.attempts = Array.isArray(res?.data) ? res.data : [] }
      catch (error) { this.attempts = []; this.errorMessage = error?.msg || error?.message || '请稍后重试' }
      finally { this.loading = false }
    },
    statusText(status) { return { IN_PROGRESS: '进行中', SUBMITTED: '已交卷', AUTO_SUBMITTED: '超时交卷' }[status] || status },
    scoreText(item) { return item.status === 'IN_PROGRESS' ? '尚未交卷' : `${item.objectiveScore ?? 0} / ${item.objectiveTotalScore ?? 0}` },
    formatTime(value) { return value ? String(value).replace('T', ' ').slice(0, 16) : '—' },
    openAttempt(item) {
      const target = item.status === 'IN_PROGRESS' ? 'attempt/attempt' : 'attemptResult/attemptResult'
      uni.navigateTo({ url: `/subpackage_exam/${target}?attemptId=${encodeURIComponent(item.id)}` })
    }
  }
}
</script>

<style lang="scss" scoped>
.history-page{min-height:100vh;background:#f7f7f9}.list{padding:24rpx}.record{padding:28rpx;margin-bottom:20rpx;background:#fff;border-radius:24rpx}.record-head,.score{display:flex;justify-content:space-between;align-items:center}.record-title{font-size:32rpx;font-weight:700}.tag{padding:6rpx 14rpx;border-radius:18rpx;background:#eef2f8;color:#667085;font-size:22rpx}.tag.active{background:#e9f7ef;color:#1f9d61}.score{margin-top:24rpx;font-size:27rpx}.score text:last-child{font-weight:700;color:#3478f6}.meta{display:flex;flex-direction:column;gap:8rpx;margin-top:18rpx;color:#8e8e93;font-size:23rpx}.link{display:block;margin-top:22rpx;text-align:right;color:#3478f6}.state{padding:160rpx 30rpx;text-align:center;color:#8e8e93;display:flex;flex-direction:column;gap:18rpx}
</style>
