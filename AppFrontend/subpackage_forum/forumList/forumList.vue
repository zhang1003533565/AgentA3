<template>
  <view class="forum-container">
    <nav-bar title="校园论坛" :showBack="false" />
    <!-- 顶部搜索栏 -->
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
      <view class="publish-btn" @click="goToPublish">
        <text>发帖</text>
      </view>
    </view>

    <!-- 话题标签 -->
    <scroll-view class="topic-scroll" scroll-x>
      <view class="topic-list">
        <view 
          v-for="(item, index) in topics" 
          :key="index"
          class="topic-item"
          :class="{ active: currentTopic === item.id }"
          @click="selectTopic(item.id)"
        >
          # {{ item.name }}
        </view>
      </view>
    </scroll-view>

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
    </scroll-view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
export default {
  components: { NavBar },
  data() {
    return {
      searchKeyword: '',
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
      isRefreshing: false
    }
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
    
    // 跳转到发布
    goToPublish() {
      uni.navigateTo({
        url: '/subpackage_forum/publishPost/publishPost'
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
    }
  }
}
</script>

<style lang="scss">
.forum-container {
  min-height: 100vh;
  background-color: #F7F7F9;
  padding-bottom: 120rpx;
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
    margin-right: 20rpx;
    
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
  
  .publish-btn {
    padding: 16rpx 32rpx;
    background-color: #5C7A99;
    border-radius: 32rpx;
    
    text {
      font-size: 28rpx;
      color: #FFFFFF;
      font-weight: 500;
    }
  }
}

.topic-scroll {
  background-color: #FFFFFF;
  padding: 0 20rpx 20rpx;
  white-space: nowrap;
  
  .topic-list {
    display: flex;
  }
  
  .topic-item {
    display: inline-block;
    padding: 12rpx 28rpx;
    margin-right: 16rpx;
    font-size: 26rpx;
    color: #666;
    background-color: #F5F5F7;
    border-radius: 32rpx;
    
    &.active {
      color: #FFFFFF;
      background-color: #5C7A99;
    }
  }
}

.post-list {
  height: calc(100vh - 200rpx);
  padding: 20rpx;
  
  .post-item {
    background-color: #FFFFFF;
    border-radius: 16rpx;
    padding: 24rpx;
    margin-bottom: 20rpx;
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
</style>
