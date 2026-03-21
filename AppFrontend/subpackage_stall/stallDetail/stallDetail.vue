<template>
  <view class="stall-detail-page">
    <nav-bar :title="stall.name || '档口详情'" />

    <scroll-view class="detail-scroll" scroll-y :style="{ paddingTop: navBarHeight + 'px' }">
      <view class="hero">
        <image class="hero-bg" :src="stall.image" mode="aspectFill" />
        <view class="hero-mask" />
        <view class="hero-content">
          <text class="hero-cuisine">{{ stall.cuisine }}</text>
          <text class="hero-name">{{ stall.name }}</text>
          <view class="hero-rating-row">
            <text class="hero-score">★ {{ stall.score.toFixed(1) }}</text>
            <text class="hero-count">{{ totalReviews }}条评价</text>
          </view>
        </view>
      </view>

      <view class="card">
        <view class="title-row">
          <text class="stall-name">{{ stall.name }}</text>
          <text class="stall-score">★ {{ stall.score.toFixed(1) }}</text>
        </view>
        <text class="meta">所属餐厅：{{ stall.restaurantName }}</text>
        <text class="meta">菜系：{{ stall.cuisine }}</text>
        <text class="meta">营业时间：{{ stall.openTime }}</text>
        <text class="meta">人均：{{ stall.price }}</text>
        <text class="desc">{{ stall.description }}</text>

        <view class="tag-row">
          <text v-for="(tag, index) in stall.tags" :key="index" class="tag">{{ tag }}</text>
        </view>
      </view>

      <view class="card">
        <text class="section-title">评分概览</text>
        <view class="stat-item">
          <text class="stat-label">口味</text>
          <view class="bar-track"><view class="bar-fill" :style="{ width: scoreWidth(stall.tasteScore) }" /></view>
          <text class="stat-value">{{ stall.tasteScore.toFixed(1) }}</text>
        </view>
        <view class="stat-item">
          <text class="stat-label">服务</text>
          <view class="bar-track"><view class="bar-fill" :style="{ width: scoreWidth(stall.serviceScore) }" /></view>
          <text class="stat-value">{{ stall.serviceScore.toFixed(1) }}</text>
        </view>
        <view class="stat-item">
          <text class="stat-label">环境</text>
          <view class="bar-track"><view class="bar-fill" :style="{ width: scoreWidth(stall.envScore) }" /></view>
          <text class="stat-value">{{ stall.envScore.toFixed(1) }}</text>
        </view>
      </view>

      <view class="card">
        <text class="section-title">用户评价</text>
        <view v-for="(review, index) in stall.reviews" :key="index" class="review-item">
          <view class="review-head">
            <view class="review-user-wrap">
              <view class="review-avatar">{{ review.user.slice(0, 1) }}</view>
              <text class="review-user">{{ review.user }}</text>
            </view>
            <text class="review-score">{{ stars(review.score) }} {{ review.score }}分</text>
          </view>
          <text class="review-content">{{ review.content }}</text>
          <view class="review-tags">
            <text v-for="(tag, i) in review.tags" :key="i" class="review-tag">{{ tag }}</text>
          </view>
          <text class="review-time">{{ review.time }}</text>
        </view>
        <view v-if="!stall.reviews.length" class="empty-tip">暂无评价</view>
      </view>

      <view class="bottom-gap" />
    </scroll-view>

    <view class="bottom-bar">
      <view class="action-btn secondary" @click="handleCollect">收藏</view>
      <view class="action-btn primary" @click="handleReview">写评价</view>
    </view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'

export default {
  components: { NavBar },
  data() {
    return {
      navBarHeight: 88,
      stall: {
        id: '',
        name: '',
        image: '',
        restaurantName: '',
        cuisine: '',
        openTime: '',
        score: 0,
        tasteScore: 0,
        serviceScore: 0,
        envScore: 0,
        price: '',
        description: '',
        tags: [],
        reviews: []
      }
    }
  },
  computed: {
    totalReviews() {
      return (this.stall.reviews || []).length
    }
  },
  onLoad(options) {
    const sys = uni.getSystemInfoSync()
    this.navBarHeight = (sys.statusBarHeight || 0) + 44
    this.loadStall(options.stallId)
  },
  methods: {
    scoreWidth(score) {
      const s = Math.max(0, Math.min(5, score || 0))
      return `${(s / 5) * 100}%`
    },
    stars(score) {
      const n = Math.max(1, Math.min(5, Math.round(score || 0)))
      return '★'.repeat(n)
    },
    handleCollect() {
      uni.showToast({ title: '已加入收藏', icon: 'none' })
    },
    handleReview() {
      uni.showToast({ title: '评价功能开发中', icon: 'none' })
    },
    loadStall(stallId) {
      const mockData = {
        '301': {
          id: '301',
          name: '家常菜档口',
          image: 'https://picsum.photos/seed/stall301/1200/600',
          restaurantName: '第一食堂',
          cuisine: '中式快餐',
          openTime: '10:30 - 13:30 / 16:30 - 19:30',
          score: 4.7,
          tasteScore: 4.8,
          serviceScore: 4.6,
          envScore: 4.3,
          price: '15-25元',
          description: '主打家常盖浇饭、两荤一素套餐，支持线上点单。',
          tags: ['出餐快', '高性价比', '午餐热门'],
          reviews: [
            { user: '张同学', score: 5, content: '糖醋里脊很稳，排队也快。', time: '2小时前', tags: ['口味在线', '出餐快'] },
            { user: '王同学', score: 4, content: '分量足，口味偏咸一点。', time: '昨天', tags: ['分量大'] }
          ]
        },
        '302': {
          id: '302',
          name: '面食档口',
          image: 'https://picsum.photos/seed/stall302/1200/600',
          restaurantName: '第一食堂',
          cuisine: '面条/饺子',
          openTime: '07:00 - 20:30',
          score: 4.5,
          tasteScore: 4.6,
          serviceScore: 4.4,
          envScore: 4.2,
          price: '10-20元',
          description: '手擀面、牛肉面、水饺供应稳定，早餐时段人气高。',
          tags: ['早餐推荐', '面食丰富'],
          reviews: [
            { user: '李同学', score: 5, content: '牛肉面汤底不错，推荐。', time: '1天前', tags: ['汤底好', '性价比高'] }
          ]
        },
        '303': {
          id: '303',
          name: '轻食档口',
          image: 'https://picsum.photos/seed/stall303/1200/600',
          restaurantName: '第一食堂',
          cuisine: '沙拉/简餐',
          openTime: '09:00 - 19:00',
          score: 4.3,
          tasteScore: 4.2,
          serviceScore: 4.3,
          envScore: 4.5,
          price: '18-30元',
          description: '提供鸡胸肉沙拉、低脂便当和酸奶杯。',
          tags: ['轻食', '健身友好'],
          reviews: []
        },
        '401': {
          id: '401',
          name: '川湘风味',
          image: 'https://picsum.photos/seed/stall401/1200/600',
          restaurantName: '第二食堂',
          cuisine: '川菜/湘菜',
          openTime: '10:30 - 20:30',
          score: 4.6,
          tasteScore: 4.8,
          serviceScore: 4.4,
          envScore: 4.1,
          price: '16-28元',
          description: '麻辣、香辣口味为主，支持辣度选择。',
          tags: ['重口味', '下饭'],
          reviews: [
            { user: '赵同学', score: 5, content: '回锅肉很香，下饭。', time: '30分钟前', tags: ['香辣', '菜量足'] },
            { user: '陈同学', score: 4, content: '味道好，偶尔会偏辣。', time: '3天前', tags: ['辣度高'] }
          ]
        },
        '402': {
          id: '402',
          name: '烤肉饭档口',
          image: 'https://picsum.photos/seed/stall402/1200/600',
          restaurantName: '第二食堂',
          cuisine: '日韩料理',
          openTime: '11:00 - 21:00',
          score: 4.4,
          tasteScore: 4.5,
          serviceScore: 4.3,
          envScore: 4.2,
          price: '20-35元',
          description: '现烤鸡腿饭、牛肉饭，酱汁口味偏甜。',
          tags: ['现烤', '甜口'],
          reviews: [
            { user: '周同学', score: 4, content: '鸡腿很嫩，等餐10分钟左右。', time: '昨天', tags: ['肉质嫩'] }
          ]
        },
        '403': {
          id: '403',
          name: '夜宵档口',
          image: 'https://picsum.photos/seed/stall403/1200/600',
          restaurantName: '第二食堂',
          cuisine: '烧烤/小吃',
          openTime: '17:00 - 22:00',
          score: 4.2,
          tasteScore: 4.3,
          serviceScore: 4.1,
          envScore: 3.9,
          price: '12-30元',
          description: '晚间供应炸串、烤串、炒粉，适合宵夜。',
          tags: ['夜宵', '选择多'],
          reviews: [
            { user: '孙同学', score: 4, content: '夜里选择多，人也多。', time: '5天前', tags: ['晚间热门'] }
          ]
        }
      }

      this.stall = mockData[stallId] || {
        id: stallId || '',
        name: '档口详情',
        image: 'https://picsum.photos/seed/stalldefault/1200/600',
        restaurantName: '未知餐厅',
        cuisine: '未知菜系',
        openTime: '暂无',
        score: 0,
        tasteScore: 0,
        serviceScore: 0,
        envScore: 0,
        price: '暂无',
        description: '暂无数据',
        tags: [],
        reviews: []
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.stall-detail-page {
  min-height: 100vh;
  background: #f6f7fb;
}

.detail-scroll {
  height: 100vh;
}

.hero {
  margin: 24rpx 24rpx 0;
  border-radius: 24rpx;
  overflow: hidden;
  position: relative;
  height: 260rpx;
}

.hero-bg {
  width: 100%;
  height: 100%;
}

.hero-mask {
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  background: linear-gradient(180deg, rgba(0, 0, 0, 0.08) 0%, rgba(0, 0, 0, 0.5) 100%);
}

.hero-content {
  position: absolute;
  left: 24rpx;
  right: 24rpx;
  bottom: 22rpx;
}

.hero-cuisine {
  display: inline-block;
  font-size: 22rpx;
  color: #fff;
  background: rgba(255, 255, 255, 0.22);
  padding: 6rpx 14rpx;
  border-radius: 999rpx;
}

.hero-name {
  margin-top: 12rpx;
  display: block;
  font-size: 38rpx;
  color: #fff;
  font-weight: 700;
}

.hero-rating-row {
  margin-top: 10rpx;
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.hero-score {
  font-size: 26rpx;
  color: #ffd666;
  font-weight: 600;
}

.hero-count {
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.92);
}

.card {
  margin: 24rpx;
  padding: 28rpx;
  border-radius: 20rpx;
  background: #fff;
  box-shadow: 0 10rpx 24rpx rgba(31, 35, 41, 0.05);
}

.title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16rpx;
}

.stall-name {
  font-size: 36rpx;
  color: #1f2329;
  font-weight: 700;
}

.stall-score {
  font-size: 26rpx;
  color: #ff7d00;
  font-weight: 600;
}

.meta {
  margin-top: 12rpx;
  display: block;
  font-size: 24rpx;
  color: #4e5969;
}

.desc {
  margin-top: 16rpx;
  display: block;
  font-size: 25rpx;
  color: #1f2329;
  line-height: 1.7;
}

.tag-row {
  margin-top: 16rpx;
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}

.tag {
  padding: 8rpx 16rpx;
  border-radius: 999rpx;
  background: #eef3ff;
  color: #165dff;
  font-size: 22rpx;
}

.section-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #1f2329;
  margin-bottom: 18rpx;
  display: block;
}

.stat-item {
  display: flex;
  align-items: center;
  margin-bottom: 18rpx;
}

.stat-item:last-child {
  margin-bottom: 0;
}

.stat-label {
  width: 72rpx;
  font-size: 24rpx;
  color: #4e5969;
}

.bar-track {
  flex: 1;
  height: 14rpx;
  border-radius: 999rpx;
  background: #f2f3f5;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  border-radius: 999rpx;
  background: linear-gradient(90deg, #ffb84d 0%, #ff7d00 100%);
}

.stat-value {
  width: 54rpx;
  text-align: right;
  font-size: 24rpx;
  color: #ff7d00;
}

.review-item {
  padding: 20rpx;
  border-radius: 16rpx;
  background: #f9fafc;
  margin-bottom: 14rpx;
}

.review-item:last-child {
  margin-bottom: 0;
}

.review-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.review-user-wrap {
  display: flex;
  align-items: center;
}

.review-avatar {
  width: 44rpx;
  height: 44rpx;
  border-radius: 999rpx;
  background: #d9e6ff;
  color: #165dff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22rpx;
  font-weight: 600;
  margin-right: 10rpx;
}

.review-user {
  font-size: 26rpx;
  color: #1f2329;
  font-weight: 500;
}

.review-score {
  font-size: 22rpx;
  color: #ff7d00;
}

.review-content {
  margin-top: 10rpx;
  display: block;
  font-size: 24rpx;
  color: #4e5969;
  line-height: 1.7;
}

.review-tags {
  margin-top: 10rpx;
  display: flex;
  flex-wrap: wrap;
  gap: 10rpx;
}

.review-tag {
  padding: 6rpx 12rpx;
  border-radius: 999rpx;
  background: #fff3e8;
  color: #ff7d00;
  font-size: 20rpx;
}

.review-time {
  margin-top: 8rpx;
  display: block;
  font-size: 22rpx;
  color: #86909c;
}

.empty-tip {
  padding: 20rpx 0 6rpx;
  text-align: center;
  font-size: 24rpx;
  color: #86909c;
}

.bottom-gap {
  height: 120rpx;
}

.bottom-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 18rpx 24rpx 26rpx;
  background: #fff;
  box-shadow: 0 -8rpx 24rpx rgba(31, 35, 41, 0.06);
  display: flex;
  gap: 16rpx;
}

.action-btn {
  flex: 1;
  height: 76rpx;
  border-radius: 14rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  font-weight: 600;
}

.action-btn.secondary {
  color: #165dff;
  background: #eef3ff;
}

.action-btn.primary {
  color: #fff;
  background: linear-gradient(135deg, #4080ff 0%, #165dff 100%);
}
</style>
