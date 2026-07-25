<template>
  <view class="page">
    <nav-bar title="AI 文生图" :showBack="true" />

    <scroll-view class="content" scroll-y>
      <view class="hero">
        <text class="hero-title">图片智能体</text>
        <text class="hero-subtitle">输入提示词，选择风格、数量和尺寸，生成单张或批量图片</text>
      </view>

      <view class="panel">
        <text class="label">提示词</text>
        <textarea
          class="prompt-input"
          v-model="form.prompt"
          maxlength="1000"
          placeholder="例如：为操作系统进程调度生成一张课堂教学插画，包含 CPU、就绪队列和等待队列"
        />

        <view class="field-row">
          <view class="field">
            <text class="label">风格</text>
            <picker :range="styleOptions" :value="styleIndex" @change="onStyleChange">
              <view class="picker-value">{{ form.style }}</view>
            </picker>
          </view>
          <view class="field">
            <text class="label">尺寸</text>
            <picker :range="sizeOptions" :value="sizeIndex" @change="onSizeChange">
              <view class="picker-value">{{ form.size }}</view>
            </picker>
          </view>
        </view>

        <view class="field-row">
          <view class="field">
            <text class="label">数量</text>
            <view class="count-control">
              <button class="count-btn" @tap="changeCount(-1)">-</button>
              <text class="count-value">{{ form.count }}</text>
              <button class="count-btn" @tap="changeCount(1)">+</button>
            </view>
          </view>
          <view class="field">
            <text class="label">Seed</text>
            <input class="text-input" v-model="seedText" type="number" placeholder="可选" />
          </view>
        </view>

        <text class="label">负向提示词</text>
        <input class="text-input" v-model="form.negativePrompt" placeholder="低清晰度、文字乱码、人物畸形" />

        <view class="actions">
          <button class="primary-btn" :disabled="loading" @tap="submitGenerate(false)">
            {{ loading ? '生成中...' : '生成图片' }}
          </button>
          <button class="secondary-btn" :disabled="loading" @tap="submitGenerate(true)">批量生成</button>
        </view>
      </view>

      <view class="status-card" v-if="task.taskId || loading">
        <view class="status-line">
          <text class="status-title">任务状态</text>
          <text class="status-pill" :class="`status-${task.status || 'running'}`">{{ statusText }}</text>
        </view>
        <text class="task-id" v-if="task.taskId">任务ID：{{ task.taskId }}</text>
        <text class="task-message" v-if="task.message">{{ task.message }}</text>
        <view class="actions compact" v-if="canRetry">
          <button class="secondary-btn" @tap="retry">重试</button>
        </view>
      </view>

      <view class="result-section" v-if="images.length">
        <text class="section-title">生成结果</text>
        <view class="image-grid">
          <view class="image-card" v-for="item in images" :key="item.index">
            <image class="result-image" :src="item.url || base64Src(item.base64)" mode="aspectFill" @tap="previewImage(item)" />
            <view class="image-meta">
              <text>第 {{ item.index + 1 }} 张</text>
              <text>{{ item.status }}</text>
            </view>
            <text class="error-text" v-if="item.errorMessage">{{ item.errorMessage }}</text>
          </view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { generateImage, generateImagesBatch, getImageTask } from '@/api/ai.js'

const styleOptions = ['扁平教学插画', 'PPT 封面图', '高质量数字插画', '写实摄影风', '水彩插画', '国风插画']
const sizeOptions = ['1664x928', '1472x1104', '1328x1328', '1104x1472', '928x1664']
const styleIndex = ref(0)
const sizeIndex = ref(0)
const seedText = ref('')
const loading = ref(false)
const lastPayload = ref(null)
const images = ref([])
const task = reactive({
  taskId: '',
  providerTaskId: '',
  status: '',
  message: ''
})

const form = reactive({
  prompt: '',
  style: styleOptions[0],
  size: sizeOptions[0],
  count: 1,
  negativePrompt: '低清晰度、文字乱码、人物畸形、过度拥挤',
  returnType: 'url'
})

const statusText = computed(() => {
  const map = {
    pending: '等待中',
    running: '生成中',
    success: '已完成',
    partial_success: '部分成功',
    failed: '失败'
  }
  return map[task.status] || '生成中'
})

const canRetry = computed(() => task.status === 'failed' || task.status === 'partial_success')

const onStyleChange = (event) => {
  styleIndex.value = Number(event.detail.value)
  form.style = styleOptions[styleIndex.value]
}

const onSizeChange = (event) => {
  sizeIndex.value = Number(event.detail.value)
  form.size = sizeOptions[sizeIndex.value]
}

const changeCount = (step) => {
  form.count = Math.max(1, Math.min(8, form.count + step))
}

const buildPayload = (forceBatch) => {
  const prompt = form.prompt.trim()
  if (!prompt) {
    uni.showToast({ title: '请输入图片提示词', icon: 'none' })
    return null
  }
  const seed = seedText.value ? Number(seedText.value) : undefined
  return {
    prompt,
    style: form.style,
    size: form.size,
    count: forceBatch ? Math.max(2, form.count) : form.count,
    seed,
    negativePrompt: form.negativePrompt,
    returnType: form.returnType,
    metadata: {
      source: 'app_image_generate',
      usage: 'campus_ai_image'
    }
  }
}

const submitGenerate = async (forceBatch) => {
  const payload = buildPayload(forceBatch)
  if (!payload) return
  lastPayload.value = { ...payload, forceBatch }
  loading.value = true
  images.value = []
  task.taskId = ''
  task.providerTaskId = ''
  task.status = 'running'
  task.message = ''
  try {
    const res = forceBatch || payload.count > 1
      ? await generateImagesBatch(payload)
      : await generateImage(payload)
    handleResult(res?.data || {})
    if (task.status === 'running' && task.taskId) {
      await pollTask(task.taskId)
    }
  } catch (error) {
    task.status = 'failed'
    task.message = error?.message || '图片生成失败'
    uni.showToast({ title: task.message, icon: 'none' })
  } finally {
    loading.value = false
  }
}

const handleResult = (data) => {
  task.taskId = data.taskId || ''
  task.providerTaskId = data.providerTaskId || ''
  task.status = data.status || ''
  task.message = data.message || ''
  images.value = Array.isArray(data.images) ? data.images : []
}

const pollTask = async (taskId) => {
  for (let i = 0; i < 12; i += 1) {
    await new Promise((resolve) => setTimeout(resolve, 2500))
    const res = await getImageTask(taskId)
    handleResult(res?.data || {})
    if (['success', 'partial_success', 'failed'].includes(task.status)) return
  }
}

const retry = () => {
  if (!lastPayload.value) return
  const { forceBatch, ...payload } = lastPayload.value
  form.prompt = payload.prompt
  form.style = payload.style
  form.size = payload.size
  form.count = payload.count
  form.negativePrompt = payload.negativePrompt
  submitGenerate(forceBatch)
}

const base64Src = (value) => value ? `data:image/png;base64,${value}` : ''

const previewImage = (item) => {
  const urls = images.value.map((image) => image.url || base64Src(image.base64)).filter(Boolean)
  const current = item.url || base64Src(item.base64)
  if (!current) return
  uni.previewImage({ urls, current })
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #f6f8fb;
}

.content {
  height: 100vh;
  box-sizing: border-box;
  padding: 24rpx;
}

.hero {
  padding: 28rpx 4rpx 20rpx;
}

.hero-title {
  display: block;
  font-size: 44rpx;
  font-weight: 700;
  color: #111827;
}

.hero-subtitle {
  display: block;
  margin-top: 12rpx;
  font-size: 26rpx;
  color: #6b7280;
  line-height: 1.5;
}

.panel,
.status-card,
.result-section {
  background: #fff;
  border-radius: 24rpx;
  padding: 28rpx;
  margin-bottom: 24rpx;
}

.label {
  display: block;
  font-size: 24rpx;
  color: #6b7280;
  margin-bottom: 12rpx;
}

.prompt-input {
  width: 100%;
  min-height: 180rpx;
  box-sizing: border-box;
  padding: 22rpx;
  background: #f8fafc;
  border-radius: 18rpx;
  font-size: 28rpx;
  color: #111827;
  margin-bottom: 24rpx;
}

.field-row {
  display: flex;
  gap: 20rpx;
  margin-bottom: 24rpx;
}

.field {
  flex: 1;
  min-width: 0;
}

.picker-value,
.text-input {
  min-height: 80rpx;
  box-sizing: border-box;
  padding: 0 20rpx;
  display: flex;
  align-items: center;
  background: #f8fafc;
  border-radius: 18rpx;
  font-size: 26rpx;
  color: #111827;
}

.count-control {
  height: 80rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #f8fafc;
  border-radius: 18rpx;
  overflow: hidden;
}

.count-btn {
  width: 84rpx;
  height: 80rpx;
  line-height: 80rpx;
  padding: 0;
  border-radius: 0;
  background: transparent;
  color: #111827;
  font-size: 34rpx;
}

.count-value {
  font-size: 30rpx;
  font-weight: 700;
  color: #111827;
}

.actions {
  display: flex;
  gap: 20rpx;
  margin-top: 28rpx;
}

.actions.compact {
  margin-top: 18rpx;
}

.primary-btn,
.secondary-btn {
  flex: 1;
  height: 88rpx;
  line-height: 88rpx;
  border-radius: 999rpx;
  font-size: 28rpx;
  font-weight: 600;
}

.primary-btn {
  background: #111827;
  color: #fff;
}

.secondary-btn {
  background: #eef2ff;
  color: #4f46e5;
}

.status-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
}

.status-title,
.section-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #111827;
}

.status-pill {
  padding: 8rpx 18rpx;
  border-radius: 999rpx;
  background: #eef2ff;
  color: #4f46e5;
  font-size: 22rpx;
}

.status-success {
  background: #ecfdf5;
  color: #059669;
}

.status-failed {
  background: #fef2f2;
  color: #dc2626;
}

.status-partial_success {
  background: #fff7ed;
  color: #ea580c;
}

.task-id,
.task-message {
  display: block;
  margin-top: 14rpx;
  font-size: 22rpx;
  color: #6b7280;
  word-break: break-all;
}

.image-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18rpx;
  margin-top: 20rpx;
}

.image-card {
  background: #f8fafc;
  border-radius: 20rpx;
  overflow: hidden;
}

.result-image {
  width: 100%;
  height: 260rpx;
  background: #e5e7eb;
}

.image-meta {
  display: flex;
  justify-content: space-between;
  padding: 14rpx 16rpx;
  font-size: 22rpx;
  color: #6b7280;
}

.error-text {
  display: block;
  padding: 0 16rpx 16rpx;
  font-size: 22rpx;
  color: #dc2626;
}
</style>
