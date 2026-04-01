<template>
  <view class="detail-container">
    <nav-bar title="帖子详情" :showBack="true" />
    <!-- 帖子内容 -->
    <view class="post-section">
      <!-- 用户信息（点击头像/昵称进入个人主页） -->
      <view class="post-header">
        <view class="user-avatar-wrap" @click="goUserProfile(postDetail)">
          <image class="user-avatar" :src="postDetail.avatar || '/static/logo.png'" mode="aspectFill" />
        </view>
        <view class="user-info" @click="goUserProfile(postDetail)">
          <text class="user-name">{{ postDetail.userName }}</text>
          <text class="post-time">{{ postDetail.createTime }}</text>
        </view>
        <view class="follow-btn" v-if="!postDetail.isFollow" @click.stop="toggleFollow">
          <text>+ 关注</text>
        </view>
        <view class="follow-btn followed" v-else @click.stop="toggleFollow">
          <text>已关注</text>
        </view>
      </view>

      <!-- 帖子正文 -->
      <view class="post-content">
        <text class="post-title" v-if="postDetail.title">{{ postDetail.title }}</text>
        <text class="post-text">{{ postDetail.content }}</text>
        <view class="post-images" v-if="postDetail.images && postDetail.images.length">
          <image 
            v-for="(img, index) in postDetail.images" 
            :key="index"
            class="post-image"
            :src="img"
            mode="aspectFill"
            @click="previewImage(index)"
          />
        </view>
        <view class="topic-tags" v-if="postDetail.topicName">
          <text class="topic-tag"># {{ postDetail.topicName }}</text>
        </view>
      </view>

      <!-- 互动数据 -->
      <view class="post-stats">
        <text class="stat-item">{{ postDetail.likeCount || 0 }} 点赞</text>
        <text class="stat-divider">·</text>
        <text class="stat-item">{{ postDetail.commentCount || 0 }} 评论</text>
        <text class="stat-divider">·</text>
        <text class="stat-item">{{ postDetail.viewCount || 0 }} 浏览</text>
      </view>
    </view>

    <!-- 评论列表 -->
    <view class="comment-section">
      <view class="section-title">
        <text>全部评论 ({{ commentList.length }})</text>
      </view>

      <view class="comment-list">
        <view 
          v-for="(item, index) in commentList" 
          :key="index"
          class="comment-item"
        >
          <view class="comment-avatar-wrap" @click="goUserProfile(item)">
            <image class="comment-avatar" :src="item.avatar || '/static/logo.png'" mode="aspectFill" />
          </view>
          <view class="comment-content">
            <view class="comment-header">
              <text class="comment-name comment-name--link" @click="goUserProfile(item)">{{ item.userName }}</text>
              <text class="comment-time">{{ item.createTime }}</text>
            </view>
            <text class="comment-text">{{ item.content }}</text>
            <view class="comment-actions">
              <view class="action-item" @click="toggleCommentLike(item)">
                <text class="action-icon">{{ item.isLiked ? '❤️' : '🤍' }}</text>
                <text class="action-count">{{ item.likeCount || '' }}</text>
              </view>
              <view class="action-item" @click="replyComment(item)">
                <text class="action-icon">💬</text>
                <text class="action-count">回复</text>
              </view>
            </view>

            <!-- 子评论 -->
            <view class="sub-comments" v-if="item.replies && item.replies.length">
              <view 
                v-for="(reply, rIndex) in item.replies" 
                :key="rIndex"
                class="sub-comment-item"
              >
                <text class="sub-comment-user">{{ reply.userName }}</text>
                <text class="sub-comment-text">：{{ reply.content }}</text>
              </view>
            </view>
          </view>
        </view>

        <view v-if="commentList.length === 0" class="empty-comment">
          <text>暂无评论，快来抢沙发吧~</text>
        </view>
      </view>
    </view>

    <!-- 底部操作栏 -->
    <view class="bottom-bar">
      <view class="comment-input" @click="showCommentInput">
        <text class="input-placeholder">写评论...</text>
      </view>
      <view class="action-btns">
        <view class="action-btn" @click="toggleLike">
          <text class="btn-icon">{{ postDetail.isLiked ? '❤️' : '🤍' }}</text>
          <text class="btn-text">{{ postDetail.likeCount || 0 }}</text>
        </view>
        <view class="action-btn" @click="collectPost">
          <text class="btn-icon">{{ postDetail.isCollected ? '⭐' : '☆' }}</text>
          <text class="btn-text">{{ postDetail.collectCount || 0 }}</text>
        </view>
        <view class="action-btn" @click="sharePost">
          <text class="btn-icon">🔗</text>
          <text class="btn-text">分享</text>
        </view>
      </view>
    </view>

    <!-- 评论输入弹窗 -->
    <view class="comment-popup" v-if="showCommentPopup" @click="hideCommentInput">
      <view class="popup-content" @click.stop>
        <textarea 
          class="comment-textarea"
          v-model="commentContent"
          :placeholder="replyTarget ? `回复 ${replyTarget.userName}...` : '写下你的评论...'"
          :focus="showCommentPopup"
          :maxlength="500"
        />
        <view class="popup-actions">
          <text class="char-count">{{ commentContent.length }}/500</text>
          <view class="submit-btn" @click="submitComment">
            <text>发送</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import {
  createComment,
  getCommentList,
  getFollowStatus,
  getPostDetail,
  parseImageList,
  toggleFollowUser,
  togglePostLike
} from '@/api/forum.js'

export default {
  components: { NavBar },
  data() {
    return {
      postId: null,
      postDetail: {
        id: null,
        userId: '',
        userName: '',
        avatar: '',
        title: '',
        content: '',
        images: [],
        topicName: '',
        likeCount: 0,
        commentCount: 0,
        viewCount: 0,
        collectCount: 0,
        isLiked: false,
        isCollected: false,
        isFollow: false,
        createTime: ''
      },
      commentList: [],
      showCommentPopup: false,
      commentContent: '',
      replyTarget: null
    }
  },
  onLoad(options) {
    this.postId = options.id
    this.loadPostDetail()
    this.loadComments()
  },
  methods: {
    async loadPostDetail() {
      try {
        const res = await getPostDetail(this.postId)
        const data = res?.data || {}
        this.postDetail = {
          id: data.id,
          userId: data.userId,
          userName: data.username || '匿名用户',
          avatar: data.avatar || '/static/logo.png',
          title: data.title || '',
          content: data.content || '',
          images: parseImageList(data.images),
          topicName: data.topicName || '',
          likeCount: data.likeCount || 0,
          commentCount: data.commentCount || 0,
          viewCount: data.viewCount || 0,
          collectCount: 0,
          isLiked: !!data.isLiked,
          isCollected: !!data.isFavorited,
          isFollow: false,
          createTime: this.formatDateTime(data.createTime)
        }
        await this.loadFollowStatus()
      } catch (error) {
        uni.showToast({ title: '帖子加载失败', icon: 'none' })
      }
    },
    async loadFollowStatus() {
      if (!this.postDetail.userId) return
      try {
        const res = await getFollowStatus(this.postDetail.userId)
        this.postDetail.isFollow = !!res?.data?.following
      } catch (error) {
        this.postDetail.isFollow = false
      }
    },
    goUserProfile(user) {
      const uid = user.userId || user.id || ''
      uni.navigateTo({
        url: '/subpackage_forum/userProfile/userProfile?id=' + encodeURIComponent(uid)
      })
    },
    async loadComments() {
      try {
        const res = await getCommentList({
          postId: this.postId,
          pageNum: 1,
          pageSize: 50
        })
        const records = res?.data?.records || []
        this.commentList = records.map(this.formatCommentItem)
      } catch (error) {
        this.commentList = []
      }
    },
    formatCommentItem(item) {
      return {
        id: item.id,
        userId: item.userId,
        userName: item.username || '匿名用户',
        avatar: item.avatar || '/static/logo.png',
        content: item.content || '',
        likeCount: item.likeCount || 0,
        isLiked: !!item.isLiked,
        createTime: this.formatDateTime(item.createTime),
        replies: (item.children || []).map((child) => ({
          id: child.id,
          userId: child.userId,
          userName: child.username || '匿名用户',
          content: child.content || '',
          replyToUsername: child.replyToUsername || ''
        }))
      }
    },
    previewImage(index) {
      uni.previewImage({
        urls: this.postDetail.images,
        current: index
      })
    },
    async toggleFollow() {
      if (!this.postDetail.userId) return
      try {
        await toggleFollowUser(this.postDetail.userId)
        this.postDetail.isFollow = !this.postDetail.isFollow
        uni.showToast({
          title: this.postDetail.isFollow ? '关注成功' : '已取消关注',
          icon: 'none'
        })
      } catch (error) {}
    },
    async toggleLike() {
      try {
        const res = await togglePostLike(this.postId)
        this.postDetail.isLiked = !!res?.data?.liked
        this.postDetail.likeCount = Number(res?.data?.likeCount ?? this.postDetail.likeCount)
      } catch (error) {}
    },
    collectPost() {
      uni.showToast({ title: '帖子收藏接口暂未开放', icon: 'none' })
    },
    sharePost() {
      uni.showActionSheet({
        itemList: ['复制链接', '分享到微信'],
        success: (res) => {
          if (res.tapIndex === 0) {
            uni.setClipboardData({
              data: `https://campus.edu.cn/forum/post/${this.postId}`,
              success: () => {
                uni.showToast({ title: '链接已复制', icon: 'success' })
              }
            })
          }
        }
      })
    },
    showCommentInput() {
      this.showCommentPopup = true
      this.replyTarget = null
    },
    hideCommentInput() {
      this.showCommentPopup = false
      this.commentContent = ''
      this.replyTarget = null
    },
    replyComment(item) {
      this.replyTarget = item
      this.showCommentPopup = true
    },
    toggleCommentLike(item) {
      uni.showToast({ title: '评论点赞暂未开放', icon: 'none' })
    },
    async submitComment() {
      if (!this.commentContent.trim()) {
        uni.showToast({ title: '请输入评论内容', icon: 'none' })
        return
      }
      try {
        await createComment({
          postId: this.postId,
          content: this.commentContent.trim(),
          parentId: this.replyTarget ? this.replyTarget.id : null,
          replyToId: this.replyTarget ? this.replyTarget.userId : null
        })
        uni.showToast({ title: '评论成功', icon: 'success' })
        this.hideCommentInput()
        this.loadPostDetail()
        this.loadComments()
      } catch (error) {}
    },
    formatDateTime(value) {
      if (!value) return '刚刚'
      return String(value).replace('T', ' ').slice(0, 16)
    }
  }
}
</script>

<style lang="scss">
.detail-container {
  min-height: 100vh;
  background-color: #F7F7F9;
  padding-bottom: 120rpx;
}

.post-section {
  background-color: #FFFFFF;
  padding: 24rpx;
  margin-bottom: 20rpx;

  .post-header {
    display: flex;
    align-items: center;
    margin-bottom: 24rpx;

    .user-avatar-wrap {
      margin-right: 16rpx;
      flex-shrink: 0;
    }

    .user-avatar {
      width: 80rpx;
      height: 80rpx;
      border-radius: 50%;
      display: block;
    }

    .user-info {
      flex: 1;
      display: flex;
      flex-direction: column;

      .user-name {
        font-size: 30rpx;
        font-weight: 600;
        color: #1D1D1F;
      }

      .post-time {
        font-size: 24rpx;
        color: #8E8E93;
        margin-top: 4rpx;
      }
    }

    .follow-btn {
      padding: 12rpx 28rpx;
      background-color: #5C7A99;
      border-radius: 32rpx;

      text {
        font-size: 26rpx;
        color: #FFFFFF;
      }

      &.followed {
        background-color: #F5F5F7;

        text {
          color: #8E8E93;
        }
      }
    }
  }

  .post-content {
    .post-title {
      display: block;
      font-size: 36rpx;
      font-weight: 700;
      color: #1D1D1F;
      margin-bottom: 16rpx;
      line-height: 1.4;
    }

    .post-text {
      font-size: 30rpx;
      color: #4A4A4A;
      line-height: 1.8;
      white-space: pre-wrap;
    }

    .post-images {
      display: flex;
      flex-wrap: wrap;
      gap: 12rpx;
      margin-top: 20rpx;

      .post-image {
        width: calc(33.33% - 8rpx);
        height: 200rpx;
        border-radius: 12rpx;
        background-color: #F5F5F7;
      }
    }

    .topic-tags {
      margin-top: 20rpx;

      .topic-tag {
        font-size: 24rpx;
        color: #5C7A99;
        background-color: rgba(92, 122, 153, 0.1);
        padding: 8rpx 20rpx;
        border-radius: 24rpx;
      }
    }
  }

  .post-stats {
    margin-top: 24rpx;
    padding-top: 20rpx;
    border-top: 1rpx solid #F0F0F0;

    .stat-item {
      font-size: 24rpx;
      color: #8E8E93;
    }

    .stat-divider {
      margin: 0 12rpx;
      color: #E0E0E0;
    }
  }
}

.comment-section {
  background-color: #FFFFFF;
  padding: 24rpx;

  .section-title {
    font-size: 30rpx;
    font-weight: 600;
    color: #1D1D1F;
    margin-bottom: 24rpx;
  }

  .comment-item {
    display: flex;
    padding: 20rpx 0;
    border-bottom: 1rpx solid #F5F5F7;

    .comment-avatar-wrap {
      margin-right: 16rpx;
      flex-shrink: 0;
    }

    .comment-avatar {
      width: 64rpx;
      height: 64rpx;
      border-radius: 50%;
      display: block;
    }

    .comment-content {
      flex: 1;
      min-width: 0;

      .comment-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 8rpx;

        .comment-name {
          font-size: 26rpx;
          font-weight: 600;
          color: #1D1D1F;

          &.comment-name--link {
            color: #5C7A99;
          }
        }

        .comment-time {
          font-size: 22rpx;
          color: #8E8E93;
        }
      }

      .comment-text {
        font-size: 28rpx;
        color: #4A4A4A;
        line-height: 1.6;
      }

      .comment-actions {
        display: flex;
        margin-top: 12rpx;

        .action-item {
          display: flex;
          align-items: center;
          margin-right: 32rpx;

          .action-icon {
            font-size: 28rpx;
            margin-right: 4rpx;
          }

          .action-count {
            font-size: 22rpx;
            color: #8E8E93;
          }
        }
      }

      .sub-comments {
        margin-top: 16rpx;
        padding: 16rpx;
        background-color: #F7F7F9;
        border-radius: 12rpx;

        .sub-comment-item {
          margin-bottom: 8rpx;

          &:last-child {
            margin-bottom: 0;
          }

          .sub-comment-user {
            font-size: 24rpx;
            font-weight: 600;
            color: #5C7A99;
          }

          .sub-comment-text {
            font-size: 24rpx;
            color: #4A4A4A;
          }
        }
      }
    }
  }

  .empty-comment {
    text-align: center;
    padding: 60rpx 0;
    color: #8E8E93;
    font-size: 28rpx;
  }
}

.bottom-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  padding: 16rpx 24rpx;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
  background-color: #FFFFFF;
  border-top: 1rpx solid #F0F0F0;

  .comment-input {
    flex: 1;
    height: 72rpx;
    background-color: #F5F5F7;
    border-radius: 36rpx;
    padding: 0 28rpx;
    display: flex;
    align-items: center;
    margin-right: 20rpx;

    .input-placeholder {
      font-size: 28rpx;
      color: #8E8E93;
    }
  }

  .action-btns {
    display: flex;

    .action-btn {
      display: flex;
      flex-direction: column;
      align-items: center;
      margin-left: 32rpx;

      .btn-icon {
        font-size: 36rpx;
      }

      .btn-text {
        font-size: 20rpx;
        color: #8E8E93;
        margin-top: 4rpx;
      }
    }
  }
}

.comment-popup {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: flex-end;
  z-index: 9999;

  .popup-content {
    width: 100%;
    background-color: #FFFFFF;
    border-radius: 24rpx 24rpx 0 0;
    padding: 24rpx;
    padding-bottom: calc(24rpx + env(safe-area-inset-bottom));

    .comment-textarea {
      width: 100%;
      height: 200rpx;
      font-size: 28rpx;
      color: #1D1D1F;
      line-height: 1.6;
    }

    .popup-actions {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-top: 16rpx;

      .char-count {
        font-size: 24rpx;
        color: #8E8E93;
      }

      .submit-btn {
        padding: 16rpx 48rpx;
        background-color: #5C7A99;
        border-radius: 32rpx;

        text {
          font-size: 28rpx;
          color: #FFFFFF;
        }
      }
    }
  }
}
</style>
