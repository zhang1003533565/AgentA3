<template>
  <view class="exam-page">
    <nav-bar title="我的试卷" :showBack="true" fixed placeholder />
    <view class="toolbar">
      <view class="search-box">
        <image src="/static/icons/line/search.svg" mode="aspectFit" />
        <input v-model="keyword" placeholder="搜索试卷" confirm-type="search" @input="scheduleSearch" @confirm="refresh" />
        <text v-if="keyword" @click="clearSearch">×</text>
      </view>
    </view>
    <scroll-view class="paper-list" scroll-y refresher-enabled :refresher-triggered="refreshing"
      @refresherrefresh="refresh" @scrolltolower="loadMore">
      <view v-for="paper in papers" :key="paper.id" class="card" @click="openPaper(paper.id)">
        <view class="card-head">
          <text class="title">{{ paper.title }}</text>
          <text v-if="paper.inProgressAttemptId" class="status active">进行中</text>
          <text v-else class="status">可答题</text>
        </view>
        <text v-if="paper.subtitle" class="subtitle">{{ paper.subtitle }}</text>
        <view class="meta">
          <text>{{ paper.questionCount || 0 }} 题</text>
          <text>{{ paper.durationMinutes || 0 }} 分钟</text>
          <text>已答 {{ paper.attemptCount || 0 }} 次</text>
        </view>
        <view class="card-action" @click.stop="openPrimary(paper)">
          {{ paper.inProgressAttemptId ? '继续答题' : '查看试卷' }}
        </view>
      </view>
      <view v-if="errorMessage && !loading" class="state-block">
        <text>加载失败</text><text class="state-desc">{{ errorMessage }}</text><button @click="refresh">重新加载</button>
      </view>
      <view v-else-if="!loading && papers.length === 0" class="state-block">
        <text>暂无已发布试卷</text><text class="state-desc">老师发布试卷后会显示在这里</text>
      </view>
      <view class="load-more"><text v-if="loading">加载中...</text><text v-else-if="noMore && papers.length">没有更多了</text></view>
    </scroll-view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { getExamPapers } from '@/api/exam.js'

export default {
  components: { NavBar },
  data() {
    return { papers: [], keyword: '', pageNum: 0, pageSize: 10, loading: false, refreshing: false, noMore: false, errorMessage: '', searchTimer: null }
  },
  onLoad() { this.loadPapers(true) },
  onShow() { if (this.papers.length) this.refresh() },
  onUnload() { if (this.searchTimer) clearTimeout(this.searchTimer) },
  methods: {
    async refresh() {
      this.refreshing = true
      this.pageNum = 0
      this.noMore = false
      await this.loadPapers(true)
      this.refreshing = false
    },
    async loadPapers(reset = false) {
      if (this.loading || (!reset && this.noMore)) return
      this.loading = true
      this.errorMessage = ''
      try {
        const res = await getExamPapers({ page: this.pageNum, size: this.pageSize, keyword: this.keyword.trim() })
        const data = res?.data || {}
        const records = data.content || data.records || []
        this.papers = reset ? records : [...this.papers, ...records]
        const total = Number(data.totalElements ?? data.total ?? 0)
        this.noMore = this.papers.length >= total || records.length < this.pageSize
      } catch (error) {
        if (reset) this.papers = []
        this.errorMessage = error?.msg || error?.message || '请检查网络后重试'
      } finally { this.loading = false }
    },
    loadMore() { if (!this.loading && !this.noMore) { this.pageNum += 1; this.loadPapers(false) } },
    scheduleSearch() {
      if (this.searchTimer) clearTimeout(this.searchTimer)
      this.searchTimer = setTimeout(() => this.refresh(), 300)
    },
    clearSearch() { this.keyword = ''; this.refresh() },
    openPaper(id) { uni.navigateTo({ url: `/subpackage_exam/paperDetail/paperDetail?paperId=${encodeURIComponent(id)}` }) },
    openPrimary(paper) {
      if (paper.inProgressAttemptId) {
        uni.navigateTo({ url: `/subpackage_exam/attempt/attempt?attemptId=${encodeURIComponent(paper.inProgressAttemptId)}` })
      } else this.openPaper(paper.id)
    }
  }
}
</script>

<style lang="scss" scoped>
.exam-page{min-height:100vh;background:#f7f7f9}.toolbar{padding:20rpx 24rpx}.search-box{height:76rpx;padding:0 22rpx;background:#fff;border-radius:38rpx;display:flex;align-items:center;gap:16rpx}.search-box image{width:34rpx;height:34rpx}.search-box input{flex:1}.paper-list{height:calc(100vh - 180rpx);padding:0 24rpx;box-sizing:border-box}.card{margin-bottom:20rpx;padding:28rpx;background:#fff;border-radius:24rpx;box-shadow:0 8rpx 24rpx rgba(34,55,90,.06)}.card-head{display:flex;align-items:flex-start;gap:16rpx}.title{flex:1;font-size:34rpx;font-weight:700;color:#1d1d1f}.status{padding:6rpx 14rpx;border-radius:18rpx;background:#eef2f8;color:#667085;font-size:22rpx}.status.active{background:#e9f7ef;color:#1f9d61}.subtitle{display:block;margin-top:12rpx;color:#6e6e73;font-size:26rpx}.meta{display:flex;gap:24rpx;margin-top:22rpx;color:#8e8e93;font-size:24rpx}.card-action{margin-top:24rpx;padding-top:22rpx;border-top:1px solid #eee;text-align:right;color:#3478f6;font-weight:600}.state-block{padding:140rpx 40rpx;text-align:center;display:flex;flex-direction:column;gap:18rpx;color:#3a3a3c}.state-desc{color:#8e8e93;font-size:24rpx}.state-block button{font-size:26rpx}.load-more{text-align:center;padding:24rpx;color:#8e8e93;font-size:24rpx}
</style>
