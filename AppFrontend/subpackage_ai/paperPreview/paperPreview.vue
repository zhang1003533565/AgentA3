<template>
  <view class="page">
    <nav-bar title="试卷预览" :showBack="true" placeholder :rightText="allExpanded ? '全部收起' : '全部展开'" @right-click="toggleAll" />
    <scroll-view scroll-y class="paper">
      <view class="header">
        <view class="layout-entry" @click="openLayout">纸质版式</view>
        <text class="title">{{ paper.name }}</text>
        <text>{{ paper.subject }} · {{ paper.category }}</text>
        <text>{{ paper.duration ? paper.duration + '分钟 · ' : '' }}{{ paper.questionCount || 0 }}题 · {{ paper.totalScore || 0 }}分</text>
        <text v-if="paper.remark" class="remark">{{ paper.remark }}</text>
      </view>

      <view v-for="(item, index) in paper.questions" :key="questionId(item)" class="question">
        <view class="q-title"><text>{{ index + 1 }}. {{ item.question.content }}</text><text>（{{ item.score }}分）</text></view>
        <view v-if="options(item.question.options).length" class="options">
          <text v-for="(option, optionIndex) in options(item.question.options)" :key="optionIndex">{{ String.fromCharCode(65 + optionIndex) }}. {{ option }}</text>
        </view>

        <button class="answer-toggle" @click="toggleQuestion(item)">{{ isExpanded(item) ? '收起答案解析' : '查看答案解析' }}</button>
        <view v-if="isExpanded(item)" class="answer">
          <text><text class="answer-label">正确答案：</text>{{ item.question.answer || '暂无' }}</text>
          <text><text class="answer-label">答案解析：</text>{{ item.question.analysis || '暂无' }}</text>
        </view>
      </view>
    </scroll-view>

    <view class="bottom"><button class="secondary" @click="save">保存草稿</button><button class="primary" :loading="saving" @click="complete">完成组卷</button></view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { getPaper, completePaper } from '@/api/paper.js'

export default {
  components: { NavBar },
  data() { return { paperId: null, paper: { questions: [] }, expandedQuestionIds: [], saving: false } },
  computed: {
    allExpanded() { return this.paper.questions.length > 0 && this.expandedQuestionIds.length === this.paper.questions.length }
  },
  onLoad(query) { this.paperId = query.paperId; this.load() },
  methods: {
    async load() { const result = await getPaper(this.paperId); this.paper = result.data || { questions: [] }; this.expandedQuestionIds = [] },
    questionId(item) { return item.questionId || item.question?.id || item.id },
    isExpanded(item) { return this.expandedQuestionIds.includes(this.questionId(item)) },
    toggleQuestion(item) {
      const id = this.questionId(item)
      this.expandedQuestionIds = this.isExpanded(item) ? this.expandedQuestionIds.filter(value => value !== id) : [...this.expandedQuestionIds, id]
    },
    toggleAll() { this.expandedQuestionIds = this.allExpanded ? [] : this.paper.questions.map(item => this.questionId(item)) },
    options(value) { if (!value) return []; if (Array.isArray(value)) return value; try { return JSON.parse(value) } catch (error) { return [] } },
    openLayout() { uni.navigateTo({ url: `/subpackage_ai/paperLayout/paperLayout?paperId=${this.paperId}` }) },
    save() { uni.showToast({ title: '草稿已保存', icon: 'success' }) },
    async complete() {
      if (!this.paper.questions.length) return uni.showToast({ title: '至少选择一道题', icon: 'none' })
      if (this.paper.questions.some(item => Number(item.score) <= 0)) return uni.showToast({ title: '题目分值必须大于0', icon: 'none' })
      this.saving = true
      try { await completePaper(this.paperId); uni.showToast({ title: '组卷完成', icon: 'success' }); setTimeout(() => uni.redirectTo({ url: '/subpackage_ai/paperMine/paperMine' }), 700) } finally { this.saving = false }
    }
  }
}
</script>

<style scoped lang="scss">
.page{min-height:100vh;background:#eef2f7;padding-bottom:120rpx}.paper{height:calc(100vh - 210rpx);background:#fff;margin:20rpx 24rpx;width:auto;padding:34rpx;box-sizing:border-box;border-radius:12rpx}.header{position:relative;text-align:center;border-bottom:2rpx solid #d9dee7;padding-bottom:24rpx;color:#69758a}.layout-entry{position:absolute;right:0;top:0;padding:8rpx 16rpx;border:1rpx solid #a9c2f4;border-radius:24rpx;color:#4775e5;font-size:22rpx;background:#f4f8ff}.header text{display:block;margin:8rpx}.title{font-size:36rpx!important;font-weight:700;color:#202d42}.remark{font-size:23rpx}.question{padding:26rpx 0;border-bottom:1rpx solid #edf0f5}.q-title{display:flex;justify-content:space-between;gap:20rpx;color:#26354c;font-size:27rpx;line-height:1.6}.options text,.answer>text{display:block;font-size:25rpx;line-height:1.8;color:#566379}.options{margin:10rpx 0 0 30rpx}.answer-toggle{margin:18rpx 0 0;background:#eef4ff;color:#4775e5;border-radius:28rpx;font-size:23rpx;line-height:56rpx}.answer{background:#f1f6ff;padding:18rpx;border-radius:12rpx;margin-top:14rpx}.answer-label{color:#315dae;font-weight:600}.bottom{position:fixed;bottom:0;left:0;right:0;background:#fff;display:flex;gap:16rpx;padding:16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom))}.bottom button{flex:1;border-radius:40rpx;font-size:26rpx}.secondary{background:#edf2fc;color:#4d78e8}.primary{background:#4d78e8;color:#fff}
</style>
