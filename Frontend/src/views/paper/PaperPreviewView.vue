<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { completePaper, getPaper } from '../../api/paper'
import { parseQuestionOptions, paperQuestionId } from '../../utils/paperQuestion'
import PaperPageShell from './PaperPageShell.vue'

const route = useRoute()
const router = useRouter()

const paperId = ref(route.query.paperId)
const paper = ref({ questions: [] })
const expandedQuestionIds = ref([])
const saving = ref(false)

const allExpanded = computed(() => (
  paper.value.questions.length > 0 && expandedQuestionIds.value.length === paper.value.questions.length
))

async function load() {
  paper.value = await getPaper(paperId.value) || { questions: [] }
  expandedQuestionIds.value = []
}

function isExpanded(item) {
  return expandedQuestionIds.value.includes(paperQuestionId(item))
}

function toggleQuestion(item) {
  const id = paperQuestionId(item)
  expandedQuestionIds.value = isExpanded(item)
    ? expandedQuestionIds.value.filter((value) => value !== id)
    : [...expandedQuestionIds.value, id]
}

function toggleAll() {
  expandedQuestionIds.value = allExpanded.value
    ? []
    : paper.value.questions.map((item) => paperQuestionId(item))
}

function openLayout() {
  router.push({ path: '/paper/layout', query: { paperId: paperId.value } })
}

async function complete() {
  if (!paper.value.questions.length) {
    window.alert('至少选择一道题')
    return
  }
  if (paper.value.questions.some((item) => Number(item.score) <= 0)) {
    window.alert('题目分值必须大于0')
    return
  }
  saving.value = true
  try {
    await completePaper(paperId.value)
    router.replace('/paper/mine')
  } catch (cause) {
    window.alert(cause.message || '组卷失败')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <PaperPageShell title="试卷预览" back-to="/paper/selected" :show-steps="true" :step="3">
    <template #extra>
      <button class="paper-link" type="button" @click="toggleAll">{{ allExpanded ? '全部收起' : '全部展开' }}</button>
    </template>

    <div class="preview-panel">
      <div class="header">
        <button class="layout-entry" type="button" @click="openLayout">纸质版式</button>
        <h2>{{ paper.name }}</h2>
        <p>{{ paper.subject }} · {{ paper.category }}</p>
        <p>{{ paper.duration ? `${paper.duration}分钟 · ` : '' }}{{ paper.questionCount || 0 }}题 · {{ paper.totalScore || 0 }}分</p>
        <p v-if="paper.remark" class="remark">{{ paper.remark }}</p>
      </div>

      <article v-for="(item, index) in paper.questions" :key="paperQuestionId(item)" class="question">
        <div class="q-title">
          <p>{{ index + 1 }}. {{ item.question.content }}</p>
          <span>（{{ item.score }}分）</span>
        </div>
        <div v-if="parseQuestionOptions(item.question.options).length" class="options">
          <p v-for="(option, optionIndex) in parseQuestionOptions(item.question.options)" :key="optionIndex">
            {{ String.fromCharCode(65 + optionIndex) }}. {{ option }}
          </p>
        </div>
        <button class="answer-toggle" type="button" @click="toggleQuestion(item)">
          {{ isExpanded(item) ? '收起答案解析' : '查看答案解析' }}
        </button>
        <div v-if="isExpanded(item)" class="answer">
          <p><strong>正确答案：</strong>{{ item.question.answer || '暂无' }}</p>
          <p><strong>答案解析：</strong>{{ item.question.analysis || '暂无' }}</p>
        </div>
      </article>
    </div>

    <footer class="paper-bottom">
      <button class="paper-btn paper-btn--secondary" type="button" @click="router.push({ path: '/paper/print', query: { paperId } })">
        真实 PDF 预览
      </button>
      <button class="paper-btn paper-btn--primary" type="button" :disabled="saving" @click="complete">
        {{ saving ? '提交中…' : '完成组卷' }}
      </button>
    </footer>
  </PaperPageShell>
</template>

<style scoped>
@import './paper.css';

.preview-panel {
  padding: 24px;
  border: 1px solid #e2e7ed;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 5px 18px rgba(16, 24, 40, 0.04);
}

.header {
  position: relative;
  margin-bottom: 18px;
  padding-bottom: 18px;
  border-bottom: 1px solid #d9dee7;
  text-align: center;
  color: #69758a;
}

.layout-entry {
  position: absolute;
  top: 0;
  right: 0;
  padding: 6px 12px;
  border: 1px solid #9abbd6;
  border-radius: 8px;
  color: #1e6bb8;
  background: #f5faff;
  font-size: 13px;
}

.header h2 {
  margin: 0 0 8px;
  color: #202d42;
  font-size: 24px;
}

.question {
  padding: 18px 0;
  border-bottom: 1px solid #edf0f5;
}

.q-title {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  line-height: 1.6;
}

.options p {
  margin: 8px 0 0 24px;
  line-height: 1.7;
  color: #566379;
}

.answer-toggle {
  margin-top: 12px;
  padding: 8px 14px;
  border-radius: 8px;
  color: #1e6bb8;
  background: #eef4ff;
}

.answer {
  margin-top: 12px;
  padding: 14px;
  border-radius: 8px;
  background: #f5f8fb;
  line-height: 1.7;
}
</style>
