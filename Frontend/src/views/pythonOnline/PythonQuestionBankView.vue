<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppTabBar from '../../components/AppTabBar.vue'
import { getPythonProblemList } from '../../api/pythonProblem'
import { DIFFICULTY_LABELS, PROGRESS_STORAGE_KEY } from '../../utils/pythonOnlineIde'

const router = useRouter()

const questions = ref([])
const searchKeyword = ref('')
const activeDifficulty = ref('all')
const activeStatus = ref('all')
const activeTags = ref([])
const searchFocused = ref(false)
const loading = ref(false)
const loadError = ref(false)

const allTags = computed(() => {
  const map = {}
  questions.value.forEach((q) => {
    q.tags.forEach((t) => {
      map[t] = (map[t] || 0) + 1
    })
  })
  return Object.keys(map)
    .map((k) => ({ name: k, count: map[k] }))
    .sort((a, b) => b.count - a.count)
})

const difficultyOptions = computed(() => [
  { key: 'all', label: '全部', count: totalCount.value },
  { key: 'easy', label: '简单', count: easyTotal.value },
  { key: 'medium', label: '中等', count: mediumTotal.value },
  { key: 'hard', label: '困难', count: hardTotal.value },
])

const statusOptions = computed(() => [
  { key: 'all', label: '全部', count: totalCount.value },
  { key: 'done', label: '已完成', count: doneCount.value },
  { key: 'undone', label: '未完成', count: totalCount.value - doneCount.value },
])

const filteredQuestions = computed(() => {
  let list = questions.value
  if (activeDifficulty.value !== 'all') {
    list = list.filter((q) => q.difficulty === activeDifficulty.value)
  }
  if (activeStatus.value === 'done') {
    list = list.filter((q) => q.done)
  } else if (activeStatus.value === 'undone') {
    list = list.filter((q) => !q.done)
  }
  if (activeTags.value.length > 0) {
    const tags = activeTags.value
    list = list.filter((q) => tags.some((t) => q.tags.includes(t)))
  }
  if (searchKeyword.value.trim()) {
    const kw = searchKeyword.value.trim().toLowerCase()
    list = list.filter((q) =>
      q.title.toLowerCase().includes(kw)
      || q.tags.some((t) => t.toLowerCase().includes(kw))
      || String(q.number).includes(kw),
    )
  }
  return list
})

const doneCount = computed(() => questions.value.filter((q) => q.done).length)
const totalCount = computed(() => questions.value.length)
const judgeableTotal = computed(() => questions.value.filter((q) => q.judgeable).length)
const unjudgeableTotal = computed(() => totalCount.value - judgeableTotal.value)
const easyTotal = computed(() => questions.value.filter((q) => q.difficulty === 'easy').length)
const mediumTotal = computed(() => questions.value.filter((q) => q.difficulty === 'medium').length)
const hardTotal = computed(() => questions.value.filter((q) => q.difficulty === 'hard').length)
const progressPercent = computed(() => {
  if (judgeableTotal.value === 0) return 0
  return Math.round(doneCount.value / judgeableTotal.value * 100)
})

function getSolvedIds() {
  try {
    return JSON.parse(localStorage.getItem(PROGRESS_STORAGE_KEY) || '[]')
  } catch {
    return []
  }
}

async function loadProblems() {
  loadError.value = false
  if (questions.value.length === 0) {
    loading.value = true
  }
  try {
    const res = await getPythonProblemList()
    const list = (res && res.data) || []
    const solved = getSolvedIds()
    const solvedSet = Object.fromEntries(solved.map((id) => [id, true]))
    questions.value = list.map((p) => ({
      ...p,
      done: !!solvedSet[p.id],
      judgeable: !!p.judgeable,
    }))
  } catch (e) {
    console.error('加载题库失败:', e)
    loadError.value = true
  }
  loading.value = false
}

function difficultyLabel(d) {
  return DIFFICULTY_LABELS[d] || d
}

function focusSearch() {
  searchFocused.value = true
}

function clearSearch() {
  searchKeyword.value = ''
}

function selectDifficulty(key) {
  activeDifficulty.value = key
}

function selectStatus(key) {
  activeStatus.value = key
}

function toggleTag(name) {
  const idx = activeTags.value.indexOf(name)
  if (idx === -1) activeTags.value.push(name)
  else activeTags.value.splice(idx, 1)
}

function goToDetail(id) {
  router.push(`/learning/problems/${id}`)
}

onMounted(loadProblems)
</script>

<template>
  <div class="feature-page">
    <AppTabBar />
    <div class="question-bank-page">
      <header class="page-header">
        <h1 class="page-title">题库</h1>
        <router-link class="plan-link" to="/learning/plan">学习规划</router-link>
      </header>

      <div class="search-bar">
        <div class="search-bar-placeholder" @click="focusSearch">
          <img class="search-icon" src="/icons/search.svg" alt="" />
          <input
            v-model="searchKeyword"
            class="search-input"
            placeholder="搜索题目、标签或题号..."
            type="search"
            :autofocus="searchFocused"
            @blur="searchFocused = false"
          />
          <div v-if="searchKeyword" class="search-clear" @click.stop="clearSearch">×</div>
        </div>
      </div>

      <div v-if="loading && questions.length === 0" class="state-block">
        <span class="empty-state-text">加载中...</span>
      </div>
      <div v-else-if="loadError && questions.length === 0" class="state-block">
        <span class="empty-state-text">题库加载失败</span>
        <span class="empty-state-hint">请检查网络连接后重试</span>
        <div class="retry-btn" @click="loadProblems">
          <span class="retry-btn-text">重新加载</span>
        </div>
      </div>
      <div v-else class="bank-content">
        <div class="progress-card">
          <div class="progress-header">
            <span class="progress-label">当前进度</span>
            <span class="progress-sub">{{ doneCount }}/{{ judgeableTotal }} 已解决</span>
          </div>
          <div class="progress-track">
            <div class="progress-fill" :style="{ width: progressPercent + '%' }" />
          </div>
          <span class="progress-note">共 {{ totalCount }} 题，其中 {{ unjudgeableTotal }} 题暂不支持在线判题，不计入进度</span>
          <div class="progress-stats">
            <div class="stat-item" @click="selectDifficulty('easy')">
              <div class="stat-dot stat-dot--easy" />
              <span class="stat-text">简单</span>
              <span class="stat-num">{{ easyTotal }}</span>
            </div>
            <div class="stat-item" @click="selectDifficulty('medium')">
              <div class="stat-dot stat-dot--medium" />
              <span class="stat-text">中等</span>
              <span class="stat-num">{{ mediumTotal }}</span>
            </div>
            <div class="stat-item" @click="selectDifficulty('hard')">
              <div class="stat-dot stat-dot--hard" />
              <span class="stat-text">困难</span>
              <span class="stat-num">{{ hardTotal }}</span>
            </div>
          </div>
        </div>

        <div class="filter-section">
          <div class="filter-label">难度</div>
          <div class="filter-scroll">
            <div class="filter-row">
              <div
                v-for="d in difficultyOptions"
                :key="d.key"
                class="filter-chip"
                :class="{ 'filter-chip--active': activeDifficulty === d.key }"
                @click="selectDifficulty(d.key)"
              >
                <span>{{ d.label }}</span>
                <span v-if="d.count !== null" class="filter-chip-count">{{ d.count }}</span>
              </div>
            </div>
          </div>
        </div>

        <div class="filter-section">
          <div class="filter-label">状态</div>
          <div class="filter-scroll">
            <div class="filter-row">
              <div
                v-for="s in statusOptions"
                :key="s.key"
                class="filter-chip"
                :class="{ 'filter-chip--active': activeStatus === s.key }"
                @click="selectStatus(s.key)"
              >
                <span>{{ s.label }}</span>
                <span v-if="s.count !== null" class="filter-chip-count">{{ s.count }}</span>
              </div>
            </div>
          </div>
        </div>

        <div class="filter-section">
          <div class="filter-label">标签</div>
          <div class="tag-grid">
            <div
              v-for="t in allTags"
              :key="t.name"
              class="tag-chip"
              :class="{ 'tag-chip--active': activeTags.includes(t.name) }"
              @click="toggleTag(t.name)"
            >
              <span>{{ t.name }}</span>
              <span class="tag-chip-count">{{ t.count }}</span>
            </div>
          </div>
        </div>

        <div class="result-header">
          <span class="result-title">题目列表</span>
          <span class="result-count">{{ filteredQuestions.length }} 道</span>
        </div>

        <div class="question-list">
          <div
            v-for="q in filteredQuestions"
            :key="q.id"
            class="question-item"
            @click="goToDetail(q.id)"
          >
            <div class="question-status" :class="{ 'question-status--done': q.done }">
              <span v-if="q.done" class="status-check">✓</span>
              <div v-else class="empty-circle" />
            </div>
            <div class="question-content">
              <div class="question-title-row">
                <span class="question-number">{{ q.number }}.</span>
                <span class="question-title">{{ q.title }}</span>
                <div class="difficulty-tag" :class="'difficulty-tag--' + q.difficulty">{{ difficultyLabel(q.difficulty) }}</div>
              </div>
              <div class="question-meta">
                <span class="meta-item">通过率 {{ q.passRate }}%</span>
                <div class="meta-tags-row">
                  <span
                    v-for="t in q.tags"
                    :key="t"
                    class="meta-tag"
                    @click.stop="toggleTag(t)"
                  >{{ t }}</span>
                </div>
              </div>
              <div class="question-footer">
                <img class="star-icon" src="/icons/star.svg" alt="" />
              </div>
            </div>
          </div>
          <div v-if="filteredQuestions.length === 0" class="empty-state">
            <span class="empty-state-text">未找到匹配的题目</span>
            <span class="empty-state-hint">试试调整筛选条件</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.question-bank-page {
  min-height: calc(100vh - 64px);
  background: #f5f6f8;
  padding-bottom: 30px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 14px 0;
  background: #f5f6f8;
}

.page-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: #111827;
}

.plan-link {
  font-size: 13px;
  color: #4f46e5;
  text-decoration: none;
}

.search-bar {
  display: flex;
  align-items: center;
  padding: 10px 14px;
  background: #f5f6f8;
}

.search-bar-placeholder {
  display: flex;
  align-items: center;
  flex: 1;
  padding: 9px 12px;
  background: #ffffff;
  border-radius: 8px;
  border: 1px solid #eef1f4;
}

.search-icon {
  width: 16px;
  height: 16px;
  margin-right: 7px;
  opacity: 0.4;
  flex-shrink: 0;
}

.search-input {
  flex: 1;
  min-width: 0;
  font-size: 14px;
  color: #111827;
  height: 20px;
  line-height: 20px;
  border: none;
  outline: none;
  background: transparent;
}

.search-input::placeholder {
  color: #9ca3af;
}

.search-clear {
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  color: #9ca3af;
  flex-shrink: 0;
  cursor: pointer;
}

.progress-card {
  margin: 6px 14px 4px;
  padding: 12px 14px;
  background: #ffffff;
  border-radius: 10px;
  border: 1px solid #eef1f4;
}

.progress-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 8px;
}

.progress-label {
  font-size: 13px;
  font-weight: 700;
  color: #111827;
}

.progress-sub {
  font-size: 11.5px;
  color: #6b7280;
}

.progress-track {
  height: 4px;
  background: #e5e7eb;
  border-radius: 999px;
  overflow: hidden;
  margin-bottom: 9px;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #4f46e5, #7c3aed);
  border-radius: 999px;
}

.progress-note {
  display: block;
  font-size: 10.5px;
  color: #9ca3af;
  margin-top: 6px;
  margin-bottom: 3px;
}

.progress-stats {
  display: flex;
  gap: 14px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 3px;
  cursor: pointer;
}

.stat-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
}

.stat-dot--easy { background: #10b981; }
.stat-dot--medium { background: #8b5cf6; }
.stat-dot--hard { background: #ef4444; }

.stat-text {
  font-size: 12px;
  color: #6b7280;
}

.stat-num {
  font-size: 11px;
  color: #9ca3af;
  font-weight: 500;
}

.filter-section {
  padding: 4px 14px 2px;
}

.filter-label {
  font-size: 12px;
  color: #9ca3af;
  margin-bottom: 6px;
  font-weight: 500;
}

.filter-scroll {
  overflow-x: auto;
  white-space: nowrap;
}

.filter-row {
  display: inline-flex;
  gap: 6px;
  padding-bottom: 4px;
}

.filter-chip {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 6px 11px;
  border-radius: 999px;
  font-size: 13px;
  color: #4b5563;
  background: #f3f4f6;
  flex-shrink: 0;
  cursor: pointer;
}

.filter-chip--active {
  background: #4f46e5;
  color: #ffffff;
}

.filter-chip-count {
  font-size: 10px;
  opacity: 0.7;
}

.tag-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding-bottom: 4px;
}

.tag-chip {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 5px 10px;
  border-radius: 999px;
  font-size: 12px;
  color: #4b5563;
  background: #f3f4f6;
  cursor: pointer;
}

.tag-chip--active {
  background: rgba(79, 70, 229, 0.1);
  color: #4f46e5;
  font-weight: 600;
}

.tag-chip-count {
  font-size: 10px;
  opacity: 0.55;
}

.result-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 9px 14px 6px;
}

.result-title {
  font-size: 14px;
  font-weight: 700;
  color: #111827;
}

.result-count {
  font-size: 11.5px;
  color: #9ca3af;
}

.question-list {
  padding: 0 14px;
}

.question-item {
  display: flex;
  align-items: flex-start;
  padding: 14px;
  margin-bottom: 7px;
  background: #ffffff;
  border-radius: 10px;
  border: 1px solid #eef1f4;
  cursor: pointer;
}

.question-status {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  border: 1.5px solid #d1d5db;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 10px;
  flex-shrink: 0;
}

.question-status--done {
  border-color: #10b981;
  background: #10b981;
}

.status-check {
  font-size: 12px;
  color: #fff;
  font-weight: 700;
  line-height: 1;
}

.empty-circle {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  border: 1.5px solid #d1d5db;
}

.question-content {
  flex: 1;
  min-width: 0;
}

.question-title-row {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-bottom: 6px;
}

.question-number {
  font-size: 14px;
  font-weight: 600;
  color: #111827;
  flex-shrink: 0;
}

.question-title {
  font-size: 15px;
  font-weight: 600;
  color: #111827;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.difficulty-tag {
  padding: 1.5px 7px;
  border-radius: 3px;
  font-size: 10.5px;
  font-weight: 600;
  margin-left: auto;
  flex-shrink: 0;
}

.difficulty-tag--easy { background: #d1fae5; color: #065f46; }
.difficulty-tag--medium { background: #ede9fe; color: #5b21b6; }
.difficulty-tag--hard { background: #fecaca; color: #991b1b; }

.question-meta {
  display: flex;
  flex-direction: column;
  gap: 5px;
  margin-bottom: 6px;
}

.meta-item {
  font-size: 12px;
  color: #6b7280;
}

.meta-tags-row {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}

.meta-tag {
  padding: 2px 7px;
  border-radius: 3px;
  font-size: 10.5px;
  color: #6b7280;
  background: #f3f4f6;
  cursor: pointer;
}

.question-footer {
  display: flex;
  align-items: center;
  gap: 8px;
}

.star-icon {
  width: 14px;
  height: 14px;
  opacity: 0.4;
}

.empty-state,
.state-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 14px;
}

.state-block {
  padding: 60px 14px;
}

.retry-btn {
  margin-top: 14px;
  padding: 8px 22px;
  border-radius: 999px;
  background: #ffffff;
  border: 1px solid #dfe3ea;
  cursor: pointer;
}

.retry-btn-text {
  font-size: 13px;
  color: #4b5563;
}

.empty-state-text {
  font-size: 14px;
  color: #9ca3af;
  margin-bottom: 4px;
}

.empty-state-hint {
  font-size: 12px;
  color: #d1d5db;
}
</style>
