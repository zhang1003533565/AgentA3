<template>
  <view class="forum-container">
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
              <text class="category-text"># {{ item.name }}</text>
              <view class="active-line" v-if="currentTopic === item.id"></view>
            </view>
          </view>
        </scroll-view>
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
      <view 
        v-for="(item, index) in postList" 
        :key="index"
        class="post-item"
        @click="goToDetail(item.id)"
      >
        <!-- 用户信息 -->
        <view class="post-header">
          <image class="user-avatar" :src="item.avatar || '/static/logo.png'" mode="aspectFill" />
          <view class="user-info">
            <text class="user-name">{{ item.userName }}</text>
            <text class="post-time">{{ item.createTime }}</text>
          </view>
          <view class="topic-tag" v-if="item.topicName"># {{ item.topicName }}</view>
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
          <view class="action-item" @click.stop="sharePost(item)">
            <text class="action-icon">🔗</text>
            <text class="action-count">分享</text>
          </view>
        </view>
      </view>

      <!-- 加载更多 -->
      <view class="load-more">
        <text v-if="loading">加载中...</text>
        <text v-else-if="noMore">没有更多了</text>
        <text v-else-if="postList.length === 0">暂无帖子，快来发布第一篇吧~</text>
      </view>
      <!-- 底部留白，避免被悬浮发帖条遮挡 -->
      <view class="post-list-bottom-pad" />
    </scroll-view>

    <!-- 底部悬浮发帖条 -->
    <view class="bottom-publish-bar" @click="openPublishModal">
      <view class="bottom-publish-inner">
        <view class="bottom-publish-placeholder">
          <text class="placeholder-icon">✎</text>
          <text class="placeholder-text">说点什么，分享你的校园生活...</text>
        </view>
      </view>
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
export default {
  components: { NavBar, PostEditor },
  data() {
    return {
      searchKeyword: '',
      currentUserAvatar: '', // 当前用户头像，可后续从登录态获取
      currentTopic: 0,
      topics: [
        { id: 0, name: '推荐' },
        { id: 1, name: '校园生活' },
        { id: 2, name: '学习交流' },
        { id: 3, name: '求职招聘' },
        { id: 4, name: '二手交易' },
        { id: 5, name: '情感树洞' },
        { id: 6, name: '美食探店' }
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
      publishTopics: [
        { id: 1, name: '校园生活' },
        { id: 2, name: '学习交流' },
        { id: 3, name: '求职招聘' },
        { id: 4, name: '二手交易' },
        { id: 5, name: '情感树洞' },
        { id: 6, name: '美食探店' },
        { id: 7, name: '求助问答' },
        { id: 8, name: '失物招领' }
      ]
    }
  },
  computed: {
    canPublish() {
      return this.publishForm.content.trim().length >= 10
    }
  },
  onBackPress() {
    if (!this.showPublishModal) return false
    this.closePublishModal()
    return true
  },
  onLoad() {
    this.loadPostList()
  },
  methods: {
    // 加载帖子列表
    async loadPostList() {
      if (this.loading || this.noMore) return
      
      this.loading = true
      
      // 模拟数据，后续替换为真实接口
      setTimeout(() => {
        const mockData = [
          {
            id: 1,
            userName: '张三',
            avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=zhangsan',
            title: '分享一下我的考研经验',
            content: '今年成功上岸985，分享一下我的备考经验，希望对学弟学妹有帮助。英语一定要坚持背单词，政治可以晚点开始...',
            images: [],
            topicName: '学习交流',
            likeCount: 128,
            commentCount: 36,
            isLiked: false,
            createTime: '2小时前'
          },
          {
            id: 2,
            userName: '李四',
            avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=lisi',
            title: '',
            content: '今天食堂新出的糖醋排骨真的绝了！强烈推荐大家去二食堂三楼尝尝，阿姨手抖都给我盛了一大勺',
            images: ['https://picsum.photos/200/150?random=1'],
            topicName: '美食探店',
            likeCount: 256,
            commentCount: 89,
            isLiked: true,
            createTime: '3小时前'
          },
          {
            id: 3,
            userName: '王五',
            avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=wangwu',
            title: '出二手自行车，九成新',
            content: '毕业了出自行车，买来骑了不到半年，原价800现在400出，有意向的私聊~',
            images: ['https://picsum.photos/200/150?random=2', 'https://picsum.photos/200/150?random=3'],
            topicName: '二手交易',
            likeCount: 45,
            commentCount: 12,
            isLiked: false,
            createTime: '5小时前'
          },
          {
            id: 4,
            userName: '赵六',
            avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=zhaoliu',
            title: '有没有一起打羽毛球的小伙伴',
            content: '周末想打羽毛球，一个人太无聊了，有没有想一起的？可以约体育馆~',
            images: [],
            topicName: '校园生活',
            likeCount: 67,
            commentCount: 23,
            isLiked: false,
            createTime: '昨天'
          }
        ]
        
        if (this.page === 1) {
          this.postList = mockData
        } else {
          this.postList = [...this.postList, ...mockData]
        }
        
        if (this.page >= 3) {
          this.noMore = true
        }
        
        this.loading = false
        this.isRefreshing = false
      }, 500)
    },
    
    // 搜索
    handleSearch() {
      this.page = 1
      this.noMore = false
      this.loadPostList()
    },
    
    // 选择话题
    selectTopic(topicId) {
      this.currentTopic = topicId
      this.page = 1
      this.noMore = false
      this.loadPostList()
    },
    
    // 加载更多
    loadMore() {
      if (!this.loading && !this.noMore) {
        this.page++
        this.loadPostList()
      }
    },
    
    // 下拉刷新
    onRefresh() {
      this.isRefreshing = true
      this.page = 1
      this.noMore = false
      this.loadPostList()
    },
    
    // 跳转到详情
    goToDetail(id) {
      uni.navigateTo({
        url: `/subpackage_forum/postDetail/postDetail?id=${id}`
      })
    },

    // 跳转论坛个人主页（我的）
    goToUserProfile() {
      uni.navigateTo({
        url: '/subpackage_forum/userProfile/userProfile'
      })
    },
    
    // 点赞
    toggleLike(item) {
      item.isLiked = !item.isLiked
      item.likeCount += item.isLiked ? 1 : -1
      // TODO: 调用后端接口
    },
    
    // 分享
    sharePost(item) {
      uni.showActionSheet({
        itemList: ['复制链接', '分享到微信'],
        success: (res) => {
          if (res.tapIndex === 0) {
            uni.setClipboardData({
              data: `https://campus.edu.cn/forum/post/${item.id}`,
              success: () => {
                uni.showToast({ title: '链接已复制', icon: 'success' })
              }
            })
          }
        }
      })
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

    publishPost() {
      if (!this.canPublish) {
        uni.showToast({ title: '内容至少10个字', icon: 'none' })
        return
      }

      uni.showLoading({ title: '发布中...' })
      setTimeout(() => {
        uni.hideLoading()
        uni.showToast({ title: '发布成功', icon: 'success' })
        setTimeout(() => {
          this.closePublishModal()
        }, 300)
      }, 1000)
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
  &::-webkit-scrollbar {
    display: none;
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
  height: calc(100vh - 200rpx);
  padding: 20rpx;
  
  .post-item {
    background-color: #FFFFFF;
    border-radius: 16rpx;
    padding: 24rpx;
    margin-bottom: 28rpx;
  }
  
  .post-list-bottom-pad {
    height: 200rpx;
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

/* 底部悬浮发帖条 */
.bottom-publish-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  width: 100%;
  padding: 24rpx 32rpx;
  padding-bottom: calc(24rpx + constant(safe-area-inset-bottom));
  padding-bottom: calc(24rpx + env(safe-area-inset-bottom));
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(20rpx);
  -webkit-backdrop-filter: blur(20rpx);
  box-shadow: 0 -8rpx 20rpx rgba(0, 0, 0, 0.05);
  z-index: 100;
  box-sizing: border-box;
}

.bottom-publish-inner {
  display: flex;
  align-items: center;
  height: 80rpx;
  background-color: #F3F4F6;
  border-radius: 40rpx;
  padding: 0 28rpx;
}

.bottom-publish-placeholder {
  flex: 1;
  display: flex;
  align-items: center;
}

.placeholder-icon {
  font-size: 32rpx;
  color: #9CA3AF;
  margin-right: 16rpx;
}

.placeholder-text {
  font-size: 28rpx;
  color: #9CA3AF;
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
</style>
