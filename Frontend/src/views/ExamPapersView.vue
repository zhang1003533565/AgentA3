<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listExamPapers, startExam } from '../api/exam'

const router = useRouter()
const papers = ref([])
const loading = ref(true)
const starting = ref(null)
const error = ref('')

async function loadPapers() {
  loading.value = true
  error.value = ''
  try {
    const result = await listExamPapers({ page: 0, size: 30 })
    papers.value = result.content || []
  } catch (cause) {
    error.value = cause.message
  } finally {
    loading.value = false
  }
}

async function enterExam(paper) {
  starting.value = paper.id
  error.value = ''
  try {
    const attempt = await startExam(paper.id)
    router.push(`/mine/papers/attempts/${attempt.id}`)
  } catch (cause) {
    error.value = cause.message
  } finally {
    starting.value = null
  }
}

onMounted(loadPapers)
</script>

<template>
  <div class="papers-shell">
    <header class="papers-header">
      <button type="button" aria-label="返回个人中心" @click="router.push('/mine')">‹</button>
      <div><h1>我的试卷</h1><p>考试与练习</p></div>
    </header>
    <main class="papers-main">
      <div v-if="error" class="message message--error">{{ error }}</div>
      <div v-if="loading" class="empty">正在加载试卷…</div>
      <div v-else-if="!papers.length" class="empty">暂无可参加的试卷</div>
      <article v-for="paper in papers" :key="paper.id" class="paper">
        <div class="paper__status">
          <span>可参加</span><small>已完成 {{ paper.attemptCount }} 次</small>
        </div>
        <h2>{{ paper.title }}</h2>
        <p>{{ paper.subtitle || '请在规定时间内独立完成试卷' }}</p>
        <div class="paper__facts">
          <div><b>{{ paper.questionCount }}</b><span>题目</span></div>
          <div><b>{{ paper.totalScore }}</b><span>总分</span></div>
          <div><b>{{ paper.durationMinutes }}</b><span>分钟</span></div>
        </div>
        <button type="button" :disabled="starting === paper.id" @click="enterExam(paper)">
          {{ starting === paper.id ? '正在生成试卷…' : paper.inProgressAttemptId ? '继续答题' : '开始考试' }}
        </button>
        <button class="history-entry" type="button"
          @click="router.push(`/mine/papers/${paper.id}/history`)">
          <span><i></i>历史记录</span>
          <span>共完成 {{ paper.attemptCount }} 次　›</span>
        </button>
      </article>
    </main>
  </div>
</template>

<style scoped>
.papers-shell{min-height:100vh;background:#f4f6f8}.papers-header{display:flex;align-items:center;gap:14px;padding:20px max(18px,calc((100% - 760px)/2));border-bottom:1px solid #e4e7ec;background:#fff}.papers-header>button{width:38px;height:38px;border-radius:50%;color:#344054;background:#f0f2f5;font-size:29px}.papers-header h1{margin:0;color:#1d2939;font-size:23px}.papers-header p{margin:3px 0 0;color:#98a2b3;font-size:13px}.papers-main{width:min(100%,760px);margin:auto;padding:20px 18px}.paper{margin-bottom:16px;padding:22px;border:1px solid #e1e5ea;border-radius:10px;background:#fff;box-shadow:0 8px 22px rgba(16,24,40,.04)}.paper__status{display:flex;align-items:center;justify-content:space-between}.paper__status span{padding:4px 9px;border-radius:4px;color:#2772b8;background:#eaf4fd;font-size:12px}.paper__status small{color:#98a2b3}.paper h2{margin:17px 0 8px;color:#1d2939;font-size:20px}.paper>p{margin:0;color:#667085;font-size:14px}.paper__facts{display:grid;grid-template-columns:repeat(3,1fr);margin:22px 0;padding:16px 0;border-block:1px solid #eef0f2}.paper__facts div{text-align:center}.paper__facts div+div{border-left:1px solid #eef0f2}.paper__facts b,.paper__facts span{display:block}.paper__facts b{color:#344054;font-size:19px}.paper__facts span{margin-top:3px;color:#98a2b3;font-size:12px}.paper>button:not(.history-entry){width:100%;height:44px;border-radius:6px;color:#fff;background:#2f76bd;font-weight:700}.paper>button:disabled{background:#9ab9d8}.history-entry{display:flex;align-items:center;justify-content:space-between;width:100%;margin-top:14px;padding:16px 2px 2px;border:0;border-top:1px solid #eef0f2;color:#344054;background:transparent;text-align:left}.history-entry span:first-child{display:flex;align-items:center;gap:9px;font-weight:700}.history-entry span:last-child{color:#98a2b3;font-size:13px}.history-entry i{display:inline-block;width:15px;height:15px;border:2px solid #5f7895;border-radius:50%;box-shadow:inset 0 0 0 3px #fff;background:#5f7895}.message,.empty{padding:36px;text-align:center;color:#667085}.message--error{margin-bottom:14px;padding:12px;border-radius:6px;color:#b42318;background:#fff0ee}
</style>
