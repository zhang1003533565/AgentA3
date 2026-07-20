<template>
  <view class="resource-viewer" :class="{ 'resource-viewer--failed': failed }">
    <view class="resource-viewer__head">
      <view class="resource-viewer__heading">
        <text class="resource-viewer__eyebrow">{{ kindLabel }}</text>
        <text class="resource-viewer__title">{{ title }}</text>
      </view>
      <text class="resource-viewer__review" :class="`resource-viewer__review--${reviewStatus}`">{{ reviewLabel }}</text>
    </view>

    <text v-if="summary" class="resource-viewer__summary">{{ summary }}</text>
    <safe-markdown v-if="content" :content="content" class="resource-viewer__content" />

    <view v-if="evidenceSources.length" class="resource-viewer__evidence">
      <text class="resource-viewer__section-title">来源与依据</text>
      <view v-for="source in evidenceSources" :key="source.evidenceId || source.id || source.title" class="resource-viewer__source">
        <text class="resource-viewer__source-title">{{ source.title || source.evidenceId || source.id || '课程知识库来源' }}</text>
        <text v-if="source.excerpt" class="resource-viewer__source-excerpt">{{ source.excerpt }}</text>
      </view>
    </view>
    <view v-else-if="evidenceIds.length" class="resource-viewer__evidence-ids">
      <text>证据编号：{{ evidenceIds.join('、') }}</text>
    </view>

    <text v-if="failed" class="resource-viewer__error">{{ error?.message || '该资源生成失败，可单独重试。' }}</text>
    <view class="resource-viewer__actions">
      <button v-for="action in actions" :key="action.type" class="resource-viewer__action resource-viewer__action--secondary" @tap="emitAction(action)">
        {{ action.label }}
      </button>
      <button v-if="failed || retryable" class="resource-viewer__action" @tap="retry">重新生成本项</button>
    </view>
  </view>
</template>

<script>
import SafeMarkdown from '@/components/safe-markdown/safe-markdown.vue'
import { learningResourceReviewStatus } from '@/subpackage_learning/learningView.js'

const KIND_LABELS = {
  knowledge_note: '个性化讲义', mind_map: '知识思维导图', practice_set: '分层练习题',
  code_lab: 'Python 代码实验', presentation: 'PPT 课件', extended_reading: '拓展阅读'
}

export default {
  name: 'LearningResourceViewer',
  components: { SafeMarkdown },
  props: {
    resource: { type: Object, default: () => ({}) },
    resourceType: { type: String, default: '' },
    workflowId: { type: String, default: '' },
    failed: { type: Boolean, default: false },
    error: { type: Object, default: null }
  },
  emits: ['retry', 'action'],
  computed: {
    kindLabel() {
      const kind = this.resourceType || this.resource.metadata?.resourceKind || this.resource.resourceKind
      return KIND_LABELS[kind] || kind || '学习资源'
    },
    title() { return this.resource.title || this.resource.name || this.resource.payload?.title || this.kindLabel },
    summary() { return this.resource.summary || this.resource.description || this.resource.payload?.summary || '' },
    content() {
      return this.resource.markdown || this.resource.content || this.resource.payload?.markdown || this.resource.payload?.content || ''
    },
    reviewStatus() {
      return learningResourceReviewStatus(this.resource, this.failed)
    },
    reviewLabel() {
      if (['reviewed', 'grounded', 'verified', 'passed'].includes(this.reviewStatus)) return '审核通过'
      if (this.reviewStatus === 'generation_failed') return '生成失败'
      if (this.reviewStatus === 'model_only') return '依据不足'
      return '等待审核'
    },
    evidenceIds() {
      const direct = this.resource.metadata?.evidenceIds || this.resource.evidenceIds || this.resource.payload?.evidenceIds || []
      return Array.isArray(direct) ? direct.map(String).filter(Boolean) : []
    },
    evidenceSources() {
      const sources = this.resource.evidenceChain?.sources || this.resource.evidenceSources || []
      return Array.isArray(sources) ? sources : []
    },
    actions() {
      const value = this.resource.actions || this.resource.payload?.actions || []
      return (Array.isArray(value) ? value : []).filter(action => action && action.type && action.disabled !== true)
    },
    retryable() {
      return this.error?.retryable === true || this.resource.retryable === true
    }
  },
  methods: {
    retry() {
      this.$emit('retry', this.resourceType)
    },
    emitAction(action) {
      this.$emit('action', { resource: this.resource, resourceType: this.resourceType, workflowId: this.workflowId, action })
    }
  }
}
</script>

<style scoped>
.resource-viewer{padding:28rpx;margin-bottom:22rpx;border:1px solid #e5eaf2;border-radius:24rpx;background:#fff;box-shadow:0 12rpx 34rpx rgba(15,23,42,.05)}.resource-viewer--failed{border-color:#fecaca;background:#fffafa}
.resource-viewer__head{display:flex;align-items:flex-start;justify-content:space-between;gap:20rpx}.resource-viewer__eyebrow,.resource-viewer__title{display:block}.resource-viewer__eyebrow{color:#6366f1;font-size:22rpx;font-weight:700}.resource-viewer__title{margin-top:7rpx;color:#172033;font-size:32rpx;font-weight:750}.resource-viewer__review{flex-shrink:0;padding:8rpx 14rpx;border-radius:999rpx;background:#fff7ed;color:#c2410c;font-size:21rpx}.resource-viewer__review--reviewed,.resource-viewer__review--grounded,.resource-viewer__review--verified,.resource-viewer__review--passed{background:#ecfdf5;color:#047857}
.resource-viewer__summary{display:block;margin-top:18rpx;color:#64748b;font-size:25rpx;line-height:1.6}.resource-viewer__content{margin-top:20rpx}.resource-viewer__evidence{margin-top:22rpx;padding:20rpx;border-radius:16rpx;background:#f8fafc}.resource-viewer__section-title{display:block;color:#334155;font-size:24rpx;font-weight:700}.resource-viewer__source{margin-top:12rpx}.resource-viewer__source-title,.resource-viewer__source-excerpt{display:block}.resource-viewer__source-title{color:#475569;font-size:23rpx;font-weight:650}.resource-viewer__source-excerpt,.resource-viewer__evidence-ids{margin-top:6rpx;color:#64748b;font-size:22rpx;line-height:1.55}.resource-viewer__error{display:block;margin-top:18rpx;color:#b91c1c;font-size:24rpx}.resource-viewer__actions{display:flex;flex-wrap:wrap;gap:14rpx;margin-top:22rpx}.resource-viewer__action{margin:0;padding:0 22rpx;border:0;border-radius:14rpx;background:#4f46e5;color:#fff;font-size:24rpx;line-height:68rpx}.resource-viewer__action--secondary{background:#eef2ff;color:#4338ca}
</style>
