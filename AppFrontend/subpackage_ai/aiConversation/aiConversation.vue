<template>
  <view class="conversation-page">
    <nav-bar title="AI 会话" :showBack="true" fixed placeholder />

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
          <text class="message-text">{{ message.content }}</text>
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
        :disabled="sending"
      />
      <view class="send-btn" :class="{ disabled: !canSend }" @click="sendMessage">发送</view>
    </view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { getLeaderSessionDetail, queryLeaderAgent } from '@/api/ai.js'

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
      this.messages.push({
        localId: `${message.role}-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
        ...message
      })
      this.scrollToBottom()
    },
    async sendMessage() {
      const text = this.inputValue.trim()
      if (!text || !this.canSend) return
      this.inputValue = ''
      this.sending = true
      this.appendMessage({ role: 'user', content: text })
      try {
        const res = await queryLeaderAgent({
          sessionId: this.sessionId,
          agentName: 'leader_agent',
          input: text
        })
        const payload = res?.data || {}
        if (payload.sessionId) {
          this.sessionId = payload.sessionId
          uni.setStorageSync(STORAGE_KEY, payload.sessionId)
        }
        this.appendMessage({
          role: 'assistant',
          content: payload.answer || 'Leader 这次没有返回可用答案，请换一种问法再试。',
          answerType: payload.answerType || 'text'
        })
      } catch (error) {
        this.appendMessage({
          role: 'assistant',
          content: `这次没有顺利完成请求：${error?.message || error?.msg || '请稍后再试'}`
        })
      } finally {
        this.sending = false
      }
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
  min-height: 100vh;
  background: #F7F7F9;
  display: flex;
  flex-direction: column;
}

.message-list {
  flex: 1;
  height: 0;
  padding: 24rpx;
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

.composer {
  flex-shrink: 0;
  display: flex;
  align-items: flex-end;
  gap: 16rpx;
  padding: 18rpx 22rpx calc(18rpx + env(safe-area-inset-bottom));
  background: #FFFFFF;
  border-top: 1rpx solid #ECEFF5;
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
