<template>
  <view class="page">
    <nav-bar title="试卷生成" :showBack="true" placeholder />
    <scroll-view scroll-y class="scroll">
      <view class="feature-grid">
        <view class="feature-card create-card" @click="create">
          <text class="feature-icon">＋</text><text class="feature-name">创建新试卷</text><text class="feature-desc">填写基本信息后选择题目</text>
        </view>
        <view class="feature-card" @click="goMine">
          <text class="feature-icon">📄</text><text class="feature-name">我的试卷</text><text class="feature-desc">查看自己创建的试卷</text>
        </view>
      </view>

      <view class="section-title">选题来源</view>
      <view class="source-grid">
        <view v-for="item in sources" :key="item.key" class="source-card" @click="choose(item.key)">
          <text class="source-icon">{{ item.icon }}</text><text class="source-name">{{ item.name }}</text><text class="source-desc">{{ item.desc }}</text>
        </view>
      </view>

      <view class="section-title row"><text>最近编辑</text><text class="more" @click="goMine">我的试卷 ›</text></view>
      <view v-for="paper in papers" :key="paper.id" class="swipe-row">
        <view v-if="paper.status === 'draft'" class="delete-action" @click.stop="confirmDelete(paper)">删除</view>
        <view :class="['paper-card', swipedPaperId === paper.id ? 'swiped' : '']" @touchstart="touchStart($event, paper)" @touchend="touchEnd($event, paper)" @click="openPaper(paper)">
          <view><text class="paper-name">{{ paper.name }}</text><text class="paper-meta">{{ paper.subject }} · {{ paper.questionCount || 0 }}题 · {{ paper.totalScore || 0 }}分</text></view>
          <text class="status">{{ paper.status === 'draft' ? '草稿' : '已完成' }}</text>
        </view>
      </view>
      <view v-if="!loading && papers.length === 0" class="empty"><text>还没有试卷</text><text>点击上方创建你的第一份试卷</text></view>
    </scroll-view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { listPapers, deletePaper } from '@/api/paper.js'

export default {
  components: { NavBar },
  data() {
    return {
      papers: [], loading: false, swipedPaperId: null, touchStartX: 0,
      sources: [
        { key: 'public', icon: '🌐', name: '共有题库', desc: '共同维护' },
        { key: 'private', icon: '📚', name: '私有题库', desc: '我的题库' },
        { key: 'favorite', icon: '★', name: '收藏夹', desc: '快速选题' }
      ]
    }
  },
  onShow() { this.load() },
  methods: {
    async load() { this.loading = true; try { const result = await listPapers({ status: 'draft' }); this.papers = result.data || [] } finally { this.loading = false } },
    create() { uni.navigateTo({ url: '/subpackage_ai/paperInfo/paperInfo' }) },
    goMine() { uni.navigateTo({ url: '/subpackage_ai/paperMine/paperMine' }) },
    choose(source) { const paper = this.papers[0]; uni.navigateTo({ url: `/subpackage_ai/paperSelect/paperSelect?${paper ? 'paperId=' + paper.id + '&' : ''}source=${source}` }) },
    open(id) { uni.navigateTo({ url: `/subpackage_ai/paperSelect/paperSelect?paperId=${id}&source=public` }) },
    openPaper(paper) { if (this.swipedPaperId === paper.id) { this.swipedPaperId = null; return } this.open(paper.id) },
    touchStart(event, paper) { if (paper.status !== 'draft') return; this.touchStartX = event.touches[0].clientX },
    touchEnd(event, paper) { if (paper.status !== 'draft') return; const distance = event.changedTouches[0].clientX - this.touchStartX; if (distance < -45) this.swipedPaperId = paper.id; else if (distance > 35) this.swipedPaperId = null },
    confirmDelete(paper) {
      uni.showModal({ title: '确认删除？', content: '删除后该草稿无法恢复。', cancelText: '取消', confirmText: '删除', confirmColor: '#df6565', success: async result => { if (!result.confirm) return; await deletePaper(paper.id); this.papers = this.papers.filter(item => item.id !== paper.id); this.swipedPaperId = null; uni.showToast({ title: '删除成功', icon: 'success' }); await this.load() } })
    }
  }
}
</script>

<style scoped lang="scss">
.page{min-height:100vh;background:#f5f8fc}.scroll{height:calc(100vh - 88rpx);padding:24rpx;box-sizing:border-box}.feature-grid{display:flex;gap:16rpx}.feature-card{flex:1;background:#fff;border-radius:22rpx;padding:28rpx;box-shadow:0 6rpx 22rpx rgba(61,94,145,.06)}.create-card{background:linear-gradient(135deg,#6395f1,#4d72df);color:#fff}.feature-icon,.feature-name,.feature-desc{display:block}.feature-icon{font-size:38rpx}.feature-name{font-size:30rpx;font-weight:700;margin-top:12rpx}.feature-desc{font-size:22rpx;color:#8f9bad;margin-top:9rpx}.create-card .feature-desc{color:rgba(255,255,255,.85)}.section-title{font-size:30rpx;font-weight:700;color:#24324a;margin:30rpx 4rpx 18rpx}.row{display:flex;justify-content:space-between}.more{font-size:25rpx;color:#4c77e8;font-weight:400}.source-grid{display:flex;gap:16rpx}.source-card{flex:1;background:#fff;border-radius:20rpx;padding:24rpx 12rpx}.source-icon,.source-name,.source-desc{display:block}.source-icon{font-size:42rpx}.source-name{font-size:27rpx;font-weight:600;margin-top:12rpx}.source-desc{color:#9aa6b8;font-size:21rpx;margin-top:8rpx}.swipe-row{position:relative;overflow:hidden;border-radius:18rpx;margin-bottom:14rpx;background:#df5656}.delete-action{position:absolute;right:0;top:0;bottom:0;width:150rpx;display:flex;align-items:center;justify-content:center;background:#df5656;color:#fff;font-size:27rpx}.paper-card{position:relative;z-index:1;background:#fff;border-radius:18rpx;padding:25rpx 24rpx;display:flex;justify-content:space-between;transition:transform .22s ease}.paper-card.swiped{transform:translateX(-150rpx)}.paper-name,.paper-meta{display:block}.paper-meta{color:#98a4b6;font-size:23rpx;margin-top:10rpx}.status{color:#5c82ee}.empty{text-align:center;color:#a3adbd;padding:100rpx 0}.empty text{display:block;margin:10rpx}
</style>
