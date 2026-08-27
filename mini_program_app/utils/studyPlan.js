const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/

export function todayDate() {
  const now = new Date()
  return toDateInputValue(now)
}

export function toDateInputValue(value) {
  if (!value) return ''
  if (typeof value === 'string' && DATE_PATTERN.test(value.slice(0, 10))) return value.slice(0, 10)
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export function formatPlanDate(value) {
  const date = toDateInputValue(value)
  return date ? date.replace(/-/g, '/') : '未排期'
}

export function addPlanDays(value, days) {
  const date = toDateInputValue(value)
  if (!date) return ''
  const result = new Date(`${date}T00:00:00`)
  result.setDate(result.getDate() + Number(days || 0))
  return toDateInputValue(result)
}

export function schedulePlanTasks(tasks, startDate = todayDate()) {
  let cursor = toDateInputValue(startDate) || todayDate()
  return (Array.isArray(tasks) ? tasks : []).map((task, index) => {
    const estimatedDays = Math.max(1, Number(task?.estimatedDays) || 1)
    const plannedStartDate = cursor
    const plannedEndDate = addPlanDays(cursor, estimatedDays - 1)
    cursor = addPlanDays(cursor, estimatedDays)
    return { ...task, orderNum: index + 1, estimatedDays, plannedStartDate, plannedEndDate }
  })
}

export function isPlanTaskToday(task, date = todayDate()) {
  const start = toDateInputValue(task?.plannedStartDate)
  const end = toDateInputValue(task?.plannedEndDate)
  return Boolean(start && end && start <= date && date <= end)
}

export function normalizePlanTask(task, index = 0) {
  return {
    id: task?.id,
    orderNum: index + 1,
    taskName: String(task?.taskName || '').trim(),
    stage: String(task?.stage || '').trim(),
    estimatedDays: Math.max(1, Number(task?.estimatedDays) || 1),
    priority: ['高', '中', '低'].includes(task?.priority) ? task.priority : '中',
    description: String(task?.description || '').trim(),
    progressPercent: Math.max(0, Math.min(100, Number(task?.progressPercent) || 0)),
    isCompleted: Boolean(task?.isCompleted),
    status: task?.status || 'pending',
    plannedStartDate: toDateInputValue(task?.plannedStartDate),
    plannedEndDate: toDateInputValue(task?.plannedEndDate)
  }
}

export function buildStudyGoalPayload(goal, tasks) {
  return {
    goal: {
      title: String(goal?.title || '').trim(),
      description: String(goal?.description || '').trim(),
      startDate: toDateInputValue(goal?.startDate) || todayDate(),
      targetDate: toDateInputValue(goal?.targetDate) || null,
      dailyStudyMinutes: Math.max(15, Math.min(720, Number(goal?.dailyStudyMinutes) || 60))
    },
    tasks: (Array.isArray(tasks) ? tasks : []).map((task, index) => ({
      taskName: String(task?.taskName || '').trim(),
      stage: String(task?.stage || '').trim(),
      estimatedDays: Math.max(1, Number(task?.estimatedDays) || 1),
      priority: task?.priority || '中',
      orderNum: index + 1,
      isCompleted: false,
      progressPercent: 0,
      description: String(task?.description || '').trim()
    }))
  }
}

export function statusText(status) {
  return {
    pending: '未开始',
    in_progress: '进行中',
    blocked: '受阻',
    skipped: '已跳过',
    completed: '已完成'
  }[status] || '未开始'
}
