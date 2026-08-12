<template>
  <view class="detail-container">
    <nav-bar title="帖子详情" :showBack="true" fixed placeholder />
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
        <view class="follow-btn" v-if="!isAuthorSelf && !postDetail.isFollow" @click.stop="toggleFollow">
          <text>+ 关注</text>
        </view>
        <view class="follow-btn followed" v-else-if="!isAuthorSelf" @click.stop="toggleFollow">
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
        <view class="stat-action" @click="toggleLike">
          <image
            class="stat-icon-img"
            :src="postDetail.isLiked ? '/static/icons/line/thumb-up-filled.svg' : '/static/icons/line/thumb-up.svg'"
            mode="aspectFit"
          />
          <text class="stat-num">{{ postDetail.likeCount || 0 }}</text>
          <text class="stat-label">点赞</text>
        </view>
        <view class="stat-action">
          <text class="stat-icon">💬</text>
          <text class="stat-num">{{ postDetail.commentCount || 0 }}</text>
          <text class="stat-label">评论</text>
        </view>
        <view class="stat-action">
          <text class="stat-icon">👁️</text>
          <text class="stat-num">{{ postDetail.viewCount || 0 }}</text>
          <text class="stat-label">浏览</text>
        </view>
      </view>
    </view>

    <!-- 评论列表 -->
    <view class="comment-section">
      <view class="section-title">
        <text>全部评论 ({{ commentList.length }})</text>
        <view class="comment-sort">
          <view
            v-for="s in commentSortOptions"
            :key="s.value"
            class="sort-item"
            :class="{ active: commentSort === s.value }"
            @click="commentSort = s.value"
          >
            <text>{{ s.label }}</text>
          </view>
        </view>
      </view>

      <view class="comment-list">
        <view 
          v-for="(item, index) in sortedComments" 
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
            <view class="comment-images" v-if="item.images && item.images.length">
              <image
                v-for="(img, imgIndex) in item.images"
                :key="imgIndex"
                class="comment-image"
                :src="img"
                mode="aspectFill"
                @click="previewCommentImage(item.images, imgIndex)"
              />
            </view>
            <view class="comment-actions">
              <view class="action-item" @click="toggleCommentLike(item)">
                <image
                  class="action-icon-img"
                  :src="item.isLiked ? '/static/icons/line/thumb-up-filled.svg' : '/static/icons/line/thumb-up.svg'"
                  mode="aspectFit"
                />
                <text class="action-count">{{ item.likeCount || 0 }}</text>
              </view>
              <view class="action-item" @click="replyComment(item)">
                <text class="action-icon">💬</text>
                <text class="action-count">回复</text>
              </view>
              <view class="action-item" @click="reportComment(item)">
                <text class="action-icon">⚠️</text>
                <text class="action-count">举报</text>
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
      <view class="comment-input-wrap">
        <view class="input-image-btn" @click="chooseCommentImage">
          <image class="input-image-icon" src="/static/icons/proicons--photo.svg" mode="aspectFit" />
        </view>
        <input
          class="comment-input"
          v-model="commentContent"
          :placeholder="replyTarget ? `回复 ${replyTarget.userName}...` : '说点什么吧...'"
          confirm-type="send"
          @confirm="submitComment"
        />
        <view class="send-btn" :class="{ disabled: !canSendComment }" @click="submitComment">
          <text>发送</text>
        </view>
        <view class="more-btn" @click="openPostMenu">
          <image class="more-icon" src="/static/icons/line/more.svg" mode="aspectFit" />
        </view>
      </view>
      <!-- 待发送的评论图片预览 -->
      <view class="comment-preview-list" v-if="commentImages.length">
        <view v-for="(img, index) in commentImages" :key="index" class="comment-preview-item">
          <image class="comment-preview-img" :src="img" mode="aspectFill" />
          <view class="preview-remove" @click="removeCommentImage(index)">
            <text>×</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import {
  createReport,
  createComment,
  deletePost,
  getCommentList,
  getFollowStatus,
  getPostDetail,
  parseImageList,
  toggleCommentLike as toggleForumCommentLike,
  toggleFollowUser,
  togglePostLike
} from '@/api/forum.js'
import { getCurrentUserId } from '@/utils/storage.js'
import { uploadImages } from '@/utils/upload.js'

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
      commentSort: 'time',
      commentSortOptions: [
        { value: 'time', label: '最新' },
        { value: 'like', label: '点赞' },
        { value: 'reply', label: '回复' }
      ],
      commentContent: '',
      commentImages: [],
      replyTarget: null,
      currentUserId: ''
    }
  },
  computed: {
    isAuthorSelf() {
      return !!this.currentUserId && String(this.currentUserId) === String(this.postDetail.userId || '')
    },
    canSendComment() {
      return !!(this.commentContent && this.commentContent.trim()) || this.commentImages.length > 0
    },
    sortedComments() {
      const list = [...this.commentList]
      if (this.commentSort === 'like') {
        return list.sort((a, b) => (b.likeCount || 0) - (a.likeCount || 0))
      }
      if (this.commentSort === 'reply') {
        return list.sort((a, b) => (b.replies?.length || 0) - (a.replies?.length || 0))
      }
      return list.sort((a, b) => String(b.createTime || '').localeCompare(String(a.createTime || '')))
    }
  },
  onLoad(options) {
    this.currentUserId = getCurrentUserId()
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
      const name = user.userName || user.username || ''
      const avatar = user.avatar || ''
      uni.navigateTo({
        url: `/subpackage_forum/userProfile/userProfile?id=${encodeURIComponent(uid)}&name=${encodeURIComponent(name)}&avatar=${encodeURIComponent(avatar)}`
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
        images: parseImageList(item.images),
        likeCount: item.likeCount || 0,
        isLiked: !!item.isLiked,
        createTime: this.formatDateTime(item.createTime),
        replies: (item.children || []).map((child) => ({
          id: child.id,
          userId: child.userId,
          userName: child.username || '匿名用户',
          content: child.content || '',
          images: parseImageList(child.images),
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
        const res = await toggleFollowUser(this.postDetail.userId)
        const nextFollowing = typeof res?.data?.following === 'boolean'
          ? res.data.following
          : !this.postDetail.isFollow
        this.postDetail.isFollow = nextFollowing
        uni.showToast({
          title: nextFollowing ? '关注成功' : '已取消关注',
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
    chooseReportReason() {
      return new Promise((resolve) => {
        uni.showActionSheet({
          itemList: ['垃圾广告', '虚假信息', '人身攻击', '低俗违规', '其他'],
          success: (res) => {
            const reasons = [
              { reasonType: 1, reasonText: '垃圾广告' },
              { reasonType: 2, reasonText: '虚假信息' },
              { reasonType: 3, reasonText: '人身攻击' },
              { reasonType: 4, reasonText: '低俗违规' },
              { reasonType: 5, reasonText: '其他' }
            ]
            resolve(reasons[res.tapIndex])
          },
          fail: () => resolve(null)
        })
      })
    },
    async submitReport(targetType, targetId) {
      const reason = await this.chooseReportReason()
      if (!reason) return
      try {
        await createReport({
          targetType,
          targetId,
          reasonType: reason.reasonType,
          reasonText: reason.reasonText
        })
        uni.showToast({ title: '举报已提交', icon: 'success' })
      } catch (error) {
        uni.showToast({ title: error?.message || '举报提交失败', icon: 'none' })
      }
    },
    reportComment(item) {
      this.submitReport(2, item.id)
    },
    openPostMenu() {
      if (this.isAuthorSelf) {
        // 帖主：显示删除操作
        uni.showActionSheet({
          itemList: ['删除帖子'],
          itemColor: '#FF3B30',
          success: (res) => {
            if (res.tapIndex === 0) {
              this.confirmDeletePost()
            }
          }
        })
      } else {
        // 非帖主：显示举报操作
        uni.showActionSheet({
          itemList: ['举报帖子'],
          itemColor: '#FF3B30',
          success: (res) => {
            if (res.tapIndex === 0) {
              uni.navigateTo({
                url: `/subpackage_forum/reportPost/reportPost?postId=${this.postId}`
              })
            }
          }
        })
      }
    },
    confirmDeletePost() {
      uni.showModal({
        title: '删除帖子',
        content: '确定要删除这篇帖子吗？删除后不可恢复。',
        confirmText: '删除',
        confirmColor: '#FF3B30',
        success: async (res) => {
          if (!res.confirm) return
          try {
            await deletePost(this.postId)
            uni.showToast({ title: '删除成功', icon: 'success' })
            setTimeout(() => {
              uni.navigateBack()
            }, 600)
          } catch (error) {
            uni.showToast({ title: error?.message || '删除失败', icon: 'none' })
          }
        }
      })
    },
    hideCommentInput() {
      this.commentContent = ''
      this.commentImages = []
      this.replyTarget = null
    },
    replyComment(item) {
      this.replyTarget = item
    },
    chooseCommentImage() {
      const remaining = 9 - this.commentImages.length
      if (remaining <= 0) {
        uni.showToast({ title: '最多上传9张图片', icon: 'none' })
        return
      }
      uni.chooseImage({
        count: remaining,
        sizeType: ['compressed'],
        sourceType: ['album', 'camera'],
        success: async (res) => {
          const files = res.tempFilePaths
          try {
            const urls = await uploadImages(files)
            this.commentImages = [...this.commentImages, ...urls]
          } catch (error) {
            uni.showToast({ title: error?.msg || '图片上传失败', icon: 'none' })
          }
        }
      })
    },
    removeCommentImage(index) {
      this.commentImages.splice(index, 1)
    },
    previewCommentImage(images, index) {
      uni.previewImage({
        urls: images,
        current: index
      })
    },
    async toggleCommentLike(item) {
      try {
        const res = await toggleForumCommentLike(item.id)
        item.isLiked = !!res?.data?.liked
        item.likeCount = Number(res?.data?.likeCount ?? item.likeCount)
      } catch (error) {
        uni.showToast({ title: error?.message || '操作失败', icon: 'none' })
      }
    },
    async submitComment() {
      if (!this.canSendComment) {
        uni.showToast({ title: '请输入评论内容', icon: 'none' })
        return
      }
      try {
        await createComment({
          postId: this.postId,
          content: (this.commentContent || '').trim(),
          parentId: this.replyTarget ? this.replyTarget.id : null,
          replyToId: this.replyTarget ? this.replyTarget.userId : null,
          images: this.commentImages
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
    display: flex;
    align-items: center;
    margin-top: 24rpx;
    padding-top: 20rpx;
    border-top: 1rpx solid #F0F0F0;

    .stat-action {
      display: flex;
      align-items: center;
      margin-right: 48rpx;
      padding: 8rpx 0;

      .stat-icon {
        font-size: 30rpx;
        margin-right: 8rpx;
      }

      .stat-icon-img {
        width: 34rpx;
        height: 34rpx;
        margin-right: 8rpx;
      }

      .stat-num {
        font-size: 26rpx;
        font-weight: 600;
        color: #4A4A4A;
        margin-right: 6rpx;
      }

      .stat-label {
        font-size: 24rpx;
        color: #8E8E93;
      }
    }
  }
}

.comment-section {
  background-color: #FFFFFF;
  padding: 24rpx;

  .section-title {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: 30rpx;
    font-weight: 600;
    color: #1D1D1F;
    margin-bottom: 24rpx;

    .comment-sort {
      display: flex;
      align-items: center;

      .sort-item {
        padding: 8rpx 20rpx;
        margin-left: 16rpx;
        font-size: 24rpx;
        font-weight: 400;
        color: #8E8E93;
        background-color: #F5F5F7;
        border-radius: 24rpx;

        &.active {
          color: #FFFFFF;
          background-color: #5C7A99;
        }
      }
    }
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

      .comment-images {
        display: flex;
        flex-wrap: wrap;
        gap: 12rpx;
        margin-top: 12rpx;

        .comment-image {
          width: 160rpx;
          height: 120rpx;
          border-radius: 12rpx;
          background-color: #F5F5F7;
        }
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

          .action-icon-img {
            width: 30rpx;
            height: 30rpx;
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
  padding: 16rpx 24rpx;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
  background-color: #FFFFFF;
  border-top: 1rpx solid #F0F0F0;

  .comment-input-wrap {
    display: flex;
    align-items: center;

    .input-image-btn {
      flex-shrink: 0;
      width: 72rpx;
      height: 72rpx;
      display: flex;
      align-items: center;
      justify-content: center;
      margin-right: 12rpx;
    }

    .input-image-icon {
      width: 44rpx;
      height: 44rpx;
      color: #5C7A99;
    }

    .comment-input {
      flex: 1;
      min-width: 0;
      height: 72rpx;
      background-color: #F5F5F7;
      border-radius: 36rpx;
      padding: 0 28rpx;
      font-size: 28rpx;
      color: #1D1D1F;
    }

    .send-btn {
      flex-shrink: 0;
      margin-left: 16rpx;
      padding: 0 28rpx;
      height: 72rpx;
      border-radius: 36rpx;
      background-color: #5C7A99;
      display: flex;
      align-items: center;
      justify-content: center;

      text {
        font-size: 26rpx;
        color: #FFFFFF;
        font-weight: 600;
      }

      &.disabled {
        opacity: 0.5;
      }
    }

    .more-btn {
      flex-shrink: 0;
      width: 72rpx;
      height: 72rpx;
      display: flex;
      align-items: center;
      justify-content: center;
      margin-left: 8rpx;
    }

    .more-icon {
      width: 44rpx;
      height: 44rpx;
      color: #48484A;
    }
  }

  .comment-preview-list {
    display: flex;
    flex-wrap: wrap;
    gap: 12rpx;
    margin-top: 16rpx;

    .comment-preview-item {
      position: relative;
      width: 120rpx;
      height: 120rpx;

      .comment-preview-img {
        width: 100%;
        height: 100%;
        border-radius: 12rpx;
      }

      .preview-remove {
        position: absolute;
        top: -10rpx;
        right: -10rpx;
        width: 36rpx;
        height: 36rpx;
        background-color: rgba(0, 0, 0, 0.6);
        border-radius: 50%;
        display: flex;
        align-items: center;
        justify-content: center;

        text {
          color: #FFFFFF;
          font-size: 24rpx;
        }
      }
    }
  }
}
</style>
