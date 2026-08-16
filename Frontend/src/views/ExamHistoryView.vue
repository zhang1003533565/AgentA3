<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listExamHistory } from '../api/exam'

const route = useRoute()
const router = useRouter()
const records = ref([])
const loading = ref(true)
const error = ref('')
const completedCount = computed(() => records.value.length)

function formatDate(value) {
  if (!value) return '—'
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false,
  }).format(new Date(value)).replaceAll('/', '-')
}

function duration(record) {
  if (!record.startedAt || !record.submittedAt) return '—'
  const seconds = Math.max(0, Math.floor(
    (new Date(record.submittedAt) - new Date(record.startedAt)) / 1000,
  ))
  return `${Math.floor(seconds / 60)}分${seconds % 60}秒`
}

onMounted(async () => {
  try {
    records.value = await listExamHistory(route.params.paperId)
  } catch (cause) {
    error.value = cause.message
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="history-page">
    <header class="history-header">
      <button type="button" aria-label="返回我的试卷" @click="router.push('/mine/papers')">‹</button>
      <div><h1>历史记录</h1><p>共完成 {{ completedCount }} 次考试</p></div>
    </header>
    <main class="history-main">
      <div v-if="error" class="state state--error">{{ error }}</div>
      <div v-else-if="loading" class="state">正在加载历史记录…</div>
      <div v-else-if="!records.length" class="state">
        <b>暂无历史记录</b><span>完成并提交试卷后，记录会显示在这里。</span>
      </div>
      <template v-else>
        <div class="summary"><span>累计完成</span><strong>{{ completedCount }}</strong><em>次</em></div>
        <article v-for="record in records" :key="record.id" class="record"
          @click="router.push(`/mine/papers/results/${record.id}`)">
          <div class="record__head">
            <div>
              <b>第 {{ record.attemptNo }} 次作答</b>
              <span>{{ record.status === 'AUTO_SUBMITTED' ? '自动交卷' : '已交卷' }}</span>
            </div>
            <strong>{{ record.objectiveScore ?? 0 }}<small>/ {{ record.objectiveTotalScore ?? 100 }} 分</small></strong>
          </div>
          <dl>
            <div><dt>开始时间</dt><dd>{{ formatDate(record.startedAt) }}</dd></div>
            <div><dt>交卷时间</dt><dd>{{ formatDate(record.submittedAt) }}</dd></div>
            <div><dt>答题用时</dt><dd>{{ duration(record) }}</dd></div>
          </dl>
          <div class="record__actions">
            <button type="button" @click.stop="router.push(`/mine/papers/results/${record.id}`)">查看成绩</button>
            <button class="primary" type="button"
              @click.stop="router.push(`/mine/papers/results/${record.id}/details`)">查看答题记录</button>
          </div>
        </article>
      </template>
    </main>
  </div>
</template>

<style scoped>
.history-page{min-height:100vh;background:#f4f6f8}.history-header{display:flex;align-items:center;gap:14px;padding:20px max(18px,calc((100% - 760px)/2));border-bottom:1px solid #e4e7ec;background:#fff}.history-header>button{width:38px;height:38px;border:0;border-radius:50%;color:#344054;background:#f0f2f5;font-size:29px}.history-header h1{margin:0;color:#1d2939;font-size:23px}.history-header p{margin:3px 0 0;color:#98a2b3;font-size:13px}.history-main{box-sizing:border-box;width:min(100%,760px);margin:auto;padding:20px 18px}.summary{display:flex;align-items:baseline;justify-content:center;margin-bottom:16px;padding:20px;border:1px solid #e1e5ea;border-radius:10px;background:#fff;color:#667085}.summary strong{margin-left:13px;color:#2f76bd;font-size:36px}.summary em{margin-left:5px;font-style:normal}.record{margin-bottom:14px;padding:20px 22px;border:1px solid #e1e5ea;border-radius:10px;background:#fff;box-shadow:0 6px 18px rgba(16,24,40,.035);cursor:pointer;transition:border-color .18s,transform .18s}.record:hover{border-color:#a8c8e8;transform:translateY(-1px)}.record__head{display:flex;align-items:center;justify-content:space-between;padding-bottom:16px;border-bottom:1px solid #eef0f2}.record__head>div{display:flex;align-items:center;gap:10px}.record__head b{color:#1d2939;font-size:17px}.record__head span{padding:3px 8px;border-radius:4px;color:#2772b8;background:#eaf4fd;font-size:12px}.record__head>strong{color:#2f76bd;font-size:27px}.record__head small{margin-left:3px;color:#98a2b3;font-size:13px;font-weight:400}.record dl{display:grid;grid-template-columns:repeat(3,1fr);gap:12px;margin:16px 0}.record dt{margin-bottom:5px;color:#98a2b3;font-size:12px}.record dd{margin:0;color:#475467;font-size:13px}.record__actions{display:flex;justify-content:flex-end;gap:10px}.record__actions button{height:34px;padding:0 15px;border:1px solid #cfd7e1;border-radius:5px;color:#475467;background:#fff}.record__actions button.primary{color:#fff;border-color:#2f76bd;background:#2f76bd}.state{display:grid;gap:9px;padding:70px 20px;border:1px solid #e1e5ea;border-radius:10px;background:#fff;color:#98a2b3;text-align:center}.state b{color:#475467;font-size:18px}.state--error{color:#b42318;background:#fff0ee}@media(max-width:620px){.record dl{grid-template-columns:1fr}.record__head{align-items:flex-start}.record__head>div{align-items:flex-start;flex-direction:column;gap:6px}.record__actions button{flex:1}}
</style>
