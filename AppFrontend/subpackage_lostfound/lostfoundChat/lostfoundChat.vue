<template>
  <view class="page-root">
    <view class="screen">
      <view class="container">
        <nav-bar :title="curChat ? curChat.otherName : '聊天'" :fixed="true" :placeholder="true" />

        <scroll-view scroll-y class="chat-body" :scroll-into-view="scrollBottom" scroll-with-animation @scroll="onChatScroll">
          <view v-for="m in chatMessages" :key="m.id" :id="'msg-' + m.id">
            <view class="msg" :class="m.type">
              <view v-if="m.type === 's'" class="msg-content-s">
                <view class="msg-bubble-group">
                  <view class="mbub mbub-s">
                    <text>{{ m.content }}</text>
                  </view>
                  <view class="mtime mtime-s">{{ formatClock(m.time) }}</view>
                </view>
                <view class="mava mava-s">我</view>
              </view>
              <view v-else class="msg-content-r">
                <view class="mava mava-r">{{ curChat ? curChat.otherName[0] : '' }}</view>
                <view class="msg-bubble-group">
                  <view class="mbub mbub-r">
                    <text>{{ m.content }}</text>
                  </view>
                  <view class="mtime mtime-r">{{ formatClock(m.time) }}</view>
                </view>
              </view>
            </view>
          </view>
        </scroll-view>

        <view class="chat-footer-new">
          <view class="chat-input-bar">
            <input v-model="messageInput" class="chat-input-new" placeholder="输入消息..." @confirm="sendMsg" />
            <view class="chat-send-btn" @click="sendMsg">
              <text>➤</text>
            </view>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import {
  createOrGetChatSession,
  getChatMessages,
  getChatSessions,
  sendChatMessage
} from '@/api/secondhand'

function normalizeSession(item) {
  return {
    id: item.sessionId,
    itemId: item.itemId,
    otherName: item.otherUsername || item.sellerName || '用户',
    lastMsg: item.lastMessage || '',
    lastTime: item.lastTime || ''
  }
}

function normalizeMessage(item) {
  return {
    id: item.id,
    type: item.isMine ? 's' : 'r',
    content: item.content,
    time: item.createTime || ''
  }
}

export default {
  components: {
    NavBar
  },
  data() {
    return {
      itemId: null,
      sessionId: null,
      curChat: null,
      messages: [],
      messageInput: '',
      scrollBottom: '',
      isNearBottom: false
    }
  },
  computed: {
    chatMessages() {
      return this.messages
    }
  },
  async onLoad(options) {
    this.itemId = options.itemId
    this.sessionId = options.sessionId
    await this.initChat()
  },
  methods: {
    async initChat() {
      try {
        if (!this.sessionId && this.itemId) {
          const sessionRes = await createOrGetChatSession(this.itemId)
          this.sessionId = sessionRes?.data?.sessionId
        }
        if (!this.sessionId) {
          uni.showToast({ title: '会话不存在', icon: 'none' })
          return
        }
        const sessionListRes = await getChatSessions({ current: 1, size: 100 })
        const sessions = Array.isArray(sessionListRes?.data?.records) ? sessionListRes.data.records : []
        const matched = sessions.find((item) => Number(item.sessionId) === Number(this.sessionId))
        this.curChat = matched ? normalizeSession(matched) : { id: this.sessionId, otherName: '聊天' }
        await this.loadMessages()
      } catch (e) {
        console.error('初始化聊天失败', e)
      }
    },
    onChatScroll(e) {
      const { scrollTop, scrollHeight, clientHeight } = e.detail
      const distanceToBottom = scrollHeight - scrollTop - clientHeight
      this.isNearBottom = distanceToBottom <= 160
    },
    updateCardPosition() {
      this.$nextTick(() => {
        uni.createSelectorQuery()
          .in(this)
          .select('.chat-body')
          .boundingClientRect()
          .select('.chat-body >>> .uni-scroll-view-content')
          .boundingClientRect()
          .exec((res) => {
            if (!res || res.length < 2 || !res[0] || !res[1]) return
            const bodyRect = res[0]
            const contentRect = res[1]
            if (contentRect.height <= bodyRect.height) {
              this.isNearBottom = false
            } else {
              this.isNearBottom = true
            }
          })
      })
    },
    async loadMessages() {
      try {
        const res = await getChatMessages(this.sessionId, { current: 1, size: 100 })
        const records = Array.isArray(res?.data?.records) ? res.data.records : []
        this.messages = records.map(normalizeMessage)
        this.$nextTick(() => {
          if (this.messages.length) {
            this.scrollBottom = `msg-${this.messages[this.messages.length - 1].id}`
          }
          this.updateCardPosition()
        })
      } catch (error) {
        console.error('加载消息失败', error)
      }
    },
    formatClock(value) {
      if (!value) return ''
      const date = new Date(value.replace(/-/g, '/'))
      return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    },
    async sendMsg() {
      const c = this.messageInput.trim()
      if (!c || !this.sessionId) return
      try {
        await sendChatMessage({
          sessionId: Number(this.sessionId),
          content: c,
          messageType: 1
        })
        this.messageInput = ''
        await this.loadMessages()
      } catch (error) {
        console.error('发送消息失败', error)
      }
    },
    reqExchange() {
      uni.showToast({ title: '该功能暂未接入后端', icon: 'none' })
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

.chat-body {
  height: calc(100vh - 240rpx);
  padding: 32rpx 0 0rpx;
  box-sizing: border-box;
}

.msg {
  margin-bottom: 32rpx;
  display: flex;
}

.msg-content-s {
  display: flex;
  flex-direction: row;
  align-items: flex-start;
  gap: 16rpx;
  margin-left: auto;
  justify-content: flex-end;
  padding-right: 64rpx;
}

.msg-content-r {
  display: flex;
  align-items: flex-start;
  gap: 16rpx;
  margin-right: auto;
}

.msg-bubble-group {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  max-width: 520rpx;
}

.msg-content-s .msg-bubble-group {
  align-items: flex-end;
}

.mava {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  background: #7ba8d4;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26rpx;
  font-weight: 700;
  flex-shrink: 0;
}

.mava-r {
  background: #7ba8d4;
}

.mava-s {
  background: #7ba8d4;
}

.mbub {
  padding: 20rpx 28rpx;
  border-radius: 24rpx;
  font-size: 28rpx;
  max-width: 100%;
  word-break: break-all;
  line-height: 1.5;
}

.mbub-s {
  background: #7ba8d4;
  color: #fff;
}

.mbub-r {
  background: #fff;
  color: rgba(0, 0, 0, 0.85);
}

.mtime {
  font-size: 20rpx;
  color: rgba(0, 0, 0, 0.35);
  margin-top: 8rpx;
  padding: 0 4rpx;
}

.msys {
  text-align: center;
  font-size: 24rpx;
  color: rgba(0, 0, 0, 0.4);
  margin: 32rpx 0;
}

.excard-new {
  margin: 32rpx 0;
  padding: 40rpx;
  background: rgba(123, 168, 212, 0.1);
  border-radius: 24rpx;
  text-align: center;
}

.excard-new-title {
  font-size: 30rpx;
  font-weight: 700;
  color: rgba(0, 0, 0, 0.85);
  margin-bottom: 12rpx;
}

.excard-new-desc {
  font-size: 24rpx;
  color: rgba(0, 0, 0, 0.5);
  margin-bottom: 24rpx;
}

.excard-new-btn {
  padding: 20rpx 48rpx;
  border-radius: 999rpx;
  background: #7ba8d4;
  color: #fff;
  font-size: 28rpx;
  font-weight: 700;
  border: none;
}

.revealed-new {
  padding: 40rpx 32rpx;
  background: #fff;
  border-radius: 24rpx;
  text-align: center;
  width: calc(100% - 96rpx);
  max-width: 500rpx;
  box-sizing: border-box;
  box-shadow: 0 8rpx 24rpx rgba(0, 0, 0, 0.06);
}

.revealed-flow {
  position: relative;
  margin: 20rpx auto 0;
}

.revealed-fixed {
  position: fixed;
  left: 50%;
  transform: translateX(-50%);
  bottom: 24rpx;
  z-index: 9;
  box-shadow: 0 6rpx 20rpx rgba(0, 0, 0, 0.08);
}

.rev-row-new {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 28rpx;
  margin-bottom: 28rpx;
}

.rev-ava-new {
  width: 80rpx;
  height: 80rpx;
  border-radius: 50%;
  background: #7ba8d4;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 30rpx;
  font-weight: 700;
}

.rev-ava-new.them {
  background: rgba(0, 0, 0, 0.5);
}

.rev-icon-new {
  font-size: 36rpx;
  color: #7ba8d4;
}

.rev-phone-new {
  font-size: 36rpx;
  font-weight: 800;
  color: #5c8ab8;
  margin-bottom: 8rpx;
}

.rev-label-new {
  font-size: 24rpx;
  color: rgba(0, 0, 0, 0.5);
}

.chat-footer-new {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  background: rgba(232, 240, 248, 0.98);
  padding: 16rpx 32rpx 24rpx;
  border-top: none;
  z-index: 10;
}

.chat-ex-btn-new {
  position: absolute;
  top: -68rpx;
  left: 50%;
  transform: translateX(-50%);
  padding: 12rpx 32rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.95);
  font-size: 24rpx;
  font-weight: 700;
  color: #7ba8d4;
  border: 2rpx solid rgba(123, 168, 212, 0.3);
  text-align: center;
  box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.08);
  backdrop-filter: blur(10rpx);
  z-index: 10;
  white-space: nowrap;
}

.chat-ex-btn-new.exchanged {
  color: rgba(123, 168, 212, 0.5);
  border-color: rgba(123, 168, 212, 0.15);
  background: rgba(255, 255, 255, 0.6);
}

.chat-input-bar {
  display: flex;
  align-items: center;
  gap: 16rpx;
  background: rgba(255, 255, 255, 0.8);
  border-radius: 999rpx;
  padding: 8rpx 8rpx 8rpx 24rpx;
}

.chat-input-icon {
  font-size: 32rpx;
  opacity: 0.5;
}

.chat-input-new {
  flex: 1;
  height: 72rpx;
  font-size: 28rpx;
  background: transparent;
}

.chat-send-btn {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  background: #7ba8d4;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  flex-shrink: 0;
}
</style>
