<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import examHero from '../assets/ai-tools/exam-hero.png'
import campusIllustrations from '../assets/ai-tools/campus-illustrations-strip.png'
import toolIllustrations from '../assets/ai-tools/tool-illustrations-strip.png'
import { getCampusCourses } from '../api/campusCourse'
import AppTabBar from '../components/AppTabBar.vue'

const router = useRouter()

const heroIndex = ref(0)
const activeCategory = ref('hot')
const toolIndex = ref(1)
const toolPosition = ref(0)
const campusCourses = ref([])
const campusLoading = ref(false)
const carouselPaused = ref(false)
const dragStartX = ref(null)
let autoplayTimer
let revealObserver

const heroSlides = [
  {
    key: 'exam',
    eyebrow: 'AI SMART EXAM',
    title: '试卷生成',
    subtitle: '智能生成各学科标准化试卷',
    features: ['多学科支持', '题型智能匹配', '一键导出打印'],
    color: '#ff3943',
    image: examHero,
    route: 'exam',
  },
  {
    key: 'mind',
    eyebrow: 'KNOWLEDGE MAPPING',
    title: '思维导图',
    subtitle: '把复杂知识整理成清晰结构',
    features: ['章节知识梳理', '层级关系清晰', '支持继续编辑'],
    color: '#6c43d9',
    image: toolIllustrations,
    artSet: 'core',
    art: 4,
    route: 'mind_map',
  },
  {
    key: 'ppt',
    eyebrow: 'AI PRESENTATION',
    title: 'PPT生成',
    subtitle: '从主题到演示文稿一站完成',
    features: ['智能规划大纲', '自动生成逐页内容', '快速导出课件'],
    color: '#ff9900',
    image: toolIllustrations,
    artSet: 'core',
    art: 3,
    route: 'presentation',
  },
  {
    key: 'image',
    eyebrow: 'TEXT TO IMAGE',
    title: 'AI文生图',
    subtitle: '把文字描述转化为视觉作品',
    features: ['自然语言描述', '多种画面风格', '生成结果预览'],
    color: '#7546d9',
    image: toolIllustrations,
    artSet: 'core',
    art: 1,
    route: 'image',
  },
  {
    key: 'writing',
    eyebrow: 'AI WRITING',
    title: '智能写作',
    subtitle: '快速生成校园常用文稿',
    features: ['多种表达语气', '目标字数控制', '支持继续润色'],
    color: '#2975df',
    image: toolIllustrations,
    artSet: 'core',
    art: 0,
    route: 'writing',
  },
  {
    key: 'chat',
    eyebrow: 'CAMPUS COPILOT',
    title: 'AI对话',
    subtitle: '上传资料，即问即答',
    features: ['多资源理解', '图片智能识别', '校园服务协作'],
    color: '#1768e6',
    image: campusIllustrations,
    artSet: 'service',
    art: 0,
    route: 'writing',
  },
]

const categories = [
  { key: 'hot', label: '热门工具', icon: 'flame', color: '#ff343f' },
  { key: 'creation', label: 'AI创作', icon: 'bolt', color: '#7546d9' },
  { key: 'diagram', label: '图表设计', icon: 'campus', color: '#18a37d' },
  { key: 'learning', label: '学习测评', icon: 'book', color: '#f59e0b' },
  { key: 'campus', label: '校园求职', icon: 'briefcase', color: '#1768e6' },
  { key: 'convert', label: '格式转换', icon: 'convert', color: '#315f8c' },
]

const baseTools = [
  { name: '校园 AI 助手', desc: '多资源上传、识图与校园智能问答', category: ['hot', 'campus'], artSet: 'service', art: 0, route: '/ai', accent: '#1768e6' },
  { name: '智能写作', desc: '生成校园常用文稿并支持润色', category: ['hot', 'creation'], artSet: 'core', art: 0, route: '/ai-studio/writing', accent: '#4077de' },
  { name: 'AI 文生图', desc: '根据文字描述生成图片', category: ['hot', 'creation'], artSet: 'core', art: 1, route: '/ai-studio/image', accent: '#7546d9' },
  { name: '试卷生成', desc: '生成结构化练习与标准试卷', category: ['hot', 'learning'], artSet: 'core', art: 2, route: '/ai-studio/exam', accent: '#ff3943' },
  { name: 'PPT 生成', desc: '生成演示大纲与文稿资源', category: ['hot', 'creation', 'learning'], artSet: 'core', art: 3, route: '/ai-studio/presentation', accent: '#ff9900' },
  { name: '思维导图', desc: '梳理主题与课程知识结构', category: ['hot', 'diagram', 'learning'], artSet: 'core', art: 4, route: '/ai-studio/mind_map', accent: '#7546d9' },
  { name: '架构图', desc: '生成系统架构可视化资源', category: ['diagram'], artSet: 'core', art: 5, route: '/ai-studio/architecture', accent: '#3b82f6' },
  { name: '流程图', desc: '生成清晰的业务与逻辑流程', category: ['diagram'], artSet: 'core', art: 6, route: '/ai-studio/flowchart', accent: '#ee5eaa' },
  { name: '知识图谱', desc: '查看课程知识关系与学习路径', category: ['diagram', 'learning'], artSet: 'core', art: 4, route: '/learning/knowledge-graph', accent: '#18a37d' },
  { name: '校园地图', desc: '查询校园地点、设施和导航', category: ['campus'], artSet: 'service', art: 1, route: '/map', accent: '#56aa1b' },
  { name: 'AI 简历', desc: '智能创建、解析与优化简历', category: ['hot', 'campus'], artSet: 'service', art: 2, route: '/resume', accent: '#1768e6' },
  { name: 'PDF → Word', desc: 'PDF 转 Word 文档', category: ['convert'], artSet: 'core', art: 0, route: '/convert?type=pdf_to_docx', accent: '#5C7A99' },
  { name: 'PPT → Word', desc: 'PPT 转 Word 文档', category: ['convert'], artSet: 'core', art: 1, route: '/convert?type=ppt_to_docx', accent: '#6B9B7A' },
  { name: 'Word → PDF', desc: 'Word 转 PDF 文档', category: ['convert'], artSet: 'core', art: 2, route: '/convert?type=docx_to_pdf', accent: '#B89B7A' },
  { name: 'PDF → PPT', desc: 'PDF 转 PPT 演示文稿', category: ['convert'], artSet: 'core', art: 3, route: '/convert?type=pdf_to_ppt', accent: '#8B7AB8' },
  { name: 'PPT → PDF', desc: 'PPT 转 PDF 文档', category: ['convert'], artSet: 'core', art: 4, route: '/convert?type=ppt_to_pdf', accent: '#7A9BB8' },
  { name: 'Word → PPT', desc: 'Word 转 PPT 演示文稿', category: ['convert'], artSet: 'core', art: 5, route: '/convert?type=docx_to_ppt', accent: '#A67B7B' },
]

const displayedTools = computed(() => {
  if (activeCategory.value === 'campus') {
    const courseTools = campusCourses.value.map((course, index) => ({
      name: course.name,
      desc: `${course.currentChapterTitle || course.bookTitle} · ${course.progressPercent || 0}%`,
      artSet: 'core',
      art: index % 7,
      route: `/courses/${course.id}`,
      accent: '#56aa1b',
    }))
    return [...baseTools.filter((tool) => tool.category.includes('campus')), ...courseTools]
  }
  return baseTools.filter((tool) => tool.category.includes(activeCategory.value))
})

const carouselTools = computed(() => {
  const tools = displayedTools.value
  if (!tools.length) return []
  return Array.from({ length: 31 }, (_, copyIndex) => (
    tools.map((tool, sourceIndex) => ({
      ...tool,
      sourceIndex,
      loopKey: `${copyIndex}-${sourceIndex}-${tool.name}`,
    }))
  )).flat()
})

function circularOffset(index, active, total) {
  let offset = index - active
  if (offset > total / 2) offset -= total
  if (offset < -total / 2) offset += total
  return offset
}

function heroClass(index) {
  const offset = circularOffset(index, heroIndex.value, heroSlides.length)
  return {
    active: offset === 0,
    previous: offset === -1,
    'previous-far': offset === -2,
    next: offset === 1,
    'next-far': offset === 2,
    hidden: Math.abs(offset) > 2,
  }
}

function changeHero(direction) {
  heroIndex.value = (heroIndex.value + direction + heroSlides.length) % heroSlides.length
  restartAutoplay()
}

function startDrag(event) {
  dragStartX.value = event.clientX ?? event.touches?.[0]?.clientX ?? null
}

function endDrag(event) {
  if (dragStartX.value === null) return
  const endX = event.clientX ?? event.changedTouches?.[0]?.clientX ?? dragStartX.value
  const distance = endX - dragStartX.value
  dragStartX.value = null
  if (Math.abs(distance) < 45) return
  changeHero(distance > 0 ? -1 : 1)
}

function restartAutoplay() {
  window.clearInterval(autoplayTimer)
  autoplayTimer = window.setInterval(() => {
    if (!carouselPaused.value) changeHero(1)
  }, 5200)
}

function setCategory(key) {
  activeCategory.value = key
}

function moveTools(direction) {
  const total = displayedTools.value.length
  if (!total) return
  toolPosition.value += direction
  toolIndex.value = (toolPosition.value % total + total) % total
}

function selectCarouselTool(tool, position) {
  if (position === toolPosition.value) {
    openTool(tool)
    return
  }
  toolPosition.value = position
  toolIndex.value = tool.sourceIndex
}

function selectToolDot(index) {
  const total = displayedTools.value.length
  if (!total) return
  toolIndex.value = index
  toolPosition.value = total * 15 + index
}

function openTool(tool) {
  if (tool.route?.startsWith('/')) {
    router.push(tool.route)
    return
  }
  router.push(`/ai-studio/${tool.route || 'writing'}`)
}

function openHero(slide) {
  router.push(`/ai-studio/${slide.route}`)
}

function spriteStyle(artSet, index) {
  const isService = artSet === 'service'
  return {
    backgroundImage: `url(${isService ? campusIllustrations : toolIllustrations})`,
    backgroundSize: `${isService ? 300 : 700}% 100%`,
    backgroundPosition: `${index * (isService ? 50 : (100 / 6))}% center`,
  }
}

function heroArtStyle(slide) {
  if (!slide.artSet) return { backgroundImage: `url(${slide.image})` }
  return spriteStyle(slide.artSet, slide.art)
}

async function loadCampusCourses() {
  if (campusLoading.value || campusCourses.value.length) return
  campusLoading.value = true
  try {
    const response = await getCampusCourses()
    campusCourses.value = response.data || []
  } catch {
    campusCourses.value = []
  } finally {
    campusLoading.value = false
  }
}

watch(activeCategory, (category) => {
  if (category === 'campus') loadCampusCourses()
})

watch(displayedTools, (tools) => {
  toolIndex.value = tools.length > 1 ? 1 : 0
  toolPosition.value = tools.length * 15 + toolIndex.value
}, { immediate: true })

onMounted(() => {
  restartAutoplay()
  revealObserver = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      if (entry.isIntersecting) entry.target.classList.add('is-visible')
    })
  }, { threshold: 0.12 })
  document.querySelectorAll('.reveal-section').forEach((element) => revealObserver.observe(element))
})

onBeforeUnmount(() => {
  window.clearInterval(autoplayTimer)
  revealObserver?.disconnect()
})
</script>

<template>
  <div class="ai-tools-page">
    <AppTabBar />

    <main>
      <section
        class="hero-stage reveal-section"
        aria-label="AI工具推荐"
        @mouseenter="carouselPaused = true"
        @mouseleave="carouselPaused = false"
        @pointerdown="startDrag"
        @pointerup="endDrag"
        @pointercancel="dragStartX = null"
      >
        <button class="carousel-arrow carousel-arrow--left" type="button" aria-label="上一个工具" @click="changeHero(-1)">
          <svg viewBox="0 0 24 24"><path d="m15 5-7 7 7 7" /></svg>
        </button>

        <article
          v-for="(slide, index) in heroSlides"
          :key="slide.key"
          class="hero-slide"
          :class="heroClass(index)"
          :style="{ '--hero-color': slide.color }"
        >
          <span class="hero-slide__image" :style="heroArtStyle(slide)" aria-hidden="true"></span>
          <div class="hero-slide__veil"></div>
          <div class="hero-slide__content">
            <span>{{ slide.eyebrow }}</span>
            <h1>{{ slide.title }}</h1>
            <p>{{ slide.subtitle }}</p>
            <ul>
              <li v-for="feature in slide.features" :key="feature">
                <svg viewBox="0 0 20 20"><path d="m5.5 10 3 3 6-7" /></svg>
                {{ feature }}
              </li>
            </ul>
            <button type="button" @click="openHero(slide)">
              立即使用
              <svg viewBox="0 0 24 24"><path d="m9 5 7 7-7 7" /></svg>
            </button>
          </div>
        </article>

        <button class="carousel-arrow carousel-arrow--right" type="button" aria-label="下一个工具" @click="changeHero(1)">
          <svg viewBox="0 0 24 24"><path d="m9 5 7 7-7 7" /></svg>
        </button>

        <div class="hero-dots" aria-label="轮播位置">
          <button
            v-for="(slide, index) in heroSlides"
            :key="slide.key"
            type="button"
            :class="{ active: heroIndex === index }"
            :aria-label="`切换到${slide.title}`"
            @click="heroIndex = index; restartAutoplay()"
          ></button>
        </div>
      </section>

      <section class="category-dock reveal-section">
        <button
          v-for="category in categories"
          :key="category.key"
          type="button"
          :class="{ active: activeCategory === category.key }"
          :style="{ '--category-color': category.color }"
          @click="setCategory(category.key)"
        >
          <span class="category-icon">
            <svg v-if="category.icon === 'flame'" viewBox="0 0 24 24"><path d="M13 2c1 5-3 5-1 9 1.6-1.2 2.4-2.5 2.2-4.2C18 9.2 20 12 19 16a7 7 0 0 1-14 0c0-3.6 2-6.8 5.5-9-.4 2.2.2 3.8 1.5 4.8" /></svg>
            <svg v-else-if="category.icon === 'book'" viewBox="0 0 24 24"><path d="M3 5.5A3.5 3.5 0 0 1 6.5 2H11v17H6.5A3.5 3.5 0 0 0 3 22V5.5Zm18 0A3.5 3.5 0 0 0 17.5 2H13v17h4.5A3.5 3.5 0 0 1 21 22V5.5Z" /></svg>
            <svg v-else-if="category.icon === 'campus'" viewBox="0 0 24 24"><path d="M3 21V9l5-3v15m8 0V6l5 3v12M8 9h8M6 12h1m-1 3h1m-1 3h1m10-6h1m-1 3h1m-1 3h1M10 13h4v8h-4z" /></svg>
            <svg v-else-if="category.icon === 'briefcase'" viewBox="0 0 24 24"><path d="M8 7V5a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2M3 9h18v11H3zM3 13c5 2 13 2 18 0M10 13h4" /></svg>
            <svg v-else-if="category.icon === 'convert'" viewBox="0 0 24 24"><path d="M4 8h13m-3-3 3 3-3 3M20 16H7m3 3-3-3 3-3" /></svg>
            <svg v-else viewBox="0 0 24 24"><path d="m13 2-8 12h6l-1 8 9-13h-6z" /></svg>
          </span>
          <strong>{{ category.label }}</strong>
        </button>
      </section>

      <section class="tools-section reveal-section">
        <header class="section-title">
          <div>
            <p>{{ categories.find((item) => item.key === activeCategory)?.label }}</p>
            <h2>找到适合你的智能工具</h2>
          </div>
          <button type="button" @click="router.push('/ai-studio')">
            查看全部
            <svg viewBox="0 0 24 24"><path d="m9 5 7 7-7 7" /></svg>
          </button>
        </header>

        <div class="tool-carousel">
          <button
            class="tool-arrow tool-arrow--left"
            type="button"
            aria-label="上一个工具"
            @click="moveTools(-1)"
          >
            <svg viewBox="0 0 24 24"><path d="m15 5-7 7 7 7" /></svg>
          </button>

          <div v-if="campusLoading" class="tools-loading">正在加载校园课程...</div>
          <div v-else class="tool-window">
            <div
              class="tool-track"
              :style="{ '--tool-position': toolPosition }"
            >
              <button
                v-for="(tool, index) in carouselTools"
                :key="`${activeCategory}-${tool.loopKey}`"
                class="tool-poster"
                :class="{ active: toolPosition === index }"
                :style="{ '--accent': tool.accent }"
                type="button"
                @click="selectCarouselTool(tool, index)"
              >
                <span class="tool-poster__art" :style="spriteStyle(tool.artSet, tool.art)"></span>
                <span class="tool-poster__content">
                  <strong>{{ tool.name }}</strong>
                  <em>{{ tool.desc }}</em>
                  <span v-if="toolPosition === index" class="tool-poster__action">
                    立即使用
                    <svg viewBox="0 0 24 24"><path d="m9 5 7 7-7 7" /></svg>
                  </span>
                </span>
              </button>
            </div>
          </div>

          <button
            class="tool-arrow tool-arrow--right"
            type="button"
            aria-label="下一个工具"
            @click="moveTools(1)"
          >
            <svg viewBox="0 0 24 24"><path d="m9 5 7 7-7 7" /></svg>
          </button>
        </div>

        <div class="tool-dots">
          <button
            v-for="(_, index) in displayedTools"
            :key="index"
            type="button"
            :class="{ active: toolIndex === index }"
            :aria-label="`切换到第${index + 1}个工具`"
            @click="selectToolDot(index)"
          ></button>
        </div>

        <p class="drag-hint">
          <svg viewBox="0 0 24 24"><path d="M8 11V7a2 2 0 0 1 4 0v3-5a2 2 0 0 1 4 0v5-2a2 2 0 0 1 4 0v6c0 5-3 8-8 8-3 0-5-1.5-7-4l-2-3a2 2 0 0 1 3-2l2 2" /></svg>
          点击卡片或左右按钮探索更多工具
        </p>
      </section>
    </main>
  </div>
</template>

<style scoped>
.ai-tools-page {
  --ink: #17191f;
  min-height: 100vh;
  padding-top: 60px;
  overflow: hidden;
  color: var(--ink);
  background:
    radial-gradient(circle at 8% 20%, rgba(207, 232, 255, 0.38), transparent 28%),
    radial-gradient(circle at 95% 30%, rgba(255, 229, 190, 0.34), transparent 25%),
    #fffdfa;
  font-family: Inter, "PingFang SC", "Microsoft YaHei", sans-serif;
}

button,
a {
  -webkit-tap-highlight-color: transparent;
}

svg {
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.9;
}

.tools-header {
  position: relative;
  z-index: 30;
  height: 76px;
  border-bottom: 1px solid rgba(16, 24, 40, 0.06);
  background: rgba(255, 254, 251, 0.92);
  backdrop-filter: blur(18px);
}

.tools-header__inner {
  display: flex;
  align-items: center;
  width: min(1440px, calc(100% - 48px));
  height: 100%;
  margin: 0 auto;
  gap: 28px;
}

.campus-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  color: inherit;
  text-decoration: none;
  white-space: nowrap;
}

.campus-brand__mark {
  display: grid;
  place-items: center;
  width: 46px;
  height: 46px;
  border-radius: 12px;
  color: #fff;
  background: linear-gradient(145deg, #1068f4, #1846b9);
  box-shadow: 0 10px 22px rgba(21, 94, 239, 0.24);
  font-size: 20px;
  font-weight: 900;
}

.campus-brand > span:last-child {
  display: grid;
  gap: 2px;
}

.campus-brand strong {
  font-size: 20px;
  letter-spacing: 0.02em;
}

.campus-brand em {
  color: #5f636e;
  font-size: 11px;
  font-style: normal;
  letter-spacing: 0.22em;
}

.tools-nav {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
  gap: clamp(12px, 2.2vw, 36px);
}

.tools-nav a {
  position: relative;
  display: grid;
  place-items: center;
  height: 76px;
  color: #252831;
  font-size: 14px;
  font-weight: 650;
  text-decoration: none;
  white-space: nowrap;
}

.tools-nav a::after {
  position: absolute;
  right: 4px;
  bottom: 9px;
  left: 4px;
  height: 4px;
  border-radius: 99px;
  background: #1758db;
  content: "";
  opacity: 0;
  transform: scaleX(0.3);
  transition: 0.25s ease;
}

.tools-nav a:hover,
.tools-nav a.active {
  color: #174fc2;
}

.tools-nav a.active::after {
  opacity: 1;
  transform: scaleX(1);
}

.user-entry {
  display: flex;
  align-items: center;
  gap: 9px;
  color: #252831;
  font-size: 13px;
  text-decoration: none;
  white-space: nowrap;
}

.user-entry > svg {
  width: 17px;
  height: 17px;
}

.user-avatar {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border: 2px solid #fff;
  border-radius: 50%;
  overflow: hidden;
  color: #174fc2;
  background: #e9f1ff;
  box-shadow: 0 3px 10px rgba(27, 42, 72, 0.15);
  font-weight: 800;
}

.user-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.hero-stage {
  position: relative;
  width: 100%;
  height: clamp(340px, 31vw, 455px);
  margin-top: 14px;
  touch-action: pan-y;
  user-select: none;
}

.hero-stage::after {
  position: absolute;
  right: 0;
  bottom: 14px;
  left: 0;
  height: 54px;
  background: linear-gradient(180deg, transparent, rgba(29, 53, 100, 0.08));
  clip-path: polygon(0 0, 50% 68%, 100% 0, 100% 100%, 0 100%);
  content: "";
  pointer-events: none;
}

.hero-slide {
  position: absolute;
  top: 0;
  left: 50%;
  width: min(760px, 32vw);
  height: calc(100% - 40px);
  border-radius: 30px;
  overflow: hidden;
  background: var(--hero-color);
  box-shadow: 0 22px 54px rgba(28, 33, 48, 0.14);
  opacity: 0;
  transform-origin: center;
  transform: translateX(-50%) scale(0.84);
  transition:
    transform 0.72s cubic-bezier(0.22, 0.82, 0.22, 1),
    opacity 0.45s ease,
    filter 0.45s ease;
}

.hero-slide.active {
  z-index: 4;
  opacity: 1;
  transform: translateX(-50%) scale(1);
}

.hero-slide.previous,
.hero-slide.next {
  z-index: 3;
  opacity: 0.9;
  filter: saturate(0.88) brightness(0.98);
}

.hero-slide.previous {
  transform: translateX(calc(-50% - min(22vw, 540px))) scale(0.66) rotate(-2.5deg);
}

.hero-slide.next {
  transform: translateX(calc(-50% + min(22vw, 540px))) scale(0.66) rotate(2.5deg);
}

.hero-slide.previous-far,
.hero-slide.next-far {
  z-index: 1;
  opacity: 0.54;
  filter: saturate(0.62) brightness(0.94);
}

.hero-slide.previous-far {
  transform: translateX(calc(-50% - min(36vw, 760px))) scale(0.38) rotate(-5deg);
}

.hero-slide.next-far {
  transform: translateX(calc(-50% + min(36vw, 760px))) scale(0.38) rotate(5deg);
}

.hero-slide.hidden {
  pointer-events: none;
  transform: translateX(-50%) scale(0.72);
}

.hero-slide__image {
  position: absolute;
  inset: 0;
  display: block;
  width: 100%;
  height: 100%;
  background-position: center;
  background-repeat: no-repeat;
  background-size: cover;
  transition: transform 4.5s ease;
}

.hero-slide.active .hero-slide__image {
  transform: scale(1.035);
}

.hero-slide__veil {
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, var(--hero-color) 0%, color-mix(in srgb, var(--hero-color) 90%, transparent) 38%, transparent 75%);
}

.hero-slide__content {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  width: 52%;
  height: 100%;
  padding: clamp(30px, 3.2vw, 52px);
  color: #fff;
}

.hero-slide__content > span {
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.18em;
  opacity: 0.78;
}

.hero-slide h1 {
  margin: 12px 0 5px;
  font-size: clamp(30px, 2.9vw, 58px);
  line-height: 1.05;
  letter-spacing: -0.03em;
}

.hero-slide p {
  margin: 0;
  font-size: clamp(15px, 1.4vw, 19px);
  font-weight: 600;
}

.hero-slide ul {
  display: grid;
  gap: 7px;
  margin: 22px 0 18px;
  padding: 0;
  list-style: none;
}

.hero-slide li {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 650;
}

.hero-slide li svg {
  width: 18px;
  height: 18px;
  padding: 3px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.24);
}

.hero-slide__content > button {
  display: flex;
  align-items: center;
  gap: 7px;
  min-height: 38px;
  margin-top: auto;
  padding: 0 16px;
  border-radius: 999px;
  color: var(--hero-color);
  background: #fff;
  box-shadow: 0 8px 20px rgba(17, 22, 39, 0.16);
  font-size: 13px;
  font-weight: 800;
  transition: 0.2s ease;
}

.hero-slide__content > button:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 24px rgba(17, 22, 39, 0.2);
}

.hero-slide__content > button svg,
.section-title button svg,
.tool-poster__action svg {
  width: 16px;
  height: 16px;
}

.carousel-arrow,
.tool-arrow {
  position: absolute;
  z-index: 12;
  display: grid;
  place-items: center;
  border: 1px solid rgba(22, 31, 49, 0.1);
  border-radius: 50%;
  color: #252a34;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 8px 22px rgba(23, 32, 52, 0.15);
  backdrop-filter: blur(8px);
  transition: 0.22s ease;
}

.carousel-arrow {
  top: 43%;
  width: 48px;
  height: 48px;
}

.carousel-arrow:hover,
.tool-arrow:hover:not(:disabled) {
  color: #1758db;
  transform: scale(1.08);
}

.carousel-arrow svg,
.tool-arrow svg {
  width: 24px;
  height: 24px;
}

.carousel-arrow--left {
  left: 20px;
}

.carousel-arrow--right {
  right: 20px;
}

.hero-dots {
  position: absolute;
  z-index: 14;
  right: 0;
  bottom: 9px;
  left: 0;
  display: flex;
  justify-content: center;
  gap: 8px;
}

.hero-dots button,
.tool-dots button {
  width: 8px;
  height: 8px;
  padding: 0;
  border-radius: 99px;
  background: #c7c8cc;
  transition: 0.25s ease;
}

.hero-dots button.active,
.tool-dots button.active {
  width: 22px;
  background: #ef3441;
}

.category-dock {
  position: relative;
  z-index: 16;
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  width: min(1080px, calc(100% - 56px));
  min-height: 72px;
  margin: -32px auto 0;
  border: 1px solid rgba(21, 31, 50, 0.08);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 18px 34px rgba(25, 36, 62, 0.16);
  backdrop-filter: blur(18px);
}

.category-dock button {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  min-width: 0;
  border-radius: 999px;
  color: #30343c;
  background: transparent;
  font-size: 17px;
  transition: 0.3s ease;
}

.category-dock button + button::before {
  position: absolute;
  left: 0;
  width: 1px;
  height: 28px;
  background: #e7e8ec;
  content: "";
}

.category-dock button.active {
  color: #fff;
  background: var(--category-color);
  box-shadow: 0 10px 25px color-mix(in srgb, var(--category-color) 34%, transparent);
  transform: scale(1.025);
}

.category-dock button.active::before,
.category-dock button.active + button::before {
  opacity: 0;
}

.category-icon {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  color: var(--category-color);
}

.category-dock button.active .category-icon {
  color: #fff;
}

.category-icon svg {
  width: 30px;
  height: 30px;
  stroke-width: 2;
}

.tools-section {
  padding: 62px 0 28px;
}

.section-title {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  width: min(1420px, calc(100% - 48px));
  margin: 0 auto 24px;
}

.section-title p {
  position: relative;
  width: max-content;
  margin: 0 0 7px;
  font-size: 22px;
  font-weight: 900;
}

.section-title p::after {
  position: absolute;
  bottom: -8px;
  left: 0;
  width: 34px;
  height: 4px;
  border-radius: 999px;
  background: #ef3441;
  content: "";
}

.section-title h2 {
  margin: 17px 0 0;
  color: #858994;
  font-size: 13px;
  font-weight: 500;
}

.section-title > button {
  display: flex;
  align-items: center;
  gap: 7px;
  min-height: 38px;
  padding: 0 16px;
  border: 1px solid #dfe1e6;
  border-radius: 999px;
  color: #30343c;
  background: #fff;
  font-size: 13px;
  font-weight: 700;
}

.tool-carousel {
  position: relative;
  min-height: 390px;
}

.tool-window {
  position: relative;
  width: 100%;
  min-height: 390px;
  overflow: hidden;
}

.tool-track {
  --card-width: 246px;
  --card-gap: 18px;
  position: absolute;
  top: 25px;
  left: 50%;
  display: flex;
  gap: var(--card-gap);
  width: max-content;
  transform: translateX(calc(-1 * var(--tool-position) * (var(--card-width) + var(--card-gap)) - var(--card-width) / 2));
  transition: transform 0.46s cubic-bezier(0.22, 0.82, 0.22, 1);
}

.tool-poster {
  display: flex;
  flex: 0 0 var(--card-width);
  flex-direction: column;
  width: var(--card-width);
  height: 328px;
  padding: 0;
  border: 1px solid rgba(19, 34, 59, 0.08);
  border-radius: 22px;
  overflow: hidden;
  color: #151820;
  background: #fff;
  box-shadow: 0 15px 32px rgba(23, 37, 63, 0.13);
  text-align: left;
  opacity: 0.78;
  transform: translateY(16px) scale(0.94);
  transition:
    transform 0.5s cubic-bezier(0.22, 0.82, 0.22, 1),
    opacity 0.35s ease,
    box-shadow 0.35s ease;
}

.tool-poster:hover {
  opacity: 1;
  transform: translateY(4px) scale(0.98);
}

.tool-poster.active {
  z-index: 3;
  opacity: 1;
  transform: translateY(-4px) scale(1.08);
  box-shadow: 0 25px 48px color-mix(in srgb, var(--accent) 20%, rgba(23, 37, 63, 0.18));
}

.tool-poster__art {
  display: block;
  width: 100%;
  height: 214px;
  background-repeat: no-repeat;
}

.tool-poster__content {
  position: relative;
  display: grid;
  gap: 5px;
  flex: 1;
  align-content: start;
  padding: 16px 18px;
  border-top: 4px solid color-mix(in srgb, var(--accent) 85%, #fff);
  background: linear-gradient(180deg, #fff, color-mix(in srgb, var(--accent) 5%, #fff));
}

.tool-poster__content strong {
  font-size: 20px;
  line-height: 1.25;
}

.tool-poster__content em {
  color: #6d717c;
  font-size: 12px;
  font-style: normal;
}

.tool-poster__action {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  width: 112px;
  min-height: 32px;
  margin-top: 8px;
  border-radius: 999px;
  color: #fff;
  background: var(--accent);
  font-size: 12px;
  font-weight: 800;
  animation: action-in 0.35s ease both;
}

.tool-arrow {
  top: 44%;
  width: 44px;
  height: 44px;
}

.tool-arrow--left {
  left: 20px;
}

.tool-arrow--right {
  right: 20px;
}

.tools-loading {
  display: grid;
  min-height: 340px;
  place-items: center;
  color: #727782;
}

.tool-dots {
  display: flex;
  justify-content: center;
  gap: 8px;
  margin-top: 2px;
}

.drag-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  width: max-content;
  margin: 18px auto 0;
  padding: 8px 14px;
  border: 1px solid #e1e3e8;
  border-radius: 999px;
  color: #858994;
  background: rgba(255, 255, 255, 0.7);
  font-size: 11px;
}

.drag-hint svg {
  width: 16px;
  height: 16px;
}

.reveal-section {
  opacity: 0;
  transform: translateY(26px);
  transition:
    opacity 0.7s ease,
    transform 0.7s cubic-bezier(0.22, 0.82, 0.22, 1);
}

.reveal-section.is-visible {
  opacity: 1;
  transform: translateY(0);
}

@keyframes action-in {
  from { opacity: 0; transform: translateY(6px); }
  to { opacity: 1; transform: translateY(0); }
}

@media (max-width: 1120px) {
  .tools-header__inner {
    gap: 16px;
  }

  .tools-nav {
    justify-content: flex-start;
    overflow-x: auto;
    scrollbar-width: none;
  }

  .user-entry > span:not(.user-avatar) {
    display: none;
  }

  .hero-slide {
    width: 38vw;
  }

  .hero-slide.previous {
    transform: translateX(calc(-50% - 25vw)) scale(0.62);
  }

  .hero-slide.next {
    transform: translateX(calc(-50% + 25vw)) scale(0.62);
  }

  .hero-slide.previous-far {
    transform: translateX(calc(-50% - 43vw)) scale(0.34);
  }

  .hero-slide.next-far {
    transform: translateX(calc(-50% + 43vw)) scale(0.34);
  }
}

@media (max-width: 760px) {
  .tools-header {
    height: 64px;
  }

  .tools-header__inner {
    width: calc(100% - 24px);
  }

  .campus-brand__mark {
    width: 40px;
    height: 40px;
    border-radius: 10px;
    font-size: 17px;
  }

  .campus-brand strong {
    font-size: 16px;
  }

  .campus-brand em,
  .user-entry {
    display: none;
  }

  .tools-nav a {
    height: 64px;
    font-size: 13px;
  }

  .hero-stage {
    height: 390px;
    margin-top: 8px;
  }

  .hero-slide {
    width: calc(100% - 38px);
    height: 346px;
    border-radius: 24px;
  }

  .hero-slide.previous {
    transform: translateX(calc(-50% - 92vw)) scale(0.9);
  }

  .hero-slide.next {
    transform: translateX(calc(-50% + 92vw)) scale(0.9);
  }

  .hero-slide.previous-far,
  .hero-slide.next-far {
    opacity: 0;
    pointer-events: none;
  }

  .hero-slide__content {
    width: 68%;
    padding: 28px 24px;
  }

  .hero-slide h1 {
    font-size: 38px;
  }

  .hero-slide p {
    font-size: 14px;
  }

  .hero-slide li {
    font-size: 12px;
  }

  .carousel-arrow {
    display: none;
  }

  .category-dock {
    display: flex;
    width: calc(100% - 24px);
    min-height: 62px;
    margin-top: -24px;
    overflow-x: auto;
    border-radius: 22px;
    scrollbar-width: none;
  }

  .category-dock button {
    flex: 0 0 132px;
    gap: 7px;
    border-radius: 20px;
    font-size: 14px;
  }

  .category-icon {
    width: 27px;
    height: 27px;
  }

  .category-icon svg {
    width: 25px;
    height: 25px;
  }

  .tools-section {
    padding-top: 45px;
  }

  .section-title {
    width: calc(100% - 28px);
  }

  .section-title p {
    font-size: 20px;
  }

  .section-title h2 {
    display: none;
  }

  .tool-track {
    --card-width: 224px;
    --card-gap: 14px;
  }

  .tool-poster {
    height: 316px;
  }

  .tool-poster__art {
    height: 202px;
  }

  .tool-arrow {
    width: 40px;
    height: 40px;
  }

  .tool-arrow--left {
    left: 8px;
  }

  .tool-arrow--right {
    right: 8px;
  }
}

@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    scroll-behavior: auto !important;
    animation-duration: 0.01ms !important;
    transition-duration: 0.01ms !important;
  }
}
</style>
