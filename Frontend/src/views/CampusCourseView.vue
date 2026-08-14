<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import AppTabBar from '../components/AppTabBar.vue'
import { getCampusCourse, updateCampusCourseProgress } from '../api/campusCourse'

const route = useRoute()
const router = useRouter()
const course = ref(null)
const loading = ref(true)
const error = ref('')
const activeChapterId = ref(null)
const saving = ref(false)

const completedCount = computed(() => (course.value?.chapters || []).filter((item) => item.completed).length)

async function loadCourse() {
  loading.value = true
  error.value = ''
  try {
    const response = await getCampusCourse(route.params.courseId)
    course.value = response.data
    activeChapterId.value ||= course.value?.chapters?.find((item) => !item.completed)?.id || null
  } catch (requestError) {
    error.value = requestError.message || '课程加载失败'
  } finally {
    loading.value = false
  }
}

async function toggleProgress(chapter) {
  if (saving.value) return
  saving.value = true
  try {
    const response = await updateCampusCourseProgress(course.value.id, chapter.id, !chapter.completed)
    course.value = response.data
  } finally {
    saving.value = false
  }
}

onMounted(loadCourse)
</script>

<template>
  <div class="phone-shell">
    <AppTabBar />
    <main class="page course-reader">
      <button class="back-link" type="button" @click="router.back()">← 返回校园课程</button>
      <p v-if="loading" class="state">正在打开课程书...</p>
      <p v-else-if="error" class="state">{{ error }}</p>
      <template v-else-if="course">
        <header class="book-header card">
          <div class="book-cover">课</div>
          <div>
            <p class="eyebrow">CAMPUS COURSE</p>
            <h1>{{ course.name }}</h1>
            <strong>{{ course.bookTitle }}</strong>
            <span>{{ course.ownerName }}</span>
          </div>
        </header>

        <img
          v-if="course.displayImageUrl || course.coverUrl"
          class="course-display-image"
          :src="course.displayImageUrl || course.coverUrl"
          :alt="`${course.name}展示图`"
        >

        <section class="progress-panel card">
          <div><strong>学习进度</strong><span>{{ course.progressPercent }}%</span></div>
          <progress :value="course.progressPercent" max="100" />
          <small>{{ completedCount }}/{{ course.chapters.length }} 个章节已完成</small>
        </section>

        <section class="reader-section">
          <div class="section-title"><h2>课程目录</h2><span>{{ course.chapters.length }} 章</span></div>
          <article v-for="(chapter, index) in course.chapters" :key="chapter.id" class="chapter card">
            <button class="chapter-head" type="button" @click="activeChapterId = activeChapterId === chapter.id ? null : chapter.id">
              <span class="chapter-number">{{ chapter.completed ? '✓' : index + 1 }}</span>
              <span><strong>{{ chapter.title }}</strong><small>{{ chapter.estimatedMinutes || 30 }} 分钟 · {{ chapter.required ? '必修' : '选修' }}</small></span>
              <em>{{ activeChapterId === chapter.id ? '收起' : '阅读' }}</em>
            </button>
            <div v-if="activeChapterId === chapter.id" class="chapter-body">
              <p>{{ chapter.summary }}</p>
              <div class="chapter-content">{{ chapter.content || '管理员暂未录入本章正文。' }}</div>
              <a v-if="chapter.resourceUrl" :href="chapter.resourceUrl" target="_blank" rel="noreferrer">打开附加资料</a>
              <button type="button" @click="toggleProgress(chapter)">{{ chapter.completed ? '标记为未完成' : '完成本章学习' }}</button>
            </div>
          </article>
        </section>

        <section class="reader-section">
          <div class="section-title"><h2>课程考试</h2><span>{{ course.exams.length }} 场</span></div>
          <button v-for="exam in course.exams" :key="exam.id" class="exam-card card" type="button" @click="router.push('/mine/papers')">
            <span>考</span>
            <div><strong>{{ exam.title }}</strong><small>{{ exam.chapterScope || '全部章节' }} · {{ exam.questionCount }} 题 · {{ exam.durationMinutes }} 分钟</small></div>
            <em>进入考试</em>
          </button>
          <p v-if="!course.exams.length" class="state card">当前课程暂无已发布考试</p>
        </section>
      </template>
    </main>
  </div>
</template>

<style scoped>
.course-reader{padding-bottom:96px}.back-link{margin-bottom:14px;color:#526f88;background:transparent}.book-header{display:flex;gap:18px;padding:20px}.book-cover{display:grid;width:90px;height:116px;flex:none;place-items:center;border-radius:7px 12px 12px 7px;background:#e8eef3;color:#526f88;font-size:28px;font-weight:900;box-shadow:inset 5px 0 #cbd7e2}.book-header h1{margin:4px 0 8px;font-size:24px}.book-header strong,.book-header span{display:block}.book-header span{margin-top:14px;color:#64748b;font-size:13px}.course-display-image{display:block;width:100%;aspect-ratio:16/9;margin-top:14px;border-radius:10px;object-fit:cover;background:#e8eef3}.progress-panel{margin-top:14px;padding:16px}.progress-panel>div{display:flex;justify-content:space-between}.progress-panel progress{width:100%;margin:12px 0;accent-color:#607d96}.progress-panel small{color:#64748b}.reader-section{margin-top:22px}.section-title{display:flex;align-items:center;justify-content:space-between}.section-title h2{font-size:19px}.section-title span{color:#94a3b8;font-size:13px}.chapter{margin-top:10px;overflow:hidden}.chapter-head{display:flex;width:100%;align-items:center;gap:12px;padding:14px;text-align:left;background:#fff}.chapter-number{display:grid;width:34px;height:34px;flex:none;place-items:center;border:1px solid #cbd5df;border-radius:50%}.chapter-head>span:nth-child(2){display:grid;flex:1;gap:4px}.chapter-head small,.chapter-head em,.exam-card small,.exam-card em{color:#64748b;font-size:12px;font-style:normal}.chapter-body{padding:0 16px 16px;border-top:1px solid #edf0f3}.chapter-content{margin:12px 0;white-space:pre-wrap;line-height:1.7}.chapter-body a{display:block;margin:12px 0;color:#2563eb}.chapter-body button{width:100%;padding:10px;border-radius:8px;color:#fff;background:#526f88}.exam-card{display:flex;width:100%;align-items:center;gap:12px;margin-top:10px;padding:14px;text-align:left}.exam-card>span{display:grid;width:38px;height:38px;place-items:center;border-radius:9px;background:#eef1f7;color:#5b6686}.exam-card div{display:grid;flex:1;gap:4px}.state{padding:40px 16px;color:#64748b;text-align:center}
</style>
