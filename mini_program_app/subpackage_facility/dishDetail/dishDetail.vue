<template>
  <view class="dish-detail-page">
    <nav-bar
      :title="dishInfo.name || '菜品详情'"
      :fixed="true"
      :placeholder="true"
      :border="true"
    />

    <scroll-view class="page-scroll" scroll-y>
      <!-- 菜品信息卡片 -->
      <view class="dish-info-card">
        <view class="dish-info-img" :style="dishInfo.imageUrl ? { backgroundImage: `url(${dishInfo.imageUrl})` } : {}"></view>
        <view class="dish-info-right">
          <text class="dish-info-name">{{ dishInfo.name || '菜品名称' }}</text>
          <view class="dish-info-stall">
            <text class="stall-icon">📍</text>
            <text>{{ dishInfo.stallName || '档口位置' }}</text>
          </view>
          <view class="dish-info-price-row">
            <text class="dish-info-price">¥{{ dishInfo.price || '0.0' }}</text>
          </view>
        </view>
      </view>

      <!-- 评分总览 -->
      <view class="detail-rating-overview">
        <view class="detail-rating-top">
          <text class="detail-rating-score">{{ recommendRate }}%</text>
          <text class="detail-rating-label">好评率</text>
        </view>
        <view class="detail-rating-stats">
          <view class="detail-stat-box">
            <text class="detail-stat-value total">{{ totalCount }}</text>
            <text class="detail-stat-label">总评价</text>
          </view>
          <view class="detail-stat-box">
            <text class="detail-stat-value good">{{ recommendCount }}</text>
            <text class="detail-stat-label">推荐</text>
          </view>
          <view class="detail-stat-box">
            <text class="detail-stat-value neutral">{{ neutralCount }}</text>
            <text class="detail-stat-label">一般</text>
          </view>
          <view class="detail-stat-box">
            <text class="detail-stat-value bad">{{ avoidCount }}</text>
            <text class="detail-stat-label">避雷</text>
          </view>
        </view>
      </view>

      <!-- 关键词云 -->
      <view class="detail-keywords" v-if="keywords.length > 0">
        <view class="detail-keywords-title">大家怎么说</view>
        <view class="detail-keywords-cloud">
          <text
            v-for="(keyword, index) in keywords"
            :key="index"
            class="detail-keyword"
            :class="keyword.type"
          >
            {{ keyword.text }}<text class="keyword-count">{{ keyword.count }}</text>
          </text>
        </view>
      </view>

      <!-- 评价列表标题 -->
      <view class="detail-reviews-header">
        <text class="detail-reviews-title">全部评价</text>
        <text class="detail-reviews-count">{{ reviewList.length }}条</text>
      </view>

      <!-- 评价列表 -->
      <view
        v-for="(review, index) in reviewList"
        :key="`${review.id}-${index}`"
        class="detail-review-card"
      >
        <view class="detail-review-header">
          <view class="detail-review-avatar" :style="{ background: getAvatarColor(index) }">
            {{ getAvatar(review) }}
          </view>
          <view class="detail-review-user">
            <text class="detail-review-name">{{ review.isAnonymous ? '匿名用户' : (review.userName || '用户') }}</text>
            <view class="detail-review-meta">
              <text class="detail-review-time">{{ formatTime(review.createTime) }}</text>
            </view>
          </view>
        </view>
        <text class="detail-review-text">{{ review.content || '暂无评价内容' }}</text>
        <view class="detail-review-images" v-if="review.images">
          <image
            v-for="(img, idx) in review.images.split(',')"
            :key="idx"
            :src="img"
            mode="aspectFill"
            class="review-img"
          />
        </view>
        <view class="detail-review-footer">
          <text class="review-verdict" :class="getVerdictClass(review)">
            {{ getVerdictText(review) }}
          </text>
          <view class="detail-review-like" :class="{ active: likedReviews[review.id] }" @click="toggleLike(review)">
            <text class="like-icon">♡</text>
            <text>{{ (review.helpfulCount || 0) + (likedReviews[review.id] ? 1 : 0) }}</text>
          </view>
        </view>
      </view>
      <view v-if="reviewList.length === 0" class="no-review">
        <text>暂无评价，快来抢沙发吧~</text>
      </view>

      <view class="bottom-space"></view>
    </scroll-view>

    <!-- 底部写评价栏 -->
    <view class="detail-review-footer-bar">
      <view class="detail-review-bar-content" @click="openReviewEditor">
        <text class="edit-icon">✏️</text>
        <text>写评价</text>
      </view>
    </view>

    <!-- 写评价弹窗 -->
    <view v-if="showReviewEditor" class="review-editor-overlay" @click="closeReviewEditor">
      <view class="review-editor-panel" @click.stop>
        <view class="review-editor-header">
          <view class="review-editor-close" @click="closeReviewEditor">✕</view>
          <text class="review-editor-title">写评价</text>
          <text class="review-editor-submit" @click="submitReview">发表</text>
        </view>

        <!-- 推荐/一般/避雷选择 -->
        <view class="review-verdict-selector">
          <view
            class="verdict-option"
            :class="{ 'active-recommend': verdictType === 'recommend' }"
            @click="selectVerdict('recommend')"
          >
            <text class="verdict-icon">👍</text>
            <text class="verdict-label">推荐</text>
          </view>
          <view
            class="verdict-option"
            :class="{ 'active-neutral': verdictType === 'neutral' }"
            @click="selectVerdict('neutral')"
          >
            <text class="verdict-icon">😐</text>
            <text class="verdict-label">一般</text>
          </view>
          <view
            class="verdict-option"
            :class="{ 'active-avoid': verdictType === 'avoid' }"
            @click="selectVerdict('avoid')"
          >
            <text class="verdict-icon">👎</text>
            <text class="verdict-label">避雷</text>
          </view>
        </view>

        <!-- 评价内容输入 -->
        <textarea
          v-model="reviewContent"
          class="review-editor-textarea"
          maxlength="200"
          placeholder="说说这道菜怎么样..."
        />

        <!-- 图片上传区域 -->
        <view class="review-image-upload-area">
          <view class="review-image-list">
            <view v-for="(img, index) in reviewImages" :key="index" class="review-image-item">
              <image :src="img" mode="aspectFill" class="preview-img" />
              <view class="remove-img" @click="removeImage(index)">×</view>
            </view>
          </view>
          <view v-if="reviewImages.length < 3" class="review-image-add" @click="chooseImage">
            <text class="add-icon">➕</text>
            <text class="add-text">添加图片</text>
          </view>
        </view>

        <!-- 同步至校园论坛 -->
        <view class="review-sync-forum" @click="syncForum = !syncForum">
          <view class="sync-checkbox" :class="{ checked: syncForum }">
            <text v-if="syncForum" class="check-icon">✓</text>
          </view>
          <text class="sync-label">同步至校园论坛</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import { getDishDetail, getDishReviewList, getDishReviewSummary, createDishReview, uploadDiningImage } from '@/api/dining.js'
import NavBar from '@/components/nav-bar/nav-bar.vue'

export default {
  components: {
    NavBar
  },
  data() {
    return {
      dishId: '',
      dishInfo: {},
      reviewList: [],
      reviewSummary: {
        totalCount: 0,
        recommendCount: 0,
        neutralCount: 0,
        avoidCount: 0,
        recommendRate: 0
      },
      showReviewEditor: false,
      reviewContent: '',
      reviewImages: [],
      verdictType: 'recommend',
      syncForum: false,
      likedReviews: {}
    }
  },
  computed: {
    totalCount() {
      return this.reviewSummary.totalCount || 0
    },
    recommendCount() {
      return this.reviewSummary.recommendCount || 0
    },
    neutralCount() {
      return this.reviewSummary.neutralCount || 0
    },
    avoidCount() {
      return this.reviewSummary.avoidCount || 0
    },
    recommendRate() {
      return this.reviewSummary.recommendRate || 0
    },
    keywords() {
      const keywordMap = {
        '好吃': 'positive', '美味': 'positive', '推荐': 'positive', '正宗': 'positive',
        '新鲜': 'positive', '入味': 'positive', 'Q弹': 'positive', '浓郁': 'positive',
        '实惠': 'positive', '足': 'positive', '香': 'positive', '嫩': 'positive',
        '绝配': 'positive', '完美': 'positive', '必吃': 'positive', '养胃': 'positive',
        '咸': 'negative', '油': 'negative', '硬': 'negative', '贵': 'negative',
        '排队久': 'negative', '太油': 'negative', '偏咸': 'negative'
      }
      const counts = {}
      this.reviewList.forEach(r => {
        if (r.content) {
          Object.keys(keywordMap).forEach(word => {
            if (r.content.includes(word)) {
              const type = keywordMap[word]
              const key = `${word}-${type}`
              counts[key] = { text: word, type, count: (counts[key]?.count || 0) + 1 }
            }
          })
        }
      })
      return Object.values(counts)
        .sort((a, b) => b.count - a.count)
        .slice(0, 8)
    }
  },
  onLoad(options) {
    if (options.dishId) {
      this.dishId = options.dishId
      this.loadDishDetail()
      this.loadReviewSummary()
      this.loadReviews()
    }
  },
  methods: {
    // 加载菜品详情
    async loadDishDetail() {
      try {
        const res = await getDishDetail(this.dishId)
        this.dishInfo = res.data || {}
      } catch (error) {
        console.error('加载菜品详情失败:', error)
      }
    },
    // 加载评价列表
    async loadReviews() {
      try {
        const res = await getDishReviewList({ dishId: this.dishId })
        this.reviewList = res.data || []
      } catch (error) {
        console.error('加载评价失败:', error)
      }
    },
    async loadReviewSummary() {
      try {
        const res = await getDishReviewSummary({ dishId: this.dishId })
        this.reviewSummary = res.data || this.reviewSummary
      } catch (error) {
        console.error('加载评价摘要失败:', error)
      }
    },
    // 打开评价弹窗
    openReviewEditor() {
      const token = uni.getStorageSync('token') || ''
      if (!token) {
        uni.showToast({ title: '请先登录', icon: 'none' })
        return
      }
      this.showReviewEditor = true
      this.reviewContent = ''
      this.reviewImages = []
      this.verdictType = 'recommend'
      this.syncForum = false
    },
    // 关闭评价弹窗
    closeReviewEditor() {
      this.showReviewEditor = false
    },
    // 选择评价类型
    selectVerdict(type) {
      this.verdictType = type
    },
    // 选择图片
    chooseImage() {
      uni.chooseImage({
        count: 3 - this.reviewImages.length,
        sizeType: ['compressed'],
        sourceType: ['album', 'camera'],
        success: (res) => {
          this.reviewImages = [...this.reviewImages, ...res.tempFilePaths]
        }
      })
    },
    // 删除图片
    removeImage(index) {
      this.reviewImages.splice(index, 1)
    },
    // 提交评价
    async submitReview() {
      if (!this.reviewContent.trim()) {
        uni.showToast({ title: '请输入评价内容', icon: 'none' })
        return
      }
      try {
        const ratingMap = { recommend: 5, neutral: 3, avoid: 2 }
        uni.showLoading({ title: '提交中', mask: true })
        const imageUrls = this.reviewImages.length
          ? await Promise.all(this.reviewImages.map(filePath => uploadDiningImage(filePath)))
          : []
        await createDishReview({
          dishId: this.dishId,
          stallId: this.dishInfo.stallId,
          rating: ratingMap[this.verdictType],
          content: this.reviewContent.trim(),
          images: imageUrls.join(','),
          isAnonymous: false
        })
        uni.showToast({ title: '评价成功', icon: 'success' })
        this.showReviewEditor = false
        this.reviewImages = []
        this.reviewContent = ''
        this.loadReviewSummary()
        this.loadReviews()
      } catch (error) {
        console.error('提交评价失败:', error)
        if (error?.code === 400 && error?.msg?.includes('已评价')) {
          uni.showToast({ title: '您已评价过该菜品', icon: 'none' })
        } else {
          uni.showToast({ title: error?.msg || '评价失败', icon: 'none' })
        }
      } finally {
        uni.hideLoading()
      }
    },
    // 点赞
    toggleLike(review) {
      this.$set(this.likedReviews, review.id, !this.likedReviews[review.id])
    },
    // 获取头像
    getAvatar(review) {
      if (review.isAnonymous) return '匿'
      const name = review.userName || '用户'
      return name.slice(0, 1)
    },
    // 获取头像颜色
    getAvatarColor(index) {
      const colors = ['#CDAE7D', '#D4856A', '#81C784', '#64B5F6', '#FFB74D', '#E57373', '#BA68C8', '#4DB6AC']
      return colors[index % colors.length]
    },
    // 获取评价类型样式
    getVerdictClass(review) {
      if (review.rating >= 4) return 'recommend'
      if (review.rating === 3) return 'neutral'
      return 'avoid'
    },
    // 获取评价类型文本
    getVerdictText(review) {
      if (review.rating >= 4) return '👍 推荐'
      if (review.rating === 3) return '😐 一般'
      return '👎 避雷'
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
.dish-detail-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #F7F7F9;
}

.page-scroll {
  flex: 1;
  height: 0;
  padding: 0;
  box-sizing: border-box;
}

/* 菜品信息卡片 */
.dish-info-card {
  display: flex;
  align-items: center;
  gap: 28rpx;
  padding: 36rpx 28rpx;
  background: #fff;
  border-bottom: 1rpx solid rgba(0,0,0,.06);
}

.dish-info-img {
  width: 144rpx;
  height: 144rpx;
  border-radius: 28rpx;
  flex-shrink: 0;
  background: linear-gradient(135deg, #5C7A99, #8BB8D9);
  background-size: cover;
  background-position: center;
}

.dish-info-right {
  flex: 1;
  min-width: 0;
}

.dish-info-name {
  display: block;
  font-size: 36rpx;
  font-weight: 900;
  color: #2f2a25;
  margin-bottom: 8rpx;
}

.dish-info-stall {
  font-size: 24rpx;
  font-weight: 600;
  color: rgba(0,0,0,.4);
  display: flex;
  align-items: center;
  gap: 8rpx;
  margin-bottom: 12rpx;
}

.stall-icon {
  font-size: 20rpx;
}

.dish-info-price-row {
  display: flex;
  align-items: baseline;
  gap: 20rpx;
}

.dish-info-price {
  font-size: 44rpx;
  font-weight: 900;
  color: #5C7A99;
}

/* 评分总览 */
.detail-rating-overview {
  background: #fff;
  border-radius: 36rpx;
  padding: 36rpx;
  margin: 28rpx;
  border: none;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.04);
}

.detail-rating-top {
  display: flex;
  align-items: baseline;
  gap: 20rpx;
  margin-bottom: 28rpx;
}

.detail-rating-score {
  font-size: 84rpx;
  font-weight: 900;
  color: #5C7A99;
  line-height: 1;
}

.detail-rating-label {
  font-size: 26rpx;
  font-weight: 600;
  color: rgba(0,0,0,.4);
}

.detail-rating-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20rpx;
}

.detail-stat-box {
  background: rgba(255,255,255,.9);
  border-radius: 24rpx;
  padding: 24rpx 16rpx;
  text-align: center;
  border: 1rpx solid rgba(0,0,0,.04);
}

.detail-stat-value {
  display: block;
  font-size: 40rpx;
  font-weight: 900;
  line-height: 1;
  margin-bottom: 8rpx;
}

.detail-stat-value.total { color: #2f2a25; }
.detail-stat-value.good { color: #4CAF50; }
.detail-stat-value.neutral { color: #FF9800; }
.detail-stat-value.bad { color: #F44336; }

.detail-stat-label {
  font-size: 20rpx;
  font-weight: 600;
  color: rgba(0,0,0,.4);
}

/* 关键词云 */
.detail-keywords {
  background: #fff;
  border-radius: 32rpx;
  padding: 32rpx;
  margin: 0 28rpx 28rpx;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.04);
}

.detail-keywords-title {
  font-size: 26rpx;
  font-weight: 700;
  color: rgba(0,0,0,.6);
  margin-bottom: 24rpx;
}

.detail-keywords-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.detail-keyword {
  display: inline-flex;
  align-items: center;
  gap: 4rpx;
  padding: 12rpx 24rpx;
  border-radius: 999rpx;
  font-size: 24rpx;
  font-weight: 600;
}

.detail-keyword.positive {
  background: linear-gradient(135deg, rgba(76,175,80,.1), rgba(76,175,80,.05));
  color: #2E7D32;
  border: 1rpx solid rgba(76,175,80,.2);
}

.detail-keyword.negative {
  background: linear-gradient(135deg, rgba(244,67,54,.1), rgba(244,67,54,.05));
  color: #C62828;
  border: 1rpx solid rgba(244,67,54,.2);
}

.keyword-count {
  font-size: 20rpx;
  opacity: .6;
  margin-left: 4rpx;
}

/* 评价列表标题 */
.detail-reviews-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 32rpx 28rpx 16rpx;
}

.detail-reviews-title {
  font-size: 28rpx;
  font-weight: 700;
  color: rgba(0,0,0,.6);
}

.detail-reviews-count {
  font-size: 24rpx;
  font-weight: 600;
  color: rgba(0,0,0,.35);
}

/* 评价列表 */
.detail-review-card {
  margin: 0 28rpx 20rpx;
  background: rgba(255,255,255,.95);
  border-radius: 28rpx;
  padding: 28rpx;
  border: 1rpx solid rgba(0,0,0,.06);
}

.detail-review-header {
  display: flex;
  align-items: center;
  gap: 20rpx;
  margin-bottom: 16rpx;
}

.detail-review-avatar {
  width: 68rpx;
  height: 68rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  font-weight: 700;
  color: #fff;
  flex-shrink: 0;
}

.detail-review-user {
  flex: 1;
  min-width: 0;
}

.detail-review-name {
  font-size: 26rpx;
  font-weight: 700;
  color: rgba(0,0,0,.7);
}

.detail-review-meta {
  display: flex;
  align-items: center;
  gap: 16rpx;
  font-size: 22rpx;
  margin-top: 4rpx;
}

.detail-review-time {
  color: rgba(0,0,0,.35);
}

.detail-review-text {
  font-size: 26rpx;
  font-weight: 500;
  color: rgba(0,0,0,.65);
  line-height: 1.6;
  margin-bottom: 16rpx;
}

.detail-review-images {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  margin-bottom: 16rpx;
}

.review-img {
  width: 160rpx;
  height: 160rpx;
  border-radius: 16rpx;
}

.detail-review-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.review-verdict {
  font-size: 22rpx;
  font-weight: 600;
  padding: 8rpx 20rpx;
  border-radius: 12rpx;
}

.review-verdict.recommend {
  background: rgba(76,175,80,.1);
  color: #4CAF50;
}

.review-verdict.neutral {
  background: rgba(0,0,0,.04);
  color: rgba(0,0,0,.5);
}

.review-verdict.avoid {
  background: rgba(244,67,54,.1);
  color: #F44336;
}

.detail-review-like {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 10rpx 20rpx;
  border-radius: 999rpx;
  border: 1rpx solid rgba(0,0,0,.08);
  background: rgba(255,255,255,.5);
  font-size: 22rpx;
  font-weight: 600;
  color: rgba(0,0,0,.5);
}

.detail-review-like.active {
  background: rgba(205,174,125,.15);
  border-color: #c9a55c;
  color: #8b6914;
}

.like-icon {
  font-size: 28rpx;
}

.no-review {
  padding: 80rpx 0;
  text-align: center;
  font-size: 24rpx;
  color: #b5ac9f;
}

.bottom-space {
  height: 140rpx;
}

/* 底部写评价栏 */
.detail-review-footer-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  background: rgba(239,233,222,.98);
  padding: 20rpx 28rpx;
  border-top: 1rpx solid rgba(0,0,0,.06);
}

.detail-review-bar-content {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
  padding: 20rpx 28rpx;
  border-radius: 999rpx;
  background: #fff;
  border: 1rpx solid rgba(0,0,0,.08);
  font-size: 26rpx;
  color: rgba(0,0,0,.4);
}

.edit-icon {
  font-size: 28rpx;
}

/* 写评价弹窗 */
.review-editor-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,.5);
  z-index: 99;
  display: flex;
  align-items: flex-end;
  justify-content: center;
}

.review-editor-panel {
  width: 100%;
  background: #EFE9DE;
  border-radius: 40rpx 40rpx 0 0;
  padding: 32rpx;
}

.review-editor-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 32rpx;
}

.review-editor-close {
  width: 56rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  color: rgba(0,0,0,.4);
  font-size: 32rpx;
}

.review-editor-title {
  font-size: 30rpx;
  font-weight: 700;
  color: rgba(0,0,0,.7);
}

.review-editor-submit {
  font-size: 28rpx;
  font-weight: 700;
  color: #5C7A99;
}

/* 推荐/一般/避雷选择 */
.review-verdict-selector {
  display: flex;
  gap: 20rpx;
  margin-bottom: 28rpx;
}

.verdict-option {
  flex: 1;
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  padding: 16rpx 24rpx;
  border-radius: 20rpx;
  border: 3rpx solid rgba(0,0,0,.08);
  background: rgba(255,255,255,.6);
}

.verdict-icon {
  font-size: 28rpx;
}

.verdict-label {
  font-size: 24rpx;
  font-weight: 700;
  color: rgba(0,0,0,.4);
}

.verdict-option.active-recommend {
  background: rgba(76,175,80,.1);
  border-color: #4CAF50;
}

.verdict-option.active-recommend .verdict-label {
  color: #4CAF50;
}

.verdict-option.active-neutral {
  background: rgba(0,0,0,.04);
  border-color: rgba(0,0,0,.2);
}

.verdict-option.active-neutral .verdict-label {
  color: rgba(0,0,0,.5);
}

.verdict-option.active-avoid {
  background: rgba(244,67,54,.1);
  border-color: #F44336;
}

.verdict-option.active-avoid .verdict-label {
  color: #F44336;
}

/* 评价内容输入 */
.review-editor-textarea {
  width: 100%;
  min-height: 200rpx;
  padding: 24rpx;
  border-radius: 24rpx;
  border: 1rpx solid rgba(0,0,0,.08);
  background: #fff;
  font-size: 28rpx;
  color: #2f2a25;
  box-sizing: border-box;
  margin-bottom: 28rpx;
}

/* 图片上传区域 */
.review-image-upload-area {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  margin-bottom: 28rpx;
}

.review-image-list {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.review-image-item {
  width: 144rpx;
  height: 144rpx;
  border-radius: 16rpx;
  overflow: hidden;
  position: relative;
}

.preview-img {
  width: 100%;
  height: 100%;
}

.remove-img {
  position: absolute;
  top: 4rpx;
  right: 4rpx;
  width: 36rpx;
  height: 36rpx;
  border-radius: 50%;
  background: rgba(0,0,0,.6);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24rpx;
}

.review-image-add {
  width: 144rpx;
  height: 144rpx;
  border-radius: 16rpx;
  border: 2rpx dashed rgba(0,0,0,.15);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  color: rgba(0,0,0,.3);
}

.add-icon {
  font-size: 40rpx;
}

.add-text {
  font-size: 20rpx;
}

/* 同步至校园论坛 */
.review-sync-forum {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.sync-checkbox {
  width: 40rpx;
  height: 40rpx;
  border-radius: 8rpx;
  border: 3rpx solid rgba(0,0,0,.15);
  display: flex;
  align-items: center;
  justify-content: center;
}

.sync-checkbox.checked {
  background: #4CAF50;
  border-color: #4CAF50;
}

.check-icon {
  color: #fff;
  font-size: 24rpx;
  font-weight: 700;
}

.sync-label {
  font-size: 24rpx;
  color: rgba(0,0,0,.6);
}
</style>
