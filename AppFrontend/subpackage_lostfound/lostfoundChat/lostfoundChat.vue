<template>
  <view class="page-root">
    <view class="screen">
      <view class="container">
        <nav-bar :title="chatTitle" :fixed="true" :placeholder="true" />

        <view v-if="curChat" class="product-card" @click="goProduct">
          <image v-if="curChat.itemImage" class="product-img" :src="curChat.itemImage" mode="aspectFill" />
          <view v-else class="product-img product-empty">{{ curChat.itemTitle ? curChat.itemTitle[0] : '物' }}</view>
          <view class="product-info">
            <view class="product-title">{{ curChat.itemTitle || '商品' }}</view>
            <view class="product-meta">
              <text class="product-price">¥{{ priceText(curChat.itemPrice) }}</text>
              <text class="item-status">{{ itemStatusText }}</text>
              <text v-if="tradeInfo" class="trade-status">{{ tradeStatusText }}</text>
            </view>
          </view>
        </view>

        <scroll-view scroll-y class="chat-body" :scroll-into-view="scrollBottom" scroll-with-animation>
          <view v-for="m in chatMessages" :key="m.id" :id="'msg-' + m.id">
            <view v-if="m.type === 'sys'" class="system-msg">
              <view v-if="m.tradeAction" class="trade-event-card">
                <view class="trade-event-title">{{ tradeActionTitle(m.tradeAction) }}</view>
                <view class="trade-event-desc">{{ tradeActionDesc(m.tradeAction, m.content) }}</view>
              </view>
              <text v-else>{{ m.content }}</text>
            </view>

            <view v-else-if="m.type === 'contact'" class="contact-msg">
              <view class="contact-card">
                <view class="contact-title">联系方式</view>
                <view v-for="line in contactLines(m.content)" :key="line" class="contact-line">{{ line }}</view>
              </view>
            </view>

            <view v-else class="msg" :class="m.type">
              <view v-if="m.type === 's'" class="msg-content-s">
                <view class="msg-bubble-group">
                  <view class="mbub mbub-s">
                    <image v-if="m.messageType === 2" class="chat-img" :src="m.content" mode="widthFix" @click="previewImage(m.content)" />
                    <text v-else>{{ m.content }}</text>
                  </view>
                  <view class="mtime mtime-s">{{ formatClock(m.time) }}</view>
                </view>
                <view class="mava mava-s">我</view>
              </view>
              <view v-else class="msg-content-r">
                <view class="mava mava-r">{{ otherInitial }}</view>
                <view class="msg-bubble-group">
                  <view class="mbub mbub-r">
                    <image v-if="m.messageType === 2" class="chat-img" :src="m.content" mode="widthFix" @click="previewImage(m.content)" />
                    <text v-else>{{ m.content }}</text>
                  </view>
                  <view class="mtime mtime-r">{{ formatClock(m.time) }}</view>
                </view>
              </view>
            </view>
          </view>
        </scroll-view>

        <view class="chat-footer-new">
          <view v-if="tradeActionButtons.length" class="quick-actions">
            <button
              v-for="action in tradeActionButtons"
              :key="action.type"
              class="quick-action-btn"
              :class="action.type"
              :disabled="acting"
              @click="runTradeAction(action.type)"
            >
              {{ action.label }}
            </button>
          </view>
          <view class="chat-input-bar">
            <view class="chat-image-btn" @click="sendImage">
              <text>＋</text>
            </view>
            <input v-model="messageInput" class="chat-input-new" placeholder="输入消息..." @confirm="sendMsg" />
            <view class="chat-send-btn" @click="sendMsg">
              <text>➤</text>
            </view>
          </view>
        </view>

        <view v-if="contactVisible" class="contact-mask" @click="closeContactDialog">
          <view class="contact-dialog" @click.stop>
            <view class="dialog-title">发送联系方式</view>
            <input v-model="contactForm.wechat" class="contact-input" placeholder="微信" />
            <input v-model="contactForm.phone" class="contact-input" placeholder="手机号" type="number" />
            <input v-model="contactForm.other" class="contact-input" placeholder="其他联系方式" />
            <view class="dialog-actions">
              <button class="dialog-btn ghost" @click="closeContactDialog">取消</button>
              <button class="dialog-btn primary" @click="submitContactInfo">发送</button>
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
  completeTradeRecord,
  confirmTradeRecord,
  createOrGetChatSession,
  getChatMessages,
  getChatSessions,
  getTradeRecords,
  reserveSecondhandItem,
  sendChatMessage
} from '@/api/secondhand'
import { getUploadErrorMessage, uploadImage } from '@/utils/upload'

function normalizeSession(item) {
  return {
    id: item.sessionId,
    itemId: item.itemId,
    itemTitle: item.itemTitle || '',
    itemImage: item.itemImage || '',
    itemPrice: item.itemPrice,
    itemStatus: item.itemStatus,
    itemStatusText: item.itemStatusText || '',
    otherUserId: item.otherUserId,
    otherName: item.otherUsername || item.sellerName || '用户',
    lastMsg: item.lastMessage || '',
    lastTime: item.lastTime || '',
    isSeller: item.isSeller,
    tradeId: item.tradeId,
    tradeStatus: item.tradeStatus,
    tradeStatusText: item.tradeStatusText || ''
  }
}

function normalizeMessage(item) {
  if (Number(item.messageType) === 0) {
    return {
      id: item.id,
      type: 'sys',
      tradeAction: item.tradeAction || '',
      content: item.content,
      time: item.createTime || ''
    }
  }
  if (Number(item.messageType) === 4) {
    return {
      id: item.id,
      type: 'contact',
      messageType: 4,
      tradeAction: item.tradeAction || 'CONTACT_SHARE',
      content: item.content,
      time: item.createTime || ''
    }
  }
  return {
    id: item.id,
    type: item.isMine ? 's' : 'r',
    messageType: Number(item.messageType || 1),
    content: item.content,
    time: item.createTime || ''
  }
}

const TRADE_TEXT = {
  WAIT_CONFIRM: '购买意向待确认',
  TRADING: '双方已确认线下交易',
  COMPLETED: '交易已完成',
  CANCELLED: '交易已取消'
}

export default {
  components: {
    NavBar
  },
  data() {
    return {
      itemId: null,
      targetUserId: null,
      sessionId: null,
      curChat: null,
      tradeInfo: null,
      messages: [],
      messageInput: '',
      scrollBottom: '',
      acting: false,
      uploadingImage: false,
      contactVisible: false,
      contactForm: { wechat: '', phone: '', other: '' }
    }
  },
  computed: {
    chatTitle() {
      return this.curChat ? this.curChat.otherName : '聊天'
    },
    otherInitial() {
      return this.curChat && this.curChat.otherName ? this.curChat.otherName[0] : ''
    },
    chatMessages() {
      return this.messages
    },
    itemStatusText() {
      if (this.curChat?.itemStatusText) return this.curChat.itemStatusText
      const status = Number(this.curChat?.itemStatus)
      if (status === 2) return '出售中'
      if (status === 5) return '交易中'
      if (status === 3) return '已完成'
      if (status === 4) return '已下架'
      return ''
    },
    tradeStatusText() {
      return this.tradeInfo ? (TRADE_TEXT[this.tradeInfo.status] || this.tradeInfo.statusText || '') : ''
    },
    tradeActionButtons() {
      if (!this.curChat) return []
      const itemStatus = Number(this.curChat.itemStatus)
      if (!this.tradeInfo) {
        if (!this.curChat.isSeller && itemStatus === 2) return [{ type: 'intent', label: '我想购买' }]
        return []
      }
      if (this.tradeInfo.status === 'WAIT_CONFIRM') {
        return this.tradeInfo.isSeller ? [{ type: 'confirm', label: '确认交易' }] : []
      }
      if (this.tradeInfo.status === 'TRADING') {
        const actions = [{ type: 'shareContact', label: '发送联系方式' }]
        if (this.tradeInfo.isSeller) actions.push({ type: 'complete', label: '标记交易完成' })
        return actions
      }
      return []
    }
  },
  async onLoad(options) {
    this.itemId = options.itemId
    this.targetUserId = options.targetUserId || options.buyerId || options.sellerId || null
    this.sessionId = options.sessionId
    await this.initChat()
  },
  methods: {
    async initChat() {
      try {
        if (!this.sessionId && this.itemId) {
          const sessionRes = await createOrGetChatSession(this.itemId, this.targetUserId)
          this.sessionId = sessionRes?.data?.sessionId
        }
        if (!this.sessionId) {
          uni.showToast({ title: '会话不存在', icon: 'none' })
          return
        }
        await this.loadSession()
        await this.loadTradeInfo()
        await this.loadMessages()
      } catch (e) {
        console.error('初始化聊天失败', e)
        uni.showToast({ title: '聊天初始化失败', icon: 'none' })
      }
    },
    async loadSession() {
      const sessionListRes = await getChatSessions({ current: 1, size: 100 })
      const sessions = Array.isArray(sessionListRes?.data?.records) ? sessionListRes.data.records : []
      const matched = sessions.find((item) => Number(item.sessionId) === Number(this.sessionId))
      this.curChat = matched ? normalizeSession(matched) : { id: this.sessionId, otherName: '聊天' }
    },
    async loadTradeInfo() {
      if (!this.curChat || !this.curChat.itemId) {
        this.tradeInfo = null
        return
      }
      try {
        const res = await getTradeRecords({ current: 1, size: 100 })
        const records = Array.isArray(res?.data?.records) ? res.data.records : []
        const matched = records.find((record) => {
          if (Number(record.itemId) !== Number(this.curChat.itemId)) return false
          if (!this.curChat.otherUserId) return true
          return Number(record.buyerId) === Number(this.curChat.otherUserId) ||
            Number(record.sellerId) === Number(this.curChat.otherUserId)
        })
        this.tradeInfo = matched
          ? {
              id: matched.id,
              status: matched.status,
              statusText: matched.statusText,
              isSeller: matched.isSeller
            }
          : null
      } catch (e) {
        console.warn('加载交易信息失败', e)
        this.tradeInfo = null
      }
    },
    async loadMessages() {
      try {
        const res = await getChatMessages(this.sessionId, { current: 1, size: 100 })
        const records = Array.isArray(res?.data?.records) ? res.data.records : []
        const sorted = [...records].sort((a, b) => {
          const ta = a.createTime || ''
          const tb = b.createTime || ''
          if (ta < tb) return -1
          if (ta > tb) return 1
          return (a.id || 0) - (b.id || 0)
        })
        this.messages = sorted.map(normalizeMessage)
        this.$nextTick(() => {
          if (this.messages.length) {
            this.scrollBottom = `msg-${this.messages[this.messages.length - 1].id}`
          }
        })
      } catch (error) {
        console.error('加载消息失败', error)
      }
    },
    priceText(value) {
      const price = Number(value)
      return Number.isFinite(price) ? price.toFixed(price % 1 === 0 ? 0 : 2) : '--'
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
    async sendImage() {
      if (!this.sessionId || this.uploadingImage) return
      try {
        const chooseRes = await new Promise((resolve, reject) => {
          uni.chooseImage({
            count: 1,
            sizeType: ['compressed'],
            sourceType: ['album', 'camera'],
            success: resolve,
            fail: reject
          })
        })
        const filePath = chooseRes.tempFilePaths && chooseRes.tempFilePaths[0]
        if (!filePath) return
        this.uploadingImage = true
        uni.showLoading({ title: '图片发送中...' })
        const url = await uploadImage(filePath)
        if (!url) throw new Error('图片上传失败')
        await sendChatMessage({
          sessionId: Number(this.sessionId),
          content: url,
          messageType: 2
        })
        await this.loadMessages()
      } catch (error) {
        if (error?.errMsg && String(error.errMsg).includes('cancel')) return
        console.error('发送图片失败', error)
        uni.showToast({ title: getUploadErrorMessage(error), icon: 'none' })
      } finally {
        this.uploadingImage = false
        uni.hideLoading()
      }
    },
    previewImage(url) {
      if (!url) return
      uni.previewImage({ urls: [url], current: url })
    },
    async runTradeAction(type) {
      if (this.acting) return
      if (type === 'intent') {
        await this.expressPurchaseIntent()
        return
      }
      if (type === 'shareContact') {
        await this.sendContactInfo()
        return
      }
      if (!this.tradeInfo || !this.tradeInfo.id) return
      const actions = {
        confirm: confirmTradeRecord,
        complete: completeTradeRecord
      }
      const action = actions[type]
      if (!action) return
      try {
        this.acting = true
        await action(this.tradeInfo.id)
        uni.showToast({ title: '状态已更新', icon: 'success' })
        await this.loadSession()
        await this.loadTradeInfo()
        await this.loadMessages()
      } catch (e) {
        console.error('交易操作失败', e)
        uni.showToast({ title: e?.data?.msg || e?.msg || '操作失败', icon: 'none' })
      } finally {
        this.acting = false
      }
    },
    async expressPurchaseIntent() {
      if (!this.curChat || !this.curChat.itemId || this.acting) return
      try {
        this.acting = true
        await reserveSecondhandItem(this.curChat.itemId)
        uni.showToast({ title: '购买意向已发送', icon: 'success' })
        await this.loadSession()
        await this.loadTradeInfo()
        await this.loadMessages()
      } catch (e) {
        console.error('发送购买意向失败', e)
        uni.showToast({ title: e?.data?.msg || e?.msg || '发送失败', icon: 'none' })
      } finally {
        this.acting = false
      }
    },
    async sendContactInfo() {
      if (!this.tradeInfo || this.tradeInfo.status !== 'TRADING') {
        uni.showToast({ title: '双方确认线下交易后才能发送联系方式', icon: 'none' })
        return
      }
      this.contactVisible = true
    },
    closeContactDialog() {
      this.contactVisible = false
    },
    async submitContactInfo() {
      try {
        const parts = []
        const wechat = this.contactForm.wechat.trim()
        const phone = this.contactForm.phone.trim()
        const other = this.contactForm.other.trim()
        if (wechat) parts.push(`微信：${wechat}`)
        if (phone) parts.push(`手机号：${phone}`)
        if (other) parts.push(`其他：${other}`)
        if (!parts.length) {
          uni.showToast({ title: '请至少填写一种联系方式', icon: 'none' })
          return
        }
        await sendChatMessage({
          sessionId: Number(this.sessionId),
          content: parts.join('\n'),
          messageType: 4
        })
        uni.showToast({ title: '已发送', icon: 'success' })
        this.contactVisible = false
        this.contactForm = { wechat: '', phone: '', other: '' }
        await this.loadMessages()
      } catch (e) {
        console.error('发送联系方式失败', e)
        uni.showToast({ title: e?.data?.msg || e?.msg || '发送失败', icon: 'none' })
      }
    },
    tradeActionTitle(action) {
      const map = {
        TRADE_INTENT: '购买意向',
        TRADE_CONFIRM: '已确认交易',
        TRADE_COMPLETE: '交易完成',
        TRADE_CANCEL: '交易取消'
      }
      return map[action] || '交易状态'
    },
    tradeActionDesc(action, content) {
      const map = {
        TRADE_INTENT: '你表达了购买意向，等待对方确认',
        TRADE_CONFIRM: '双方已确认线下交易，建议尽快约定时间地点',
        TRADE_COMPLETE: '该商品交易已完成',
        TRADE_CANCEL: '交易已取消'
      }
      return map[action] || content
    },
    contactLines(content) {
      return String(content || '').split(/\n/).filter(Boolean)
    },
    goProduct() {
      if (!this.curChat || !this.curChat.itemId) return
      uni.navigateTo({ url: `/subpackage_lostfound/lostfoundDetail/lostfoundDetail?id=${this.curChat.itemId}` })
    }
  }
}
</script>

<style scoped>
.page-root,
.screen {
  width: 100%;
  min-height: 100vh;
  background: #f0f5fa;
}

.container {
  width: 100%;
  max-width: 430px;
  min-height: 100vh;
  margin: 0 auto;
  padding: 0 16rpx;
  box-sizing: border-box;
  background: #e8f0f8;
  position: relative;
}

.product-card {
  display: flex;
  align-items: center;
  gap: 18rpx;
  margin: 16rpx 0 0;
  padding: 18rpx;
  border-radius: 18rpx;
  background: #fff;
  box-shadow: 0 4rpx 16rpx rgba(43, 68, 94, 0.08);
}

.product-img {
  width: 96rpx;
  height: 96rpx;
  border-radius: 14rpx;
  background: #edf4fb;
  flex-shrink: 0;
}

.product-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #5c8ab8;
  font-size: 34rpx;
  font-weight: 800;
}

.product-info {
  flex: 1;
  min-width: 0;
}

.product-title {
  color: #172331;
  font-size: 27rpx;
  font-weight: 800;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10rpx;
  margin-top: 10rpx;
}

.product-price {
  color: #f26a42;
  font-size: 25rpx;
  font-weight: 900;
}

.item-status,
.trade-status {
  padding: 4rpx 12rpx;
  border-radius: 999rpx;
  background: #edf4fb;
  color: #5c7894;
  font-size: 20rpx;
  font-weight: 700;
}

.trade-status {
  background: rgba(92, 138, 184, 0.14);
  color: #4f7599;
}

.chat-body {
  height: calc(100vh - 350rpx);
  padding: 28rpx 0 170rpx;
  box-sizing: border-box;
}

.system-msg,
.contact-msg {
  display: flex;
  justify-content: center;
  margin-bottom: 28rpx;
}

.system-msg > text {
  max-width: 560rpx;
  padding: 10rpx 20rpx;
  border-radius: 999rpx;
  background: rgba(84, 99, 116, 0.1);
  color: #7d8c9c;
  font-size: 22rpx;
  line-height: 1.45;
}

.trade-event-card,
.contact-card {
  width: 560rpx;
  padding: 22rpx 24rpx;
  border-radius: 20rpx;
  background: #fff;
  box-shadow: 0 4rpx 16rpx rgba(43, 68, 94, 0.08);
  box-sizing: border-box;
  text-align: center;
}

.trade-event-title,
.contact-title {
  color: #172331;
  font-size: 27rpx;
  font-weight: 900;
}

.trade-event-desc,
.contact-line {
  margin-top: 10rpx;
  color: #65788c;
  font-size: 23rpx;
  line-height: 1.5;
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

.mbub {
  padding: 20rpx 28rpx;
  border-radius: 24rpx;
  font-size: 28rpx;
  max-width: 100%;
  word-break: break-all;
  line-height: 1.5;
}

.chat-img {
  width: 280rpx;
  max-height: 360rpx;
  border-radius: 18rpx;
  display: block;
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

.chat-footer-new {
  position: fixed;
  bottom: 0;
  left: 50%;
  width: 100%;
  max-width: 430px;
  transform: translateX(-50%);
  background: rgba(232, 240, 248, 0.98);
  padding: 14rpx 32rpx 24rpx;
  box-sizing: border-box;
  z-index: 10;
}

.quick-actions {
  display: flex;
  justify-content: center;
  gap: 14rpx;
  margin-bottom: 14rpx;
  flex-wrap: wrap;
}

.quick-action-btn {
  height: 56rpx;
  margin: 0;
  padding: 0 26rpx;
  border-radius: 999rpx;
  background: #fff;
  color: #5c7894;
  border: 1rpx solid #d7e4ef;
  font-size: 23rpx;
  font-weight: 800;
  line-height: 56rpx;
}

.quick-action-btn.intent,
.quick-action-btn.confirm,
.quick-action-btn.complete {
  background: #7ba8d4;
  color: #fff;
  border-color: #7ba8d4;
}

.quick-action-btn::after {
  border: none;
}

.chat-input-bar {
  display: flex;
  align-items: center;
  gap: 16rpx;
  background: rgba(255, 255, 255, 0.86);
  border-radius: 999rpx;
  padding: 8rpx 8rpx 8rpx 24rpx;
}

.chat-image-btn {
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  background: #edf4fb;
  color: #5c7894;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 34rpx;
  font-weight: 700;
  flex-shrink: 0;
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

.contact-mask {
  position: fixed;
  inset: 0;
  z-index: 30;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(17, 28, 43, 0.28);
}

.contact-dialog {
  width: 620rpx;
  padding: 30rpx;
  border-radius: 24rpx;
  background: #fff;
  box-sizing: border-box;
}

.dialog-title {
  color: #172331;
  font-size: 31rpx;
  font-weight: 900;
  margin-bottom: 22rpx;
}

.contact-input {
  height: 78rpx;
  margin-bottom: 16rpx;
  padding: 0 22rpx;
  border-radius: 18rpx;
  background: #f2f6fa;
  font-size: 27rpx;
  box-sizing: border-box;
}

.dialog-actions {
  display: flex;
  gap: 16rpx;
  margin-top: 8rpx;
}

.dialog-btn {
  flex: 1;
  height: 76rpx;
  border-radius: 20rpx;
  font-size: 27rpx;
  font-weight: 900;
  line-height: 76rpx;
}

.dialog-btn::after {
  border: none;
}

.dialog-btn.ghost {
  background: #f2f6fa;
  color: #5c7894;
}

.dialog-btn.primary {
  background: #7ba8d4;
  color: #fff;
}
</style>
