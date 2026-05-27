<template>
  <view class="history-page">
    <nav-bar title="AI 会话历史" :showBack="true" fixed placeholder />

    <view class="history-toolbar">
      <view class="search-box">
        <image class="search-icon" src="/static/icons/line/search.svg" mode="aspectFit" />
        <input
          v-model="keyword"
          class="search-input"
          placeholder="搜索会话内容"
          confirm-type="search"
          @input="handleKeywordInput"
          @confirm="refresh"
        />
        <text v-if="keyword" class="search-clear" @click="clearKeyword">×</text>
      </view>
      <view class="new-session-btn" @click="createSession">新会话</view>
    </view>

    <scroll-view
      class="history-list"
      scroll-y
      refresher-enabled
      :refresher-triggered="refreshing"
      @refresherrefresh="refresh"
      @scrolltolower="loadMore"
    >
      <view
        v-for="item in sessions"
        :key="item.sessionId"
        class="session-card"
        @click="openSession(item.sessionId)"
      >
        <view class="session-top">
          <text class="session-title">{{ item.title || 'Leader 会话' }}</text>
          <text class="session-count">{{ item.messageCount || 0 }} 条</text>
        </view>
        <text class="session-preview">{{ item.lastMessage || '暂无消息' }}</text>
        <text class="session-time">{{ formatTime(item.updateTime || item.createTime) }}</text>
      </view>

      <view v-if="!loading && sessions.length === 0" class="empty-state">
        <text class="empty-title">{{ keyword ? '没有匹配的会话' : '还没有 AI 会话' }}</text>
        <text class="empty-desc">{{ keyword ? '换个关键词再试试，或直接开启一次新会话。' : '从悬浮助手或这里的新会话开始，对话会自动记录。' }}</text>
        <view class="empty-action" @click="createSession">开始新会话</view>
      </view>

      <view class="load-more">
        <text v-if="loading">加载中...</text>
        <text v-else-if="noMore && sessions.length">没有更多了</text>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { getLeaderSessions } from '@/api/ai.js'

export default {
  components: { NavBar },
  data() {
    return {
      sessions: [],
      keyword: '',
      searchTimer: null,
      loadedOnce: false,
      pageNum: 1,
      pageSize: 20,
      loading: false,
      refreshing: false,
      noMore: false
    }
  },
  onLoad() {
    this.loadSessions(true)
  },
  onShow() {
    if (!this.loadedOnce) {
      this.loadedOnce = true
      return
    }
    this.refresh()
  },
  onUnload() {
    if (this.searchTimer) {
      clearTimeout(this.searchTimer)
      this.searchTimer = null
    }
  },
  onPullDownRefresh() {
    this.refresh().finally(() => uni.stopPullDownRefresh())
  },
  methods: {
    async refresh() {
      this.refreshing = true
      this.pageNum = 1
      this.noMore = false
      await this.loadSessions(true)
      this.refreshing = false
    },
    async loadSessions(reset = false) {
      if (this.loading || (!reset && this.noMore)) return
      this.loading = true
      try {
        const res = await getLeaderSessions({
          pageNum: this.pageNum,
          pageSize: this.pageSize,
          keyword: this.keyword.trim()
        })
        const data = res?.data || {}
        const records = data.records || []
        this.sessions = reset ? records : [...this.sessions, ...records]
        const total = Number(data.total || 0)
        this.noMore = this.sessions.length >= total || records.length < this.pageSize
      } catch (error) {
        if (reset) this.sessions = []
      } finally {
        this.loading = false
      }
    },
    loadMore() {
      if (this.loading || this.noMore) return
      this.pageNum += 1
      this.loadSessions(false)
    },
    openSession(sessionId) {
      uni.navigateTo({
        url: `/subpackage_ai/aiConversation/aiConversation?sessionId=${encodeURIComponent(sessionId)}`
      })
    },
    createSession() {
      uni.navigateTo({ url: '/subpackage_ai/aiConversation/aiConversation' })
    },
    handleKeywordInput() {
      if (this.searchTimer) {
        clearTimeout(this.searchTimer)
      }
      this.searchTimer = setTimeout(() => {
        this.refresh()
      }, 300)
    },
    clearKeyword() {
      this.keyword = ''
      this.refresh()
    },
    formatTime(value) {
      if (!value) return ''
      return String(value).replace('T', ' ').slice(0, 16)
    }
  }
}
</script>

<style lang="scss" scoped>
.history-page {
  min-height: 100vh;
  background: #F7F7F9;
}

.history-toolbar {
  display: flex;
  align-items: center;
  gap: 18rpx;
  padding: 20rpx 24rpx 10rpx;
  background: #F7F7F9;
  box-sizing: border-box;
}

.search-box {
  flex: 1;
  min-width: 0;
  height: 76rpx;
  padding: 0 20rpx;
  border-radius: 999rpx;
  background: #FFFFFF;
  display: flex;
  align-items: center;
  box-shadow: 0 10rpx 24rpx rgba(44, 75, 130, 0.05);
}

.search-icon {
  width: 32rpx;
  height: 32rpx;
  margin-right: 12rpx;
  opacity: 0.56;
}

.search-input {
  flex: 1;
  min-width: 0;
  font-size: 26rpx;
  color: #1D1D1F;
}

.search-clear {
  width: 40rpx;
  height: 40rpx;
  border-radius: 50%;
  background: #EEF2F8;
  color: #8B94A3;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  line-height: 1;
}

.new-session-btn {
  height: 76rpx;
  padding: 0 24rpx;
  border-radius: 999rpx;
  background: linear-gradient(135deg, #2F6FE4, #5A9BFF);
  color: #FFFFFF;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26rpx;
  font-weight: 700;
  box-shadow: 0 12rpx 26rpx rgba(47, 111, 228, 0.22);
}

.history-list {
  height: calc(100vh - 194rpx);
  padding: 24rpx;
  box-sizing: border-box;
}

.session-card {
  padding: 26rpx;
  margin-bottom: 20rpx;
  background: #FFFFFF;
  border-radius: 24rpx;
  box-shadow: 0 12rpx 28rpx rgba(44, 75, 130, 0.06);
}

.session-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
}

.session-title {
  flex: 1;
  min-width: 0;
  font-size: 30rpx;
  font-weight: 700;
  color: #1D1D1F;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-count {
  padding: 6rpx 14rpx;
  border-radius: 999rpx;
  background: #EAF2FF;
  color: #2F6FE4;
  font-size: 22rpx;
}

.session-preview {
  display: -webkit-box;
  margin-top: 16rpx;
  font-size: 26rpx;
  line-height: 1.55;
  color: #5E6673;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}

.session-time {
  display: block;
  margin-top: 14rpx;
  font-size: 22rpx;
  color: #A0A7B2;
}

.empty-state {
  margin: 180rpx 32rpx 0;
  padding: 48rpx 36rpx;
  border-radius: 28rpx;
  background: #FFFFFF;
  text-align: center;
}

.empty-title {
  display: block;
  font-size: 30rpx;
  font-weight: 700;
  color: #2A2D33;
}

.empty-desc {
  display: block;
  margin-top: 12rpx;
  font-size: 24rpx;
  color: #8B94A3;
  line-height: 1.6;
}

.empty-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-top: 28rpx;
  height: 68rpx;
  padding: 0 30rpx;
  border-radius: 999rpx;
  background: #EAF2FF;
  color: #2F6FE4;
  font-size: 25rpx;
  font-weight: 700;
}

.load-more {
  padding: 28rpx 0 56rpx;
  text-align: center;
  color: #A0A7B2;
  font-size: 24rpx;
}
</style>
