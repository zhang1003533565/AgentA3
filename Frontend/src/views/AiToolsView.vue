<script setup>
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import AppTabBar from '../components/AppTabBar.vue'
import { getCampusCourses } from '../api/campusCourse'

const router = useRouter()
const activeTab = ref('hot')
const selectedTool = ref(null)
const campusCourses = ref([])
const campusLoading = ref(false)
const campusError = ref('')

const quickActions = [
  { name: 'AI对话', tone: '问答协作', color: '#3b82f6' },
  { name: 'AI伪原创', tone: '文本改写', color: '#8b5cf6' },
  { name: '文案提取', tone: '重点提炼', color: '#10b981' },
  { name: '视频去字幕', tone: '视频处理', color: '#f59e0b' },
  { name: 'AI玩图', tone: '图片创作', color: '#ec4899' },
]

const tabs = [
  { key: 'hot', label: '热门工具' },
  { key: 'format', label: '格式转换' },
  { key: 'campus', label: '校园课程' },
  { key: 'work', label: '职场创意' },
  { key: 'social', label: '社交媒体' },
]

const toolCategories = {
  hot: [
    { name: '试卷生成', desc: '智能生成标准化试卷', color: '#ef4444' },
    { name: 'PPT生成', desc: '一键生成演示文稿', color: '#f97316' },
    { name: '思维导图', desc: '知识梳理思维导图', color: '#8b5cf6' },
    { name: '架构图', desc: '系统架构可视化', color: '#64748b' },
    { name: '流程图', desc: '逻辑流程一键绘制', color: '#14b8a6' },
    { name: '复习资料', desc: '智能整理复习重点', color: '#eab308' },
  ],
  format: [
    { name: 'PPT转PDF', desc: '一键PPT转PDF', color: '#ef4444' },
    { name: 'PDF转PPT', desc: '一键PDF转PPT', color: '#64748b' },
    { name: 'PDF转Excel', desc: 'PDF秒变Excel', color: '#22c55e' },
    { name: 'PPT转图片', desc: '一键PPT秒变图片', color: '#a855f7' },
    { name: 'PDF转Word', desc: 'PDF转Word快准稳', color: '#0ea5e9' },
    { name: 'Word转PDF', desc: 'Word转PDF快准稳', color: '#2563eb' },
  ],
  campus: [],
  work: [
    { name: 'PPT大纲', desc: '智能规划PPT要点', color: '#ef4444' },
    { name: '简历制作', desc: '轻松打造吸睛简历', color: '#64748b' },
    { name: '心得体会', desc: '一键生成心得感悟', color: '#8b5cf6' },
    { name: '工作总结', desc: '助力产出优质总结', color: '#f59e0b' },
    { name: '文本比较', desc: '智能分析文本异同', color: '#0ea5e9' },
    { name: '长文本写作', desc: '一键生成优质长文', color: '#10b981' },
  ],
  social: [
    { name: '视频灵感', desc: '助力开启灵感源泉', color: '#ef4444' },
    { name: '短视频文案', desc: '开启爆款视频之路', color: '#64748b' },
    { name: '视频标题', desc: '生成吸睛标题', color: '#8b5cf6' },
    { name: 'AI写小说', desc: '智能编写奇妙故事', color: '#f97316' },
    { name: '旅游攻略', desc: '开启畅玩旅行指南', color: '#14b8a6' },
    { name: '智能翻译', desc: '智能打破语言壁垒', color: '#2563eb' },
  ],
}

const currentTools = computed(() => activeTab.value === 'campus'
  ? campusCourses.value
  : toolCategories[activeTab.value] || [])

watch(activeTab, async (tab) => {
  if (tab !== 'campus' || campusLoading.value) return
  campusLoading.value = true
  campusError.value = ''
  try {
    const response = await getCampusCourses()
    campusCourses.value = (response.data || []).map((course, index) => ({
      courseId: course.id,
      name: course.name,
      desc: `${course.currentChapterTitle || course.bookTitle} · ${course.progressPercent || 0}%`,
      color: index % 2 ? '#64748b' : '#2563eb',
    }))
  } catch (error) {
    campusCourses.value = []
    campusError.value = error.message || '课程加载失败'
  } finally {
    campusLoading.value = false
  }
})

function selectTool(tool) {
  if (tool.courseId) {
    router.push(`/courses/${tool.courseId}`)
    return
  }
  const routeByName = {
    '智能写作': 'writing',
    'AI对话': 'writing',
    'AI伪原创': 'writing',
    '文案提取': 'writing',
    'AI玩图': 'image',
    '试卷生成': 'exam',
    'PPT生成': 'presentation',
    'AIPPT': 'presentation',
    '思维导图': 'mind_map',
    '架构图': 'architecture',
    '流程图': 'flowchart',
    '复习资料': 'writing',
  }
  const target = routeByName[tool.name]
  if (target) {
    router.push(`/ai-studio/${target}`)
    return
  }
  selectedTool.value = tool
}
</script>

<template>
  <div class="phone-shell">
    <AppTabBar />

    <main class="page">
      <header class="topbar">
        <div>
          <p class="eyebrow">AI Tools</p>
          <h1>AI工具</h1>
        </div>
      </header>

      <section class="ai-tools-hero card">
        <div class="hero-main">
          <span>智能写作</span>
          <strong>DeepSeek 赋能</strong>
          <button type="button" @click="selectTool({ name: '智能写作', desc: '输入主题后生成文案内容' })">
            立即创作
          </button>
        </div>
        <div class="hero-side">
          <button type="button" @click="selectTool({ name: 'AI视频', desc: '轻松生成视频脚本与素材方案' })">
            <strong>AI视频</strong>
            <span>轻松生成视频</span>
          </button>
          <button type="button" @click="selectTool({ name: 'AIPPT', desc: '智能生成演示大纲与页面方案' })">
            <strong>AIPPT</strong>
            <span>智能PPT神器</span>
          </button>
        </div>
      </section>

      <section class="quick-actions card">
        <button
          v-for="item in quickActions"
          :key="item.name"
          class="quick-action"
          type="button"
          @click="selectTool(item)"
        >
          <span :style="{ background: item.color }">{{ item.name.slice(0, 1) }}</span>
          <strong>{{ item.name }}</strong>
          <em>{{ item.tone }}</em>
        </button>
      </section>

      <section class="tabs-row">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          :class="{ active: activeTab === tab.key }"
          type="button"
          @click="activeTab = tab.key"
        >
          {{ tab.label }}
        </button>
      </section>

      <section class="tools-grid">
        <p v-if="activeTab === 'campus' && campusLoading" class="course-state">正在加载校园课程...</p>
        <p v-else-if="activeTab === 'campus' && campusError" class="course-state">{{ campusError }}</p>
        <p v-else-if="activeTab === 'campus' && !currentTools.length" class="course-state">管理员暂未发布课程</p>
        <button
          v-for="tool in currentTools"
          :key="tool.name"
          class="tool-card card"
          type="button"
          @click="selectTool(tool)"
        >
          <span :style="{ background: tool.color }">{{ tool.name.slice(0, 1) }}</span>
          <div>
            <strong>{{ tool.name }}</strong>
            <em>{{ tool.desc }}</em>
          </div>
        </button>
      </section>

      <section v-if="selectedTool" class="selected-tool card">
        <div>
          <strong>{{ selectedTool.name }}</strong>
          <span>{{ selectedTool.desc || selectedTool.tone || '工具已选中' }}</span>
        </div>
        <button class="ghost-button" type="button" @click="selectedTool = null">关闭</button>
      </section>
    </main>
  </div>
</template>

<style scoped>
.eyebrow {
  margin: 0 0 6px;
  color: #2563eb;
  font-size: 12px;
  font-weight: 800;
  text-transform: uppercase;
}

.ai-tools-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(0, 0.9fr);
  gap: 12px;
  padding: 12px;
}

.hero-main,
.hero-side button {
  border-radius: 8px;
  color: #ffffff;
}

.hero-main {
  display: grid;
  align-content: space-between;
  min-height: 178px;
  padding: 18px;
  background: linear-gradient(135deg, #2563eb, #0f766e);
}

.hero-main span,
.hero-side span {
  opacity: 0.86;
  font-size: 13px;
}

.hero-main strong {
  font-size: 24px;
  line-height: 1.2;
}

.hero-main button {
  width: max-content;
  min-height: 34px;
  padding: 0 12px;
  border-radius: 8px;
  color: #2563eb;
  background: #ffffff;
  font-weight: 800;
}

.hero-side {
  display: grid;
  gap: 12px;
}

.hero-side button {
  display: grid;
  gap: 6px;
  align-content: center;
  padding: 14px;
  text-align: left;
}

.hero-side button:first-child {
  background: #f97316;
}

.hero-side button:last-child {
  background: #8b5cf6;
}

.quick-actions {
  display: grid;
  grid-template-columns: repeat(5, minmax(76px, 1fr));
  gap: 8px;
  margin-top: 16px;
  padding: 12px;
  overflow-x: auto;
}

.quick-action,
.tool-card {
  text-align: left;
  background: #ffffff;
}

.quick-action {
  display: grid;
  gap: 7px;
  justify-items: center;
  min-width: 76px;
  padding: 10px 6px;
  border-radius: 8px;
}

.quick-action span,
.tool-card > span {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 8px;
  color: #ffffff;
  font-weight: 900;
}

.quick-action strong {
  color: #111827;
  font-size: 13px;
}

.quick-action em,
.tool-card em,
.selected-tool span {
  color: #64748b;
  font-size: 12px;
  font-style: normal;
}

.tabs-row {
  display: flex;
  gap: 8px;
  margin-top: 16px;
  overflow-x: auto;
  scrollbar-width: none;
}

.tabs-row::-webkit-scrollbar {
  display: none;
}

.tabs-row button {
  flex: 0 0 auto;
  min-height: 36px;
  padding: 0 12px;
  border-radius: 999px;
  color: #64748b;
  background: #ffffff;
  font-weight: 800;
}

.tabs-row button.active {
  color: #ffffff;
  background: #2563eb;
}

.tools-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 16px;
}

.course-state {
  grid-column: 1 / -1;
  padding: 40px 16px;
  color: #64748b;
  text-align: center;
}

.tool-card {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 88px;
  padding: 14px;
}

.tool-card div {
  display: grid;
  gap: 5px;
  min-width: 0;
}

.tool-card strong {
  color: #111827;
  font-size: 15px;
}

.selected-tool {
  position: sticky;
  bottom: 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 16px;
  padding: 14px;
}

.selected-tool div {
  display: grid;
  gap: 4px;
}

.selected-tool strong {
  color: #111827;
}

@media (max-width: 380px) {
  .ai-tools-hero {
    grid-template-columns: 1fr;
  }

  .tools-grid {
    grid-template-columns: 1fr;
  }
}
</style>
