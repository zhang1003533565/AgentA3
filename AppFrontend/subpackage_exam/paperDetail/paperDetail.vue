<template>
  <view class="detail-page">
    <nav-bar title="试卷详情" :showBack="true" fixed placeholder />
    <view v-if="paper" class="content">
      <view class="hero">
        <text class="title">{{ paper.title }}</text><text v-if="paper.subtitle" class="subtitle">{{ paper.subtitle }}</text>
        <view class="facts"><text>{{ paper.questionCount || 0 }} 题</text><text>{{ paper.durationMinutes || 0 }} 分钟</text><text>{{ scoreText }}</text></view>
      </view>
      <view class="section"><text class="section-title">答题说明</text><text class="body">{{ paper.precautions || '请在规定时间内独立完成并提交。' }}</text></view>
      <view class="section actions">
        <view class="secondary" @click="downloadPdf">{{ downloading ? '正在准备...' : '打开空白试卷 PDF' }}</view>
        <view class="secondary" @click="openHistory">查看答题历史</view>
      </view>
      <view class="primary" :class="{ disabled: starting }" @click="startOrResume">
        {{ starting ? '正在进入...' : (paper.inProgressAttemptId ? '继续答题' : '开始答题') }}
      </view>
    </view>
    <view v-else-if="errorMessage" class="state"><text>加载失败</text><text>{{ errorMessage }}</text><button @click="loadPaper">重新加载</button></view>
    <view v-else class="state">加载中...</view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { downloadExamPaperPdf, getExamPaperDetail, startExamAttempt } from '@/api/exam.js'

export default {
  components: { NavBar },
  data() { return { paperId: '', paper: null, errorMessage: '', starting: false, downloading: false } },
  computed: { scoreText() { return this.paper?.totalScore == null ? '总分待定' : `${this.paper.totalScore} 分` } },
  onLoad(options) { this.paperId = options?.paperId || ''; this.loadPaper() },
  onShow() { if (this.paper) this.loadPaper() },
  methods: {
    async loadPaper() {
      if (!this.paperId) { this.errorMessage = '缺少试卷编号'; return }
      this.errorMessage = ''
      try { const res = await getExamPaperDetail(this.paperId); this.paper = res?.data || null }
      catch (error) { this.paper = null; this.errorMessage = error?.msg || error?.message || '请稍后重试' }
    },
    async startOrResume() {
      if (this.starting || !this.paper) return
      if (this.paper.inProgressAttemptId) { this.openAttempt(this.paper.inProgressAttemptId); return }
      this.starting = true
      try { const res = await startExamAttempt(this.paperId); this.openAttempt(res?.data?.id) }
      catch (error) { uni.showToast({ title: error?.msg || error?.message || '开始答题失败', icon: 'none' }) } finally { this.starting = false }
    },
    openAttempt(attemptId) {
      if (!attemptId) return
      uni.navigateTo({ url: `/subpackage_exam/attempt/attempt?attemptId=${encodeURIComponent(attemptId)}` })
    },
    openHistory() { uni.navigateTo({ url: `/subpackage_exam/attemptHistory/attemptHistory?paperId=${encodeURIComponent(this.paperId)}&title=${encodeURIComponent(this.paper?.title || '')}` }) },
    async downloadPdf() {
      if (this.downloading) return
      this.downloading = true
      uni.showLoading({ title: '准备 PDF...' })
      try { await downloadExamPaperPdf(this.paperId) }
      catch (error) { uni.showToast({ title: error?.message || 'PDF 打开失败', icon: 'none' }) }
      finally { uni.hideLoading(); this.downloading = false }
    }
  }
}
</script>

<style lang="scss" scoped>
.detail-page{min-height:100vh;background:#f7f7f9}.content{padding:24rpx}.hero,.section{padding:32rpx;margin-bottom:22rpx;background:#fff;border-radius:24rpx}.title{display:block;font-size:40rpx;font-weight:700;color:#1d1d1f}.subtitle{display:block;margin-top:12rpx;color:#6e6e73}.facts{display:flex;gap:28rpx;margin-top:28rpx;color:#3478f6;font-size:25rpx}.section-title{display:block;font-size:30rpx;font-weight:650;margin-bottom:16rpx}.body{color:#636366;line-height:1.7;white-space:pre-wrap}.actions{display:flex;gap:18rpx}.secondary{flex:1;padding:24rpx 10rpx;border:1px solid #3478f6;border-radius:18rpx;text-align:center;color:#3478f6;font-size:25rpx}.primary{margin:34rpx 0;padding:28rpx;border-radius:20rpx;text-align:center;background:#3478f6;color:#fff;font-weight:700}.disabled{opacity:.6}.state{padding:180rpx 40rpx;text-align:center;color:#8e8e93;display:flex;flex-direction:column;gap:20rpx}
</style>
