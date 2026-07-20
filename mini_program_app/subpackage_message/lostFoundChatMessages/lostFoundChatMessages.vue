<template>
  <view class="chat-message-page">
    <nav-bar title="聊天消息" />

    <scroll-view scroll-y class="page-scroll" :show-scrollbar="false">
      <view v-if="chatGroups.length > 0" class="chat-list">
        <view
          v-for="group in chatGroups"
          :key="group.groupKey"
          class="chat-item"
          :class="{ unread: group.unreadCount > 0 }"
          @click="openChat(group)"
        >
          <view class="avatar">
            <text>{{ avatarText(group.senderName) }}</text>
            <view v-if="group.unreadCount > 0" class="unread-dot"></view>
          </view>
          <view class="chat-body">
            <view class="chat-row">
              <text class="sender-name">{{ group.senderName }}</text>
              <text class="chat-time">{{ formatTime(group.latestTime) }}</text>
            </view>
            <text class="chat-preview">{{ group.preview }}</text>
          </view>
          <view class="chat-right">
            <text v-if="group.unreadCount > 0" class="chat-count">{{ formatCount(group.unreadCount) }}</text>
            <text class="chat-arrow">›</text>
          </view>
        </view>
      </view>

      <view v-else-if="!loading" class="empty-state">
        <view class="empty-icon"></view>
        <text class="empty-title">暂无聊天消息</text>
        <text class="empty-desc">旧物交易中的用户来信会出现在这里</text>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { getAppMessages, markAppMessageRead } from '@/api/message'
import { refreshMessageState } from '@/utils/messageStore'

export default {
  components: { NavBar },
  data() {
    return {
      loading: false,
      messages: []
    }
  },
  computed: {
    chatGroups() {
      const map = new Map()
      this.messages
        .filter((item) => item.moduleType === 'LOST_FOUND' && item.eventType === 'CHAT_MESSAGE')
        .forEach((message) => {
          const params = this.parseTargetParams(message.targetParams)
          const sessionId = params.sessionId || message.id
          const groupKey = `chat-${sessionId}`
          const existing = map.get(groupKey)
          const normalized = this.normalizeChatMessage(message, params)
          if (!existing) {
            map.set(groupKey, {
              groupKey,
              sessionId,
              targetPage: message.targetPage,
              targetParamsObject: params,
              senderName: normalized.senderName,
              preview: normalized.preview,
              latestTime: normalized.timeValue,
              unreadCount: normalized.isRead ? 0 : 1,
              unreadIds: normalized.isRead ? [] : [message.id]
            })
            return
          }
          if (normalized.timeValue >= existing.latestTime) {
            existing.senderName = normalized.senderName
            existing.preview = normalized.preview
            existing.latestTime = normalized.timeValue
            existing.targetPage = message.targetPage
            existing.targetParamsObject = params
          }
          if (!normalized.isRead) {
            existing.unreadCount += 1
            existing.unreadIds.push(message.id)
          }
        })
      return Array.from(map.values()).sort((a, b) => b.latestTime - a.latestTime)
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
      } catch (e) {
        console.error('加载聊天消息失败', e)
        uni.showToast({ title: '消息加载失败', icon: 'none' })
      } finally {
        this.loading = false
      }
    },
    normalizeChatMessage(message, params) {
      return {
        senderName: this.cleanText(message.title || '对方用户'),
        preview: this.cleanText(message.content || '新消息'),
        timeValue: this.timeValue(message.createTime),
        isRead: Boolean(message.isRead),
        params
      }
    },
    async openChat(group) {
      if (!group) return
      try {
        if (group.unreadIds.length > 0) {
          await Promise.all(group.unreadIds.map((id) => markAppMessageRead(id)))
          await refreshMessageState('chat-message-group-read')
        }
      } catch (e) {
        console.error('标记聊天消息已读失败', e)
      }
      const url = this.buildTargetUrl(group)
      if (!url) {
        uni.showToast({ title: '聊天暂不可跳转', icon: 'none' })
        return
      }
      uni.navigateTo({ url })
    },
    buildTargetUrl(group) {
      if (!group.targetPage) return ''
      const params = group.targetParamsObject || {}
      const query = Object.keys(params)
        .filter((key) => params[key] !== undefined && params[key] !== null && params[key] !== '')
        .map((key) => `${encodeURIComponent(key)}=${encodeURIComponent(params[key])}`)
        .join('&')
      return query ? `${group.targetPage}?${query}` : group.targetPage
    },
    parseTargetParams(value) {
      if (!value) return {}
      if (typeof value === 'object') return value
      try {
        return JSON.parse(value)
      } catch (e) {
        return {}
      }
    },
    cleanText(value) {
      const text = value == null ? '' : String(value)
      const repaired = this.repairMojibake(text)
      return repaired || text
    },
    repairMojibake(value) {
      if (!value || !/[ÃÂÄÅÆÇÈÉÊËÌÍÎÏÐÑÒÓÔÕÖØÙÚÛÜÝÞßàáâãäåæçèéêëìíîïðñòóôõöøùúûüýþÿ]/.test(value)) {
        return value
      }
      try {
        const decoded = decodeURIComponent(escape(value))
        return decoded && decoded !== value ? decoded : value
      } catch (e) {
        return value
      }
    },
    avatarText(name) {
      const text = this.cleanText(name || '用')
      return text.slice(0, 1)
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
.chat-message-page {
  min-height: 100vh;
  background: #F5F6F8;
}

.page-scroll {
  height: calc(100vh - 88rpx - var(--status-bar-height));
  padding: 24rpx 24rpx 48rpx;
  box-sizing: border-box;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.page-scroll::-webkit-scrollbar {
  width: 0;
  height: 0;
  display: none;
}

.chat-list {
  overflow: hidden;
  border-radius: 16rpx;
  background: #FFFFFF;
}

.chat-item {
  position: relative;
  display: flex;
  align-items: center;
  min-height: 132rpx;
  padding: 24rpx;
  box-sizing: border-box;
  background: #FFFFFF;
}

.chat-item::after {
  content: '';
  position: absolute;
  left: 126rpx;
  right: 0;
  bottom: 0;
  height: 1rpx;
  background: #EEF1F4;
}

.chat-item:last-child::after {
  display: none;
}

.chat-item:active {
  background: #F7F9FB;
}

.avatar {
  position: relative;
  width: 78rpx;
  height: 78rpx;
  margin-right: 22rpx;
  border-radius: 18rpx;
  background: #EEF4F8;
  color: #42576B;
  font-size: 28rpx;
  font-weight: 800;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
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

.chat-body {
  flex: 1;
  min-width: 0;
}

.chat-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  margin-bottom: 8rpx;
}

.sender-name {
  flex: 1;
  min-width: 0;
  color: #202326;
  font-size: 29rpx;
  font-weight: 800;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-time {
  color: #9AA5AF;
  font-size: 23rpx;
  flex-shrink: 0;
}

.chat-preview {
  display: block;
  color: #687481;
  font-size: 25rpx;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-right {
  display: flex;
  align-items: center;
  gap: 10rpx;
  margin-left: 16rpx;
  flex-shrink: 0;
}

.chat-count {
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

.chat-arrow {
  color: #B8C1CC;
  font-size: 36rpx;
  line-height: 1;
}

.empty-state {
  min-height: 420rpx;
  border-radius: 16rpx;
  background: #FFFFFF;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
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
