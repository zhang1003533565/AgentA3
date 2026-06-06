<template>
  <view class="conversation-page">
    <nav-bar title="AI 会话" :showBack="true" fixed placeholder />

    <view class="conversation-actions">
      <view class="conversation-action" @click="openHistory">历史对话</view>
      <view class="conversation-action conversation-action--primary" @click="startNewConversation">新对话</view>
    </view>

    <scroll-view
      class="message-list"
      scroll-y
      :scroll-into-view="scrollAnchor"
    >
      <view v-if="messages.length === 0" class="conversation-empty">
        <text class="conversation-empty__title">和 Leader 开始聊吧</text>
        <text class="conversation-empty__desc">这里会保存成一条历史会话，下次可以从个人中心继续打开。</text>
      </view>

      <view
        v-for="(message, index) in messages"
        :key="message.localId || message.id"
        :id="`msg-${index}`"
        class="message-row"
        :class="message.role === 'user' ? 'message-row--user' : 'message-row--assistant'"
      >
        <view class="message-bubble" :class="message.role === 'user' ? 'message-bubble--user' : 'message-bubble--assistant'">
          <view v-if="message.type === 'thinking'" class="thinking-indicator">
            <text class="thinking-text">思考中</text>
            <view class="thinking-dots">
              <text></text>
              <text></text>
              <text></text>
            </view>
          </view>
          <text v-else class="message-text">{{ message.content }}</text>
        </view>
      </view>
      <view id="message-anchor"></view>
    </scroll-view>

    <view class="composer">
      <textarea
        v-model="inputValue"
        class="composer-input"
        placeholder="继续问 Leader 智能助手"
        maxlength="4000"
        auto-height
        confirm-type="send"
        :confirm-hold="true"
        :disabled="sending"
        @confirm="handleInputConfirm"
        @keydown.enter="handleEnterKey"
      />
      <view class="send-btn" :class="{ disabled: !canSend }" @click="sendMessage">发送</view>
    </view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { getLeaderSessionDetail, queryLeaderAgent, streamLeaderAgent } from '@/api/ai.js'

const STORAGE_KEY = 'aiAssistantSessionId'

export default {
  components: { NavBar },
  data() {
    return {
      sessionId: '',
      messages: [],
      inputValue: '',
      sending: false,
      scrollAnchor: 'message-anchor'
    }
  },
  computed: {
    canSend() {
      return !this.sending && this.inputValue.trim().length > 0
    }
  },
  onLoad(options) {
    this.sessionId = options.sessionId ? decodeURIComponent(options.sessionId) : ''
    if (this.sessionId) {
      uni.setStorageSync(STORAGE_KEY, this.sessionId)
    }
    this.loadDetail()
  },
  methods: {
    async loadDetail() {
      if (!this.sessionId) return
      try {
        const res = await getLeaderSessionDetail(this.sessionId)
        const data = res?.data || {}
        this.messages = (data.messages || []).map((item) => ({
          ...item,
          localId: `${item.role}-${item.id}`
        }))
        this.scrollToBottom()
      } catch (error) {
        this.messages = []
      }
    },
    appendMessage(message) {
      const item = {
        localId: `${message.role}-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
        ...message
      }
      this.messages.push(item)
      this.scrollToBottom()
      return item
    },
    async sendMessage() {
      const text = this.inputValue.trim()
      if (!text || !this.canSend) return
      this.inputValue = ''
      this.sending = true
      this.appendMessage({ role: 'user', content: text })
      const thinkingMessage = this.appendMessage({ role: 'assistant', type: 'thinking', content: '思考中' })
      let streamStarted = false
      let streamTouched = false
      try {
        await streamLeaderAgent({
          sessionId: this.sessionId,
          agentName: 'leader_agent',
          input: text
        }, {
          onEvent: () => {
            streamTouched = true
          },
          onSession: (payload) => {
            streamTouched = true
            this.syncSessionId(payload?.sessionId)
          },
          onDelta: (content) => {
            if (!content) return
            streamTouched = true
            streamStarted = true
            this.appendMessageContent(thinkingMessage.localId, content)
          },
          onDone: (payload) => {
            streamTouched = true
            this.syncSessionId(payload?.sessionId)
            const finalAnswer = payload?.answer || ''
            const current = this.messages.find((item) => item.localId === thinkingMessage.localId)
            if (finalAnswer && current && current.content !== finalAnswer) {
              this.replaceMessage(thinkingMessage.localId, {
                role: 'assistant',
                content: finalAnswer,
                answerType: payload.answerType || 'text'
              })
            } else if (!current || current.type === 'thinking') {
              this.replaceMessage(thinkingMessage.localId, {
                role: 'assistant',
                content: finalAnswer || 'Leader 这次没有返回可用答案，请换一种问法再试。',
                answerType: payload?.answerType || 'text'
              })
            }
          },
          onError: (payload) => {
            streamTouched = true
            throw new Error(payload?.message || '流式请求失败')
          }
        })
      } catch (error) {
        if (streamStarted || streamTouched) {
          this.replaceMessage(thinkingMessage.localId, {
            role: 'assistant',
            content: `这次流式回复中断了：${error?.message || error?.msg || '请稍后再试'}`
          })
        } else if (error?.fallbackToNormalRequest) {
          await this.sendMessageFallback(text, thinkingMessage.localId, error)
        } else {
          this.replaceMessage(thinkingMessage.localId, {
            role: 'assistant',
            content: `这次没有顺利完成请求：${error?.message || error?.msg || '请稍后再试'}`
          })
        }
      } finally {
        this.sending = false
      }
    },
    async sendMessageFallback(text, localId, streamError) {
      try {
        const res = await queryLeaderAgent({
          sessionId: this.sessionId,
          agentName: 'leader_agent',
          input: text
        })
        const payload = res?.data || {}
        this.syncSessionId(payload.sessionId)
        this.replaceMessage(localId, {
          role: 'assistant',
          content: payload.answer || 'Leader 这次没有返回可用答案，请换一种问法再试。',
          answerType: payload.answerType || 'text'
        })
      } catch (error) {
        this.replaceMessage(localId, {
          role: 'assistant',
          content: `这次没有顺利完成请求：${error?.message || error?.msg || streamError?.message || '请稍后再试'}`
        })
      }
    },
    replaceMessage(localId, message) {
      const index = this.messages.findIndex((item) => item.localId === localId)
      if (index === -1) {
        this.removeThinkingMessages()
        this.appendMessage(message)
        return
      }
      this.messages.splice(index, 1, {
        localId,
        ...message
      })
      this.scrollToBottom()
    },
    appendMessageContent(localId, content) {
      const index = this.messages.findIndex((item) => item.localId === localId)
      if (index === -1) return
      const current = this.messages[index]
      this.messages.splice(index, 1, {
        ...current,
        type: '',
        content: `${current.type === 'thinking' ? '' : current.content || ''}${content}`
      })
      this.scrollToBottom()
    },
    removeThinkingMessages() {
      const nextMessages = this.messages.filter((item) => item.type !== 'thinking')
      if (nextMessages.length === this.messages.length) return
      this.messages = nextMessages
      this.scrollToBottom()
    },
    syncSessionId(sessionId) {
      if (!sessionId) return
      this.sessionId = sessionId
      uni.setStorageSync(STORAGE_KEY, sessionId)
    },
    openHistory() {
      uni.redirectTo({ url: '/subpackage_ai/aiHistory/aiHistory' })
    },
    startNewConversation() {
      if (this.sending) return
      this.sessionId = ''
      this.messages = []
      this.inputValue = ''
      uni.removeStorageSync(STORAGE_KEY)
      this.scrollToBottom()
    },
    handleInputConfirm() {
      this.sendMessage()
    },
    handleEnterKey(event) {
      if (event?.shiftKey) {
        return
      }
      event?.preventDefault?.()
      this.sendMessage()
    },
    scrollToBottom() {
      this.$nextTick(() => {
        this.scrollAnchor = ''
        this.$nextTick(() => {
          this.scrollAnchor = 'message-anchor'
        })
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.conversation-page {
  height: 100vh;
  background: #F7F7F9;
  display: flex;
  flex-direction: column;
  padding-bottom: 116rpx;
  box-sizing: border-box;
  overflow: hidden;
}

.conversation-actions {
  flex-shrink: 0;
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 14rpx 24rpx 12rpx;
  background: #F7F7F9;
  box-sizing: border-box;
  width: 100%;
}

.composer > .composer-actions {
  display: none !important;
}

.conversation-action {
  flex: 1;
  height: 58rpx;
  border-radius: 999rpx;
  background: #FFFFFF;
  color: #5B6472;
  font-size: 24rpx;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8rpx 18rpx rgba(31, 41, 55, 0.04);
}

.conversation-action--primary {
  background: #E8F1FF;
  color: #2F6FE4;
}

.message-list {
  flex: 1;
  height: 0;
  min-height: 0;
  padding: 24rpx 24rpx 32rpx;
  box-sizing: border-box;
}

.conversation-empty {
  margin: 180rpx 28rpx 0;
  padding: 44rpx 34rpx;
  border-radius: 30rpx;
  background:
    radial-gradient(circle at top right, rgba(90, 155, 255, 0.16), transparent 34%),
    #FFFFFF;
  border: 1rpx solid rgba(47, 111, 228, 0.08);
  text-align: center;
  box-shadow: 0 14rpx 34rpx rgba(72, 103, 163, 0.08);
}

.conversation-empty__title {
  display: block;
  font-size: 32rpx;
  font-weight: 800;
  color: #1D1D1F;
}

.conversation-empty__desc {
  display: block;
  margin-top: 14rpx;
  font-size: 25rpx;
  line-height: 1.65;
  color: #6B7280;
}

.message-row {
  display: flex;
  margin-bottom: 18rpx;
}

.message-row--user {
  justify-content: flex-end;
}

.message-row--assistant {
  justify-content: flex-start;
}

.message-bubble {
  max-width: 82%;
  padding: 20rpx 24rpx;
  border-radius: 24rpx;
}

.message-bubble--user {
  background: linear-gradient(135deg, #3D7DF5, #69A6FF);
  color: #FFFFFF;
}

.message-bubble--assistant {
  background: #FFFFFF;
  color: #1F2933;
  box-shadow: 0 10rpx 24rpx rgba(72, 103, 163, 0.08);
}

.message-text {
  font-size: 28rpx;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-all;
}

.thinking-indicator {
  display: flex;
  align-items: center;
  gap: 14rpx;
}

.thinking-text {
  font-size: 26rpx;
  color: #6B7280;
}

.thinking-dots {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.thinking-dots text {
  width: 10rpx;
  height: 10rpx;
  border-radius: 50%;
  background: #8CB6FF;
  animation: thinking-bounce 1s ease-in-out infinite;
}

.thinking-dots text:nth-child(2) {
  animation-delay: 0.16s;
}

.thinking-dots text:nth-child(3) {
  animation-delay: 0.32s;
}

@keyframes thinking-bounce {
  0%, 80%, 100% {
    opacity: 0.35;
    transform: translateY(0);
  }
  40% {
    opacity: 1;
    transform: translateY(-6rpx);
  }
}

.composer {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 20;
  display: flex;
  align-items: flex-end;
  gap: 16rpx;
  flex-wrap: nowrap;
  padding: 18rpx 22rpx calc(18rpx + env(safe-area-inset-bottom));
  background: #FFFFFF;
  border-top: 1rpx solid #ECEFF5;
  box-sizing: border-box;
  box-shadow: 0 -12rpx 28rpx rgba(31, 41, 55, 0.05);
}

.composer-input {
  flex: 1;
  min-height: 76rpx;
  max-height: 220rpx;
  padding: 18rpx 20rpx;
  box-sizing: border-box;
  background: #F5F7FB;
  border-radius: 22rpx;
  font-size: 28rpx;
  line-height: 1.5;
}

.send-btn {
  width: 112rpx;
  height: 76rpx;
  border-radius: 999rpx;
  background: #2F6FE4;
  color: #FFFFFF;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26rpx;
  font-weight: 700;
}

.send-btn.disabled {
  opacity: 0.45;
}
</style>
