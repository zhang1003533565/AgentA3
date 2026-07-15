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
              <view v-if="cardActions(m).length" class="trade-event-card" :class="tradeCardClass(m)">
                <view class="trade-card-top">
                  <text class="trade-actor-tag">{{ tradeActorLabel(m) }}</text>
                  <text class="trade-time">{{ formatClock(m.time) }}</text>
                </view>
                <view class="trade-card-headline">
                  <view class="trade-icon" :class="tradeIconClass(m.tradeAction)"></view>
                  <view class="trade-event-title">{{ tradeActionTitle(m.tradeAction) }}</view>
                </view>
                <view class="trade-event-desc">{{ tradeActionDesc(m) }}</view>
                <view class="trade-card-actions">
                  <button
                    v-for="action in cardActions(m)"
                    :key="action.type"
                    class="trade-card-btn"
                    :class="action.type"
                    :disabled="acting"
                    @click="runTradeAction(action.type)"
                  >
                    {{ action.label }}
                  </button>
                </view>
              </view>
              <view v-else-if="systemLineText(m)" class="system-line">
                <view class="system-line-icon" :class="systemLineIconClass(m)"></view>
                <text class="system-line-text">{{ systemLineText(m) }}</text>
                <text class="system-line-time">{{ formatClock(m.time) }}</text>
              </view>
              <text v-else>{{ m.content }}</text>
            </view>

            <view v-else-if="m.type === 'contact'" class="contact-msg">
              <view class="contact-card" :class="tradeCardClass(m)">
                <view class="trade-card-top">
                  <text class="trade-actor-tag">{{ tradeActorLabel(m) }}</text>
                  <text class="trade-time">{{ formatClock(m.time) }}</text>
                </view>
                <view class="trade-card-headline">
                  <view class="trade-icon icon-contact"></view>
                  <view class="contact-title">联系方式</view>
                </view>
                <view class="contact-list">
                  <view v-for="item in contactItems(m.content)" :key="item.label + item.value" class="contact-row">
                    <view class="contact-row-main">
                      <view class="contact-label">{{ item.label }}</view>
                      <view class="contact-value">{{ item.value }}</view>
                    </view>
                    <button class="copy-btn" @click.stop="copyContact(item)">
                      <text class="copy-icon"></text>
                      <text>复制</text>
                    </button>
                  </view>
                </view>
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

          <view v-for="card in standaloneTradeCards" :key="card.type" :id="'trade-action-card-' + card.type" class="system-msg">
            <view class="trade-event-card action-card" :class="card.cardClass">
              <view class="trade-card-top">
                <text class="trade-actor-tag">{{ card.tag }}</text>
                <text class="trade-time">{{ card.time }}</text>
              </view>
              <view class="trade-card-headline">
                <view class="trade-icon" :class="card.iconClass"></view>
                <view class="trade-event-title">{{ card.title }}</view>
              </view>
              <view class="trade-event-desc">{{ card.desc }}</view>
              <view class="trade-card-actions">
                <button
                  v-for="action in card.actions"
                  :key="action.type"
                  class="trade-card-btn"
                  :class="action.type"
                  :disabled="acting"
                  @click="runTradeAction(action.type)"
                >
                  {{ action.label }}
                </button>
              </view>
            </view>
          </view>
        </scroll-view>

        <view class="chat-footer-new">
          <view v-if="morePanelVisible" class="more-panel">
            <view class="more-action" @click="sendImage">
              <view class="more-icon image-icon"></view>
              <text>图片</text>
            </view>
            <view class="more-action" :class="{ disabled: !canShareContact }" @click="sendMyContact">
              <view class="more-icon contact-icon"></view>
              <text>{{ savedContact ? '发送我的联系方式' : '我的联系方式' }}</text>
            </view>
          </view>
          <view class="chat-input-bar">
            <view class="chat-image-btn" @click="toggleMorePanel">
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
      time: item.createTime || '',
      isMine: !!item.isMine
    }
  }
  if (Number(item.messageType) === 4) {
    return {
      id: item.id,
      type: 'contact',
      messageType: 4,
      tradeAction: item.tradeAction || 'CONTACT_SHARE',
      content: item.content,
      time: item.createTime || '',
      isMine: !!item.isMine
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
      contactForm: { wechat: '', phone: '', other: '' },
      savedContact: null,
      morePanelVisible: false
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
    hasContactShare() {
      return this.messages.some((message) => message.tradeAction === 'CONTACT_SHARE')
    },
    canShareContact() {
      return !!(this.tradeInfo && this.tradeInfo.status === 'TRADING' && !this.hasContactShare)
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
        const actions = []
        if (!this.hasContactShare) actions.push({ type: 'shareContact', label: '发送联系方式' })
        if (this.tradeInfo.isSeller) actions.push({ type: 'complete', label: '标记交易完成' })
        return actions
      }
      return []
    },
    standaloneTradeCards() {
      const actions = this.tradeActionButtons
      if (!actions.length) return []
      const hasHostCard = this.messages.some((message) => {
        if (this.tradeInfo?.status === 'WAIT_CONFIRM') return message.tradeAction === 'TRADE_INTENT'
        return false
      })
      if (hasHostCard) return []
      if (!this.tradeInfo) {
        return [{
          type: 'intent',
          tag: '我发起',
          time: '',
          iconClass: 'icon-intent',
          title: '购买意向',
          desc: '对这个商品感兴趣，可以先向卖家表达购买意向。',
          cardClass: 'mine-card intent-card',
          actions
        }]
      }
      if (this.tradeInfo.status === 'WAIT_CONFIRM') {
        return [{
          type: 'confirm',
          tag: '对方发起',
          time: '',
          iconClass: 'icon-intent',
          title: '购买意向',
          desc: '买家希望购买该商品，请确认是否进入线下交易沟通。',
          cardClass: 'other-card intent-card',
          actions
        }]
      }
      if (this.tradeInfo.status === 'TRADING') {
        const cards = []
        const contactAction = actions.find((action) => action.type === 'shareContact')
        if (contactAction) {
          cards.push({
            type: 'contact',
            tag: '可选辅助',
            time: '',
            iconClass: 'icon-contact',
            title: '发送联系方式',
            desc: '可选择发送微信、手机号或其他联系方式，便于线下沟通。',
            cardClass: 'system-card contact-share-card',
            actions: [contactAction]
          })
        }
        const completeAction = actions.find((action) => action.type === 'complete')
        if (completeAction) {
          cards.push({
            type: 'complete',
            tag: '卖家操作',
            time: '',
            iconClass: 'icon-done',
            title: '交易完成',
            desc: '线下交易完成后，由卖家标记该商品交易完成。',
            cardClass: 'system-card done-card',
            actions: [completeAction]
          })
        }
        return cards
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
        this.loadSavedContact()
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
        this.morePanelVisible = false
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
    toggleMorePanel() {
      this.morePanelVisible = !this.morePanelVisible
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
      if (this.hasContactShare) {
        uni.showToast({ title: '已发送联系方式', icon: 'none' })
        return
      }
      if (this.savedContact && this.hasContactContent(this.savedContact)) {
        await this.sendSavedContact()
        return
      }
      this.contactVisible = true
    },
    closeContactDialog() {
      this.contactVisible = false
    },
    async submitContactInfo() {
      try {
        if (this.hasContactShare) {
          uni.showToast({ title: '已发送联系方式', icon: 'none' })
          return
        }
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
        this.savedContact = { wechat, phone, other }
        uni.setStorageSync('marketContactInfo', this.savedContact)
        await sendChatMessage({
          sessionId: Number(this.sessionId),
          content: parts.join('\n'),
          messageType: 4
        })
        uni.showToast({ title: '已发送', icon: 'success' })
        this.contactVisible = false
        this.morePanelVisible = false
        this.contactForm = { wechat: '', phone: '', other: '' }
        await this.loadMessages()
      } catch (e) {
        console.error('发送联系方式失败', e)
        uni.showToast({ title: e?.data?.msg || e?.msg || '发送失败', icon: 'none' })
      }
    },
    loadSavedContact() {
      try {
        const contact = uni.getStorageSync('marketContactInfo')
        if (contact && typeof contact === 'object') {
          this.savedContact = {
            wechat: contact.wechat || '',
            phone: contact.phone || '',
            other: contact.other || ''
          }
          this.contactForm = { ...this.savedContact }
        }
      } catch (e) {
        this.savedContact = null
      }
    },
    hasContactContent(contact) {
      return !!(contact && (contact.wechat || contact.phone || contact.other))
    },
    buildContactContent(contact) {
      const parts = []
      if (contact.wechat) parts.push(`微信：${contact.wechat}`)
      if (contact.phone) parts.push(`手机号：${contact.phone}`)
      if (contact.other) parts.push(`其他：${contact.other}`)
      return parts.join('\n')
    },
    async sendMyContact() {
      if (!this.canShareContact) {
        uni.showToast({ title: this.hasContactShare ? '已发送联系方式' : '确认交易后才能发送联系方式', icon: 'none' })
        return
      }
      if (!this.savedContact || !this.hasContactContent(this.savedContact)) {
        this.contactForm = { wechat: '', phone: '', other: '' }
        this.contactVisible = true
        return
      }
      await this.sendSavedContact()
    },
    async sendSavedContact() {
      if (!this.canShareContact) {
        uni.showToast({ title: this.hasContactShare ? '已发送联系方式' : '确认交易后才能发送联系方式', icon: 'none' })
        return
      }
      try {
        await sendChatMessage({
          sessionId: Number(this.sessionId),
          content: this.buildContactContent(this.savedContact),
          messageType: 4
        })
        this.morePanelVisible = false
        uni.showToast({ title: '已发送', icon: 'success' })
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
    tradeActionDesc(message) {
      const mine = !!message.isMine
      const map = {
        TRADE_INTENT: mine ? '你表达了购买意向，等待卖家确认。' : '买家希望购买该商品，请确认是否进入线下交易沟通。',
        TRADE_CONFIRM: '双方已进入交易阶段，可交换联系方式并约定交易地点。',
        TRADE_COMPLETE: mine ? '你已标记该商品交易完成。' : '该商品交易已完成。',
        TRADE_CANCEL: '交易已取消。'
      }
      return map[message.tradeAction] || message.content
    },
    tradeIconClass(action) {
      const map = {
        TRADE_INTENT: 'icon-intent',
        TRADE_CONFIRM: 'icon-confirm',
        TRADE_COMPLETE: 'icon-done',
        TRADE_CANCEL: 'icon-cancel'
      }
      return map[action] || 'icon-info'
    },
    tradeActorLabel(message) {
      if (message.tradeAction === 'CONTACT_SHARE') return message.isMine ? '我发送' : '对方发送'
      if (message.tradeAction === 'TRADE_COMPLETE') return message.isMine ? '我标记' : '对方标记'
      if (message.tradeAction === 'TRADE_CANCEL') return '系统通知'
      return message.isMine ? '我发起' : '对方发起'
    },
    tradeCardClass(message) {
      const actorClass = message.isMine ? 'mine-card' : 'other-card'
      const actionClassMap = {
        TRADE_INTENT: 'intent-card',
        TRADE_CONFIRM: 'confirm-card',
        CONTACT_SHARE: 'contact-share-card',
        TRADE_COMPLETE: 'done-card',
        TRADE_CANCEL: 'cancel-card'
      }
      return `${actorClass} ${actionClassMap[message.tradeAction] || 'system-card'}`
    },
    cardActions(message) {
      if (!this.tradeInfo) return []
      if (message.tradeAction === 'TRADE_INTENT' && this.tradeInfo.status === 'WAIT_CONFIRM' && this.tradeInfo.isSeller) {
        return [{ type: 'confirm', label: '确认交易' }]
      }

      return []
    },
    systemLineText(message) {
      const mine = !!message.isMine
      const map = {
        TRADE_INTENT: mine ? '你表达了购买意向' : '买家表达了购买意向',
        TRADE_CONFIRM: mine ? '你已确认线下交易' : '对方已确认与你交易',
        TRADE_COMPLETE: '商品交易已完成',
        TRADE_CANCEL: '交易已取消'
      }
      return map[message.tradeAction] || ''
    },
    systemLineIconClass(message) {
      return this.tradeIconClass(message.tradeAction)
    },
    contactItems(content) {
      return String(content || '').split(/\n/).filter(Boolean).map((line) => {
        const parts = line.split('：')
        if (parts.length >= 2) {
          return { label: parts.shift().trim(), value: parts.join('：').trim() }
        }
        const legacyParts = line.split(':')
        if (legacyParts.length >= 2) {
          return { label: legacyParts.shift().trim(), value: legacyParts.join(':').trim() }
        }
        return { label: '联系方式', value: line.trim() }
      })
    },
    copyContact(item) {
      if (!item || !item.value) return
      uni.setClipboardData({
        data: item.value,
        success: () => {
          const label = item.label === '微信' ? '微信号' : item.label
          uni.showToast({ title: `已复制${label}`, icon: 'none' })
        }
      })
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
  padding: 28rpx 0 132rpx;
  box-sizing: border-box;
}

.system-msg,
.contact-msg {
  display: flex;
  flex-direction: column;
  align-items: center;
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
  width: 590rpx;
  padding: 22rpx 24rpx;
  border-radius: 22rpx;
  background: #fff;
  box-shadow: 0 6rpx 18rpx rgba(43, 68, 94, 0.09);
  box-sizing: border-box;
}

.mine-card {
  border-left: 8rpx solid #7ba8d4;
}

.other-card {
  border-left: 8rpx solid #d9a15f;
}

.intent-card {
  border-left: 8rpx solid #7ba8d4;
}

.confirm-card {
  border-left: 8rpx solid #d9a15f;
}

.contact-share-card,
.done-card {
  border-left: 8rpx solid #7fb59b;
}

.cancel-card,
.system-card {
  border-left: 8rpx solid #9aa9b8;
}

.trade-card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18rpx;
}

.trade-actor-tag {
  padding: 6rpx 14rpx;
  border-radius: 999rpx;
  background: rgba(123, 168, 212, 0.16);
  color: #4f7599;
  font-size: 21rpx;
  font-weight: 900;
}

.other-card .trade-actor-tag {
  background: rgba(217, 161, 95, 0.16);
  color: #9a672c;
}

.confirm-card .trade-actor-tag {
  background: rgba(217, 161, 95, 0.16);
  color: #9a672c;
}

.contact-share-card .trade-actor-tag,
.done-card .trade-actor-tag {
  background: rgba(127, 181, 155, 0.16);
  color: #4f7f65;
}

.cancel-card .trade-actor-tag,
.system-card .trade-actor-tag {
  background: rgba(154, 169, 184, 0.16);
  color: #65788c;
}

.trade-time {
  color: #9aa9b8;
  font-size: 20rpx;
}

.trade-card-headline {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.trade-icon {
  width: 62rpx;
  height: 62rpx;
  border-radius: 18rpx;
  background: #edf4fb;
  color: #5c7894;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 34rpx;
  font-weight: 900;
  flex-shrink: 0;
}

.other-card .trade-icon,
.confirm-card .trade-icon {
  background: #fbf1e6;
  color: #b87535;
}

.intent-card .trade-icon {
  background: #edf4fb;
  color: #5c7894;
}

.contact-share-card .trade-icon,
.done-card .trade-icon {
  background: #edf7f1;
  color: #4f8a69;
}

.cancel-card .trade-icon,
.system-card .trade-icon {
  background: #f2f6fa;
  color: #65788c;
}


.trade-icon::before,
.trade-icon::after,
.system-line-icon::before,
.system-line-icon::after {
  content: '';
  position: absolute;
  box-sizing: border-box;
}

.trade-icon,
.system-line-icon {
  position: relative;
}

.icon-intent::before {
  left: 17rpx;
  top: 22rpx;
  width: 28rpx;
  height: 24rpx;
  border: 3rpx solid currentColor;
  border-radius: 5rpx;
}

.icon-intent::after {
  left: 23rpx;
  top: 14rpx;
  width: 16rpx;
  height: 14rpx;
  border: 3rpx solid currentColor;
  border-bottom: 0;
  border-radius: 14rpx 14rpx 0 0;
}

.system-line-icon.icon-intent::before {
  left: 5rpx;
  top: 10rpx;
  width: 18rpx;
  height: 14rpx;
  border-width: 2rpx;
  border-radius: 4rpx;
}

.system-line-icon.icon-intent::after {
  left: 9rpx;
  top: 5rpx;
  width: 10rpx;
  height: 8rpx;
  border-width: 2rpx;
}

.icon-confirm::before,
.icon-done::before {
  left: 18rpx;
  top: 15rpx;
  width: 26rpx;
  height: 30rpx;
  border: 3rpx solid currentColor;
  border-radius: 9rpx 9rpx 12rpx 12rpx;
}

.icon-confirm::after,
.icon-done::after {
  left: 26rpx;
  top: 26rpx;
  width: 15rpx;
  height: 8rpx;
  border-left: 3rpx solid currentColor;
  border-bottom: 3rpx solid currentColor;
  transform: rotate(-45deg);
}

.system-line-icon.icon-confirm::before,
.system-line-icon.icon-done::before {
  left: 6rpx;
  top: 3rpx;
  width: 16rpx;
  height: 20rpx;
  border-width: 2rpx;
  border-radius: 6rpx 6rpx 8rpx 8rpx;
}

.system-line-icon.icon-confirm::after,
.system-line-icon.icon-done::after {
  left: 11rpx;
  top: 11rpx;
  width: 9rpx;
  height: 5rpx;
  border-left-width: 2rpx;
  border-bottom-width: 2rpx;
}

.icon-contact::before {
  left: 18rpx;
  top: 15rpx;
  width: 26rpx;
  height: 34rpx;
  border: 3rpx solid currentColor;
  border-radius: 16rpx;
  transform: rotate(-22deg);
}

.icon-contact::after {
  left: 26rpx;
  top: 20rpx;
  width: 10rpx;
  height: 24rpx;
  border-top: 3rpx solid currentColor;
  border-bottom: 3rpx solid currentColor;
  transform: rotate(-22deg);
}

.system-line-icon.icon-contact::before {
  left: 7rpx;
  top: 3rpx;
  width: 14rpx;
  height: 21rpx;
  border-width: 2rpx;
  border-radius: 10rpx;
  transform: rotate(-22deg);
}

.system-line-icon.icon-contact::after {
  left: 11rpx;
  top: 6rpx;
  width: 6rpx;
  height: 15rpx;
  border-top-width: 2rpx;
  border-bottom-width: 2rpx;
  transform: rotate(-22deg);
}

.icon-cancel::before,
.icon-cancel::after {
  left: 30rpx;
  top: 17rpx;
  width: 3rpx;
  height: 28rpx;
  background: currentColor;
  border-radius: 999rpx;
}

.icon-cancel::before {
  transform: rotate(45deg);
}

.icon-cancel::after {
  transform: rotate(-45deg);
}

.system-line-icon.icon-cancel::before,
.system-line-icon.icon-cancel::after {
  left: 13rpx;
  top: 5rpx;
  width: 2rpx;
  height: 18rpx;
  background: currentColor;
  border-radius: 999rpx;
}

.icon-info::before {
  left: 27rpx;
  top: 24rpx;
  width: 8rpx;
  height: 20rpx;
  border-left: 3rpx solid currentColor;
}

.icon-info::after {
  left: 26rpx;
  top: 17rpx;
  width: 6rpx;
  height: 6rpx;
  border-radius: 50%;
  background: currentColor;
}
.trade-copy {
  flex: 1;
  min-width: 0;
}

.trade-event-title,
.contact-title {
  color: #172331;
  font-size: 28rpx;
  font-weight: 900;
}

.trade-event-desc {
  margin-top: 14rpx;
  color: #65788c;
  font-size: 23rpx;
  line-height: 1.55;
}

.contact-list {
  margin-top: 12rpx;
}

.trade-card-actions {
  display: flex;
  justify-content: flex-end;
  gap: 14rpx;
  margin-top: 22rpx;
}

.trade-card-btn {
  min-width: 168rpx;
  height: 58rpx;
  margin: 0;
  padding: 0 24rpx;
  border-radius: 999rpx;
  background: #7ba8d4;
  color: #fff;
  font-size: 23rpx;
  font-weight: 900;
  line-height: 58rpx;
}

.trade-card-btn.intent {
  background: #7ba8d4;
  color: #fff;
}

.trade-card-btn.confirm {
  background: #d9a15f;
  color: #fff;
}

.trade-card-btn.shareContact {
  background: #edf7f1;
  color: #4f8a69;
}

.trade-card-btn.complete {
  background: #7fb59b;
  color: #fff;
}

.trade-card-btn::after {
  border: none;
}

.contact-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 18rpx 0;
  border-bottom: 1rpx solid rgba(101, 120, 140, 0.14);
}

.contact-row:last-child {
  border-bottom: none;
  padding-bottom: 4rpx;
}

.contact-row-main {
  flex: 1;
  min-width: 0;
}

.contact-label {
  color: #7d8c9c;
  font-size: 22rpx;
  font-weight: 800;
}

.contact-value {
  margin-top: 8rpx;
  color: #172331;
  font-size: 30rpx;
  font-weight: 900;
  word-break: break-all;
}

.copy-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6rpx;
  min-width: 104rpx;
  height: 50rpx;
  margin: 0;
  padding: 0 16rpx;
  border-radius: 999rpx;
  background: transparent;
  border: 1rpx solid rgba(79, 138, 105, 0.34);
  color: #4f8a69;
  font-size: 22rpx;
  font-weight: 900;
  line-height: 50rpx;
  flex-shrink: 0;
}

.copy-icon {
  position: relative;
  width: 20rpx;
  height: 20rpx;
  flex-shrink: 0;
}

.copy-icon::before,
.copy-icon::after {
  content: '';
  position: absolute;
  width: 12rpx;
  height: 12rpx;
  border: 2rpx solid currentColor;
  border-radius: 3rpx;
  box-sizing: border-box;
}

.copy-icon::before {
  left: 2rpx;
  top: 5rpx;
}

.copy-icon::after {
  left: 6rpx;
  top: 1rpx;
  background: #fff;
}

.copy-btn::after {
  border: none;
}

.system-line {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  max-width: 620rpx;
  padding: 10rpx 18rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.72);
  box-shadow: 0 3rpx 10rpx rgba(43, 68, 94, 0.05);
  color: #546374;
  box-sizing: border-box;
}

.system-line-icon {
  position: relative;
  width: 28rpx;
  height: 28rpx;
  margin-right: 10rpx;
  flex-shrink: 0;
  color: #1f2a36;
}

.system-line-text {
  font-size: 22rpx;
  line-height: 1.35;
  color: #546374;
}

.system-line-time {
  margin-left: 12rpx;
  font-size: 19rpx;
  color: #9aa9b8;
  flex-shrink: 0;
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

.more-panel {
  display: flex;
  gap: 18rpx;
  margin-bottom: 14rpx;
  padding: 18rpx;
  border-radius: 24rpx;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 6rpx 18rpx rgba(43, 68, 94, 0.08);
}

.more-action {
  width: 152rpx;
  height: 120rpx;
  border-radius: 18rpx;
  background: #f6f9fc;
  color: #172331;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10rpx;
  font-size: 22rpx;
  font-weight: 800;
}

.more-action.disabled {
  color: #9aa9b8;
  opacity: 0.62;
}

.more-icon {
  position: relative;
  width: 38rpx;
  height: 38rpx;
  color: #1f2a36;
}

.more-icon::before,
.more-icon::after {
  content: '';
  position: absolute;
  box-sizing: border-box;
}

.image-icon::before {
  left: 4rpx;
  top: 7rpx;
  width: 30rpx;
  height: 24rpx;
  border: 3rpx solid currentColor;
  border-radius: 6rpx;
}

.image-icon::after {
  left: 10rpx;
  top: 20rpx;
  width: 20rpx;
  height: 10rpx;
  border-left: 3rpx solid currentColor;
  border-bottom: 3rpx solid currentColor;
  transform: skew(-28deg);
}

.contact-icon::before {
  left: 10rpx;
  top: 5rpx;
  width: 18rpx;
  height: 28rpx;
  border: 3rpx solid currentColor;
  border-radius: 12rpx;
  transform: rotate(-22deg);
}

.contact-icon::after {
  left: 15rpx;
  top: 10rpx;
  width: 8rpx;
  height: 18rpx;
  border-top: 3rpx solid currentColor;
  border-bottom: 3rpx solid currentColor;
  transform: rotate(-22deg);
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
