<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppTabBar from '../../components/AppTabBar.vue'
import { getPythonProblemDetail, getPythonProblemList } from '../../api/pythonProblem'
import { DIFFICULTY_LABELS } from '../../utils/pythonOnlineIde'

const route = useRoute()
const router = useRouter()

const problemId = ref(0)
const problem = ref(null)
const problemMap = ref({})
const loadError = ref(false)

const similarProblems = computed(() => {
  if (!problem.value) return []
  const ids = problem.value.similarIds || []
  const map = problemMap.value
  return ids.map((id) => map[id]).filter(Boolean)
})

async function loadProblem() {
  loadError.value = false
  try {
    const res = await getPythonProblemDetail(problemId.value)
    if (!res || !res.data) {
      loadError.value = true
      return
    }
    problem.value = res.data
    const listRes = await getPythonProblemList()
    const map = {}
    ;((listRes && listRes.data) || []).forEach((p) => {
      map[p.id] = p
    })
    problemMap.value = map
  } catch (e) {
    console.error('加载题目详情失败:', e)
    loadError.value = true
  }
}

function difficultyLabel(d) {
  return DIFFICULTY_LABELS[d] || d
}

function goToProblem(id) {
  router.replace(`/learning/problems/${id}`)
}

function goToPractice() {
  router.push(`/learning/practice/${problemId.value}`)
}

onMounted(() => {
  problemId.value = Number(route.params.id) || 0
  loadProblem()
})
</script>

<template>
  <div class="feature-page">
    <AppTabBar />
    <div class="problem-detail-page">
      <header class="page-header">
        <button class="back-btn" type="button" @click="router.push('/learning')">‹ 返回题库</button>
        <h1 class="page-title">题目详情</h1>
      </header>

      <div v-if="!problem && !loadError" class="detail-state">
        <span class="detail-state-text">加载中...</span>
      </div>
      <div v-else-if="loadError" class="detail-state">
        <span class="detail-state-text">题目详情加载失败</span>
        <span class="detail-state-hint">题目可能不存在，或网络连接异常</span>
        <div class="retry-btn" @click="loadProblem">
          <span class="retry-btn-text">重新加载</span>
        </div>
      </div>
      <template v-else-if="problem">
        <div class="problem-header">
          <div class="problem-title-row">
            <span class="problem-number">{{ problem.number }}.</span>
            <span class="problem-title">{{ problem.title }}</span>
            <div class="difficulty-tag" :class="'difficulty-tag--' + problem.difficulty">{{ difficultyLabel(problem.difficulty) }}</div>
          </div>
          <div class="problem-stats">
            <div class="stat-item">
              <img class="stat-icon" src="/icons/chart.svg" alt="" />
              <span class="stat-text">通过率 {{ problem.passRate }}%</span>
            </div>
            <div class="stat-item">
              <img class="stat-icon" src="/icons/edit-3.svg" alt="" />
              <span class="stat-text">提交数 {{ problem.submissions }}</span>
            </div>
          </div>
        </div>

        <div class="problem-content">
          <span class="problem-desc">{{ problem.description }}</span>

          <div v-for="(ex, ei) in problem.examples" :key="ei" class="example-block">
            <span class="example-title">示例 {{ ei + 1 }}：</span>
            <span v-for="(line, li) in ex.input" :key="'in' + li" class="example-text">输入：{{ line }}</span>
            <span v-for="(line, li) in ex.output" :key="'out' + li" class="example-text">输出：{{ line }}</span>
            <span v-if="ex.explain" class="example-explain">{{ ex.explain }}</span>
          </div>
        </div>

        <div class="tags-section">
          <span class="tags-label">相关标签</span>
          <div class="tags-list">
            <div v-for="t in problem.tags" :key="t" class="tag">{{ t }}</div>
          </div>
        </div>

        <div v-if="similarProblems.length > 0" class="similar-section">
          <span class="similar-title">相似题目</span>
          <div class="similar-list">
            <div
              v-for="s in similarProblems"
              :key="s.id"
              class="similar-item"
              @click="goToProblem(s.id)"
            >
              <span class="similar-name">{{ s.number }}. {{ s.title }}</span>
              <div class="difficulty-tag" :class="'difficulty-tag--' + s.difficulty">{{ difficultyLabel(s.difficulty) }}</div>
            </div>
          </div>
        </div>

        <div class="bottom-action">
          <button class="practice-btn" type="button" @click="goToPractice">
            <img class="btn-icon" src="/icons/edit-3.svg" alt="" />
            <span class="btn-text">去编程</span>
          </button>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.problem-detail-page {
  min-height: calc(100vh - 64px);
  background: #ffffff;
  padding-bottom: 70px;
}

.page-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border-bottom: 1px solid #f3f4f6;
}

.back-btn {
  border: none;
  background: transparent;
  font-size: 14px;
  color: #4f46e5;
  cursor: pointer;
  padding: 0;
}

.page-title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: #111827;
}

.detail-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 14px;
}

.detail-state-text {
  font-size: 14px;
  color: #9ca3af;
}

.detail-state-hint {
  font-size: 12px;
  color: #d1d5db;
  margin-top: 4px;
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

.problem-header {
  padding: 16px 14px;
  border-bottom: 1px solid #f3f4f6;
}

.problem-title-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 10px;
}

.problem-number {
  font-size: 16px;
  font-weight: 600;
  color: #111827;
}

.problem-title {
  font-size: 18px;
  font-weight: 700;
  color: #111827;
}

.difficulty-tag {
  padding: 3px 10px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
  margin-left: auto;
}

.difficulty-tag--easy { background: #d1fae5; color: #065f46; }
.difficulty-tag--medium { background: #ede9fe; color: #5b21b6; }
.difficulty-tag--hard { background: #fecaca; color: #991b1b; }

.problem-stats {
  display: flex;
  gap: 16px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.stat-icon {
  width: 16px;
  height: 16px;
  opacity: 0.6;
}

.stat-text {
  font-size: 13px;
  color: #6b7280;
}

.problem-content {
  padding: 16px 14px;
}

.problem-desc {
  display: block;
  font-size: 14px;
  color: #374151;
  line-height: 1.8;
  white-space: pre-line;
}

.example-block {
  margin-top: 14px;
  padding: 12px;
  background: #f9fafb;
  border-radius: 8px;
  border-left: 3px solid #4f46e5;
}

.example-title {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: #111827;
  margin-bottom: 6px;
}

.example-text {
  display: block;
  font-size: 13px;
  color: #374151;
  font-family: 'Courier New', monospace;
  line-height: 1.6;
}

.example-explain {
  display: block;
  font-size: 13px;
  color: #6b7280;
  margin-top: 6px;
}

.tags-section {
  padding: 0 14px 12px;
}

.tags-label {
  display: block;
  font-size: 13px;
  color: #6b7280;
  margin-bottom: 8px;
}

.tags-list {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.tag {
  padding: 4px 10px;
  background: #f3f4f6;
  border-radius: 999px;
  font-size: 12px;
  color: #4b5563;
}

.similar-section {
  padding: 12px 14px;
  border-top: 1px solid #f3f4f6;
}

.similar-title {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: #111827;
  margin-bottom: 10px;
}

.similar-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.similar-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  background: #f9fafb;
  border-radius: 8px;
  cursor: pointer;
}

.similar-item:active {
  background: #f3f4f6;
}

.similar-name {
  font-size: 13px;
  color: #374151;
}

.bottom-action {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 10px 14px;
  background: #ffffff;
  box-shadow: 0 -2px 10px rgba(0, 0, 0, 0.05);
}

.practice-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  height: 48px;
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
  border-radius: 24px;
  border: none;
  color: #ffffff;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
}

.btn-icon {
  width: 18px;
  height: 18px;
  filter: brightness(0) invert(1);
}

.btn-text {
  color: #ffffff;
}
</style>
