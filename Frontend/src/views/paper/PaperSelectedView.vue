<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getPaper, removePaperQuestion, updatePaperQuestion } from '../../api/paper'
import { paperQuestionId } from '../../utils/paperQuestion'
import PaperPageShell from './PaperPageShell.vue'

const route = useRoute()
const router = useRouter()

const paperId = ref(route.query.paperId)
const paper = ref({ questions: [] })

async function load() {
  paper.value = await getPaper(paperId.value) || { questions: [] }
}

async function updateScore(item, value) {
  const score = Number(value)
  if (score <= 0) {
    window.alert('分值必须大于0')
    return load()
  }
  paper.value = await updatePaperQuestion(paperId.value, item.questionId, { score })
}

async function move(index, delta) {
  const target = index + delta
  if (target < 0 || target >= paper.value.questions.length) return
  const current = paper.value.questions[index]
  paper.value = await updatePaperQuestion(paperId.value, current.questionId, { questionOrder: target + 1 })
}

async function remove(item) {
  if (!window.confirm('确认从试卷中移除这道题？')) return
  paper.value = await removePaperQuestion(paperId.value, item.questionId)
}

function backSelect() {
  router.back()
}

function preview() {
  if (!paper.value.questionCount) {
    window.alert('请至少选择一道题')
    return
  }
  router.push({ path: '/paper/preview', query: { paperId: paperId.value } })
}

onMounted(load)
</script>

<template>
  <PaperPageShell title="已选题目" back-to="/paper/select">
    <div class="summary">共 {{ paper.questionCount || 0 }} 题　总分 {{ paper.totalScore || 0 }} 分</div>

    <article v-for="(item, index) in paper.questions" :key="paperQuestionId(item)" class="paper-card item-card">
      <div class="top">
        <span class="order">{{ index + 1 }}</span>
        <span class="type">{{ item.question.questionType }}</span>
        <div class="sort">
          <button type="button" @click="move(index, -1)">↑</button>
          <button type="button" @click="move(index, 1)">↓</button>
        </div>
      </div>
      <p>{{ item.question.content }}</p>
      <div class="foot">
        <label class="score">
          <input type="number" :value="item.score" @change="updateScore(item, $event.target.value)" />
          <span>分</span>
        </label>
        <button class="danger" type="button" @click="remove(item)">移除</button>
      </div>
    </article>

    <div v-if="!paper.questions?.length" class="paper-empty">尚未选择题目</div>

    <footer class="paper-bottom">
      <button class="paper-btn paper-btn--secondary" type="button" @click="backSelect">继续选题</button>
      <button class="paper-btn paper-btn--primary" type="button" @click="preview">预览试卷</button>
    </footer>
  </PaperPageShell>
</template>

<style scoped>
@import './paper.css';

.summary {
  margin-bottom: 16px;
  padding: 16px;
  border-radius: 12px;
  color: #4269c8;
  background: #eaf2ff;
  text-align: center;
  font-weight: 600;
}

.top {
  display: flex;
  align-items: center;
  gap: 10px;
}

.order {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  color: #fff;
  background: #4d78e8;
  font-size: 13px;
}

.type {
  color: #5774b8;
  font-size: 13px;
}

.sort {
  margin-left: auto;
}

.sort button {
  padding: 4px 10px;
  color: #4d78e8;
}

.item-card p {
  margin: 14px 0;
  line-height: 1.6;
}

.foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.score {
  display: flex;
  align-items: center;
  gap: 6px;
}

.score input {
  width: 64px;
  padding: 6px 8px;
  border: 1px solid #d0d5dd;
  border-radius: 8px;
  text-align: center;
}

.danger {
  color: #e16767;
}
</style>
