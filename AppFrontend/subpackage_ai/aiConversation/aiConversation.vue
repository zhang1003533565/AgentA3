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
          <view v-else class="message-content">
            <view v-if="message.role === 'assistant' && getOutputTypeTags(message).length" class="output-type-list">
              <text
                v-for="type in getOutputTypeTags(message)"
                :key="`${message.localId || message.id}-type-${type}`"
                class="output-type-tag"
                :class="`output-type-tag--${type}`"
              >{{ getOutputTypeLabel(type) }}</text>
            </view>
            <text v-if="getDisplayText(message)" class="message-text">{{ getDisplayText(message) }}</text>
            <view v-if="getMessageAttachments(message).length" class="attachment-list">
              <view
                v-for="(file, fileIndex) in getMessageAttachments(message)"
                :key="`${message.localId || message.id}-file-${fileIndex}`"
                class="attachment-item"
                :class="`attachment-item--${file.type}`"
              >
                <image
                  v-if="file.type === 'image'"
                  class="attachment-image"
                  :src="file.url"
                  mode="aspectFill"
                  @click="previewAttachmentImage(file, message)"
                />
                <video
                  v-else-if="file.type === 'video'"
                  class="attachment-video"
                  :src="file.url"
                  controls
                  object-fit="contain"
                ></video>
                <view v-else class="attachment-file" @click="openAttachment(file)">
                  <view class="attachment-file__icon" :class="`attachment-file__icon--${file.type}`">{{ file.extLabel }}</view>
                  <view class="attachment-file__body">
                    <text class="attachment-file__name">{{ file.name }}</text>
                    <text class="attachment-file__meta">{{ file.typeLabel }} · 点击打开</text>
                  </view>
                </view>
              </view>
            </view>
          </view>
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
            this.replaceMessage(thinkingMessage.localId, {
              role: 'assistant',
              content: finalAnswer || current?.content || 'Leader 这次没有返回可用答案，请换一种问法再试。',
              answerType: payload?.answerType || 'text',
              outputType: payload?.outputType || payload?.answerType || 'text',
              outputTypes: payload?.outputTypes || [],
              outputMeta: payload?.outputMeta || {},
              attachments: payload?.attachments || []
            })
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
          answerType: payload.answerType || 'text',
          outputType: payload.outputType || payload.answerType || 'text',
          outputTypes: payload.outputTypes || [],
          outputMeta: payload.outputMeta || {},
          attachments: payload.attachments || []
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
    },
    getDisplayText(message) {
      const content = String(message?.content || '')
      try {
        const parsed = JSON.parse(content)
        if (parsed && Array.isArray(parsed.images)) {
          return String(parsed.message || '').trim()
        }
      } catch (error) {
        // Non-JSON responses continue through normal text cleanup.
      }
      return content
        .replace(this.markdownAttachmentPattern(), '')
        .replace(this.attachmentUrlPattern(), '')
        .trim()
    },
    getOutputTypeTags(message) {
      return [this.detectMessageType(message)]
    },
    detectMessageType(message) {
      const attachments = this.getMessageAttachments(message)
      if (attachments.some((item) => item.type === 'image')) return 'image'
      const content = String(message?.content || '')
      if (this.containsFormula(content)) return 'formula'
      return 'text'
    },
    containsFormula(content) {
      return /```(?:math|latex|tex)\b|\$\$[\s\S]+?\$\$|\\\[[\s\S]+?\\\]|\\\([\s\S]+?\\\)|(?:公式|方程)\s*[:：]/i.test(String(content || ''))
    },
    getOutputTypeLabel(type) {
      const labels = {
        text: '文本',
        image: '照片',
        formula: '公式'
      }
      return labels[type] || '文本'
    },
    getMessageAttachments(message) {
      const structured = this.normalizeAttachments(message?.attachments || message?.files || message?.fileList || [])
      const parsed = this.extractAttachmentsFromText(message?.content || '')
      const seen = new Set()
      return [...structured, ...parsed].filter((file) => {
        if (!file.url || seen.has(file.url)) return false
        seen.add(file.url)
        return true
      })
    },
    normalizeAttachments(value) {
      const list = Array.isArray(value) ? value : []
      return list.map((item) => this.normalizeAttachment(item)).filter(Boolean)
    },
    normalizeAttachment(item) {
      if (!item) return null
      const url = String(item.url || item.fileUrl || item.path || item.href || '').trim()
      if (!url) return null
      const name = String(item.name || item.fileName || this.fileNameFromUrl(url)).trim()
      return this.buildAttachment(url, name, item.type || item.fileType || item.mimeType)
    },
    extractAttachmentsFromText(text) {
      const content = String(text || '')
      const files = []
      try {
        const parsed = JSON.parse(content)
        if (Array.isArray(parsed?.images)) {
          parsed.images.forEach((item) => {
            const value = typeof item === 'string' ? { url: item, type: 'image' } : { ...item, type: item?.type || 'image' }
            files.push(this.normalizeAttachment(value))
          })
        }
      } catch (error) {
        // Plain text responses are inspected below.
      }
      const markdownPattern = this.markdownAttachmentPattern()
      let match
      while ((match = markdownPattern.exec(content)) !== null) {
        files.push(this.buildAttachment(match[2], match[1] || this.fileNameFromUrl(match[2]), ''))
      }
      const plainText = content.replace(this.markdownAttachmentPattern(), '')
      const matches = plainText.match(this.attachmentUrlPattern()) || []
      files.push(...matches.map((url) => this.buildAttachment(url, this.fileNameFromUrl(url), '')))
      return files.filter(Boolean)
    },
    markdownAttachmentPattern() {
      return /!?\[([^\]]+)\]\((https?:\/\/[^\s"'<>，。！？；、)]+?\.(?:png|jpe?g|gif|webp|bmp|mp4|mov|m4v|webm|ogg|pdf|docx?|pptx?)(?:\?[^\s"'<>，。！？；、)]*)?)\)/gi
    },
    attachmentUrlPattern() {
      return /https?:\/\/[^\s"'<>，。！？；、]+?\.(?:png|jpe?g|gif|webp|bmp|mp4|mov|m4v|webm|ogg|pdf|docx?|pptx?)(?:\?[^\s"'<>，。！？；、]*)?/gi
    },
    buildAttachment(url, name, typeHint) {
      const ext = this.fileExt(name || url)
      const hinted = String(typeHint || '').toLowerCase()
      let type = 'file'
      if (hinted.includes('image') || ['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp'].includes(ext)) type = 'image'
      else if (hinted.includes('video') || ['mp4', 'mov', 'm4v', 'webm', 'ogg'].includes(ext)) type = 'video'
      else if (['pdf'].includes(ext)) type = 'pdf'
      else if (['doc', 'docx'].includes(ext)) type = 'docx'
      else if (['ppt', 'pptx'].includes(ext)) type = 'ppt'
      if (!['image', 'video', 'pdf', 'docx', 'ppt'].includes(type)) return null
      return {
        url,
        name: name || this.fileNameFromUrl(url),
        type,
        extLabel: (ext || type).toUpperCase(),
        typeLabel: this.attachmentTypeLabel(type)
      }
    },
    fileNameFromUrl(url) {
      const clean = String(url || '').split('?')[0]
      const name = decodeURIComponent(clean.substring(clean.lastIndexOf('/') + 1) || '文件')
      return name || '文件'
    },
    fileExt(value) {
      const clean = String(value || '').split('?')[0].toLowerCase()
      const index = clean.lastIndexOf('.')
      return index >= 0 ? clean.slice(index + 1) : ''
    },
    attachmentTypeLabel(type) {
      const labels = {
        image: '图片',
        video: '视频',
        pdf: 'PDF',
        docx: 'Word 文档',
        ppt: 'PPT 演示文稿'
      }
      return labels[type] || '文件'
    },
    previewAttachmentImage(file, message) {
      const urls = this.getMessageAttachments(message).filter((item) => item.type === 'image').map((item) => item.url)
      uni.previewImage({ urls, current: file.url })
    },
    openAttachment(file) {
      if (!file?.url) return
      const openWithUrl = () => {
        uni.setClipboardData({
          data: file.url,
          success: () => uni.showToast({ title: '文件链接已复制', icon: 'none' })
        })
      }
      if (typeof uni.downloadFile !== 'function' || typeof uni.openDocument !== 'function') {
        openWithUrl()
        return
      }
      uni.showLoading({ title: '打开中...' })
      uni.downloadFile({
        url: file.url,
        success: (res) => {
          const filePath = res.tempFilePath
          uni.openDocument({
            filePath,
            showMenu: true,
            fail: openWithUrl
          })
        },
        fail: openWithUrl,
        complete: () => uni.hideLoading()
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

.message-content {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.output-type-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8rpx;
}

.output-type-tag {
  padding: 5rpx 12rpx;
  border-radius: 999rpx;
  background: #EEF3FA;
  color: #5B6472;
  font-size: 20rpx;
  font-weight: 700;
  line-height: 1.4;
}

.output-type-tag--image {
  background: #E8F7EF;
  color: #16865B;
}

.output-type-tag--formula {
  background: #FFF3DF;
  color: #B96800;
}

.output-type-tag--code,
.output-type-tag--mermaid {
  background: #E8F1FF;
  color: #2F6FE4;
}

.attachment-list {
  display: flex;
  flex-direction: column;
  gap: 14rpx;
}

.attachment-item {
  width: 100%;
}

.attachment-image {
  width: 420rpx;
  max-width: 100%;
  height: 260rpx;
  border-radius: 18rpx;
  background: #EEF2F7;
  display: block;
}

.attachment-video {
  width: 460rpx;
  max-width: 100%;
  height: 280rpx;
  border-radius: 18rpx;
  background: #111827;
  overflow: hidden;
}

.attachment-file {
  width: 460rpx;
  max-width: 100%;
  min-height: 104rpx;
  border-radius: 18rpx;
  background: #F6F8FC;
  border: 1rpx solid rgba(100, 116, 139, 0.12);
  display: flex;
  align-items: center;
  gap: 18rpx;
  padding: 18rpx;
  box-sizing: border-box;
}

.attachment-file__icon {
  width: 72rpx;
  height: 72rpx;
  border-radius: 16rpx;
  background: #E8F1FF;
  color: #2F6FE4;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20rpx;
  font-weight: 800;
  flex-shrink: 0;
}

.attachment-file__icon--pdf {
  background: #FFF1F1;
  color: #E5484D;
}

.attachment-file__icon--docx {
  background: #EAF2FF;
  color: #2563EB;
}

.attachment-file__icon--ppt {
  background: #FFF3E8;
  color: #EA580C;
}

.attachment-file__body {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.attachment-file__name {
  font-size: 26rpx;
  font-weight: 700;
  color: #1F2937;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.attachment-file__meta {
  font-size: 22rpx;
  color: #7B8794;
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
