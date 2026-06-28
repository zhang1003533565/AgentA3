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
            <view v-if="isMessageGenerating(message)" class="generation-status">
              <view class="generation-spinner"></view>
              <text class="generation-status__text">图片生成中</text>
            </view>
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
            <view
              v-if="message.role === 'assistant' && (getMessageChoicePrompt(message) || getFollowUpActions(message).length)"
              class="follow-up-panel"
            >
              <text v-if="getMessageChoicePrompt(message)" class="follow-up-prompt">{{ getMessageChoicePrompt(message) }}</text>
              <view v-if="getFollowUpActions(message).length" class="follow-up-actions">
                <view
                  v-for="(action, actionIndex) in getFollowUpActions(message)"
                  :key="`${message.localId || message.id}-follow-${actionIndex}`"
                  class="follow-up-action"
                  :class="action.style === 'secondary' ? 'follow-up-action--secondary' : 'follow-up-action--primary'"
                  @click="handleFollowUpAction(action)"
                >{{ action.label }}</view>
              </view>
            </view>
          </view>
          <view v-if="message.role === 'assistant' && hasCallDetail(message)" class="call-detail-panel">
            <view class="call-detail-header" @click="toggleCallDetail(message)">
              <view class="call-detail-heading">
                <view class="call-detail-status-dot" :class="`call-detail-status-dot--${getCallDetailStatus(message)}`"></view>
                <view class="call-detail-title-wrap">
                  <text class="call-detail-title">调用明细</text>
                  <text class="call-detail-summary">{{ getCallDetailSummary(message) }}</text>
                </view>
              </view>
              <text class="call-detail-toggle">{{ isCallDetailExpanded(message) ? '收起' : '展开' }}</text>
            </view>
            <view v-if="isCallDetailExpanded(message)" class="call-detail-body">
              <view class="call-detail-meta">
                <view
                  v-for="item in getCallDetailMetaItems(message)"
                  :key="`${message.localId || message.id}-meta-${item.label}`"
                  class="call-detail-meta-item"
                >
                  <text class="call-detail-meta-label">{{ item.label }}</text>
                  <text class="call-detail-meta-value">{{ item.value }}</text>
                </view>
              </view>
              <view v-if="getCallDetailTools(message).length" class="call-detail-tools">
                <text
                  v-for="tool in getCallDetailTools(message)"
                  :key="`${message.localId || message.id}-tool-${tool}`"
                  class="call-detail-tool"
                >{{ tool }}</text>
              </view>
              <view class="call-detail-steps">
                <view
                  v-for="(step, stepIndex) in getCallDetailSteps(message)"
                  :key="`${message.localId || message.id}-step-${stepIndex}`"
                  class="call-detail-step"
                  :class="`call-detail-step--${step.status}`"
                >
                  <view class="call-detail-step-dot"></view>
                  <view class="call-detail-step-body">
                    <view class="call-detail-step-head">
                      <text class="call-detail-step-title">{{ step.title }}</text>
                      <text class="call-detail-step-status">{{ step.statusLabel }}</text>
                    </view>
                    <text v-if="step.description" class="call-detail-step-desc">{{ step.description }}</text>
                  </view>
                </view>
              </view>
              <text v-if="getCallDetailError(message)" class="call-detail-error">{{ getCallDetailError(message) }}</text>
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
const CALL_DETAIL_STAGE_LABELS = {
  status: '请求已提交',
  processing: '处理中',
  session: '建立会话',
  leader_plan: '意图识别与路由',
  leader_route: '意图识别与路由',
  retrieve: '检索资料',
  search: '检索资料',
  answer: '生成回答',
  direct_agent: '执行智能体',
  agent_answer: '执行智能体',
  agent_failed: '执行智能体',
  done: '完成',
  error: '异常'
}
const CALL_DETAIL_STATUS_LABELS = {
  running: '进行中',
  completed: '已完成',
  failed: '失败'
}
const AGENT_LABELS = {
  leader_agent: 'Leader 智能体',
  profile_summary_agent: '个人画像汇总智能体',
  mind_map_agent: '思维导图图片提示词智能体',
  diagram_mind_map_agent: '思维导图图片生成智能体',
  diagram_flowchart_agent: '图表流程图智能体',
  diagram_activity_agent: '图表活动图智能体',
  diagram_architecture_agent: '图表架构图智能体',
  diagram_flowchart_prompt_agent: '流程图提示词智能体',
  diagram_activity_prompt_agent: '活动图提示词智能体',
  architecture_prompt_agent: '架构图提示词智能体',
  textbook_knowledge_agent: '教材知识点智能体',
  image_agent: '图片智能体',
  ppt_outline_agent: 'PPT 大纲智能体',
  ppt_layout_agent: 'PPT 布局智能体',
  ppt_review_agent: 'PPT 审查智能体',
  ppt_image_agent: 'PPT 图片智能体',
  ppt_to_docx_agent: 'PPT 转 DOCX 智能体',
  meeting_summary_agent: '会议总结智能体'
}
const INTENT_LABELS = {
  smalltalk: '闲聊',
  campus_search: '校园检索',
  schedule: '课表查询',
  mind_map: '思维导图',
  diagram_mind_map: '思维导图图片',
  diagram_architecture: '架构图',
  diagram_flowchart: '流程图',
  diagram_activity: '活动图',
  textbook_knowledge: '教材知识点',
  question_bank: '题库生成',
  ppt: 'PPT 生成',
  image: '图片生成',
  profile_summary: '画像汇总',
  meeting: '会议处理'
}

export default {
  components: { NavBar },
  data() {
    return {
      sessionId: '',
      messages: [],
      inputValue: '',
      sending: false,
      blockingRequestCount: 0,
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
      const releaseComposer = this.createComposerRelease()
      this.appendMessage({ role: 'user', content: text })
      const thinkingMessage = this.appendMessage({
        role: 'assistant',
        type: 'thinking',
        content: '思考中',
        callDetail: this.createInitialCallDetail(text),
        callDetailExpanded: false
      })
      let streamStarted = false
      let streamTouched = false
      try {
        await streamLeaderAgent({
          sessionId: this.sessionId,
          agentName: 'leader_agent',
          input: text
        }, {
          onEvent: (eventName, payload) => {
            streamTouched = true
            if (eventName === 'generation_start') {
              streamStarted = true
              this.applyGenerationStart(thinkingMessage.localId, payload)
              releaseComposer()
              return
            }
            this.updateCallDetail(thinkingMessage.localId, this.buildLiveCallDetailPatch(eventName, payload))
          },
          onSession: (payload) => {
            streamTouched = true
            this.syncSessionId(payload?.sessionId)
            this.updateCallDetail(thinkingMessage.localId, {
              model: payload?.model || '',
              agentName: payload?.agentName || 'leader_agent',
              answerType: payload?.answerType || '',
              currentStep: 'session',
              traceAppend: this.createLiveTraceStep('session', {
                agentName: payload?.agentName || 'leader_agent',
                model: payload?.model || ''
              })
            })
          },
          onSearch: (payload) => {
            streamTouched = true
            this.updateCallDetail(thinkingMessage.localId, {
              searchKeyword: payload?.searchKeyword || '',
              matchedResults: payload?.matchedResults || [],
              retrievalMeta: payload?.retrievalMeta || {},
              currentStep: 'retrieve',
              traceAppend: this.createLiveTraceStep('retrieve', {
                keyword: payload?.searchKeyword || '',
                matchedCount: Array.isArray(payload?.matchedResults) ? payload.matchedResults.length : 0,
                ...((payload && payload.retrievalMeta) || {})
              })
            })
          },
          onDelta: (content) => {
            if (!content) return
            streamTouched = true
            streamStarted = true
            this.updateCallDetail(thinkingMessage.localId, {
              currentStep: 'answer',
              traceAppendOnce: this.createLiveTraceStep('answer', { streaming: true })
            })
            this.appendMessageContent(thinkingMessage.localId, content)
          },
          onDone: (payload) => {
            streamTouched = true
            if (!this.messages.some((item) => item.localId === thinkingMessage.localId)) return
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
              attachments: payload?.attachments || [],
              agentName: payload?.agentName || current?.callDetail?.agentName || 'leader_agent',
              searchKeyword: payload?.searchKeyword || current?.callDetail?.searchKeyword || '',
              retrievalMeta: payload?.retrievalMeta || payload?.metadata || current?.callDetail?.retrievalMeta || {},
              matchedResults: payload?.matchedResults || current?.callDetail?.matchedResults || [],
              trace: payload?.trace || current?.callDetail?.trace || [],
              callDetail: this.buildFinalCallDetail(payload, current?.callDetail, 'completed'),
              callDetailExpanded: current?.callDetailExpanded || false
            })
          },
          onError: (payload) => {
            streamTouched = true
            const streamError = new Error(payload?.message || '流式请求失败')
            streamError.payload = payload || {}
            throw streamError
          }
        })
      } catch (error) {
        if (!this.messages.some((item) => item.localId === thinkingMessage.localId)) return
        const current = this.messages.find((item) => item.localId === thinkingMessage.localId)
        const errorCallDetail = this.buildErrorCallDetail(error, current?.callDetail)
        const errorContent = this.buildFailureContent(errorCallDetail, streamStarted || streamTouched ? '这次流式回复中断了' : '这次没有顺利完成请求')
        if (streamStarted || streamTouched) {
          this.replaceMessage(thinkingMessage.localId, {
            role: 'assistant',
            content: errorContent,
            callDetail: errorCallDetail,
            callDetailExpanded: current?.callDetailExpanded || false
          })
        } else if (error?.fallbackToNormalRequest) {
          await this.sendMessageFallback(text, thinkingMessage.localId, error)
        } else {
          this.replaceMessage(thinkingMessage.localId, {
            role: 'assistant',
            content: errorContent,
            callDetail: errorCallDetail,
            callDetailExpanded: current?.callDetailExpanded || false
          })
        }
      } finally {
        releaseComposer()
      }
    },
    createComposerRelease() {
      this.blockingRequestCount += 1
      this.sending = true
      let released = false
      return () => {
        if (released) return
        released = true
        this.blockingRequestCount = Math.max(0, this.blockingRequestCount - 1)
        this.sending = this.blockingRequestCount > 0
      }
    },
    applyGenerationStart(localId, payload = {}) {
      const current = this.messages.find((item) => item.localId === localId)
      if (!current) return
      this.syncSessionId(payload?.sessionId)
      const callDetail = this.buildFinalCallDetail(payload, current?.callDetail, 'running')
      callDetail.currentStep = 'agent_answer'
      callDetail.status = 'running'
      this.replaceMessage(localId, {
        role: 'assistant',
        content: payload?.answer || '已开始生成图片，你可以继续提问，生成完成后会更新到这里。',
        answerType: payload?.answerType || 'image_generation',
        outputType: payload?.outputType || 'image',
        outputTypes: payload?.outputTypes || ['image'],
        outputMeta: {
          ...((payload && payload.outputMeta) || {}),
          generationStatus: 'running'
        },
        attachments: payload?.attachments || [],
        agentName: payload?.agentName || callDetail.agentName || 'image_agent',
        searchKeyword: payload?.searchKeyword || '',
        retrievalMeta: payload?.retrievalMeta || payload?.metadata || callDetail.retrievalMeta || {},
        matchedResults: payload?.matchedResults || [],
        trace: payload?.trace || callDetail.trace || [],
        callDetail,
        callDetailExpanded: current?.callDetailExpanded || false
      })
    },
    async sendMessageFallback(text, localId, streamError) {
      try {
        const res = await queryLeaderAgent({
          sessionId: this.sessionId,
          agentName: 'leader_agent',
          input: text
        })
        const payload = res?.data || {}
        if (!this.messages.some((item) => item.localId === localId)) return
        this.syncSessionId(payload.sessionId)
        this.replaceMessage(localId, {
          role: 'assistant',
          content: payload.answer || 'Leader 这次没有返回可用答案，请换一种问法再试。',
          answerType: payload.answerType || 'text',
          outputType: payload.outputType || payload.answerType || 'text',
          outputTypes: payload.outputTypes || [],
          outputMeta: payload.outputMeta || {},
          attachments: payload.attachments || [],
          agentName: payload.agentName || 'leader_agent',
          searchKeyword: payload.searchKeyword || '',
          retrievalMeta: payload.retrievalMeta || {},
          matchedResults: payload.matchedResults || [],
          trace: payload.trace || [],
          callDetail: this.buildFinalCallDetail(payload, null, 'completed'),
          callDetailExpanded: false
        })
      } catch (error) {
        if (!this.messages.some((item) => item.localId === localId)) return
        const errorCallDetail = this.buildErrorCallDetail(error, null)
        this.replaceMessage(localId, {
          role: 'assistant',
          content: this.buildFailureContent(errorCallDetail, '这次没有顺利完成请求', streamError?.message),
          callDetail: errorCallDetail,
          callDetailExpanded: false
        })
      }
    },
    createInitialCallDetail(input) {
      return {
        status: 'running',
        currentStep: 'status',
        agentName: 'leader_agent',
        inputPreview: this.truncateText(input, 80),
        retrievalMeta: {},
        matchedResults: [],
        trace: [
          this.createLiveTraceStep('status', {
            stage: 'processing',
            message: '已提交给 Leader 智能助手'
          })
        ]
      }
    },
    createLiveTraceStep(stage, detail = {}) {
      return {
        stage: stage || 'status',
        detail: {
          ...(detail || {}),
          live: true
        }
      }
    },
    buildLiveCallDetailPatch(eventName, payload) {
      const stage = payload?.stage || eventName || 'status'
      return {
        status: 'running',
        currentStep: stage,
        traceAppendOnce: this.createLiveTraceStep(stage, payload || {})
      }
    },
    updateCallDetail(localId, patch = {}) {
      const index = this.messages.findIndex((item) => item.localId === localId)
      if (index === -1) return
      const current = this.messages[index]
      const currentDetail = this.normalizeCallDetail(current)
      let nextTrace = Array.isArray(currentDetail.trace) ? [...currentDetail.trace] : []
      if (Array.isArray(patch.trace)) {
        nextTrace = patch.trace
      }
      if (patch.traceAppend) {
        nextTrace.push(patch.traceAppend)
      }
      if (patch.traceAppendOnce) {
        const nextStage = String(patch.traceAppendOnce.stage || '')
        const exists = nextTrace.some((step) => String(step?.stage || '') === nextStage)
        if (!exists) nextTrace.push(patch.traceAppendOnce)
      }
      const nextDetail = {
        ...currentDetail,
        ...patch,
        status: patch.status || currentDetail.status || 'running',
        retrievalMeta: {
          ...(currentDetail.retrievalMeta || {}),
          ...((patch && patch.retrievalMeta) || {})
        },
        matchedResults: Array.isArray(patch.matchedResults) ? patch.matchedResults : currentDetail.matchedResults,
        trace: nextTrace
      }
      delete nextDetail.traceAppend
      delete nextDetail.traceAppendOnce
      this.messages.splice(index, 1, {
        ...current,
        callDetail: nextDetail
      })
    },
    buildFinalCallDetail(payload = {}, currentDetail = null, status = 'completed') {
      const previous = this.normalizeCallDetail({ role: 'assistant', callDetail: currentDetail || {} })
      const retrievalMeta = {
        ...(previous.retrievalMeta || {}),
        ...((payload && payload.metadata) || {}),
        ...((payload && payload.retrievalMeta) || {})
      }
      const payloadTrace = Array.isArray(payload?.trace) ? payload.trace : []
      const trace = payloadTrace.length ? payloadTrace : previous.trace
      const detail = {
        ...previous,
        status,
        currentStep: status === 'completed' ? 'done' : previous.currentStep,
        model: payload?.model || previous.model || '',
        answerType: payload?.answerType || previous.answerType || '',
        outputType: payload?.outputType || previous.outputType || '',
        ragStrategy: payload?.ragStrategy || payload?.strategy || previous.ragStrategy || '',
        agentName: payload?.agentName || retrievalMeta.executedAgent || retrievalMeta.targetAgent || retrievalMeta.agentName || previous.agentName || 'leader_agent',
        searchKeyword: payload?.searchKeyword || previous.searchKeyword || '',
        retrievalMeta,
        matchedResults: Array.isArray(payload?.matchedResults) ? payload.matchedResults : previous.matchedResults,
        trace: this.withTerminalTrace(trace, status)
      }
      detail.intent = this.getTraceValue(detail.trace, 'intent') || retrievalMeta.intent || previous.intent || ''
      return detail
    },
    buildErrorCallDetail(error, currentDetail = null) {
      const previous = this.normalizeCallDetail({ role: 'assistant', type: 'thinking', callDetail: currentDetail || {} })
      const payload = error?.payload || {}
      const retrievalMeta = {
        ...(previous.retrievalMeta || {}),
        ...((payload && payload.retrievalMeta) || {})
      }
      const payloadTrace = Array.isArray(payload?.trace) ? payload.trace : []
      const trace = payloadTrace.length ? payloadTrace : previous.trace
      const message = payload?.message || error?.message || error?.msg || '请求失败'
      const rawMessage = payload?.rawMessage || retrievalMeta.rawFailureReason || ''
      const failedAgent = payload?.failedAgent
        || payload?.agentName
        || retrievalMeta.failedAgent
        || retrievalMeta.executedAgent
        || retrievalMeta.targetAgent
        || previous.agentName
        || 'leader_agent'
      const stage = payload?.stage || retrievalMeta.failureStage || 'error'
      return {
        ...previous,
        status: 'failed',
        currentStep: stage,
        agentName: failedAgent,
        intent: payload?.intent || retrievalMeta.intent || previous.intent || '',
        routeReason: payload?.routeReason || retrievalMeta.routeReason || previous.routeReason || '',
        model: retrievalMeta.model || previous.model || '',
        retrievalMeta,
        error: message,
        rawError: rawMessage,
        trace: this.withTerminalTrace(trace, 'failed', message, {
          stage,
          agentName: failedAgent,
          rawMessage,
          statusCode: payload?.statusCode || retrievalMeta.statusCode || ''
        })
      }
    },
    buildFailureContent(detail, prefix, fallbackMessage = '') {
      const normalized = this.normalizeCallDetail({ role: 'assistant', callDetail: detail || {} })
      const agentLabel = this.getAgentLabel(normalized.agentName)
      const stageLabel = this.getStageLabel(normalized.currentStep || 'error')
      const reason = normalized.error || fallbackMessage || '请稍后再试'
      return `${prefix}：${agentLabel} 在「${stageLabel}」阶段失败。原因：${reason}`
    },
    withTerminalTrace(trace, status, errorMessage = '', extraDetail = {}) {
      const list = Array.isArray(trace) ? [...trace] : []
      const terminalStage = status === 'failed' ? 'error' : 'done'
      const actualStage = status === 'failed' && extraDetail?.stage ? extraDetail.stage : terminalStage
      const exists = list.some((step) => String(step?.stage || '') === actualStage)
      if (!exists) {
        list.push(this.createLiveTraceStep(actualStage, errorMessage ? { message: errorMessage, ...(extraDetail || {}) } : { message: '调用流程已完成' }))
      }
      return list
    },
    hasCallDetail(message) {
      if (!message || message.role !== 'assistant') return false
      if (message.type === 'thinking') return true
      if (message.callDetail && Object.keys(message.callDetail).length) return true
      if (Array.isArray(message.trace) && message.trace.length) return true
      if (message.retrievalMeta && Object.keys(message.retrievalMeta).length) return true
      return Boolean(message.agentName || message.searchKeyword)
    },
    getMessageKey(message) {
      return message?.localId || message?.id || ''
    },
    toggleCallDetail(message) {
      const key = this.getMessageKey(message)
      if (!key) return
      const index = this.messages.findIndex((item) => this.getMessageKey(item) === key)
      if (index === -1) return
      this.messages.splice(index, 1, {
        ...this.messages[index],
        callDetailExpanded: !this.messages[index].callDetailExpanded
      })
      this.scrollToBottom()
    },
    isCallDetailExpanded(message) {
      return Boolean(message?.callDetailExpanded)
    },
    getCallDetailStatus(message) {
      return this.normalizeCallDetail(message).status || 'completed'
    },
    getCallDetailSummary(message) {
      const detail = this.normalizeCallDetail(message)
      const statusLabel = CALL_DETAIL_STATUS_LABELS[detail.status] || '已完成'
      const currentLabel = this.getStageLabel(detail.currentStep || this.getLastTraceStage(detail.trace))
      const agentLabel = this.getAgentLabel(detail.agentName)
      if (detail.status === 'running') {
        return `${currentLabel} · ${agentLabel}`
      }
      if (detail.status === 'failed') {
        return `${statusLabel} · ${currentLabel}`
      }
      const intentLabel = detail.intent ? this.getIntentLabel(detail.intent) : ''
      return `${statusLabel} · ${intentLabel || agentLabel}`
    },
    getCallDetailMetaItems(message) {
      const detail = this.normalizeCallDetail(message)
      const matchedCount = this.getMatchedCount(detail)
      const items = [
        { label: '状态', value: CALL_DETAIL_STATUS_LABELS[detail.status] || '已完成' },
        { label: '意图', value: detail.intent ? this.getIntentLabel(detail.intent) : '未识别' },
        { label: '智能体', value: this.getAgentLabel(detail.agentName) },
        { label: '检索', value: detail.searchKeyword || this.getRetrievalLabel(detail) },
        { label: '命中', value: `${matchedCount} 条` }
      ]
      if (detail.model) {
        items.push({ label: '模型', value: this.truncateText(detail.model, 34) })
      }
      if (detail.status === 'failed') {
        items.push({ label: '阶段', value: this.getStageLabel(detail.currentStep || 'error') })
      }
      const routeReason = detail.retrievalMeta?.routeReason
      if (routeReason) {
        items.push({ label: '原因', value: this.truncateText(routeReason, 42) })
      }
      return items
    },
    getCallDetailTools(message) {
      const detail = this.normalizeCallDetail(message)
      const tools = []
      const trace = Array.isArray(detail.trace) ? detail.trace : []
      if (trace.some((step) => ['leader_plan', 'leader_route'].includes(String(step?.stage || ''))) || detail.intent) {
        tools.push('Leader 意图识别')
      }
      const usedRetrieval = Boolean(detail.searchKeyword)
        || trace.some((step) => ['retrieve', 'search'].includes(String(step?.stage || '')) && !step?.detail?.skipped)
      if (usedRetrieval) {
        tools.push('业务/知识库检索')
      }
      if (detail.agentName && detail.agentName !== 'leader_agent') {
        tools.push(this.getAgentLabel(detail.agentName))
      } else {
        tools.push('Leader 回答汇总')
      }
      return [...new Set(tools.filter(Boolean))]
    },
    getCallDetailSteps(message) {
      const detail = this.normalizeCallDetail(message)
      const trace = Array.isArray(detail.trace) && detail.trace.length
        ? detail.trace
        : [this.createLiveTraceStep(detail.currentStep || 'status', {})]
      return trace.map((step, index) => {
        const stage = String(step?.stage || 'status')
        const isLast = index === trace.length - 1
        const status = detail.status === 'failed' && isLast
          ? 'failed'
          : (detail.status === 'running' && isLast ? 'running' : 'completed')
        return {
          title: this.getStageLabel(stage),
          description: this.describeCallDetailStep(stage, step?.detail || {}, detail),
          status,
          statusLabel: CALL_DETAIL_STATUS_LABELS[status] || '已完成'
        }
      })
    },
    getCallDetailError(message) {
      return this.normalizeCallDetail(message).error || ''
    },
    normalizeCallDetail(message) {
      const raw = message?.callDetail || {}
      const messageTrace = Array.isArray(message?.trace) ? message.trace : []
      const rawTrace = Array.isArray(raw.trace) ? raw.trace : []
      const trace = rawTrace.length ? rawTrace : messageTrace
      const retrievalMeta = {
        ...((message && message.retrievalMeta) || {}),
        ...((raw && raw.retrievalMeta) || {})
      }
      const matchedResults = Array.isArray(raw.matchedResults)
        ? raw.matchedResults
        : (Array.isArray(message?.matchedResults) ? message.matchedResults : [])
      const agentName = raw.agentName
        || message?.agentName
        || retrievalMeta.executedAgent
        || retrievalMeta.targetAgent
        || retrievalMeta.agentName
        || this.getTraceValue(trace, 'agent')
        || this.getTraceValue(trace, 'agentName')
        || this.getTraceValue(trace, 'targetAgent')
        || 'leader_agent'
      const generationStatus = message?.outputMeta?.generationStatus || retrievalMeta.generationStatus || raw.generationStatus || ''
      const status = raw.status || (generationStatus === 'running' ? 'running' : (message?.type === 'thinking' ? 'running' : (raw.error ? 'failed' : 'completed')))
      const currentStep = raw.currentStep || this.getLastTraceStage(trace) || (status === 'running' ? 'status' : 'done')
      return {
        ...raw,
        status,
        currentStep,
        trace,
        retrievalMeta,
        matchedResults,
        agentName,
        intent: raw.intent || retrievalMeta.intent || this.getTraceValue(trace, 'intent') || '',
        searchKeyword: raw.searchKeyword || message?.searchKeyword || this.getTraceValue(trace, 'keyword') || '',
        model: raw.model || message?.model || '',
        ragStrategy: raw.ragStrategy || message?.ragStrategy || retrievalMeta.leaderRagStrategy || '',
        answerType: raw.answerType || message?.answerType || retrievalMeta.answerType || '',
        outputType: raw.outputType || message?.outputType || retrievalMeta.outputType || '',
        error: raw.error || ''
      }
    },
    describeCallDetailStep(stage, detail, normalized) {
      if (stage === 'leader_plan' || stage === 'leader_route') {
        const intent = detail.intent || normalized.intent
        const agent = detail.targetAgent || detail.agentName || normalized.agentName
        const needRetrieval = detail.needRetrieval === true ? '需要检索' : '不需要检索'
        return `意图：${intent ? this.getIntentLabel(intent) : '未识别'} · 路由：${this.getAgentLabel(agent)} · ${needRetrieval}`
      }
      if (stage === 'retrieve' || stage === 'search') {
        if (detail.skipped) {
          return `跳过检索：${detail.reason || '没有检索关键词'}`
        }
        const keyword = detail.keyword || normalized.searchKeyword || '未提供'
        const count = detail.matchedCount !== undefined && detail.matchedCount !== null
          ? detail.matchedCount
          : (detail.javaBackendCount !== undefined && detail.javaBackendCount !== null ? detail.javaBackendCount : this.getMatchedCount(normalized))
        return `关键词：${keyword} · 命中 ${count || 0} 条`
      }
      if (stage === 'answer') {
        const agent = detail.agent || detail.agentName || normalized.agentName
        const length = detail.answerLength ? ` · 回答 ${detail.answerLength} 字` : ''
        return `由 ${this.getAgentLabel(agent)} 生成${length}`
      }
      if (stage === 'direct_agent' || stage === 'agent_answer') {
        const agent = detail.agentName || normalized.agentName
        const length = detail.answerLength ? ` · 输出 ${detail.answerLength} 字` : ''
        return `${this.getAgentLabel(agent)} 正在执行${length}`
      }
      if (stage === 'agent_failed') {
        const agent = detail.agentName || normalized.agentName
        const rawMessage = detail.rawMessage ? ` · 原始错误：${this.truncateText(detail.rawMessage, 88)}` : ''
        return `${this.getAgentLabel(agent)} 执行失败：${detail.message || normalized.error || '未知错误'}${rawMessage}`
      }
      if (stage === 'session') {
        const agent = detail.agentName || normalized.agentName
        const model = detail.model || normalized.model
        return model ? `${this.getAgentLabel(agent)} · ${model}` : this.getAgentLabel(agent)
      }
      if (stage === 'done') {
        return '回答、附件和调用轨迹已返回'
      }
      if (stage === 'error') {
        return detail.message || normalized.error || '调用过程中断'
      }
      return detail.message || (detail.stage === 'processing' ? '服务已收到请求，正在处理' : '正在处理')
    },
    getTraceValue(trace, key) {
      if (!Array.isArray(trace)) return ''
      for (const step of trace) {
        const detail = step?.detail || {}
        if (detail[key] !== undefined && detail[key] !== null && detail[key] !== '') {
          return detail[key]
        }
      }
      return ''
    },
    getLastTraceStage(trace) {
      return Array.isArray(trace) && trace.length ? String(trace[trace.length - 1]?.stage || '') : ''
    },
    getStageLabel(stage) {
      return CALL_DETAIL_STAGE_LABELS[stage] || stage || '处理中'
    },
    getIntentLabel(intent) {
      return INTENT_LABELS[intent] || intent
    },
    getAgentLabel(agentName) {
      if (!agentName) return 'Leader 智能体'
      if (AGENT_LABELS[agentName]) return AGENT_LABELS[agentName]
      if (String(agentName).startsWith('textbook_question_')) return '题库生成智能体'
      if (String(agentName).startsWith('meeting_')) return '会议智能体'
      return agentName
    },
    getMatchedCount(detail) {
      if (Array.isArray(detail?.matchedResults) && detail.matchedResults.length) {
        return detail.matchedResults.length
      }
      const meta = detail?.retrievalMeta || {}
      const javaCount = Number(meta.javaBackendCount || 0)
      const documentCount = Number(meta.documentCount || 0)
      return javaCount + documentCount
    },
    getRetrievalLabel(detail) {
      const meta = detail?.retrievalMeta || {}
      if (meta.needRetrieval === false || meta.retrievalSkipped || meta.leaderAction === 'direct_answer') {
        return '未检索'
      }
      return detail?.status === 'running' ? '等待中' : '无'
    },
    truncateText(value, maxLength = 40) {
      const text = String(value || '')
      return text.length > maxLength ? `${text.slice(0, maxLength)}...` : text
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
    isMessageGenerating(message) {
      if (!message || message.role !== 'assistant' || message.type === 'thinking') return false
      const meta = message.outputMeta || {}
      if (meta.generationStatus === 'running') return true
      const detail = this.normalizeCallDetail(message)
      if (detail.status !== 'running') return false
      const outputType = this.normalizeOutputType(message.outputType || message.answerType || detail.outputType)
      return outputType === 'image'
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
      const rawTypes = []
      if (Array.isArray(message?.outputTypes)) rawTypes.push(...message.outputTypes)
      if (message?.outputType) rawTypes.push(message.outputType)
      if (message?.answerType) rawTypes.push(message.answerType)
      rawTypes.push(this.detectMessageType(message))
      const normalized = rawTypes
        .map((type) => this.normalizeOutputType(type))
        .filter(Boolean)
      return [...new Set(normalized)]
    },
    detectMessageType(message) {
      const attachments = this.getMessageAttachments(message)
      if (attachments.some((item) => item.type === 'image')) return 'image'
      if (attachments.some((item) => item.type === 'video')) return 'video'
      if (attachments.some((item) => ['pdf', 'docx', 'ppt', 'excel', 'file'].includes(item.type))) return 'document'
      const content = String(message?.content || '')
      if (this.containsFormula(content)) return 'formula'
      if ((message?.answerType || '').includes('document')) return 'document'
      if ((message?.answerType || '').includes('image')) return 'image'
      return 'text'
    },
    normalizeOutputType(type) {
      const value = String(type || '').toLowerCase()
      if (!value) return ''
      if (value.includes('image') || value === 'picture') return 'image'
      if (value.includes('video')) return 'video'
      if (value.includes('document') || value.includes('docx') || value.includes('pdf') || value.includes('ppt') || value.includes('excel')) return 'document'
      if (value.includes('mermaid') || value.includes('diagram')) return 'diagram'
      if (value.includes('formula') || value.includes('math')) return 'formula'
      if (value.includes('question_bank')) return 'question'
      if (value.includes('markdown') || value.includes('tool_result')) return 'text'
      return value === 'text' ? 'text' : ''
    },
    containsFormula(content) {
      return /```(?:math|latex|tex)\b|\$\$[\s\S]+?\$\$|\\\[[\s\S]+?\\\]|\\\([\s\S]+?\\\)|(?:公式|方程)\s*[:：]/i.test(String(content || ''))
    },
    getOutputTypeLabel(type) {
      const labels = {
        text: '文本',
        image: '图片',
        video: '视频',
        document: '文档',
        diagram: '图表',
        formula: '公式',
        question: '题库'
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
        ;['documents', 'files', 'attachments'].forEach((key) => {
          if (Array.isArray(parsed?.[key])) {
            parsed[key].forEach((item) => files.push(this.normalizeAttachment(item)))
          }
        })
        ;[
          ['imageUrl', 'image'],
          ['image_url', 'image'],
          ['documentUrl', 'document'],
          ['document_url', 'document'],
          ['fileUrl', 'file'],
          ['file_url', 'file'],
          ['url', parsed?.type || '']
        ].forEach(([key, type]) => {
          if (parsed?.[key]) {
            files.push(this.normalizeAttachment({ ...parsed, url: parsed[key], type }))
          }
        })
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
      return /!?\[([^\]]+)\]\(((?:https?:\/\/|\/uploads\/)[^\s"'<>，。！？；、)]+?\.(?:png|jpe?g|gif|webp|bmp|mp4|mov|m4v|webm|ogg|pdf|docx?|pptx?|xlsx?|csv|md|mmd|zip)(?:\?[^\s"'<>，。！？；、)]*)?)\)/gi
    },
    attachmentUrlPattern() {
      return /(?:https?:\/\/|\/uploads\/)[^\s"'<>，。！？；、]+?\.(?:png|jpe?g|gif|webp|bmp|mp4|mov|m4v|webm|ogg|pdf|docx?|pptx?|xlsx?|csv|md|mmd|zip)(?:\?[^\s"'<>，。！？；、]*)?/gi
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
      else if (['xls', 'xlsx', 'csv'].includes(ext)) type = 'excel'
      else if (hinted.includes('document')) type = 'file'
      if (!['image', 'video', 'pdf', 'docx', 'ppt', 'excel', 'file'].includes(type)) return null
      if (type === 'file' && !ext) return null
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
        ppt: 'PPT 演示文稿',
        excel: '表格文件',
        file: '文档'
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
    },
    getMessageChoicePrompt(message) {
      return String(message?.outputMeta?.choicePrompt || '').trim()
    },
    getFollowUpActions(message) {
      const actions = message?.outputMeta?.followUpActions
      return Array.isArray(actions)
        ? actions
          .filter((item) => item && item.label && item.prompt)
          .slice(0, 3)
        : []
    },
    handleFollowUpAction(action) {
      if (!action?.prompt || this.sending) return
      this.inputValue = action.prompt
      this.sendMessage()
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

.generation-status {
  display: inline-flex;
  align-items: center;
  gap: 12rpx;
  align-self: flex-start;
  max-width: 100%;
  padding: 10rpx 14rpx;
  border-radius: 10rpx;
  background: #F3F7FF;
  color: #2F6FE4;
}

.generation-spinner {
  width: 18rpx;
  height: 18rpx;
  border-radius: 50%;
  border: 3rpx solid rgba(47, 111, 228, 0.18);
  border-top-color: #2F6FE4;
  animation: generation-spin 0.9s linear infinite;
  flex-shrink: 0;
}

.generation-status__text {
  font-size: 24rpx;
  line-height: 1.4;
  font-weight: 600;
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

.output-type-tag--video {
  background: #F0E8FF;
  color: #6D3FD1;
}

.output-type-tag--document {
  background: #EAF2FF;
  color: #2563EB;
}

.output-type-tag--diagram {
  background: #EEF8F8;
  color: #087F8C;
}

.output-type-tag--question {
  background: #F7F1E8;
  color: #9A5B14;
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

.attachment-file__icon--excel {
  background: #EAF8EF;
  color: #16865B;
}

.attachment-file__icon--file {
  background: #F1F5F9;
  color: #475569;
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

.follow-up-panel {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  padding-top: 4rpx;
}

.follow-up-prompt {
  font-size: 24rpx;
  line-height: 1.55;
  color: #64748B;
}

.follow-up-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}

.follow-up-action {
  min-height: 54rpx;
  padding: 0 18rpx;
  border-radius: 999rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24rpx;
  font-weight: 700;
}

.follow-up-action--primary {
  background: #E8F1FF;
  color: #2F6FE4;
}

.follow-up-action--secondary {
  background: #F4F7FB;
  color: #526070;
  border: 1rpx solid rgba(100, 116, 139, 0.12);
}

.call-detail-panel {
  margin-top: 14rpx;
  padding-top: 14rpx;
  border-top: 1rpx solid #EEF2F7;
}

.call-detail-header {
  min-height: 54rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
}

.call-detail-heading {
  min-width: 0;
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.call-detail-status-dot {
  width: 14rpx;
  height: 14rpx;
  border-radius: 50%;
  background: #94A3B8;
  flex-shrink: 0;
}

.call-detail-status-dot--running {
  background: #2F6FE4;
  box-shadow: 0 0 0 6rpx rgba(47, 111, 228, 0.12);
}

.call-detail-status-dot--completed {
  background: #18A058;
}

.call-detail-status-dot--failed {
  background: #E5484D;
}

.call-detail-title-wrap {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.call-detail-title {
  font-size: 24rpx;
  font-weight: 800;
  color: #243044;
  line-height: 1.3;
}

.call-detail-summary {
  font-size: 21rpx;
  line-height: 1.35;
  color: #7B8794;
  word-break: break-all;
}

.call-detail-toggle {
  flex-shrink: 0;
  font-size: 22rpx;
  font-weight: 800;
  color: #2F6FE4;
}

.call-detail-body {
  margin-top: 12rpx;
  padding: 16rpx;
  border-radius: 18rpx;
  background: #F7F9FD;
  border: 1rpx solid rgba(100, 116, 139, 0.12);
  display: flex;
  flex-direction: column;
  gap: 14rpx;
}

.call-detail-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10rpx;
}

.call-detail-meta-item {
  width: calc(50% - 5rpx);
  min-height: 70rpx;
  padding: 10rpx 12rpx;
  border-radius: 14rpx;
  background: #FFFFFF;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.call-detail-meta-label {
  font-size: 20rpx;
  color: #8A96A8;
  line-height: 1.25;
}

.call-detail-meta-value {
  font-size: 22rpx;
  font-weight: 700;
  color: #263244;
  line-height: 1.35;
  word-break: break-all;
}

.call-detail-tools {
  display: flex;
  flex-wrap: wrap;
  gap: 8rpx;
}

.call-detail-tool {
  padding: 6rpx 12rpx;
  border-radius: 999rpx;
  background: #E8F1FF;
  color: #2F6FE4;
  font-size: 20rpx;
  font-weight: 700;
  line-height: 1.35;
}

.call-detail-steps {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.call-detail-step {
  display: flex;
  gap: 12rpx;
}

.call-detail-step-dot {
  width: 16rpx;
  height: 16rpx;
  margin-top: 8rpx;
  border-radius: 50%;
  background: #CBD5E1;
  flex-shrink: 0;
}

.call-detail-step--running .call-detail-step-dot {
  background: #2F6FE4;
}

.call-detail-step--completed .call-detail-step-dot {
  background: #18A058;
}

.call-detail-step--failed .call-detail-step-dot {
  background: #E5484D;
}

.call-detail-step-body {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.call-detail-step-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12rpx;
}

.call-detail-step-title {
  min-width: 0;
  flex: 1;
  font-size: 23rpx;
  font-weight: 800;
  color: #253047;
  line-height: 1.35;
}

.call-detail-step-status {
  flex-shrink: 0;
  font-size: 20rpx;
  color: #7B8794;
  line-height: 1.35;
}

.call-detail-step-desc {
  font-size: 21rpx;
  line-height: 1.5;
  color: #607086;
  word-break: break-all;
}

.call-detail-error {
  padding: 10rpx 12rpx;
  border-radius: 12rpx;
  background: #FFF1F1;
  color: #C53030;
  font-size: 21rpx;
  line-height: 1.45;
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

@keyframes generation-spin {
  to {
    transform: rotate(360deg);
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
