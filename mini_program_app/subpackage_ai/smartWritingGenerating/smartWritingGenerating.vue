<template>
  <view class="generating-page">
    <nav-bar title="智能写作" :showBack="true" fixed placeholder />

    <view class="generating-card">
      <view class="generating-badge">AI 正在为你创作</view>
      <text class="generating-title">{{ sceneLabel }}生成中</text>
      <text class="generating-subtitle">{{ currentMessage }}</text>

      <view class="progress-ring" :style="{ '--progress': `${progress}%` }">
        <view class="progress-ring__inner">
          <text>{{ progress }}%</text>
        </view>
      </view>

      <view class="stage-list">
        <view
          v-for="(stage, index) in stages"
          :key="stage"
          class="stage-item"
          :class="{
            'stage-item--done': progress > stageThresholds[index],
            'stage-item--active': progress >= stageThresholds[index] && progress <= (stageThresholds[index + 1] || 100)
          }"
        >
          <view class="stage-icon">{{ progress > stageThresholds[index] ? '✓' : '○' }}</view>
          <text class="stage-label">{{ stage }}</text>
        </view>
      </view>

      <text v-if="errorMessage" class="error-message">{{ errorMessage }}</text>

      <view v-if="errorMessage" class="retry-btn" @tap="startGeneration">重新生成</view>
      <view class="robot" aria-hidden="true">
        <view class="robot-ear robot-ear--left"></view>
        <view class="robot-head">
          <view class="robot-eye"></view>
          <view class="robot-eye"></view>
          <view class="robot-mouth"></view>
        </view>
        <view class="robot-ear robot-ear--right"></view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { writeWithAi } from '@/api/ai.js'
import {
  buildSmartWritingTitle,
  saveSmartWritingHistory
} from '@/utils/smartWritingHistory.js'

const sceneLabels = {
  weekly: '周报',
  summary: '工作总结',
  holiday: '节日祝福',
  review: '评语'
}

const sceneMessages = {
  weekly: ['正在整理本周工作重点...', '正在归纳完成情况...', '正在优化周报表达...'],
  summary: ['正在梳理工作成果...', '正在提炼总结结构...', '正在完善总结内容...'],
  holiday: ['正在分析祝福场景...', '正在组织祝福语言...', '正在润色节日表达...'],
  review: ['正在分析评价对象...', '正在提炼表现亮点...', '正在完善评语内容...']
}

const stages = ['分析创作内容', '组织文章结构', '生成核心内容', '完善表达细节', '优化成稿']
const stageThresholds = [4, 24, 45, 68, 86]
const progress = ref(4)
const prompt = ref('')
const scene = ref('weekly')
const wordCount = ref('自动')
const model = ref('DeepSeek')
const errorMessage = ref('')
const stepTimer = ref(null)
const messageIndex = ref(0)
const sceneDetails = ref({})

const sceneLabel = computed(() => sceneLabels[scene.value] || '作品')
const currentMessage = computed(() => {
  const messages = sceneMessages[scene.value] || sceneMessages.weekly
  return messages[messageIndex.value % messages.length]
})

onLoad((options) => {
  prompt.value = options?.prompt ? decodeURIComponent(options.prompt) : ''
  scene.value = options?.scene ? decodeURIComponent(options.scene) : 'weekly'
  wordCount.value = options?.wordCount ? decodeURIComponent(options.wordCount) : '自动'
  model.value = options?.model ? decodeURIComponent(options.model) : 'DeepSeek'
  if (options?.details) {
    try {
      sceneDetails.value = JSON.parse(decodeURIComponent(options.details))
    } catch (error) {
      sceneDetails.value = {}
    }
  }
  startGeneration()
})

onUnload(() => {
  stopProgress()
})

const stopProgress = () => {
  if (stepTimer.value) {
    clearInterval(stepTimer.value)
    stepTimer.value = null
  }
}

const startProgress = () => {
  stopProgress()
  stepTimer.value = setInterval(() => {
    if (progress.value >= 94) return
    const increment = progress.value < 35 ? 4 : progress.value < 70 ? 2 : 1
    progress.value = Math.min(progress.value + increment, 94)
    if (progress.value % 12 < 2) {
      messageIndex.value += 1
    }
  }, 420)
}

const startGeneration = async () => {
  if (!prompt.value.trim()) {
    errorMessage.value = '缺少创作内容，请返回上一页重新输入'
    return
  }
  errorMessage.value = ''
  progress.value = 4
  messageIndex.value = 0
  startProgress()
  try {
    const finalPrompt = [
      `创作场景：${sceneLabel.value}`,
      wordCount.value && wordCount.value !== '自动' ? `字数要求：${wordCount.value}` : '',
      `写作需求：${prompt.value.trim()}`
    ].filter(Boolean).join('\n')
    const response = await writeWithAi({
      prompt: finalPrompt,
      wordCount: wordCount.value,
      modelName: model.value
    })
    const content = response?.data?.content || ''
    if (!content) {
      throw new Error('AI 未返回内容')
    }
    const resultModel = response?.data?.model || model.value
    const record = saveSmartWritingHistory({
      title: buildSmartWritingTitle(sceneLabel.value, prompt.value),
      sceneKey: scene.value,
      sceneLabel: sceneLabel.value,
      prompt: prompt.value.trim(),
      sceneDetails: sceneDetails.value,
      content,
      wordCount: wordCount.value,
      model: resultModel,
      modelConfigPrefix: model.value
    })
    stopProgress()
    progress.value = 100
    setTimeout(() => {
      uni.redirectTo({
        url: `/subpackage_ai/smartWritingDetail/smartWritingDetail?id=${encodeURIComponent(record.id)}&source=history`
      })
    }, 520)
  } catch (error) {
    stopProgress()
    errorMessage.value = formatGenerationError(error)
  }
}

const formatGenerationError = (error) => {
  const message = error?.msg || error?.message || error?.data?.msg || error?.data?.message || ''
  if (message.includes('AI Key 验证失败') || message.includes('Authentication Fails')) {
    return 'AI Key 验证失败，请检查后台模型配置中的 API Key 是否正确、是否可用'
  }
  if (message.includes('额度') || message.includes('quota') || message.includes('balance')) {
    return '模型额度不足，请检查服务商账号余额或额度'
  }
  if (message.includes('model') || message.includes('模型')) {
    return '模型配置不可用，请检查后台模型 ID 和服务地址'
  }
  return message || 'AI 创作失败，请重试'
}
</script>

<style lang="scss" scoped>
.generating-page {
  min-height: 100vh;
  background: linear-gradient(180deg, #f3f4ff 0%, #fbfaff 100%);
}

.generating-card {
  display: flex;
  min-height: calc(100vh - 150rpx);
  align-items: center;
  padding: 54rpx 38rpx 34rpx;
  flex-direction: column;
  box-sizing: border-box;
}

.generating-badge {
  padding: 12rpx 28rpx;
  border-radius: 999rpx;
  background: #f0edff;
  color: #6553d7;
  font-size: 22rpx;
  font-weight: 650;
}

.generating-title {
  margin-top: 34rpx;
  color: #252833;
  font-size: 34rpx;
  font-weight: 750;
}

.generating-subtitle {
  margin-top: 12rpx;
  color: #9aa0b1;
  font-size: 23rpx;
  text-align: center;
}

.progress-ring {
  display: flex;
  width: 250rpx;
  height: 250rpx;
  margin-top: 34rpx;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: conic-gradient(#6434dd var(--progress), #eceaff 0);
  transform: rotate(-90deg);
}

.progress-ring__inner {
  display: flex;
  width: 202rpx;
  height: 202rpx;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #fbfaff;
  transform: rotate(90deg);
}

.progress-ring__inner text {
  color: #6534dd;
  font-size: 44rpx;
  font-weight: 750;
}

.stage-list {
  width: 100%;
  max-width: 520rpx;
  margin-top: 40rpx;
}

.stage-item {
  display: flex;
  align-items: center;
  min-height: 54rpx;
  gap: 18rpx;
  color: #9aa1b2;
}

.stage-item--done,
.stage-item--active {
  color: #333743;
  font-weight: 650;
}

.stage-icon {
  display: flex;
  width: 34rpx;
  height: 34rpx;
  align-items: center;
  justify-content: center;
  color: #b7afef;
  font-size: 24rpx;
}

.stage-item--done .stage-icon {
  color: #54c995;
}

.stage-item--active .stage-icon {
  color: #6d5de5;
}

.stage-label {
  font-size: 25rpx;
}

.error-message {
  width: 100%;
  max-width: 520rpx;
  margin-top: 24rpx;
  color: #d05d69;
  font-size: 22rpx;
  line-height: 1.55;
  text-align: center;
  word-break: break-word;
}

.retry-btn {
  margin-top: 20rpx;
  padding: 14rpx 34rpx;
  border-radius: 999rpx;
  background: #eeeaff;
  color: #5b4bd3;
  font-size: 23rpx;
  font-weight: 650;
}

.robot {
  position: relative;
  display: flex;
  align-items: center;
  margin-top: auto;
  padding-top: 28rpx;
}

.robot-head {
  position: relative;
  display: flex;
  width: 142rpx;
  height: 92rpx;
  align-items: center;
  justify-content: center;
  gap: 28rpx;
  border-radius: 30rpx;
  background: #111633;
  box-shadow: 0 0 36rpx rgba(102, 71, 229, 0.4);
}

.robot-eye {
  width: 14rpx;
  height: 14rpx;
  border-radius: 50%;
  background: #73baff;
  box-shadow: 0 0 16rpx #55a4ff;
}

.robot-mouth {
  position: absolute;
  bottom: 18rpx;
  left: 50%;
  width: 28rpx;
  height: 5rpx;
  border-radius: 99rpx;
  background: #6479d8;
  transform: translateX(-50%);
}

.robot-ear {
  width: 28rpx;
  height: 58rpx;
  border-radius: 14rpx;
  background: #d9d4ff;
  box-shadow: 0 0 22rpx rgba(111, 80, 227, 0.22);
}

.robot-ear--left {
  margin-right: -8rpx;
}

.robot-ear--right {
  margin-left: -8rpx;
}
</style>
