<template>
  <view class="profile-container">
    <nav-bar title="个人主页" :showBack="true" :glass="true" />
    <view class="profile-content">
      <view class="profile-card">
        <image class="profile-avatar" :src="userInfo.avatar || '/static/logo.png'" mode="aspectFill" />
        <text class="profile-name">{{ userInfo.userName }}</text>
        <view class="profile-stats">
          <view class="stat-item" @click="goToFollowing">
            <text class="stat-num">{{ userInfo.followCount ?? 0 }}</text>
            <text class="stat-label">关注</text>
          </view>
          <view class="stat-item" @click="goToFollowers">
            <text class="stat-num">{{ userInfo.fansCount ?? 0 }}</text>
            <text class="stat-label">粉丝</text>
          </view>
        </view>
        <view class="follow-btn" v-if="!isSelf && !userInfo.isFollow" @click="toggleFollow">
          <text>+ 关注</text>
        </view>
        <view class="follow-btn followed" v-else-if="!isSelf" @click="toggleFollow">
          <text>已关注</text>
        </view>
      </view>

      <!-- 帖子分类 Tab：我的帖子 / 赞过的帖子 -->
      <view class="post-section">
        <view class="post-tabs">
          <view
            class="post-tab"
            :class="{ active: activeTab === 'posts' }"
            @click="activeTab = 'posts'"
          >
            <text>我的帖子</text>
            <text class="tab-count">{{ postList.length }}</text>
          </view>
          <view
            class="post-tab"
            :class="{ active: activeTab === 'likes' }"
            @click="activeTab = 'likes'"
          >
            <text>赞过的帖子</text>
            <text class="tab-count">{{ likedList.length }}</text>
          </view>
        </view>

        <!-- 我的帖子 -->
        <view v-if="activeTab === 'posts'">
          <view v-if="postList.length" class="post-list">
            <view
              v-for="item in postList"
              :key="item.id"
              class="post-item"
              @click="goToPost(item.id)"
            >
              <text class="post-title">{{ item.title || '无标题帖子' }}</text>
              <text class="post-excerpt" v-if="item.content">{{ item.content }}</text>
              <view class="post-images" v-if="item.images && item.images.length">
                <image
                  v-for="(img, imgIndex) in item.images.slice(0, 3)"
                  :key="imgIndex"
                  class="post-image"
                  :src="img"
                  mode="aspectFill"
                />
              </view>
              <view class="post-meta">
                <text>{{ item.createTime }}</text>
                <text>{{ item.likeCount || 0 }} 赞</text>
                <text>{{ item.commentCount || 0 }} 评</text>
              </view>
            </view>
          </view>
          <view v-else class="placeholder-empty">
            <image
              class="empty-icon"
              mode="aspectFit"
              src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='24' height='24' viewBox='0 0 24 24' fill='none' stroke='%23e5e7eb' stroke-width='1.5' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2z'/%3E%3Cpolyline points='14 2 14 8 20 8'/%3E%3Cpath d='M9 15h2'/%3E%3Cpath d='M9 11h6'/%3E%3C/svg%3E"
            />
            <text class="placeholder-desc">暂时没有可展示的帖子</text>
          </view>
        </view>

        <!-- 赞过的帖子 -->
        <view v-else>
          <view v-if="likedList.length" class="post-list">
            <view
              v-for="item in likedList"
              :key="item.id"
              class="post-item"
              @click="goToPost(item.id)"
            >
              <text class="post-title">{{ item.title || '无标题帖子' }}</text>
              <text class="post-excerpt" v-if="item.content">{{ item.content }}</text>
              <view class="post-images" v-if="item.images && item.images.length">
                <image
                  v-for="(img, imgIndex) in item.images.slice(0, 3)"
                  :key="imgIndex"
                  class="post-image"
                  :src="img"
                  mode="aspectFill"
                />
              </view>
              <view class="post-meta">
                <text>{{ item.createTime }}</text>
                <text>{{ item.likeCount || 0 }} 赞</text>
                <text>{{ item.commentCount || 0 }} 评</text>
              </view>
            </view>
          </view>
          <view v-else class="placeholder-empty">
            <image
              class="empty-icon"
              mode="aspectFit"
              src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='24' height='24' viewBox='0 0 24 24' fill='none' stroke='%23e5e7eb' stroke-width='1.5' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='M14 9V5a3 3 0 0 0-3-3l-4 9v11h11.28a2 2 0 0 0 2-1.7l1.38-9a2 2 0 0 0-2-2.3H14zM7 22H4a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2h3'/%3E%3C/svg%3E"
            />
            <text class="placeholder-desc">还没有赞过的帖子</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import {
  getFollowStatus,
  getMyForumPosts,
  getPostList,
  getUserLikes,
  getUserPosts,
  parseImageList,
  toggleFollowUser
} from '@/api/forum.js'
import { getCurrentUserId, getUserInfo } from '@/utils/storage.js'

export default {
  components: { NavBar },
  data() {
    return {
      userId: '',
      routeUserId: '',
      userAvatar: '',
      isSelf: false,
      activeTab: 'posts',
      postList: [],
      likedList: [],
      userInfo: {
        userId: '',
        userName: '用户',
        avatar: '',
        bio: '这个人很懒，什么都没写~',
        postCount: 0,
        followCount: 0,
        fansCount: 0,
        isFollow: false
      },
      userName: ''
    }
  },
  onLoad(options) {
    this.routeUserId = options.id || options.userId || ''
    this.userId = this.routeUserId
    this.userName = options.name || ''
    this.userAvatar = decodeURIComponent(options.avatar || '')
    this.loadUserProfile()
  },
  methods: {
    async loadUserProfile() {
      const localUser = getUserInfo() || {}
      const explicitTargetId = this.routeUserId || this.userId || ''
      const currentUserId = getCurrentUserId()
      const targetId = explicitTargetId || currentUserId || localUser.id || localUser.userId || ''
      this.userId = targetId

      const localUserId = String(currentUserId || localUser.id || localUser.userId || '')
      const currentTargetId = String(this.userId || '')
      const isOtherUser = !!explicitTargetId && (!localUserId || currentTargetId !== localUserId)
      this.isSelf = !isOtherUser

      if (isOtherUser) {
        this.userInfo = {
          ...this.userInfo,
          userId: targetId,
          userName: this.userName || `用户${targetId}`,
          avatar: this.userAvatar || '/static/logo.png',
          bio: '这个人很懒，什么都没写~'
        }
      } else {
        const seed = localUser.username || targetId || 'user'
        this.userInfo = {
          ...this.userInfo,
          userId: targetId,
          userName: localUser.username || localUser.realName || `用户${targetId || ''}`,
          avatar: localUser.avatar || this.userAvatar || `https://api.dicebear.com/7.x/avataaars/svg?seed=${encodeURIComponent(seed)}`,
          bio: localUser.college ? `${localUser.college}${localUser.major ? ` · ${localUser.major}` : ''}` : '这个人很懒，什么都没写~'
        }
      }

      await Promise.all([this.loadPosts(), this.loadLikedPosts(), this.loadFollowMeta()])
    },
    async loadPosts() {
      if (!this.isSelf && !this.userId) return
      try {
        let res
        if (this.isSelf) {
          try {
            res = await getMyForumPosts({ pageNum: 1, pageSize: 20 })
          } catch (error) {
            res = this.userId
              ? await getUserPosts(this.userId, { pageNum: 1, pageSize: 20 })
              : await getPostList({ pageNum: 1, pageSize: 20 })
          }
        } else {
          res = await getUserPosts(this.userId, { pageNum: 1, pageSize: 20 })
        }
        const records = res?.data?.records || []
        this.postList = records.map((item) => ({
          id: item.id,
          title: item.title || '',
          content: item.content || '',
          images: parseImageList(item.images),
          likeCount: item.likeCount || 0,
          commentCount: item.commentCount || 0,
          createTime: this.formatDateTime(item.createTime)
        }))
        this.userInfo.postCount = Number(res?.data?.total || this.postList.length)
      } catch (error) {
        this.postList = []
        this.userInfo.postCount = 0
      }
    },
    async loadFollowMeta() {
      if (!this.userId) return
      try {
        const res = await getFollowStatus(this.userId)
        this.userInfo.isFollow = !!res?.data?.following
        this.userInfo.fansCount = Number(res?.data?.followerCount || 0)
        this.userInfo.followCount = Number(res?.data?.followingCount || 0)
      } catch (error) {
        this.userInfo.isFollow = false
      }
    },
    async loadLikedPosts() {
      if (!this.userId) return
      try {
        const res = await getUserLikes(this.userId, { pageNum: 1, pageSize: 50 })
        const records = res?.data?.records || []
        this.likedList = records
          .filter((item) => item.postId)
          .map((item) => ({
            id: item.postId,
            title: item.postTitle || '',
            content: item.content || '',
            images: parseImageList(item.images),
            likeCount: item.likeCount || 0,
            commentCount: item.commentCount || 0,
            createTime: this.formatDateTime(item.createTime)
          }))
      } catch (error) {
        this.likedList = []
      }
    },
    async toggleFollow() {
      if (!this.userId) {
        uni.showToast({ title: '当前用户信息不完整', icon: 'none' })
        return
      }
      try {
        const res = await toggleFollowUser(this.userId)
        const status = res?.data || {}
        const nextFollowing = typeof status.following === 'boolean' ? status.following : !this.userInfo.isFollow
        this.userInfo.isFollow = nextFollowing
        if (status.followerCount !== undefined) {
          this.userInfo.fansCount = Number(status.followerCount || 0)
        } else {
          this.userInfo.fansCount = Math.max(0, this.userInfo.fansCount + (nextFollowing ? 1 : -1))
        }
        uni.showToast({
          title: nextFollowing ? '关注成功' : '已取消关注',
          icon: 'none'
        })
      } catch (error) {
        uni.showToast({ title: error?.message || '操作失败', icon: 'none' })
      }
    },
    goToFollowing() {
      uni.navigateTo({
        url: `/subpackage_forum/followingList/followingList?userId=${encodeURIComponent(this.userId || '')}&name=${encodeURIComponent(this.userInfo.userName || '')}`
      })
    },
    goToFollowers() {
      uni.navigateTo({
        url: `/subpackage_forum/followersList/followersList?userId=${encodeURIComponent(this.userId || '')}&name=${encodeURIComponent(this.userInfo.userName || '')}`
      })
    },
    goToPost(id) {
      uni.navigateTo({
        url: `/subpackage_forum/postDetail/postDetail?id=${id}`
      })
    },
    formatDateTime(value) {
      if (!value) return ''
      return String(value).replace('T', ' ').slice(0, 16)
    }
  }
}
</script>

<style lang="scss" scoped>
.profile-container {
  min-height: 100vh;
  background: linear-gradient(to bottom, #f8fafc 0, #eff6ff 280rpx, #f7f8fa 280rpx, #f7f8fa 100%);
}

.profile-content {
  padding: 24rpx;
  padding-top: 140rpx;
}

.profile-card {
  position: relative;
  background-color: #ffffff;
  border-radius: 24rpx;
  padding: 32rpx 24rpx 24rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  margin: -48rpx 8rpx 16rpx;
  box-shadow: 0 8rpx 24rpx rgba(15, 23, 42, 0.05);
  border: 1rpx solid #f9fafb;

  .profile-avatar {
    width: 96rpx;
    height: 96rpx;
    border-radius: 50%;
    margin-bottom: 12rpx;
    border: 4rpx solid #ffffff;
    box-shadow: 0 6rpx 20rpx rgba(0, 0, 0, 0.06);
    margin-top: -48rpx;
    background-color: #e5e7eb;
  }

  .profile-name {
    font-size: 30rpx;
    font-weight: 700;
    color: #111827;
    margin-bottom: 16rpx;
    letter-spacing: 0.02em;
  }

  .profile-stats {
    width: 100%;
    display: flex;
    align-items: stretch;
    justify-content: space-between;
    margin-bottom: 20rpx;

    .stat-item {
      flex: 1;
      position: relative;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 0 12rpx;
      gap: 4rpx;

      & + .stat-item::before {
        content: '';
        position: absolute;
        left: 0;
        top: 50%;
        transform: translateY(-50%);
        width: 1rpx;
        height: 60%;
        min-height: 32rpx;
        background: rgba(243, 244, 246, 0.9);
        border-radius: 999rpx;
      }

      .stat-num {
        font-size: 28rpx;
        font-weight: 700;
        color: #111827;
      }

      .stat-label {
        font-size: 20rpx;
        color: #9ca3af;
      }
    }
  }

  .follow-btn {
    min-height: 64rpx;
    padding: 0 56rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    background-color: #111827;
    border-radius: 999rpx;
    transform-origin: center;
    transition: transform 0.12s ease-out, background-color 0.12s ease-out;

    text {
      font-size: 24rpx;
      font-weight: 600;
      letter-spacing: 0.02em;
      color: #ffffff;
    }

    &:active {
      transform: scale(0.95);
    }

    &.followed {
      background-color: #f3f4f6;

      text {
        color: #6b7280;
      }
    }
  }
}

.post-section {
  background-color: #ffffff;
  border-radius: 24rpx;
  padding: 24rpx 24rpx 32rpx;
  margin: 0 8rpx 24rpx;

  .post-tabs {
    display: flex;
    background-color: #f3f4f6;
    border-radius: 999rpx;
    padding: 6rpx;
    margin-bottom: 24rpx;

    .post-tab {
      flex: 1;
      height: 64rpx;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8rpx;
      border-radius: 999rpx;
      font-size: 26rpx;
      color: #6b7280;
      transition: all 0.2s ease;

      .tab-count {
        font-size: 22rpx;
        color: #9ca3af;
      }

      &.active {
        background-color: #ffffff;
        color: #111827;
        font-weight: 600;
        box-shadow: 0 4rpx 12rpx rgba(15, 23, 42, 0.06);

        .tab-count {
          color: #5C7A99;
        }
      }
    }
  }
}

.placeholder-section {
  background-color: #ffffff;
  border-radius: 24rpx;
  padding: 24rpx 24rpx 32rpx;
  margin: 0 8rpx 24rpx;
  border-top: 1rpx solid #e5e7eb;
}

.post-section {
  .post-list {
    display: flex;
    flex-direction: column;
    gap: 20rpx;
  }

  .post-item {
    padding: 24rpx;
    border-radius: 24rpx;
    background: #f8fafc;
  }

  .post-title {
    display: block;
    font-size: 28rpx;
    line-height: 1.5;
    color: #111827;
    font-weight: 600;
  }

  .post-excerpt {
    display: -webkit-box;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 2;
    overflow: hidden;
    margin-top: 12rpx;
    font-size: 26rpx;
    line-height: 1.6;
    color: #6b7280;
  }

  .post-images {
    display: flex;
    gap: 12rpx;
    margin-top: 16rpx;

    .post-image {
      width: 180rpx;
      height: 140rpx;
      border-radius: 16rpx;
      background: #e5e7eb;
      flex-shrink: 0;
    }
  }

  .post-meta {
    display: flex;
    gap: 24rpx;
    margin-top: 16rpx;
    font-size: 22rpx;
    color: #9ca3af;
  }

  .placeholder-empty {
    padding: 80rpx 0;
    text-align: center;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;

    &.compact {
      padding: 48rpx 0;
    }

    .empty-icon {
      width: 96rpx;
      height: 96rpx;
      margin-bottom: 24rpx;
      opacity: 0.6;
    }

    .placeholder-desc {
      font-size: 24rpx;
      color: #d1d5db;
    }
  }
}
</style>
