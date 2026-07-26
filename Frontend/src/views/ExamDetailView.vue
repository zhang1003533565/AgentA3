<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getExamResult } from '../api/exam'

const route = useRoute()
const router = useRouter()
const result = ref(null)
const error = ref('')

const groups = computed(() => {
  const definitions = [
    ['single_choice', '一、单选题'], ['multiple_choice', '二、多选题'],
    ['true_false', '三、判断题'], ['fill_blank', '四、填空题'],
    ['calculation', '五、计算题'], ['essay', '六、论述题'],
    ['short_answer', '七、简答题'], ['material_analysis', '八、材料分析题'],
  ]
  let displayNumber = 0
  return definitions.map(([type, label]) => ({
    type,
    label,
    questions: (result.value?.questions || [])
      .filter(item => item.type === type)
      .map(item => ({ ...item, displayNumber: ++displayNumber })),
  })).filter(group => group.questions.length)
})

function parse(value, fallback = {}) {
  try { return value ? JSON.parse(value) : fallback } catch { return fallback }
}
function options(question) { return parse(question.bodyJson).options || [] }
function typeName(type) {
  return {
    single_choice: '单选题', multiple_choice: '多选题', true_false: '判断题',
    fill_blank: '填空题', short_answer: '简答题', essay: '论述题',
    material_analysis: '材料分析题', calculation: '计算题',
  }[type] || '题目'
}
function optionText(question, key) {
  return options(question).find(item => item.key === key)?.text || key
}
function userAnswer(question) {
  const answer = parse(question.userAnswerJson)
  if (question.type === 'single_choice') {
    return answer.selectedOption ? `${answer.selectedOption}. ${optionText(question, answer.selectedOption)}` : '未作答'
  }
  if (question.type === 'multiple_choice') {
    return answer.selectedOptions?.length
      ? answer.selectedOptions.map(key => `${key}. ${optionText(question, key)}`).join('；') : '未作答'
  }
  if (question.type === 'true_false') return typeof answer.value === 'boolean' ? (answer.value ? '正确' : '错误') : '未作答'
  if (question.type === 'fill_blank') return answer.blanks?.map(item => item.value || '未填').join('；') || '未作答'
  return answer.text?.trim() || '未作答'
}
function correctAnswer(question) {
  const answer = parse(question.answerJson)
  if (question.type === 'single_choice') {
    return `${answer.correctOption}. ${optionText(question, answer.correctOption)}`
  }
  if (question.type === 'multiple_choice') {
    return (answer.correctOptions || []).map(key => `${key}. ${optionText(question, key)}`).join('；')
  }
  if (question.type === 'true_false') return answer.correct ? '正确' : '错误'
  if (question.type === 'fill_blank') {
    return (answer.blanks || []).map(item => (item.answers || []).join('/')).join('；')
  }
  return answer.referenceAnswer || question.analysis || '请参考评分要点'
}
function jump(index) {
  document.getElementById(`review-question-${index}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

onMounted(async () => {
  try { result.value = await getExamResult(route.params.attemptId) }
  catch (cause) { error.value = cause.message }
})
</script>

<template>
  <div class="review-page">
    <header class="review-header">
      <button type="button" @click="router.push(`/mine/papers/results/${route.params.attemptId}`)">‹ 返回成绩</button>
      <div><h1>Python基础能力随机考试</h1><p>共 {{ result?.questions.length || 0 }} 题　满分 {{ result?.objectiveTotalScore || 0 }} 分</p></div>
      <strong>最终成绩 <b>{{ result?.objectiveScore || 0 }}</b> 分</strong>
    </header>
    <div v-if="error" class="loading">{{ error }}</div>
    <div v-else-if="!result" class="loading">正在加载试卷详情…</div>
    <main v-else class="review-layout">
      <section class="question-list">
        <div v-for="group in groups" :key="group.type" class="question-group">
          <h2>{{ group.label }}（{{ group.questions.length }}题）</h2>
          <article v-for="question in group.questions" :id="`review-question-${question.displayNumber}`"
            :key="question.id" class="review-question">
            <div class="stem"><b>{{ question.displayNumber }}.</b><span class="type">{{ typeName(question.type) }} · {{ question.maxScore }}分</span>{{ question.stem }}</div>
            <ul v-if="options(question).length" class="option-list">
              <li v-for="option in options(question)" :key="option.key">{{ option.key }}. {{ option.text }}</li>
            </ul>
            <div class="answer-box" :class="question.correct ? 'answer-box--right' : 'answer-box--wrong'">
              <p><b>我的答案：</b>{{ userAnswer(question) }}<strong>{{ question.score || 0 }} / {{ question.maxScore }} 分</strong></p>
              <p><b>正确答案：</b>{{ correctAnswer(question) }}</p>
              <p class="analysis"><b>答案解析：</b>{{ question.analysis || '本题暂无补充解析。' }}</p>
            </div>
          </article>
        </div>
      </section>
      <aside class="question-map">
        <div class="map-legend"><span class="right-dot"></span>正确 <span class="wrong-dot"></span>错误</div>
        <section v-for="group in groups" :key="group.type">
          <h3>{{ group.label }}</h3>
          <div class="number-grid">
            <button v-for="question in group.questions" :key="question.id" type="button"
              :class="question.correct ? 'right' : 'wrong'"
              @click="jump(question.displayNumber)">{{ question.displayNumber }}</button>
          </div>
        </section>
      </aside>
    </main>
  </div>
</template>

<style scoped>
.review-page{min-height:100vh;background:#f3f5f7}.review-header{display:grid;grid-template-columns:150px 1fr auto;align-items:center;gap:20px;padding:22px max(20px,calc((100% - 1440px)/2));border-bottom:1px solid #e4e7ec;background:#fff}.review-header>button{color:#2e73b6;background:transparent;text-align:left}.review-header h1{margin:0 0 7px;color:#16233b;font-size:23px}.review-header p{margin:0;color:#98a2b3}.review-header>strong{color:#f0644c;font-weight:500}.review-header>strong b{font-size:31px}.review-layout{display:grid;grid-template-columns:minmax(0,1fr) 330px;gap:20px;width:min(calc(100% - 36px),1440px);margin:20px auto}.question-list,.question-map{border:1px solid #e4e7ec;border-radius:10px;background:#fff}.question-list{padding:26px 34px}.question-group>h2{margin:10px 0 28px;color:#16233b;font-size:21px}.review-question{scroll-margin-top:20px;margin-bottom:38px;padding-bottom:30px;border-bottom:1px solid #edf0f2}.stem{color:#17233a;font-size:17px;line-height:1.8}.stem>b{margin-right:7px}.type{margin-right:10px;color:#98a2b3;font-size:13px}.option-list{display:grid;gap:10px;margin:17px 0;padding:0;list-style:none;color:#344054}.answer-box{margin-top:20px;border-left:5px solid;padding:15px 18px;background:#f8fafc}.answer-box--right{border-color:#3b82f6}.answer-box--wrong{border-color:#ef5b5b}.answer-box p{position:relative;margin:8px 0;color:#475467;line-height:1.65}.answer-box p>strong{position:absolute;right:0;color:#17233a}.answer-box--right>p:first-child{color:#2875de}.answer-box--wrong>p:first-child{color:#d94b4b}.answer-box .analysis{margin-top:13px;padding-top:13px;border-top:1px dashed #dfe4e9}.question-map{position:sticky;top:20px;align-self:start;max-height:calc(100vh - 40px);padding:22px;overflow:auto}.map-legend{padding-bottom:14px;border-bottom:1px solid #edf0f2;color:#667085;font-size:13px}.map-legend span{display:inline-block;width:9px;height:9px;margin:0 5px 0 13px;border-radius:50%}.right-dot{background:#3b82f6}.wrong-dot{background:#ef5b5b}.question-map h3{margin:20px 0 12px;color:#344054;font-size:14px}.number-grid{display:grid;grid-template-columns:repeat(5,1fr);gap:9px}.number-grid button{aspect-ratio:1;border:1px solid;border-radius:6px;background:#fff}.number-grid button.right{color:#2875de;border-color:#3b82f6;background:#eef6ff}.number-grid button.wrong{color:#d94040;border-color:#ef5b5b;background:#fff1f1}.loading{padding:80px;text-align:center;color:#667085}@media(max-width:900px){.review-header{grid-template-columns:auto 1fr}.review-header>strong{grid-column:2}.review-layout{grid-template-columns:1fr}.question-map{position:static;grid-row:1;max-height:none}.question-list{padding:20px}.number-grid{grid-template-columns:repeat(8,1fr)}}@media(max-width:560px){.review-header{grid-template-columns:1fr;padding:16px}.review-header>strong{grid-column:auto}.number-grid{grid-template-columns:repeat(6,1fr)}}
</style>
