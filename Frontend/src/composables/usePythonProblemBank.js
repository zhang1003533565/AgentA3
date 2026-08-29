import { computed, ref } from 'vue'
import { getPythonProblemList } from '../api/pythonProblem'
import { DIFFICULTY_LABELS, PROGRESS_STORAGE_KEY } from '../utils/pythonOnlineIde'

export function getSolvedIds() {
  try {
    return JSON.parse(localStorage.getItem(PROGRESS_STORAGE_KEY) || '[]')
  } catch {
    return []
  }
}

export function usePythonProblemBank() {
  const questions = ref([])
  const loading = ref(false)
  const loadError = ref(false)
  const searchKeyword = ref('')
  const activeDifficulty = ref('all')
  const activeStatus = ref('all')
  const activeTags = ref([])

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

  function difficultyLabel(d) {
    return DIFFICULTY_LABELS[d] || d
  }

  function toggleTag(name) {
    const idx = activeTags.value.indexOf(name)
    if (idx === -1) activeTags.value.push(name)
    else activeTags.value.splice(idx, 1)
  }

  function clearFilters() {
    searchKeyword.value = ''
    activeDifficulty.value = 'all'
    activeStatus.value = 'all'
    activeTags.value = []
  }

  async function loadProblems() {
    loadError.value = false
    if (questions.value.length === 0) loading.value = true
    try {
      const res = await getPythonProblemList()
      const list = (res && res.data) || []
      const solvedSet = Object.fromEntries(getSolvedIds().map((id) => [id, true]))
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

  function findProblemIndex(id) {
    return questions.value.findIndex((q) => q.id === id)
  }

  function findAdjacentId(id, direction) {
    const list = questions.value
    const index = list.findIndex((q) => q.id === id)
    if (index < 0) return null
    const next = list[index + direction]
    return next ? next.id : null
  }

  return {
    questions,
    loading,
    loadError,
    searchKeyword,
    activeDifficulty,
    activeStatus,
    activeTags,
    allTags,
    doneCount,
    totalCount,
    judgeableTotal,
    unjudgeableTotal,
    progressPercent,
    difficultyOptions,
    statusOptions,
    filteredQuestions,
    difficultyLabel,
    toggleTag,
    clearFilters,
    loadProblems,
    findProblemIndex,
    findAdjacentId,
  }
}
