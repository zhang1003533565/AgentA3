<template>
  <view class="page">
    <!-- 通用导航栏 -->
    <nav-bar title="AI试卷生成" :showBack="true" />

    <view class="content">
      <!-- 考试类型 Tab -->
      <view class="section-card">
        <view class="exam-type-tabs">
          <view
            class="exam-type-tab"
            :class="{ 'exam-type-tab--active': examType === item.value }"
            v-for="item in examTypes"
            :key="item.value"
            @tap="examType = item.value"
          >
            <text class="exam-type-tab-text">{{ item.label }}</text>
          </view>
        </view>
      </view>

      <!-- 知识点输入 -->
      <view class="section-card">
        <view class="field-label">知识点输入</view>
        <textarea
          class="knowledge-input"
          v-model="knowledgeText"
          placeholder="请输入需要考察的知识点，智能体将根据知识点出题\n例如：Java面向对象、集合框架、IO流、多线程、异常处理"
          :maxlength="500"
        />
        <view class="input-footer">
          <text class="char-count">{{ knowledgeText.length }}/500</text>
        </view>
      </view>

      <!-- 试卷难度 -->
      <view class="section-card">
        <view class="field-label-row">
          <text class="field-label">试卷难度</text>
          <text class="field-info-icon">ⓘ</text>
        </view>
        <view class="difficulty-row">
          <view class="stars">
            <text
              class="star"
              :class="{ 'star--active': idx < difficulty }"
              v-for="idx in 5"
              :key="idx"
              @tap="difficulty = idx"
            >★</text>
          </view>
          <text class="difficulty-label">{{ difficultyLabels[difficulty - 1] || '' }}</text>
        </view>
      </view>

      <!-- 题目数量 -->
      <view class="section-card">
        <view class="field-label">题目数量</view>
        <view class="stepper-row">
          <view class="stepper-btn" @tap="decreaseTotal">−</view>
          <text class="stepper-value">{{ totalQuestions }}</text>
          <text class="stepper-unit">题</text>
          <view class="stepper-btn" @tap="increaseTotal">+</view>
        </view>
      </view>

      <!-- 题型分布 -->
      <view class="section-card">
        <view class="field-label">题型分布</view>
        <view class="question-type-list">
          <view class="question-type-item" v-for="item in questionTypes" :key="item.key">
            <view class="qt-left">
              <view class="qt-checkbox" :class="{ 'qt-checkbox--checked': item.enabled }" @tap="toggleType(item)">
                <text class="qt-check-icon" v-if="item.enabled">✓</text>
              </view>
              <text class="qt-name">{{ item.name }}</text>
              <text class="qt-percent">({{ getPercent(item) }}%)</text>
            </view>
            <view class="qt-right">
              <view class="mini-stepper-btn" @tap="decreaseType(item)">−</view>
              <text class="mini-stepper-value">{{ item.count }}</text>
              <view class="mini-stepper-btn" @tap="increaseType(item)">+</view>
              <text class="mini-stepper-unit">题</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 知识点覆盖 -->
      <view class="section-card">
        <view class="field-label-row">
          <text class="field-label">知识点覆盖</text>
          <text class="smart-recommend" @tap="smartRecommend">智能推荐 ↻</text>
        </view>
        <view class="knowledge-tags">
          <view
            class="knowledge-tag"
            v-for="(tag, idx) in knowledgeTags"
            :key="idx"
            @tap="removeTag(idx)"
          >
            <text class="tag-text">{{ tag }}</text>
            <text class="tag-close">✕</text>
          </view>
        </view>
      </view>

      <!-- 生成试卷按钮 -->
      <view class="generate-btn" :class="{ 'generate-btn--disabled': isGenerating }" @tap="generateExam">
        <text class="generate-btn-text">{{ isGenerating ? ' 生成中...' : '✦ 生成试卷' }}</text>
      </view>
      
      <!-- 试卷预览 -->
      <view class="section-card preview-card">
        <view class="preview-header">
          <text class="preview-title">试卷预览</text>
          <text class="preview-clear" @tap="clearPreview">清空预览</text>
        </view>
      
        <!-- 题目列表 -->
        <view class="questions-list" v-if="previewQuestions.length > 0">
          <view class="question-item" v-for="(q, index) in previewQuestions" :key="index">
            <view class="question-header">
              <text class="question-number">{{ index + 1 }}.</text>
              <text class="question-type">[{{ q.type || '题目' }}]</text>
            </view>
            <view class="question-stem">
              <text>{{ q.stem || q.question || '' }}</text>
            </view>
            <!-- 选择题选项 -->
            <view class="question-options" v-if="q.options && q.options.length > 0">
              <view class="option-item" v-for="opt in q.options" :key="opt.label">
                <text class="option-label">{{ opt.label }}.</text>
                <text class="option-text">{{ opt.text || opt.content || '' }}</text>
              </view>
            </view>
            <!-- 答案和解析 -->
            <view class="question-answer">
              <text class="answer-label">答案：</text>
              <text class="answer-value">{{ q.answer || q.correctAnswer || '' }}</text>
            </view>
            <view class="question-explanation" v-if="q.explanation || q.analysis">
              <text class="explanation-label">解析：</text>
              <text class="explanation-value">{{ q.explanation || q.analysis || '' }}</text>
            </view>
          </view>
        </view>
      
        <!-- 原始文本预览（当无法解析为题目时） -->
        <view class="preview-body" v-else-if="previewContent">
          <text class="preview-text">{{ previewContent }}</text>
        </view>
      
        <!-- 空状态 -->
        <view class="preview-empty" v-else>
          <text class="preview-empty-text">点击“生成试卷”后在此预览</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { queryLeaderAgent } from '@/api/ai.js'

// 考试类型
const examTypes = [
  { label: '期末考试', value: 'final' },
  { label: '单元测试', value: 'unit' },
  { label: '模拟考试', value: 'mock' },
  { label: '竞赛考试', value: 'contest' },
]
const examType = ref('final')

// 知识点文本输入
const knowledgeText = ref('')

// 难度
const difficulty = ref(3)
const difficultyLabels = ['简单', '较易', '中等', '较难', '困难']

// 题目数量
const totalQuestions = ref(20)
const increaseTotal = () => { if (totalQuestions.value < 100) totalQuestions.value++ }
const decreaseTotal = () => { if (totalQuestions.value > 5) totalQuestions.value-- }

// 题型分布（percent 由计算属性动态得出）
const questionTypes = ref([
  { key: 'single', name: '单选题', enabled: true, count: 4 },
  { key: 'multiple', name: '多选题', enabled: true, count: 4 },
  { key: 'judge', name: '判断题', enabled: true, count: 2 },
  { key: 'fill', name: '填空题', enabled: true, count: 3 },
  { key: 'short', name: '简答题', enabled: true, count: 3 },
  { key: 'calc', name: '计算题', enabled: true, count: 2 },
  { key: 'code', name: '编程题', enabled: true, count: 2 },
])

// 已启用的题型总题数
const enabledTotal = computed(() => {
  return questionTypes.value.filter(t => t.enabled).reduce((sum, t) => sum + t.count, 0)
})

// 动态计算每个题型的百分比
const getPercent = (item) => {
  if (!item.enabled || enabledTotal.value === 0) return 0
  return Math.round(item.count / enabledTotal.value * 100)
}

const toggleType = (item) => { item.enabled = !item.enabled }
const increaseType = (item) => { if (item.count < 50) item.count++ }
const decreaseType = (item) => { if (item.count > 0) item.count-- }

// 知识点
const knowledgeTags = ref(['面向对象', '集合框架', 'IO流', '多线程', '异常处理', '反射机制'])
const removeTag = (idx) => { knowledgeTags.value.splice(idx, 1) }
const smartRecommend = () => {
  const recommended = ['设计模式', '泛型编程', 'Lambda表达式', 'Stream API', '并发编程']
  recommended.forEach(t => {
    if (!knowledgeTags.value.includes(t)) knowledgeTags.value.push(t)
  })
}

// 试卷预览
const previewContent = ref('')
const previewQuestions = ref([])
const isGenerating = ref(false)
const clearPreview = () => { previewContent.value = ''; previewQuestions.value = [] }

// 生成试卷
const generateExam = async () => {
  if (!knowledgeText.value.trim()) {
    uni.showToast({ title: '请输入知识点', icon: 'none' })
    return
  }
  const enabledTypes = questionTypes.value.filter(t => t.enabled)
  if (enabledTypes.length === 0) {
    uni.showToast({ title: '请至少选择一种题型', icon: 'none' })
    return
  }

  isGenerating.value = true
  previewContent.value = ''
  previewQuestions.value = []

  // 构建 AI 提示词 - 使用 Leader 能识别的关键词
  const typeSummary = enabledTypes.map(t => `${t.name}${t.count}题`).join('、')
  const prompt = `请帮我出题，生成一份试卷。

知识点：${knowledgeText.value}
考试类型：${examTypes.find(e => e.value === examType.value).label}
难度：${difficultyLabels[difficulty.value - 1]}
题目数量：${totalQuestions.value}题
题型分布：${typeSummary}

请根据以上知识点和要求生成题目，每道题包含题干、选项（如果是选择题）、正确答案和解析。`

  try {
    const res = await queryLeaderAgent({ input: prompt })
    const answer = res?.data?.answer || ''

    // 尝试解析 JSON 回答
    let questions = []
    try {
      // 尝试直接解析
      const parsed = JSON.parse(answer)
      questions = parsed.questions || parsed.data || parsed.items || []
      if (!Array.isArray(questions)) {
        // 尝试从文本中提取 JSON
        const jsonMatch = answer.match(/\{[\s\S]*"questions"[\s\S]*\[[\s\S]*\][\s\S]*\}/)
        if (jsonMatch) {
          const extracted = JSON.parse(jsonMatch[0])
          questions = extracted.questions || []
        }
      }
    } catch (e) {
      // JSON 解析失败，尝试从 markdown 代码块中提取
      const codeBlockMatch = answer.match(/```(?:json)?\s*([\s\S]*?)```/)
      if (codeBlockMatch) {
        try {
          const extracted = JSON.parse(codeBlockMatch[1])
          questions = extracted.questions || extracted.data || extracted.items || []
        } catch (e2) {
          // 解析失败，显示原始文本
        }
      }
    }

    if (questions.length > 0) {
      previewQuestions.value = questions
      previewContent.value = ''
      uni.showToast({ title: `已生成 ${questions.length} 道题目`, icon: 'success' })
    } else {
      // 没有解析出题目，显示原始回答
      previewContent.value = answer || 'AI 未返回有效内容'
      uni.showToast({ title: '生成完成，请查看预览', icon: 'none' })
    }
  } catch (error) {
    // 提取错误信息
    let errorMsg = '未知错误'
    if (error?.data?.msg) errorMsg = error.data.msg
    else if (error?.data?.message) errorMsg = error.data.message
    else if (error?.errMsg) errorMsg = error.errMsg
    else if (error?.message) errorMsg = error.message
    
    previewContent.value = `生成失败：${errorMsg}\n\n可能原因：\n1. AI 服务未启动或连接失败\n2. LLM 模型配置缺失\n3. 知识点内容无法生成题目\n4. 请求超时`
    uni.showToast({ title: '生成失败', icon: 'error' })
  } finally {
    isGenerating.value = false
  }
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background-color: #F6F8FB;
  box-sizing: border-box;
}

.content {
  padding: 20rpx 24rpx 40rpx;
}

.section-card {
  background: #FFFFFF;
  border-radius: 16rpx;
  padding: 28rpx 24rpx;
  margin-bottom: 20rpx;
}

/* 考试类型 Tab */
.exam-type-tabs {
  display: flex;
  gap: 16rpx;
}

.exam-type-tab {
  flex: 1;
  padding: 18rpx 0;
  text-align: center;
  background: #F0F2F5;
  border-radius: 12rpx;
}

.exam-type-tab--active {
  background: #EEF0FF;
  border: 2rpx solid #4D6BFE;
}

.exam-type-tab-text {
  font-size: 26rpx;
  color: #666;
}

.exam-type-tab--active .exam-type-tab-text {
  color: #4D6BFE;
  font-weight: 600;
}

/* 字段标签 */
.field-label {
  font-size: 30rpx;
  font-weight: 700;
  color: #222;
  margin-bottom: 20rpx;
}

.field-label-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20rpx;
}

.field-info-icon {
  font-size: 28rpx;
  color: #AAA;
}

/* 知识点文本输入 */
.knowledge-input {
  width: 100%;
  min-height: 180rpx;
  padding: 20rpx;
  background: #F8F9FA;
  border-radius: 12rpx;
  font-size: 28rpx;
  color: #333;
  line-height: 1.6;
  box-sizing: border-box;
}

.input-footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 12rpx;
}

.char-count {
  font-size: 24rpx;
  color: #BBB;
}

/* 难度 */
.difficulty-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.stars {
  display: flex;
  gap: 8rpx;
}

.star {
  font-size: 44rpx;
  color: #DDD;
}

.star--active {
  color: #FFB800;
}

.difficulty-label {
  font-size: 26rpx;
  color: #888;
  margin-left: 8rpx;
}

/* 题目数量 Stepper */
.stepper-row {
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.stepper-btn {
  width: 60rpx;
  height: 60rpx;
  background: #F0F2F5;
  border-radius: 12rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32rpx;
  color: #333;
  font-weight: 600;
}

.stepper-value {
  font-size: 36rpx;
  font-weight: 700;
  color: #222;
  min-width: 60rpx;
  text-align: center;
}

.stepper-unit {
  font-size: 26rpx;
  color: #888;
}

/* 题型分布 */
.question-type-list {
  display: flex;
  flex-direction: column;
}

.question-type-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 18rpx 0;
  border-bottom: 1rpx solid #F5F5F5;
}

.question-type-item:last-child {
  border-bottom: none;
}

.qt-left {
  display: flex;
  align-items: center;
  gap: 14rpx;
}

.qt-checkbox {
  width: 40rpx;
  height: 40rpx;
  border-radius: 8rpx;
  border: 2rpx solid #DDD;
  display: flex;
  align-items: center;
  justify-content: center;
}

.qt-checkbox--checked {
  background: #4D6BFE;
  border-color: #4D6BFE;
}

.qt-check-icon {
  color: #FFF;
  font-size: 24rpx;
  font-weight: 700;
}

.qt-name {
  font-size: 28rpx;
  color: #333;
}

.qt-percent {
  font-size: 24rpx;
  color: #999;
}

.qt-right {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.mini-stepper-btn {
  width: 44rpx;
  height: 44rpx;
  background: #F0F2F5;
  border-radius: 8rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26rpx;
  color: #333;
  font-weight: 600;
}

.mini-stepper-value {
  font-size: 30rpx;
  font-weight: 700;
  color: #222;
  min-width: 36rpx;
  text-align: center;
}

.mini-stepper-unit {
  font-size: 24rpx;
  color: #888;
}

/* 知识点标签 */
.smart-recommend {
  font-size: 24rpx;
  color: #4D6BFE;
}

.knowledge-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 14rpx;
}

.knowledge-tag {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 12rpx 20rpx;
  background: #EEF0FF;
  border-radius: 999rpx;
}

.tag-text {
  font-size: 24rpx;
  color: #4D6BFE;
}

.tag-close {
  font-size: 20rpx;
  color: #4D6BFE;
}

/* 生成按钮 */
.generate-btn {
  background: linear-gradient(135deg, #6A8CFE 0%, #4D6BFE 100%);
  border-radius: 16rpx;
  padding: 30rpx 0;
  text-align: center;
  margin-bottom: 20rpx;
}

.generate-btn-text {
  color: #FFF;
  font-size: 32rpx;
  font-weight: 700;
}

.generate-btn--disabled {
  opacity: 0.6;
}

/* 试卷预览 */
.preview-card {
  margin-bottom: 40rpx;
}

.preview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20rpx;
}

.preview-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #222;
}

.preview-clear {
  font-size: 24rpx;
  color: #999;
}

/* 题目列表 */
.questions-list {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.question-item {
  background: #F8F9FA;
  border-radius: 12rpx;
  padding: 24rpx;
}

.question-header {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 16rpx;
}

.question-number {
  font-size: 30rpx;
  font-weight: 700;
  color: #4D6BFE;
}

.question-type {
  font-size: 24rpx;
  color: #888;
  background: #EEF0FF;
  padding: 4rpx 12rpx;
  border-radius: 6rpx;
}

.question-stem {
  font-size: 28rpx;
  color: #333;
  line-height: 1.6;
  margin-bottom: 16rpx;
}

.question-options {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  margin-bottom: 16rpx;
}

.option-item {
  display: flex;
  align-items: flex-start;
  gap: 12rpx;
}

.option-label {
  font-size: 28rpx;
  font-weight: 600;
  color: #555;
  min-width: 32rpx;
}

.option-text {
  font-size: 28rpx;
  color: #333;
  line-height: 1.5;
  flex: 1;
}

.question-answer {
  display: flex;
  align-items: flex-start;
  gap: 8rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid #E8E8E8;
}

.answer-label {
  font-size: 26rpx;
  color: #888;
}

.answer-value {
  font-size: 26rpx;
  color: #4D6BFE;
  font-weight: 600;
}

.question-explanation {
  display: flex;
  align-items: flex-start;
  gap: 8rpx;
  margin-top: 12rpx;
}

.explanation-label {
  font-size: 26rpx;
  color: #888;
}

.explanation-value {
  font-size: 26rpx;
  color: #666;
  line-height: 1.6;
  flex: 1;
}

.preview-body {
  background: #F8F9FA;
  border-radius: 12rpx;
  padding: 24rpx;
}

.preview-text {
  font-size: 26rpx;
  color: #333;
  line-height: 1.8;
  white-space: pre-wrap;
}

.preview-empty {
  padding: 40rpx 0;
  text-align: center;
}

.preview-empty-text {
  font-size: 26rpx;
  color: #BBB;
}
</style>
