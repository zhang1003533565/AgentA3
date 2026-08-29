<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getPaperBank, listQuestions, removeQuestionFromBank } from '../../api/paper'
import PaperPageShell from './PaperPageShell.vue'

const route = useRoute()
const router = useRouter()

const id = ref(route.params.id)
const bank = ref({})
const questions = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const [bankData, questionList] = await Promise.all([
      getPaperBank(id.value),
      listQuestions(id.value),
    ])
    bank.value = bankData || {}
    questions.value = questionList || []
  } finally {
    loading.value = false
  }
}

function openDetail(questionId) {
  router.push(`/paper/questions/${questionId}`)
}

async function remove(question) {
  if (!window.confirm('仅从当前题库组移除，不会删除原题，确认移除？')) return
  await removeQuestionFromBank(id.value, question.id)
  questions.value = questions.value.filter((item) => item.id !== question.id)
  bank.value.questionCount = questions.value.length
}

onMounted(load)
</script>

<template>
  <PaperPageShell :title="bank.name || '题库详情'" back-to="/paper/banks">
    <p class="meta">{{ bank.description || '暂无说明' }} · {{ questions.length }} 题</p>
    <div v-if="loading" class="paper-state">加载中…</div>
    <article v-for="question in questions" :key="question.id" class="paper-card">
      <div class="tags">
        <span>{{ question.questionType }}</span>
        <span>{{ question.difficulty }}</span>
      </div>
      <p>{{ question.content }}</p>
      <div class="actions">
        <button type="button" @click="openDetail(question.id)">查看详情</button>
        <button class="danger" type="button" @click="remove(question)">移除</button>
      </div>
    </article>
    <div v-if="!loading && !questions.length" class="paper-empty">题库中暂无题目</div>
  </PaperPageShell>
</template>

<style scoped>
@import './paper.css';

.meta {
  margin: 0 0 16px;
  color: #8c98aa;
}

.tags span {
  display: inline-block;
  margin-right: 8px;
  padding: 4px 8px;
  border-radius: 6px;
  color: #4775e5;
  background: #eaf2ff;
  font-size: 12px;
}

.actions {
  display: flex;
  gap: 16px;
  margin-top: 12px;
}

.danger {
  color: #df6565;
}
</style>
