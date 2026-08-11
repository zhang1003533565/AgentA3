<template>
  <view v-if="visible" class="optimize-mask" @tap="onMaskTap">
    <view class="optimize-sheet" :class="{ 'optimize-sheet--visible': visible }" @tap.stop>
      <!-- 拖拽指示条 -->
      <view class="sheet-drag-bar">
        <view class="drag-indicator"></view>
      </view>

      <!-- 标题区 -->
      <view class="sheet-header">
        <view class="header-icon-wrap">
          <view class="header-icon">
            <view class="icon-refresh"></view>
          </view>
        </view>
        <text class="header-title">{{ title }}</text>
        <text class="header-subtitle">告诉AI你希望如何调整当前内容</text>
      </view>

      <!-- 输入区 -->
      <view class="sheet-input-wrap">
        <textarea
          class="optimize-input"
          v-model="instruction"
          placeholder="例如：增加Linux网络管理部分的内容&#10;调整知识结构层级，简化部分节点&#10;补充一些实战案例..."
          placeholder-class="optimize-placeholder"
          :maxlength="200"
          @input="onInput"
          auto-height
          :show-confirm-bar="false"
        />
        <text class="input-counter">{{ instruction.length }}/200</text>
      </view>

      <!-- 快捷建议 -->
      <view class="sheet-suggestions">
        <text class="suggestions-label">快捷建议</text>
        <view class="suggestion-tags">
          <view
            v-for="tag in quickTags"
            :key="tag.label"
            class="suggestion-tag"
            :class="{ 'suggestion-tag--active': selectedTags.includes(tag.label) }"
            @tap="toggleTag(tag)"
          >
            <text class="tag-icon">{{ tag.icon }}</text>
            <text class="tag-text">{{ tag.label }}</text>
          </view>
        </view>
      </view>

      <!-- 底部按钮 -->
      <view class="sheet-actions">
        <view class="action-btn action-btn--cancel" @tap="onCancel">
          <text class="action-btn-text action-btn-text--cancel">取消</text>
        </view>
        <view class="action-btn action-btn--submit" @tap="onSubmit">
          <view class="submit-icon">
            <view class="submit-sparkle"></view>
          </view>
          <text class="action-btn-text action-btn-text--submit">开始优化</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  title: { type: String, default: '优化思维导图' },
  currentMindMap: { type: Object, default: () => ({}) }
})

const emit = defineEmits(['close', 'optimize'])

const instruction = ref('')
const selectedTags = ref([])

const quickTags = [
  { icon: '', label: '增加细节', text: '请增加更多细节和子节点，让内容更丰富' },
  { icon: '🗂', label: '简化结构', text: '请简化结构，合并相似节点，让层级更清晰' },
  { icon: '⊞', label: '调整布局', text: '请调整节点布局，让整体结构更均衡' },
  { icon: '⊕', label: '补充遗漏', text: '请检查并补充可能遗漏的重要内容' },
  { icon: '↻', label: '重新整理', text: '请重新整理逻辑结构，让知识体系更连贯' }
]

function onInput() {
  // 同步更新选中的标签状态
  selectedTags.value = quickTags
    .filter(tag => instruction.value.includes(tag.text))
    .map(tag => tag.label)
}

function toggleTag(tag) {
  const idx = selectedTags.value.indexOf(tag.label)
  if (idx > -1) {
    selectedTags.value.splice(idx, 1)
    // 从输入框中移除对应文本
    instruction.value = instruction.value.replace(tag.text, '').replace(/\n{2,}/g, '\n').trim()
  } else {
    selectedTags.value.push(tag.label)
    const prefix = instruction.value ? '\n' : ''
    instruction.value = (instruction.value + prefix + tag.text).trim()
  }
}

function onMaskTap() {
  emit('close')
}

function onCancel() {
  instruction.value = ''
  selectedTags.value = []
  emit('close')
}

function onSubmit() {
  if (!instruction.value.trim()) {
    uni.showToast({ title: '请输入优化要求', icon: 'none' })
    return
  }
  emit('optimize', {
    currentMindMap: props.currentMindMap,
    userInstruction: instruction.value.trim()
  })
  instruction.value = ''
  selectedTags.value = []
}

watch(() => props.visible, (val) => {
  if (!val) {
    instruction.value = ''
    selectedTags.value = []
  }
})
</script>

<style lang="scss" scoped>
.optimize-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 1000;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  background: rgba(15, 23, 42, 0.45);
}

.optimize-sheet {
  width: 100%;
  max-height: 55vh;
  background: #FFFFFF;
  border-radius: 32rpx 32rpx 0 0;
  padding: 0 32rpx calc(28rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
  transform: translateY(100%);
  transition: transform 0.32s cubic-bezier(0.22, 1, 0.36, 1);
  overflow-y: auto;
}

.optimize-sheet--visible {
  transform: translateY(0);
}

/* 拖拽指示条 */
.sheet-drag-bar {
  display: flex;
  justify-content: center;
  padding: 16rpx 0 8rpx;
}

.drag-indicator {
  width: 72rpx;
  height: 8rpx;
  border-radius: 999rpx;
  background: #E2E5EA;
}

/* 标题区 */
.sheet-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 12rpx 0 28rpx;
}

.header-icon-wrap {
  margin-bottom: 16rpx;
}

.header-icon {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, #7C5FE0, #5B6BFE);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8rpx 24rpx rgba(91, 107, 254, 0.25);
}

.icon-refresh {
  width: 32rpx;
  height: 32rpx;
  border: 4rpx solid #FFFFFF;
  border-top-color: transparent;
  border-radius: 50%;
  position: relative;
  box-sizing: border-box;
}

.icon-refresh::before {
  content: '';
  position: absolute;
  top: -3rpx;
  right: -2rpx;
  width: 0;
  height: 0;
  border-left: 10rpx solid #FFFFFF;
  border-top: 7rpx solid transparent;
  border-bottom: 7rpx solid transparent;
}

.header-title {
  color: #1E293B;
  font-size: 34rpx;
  font-weight: 800;
  letter-spacing: 1rpx;
}

.header-subtitle {
  margin-top: 8rpx;
  color: #94A3B8;
  font-size: 24rpx;
  font-weight: 400;
}

/* 输入区 */
.sheet-input-wrap {
  position: relative;
  margin-bottom: 24rpx;
  padding: 24rpx;
  border-radius: 20rpx;
  border: 2rpx solid #EEF0F5;
  background: #FAFBFC;
  box-sizing: border-box;
  transition: border-color 0.2s ease;
}

.sheet-input-wrap:focus-within {
  border-color: #C7D2FE;
}

.optimize-input {
  width: 100%;
  min-height: 160rpx;
  color: #334155;
  font-size: 26rpx;
  line-height: 1.6;
  background: transparent;
  box-sizing: border-box;
}

.optimize-placeholder {
  color: #B0B8C8;
  font-size: 26rpx;
  line-height: 1.6;
}

.input-counter {
  position: absolute;
  right: 24rpx;
  bottom: 16rpx;
  color: #C0C5D2;
  font-size: 22rpx;
  font-weight: 500;
}

/* 快捷建议 */
.sheet-suggestions {
  margin-bottom: 28rpx;
}

.suggestions-label {
  display: block;
  margin-bottom: 16rpx;
  color: #475569;
  font-size: 24rpx;
  font-weight: 600;
}

.suggestion-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 14rpx;
}

.suggestion-tag {
  display: inline-flex;
  align-items: center;
  gap: 8rpx;
  height: 56rpx;
  padding: 0 22rpx;
  border-radius: 999rpx;
  background: #F1F0FF;
  border: 1rpx solid #E8E6FF;
  box-sizing: border-box;
  transition: all 0.2s ease;
}

.suggestion-tag--active {
  background: #7C5FE0;
  border-color: #7C5FE0;
}

.suggestion-tag--active .tag-text {
  color: #FFFFFF;
}

.suggestion-tag--active .tag-icon {
  opacity: 0.9;
}

.tag-icon {
  font-size: 22rpx;
  line-height: 1;
}

.tag-text {
  color: #5B5FE0;
  font-size: 24rpx;
  font-weight: 600;
  white-space: nowrap;
}

/* 底部按钮 */
.sheet-actions {
  display: flex;
  gap: 20rpx;
  padding-bottom: 8rpx;
}

.action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 92rpx;
  border-radius: 46rpx;
  box-sizing: border-box;
  transition: opacity 0.15s ease, transform 0.12s ease;
}

.action-btn:active {
  transform: scale(0.97);
}

.action-btn--cancel {
  flex: 1;
  background: #FFFFFF;
  border: 2rpx solid #E2E5EA;
}

.action-btn--submit {
  flex: 1.4;
  background: linear-gradient(135deg, #7C5FE0, #5B6BFE);
  box-shadow: 0 8rpx 24rpx rgba(91, 107, 254, 0.3);
}

.action-btn-text {
  font-size: 30rpx;
  font-weight: 700;
  letter-spacing: 1rpx;
}

.action-btn-text--cancel {
  color: #475569;
}

.action-btn-text--submit {
  color: #FFFFFF;
}

.submit-icon {
  position: relative;
  width: 32rpx;
  height: 32rpx;
  margin-right: 10rpx;
}

.submit-sparkle {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 4rpx;
  height: 20rpx;
  background: #FFFFFF;
  border-radius: 999rpx;
  transform: translate(-50%, -50%);
}

.submit-sparkle::before,
.submit-sparkle::after {
  content: '';
  position: absolute;
  left: 50%;
  top: 50%;
  width: 20rpx;
  height: 4rpx;
  background: #FFFFFF;
  border-radius: 999rpx;
  transform: translate(-50%, -50%);
}

.submit-sparkle::before {
  transform: translate(-50%, -50%) rotate(45deg);
}

.submit-sparkle::after {
  transform: translate(-50%, -50%) rotate(-45deg);
}
</style>
