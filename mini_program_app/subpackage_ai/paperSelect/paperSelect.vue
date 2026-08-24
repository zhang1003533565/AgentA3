<template>
  <view class="page">
    <nav-bar title="选择试题" :showBack="true" placeholder />
    <view class="steps"><view class="step active"><text class="step-no">1</text><text>试卷信息与选题</text></view><view class="step"><text class="step-no">2</text><text>页面格式</text></view><view class="step"><text class="step-no">3</text><text>预览与确认</text></view></view>
    <view v-if="paperId" class="summary">
      <view><text class="paper-name">{{ paper.name }}</text><text class="paper-meta">已选 {{ paper.questionCount || 0 }} 题 · {{ paper.totalScore || 0 }} 分</text></view>
      <text class="selected-link" @click="viewSelected">查看已选</text>
    </view>

    <view class="source-tabs">
      <text v-for="tab in sourceTabs" :key="tab.value" :class="{ active: source === tab.value }" @click="switchSource(tab.value)">{{ tab.label }}</text>
    </view>
    <view v-if="source === 'private' && !bankId" class="bank-manage" @click="manageBanks">管理我的题库组 ›</view>

    <view class="filters">
      <view class="search"><input v-model="keyword" :placeholder="source === 'favorite' ? '搜索收藏题目' : '搜索题库或题目'" confirm-type="search" @confirm="search" /></view>
      <view v-if="showQuestions" class="chip" @click="openFilter('type')">题型{{ type ? '：' + type : '' }}</view>
      <view v-if="showQuestions" class="chip" @click="openFilter('difficulty')">难度{{ difficulty ? '：' + difficulty : '' }}</view>
    </view>

    <scroll-view v-if="showBanks" scroll-y class="list">
      <view v-for="bank in banks" :key="bank.id" class="bank-card" @click="openBank(bank)">
        <view><text class="bank-name">{{ bank.name }}</text><text class="bank-desc">{{ bank.description || (source === 'public' ? '公共题库' : '我的题库组') }}</text></view>
        <text class="bank-count">{{ bank.questionCount || 0 }} 题 ›</text>
      </view>
      <view v-if="!loading && !banks.length" class="empty">暂无{{ source === 'public' ? '公共题库' : '私有题库' }}</view>
    </scroll-view>

    <scroll-view v-else scroll-y class="list">
      <view v-for="question in questions" :key="question.id" :class="['question-card', question.selected ? 'chosen' : '']">
        <view class="tags"><text>{{ question.questionType }}</text><text>{{ question.difficulty }}</text><text v-if="question.chapter">{{ question.chapter }}</text></view>
        <text class="content">{{ question.content }}</text>
        <text class="knowledge">{{ question.knowledgePoint || question.bankName }}</text>
        <view class="actions">
          <text @click="detail(question)">查看详情</text><text @click="toggleFavorite(question)">{{ question.favorited ? '★ 已收藏' : '☆ 收藏' }}</text>
          <button :disabled="busyId === question.id" :class="{added: question.selected}" @click="togglePaperQuestion(question)">{{ question.selected ? '移出试卷' : '加入试卷' }}</button>
        </view>
      </view>
      <view v-if="!loading && !questions.length" class="empty">暂无题目</view>
    </scroll-view>

    <view v-if="paperId" class="bottom"><text>已选 {{ paper.questionCount || 0 }} 题</text><button @click="viewSelected">查看已选题目</button></view>

    <view v-if="filterPanel" class="filter-mask" @click="closeFilter">
      <view class="filter-panel" @click.stop>
        <view class="filter-title"><text>{{ filterPanel === 'type' ? '选择题型' : '选择难度' }}</text><text class="close" @click="closeFilter">关闭</text></view>
        <view class="filter-options">
          <view v-for="option in currentFilterOptions" :key="option" :class="['filter-option', isFilterSelected(option) ? 'selected' : '']" @click="selectFilter(option)">{{ option }}</view>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { listPaperQuestions, listPaperBanks, listQuestions, listFavoriteQuestions, listPaperDictionaries, addPaperQuestion, removePaperQuestion, favoriteQuestion, unfavoriteQuestion } from '@/api/paper.js'

export default {
  components: { NavBar },
  data() {
    return {
      paperId: null, paper: {}, source: 'public', bankId: null,
      banks: [], questions: [], keyword: '', type: '', difficulty: '',
      types: [], difficulties: [], loading: false, busyId: null, filterPanel: '', selectedQuestionIds: [],
      sourceTabs: [
        { label: '共有题库', value: 'public' },
        { label: '私有题库', value: 'private' },
        { label: '收藏夹', value: 'favorite' }
      ]
    }
  },
  computed: {
    showBanks() { return this.source !== 'favorite' && !this.bankId },
    showQuestions() { return !this.showBanks },
    currentFilterOptions() { return this.filterPanel === 'type' ? this.types : this.difficulties }
  },
  onLoad(query) { this.paperId = query.paperId || null; this.source = query.source || 'public' },
  onShow() {
    this.loadDictionaries().finally(() => { this.loadPaper(); this.loadSource() })
  },
  methods: {
    async loadDictionaries() {
      const [types, difficulties] = await Promise.all([
        listPaperDictionaries('question_type'),
        listPaperDictionaries('difficulty')
      ])
      this.types = ['全部', ...(types.data || []).map(item => item.name)]
      this.difficulties = ['全部', ...(difficulties.data || []).map(item => item.name)]
    },
    async loadPaper() {
      if (!this.paperId) return
      const result = await listPaperQuestions(this.paperId)
      this.applyPaperState(result.data)
    },
    async loadSource() {
      this.bankId = null; this.questions = []; this.loading = true
      try {
        if (this.source === 'favorite') return await this.loadQuestions()
        const result = await listPaperBanks({ visibility: this.source, keyword: this.keyword })
        this.banks = result.data || []
      } finally { this.loading = false }
    },
    switchSource(source) {
      if (source === this.source) return
      this.source = source
      this.bankId = null
      this.keyword = ''
      this.type = ''
      this.difficulty = ''
      this.banks = []
      this.questions = []
      this.loadSource()
    },
    manageBanks() { uni.navigateTo({ url: '/subpackage_ai/paperBank/paperBank' }) },
    openBank(bank) { this.bankId = bank.id; this.keyword = ''; this.loadQuestions() },
    search() { this.showBanks ? this.loadSource() : this.loadQuestions() },
    openFilter(panel) { this.filterPanel = panel },
    closeFilter() { this.filterPanel = '' },
    isFilterSelected(option) { const value = option === '全部' ? '' : option; return this.filterPanel === 'type' ? this.type === value : this.difficulty === value },
    selectFilter(option) { const value = option === '全部' ? '' : option; if (this.filterPanel === 'type') this.type = value; else this.difficulty = value; this.closeFilter(); this.loadQuestions() },
    async loadQuestions() {
      this.loading = true
      try {
        const params = { paperId: this.paperId, keyword: this.keyword, type: this.type, difficulty: this.difficulty }
        const result = this.source === 'favorite' ? await listFavoriteQuestions(params) : await listQuestions(this.bankId, params)
        this.questions = result.data || []
        this.syncQuestionSelection()
      } finally { this.loading = false }
    },
    syncQuestionSelection() { this.questions.forEach(question => { question.selected = this.selectedQuestionIds.includes(Number(question.id)) }) },
    applyPaperState(paper) { this.paper = paper || {}; this.selectedQuestionIds = (this.paper.questions || []).map(item => Number(item.questionId)); this.syncQuestionSelection() },
    async togglePaperQuestion(question) {
      if (!this.paperId) return uni.showToast({ title: '请先创建试卷', icon: 'none' })
      this.busyId = question.id
      try {
        if (question.selected) {
          await removePaperQuestion(this.paperId, question.id)
          await this.loadPaper()
          await this.loadQuestions()
          uni.showToast({ title: '已移出试卷', icon: 'none' })
        } else {
          await addPaperQuestion(this.paperId, {
            questionId: question.id,
            score: 5,
            questionOrder: Number(this.paper.questionCount || 0) + 1,
            sourceType: this.source,
            sourceId: question.bankId
          })
          await this.loadPaper()
          await this.loadQuestions()
          uni.showToast({ title: '已加入试卷', icon: 'success' })
        }
      } finally { this.busyId = null }
    },
    async toggleFavorite(question) {
      if (question.favorited) await unfavoriteQuestion(question.id); else await favoriteQuestion(question.id)
      question.favorited = !question.favorited
    },
    detail(question) { uni.navigateTo({ url: `/subpackage_ai/paperQuestionDetail/paperQuestionDetail?questionId=${question.id}${this.paperId ? '&paperId=' + this.paperId : ''}` }) },
    viewSelected() { uni.navigateTo({ url: `/subpackage_ai/paperSelected/paperSelected?paperId=${this.paperId}` }) }
  }
}
</script>

<style scoped lang="scss">
.page{min-height:100vh;background:#f5f7fa;padding-bottom:110rpx}.steps{display:flex;align-items:center;padding:20rpx;background:#fff;border-bottom:1rpx solid #e2e7ed}.step{flex:1;display:flex;flex-direction:column;align-items:center;gap:6rpx;color:#98a2b3;font-size:19rpx}.step-no{width:38rpx;height:38rpx;line-height:38rpx;text-align:center;border-radius:50%;background:#eef1f4;color:#697586}.step.active{color:#1e6bb8;font-weight:600}.step.active .step-no{background:#1e6bb8;color:#fff}.summary{margin:18rpx 24rpx;padding:24rpx;background:#fff;border:1rpx solid #e4e8ed;border-radius:10rpx;display:flex;justify-content:space-between}.paper-name{display:block;font-weight:700}.paper-meta,.bank-desc,.knowledge{display:block;color:#929dad;font-size:22rpx;margin-top:8rpx}.selected-link{color:#1e6bb8}.filters{display:flex;gap:12rpx;padding:18rpx 24rpx}.search{flex:1;background:#fff;border:1rpx solid #e0e6ed;border-radius:8rpx;padding:14rpx 20rpx}.chip{background:#fff;border-radius:8rpx;padding:14rpx 20rpx}.list{height:calc(100vh - 330rpx);padding:0 24rpx;box-sizing:border-box}.bank-card,.question-card{background:#fff;border:1rpx solid #e4e8ed;border-radius:10rpx;padding:24rpx;margin-bottom:14rpx}.bank-card{display:flex;justify-content:space-between;align-items:center}.bank-name{display:block;font-weight:600}.bank-count{color:#1e6bb8}.tags text{background:#eef5fb;color:#416a91;padding:6rpx 12rpx;border-radius:6rpx;font-size:20rpx;margin-right:8rpx}.content{display:block;margin:18rpx 0;line-height:1.6}.actions{display:flex;justify-content:space-between;align-items:center;color:#d18a21}.actions button{margin:0;background:#1e6bb8;color:#fff;border-radius:8rpx;font-size:23rpx}.actions .added{background:#edf2f7;color:#667085}.chosen{border-color:#8ab3d6}.empty{text-align:center;color:#9ba6b7;padding:100rpx 0}.bottom{position:fixed;left:0;right:0;bottom:0;background:#fff;padding:16rpx 24rpx;display:flex;justify-content:space-between;align-items:center;border-top:1rpx solid #e2e7ed}.bottom button{margin:0;background:#1e6bb8;color:#fff;border-radius:8rpx;font-size:24rpx}
.source-tabs{display:flex;background:#fff;padding:0 24rpx;border-bottom:1rpx solid #e8edf5}.source-tabs text{flex:1;text-align:center;padding:22rpx 0;color:#7d899a;font-size:26rpx}.source-tabs .active{color:#4775e5;font-weight:700;border-bottom:4rpx solid #4775e5}.chip{background:#fff!important;color:#26354c!important;border:2rpx solid #cfe0ff;border-radius:28rpx;min-width:110rpx;max-width:220rpx;padding:13rpx 18rpx;text-align:center;font-size:23rpx;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.filter-mask{position:fixed;z-index:1000;left:0;right:0;top:0;bottom:0;background:rgba(24,38,63,.42);display:flex;align-items:flex-end}.filter-panel{width:100%;background:#fff;border-radius:28rpx 28rpx 0 0;padding:26rpx 28rpx calc(28rpx + env(safe-area-inset-bottom));box-sizing:border-box}.filter-title{display:flex;justify-content:space-between;align-items:center;color:#24324a;font-size:31rpx;font-weight:700;padding-bottom:20rpx}.filter-title .close{font-size:24rpx;color:#6f7d91;font-weight:400}.filter-options{display:grid;grid-template-columns:repeat(2,1fr);gap:14rpx}.filter-option{background:#f5f8fc;border:2rpx solid transparent;color:#35445b;border-radius:16rpx;padding:22rpx;text-align:center;font-size:27rpx}.filter-option.selected{background:#eaf2ff;border-color:#79a1f2;color:#3768cf;font-weight:600}
.bank-manage{background:#eef5ff;color:#4775e5;text-align:right;padding:16rpx 24rpx;font-size:24rpx}
</style>
