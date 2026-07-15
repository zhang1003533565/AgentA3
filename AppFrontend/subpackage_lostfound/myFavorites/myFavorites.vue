<template>
  <view class="page-root">
    <view class="container">
      <nav-bar title="我的收藏" :fixed="true" :placeholder="true" />
      <scroll-view scroll-y class="page-body" refresher-enabled :refresher-triggered="refreshing" @refresherrefresh="refresh">
        <view v-if="!loading && items.length === 0" class="empty">
          <view class="empty-icon">☆</view>
          <view class="empty-title">还没有收藏商品</view>
        </view>
        <view v-for="item in items" :key="item.id" class="item-card" @click="goDetail(item.id)">
          <image v-if="item.images.length" class="cover" :src="item.images[0]" mode="aspectFill" />
          <view v-else class="cover placeholder">{{ emoji(item.id) }}</view>
          <view class="body">
            <view class="title">{{ item.title || '未命名商品' }}</view>
            <view class="price">¥{{ priceText(item.price) }}</view>
            <view class="meta">{{ item.campusName || '校内' }} · {{ item.pickupPoint || item.tradeLocation || item.location || '校内自提' }}</view>
          </view>
        </view>
      </scroll-view>
    </view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { getMyFavorites } from '@/api/secondhand'
const EMOJIS = ['📱', '💻', '📷', '🎧', '⌚', '📚', '👟', '🧥', '📦']
function normalize(raw = {}) {
  return {
    id: raw.id,
    title: raw.title || raw.name || '',
    price: raw.price,
    images: Array.isArray(raw.images) ? raw.images : [],
    campusName: raw.campusName || '',
    tradeLocation: raw.tradeLocation || '',
    pickupPoint: raw.pickupPoint || '',
    location: raw.location || ''
  }
}
export default {
  components: { NavBar },
  data() { return { loading: false, refreshing: false, items: [] } },
  onShow() { this.loadItems() },
  methods: {
    async refresh() { this.refreshing = true; await this.loadItems(); this.refreshing = false },
    async loadItems() {
      try {
        this.loading = true
        const res = await getMyFavorites({ current: 1, size: 100 })
        const records = Array.isArray(res?.data?.records) ? res.data.records : []
        this.items = records.map(normalize)
      } catch (e) {
        console.error('加载收藏失败', e)
        uni.showToast({ title: '加载失败', icon: 'none' })
      } finally { this.loading = false }
    },
    priceText(value) { const price = Number(value); return Number.isFinite(price) ? price.toFixed(price % 1 === 0 ? 0 : 2) : '--' },
    emoji(id) { return EMOJIS[(Number(id) || 0) % EMOJIS.length] },
    goDetail(id) { uni.navigateTo({ url: `/subpackage_lostfound/lostfoundDetail/lostfoundDetail?id=${id}` }) }
  }
}
</script>

<style scoped>
.page-root { min-height: 100vh; background: #f0f5fa; }
.container { width: 100%; max-width: 430px; min-height: 100vh; margin: 0 auto; padding: 0 16rpx; box-sizing: border-box; background: #e8f0f8; }
.page-body { height: calc(100vh - 88rpx); padding: 24rpx 0; box-sizing: border-box; }
.empty { padding: 160rpx 0; text-align: center; color: #8aa1b2; }
.empty-icon { font-size: 72rpx; margin-bottom: 20rpx; }
.empty-title { font-size: 28rpx; font-weight: 800; }
.item-card { display: flex; gap: 20rpx; padding: 20rpx; margin-bottom: 18rpx; background: #fff; border-radius: 18rpx; box-shadow: 0 6rpx 18rpx rgba(43, 68, 94, 0.08); }
.cover { width: 150rpx; height: 150rpx; border-radius: 14rpx; background: #edf4fa; flex-shrink: 0; }
.placeholder { display: flex; align-items: center; justify-content: center; font-size: 48rpx; }
.body { flex: 1; min-width: 0; }
.title { color: #172331; font-size: 28rpx; font-weight: 900; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.price { margin-top: 14rpx; color: #d56b55; font-size: 30rpx; font-weight: 900; }
.meta { margin-top: 14rpx; color: #7d8c9c; font-size: 23rpx; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>