<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getExamResult } from '../api/exam'
import { getUserInfo } from '../utils/auth'

const route = useRoute()
const router = useRouter()
const result = ref(null)
const error = ref('')
const user = getUserInfo() || {}

const correctCount = computed(() => result.value?.questions.filter(item => item.correct).length || 0)
const accuracy = computed(() => result.value?.questions.length
  ? Math.round(correctCount.value / result.value.questions.length * 100) : 0)
const durationText = computed(() => {
  if (!result.value?.startedAt || !result.value?.submittedAt) return '—'
  const seconds = Math.max(0,
    Math.floor((new Date(result.value.submittedAt) - new Date(result.value.startedAt)) / 1000))
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor(seconds % 3600 / 60)
  const rest = seconds % 60
  return hours ? `${hours}小时${minutes}分钟${rest}秒` : `${minutes}分钟${rest}秒`
})
const submitMethod = computed(() => result.value?.status === 'AUTO_SUBMITTED' ? '系统自动收卷' : '学生主动交卷')

function formatDate(value) {
  if (!value) return '—'
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false,
  }).format(new Date(value)).replaceAll('/', '-')
}

onMounted(async () => {
  try { result.value = await getExamResult(route.params.attemptId) }
  catch (cause) { error.value = cause.message }
})
</script>

<template>
  <div class="success-page">
    <header class="page-bar">考试</header>
    <div class="page-decoration" aria-hidden="true">
      <span></span><span></span><span></span>
    </div>
    <main v-if="error" class="success-card">{{ error }}</main>
    <main v-else-if="result" class="success-card">
      <div class="success-mark"><span>✓</span></div>
      <h1>交卷成功</h1>
      <p class="submitted-time">当前时间 <b>{{ formatDate(result.submittedAt) }}</b></p>
      <div class="score-line"><span>本次成绩</span><b>{{ result.objectiveScore }}</b><em>分</em></div>
      <div class="full-score">最终成绩 {{ result.objectiveTotalScore }} 分 · 正确率 {{ accuracy }}%</div>
      <dl class="exam-info">
        <div><dt>姓名</dt><dd>{{ user.realName || user.username || '—' }}</dd></div>
        <div><dt>开始时间</dt><dd>{{ formatDate(result.startedAt) }}</dd></div>
        <div><dt>结束时间</dt><dd>{{ formatDate(result.submittedAt) }}</dd></div>
        <div><dt>考试用时</dt><dd>{{ durationText }}</dd></div>
        <div><dt>提交方式</dt><dd>{{ submitMethod }}</dd></div>
      </dl>
      <button class="detail-button" type="button"
        @click="router.push(`/mine/papers/results/${result.id}/details`)">查看试卷详情</button>
      <button class="back-button" type="button" @click="router.push('/mine/papers')">返回我的试卷</button>
    </main>
    <main v-else class="success-card">正在统计考试成绩…</main>
  </div>
</template>

<style scoped>
.success-page{position:relative;display:grid;box-sizing:border-box;min-height:100vh;padding:94px 16px 44px;place-items:center;overflow:hidden;background:#f5f7fb}.page-bar{position:absolute;inset:0 0 auto;height:44px;color:#d8e0ec;background:#344b69;text-align:center;line-height:44px}.page-decoration{position:absolute;right:4%;bottom:4%;width:270px;height:220px;opacity:.45}.page-decoration span{position:absolute;display:block;border:18px solid #dbe8fb;border-radius:18px}.page-decoration span:nth-child(1){right:0;bottom:0;width:170px;height:145px}.page-decoration span:nth-child(2){right:105px;bottom:30px;width:100px;height:115px}.page-decoration span:nth-child(3){right:45px;bottom:115px;width:70px;height:70px}.success-card{position:relative;z-index:1;box-sizing:border-box;width:min(100%,520px);padding:42px 52px 36px;border:1px solid #edf0f5;border-radius:10px;background:#fff;box-shadow:0 12px 38px rgba(52,75,105,.08);text-align:center}.success-mark{display:grid;width:54px;height:54px;margin:0 auto 13px;place-items:center;border:5px solid #e7f0ff;border-radius:50%;background:#f3f7ff}.success-mark span{display:grid;width:34px;height:34px;place-items:center;border-radius:50%;color:#fff;background:#3485f5;font-size:21px;font-weight:800}.success-card h1{margin:0 0 25px;color:#3485f5;font-size:30px;line-height:1.2}.submitted-time{margin:0;color:#98a2b3;font-size:14px}.submitted-time b{color:#667085}.score-line{display:flex;align-items:baseline;justify-content:center;gap:8px;margin:16px 0 10px;color:#7c8596}.score-line b{color:#737b8d;font-size:48px;line-height:1}.score-line em{font-size:15px;font-style:normal}.full-score{display:inline-block;padding:7px 15px;border:1px solid #ffd8d2;border-radius:18px;color:#f0644c;background:#fff2f0;font-size:13px}.exam-info{box-sizing:border-box;margin:24px 0 26px;padding:16px 22px;border-radius:5px;background:#f7f8fa;text-align:left}.exam-info div{display:grid;grid-template-columns:88px 1fr;padding:7px 0}.exam-info dt{color:#8f98aa}.exam-info dt::after{content:"："}.exam-info dd{margin:0;color:#667085}.detail-button,.back-button{box-sizing:border-box;width:100%;height:42px;border-radius:21px;font-size:14px;font-weight:700;cursor:pointer}.detail-button{color:#3485f5;border:1px solid #70a9ff;background:#fff}.detail-button:hover{background:#f1f6ff}.back-button{margin-top:10px;color:#7c8596;border:0;background:transparent}.back-button:hover{color:#3485f5}@media(max-width:680px){.success-page{padding-top:70px}.success-card{padding:32px 22px}.page-decoration{display:none}.exam-info div{grid-template-columns:80px 1fr}.score-line b{font-size:42px}}
</style>
