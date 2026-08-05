<template>
  <view class="page">
    <nav-bar title="添加题目" :showBack="true" fixed placeholder />

    <view class="search-bar">
      <input
        v-model="keyword"
        class="search-input"
        placeholder="搜索题干关键词"
        confirm-type="search"
        @confirm="refresh"
      />
      <text class="search-btn" @tap="refresh">搜索</text>
    </view>

    <scroll-view
      class="list"
      scroll-y
      @scrolltolower="loadMore"
    >
      <view v-if="loading && !questions.length" class="state-block"><text>加载中...</text></view>
      <view v-else-if="errorMessage && !questions.length" class="state-block">
        <text>加载失败</text>
        <text class="state-desc">{{ errorMessage }}</text>
        <view class="retry" @tap="refresh">重新加载</view>
      </view>
      <view v-else-if="!loading && !questions.length" class="state-block">
        <text>暂无可添加题目</text>
        <text class="state-desc">请先通过管理端「题库生成」导入系统题库，或等待已有公共题目</text>
      </view>

      <view v-for="q in questions" :key="q.id" class="q-card">
        <view class="q-head">
          <text class="q-no">{{ typeLabel(q.type) }}</text>
          <text class="q-diff">{{ difficultyLabel(q.difficulty) }}</text>
        </view>
        <text class="q-stem">{{ q.stem || '（无题干）' }}</text>
        <view class="q-actions">
          <text v-if="inFolderMap[String(q.id)]" class="added">已在收藏夹</text>
          <view
            v-else
            class="add-btn"
            :class="{ disabled: addingId === q.id }"
            @tap="addOne(q)"
          >{{ addingId === q.id ? '添加中...' : '加入收藏夹' }}</view>
        </view>
      </view>

      <view v-if="questions.length && !hasMore" class="list-end">没有更多了</view>
      <view class="list-footer"></view>
    </scroll-view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { listExamQuestions } from '@/api/examQuestion.js'
import {
  addQuestionToFolder,
  listQuestionFolderQuestions
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
      keyword: '',
      questions: [],
      inFolderMap: {},
      loading: false,
      errorMessage: '',
      current: 1,
      hasMore: true,
      addingId: null
    }
  },
  onLoad(query) {
    this.folderId = query?.folderId || query?.id || ''
    this.bootstrap()
  },
  methods: {
    async bootstrap() {
      await this.loadFolderQuestionIds()
      await this.refresh()
    },
    async loadFolderQuestionIds() {
      if (!this.folderId) return
      try {
        const res = await listQuestionFolderQuestions(this.folderId, { current: 1, size: 100 })
        const records = res?.data?.records || []
        const map = {}
        records.forEach((item) => {
          map[String(item.id)] = true
        })
        this.inFolderMap = map
      } catch (error) {
        this.inFolderMap = {}
      }
    },
    async refresh() {
      this.current = 1
      this.hasMore = true
      this.questions = []
      await this.fetchPage(true)
    },
    async loadMore() {
      if (!this.hasMore || this.loading) return
      this.current += 1
      await this.fetchPage(false)
    },
    async fetchPage(replace) {
      this.loading = true
      this.errorMessage = ''
      try {
        const res = await listExamQuestions({
          current: this.current,
          size: 20,
          keyword: this.keyword.trim() || undefined
        })
        const page = res?.data || {}
        const records = page.records || []
        this.questions = replace ? records : this.questions.concat(records)
        const total = Number(page.total || 0)
        this.hasMore = this.questions.length < total && records.length > 0
      } catch (error) {
        if (replace) this.questions = []
        this.errorMessage = error?.msg || error?.message || '请检查网络后重试'
      } finally {
        this.loading = false
      }
    },
    async addOne(q) {
      if (!this.folderId || !q?.id || this.addingId) return
      this.addingId = q.id
      try {
        await addQuestionToFolder(this.folderId, q.id)
        this.inFolderMap = { ...this.inFolderMap, [String(q.id)]: true }
        uni.showToast({ title: '已加入', icon: 'success' })
      } catch (error) {
        // request toast
      } finally {
        this.addingId = null
      }
    },
    typeLabel(type) {
      return TYPE_LABELS[type] || type || '题目'
    },
    difficultyLabel(value) {
      return DIFF_LABELS[value] || value || '—'
    }
  }
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #f6f8fb;
  box-sizing: border-box;
}

.search-bar {
  margin: 16rpx 24rpx 0;
  padding: 16rpx;
  background: #ffffff;
  border: 1px solid #e4ebf1;
  border-radius: 18rpx;
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.search-input {
  flex: 1;
  height: 68rpx;
  padding: 0 20rpx;
  background: #f8fafc;
  border: 1px solid #dfe7ee;
  border-radius: 14rpx;
  font-size: 24rpx;
}

.search-btn {
  font-size: 24rpx;
  font-weight: 700;
  color: #5e7387;
  padding: 0 8rpx;
}

.list {
  height: calc(100vh - 200rpx);
  padding: 16rpx 24rpx 0;
  box-sizing: border-box;
}

.q-card {
  background: #ffffff;
  border: 1px solid #e4ebf1;
  border-radius: 22rpx;
  padding: 28rpx;
  margin-bottom: 16rpx;
}

.q-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16rpx;
}

.q-no {
  font-size: 26rpx;
  font-weight: 700;
  color: #233243;
}

.q-diff {
  padding: 6rpx 14rpx;
  border-radius: 999rpx;
  background: #e8eef3;
  color: #516274;
  font-size: 20rpx;
  font-weight: 700;
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
}

.add-btn {
  padding: 12rpx 22rpx;
  border-radius: 999rpx;
  background: #5e7387;
  color: #ffffff;
  font-size: 24rpx;
  font-weight: 700;
}

.add-btn.disabled {
  opacity: 0.6;
}

.added {
  font-size: 24rpx;
  font-weight: 700;
  color: #91a0af;
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

.list-end {
  text-align: center;
  padding: 20rpx;
  font-size: 22rpx;
  color: #91a0af;
}

.list-footer {
  height: 40rpx;
}
</style>
