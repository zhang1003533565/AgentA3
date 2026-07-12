<template>
  <view class="result-page">
    <nav-bar title="答题结果" :showBack="true" fixed placeholder />
    <view v-if="result" class="content">
      <view class="score-card">
        <text class="caption">客观题得分</text><text class="score">{{ result.objectiveScore ?? 0 }}</text><text class="total">/ {{ result.objectiveTotalScore ?? 0 }}</text>
        <text class="meta">第 {{ result.attemptNo }} 次 · {{ statusText(result.status) }}</text>
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
  onLoad(options) { this.attemptId = options?.attemptId || ''; this.loadResult() },
  methods: {
    async loadResult() {
      if (!this.attemptId) { this.errorMessage = '缺少答题记录编号'; return }
      this.errorMessage = ''
      try { const res = await getExamAttemptResult(this.attemptId); this.result = res?.data || null }
      catch (error) { this.result = null; this.errorMessage = error?.msg || error?.message || '请稍后重试' }
    },
    statusText(status) { return status === 'AUTO_SUBMITTED' ? '超时交卷' : '已交卷' },
    judgeText(question) { if (question.correct === true) return `正确 +${question.maxScore ?? 0}`; if (question.correct === false) return '错误'; return '简答题' },
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
.result-page{min-height:100vh;background:#f7f7f9}.content{padding:24rpx}.score-card{padding:42rpx 30rpx;margin-bottom:22rpx;background:linear-gradient(135deg,#3478f6,#6b9cff);border-radius:26rpx;color:#fff;text-align:center}.caption,.meta{display:block;opacity:.9}.score{font-size:84rpx;font-weight:800}.total{font-size:32rpx}.meta{margin-top:14rpx;font-size:24rpx}.question-card{padding:28rpx;margin-bottom:20rpx;background:#fff;border-radius:22rpx}.question-head{display:flex;gap:16rpx;justify-content:space-between;font-weight:650;line-height:1.5}.question-head>text:first-child{flex:1}.judge{color:#ef4444;white-space:nowrap}.judge.correct{color:#1f9d61}.line,.analysis,.scoring{display:block;margin-top:18rpx;color:#636366;line-height:1.6;white-space:pre-wrap}.answer{color:#1d1d1f}.analysis{padding:18rpx;background:#f4f7fb;border-radius:14rpx}.scoring{font-size:23rpx;color:#8e8e93}.state{padding:180rpx 40rpx;text-align:center;color:#8e8e93;display:flex;flex-direction:column;gap:18rpx}
</style>
