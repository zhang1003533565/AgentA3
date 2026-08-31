<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  addPaperQuestion,
  favoriteQuestion,
  listFavoriteQuestions,
  listPaperBanks,
  listPaperDictionaries,
  listPaperQuestions,
  listQuestions,
  removePaperQuestion,
  unfavoriteQuestion,
} from '../../api/paper'
import PaperPageShell from './PaperPageShell.vue'

const route = useRoute()
const router = useRouter()

const paperId = ref(route.query.paperId || null)
const source = ref(route.query.source || 'public')
const bankId = ref(null)
const paper = ref({})
const banks = ref([])
const questions = ref([])
const keyword = ref('')
const type = ref('')
const difficulty = ref('')
const types = ref([])
const difficulties = ref([])
const loading = ref(false)
const busyId = ref(null)
const filterPanel = ref('')
const selectedQuestionIds = ref([])

const sourceTabs = [
  { label: '共有题库', value: 'public' },
  { label: '私有题库', value: 'private' },
  { label: '收藏夹', value: 'favorite' },
]

const showBanks = computed(() => source.value !== 'favorite' && !bankId.value)
const showQuestions = computed(() => !showBanks.value)
const currentFilterOptions = computed(() => (filterPanel.value === 'type' ? types.value : difficulties.value))

function applyPaperState(data) {
  paper.value = data || {}
  selectedQuestionIds.value = (paper.value.questions || []).map((item) => Number(item.questionId))
  syncQuestionSelection()
}

function syncQuestionSelection() {
  questions.value = questions.value.map((question) => ({
    ...question,
    selected: selectedQuestionIds.value.includes(Number(question.id)),
  }))
}

async function loadDictionaries() {
  const [typeList, difficultyList] = await Promise.all([
    listPaperDictionaries('question_type'),
    listPaperDictionaries('difficulty'),
  ])
  types.value = ['全部', ...(typeList || []).map((item) => item.name)]
  difficulties.value = ['全部', ...(difficultyList || []).map((item) => item.name)]
}

async function loadPaper() {
  if (!paperId.value) return
  const data = await listPaperQuestions(paperId.value)
  applyPaperState(data)
}

async function loadSource() {
  bankId.value = null
  questions.value = []
  loading.value = true
  try {
    if (source.value === 'favorite') {
      await loadQuestions()
      return
    }
    banks.value = await listPaperBanks({ visibility: source.value, keyword: keyword.value }) || []
  } finally {
    loading.value = false
  }
}

async function loadQuestions() {
  loading.value = true
  try {
    const params = {
      paperId: paperId.value,
      keyword: keyword.value,
      type: type.value,
      difficulty: difficulty.value,
    }
    const list = source.value === 'favorite'
      ? await listFavoriteQuestions(params)
      : await listQuestions(bankId.value, params)
    questions.value = list || []
    syncQuestionSelection()
  } finally {
    loading.value = false
  }
}

function switchSource(next) {
  if (next === source.value) return
  source.value = next
  bankId.value = null
  keyword.value = ''
  type.value = ''
  difficulty.value = ''
  loadSource()
}

function openBank(bank) {
  bankId.value = bank.id
  keyword.value = ''
  loadQuestions()
}

function search() {
  if (showBanks.value) loadSource()
  else loadQuestions()
}

function selectFilter(option) {
  const value = option === '全部' ? '' : option
  if (filterPanel.value === 'type') type.value = value
  else difficulty.value = value
  filterPanel.value = ''
  loadQuestions()
}

function isFilterSelected(option) {
  const value = option === '全部' ? '' : option
  return filterPanel.value === 'type' ? type.value === value : difficulty.value === value
}

async function togglePaperQuestion(question) {
  if (!paperId.value) {
    window.alert('请先创建试卷')
    return
  }
  busyId.value = question.id
  try {
    if (question.selected) {
      await removePaperQuestion(paperId.value, question.id)
    } else {
      await addPaperQuestion(paperId.value, {
        questionId: question.id,
        score: 5,
        questionOrder: Number(paper.value.questionCount || 0) + 1,
        sourceType: source.value,
        sourceId: question.bankId,
      })
    }
    await loadPaper()
    await loadQuestions()
  } finally {
    busyId.value = null
  }
}

async function toggleFavorite(question) {
  if (question.favorited) await unfavoriteQuestion(question.id)
  else await favoriteQuestion(question.id)
  question.favorited = !question.favorited
}

function viewSelected() {
  router.push({ path: '/paper/selected', query: { paperId: paperId.value } })
}

function openDetail(question) {
  router.push({
    path: `/paper/questions/${question.id}`,
    query: paperId.value ? { paperId: paperId.value } : undefined,
  })
}

function manageBanks() {
  router.push('/paper/banks')
}

watch(() => route.query.paperId, (value) => {
  paperId.value = value || null
  loadPaper()
})

onMounted(async () => {
  await loadDictionaries()
  await loadPaper()
  await loadSource()
})
</script>

<template>
  <PaperPageShell title="选择试题" back-to="/paper/info" :show-steps="true" :step="1">
    <div v-if="paperId" class="summary paper-card">
      <div>
        <strong>{{ paper.name }}</strong>
        <span>已选 {{ paper.questionCount || 0 }} 题 · {{ paper.totalScore || 0 }} 分</span>
      </div>
      <button class="paper-link" type="button" @click="viewSelected">查看已选</button>
    </div>

    <div class="source-tabs">
      <button
        v-for="tab in sourceTabs"
        :key="tab.value"
        type="button"
        :class="{ active: source === tab.value }"
        @click="switchSource(tab.value)"
      >
        {{ tab.label }}
      </button>
    </div>

    <button v-if="source === 'private' && !bankId" class="bank-manage" type="button" @click="manageBanks">
      管理我的题库组 ›
    </button>

    <div class="filters">
      <input v-model="keyword" :placeholder="source === 'favorite' ? '搜索收藏题目' : '搜索题库或题目'" @keyup.enter="search" />
      <template v-if="showQuestions">
        <button class="chip" type="button" @click="filterPanel = 'type'">题型{{ type ? `：${type}` : '' }}</button>
        <button class="chip" type="button" @click="filterPanel = 'difficulty'">难度{{ difficulty ? `：${difficulty}` : '' }}</button>
      </template>
    </div>

    <div v-if="loading" class="paper-state">正在加载…</div>

    <template v-else-if="showBanks">
      <button v-for="bank in banks" :key="bank.id" class="paper-card bank-card" type="button" @click="openBank(bank)">
        <div>
          <strong>{{ bank.name }}</strong>
          <span>{{ bank.description || (source === 'public' ? '公共题库' : '我的题库组') }}</span>
        </div>
        <em>{{ bank.questionCount || 0 }} 题 ›</em>
      </button>
      <div v-if="!banks.length" class="paper-empty">暂无{{ source === 'public' ? '公共题库' : '私有题库' }}</div>
    </template>

    <template v-else>
      <article v-for="question in questions" :key="question.id" class="paper-card question-card" :class="{ chosen: question.selected }">
        <div class="tags">
          <span>{{ question.questionType }}</span>
          <span>{{ question.difficulty }}</span>
          <span v-if="question.chapter">{{ question.chapter }}</span>
        </div>
        <p>{{ question.content }}</p>
        <small>{{ question.knowledgePoint || question.bankName }}</small>
        <div class="actions">
          <button type="button" @click="openDetail(question)">查看详情</button>
          <button type="button" @click="toggleFavorite(question)">{{ question.favorited ? '★ 已收藏' : '☆ 收藏' }}</button>
          <button
            type="button"
            :disabled="busyId === question.id"
            :class="{ added: question.selected }"
            @click="togglePaperQuestion(question)"
          >
            {{ question.selected ? '移出试卷' : '加入试卷' }}
          </button>
        </div>
      </article>
      <div v-if="!questions.length" class="paper-empty">暂无题目</div>
    </template>

    <footer v-if="paperId" class="paper-bottom">
      <span>已选 {{ paper.questionCount || 0 }} 题</span>
      <button class="paper-btn paper-btn--primary" type="button" @click="viewSelected">查看已选题目</button>
    </footer>

    <div v-if="filterPanel" class="paper-mask" @click.self="filterPanel = ''">
      <div class="paper-modal">
        <div class="paper-modal__head">
          <h2>{{ filterPanel === 'type' ? '选择题型' : '选择难度' }}</h2>
          <button class="paper-modal__close" type="button" @click="filterPanel = ''">×</button>
        </div>
        <div class="filter-options">
          <button
            v-for="option in currentFilterOptions"
            :key="option"
            type="button"
            class="filter-option"
            :class="{ selected: isFilterSelected(option) }"
            @click="selectFilter(option)"
          >
            {{ option }}
          </button>
        </div>
      </div>
    </div>
  </PaperPageShell>
</template>

<style scoped>
@import './paper.css';

.summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.summary span {
  display: block;
  margin-top: 6px;
  color: #929dad;
  font-size: 13px;
}

.source-tabs {
  display: flex;
  margin: 16px 0;
  border-bottom: 1px solid #e8edf5;
  background: #fff;
}

.source-tabs button {
  flex: 1;
  padding: 14px 0;
  color: #7d899a;
  background: transparent;
  border-bottom: 2px solid transparent;
}

.source-tabs button.active {
  color: #4775e5;
  border-bottom-color: #4775e5;
  font-weight: 700;
}

.bank-manage {
  display: block;
  width: 100%;
  margin-bottom: 12px;
  padding: 10px 0;
  color: #4775e5;
  background: #eef5ff;
  text-align: right;
  font-size: 13px;
}

.filters {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
}

.filters input {
  flex: 1;
  padding: 10px 14px;
  border: 1px solid #e0e6ed;
  border-radius: 8px;
  background: #fff;
}

.chip {
  padding: 10px 14px;
  border: 1px solid #cfe0ff;
  border-radius: 999px;
  color: #26354c;
  background: #fff;
  white-space: nowrap;
}

.bank-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  text-align: left;
}

.bank-card span {
  display: block;
  margin-top: 6px;
  color: #8493a8;
  font-size: 13px;
}

.bank-card em {
  color: #1e6bb8;
  font-style: normal;
}

.question-card p {
  margin: 12px 0;
  line-height: 1.6;
}

.question-card small {
  color: #929dad;
}

.tags span {
  display: inline-block;
  margin-right: 8px;
  padding: 4px 8px;
  border-radius: 6px;
  color: #416a91;
  background: #eef5fb;
  font-size: 12px;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  margin-top: 12px;
}

.actions button:last-child {
  margin-left: auto;
  padding: 8px 14px;
  border-radius: 8px;
  color: #fff;
  background: #1e6bb8;
}

.actions button.added {
  color: #667085;
  background: #edf2f7;
}

.chosen {
  border-color: #8ab3d6;
}

.filter-options {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
}

.filter-option {
  padding: 14px;
  border: 1px solid transparent;
  border-radius: 10px;
  background: #f5f8fc;
}

.filter-option.selected {
  border-color: #79a1f2;
  color: #3768cf;
  background: #eaf2ff;
  font-weight: 600;
}
</style>
