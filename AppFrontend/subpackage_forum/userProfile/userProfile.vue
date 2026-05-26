<template>
  <view class="profile-container">
    <nav-bar title="个人主页" :showBack="true" :glass="true" />
    <view class="profile-content">
      <!-- 用户信息卡片 -->
      <view class="profile-card">
        <image class="profile-avatar" :src="userInfo.avatar || '/static/logo.png'" mode="aspectFill" />
        <text class="profile-name">{{ userInfo.userName }}</text>
        <text class="profile-bio" v-if="userInfo.bio">{{ userInfo.bio }}</text>
        <view class="profile-stats">
          <view class="stat-item">
            <text class="stat-num">{{ userInfo.postCount ?? 0 }}</text>
            <text class="stat-label">帖子</text>
          </view>
          <view class="stat-item">
            <text class="stat-num">{{ userInfo.followCount ?? 0 }}</text>
            <text class="stat-label">关注</text>
          </view>
          <view class="stat-item">
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

      <view class="placeholder-section">
        <text class="placeholder-title">TA 的帖子</text>
        <view v-if="postList.length" class="post-list">
          <view
            v-for="item in postList"
            :key="item.id"
            class="post-item"
            @click="goToPost(item.id)"
          >
            <text class="post-title">{{ item.title || '无标题帖子' }}</text>
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
    </view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { getFollowStatus, getMyForumPosts, getPostList, getUserPosts, toggleFollowUser } from '@/api/forum.js'
import { getUserInfo } from '@/utils/storage.js'

export default {
  components: { NavBar },
  data() {
    return {
      userId: '',
      userAvatar: '',
      isSelf: false,
      postList: [],
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
    this.userId = options.id || options.userId || ''
    this.userName = options.name || ''
    this.userAvatar = decodeURIComponent(options.avatar || '')
    this.loadUserProfile()
  },
  methods: {
    async loadUserProfile() {
      const localUser = getUserInfo() || {}
      // 如果有传入userId，显示该用户的主页；否则显示当前登录用户的主页
      const targetId = this.userId || localUser.id || localUser.userId || ''
      this.userId = targetId
      
      // 判断是否是查看其他用户的主页
      const localUserId = String(localUser.id || localUser.userId || '')
      const currentTargetId = String(this.userId || '')
      const isOtherUser = !!currentTargetId && !!localUserId && currentTargetId !== localUserId
      this.isSelf = !isOtherUser
      
      if (isOtherUser) {
        // 查看其他用户：使用传入的ID和用户名
        this.userInfo = {
          ...this.userInfo,
          userId: targetId,
          userName: this.userName || `用户${targetId}`,
          avatar: this.userAvatar || '/static/logo.png',
          bio: '这个人很懒，什么都没写~'
        }
      } else {
        // 查看自己的主页：使用本地用户信息
        const seed = localUser.username || targetId || 'user'
        this.userInfo = {
          ...this.userInfo,
          userId: targetId,
          userName: localUser.username || localUser.realName || `用户${targetId || ''}`,
          avatar: localUser.avatar || this.userAvatar || `https://api.dicebear.com/7.x/avataaars/svg?seed=${encodeURIComponent(seed)}`,
          bio: localUser.college ? `${localUser.college}${localUser.major ? ` · ${localUser.major}` : ''}` : '这个人很懒，什么都没写~'
        }
      }

      await Promise.all([this.loadPosts(), this.loadFollowMeta()])
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
          likeCount: item.likeCount || 0,
          commentCount: item.commentCount || 0,
          createTime: this.formatDateTime(item.createTime)
        }))
        this.userInfo.postCount = Number(res?.data?.total || this.postList.length)
        if (records.length && !this.userInfo.userName.startsWith('用户')) {
          return
        }
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
    async toggleFollow() {
      if (!this.userId) {
        uni.showToast({ title: '当前用户信息不完整', icon: 'none' })
        return
      }
      try {
        await toggleFollowUser(this.userId)
        this.userInfo.isFollow = !this.userInfo.isFollow
        this.userInfo.fansCount += this.userInfo.isFollow ? 1 : -1
        uni.showToast({
          title: this.userInfo.isFollow ? '关注成功' : '已取消关注',
          icon: 'none'
        })
      } catch (error) {}
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
  padding-top: 140rpx; /* 避免卡片负 margin 与导航栏重叠，保留顶部渐变可见 */
}

.profile-card {
  position: relative;
  background-color: #ffffff;
  border-radius: 32rpx;
  padding: 72rpx 32rpx 48rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  margin: -80rpx 8rpx 24rpx;
  box-shadow: 0 24rpx 60rpx rgba(15, 23, 42, 0.08);
  border: 1rpx solid #f9fafb;

  .profile-avatar {
    width: 168rpx;
    height: 168rpx;
    border-radius: 50%;
    margin-bottom: 28rpx;
    border: 8rpx solid #ffffff;
    box-shadow: 0 16rpx 60rpx rgba(0, 0, 0, 0.08);
    margin-top: -84rpx;
    background-color: #e5e7eb;
  }

  .profile-name {
    font-size: 44rpx;
    font-weight: 700;
    color: #111827;
    margin-bottom: 12rpx;
    letter-spacing: 0.08em;
  }

  .profile-bio {
    font-size: 26rpx;
    color: #6b7280;
    text-align: center;
    margin-bottom: 40rpx;
    max-width: 100%;
    line-height: 1.625;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  .profile-stats {
    width: 100%;
    display: flex;
    align-items: stretch;
    justify-content: space-between;
    margin-bottom: 36rpx;

    .stat-item {
      flex: 1;
      position: relative;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 0 12rpx;
      gap: 8rpx;

      & + .stat-item::before {
        content: '';
        position: absolute;
        left: 0;
        top: 50%;
        transform: translateY(-50%);
        width: 1rpx;
        height: 60%;
        min-height: 40rpx;
        background: rgba(243, 244, 246, 0.9);
        border-radius: 999rpx;
      }

      .stat-num {
        font-size: 32rpx;
        font-weight: 700;
        color: #111827;
      }

      .stat-label {
        font-size: 22rpx;
        color: #9ca3af;
      }
    }
  }

  .follow-btn {
    min-height: 88rpx;
    padding: 0 72rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    background-color: #111827;
    border-radius: 999rpx;
    transform-origin: center;
    transition: transform 0.12s ease-out, background-color 0.12s ease-out;

    text {
      font-size: 28rpx;
      font-weight: 600;
      letter-spacing: 0.06em;
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

.placeholder-section {
  background-color: #ffffff;
  border-radius: 32rpx;
  padding: 24rpx 24rpx 32rpx;
  margin: 0 8rpx 24rpx;
  border-top: 1rpx solid #e5e7eb;

  .placeholder-title {
    position: relative;
    font-size: 30rpx;
    font-weight: 700;
    color: #111827;
    letter-spacing: -0.025em;
    display: block;
    margin-bottom: 24rpx;
    padding-left: 28rpx;

    &::before {
      content: '';
      position: absolute;
      left: 0;
      top: 50%;
      transform: translateY(-50%);
      width: 12rpx;
      height: 32rpx;
      border-radius: 999rpx;
      background: linear-gradient(to bottom, #6366f1, #3b82f6);
    }
  }

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

  .post-meta {
    display: flex;
    gap: 24rpx;
    margin-top: 12rpx;
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
