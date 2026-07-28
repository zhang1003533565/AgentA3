<template>
  <view class="page-root">
    <view class="screen">
      <view class="container">
        <common-page-header :title="chatTitle" :fixed="true" :placeholder="true" :showBack="true" />

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
          <view v-if="tradeMenuActions.length" class="trade-more-wrap" @click.stop>
            <view class="trade-more-btn" @click="tradeMenuVisible = !tradeMenuVisible">⋮</view>
            <view v-if="tradeMenuVisible" class="trade-menu-dropdown">
              <view
                v-for="action in tradeMenuActions"
                :key="action.type"
                class="trade-menu-item"
                :class="action.class"
                @click="handleMenuAction(action)"
              >
                {{ action.label }}
              </view>
            </view>
          </view>
        </view>

        <view v-if="cancelConfirmVisible" class="cancel-overlay" @click="cancelConfirmVisible = false">
          <view class="cancel-dialog" @click.stop>
            <view class="cancel-title">确认取消本次交易？</view>
            <view class="cancel-desc">取消后仅结束本次交易，聊天记录保留</view>
            <view class="cancel-actions">
              <button class="cancel-btn secondary" @click="cancelConfirmVisible = false">再想想</button>
              <button class="cancel-btn primary" :disabled="acting" @click="confirmCancel">确认取消</button>
            </view>
          </view>
        </view>

        <view v-if="completeConfirmVisible" class="cancel-overlay" @click="completeConfirmVisible = false">
          <view class="cancel-dialog" @click.stop>
            <view class="cancel-title">确认完成交易？</view>
            <view v-if="!hasContactExchange" class="complete-warning">双方尚未交换联系方式，是否仍确认完成交易？</view>
            <view class="cancel-desc complete-desc">
              <text>完成后：</text>
              <text>· 商品将标记为已售出</text>
              <text>· 当前交易流程结束</text>
              <text>· 无法继续修改交易状态</text>
            </view>
            <view class="cancel-actions">
              <button class="cancel-btn secondary" @click="completeConfirmVisible = false">取消</button>
              <button class="cancel-btn primary" :disabled="acting" @click="confirmComplete">确认完成</button>
            </view>
          </view>
        </view>

        <scroll-view scroll-y class="chat-body" :scroll-into-view="scrollBottom" scroll-with-animation>
          <view v-for="m in chatMessages" :key="m.id" :id="'msg-' + m.id" :class="{ 'last-msg': m === chatMessages[chatMessages.length - 1] }">
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
                <view v-if="tradeActionNote(m)" class="trade-note" :class="tradeActionNoteClass(m)">
                  <view class="trade-note-icon"></view>
                  <text>{{ tradeActionNote(m) }}</text>
                </view>
                <view class="trade-card-actions">
                  <button
                    v-for="action in cardActions(m)"
                    :key="action.type"
                    class="trade-card-btn"
                    :class="[action.type, action.class]"
                    :disabled="acting"
                    @click="handleMenuAction(action)"
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
                <view class="contact-card-top">
                  <view class="contact-confirm-tag">
                    <view class="contact-confirm-dot"></view>
                    <text>双方确认</text>
                  </view>
                  <text class="contact-card-time">{{ formatClock(m.time) }}</text>
                </view>
                <view class="contact-hero">
                  <view class="contact-hero-icon">
                    <view class="contact-shield"></view>
                    <view class="contact-orbit"></view>
                    <view class="contact-spark"></view>
                  </view>
                  <view class="contact-hero-copy">
                    <view class="contact-title">✓ 已交换联系方式</view>
                    <view class="contact-subtitle">以下信息已被双方确认</view>
                  </view>
                </view>
                <view class="contact-list">
                  <view v-for="(item, idx) in contactItems(m.content)" :key="item.label + idx" class="contact-row">
                    <view class="contact-type-icon" :class="'contact-type-icon--' + item.type"></view>
                    <view class="contact-row-main">
                      <view class="contact-label">{{ item.label }}</view>
                      <view class="contact-value" :class="{ hidden: !contactVisibility[idx] }">
                        {{ contactVisibility[idx] ? item.value : maskContactValue(item.value) }}
                      </view>
                    </view>
                    <view class="contact-row-divider"></view>
                    <view class="contact-actions">
                      <view
                        class="eye-toggle"
                        :class="{ visible: contactVisibility[idx] }"
                        @touchstart.prevent="contactVisibility[idx] = true; $forceUpdate()"
                        @touchend.prevent="contactVisibility[idx] = false; $forceUpdate()"
                        @touchcancel.prevent="contactVisibility[idx] = false; $forceUpdate()"
                      >
                        <text class="eye-slash"></text>
                      </view>
                      <button class="copy-btn" @click.stop="copyContact(item)">
                        <text class="copy-icon"></text>
                      </button>
                    </view>
                  </view>
                </view>
                <view class="trade-note contact-safety-note">
                  <view class="trade-note-icon"></view>
                  <text>请线下沟通交易细节，注意人身与财产安全。</text>
                </view>
              </view>
            </view>

            <view v-else class="msg" :class="m.type">
              <view v-if="m.type === 's'" class="msg-content-s">
                <view class="msg-bubble-group">
                  <view class="mbub mbub-s" :class="{ 'mbub-img': m.messageType === 2 }">
                    <image v-if="m.messageType === 2" class="chat-img" :src="m.content" mode="widthFix" @click="previewImage(m.content)" />
                    <text v-else>{{ m.content }}</text>
                  </view>
                  <view class="mtime mtime-s">{{ formatClock(m.time) }}</view>
                </view>
                <view class="mava mava-s">
                  <image v-if="ownMessageAvatar(m)" class="mava-img" :src="ownMessageAvatar(m)" mode="aspectFill" />
                  <text v-else>我</text>
                </view>
              </view>
              <view v-else class="msg-content-r">
                <view class="mava mava-r">
                  <image v-if="messageAvatar(m)" class="mava-img" :src="messageAvatar(m)" mode="aspectFill" />
                  <text v-else>{{ otherInitial }}</text>
                </view>
                <view class="msg-bubble-group">
                  <view class="mbub mbub-r" :class="{ 'mbub-img': m.messageType === 2 }">
                    <image v-if="m.messageType === 2" class="chat-img" :src="m.content" mode="widthFix" @click="previewImage(m.content)" />
                    <text v-else>{{ m.content }}</text>
                  </view>
                  <view class="mtime mtime-r">{{ formatClock(m.time) }}</view>
                </view>
              </view>
            </view>
          </view>

          <view v-for="(card, idx) in standaloneTradeCards" :key="card.type" :id="'trade-action-card-' + card.type" class="system-msg" :class="{ 'last-msg': idx === standaloneTradeCards.length - 1 }">
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
              <view v-if="card.note" class="trade-note" :class="card.noteClass">
                <view class="trade-note-icon"></view>
                <text>{{ card.note }}</text>
              </view>
              <view class="trade-card-actions">
                <button
                  v-for="action in card.actions"
                  :key="action.type"
                  class="trade-card-btn"
                  :class="[action.type, action.class]"
                  :disabled="acting"
                  @click="handleMenuAction(action)"
                >
                  {{ action.label }}
                </button>
              </view>
            </view>
          </view>

          <view id="bottom-spacer" class="bottom-spacer"></view>
        </scroll-view>

        <view class="chat-footer-new">
          <view v-if="showContactQuickAction" class="quick-action-row">
            <view class="quick-pill contact-pill" :class="{ 'contact-pill--exchanged': contactExchangeStatus === 'EXCHANGED' }" @click="handleContactPillClick">
              <view v-if="contactPillIconClass" class="quick-pill-icon" :class="contactPillIconClass"></view>
              <text>{{ contactPillText }}</text>
            </view>
          </view>
          <view class="chat-input-bar">
            <view class="chat-image-btn" @click="toggleMorePanel">
              <text>＋</text>
            </view>
            <input v-model="messageInput" class="chat-input-new" placeholder="输入消息..." @focus="morePanelVisible = false" @confirm="sendMsg" />
            <view class="chat-send-btn" @click="sendMsg">
              <text>➤</text>
            </view>
          </view>
          <view class="more-panel-wrapper" :class="{ open: morePanelVisible }">
            <view class="more-panel">
              <view class="more-action" @click="sendImage">
                <view class="more-icon image-icon"></view>
                <text>图片</text>
              </view>
            </view>
          </view>
        </view>

        <view v-if="contactVisible" class="contact-mask" @click="closeContactDialog">
          <view class="contact-dialog" @click.stop>
            <view class="dialog-title">交换联系方式</view>
            <view class="contact-choice-title">选择需要发送的信息</view>
            <view class="contact-choice-list">
              <view
                v-for="field in contactFieldOptions"
                :key="field.key"
                class="contact-choice-item"
                :class="{ selected: selectedContactFields.includes(field.key) }"
                @click="toggleContactField(field.key)"
              >
                <view class="contact-checkbox"></view>
                <text>{{ field.label }}</text>
              </view>
            </view>
            <view class="contact-tabs">
              <view class="contact-tab" :class="{ active: contactMode === 'template' }" @click="contactMode = 'template'">快捷模板</view>
              <view class="contact-tab" :class="{ active: contactMode === 'custom' }" @click="startCustomContact">自定义发送</view>
            </view>

            <view v-if="contactMode === 'template'" class="template-panel">
              <view v-if="contactTemplates.length" class="template-list">
                <view
                  v-for="(tpl, index) in contactTemplates"
                  :key="tpl.id"
                  class="template-item"
                  :class="{ selected: selectedTemplateIndex === index }"
                  @click="selectContactTemplate(index)"
                >
                  <view class="template-name">{{ tpl.name || '校园交易联系方式' }}</view>
                  <view class="template-summary">{{ contactSummary(tpl) }}</view>
                </view>
              </view>
              <view v-else class="empty-template">暂无模板，可先创建自定义联系方式。</view>

              <view v-if="selectedContactTemplate" class="contact-preview">
                <view class="preview-title">发送预览</view>
                <view v-for="item in contactPreviewItems(selectedContactTemplate)" :key="item.label + item.value" class="preview-row">
                  <text class="preview-label">{{ item.label }}</text>
                  <text class="preview-value">{{ item.value }}</text>
                </view>
              </view>

              <view class="template-actions">
                <button class="dialog-btn ghost" @click="startCustomContact">新建模板</button>
                <button v-if="selectedContactTemplate" class="dialog-btn ghost" @click="editContactTemplate">编辑</button>
                <button v-if="selectedContactTemplate" class="dialog-btn ghost danger" @click="deleteContactTemplate">删除</button>
              </view>
            </view>

            <view v-else class="custom-panel">
              <input v-model="contactForm.name" class="contact-input" placeholder="模板名称（例如：校园交易联系方式）" />
              <input v-model="contactForm.wechat" class="contact-input" placeholder="微信" />
              <input v-model="contactForm.phone" class="contact-input" placeholder="手机号" type="number" />
              <input v-model="contactForm.qq" class="contact-input" placeholder="QQ" type="number" />
              <view v-if="contactPreviewItems(contactForm).length" class="contact-preview">
                <view class="preview-title">发送预览</view>
                <view v-for="item in contactPreviewItems(contactForm)" :key="item.label + item.value" class="preview-row">
                  <text class="preview-label">{{ item.label }}</text>
                  <text class="preview-value">{{ item.value }}</text>
                </view>
              </view>
              <button class="save-template-btn" @click="saveContactTemplate">保存为模板</button>
            </view>

            <view class="dialog-actions">
              <button class="dialog-btn ghost" @click="closeContactDialog">取消</button>
              <button class="dialog-btn primary" @click="confirmSendContact">确认交换</button>
            </view>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import CommonPageHeader from '@/components/common-page-header/common-page-header.vue'
import {
  cancelTradeRecord,
  completeTradeRecord,
  confirmTradeRecord,
  createOrGetChatSession,
  ensureTradeRecordBySession,
  getChatMessages,
  getChatSessions,
  getTradeRecords,
  sendChatMessage
} from '@/api/secondhand'
import { getUploadErrorMessage, uploadImage } from '@/utils/upload'
import {
  clearActiveChatSession,
  refreshMessageState,
  setActiveChatSession,
  subscribeMessageStore
} from '@/utils/messageStore'
import { getUserInfo } from '@/utils/storage'
import {
  buildDefaultAvatar,
  pickAvatar,
  pickOtherAvatar,
  pickSenderAvatar
} from '@/subpackage_lostfound/utils/avatar.js'

function decodeOptionText(value) {
  if (!value) return ''
  try {
    return decodeURIComponent(String(value))
  } catch {
    return String(value)
  }
}

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
    otherAvatar: pickOtherAvatar(item),
    lastMsg: item.lastMessage || '',
    lastTime: item.lastTime || '',
    isSeller: item.isSeller,
    tradeId: item.tradeId,
    tradeStatus: item.tradeStatus,
    tradeStatusText: item.tradeStatusText || '',
    contactExchangeStatus: item.contactExchangeStatus || '',
    contactExchangeRequesterId: item.contactExchangeRequesterId,
    contactExchange: item.contactExchange || null
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
      isMine: !!item.isMine,
      senderName: item.senderName || '',
      senderAvatar: pickSenderAvatar(item)
    }
  }
  if (Number(item.messageType) === 4) {
    return {
      id: item.id,
      type: 'contact',
      messageType: 4,
      tradeAction: item.tradeAction || 'CONTACT_EXCHANGE_DONE',
      content: item.content,
      time: item.createTime || '',
      isMine: !!item.isMine,
      senderName: item.senderName || '',
      senderAvatar: pickSenderAvatar(item)
    }
  }
  return {
    id: item.id,
    type: item.isMine ? 's' : 'r',
    messageType: Number(item.messageType || 1),
    content: item.content,
    time: item.createTime || '',
    senderName: item.senderName || '',
    senderAvatar: pickSenderAvatar(item)
  }
}

const TRADE_TEXT = {
  WAIT_CONFIRM: '交易待确认',
  TRADING: '双方已确认线下交易',
  COMPLETED: '交易已完成',
  CANCELLED: '本次交易已取消'
}

const ACTIVE_TRADE_CARD_STATUSES = ['WAIT_CONFIRM', 'TRADING']

export default {
  components: {
    CommonPageHeader
  },
  data() {
    return {
      itemId: null,
      targetUserId: null,
      sessionId: null,
      currentUserInfo: null,
      curChat: null,
      routeOtherName: '',
      routeOtherAvatar: '',
      tradeInfo: null,
      messages: [],
      messageInput: '',
      scrollBottom: '',
      acting: false,
      uploadingImage: false,
      contactVisible: false,
      contactMode: 'template',
      contactForm: { id: null, name: '校园交易联系方式', wechat: '', phone: '', qq: '' },
      contactTemplates: [],
      selectedTemplateIndex: 0,
      editingTemplateIndex: null,
      savedContact: null,
      selectedContactFields: ['wechat', 'phone', 'qq'],
      contactFieldOptions: [
        { key: 'wechat', label: '微信' },
        { key: 'phone', label: '手机号' },
        { key: 'qq', label: 'QQ' }
      ],
      contactVisibility: {},
      morePanelVisible: false,
      tradeMenuVisible: false,
      cancelConfirmVisible: false,
      completeConfirmVisible: false,
      unsubscribeMessageStore: null,
      messageSyncing: false
    }
  },
  computed: {
    chatTitle() {
      return this.curChat ? this.curChat.otherName : '聊天'
    },
    otherInitial() {
      return this.curChat && this.curChat.otherName ? this.curChat.otherName[0] : ''
    },
    ownAvatarUrl() {
      if (!this.currentUserInfo) return ''
      return pickAvatar(this.currentUserInfo) || buildDefaultAvatar(this.currentUserInfo)
    },
    chatMessages() {
      return this.messages
    },
    itemStatusText() {
      if (this.curChat?.itemStatusText) return this.curChat.itemStatusText
      const status = Number(this.curChat?.itemStatus)
      if (status === 2) return '在售'
      if (status === 3) return '已售出'
      if (status === 4) return '已下架'
      return ''
    },
    tradeStatusText() {
      return this.tradeInfo ? (TRADE_TEXT[this.tradeInfo.status] || this.tradeInfo.statusText || '') : ''
    },
      currentUserId() {
      if (!this.tradeInfo) return null
      return this.tradeInfo.isSeller ? this.tradeInfo.sellerId : this.tradeInfo.buyerId
    },
    contactExchange() {
      const base = this.tradeInfo?.contactExchange || {}
      const status = base.status || this.tradeInfo?.contactExchangeStatus || 'NONE'
      const requesterId = base.requesterId || base.requesterUserId || this.tradeInfo?.contactExchangeRequesterId
      const isPending = ['REQUESTED', 'PARTIAL', 'PENDING'].includes(status)
      const requestedByMe = isPending && Number(requesterId) === Number(this.currentUserId)
      const canRespond = this.tradeInfo?.status === 'TRADING' && isPending && !requestedByMe
      return {
        ...base,
        status,
        requesterId,
        currentUserAgreed: typeof base.currentUserAgreed === 'boolean' ? base.currentUserAgreed : requestedByMe,
        otherUserAgreed: typeof base.otherUserAgreed === 'boolean' ? base.otherUserAgreed : false,
        canAgree: typeof base.canAgree === 'boolean' ? base.canAgree : canRespond,
        canDecline: typeof base.canDecline === 'boolean' ? base.canDecline : canRespond
      }
    },
    hasContactExchange() {
      return this.contactExchange?.status === 'EXCHANGED'
    },
    hasContactShare() {
      return this.hasContactExchange
    },
    isContactExchangePending() {
      return ['REQUESTED', 'PARTIAL', 'PENDING'].includes(this.contactExchange?.status)
    },
    isContactExchangeRequestedByMe() {
      return this.isContactExchangePending &&
        Number(this.contactExchange?.requesterId || this.tradeInfo?.contactExchangeRequesterId) === Number(this.currentUserId)
    },
    contactExchangeButtonLabel() {
      if (this.hasContactExchange) return '✓ 已交换联系方式'
      if (this.isContactExchangeRequestedByMe) return '等待对方确认'
      return '交换联系方式'
    },
    canShareContact() {
      return !!(this.tradeInfo &&
        this.tradeInfo.status === 'TRADING' &&
        !this.hasContactExchange &&
        !this.contactExchange?.currentUserAgreed)
    },
    contactExchangeStatus() {
      return this.contactExchange?.status || 'NONE'
    },
    showContactQuickAction() {
      return !!this.curChat
    },
    contactPillText() {
      if (this.contactExchangeStatus === 'EXCHANGED') return '✓ 已交换联系方式'
      if (['REQUESTED', 'PENDING', 'PARTIAL'].includes(this.contactExchangeStatus)) return '等待对方确认'
      return '交换联系方式'
    },
    contactPillIconClass() {
      return this.contactExchangeStatus === 'EXCHANGED' ? '' : 'phone-pill-icon'
    },
    selectedContactTemplate() {
      return this.contactTemplates[this.selectedTemplateIndex] || null
    },
    tradeMenuActions() {
      if (!this.curChat) return []
      const actions = []
      if (this.tradeInfo?.isSeller || this.curChat.isSeller) {
        actions.push({ type: 'complete', label: '标记完成', class: 'success' })
      }
      actions.push({ type: 'cancel', label: '取消交易', class: 'danger' })
      return actions
    },
    contactExchangeStateCard() {
      if (!this.tradeInfo) return null
      const exchange = this.contactExchange
      const status = exchange?.status || 'NONE'
      if (!['REQUESTED', 'PARTIAL', 'PENDING'].includes(status)) return null
      if (this.isContactExchangeRequestedByMe || exchange.currentUserAgreed) return null
      return {
        type: 'contactExchange',
        tag: '交换请求',
        time: '',
        iconClass: 'icon-contact',
        title: '交换联系方式',
        desc: '对方请求交换联系方式，双方确认后可查看彼此分享的信息。',
        note: '确认后可查看对方分享的联系方式。',
        noteClass: 'note-success',
        cardClass: 'system-card contact-share-card',
        actions: [
          { type: 'agreeContact', label: '同意交换' },
          { type: 'declineContact', label: '暂不交换', class: 'secondary' }
        ]
      }
    },
    standaloneTradeCards() {
      if (!this.tradeInfo) return []
      if (this.tradeInfo.status === 'TRADING') {
        return this.contactExchangeStateCard ? [this.contactExchangeStateCard] : []
      }
      return []
    }
  },
  async onLoad(options) {
    this.loadCurrentUser()
    this.itemId = options.itemId
    this.targetUserId = options.targetUserId || options.buyerId || options.sellerId || null
    this.sessionId = options.sessionId
    this.routeOtherName = decodeOptionText(options.otherName)
    this.routeOtherAvatar = decodeOptionText(options.otherAvatar)
    await this.initChat()
    if (this.sessionId) {
      setActiveChatSession(this.sessionId)
      this.unsubscribeMessageStore = subscribeMessageStore((state, reason) => {
        if (reason === 'subscribe') return
        if (Number(state.activeChatSessionId) === Number(this.sessionId)) {
          this.syncActiveChat(reason)
        }
      })
      await refreshMessageState('chat-open')
    }
  },
  onShow() {
    this.loadCurrentUser()
  },
  onUnload() {
    if (this.unsubscribeMessageStore) {
      this.unsubscribeMessageStore()
      this.unsubscribeMessageStore = null
    }
    clearActiveChatSession(this.sessionId)
    refreshMessageState('chat-close')
  },
  methods: {
    loadCurrentUser() {
      const userInfo = getUserInfo() || null
      const nestedUser = userInfo ? (userInfo.user || userInfo.profile || userInfo.data || {}) : {}
      this.currentUserInfo = userInfo
        ? {
            ...userInfo,
            avatar: pickAvatar(userInfo) || pickAvatar(nestedUser)
          }
        : null
    },
    messageAvatar(message) {
      if (!message) return ''
      return pickSenderAvatar(message) ||
        this.curChat?.otherAvatar ||
        this.routeOtherAvatar ||
        buildDefaultAvatar({
          username: message.senderName || this.curChat?.otherName || 'chat-other'
        })
    },
    ownMessageAvatar(message) {
      return pickSenderAvatar(message) || this.ownAvatarUrl
    },
    async syncActiveChat(reason = 'message-sync') {
      if (!this.sessionId || this.messageSyncing) return
      this.messageSyncing = true
      try {
        await this.loadSession()
        await this.loadTradeInfo()
        await this.loadMessages()
        await refreshMessageState(`chat-read-${reason}`)
      } finally {
        this.messageSyncing = false
      }
    },
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
      const fallback = {
        ...(this.curChat || {}),
        id: this.sessionId,
        itemId: this.itemId || this.curChat?.itemId,
        otherUserId: this.targetUserId || this.curChat?.otherUserId,
        otherName: this.routeOtherName || this.curChat?.otherName || '聊天',
        otherAvatar: this.routeOtherAvatar || this.curChat?.otherAvatar || ''
      }
      if (!matched) {
        this.curChat = fallback
        return
      }
      const session = normalizeSession(matched)
      this.curChat = {
        ...fallback,
        ...session,
        otherName: session.otherName || fallback.otherName,
        otherAvatar: session.otherAvatar || fallback.otherAvatar
      }
    },
    async loadTradeInfo() {
      if (!this.curChat || !this.curChat.itemId) {
        this.tradeInfo = null
        return
      }
      try {
        const res = await getTradeRecords({ current: 1, size: 100 })
        const records = Array.isArray(res?.data?.records) ? res.data.records : []
        const itemRecords = records.filter((record) => Number(record.itemId) === Number(this.curChat.itemId))
        const currentSessionId = Number(this.sessionId || this.curChat.id)
        const otherUserId = Number(this.curChat.otherUserId)
        const sessionMatched = itemRecords.find((record) => {
          const recordSessionId = Number(record.sessionId || record.chatSessionId)
          return currentSessionId && recordSessionId === currentSessionId
        })
        const participantMatched = itemRecords.find((record) => {
          if (!otherUserId) return false
          return Number(record.buyerId) === otherUserId ||
            Number(record.sellerId) === otherUserId ||
            Number(record.buyerUserId) === otherUserId ||
            Number(record.sellerUserId) === otherUserId
        })
        const matched = sessionMatched || participantMatched || itemRecords[0]
        this.tradeInfo = matched
          ? {
              id: matched.id,
              buyerId: matched.buyerId,
              sellerId: matched.sellerId,
              status: matched.status,
              statusText: matched.statusText,
              isSeller: matched.isSeller,
              contactExchangeStatus: matched.contactExchangeStatus || '',
              contactExchangeRequesterId: matched.contactExchangeRequesterId,
              contactExchange: matched.contactExchange || null
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
        this.messages = sorted.map(normalizeMessage).filter((message) => {
          return !(message.messageType === 4 && !message.content)
        })
        this.$nextTick(() => {
          this.scrollBottom = 'bottom-spacer'
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
        await refreshMessageState('chat-send')
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
        await refreshMessageState('chat-send-image')
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
    async handleContactPillClick() {
      const status = this.contactExchangeStatus
      if (status === 'EXCHANGED') {
        uni.showToast({ title: '双方已交换联系方式，可继续线下沟通交易。', icon: 'none' })
        return
      }
      if (['REQUESTED', 'PENDING', 'PARTIAL'].includes(status)) {
        uni.showToast({ title: '等待对方确认', icon: 'none' })
        return
      }
      await this.sendContactInfo()
    },
    async runTradeAction(type) {
      if (this.acting) return
      if (type === 'contactDone' || type === 'contactWaiting') {
        uni.showToast({ title: type === 'contactDone' ? '已交换联系方式' : '等待对方确认', icon: 'none' })
        return
      }
      if (type === 'shareContact') {
        await this.sendContactInfo()
        return
      }
      if (type === 'agreeContact') {
        await this.sendContactInfo()
        return
      }
      if (type === 'declineContact') {
        await this.declineContactExchange()
        return
      }
      const actions = {
        confirm: confirmTradeRecord,
        complete: completeTradeRecord,
        cancel: cancelTradeRecord
      }
      const action = actions[type]
      if (!action) return
      try {
        this.acting = true
        let tradeId = this.tradeInfo?.id
        if (!tradeId && type === 'complete') {
          const ensured = await ensureTradeRecordBySession(this.sessionId)
          tradeId = ensured?.data?.id
        }
        if (!tradeId) {
          uni.showToast({ title: '当前暂无可操作的交易记录', icon: 'none' })
          return
        }
        await action(tradeId)
        uni.showToast({ title: '状态已更新', icon: 'success' })
        await this.loadSession()
        await this.loadTradeInfo()
        await this.loadMessages()
        await refreshMessageState('trade-action')
      } catch (e) {
        console.error('交易操作失败', e)
        uni.showToast({ title: e?.data?.msg || e?.msg || '操作失败', icon: 'none' })
      } finally {
        this.acting = false
      }
    },
    handleMenuAction(action) {
      this.tradeMenuVisible = false
      if (action.type === 'cancel') {
        this.cancelConfirmVisible = true
        return
      }
      if (action.type === 'complete') {
        this.completeConfirmVisible = true
        return
      }
      this.runTradeAction(action.type)
    },
    async confirmCancel() {
      this.cancelConfirmVisible = false
      await this.runTradeAction('cancel')
    },
    async confirmComplete() {
      this.completeConfirmVisible = false
      await this.runTradeAction('complete')
    },
    async sendContactInfo() {
      if (this.hasContactExchange) {
        uni.showToast({ title: '已交换联系方式', icon: 'none' })
        return
      }
      if (this.contactExchange?.currentUserAgreed) {
        uni.showToast({ title: '等待对方确认', icon: 'none' })
        return
      }
      this.contactMode = this.contactTemplates.length ? 'template' : 'custom'
      this.contactVisible = true
    },
    closeContactDialog() {
      this.contactVisible = false
    },
    async confirmSendContact() {
      try {
        if (this.hasContactExchange) {
          uni.showToast({ title: '已交换联系方式', icon: 'none' })
          return
        }
        if (this.contactExchange?.currentUserAgreed) {
          uni.showToast({ title: '等待对方确认', icon: 'none' })
          return
        }
        const contact = this.contactMode === 'template' ? this.selectedContactTemplate : this.normalizeContactForm()
        if (!this.hasContactContent(contact)) {
          uni.showToast({ title: '请至少填写一种联系方式', icon: 'none' })
          return
        }
        const content = this.buildContactContent(contact)
        if (!content) {
          uni.showToast({ title: '请选择并填写至少一种联系方式', icon: 'none' })
          return
        }
        await sendChatMessage({
          sessionId: Number(this.sessionId),
          content,
          messageType: 4,
          contactExchangeAction: 'AGREE'
        })
        uni.showToast({ title: '交换请求已发送', icon: 'success' })
        this.contactVisible = false
        this.morePanelVisible = false
        await this.loadSession()
        await this.loadTradeInfo()
        await this.loadMessages()
        await refreshMessageState('contact-exchange')
      } catch (e) {
        console.error('交换联系方式失败', e)
        uni.showToast({ title: e?.data?.msg || e?.msg || '交换失败', icon: 'none' })
      }
    },
    async declineContactExchange() {
      if (this.hasContactExchange) return
      try {
        this.acting = true
        await sendChatMessage({
          sessionId: Number(this.sessionId),
          content: '暂不交换',
          messageType: 4,
          contactExchangeAction: 'DECLINE'
        })
        uni.showToast({ title: '已暂不交换', icon: 'none' })
        await this.loadSession()
        await this.loadTradeInfo()
        await this.loadMessages()
        await refreshMessageState('contact-decline')
      } catch (e) {
        console.error('暂不交换失败', e)
        uni.showToast({ title: e?.data?.msg || e?.msg || '操作失败', icon: 'none' })
      } finally {
        this.acting = false
      }
    },
    loadSavedContact() {
      try {
        const templates = uni.getStorageSync('marketContactTemplates')
        this.contactTemplates = Array.isArray(templates) ? templates : []
        const legacy = uni.getStorageSync('marketContactInfo')
        if (!this.contactTemplates.length && legacy && typeof legacy === 'object' && this.hasContactContent(legacy)) {
          this.contactTemplates = [{
            id: Date.now(),
            name: '校园交易联系方式',
            wechat: legacy.wechat || '',
            phone: legacy.phone || '',
            qq: legacy.qq || ''
          }]
          this.saveContactTemplates()
        }
        this.savedContact = this.contactTemplates[0] || null
        this.selectedTemplateIndex = 0
      } catch (e) {
        this.contactTemplates = []
        this.savedContact = null
      }
    },
    hasContactContent(contact) {
      return !!(contact && (contact.wechat || contact.phone || contact.qq))
    },
    buildContactContent(contact) {
      const parts = []
      if (this.selectedContactFields.includes('wechat') && contact.wechat) parts.push(`微信：${contact.wechat}`)
      if (this.selectedContactFields.includes('phone') && contact.phone) parts.push(`手机号：${contact.phone}`)
      if (this.selectedContactFields.includes('qq') && contact.qq) parts.push(`QQ：${contact.qq}`)
      return parts.join('\n')
    },
    normalizeContactForm() {
      return {
        id: this.contactForm.id,
        name: (this.contactForm.name || '校园交易联系方式').trim(),
        wechat: (this.contactForm.wechat || '').trim(),
        phone: (this.contactForm.phone || '').trim(),
        qq: (this.contactForm.qq || '').trim()
      }
    },
    saveContactTemplates() {
      uni.setStorageSync('marketContactTemplates', this.contactTemplates)
      this.savedContact = this.contactTemplates[0] || null
    },
    saveContactTemplate() {
      const contact = this.normalizeContactForm()
      if (!this.hasContactContent(contact)) {
        uni.showToast({ title: '请至少填写一种联系方式', icon: 'none' })
        return
      }
      if (this.editingTemplateIndex !== null && this.editingTemplateIndex >= 0) {
        this.contactTemplates.splice(this.editingTemplateIndex, 1, { ...contact, id: contact.id || Date.now() })
        this.selectedTemplateIndex = this.editingTemplateIndex
      } else {
        this.contactTemplates.push({ ...contact, id: Date.now() })
        this.selectedTemplateIndex = this.contactTemplates.length - 1
      }
      this.saveContactTemplates()
      this.editingTemplateIndex = null
      this.contactMode = 'template'
      uni.showToast({ title: '模板已保存', icon: 'success' })
    },
    selectContactTemplate(index) {
      this.selectedTemplateIndex = index
    },
    startCustomContact() {
      this.contactMode = 'custom'
      this.editingTemplateIndex = null
      this.contactForm = { id: null, name: '校园交易联系方式', wechat: '', phone: '', qq: '' }
    },
    editContactTemplate() {
      const tpl = this.selectedContactTemplate
      if (!tpl) return
      this.editingTemplateIndex = this.selectedTemplateIndex
      this.contactForm = { ...tpl }
      this.contactMode = 'custom'
    },
    deleteContactTemplate() {
      if (!this.selectedContactTemplate) return
      this.contactTemplates.splice(this.selectedTemplateIndex, 1)
      this.selectedTemplateIndex = Math.max(0, this.selectedTemplateIndex - 1)
      this.saveContactTemplates()
      uni.showToast({ title: '模板已删除', icon: 'none' })
    },
    toggleContactField(key) {
      const index = this.selectedContactFields.indexOf(key)
      if (index >= 0) {
        if (this.selectedContactFields.length === 1) {
          uni.showToast({ title: '至少选择一种信息', icon: 'none' })
          return
        }
        this.selectedContactFields.splice(index, 1)
      } else {
        this.selectedContactFields.push(key)
      }
    },
    contactPreviewItems(contact) {
      return this.buildContactContent(contact).split('\n').filter(Boolean).map((line) => {
        const parts = line.split('：')
        return { label: parts.shift(), value: parts.join('：') }
      })
    },
    contactSummary(contact) {
      const items = this.contactPreviewItems(contact)
      return items.slice(0, 2).map((item) => `${item.label}：${item.value}`).join(' / ') || '未填写联系方式'
    },
    tradeActionTitle(action) {
      const map = {
        TRADE_CONFIRM: '已确认交易',
        CONTACT_EXCHANGE_DONE: '✓ 已交换联系方式',
        TRADE_COMPLETE: '交易完成',
        TRADE_CANCEL: '交易取消'
      }
      return map[action] || '交易状态'
    },
    tradeActionDesc(message) {
      const mine = !!message.isMine
      const map = {
        TRADE_CONFIRM: '双方已进入交易阶段，可交换联系方式并约定交易地点。',
        CONTACT_EXCHANGE_DONE: '双方已交换联系方式，可进行线下沟通交易。',
        TRADE_COMPLETE: mine ? '你已标记该商品交易完成。' : '该商品交易已完成。',
        TRADE_CANCEL: '交易已取消。'
      }
      return map[message.tradeAction] || message.content
    },
    tradeActionNote(message) {
      const map = {
        TRADE_CONFIRM: '建议双方确认交易后，再交换联系方式并约定线下交易。',
        CONTACT_EXCHANGE_DONE: '请线下沟通交易细节，注意人身与财产安全。'
      }
      return map[message.tradeAction] || ''
    },
    tradeActionNoteClass(message) {
      const map = {
        TRADE_CONFIRM: 'note-info',
        CONTACT_EXCHANGE_DONE: 'note-success'
      }
      return map[message.tradeAction] || 'note-muted'
    },
    tradeIconClass(action) {
      const map = {
        TRADE_CONFIRM: 'icon-confirm',
        CONTACT_EXCHANGE_DONE: 'icon-contact',
        TRADE_COMPLETE: 'icon-done',
        TRADE_CANCEL: 'icon-cancel'
      }
      return map[action] || 'icon-info'
    },
    tradeActorLabel(message) {
      if (message.tradeAction === 'CONTACT_EXCHANGE_DONE') return '双方确认'
      if (message.tradeAction === 'TRADE_COMPLETE') return message.isMine ? '我标记' : '对方标记'
      if (message.tradeAction === 'TRADE_CANCEL') return '系统通知'
      return message.isMine ? '我发起' : '对方发起'
    },
    tradeCardClass(message) {
      const actorClass = message.isMine ? 'mine-card' : 'other-card'
      const actionClassMap = {
        TRADE_CONFIRM: 'confirm-card',
        CONTACT_EXCHANGE_DONE: 'contact-share-card',
        TRADE_COMPLETE: 'done-card',
        TRADE_CANCEL: 'cancel-card'
      }
      return `${actorClass} ${actionClassMap[message.tradeAction] || 'system-card'}`
    },
    cardActions() {
      if (!this.tradeInfo) return []
      if (!ACTIVE_TRADE_CARD_STATUSES.includes(this.tradeInfo.status)) return []
      return []
    },
    systemLineText(message) {
      const mine = !!message.isMine
      if (message.content === '双方可以继续在平台内交流。') {
        return '双方暂未交换联系方式，可继续通过平台聊天。'
      }
      const map = {
        TRADE_INTENT: mine ? '你发起了交易请求' : '对方发起了交易请求',
        TRADE_CONFIRM: mine ? '你已确认线下交易' : '对方已确认与你交易',
        CONTACT_EXCHANGE_DONE: '双方已交换联系方式，可进行线下沟通交易。',
        TRADE_COMPLETE: '商品交易已完成',
        TRADE_CANCEL: '交易已取消'
      }
      return map[message.tradeAction] || ''
    },
    systemLineIconClass(message) {
      if (message.content === '双方可以继续在平台内交流。') return 'icon-contact-lock'
      const map = {
        TRADE_INTENT: 'icon-info',
        TRADE_CONFIRM: 'icon-deal',
        CONTACT_EXCHANGE_DONE: 'icon-link',
        TRADE_COMPLETE: 'icon-deal',
        TRADE_CANCEL: 'icon-undo'
      }
      return map[message.tradeAction] || 'icon-info'
    },
    contactItems(content) {
      return String(content || '').split(/\n/).filter(Boolean).map((line) => {
        const parts = line.split('：')
        if (parts.length >= 2) {
          const label = parts.shift().trim()
          return { label, value: parts.join('：').trim(), type: this.contactType(label) }
        }
        const legacyParts = line.split(':')
        if (legacyParts.length >= 2) {
          const label = legacyParts.shift().trim()
          return { label, value: legacyParts.join(':').trim(), type: this.contactType(label) }
        }
        return { label: '联系方式', value: line.trim(), type: 'unknown' }
      }).filter((item) => ['wechat', 'phone', 'qq'].includes(item.type) && item.value)
    },
    contactType(label) {
      const text = String(label || '').toLowerCase()
      if (text.includes('微信') || text.includes('wechat')) return 'wechat'
      if (text.includes('手机') || text.includes('电话') || text.includes('phone')) return 'phone'
      if (text.includes('qq')) return 'qq'
      return 'unknown'
    },
    maskContactValue(value) {
      const text = String(value || '')
      if (!text) return '****'
      return `****${text.slice(-2)}`
    },
    copyContact(item) {
      if (!item || !item.value) return
      uni.setClipboardData({
        data: item.value,
        success: () => {
          uni.showToast({ title: '已复制联系方式', icon: 'none' })
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

.trade-more-wrap {
  position: relative;
  flex-shrink: 0;
  margin-left: 8rpx;
}

.trade-more-btn {
  width: 52rpx;
  height: 52rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #5c7a99;
  font-size: 34rpx;
  font-weight: 900;
  line-height: 1;
}

.trade-menu-dropdown {
  position: absolute;
  top: 58rpx;
  right: 0;
  min-width: 200rpx;
  background: #fff;
  border-radius: 14rpx;
  box-shadow: 0 8rpx 28rpx rgba(43, 68, 94, 0.18);
  overflow: hidden;
  z-index: 50;
}

.trade-menu-item {
  padding: 22rpx 28rpx;
  font-size: 26rpx;
  font-weight: 700;
  color: #172331;
  border-bottom: 1rpx solid #edf3f8;
  white-space: nowrap;
}

.trade-menu-item:last-child {
  border-bottom: none;
}

.trade-menu-item.danger {
  color: #d14343;
}

.trade-menu-item.success {
  color: #2d8a55;
}

.trade-menu-item.muted {
  color: #7d8c9c;
}

.cancel-overlay {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0, 0, 0, 0.35);
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
}

.cancel-dialog {
  width: calc(100% - 96rpx);
  max-width: 520rpx;
  background: #fff;
  border-radius: 24rpx;
  padding: 40rpx 32rpx 28rpx;
  text-align: center;
}

.cancel-title {
  font-size: 30rpx;
  font-weight: 900;
  color: #172331;
}

.cancel-desc {
  margin-top: 14rpx;
  font-size: 24rpx;
  color: #7d8c9c;
  line-height: 1.5;
}

.complete-warning {
  margin-top: 16rpx;
  padding: 16rpx 18rpx;
  border-radius: 16rpx;
  background: #fff7ed;
  color: #a86824;
  font-size: 24rpx;
  font-weight: 800;
  line-height: 1.5;
  text-align: left;
}

.complete-desc {
  display: flex;
  flex-direction: column;
  gap: 6rpx;
  text-align: left;
}

.cancel-actions {
  display: flex;
  gap: 18rpx;
  margin-top: 32rpx;
}

.cancel-btn {
  flex: 1;
  height: 80rpx;
  border-radius: 20rpx;
  font-size: 26rpx;
  font-weight: 800;
}

.cancel-btn::after { border: none; }

.cancel-btn.secondary {
  background: #f3f6f8;
  color: #5c7a99;
}

.cancel-btn.primary {
  background: #d14343;
  color: #fff;
}

.chat-body {
  height: calc(100vh - 350rpx);
  padding: 28rpx 0 16rpx;
  box-sizing: border-box;
}

.last-msg {
  margin-bottom: 72rpx;
}

.bottom-spacer {
  height: 72rpx;
  flex-shrink: 0;
}

.system-msg,
.contact-msg {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  margin-bottom: 32rpx;
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
  position: relative;
  width: 596rpx;
  padding: 26rpx 28rpx 28rpx;
  border-radius: 24rpx;
  background: #fff;
  box-shadow: 0 10rpx 28rpx rgba(43, 68, 94, 0.12);
  box-sizing: border-box;
  overflow: hidden;
}

.mine-card {
  border-left: 8rpx solid #2f6dbb;
}

.other-card {
  border-left: 8rpx solid #d79a3d;
}

.confirm-card {
  border-left: 8rpx solid #d79a3d;
}

.contact-share-card,
.done-card {
  border-left: 8rpx solid #5aa66a;
}

.cancel-card,
.system-card {
  border-left: 8rpx solid #9aa9b8;
}

.trade-card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20rpx;
}

.trade-actor-tag {
  padding: 8rpx 18rpx;
  border-radius: 999rpx;
  background: #eaf2ff;
  color: #245fa5;
  font-size: 23rpx;
  font-weight: 900;
  line-height: 1.15;
}

.other-card .trade-actor-tag {
  background: #fff2dd;
  color: #a66b1f;
}

.confirm-card .trade-actor-tag {
  background: #fff2dd;
  color: #a66b1f;
}

.contact-share-card .trade-actor-tag,
.done-card .trade-actor-tag {
  background: #e9f6eb;
  color: #2f7c43;
}

.cancel-card .trade-actor-tag,
.system-card .trade-actor-tag {
  background: #f1f4f7;
  color: #657383;
}

.trade-time {
  color: #9aa9b8;
  font-size: 23rpx;
}

.trade-card-headline {
  display: flex;
  align-items: center;
  gap: 24rpx;
}

.trade-icon {
  width: 86rpx;
  height: 86rpx;
  border-radius: 24rpx;
  background: #edf5ff;
  color: #255f9f;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 34rpx;
  font-weight: 900;
  flex-shrink: 0;
}

.other-card .trade-icon,
.confirm-card .trade-icon {
  background: #fff2df;
  color: #9e671f;
}

.contact-share-card .trade-icon,
.done-card .trade-icon {
  background: #eaf7ed;
  color: #2f8346;
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

.icon-confirm::before,
.icon-done::before {
  left: 28rpx;
  top: 23rpx;
  width: 30rpx;
  height: 38rpx;
  border: 4rpx solid currentColor;
  border-radius: 11rpx 11rpx 14rpx 14rpx;
}

.icon-confirm::after,
.icon-done::after {
  left: 37rpx;
  top: 38rpx;
  width: 17rpx;
  height: 10rpx;
  border-left: 4rpx solid currentColor;
  border-bottom: 4rpx solid currentColor;
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
  left: 28rpx;
  top: 22rpx;
  width: 30rpx;
  height: 42rpx;
  border: 4rpx solid currentColor;
  border-radius: 18rpx;
  transform: rotate(-22deg);
}

.icon-contact::after {
  left: 38rpx;
  top: 29rpx;
  width: 10rpx;
  height: 28rpx;
  border-top: 4rpx solid currentColor;
  border-bottom: 4rpx solid currentColor;
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
  left: 41rpx;
  top: 25rpx;
  width: 4rpx;
  height: 36rpx;
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
  left: 41rpx;
  top: 35rpx;
  width: 4rpx;
  height: 25rpx;
  border-left: 4rpx solid currentColor;
}

.icon-info::after {
  left: 39rpx;
  top: 24rpx;
  width: 8rpx;
  height: 8rpx;
  border-radius: 50%;
  background: currentColor;
}

.system-line-icon.icon-info::before {
  left: 13rpx;
  top: 11rpx;
  width: 2rpx;
  height: 11rpx;
  border-left-width: 2rpx;
}

.system-line-icon.icon-info::after {
  left: 11rpx;
  top: 5rpx;
  width: 6rpx;
  height: 6rpx;
}

.system-line-icon.icon-link::before,
.system-line-icon.icon-link::after {
  top: 9rpx;
  width: 13rpx;
  height: 9rpx;
  border: 2rpx solid currentColor;
  border-radius: 999rpx;
  background: transparent;
}

.system-line-icon.icon-link::before {
  left: 3rpx;
  transform: rotate(-28deg);
}

.system-line-icon.icon-link::after {
  right: 3rpx;
  transform: rotate(-28deg);
}

.system-line-icon.icon-undo::before {
  left: 6rpx;
  top: 8rpx;
  width: 16rpx;
  height: 12rpx;
  border: 2rpx solid currentColor;
  border-left-color: transparent;
  border-radius: 999rpx;
  background: transparent;
}

.system-line-icon.icon-undo::after {
  left: 4rpx;
  top: 7rpx;
  width: 8rpx;
  height: 8rpx;
  border-left: 2rpx solid currentColor;
  border-bottom: 2rpx solid currentColor;
  transform: rotate(45deg);
  background: transparent;
}

.system-line-icon.icon-deal::before {
  left: 4rpx;
  top: 11rpx;
  width: 20rpx;
  height: 8rpx;
  border: 2rpx solid currentColor;
  border-radius: 999rpx;
  background: transparent;
}

.system-line-icon.icon-deal::after {
  left: 12rpx;
  top: 8rpx;
  width: 4rpx;
  height: 13rpx;
  border-left: 2rpx solid currentColor;
  transform: rotate(45deg);
  background: transparent;
}

.system-line-icon.icon-contact-lock::before {
  left: 6rpx;
  top: 11rpx;
  width: 16rpx;
  height: 11rpx;
  border: 2rpx solid currentColor;
  border-radius: 4rpx;
  background: transparent;
}

.system-line-icon.icon-contact-lock::after {
  left: 10rpx;
  top: 5rpx;
  width: 8rpx;
  height: 9rpx;
  border: 2rpx solid currentColor;
  border-bottom: 0;
  border-radius: 999rpx 999rpx 0 0;
  background: transparent;
}
.trade-copy {
  flex: 1;
  min-width: 0;
}

.trade-event-title,
.contact-title {
  color: #172331;
  font-size: 34rpx;
  font-weight: 900;
  line-height: 1.25;
}

.trade-event-desc {
  margin-top: 16rpx;
  margin-left: 110rpx;
  color: #3f4d5c;
  font-size: 25rpx;
  line-height: 1.6;
  white-space: pre-line;
}

.trade-note {
  display: flex;
  align-items: flex-start;
  gap: 14rpx;
  margin-top: 22rpx;
  padding: 16rpx 18rpx;
  border-radius: 16rpx;
  background: #f3f7fb;
  color: #536579;
  font-size: 23rpx;
  font-weight: 800;
  line-height: 1.45;
  box-sizing: border-box;
}

.trade-note text {
  flex: 1;
  min-width: 0;
}

.trade-note-icon {
  position: relative;
  width: 30rpx;
  height: 30rpx;
  border: 3rpx solid currentColor;
  border-radius: 50%;
  box-sizing: border-box;
  flex-shrink: 0;
  margin-top: 2rpx;
}

.trade-note-icon::before {
  content: '';
  position: absolute;
  left: 10.5rpx;
  top: 10rpx;
  width: 3rpx;
  height: 10rpx;
  border-radius: 999rpx;
  background: currentColor;
}

.trade-note-icon::after {
  content: '';
  position: absolute;
  left: 10.5rpx;
  top: 5rpx;
  width: 3rpx;
  height: 3rpx;
  border-radius: 50%;
  background: currentColor;
}

.note-info {
  background: #eef5ff;
  color: #2e6096;
}

.note-warning {
  background: #fff5e8;
  color: #9d6625;
}

.note-success,
.contact-safety-note {
  background: #edf8ef;
  color: #357b48;
}

.note-muted {
  background: #f2f5f8;
  color: #607284;
}

.contact-list {
  margin-top: 30rpx;
}

.contact-card.contact-share-card {
  width: 660rpx;
  padding: 30rpx 34rpx 34rpx;
  border-left: none;
  border-radius: 34rpx;
  box-shadow: 0 18rpx 44rpx rgba(28, 42, 58, 0.13);
}

.contact-card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 34rpx;
}

.contact-confirm-tag {
  display: inline-flex;
  align-items: center;
  gap: 10rpx;
  height: 50rpx;
  padding: 0 18rpx;
  border-radius: 999rpx;
  background: #e6f5e9;
  color: #2d8a45;
  font-size: 25rpx;
  font-weight: 900;
  line-height: 50rpx;
}

.contact-confirm-dot {
  position: relative;
  width: 30rpx;
  height: 30rpx;
  border-radius: 50%;
  background: #4aa557;
  flex-shrink: 0;
}

.contact-confirm-dot::after {
  content: '';
  position: absolute;
  left: 8rpx;
  top: 8rpx;
  width: 12rpx;
  height: 7rpx;
  border-left: 4rpx solid #fff;
  border-bottom: 4rpx solid #fff;
  transform: rotate(-45deg);
}

.contact-card-time {
  color: #8d97a6;
  font-size: 28rpx;
  font-weight: 500;
}

.contact-hero {
  display: flex;
  align-items: center;
  gap: 28rpx;
  margin: 0 18rpx 28rpx;
}

.contact-hero-icon {
  position: relative;
  width: 132rpx;
  height: 118rpx;
  flex-shrink: 0;
}

.contact-shield {
  position: absolute;
  left: 28rpx;
  top: 12rpx;
  width: 72rpx;
  height: 82rpx;
  border-radius: 40rpx 40rpx 34rpx 34rpx;
  background: linear-gradient(145deg, #7ed488, #2f9846);
  box-shadow: 0 12rpx 24rpx rgba(48, 151, 69, 0.24);
  z-index: 2;
}

.contact-shield::before {
  content: '';
  position: absolute;
  left: 20rpx;
  top: 28rpx;
  width: 32rpx;
  height: 18rpx;
  border-left: 7rpx solid #fff;
  border-bottom: 7rpx solid #fff;
  transform: rotate(-45deg);
  border-radius: 2rpx;
}

.contact-shield::after {
  content: '';
  position: absolute;
  left: -18rpx;
  top: 16rpx;
  width: 92rpx;
  height: 92rpx;
  border-radius: 28rpx;
  background: rgba(74, 165, 87, 0.10);
  z-index: -1;
}

.contact-orbit {
  position: absolute;
  left: 0;
  bottom: 8rpx;
  width: 118rpx;
  height: 34rpx;
  border-bottom: 3rpx dashed rgba(84, 178, 98, 0.55);
  border-radius: 50%;
  transform: rotate(-10deg);
}

.contact-spark {
  position: absolute;
  right: 8rpx;
  top: 6rpx;
  width: 18rpx;
  height: 18rpx;
  color: #a8dfb1;
}

.contact-spark::before,
.contact-spark::after {
  content: '';
  position: absolute;
  background: currentColor;
  border-radius: 999rpx;
}

.contact-spark::before {
  left: 7rpx;
  top: 0;
  width: 4rpx;
  height: 18rpx;
}

.contact-spark::after {
  left: 0;
  top: 7rpx;
  width: 18rpx;
  height: 4rpx;
}

.contact-hero-copy {
  flex: 1;
  min-width: 0;
}

.contact-card .contact-title {
  color: #101827;
  font-size: 40rpx;
  line-height: 1.25;
  font-weight: 900;
}

.contact-subtitle {
  margin-top: 12rpx;
  color: #8d97a6;
  font-size: 26rpx;
  font-weight: 700;
}

.contact-card .contact-list {
  margin-top: 24rpx;
  padding: 24rpx 30rpx;
  border: 1rpx solid #e2e7ed;
  border-radius: 22rpx;
  box-sizing: border-box;
}

.contact-type-icon {
  position: relative;
  width: 64rpx;
  height: 64rpx;
  border-radius: 18rpx;
  background: #e9f6eb;
  color: #328742;
  flex-shrink: 0;
}

.contact-type-icon--wechat::before,
.contact-type-icon--wechat::after {
  content: '';
  position: absolute;
  border-radius: 50%;
  background: currentColor;
}

.contact-type-icon--wechat::before {
  left: 13rpx;
  top: 18rpx;
  width: 30rpx;
  height: 24rpx;
}

.contact-type-icon--wechat::after {
  right: 12rpx;
  top: 25rpx;
  width: 24rpx;
  height: 20rpx;
  box-shadow: -23rpx -4rpx 0 -9rpx #e9f6eb, -13rpx -4rpx 0 -9rpx #e9f6eb, -4rpx 4rpx 0 -8rpx #e9f6eb, 5rpx 4rpx 0 -8rpx #e9f6eb;
}

.contact-type-icon--phone::before {
  content: '';
  position: absolute;
  left: 20rpx;
  top: 12rpx;
  width: 24rpx;
  height: 40rpx;
  border: 5rpx solid currentColor;
  border-radius: 8rpx;
  box-sizing: border-box;
}

.contact-type-icon--phone::after {
  content: '';
  position: absolute;
  left: 29rpx;
  bottom: 17rpx;
  width: 6rpx;
  height: 6rpx;
  border-radius: 50%;
  background: currentColor;
}

.contact-type-icon--qq::before {
  content: '';
  position: absolute;
  left: 18rpx;
  top: 13rpx;
  width: 28rpx;
  height: 38rpx;
  border-radius: 48% 48% 42% 42%;
  background: currentColor;
}

.contact-type-icon--qq::after {
  content: '';
  position: absolute;
  left: 12rpx;
  bottom: 10rpx;
  width: 40rpx;
  height: 12rpx;
  border-radius: 50%;
  background: currentColor;
}

.contact-row-divider {
  width: 1rpx;
  height: 36rpx;
  background: #e6ebf0;
  flex-shrink: 0;
}

.trade-card-actions {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 18rpx;
  margin-top: 26rpx;
}

.trade-card-btn {
  min-width: 176rpx;
  height: 62rpx;
  margin: 0;
  padding: 0 28rpx;
  border-radius: 999rpx;
  background: #7ba8d4;
  color: #fff;
  font-size: 25rpx;
  font-weight: 900;
  line-height: 62rpx;
  box-shadow: 0 8rpx 18rpx rgba(48, 100, 170, 0.18);
}

.trade-card-btn.confirm {
  background: #d39536;
  color: #fff;
  box-shadow: 0 8rpx 18rpx rgba(173, 108, 27, 0.18);
}

.trade-card-btn.shareContact {
  background: #2f6dbb;
  color: #fff;
}

.trade-card-btn.agreeContact {
  background: #3d934f;
  color: #fff;
  box-shadow: 0 8rpx 18rpx rgba(50, 126, 67, 0.16);
}

.trade-card-btn.declineContact,
.trade-card-btn.secondary {
  background: #f2f6fa;
  color: #65788c;
  box-shadow: none;
}

.trade-card-btn.complete {
  background: #7fb59b;
  color: #fff;
}

.trade-card-btn.contactDone,
.trade-card-btn.contactWaiting {
  background: #f2f6fa;
  color: #65788c;
}

.trade-card-btn::after {
  border: none;
}

.contact-row {
  display: flex;
  align-items: center;
  gap: 22rpx;
  padding: 24rpx 0;
  border-bottom: 1rpx solid #e6ebf0;
}

.contact-row:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.contact-row-main {
  flex: 1;
  min-width: 0;
}

.contact-label {
  color: #1f2937;
  font-size: 27rpx;
  font-weight: 800;
}

.contact-value {
  margin-top: 10rpx;
  color: #626e7f;
  font-size: 30rpx;
  font-weight: 900;
  word-break: break-all;
  letter-spacing: 2rpx;
}

.contact-value.hidden {
  color: #8a94a0;
  letter-spacing: 4rpx;
}

.contact-actions {
  display: flex;
  align-items: center;
  gap: 18rpx;
  flex-shrink: 0;
}

.eye-toggle {
  width: 62rpx;
  height: 62rpx;
  position: relative;
  flex-shrink: 0;
  color: #2f8a45;
  border: 1rpx solid #e2e7ed;
  border-radius: 16rpx;
  background: #fff;
  box-sizing: border-box;
}

.eye-toggle::before {
  content: '';
  position: absolute;
  top: 20rpx;
  left: 14rpx;
  width: 34rpx;
  height: 22rpx;
  border: 3rpx solid currentColor;
  border-radius: 50%;
  box-sizing: border-box;
}

.eye-toggle::after {
  content: '';
  position: absolute;
  top: 27rpx;
  left: 27rpx;
  width: 8rpx;
  height: 8rpx;
  border-radius: 50%;
  background: currentColor;
  box-sizing: border-box;
}

.eye-slash {
  position: absolute;
  top: 14rpx;
  left: 30rpx;
  width: 3rpx;
  height: 34rpx;
  border-radius: 999rpx;
  background: currentColor;
  transform: rotate(45deg);
  transform-origin: center;
}

.eye-toggle.visible .eye-slash {
  display: none;
}

.copy-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 62rpx;
  height: 62rpx;
  margin: 0;
  padding: 0;
  border: 1rpx solid #e2e7ed;
  border-radius: 16rpx;
  background: #fff;
  color: #2f8a45;
  line-height: 62rpx;
  flex-shrink: 0;
  box-sizing: border-box;
}

.copy-icon {
  position: relative;
  width: 32rpx;
  height: 32rpx;
  flex-shrink: 0;
}

.copy-icon::before,
.copy-icon::after {
  content: '';
  position: absolute;
  width: 18rpx;
  height: 18rpx;
  border: 3rpx solid currentColor;
  border-radius: 4rpx;
  box-sizing: border-box;
}

.copy-icon::before {
  left: 3rpx;
  top: 11rpx;
}

.copy-icon::after {
  left: 11rpx;
  top: 3rpx;
  background: transparent;
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
  padding-right: 0;
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
  overflow: hidden;
}

.mava-img {
  width: 100%;
  height: 100%;
  display: block;
}

.mbub {
  padding: 20rpx 28rpx;
  border-radius: 24rpx;
  font-size: 28rpx;
  max-width: 100%;
  word-break: break-all;
  line-height: 1.5;
}

.mbub-img {
  padding: 0;
  background: transparent !important;
  border-radius: 0;
}

.chat-img {
  width: 280rpx;
  max-height: 360rpx;
  border-radius: 12rpx;
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
  background: transparent;
  padding: 14rpx 32rpx 24rpx;
  box-sizing: border-box;
  z-index: 10;
}

.quick-action-row {
  display: flex;
  align-items: center;
  gap: 18rpx;
  margin-bottom: 18rpx;
  padding: 0;
  background: transparent;
  box-shadow: none;
}

.quick-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
  width: 252rpx;
  height: 64rpx;
  padding: 0 24rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.9);
  color: #546374;
  font-size: 24rpx;
  font-weight: 900;
  line-height: 64rpx;
  box-shadow: 0 6rpx 16rpx rgba(43, 68, 94, 0.06);
  box-sizing: border-box;
  white-space: nowrap;
}

.contact-pill {
  background: #edf4fb;
  color: #5c86bd;
}

.contact-pill--exchanged {
  width: auto;
  padding: 0 24rpx;
  background: #eef7f1;
  color: #4f8a69;
}

.quick-pill-icon {
  position: relative;
  width: 30rpx;
  height: 30rpx;
  flex-shrink: 0;
  color: currentColor;
}

.phone-pill-icon::before {
  content: '';
  position: absolute;
  inset: 0;
  background: currentColor;
  -webkit-mask: url('/static/icons/mynaui--telephone-out.svg') center / contain no-repeat;
  mask: url('/static/icons/mynaui--telephone-out.svg') center / contain no-repeat;
}

.phone-pill-icon::after {
  display: none;
}

.more-panel-wrapper {
  max-height: 0;
  overflow: hidden;
  opacity: 0;
  transform: translateY(14rpx);
  transition:
    max-height 260ms cubic-bezier(0.22, 1, 0.36, 1),
    opacity 220ms ease,
    transform 260ms cubic-bezier(0.22, 1, 0.36, 1);
  pointer-events: none;
}

.more-panel-wrapper.open {
  max-height: 174rpx;
  opacity: 1;
  transform: translateY(0);
  pointer-events: auto;
}

.more-panel {
  display: flex;
  gap: 18rpx;
  margin-top: 14rpx;
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
  inset: 2rpx;
  background: currentColor;
  -webkit-mask: url('/static/icons/proicons--photo.svg') center / contain no-repeat;
  mask: url('/static/icons/proicons--photo.svg') center / contain no-repeat;
}

.image-icon::after {
  display: none;
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
  max-height: 82vh;
  padding: 30rpx;
  border-radius: 24rpx;
  background: #fff;
  box-sizing: border-box;
  overflow-y: auto;
}

.dialog-title {
  color: #172331;
  font-size: 31rpx;
  font-weight: 900;
  margin-bottom: 22rpx;
}

.contact-choice-title {
  margin-bottom: 12rpx;
  color: #65788c;
  font-size: 23rpx;
  font-weight: 800;
}

.contact-choice-list {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12rpx;
  margin-bottom: 20rpx;
}

.contact-choice-item {
  display: flex;
  align-items: center;
  gap: 12rpx;
  min-height: 58rpx;
  padding: 0 16rpx;
  border-radius: 16rpx;
  background: #f2f6fa;
  color: #65788c;
  font-size: 24rpx;
  font-weight: 800;
  box-sizing: border-box;
}

.contact-choice-item.selected {
  background: #edf4fb;
  color: #172331;
}

.contact-checkbox {
  position: relative;
  width: 28rpx;
  height: 28rpx;
  border: 3rpx solid #9aa9b8;
  border-radius: 7rpx;
  box-sizing: border-box;
  flex-shrink: 0;
}

.contact-choice-item.selected .contact-checkbox {
  border-color: #5c7894;
  background: #7ba8d4;
}

.contact-choice-item.selected .contact-checkbox::after {
  content: '';
  position: absolute;
  left: 6rpx;
  top: 3rpx;
  width: 10rpx;
  height: 6rpx;
  border-left: 3rpx solid #fff;
  border-bottom: 3rpx solid #fff;
  transform: rotate(-45deg);
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

.contact-tabs {
  display: flex;
  gap: 12rpx;
  margin-bottom: 20rpx;
  padding: 8rpx;
  border-radius: 18rpx;
  background: #f2f6fa;
}

.contact-tab {
  flex: 1;
  height: 58rpx;
  border-radius: 14rpx;
  color: #65788c;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24rpx;
  font-weight: 900;
}

.contact-tab.active {
  background: #fff;
  color: #172331;
  box-shadow: 0 3rpx 10rpx rgba(43, 68, 94, 0.08);
}

.template-list {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  margin-bottom: 16rpx;
}

.template-item {
  padding: 18rpx;
  border-radius: 18rpx;
  border: 1rpx solid rgba(101, 120, 140, 0.14);
  background: #fff;
}

.template-item.selected {
  border-color: rgba(92, 138, 184, 0.55);
  background: #f6f9fc;
}

.template-name {
  color: #172331;
  font-size: 26rpx;
  font-weight: 900;
}

.template-summary,
.empty-template {
  margin-top: 8rpx;
  color: #7d8c9c;
  font-size: 22rpx;
  line-height: 1.45;
}

.contact-preview {
  margin: 16rpx 0;
  padding: 18rpx;
  border-radius: 18rpx;
  background: #f6f9fc;
}

.preview-title {
  color: #172331;
  font-size: 24rpx;
  font-weight: 900;
  margin-bottom: 10rpx;
}

.preview-row {
  display: flex;
  gap: 16rpx;
  padding: 8rpx 0;
}

.preview-label {
  width: 120rpx;
  color: #7d8c9c;
  font-size: 22rpx;
  flex-shrink: 0;
}

.preview-value {
  color: #172331;
  font-size: 23rpx;
  font-weight: 800;
  word-break: break-all;
}

.template-actions {
  display: flex;
  gap: 12rpx;
  margin-bottom: 16rpx;
}

.template-actions .dialog-btn {
  height: 60rpx;
  border-radius: 16rpx;
  font-size: 23rpx;
  line-height: 60rpx;
}

.dialog-btn.danger {
  color: #a65f5f;
}

.save-template-btn {
  width: 100%;
  height: 66rpx;
  margin: 4rpx 0 16rpx;
  border-radius: 18rpx;
  background: #f2f6fa;
  color: #5c7894;
  font-size: 25rpx;
  font-weight: 900;
  line-height: 66rpx;
}

.save-template-btn::after {
  border: none;
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
