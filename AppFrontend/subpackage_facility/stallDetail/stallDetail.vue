<template>
  <view class="stall-page">
    <nav-bar
      :title="stallName"
      :fixed="true"
      :placeholder="true"
      :border="true"
    />

    <scroll-view class="page-scroll" scroll-y>
      <view class="empty-tip" v-if="dishList.length === 0">
        <text>暂无菜品</text>
      </view>
      <view
        v-for="dish in dishList"
        :key="dish.id"
        class="dish-card"
        @click="openDishReview(dish)"
      >
        <view class="dish-thumb" :style="dish.imageUrl ? { backgroundImage: `url(${dish.imageUrl})` } : {}"></view>
        <view class="dish-content">
          <view class="dish-title-row">
            <view class="dish-title-group">
              <text class="dish-name">{{ dish.name }}</text>
              <text class="dish-price">¥{{ dish.price }}</text>
            </view>
            <view class="dish-rate-group">
              <text class="dish-rate-label">评分</text>
              <text class="dish-rate-value" :class="rateClass(dish.rating * 20)">{{ dish.rating }}</text>
            </view>
          </view>

          <text class="dish-desc">口味：{{ dish.taste || '暂无' }}</text>

          <view class="dish-comment-hint">
            <text class="comment-icon">□</text>
            <text class="comment-text">已售{{ dish.soldCount }}份</text>
          </view>
        </view>
      </view>

      <view class="bottom-space"></view>
    </scroll-view>

    <view v-if="activeDish" class="review-overlay" @click="closeDishReview">
      <view class="review-sheet" @click.stop>
        <view class="sheet-handle"></view>

        <view class="sheet-header">
          <text class="sheet-title">{{ activeDish.name }}</text>
          <text class="sheet-count">{{ currentReviews.length }}条评论</text>
        </view>

        <scroll-view class="sheet-review-list" scroll-y>
          <view
            v-for="(review, index) in currentReviews"
            :key="`${review.id}-${index}`"
            class="review-card"
          >
            <view class="review-top">
              <view class="review-user-box">
                <view class="review-avatar">{{ getAvatar(review) }}</view>
                <view class="review-user-meta">
                  <text class="review-user">{{ review.isAnonymous ? '匿名用户' : (review.userName || '用户') }}</text>
                  <text class="review-time">{{ formatTime(review.createTime) }}</text>
                </view>
              </view>
              <view class="review-rating">
                <text v-for="i in 5" :key="i" class="star" :class="{ active: i <= Math.round(review.rating) }">★</text>
              </view>
            </view>

            <text class="review-content">{{ review.content || '暂无评价内容' }}</text>

            <view v-if="review.images" class="review-images">
              <image v-for="(img, idx) in review.images.split(',')" :key="idx" :src="img" mode="aspectFill" class="review-img" />
            </view>
          </view>
          <view v-if="currentReviews.length === 0" class="no-review">
            <text>暂无评价，快来抢沙发吧~</text>
          </view>
        </scroll-view>

        <view class="sheet-editor">
          <text class="editor-title">写评价</text>
          <textarea
            v-model="reviewDraft"
            class="editor-input"
            maxlength="200"
            placeholder="分享你的用餐体验..."
          />
          <view class="editor-actions">
            <view class="editor-upload">
              <text class="upload-icon">◫</text>
            </view>
            <view class="editor-submit" @click="submitReview">发表评价</view>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import { createDishReview, getDishList, getDishReviewList } from '@/api/dining.js'
import NavBar from '@/components/nav-bar/nav-bar.vue'

export default {
  components: {
    NavBar
  },
  data() {
    return {
      reviewDraft: '',
      activeDish: null,
      stallId: '',
      stallName: '档口详情',
      dishList: [],
      // 当前菜品的评价列表
      currentReviews: []
    }
  },
  onLoad(options) {
    if (options.stallId) {
      this.stallId = options.stallId
    }
    this.loadDishes()
  },
  methods: {
    rateClass(rate) {
      if (rate >= 95) return 'is-excellent'
      if (rate >= 85) return 'is-good'
      return 'is-warn'
    },
    openDishReview(dish) {
      this.activeDish = dish
      this.reviewDraft = ''
      // 加载该菜品的评价
      this.loadDishReviews(dish.id)
    },
    closeDishReview() {
      this.activeDish = null
      this.reviewDraft = ''
    },
    submitReview() {
      if (!this.reviewDraft.trim()) {
        uni.showToast({ title: '请输入评价内容', icon: 'none' })
        return
      }
      if (!this.activeDish) {
        uni.showToast({ title: '请选择菜品', icon: 'none' })
        return
      }
      // 调用提交评价接口
      this.submitDishReview()
    },
    // 加载菜品评价
    async loadDishReviews(dishId) {
      try {
        const res = await getDishReviewList({ dishId })
        this.currentReviews = res.data || []
      } catch (error) {
        console.error('加载评价失败:', error)
      }
    },
    // 提交菜品评价
    async submitDishReview() {
      try {
        const token = uni.getStorageSync('token') || ''
        if (!token) {
          uni.showToast({ title: '请先登录', icon: 'none' })
          return
        }
        await createDishReview({
          dishId: this.activeDish.id,
          stallId: this.activeDish.stallId,
          rating: 5.0,
          content: this.reviewDraft.trim(),
          isAnonymous: false
        })
        uni.showToast({ title: '评价成功', icon: 'success' })
        this.reviewDraft = ''
        // 重新加载评价列表
        this.loadDishReviews(this.activeDish.id)
      } catch (error) {
        console.error('提交评价失败:', error)
        if (error?.code === 400 && error?.msg?.includes('已评价')) {
          uni.showToast({ title: '您已评价过该菜品', icon: 'none' })
        } else {
          uni.showToast({ title: error?.msg || '评价失败', icon: 'none' })
        }
      }
    },
    async loadDishes() {
      try {
        const res = await getDishList({ stallId: this.stallId })
        this.dishList = res.data || []

        // 获取档口名称
        if (this.dishList.length > 0) {
          this.stallName = this.dishList[0].stallName || '档口详情'
        }
      } catch (error) {
        console.error('加载菜品数据失败:', error)
        uni.showToast({ title: '加载失败', icon: 'none' })
      }
    },
    // 获取用户头像
    getAvatar(review) {
      if (review.isAnonymous) return '匿'
      const name = review.userName || '用户'
      return name.slice(0, 1)
    },
    // 格式化时间
    formatTime(timeStr) {
      if (!timeStr) return ''
      try {
        const date = new Date(timeStr)
        const now = new Date()
        const diff = now - date
        const minutes = Math.floor(diff / 60000)
        const hours = Math.floor(diff / 3600000)
        const days = Math.floor(diff / 86400000)

        if (minutes < 1) return '刚刚'
        if (minutes < 60) return `${minutes}分钟前`
        if (hours < 24) return `${hours}小时前`
        if (days < 7) return `${days}天前`
        return date.toLocaleDateString()
      } catch (e) {
        return timeStr
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.stall-page {
  min-height: 100vh;
  background: linear-gradient(180deg, #f7f2e8 0%, #f4ede1 32%, #f6f1e8 100%);
}

.page-scroll {
  height: calc(100vh - 92rpx);
  padding: 18rpx 22rpx 0;
  box-sizing: border-box;
}

.dish-card {
  margin-bottom: 18rpx;
  padding: 18rpx;
  border-radius: 24rpx;
  background: rgba(255, 255, 255, 0.96);
  display: flex;
  gap: 18rpx;
  box-shadow: 0 14rpx 30rpx rgba(184, 160, 119, 0.12);
}

.dish-thumb {
  width: 118rpx;
  height: 118rpx;
  border-radius: 20rpx;
  flex-shrink: 0;
  background-size: cover;
  background-position: center;
  background-color: #f5f5f5;
}

.dish-content {
  flex: 1;
  min-width: 0;
}

.dish-title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16rpx;
}

.dish-title-group {
  min-width: 0;
}

.dish-name {
  font-size: 34rpx;
  font-weight: 700;
  color: #2f2a25;
}

.dish-price {
  margin-left: 10rpx;
  font-size: 30rpx;
  color: #d07e58;
  font-weight: 700;
}

.dish-rate-group {
  flex-shrink: 0;
  text-align: right;
}

.dish-rate-label {
  display: block;
  font-size: 18rpx;
  color: #b6aa9a;
}

.dish-rate-value {
  margin-top: 4rpx;
  display: block;
  font-size: 22rpx;
  font-weight: 700;
}

.dish-rate-value.is-excellent {
  color: #5ea465;
}

.dish-rate-value.is-good {
  color: #7da24d;
}

.dish-rate-value.is-warn {
  color: #d99138;
}

.dish-desc {
  display: block;
  margin-top: 10rpx;
  font-size: 22rpx;
  color: #8f8478;
  line-height: 1.5;
}

.dish-comment-hint {
  margin-top: 14rpx;
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.comment-icon {
  font-size: 18rpx;
  color: #d2a35f;
}

.comment-text {
  font-size: 20rpx;
  color: #c39d69;
}

.bottom-space {
  height: 32rpx;
}

.review-overlay {
  position: fixed;
  inset: 0;
  background: rgba(43, 37, 30, 0.32);
  display: flex;
  align-items: flex-end;
  z-index: 99;
}

.review-sheet {
  width: 100%;
  max-height: 72vh;
  background: #f9f6f0;
  border-top-left-radius: 28rpx;
  border-top-right-radius: 28rpx;
  padding: 10rpx 22rpx 24rpx;
  box-sizing: border-box;
}

.sheet-handle {
  width: 74rpx;
  height: 8rpx;
  border-radius: 999rpx;
  background: #d9d0c5;
  margin: 8rpx auto 18rpx;
}

.sheet-header {
  padding: 0 8rpx 18rpx;
}

.sheet-title {
  display: block;
  font-size: 34rpx;
  font-weight: 700;
  color: #2d2925;
}

.sheet-count {
  display: block;
  margin-top: 8rpx;
  font-size: 20rpx;
  color: #9f9385;
}

.sheet-review-list {
  max-height: 560rpx;
}

.review-card {
  margin-bottom: 16rpx;
  padding: 20rpx;
  border-radius: 22rpx;
  background: #ffffff;
}

.review-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.review-user-box {
  display: flex;
  align-items: center;
  gap: 14rpx;
}

.review-avatar {
  width: 40rpx;
  height: 40rpx;
  border-radius: 50%;
  background: #c9a15d;
  color: #fff;
  font-size: 20rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.review-user-meta {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.review-user {
  font-size: 24rpx;
  font-weight: 600;
  color: #6a5e51;
}

.review-time {
  font-size: 18rpx;
  color: #b5ac9f;
}

.review-rating {
  display: flex;
  gap: 4rpx;
}

.review-rating .star {
  font-size: 18rpx;
  color: #d9d0c5;
}

.review-rating .star.active {
  color: #f5c542;
}

.review-content {
  display: block;
  margin-top: 12rpx;
  font-size: 24rpx;
  color: #6d645a;
  line-height: 1.6;
}

.review-images {
  margin-top: 12rpx;
  display: flex;
  gap: 12rpx;
  flex-wrap: wrap;
}

.review-img {
  width: 120rpx;
  height: 120rpx;
  border-radius: 12rpx;
}

.no-review {
  padding: 40rpx 0;
  text-align: center;
  font-size: 24rpx;
  color: #b5ac9f;
}

.sheet-editor {
  margin-top: 14rpx;
  padding: 18rpx 12rpx 0;
}

.editor-title {
  display: block;
  font-size: 24rpx;
  color: #887d71;
  margin-bottom: 14rpx;
}

.editor-input {
  width: 100%;
  height: 110rpx;
  padding: 18rpx 20rpx;
  border-radius: 18rpx;
  background: #ffffff;
  box-sizing: border-box;
  font-size: 24rpx;
  color: #52483e;
}

.editor-actions {
  margin-top: 18rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.editor-upload {
  width: 48rpx;
  height: 48rpx;
  border-radius: 12rpx;
  border: 1rpx solid #ddd3c8;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #a89d91;
}

.editor-submit {
  padding: 12rpx 26rpx;
  border-radius: 999rpx;
  background: linear-gradient(90deg, #d7bb83 0%, #c49b57 100%);
  color: #fff;
  font-size: 22rpx;
}
</style>
