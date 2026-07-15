<template>
  <view v-if="visible" class="profile-mask" @tap.self="close">
    <view class="profile-dialog">
      <view class="profile-dialog__head">
        <view>
          <text class="profile-dialog__eyebrow">动态学习画像</text>
          <text class="profile-dialog__title">{{ currentQuestion?.title || '画像已补充完成' }}</text>
        </view>
        <text class="profile-dialog__close" @tap="close">×</text>
      </view>

      <template v-if="currentQuestion">
        <text class="profile-dialog__progress">第 {{ questionIndex + 1 }} / {{ questions.length }} 问</text>
        <textarea
          v-model="answer"
          class="profile-dialog__input"
          :placeholder="currentQuestion.placeholder"
          :maxlength="500"
          auto-height
        />
        <text v-if="errorMessage" class="profile-dialog__error">{{ errorMessage }}</text>
        <button class="profile-dialog__submit" :disabled="submitting || !answer.trim()" @tap="submitAnswer">
          {{ submitting ? '正在更新画像…' : questionIndex === questions.length - 1 ? '完成画像补问' : '保存并继续' }}
        </button>
      </template>
      <view v-else class="profile-dialog__done">
        <text>本轮画像问题已完成，后续学习行为仍会持续更新画像。</text>
        <button class="profile-dialog__submit" @tap="finish">返回学习中心</button>
      </view>
    </view>
  </view>
</template>

<script>
import { submitProfileAnswer } from '@/api/learning.js'
import { learningErrorMessage, PYTHON_PROFILE_QUESTIONS } from '@/subpackage_learning/learningView.js'

export default {
  name: 'LearningProfileDialog',
  props: {
    visible: { type: Boolean, default: false },
    answeredQuestionIds: { type: Array, default: () => [] }
  },
  emits: ['close', 'answered', 'complete'],
  data() {
    return { questionQueue: [], questionIndex: 0, answer: '', submitting: false, errorMessage: '' }
  },
  computed: {
    questions() {
      return this.questionQueue
    },
    currentQuestion() {
      return this.questions[this.questionIndex] || null
    }
  },
  watch: {
    visible: {
      immediate: true,
      handler(value) {
        if (value) this.startQuestionRound()
      }
    }
  },
  methods: {
    startQuestionRound() {
      const answered = new Set(this.answeredQuestionIds.map(String))
      const remaining = PYTHON_PROFILE_QUESTIONS.filter(item => !answered.has(item.id))
      this.questionQueue = [...(remaining.length ? remaining : PYTHON_PROFILE_QUESTIONS)]
      this.questionIndex = 0
      this.answer = ''
      this.errorMessage = ''
    },
    close() {
      if (!this.submitting) this.$emit('close')
    },
    finish() {
      this.$emit('complete')
      this.$emit('close')
    },
    async submitAnswer() {
      if (!this.currentQuestion || !this.answer.trim() || this.submitting) return
      this.submitting = true
      this.errorMessage = ''
      try {
        const response = await submitProfileAnswer({
          questionId: this.currentQuestion.id,
          answer: this.answer.trim()
        })
        this.$emit('answered', {
          questionId: this.currentQuestion.id,
          answer: this.answer.trim(),
          profile: response?.data || null
        })
        this.questionIndex += 1
        this.answer = ''
        if (!this.currentQuestion) this.$emit('complete')
      } catch (error) {
        this.errorMessage = learningErrorMessage(error, '画像更新失败，请重试')
      } finally {
        this.submitting = false
      }
    }
  }
}
</script>

<style scoped>
.profile-mask{position:fixed;z-index:1600;inset:0;display:flex;align-items:flex-end;background:rgba(15,23,42,.46)}
.profile-dialog{width:100%;padding:34rpx 30rpx calc(34rpx + env(safe-area-inset-bottom));border-radius:34rpx 34rpx 0 0;background:#fff;box-sizing:border-box}
.profile-dialog__head{display:flex;justify-content:space-between;gap:20rpx}.profile-dialog__eyebrow,.profile-dialog__title{display:block}.profile-dialog__eyebrow{font-size:22rpx;color:#4f46e5;font-weight:700;letter-spacing:2rpx}.profile-dialog__title{margin-top:10rpx;font-size:34rpx;font-weight:750;color:#172033}.profile-dialog__close{font-size:48rpx;color:#94a3b8;line-height:1}
.profile-dialog__progress{display:block;margin-top:24rpx;color:#64748b;font-size:24rpx}.profile-dialog__input{width:100%;min-height:180rpx;margin-top:18rpx;padding:24rpx;border-radius:20rpx;background:#f5f7fb;box-sizing:border-box;font-size:28rpx;line-height:1.6}
.profile-dialog__error{display:block;margin-top:14rpx;color:#dc2626;font-size:24rpx}.profile-dialog__submit{margin-top:24rpx;border:0;border-radius:18rpx;background:#4f46e5;color:#fff;font-size:28rpx;font-weight:700}.profile-dialog__submit[disabled]{opacity:.5}.profile-dialog__done{padding-top:28rpx;color:#475569;line-height:1.7}
</style>
