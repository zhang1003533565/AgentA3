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

      <view v-else-if="activeModule === 'FORUM'" class="category-list">
        <view
          v-for="entry in forumEntries"
          :key="entry.type"
          class="category-item"
          @click="openForumCategory(entry)"
        >
          <view class="category-icon" :class="entry.iconClass">
            <image class="category-icon-img" :src="entry.icon" mode="aspectFit" />
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
        <view v-if="forumEntries.length === 0" class="empty-state compact-empty">
          <text class="empty-title">暂无论坛消息</text>
          <text class="empty-desc">帖子收到评论或点赞后会在这里提醒你</text>
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
import { getAppMessages, getAppMessageUnreadCount, markAppMessageRead, markAppMessagesReadByCategory } from '@/api/message'
import { refreshMessageState } from '@/utils/messageStore'
import { getForumMessageUnread } from '@/api/forum.js'
import { markForumCategoryRead, isForumCategoryRead } from '@/utils/storage.js'

const LOST_FOUND_TRADE_EVENTS = ['TRADE_INTENT', 'TRADE_CONFIRM', 'TRADE_COMPLETE', 'TRADE_CANCEL']

export default {
  components: { NavBar },
  data() {
    return {
      loading: false,
      messages: [],
      unreadCount: 0,
      examUnreadCount: 0,
      activeModule: 'LOST_FOUND',
      forumCommentCount: 0,
      forumLikeCount: 0,
      forumSystemCount: 0,
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
    forumEntries() {
      return [
        {
          type: 'comment',
          title: '收到的评论',
          desc: this.forumCommentCount > 0 ? `你有 ${this.forumCommentCount} 条新的评论` : '查看帖子收到的评论与回复',
          unreadCount: this.forumCommentCount,
          iconClass: 'forum-comment-icon',
          icon: '/static/icons/line/message-circle.svg',
          timeText: '',
          url: '/subpackage_message/messageCategory/messageCategory?type=comment'
        },
        {
          type: 'like',
          title: '收到的点赞',
          desc: this.forumLikeCount > 0 ? `你有 ${this.forumLikeCount} 个新的点赞` : '查看帖子收到的点赞',
          unreadCount: this.forumLikeCount,
          iconClass: 'forum-like-icon',
          icon: '/static/icons/line/thumb-up.svg',
          timeText: '',
          url: '/subpackage_message/messageCategory/messageCategory?type=like'
        },
        {
          type: 'system',
          title: '系统通知',
          desc: this.forumSystemCount > 0 ? `你有 ${this.forumSystemCount} 条系统通知` : '查看平台维护与公告',
          unreadCount: this.forumSystemCount,
          iconClass: 'forum-system-icon',
          icon: '/static/icons/line/award.svg',
          timeText: '',
          url: '/subpackage_message/messageCategory/messageCategory?type=system'
        }
      ]
    },
    lostFoundEntries() {
      const chatUnread = this.countUnread(this.chatMessages)
      const tradeUnread = this.countUnread(this.tradeMessages)
      const systemUnread = this.countUnread(this.systemMessages)
      const latestSystemMessage = this.latestMessage(this.systemMessages)
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
          message: latestSystemMessage,
          url: this.messageTargetUrl(latestSystemMessage),
          disabled: this.systemMessages.length === 0
        }
      ]
    }
  },
  async onLoad() {
    await this.loadMessages()
    await this.loadForumMessages()
  },
  async onShow() {
    await this.loadMessages()
    await this.loadForumMessages()
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
    // 统计论坛三类消息的未读数（聚合接口一次返回，避免 N+1 查询）
    async loadForumMessages() {
      let stats = { comment: 0, like: 0, system: 0 }
      try {
        const res = await getForumMessageUnread()
        stats = {
          comment: Number(res?.data?.commentCount || 0),
          like: Number(res?.data?.likeCount || 0),
          system: Number(res?.data?.systemCount || 0)
        }
      } catch (error) {
        stats = { comment: 0, like: 0, system: 0 }
      }
      // 点赞未读数来自后端真实未读消息（已读后归零，新点赞重新出现红点）
      this.forumLikeCount = stats.like
      // 评论/系统通知暂无后端消息记录，沿用本地已读标记兼容
      this.forumCommentCount = isForumCategoryRead('comment') ? 0 : stats.comment
      this.forumSystemCount = isForumCategoryRead('system') ? 0 : stats.system
    },
    openForumCategory(entry) {
      if (entry.type === 'comment') this.forumCommentCount = 0
      if (entry.type === 'like') {
        this.forumLikeCount = 0
        // 点赞消息基于后端 app_message，标记后端已读，返回后 onShow 重新统计不再显示红点
        markAppMessagesReadByCategory({ moduleType: 'FORUM', eventTypes: ['POST_LIKE'] }).catch(() => {})
      }
      if (entry.type === 'system') this.forumSystemCount = 0
      // 点击即标记该分类已读：立即持久化，返回后 onShow 统计不再显示红点
      markForumCategoryRead(entry.type)
      uni.navigateTo({ url: entry.url })
    },
    async openCategory(entry) {
      if (!entry || entry.disabled) {
        uni.showToast({ title: '暂无相关消息', icon: 'none' })
        return
      }
      if (entry.type === 'trade' && entry.unreadCount > 0) {
        await this.markLostFoundTradeMessagesRead()
      }
      if (entry.type === 'system' && entry.unreadCount > 0) {
        await this.markLostFoundSystemMessagesRead()
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
    latestMessage(list) {
      return list.reduce((latest, item) => {
        return this.timeValue(item.createTime) > this.timeValue(latest?.createTime) ? item : latest
      }, null)
    },
    isTradeEvent(type) {
      return LOST_FOUND_TRADE_EVENTS.includes(type)
    },
    async markLostFoundTradeMessagesRead() {
      try {
        await markAppMessagesReadByCategory({
          moduleType: 'LOST_FOUND',
          eventTypes: LOST_FOUND_TRADE_EVENTS
        })
        this.messages = this.messages.map((item) => (
          item.moduleType === 'LOST_FOUND' && this.isTradeEvent(item.eventType)
            ? { ...item, isRead: true }
            : item
        ))
        await this.loadUnreadCount()
        await refreshMessageState('lost-found-trade-app-messages-read')
      } catch (error) {
        console.warn('批量标记旧物交易提醒已读失败', error)
      }
    },
    async markLostFoundSystemMessagesRead() {
      try {
        const eventTypes = [...new Set(this.systemMessages.map((item) => item.eventType).filter(Boolean))]
        if (!eventTypes.length) return
        await markAppMessagesReadByCategory({
          moduleType: 'LOST_FOUND',
          eventTypes
        })
        this.messages = this.messages.map((item) => (
          item.moduleType === 'LOST_FOUND' && eventTypes.includes(item.eventType)
            ? { ...item, isRead: true }
            : item
        ))
        await this.loadUnreadCount()
        await refreshMessageState('lost-found-system-app-messages-read')
      } catch (error) {
        console.warn('批量标记旧物其他提醒已读失败', error)
      }
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

.forum-comment-icon {
  background: #E8F5E9;
}

.forum-like-icon {
  background: #FDE8E8;
}

.forum-system-icon {
  background: #EEEEF2;
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
