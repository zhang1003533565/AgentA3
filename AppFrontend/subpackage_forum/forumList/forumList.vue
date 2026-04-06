<template>
  <view class="forum-container">
    <view class="page-fixed-header">
      <nav-bar title="校园论坛" :showBack="true" />
      <!-- 顶部搜索栏 + 个人主页入口 -->
      <view class="search-bar">
        <view class="search-input">
          <text class="search-icon">🔍</text>
          <input 
            type="text" 
            v-model="searchKeyword" 
            placeholder="搜索帖子、话题..."
            @confirm="handleSearch"
          />
        </view>
        <view class="header-avatar-wrap" @click="goToUserProfile">
          <image class="header-avatar" :src="currentUserAvatar || '/static/logo.png'" mode="aspectFill" />
        </view>
      </view>

      <!-- 分类 Tab：与「校园活动」一致的样式 -->
      <view class="nav-secondary-wrap">
        <view class="category-container">
          <scroll-view class="category-scroll" scroll-x :show-scrollbar="false">
            <view class="category-list">
              <view
                v-for="(item, index) in topics"
                :key="index"
                class="category-item"
                :class="{ active: currentTopic === item.id }"
                @click="selectTopic(item.id)"
              >
                <text class="category-text">{{ item.name }}</text>
                <view class="active-line" v-if="currentTopic === item.id"></view>
              </view>
            </view>
          </scroll-view>
        </view>
      </view>
    </view>

    <!-- 帖子列表 -->
    <scroll-view 
      class="post-list" 
      scroll-y 
      @scrolltolower="loadMore"
      refresher-enabled
      :refresher-triggered="isRefreshing"
      @refresherrefresh="onRefresh"
    >
      <!-- 顶部占位，避免被固定导航遮挡 -->
      <view class="nav-placeholder"></view>
      
      <!-- 全部：按时间排序显示所有帖子 -->
      <view v-if="currentTopic === 0" class="all-posts">
        <view 
          v-for="(item, index) in sortedPosts" 
          :key="index"
          class="post-item"
          @click="goToDetail(item.id)"
        >
          <!-- 用户信息 -->
          <view class="post-header">
            <image class="user-avatar" :src="item.avatar || '/static/logo.png'" mode="aspectFill" @click.stop="goToUserProfile(item)" />
            <view class="user-info">
              <text class="user-name">{{ item.userName }}</text>
              <text class="post-time">{{ item.createTime }}</text>
            </view>
          </view>

          <!-- 帖子内容 -->
          <view class="post-content">
            <text class="post-title" v-if="item.title">{{ item.title }}</text>
            <text class="post-text">{{ item.content }}</text>
            <view class="post-images" v-if="item.images && item.images.length">
              <image 
                v-for="(img, imgIndex) in item.images.slice(0, 3)" 
                :key="imgIndex"
                class="post-image"
                :src="img"
                mode="aspectFill"
              />
            </view>
          </view>

          <!-- 互动数据 -->
          <view class="post-footer">
            <view class="action-item" @click.stop="toggleLike(item)">
              <text class="action-icon">{{ item.isLiked ? '❤️' : '🤍' }}</text>
              <text class="action-count">{{ item.likeCount || 0 }}</text>
            </view>
            <view class="action-item">
              <text class="action-icon">💬</text>
              <text class="action-count">{{ item.commentCount || 0 }}</text>
            </view>
            <view class="action-item view-count">
              <text class="action-icon">👁️</text>
              <text class="action-count">{{ item.viewCount || 0 }}</text>
            </view>
          </view>
        </view>
      </view>
      
      <!-- 选中具体分类时，显示该分类的所有帖子（也按时间排序） -->
      <view v-else>
        <view 
          v-for="(item, index) in sortedPosts" 
          :key="index"
          class="post-item"
          @click="goToDetail(item.id)"
        >
          <!-- 用户信息 -->
          <view class="post-header">
            <image class="user-avatar" :src="item.avatar || '/static/logo.png'" mode="aspectFill" @click.stop="goToUserProfile(item)" />
            <view class="user-info">
              <text class="user-name">{{ item.userName }}</text>
              <text class="post-time">{{ item.createTime }}</text>
            </view>
          </view>

          <!-- 帖子内容 -->
          <view class="post-content">
            <text class="post-title" v-if="item.title">{{ item.title }}</text>
            <text class="post-text">{{ item.content }}</text>
            <view class="post-images" v-if="item.images && item.images.length">
              <image 
                v-for="(img, imgIndex) in item.images.slice(0, 3)" 
                :key="imgIndex"
                class="post-image"
                :src="img"
                mode="aspectFill"
              />
            </view>
          </view>

          <!-- 互动数据 -->
          <view class="post-footer">
            <view class="action-item" @click.stop="toggleLike(item)">
              <text class="action-icon">{{ item.isLiked ? '❤️' : '🤍' }}</text>
              <text class="action-count">{{ item.likeCount || 0 }}</text>
            </view>
            <view class="action-item">
              <text class="action-icon">💬</text>
              <text class="action-count">{{ item.commentCount || 0 }}</text>
            </view>
            <view class="action-item view-count">
              <text class="action-icon">👁️</text>
              <text class="action-count">{{ item.viewCount || 0 }}</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 加载更多 -->
      <view class="load-more">
        <text v-if="loading">加载中...</text>
        <text v-else-if="noMore">没有更多了</text>
        <text v-else-if="postList.length === 0 && currentTopic !== 0">暂无帖子，快来发布第一篇吧~</text>
      </view>
          <!-- 底部留白 -->
      <view class="post-list-bottom-pad" />
    </scroll-view>

    <!-- 底部悬浮发帖按钮 -->
    <view class="fab-publish-btn" v-if="!showPublishModal" @click="openPublishModal">
      <text class="fab-publish-icon">+</text>
    </view>

    <!-- 发帖弹窗 -->
    <view class="publish-modal-mask" v-if="showPublishModal" @click="closePublishModal" @touchmove.stop.prevent>
      <view 
        class="publish-modal-content" 
        :class="{ 'publish-modal-show': publishModalAnimating }"
        @click.stop
      >
        <!-- 这里嵌入发帖页面的内容 -->
        <view class="modal-header">
          <view class="modal-close" @click="closePublishModal">
            <view class="close-chevron" />
          </view>
          <text class="modal-title">发帖</text>
          <view class="modal-placeholder"></view>
        </view>
        
        <scroll-view class="modal-body" scroll-y>
          <post-editor :form="publishForm" :topics="publishTopics" />
        </scroll-view>

        <!-- 底部操作栏 -->
        <view class="modal-footer">
          <view class="draft-btn" @click="saveDraft">
            <text>存草稿</text>
          </view>
          <view class="publish-btn" :class="{ disabled: !canPublish }" @click="publishPost">
            <text>发布</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import PostEditor from '@/subpackage_forum/components/post-editor/post-editor.vue'
import {
  getPostList,
  getHotTopics,
  getTopicList,
  publishPost,
  parseImageList,
  togglePostLike
} from '@/api/forum.js'
import { getUserInfo } from '@/utils/storage.js'

export default {
  components: { NavBar, PostEditor },
  data() {
    return {
      currentUserAvatar: '',
      currentTopic: 0,
      topics: [
        { id: 0, name: '全部' },
        { id: 1, name: '热门' },
        { id: 2, name: '最新' },
        { id: 3, name: '📢公告' },
        { id: 4, name: '💰集市' },
        { id: 5, name: '😊求助' },
        { id: 6, name: '🔑失物' },
        { id: 7, name: '💕表白' },
        { id: 8, name: '🍟美食' },
        { id: 9, name: '🤝搭子' },
        { id: 10, name: '📚学习资料' },
        { id: 11, name: '🌸影忆青春' }
      ],
      postList: [],
      page: 1,
      pageSize: 10,
      loading: false,
      noMore: false,
      isRefreshing: false,
      // 发帖弹窗相关
      showPublishModal: false,
      publishModalAnimating: false,
      publishForm: {
        title: '',
        content: '',
        images: [],
        topicId: null,
        isAnonymous: false
      },
      publishTopics: [],
      hotTopics: [],
      showHotTopics: true
    }
  },
  computed: {
    // 按时间排序的帖子（时间晚的在上面）
    sortedPosts() {
      return [...this.postList].sort((a, b) => {
        return new Date(b.createTime) - new Date(a.createTime)
      })
    },
    canPublish() {
      return this.publishForm.content.trim().length >= 10
    }
  },
  onBackPress() {
    if (!this.showPublishModal) return false
    this.closePublishModal()
    return true
  },
  async onLoad() {
    this.loadCurrentUser()
    await this.loadTopics()
    await this.loadHotTopicList()
    this.loadPostList()
  },
  methods: {
    loadCurrentUser() {
      const userInfo = getUserInfo()
      const seed = userInfo?.username || 'forum-user'
      this.currentUserAvatar = `https://api.dicebear.com/7.x/avataaars/svg?seed=${encodeURIComponent(seed)}`
    },
    async loadTopics() {
      // 使用本地定义的静态分类，不从API获取
      this.publishTopics = [
        { id: 1, name: '热门' },
        { id: 2, name: '最新' },
        { id: 3, name: '📢公告' },
        { id: 4, name: '💰集市' },
        { id: 5, name: '😊求助' },
        { id: 6, name: '🔑失物' },
        { id: 7, name: '💕表白' },
        { id: 8, name: '🍟美食' },
        { id: 9, name: '🤝搭子' },
        { id: 10, name: '📚学习资料' },
        { id: 11, name: '🌸影忆青春' }
      ]
    },
    async loadHotTopicList() {
      try {
        const res = await getHotTopics({ limit: 8 })
        this.hotTopics = (res?.data || []).map((item) => ({
          id: item.id,
          name: item.topicName || '未命名话题',
          heat: item.postCount || 0
        }))
      } catch (error) {
        this.hotTopics = []
      }
    },
    async loadPostList() {
      if (this.loading || this.noMore) return
      this.loading = true
      try {
        const params = {
          pageNum: this.page,
          pageSize: this.pageSize
        }
        if (this.currentTopic !== 0) params.topicId = this.currentTopic
        if (this.searchKeyword && this.searchKeyword.trim()) params.keyword = this.searchKeyword.trim()
        const res = await getPostList(params)
        const data = res?.data || {}
        const posts = data.records || []
        const formattedPosts = posts.map(this.formatPostItem)
        this.postList = this.page === 1 ? formattedPosts : [...this.postList, ...formattedPosts]
        const total = Number(data.total || 0)
        this.noMore = this.postList.length >= total || formattedPosts.length < this.pageSize
      } catch (error) {
        if (this.page === 1) this.postList = []
      } finally {
        this.loading = false
        this.isRefreshing = false
      }
    },
    formatPostItem(post) {
      return {
        id: post.id,
        userId: post.userId,
        userName: post.username || '匿名用户',
        avatar: post.avatar || '/static/logo.png',
        title: post.title || '',
        content: post.content || '',
        images: parseImageList(post.images),
        topicName: post.topicName || '',
        topicId: post.topicId || 0,
        likeCount: post.likeCount || 0,
        commentCount: post.commentCount || 0,
        viewCount: post.viewCount || 0,
        isLiked: !!post.isLiked,
        createTime: this.formatDateTime(post.createTime)
      }
    },
    formatDateTime(value) {
      if (!value) return '刚刚'
      return String(value).replace('T', ' ').slice(0, 16)
    },
    handleSearch() {
      this.page = 1
      this.noMore = false
      this.loadPostList()
    },
    selectTopic(topicId) {
      this.currentTopic = topicId
      this.page = 1
      this.noMore = false
      this.loadPostList()
    },
    loadMore() {
      if (!this.loading && !this.noMore) {
        this.page++
        this.loadPostList()
      }
    },
    onRefresh() {
      this.isRefreshing = true
      this.page = 1
      this.noMore = false
      this.loadHotTopicList()
      this.loadPostList()
    },
    goToDetail(id) {
      uni.navigateTo({
        url: `/subpackage_forum/postDetail/postDetail?id=${id}`
      })
    },
    goToUserProfile(item) {
      const userId = item?.userId || item?.id || ''
      const userName = item?.userName || ''
      uni.navigateTo({
        url: `/subpackage_forum/userProfile/userProfile?id=${encodeURIComponent(userId)}&name=${encodeURIComponent(userName)}`
      })
    },
    async toggleLike(item) {
      try {
        const res = await togglePostLike(item.id)
        item.isLiked = !!res?.data?.liked
        item.likeCount = Number(res?.data?.likeCount ?? item.likeCount)
      } catch (error) {}
    },
    openPublishModal() {
      if (this.showPublishModal) return
      this.showPublishModal = true
      this.$nextTick(() => {
        setTimeout(() => {
          if (this.showPublishModal) this.publishModalAnimating = true
        }, 0)
      })
    },
    closePublishModal() {
      if (!this.showPublishModal) return
      this.publishModalAnimating = false
      setTimeout(() => {
        this.showPublishModal = false
      }, 250)
    },
    saveDraft() {
      if (!this.publishForm.content.trim()) {
        uni.showToast({ title: '请输入内容', icon: 'none' })
        return
      }
      uni.showToast({ title: '已保存草稿', icon: 'success' })
    },
    async publishPost() {
      if (!this.canPublish) {
        uni.showToast({ title: '内容至少10个字', icon: 'none' })
        return
      }

      uni.showLoading({ title: '发布中...' })
      
      try {
        const postData = {
          title: this.publishForm.title,
          content: this.publishForm.content,
          images: this.publishForm.images,
          topicId: this.publishForm.topicId
        }
        await publishPost(postData)
        uni.hideLoading()
        uni.showToast({ title: '发布成功', icon: 'success' })
        this.publishForm = {
          title: '',
          content: '',
          images: [],
          topicId: null,
          isAnonymous: false
        }
        setTimeout(() => {
          this.closePublishModal()
          this.page = 1
          this.noMore = false
          this.loadHotTopicList()
          this.loadPostList()
        }, 300)
      } catch (error) {
        uni.hideLoading()
      }
    },
    toggleHotTopics() {
      this.showHotTopics = !this.showHotTopics
    },
    formatHeat(heat) {
      if (heat >= 10000) {
        return (heat / 10000).toFixed(1) + 'w'
      } else if (heat >= 1000) {
        return (heat / 1000).toFixed(1) + 'k'
      }
      return heat.toString()
    },
    goToTopicDetail(topicId) {
      uni.navigateTo({
        url: `/subpackage_forum/topicDetail/topicDetail?topicId=${topicId}`
      })
    }
  }
}
</script>

<style lang="scss">
.forum-container {
  min-height: 100vh;
  background-color: #F7F7F9;
  padding-bottom: 0;
}

.search-bar {
  display: flex;
  align-items: center;
  padding: 20rpx 30rpx;
  background-color: #FFFFFF;
  
  .search-input {
    flex: 1;
    display: flex;
    align-items: center;
    height: 72rpx;
    background-color: #F5F5F7;
    border-radius: 36rpx;
    padding: 0 30rpx;
    margin-right: 24rpx;
    
    .search-icon {
      font-size: 28rpx;
      margin-right: 16rpx;
    }
    
    input {
      flex: 1;
      font-size: 28rpx;
      color: #333;
    }
  }
  
  .header-avatar-wrap {
    flex-shrink: 0;
    width: 70rpx;
    height: 70rpx;
    border-radius: 50%;
    border: 1rpx solid #E5E7EB;
    overflow: hidden;
    box-sizing: border-box;
  }
  
  .header-avatar {
    width: 100%;
    height: 100%;
    display: block;
  }
}

.page-fixed-header {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 999;
}

/* 分类 Tab：对齐「校园活动」的文字 + 下划线风格 */
.nav-secondary-wrap {
  display: flex;
  align-items: center;
  background-color: #FFFFFF;
  padding: 0 0 0 30rpx;
  margin-bottom: 20rpx;
  border-bottom: 1px solid #F2F2F2;
}
.category-container {
  flex: 1;
  min-width: 0;
}
.category-scroll {
  white-space: nowrap;
  scrollbar-width: none;
  -ms-overflow-style: none;
  &::-webkit-scrollbar {
    display: none;
  }
  /* 隐藏滚动指示器 */
  ::-webkit-scrollbar {
    display: none;
    width: 0;
    height: 0;
    background: transparent;
  }
}
.category-list {
  display: flex;
  align-items: center;
  min-height: 80rpx;
  gap: 40rpx;
}
.category-item {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 20rpx 0;
  flex-shrink: 0;
}
.category-text {
  font-size: 26rpx;
  font-weight: 400;
  color: #8E8E93;
  transition: all 0.2s ease;
}
.category-item.active .category-text {
  font-weight: 600;
  color: #1D1D1F;
}
.active-line {
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 32rpx;
  height: 4rpx;
  background-color: #5C7A99;
  border-radius: 2rpx;
}

.post-list {
  height: 100vh;
  padding: 0 20rpx;
  padding-top: 20rpx;
  
  .nav-placeholder {
    height: 280rpx;
    flex-shrink: 0;
  }
  
  /* 分组样式 - 已废弃 */
  
  .post-item {
    background-color: #FFFFFF;
    border-radius: 16rpx;
    padding: 24rpx;
    margin-bottom: 28rpx;
  }
  
  .post-list-bottom-pad {
    height: 100rpx;
    flex-shrink: 0;
  }
  
  .post-header {
    display: flex;
    align-items: center;
    margin-bottom: 20rpx;
    
    .user-avatar {
      width: 72rpx;
      height: 72rpx;
      border-radius: 50%;
      margin-right: 16rpx;
    }
    
    .user-info {
      flex: 1;
      display: flex;
      flex-direction: column;
      
      .user-name {
        font-size: 28rpx;
        font-weight: 600;
        color: #1D1D1F;
      }
      
      .post-time {
        font-size: 22rpx;
        color: #8E8E93;
        margin-top: 4rpx;
      }
    }
    
    .topic-tag {
      font-size: 22rpx;
      color: #5C7A99;
      background-color: rgba(92, 122, 153, 0.1);
      padding: 8rpx 16rpx;
      border-radius: 20rpx;
    }
  }
  
  .post-content {
    .post-title {
      display: block;
      font-size: 32rpx;
      font-weight: 600;
      color: #1D1D1F;
      margin-bottom: 12rpx;
      line-height: 1.4;
    }
    
    .post-text {
      font-size: 28rpx;
      color: #4A4A4A;
      line-height: 1.6;
      display: -webkit-box;
      -webkit-box-orient: vertical;
      -webkit-line-clamp: 4;
      overflow: hidden;
    }
    
    .post-images {
      display: flex;
      gap: 12rpx;
      margin-top: 16rpx;
      
      .post-image {
        width: 200rpx;
        height: 150rpx;
        border-radius: 12rpx;
        background-color: #F5F5F7;
      }
    }
  }
  
  .post-footer {
    display: flex;
    align-items: center;
    margin-top: 20rpx;
    padding-top: 20rpx;
    border-top: 1rpx solid #F0F0F0;
    
    .action-item {
      display: flex;
      align-items: center;
      margin-right: 48rpx;
      
      .action-icon {
        font-size: 32rpx;
        margin-right: 8rpx;
      }
      
      .action-count {
        font-size: 24rpx;
        color: #8E8E93;
      }
    }
  }
  
  .load-more {
    text-align: center;
    padding: 30rpx;
    color: #999;
    font-size: 26rpx;
  }
}

/* 悬浮发帖按钮 */
.fab-publish-btn {
  position: fixed;
  right: 30rpx;
  bottom: calc(200rpx + constant(safe-area-inset-bottom));
  bottom: calc(200rpx + env(safe-area-inset-bottom));
  width: 100rpx;
  height: 100rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 6rpx 20rpx rgba(0, 122, 255, 0.3);
  z-index: 9999;
  background: linear-gradient(135deg, #007AFF, #00C6FF);
}

.fab-publish-icon {
  font-size: 56rpx;
  font-weight: 300;
  color: #FFFFFF;
  line-height: 1;
}

.publish-modal-mask {
  position: fixed;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.45);
  z-index: 999;
}

.publish-modal-content {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 86vh;
  background: #FFFFFF;
  border-top-left-radius: 24rpx;
  border-top-right-radius: 24rpx;
  transform: translateY(100%);
  transition: transform 250ms ease;
  display: flex;
  flex-direction: column;
}

.publish-modal-show {
  transform: translateY(0);
}

.modal-header {
  height: 96rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24rpx;
  border-bottom: 1rpx solid #F0F0F0;
  box-sizing: border-box;
  flex-shrink: 0;
}

.modal-close {
  width: 72rpx;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.close-chevron {
  width: 18rpx;
  height: 18rpx;
  border-right: 3rpx solid #111827;
  border-bottom: 3rpx solid #111827;
  transform: rotate(45deg);
  box-sizing: border-box;
}

.modal-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #1D1D1F;
}

.modal-placeholder {
  width: 72rpx;
  height: 72rpx;
}

.modal-body {
  flex: 1;
  padding: 24rpx;
  box-sizing: border-box;
}

.modal-footer {
  display: flex;
  gap: 16rpx;
  padding: 20rpx 24rpx;
  padding-bottom: calc(20rpx + constant(safe-area-inset-bottom));
  padding-bottom: calc(20rpx + env(safe-area-inset-bottom));
  background: #FFFFFF;
  border-top: 1rpx solid #F0F0F0;
  box-sizing: border-box;
  flex-shrink: 0;
}

.draft-btn,
.publish-btn {
  flex: 1;
  height: 80rpx;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
}

.draft-btn {
  background: #F3F4F6;
  color: #111827;
}

.publish-btn {
  background: #5C7A99;
  color: #FFFFFF;
}

.publish-btn.disabled {
  opacity: 0.5;
}

/* 热门话题模块 */
.hot-topics-section {
  background-color: #FFFFFF;
  margin: 20rpx;
  border-radius: 16rpx;
  padding: 24rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.06);
}

.hot-topics-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20rpx;
}

.hot-topics-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #1D1D1F;
}

.hot-topics-toggle {
  font-size: 26rpx;
  color: #8E8E93;
}

.hot-topics-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.hot-topic-item {
  display: flex;
  align-items: center;
  padding: 16rpx 0;
  border-bottom: 1rpx solid #F5F5F7;
}

.hot-topic-item:last-child {
  border-bottom: none;
}

.topic-rank {
  width: 48rpx;
  height: 48rpx;
  border-radius: 12rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26rpx;
  font-weight: 600;
  margin-right: 20rpx;
  flex-shrink: 0;
}

/* 热度排名颜色 */
.topic-rank.rank-1 {
  background: linear-gradient(135deg, #FFD700, #FFA500);
  color: #FFFFFF;
}

.topic-rank.rank-2 {
  background: linear-gradient(135deg, #C0C0C0, #A0A0A0);
  color: #FFFFFF;
}

.topic-rank.rank-3 {
  background: linear-gradient(135deg, #CD7F32, #B87333);
  color: #FFFFFF;
}

.topic-rank.rank-4,
.topic-rank.rank-5,
.topic-rank.rank-6,
.topic-rank.rank-7,
.topic-rank.rank-8 {
  background-color: #F0F0F0;
  color: #666666;
}

.topic-name {
  flex: 1;
  font-size: 28rpx;
  color: #333333;
  font-weight: 500;
}

.topic-heat {
  font-size: 24rpx;
  color: #FF6B6B;
  font-weight: 500;
}
</style>
