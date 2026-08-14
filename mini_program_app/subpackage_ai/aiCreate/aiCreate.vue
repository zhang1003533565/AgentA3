<template>
  <view class="page">
    <!-- 通用导航栏 -->
    <nav-bar title="AI创作" :showBack="true" />
    
    <view class="content">
      <!-- 上方板块：顶部三大核心卡片 -->
      <view class="section-card">
        <view class="hero-section">
          <view class="hero-grid">
            <!-- 左侧大卡片：智能写作 -->
            <view class="hero-main" @tap="goToSmartWriting">
              <view class="hero-main-text">
                <text class="hero-main-title">智能写作</text>
                <text class="hero-main-subtitle">Deepseek赋能</text>
              </view>
              <view class="hero-main-cta">立即创作</view>
              <image class="hero-main-icon" src="/static/icons/ai create/DeepSeek.png" mode="aspectFit"></image>
            </view>

            <!-- 右侧上下两个小卡片 -->
            <view class="hero-side">
              <!-- 右上：AI视频 -->
              <view class="hero-small hero-video" @tap="handleToolTap({ name: 'AI视频', desc: '轻松生成视频' })">
                <view class="hero-small-text">
                  <text class="hero-small-title">AI视频</text>
                  <text class="hero-small-subtitle">轻松生成视频</text>
                </view>
                <image class="hero-small-icon" src="/static/icons/ai create/img-video.png" mode="aspectFit"></image>
              </view>
              <!-- 右下：AIPPT -->
              <view class="hero-small hero-ppt" @tap="handleToolTap({ name: 'AIPPT', desc: '智能PPT神器' })">
                <view class="hero-small-text">
                  <text class="hero-small-title">AIPPT</text>
                  <text class="hero-small-subtitle">智能PPT神器</text>
                </view>
                <view class="hero-small-deco" aria-hidden="true"></view>
              </view>
            </view>
          </view>
        </view>
      </view>

      <!-- 下方板块：分类及工具列表 -->
      <view class="section-card">
        <!-- 快捷工具区 -->
        <view class="quick-section">
          <view class="quick-actions">
            <view class="quick-item" v-for="item in quickActions" :key="item.name" @tap="handleToolTap(item)">
              <view class="quick-icon-wrap" :style="{ background: item.lightColor }">
                <image class="quick-icon" :src="item.icon" mode="aspectFit"></image>
              </view>
              <text class="quick-label">{{ item.name }}</text>
            </view>
          </view>
        </view>

        <!-- 动态分类导航条 -->
        <view class="tabs-section">
          <scroll-view class="tabs-scroll" scroll-x="true" :show-scrollbar="false">
            <view class="tabs-inner">
              <view
                class="tab-item"
                :class="{ 'tab-item--active': activeTab === index }"
                v-for="(tab, index) in tabs"
                :key="index"
                @tap="switchTab(index)"
              >
                <text class="tab-text">{{ tab }}</text>
                <view class="tab-indicator" v-if="activeTab === index"></view>
              </view>
            </view>
          </scroll-view>
        </view>

        <!-- 网格工具列表区 -->
        <view class="tools-section">
          <view v-if="activeTab === 2 && campusLoading" class="course-state">正在加载校园课程...</view>
          <view v-else-if="activeTab === 2 && campusError" class="course-state course-state--error" @tap="loadCampusCourses">
            <text>{{ campusError }}</text>
            <text class="course-state-action">点击重试</text>
          </view>
          <view v-else-if="activeTab === 2 && currentTools.length === 0" class="course-state">
            <text>暂无可学习课程</text>
            <text class="course-state-subtitle">管理员发布课程后会显示在这里</text>
          </view>
          <view v-else class="tools-grid">
            <view class="tool-item" v-for="tool in currentTools" :key="tool.courseId || tool.name" @tap="handleToolTap(tool)">
              <view v-if="tool.courseId" class="course-cover-wrapper">
                <image class="course-cover-image" :src="tool.icon" mode="aspectFill"></image>
              </view>
              <view v-else class="icon-wrapper" :style="{ '--light-color': tool.lightColor, '--theme-color': tool.themeColor }">
                <view class="icon-inner">
                  <image class="tool-icon" :src="tool.icon" mode="aspectFit"></image>
                </view>
              </view>
              <view class="tool-info">
                <text class="tool-name">{{ tool.name }}</text>
                <text class="tool-desc">{{ tool.desc }}</text>
              </view>
            </view>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { resolveAiToolDestination } from '@/subpackage_learning/aiToolRoutes.js'
import { getCampusCourses } from '@/api/campusCourse.js'

// 响应式数据
const activeTab = ref(0)
const campusCourses = ref([])
const campusLoading = ref(false)
const campusError = ref('')

// Tab 列表
const tabs = ref(['热门工具', '格式转换', '校园课程', '职场创意', '社交媒体'])

// 快捷工具 Mock 数据
const quickActions = ref([
  { name: 'AI对话', icon: '/static/icons/ai create/ai chat.png', themeColor: '#3B82F6', lightColor: 'rgba(59, 130, 246, 0.7)' },
  { name: 'AI伪原创', icon: '/static/icons/ai create/ai original.png', themeColor: '#8B5CF6', lightColor: 'rgba(139, 92, 246, 0.7)' },
  { name: '文案提取', icon: '/static/icons/ai create/extract.png', themeColor: '#10B981', lightColor: 'rgba(16, 185, 129, 0.7)' },
  { name: '视频去字幕', icon: '/static/icons/ai create/remove.png', themeColor: '#F59E0B', lightColor: 'rgba(245, 158, 11, 0.7)' },
  { name: 'AI玩图', icon: '/static/icons/ai create/ai img.png', themeColor: '#EC4899', lightColor: 'rgba(236, 72, 153, 0.7)' }
])

// 工具分类 Mock 数据
const toolCategories = {
  hot: [
    { name: '试卷生成', desc: '智能生成标准化试卷', icon: '/static/icons/ai create/exam.png', themeColor: '#FF6B6B', lightColor: 'rgba(255, 107, 107, 0.35)' },
    { name: '题库生成', desc: '资料一键生成练习题', icon: '/static/icons/ai create/exam.png', themeColor: '#5E7387', lightColor: 'rgba(94, 115, 135, 0.35)' },
    { name: 'PPT生成', desc: '一键生成演示文稿', icon: '/static/icons/ai create/ppt-pdf.png', themeColor: '#FF9F43', lightColor: 'rgba(255, 159, 67, 0.35)' },
    { name: '思维导图', desc: '知识梳理思维导图', icon: '/static/icons/ai create/outline.png', themeColor: '#A55EEA', lightColor: 'rgba(165, 94, 234, 0.35)' },
    { name: '活动图', desc: '业务流程活动图', icon: '/static/icons/ai create/summary.png', themeColor: '#48DBFB', lightColor: 'rgba(72, 219, 251, 0.35)' },
    { name: '架构图', desc: '系统架构可视化', icon: '/static/icons/ai create/plan.png', themeColor: '#5C7A99', lightColor: 'rgba(92, 122, 153, 0.35)' },
    { name: '流程图', desc: '逻辑流程一键绘制', icon: '/static/icons/ai create/video-convert.png', themeColor: '#1DD1A1', lightColor: 'rgba(29, 209, 161, 0.35)' },
    { name: '复习资料', desc: '智能整理复习重点', icon: '/static/icons/ai create/review.png', themeColor: '#FECA57', lightColor: 'rgba(254, 202, 87, 0.35)' },
    { name: 'Python个性化学习', desc: '按基础规划学习路径', icon: '/static/icons/ai create/course.png', themeColor: '#2563EB', lightColor: 'rgba(37, 99, 235, 0.35)' }
  ],
  format: [
    { name: 'PDF转Word', desc: 'PDF转Word快准稳', icon: '/static/icons/ai create/pdf-word.png', themeColor: '#5C7A99', lightColor: 'rgba(92, 122, 153, 0.35)' },
    { name: 'PDF转PPT', desc: '一键PDF转PPT', icon: '/static/icons/ai create/pdf-ppt.png', themeColor: '#5C7A99', lightColor: 'rgba(92, 122, 153, 0.35)' },
    { name: 'PPT转PDF', desc: '一键PPT转PDF', icon: '/static/icons/ai create/ppt-pdf.png', themeColor: '#FF6B6B', lightColor: 'rgba(255, 107, 107, 0.35)' },
    { name: 'PPT转Word', desc: 'PPT转Word快准稳', icon: '/static/icons/ai create/pdf-word.png', themeColor: '#A55EEA', lightColor: 'rgba(165, 94, 234, 0.35)' },
    { name: 'Word转PDF', desc: 'Word转PDF快准稳', icon: '/static/icons/ai create/word-pdf.png', themeColor: '#3B82F6', lightColor: 'rgba(59, 130, 246, 0.35)' },
    { name: 'Word转PPT', desc: 'Word转PPT快准稳', icon: '/static/icons/ai create/pdf-ppt.png', themeColor: '#3B82F6', lightColor: 'rgba(59, 130, 246, 0.35)' },
    { name: 'PDF转Excel', desc: 'PDF秒变Excel', icon: '/static/icons/ai create/pdf-excel.png', themeColor: '#1DD1A1', lightColor: 'rgba(29, 209, 161, 0.35)' },
    { name: 'PPT转图片', desc: '一键PPT秒变图片', icon: '/static/icons/ai create/ppt-img.png', themeColor: '#A55EEA', lightColor: 'rgba(165, 94, 234, 0.35)' },
    { name: 'PDF转图片', desc: '一键PDF秒变图片', icon: '/static/icons/ai create/pdf-img.png', themeColor: '#FF9F43', lightColor: 'rgba(255, 159, 67, 0.35)' },
    { name: '视频格式转换', desc: '一键改变视频格式', icon: '/static/icons/ai create/video-convert.png', themeColor: '#1DD1A1', lightColor: 'rgba(29, 209, 161, 0.35)' }
  ],
  campus: [],
  work: [
    { name: 'PPT大纲', desc: '智能规划PPT要点', icon: '/static/icons/ai create/ppt-outline.png', themeColor: '#EF4444', lightColor: 'rgba(239, 68, 68, 0.35)' },
    { name: '简历制作', desc: '轻松打造吸睛简历', icon: '/static/icons/ai create/resume.png', themeColor: '#5C7A99', lightColor: 'rgba(92, 122, 153, 0.35)' },
    { name: '心得体会', desc: '一键生成心得感悟', icon: '/static/icons/ai create/feeling.png', themeColor: '#A55EEA', lightColor: 'rgba(165, 94, 234, 0.35)' },
    { name: '工作总结', desc: '助力产出优质总结', icon: '/static/icons/ai create/work-summary.png', themeColor: '#FF9F43', lightColor: 'rgba(255, 159, 67, 0.35)' },
    { name: '文本比较', desc: '智能分析文本异同', icon: '/static/icons/ai create/compare.png', themeColor: '#5C7A99', lightColor: 'rgba(92, 122, 153, 0.35)' },
    { name: '长文本写作', desc: '一键生成优质长文', icon: '/static/icons/ai create/long-text.png', themeColor: '#1DD1A1', lightColor: 'rgba(29, 209, 161, 0.35)' },
    { name: '周报日报', desc: '轻松搞定周报撰写', icon: '/static/icons/ai create/weekly.png', themeColor: '#5C7A99', lightColor: 'rgba(92, 122, 153, 0.35)' },
    { name: '影视解说', desc: '助力打造爆款解说', icon: '/static/icons/ai create/movie.png', themeColor: '#A55EEA', lightColor: 'rgba(165, 94, 234, 0.35)' },
    { name: '文章配图', desc: '快速生成图文搭配', icon: '/static/icons/ai create/article-img.png', themeColor: '#FF6B6B', lightColor: 'rgba(255, 107, 107, 0.35)' },
    { name: '合同模板', desc: '一键获取合同模板', icon: '/static/icons/ai create/contract.png', themeColor: '#5C7A99', lightColor: 'rgba(92, 122, 153, 0.35)' }
  ],
  social: [
    { name: '视频灵感', desc: '助力开启灵感源泉', icon: '/static/icons/ai create/video-inspire.png', themeColor: '#FF6B6B', lightColor: 'rgba(255, 107, 107, 0.35)' },
    { name: '短视频文案', desc: '开启爆款视频之路', icon: '/static/icons/ai create/short-video.png', themeColor: '#5C7A99', lightColor: 'rgba(92, 122, 153, 0.35)' },
    { name: '视频标题', desc: '生成吸睛标题', icon: '/static/icons/ai create/video-title.png', themeColor: '#A55EEA', lightColor: 'rgba(165, 94, 234, 0.35)' },
    { name: 'AI写小说', desc: '智能编写奇妙故事', icon: '/static/icons/ai create/novel.png', themeColor: '#FF9F43', lightColor: 'rgba(255, 159, 67, 0.35)' },
    { name: '旅游攻略', desc: '开启畅玩旅行指南', icon: '/static/icons/ai create/travel.png', themeColor: '#1DD1A1', lightColor: 'rgba(29, 209, 161, 0.35)' },
    { name: '视频介绍', desc: '轻松打造亮眼推介', icon: '/static/icons/ai create/video-intro.png', themeColor: '#5C7A99', lightColor: 'rgba(92, 122, 153, 0.35)' },
    { name: '种草文案', desc: '一键生成心动安利', icon: '/static/icons/ai create/recommend.png', themeColor: '#FF6B6B', lightColor: 'rgba(255, 107, 107, 0.35)' },
    { name: '智能翻译', desc: '智能打破语言壁垒', icon: '/static/icons/ai create/translate.png', themeColor: '#A55EEA', lightColor: 'rgba(165, 94, 234, 0.35)' },
    { name: '好评文案', desc: '简单生成诚意好评', icon: '/static/icons/ai create/review.png', themeColor: '#1DD1A1', lightColor: 'rgba(29, 209, 161, 0.35)' },
    { name: '带货标题', desc: '一键生成吸睛标题', icon: '/static/icons/ai create/sales.png', themeColor: '#5C7A99', lightColor: 'rgba(92, 122, 153, 0.35)' }
  ]
}

// 计算属性：当前选中的工具列表
const currentTools = computed(() => {
  const keys = ['hot', 'format', 'campus', 'work', 'social']
  if (activeTab.value === 2) return campusCourses.value
  return toolCategories[keys[activeTab.value]] || []
})

// 方法：切换 Tab
const switchTab = (index) => {
  activeTab.value = index
  if (index === 2) loadCampusCourses()
}

const loadCampusCourses = async () => {
  if (campusLoading.value) return
  campusLoading.value = true
  campusError.value = ''
  try {
    const response = await getCampusCourses()
    const records = response?.data || []
    campusCourses.value = records.map((course, index) => ({
      courseId: course.id,
      name: course.name,
      desc: course.currentChapterTitle
        ? `当前：${course.currentChapterTitle} · ${course.progressPercent || 0}%`
        : `${course.bookTitle || '课程书'} · ${course.progressPercent || 0}%`,
      icon: course.coverUrl || '/static/icons/ai create/course.png',
      themeColor: index % 2 === 0 ? '#2563EB' : '#64748B',
      lightColor: index % 2 === 0 ? 'rgba(37, 99, 235, 0.25)' : 'rgba(100, 116, 139, 0.25)'
    }))
  } catch (error) {
    campusCourses.value = []
    campusError.value = error?.msg || error?.message || '课程加载失败'
  } finally {
    campusLoading.value = false
  }
}

onMounted(() => {
  loadCampusCourses()
})

const goToSmartWriting = () => {
  handleToolTap({ name: '智能写作', desc: 'Deepseek赋能' })
}

const handleToolTap = (tool) => {
  if (tool?.courseId) {
    uni.navigateTo({ url: `/subpackage_learning/campusCourseDetail/campusCourseDetail?courseId=${encodeURIComponent(tool.courseId)}` })
    return
  }
  const destination = resolveAiToolDestination(tool)
  uni.navigateTo({ url: destination })
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

/* ========== 板块样式 ========== */
.section-card {
  background: #FFFFFF;
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 20rpx;
}

.section-card:last-child {
  margin-bottom: 0;
}

.course-state {
  min-height: 220rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
  color: #475569;
  font-size: 27rpx;
}

.course-state--error {
  color: #b45309;
}

.course-state-subtitle,
.course-state-action {
  color: #94a3b8;
  font-size: 23rpx;
}

.course-state-action {
  color: #2563eb;
}

.course-cover-wrapper {
  width: 72rpx;
  height: 92rpx;
  flex-shrink: 0;
  overflow: hidden;
  border-radius: 6rpx 12rpx 12rpx 6rpx;
  background: #e8eef3;
  box-shadow: inset 6rpx 0 rgba(82, 111, 136, 0.2), 0 5rpx 12rpx rgba(30, 41, 59, 0.12);
}

.course-cover-image {
  width: 100%;
  height: 100%;
}

/* ========== 1. 顶部三大核心卡片区 ========== */
.hero-section {
  margin-bottom: 0;
}

.hero-grid {
  display: flex;
  gap: 16rpx;
}

/* 左侧大卡片 */
.hero-main {
  width: 55%;
  height: 300rpx;
  background: linear-gradient(135deg, #6A8CFE 0%, #4D6BFE 100%);
  border-radius: 32rpx;
  padding: 28rpx 24rpx;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  position: relative;
  overflow: hidden;
}

.hero-main-text {
  position: relative;
  z-index: 1;
}

.hero-main-title {
  display: block;
  color: #FFFFFF;
  font-size: 40rpx;
  font-weight: 700;
  line-height: 1.3;
}

.hero-main-subtitle {
  display: block;
  margin-top: 8rpx;
  color: rgba(255, 255, 255, 0.8);
  font-size: 24rpx;
  line-height: 1.4;
}

.hero-main-cta {
  align-self: flex-start;
  padding: 12rpx 28rpx;
  background: #FFFFFF;
  border-radius: 999rpx;
  color: #4D6BFE;
  font-size: 24rpx;
  font-weight: 600;
  position: relative;
  z-index: 1;
}

.hero-main-icon {
  position: absolute;
  right: 12rpx;
  bottom: 12rpx;
  width: 68rpx;
  height: 68rpx;
  z-index: 0;
  opacity: 0.95;
}

/* 右侧小卡片容器 */
.hero-side {
  width: 45%;
  height: 300rpx;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

/* 右侧小卡片通用样式 */
.hero-small {
  height: calc((300rpx - 12rpx) / 2);
  border-radius: 28rpx;
  padding: 24rpx 20rpx;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  position: relative;
  overflow: hidden;
}

.hero-video {
  background: linear-gradient(135deg, #9673FF 0%, #764EFF 100%);
}

.hero-ppt {
  background: linear-gradient(135deg, #FF7B6C 0%, #FF5A4D 100%);
}

.hero-small-text {
  position: relative;
  z-index: 1;
}

.hero-small-title {
  display: block;
  color: #FFFFFF;
  font-size: 30rpx;
  font-weight: 700;
  line-height: 1.3;
}

.hero-small-subtitle {
  display: block;
  margin-top: 6rpx;
  color: rgba(255, 255, 255, 0.85);
  font-size: 22rpx;
  line-height: 1.4;
}

.hero-small-deco {
  display: none;
}

/* ========== 通用样式 ========== */
.section-title {
  margin-bottom: 20rpx;
}

.section-title-text {
  font-size: 30rpx;
  color: #333333;
  font-weight: 700;
  line-height: 1.4;
}

/* ========== 1. 快捷工具区 ========== */
.quick-section {
  margin-bottom: 32rpx;
}

.quick-actions {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.quick-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 20%;
}

.quick-icon-wrap {
  width: 88rpx;
  height: 88rpx;
  border-radius: 20rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.quick-icon {
  width: 44rpx;
  height: 44rpx;
}

.quick-label {
  margin-top: 12rpx;
  font-size: 24rpx;
  color: #333333;
  text-align: center;
}

/* ========== 2. 动态分类导航条 ========== */
.tabs-section {
  margin-bottom: 24rpx;
  background: #FFFFFF;
  border-radius: 16rpx;
  padding: 0 12rpx;
}

.tabs-scroll {
  width: 100%;
  white-space: nowrap;
}

.tabs-inner {
  display: inline-flex;
  align-items: center;
  gap: 48rpx;
  padding: 20rpx 8rpx;
}

.tab-item {
  position: relative;
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  padding-bottom: 8rpx;
  flex-shrink: 0;
}

.tab-text {
  font-size: 30rpx;
  color: #666666;
  font-weight: 400;
  line-height: 1.4;
}

.tab-item--active .tab-text {
  color: #111111;
  font-weight: 700;
}

.tab-indicator {
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 40rpx;
  height: 6rpx;
  background: #3B82F6;
  border-radius: 999rpx;
}

/* ========== 4. 网格工具列表区 ========== */
.tools-section {
  background: #FFFFFF;
  border-radius: 16rpx;
  padding: 0;
}

.tools-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 0;
}

.tool-item {
  display: flex;
  align-items: center;
  padding: 24rpx 20rpx;
  background: transparent;
  border-bottom: 1px solid #F0F0F0;
}

.tool-item:nth-child(odd) {
  border-right: 1px solid #F0F0F0;
}

.tool-item:nth-last-child(-n+2) {
  border-bottom: none;
}

.icon-wrapper {
  position: relative;
  width: 72rpx;
  height: 72rpx;
  margin-right: 20rpx;
  flex-shrink: 0;
}

.icon-wrapper::before {
  content: '';
  position: absolute;
  top: -2rpx;
  left: -2rpx;
  width: 76rpx;
  height: 76rpx;
  background: var(--light-color);
  border-radius: 40% 60% 70% 30% / 40% 50% 60% 50%;
  transform: rotate(-15deg);
}

.icon-inner {
  position: relative;
  z-index: 1;
  width: 72rpx;
  height: 72rpx;
  background: var(--theme-color);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.tool-icon {
  width: 36rpx;
  height: 36rpx;
  filter: saturate(1.2) brightness(1.1);
}

.tool-info {
  display: flex;
  flex-direction: column;
}

.tool-name {
  font-size: 28rpx;
  color: #333333;
  font-weight: 500;
  line-height: 1.4;
  margin-bottom: 4rpx;
}

.tool-desc {
  font-size: 22rpx;
  color: #999999;
  line-height: 1.4;
}
</style>
