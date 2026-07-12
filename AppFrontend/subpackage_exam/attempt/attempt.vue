<template>
  <view class="exam-page">
    <view class="exam-header">
      <view>
        <text class="exam-title">在线答题</text>
        <text class="exam-progress">已答 {{ answeredCount }} / {{ questions.length }}</text>
      </view>
      <view class="countdown" :class="{ urgent: remaining <= 300 }">{{ countdownText }}</view>
    </view>

    <view v-if="loading" class="state-card">正在加载试卷...</view>
    <view v-else-if="loadError" class="state-card error">
      <text>{{ loadError }}</text>
      <button class="secondary-button" @click="loadAttempt">重新加载</button>
    </view>

    <view v-else class="question-list">
      <view v-for="(question, index) in questions" :key="question.id" class="question-card">
        <view class="question-heading">
          <text class="question-index">{{ index + 1 }}</text>
          <view class="question-copy">
            <text class="question-type">{{ typeLabel(question.type) }} · {{ question.score }} 分</text>
            <text class="question-stem">{{ question.stem }}</text>
          </view>
        </view>

        <radio-group
          v-if="question.type === 'single_choice'"
          class="option-list"
          @change="onSingleChange(question, $event)"
        >
          <label v-for="option in question.options" :key="option.key" class="option-row">
            <radio :value="option.key" :checked="answerState(question.id).value === option.key" color="#315efb" />
            <text class="option-key">{{ option.key }}.</text>
            <text>{{ option.text }}</text>
          </label>
        </radio-group>

        <checkbox-group
          v-else-if="question.type === 'multiple_choice'"
          class="option-list"
          @change="onMultipleChange(question, $event)"
        >
          <label v-for="option in question.options" :key="option.key" class="option-row">
            <checkbox
              :value="option.key"
              :checked="answerState(question.id).value.includes(option.key)"
              color="#315efb"
            />
            <text class="option-key">{{ option.key }}.</text>
            <text>{{ option.text }}</text>
          </label>
        </checkbox-group>

        <radio-group
          v-else-if="question.type === 'true_false'"
          class="judge-row"
          @change="onJudgeChange(question, $event)"
        >
          <label class="judge-option">
            <radio value="true" :checked="answerState(question.id).value === true" color="#315efb" />正确
          </label>
          <label class="judge-option">
            <radio value="false" :checked="answerState(question.id).value === false" color="#315efb" />错误
          </label>
        </radio-group>

        <view v-else-if="question.type === 'fill_blank'" class="blank-list">
          <view v-for="(blank, blankIndex) in answerState(question.id).value" :key="blank.id" class="blank-row">
            <text class="blank-label">第 {{ blankIndex + 1 }} 空</text>
            <input
              class="answer-input"
              :value="blank.value"
              placeholder="请输入答案"
              @input="onBlankInput(question, blankIndex, $event)"
            />
          </view>
        </view>

        <textarea
          v-else-if="question.type === 'short_answer'"
          class="answer-textarea"
          :value="answerState(question.id).value"
          maxlength="20000"
          placeholder="请输入你的答案"
          @input="onShortInput(question, $event)"
        />

        <view class="save-status" :class="answerState(question.id).status">
          {{ saveStatusText(answerState(question.id)) }}
        </view>
      </view>
    </view>

    <view v-if="!loading && !loadError" class="footer-actions">
      <button class="submit-button" :disabled="submitting" @click="confirmSubmit">
        {{ submitting ? '提交中...' : '提交试卷' }}
      </button>
      <text class="leave-hint">可直接返回，答案会自动保存，下次继续作答</text>
    </view>
  </view>
</template>

<script>
import { getExamAttempt, saveExamAnswer, submitExamAttempt } from '@/api/exam.js'
import {
  formatRemainingTime,
  mergeSavedAnswer,
  normalizeAnswer,
  parseAnswer,
  remainingSeconds,
  retryDelay
} from '../examState.js'

export default {
  data() {
    return {
      attemptId: null,
      attempt: null,
      questions: [],
      answers: {},
      loading: true,
      loadError: '',
      remaining: 0,
      clockTimer: null,
      refreshTimer: null,
      submitting: false,
      expiryHandled: false
    }
  },
  computed: {
    countdownText() {
      return formatRemainingTime(this.remaining)
    },
    answeredCount() {
      return Object.values(this.answers).filter((answer) => answer.answered).length
    }
  },
  onLoad(options) {
    this.attemptId = options.attemptId || options.id
    this.loadAttempt()
  },
  onHide() {
    this.flushPendingAnswers()
  },
  onUnload() {
    this.stopTimers()
    this.flushPendingAnswers()
  },
  methods: {
    async loadAttempt({ silent = false } = {}) {
      if (!this.attemptId) {
        this.loading = false
        this.loadError = '缺少答题记录参数'
        return
      }
      if (!silent) {
        this.loading = true
        this.loadError = ''
      }
      try {
        const response = await getExamAttempt(this.attemptId)
        const attempt = response.data || response
        if (attempt.status !== 'IN_PROGRESS') {
          this.goToResult()
          return
        }
        this.attempt = attempt
        this.questions = (attempt.questions || []).map(this.prepareQuestion)
        this.mergeAttemptAnswers(this.questions)
        this.syncCountdown(attempt.deadlineAt, attempt.serverNow || new Date().toISOString())
        this.startTimers()
      } catch (error) {
        if (!silent) this.loadError = this.errorMessage(error, '答题记录加载失败')
      } finally {
        if (!silent) this.loading = false
      }
    },
    prepareQuestion(question) {
      let body = {}
      try { body = question.bodyJson ? JSON.parse(question.bodyJson) : {} } catch (_) { body = {} }
      return {
        ...question,
        options: Array.isArray(body.options) ? body.options : [],
        blanks: Array.isArray(body.blanks) ? body.blanks : []
      }
    },
    mergeAttemptAnswers(questions) {
      const next = { ...this.answers }
      questions.forEach((question) => {
        const current = next[question.id]
        if (current && !current.saved) return
        const value = parseAnswer(question.type, question.userAnswerJson, question.blanks)
        next[question.id] = {
          value,
          answerJson: question.userAnswerJson || JSON.stringify(normalizeAnswer(question.type, value)),
          version: Number(question.version || 0),
          answered: Boolean(question.answered),
          saved: true,
          status: 'saved',
          retryIndex: 0,
          timer: null
        }
      })
      this.answers = next
    },
    answerState(questionId) {
      return this.answers[questionId] || { value: [], answered: false, saved: true, status: 'saved' }
    },
    onSingleChange(question, event) {
      this.updateAnswer(question, event.detail.value)
    },
    onMultipleChange(question, event) {
      this.updateAnswer(question, event.detail.value || [])
    },
    onJudgeChange(question, event) {
      this.updateAnswer(question, event.detail.value === 'true')
    },
    onBlankInput(question, blankIndex, event) {
      const blanks = this.answerState(question.id).value.map((blank) => ({ ...blank }))
      blanks[blankIndex].value = event.detail.value
      this.updateAnswer(question, blanks)
    },
    onShortInput(question, event) {
      this.updateAnswer(question, event.detail.value)
    },
    updateAnswer(question, value) {
      const previous = this.answerState(question.id)
      if (previous.timer) clearTimeout(previous.timer)
      const normalized = normalizeAnswer(question.type, value)
      const answerJson = JSON.stringify(normalized)
      const next = {
        ...previous,
        value,
        answerJson,
        answered: this.hasAnswer(question.type, normalized),
        saved: false,
        status: 'pending',
        retryIndex: 0,
        timer: null
      }
      this.$set(this.answers, question.id, next)
      this.scheduleSave(question, 500)
    },
    scheduleSave(question, delay) {
      const state = this.answerState(question.id)
      if (state.timer) clearTimeout(state.timer)
      state.timer = setTimeout(() => this.persistAnswer(question), delay)
      this.$set(this.answers, question.id, state)
    },
    async persistAnswer(question, force = false) {
      const local = this.answerState(question.id)
      if (local.saved || local.status === 'saving' || (this.submitting && !force)) return
      const sentJson = local.answerJson
      const sentVersion = local.version
      this.$set(this.answers, question.id, { ...local, timer: null, status: 'saving' })
      try {
        const response = await saveExamAnswer(this.attemptId, question.id, {
          answerJson: sentJson,
          version: sentVersion
        })
        const saved = response.data || response
        const latest = this.answerState(question.id)
        if (latest.answerJson !== sentJson) {
          this.$set(this.answers, question.id, {
            ...latest,
            version: Math.max(Number(latest.version || 0), Number(saved.version || 0)),
            status: 'pending',
            saved: false,
            retryIndex: 0
          })
          this.scheduleSave(question, 0)
          return
        }
        this.$set(this.answers, question.id, {
          ...mergeSavedAnswer(latest, saved),
          value: latest.value,
          status: 'saved',
          retryIndex: 0,
          timer: null
        })
      } catch (error) {
        const latest = this.answerState(question.id)
        if (force) {
          this.$set(this.answers, question.id, { ...latest, status: 'error', timer: null })
          throw error
        }
        const delay = retryDelay(latest.retryIndex)
        if (delay == null) {
          this.$set(this.answers, question.id, { ...latest, status: 'error', timer: null })
          return
        }
        this.$set(this.answers, question.id, {
          ...latest,
          status: 'retrying',
          retryIndex: latest.retryIndex + 1,
          timer: null
        })
        this.scheduleSave(question, delay)
      }
    },
    flushPendingAnswers(force = false) {
      return Promise.all(this.questions.map(async (question) => {
        let state = this.answerState(question.id)
        if (state.timer) clearTimeout(state.timer)
        if (state.saved) return Promise.resolve()
        if (state.status === 'saving') await this.waitForSave(question)
        state = this.answerState(question.id)
        if (state.saved) return Promise.resolve()
        return this.persistAnswer(question, force)
      }))
    },
    waitForSave(question) {
      return new Promise((resolve) => {
        const check = () => {
          const status = this.answerState(question.id).status
          if (status !== 'saving') resolve()
          else setTimeout(check, 50)
        }
        check()
      })
    },
    syncCountdown(deadlineAt, serverNow) {
      this.remaining = remainingSeconds(deadlineAt, serverNow)
      this.expiryHandled = false
      if (this.remaining === 0) this.handleExpiry()
    },
    startTimers() {
      this.stopTimers()
      this.clockTimer = setInterval(() => {
        this.remaining = Math.max(0, this.remaining - 1)
        if (this.remaining === 0) this.handleExpiry()
      }, 1000)
      this.refreshTimer = setInterval(() => this.loadAttempt({ silent: true }), 30000)
    },
    stopTimers() {
      if (this.clockTimer) clearInterval(this.clockTimer)
      if (this.refreshTimer) clearInterval(this.refreshTimer)
      this.clockTimer = null
      this.refreshTimer = null
    },
    async handleExpiry() {
      if (this.expiryHandled || this.submitting) return
      this.expiryHandled = true
      await this.submitNow(true)
    },
    confirmSubmit() {
      if (this.submitting) return
      uni.showModal({
        title: '确认交卷',
        content: `当前已答 ${this.answeredCount} / ${this.questions.length} 题，交卷后不能修改，是否继续？`,
        confirmText: '确认交卷',
        success: (result) => { if (result.confirm) this.submitNow(false) }
      })
    },
    async submitNow(expired) {
      if (this.submitting) return
      this.submitting = true
      this.stopTimers()
      try {
        if (expired) {
          try { await this.flushPendingAnswers(true) } catch (_) { /* 后端可能已按截止时间自动交卷 */ }
        } else {
          await this.flushPendingAnswers(true)
        }
        await submitExamAttempt(this.attemptId)
        if (expired) uni.showToast({ title: '考试时间已到，已自动交卷', icon: 'none' })
        this.goToResult()
      } catch (error) {
        this.submitting = false
        if (!expired) this.startTimers()
        uni.showToast({ title: this.errorMessage(error, '交卷失败，请重试'), icon: 'none' })
      }
    },
    goToResult() {
      uni.redirectTo({
        url: `/subpackage_exam/attemptResult/attemptResult?attemptId=${encodeURIComponent(this.attemptId)}`
      })
    },
    hasAnswer(type, answer) {
      if (type === 'single_choice') return Boolean(answer.selectedOption)
      if (type === 'multiple_choice') return answer.selectedOptions.length > 0
      if (type === 'true_false') return true
      if (type === 'fill_blank') return answer.blanks.some((blank) => blank.value)
      if (type === 'short_answer') return Boolean(answer.text)
      return false
    },
    typeLabel(type) {
      return ({
        single_choice: '单选题',
        multiple_choice: '多选题',
        true_false: '判断题',
        fill_blank: '填空题',
        short_answer: '简答题'
      })[type] || '题目'
    },
    saveStatusText(state) {
      return ({ pending: '等待保存', saving: '保存中...', retrying: '保存失败，正在重试', error: '保存失败，请修改后重试', saved: '已保存' })[state.status] || '已保存'
    },
    errorMessage(error, fallback) {
      return (error && (error.msg || error.message || (error.data && error.data.message))) || fallback
    }
  }
}
</script>

<style lang="scss" scoped>
.exam-page { min-height: 100vh; padding-bottom: 180rpx; background: #f5f7fb; color: #182033; }
.exam-header { position: sticky; top: 0; z-index: 10; display: flex; align-items: center; justify-content: space-between; padding: 28rpx 32rpx; background: #fff; box-shadow: 0 4rpx 18rpx rgba(27, 45, 86, .08); }
.exam-title { display: block; font-size: 34rpx; font-weight: 700; }
.exam-progress { display: block; margin-top: 8rpx; color: #78849b; font-size: 24rpx; }
.countdown { padding: 14rpx 22rpx; border-radius: 16rpx; background: #edf2ff; color: #315efb; font-size: 34rpx; font-weight: 700; font-variant-numeric: tabular-nums; }
.countdown.urgent { background: #fff0ed; color: #e94d36; }
.state-card { margin: 32rpx; padding: 48rpx 32rpx; border-radius: 20rpx; background: #fff; text-align: center; color: #667085; }
.state-card.error { color: #c3392c; }
.secondary-button { margin-top: 24rpx; width: 240rpx; color: #315efb; background: #edf2ff; }
.question-list { padding: 24rpx; }
.question-card { margin-bottom: 24rpx; padding: 30rpx; border-radius: 22rpx; background: #fff; box-shadow: 0 6rpx 22rpx rgba(27, 45, 86, .06); }
.question-heading { display: flex; align-items: flex-start; }
.question-index { display: flex; align-items: center; justify-content: center; width: 48rpx; height: 48rpx; margin-right: 18rpx; border-radius: 14rpx; background: #315efb; color: #fff; font-weight: 700; }
.question-copy { flex: 1; }
.question-type { display: block; margin-bottom: 12rpx; color: #78849b; font-size: 23rpx; }
.question-stem { display: block; font-size: 30rpx; line-height: 1.55; font-weight: 600; }
.option-list, .blank-list { display: block; margin-top: 24rpx; }
.option-row { display: flex; align-items: flex-start; margin-top: 16rpx; padding: 22rpx; border-radius: 16rpx; background: #f7f9fc; line-height: 1.5; }
.option-key { margin: 0 10rpx; font-weight: 600; }
.judge-row { display: flex; gap: 20rpx; margin-top: 28rpx; }
.judge-option { flex: 1; padding: 24rpx; border-radius: 16rpx; background: #f7f9fc; text-align: center; }
.blank-row { margin-top: 18rpx; }
.blank-label { display: block; margin-bottom: 10rpx; color: #667085; font-size: 24rpx; }
.answer-input, .answer-textarea { box-sizing: border-box; width: 100%; border: 2rpx solid #dce2ee; border-radius: 14rpx; background: #fbfcfe; }
.answer-input { height: 82rpx; padding: 0 22rpx; }
.answer-textarea { min-height: 260rpx; margin-top: 24rpx; padding: 22rpx; line-height: 1.6; }
.save-status { margin-top: 20rpx; color: #8993a4; font-size: 22rpx; text-align: right; }
.save-status.error { color: #d14343; }
.save-status.retrying { color: #c77b12; }
.footer-actions { position: fixed; right: 0; bottom: 0; left: 0; z-index: 12; padding: 20rpx 28rpx calc(18rpx + env(safe-area-inset-bottom)); background: rgba(255,255,255,.96); box-shadow: 0 -4rpx 18rpx rgba(27, 45, 86, .08); }
.submit-button { height: 84rpx; border-radius: 18rpx; color: #fff; background: #315efb; font-size: 30rpx; font-weight: 700; line-height: 84rpx; }
.submit-button[disabled] { opacity: .55; }
.leave-hint { display: block; margin-top: 10rpx; color: #8993a4; font-size: 21rpx; text-align: center; }
</style>
