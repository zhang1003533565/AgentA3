<template>
  <view class="conversation-page">
    <nav-bar title="智能助手" :showBack="true" fixed placeholder />

    <view class="conversation-actions">
      <view class="conversation-action" @click="openHistory">
        <text class="conversation-action__icon">◷</text>
        <text>历史</text>
      </view>
      <view class="conversation-action conversation-action--primary" @click="startNewConversation">
        <text class="conversation-action__icon">＋</text>
        <text>新建对话</text>
      </view>
    </view>

    <scroll-view
      class="message-list"
      scroll-y
      :scroll-into-view="scrollAnchor"
      :lower-threshold="80"
      @scroll="handleMessageScroll"
      @scrolltolower="handleReachBottom"
    >
      <view v-if="messages.length === 0" class="conversation-empty">
        <text class="conversation-empty__title">和智能助手开始聊吧</text>
        <text class="conversation-empty__desc">这里会保存成一条历史会话，下次可以从个人中心继续打开。</text>
      </view>

      <view
        v-for="(message, index) in messages"
        :key="message.localId || message.id"
        :id="`msg-${index}`"
        class="message-row"
        :class="message.role === 'user'
          ? 'message-row--user'
          : (message.role === 'action' ? 'message-row--action' : 'message-row--assistant')"
      >
        <view
          class="message-bubble"
          :class="[
            message.role === 'user'
              ? 'message-bubble--user'
              : (message.role === 'action' ? 'message-bubble--action' : 'message-bubble--assistant'),
            message.displayMode ? `message-bubble--${message.displayMode}` : ''
          ]"
        >
          <view v-if="message.role === 'action'" class="action-message">
            <text class="action-message__icon">↳</text>
            <text class="action-message__text">{{ getDisplayText(message) }}</text>
          </view>
          <view v-else-if="message.type === 'thinking' && !shouldShowCallDetail(message)" class="thinking-indicator">
            <text class="thinking-text">{{ message.content || '思考中' }}</text>
            <view class="thinking-dots">
              <text></text>
              <text></text>
              <text></text>
            </view>
          </view>
          <view v-else-if="!(message.type === 'thinking' && shouldShowCallDetail(message))" class="message-content">
            <view v-if="message.role === 'assistant'" class="assistant-identity">
              <view class="assistant-identity__avatar">AI</view>
              <text class="assistant-identity__name">智能助手</text>
              <text v-if="message.responseState === 'stopped'" class="assistant-identity__state">已停止</text>
              <text v-else-if="message.responseState === 'interrupted'" class="assistant-identity__state assistant-identity__state--warning">已中断</text>
            </view>
            <view v-if="message.role === 'assistant' && getVisibleOutputTypeTags(message).length" class="output-type-list">
              <text
                v-for="type in getVisibleOutputTypeTags(message)"
                :key="`${message.localId || message.id}-type-${type}`"
                class="output-type-tag"
                :class="`output-type-tag--${type}`"
              >{{ getOutputTypeLabel(type) }}</text>
            </view>
            <safe-markdown
              v-if="message.role === 'assistant' && getDisplayText(message)"
              class="message-text"
              :content="getDisplayText(message)"
            />
            <text v-else-if="getDisplayText(message)" class="message-text">{{ getDisplayText(message) }}</text>
            <view v-if="shouldShowSmartWritingActions(message)" class="smart-writing-action-bar">
              <view class="smart-writing-action smart-writing-action--save" @click="saveSmartWritingMessage(message)">
                <text class="smart-writing-action__icon">存</text>
                <text>{{ getSmartWritingSaveLabel(message) }}</text>
              </view>
              <view class="smart-writing-action" @click="exportSmartWritingMessage(message)">
                <text class="smart-writing-action__icon">导</text>
                <text>导出</text>
              </view>
              <view class="smart-writing-action" @click="copyMessage(message)">
                <text class="smart-writing-action__icon">复</text>
                <text>复制</text>
              </view>
            </view>
            <view v-if="message.inputAttachments?.length" class="input-attachment-list">
              <view v-for="item in message.inputAttachments" :key="item.id || item.url" class="input-attachment">
                <text class="input-attachment__name">{{ item.name }}</text>
              </view>
            </view>
            <view v-if="isMessageGenerating(message)" class="generation-status">
              <view class="generation-spinner"></view>
              <text class="generation-status__text">图片生成中</text>
            </view>
            <view v-if="getMessageResources(message).length" class="resource-list">
              <view
                v-for="resource in getMessageResources(message)"
                :key="`${message.localId || message.id}-${resource.key}`"
                class="resource-card"
                :class="[`resource-card--${resource.renderer}`, { 'resource-card--unavailable': resource.unavailable }]"
              >
                <view
                  v-if="isFileResource(resource)"
                  class="resource-file"
                  :class="{ 'resource-file--clickable': getPrimaryFileAction(resource) }"
                  @click="openFileResource(resource, message)"
                >
                  <view class="resource-file__icon" :class="`resource-file__icon--${getResourceFileClass(resource)}`">
                    <view class="resource-file__fold"></view>
                    <text class="resource-file__extension">{{ getResourceFileExtension(resource) }}</text>
                  </view>
                  <view class="resource-file__body">
                    <text class="resource-file__name">{{ resource.title }}</text>
                    <text class="resource-file__meta">
                      {{ getResourceSizeLabel(resource) }}{{ getPrimaryFileAction(resource) ? ' · 点击下载' : '' }}
                    </text>
                  </view>
                </view>
                <view v-else class="resource-card__header">
                  <view class="resource-card__icon" :class="`resource-card__icon--${resource.renderer}`">
                    {{ getResourceIcon(resource) }}
                  </view>
                  <view class="resource-card__heading">
                    <text class="resource-card__title">{{ resource.title }}</text>
                    <view class="resource-card__tags">
                      <text class="resource-card__kind">{{ getResourceKindLabel(resource) }}</text>
                      <text
                        class="resource-card__grounding"
                        :class="`resource-card__grounding--${resource.groundingStatus}`"
                      >{{ getGroundingLabel(resource.groundingStatus) }}</text>
                    </view>
                  </view>
                </view>
                <text v-if="!isFileResource(resource) && getResourceSummary(resource)" class="resource-card__summary">{{ getResourceSummary(resource) }}</text>
                <safe-markdown
                  v-if="resource.renderer === 'content' && resource.payload.content"
                  class="resource-card__content"
                  :content="resource.payload.content"
                />
                <view v-if="resource.renderer === 'business_card'" class="resource-card__business">
                  <view
                    v-for="field in getBusinessResourceFields(resource)"
                    :key="`${resource.key}-${field.label}`"
                    class="resource-card__business-field"
                  >
                    <text class="resource-card__business-label">{{ field.label }}</text>
                    <text class="resource-card__business-value">{{ field.value }}</text>
                  </view>
                </view>
                <image
                  v-if="resource.renderer === 'image' && getResourceDisplayPath(resource)"
                  class="resource-card__image"
                  :src="getResourceDisplayPath(resource)"
                  mode="aspectFit"
                  @click="previewResourceImage(resource, message)"
                />
                <video
                  v-else-if="resource.renderer === 'video' && getResourceDisplayPath(resource)"
                  class="resource-card__video"
                  :src="getResourceDisplayPath(resource)"
                  controls
                  object-fit="contain"
                  @play="reportResourceInteraction(resource, message, 'preview')"
                ></video>
                <view
                  v-else-if="resource.renderer === 'audio' && getResourceDisplayPath(resource)"
                  class="resource-card__audio"
                  @click="toggleResourceAudio(resource, message)"
                >
                  <text class="resource-card__audio-icon">{{ isResourceAudioPlaying(resource) ? '停' : '播' }}</text>
                  <text class="resource-card__audio-label">{{ isResourceAudioPlaying(resource) ? '停止播放' : '播放音频' }}</text>
                </view>
                <text v-if="resource.unavailable" class="resource-card__unavailable">旧资源链接已失效，无法继续打开</text>
                <view v-if="!isFileResource(resource) && getResourceActions(resource).length" class="resource-card__actions">
                  <view
                    v-for="action in getResourceActions(resource)"
                    :key="`${resource.key}-${action.type}`"
                    class="resource-card__action"
                    :class="{
                      'resource-card__action--loading': isResourceLoading(resource),
                      'resource-card__action--disabled': action.disabled
                    }"
                    @click="handleResourceAction(resource, action, message)"
                  >{{ action.disabled ? `${action.label}（暂不可用）` : isResourceLoading(resource) ? '处理中' : action.label }}</view>
                </view>
              </view>
            </view>
            <view
              v-if="message.role === 'assistant' && !isSmartWritingContinueMessage(message) && (getMessageChoicePrompt(message) || getFollowUpActions(message).length)"
              class="follow-up-panel"
            >
              <text v-if="getMessageChoicePrompt(message)" class="follow-up-prompt">{{ getMessageChoicePrompt(message) }}</text>
              <view v-if="getFollowUpActions(message).length" class="follow-up-actions">
                <view
                  v-for="(action, actionIndex) in getFollowUpActions(message)"
                  :key="`${message.localId || message.id}-follow-${actionIndex}`"
                  class="follow-up-action"
                  :class="action.style === 'secondary' ? 'follow-up-action--secondary' : 'follow-up-action--primary'"
                  @click="handleFollowUpAction(action, message)"
                >{{ action.label }}</view>
              </view>
            </view>
            <view v-if="message.role === 'assistant' && message.type !== 'thinking' && !isSmartWritingContinueMessage(message)" class="message-action-bar">
              <view class="message-action" @click="copyMessage(message)">复制</view>
              <view class="message-action" @click="retryMessage(message)">重答</view>
            </view>
          </view>
          <view
            v-if="message.role === 'assistant' && shouldShowCallDetail(message) && !(message.type !== 'thinking' && isSmartWritingContinueMessage(message))"
            class="call-detail-panel"
          >
            <view class="call-detail-header" @click="toggleCallDetail(message)">
              <view class="call-detail-heading">
                <view class="call-detail-status-dot" :class="`call-detail-status-dot--${getCallDetailStatus(message)}`"></view>
                <view class="call-detail-title-wrap">
                  <view class="call-detail-thinking-title">
                    <text class="call-detail-title">思考中</text>
                    <view class="thinking-dots thinking-dots--inline">
                      <text></text>
                      <text></text>
                      <text></text>
                    </view>
                  </view>
                </view>
              </view>
              <text class="call-detail-toggle">{{ isCallDetailExpanded(message) ? '收起' : '展开' }}</text>
            </view>
            <view v-if="isCallDetailExpanded(message)" class="call-detail-body">
              <view class="simple-thinking-steps">
                <view
                  v-for="(step, stepIndex) in getSimpleThinkingSteps(message)"
                  :key="`${message.localId || message.id}-simple-step-${stepIndex}`"
                  class="simple-thinking-step"
                  :class="`simple-thinking-step--${step.status}`"
                >
                  <text class="simple-thinking-step__mark">{{ step.status === 'completed' ? '✓' : '' }}</text>
                  <text class="simple-thinking-step__label">{{ step.label }}</text>
                </view>
              </view>
            </view>
          </view>
        </view>
      </view>
      <view id="message-anchor"></view>
    </scroll-view>

    <scroll-view v-if="pendingResources.length" class="pending-resources" scroll-x>
      <view v-for="item in pendingResources" :key="item.localId" class="pending-resource">
        <view class="pending-resource__body">
          <text class="pending-resource__name">{{ item.name }}</text>
          <text class="pending-resource__status">{{ item.status === 'uploading' ? '上传中' : item.status === 'error' ? item.error : '已就绪' }}</text>
        </view>
        <text v-if="item.status === 'error'" class="pending-resource__action" @click="retryPendingResource(item)">重试</text>
        <text class="pending-resource__remove" @click="removePendingResource(item.localId)">×</text>
      </view>
    </scroll-view>
    <view v-if="exportPanelMessageKey" class="export-panel-mask" @click="closeExportPanel">
      <view class="export-panel" @click.stop>
        <view class="export-option" @click="handleExportOption('txt')">
          <text class="export-option__icon">文</text>
          <text class="export-option__label">纯文本</text>
        </view>
        <view class="export-option" @click="handleExportOption('md')">
          <text class="export-option__icon">MD</text>
          <text class="export-option__label">Markdown</text>
        </view>
        <view class="export-option" @click="handleExportOption('word')">
          <text class="export-option__icon">W</text>
          <text class="export-option__label">Word</text>
        </view>
      </view>
    </view>
    <view class="composer">
      <textarea
        v-model="inputValue"
        class="composer-input"
        placeholder="继续问智能助手"
        maxlength="4000"
        auto-height
        confirm-type="send"
        :confirm-hold="true"
        @confirm="handleInputConfirm"
        @keydown.enter="handleEnterKey"
      />
      <view v-if="sending" class="send-btn send-btn--stop" :class="{ disabled: stopping }" @click="stopGeneration">
        {{ stopping ? '停止中' : '停止' }}
      </view>
      <view v-else class="send-btn" :class="{ disabled: !canSend }" @click="sendMessage()">发送</view>
    </view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import SafeMarkdown from '@/components/safe-markdown/safe-markdown.vue'
import { ASSISTANT_PUBLIC_RESOURCE_HOSTS, BASE_URL } from '@/utils/config.js'
import { uploadAiResource } from '@/utils/upload.js'
import {
  downloadAssistantResource,
  exportSmartWritingAsWord,
  getLeaderSessionDetail,
  queryLeaderAgent,
  streamLeaderAgent,
  submitAssistantResourceInteraction
} from '@/api/ai.js'
import {
  buildResourceInteractionRequest,
  countAssistantHits,
  mergeAssistantMessage,
  normalizeAssistantResources,
  normalizeResourceActions,
  resolveAssistantResourceUrl,
  resolveBusinessResourceRoute,
  summarizeEvidenceChain
} from '@/subpackage_ai/assistantMessage.js'
import { saveSmartWritingSavedWork } from '@/utils/smartWritingHistory.js'

const STORAGE_KEY = 'aiAssistantSessionId'
const SMART_WRITING_CONTINUE_CONTEXT_KEY = 'smartWritingContinueContext'
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
  generate_sql: '生成查询',
  tool_start: '准备调用工具',
  tool_call: '调用工具',
  tool_result_summary: '整理结果',
  done: '完成',
  stopped: '用户停止',
  error: '异常'
}
const CALL_DETAIL_STATUS_LABELS = {
  running: '进行中',
  completed: '已完成',
  stopped: '已停止',
  failed: '失败'
}
const AGENT_LABELS = {
  leader_agent: '智能助手',
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
  ppt_structure_agent: 'PPT 结构智能体',
  ppt_review_agent: 'PPT 审查智能体',
  ppt_image_agent: 'PPT 图片智能体',
  ppt_to_docx_agent: 'PPT 转 Word 智能体',
  meeting_summary_agent: '会议总结智能体'
}
const TOOL_LABELS = {
  text_to_sql: '结构化查询工具',
  java_schedule_api: '课表查询工具',
  java_activity_api: '活动查询工具',
  java_meeting_api: '会议查询工具',
  java_canteen_api: '食堂餐饮查询工具',
  java_facility_api: '设施位置查询工具',
  java_secondhand_api: '旧物查询工具',
  generated_export_tools: '内容整理工具',
  markdown_export_tool: 'Markdown 导出工具',
  docx_export_tool: 'Word 导出工具',
  excel_export_tool: 'Excel 导出工具',
  content_archive_tool: '附件打包工具',
  diagram_source_export_tool: '图表源码导出工具'
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
const RESOURCE_KIND_LABELS = {
  explanation: '知识讲解',
  mind_map: '思维导图',
  diagram: '图表',
  exercise: '练习题',
  code_example: '代码案例',
  extended_reading: '延伸阅读',
  image: '图片',
  video: '视频',
  audio: '音频',
  document: '文档',
  presentation: '演示文稿',
  spreadsheet: '表格',
  bundle: '资料包',
  course: '课程',
  activity: '活动',
  meeting: '会议',
  dining: '餐饮',
  facility: '校园设施',
  secondhand: '二手物品'
}
const RESOURCE_ICON_LABELS = {
  image: '图',
  video: '影',
  audio: '音',
  document: '文',
  presentation: '演',
  spreadsheet: '表',
  bundle: '包',
  content: '答',
  business_card: '校',
  generic: '资'
}
const BUSINESS_FIELD_LABELS = {
  teacherName: '教师',
  weekday: '星期',
  startSection: '开始节次',
  endSection: '结束节次',
  classroom: '教室',
  weekText: '周次',
  category: '分类',
  startTime: '开始时间',
  endTime: '结束时间',
  location: '地点',
  status: '状态',
  openingHours: '营业时间',
  rating: '评分',
  priceRange: '价格区间',
  longitude: '经度',
  latitude: '纬度',
  price: '价格',
  condition: '成色',
  createdAt: '发布时间'
}
const EVIDENCE_SOURCE_LABELS = {
  java_backend: '校园业务数据',
  knowledge_document: '知识文档',
  document: '知识文档',
  conversation_history: '历史会话',
  profile_context: '个人画像'
}
const RESOURCE_INTERACTION_BY_ACTION = {
  open_resource: 'open',
  download: 'download',
  preview: 'preview',
  follow_up: 'follow_up'
}

export default {
  components: { NavBar, SafeMarkdown },
  data() {
    return {
      sessionId: '',
      messages: [],
      inputValue: '',
      pendingResources: [],
      sending: false,
      blockingRequestCount: 0,
      scrollAnchor: 'message-anchor',
      resourceLocalPaths: {},
      resourceLoading: {},
      resourcePreloadFailures: {},
      reportedInteractions: {},
      audioContext: null,
      activeAudioKey: '',
      activeStreamTask: null,
      activeStreamLocalId: '',
      activeLlmModel: '',
      stopRequestedLocalId: '',
      stopping: false,
      autoFollowMessages: true,
      exportPanelMessageKey: '',
      viewEpoch: 0,
      localRevision: 0,
      historyRequestGeneration: 0,
      resourceContextGeneration: 0
    }
  },
  computed: {
    canSend() {
      return !this.sending
        && !this.pendingResources.some((item) => item.status === 'uploading')
        && (this.inputValue.trim().length > 0
          || this.pendingResources.some((item) => item.status === 'success'))
    }
  },
  watch: {
    messages: {
      deep: true,
      handler() {
        this.$nextTick(() => this.preloadMessageImages())
      }
    }
  },
  onLoad(options = {}) {
    this.sessionId = options.sessionId ? decodeURIComponent(options.sessionId) : ''
    if (options.prefill) {
      try { this.inputValue = decodeURIComponent(options.prefill) } catch (error) { this.inputValue = String(options.prefill) }
    }
    if (this.sessionId) {
      uni.setStorageSync(STORAGE_KEY, this.sessionId)
    }
    this.loadDetail()
    if (options.smartWritingContinue === '1') {
      const context = uni.getStorageSync(SMART_WRITING_CONTINUE_CONTEXT_KEY) || {}
      uni.removeStorageSync(SMART_WRITING_CONTINUE_CONTEXT_KEY)
      const requestText = String(context.requestText || '').trim()
      const displayText = String(context.displayText || '请帮我续写这篇作品').trim()
      const llmModel = String(context.llmModel || '').trim()
      this.activeLlmModel = llmModel
      if (requestText) {
        this.inputValue = ''
        this.$nextTick(() => {
          this.sendMessage({
            requestText,
            displayText,
            displayMode: context.displayMode,
            sceneMode: 'smart_writing_continue',
            llmModel
          })
        })
      }
    }
  },
  onUnload() {
    this.abortActiveStream(true)
    this.advanceViewEpoch()
    this.disposeAudio()
  },
  methods: {
    async loadDetail() {
      if (!this.sessionId) return
      const requestedSessionId = this.sessionId
      const requestGeneration = ++this.historyRequestGeneration
      const viewEpoch = this.viewEpoch
      const localRevision = this.localRevision
      this.resetResourceState()
      try {
        const res = await getLeaderSessionDetail(requestedSessionId)
        if (this.sessionId !== requestedSessionId
          || requestGeneration !== this.historyRequestGeneration
          || viewEpoch !== this.viewEpoch
          || localRevision !== this.localRevision) return
        const data = res?.data || {}
        this.messages = (data.messages || []).map((item) => {
          const merged = mergeAssistantMessage({}, item)
          const isAction = item.role === 'user' && String(item.answerType || '').startsWith('action_')
          return {
            ...merged,
            role: isAction ? 'action' : item.role,
            requestContent: isAction ? String(item?.outputMeta?.requestContent || '') : '',
            interactionType: isAction ? String(item?.outputMeta?.interactionType || '') : '',
            localId: `${item.role}-${item.id}`
          }
        })
        this.markLocalMutation()
        this.scrollToBottom(true)
      } catch (error) {
        if (this.sessionId === requestedSessionId
          && requestGeneration === this.historyRequestGeneration
          && viewEpoch === this.viewEpoch
          && localRevision === this.localRevision) {
          this.messages = []
          this.markLocalMutation()
        }
      }
    },
    appendMessage(message, forceScroll = false) {
      const item = {
        localId: `${message.role}-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
        ...message
      }
      this.messages.push(item)
      this.markLocalMutation()
      this.scrollToBottom(forceScroll)
      return item
    },
    async sendMessage(options = {}) {
      const requestOptions = options && typeof options === 'object' && !Array.isArray(options)
        ? options
        : {}
      const hasRequestText = Object.prototype.hasOwnProperty.call(requestOptions, 'requestText')
      const text = String(hasRequestText ? requestOptions.requestText : this.inputValue).trim()
      const inputAttachments = hasRequestText
        ? []
        : this.pendingResources.filter((item) => item.status === 'success').map((item) => item.resource)
      const requestText = text || (inputAttachments.length ? '请分析我上传的资源' : '')
      if (!requestText || this.sending || this.pendingResources.some((item) => item.status === 'uploading')) return
      const displayText = String(requestOptions.displayText || requestText).trim()
      const displayRole = requestOptions.displayRole === 'action' ? 'action' : 'user'
      const interactionType = String(requestOptions.interactionType || '').trim()
      const sceneMode = String(requestOptions.sceneMode || '').trim()
      const llmModel = String(requestOptions.llmModel || this.activeLlmModel || '').trim()
      if (!hasRequestText) {
        this.inputValue = ''
        this.pendingResources = []
      }
      const releaseComposer = this.createComposerRelease()
      this.appendMessage({
        role: displayRole,
        content: displayText,
        requestContent: requestText,
        inputAttachments,
        answerType: interactionType ? `action_${interactionType}` : 'text',
        interactionType,
        displayMode: requestOptions.displayMode || '',
        sceneMode
      }, true)
      const thinkingMessage = this.appendMessage({
        role: 'assistant',
        type: 'thinking',
        content: '思考中',
        sceneMode,
        callDetail: this.createInitialCallDetail(displayText),
        callDetailExpanded: false
      }, true)
      const requestContext = this.captureViewContext({ localId: thinkingMessage.localId })
      const isRequestCurrent = () => this.isViewContextCurrent(requestContext, true)
      let streamStarted = false
      let streamTouched = false
      let authoritativeTerminalHandled = false
      const leaderRequest = {
        sessionId: requestContext.sessionId,
        agentName: 'leader_agent',
        input: requestText,
        ...(llmModel ? { llmModel } : {}),
        ...(inputAttachments.length ? { attachments: inputAttachments } : {}),
        ...(interactionType ? {
          interactionType,
          displayInput: displayText,
          requestedOutputType: String(requestOptions.requestedOutputType || '').trim(),
          sourceMessageId: requestOptions.sourceMessageId || null
        } : {})
      }
      try {
        const streamTask = streamLeaderAgent(leaderRequest, {
          onEvent: (eventName, payload) => {
            if (!isRequestCurrent()) return
            streamTouched = true
            if (eventName === 'generation_start') {
              streamStarted = true
              this.applyGenerationStart(thinkingMessage.localId, payload)
              releaseComposer()
              return
            }
            if (eventName === 'tool_start' && payload?.message) {
              const current = this.messages.find((item) => item.localId === thinkingMessage.localId)
              if (current) {
                this.replaceMessage(thinkingMessage.localId, {
                  ...current,
                  type: 'thinking',
                  content: payload.message
                })
              }
            }
            this.updateCallDetail(thinkingMessage.localId, this.buildLiveCallDetailPatch(eventName, payload))
          },
          onSession: (payload) => {
            if (!isRequestCurrent()) return
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
            if (!isRequestCurrent()) return
            streamTouched = true
            const hasMatchedResults = Object.prototype.hasOwnProperty.call(payload || {}, 'matchedResults')
              && Array.isArray(payload?.matchedResults)
            const matchedResultsPatch = hasMatchedResults ? { matchedResults: payload.matchedResults } : {}
            const retrievalMeta = payload?.retrievalMeta || {}
            this.updateCallDetail(thinkingMessage.localId, {
              searchKeyword: payload?.searchKeyword || '',
              ...matchedResultsPatch,
              retrievalMeta,
              currentStep: 'retrieve',
              traceAppend: this.createLiveTraceStep('retrieve', {
                keyword: payload?.searchKeyword || '',
                matchedCount: countAssistantHits({ ...matchedResultsPatch, retrievalMeta }),
                ...retrievalMeta
              })
            })
          },
          onDelta: (content) => {
            if (!content || !isRequestCurrent()) return
            streamTouched = true
            streamStarted = true
            this.updateCallDetail(thinkingMessage.localId, {
              currentStep: 'answer',
              traceAppendOnce: this.createLiveTraceStep('answer', { streaming: true })
            })
            this.appendMessageContent(thinkingMessage.localId, content)
          },
          onDone: (payload) => {
            if (!isRequestCurrent() || this.stopRequestedLocalId === thinkingMessage.localId) return
            streamTouched = true
            this.syncSessionId(payload?.sessionId)
            const finalAnswer = payload?.answer || ''
            const current = this.messages.find((item) => item.localId === thinkingMessage.localId)
            const finalContent = finalAnswer || (current?.type === 'thinking' ? '' : current?.content || '')
            if (!String(finalContent).trim()) {
              throw new Error('模型未返回内容')
            }
            const merged = mergeAssistantMessage(current, {
              ...(payload || {}),
              ...(Array.isArray(payload?.resources) && payload.resources.length === 0
                && !Object.prototype.hasOwnProperty.call(payload || {}, 'attachments')
                  ? { attachments: [] }
                  : {}),
              role: 'assistant',
              type: '',
              content: finalContent,
              answerType: payload?.answerType || 'text',
              outputType: payload?.outputType || payload?.answerType || 'text',
              agentName: payload?.agentName || current?.callDetail?.agentName || 'leader_agent',
              searchKeyword: payload?.searchKeyword || current?.callDetail?.searchKeyword || '',
              sceneMode: current?.sceneMode || sceneMode,
              callDetail: this.buildFinalCallDetail(payload, current?.callDetail, 'completed'),
              callDetailExpanded: current?.callDetailExpanded || false,
              evidenceExpanded: current?.evidenceExpanded || false,
              responseState: 'completed'
            })
            this.replaceMessage(thinkingMessage.localId, merged)
            authoritativeTerminalHandled = true
            releaseComposer()
          },
          onError: (payload) => {
            if (!isRequestCurrent() || this.stopRequestedLocalId === thinkingMessage.localId) return
            streamTouched = true
            const streamError = new Error(payload?.message || '流式请求失败')
            streamError.payload = payload || {}
            throw streamError
          }
        })
        this.activeStreamTask = streamTask
        this.activeStreamLocalId = thinkingMessage.localId
        await streamTask
      } catch (error) {
        if (!isRequestCurrent() || authoritativeTerminalHandled) return
        if (this.stopRequestedLocalId === thinkingMessage.localId || this.isAbortError(error)) {
          this.finalizeStoppedMessage(thinkingMessage.localId)
          return
        }
        const current = this.messages.find((item) => item.localId === thinkingMessage.localId)
        const errorCallDetail = this.buildErrorCallDetail(error, current?.callDetail)
        if (error?.fallbackToNormalRequest && !streamStarted && !streamTouched) {
          await this.sendMessageFallback(leaderRequest, thinkingMessage.localId, error, requestContext)
        } else {
          this.messages = this.messages.filter((item) => item.localId !== thinkingMessage.localId)
          uni.showToast({
            title: errorCallDetail.error || '模型回复失败',
            icon: 'none'
          })
        }
      } finally {
        const finishingOwnStop = this.stopRequestedLocalId === thinkingMessage.localId
        if (this.activeStreamLocalId === thinkingMessage.localId) {
          this.activeStreamTask = null
          this.activeStreamLocalId = ''
        }
        if (finishingOwnStop) {
          this.stopRequestedLocalId = ''
        }
        if (!this.stopRequestedLocalId) this.stopping = false
        releaseComposer()
      }
    },
    stopGeneration() {
      if (!this.activeStreamTask || !this.activeStreamLocalId || this.stopping) return
      this.stopping = true
      this.stopRequestedLocalId = this.activeStreamLocalId
      this.finalizeStoppedMessage(this.activeStreamLocalId)
      if (typeof this.activeStreamTask.abort === 'function') {
        this.activeStreamTask.abort('user_cancelled')
      } else if (typeof this.activeStreamTask?.controller?.abort === 'function') {
        this.activeStreamTask.controller.abort('user_cancelled')
      }
    },
    abortActiveStream(silent = false) {
      if (!this.activeStreamTask) return
      if (!silent && this.activeStreamLocalId) {
        this.stopRequestedLocalId = this.activeStreamLocalId
        this.finalizeStoppedMessage(this.activeStreamLocalId)
      }
      if (typeof this.activeStreamTask.abort === 'function') {
        this.activeStreamTask.abort(silent ? 'page_unload' : 'user_cancelled')
      } else if (typeof this.activeStreamTask?.controller?.abort === 'function') {
        this.activeStreamTask.controller.abort(silent ? 'page_unload' : 'user_cancelled')
      }
    },
    isAbortError(error) {
      return error?.name === 'AbortError' || error?.code === 'ABORT_ERR'
    },
    finalizeStoppedMessage(localId) {
      const current = this.messages.find((item) => item.localId === localId)
      if (!current || current.responseState === 'stopped') return
      const partialContent = current.type === 'thinking' ? '' : String(current.content || '').trim()
      const detail = this.normalizeCallDetail(current)
      this.replaceMessage(localId, {
        ...current,
        role: 'assistant',
        type: '',
        content: partialContent ? `${partialContent}\n\n> 已停止生成。` : '已停止生成。',
        responseState: 'stopped',
        callDetail: {
          ...detail,
          status: 'stopped',
          currentStep: 'stopped',
          trace: this.withTerminalTrace(detail.trace, 'stopped')
        }
      })
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
      const merged = mergeAssistantMessage(current, {
        ...(payload || {}),
        role: 'assistant',
        type: '',
        content: payload?.answer || '',
        answerType: payload?.answerType || 'image_generation',
        outputType: payload?.outputType || 'image',
        outputTypes: payload?.outputTypes || ['image'],
        outputMeta: {
          ...((payload && payload.outputMeta) || {}),
          generationStatus: 'running'
        },
        agentName: payload?.agentName || callDetail.agentName || 'image_agent',
        searchKeyword: payload?.searchKeyword || '',
        callDetail,
        callDetailExpanded: current?.callDetailExpanded || false
      })
      this.replaceMessage(localId, merged)
    },
    async sendMessageFallback(requestPayload, localId, streamError, requestContext) {
      if (!this.isViewContextCurrent(requestContext, true)) return
      try {
        const fallbackTask = queryLeaderAgent({
          ...(requestPayload || {}),
          sessionId: requestContext.sessionId,
          agentName: 'leader_agent'
        })
        if (!this.isViewContextCurrent(requestContext, true)) {
          fallbackTask?.abort?.('stale_conversation')
          return
        }
        this.activeStreamTask = fallbackTask
        this.activeStreamLocalId = localId
        const res = await fallbackTask
        if (!this.isViewContextCurrent(requestContext, true)) return
        const beforeMerge = this.messages.find((item) => item.localId === localId)
        if (this.stopRequestedLocalId === localId || beforeMerge?.responseState === 'stopped') return
        const payload = res?.data || {}
        if (!String(payload.answer || '').trim()) {
          throw new Error('模型未返回内容')
        }
        this.syncSessionId(payload.sessionId)
        const current = this.messages.find((item) => item.localId === localId)
        const merged = mergeAssistantMessage(current, {
          ...(payload || {}),
          ...(Array.isArray(payload?.resources) && payload.resources.length === 0
            && !Object.prototype.hasOwnProperty.call(payload || {}, 'attachments')
              ? { attachments: [] }
              : {}),
          role: 'assistant',
          type: '',
          content: payload.answer,
          answerType: payload.answerType || 'text',
          outputType: payload.outputType || payload.answerType || 'text',
          agentName: payload.agentName || 'leader_agent',
          searchKeyword: payload.searchKeyword || '',
          sceneMode: current?.sceneMode || requestPayload?.sceneMode || '',
          callDetail: this.buildFinalCallDetail(payload, null, 'completed'),
          callDetailExpanded: false
        })
        this.replaceMessage(localId, merged)
      } catch (error) {
        if (!this.isViewContextCurrent(requestContext, true)) return
        const current = this.messages.find((item) => item.localId === localId)
        if (this.stopRequestedLocalId === localId || current?.responseState === 'stopped') return
        const errorCallDetail = this.buildErrorCallDetail(error, null)
        this.messages = this.messages.filter((item) => item.localId !== localId)
        uni.showToast({
          title: errorCallDetail.error || streamError?.message || '模型回复失败',
          icon: 'none'
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
        trace: [
          this.createLiveTraceStep('status', {
            stage: 'processing',
            message: '已提交给智能助手'
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
        trace: nextTrace
      }
      if (Object.prototype.hasOwnProperty.call(patch || {}, 'matchedResults') && Array.isArray(patch.matchedResults)) {
        nextDetail.matchedResults = patch.matchedResults
      } else if (Array.isArray(currentDetail.matchedResults)) {
        nextDetail.matchedResults = currentDetail.matchedResults
      } else {
        delete nextDetail.matchedResults
      }
      delete nextDetail.traceAppend
      delete nextDetail.traceAppendOnce
      this.messages.splice(index, 1, {
        ...current,
        callDetail: nextDetail
      })
      this.markLocalMutation()
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
        trace: this.withTerminalTrace(trace, status)
      }
      if (Object.prototype.hasOwnProperty.call(payload || {}, 'matchedResults') && Array.isArray(payload.matchedResults)) {
        detail.matchedResults = payload.matchedResults
      } else if (Array.isArray(previous.matchedResults)) {
        detail.matchedResults = previous.matchedResults
      } else {
        delete detail.matchedResults
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
      const message = payload?.message
        || retrievalMeta.failureReason
        || error?.message
        || error?.msg
        || '请求失败'
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
    withTerminalTrace(trace, status, errorMessage = '', extraDetail = {}) {
      const list = Array.isArray(trace) ? [...trace] : []
      const terminalStage = status === 'failed' ? 'error' : (status === 'stopped' ? 'stopped' : 'done')
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
    shouldShowCallDetail(message) {
      if (!this.hasCallDetail(message)) return false
      const detail = this.normalizeCallDetail(message)
      if (message?.type === 'thinking' || ['running', 'failed', 'stopped'].includes(detail.status)) return true
      const intent = String(detail.intent || '').toLowerCase()
      const matchedCount = this.getMatchedCount(detail)
      const meaningfulStages = (Array.isArray(detail.trace) ? detail.trace : [])
        .map((step) => String(step?.stage || ''))
        .filter((stage) => stage && !['status', 'processing', 'session', 'answer', 'done'].includes(stage))
      if (matchedCount > 0 || this.getToolNameFromDetail(detail) || meaningfulStages.length) return true
      return !['smalltalk', 'greeting'].includes(intent)
    },
    isSmartWritingContinueMessage(message) {
      return String(message?.sceneMode || '') === 'smart_writing_continue'
    },
    shouldShowSmartWritingActions(message) {
      return Boolean(message
        && message.role === 'assistant'
        && message.type !== 'thinking'
        && this.isSmartWritingContinueMessage(message)
        && String(this.getDisplayText(message) || '').trim())
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
      if (detail.status === 'stopped') return statusLabel
      const matchedCount = this.getMatchedCount(detail)
      if (matchedCount > 0) return `${statusLabel} · 命中 ${matchedCount} 条资料`
      const stepCount = this.getCallDetailSteps(message).length
      return `${statusLabel} · ${stepCount} 个步骤`
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
      const toolName = this.getToolNameFromDetail(detail)
      if (toolName) {
        items.push({ label: '工具', value: this.getToolLabel(toolName, detail.retrievalMeta?.toolDisplayName || detail.toolDisplayName) })
      }
      const toolCache = detail.retrievalMeta?.toolCache
      if (toolCache && Number(toolCache.requestCount || 0) > 0) {
        items.push({ label: '缓存', value: this.getToolCacheLabel(toolCache) })
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
    getToolCacheLabel(cache) {
      const requestCount = Number(cache?.requestCount || 0)
      const hitCount = Number(cache?.hitCount || 0)
      if (!requestCount) return '未使用'
      const hitRate = requestCount ? `${((hitCount / requestCount) * 100).toFixed(1)}%` : '0.0%'
      if (hitCount === requestCount) return `全部命中 · ${hitRate}`
      if (hitCount > 0) return `部分命中 ${hitCount}/${requestCount} · ${hitRate}`
      return `未命中 · ${hitRate}`
    },
    getCallDetailTools(message) {
      const detail = this.normalizeCallDetail(message)
      const tools = []
      const trace = Array.isArray(detail.trace) ? detail.trace : []
      if (trace.some((step) => ['leader_plan', 'leader_route'].includes(String(step?.stage || ''))) || detail.intent) {
        tools.push('智能助手意图识别')
      }
      const usedRetrieval = Boolean(detail.searchKeyword)
        || trace.some((step) => ['retrieve', 'search'].includes(String(step?.stage || '')) && !step?.detail?.skipped)
      if (usedRetrieval) {
        tools.push('业务/知识库检索')
      }
      const toolNames = []
      const detailToolName = this.getToolNameFromDetail(detail)
      if (detailToolName) toolNames.push(detailToolName)
      trace.forEach((step) => {
        const stepDetail = step?.detail || {}
        const name = stepDetail.toolName || stepDetail.tool_name
        if (name) toolNames.push({ name, displayName: stepDetail.toolDisplayName || stepDetail.tool_display_name })
      })
      toolNames.forEach((tool) => {
        if (typeof tool === 'string') {
          tools.push(this.getToolLabel(tool))
        } else {
          tools.push(this.getToolLabel(tool.name, tool.displayName))
        }
      })
      if (detail.agentName && detail.agentName !== 'leader_agent') {
        tools.push(this.getAgentLabel(detail.agentName))
      } else {
        tools.push('智能助手回答汇总')
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
    getSimpleThinkingSteps(message) {
      const detail = this.normalizeCallDetail(message)
      const labels = ['分析创作内容', '组织续写思路', '生成续写内容', '优化表达细节']
      const rawCount = Array.isArray(detail.trace) ? detail.trace.length : 1
      const completedCount = detail.status === 'completed'
        ? labels.length
        : Math.max(1, Math.min(labels.length - 1, rawCount))
      return labels.map((label, index) => ({
        label,
        status: index < completedCount ? 'completed' : 'pending'
      }))
    },
    getCallDetailError(message) {
      return this.normalizeCallDetail(message).error || ''
    },
    getExplicitMatchedResults(message, raw) {
      if (Object.prototype.hasOwnProperty.call(raw || {}, 'matchedResults') && Array.isArray(raw?.matchedResults)) {
        return raw.matchedResults
      }
      if (Object.prototype.hasOwnProperty.call(message || {}, 'matchedResults') && Array.isArray(message?.matchedResults)) {
        return message.matchedResults
      }
      return undefined
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
      const matchedResults = this.getExplicitMatchedResults(message, raw)
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
      const normalized = {
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
      if (matchedResults === undefined) delete normalized.matchedResults
      return normalized
    },
    describeCallDetailStep(stage, detail, normalized) {
      if (stage === 'leader_plan' || stage === 'leader_route') {
        const intent = detail.intent || normalized.intent
        const agent = detail.targetAgent || detail.agentName || normalized.agentName
        const toolName = detail.toolName || detail.tool_name
        const needRetrieval = detail.needRetrieval === true ? '需要检索' : '不需要检索'
        if (toolName) {
          return `意图：${intent ? this.getIntentLabel(intent) : '未识别'} · 工具：${this.getToolLabel(toolName, detail.toolDisplayName || detail.tool_display_name)} · ${needRetrieval}`
        }
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
      if (stage === 'generate_sql') {
        const toolName = detail.toolName || detail.tool_name || normalized.retrievalMeta?.toolName || 'text_to_sql'
        return `${this.getToolLabel(toolName, detail.toolDisplayName || detail.tool_display_name || normalized.retrievalMeta?.toolDisplayName)} 正在生成只读查询`
      }
      if (stage === 'tool_start') {
        const toolName = detail.toolName || detail.tool_name || normalized.retrievalMeta?.toolName
        const message = detail.message || normalized.retrievalMeta?.planningAnswer
        return message || `${this.getToolLabel(toolName, detail.toolDisplayName || detail.tool_display_name || normalized.retrievalMeta?.toolDisplayName)} 准备调用`
      }
      if (stage === 'tool_call') {
        const toolName = detail.toolName || detail.tool_name || normalized.retrievalMeta?.toolName
        return `${this.getToolLabel(toolName, detail.toolDisplayName || detail.tool_display_name || normalized.retrievalMeta?.toolDisplayName)} 已执行`
      }
      if (stage === 'tool_result_summary') {
        const count = detail.resultCount !== undefined && detail.resultCount !== null ? ` · ${detail.resultCount} 条数据` : ''
        if (detail.summarizedByModel) {
          return `智能助手已整理工具结果${count}`
        }
        return detail.error ? `工具结果整理失败，已回退展示${count}` : `工具结果已回退展示${count}`
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
      if (!agentName) return '智能助手'
      if (AGENT_LABELS[agentName]) return AGENT_LABELS[agentName]
      if (String(agentName).startsWith('textbook_question_')) return '题库生成智能体'
      if (String(agentName).startsWith('meeting_')) return '会议智能体'
      return agentName
    },
    getToolLabel(toolName, displayName = '') {
      const name = String(toolName || '').trim()
      const display = String(displayName || '').trim()
      if (display) return display
      if (!name) return '工具'
      const label = TOOL_LABELS[name]
      return label ? `${label}（${name}）` : name
    },
    getToolNameFromDetail(detail) {
      const meta = detail?.retrievalMeta || {}
      const direct = meta.toolName || meta.tool_name || detail?.toolName || detail?.tool_name
      if (direct) return direct
      const trace = Array.isArray(detail?.trace) ? detail.trace : []
      for (const step of trace) {
        const stepDetail = step?.detail || {}
        const name = stepDetail.toolName || stepDetail.tool_name
        if (name) return name
      }
      const executedAgent = meta.executedAgent || meta.targetAgent || detail?.agentName
      return TOOL_LABELS[executedAgent] ? executedAgent : ''
    },
    getMatchedCount(detail) {
      if (Array.isArray(detail?.matchedResults)) {
        return countAssistantHits({ matchedResults: detail.matchedResults })
      }
      return countAssistantHits({ retrievalMeta: detail?.retrievalMeta || {} })
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
      this.markLocalMutation()
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
      this.markLocalMutation()
      this.scrollToBottom()
    },
    removeThinkingMessages() {
      const nextMessages = this.messages.filter((item) => item.type !== 'thinking')
      if (nextMessages.length === this.messages.length) return
      this.messages = nextMessages
      this.markLocalMutation()
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
      this.advanceViewEpoch()
      this.sessionId = ''
      this.messages = []
      this.markLocalMutation()
      this.inputValue = ''
      this.pendingResources = []
      this.resetResourceState()
      uni.removeStorageSync(STORAGE_KEY)
      this.scrollToBottom(true)
    },
    resetResourceState() {
      this.disposeAudio()
      this.resourceLocalPaths = {}
      this.resourceLoading = {}
      this.resourcePreloadFailures = {}
      this.reportedInteractions = {}
      uni.hideLoading?.()
    },
    markLocalMutation() {
      this.localRevision += 1
    },
    advanceViewEpoch() {
      this.viewEpoch += 1
      this.historyRequestGeneration += 1
    },
    captureViewContext(extra = {}) {
      return {
        sessionId: this.sessionId,
        viewEpoch: this.viewEpoch,
        ...extra
      }
    },
    isViewContextCurrent(context, requireMessage = false) {
      if (!context || context.viewEpoch !== this.viewEpoch) return false
      if (!requireMessage || !context.localId) return true
      return this.messages.some((item) => item.localId === context.localId)
    },
    captureResourceContext(resource, message = null) {
      this.resourceContextGeneration += 1
      return this.captureViewContext({
        messageId: resource?.messageId ?? message?.messageId ?? message?.id,
        resourceId: resource?.id,
        resourceKey: resource?.key,
        resourceToken: `${this.viewEpoch}:${this.resourceContextGeneration}`
      })
    },
    isResourceContextCurrent(context) {
      return Boolean(context)
        && context.viewEpoch === this.viewEpoch
        && context.sessionId === this.sessionId
    },
    chooseResources() {
      if (this.sending || this.pendingResources.length >= 8) return
      const supportsAllFiles = typeof uni.chooseMessageFile === 'function'
        || typeof uni.chooseFile === 'function'
      const chooser = typeof uni.chooseMessageFile === 'function'
        ? uni.chooseMessageFile
        : (typeof uni.chooseFile === 'function' ? uni.chooseFile : uni.chooseMedia)
      if (typeof chooser !== 'function') {
        uni.showToast({ title: '当前环境暂不支持文件选择', icon: 'none' })
        return
      }
      if (!supportsAllFiles) {
        uni.showToast({ title: '当前端支持选择图片和视频', icon: 'none' })
      }
      const chooserOptions = {
        count: 8 - this.pendingResources.length,
        success: (result) => {
          const files = Array.isArray(result.tempFiles) ? result.tempFiles : []
          files.forEach((file) => {
            const entry = {
              localId: `${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
              source: file.file || file,
              name: file.name || String(file.path || file.tempFilePath || '').split('/').pop() || 'resource',
              status: 'uploading',
              resource: null,
              error: ''
            }
            this.pendingResources.push(entry)
            this.uploadPendingResource(entry)
          })
        },
        fail: (error) => {
          if (!String(error?.errMsg || '').includes('cancel')) {
            uni.showToast({ title: '选择资源失败', icon: 'none' })
          }
        }
      }
      if (supportsAllFiles) {
        chooserOptions.type = 'all'
        chooserOptions.extension = [
          'jpg', 'jpeg', 'png', 'webp', 'gif', 'pdf', 'doc', 'docx', 'ppt', 'pptx',
          'xls', 'xlsx', 'csv', 'txt', 'md', 'mmd', 'json', 'zip',
          'mp3', 'wav', 'm4a', 'ogg', 'mp4', 'mov', 'webm'
        ]
      } else {
        chooserOptions.mediaType = ['image', 'video']
      }
      chooser(chooserOptions)
    },
    async uploadPendingResource(entry) {
      entry.status = 'uploading'
      entry.error = ''
      try {
        entry.resource = await uploadAiResource(entry.source)
        entry.status = 'success'
      } catch (error) {
        entry.status = 'error'
        entry.error = error?.msg || error?.message || '上传失败'
      }
    },
    retryPendingResource(entry) {
      if (!entry || entry.status === 'uploading') return
      this.uploadPendingResource(entry)
    },
    removePendingResource(localId) {
      this.pendingResources = this.pendingResources.filter((item) => item.localId !== localId)
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
    handleMessageScroll(event) {
      const deltaY = Number(event?.detail?.deltaY || 0)
      if (deltaY < -2) {
        this.autoFollowMessages = false
      }
    },
    handleReachBottom() {
      this.autoFollowMessages = true
    },
    scrollToBottom(force = false) {
      if (!force && !this.autoFollowMessages) {
        return
      }
      if (force) this.autoFollowMessages = true
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
        if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
          if (typeof parsed.message === 'string') return parsed.message.trim()
          if (normalizeAssistantResources(message).length) return ''
        }
      } catch (error) {
        // Plain text remains the readable answer; legacy resources render below via the shared helper.
      }
      return content.trim()
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
    getVisibleOutputTypeTags(message) {
      return this.getOutputTypeTags(message).filter((type) => type !== 'text')
    },
    detectMessageType(message) {
      const resources = this.getMessageResources(message)
      if (resources.some((item) => item.renderer === 'image')) return 'image'
      if (resources.some((item) => item.renderer === 'video')) return 'video'
      if (resources.some((item) => ['document', 'presentation', 'spreadsheet', 'bundle'].includes(item.renderer))) return 'document'
      if (resources.some((item) => item.kind === 'diagram' || item.kind === 'mind_map')) return 'diagram'
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
    getMessageResources(message) {
      return normalizeAssistantResources(message)
    },
    getResourceSummary(resource) {
      const summary = String(resource?.summary || '').trim().replace(/\s+/g, ' ')
      const content = String(resource?.payload?.content || '').trim().replace(/\s+/g, ' ')
      return summary && summary !== content ? resource.summary : ''
    },
    getResourceActions(resource) {
      return normalizeResourceActions(resource)
    },
    getResourceIcon(resource) {
      return RESOURCE_ICON_LABELS[resource?.renderer] || RESOURCE_ICON_LABELS.generic
    },
    isFileResource(resource) {
      return ['document', 'presentation', 'spreadsheet', 'bundle'].includes(resource?.renderer)
    },
    getResourceFileExtension(resource) {
      const title = String(resource?.title || '')
      const titleMatch = title.match(/\.([a-z0-9]{1,8})$/i)
      const value = String(resource?.payload?.format || titleMatch?.[1] || 'FILE').replace(/^\./, '').toUpperCase()
      return ({ WORD: 'DOCX', EXCEL: 'XLSX', MARKDOWN: 'MD' })[value] || value.slice(0, 5)
    },
    getResourceFileClass(resource) {
      const extension = this.getResourceFileExtension(resource).toLowerCase()
      if (['doc', 'docx'].includes(extension)) return 'word'
      if (['xls', 'xlsx', 'csv'].includes(extension)) return 'excel'
      if (extension === 'pdf') return 'pdf'
      if (['zip', 'rar', '7z', 'tar', 'gz'].includes(extension)) return 'archive'
      if (['ppt', 'pptx'].includes(extension)) return 'powerpoint'
      return 'text'
    },
    getResourceSizeLabel(resource) {
      const size = Number(resource?.payload?.size || resource?.metadata?.size || 0)
      if (!Number.isFinite(size) || size <= 0) return this.getResourceFileExtension(resource) + ' 文件'
      if (size < 1024) return `${size} B`
      if (size < 1024 * 1024) return `${(size / 1024).toFixed(size >= 10240 ? 0 : 1)} KB`
      return `${(size / (1024 * 1024)).toFixed(1)} MB`
    },
    getPrimaryFileAction(resource) {
      return this.getResourceActions(resource).find((action) => ['download', 'preview', 'open_resource'].includes(action.type)) || null
    },
    openFileResource(resource, message) {
      const action = this.getPrimaryFileAction(resource)
      if (action) this.handleResourceAction(resource, action, message)
    },
    getResourceKindLabel(resource) {
      return RESOURCE_KIND_LABELS[resource?.kind] || '资源'
    },
    getGroundingLabel(status) {
      const labels = {
        grounded: '有来源',
        context_only: '上下文生成',
        model_only: '模型生成'
      }
      return labels[status] || labels.model_only
    },
    getBusinessResourceFields(resource) {
      const payload = resource?.payload || {}
      const hidden = new Set(['type', 'businessId', 'title', 'name', 'courseName', 'imageUrl'])
      return Object.entries(payload)
        .filter(([key, value]) => !hidden.has(key) && BUSINESS_FIELD_LABELS[key] && value !== '' && value !== null && value !== undefined)
        .slice(0, 8)
        .map(([key, value]) => ({ label: BUSINESS_FIELD_LABELS[key], value: String(value) }))
    },
    getResourceLocalPath(resource) {
      return this.resourceLocalPaths[resource?.key] || ''
    },
    getResourceDisplayPath(resource) {
      const localPath = this.getResourceLocalPath(resource)
      if (localPath) return localPath
      if (resource?.authScope !== 'public') return ''
      const candidates = [resource?.previewUrl, resource?.url]
      for (const value of candidates) {
        const rawUrl = String(value || '').trim()
        if (!rawUrl || rawUrl.startsWith('/api/')) continue
        const resolved = resolveAssistantResourceUrl(rawUrl, {
          baseUrl: BASE_URL,
          approvedHosts: ASSISTANT_PUBLIC_RESOURCE_HOSTS
        })
        if (resolved) return resolved
      }
      return ''
    },
    isResourceLoading(resource) {
      return Boolean(this.resourceLoading[resource?.key])
    },
    setResourceLoading(resource, loading, owner = null) {
      if (!resource?.key) return false
      const next = { ...this.resourceLoading }
      const ownerToken = owner?.resourceToken || owner
      if (loading) {
        next[resource.key] = ownerToken || true
      } else {
        if (ownerToken && next[resource.key] !== ownerToken) return false
        delete next[resource.key]
      }
      this.resourceLoading = next
      return true
    },
    rememberResourceLocalPath(resource, filePath) {
      if (!resource?.key || !filePath) return
      this.resourceLocalPaths = {
        ...this.resourceLocalPaths,
        [resource.key]: filePath
      }
    },
    preloadMessageImages() {
      for (const message of this.messages) {
        for (const resource of this.getMessageResources(message)) {
          if (resource.renderer !== 'image'
            || this.getResourceDisplayPath(resource)
            || this.isResourceLoading(resource)
            || this.resourcePreloadFailures[resource.key]
            || (!resource.url && !resource.previewUrl)) continue
          this.preloadResourceImage(resource, message)
        }
      }
    },
    async preloadResourceImage(resource, message) {
      const actionContext = this.captureResourceContext(resource, message)
      if (!this.setResourceLoading(resource, true, actionContext)) return
      try {
        const filePath = await downloadAssistantResource(resource, {
          approvedHosts: ASSISTANT_PUBLIC_RESOURCE_HOSTS
        })
        if (!this.isResourceContextCurrent(actionContext)) return
        this.rememberResourceLocalPath(resource, filePath)
      } catch (error) {
        if (this.isResourceContextCurrent(actionContext)) {
          this.resourcePreloadFailures = {
            ...this.resourcePreloadFailures,
            [resource.key]: true
          }
        }
      } finally {
        this.setResourceLoading(resource, false, actionContext)
      }
    },
    previewResourceImage(resource, message = null) {
      const actionContext = this.captureResourceContext(resource, message)
      const current = this.getResourceDisplayPath(resource)
      if (!current || !this.isResourceContextCurrent(actionContext)) return
      uni.previewImage({ urls: [current], current })
      this.reportResourceInteraction(resource, message, 'preview', actionContext)
    },
    isResourceAudioPlaying(resource) {
      return Boolean(resource?.key) && this.activeAudioKey === resource.key
    },
    toggleResourceAudio(resource, message = null, resourceContext = null) {
      const actionContext = resourceContext || this.captureResourceContext(resource, message)
      if (!resource?.key || !this.isResourceContextCurrent(actionContext)) return
      if (this.isResourceAudioPlaying(resource)) {
        this.disposeAudio()
        return
      }
      const source = this.getResourceDisplayPath(resource)
      if (!source || typeof uni.createInnerAudioContext !== 'function') {
        uni.showToast({ title: '当前环境无法播放音频', icon: 'none' })
        return
      }
      this.disposeAudio()
      const context = uni.createInnerAudioContext()
      this.audioContext = context
      this.activeAudioKey = resource.key
      context.src = source
      context.onEnded?.(() => {
        if (this.audioContext === context && this.isResourceContextCurrent(actionContext)) {
          this.disposeAudio()
        }
      })
      context.onError?.(() => {
        if (this.audioContext !== context || !this.isResourceContextCurrent(actionContext)) return
        this.disposeAudio()
        uni.showToast({ title: '音频播放失败', icon: 'none' })
      })
      context.play()
      if (message) this.reportResourceInteraction(resource, message, 'preview', actionContext)
    },
    disposeAudio() {
      const context = this.audioContext
      this.audioContext = null
      this.activeAudioKey = ''
      if (!context) return
      try {
        context.stop?.()
        context.destroy?.()
      } catch (error) {
        // The native audio context may already be disposed during page teardown.
      }
    },
    async handleResourceAction(resource, action, message) {
      if (!resource || !action || resource.unavailable || action.disabled || action.unavailable
        || this.isResourceLoading(resource)) return
      const actionContext = this.captureResourceContext(resource, message)
      if (!this.isResourceContextCurrent(actionContext)) return
      if (action.type === 'follow_up') {
        if (!action.prompt || this.sending) return
        this.reportResourceInteraction(resource, message, action.type, actionContext)
        this.inputValue = action.prompt
        this.sendMessage()
        return
      }
      if (action.type === 'open_resource' && resource.renderer === 'business_card') {
        const route = resolveBusinessResourceRoute(resource)
        if (!route) {
          uni.showToast({ title: '该资源暂无可打开页面', icon: 'none' })
          return
        }
        uni.navigateTo({
          url: route,
          success: () => {
            if (this.isResourceContextCurrent(actionContext)) {
              this.reportResourceInteraction(resource, message, action.type, actionContext)
            }
          }
        })
        return
      }
      if (!resource.url && !resource.previewUrl) {
        uni.showToast({ title: '资源暂不可用', icon: 'none' })
        return
      }
      this.setResourceLoading(resource, true, actionContext)
      uni.showLoading({ title: action.type === 'download' ? '下载中...' : '打开中...' })
      try {
        const filePath = await downloadAssistantResource(resource, {
          approvedHosts: ASSISTANT_PUBLIC_RESOURCE_HOSTS
        })
        if (!this.isResourceContextCurrent(actionContext)) return
        this.rememberResourceLocalPath(resource, filePath)
        this.openDownloadedResource(resource, filePath, action.type, actionContext)
        this.reportResourceInteraction(resource, message, action.type, actionContext)
      } catch (error) {
        if (!this.isResourceContextCurrent(actionContext)) return
        uni.showToast({ title: error?.message || '资源打开失败', icon: 'none' })
      } finally {
        if (this.isResourceContextCurrent(actionContext)
          && this.setResourceLoading(resource, false, actionContext)) {
          uni.hideLoading()
        }
      }
    },
    openDownloadedResource(resource, filePath, actionType = 'preview', resourceContext = null) {
      const actionContext = resourceContext || this.captureResourceContext(resource)
      if (!this.isResourceContextCurrent(actionContext)) return
      if (resource.renderer === 'image') {
        uni.previewImage({ urls: [filePath], current: filePath })
        return
      }
      if (resource.renderer === 'video') {
        uni.showToast({ title: '视频已加载，可在卡片中播放', icon: 'none' })
        return
      }
      if (resource.renderer === 'audio') {
        if (actionType === 'download' && typeof uni.saveFile === 'function') {
          uni.saveFile({
            tempFilePath: filePath,
            success: ({ savedFilePath }) => {
              if (!this.isResourceContextCurrent(actionContext)) return
              this.rememberResourceLocalPath(resource, savedFilePath || filePath)
              uni.showToast({ title: '音频已保存，可在卡片中播放', icon: 'none' })
            },
            fail: () => {
              if (this.isResourceContextCurrent(actionContext)) {
                uni.showToast({ title: '音频已临时保存，可在卡片中播放', icon: 'none' })
              }
            }
          })
          return
        }
        this.toggleResourceAudio(resource, null, actionContext)
        return
      }
      if (typeof uni.openDocument === 'function') {
        uni.openDocument({
          filePath,
          showMenu: true,
          fail: () => {
            if (this.isResourceContextCurrent(actionContext)) {
              uni.showToast({ title: '文件已下载，当前环境无法预览', icon: 'none' })
            }
          }
        })
        return
      }
      uni.showToast({ title: '文件已下载', icon: 'none' })
    },
    reportResourceInteraction(resource, message, actionType, resourceContext = null) {
      const actionContext = resourceContext || this.captureResourceContext(resource, message)
      if (!this.isResourceContextCurrent(actionContext)) return
      const interaction = RESOURCE_INTERACTION_BY_ACTION[actionType]
      const sessionId = actionContext.sessionId
      const messageId = actionContext.messageId
        ?? resource?.messageId ?? message?.messageId ?? message?.id
      const request = buildResourceInteractionRequest(sessionId, messageId, resource?.id, interaction)
      if (!request || this.reportedInteractions[request.dedupeKey]) return
      this.reportedInteractions = {
        ...this.reportedInteractions,
        [request.dedupeKey]: actionContext.resourceToken
      }
      void submitAssistantResourceInteraction(sessionId, messageId, resource.id, interaction).catch((error) => {
        if (!this.isResourceContextCurrent(actionContext)
          || this.reportedInteractions[request.dedupeKey] !== actionContext.resourceToken) return
        const next = { ...this.reportedInteractions }
        delete next[request.dedupeKey]
        this.reportedInteractions = next
        console.warn('[assistant-resource-interaction]', {
          sessionId: this.truncateText(sessionId, 80),
          messageId: this.truncateText(messageId, 80),
          resourceId: this.truncateText(resource.id, 80),
          action: interaction,
          status: Number(error?.statusCode || error?.status || 0) || 'failed'
        })
      })
    },
    getEvidenceSummary(message) {
      return summarizeEvidenceChain(message?.evidenceChain)
    },
    shouldShowEvidence(message) {
      if (!message || message.role !== 'assistant' || message.type === 'thinking' || !message.evidenceChain) return false
      const summary = this.getEvidenceSummary(message)
      if (summary.state === 'legacy_missing') return false
      const intent = String(this.normalizeCallDetail(message).intent || '').toLowerCase()
      if (summary.status === 'model_only' && ['smalltalk', 'greeting'].includes(intent)) return false
      return true
    },
    getEvidenceCompactLabel(message) {
      const summary = this.getEvidenceSummary(message)
      if (summary.state !== 'available') return summary.label
      if (summary.status === 'grounded') return `引用 ${summary.sourceCount} 个来源`
      if (summary.status === 'context_only') return '基于本次会话上下文'
      return '未引用外部资料'
    },
    getEvidenceIcon(message) {
      const summary = this.getEvidenceSummary(message)
      if (['malformed', 'integrity_failed', 'generation_failed'].includes(summary.state)) return '!'
      return summary.status === 'grounded' ? '✓' : 'i'
    },
    getEvidenceAgentLabel(message) {
      const agent = this.getEvidenceSummary(message).agent
      return agent ? this.getAgentLabel(agent) : '智能体未记录'
    },
    getEvidenceGeneratedAtLabel(message) {
      return this.getEvidenceSummary(message).generatedAt || '生成时间未记录'
    },
    getEvidenceSources(message) {
      const summary = this.getEvidenceSummary(message)
      if (!summary.trusted) return []
      return Array.isArray(message?.evidenceChain?.sources)
        ? message.evidenceChain.sources.slice(0, 20)
        : []
    },
    getEvidenceSourceLabel(sourceType) {
      return EVIDENCE_SOURCE_LABELS[sourceType] || '来源记录'
    },
    toggleEvidence(message) {
      const key = this.getMessageKey(message)
      const index = this.messages.findIndex((item) => this.getMessageKey(item) === key)
      if (index === -1) return
      this.messages.splice(index, 1, {
        ...this.messages[index],
        evidenceExpanded: !this.messages[index].evidenceExpanded
      })
      this.scrollToBottom()
    },
    isEvidenceExpanded(message) {
      return Boolean(message?.evidenceExpanded)
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
    copyMessage(message) {
      const content = this.getDisplayText(message)
      if (!content || typeof uni.setClipboardData !== 'function') return
      uni.setClipboardData({
        data: content,
        success: () => uni.showToast({ title: '已复制', icon: 'none' }),
        fail: () => uni.showToast({ title: '复制失败', icon: 'none' })
      })
    },
    getSmartWritingSaveLabel(message) {
      return message?.outputMeta?.savedWorkId ? '已保存' : '保存'
    },
    buildSmartWritingRecord(message) {
      const content = String(this.getDisplayText(message) || '').trim()
      const titleSource = content.split(/\n+/).find(Boolean) || '智能写作作品'
      const title = titleSource.length > 18 ? `${titleSource.slice(0, 18)}...` : titleSource
      const idSource = message?.messageId || message?.id || message?.localId || Date.now()
      return {
        id: `smart-writing-continue-${idSource}`,
        title,
        scene: 'continue',
        sceneLabel: 'AI续写',
        prompt: titleSource,
        content,
        model: this.activeLlmModel || message?.model || message?.outputMeta?.model || '',
        modelConfigPrefix: this.activeLlmModel || '',
        createdAt: new Date().toISOString()
      }
    },
    formatExportTime(value = new Date()) {
      const date = value instanceof Date ? value : new Date(value)
      if (Number.isNaN(date.getTime())) return ''
      const pad = (number) => String(number).padStart(2, '0')
      return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
    },
    getSafeExportFilename(title, extension) {
      const safeTitle = String(title || '智能写作')
        .replace(/[\\/:*?"<>|]/g, '')
        .replace(/\s+/g, '-')
        .slice(0, 28) || '智能写作'
      return `${safeTitle}-${Date.now()}.${extension}`
    },
    buildSmartWritingExportData(message) {
      const record = this.buildSmartWritingRecord(message)
      const generatedAt = this.formatExportTime()
      const model = record.model || '未记录'
      const title = record.title || '智能写作作品'
      return {
        title,
        text: [
          `标题：${title}`,
          `类型：${record.sceneLabel || '智能写作'}`,
          `导出时间：${generatedAt}`,
          `模型：${model}`,
          '',
          '正文：',
          record.content
        ].join('\n'),
        markdown: [
          `# ${title}`,
          '',
          `- 类型：${record.sceneLabel || '智能写作'}`,
          `- 导出时间：${generatedAt}`,
          `- 模型：${model}`,
          '',
          '## 正文',
          '',
          record.content
        ].join('\n')
      }
    },
    downloadTextContent(content, filename, successTitle) {
      if (typeof window !== 'undefined' && typeof document !== 'undefined' && typeof Blob !== 'undefined') {
        const blob = new Blob([`\uFEFF${content}`], { type: 'text/plain;charset=utf-8' })
        const url = window.URL.createObjectURL(blob)
        const link = document.createElement('a')
        link.href = url
        link.download = filename
        document.body.appendChild(link)
        link.click()
        document.body.removeChild(link)
        window.URL.revokeObjectURL(url)
        uni.showToast({ title: successTitle, icon: 'success' })
        return
      }
      uni.showToast({ title: '当前环境暂不支持下载文件', icon: 'none' })
    },
    saveSmartWritingMessage(message) {
      if (!this.shouldShowSmartWritingActions(message)) {
        uni.showToast({ title: '暂无可保存内容', icon: 'none' })
        return
      }
      const savedRecord = saveSmartWritingSavedWork(this.buildSmartWritingRecord(message))
      const key = this.getMessageKey(message)
      const index = this.messages.findIndex((item) => this.getMessageKey(item) === key)
      if (index >= 0) {
        const outputMeta = { ...(this.messages[index].outputMeta || {}), savedWorkId: savedRecord.id }
        this.messages.splice(index, 1, { ...this.messages[index], outputMeta })
        this.markLocalMutation()
      }
      uni.showToast({ title: '已保存', icon: 'success' })
    },
    exportSmartWritingMessage(message) {
      const content = String(this.getDisplayText(message) || '').trim()
      if (!content) {
        uni.showToast({ title: '暂无可导出内容', icon: 'none' })
        return
      }
      this.exportPanelMessageKey = this.getMessageKey(message)
    },
    closeExportPanel() {
      this.exportPanelMessageKey = ''
    },
    getExportPanelMessage() {
      if (!this.exportPanelMessageKey) return null
      return this.messages.find((item) => this.getMessageKey(item) === this.exportPanelMessageKey) || null
    },
    handleExportOption(type) {
      const message = this.getExportPanelMessage()
      this.closeExportPanel()
      if (!message) return
      const exportData = this.buildSmartWritingExportData(message)
      if (type === 'txt') {
        this.downloadTextContent(
          exportData.text,
          this.getSafeExportFilename(exportData.title, 'txt'),
          '已导出文本'
        )
      } else if (type === 'md') {
        this.downloadTextContent(
          exportData.markdown,
          this.getSafeExportFilename(exportData.title, 'md'),
          '已导出 Markdown'
        )
      } else if (type === 'word') {
        this.downloadWordDocument(message, exportData)
      }
    },
    downloadWordDocument(message, exportData) {
      uni.showLoading({ title: '正在生成文档...' })
      const record = this.buildSmartWritingRecord(message)
      const payload = {
        title: exportData.title,
        sceneLabel: record.sceneLabel || '',
        generatedAt: record.createdAt ? this.formatExportTime(new Date(record.createdAt)) : '',
        model: record.model || '',
        content: String(record.content || '').trim()
      }
      exportSmartWritingAsWord(payload)
        .then(blob => {
          if (typeof window !== 'undefined' && typeof document !== 'undefined' && typeof URL !== 'undefined') {
            const url = window.URL.createObjectURL(blob)
            const link = document.createElement('a')
            link.href = url
            link.download = this.getSafeExportFilename(exportData.title, 'docx')
            document.body.appendChild(link)
            link.click()
            document.body.removeChild(link)
            window.URL.revokeObjectURL(url)
            uni.hideLoading()
            uni.showToast({ title: '已导出 Word 文档', icon: 'success' })
          } else {
            uni.hideLoading()
            uni.showToast({ title: '当前环境暂不支持下载文件', icon: 'none' })
          }
        })
        .catch(error => {
          uni.hideLoading()
          const msg = error?.message || '导出失败'
          uni.showToast({ title: msg, icon: 'none' })
        })
    },
    retryMessage(message) {
      if (!message || this.sending) return
      const index = this.messages.findIndex((item) => this.getMessageKey(item) === this.getMessageKey(message))
      if (index < 0) return
      let source = null
      for (let cursor = index - 1; cursor >= 0; cursor -= 1) {
        if (['user', 'action'].includes(this.messages[cursor]?.role)) {
          source = this.messages[cursor]
          break
        }
      }
      const requestText = String(source?.requestContent || source?.content || '').trim()
      if (!requestText) return
      return this.sendMessage({
        requestText,
        displayText: '重新生成上一条回答',
        displayRole: 'action',
        interactionType: 'retry',
        sourceMessageId: message?.messageId || message?.id || null
      })
    },
    handleFollowUpAction(action, message) {
      if (!action?.prompt || this.sending) return
      return this.sendMessage({
        requestText: action.prompt,
        displayText: `已请求：${action.label}`,
        displayRole: 'action',
        interactionType: 'transform',
        requestedOutputType: action.outputType || '',
        sourceMessageId: message?.messageId || message?.id || null
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.conversation-page {
  position: relative;
  height: 100vh;
  background: #F7F7F9;
  display: flex;
  flex-direction: column;
  padding-bottom: 0;
  box-sizing: border-box;
  overflow: hidden;
}

.conversation-actions {
  flex-shrink: 0;
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12rpx;
  padding: 12rpx 24rpx;
  background: rgba(247, 247, 249, 0.96);
  border-bottom: 1rpx solid rgba(226, 232, 240, 0.72);
  box-sizing: border-box;
  width: 100%;
}

.composer > .composer-actions {
  display: none !important;
}

.conversation-action {
  flex: none;
  min-width: 112rpx;
  height: 60rpx;
  padding: 0 20rpx;
  border-radius: 999rpx;
  background: #FFFFFF;
  color: #5B6472;
  font-size: 24rpx;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1rpx solid #E5EAF1;
  box-sizing: border-box;
  gap: 8rpx;
}

.conversation-action--primary {
  background: #2F6FE4;
  color: #FFFFFF;
  border-color: #2F6FE4;
}

.conversation-action__icon {
  font-size: 26rpx;
  line-height: 1;
}

.message-list {
  flex: 1;
  height: 0;
  min-height: 0;
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
  margin-bottom: 24rpx;
}

.message-row--user {
  justify-content: flex-end;
}

.message-row--assistant {
  justify-content: flex-start;
}

.message-row--action {
  justify-content: flex-end;
  margin-top: -8rpx;
  margin-bottom: 18rpx;
}

.message-bubble {
  padding: 20rpx 24rpx;
  border-radius: 24rpx;
  box-sizing: border-box;
}

.message-bubble--user {
  max-width: 82%;
  background: linear-gradient(135deg, #3D7DF5, #69A6FF);
  color: #FFFFFF;
}

.message-bubble--smart-writing-source {
  width: 100%;
  max-width: 100%;
  background: #FFFFFF;
  color: #1F2933;
  border: 1rpx solid rgba(100, 116, 139, 0.1);
  box-shadow: 0 8rpx 22rpx rgba(72, 103, 163, 0.06);
}

.message-bubble--assistant {
  width: 100%;
  max-width: 100%;
  padding: 24rpx 26rpx;
  background: #FFFFFF;
  color: #1F2933;
  border: 1rpx solid rgba(100, 116, 139, 0.1);
  box-shadow: 0 8rpx 22rpx rgba(72, 103, 163, 0.06);
}

.message-bubble--action {
  max-width: 84%;
  padding: 12rpx 18rpx;
  border-radius: 16rpx;
  background: #EEF4FF;
  color: #48627F;
  border: 1rpx solid rgba(47, 111, 228, 0.12);
}

.action-message {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.action-message__icon {
  color: #2F6FE4;
  font-size: 24rpx;
  font-weight: 800;
}

.action-message__text {
  font-size: 24rpx;
  line-height: 1.5;
}

.message-text {
  font-size: 30rpx;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.message-content {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.assistant-identity {
  display: flex;
  align-items: center;
  gap: 10rpx;
  min-height: 40rpx;
}

.assistant-identity__avatar {
  width: 40rpx;
  height: 40rpx;
  border-radius: 12rpx;
  background: #E8F1FF;
  color: #2F6FE4;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18rpx;
  font-weight: 800;
}

.assistant-identity__name {
  font-size: 24rpx;
  font-weight: 800;
  color: #243044;
}

.assistant-identity__state {
  padding: 3rpx 9rpx;
  border-radius: 999rpx;
  background: #F1F5F9;
  color: #64748B;
  font-size: 19rpx;
}

.assistant-identity__state--warning {
  background: #FFF3E8;
  color: #A45B00;
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

.resource-list {
  display: flex;
  flex-direction: column;
  gap: 14rpx;
}

.resource-card {
  width: 100%;
  padding: 18rpx;
  border-radius: 18rpx;
  background: #F7F9FD;
  border: 1rpx solid rgba(100, 116, 139, 0.14);
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 14rpx;
}

.resource-card--business_card {
  background: linear-gradient(145deg, #F5F9FF, #FFFFFF);
  border-color: rgba(47, 111, 228, 0.18);
}

.resource-card--unavailable {
  opacity: 0.72;
}

.resource-card__header {
  display: flex;
  align-items: center;
  gap: 14rpx;
}

.resource-card__icon {
  width: 64rpx;
  height: 64rpx;
  border-radius: 15rpx;
  background: #E8F1FF;
  color: #2F6FE4;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20rpx;
  font-weight: 800;
  flex-shrink: 0;
}

.resource-card__icon--business_card {
  background: #E8F7EF;
  color: #16865B;
}

.resource-card__icon--video,
.resource-card__icon--audio {
  background: #F0E8FF;
  color: #6D3FD1;
}

.resource-card__heading {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.resource-card__title {
  font-size: 26rpx;
  font-weight: 800;
  color: #1F2937;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: normal;
  word-break: break-all;
}

.resource-card__tags,
.resource-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8rpx;
}

.resource-card__kind,
.resource-card__grounding {
  padding: 4rpx 10rpx;
  border-radius: 999rpx;
  background: #EAF2FF;
  color: #2F6FE4;
  font-size: 19rpx;
  font-weight: 700;
}

.resource-card__grounding--context_only {
  background: #FFF3DF;
  color: #A45B00;
}

.resource-card__grounding--model_only {
  background: #F1F5F9;
  color: #64748B;
}

.resource-card__summary,
.resource-card__content {
  font-size: 22rpx;
  line-height: 1.55;
  color: #607086;
  white-space: pre-wrap;
  word-break: break-all;
}

.resource-card__content {
  max-height: 240rpx;
  overflow: hidden;
  color: #344256;
}

.resource-card__business {
  display: flex;
  flex-wrap: wrap;
  gap: 10rpx;
}

.resource-card__business-field {
  width: calc(50% - 5rpx);
  display: flex;
  flex-direction: column;
  gap: 3rpx;
  padding: 10rpx 12rpx;
  border-radius: 12rpx;
  background: rgba(255, 255, 255, 0.86);
  box-sizing: border-box;
}

.resource-card__business-label {
  font-size: 19rpx;
  color: #8A96A8;
}

.resource-card__business-value {
  font-size: 22rpx;
  font-weight: 700;
  color: #263244;
  word-break: break-all;
}

.resource-card__image,
.resource-card__video {
  width: 100%;
  height: 360rpx;
  border-radius: 16rpx;
  background: #E8EDF4;
  overflow: hidden;
}

.resource-card__video {
  background: #111827;
}

.resource-card__audio {
  min-height: 82rpx;
  padding: 14rpx 18rpx;
  border-radius: 16rpx;
  background: #F0E8FF;
  color: #6D3FD1;
  display: flex;
  align-items: center;
  gap: 14rpx;
}

.resource-card__audio-icon {
  width: 44rpx;
  height: 44rpx;
  border-radius: 50%;
  background: #6D3FD1;
  color: #FFFFFF;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 19rpx;
  font-weight: 800;
}

.resource-card__audio-label {
  font-size: 23rpx;
  font-weight: 800;
}

.resource-card__unavailable {
  padding: 10rpx 12rpx;
  border-radius: 10rpx;
  background: #FFF3E8;
  color: #A45B00;
  font-size: 21rpx;
}

.resource-card__action {
  min-height: 50rpx;
  padding: 0 18rpx;
  border-radius: 999rpx;
  background: #E8F1FF;
  color: #2F6FE4;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22rpx;
  font-weight: 800;
}

.resource-card__action--loading {
  opacity: 0.55;
}

.resource-card__action--disabled {
  opacity: 0.58;
  pointer-events: none;
}

.follow-up-panel {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  padding-top: 2rpx;
  border-top: 1rpx solid #EEF2F7;
}

.follow-up-prompt {
  margin-top: 10rpx;
  font-size: 23rpx;
  line-height: 1.55;
  color: #64748B;
}

.follow-up-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}

.follow-up-action {
  min-height: 60rpx;
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

.resource-file {
  display: flex;
  align-items: center;
  gap: 22rpx;
  min-height: 128rpx;
}

.resource-file--clickable {
  cursor: pointer;
}

.resource-file__icon {
  position: relative;
  width: 88rpx;
  height: 112rpx;
  flex-shrink: 0;
  overflow: hidden;
  border-radius: 8rpx 18rpx 8rpx 8rpx;
  background: #64748B;
  box-shadow: inset 0 0 0 1rpx rgba(15, 23, 42, 0.08);
}

.resource-file__fold {
  position: absolute;
  top: 0;
  right: 0;
  width: 26rpx;
  height: 26rpx;
  background: rgba(255, 255, 255, 0.72);
  clip-path: polygon(0 0, 100% 100%, 0 100%);
}

.resource-file__extension {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 18rpx;
  color: #FFFFFF;
  text-align: center;
  font-size: 21rpx;
  font-weight: 900;
  letter-spacing: 1rpx;
}

.resource-file__icon--word { background: #2878D0; }
.resource-file__icon--excel { background: #16865B; }
.resource-file__icon--pdf { background: #E44848; }
.resource-file__icon--archive { background: #7A5CC7; }
.resource-file__icon--powerpoint { background: #D66B2C; }
.resource-file__icon--text { background: #64748B; }

.resource-file__body {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.resource-file__name {
  color: #172033;
  font-size: 28rpx;
  font-weight: 700;
  line-height: 1.35;
  word-break: break-all;
}

.resource-file__meta {
  color: #8490A3;
  font-size: 22rpx;
}

.message-action-bar {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding-top: 2rpx;
}

.message-action {
  min-height: 56rpx;
  padding: 0 16rpx;
  border-radius: 14rpx;
  color: #667085;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22rpx;
  font-weight: 700;
}

.message-action:active {
  background: #F1F5F9;
  color: #2F6FE4;
}

.smart-writing-action-bar {
  display: flex;
  align-items: center;
  justify-content: space-around;
  gap: 18rpx;
  margin-top: 24rpx;
  padding-top: 20rpx;
  border-top: 1rpx solid #EEF2F7;
}

.smart-writing-action {
  min-width: 116rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
  color: #5B6472;
  font-size: 23rpx;
  line-height: 1.2;
}

.smart-writing-action__icon {
  width: 52rpx;
  height: 52rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 14rpx;
  background: #F1F5F9;
  color: #365F7D;
  font-size: 22rpx;
  font-weight: 800;
}

.smart-writing-action--save {
  color: #23A36A;
}

.smart-writing-action--save .smart-writing-action__icon {
  background: #EAF8F1;
  color: #20A464;
}

.smart-writing-action:active .smart-writing-action__icon {
  background: #E8F0FF;
  color: #2F6FE4;
}

.export-panel-mask {
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  z-index: 22;
  background: rgba(17, 24, 39, 0.18);
}

.export-panel {
  position: absolute;
  left: 28rpx;
  right: 28rpx;
  bottom: calc(118rpx + env(safe-area-inset-bottom));
  z-index: 23;
  display: flex;
  align-items: center;
  justify-content: space-around;
  gap: 16rpx;
  padding: 22rpx 24rpx;
  border-radius: 28rpx;
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 18rpx 48rpx rgba(20, 25, 40, 0.16);
  box-sizing: border-box;
}

.export-option {
  flex: 1;
  min-width: 0;
  min-height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10rpx;
  border-radius: 18rpx;
  color: #4B5563;
  font-size: 24rpx;
  font-weight: 700;
}

.export-option__icon {
  width: 44rpx;
  height: 44rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 13rpx;
  background: #EEF4FF;
  color: #2F6FE4;
  font-size: 19rpx;
  font-weight: 800;
}

.export-option:active {
  background: #F3F6FB;
}

.evidence-panel {
  padding: 12rpx 0 0;
  border-radius: 0;
  background: transparent;
  border: 0;
  border-top: 1rpx solid #EEF2F7;
}

.evidence-panel--available {
  background: transparent;
  border-color: #EEF2F7;
}

.evidence-panel--malformed,
.evidence-panel--integrity_failed,
.evidence-panel--generation_failed {
  background: transparent;
  border-color: rgba(229, 72, 77, 0.16);
}

.evidence-panel__header,
.evidence-source__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14rpx;
}

.evidence-panel__heading-row {
  min-width: 0;
  flex: 1;
  display: flex;
  align-items: center;
  gap: 10rpx;
}

.evidence-panel__icon {
  width: 30rpx;
  height: 30rpx;
  border-radius: 50%;
  background: #EEF4FF;
  color: #2F6FE4;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18rpx;
  font-weight: 800;
}

.evidence-panel__title {
  font-size: 23rpx;
  font-weight: 800;
  color: #263244;
}

.evidence-panel__summary {
  font-size: 20rpx;
  line-height: 1.4;
  color: #68768A;
}

.evidence-panel__meta {
  display: block;
  margin-bottom: 4rpx;
  font-size: 19rpx;
  line-height: 1.4;
  color: #8A96A8;
  word-break: break-all;
}

.evidence-panel__toggle {
  flex-shrink: 0;
  font-size: 21rpx;
  font-weight: 800;
  color: #2F6FE4;
}

.evidence-panel__sources {
  margin-top: 12rpx;
  display: flex;
  flex-direction: column;
  gap: 10rpx;
}

.evidence-source {
  padding: 12rpx;
  border-radius: 12rpx;
  background: rgba(255, 255, 255, 0.88);
  display: flex;
  flex-direction: column;
  gap: 7rpx;
}

.evidence-source__title {
  min-width: 0;
  flex: 1;
  font-size: 22rpx;
  font-weight: 800;
  color: #283548;
  word-break: break-all;
}

.evidence-source__type {
  flex-shrink: 0;
  font-size: 19rpx;
  color: #2F6FE4;
}

.evidence-source__excerpt,
.evidence-source__time {
  font-size: 20rpx;
  line-height: 1.5;
  color: #607086;
  word-break: break-all;
}

.evidence-source__time {
  color: #8A96A8;
}

.call-detail-panel {
  margin-top: 10rpx;
  padding-top: 10rpx;
  border-top: 1rpx solid #EEF2F7;
}

.call-detail-header {
  min-height: 50rpx;
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
  flex-direction: row;
  align-items: center;
}

.call-detail-thinking-title {
  display: flex;
  align-items: center;
  gap: 12rpx;
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
  margin-top: 16rpx;
  padding: 4rpx 0 2rpx;
  display: flex;
  flex-direction: column;
  gap: 18rpx;
}

.simple-thinking-steps {
  display: flex;
  flex-direction: column;
  gap: 18rpx;
}

.simple-thinking-step {
  display: flex;
  align-items: center;
  gap: 18rpx;
  color: #9AA5B5;
  font-size: 27rpx;
  line-height: 1.35;
}

.simple-thinking-step--completed {
  color: #243044;
  font-weight: 750;
}

.simple-thinking-step__mark {
  width: 22rpx;
  min-width: 22rpx;
  color: #6C4DF6;
  font-size: 26rpx;
  line-height: 1;
  text-align: center;
}

.simple-thinking-step:not(.simple-thinking-step--completed) .simple-thinking-step__mark {
  width: 18rpx;
  height: 18rpx;
  border: 2rpx solid #B9A8FF;
  border-radius: 50%;
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

.thinking-dots--inline {
  gap: 7rpx;
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

.pending-resources {
  flex-shrink: 0;
  width: 100%;
  padding: 12rpx 24rpx 0;
  box-sizing: border-box;
  white-space: nowrap;
  background: #FFFFFF;
}

.pending-resource {
  display: inline-flex;
  width: 360rpx;
  align-items: center;
  gap: 12rpx;
  margin-right: 12rpx;
  padding: 16rpx;
  border: 1rpx solid #E5E7EB;
  border-radius: 16rpx;
  background: #F8FAFC;
  box-sizing: border-box;
}

.pending-resource__body {
  min-width: 0;
  flex: 1;
}

.pending-resource__name,
.pending-resource__status {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pending-resource__name { color: #1F2937; font-size: 24rpx; }
.pending-resource__status { margin-top: 4rpx; color: #718096; font-size: 20rpx; }
.pending-resource__action { color: #365F7D; font-size: 22rpx; }
.pending-resource__remove { color: #718096; font-size: 34rpx; }

.input-attachment-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8rpx;
  margin-top: 12rpx;
}

.input-attachment {
  max-width: 100%;
  padding: 8rpx 12rpx;
  border: 1rpx solid rgba(54, 95, 125, 0.18);
  border-radius: 10rpx;
  background: rgba(54, 95, 125, 0.06);
}

.input-attachment__name {
  display: block;
  overflow: hidden;
  color: #365F7D;
  font-size: 21rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.composer {
  position: relative;
  flex-shrink: 0;
  width: 100%;
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
  font-size: 30rpx;
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

.send-btn--stop {
  background: #E5484D;
}
</style>
