<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  addPaperQuestion,
  addQuestionToBank,
  favoriteQuestion,
  getQuestionDetail,
  listPaperBanks,
  unfavoriteQuestion,
} from '../../api/paper'
import { parseQuestionOptions } from '../../utils/paperQuestion'
import PaperPageShell from './PaperPageShell.vue'

const route = useRoute()
const router = useRouter()

const questionId = ref(route.params.questionId)
const paperId = ref(route.query.paperId || null)
const question = ref({})
const loading = ref(false)
const error = ref(false)

const options = computed(() => parseQuestionOptions(question.value.options))

async function load() {
  loading.value = true
  error.value = false
  try {
    question.value = await getQuestionDetail(questionId.value, paperId.value) || {}
  } catch {
    error.value = true
  } finally {
    loading.value = false
  }
}

async function toggleFavorite() {
  if (question.value.favorited) await unfavoriteQuestion(questionId.value)
  else await favoriteQuestion(questionId.value)
  question.value.favorited = !question.value.favorited
}

async function addToBank() {
  const banks = await listPaperBanks({ visibility: 'private' }) || []
  if (!banks.length) {
    window.alert('暂无私有题库组')
    return
  }
  const names = banks.map((bank) => bank.name)
  const choice = window.prompt(`输入序号加入题库组：\n${names.map((name, index) => `${index + 1}. ${name}`).join('\n')}`)
  const index = Number(choice) - 1
  if (index < 0 || index >= banks.length) return
  await addQuestionToBank(banks[index].id, questionId.value)
  window.alert('已加入题库组')
}

async function addToPaper() {
  if (!paperId.value) {
    window.alert('请先创建试卷')
    return
  }
  await addPaperQuestion(paperId.value, {
    questionId: Number(questionId.value),
    score: 5,
    sourceType: 'detail',
    sourceId: question.value.bankId,
  })
  question.value.selected = true
  window.alert('已加入试卷')
}

onMounted(load)
</script>

<template>
  <PaperPageShell title="题目详情" :back-to="paperId ? `/paper/select?paperId=${paperId}` : '/paper/select'">
    <div v-if="loading" class="paper-state">正在加载…</div>
    <div v-else-if="error" class="paper-empty paper-empty--error" @click="load">加载失败，点击重试</div>
    <template v-else>
      <section class="paper-card">
        <div class="tags">
          <span>{{ question.questionType }}</span>
          <span>{{ question.difficulty }}</span>
        </div>
        <p>科目：{{ question.subject || '—' }}</p>
        <p>章节：{{ question.chapter || '—' }}</p>
        <p>知识点：{{ question.knowledgePoint || '—' }}</p>
      </section>
      <section class="paper-card">
        <h3>题目</h3>
        <p class="stem">{{ question.content }}</p>
        <p v-for="(option, index) in options" :key="index">{{ String.fromCharCode(65 + index) }}. {{ option }}</p>
      </section>
      <section class="paper-card answer">
        <p><strong>正确答案：</strong>{{ question.answer || '暂无' }}</p>
        <p><strong>答案解析：</strong>{{ question.analysis || '暂无' }}</p>
      </section>
      <section class="paper-card">
        <p>所属题库：{{ question.bankName || '—' }}</p>
      </section>
    </template>

    <footer class="paper-bottom">
      <button class="paper-btn paper-btn--secondary" type="button" @click="toggleFavorite">
        {{ question.favorited ? '取消收藏' : '收藏' }}
      </button>
      <button class="paper-btn paper-btn--ghost" type="button" @click="addToBank">加入题库组</button>
      <button class="paper-btn paper-btn--primary" type="button" :disabled="question.selected" @click="addToPaper">
        {{ question.selected ? '已加入' : '加入试卷' }}
      </button>
    </footer>
  </PaperPageShell>
</template>

<style scoped>
@import './paper.css';

.tags span {
  display: inline-block;
  margin-right: 8px;
  padding: 4px 8px;
  border-radius: 6px;
  color: #4775e5;
  background: #eaf2ff;
}

.stem {
  line-height: 1.7;
}

.answer {
  background: #eef5ff;
}
</style>
