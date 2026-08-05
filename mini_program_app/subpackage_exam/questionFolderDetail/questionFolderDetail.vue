<template>
  <view class="page">
    <nav-bar title="收藏夹详情" :showBack="true" fixed placeholder />

    <view v-if="loading && !detail" class="state-block"><text>加载中...</text></view>
    <view v-else-if="errorMessage && !detail" class="state-block">
      <text>加载失败</text>
      <text class="state-desc">{{ errorMessage }}</text>
      <view class="retry" @tap="loadDetail">重新加载</view>
    </view>

    <block v-else-if="detail">
      <view class="info-card">
        <view class="info-head">
          <text class="title">{{ detail.name }}</text>
          <text class="badge">{{ detail.visibilityLabel || (detail.visibility === 'PUBLIC' ? '公共' : '私有') }}</text>
        </view>
        <view class="meta">
          <text>{{ detail.questionCount || questions.length || 0 }} 题</text>
          <text>更新 {{ formatTime(detail.updateTime || detail.createTime) }}</text>
        </view>
        <view v-if="detail.ownerUsername" class="owner">
          创建者：{{ detail.ownerUsername }}
          <text v-if="detail.ownerPersonalNumber"> · 学号 {{ detail.ownerPersonalNumber }}</text>
        </view>
      </view>

      <view class="section-title">题目列表</view>

      <view v-if="!questions.length" class="state-block state-block--soft">
        <text>暂无题目</text>
        <text class="state-desc">可从题库生成或录题后加入此收藏夹</text>
      </view>

      <view v-for="(q, index) in questions" :key="q.id || index" class="q-card">
        <view class="q-head">
          <text class="q-no">{{ index + 1 }}. {{ typeLabel(q.type) }}</text>
          <text class="q-diff">{{ difficultyLabel(q.difficulty) }}</text>
        </view>
        <text class="q-stem">{{ q.stem || '（无题干）' }}</text>
      </view>
    </block>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { getQuestionFolderDetail, listQuestionFolderQuestions } from '@/api/questionFolder.js'

const TYPE_LABELS = {
  single_choice: '单选题',
  multiple_choice: '多选题',
  true_false: '判断题',
  fill_blank: '填空题',
  short_answer: '简答题',
  calculation: '计算题',
  programming: '编程题'
}

const DIFF_LABELS = { easy: '简单', medium: '中等', hard: '困难' }

export default {
  components: { NavBar },
  data() {
    return {
      folderId: '',
      detail: null,
      questions: [],
      loading: false,
      errorMessage: ''
    }
  },
  onLoad(query) {
    this.folderId = query?.id || ''
    this.loadDetail()
  },
  methods: {
    async loadDetail() {
      if (!this.folderId) {
        this.errorMessage = '缺少收藏夹 ID'
        return
      }
      this.loading = true
      this.errorMessage = ''
      try {
        const [detailRes, listRes] = await Promise.all([
          getQuestionFolderDetail(this.folderId),
          listQuestionFolderQuestions(this.folderId, { current: 1, size: 50 })
        ])
        this.detail = detailRes?.data || null
        const page = listRes?.data || {}
        this.questions = page.records || this.detail?.questions || []
      } catch (error) {
        this.detail = null
        this.questions = []
        this.errorMessage = error?.msg || error?.message || '请检查网络后重试'
      } finally {
        this.loading = false
      }
    },
    typeLabel(type) {
      return TYPE_LABELS[type] || type || '题目'
    },
    difficultyLabel(value) {
      return DIFF_LABELS[value] || value || '—'
    },
    formatTime(value) {
      if (!value) return '—'
      const text = String(value).replace('T', ' ')
      return text.length >= 16 ? text.slice(0, 16) : text
    }
  }
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #f6f8fb;
  padding: 16rpx 24rpx 40rpx;
  box-sizing: border-box;
}

.info-card,
.q-card {
  background: #ffffff;
  border: 1px solid #e4ebf1;
  border-radius: 22rpx;
  padding: 28rpx;
  margin-bottom: 16rpx;
}

.info-head,
.q-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16rpx;
}

.title {
  flex: 1;
  font-size: 32rpx;
  font-weight: 800;
  color: #18222f;
}

.badge,
.q-diff {
  padding: 6rpx 14rpx;
  border-radius: 999rpx;
  background: #e8eef3;
  color: #516274;
  font-size: 20rpx;
  font-weight: 700;
}

.meta {
  display: flex;
  gap: 24rpx;
  margin-top: 16rpx;
  font-size: 24rpx;
  color: #7b8794;
}

.owner {
  margin-top: 12rpx;
  font-size: 22rpx;
  color: #667085;
}

.section-title {
  margin: 12rpx 0 16rpx;
  font-size: 28rpx;
  font-weight: 700;
  color: #18222f;
}

.q-no {
  font-size: 26rpx;
  font-weight: 700;
  color: #233243;
}

.q-stem {
  display: block;
  margin-top: 14rpx;
  font-size: 26rpx;
  line-height: 1.6;
  color: #4b5d71;
}

.state-block {
  padding: 120rpx 40rpx;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16rpx;
  color: #314253;
  font-size: 28rpx;
  font-weight: 700;
}

.state-block--soft {
  padding: 80rpx 40rpx;
  background: #ffffff;
  border: 1px solid #e4ebf1;
  border-radius: 22rpx;
}

.state-desc {
  font-size: 24rpx;
  font-weight: 400;
  color: #7b8794;
  line-height: 1.5;
}

.retry {
  margin-top: 8rpx;
  padding: 14rpx 28rpx;
  border-radius: 14rpx;
  background: #e8eef3;
  color: #304152;
  font-size: 24rpx;
  font-weight: 700;
}
</style>
