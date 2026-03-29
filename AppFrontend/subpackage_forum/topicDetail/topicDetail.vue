<template>
  <view class="topic-detail-page">
    <!-- 返回按钮 -->
    <view class="back-button" @click="goBack">
      <text class="back-icon">←</text>
    </view>
    
    <!-- 顶部话题信息 -->
    <view class="topic-header">
      <view class="topic-info">
        <text class="topic-name"># {{ topicInfo.name }}</text>
        <view class="topic-stats">
          <text class="stat-item">{{ topicInfo.participants }}人参与</text>
          <text class="stat-divider">|</text>
          <text class="stat-item">{{ topicInfo.posts }}条动态</text>
        </view>
      </view>
      <view class="join-btn" @click="openPublishModal">
        <text>参与话题</text>
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
          <view class="action-item">
            <text class="action-icon">{{ item.isLiked ? '❤️' : '🤍' }}</text>
            <text class="action-count">{{ item.likeCount || 0 }}</text>
          </view>
          <view class="action-item">
            <text class="action-icon">💬</text>
            <text class="action-count">{{ item.commentCount || 0 }}</text>
          </view>
          <view class="action-item">
            <text class="action-icon">👁️</text>
            <text class="action-count">{{ item.viewCount || 0 }}</text>
          </view>
        </view>
      </view>

      <!-- 加载更多 -->
      <view class="load-more">
        <text v-if="loading">加载中...</text>
        <text v-else-if="noMore">没有更多了</text>
        <text v-else-if="postList.length === 0">暂无帖子</text>
      </view>
    </scroll-view>

    <!-- 发帖弹窗 -->
    <view class="publish-modal-mask" v-if="showPublishModal" @click="closePublishModal" @touchmove.stop.prevent>
      <view 
        class="publish-modal-content" 
        :class="{ 'publish-modal-show': publishModalAnimating }"
        @click.stop
      >
        <view class="modal-header">
          <view class="modal-close" @click="closePublishModal">
            <view class="close-chevron" />
          </view>
          <text class="modal-title">发帖</text>
          <view class="modal-placeholder"></view>
        </view>
        
        <scroll-view class="modal-body" scroll-y>
          <post-editor :form="publishForm" :topics="[{ id: topicId, name: topicInfo.name }]" />
        </scroll-view>

        <view class="modal-footer">
          <view class="draft-btn" @click="saveDraft">
            <text>存草稿</text>
          </view>
          <view class="publish-btn" @click="publishPost">
            <text>发布</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import PostEditor from '@/subpackage_forum/components/post-editor/post-editor.vue'
import { getTopicDetail, getTopicPosts, publishPost } from '@/api/forum.js'

export default {
  components: { PostEditor },
  data() {
    return {
      topicId: null,
      topicInfo: {
        name: '',
        participants: 0,
        posts: 0
      },
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
      }
    }
  },
  onLoad(options) {
    console.log('页面加载，参数:', options)
    if (options.topicId) {
      this.topicId = options.topicId
      console.log('设置topicId为:', this.topicId)
      this.loadTopicInfo()
      this.loadPostList()
    }
  },
  onShow() {
    // 测试：页面显示时自动打开弹窗（用于调试）
    // setTimeout(() => {
    //   this.openPublishModal()
    // }, 1000)
  },
  methods: {
    // 加载话题信息
    async loadTopicInfo() {
      try {
        const res = await getTopicDetail(this.topicId)
        
        if (res.code === 200) {
          const topic = res.data
          this.topicInfo = {
            name: topic.topicName || '未知话题',
            participants: topic.postCount || 0,
            posts: topic.postCount || 0
          }
        } else {
          // 使用虚拟数据
          this.useMockTopicData()
        }
      } catch (error) {
        console.error('加载话题信息失败:', error)
        // 使用虚拟数据
        this.useMockTopicData()
      }
    },
    
    // 使用虚拟话题数据
    useMockTopicData() {
      const mockTopics = {
        '1': { name: '考研经验分享', participants: 238, posts: 1127 },
        '2': { name: '食堂美食推荐', participants: 156, posts: 892 },
        '3': { name: '二手自行车转让', participants: 89, posts: 456 },
        '4': { name: '图书馆占座', participants: 67, posts: 234 },
        '5': { name: '社团招新', participants: 123, posts: 567 },
        '6': { name: '失物招领', participants: 234, posts: 789 },
        '7': { name: '兼职信息', participants: 98, posts: 345 },
        '8': { name: '校园跑腿', participants: 45, posts: 123 }
      }
      
      this.topicInfo = mockTopics[this.topicId] || { 
        name: '默认话题', 
        participants: 0, 
        posts: 0 
      }
    },

    // 加载帖子列表
    async loadPostList() {
      if (this.loading || this.noMore) return
      
      this.loading = true
      
      try {
        const params = {
          pageNum: this.page,
          pageSize: this.pageSize
        }
        
        const res = await getTopicPosts(this.topicId, params)
        
        if (res.code === 200) {
          const data = res.data
          const posts = data.records || data.list || []
          
          // 格式化数据
          const formattedPosts = posts.map(post => ({
            id: post.id,
            userName: post.authorName || '匿名用户',
            avatar: post.authorAvatar || '/static/logo.png',
            title: post.title || '',
            content: post.content || '',
            images: post.images || [],
            likeCount: post.likeCount || 0,
            commentCount: post.commentCount || 0,
            viewCount: post.viewCount || 0,
            isLiked: post.isLiked || false,
            createTime: post.createTime || '刚刚'
          }))
          
          if (this.page === 1) {
            this.postList = formattedPosts
          } else {
            this.postList = [...this.postList, ...formattedPosts]
          }
          
          // 判断是否还有更多数据
          if (formattedPosts.length < this.pageSize) {
            this.noMore = true
          }
        } else {
          // 使用虚拟数据
          this.useMockPostData()
        }
      } catch (error) {
        console.error('加载帖子列表失败:', error)
        // 使用虚拟数据
        this.useMockPostData()
      } finally {
        this.loading = false
        this.isRefreshing = false
      }
    },
    
    // 使用虚拟帖子数据
    useMockPostData() {
      console.log('使用虚拟帖子数据，当前topicId:', this.topicId)
      // 模拟不同话题的帖子数据
      const mockPostsByTopic = {
        '1': [
          {
            id: 1,
            userName: '张三',
            avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=zhangsan',
            title: '分享一下我的考研经验',
            content: '今年成功上岸985，分享一下我的备考经验，希望对学弟学妹有帮助。英语一定要坚持背单词，政治可以晚点开始...',
            images: [],
            likeCount: 128,
            commentCount: 36,
            viewCount: 1234,
            isLiked: false,
            createTime: '2小时前'
          },
          {
            id: 2,
            userName: '李四',
            avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=lisi',
            title: '',
            content: '求推荐数学复习资料，感觉自己基础不太扎实',
            images: [],
            likeCount: 45,
            commentCount: 12,
            viewCount: 567,
            isLiked: true,
            createTime: '5小时前'
          }
        ],
        '2': [
          {
            id: 3,
            userName: '王五',
            avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=wangwu',
            title: '二食堂三楼糖醋排骨真的绝了',
            content: '今天食堂新出的糖醋排骨真的绝了！强烈推荐大家去二食堂三楼尝尝，阿姨手抖都给我盛了一大勺',
            images: ['https://picsum.photos/200/150?random=1'],
            likeCount: 256,
            commentCount: 89,
            viewCount: 2345,
            isLiked: true,
            createTime: '3小时前'
          },
          {
            id: 4,
            userName: '赵六',
            avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=zhaoliu',
            title: '',
            content: '一食堂早餐推荐：豆浆油条配咸菜，yyds！',
            images: [],
            likeCount: 78,
            commentCount: 23,
            viewCount: 890,
            isLiked: false,
            createTime: '昨天'
          }
        ],
        'default': [
          {
            id: 5,
            userName: '匿名用户',
            avatar: '/static/logo.png',
            title: '欢迎参与话题讨论',
            content: '这里是话题讨论区，欢迎大家积极发言，分享你的观点和经验。',
            images: [],
            likeCount: 15,
            commentCount: 5,
            viewCount: 156,
            isLiked: false,
            createTime: '刚刚'
          }
        ]
      }
      
      const mockPosts = mockPostsByTopic[this.topicId] || mockPostsByTopic['default']
      console.log('选中的虚拟帖子数据:', mockPosts)
      
      if (this.page === 1) {
        this.postList = mockPosts
        console.log('设置postList为:', this.postList)
      } else {
        // 分页模拟
        const startIndex = (this.page - 1) * this.pageSize
        const endIndex = startIndex + this.pageSize
        const pagePosts = mockPosts.slice(startIndex, endIndex)
        this.postList = [...this.postList, ...pagePosts]
      }
      
      // 模拟没有更多数据
      if (this.postList.length >= mockPosts.length) {
        this.noMore = true
      }
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

    // 跳转到帖子详情
    goToDetail(id) {
      console.log('查看帖子详情:', id)
      console.log('跳转URL:', `/subpackage_forum/postDetail/postDetail?id=${id}`)
      uni.navigateTo({
        url: `/subpackage_forum/postDetail/postDetail?id=${id}`
      })
    },

    // 返回上一页
    goBack() {
      uni.navigateBack()
    },

    // 打开发帖弹窗
    openPublishModal() {
      console.log('点击参与话题按钮')
      console.log('当前topicId:', this.topicId)
      if (this.showPublishModal) return
      this.showPublishModal = true
      this.publishForm.topicId = this.topicId // 自动带上当前话题ID
      console.log('showPublishModal设置为true')
      this.$nextTick(() => {
        setTimeout(() => {
          if (this.showPublishModal) this.publishModalAnimating = true
          console.log('publishModalAnimating设置为true')
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
      if (!this.publishForm.content.trim()) {
        uni.showToast({ title: '请输入内容', icon: 'none' })
        return
      }

      uni.showLoading({ title: '发布中...' })
      
      try {
        const postData = {
          title: this.publishForm.title,
          content: this.publishForm.content,
          images: this.publishForm.images,
          topicId: this.topicId  // 自动带上当前话题ID
        }
        
        const res = await publishPost(postData)
        
        if (res.code === 200) {
          uni.hideLoading()
          uni.showToast({ title: '发布成功', icon: 'success' })
          setTimeout(() => {
            this.closePublishModal()
            // 重新加载帖子列表
            this.page = 1
            this.noMore = false
            this.loadPostList()
          }, 300)
        } else {
          uni.hideLoading()
          uni.showToast({ title: res.message || '发布失败', icon: 'none' })
        }
      } catch (error) {
        console.error('发布帖子失败:', error)
        uni.hideLoading()
        uni.showToast({ title: '网络错误', icon: 'none' })
      }
    }
  }
}
</script>

<style lang="scss">
.topic-detail-page {
  min-height: 100vh;
  background-color: #F7F7F9;
  display: flex;
  flex-direction: column;
  padding-bottom: constant(safe-area-inset-bottom);
  padding-bottom: env(safe-area-inset-bottom);
}

.back-button {
  position: fixed;
  top: var(--status-bar-height);
  top: constant(safe-area-inset-top);
  top: env(safe-area-inset-top);
  left: 30rpx;
  z-index: 999;
  width: 60rpx;
  height: 60rpx;
  border-radius: 50%;
  background-color: rgba(0, 0, 0, 0.3);
  backdrop-filter: blur(10rpx);
  display: flex;
  align-items: center;
  justify-content: center;
}

.back-icon {
  font-size: 36rpx;
  color: #FFFFFF;
  font-weight: bold;
}

.topic-header {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 60rpx 30rpx 40rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-shrink: 0;
}

.topic-info {
  flex: 1;
}

.topic-name {
  font-size: 36rpx;
  font-weight: 600;
  color: #FFFFFF;
  display: block;
  margin-bottom: 16rpx;
}

.topic-stats {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.stat-item {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.9);
}

.stat-divider {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.6);
}

.join-btn {
  background-color: rgba(255, 255, 255, 0.2);
  backdrop-filter: blur(10rpx);
  padding: 16rpx 32rpx;
  border-radius: 32rpx;
  border: 1rpx solid rgba(255, 255, 255, 0.3);
  
  text {
    font-size: 26rpx;
    color: #FFFFFF;
    font-weight: 500;
  }
}

.post-list {
  flex: 1;
  min-height: 0;
  padding: 20rpx;
  
  .post-item {
    background-color: #FFFFFF;
    border-radius: 16rpx;
    padding: 24rpx;
    margin-bottom: 28rpx;
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

/* 发帖弹窗样式 */
.publish-modal-mask {
  position: fixed;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.45);
  z-index: 1001;
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
