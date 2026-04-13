<template>
  <view class="page-root">
    <view class="screen">
      <view class="container">
        <nav-bar :title="curChat ? curChat.otherName : '聊天'" :fixed="true" :placeholder="true" />

        <scroll-view scroll-y class="chat-body" :scroll-into-view="scrollBottom" scroll-with-animation @scroll="onChatScroll">
          <view v-for="m in chatMessages" :key="m.id" :id="'msg-' + m.id">
            <view v-if="m.type === 'sys'" class="msys">{{ m.content }}</view>
            <view v-else class="msg" :class="m.type">
              <view v-if="m.type === 's'" class="msg-content-s">
                <view class="msg-bubble-group">
                  <view class="mbub mbub-s">
                    <text>{{ m.content }}</text>
                  </view>
                  <view class="mtime mtime-s">{{ new Date(m.time).toLocaleTimeString([], {hour:'2-digit', minute:'2-digit'}) }}</view>
                </view>
                <view class="mava mava-s">我</view>
              </view>
              <view v-else class="msg-content-r">
                <view class="mava mava-r">{{ curChat ? curChat.otherName[0] : '' }}</view>
                <view class="msg-bubble-group">
                  <view class="mbub mbub-r">
                    <text>{{ m.content }}</text>
                  </view>
                  <view class="mtime mtime-r">{{ new Date(m.time).toLocaleTimeString([], {hour:'2-digit', minute:'2-digit'}) }}</view>
                </view>
              </view>
            </view>
          </view>

          <view v-if="exchangeStatus.status === 'none'" class="excard-new">
            <view class="excard-new-title">想要进一步沟通？</view>
            <view class="excard-new-desc">交换微信后可以更方便联系</view>
            <button class="excard-new-btn" @click="reqExchange">申请交换微信</button>
          </view>
          <view v-else-if="exchangeStatus.status === 'pending'" class="excard-new">
            <view class="excard-new-title">等待对方同意</view>
            <view class="excard-new-desc">对方同意后互相显示微信号</view>
            <button class="excard-new-btn" disabled>等待中...</button>
          </view>

          <view
            v-if="exchangeStatus.status === 'done' && !isNearBottom"
            class="revealed-new revealed-flow"
          >
            <view class="rev-row-new">
              <view class="rev-ava-new">我</view>
              <view class="rev-icon-new">⇄</view>
              <view class="rev-ava-new them">{{ curChat ? curChat.otherName[0] : '' }}</view>
            </view>
            <view class="rev-phone-new">{{ curChat ? curChat.otherPhone : 'wx_******' }}</view>
            <view class="rev-label-new">对方微信号</view>
          </view>
        </scroll-view>

        <view
          v-if="exchangeStatus.status === 'done' && isNearBottom"
          class="revealed-new revealed-fixed"
        >
          <view class="rev-row-new">
            <view class="rev-ava-new">我</view>
            <view class="rev-icon-new">⇄</view>
            <view class="rev-ava-new them">{{ curChat ? curChat.otherName[0] : '' }}</view>
          </view>
          <view class="rev-phone-new">{{ curChat ? curChat.otherPhone : 'wx_******' }}</view>
          <view class="rev-label-new">对方微信号</view>
        </view>

        <view class="chat-footer-new">
          <view class="chat-ex-btn-new" :class="{ 'exchanged': exchangeStatus.status === 'done' }" :disabled="exchangeStatus.status !== 'none'" @click="reqExchange">
            {{ exchangeStatus.status === 'none' ? '交换微信' : (exchangeStatus.status === 'pending' ? '等待同意...' : '已交换') }}
          </view>
          <view class="chat-input-bar">
            <view class="chat-input-icon">🖼️</view>
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

const STORAGE_KEYS = {
  items: 'items',
  chats: 'chats',
  msgs: 'msgs',
  exStatus: 'exStatus'
}

export default {
  components: {
    NavBar
  },
  data() {
    return {
      itemId: null,
      curItem: null,
      curChat: null,
      messageInput: '',
      scrollBottom: '',
      isNearBottom: false
    }
  },
  computed: {
    chatMessages() {
      if (!this.curChat) return []
      const list = this.msgs[this.curChat.id] || []
      return list
    },
    exchangeStatus() {
      if (!this.curChat) return { status: 'none' }
      return this.exStatus[this.curChat.id] || { status: 'none' }
    }
  },
  onLoad(options) {
    this.itemId = options.itemId
    this.initChat()
  },
  methods: {
    initChat() {
      try {
        const items = uni.getStorageSync(STORAGE_KEYS.items) || []
        const item = items.find(i => String(i.id) === String(this.itemId))
        if (!item) {
          uni.showToast({ title: '商品不存在', icon: 'none' })
          setTimeout(() => uni.navigateBack(), 1500)
          return
        }
        this.curItem = item

        const chats = uni.getStorageSync(STORAGE_KEYS.chats) || []
        let chat = chats.find(c => c.itemId === item.id)

        if (!chat) {
          const firstMsgId = Date.now()
          chat = {
            id: 'c_' + firstMsgId,
            itemId: item.id,
            itemName: item.name,
            otherId: item.userId,
            otherName: item.userName,
            otherPhone: item.userPhone,
            otherAva: item.userAva,
            lastMsg: '你好',
            lastTime: firstMsgId,
            unread: 0
          }
          chats.unshift(chat)
          
          const msgs = uni.getStorageSync(STORAGE_KEYS.msgs) || {}
          msgs[chat.id] = [{
            id: firstMsgId,
            type: 's',
            content: '你好',
            time: firstMsgId
          }]
          
          const exStatus = uni.getStorageSync(STORAGE_KEYS.exStatus) || {}
          exStatus[chat.id] = { status: 'none' }
          
          uni.setStorageSync(STORAGE_KEYS.chats, chats)
          uni.setStorageSync(STORAGE_KEYS.msgs, msgs)
          uni.setStorageSync(STORAGE_KEYS.exStatus, exStatus)
        }

        this.curChat = chat
        chat.unread = 0
        uni.setStorageSync(STORAGE_KEYS.chats, chats)

        this.$nextTick(() => {
          const list = this.msgs[this.curChat.id] || []
          if (list.length) {
            this.scrollBottom = 'msg-' + list[list.length - 1].id
          }
          this.updateCardPosition()
        })
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
    sendMsg() {
      const c = this.messageInput.trim()
      if (!c || !this.curChat) return

      const msgId = Date.now()
      const msgs = uni.getStorageSync(STORAGE_KEYS.msgs) || {}
      const chats = uni.getStorageSync(STORAGE_KEYS.chats) || []

      if (!msgs[this.curChat.id]) {
        msgs[this.curChat.id] = []
      }

      msgs[this.curChat.id].push({
        id: msgId,
        type: 's',
        content: c,
        time: msgId
      })

      this.curChat.lastMsg = c
      this.curChat.lastTime = msgId
      this.messageInput = ''
      
      uni.setStorageSync(STORAGE_KEYS.msgs, msgs)
      uni.setStorageSync(STORAGE_KEYS.chats, chats)
      this.scrollBottom = 'msg-' + msgId

      this.$nextTick(() => {
        this.updateCardPosition()
      })

      setTimeout(() => {
        const replyId = Date.now()
        const msgs2 = uni.getStorageSync(STORAGE_KEYS.msgs) || {}
        const chats2 = uni.getStorageSync(STORAGE_KEYS.chats) || []
        
        if (!msgs2[this.curChat.id]) {
          msgs2[this.curChat.id] = []
        }

        msgs2[this.curChat.id].push({
          id: replyId,
          type: 'r',
          content: '在的，可以聊聊～',
          time: replyId
        })

        this.curChat.lastMsg = '在的，可以聊聊～'
        this.curChat.lastTime = replyId
        
        uni.setStorageSync(STORAGE_KEYS.msgs, msgs2)
        uni.setStorageSync(STORAGE_KEYS.chats, chats2)
        this.scrollBottom = 'msg-' + replyId

        this.$nextTick(() => {
          this.updateCardPosition()
        })
      }, 1500)
    },
    reqExchange() {
      if (!this.curChat) return
      const exStatus = uni.getStorageSync(STORAGE_KEYS.exStatus) || {}
      const msgs = uni.getStorageSync(STORAGE_KEYS.msgs) || {}
      
      if (exStatus[this.curChat.id]?.status !== 'none') return

      exStatus[this.curChat.id] = { status: 'pending' }
      uni.setStorageSync(STORAGE_KEYS.exStatus, exStatus)

      const sysId1 = Date.now()
      if (!msgs[this.curChat.id]) {
        msgs[this.curChat.id] = []
      }
      msgs[this.curChat.id].push({
        id: sysId1,
        type: 'sys',
        content: '你申请交换微信',
        time: sysId1
      })

      uni.setStorageSync(STORAGE_KEYS.msgs, msgs)
      this.scrollBottom = 'msg-' + sysId1
      uni.showToast({ title: '已发送请求', icon: 'none' })

      setTimeout(() => {
        const exStatus2 = uni.getStorageSync(STORAGE_KEYS.exStatus) || {}
        const msgs2 = uni.getStorageSync(STORAGE_KEYS.msgs) || {}
        
        exStatus2[this.curChat.id] = { status: 'done' }
        uni.setStorageSync(STORAGE_KEYS.exStatus, exStatus2)

        const sysId2 = Date.now()
        if (!msgs2[this.curChat.id]) {
          msgs2[this.curChat.id] = []
        }
        msgs2[this.curChat.id].push({
          id: sysId2,
          type: 'sys',
          content: '对方同意交换微信',
          time: sysId2
        })

        uni.setStorageSync(STORAGE_KEYS.msgs, msgs2)
        this.scrollBottom = 'msg-' + sysId2
        uni.showToast({ title: '已交换微信', icon: 'none' })

        this.$nextTick(() => {
          this.updateCardPosition()
        })
      }, 2500)
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
