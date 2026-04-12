<template>
  <view class="page-root">
    <view class="screen">
      <view class="container">
        <nav-bar title="我发布的" :fixed="true" :placeholder="true" />
        
        <scroll-view scroll-y class="page-body">
          <view v-if="myItems.length === 0" class="empty">
            <view class="empty-i">📦</view>
            <view class="empty-t">还没有发布过</view>
          </view>
          <view v-for="item in myItems" :key="item.id" class="micard">
            <view class="miimg" @click="goToDetail(item.id)">
              <text v-if="item.type === 'want'">🔍</text>
              <image v-else-if="item.images && item.images.length" :src="item.images[0]" mode="aspectFill" />
              <text v-else>{{ emoji(item.id) }}</text>
            </view>
            <view class="mibody">
              <view class="miname">{{ item.name }}</view>
              <view v-if="item.type === 'sell'" class="miprice">
                <small>¥</small>{{ item.price }}
              </view>
              <view class="mitime">{{ fmt(item.ctime) }}发布</view>
            </view>
            <button
              class="micard-btn"
              :style="{ borderColor: item.status === 'online' ? '#5C8AB8' : '#6FBF73', color: item.status === 'online' ? '#5C8AB8' : '#6FBF73' }"
              @click="toggleStatus(item.id)"
            >
              {{ item.status === 'online' ? '下架' : '上架' }}
            </button>
          </view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'

const STORAGE_KEYS = {
  items: 'items'
}

const EMOJIS = ['📱', '💻', '📷', '🎧', '⌚', '📚', '👟', '🧥', '🪑', '🏠', '🎮', '🎸', '🖥️', '📦']

export default {
  components: {
    NavBar
  },
  data() {
    return {
      items: []
    }
  },
  computed: {
    myItems() {
      return this.items.filter(item => item.userId === 'me')
    }
  },
  onLoad() {
    this.loadFromStorage()
  },
  onShow() {
    this.loadFromStorage()
  },
  methods: {
    loadFromStorage() {
      try {
        const stored = uni.getStorageSync(STORAGE_KEYS.items)
        if (stored) {
          this.items = JSON.parse(stored)
        }
      } catch (e) {
        console.error('加载数据失败', e)
      }
    },
    saveToStorage() {
      try {
        uni.setStorageSync(STORAGE_KEYS.items, JSON.stringify(this.items))
      } catch (e) {
        console.error('保存数据失败', e)
      }
    },
    emoji(id) {
      return EMOJIS[id % EMOJIS.length]
    },
    fmt(ts) {
      const d = new Date(ts)
      const now = new Date()
      const diff = now - d
      if (diff < 60000) return '刚刚'
      if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
      if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
      return `${d.getMonth() + 1}/${d.getDate()}`
    },
    toggleStatus(id) {
      const item = this.items.find(i => i.id === id)
      if (item) {
        item.status = item.status === 'online' ? 'offline' : 'online'
        this.saveToStorage()
        uni.showToast({
          title: item.status === 'online' ? '已上架' : '已下架',
          icon: 'none'
        })
      }
    },
    goToDetail(id) {
      uni.navigateTo({
        url: `/subpackage_lostfound/lostfoundDetail/lostfoundDetail?id=${id}`
      })
    }
  }
}
</script>

<style lang="scss">
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

.page {
  width: 100%;
  min-height: 100vh;
  box-sizing: border-box;
}

.page-body {
  flex: 1;
  overflow-y: auto;
}

.empty {
  padding: 120rpx 0;
  text-align: center;
}

.empty-i {
  font-size: 80rpx;
  margin-bottom: 24rpx;
}

.empty-t {
  font-size: 28rpx;
  color: #9ab0c0;
}

.micard {
  display: flex;
  align-items: center;
  gap: 24rpx;
  padding: 24rpx;
  background: #fff;
  border-radius: 20rpx;
  margin-bottom: 20rpx;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.06);
}

.miimg {
  width: 120rpx;
  height: 120rpx;
  border-radius: 16rpx;
  background: linear-gradient(135deg, rgba(123, 168, 212, 0.35), rgba(92, 138, 184, 0.35));
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 48rpx;
  overflow: hidden;
  flex-shrink: 0;
}

.miimg image {
  width: 100%;
  height: 100%;
}

.mibody {
  flex: 1;
  min-width: 0;
}

.miname {
  font-size: 28rpx;
  font-weight: 600;
  color: #2c3e50;
  margin-bottom: 8rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.miprice {
  font-size: 32rpx;
  font-weight: 800;
  color: #5C8AB8;
  margin-bottom: 4rpx;
}

.miprice small {
  font-size: 22rpx;
  font-weight: 600;
}

.mitime {
  font-size: 22rpx;
  color: #9ab0c0;
}

.micard-btn {
  padding: 12rpx 24rpx;
  border-radius: 999rpx;
  font-size: 24rpx;
  font-weight: 600;
  background: transparent;
  border: 2rpx solid;
}

.micard-btn::after {
  border: none;
}
</style>
