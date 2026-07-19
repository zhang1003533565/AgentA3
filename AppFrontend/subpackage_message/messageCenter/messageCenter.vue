<template>
  <view class="message-center-page">
    <nav-bar title="消息" />

    <scroll-view scroll-y class="message-scroll" :show-scrollbar="false">
      <view class="module-tabs">
        <view
          v-for="module in modules"
          :key="module.type"
          class="module-tab"
          :class="{ active: activeModule === module.type }"
          @click="activeModule = module.type"
        >
          <text>{{ module.label }}</text>
          <view v-if="moduleUnread(module.type) > 0" class="module-badge">{{ formatCount(moduleUnread(module.type)) }}</view>
        </view>
      </view>

      <view class="section-head">
        <view class="section-mark"></view>
        <text class="section-title">{{ activeModuleLabel }}</text>
      </view>

      <view v-if="activeModule === 'LOST_FOUND'" class="category-list">
        <view
          v-for="entry in lostFoundEntries"
          :key="entry.type"
          class="category-item"
          :class="{ disabled: entry.disabled }"
          @click="openCategory(entry)"
        >
          <view class="category-icon" :class="entry.iconClass">
            <image class="category-icon-img" src="/static/icons/line/message-circle.svg" mode="aspectFit" />
            <view v-if="entry.unreadCount > 0" class="unread-dot"></view>
          </view>
          <view class="category-body">
            <view class="category-row">
              <text class="category-title">{{ entry.title }}</text>
              <text class="category-time">{{ entry.timeText }}</text>
            </view>
            <text class="category-desc">{{ entry.desc }}</text>
          </view>
          <view class="category-right">
            <text v-if="entry.unreadCount > 0" class="category-count">{{ formatCount(entry.unreadCount) }}</text>
            <text class="category-arrow">›</text>
          </view>
        </view>
      </view>

      <view v-else-if="activeModule === 'EXAM'" class="category-list">
        <view
          v-for="entry in examEntries"
          :key="entry.id"
          class="category-item"
          @click="openCategory(entry)"
        >
          <view class="category-icon exam-icon">
            <image class="category-icon-img" src="/static/icons/line/message-circle.svg" mode="aspectFit" />
            <view v-if="!entry.isRead" class="unread-dot"></view>
          </view>
          <view class="category-body">
            <view class="category-row">
              <text class="category-title">{{ entry.title }}</text>
              <text class="category-time">{{ formatTime(entry.createTime) }}</text>
            </view>
            <text class="category-desc">{{ entry.content }}</text>
          </view>
          <view class="category-right"><text class="category-arrow">›</text></view>
        </view>
        <view v-if="examEntries.length === 0" class="empty-state compact-empty">
          <text class="empty-title">暂无题库消息</text>
          <text class="empty-desc">后台题库任务完成后会在这里提醒你</text>
        </view>
      </view>

      <view v-else class="empty-state">
        <view class="empty-icon"></view>
        <text class="empty-title">暂无{{ activeModuleLabel }}消息</text>
        <text class="empty-desc">该模块消息入口已预留，后续接入后会显示提醒</text>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { getAppMessages, getAppMessageUnreadCount, markAppMessageRead } from '@/api/message'
import { refreshMessageState } from '@/utils/messageStore'

export default {
  components: { NavBar },
  data() {
    return {
      loading: false,
      messages: [],
      unreadCount: 0,
      examUnreadCount: 0,
      activeModule: 'LOST_FOUND',
      modules: [
        { type: 'LOST_FOUND', label: '旧物交易' },
        { type: 'FORUM', label: '论坛' },
        { type: 'EXAM', label: '题库' },
        { type: 'MEETING', label: '会议' },
        { type: 'LEARNING', label: '学习' }
      ]
    }
  },
  computed: {
    activeModuleLabel() {
      const item = this.modules.find((module) => module.type === this.activeModule)
      return item ? item.label : '消息'
    },
    lostFoundMessages() {
      return this.messages.filter((item) => item.moduleType === 'LOST_FOUND')
    },
    chatMessages() {
      return this.lostFoundMessages.filter((item) => item.eventType === 'CHAT_MESSAGE')
    },
    tradeMessages() {
      return this.lostFoundMessages.filter((item) => this.isTradeEvent(item.eventType))
    },
    systemMessages() {
      return this.lostFoundMessages.filter((item) => !this.isTradeEvent(item.eventType) && item.eventType !== 'CHAT_MESSAGE')
    },
    examMessages() {
      return this.messages.filter((item) => item.moduleType === 'EXAM')
    },
    examEntries() {
      return this.examMessages
    },
    lostFoundEntries() {
      const chatUnread = this.countUnread(this.chatMessages)
      const tradeUnread = this.countUnread(this.tradeMessages)
      const systemUnread = this.countUnread(this.systemMessages)
      return [
        {
          type: 'chat',
          title: '聊天消息',
          desc: chatUnread > 0 ? `你有 ${chatUnread} 条新的用户来信` : '查看旧物交易中的用户来信',
          unreadCount: chatUnread,
          latestTime: this.latestTime(this.chatMessages),
          timeText: this.formatTime(this.latestTime(this.chatMessages)),
          iconClass: 'chat-icon',
          url: '/subpackage_message/lostFoundChatMessages/lostFoundChatMessages'
        },
        {
          type: 'trade',
          title: '交易提醒',
          desc: tradeUnread > 0 ? `你有 ${tradeUnread} 条交易状态提醒` : '购买意向、交易确认、交易完成',
          unreadCount: tradeUnread,
          latestTime: this.latestTime(this.tradeMessages),
          timeText: this.formatTime(this.latestTime(this.tradeMessages)),
          iconClass: 'trade-icon',
          url: '/subpackage_lostfound/marketTradeNotifications/marketTradeNotifications'
        },
        {
          type: 'system',
          title: '其他提醒',
          desc: systemUnread > 0 ? `你有 ${systemUnread} 条系统提醒` : '联系方式交换等辅助提醒',
          unreadCount: systemUnread,
          latestTime: this.latestTime(this.systemMessages),
          timeText: this.formatTime(this.latestTime(this.systemMessages)),
          iconClass: 'system-icon',
          disabled: this.systemMessages.length === 0
        }
      ]
    }
  },
  async onLoad() {
    await this.loadMessages()
  },
  async onShow() {
    await this.loadMessages()
  },
  methods: {
    async loadMessages() {
      try {
        this.loading = true
        const res = await getAppMessages({ current: 1, size: 100 })
        this.messages = Array.isArray(res?.data?.records) ? res.data.records : []
        await this.loadUnreadCount()
      } catch (e) {
        console.error('加载消息中心失败', e)
      } finally {
        this.loading = false
      }
    },
    async loadUnreadCount() {
      try {
        const res = await getAppMessageUnreadCount()
        this.unreadCount = Number(res?.data?.lostFound || 0)
        this.examUnreadCount = Number(res?.data?.exam || 0)
      } catch (e) {
        console.error('加载未读数量失败', e)
      }
    },
    async openCategory(entry) {
      if (!entry || entry.disabled) {
        uni.showToast({ title: '暂无相关消息', icon: 'none' })
        return
      }
      if (entry.id && !entry.isRead) {
        try {
          await markAppMessageRead(entry.id)
          entry.isRead = true
          await this.loadUnreadCount()
          await refreshMessageState('exam-message-read')
        } catch (error) {
          console.warn('标记题库消息已读失败', error)
        }
      }
      const url = entry.url || this.messageTargetUrl(entry)
      if (!url) {
        uni.showToast({ title: '暂未接入详情页', icon: 'none' })
        return
      }
      uni.navigateTo({ url })
    },
    moduleUnread(type) {
      if (type === 'LOST_FOUND') return this.unreadCount
      if (type === 'EXAM') return this.examUnreadCount
      return 0
    },
    messageTargetUrl(message) {
      if (!message?.targetPage) return ''
      let params = {}
      try {
        params = message.targetParams ? JSON.parse(message.targetParams) : {}
      } catch (error) {
        params = {}
      }
      const query = Object.entries(params)
        .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
        .join('&')
      return query ? `${message.targetPage}?${query}` : message.targetPage
    },
    countUnread(list) {
      return list.filter((item) => !item.isRead).length
    },
    latestTime(list) {
      return list.reduce((latest, item) => {
        const current = this.timeValue(item.createTime)
        return current > latest ? current : latest
      }, 0)
    },
    isTradeEvent(type) {
      return ['TRADE_INTENT', 'TRADE_CONFIRM', 'TRADE_COMPLETE', 'TRADE_CANCEL'].includes(type)
    },
    formatCount(value) {
      const count = Number(value || 0)
      return count > 99 ? '99+' : count
    },
    formatTime(value) {
      if (!value) return ''
      const time = typeof value === 'number' ? new Date(value) : new Date(String(value).replace(/-/g, '/'))
      if (Number.isNaN(time.getTime())) return ''
      const diff = Date.now() - time.getTime()
      const minute = 60 * 1000
      const hour = 60 * minute
      const day = 24 * hour
      if (diff < minute) return '刚刚'
      if (diff < hour) return `${Math.floor(diff / minute)}分钟前`
      if (diff < day) return `${Math.floor(diff / hour)}小时前`
      const month = `${time.getMonth() + 1}`.padStart(2, '0')
      const date = `${time.getDate()}`.padStart(2, '0')
      return `${month}-${date}`
    },
    timeValue(value) {
      if (!value) return 0
      const time = new Date(String(value).replace(/-/g, '/'))
      return Number.isNaN(time.getTime()) ? 0 : time.getTime()
    }
  }
}
</script>

<style lang="scss" scoped>
.message-center-page {
  min-height: 100vh;
  background: #F5F6F8;
}

.message-scroll {
  height: calc(100vh - 88rpx - var(--status-bar-height));
  box-sizing: border-box;
  padding: 24rpx 24rpx 48rpx;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.message-scroll::-webkit-scrollbar {
  width: 0;
  height: 0;
  display: none;
}

.module-tabs {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 28rpx;
  padding: 8rpx;
  border-radius: 18rpx;
  background: #FFFFFF;
  box-sizing: border-box;
}

.module-tab {
  position: relative;
  flex: 1;
  height: 64rpx;
  border-radius: 14rpx;
  color: #6E7A86;
  font-size: 24rpx;
  font-weight: 800;
  display: flex;
  align-items: center;
  justify-content: center;
  white-space: nowrap;
}

.module-tab.active {
  background: #EEF4F8;
  color: #1D1D1F;
}

.module-badge {
  position: absolute;
  top: 6rpx;
  right: 8rpx;
  min-width: 28rpx;
  height: 28rpx;
  padding: 0 8rpx;
  border-radius: 999rpx;
  background: #D95D5D;
  color: #FFFFFF;
  font-size: 18rpx;
  font-weight: 800;
  line-height: 28rpx;
  text-align: center;
  box-sizing: border-box;
}

.section-head {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 18rpx;
  padding: 0 4rpx;
}

.section-mark {
  width: 8rpx;
  height: 32rpx;
  border-radius: 999rpx;
  background: #7F9AB6;
}

.section-title {
  color: #1D1D1F;
  font-size: 30rpx;
  font-weight: 800;
}

.category-list {
  overflow: hidden;
  border-radius: 16rpx;
  background: #FFFFFF;
}

.category-item {
  position: relative;
  display: flex;
  align-items: center;
  min-height: 132rpx;
  padding: 24rpx;
  box-sizing: border-box;
  background: #FFFFFF;
}

.category-item::after {
  content: '';
  position: absolute;
  left: 126rpx;
  right: 0;
  bottom: 0;
  height: 1rpx;
  background: #EEF1F4;
}

.category-item:last-child::after {
  display: none;
}

.category-item:active {
  background: #F7F9FB;
}

.category-item.disabled {
  opacity: 0.68;
}

.category-icon {
  position: relative;
  width: 78rpx;
  height: 78rpx;
  margin-right: 22rpx;
  border-radius: 18rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.category-icon-img {
  width: 38rpx;
  height: 38rpx;
}

.chat-icon {
  background: #EEF4F8;
}

.trade-icon {
  background: #F0F5EF;
}

.system-icon {
  background: #F4F1EA;
}

.exam-icon {
  background: #EEF0FF;
}

.unread-dot {
  position: absolute;
  top: -4rpx;
  right: -4rpx;
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  background: #D95D5D;
  border: 4rpx solid #FFFFFF;
}

.category-body {
  flex: 1;
  min-width: 0;
}

.category-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  margin-bottom: 8rpx;
}

.category-title {
  flex: 1;
  min-width: 0;
  color: #202326;
  font-size: 29rpx;
  font-weight: 800;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.category-time {
  color: #9AA5AF;
  font-size: 23rpx;
  flex-shrink: 0;
}

.category-desc {
  display: block;
  color: #687481;
  font-size: 25rpx;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.category-right {
  display: flex;
  align-items: center;
  gap: 10rpx;
  margin-left: 16rpx;
  flex-shrink: 0;
}

.category-count {
  min-width: 32rpx;
  height: 32rpx;
  padding: 0 9rpx;
  border-radius: 999rpx;
  background: #D95D5D;
  color: #FFFFFF;
  font-size: 20rpx;
  font-weight: 800;
  line-height: 32rpx;
  text-align: center;
  box-sizing: border-box;
}

.category-arrow {
  color: #B8C1CC;
  font-size: 36rpx;
  line-height: 1;
  flex-shrink: 0;
}

.empty-state {
  min-height: 360rpx;
  border-radius: 16rpx;
  background: #FFFFFF;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.compact-empty {
  min-height: 260rpx;
}

.empty-icon {
  width: 84rpx;
  height: 84rpx;
  margin-bottom: 18rpx;
  border-radius: 50%;
  background: #EEF4F8;
  position: relative;
}

.empty-icon::before,
.empty-icon::after {
  content: '';
  position: absolute;
  background: #8FA5BC;
}

.empty-icon::before {
  left: 22rpx;
  top: 26rpx;
  width: 40rpx;
  height: 28rpx;
  border-radius: 8rpx;
  background: transparent;
  border: 4rpx solid #8FA5BC;
}

.empty-icon::after {
  left: 34rpx;
  top: 52rpx;
  width: 18rpx;
  height: 4rpx;
  border-radius: 999rpx;
}

.empty-title {
  color: #30363D;
  font-size: 28rpx;
  font-weight: 800;
}

.empty-desc {
  margin-top: 8rpx;
  color: #8A96A3;
  font-size: 24rpx;
}
</style>
