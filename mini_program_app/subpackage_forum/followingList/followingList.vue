<template>
  <view class="following-container">
    <nav-bar title="关注列表" :showBack="true" fixed placeholder />
    <view class="following-content">
      <view v-if="list.length" class="following-list">
        <view
          v-for="item in list"
          :key="item.userId"
          class="following-item"
          @click="goToUser(item)"
        >
          <image class="following-avatar" :src="item.avatar || '/static/logo.png'" mode="aspectFill" />
          <view class="following-info">
            <text class="following-name">{{ item.userName }}</text>
            <text class="following-desc">{{ item.isFollowing ? '已关注' : '未关注' }}</text>
          </view>
          <view class="following-action" @click.stop="toggleFollow(item)">
            <text>{{ item.isFollowing ? '取消关注' : '关注' }}</text>
          </view>
        </view>
      </view>
      <view v-else class="empty">
        <image
          class="empty-icon"
          mode="aspectFit"
          src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='24' height='24' viewBox='0 0 24 24' fill='none' stroke='%23e5e7eb' stroke-width='1.5' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2'/%3E%3Ccircle cx='9' cy='7' r='4'/%3E%3Cpath d='M19 8v6'/%3E%3Cpath d='M22 11h-6'/%3E%3C/svg%3E"
        />
        <text class="empty-desc">还没有关注任何人</text>
      </view>
    </view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { getFollowing, getMyFollowing, toggleFollowUser } from '@/api/forum.js'

export default {
  components: { NavBar },
  data() {
    return {
      userId: '',
      list: []
    }
  },
  onLoad(options) {
    this.userId = options.userId || ''
    this.loadList()
  },
  methods: {
    async loadList() {
      try {
        const params = { pageNum: 1, pageSize: 50 }
        const res = this.userId
          ? await getFollowing(this.userId, params)
          : await getMyFollowing(params)
        const records = res?.data?.records || []
        this.list = records
          .map((item) => {
            const id = item.userId || item.followId
            return {
              userId: id,
              userName: item.username || `用户${id || ''}`,
              avatar: item.avatar || '/static/logo.png',
              isFollowing: !!item.isFollowing
            }
          })
          .filter((item) => item.userId)
      } catch (error) {
        this.list = []
      }
    },
    async toggleFollow(item) {
      const targetId = item.userId
      if (!targetId) return
      try {
        const res = await toggleFollowUser(targetId)
        const status = res?.data || {}
        item.isFollowing = typeof status.following === 'boolean' ? status.following : !item.isFollowing
        uni.showToast({
          title: item.isFollowing ? '关注成功' : '已取消关注',
          icon: 'none'
        })
      } catch (error) {
        uni.showToast({ title: error?.message || '操作失败', icon: 'none' })
      }
    },
    goToUser(item) {
      const id = item.userId
      if (!id) return
      uni.navigateTo({
        url: `/subpackage_forum/userProfile/userProfile?id=${encodeURIComponent(id)}&name=${encodeURIComponent(item.userName || '')}&avatar=${encodeURIComponent(item.avatar || '')}`
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.following-container {
  min-height: 100vh;
  background-color: #F7F7F9;
}

.following-content {
  padding: 24rpx;
}

.following-list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.following-item {
  min-height: 112rpx;
  padding: 20rpx 24rpx;
  border-radius: 24rpx;
  background: #ffffff;
  display: flex;
  align-items: center;
  gap: 20rpx;
  box-shadow: 0 4rpx 16rpx rgba(15, 23, 42, 0.04);
}

.following-avatar {
  width: 80rpx;
  height: 80rpx;
  border-radius: 50%;
  background: #e5e7eb;
  flex-shrink: 0;
}

.following-info {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.following-name {
  font-size: 28rpx;
  font-weight: 600;
  color: #111827;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.following-desc {
  font-size: 22rpx;
  color: #9ca3af;
}

.following-action {
  min-height: 56rpx;
  padding: 0 24rpx;
  border-radius: 999rpx;
  background: #f3f4f6;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;

  text {
    font-size: 24rpx;
    color: #5C7A99;
    font-weight: 600;
  }
}

.empty {
  padding: 160rpx 0;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;

  .empty-icon {
    width: 96rpx;
    height: 96rpx;
    margin-bottom: 24rpx;
    opacity: 0.6;
  }

  .empty-desc {
    font-size: 24rpx;
    color: #d1d5db;
  }
}
</style>
