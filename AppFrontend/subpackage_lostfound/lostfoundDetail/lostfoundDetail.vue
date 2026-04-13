<template>
  <view class="page-root">
    <view class="screen">
      <view class="container">
        <nav-bar title="详情" :fixed="true" :placeholder="true" />

        <scroll-view scroll-y class="page-body">
          <view class="dimg">
            <image v-if="curItem.images && curItem.images.length" :src="curItem.images[imgIdx]" mode="aspectFill" class="dimg-src" />
            <text v-else class="dimg-emoji">{{ curItem.emoji || emoji(curItem.id) }}</text>
            <view v-if="curItem.images && curItem.images.length > 1" class="counter">{{ imgIdx + 1 }}/{{ curItem.images.length }}</view>
          </view>

          <view class="dinfo">
            <view v-if="curItem.type === 'sell'" class="dprice">
              <small>¥</small>{{ curItem.price }}
            </view>
            <view class="dtitle">{{ curItem.name }}</view>
            <view class="ddesc">{{ curItem.desc }}</view>
          </view>

          <view class="seller" @click="openChat">
            <view class="sava">{{ curItem.userName ? curItem.userName.slice(0,1) : '' }}</view>
            <view class="sinfo">
              <view class="sname">{{ curItem.userName }}</view>
              <view class="stime">{{ fmt(curItem.ctime) }}</view>
            </view>
            <view class="sarrow">›</view>
          </view>
        </scroll-view>

        <view class="abar">
          <button class="abtn" @click="openChat">{{ curItem.type === 'want' ? '我有' : '我想要' }}</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { getSecondhandItemDetail } from '@/api/secondhand'

const EMOJIS = ['📱', '💻', '📷', '🎧', '⌚', '📚', '👟', '🧥', '🪑', '🏠', '🎮', '🎸', '🖥️', '📦']

function formatTimestamp(value) {
  if (!value) return ''
  return value.replace('T', ' ')
}

function normalizeItem(item) {
  return {
    id: item.id,
    name: item.title,
    desc: item.description || '',
    price: item.price,
    type: 'sell',
    images: Array.isArray(item.images) ? item.images : [],
    userName: item.seller?.username || '用户',
    userAva: item.seller?.avatar || '',
    ctime: formatTimestamp(item.createTime)
  }
}

export default {
  components: {
    NavBar
  },
  data() {
    return {
      itemId: null,
      curItem: {},
      imgIdx: 0
    }
  },
  async onLoad(options) {
    this.itemId = options.id
    await this.loadItem()
  },
  methods: {
    async loadItem() {
      try {
        const res = await getSecondhandItemDetail(this.itemId)
        this.curItem = normalizeItem(res?.data || {})
      } catch (e) {
        console.error('加载数据失败', e)
        uni.showToast({ title: '商品不存在', icon: 'none' })
        setTimeout(() => uni.navigateBack(), 1500)
      }
    },
    emoji(id) {
      return EMOJIS[(id || 0) % EMOJIS.length]
    },
    fmt(ts) {
      if (!ts) return ''
      const time = typeof ts === 'string' ? new Date(ts.replace(/-/g, '/')).getTime() : ts
      const diff = Date.now() - time
      if (diff < 60000) return '刚刚'
      if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
      if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
      const d = new Date(time)
      return `${d.getMonth() + 1}/${d.getDate()}`
    },
    openChat() {
      if (!this.curItem || !this.curItem.id) return
      uni.navigateTo({
        url: `/subpackage_lostfound/lostfoundChat/lostfoundChat?itemId=${this.curItem.id}`
      })
    }
  }
}
</script>

<style scoped>
.page-root {
  width: 100%;
  min-height: 100vh;
  background: #F0F5FA;
}

.screen {
  width: 100%;
  background: #F0F5FA;
  min-height: 100vh;
}

.container {
  width: 100%;
  max-width: 430px;
  margin: 0 auto;
  box-sizing: border-box;
  padding: 0 16rpx;
  background: #E8F0F8;
  min-height: 100vh;
  position: relative;
}

.page-body {
  flex: 1;
  overflow-y: auto;
}

.dimg {
  width: 100%;
  aspect-ratio: 3 / 2;
  background: linear-gradient(135deg, rgba(123, 168, 212, 0.35), rgba(92, 138, 184, 0.35));
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 160rpx;
  overflow: hidden;
  position: relative;
}

.dimg-src {
  width: 100%;
  height: 100%;
}

.counter {
  position: absolute;
  bottom: 24rpx;
  right: 24rpx;
  background: rgba(0, 0, 0, 0.75);
  color: #fff;
  padding: 8rpx 20rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
  font-weight: 600;
}

.dinfo {
  padding: 32rpx;
}

.dprice {
  font-size: 56rpx;
  font-weight: 900;
  color: #5C8AB8;
  margin-bottom: 12rpx;
}

.dprice small {
  font-size: 32rpx;
  font-weight: 600;
}

.dtitle {
  font-size: 36rpx;
  font-weight: 700;
  color: rgba(0, 0, 0, 0.85);
  margin-bottom: 24rpx;
  line-height: 1.4;
}

.ddesc {
  font-size: 30rpx;
  color: rgba(0, 0, 0, 0.5);
  line-height: 1.7;
  margin-bottom: 32rpx;
}

.seller {
  display: flex;
  align-items: center;
  gap: 24rpx;
  padding: 32rpx;
  background: #fff;
  border-radius: 20rpx;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.06);
  margin: 0 32rpx 32rpx;
}

.sava {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, #7ba8d4, #5c8ab8);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 36rpx;
  font-weight: 700;
  flex-shrink: 0;
}

.sinfo {
  flex: 1;
}

.sname {
  font-size: 30rpx;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.85);
}

.stime {
  font-size: 24rpx;
  color: rgba(0, 0, 0, 0.5);
  margin-top: 4rpx;
}

.sarrow {
  font-size: 28rpx;
  color: rgba(0, 0, 0, 0.2);
}

.abar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 24rpx 32rpx 56rpx;
  background: linear-gradient(to top, rgba(232, 240, 248, 1) 0%, rgba(232, 240, 248, 0.98) 100%);
  border-top: 2rpx solid rgba(0, 0, 0, 0.06);
  z-index: 50;
}

.abtn {
  height: 88rpx;
  border-radius: 24rpx;
  background: #7ba8d4;
  color: #fff;
  font-size: 30rpx;
  font-weight: 800;
  border: none;
  box-shadow: 0 8rpx 24rpx rgba(123, 168, 212, 0.6);
}
</style>
