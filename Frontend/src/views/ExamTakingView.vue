<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getExamAttempt, saveExamAnswer, submitExam } from '../api/exam'

const route = useRoute()
const router = useRouter()
const attempt = ref(null)
const currentIndex = ref(0)
const answer = ref({})
const secondsLeft = ref(0)
const loading = ref(true)
const saving = ref(false)
const submitting = ref(false)
const showSheet = ref(false)
const error = ref('')
let timer = null
let saveChain = Promise.resolve()

const current = computed(() => attempt.value?.questions?.[currentIndex.value])
const body = computed(() => parseJson(current.value?.bodyJson, {}))
const answeredRate = computed(() => attempt.value
  ? Math.round(attempt.value.answeredCount / attempt.value.questionCount * 100) : 0)
const timeText = computed(() => {
  const hours = Math.floor(secondsLeft.value / 3600)
  const minutes = Math.floor(secondsLeft.value % 3600 / 60)
  const seconds = secondsLeft.value % 60
  return [hours, minutes, seconds].map(value => String(value).padStart(2, '0')).join(':')
})
const typeName = computed(() => ({
  single_choice: '单选题', multiple_choice: '多选题', true_false: '判断题',
  fill_blank: '填空题', short_answer: '简答题', essay: '论述题',
  material_analysis: '材料分析题', calculation: '计算题',
})[current.value?.type] || '题目')

function parseJson(value, fallback) {
  try { return value ? JSON.parse(value) : fallback } catch { return fallback }
}
function emptyAnswer(type) {
  if (type === 'single_choice') return { selectedOption: '' }
  if (type === 'multiple_choice') return { selectedOptions: [] }
  if (type === 'true_false') return { value: null }
  if (type === 'fill_blank') return {
    blanks: (parseJson(current.value?.bodyJson, {}).blanks || [])
      .map(blank => ({ id: blank.id, value: '' })),
  }
  return { text: '' }
}
function blankValue(id) {
  return answer.value.blanks?.find(blank => blank.id === id)?.value || ''
}
function updateBlank(id, value) {
  const blanks = [...(answer.value.blanks || [])]
  const index = blanks.findIndex(blank => blank.id === id)
  if (index >= 0) blanks[index] = { id, value }
  else blanks.push({ id, value })
  answer.value = { blanks }
}
function syncAnswer() {
  answer.value = parseJson(current.value?.userAnswerJson, emptyAnswer(current.value?.type))
}
function chooseSingle(key) {
  answer.value = { selectedOption: key }
  persistAnswer()
}
function toggleMultiple(key) {
  const selected = new Set(answer.value.selectedOptions || [])
  selected.has(key) ? selected.delete(key) : selected.add(key)
  answer.value = { selectedOptions: [...selected] }
  persistAnswer()
}
function chooseBoolean(value) {
  answer.value = { value }
  persistAnswer()
}
function persistAnswer() {
  if (!current.value) return Promise.resolve()
  const question = current.value
  const answerJson = JSON.stringify(answer.value)
  saveChain = saveChain.then(async () => {
    saving.value = true
    error.value = ''
    try {
      const saved = await saveExamAnswer(attempt.value.id, question.id, {
        answerJson,
        version: question.version,
      })
      if (question.answered !== saved.answered) {
        attempt.value.answeredCount += saved.answered ? 1 : -1
      }
      question.answered = saved.answered
      question.version = saved.version
      question.userAnswerJson = saved.answerJson
    } catch (cause) {
      error.value = cause.message
    } finally {
      saving.value = false
    }
  })
  return saveChain
}
async function goTo(index) {
  if (index < 0 || index >= attempt.value.questionCount) return
  currentIndex.value = index
  syncAnswer()
  showSheet.value = false
  await nextTick()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}
async function finish(force = false) {
  if (submitting.value) return
  if (!force && !window.confirm(
    `已答 ${attempt.value.answeredCount} 题，未答 ${attempt.value.questionCount - attempt.value.answeredCount} 题，确定交卷吗？`,
  )) return
  submitting.value = true
  try {
    await saveChain
    await submitExam(attempt.value.id)
    router.replace(`/mine/papers/results/${attempt.value.id}`)
  } catch (cause) {
    error.value = cause.message
    submitting.value = false
  }
}
async function loadAttempt() {
  try {
    attempt.value = await getExamAttempt(route.params.attemptId)
    if (attempt.value.status !== 'IN_PROGRESS') {
      router.replace(`/mine/papers/results/${attempt.value.id}`)
      return
    }
    const serverNow = new Date(attempt.value.serverNow).getTime()
    secondsLeft.value = Math.max(0,
      Math.floor((new Date(attempt.value.deadlineAt).getTime() - serverNow) / 1000))
    syncAnswer()
    timer = window.setInterval(() => {
      secondsLeft.value = Math.max(0, secondsLeft.value - 1)
      if (secondsLeft.value === 0) {
        window.clearInterval(timer)
        finish(true)
      }
    }, 1000)
  } catch (cause) {
    error.value = cause.message
  } finally {
    loading.value = false
  }
}

onMounted(loadAttempt)
onBeforeUnmount(() => window.clearInterval(timer))
</script>

<template>
  <div class="exam-shell">
    <header class="exam-header">
      <button type="button" aria-label="返回试卷列表" @click="router.push('/mine/papers')">‹</button>
      <div><strong>Python基础能力随机考试</strong><small>第 {{ attempt?.attemptNo || '-' }} 次考试</small></div>
      <p><small>剩余时间</small><b>{{ timeText }}</b></p>
    </header>
    <div v-if="loading" class="state">正在随机抽取84道题…</div>
    <main v-else-if="attempt && current" class="exam-main">
      <div class="progress-label"><span>答题进度 {{ attempt.answeredCount }}/{{ attempt.questionCount }}</span><b>{{ answeredRate }}%</b></div>
      <div class="progress"><i :style="{ width: `${answeredRate}%` }"></i></div>
      <div v-if="error" class="error">{{ error }}</div>
      <section class="question">
        <div class="question__meta"><span>{{ currentIndex + 1 }}</span><em>{{ typeName }}</em><b>{{ current.score }}分</b></div>
        <h1>{{ current.stem }}</h1>
        <div v-if="current.type === 'single_choice'" class="options">
          <button v-for="option in body.options" :key="option.key" type="button"
            :class="{ selected: answer.selectedOption === option.key }" @click="chooseSingle(option.key)">
            <span>{{ option.key }}</span><p>{{ option.text }}</p>
          </button>
        </div>
        <div v-else-if="current.type === 'multiple_choice'" class="options">
          <p class="hint">本题有多个正确答案，请选择所有正确选项</p>
          <button v-for="option in body.options" :key="option.key" type="button"
            :class="{ selected: answer.selectedOptions?.includes(option.key) }" @click="toggleMultiple(option.key)">
            <span>{{ option.key }}</span><p>{{ option.text }}</p>
          </button>
        </div>
        <div v-else-if="current.type === 'true_false'" class="options">
          <button type="button" :class="{ selected: answer.value === true }" @click="chooseBoolean(true)"><span>✓</span><p>正确</p></button>
          <button type="button" :class="{ selected: answer.value === false }" @click="chooseBoolean(false)"><span>×</span><p>错误</p></button>
        </div>
        <div v-else-if="current.type === 'fill_blank'" class="blanks">
          <label v-for="(blank,index) in body.blanks" :key="blank.id">
            <span>第 {{ index+1 }} 空</span>
            <input :value="blankValue(blank.id)" :placeholder="blank.placeholder || '请输入答案'"
              @input="updateBlank(blank.id, $event.target.value)" @blur="persistAnswer">
          </label>
        </div>
        <div v-else class="written">
          <p>请按照要点和步骤完整作答，本题按步骤得分点评分。</p>
          <textarea v-model="answer.text" placeholder="请输入你的答案、分析过程或计算步骤" @blur="persistAnswer"></textarea>
        </div>
        <p class="save-state">{{ saving ? '正在保存…' : current.answered ? '答案已保存' : '请选择答案' }}</p>
      </section>
      <nav class="question-nav">
        <button type="button" :disabled="currentIndex === 0" @click="goTo(currentIndex - 1)">上一题</button>
        <button v-if="currentIndex < attempt.questionCount - 1" class="primary" type="button" @click="goTo(currentIndex + 1)">下一题</button>
        <button v-else class="submit" type="button" @click="finish(false)">提交试卷</button>
      </nav>
    </main>
    <footer v-if="attempt" class="exam-footer">
      <button type="button" @click="showSheet = true">▦ 答题卡</button>
      <span>{{ attempt.answeredCount }} / {{ attempt.questionCount }}</span>
      <button class="submit" type="button" :disabled="submitting" @click="finish(false)">{{ submitting ? '提交中…' : '交卷' }}</button>
    </footer>
    <div v-if="showSheet" class="mask" @click.self="showSheet = false">
      <section class="sheet">
        <header><div><h2>答题卡</h2><p><i></i>已答 {{ attempt.answeredCount }}　<span></span>未答 {{ attempt.questionCount-attempt.answeredCount }}</p></div><button type="button" @click="showSheet=false">×</button></header>
        <h3>一、单选题（40题）</h3>
        <div class="grid"><button v-for="(item,index) in attempt.questions.slice(0,40)" :key="item.id" type="button" :class="{ done:item.answered,current:index===currentIndex }" @click="goTo(index)">{{ index+1 }}</button></div>
        <h3>二、多选题（10题）</h3>
        <div class="grid"><button v-for="(item,index) in attempt.questions.slice(40,50)" :key="item.id" type="button" :class="{ done:item.answered,current:index+40===currentIndex }" @click="goTo(index+40)">{{ index+41 }}</button></div>
        <h3>三、判断题（20题）</h3>
        <div class="grid"><button v-for="(item,index) in attempt.questions.slice(50,70)" :key="item.id" type="button" :class="{ done:item.answered,current:index+50===currentIndex }" @click="goTo(index+50)">{{ index+51 }}</button></div>
        <h3>四、填空题（10题）</h3>
        <div class="grid"><button v-for="(item,index) in attempt.questions.slice(70,80)" :key="item.id" type="button" :class="{ done:item.answered,current:index+70===currentIndex }" @click="goTo(index+70)">{{ index+71 }}</button></div>
        <h3>五、综合题（4题）</h3>
        <div class="grid"><button v-for="(item,index) in attempt.questions.slice(80,84)" :key="item.id" type="button" :class="{ done:item.answered,current:index+80===currentIndex }" @click="goTo(index+80)">{{ index+81 }}</button></div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.exam-shell{min-height:100vh;padding-bottom:70px;background:#f4f5f7}.exam-header{position:sticky;top:0;z-index:5;display:grid;grid-template-columns:44px 1fr auto;align-items:center;padding:11px max(15px,calc((100% - 800px)/2));color:#fff;background:#2e73b6}.exam-header>button{color:#fff;background:transparent;font-size:30px}.exam-header>div{text-align:center}.exam-header strong,.exam-header small{display:block}.exam-header strong{font-size:17px}.exam-header small{margin-top:2px;font-size:11px;opacity:.8}.exam-header>p{margin:0;text-align:center}.exam-header>p b{display:block;font-size:17px}.exam-main{width:min(100%,800px);margin:auto;padding:18px}.progress-label{display:flex;justify-content:space-between;color:#667085;font-size:13px}.progress-label b{color:#2e73b6}.progress{height:5px;margin:9px 0 18px;border-radius:5px;background:#dfe4e9}.progress i{display:block;height:100%;border-radius:5px;background:#2e73b6}.question{padding:22px;border:1px solid #e0e4e9;border-radius:9px;background:#fff}.question__meta{display:flex;align-items:center;gap:9px}.question__meta span{display:grid;width:28px;height:28px;place-items:center;border-radius:4px;color:#fff;background:#2e73b6}.question__meta em{padding:4px 8px;border-radius:3px;color:#2e73b6;background:#eaf3fb;font-size:12px;font-style:normal}.question__meta b{margin-left:auto;color:#98a2b3;font-size:12px}.question h1{margin:22px 0;font-size:18px;line-height:1.7;font-weight:600}.options{display:grid;gap:12px}.options>button{display:flex;align-items:center;gap:12px;min-height:54px;padding:10px 14px;border:1px solid #d9dfe6;border-radius:7px;color:#344054;background:#fff;text-align:left}.options>button>span{display:grid;flex:0 0 29px;height:29px;place-items:center;border:1px solid #cbd3dc;border-radius:50%}.options button p{margin:0}.options>button.selected{color:#235f98;border-color:#2e73b6;background:#eef6fd}.options>button.selected>span{color:#fff;border-color:#2e73b6;background:#2e73b6}.hint{margin:0 0 2px;color:#98a2b3;font-size:12px}.blanks{display:grid;gap:14px}.blanks label{display:grid;gap:6px;color:#667085;font-size:13px}.blanks input{height:44px;padding:0 12px;border:1px solid #d9dfe6;border-radius:6px}.written>p{color:#667085;font-size:13px}.written textarea{width:100%;min-height:220px;padding:13px;border:1px solid #d9dfe6;border-radius:7px;line-height:1.7;resize:vertical}.blanks input:focus,.written textarea:focus{outline:2px solid #b9d9f4;border-color:#2e73b6}.save-state{margin:18px 0 0;color:#98a2b3;font-size:12px;text-align:right}.question-nav{display:flex;gap:12px;margin-top:18px}.question-nav button{flex:1;height:44px;border:1px solid #d5dae1;border-radius:6px;background:#fff}.question-nav button:disabled{color:#b5bbc3}.question-nav .primary,.question-nav .submit{color:#fff;border-color:#2e73b6;background:#2e73b6}.exam-footer{position:fixed;right:0;bottom:0;left:0;z-index:4;display:flex;align-items:center;justify-content:space-between;padding:12px max(18px,calc((100% - 800px)/2));border-top:1px solid #e1e4e8;background:#fff}.exam-footer button{padding:9px 18px;border-radius:5px;color:#245f97;background:#edf4fa}.exam-footer .submit{color:#fff;background:#2e73b6}.exam-footer span{color:#667085;font-size:13px}.mask{position:fixed;inset:0;z-index:10;background:rgba(16,24,40,.48)}.sheet{position:absolute;right:0;bottom:0;left:0;max-height:78vh;padding:22px max(18px,calc((100% - 800px)/2));border-radius:16px 16px 0 0;background:#fff;overflow:auto}.sheet header{display:flex;justify-content:space-between}.sheet h2{margin:0}.sheet header p{color:#667085;font-size:12px}.sheet header p i,.sheet header p span{display:inline-block;width:10px;height:10px;border-radius:2px;background:#5f9dd2}.sheet header p span{background:#e4e7ec}.sheet header>button{background:transparent;font-size:26px}.sheet h3{font-size:14px}.grid{display:grid;grid-template-columns:repeat(10,1fr);gap:9px}.grid button{aspect-ratio:1;border-radius:5px;color:#667085;background:#f0f2f5}.grid button.done{color:#fff;background:#659dd0}.grid button.current{outline:2px solid #215e98}.error{margin-bottom:12px;padding:11px;border-radius:6px;color:#b42318;background:#fff0ee}.state{padding:80px 20px;text-align:center;color:#667085}@media(max-width:560px){.grid{grid-template-columns:repeat(7,1fr)}.question{padding:18px}.exam-header strong{font-size:14px}}
</style>
