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
        <view v-if="canEdit" class="info-actions">
          <view class="ghost-btn" @tap="toggleVisibility">
            {{ detail.visibility === 'PUBLIC' ? '改为私有' : '改为公共' }}
          </view>
          <view class="primary-btn" @tap="goAdd">添加题目</view>
        </view>
      </view>

      <view class="section-title">题目列表</view>

      <view v-if="!questions.length" class="state-block state-block--soft">
        <text>暂无题目</text>
        <text class="state-desc">{{ canEdit ? '点击上方「添加题目」，从系统题库挑选加入' : '收藏夹内还没有题目' }}</text>
        <view v-if="canEdit" class="retry" @tap="goAdd">添加题目</view>
      </view>

      <view v-for="(q, index) in questions" :key="q.id || index" class="q-card">
        <view class="q-head">
          <text class="q-no">{{ index + 1 }}. {{ typeLabel(q.type) }}</text>
          <text class="q-diff">{{ difficultyLabel(q.difficulty) }} · {{ q.visibility === 'PUBLIC' ? '公开题' : '私有题' }}</text>
        </view>
        <text class="q-stem">{{ q.stem || '（无题干）' }}</text>
        <view v-if="canEdit" class="q-actions" @tap.stop>
          <text class="link" @tap="goPush(q)">推送到收藏夹</text>
          <text class="link danger" @tap="confirmRemove(q)">移出收藏夹</text>
        </view>
      </view>
    </block>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { getUserInfo } from '@/utils/storage.js'
import {
  getQuestionFolderDetail,
  listQuestionFolderQuestions,
  removeQuestionFromFolder,
  changeQuestionFolderVisibility
} from '@/api/questionFolder.js'

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
      errorMessage: '',
      isAdmin: false
    }
  },
  computed: {
    canEdit() {
      if (!this.detail) return false
      if (this.isAdmin) return true
      return Boolean(this.detail.ownedByCurrentUser)
    }
  },
  onLoad(query) {
    const info = getUserInfo() || {}
    this.isAdmin = String(info.role || '').toUpperCase() === 'ADMIN'
    this.folderId = query?.id || ''
    this.loadDetail()
  },
  onShow() {
    if (this.folderId && this.detail) this.loadDetail()
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
    goAdd() {
      uni.navigateTo({
        url: `/subpackage_exam/questionFolderAdd/questionFolderAdd?folderId=${encodeURIComponent(this.folderId)}`
      })
    },
    goPush(q) {
      if (!q?.id) return
      uni.navigateTo({
        url: `/subpackage_exam/questionFolderPush/questionFolderPush?folderId=${encodeURIComponent(this.folderId)}&questionIds=${encodeURIComponent(String(q.id))}`
      })
    },
    async applyVisibility(visibility, publishContainedQuestions) {
      try {
        await changeQuestionFolderVisibility(this.folderId, {
          visibility,
          publishContainedQuestions: Boolean(publishContainedQuestions)
        })
        uni.showToast({ title: visibility === 'PUBLIC' ? '已改为公共' : '已改为私有', icon: 'success' })
        this.loadDetail()
      } catch (error) {
        // request toast
      }
    },
    toggleVisibility() {
      if (!this.detail) return
      const toPublic = this.detail.visibility !== 'PUBLIC'
      if (!toPublic) {
        uni.showModal({
          title: '改为私有收藏夹',
          content: '改为私有后，仅你（及管理员）可见该收藏夹。',
          success: (res) => {
            if (res.confirm) this.applyVisibility('PRIVATE', false)
          }
        })
        return
      }
      uni.showActionSheet({
        itemList: ['改为公共（题目可见性不变）', '改为公共并公开夹内私有题'],
        success: (res) => {
          if (res.tapIndex === 0) this.applyVisibility('PUBLIC', false)
          if (res.tapIndex === 1) this.applyVisibility('PUBLIC', true)
        }
      })
    },
    confirmRemove(q) {
      if (!q?.id) return
      uni.showModal({
        title: '移出收藏夹',
        content: '确定将这道题移出当前收藏夹吗？不会删除系统题库中的原题。',
        success: async (res) => {
          if (!res.confirm) return
          try {
            await removeQuestionFromFolder(this.folderId, q.id)
            uni.showToast({ title: '已移出', icon: 'success' })
            this.loadDetail()
          } catch (error) {
            // request toast
          }
        }
      })
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

.info-actions {
  margin-top: 20rpx;
  display: flex;
  justify-content: flex-end;
  gap: 16rpx;
}

.ghost-btn {
  padding: 12rpx 22rpx;
  border-radius: 999rpx;
  background: #e8eef3;
  color: #304152;
  font-size: 24rpx;
  font-weight: 700;
}

.primary-btn {
  padding: 12rpx 22rpx;
  border-radius: 999rpx;
  background: #5e7387;
  color: #ffffff;
  font-size: 24rpx;
  font-weight: 700;
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

.q-actions {
  margin-top: 20rpx;
  padding-top: 18rpx;
  border-top: 1px solid #eef2f5;
  display: flex;
  justify-content: flex-end;
  gap: 28rpx;
}

.link {
  font-size: 24rpx;
  font-weight: 700;
  color: #5e7387;
}

.link.danger {
  font-size: 24rpx;
  font-weight: 700;
  color: #b42318;
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
