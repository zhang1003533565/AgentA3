<template>
  <view class="page">
    <nav-bar title="我的题库" :showBack="true" fixed placeholder />

    <view class="tabs">
      <view
        class="tab"
        :class="{ 'tab--active': visibility === 'PUBLIC' }"
        @tap="switchTab('PUBLIC')"
      >公共题库</view>
      <view
        class="tab"
        :class="{ 'tab--active': visibility === 'PRIVATE' }"
        @tap="switchTab('PRIVATE')"
      >私有题库</view>
    </view>

    <view v-if="isAdmin && visibility === 'PRIVATE'" class="admin-filter">
      <view class="filter-box">
        <input
          v-model="ownerKeyword"
          class="filter-input"
          placeholder="按用户名 / 学号 / 用户ID 筛选"
          confirm-type="search"
          @confirm="refresh"
        />
        <text class="filter-btn" @tap="refresh">筛选</text>
        <text v-if="ownerKeyword" class="filter-clear" @tap="clearFilter">清除</text>
      </view>
      <text class="filter-tip">管理员可查看全部私有收藏夹</text>
    </view>

    <view class="toolbar">
      <text class="toolbar-title">{{ visibility === 'PUBLIC' ? '公共收藏夹' : '私有收藏夹' }}</text>
      <view class="create-btn" @tap="openCreate">新建收藏夹</view>
    </view>

    <scroll-view
      class="list"
      scroll-y
      refresher-enabled
      :refresher-triggered="refreshing"
      @refresherrefresh="onPullRefresh"
    >
      <view v-if="loading && !folders.length" class="state-block">
        <text>加载中...</text>
      </view>
      <view v-else-if="errorMessage && !folders.length" class="state-block">
        <text>加载失败</text>
        <text class="state-desc">{{ errorMessage }}</text>
        <view class="retry" @tap="refresh">重新加载</view>
      </view>
      <view v-else-if="!loading && !folders.length" class="state-block">
        <text>暂无收藏夹</text>
        <text class="state-desc">{{ emptyHint }}</text>
        <view class="retry" @tap="openCreate">新建收藏夹</view>
      </view>

      <view
        v-for="item in folders"
        :key="item.id"
        class="card"
        @tap="openDetail(item)"
      >
        <view class="card-head">
          <text class="title">{{ item.name }}</text>
          <text class="badge">{{ item.visibilityLabel || (item.visibility === 'PUBLIC' ? '公共' : '私有') }}</text>
        </view>
        <view class="meta">
          <text>{{ item.questionCount || 0 }} 题</text>
          <text>{{ formatTime(item.updateTime || item.createTime) }}</text>
        </view>
        <view v-if="isAdmin && visibility === 'PRIVATE'" class="owner">
          所属：{{ item.ownerUsername || ('用户' + item.ownerUserId) }}
          <text v-if="item.ownerPersonalNumber"> · 学号 {{ item.ownerPersonalNumber }}</text>
        </view>
        <view class="card-actions" @tap.stop>
          <text class="link" @tap="openRename(item)">重命名</text>
          <text class="link danger" @tap="confirmDelete(item)">删除</text>
        </view>
      </view>
      <view class="list-footer"></view>
    </scroll-view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { getUserInfo } from '@/utils/storage.js'
import {
  listQuestionFolders,
  createQuestionFolder,
  renameQuestionFolder,
  deleteQuestionFolder
} from '@/api/questionFolder.js'

export default {
  components: { NavBar },
  data() {
    return {
      visibility: 'PUBLIC',
      folders: [],
      loading: false,
      refreshing: false,
      errorMessage: '',
      ownerKeyword: '',
      isAdmin: false
    }
  },
  computed: {
    emptyHint() {
      if (this.visibility === 'PUBLIC') return '创建公共收藏夹后，所有同学都能看到'
      if (this.isAdmin && this.ownerKeyword) return '没有匹配该用户的私有收藏夹'
      return '创建私有收藏夹后，仅自己可见'
    }
  },
  onLoad() {
    const info = getUserInfo() || {}
    this.isAdmin = String(info.role || '').toUpperCase() === 'ADMIN'
    this.refresh()
  },
  onShow() {
    if (this.folders.length) this.refresh()
  },
  methods: {
    switchTab(visibility) {
      if (this.visibility === visibility) return
      this.visibility = visibility
      if (visibility !== 'PRIVATE') this.ownerKeyword = ''
      this.refresh()
    },
    clearFilter() {
      this.ownerKeyword = ''
      this.refresh()
    },
    async onPullRefresh() {
      this.refreshing = true
      await this.refresh()
      this.refreshing = false
    },
    async refresh() {
      this.loading = true
      this.errorMessage = ''
      try {
        const res = await listQuestionFolders({
          visibility: this.visibility,
          ownerKeyword: this.isAdmin && this.visibility === 'PRIVATE'
            ? this.ownerKeyword.trim()
            : undefined
        })
        this.folders = res?.data || []
      } catch (error) {
        this.folders = []
        this.errorMessage = error?.msg || error?.message || '请检查网络后重试'
      } finally {
        this.loading = false
      }
    },
    openCreate() {
      uni.showModal({
        title: '新建收藏夹',
        editable: true,
        placeholderText: '请输入收藏夹名称',
        success: async (res) => {
          if (!res.confirm) return
          const name = String(res.content || '').trim()
          if (!name) {
            uni.showToast({ title: '请输入名称', icon: 'none' })
            return
          }
          try {
            await createQuestionFolder({ name, visibility: this.visibility })
            uni.showToast({ title: '已创建', icon: 'success' })
            this.refresh()
          } catch (error) {
            // request toast
          }
        }
      })
    },
    openRename(item) {
      uni.showModal({
        title: '重命名收藏夹',
        editable: true,
        placeholderText: '请输入新名称',
        content: item.name,
        success: async (res) => {
          if (!res.confirm) return
          const name = String(res.content || '').trim()
          if (!name) {
            uni.showToast({ title: '请输入名称', icon: 'none' })
            return
          }
          try {
            await renameQuestionFolder(item.id, { name })
            uni.showToast({ title: '已重命名', icon: 'success' })
            this.refresh()
          } catch (error) {
            // request toast
          }
        }
      })
    },
    confirmDelete(item) {
      uni.showModal({
        title: '删除收藏夹',
        content: `确定删除「${item.name}」吗？收藏夹内题目关联将一并清除。`,
        success: async (res) => {
          if (!res.confirm) return
          try {
            await deleteQuestionFolder(item.id)
            uni.showToast({ title: '已删除', icon: 'success' })
            this.refresh()
          } catch (error) {
            // request toast
          }
        }
      })
    },
    openDetail(item) {
      uni.navigateTo({
        url: `/subpackage_exam/questionFolderDetail/questionFolderDetail?id=${encodeURIComponent(item.id)}`
      })
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
  box-sizing: border-box;
}

.tabs {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12rpx;
  margin: 16rpx 24rpx 0;
  padding: 8rpx;
  background: #ffffff;
  border-radius: 18rpx;
  border: 1px solid #e4ebf1;
}

.tab {
  height: 72rpx;
  border-radius: 14rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26rpx;
  font-weight: 700;
  color: #667085;
}

.tab--active {
  background: #e8eef3;
  color: #304152;
}

.admin-filter {
  margin: 16rpx 24rpx 0;
  padding: 20rpx;
  background: #ffffff;
  border-radius: 18rpx;
  border: 1px solid #e4ebf1;
}

.filter-box {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.filter-input {
  flex: 1;
  height: 68rpx;
  padding: 0 20rpx;
  background: #f8fafc;
  border: 1px solid #dfe7ee;
  border-radius: 14rpx;
  font-size: 24rpx;
}

.filter-btn,
.filter-clear {
  font-size: 24rpx;
  font-weight: 700;
  color: #5e7387;
  padding: 0 8rpx;
}

.filter-tip {
  display: block;
  margin-top: 12rpx;
  font-size: 22rpx;
  color: #91a0af;
}

.toolbar {
  margin: 20rpx 24rpx 8rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.toolbar-title {
  font-size: 28rpx;
  font-weight: 700;
  color: #18222f;
}

.create-btn {
  padding: 12rpx 22rpx;
  border-radius: 999rpx;
  background: #5e7387;
  color: #ffffff;
  font-size: 24rpx;
  font-weight: 700;
}

.list {
  height: calc(100vh - 320rpx);
  padding: 8rpx 24rpx 0;
  box-sizing: border-box;
}

.card {
  margin-bottom: 16rpx;
  padding: 28rpx;
  background: #ffffff;
  border: 1px solid #e4ebf1;
  border-radius: 22rpx;
}

.card-head {
  display: flex;
  align-items: flex-start;
  gap: 16rpx;
}

.title {
  flex: 1;
  font-size: 30rpx;
  font-weight: 700;
  color: #1f2937;
}

.badge {
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

.card-actions {
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

.list-footer {
  height: 40rpx;
}
</style>
