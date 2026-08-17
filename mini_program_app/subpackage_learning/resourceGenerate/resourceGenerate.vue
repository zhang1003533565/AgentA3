<template>
  <view class="learning-page">
    <nav-bar class="resource-nav" :class="{ 'resource-nav--presentation': isPresentationMode }" title="个性化资源包" :showBack="true" fixed placeholder>
      <template #right>
        <view v-if="isPresentationMode" class="presentation-history-action" @tap.stop="openPresentationHistory">
          <image class="presentation-history-action__icon" src="/static/icons/history-lucide.svg" mode="aspectFit" />
          <text>生成历史</text>
        </view>
      </template>
    </nav-bar>

    <ai-presentation-flow v-if="isPresentationMode" ref="presentationFlow" :initial-topic="topic" />

    <template v-else>
    <view class="request-card">
      <text class="request-card__title">这次想攻克什么？</text>
      <textarea v-model="topic" class="request-card__input" placeholder="例如：理解 Python 列表切片，并能完成常见编程题" :maxlength="500" auto-height />
      <scroll-view class="resource-types" scroll-x :show-scrollbar="false">
        <view class="resource-types__inner">
          <view v-for="option in resourceTypeOptions" :key="option.value" class="resource-type" :class="{ 'resource-type--active': selectedResourceTypes.includes(option.value) }" @tap="toggleResourceType(option.value)">{{ option.label }}</view>
        </view>
      </scroll-view>
      <button class="generate-button" :disabled="generating || !topic.trim() || !selectedResourceTypes.length" @tap="startGeneration">
        {{ generating ? '多智能体协同生成中…' : '生成个性化资源包' }}
      </button>
    </view>

    <view v-if="generating || learningState.progress > 0" class="progress-card">
      <view class="progress-card__head"><text>{{ learningState.message || stageLabel }}</text><text>{{ learningState.progress }}%</text></view>
      <view class="progress-card__track"><view class="progress-card__value" :style="{ width: `${learningState.progress}%` }"></view></view>
      <text v-if="learningState.agentName" class="progress-card__agent">当前智能体：{{ learningState.agentName }}</text>
      <text v-if="learningState.workflowId" class="progress-card__workflow">工作流 {{ learningState.workflowId }}</text>
    </view>

    <view v-if="traceItems.length" class="trace-card">
      <view class="trace-card__head" @tap="traceExpanded = !traceExpanded">
        <view><text class="trace-card__title">智能体协作链路</text><text class="trace-card__count">{{ traceItems.length }} 个事件</text></view>
        <text class="trace-card__toggle">{{ traceExpanded ? '收起' : '展开' }}</text>
      </view>
      <view v-if="traceExpanded" class="trace-list">
        <view v-for="item in traceItems" :key="`${item.sequence}-${item.eventName}-${item.agentName}`" class="trace-item">
          <view class="trace-item__rail"><view :class="`trace-item__dot--${item.status || 'running'}`"></view></view>
          <view class="trace-item__copy">
            <view><text class="trace-item__agent">{{ item.agentName || stageName(item.eventName) }}</text><text class="trace-item__status">{{ traceStatus(item.status) }}</text></view>
            <text class="trace-item__message">{{ item.message || stageName(item.eventName) }}</text>
            <text v-if="item.resourceType" class="trace-item__resource">{{ resourceLabel(item.resourceType) }}</text>
          </view>
        </view>
      </view>
    </view>

    <view v-if="pageState !== 'ready' && !resourceEntries.length" class="state-card">
      <text class="state-card__title">{{ currentStateCopy.title }}</text>
      <text class="state-card__desc">{{ currentStateCopy.description }}</text>
      <button v-if="pageState !== 'loading' && pageState !== 'empty'" class="state-card__button" @tap="retryLoad">{{ currentStateCopy.action }}</button>
    </view>

    <view v-if="resourceEntries.length" class="resource-section">
      <view class="resource-section__head"><text class="resource-section__title">本次资源</text><text class="resource-section__count">{{ resourceEntries.length }} 类</text></view>
      <learning-resource-viewer
        v-for="entry in resourceEntries"
        :key="entry.type"
        :resource="entry.resource"
        :resource-type="entry.type"
        :workflow-id="learningState.workflowId"
        :failed="entry.failed"
        :error="entry.error"
        @retry="retryResource"
        @action="handleResourceAction"
      />
    </view>

    <view v-if="pageState === 'generation_failed' && resourceEntries.length" class="failure-banner">
      <text>{{ currentStateCopy.description }}</text>
      <button @tap="retryGeneration">重试失败资源</button>
    </view>
    </template>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import AiPresentationFlow from './AIPresentationFlow.vue'
import LearningResourceViewer from '@/components/learning-resource-viewer/learning-resource-viewer.vue'
import { downloadAssistantResource } from '@/api/ai.js'
import { getLearningWorkflow, retryLearningResource, streamLearningResources } from '@/api/learning.js'
import { createLearningState, reduceLearningEvent, restoreLearningState } from '@/subpackage_learning/learningState.js'
import { classifyLearningError, learningErrorMessage, responseData, stateCopy } from '@/subpackage_learning/learningView.js'

const WORKFLOW_STORAGE_KEY = 'pythonLearningWorkflowId'
const CORE_RESOURCE_TYPES = ['knowledge_note', 'mind_map', 'practice_set', 'code_lab', 'presentation', 'extended_reading']

export default {
  components: { NavBar, AiPresentationFlow, LearningResourceViewer },
  data() {
    return {
      topic: '',
      isPresentationMode: false,
      selectedResourceTypes: [...CORE_RESOURCE_TYPES],
      pageState: 'empty',
      errorMessage: '',
      learningState: createLearningState(),
      generating: false,
      traceExpanded: false,
      resourceTypeOptions: [
        { value: 'knowledge_note', label: '讲义' }, { value: 'mind_map', label: '思维导图' },
        { value: 'practice_set', label: '练习题' }, { value: 'code_lab', label: '代码实验' },
        { value: 'presentation', label: 'PPT' }, { value: 'extended_reading', label: '拓展阅读' }
      ]
    }
  },
  computed: {
    currentStateCopy() { return stateCopy(this.pageState, this.errorMessage) },
    stageLabel() {
      const labels = { planning: '规划资源包', retrieval: '检索课程知识库', review_start: '审核课程事实', review_result: '完成事实审核', exporting: '导出真实文件', pathing: '同步学习路径', persisting: '保存工作流' }
      return labels[this.learningState.stage] || '准备生成资源'
    },
    resourceEntries() {
      const resources = this.learningState.resources || {}
      const errors = this.learningState.errors || {}
      return [...new Set([...Object.keys(resources), ...Object.keys(errors).filter(key => key !== 'workflow')])].map(type => ({
        type, resource: resources[type] || {}, error: errors[type] || null, failed: Boolean(errors[type])
      }))
    },
    traceItems() {
      return Array.isArray(this.learningState.trace) ? this.learningState.trace : []
    }
  },
  onLoad(options = {}) {
    this.topic = this.decodeOption(options.topic || options.prompt || '')
    const requestedType = this.decodeOption(options.resourceType || '')
    this.isPresentationMode = requestedType === 'presentation'
    if (CORE_RESOURCE_TYPES.includes(requestedType)) this.selectedResourceTypes = [requestedType]
    if (this.isPresentationMode) return
    const workflowId = this.decodeOption(options.workflowId || '') || uni.getStorageSync(WORKFLOW_STORAGE_KEY) || ''
    if (workflowId) {
      this.learningState = createLearningState(workflowId)
      this.resumeWorkflow(workflowId)
    }
  },
  methods: {
    decodeOption(value) {
      let decoded = String(value || '')
      for (let index = 0; index < 2; index += 1) {
        try {
          const next = decodeURIComponent(decoded)
          if (next === decoded) break
          decoded = next
        } catch (error) {
          break
        }
      }
      return decoded
    },
    stageName(value) {
      const labels = { accepted: '工作流受理', planning_start: '路径规划开始', planning_done: '路径规划完成', agent_start: '资源生成开始', agent_done: '资源生成完成', review_start: '内容审核开始', review_done: '内容审核完成', packaging_start: '资源组装开始', packaging_done: '资源组装完成', done: '工作流完成', completed: '工作流完成' }
      return labels[value] || value || '工作流事件'
    },
    traceStatus(value) {
      return value === 'failed' ? '失败' : value === 'completed' ? '完成' : '运行中'
    },
    resourceLabel(value) {
      return this.resourceTypeOptions.find(item => item.value === value)?.label || value
    },
    openPresentationHistory() {
      const flow = this.$refs.presentationFlow
      if (flow && typeof flow.openHistory === 'function') flow.openHistory('generation')
    },
    toggleResourceType(type) {
      if (this.generating) return
      if (this.selectedResourceTypes.includes(type)) {
        if (this.selectedResourceTypes.length > 1) this.selectedResourceTypes = this.selectedResourceTypes.filter(item => item !== type)
      } else this.selectedResourceTypes = [...this.selectedResourceTypes, type]
    },
    rememberWorkflow(workflowId) {
      const value = String(workflowId || '').trim()
      if (!value) return
      uni.setStorageSync(WORKFLOW_STORAGE_KEY, value)
    },
    applyEvent(eventName, payload = {}) {
      this.learningState = reduceLearningEvent(this.learningState, eventName, payload)
      this.rememberWorkflow(this.learningState.workflowId)
      if (this.learningState.status === 'ready') this.pageState = this.resourceEntries.length ? 'ready' : 'empty'
      else if (this.learningState.status === 'generation_failed') this.pageState = 'generation_failed'
      else if (this.learningState.status === 'dependency_unavailable') this.pageState = 'dependency_unavailable'
      else this.pageState = 'loading'
    },
    async startGeneration() {
      if (this.generating || !this.topic.trim() || !this.selectedResourceTypes.length) return
      this.generating = true
      this.pageState = 'loading'
      this.errorMessage = ''
      this.learningState = createLearningState()
      try {
        await streamLearningResources({
          courseKey: 'python',
          topic: this.topic.trim(),
          requestedResourceTypes: [...this.selectedResourceTypes]
        }, {
          onSession: payload => this.applyEvent('session', payload),
          onEvent: (eventName, payload) => this.applyEvent(eventName, payload),
          onDone: payload => this.applyEvent('done', payload),
          onError: payload => this.applyEvent(payload?.event || 'generation_failed', payload)
        })
        if (!['ready', 'generation_failed', 'dependency_unavailable'].includes(this.pageState)) {
          this.pageState = 'network_error'
          this.errorMessage = '生成连接已结束，但服务端未返回完成状态，可使用工作流编号恢复。'
        }
      } catch (error) {
        this.pageState = classifyLearningError(error, 'generation_failed')
        this.errorMessage = learningErrorMessage(error)
      } finally {
        this.generating = false
      }
    },
    async resumeWorkflow(workflowId = this.learningState.workflowId) {
      if (!workflowId) return
      this.pageState = 'loading'
      this.errorMessage = ''
      try {
        const snapshot = responseData(await getLearningWorkflow(workflowId)) || {}
        this.learningState = restoreLearningState(this.learningState, snapshot)
        if (!this.topic.trim()) this.topic = String(snapshot.topic || snapshot.request?.topic || snapshot.requestInput || '')
        this.rememberWorkflow(this.learningState.workflowId || workflowId)
        const status = String(this.learningState.status || '').toLowerCase()
        if (status === 'generation_failed' || Object.keys(this.learningState.errors || {}).length) this.pageState = 'generation_failed'
        else if (status === 'dependency_unavailable') this.pageState = 'dependency_unavailable'
        else if (['ready', 'completed', 'done'].includes(status) || this.resourceEntries.length) this.pageState = 'ready'
        else this.pageState = 'loading'
      } catch (error) {
        this.pageState = classifyLearningError(error)
        this.errorMessage = learningErrorMessage(error)
      }
    },
    retryLoad() {
      if (this.pageState === 'generation_failed') return this.retryGeneration()
      if (this.learningState.workflowId) return this.resumeWorkflow()
      return this.startGeneration()
    },
    async retryResource(resourceType) {
      if (!this.learningState.workflowId || !resourceType) return
      this.generating = true
      this.applyEvent('retrying', { workflowId: this.learningState.workflowId, resourceType, progress: this.learningState.progress })
      try {
        const payload = responseData(await retryLearningResource(this.learningState.workflowId, resourceType)) || {}
        if (payload.resources || payload.status || payload.errors) {
          this.learningState = restoreLearningState(this.learningState, payload)
        } else {
          this.applyEvent('agent_done', { workflowId: this.learningState.workflowId, resourceType, resource: payload.resource || payload, progress: payload.progress })
        }
        await this.resumeWorkflow(this.learningState.workflowId)
      } catch (error) {
        this.applyEvent('agent_failed', { workflowId: this.learningState.workflowId, resourceType, message: learningErrorMessage(error, '重试失败'), retryable: true })
      } finally {
        this.generating = false
      }
    },
    async retryGeneration() {
      const failedTypes = Object.keys(this.learningState.errors || {}).filter(type => type !== 'workflow')
      if (!failedTypes.length && !this.topic.trim()) {
        uni.showToast({ title: '请先填写本次学习主题', icon: 'none' })
        return
      }
      if (!failedTypes.length) return this.startGeneration()
      for (const type of failedTypes) await this.retryResource(type)
    },
    async handleResourceAction({ resource, action }) {
      const type = String(action?.type || '').toLowerCase()
      if (type === 'start_learning') {
        uni.navigateTo({ url: '/subpackage_learning/learningPath/learningPath' })
        return
      }
      const target = type === 'preview'
        ? action?.previewUrl || action?.url || resource?.previewUrl || resource?.url
        : action?.url || action?.href || resource?.url || resource?.downloadUrl || resource?.payload?.url
      if (!target) {
        uni.showToast({ title: '该资源暂未提供可打开文件', icon: 'none' })
        return
      }
      try {
        uni.showLoading({ title: '正在读取资源' })
        const path = await downloadAssistantResource({
          ...resource,
          url: target,
          authScope: action?.authScope || resource.authScope || resource.metadata?.authScope || 'session_owner'
        })
        if (type === 'preview' && /\.(png|jpe?g|gif|webp)$/i.test(path)) uni.previewImage({ urls: [path], current: path })
        else uni.openDocument({ filePath: path, showMenu: true, fail: () => uni.showToast({ title: '文件已下载，请在下载列表打开', icon: 'none' }) })
      } catch (error) {
        uni.showToast({ title: learningErrorMessage(error, '资源打开失败'), icon: 'none' })
      } finally {
        uni.hideLoading()
      }
    }
  }
}
</script>

<style scoped>
.learning-page{min-height:100vh;padding:24rpx 24rpx 80rpx;background:#f5f7fb;box-sizing:border-box;color:#172033}.request-card,.progress-card,.state-card{padding:28rpx;border-radius:24rpx;background:#fff}.request-card__title{display:block;font-size:34rpx;font-weight:780}.request-card__input{width:100%;min-height:150rpx;margin-top:20rpx;padding:22rpx;border-radius:18rpx;background:#f5f7fb;box-sizing:border-box;font-size:27rpx;line-height:1.6}.resource-types{margin-top:18rpx;white-space:nowrap}.resource-types__inner{display:inline-flex;gap:12rpx;padding:4rpx 2rpx}.resource-type{padding:12rpx 20rpx;border:1px solid #dbe2ea;border-radius:999rpx;color:#64748b;font-size:23rpx}.resource-type--active{border-color:#6366f1;background:#eef2ff;color:#4338ca}.generate-button{margin-top:24rpx;border:0;border-radius:17rpx;background:#4f46e5;color:#fff;font-size:27rpx;font-weight:700}.generate-button[disabled]{opacity:.48}.progress-card{margin-top:20rpx}.progress-card__head{display:flex;justify-content:space-between;color:#334155;font-size:25rpx;font-weight:650}.progress-card__track{height:12rpx;margin-top:16rpx;border-radius:999rpx;background:#e7eaf0;overflow:hidden}.progress-card__value{height:100%;border-radius:inherit;background:linear-gradient(90deg,#4f46e5,#22c55e);transition:width .25s}.progress-card__agent,.progress-card__workflow{display:block;margin-top:12rpx;color:#64748b;font-size:21rpx}.progress-card__workflow{color:#94a3b8}.state-card{margin-top:20rpx;padding:64rpx 32rpx;text-align:center}.state-card__title,.state-card__desc{display:block}.state-card__title{font-size:31rpx;font-weight:750}.state-card__desc{margin-top:13rpx;color:#64748b;font-size:24rpx;line-height:1.6}.state-card__button{margin-top:24rpx;border:0;border-radius:16rpx;background:#4f46e5;color:#fff}.resource-section{margin-top:26rpx}.resource-section__head{display:flex;justify-content:space-between;margin-bottom:18rpx}.resource-section__title{font-size:32rpx;font-weight:780}.resource-section__count{color:#64748b;font-size:23rpx}.failure-banner{position:sticky;bottom:20rpx;display:flex;align-items:center;justify-content:space-between;gap:18rpx;padding:20rpx 24rpx;border-radius:18rpx;background:#7f1d1d;color:#fff;font-size:23rpx}.failure-banner text{flex:1}.failure-banner button{margin:0;border:0;background:#fff;color:#7f1d1d;font-size:22rpx}
.resource-nav--presentation :deep(.nav-inner){grid-template-columns:112rpx 1fr 174rpx!important}.resource-nav--presentation :deep(.nav-side--right){overflow:visible}.presentation-history-action{display:flex;height:64rpx;align-items:center;justify-content:center;gap:8rpx;padding:0 18rpx;border:1px solid #d8e0ec;border-radius:999rpx;background:#fff;color:#5265f5;box-sizing:border-box;font-size:22rpx;font-weight:760;line-height:1;box-shadow:0 8rpx 18rpx rgba(43,60,120,.05)}.presentation-history-action:active{background:#f6f7ff;transform:scale(.97)}.presentation-history-action__icon{width:28rpx;height:28rpx;flex:none}
.trace-card{margin-top:20rpx;padding:24rpx;border-radius:22rpx;background:#fff}.trace-card__head,.trace-card__head>view,.trace-item__copy>view{display:flex;align-items:center}.trace-card__head{justify-content:space-between}.trace-card__head>view{gap:12rpx}.trace-card__title{font-size:27rpx;font-weight:720}.trace-card__count,.trace-card__toggle{color:#708096;font-size:20rpx}.trace-list{margin-top:22rpx}.trace-item{display:flex;gap:16rpx;min-height:92rpx}.trace-item__rail{position:relative;width:20rpx}.trace-item__rail:after{content:'';position:absolute;left:8rpx;top:20rpx;bottom:-4rpx;width:2rpx;background:#dbe2e9}.trace-item:last-child .trace-item__rail:after{display:none}.trace-item__rail>view{position:relative;z-index:1;width:16rpx;height:16rpx;border-radius:50%;background:#7890a8}.trace-item__rail .trace-item__dot--completed{background:#4d8a72}.trace-item__rail .trace-item__dot--failed{background:#b65f58}.trace-item__copy{flex:1;padding-bottom:18rpx}.trace-item__copy>view{justify-content:space-between}.trace-item__agent{font-size:23rpx;font-weight:680}.trace-item__status{color:#708096;font-size:18rpx}.trace-item__message{display:block;margin-top:6rpx;color:#64748b;font-size:21rpx}.trace-item__resource{display:inline-block;margin-top:7rpx;padding:4rpx 10rpx;border-radius:999rpx;background:#edf2f6;color:#536b82;font-size:17rpx}
</style>
