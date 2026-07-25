<template>
  <view class="result-page">
    <nav-bar title="答题结果" :showBack="true" fixed placeholder />
    <view v-if="result" class="content">
      <view class="score-card">
        <text class="caption">客观题得分</text><text class="score">{{ result.objectiveScore ?? 0 }}</text><text class="total">/ {{ result.objectiveTotalScore ?? 0 }}</text>
        <text class="meta">第 {{ result.attemptNo }} 次 · {{ statusText(result.status) }}</text>
      </view>
      <view v-if="learningUpdate" class="learning-update-card">
        <view class="learning-update-head">
          <view><text class="learning-update-eyebrow">LEARNING LOOP</text><text class="learning-update-title">本次答题已更新学习方案</text></view>
          <text class="learning-update-evidence">{{ evidenceStatusText(learningUpdate.evidenceStatus) }}</text>
        </view>
        <view v-if="weakKnowledgePoints.length" class="learning-update-section">
          <text class="learning-update-label">需要巩固</text>
          <view class="learning-update-tags"><text v-for="point in weakKnowledgePoints" :key="point">{{ point }}</text></view>
        </view>
        <view v-if="learningUpdate.pathVersionBefore != null || learningUpdate.pathVersionAfter != null" class="learning-update-version">
          <text>路径版本</text><text>{{ learningUpdate.pathVersionBefore ?? '—' }} → {{ learningUpdate.pathVersionAfter ?? '—' }}</text>
        </view>
        <view v-if="changedNodes.length" class="learning-update-section">
          <text class="learning-update-label">路径调整</text>
          <text v-for="(node, index) in changedNodes" :key="node.id || node.itemKey || index" class="learning-update-node">{{ changedNodeText(node) }}</text>
        </view>
        <view v-if="learningUpdate.nextRecommendation" class="learning-update-next">
          <text class="learning-update-label">下一步推荐</text>
          <text class="learning-update-next-title">{{ recommendationText(learningUpdate.nextRecommendation) }}</text>
        </view>
        <button class="learning-update-cta" @tap="openLearningPath">查看调整后的学习路径</button>
      </view>
      <view v-for="(question, index) in result.questions || []" :key="question.id" class="question-card">
        <view class="question-head"><text>{{ index + 1 }}. {{ question.stem }}</text><text class="judge" :class="{ correct: question.correct === true }">{{ judgeText(question) }}</text></view>
        <text class="line">作答：{{ answerText(question.userAnswerJson) }}</text>
        <text class="line answer">答案：{{ answerText(question.answerJson) }}</text>
        <text v-if="question.analysis" class="analysis">解析：{{ question.analysis }}</text>
        <text v-if="question.scoringJson" class="scoring">评分规则：{{ answerText(question.scoringJson) }}</text>
      </view>
    </view>
    <view v-else-if="errorMessage" class="state"><text>加载失败</text><text>{{ errorMessage }}</text><button @click="loadResult">重新加载</button></view>
    <view v-else class="state">加载中...</view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { getExamAttemptResult } from '@/api/exam.js'

export default {
  components: { NavBar },
  data() { return { attemptId: '', result: null, errorMessage: '' } },
  computed: {
    learningUpdate() { return this.result?.learningUpdate || null },
    weakKnowledgePoints() {
      const value = this.learningUpdate?.weakKnowledgePoints
      return (Array.isArray(value) ? value : []).map(item => typeof item === 'string' ? item : item.knowledgePoint || item.name || '').filter(Boolean)
    },
    changedNodes() {
      const value = this.learningUpdate?.changedNodes
      return Array.isArray(value) ? value : []
    }
  },
  onLoad(options) { this.attemptId = options?.attemptId || ''; this.loadResult() },
  methods: {
    async loadResult() {
      if (!this.attemptId) { this.errorMessage = '缺少答题记录编号'; return }
      this.errorMessage = ''
      try { const res = await getExamAttemptResult(this.attemptId); this.result = res?.data || null }
      catch (error) { this.result = null; this.errorMessage = error?.msg || error?.message || '请稍后重试' }
    },
    statusText(status) { return status === 'AUTO_SUBMITTED' ? '超时交卷' : '已交卷' },
    evidenceStatusText(status) {
      return { grounded: '证据已记录', verified: '证据已验证', recorded: '证据已记录', skipped: '未产生新证据' }[String(status || '').toLowerCase()] || status || '学习证据已同步'
    },
    changedNodeText(node) {
      if (typeof node === 'string') return node
      const topic = node?.knowledgePoint || node?.title || node?.itemKey || '学习节点'
      const reason = node?.reason || node?.rationale || node?.changeType || ''
      return reason ? `${topic}：${reason}` : topic
    },
    recommendationText(value) {
      if (typeof value === 'string') return value
      return value?.knowledgePoint || value?.objective || value?.title || value?.rationale || '继续完成下一项推荐任务'
    },
    openLearningPath() { uni.navigateTo({ url: '/subpackage_learning/learningPath/learningPath' }) },
    judgeText(question) { if (question.correct === true) return `正确 +${question.maxScore ?? 0}`; if (question.correct === false) return '错误'; return '待人工评分' },
    answerText(json) {
      if (!json) return '未作答'
      try {
        const value = typeof json === 'string' ? JSON.parse(json) : json
        if (value.selectedOption) return value.selectedOption
        if (Array.isArray(value.selectedOptions)) return value.selectedOptions.join('、') || '未作答'
        if (typeof value.value === 'boolean') return value.value ? '正确' : '错误'
        if (Array.isArray(value.blanks)) return value.blanks.map(item => `${item.id}: ${item.value || (item.answers || []).join('/')}`).join('；')
        if (value.text || value.referenceAnswer) return value.text || value.referenceAnswer
        return JSON.stringify(value)
      } catch (error) { return String(json) }
    }
  }
}
</script>

<style lang="scss" scoped>
.result-page{min-height:100vh;background:#f7f7f9}.content{padding:24rpx}.score-card{padding:42rpx 30rpx;margin-bottom:22rpx;background:linear-gradient(135deg,#3478f6,#6b9cff);border-radius:26rpx;color:#fff;text-align:center}.caption,.meta{display:block;opacity:.9}.score{font-size:84rpx;font-weight:800}.total{font-size:32rpx}.meta{margin-top:14rpx;font-size:24rpx}.learning-update-card{padding:28rpx;margin-bottom:22rpx;border:1rpx solid #dbeafe;border-radius:24rpx;background:linear-gradient(145deg,#fff,#eff6ff)}.learning-update-head{display:flex;justify-content:space-between;align-items:flex-start;gap:18rpx}.learning-update-eyebrow,.learning-update-title{display:block}.learning-update-eyebrow{color:#2563eb;font-size:19rpx;font-weight:750;letter-spacing:2rpx}.learning-update-title{margin-top:8rpx;color:#172033;font-size:30rpx;font-weight:760}.learning-update-evidence{flex-shrink:0;padding:8rpx 13rpx;border-radius:999rpx;background:#dcfce7;color:#166534;font-size:20rpx}.learning-update-section{margin-top:20rpx}.learning-update-label{display:block;color:#475569;font-size:23rpx;font-weight:700}.learning-update-tags{display:flex;flex-wrap:wrap;gap:9rpx;margin-top:11rpx}.learning-update-tags text{padding:8rpx 13rpx;border-radius:999rpx;background:#fee2e2;color:#b91c1c;font-size:21rpx}.learning-update-version{display:flex;justify-content:space-between;margin-top:20rpx;padding:16rpx;border-radius:14rpx;background:#fff;color:#475569;font-size:23rpx}.learning-update-node{display:block;margin-top:9rpx;color:#64748b;font-size:22rpx;line-height:1.55}.learning-update-next{margin-top:20rpx;padding:18rpx;border-radius:15rpx;background:#eef2ff}.learning-update-next-title{display:block;margin-top:8rpx;color:#3730a3;font-size:25rpx;font-weight:680}.learning-update-cta{margin-top:22rpx;border:0;border-radius:15rpx;background:#2563eb;color:#fff;font-size:25rpx}.question-card{padding:28rpx;margin-bottom:20rpx;background:#fff;border-radius:22rpx}.question-head{display:flex;gap:16rpx;justify-content:space-between;font-weight:650;line-height:1.5}.question-head>text:first-child{flex:1}.judge{color:#ef4444;white-space:nowrap}.judge.correct{color:#1f9d61}.line,.analysis,.scoring{display:block;margin-top:18rpx;color:#636366;line-height:1.6;white-space:pre-wrap}.answer{color:#1d1d1f}.analysis{padding:18rpx;background:#f4f7fb;border-radius:14rpx}.scoring{font-size:23rpx;color:#8e8e93}.state{padding:180rpx 40rpx;text-align:center;color:#8e8e93;display:flex;flex-direction:column;gap:18rpx}
</style>
