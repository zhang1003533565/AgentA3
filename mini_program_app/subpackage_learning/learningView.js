export const LEARNING_PAGE_STATES = Object.freeze([
  'loading',
  'ready',
  'empty',
  'dependency_unavailable',
  'network_error',
  'generation_failed'
])

export const PYTHON_PROFILE_QUESTIONS = Object.freeze([
  {
    id: 'python_goal',
    title: '你希望用 Python 完成什么目标？',
    placeholder: '例如：掌握数据分析，能够独立完成课程项目'
  },
  {
    id: 'python_level',
    title: '你目前的 Python 基础如何？',
    placeholder: '例如：刚入门；学过语法但项目经验较少'
  },
  {
    id: 'python_weak_topic',
    title: '目前最容易卡住的知识点是什么？',
    placeholder: '例如：列表切片、函数参数、面向对象'
  },
  {
    id: 'python_resource_preference',
    title: '你更喜欢哪种学习材料？',
    placeholder: '例如：图解、短讲义、练习题、代码实验或 PPT'
  },
  {
    id: 'python_weekly_time',
    title: '你每周能安排多少学习时间？',
    placeholder: '例如：每周 4 小时，工作日晚间学习'
  }
])

const STATE_COPY = Object.freeze({
  loading: { title: '正在同步学习数据', description: '系统正在读取真实学习状态，请稍候。', action: '' },
  ready: { title: '学习数据已就绪', description: '', action: '' },
  empty: { title: '还没有学习数据', description: '先补充学习目标，或生成第一份个性化资源包。', action: '开始设置' },
  dependency_unavailable: { title: '学习依赖暂不可用', description: '课程知识库或生成服务暂时不可用，可稍后重试。', action: '重新检查' },
  network_error: { title: '网络连接失败', description: '未能连接学习服务，已保留当前页面内容。', action: '重新加载' },
  generation_failed: { title: '部分资源生成失败', description: '已成功的内容仍可使用，失败资源可以单独重试。', action: '重试失败项' }
})

function text(value) {
  return String(value || '').trim()
}

export function stateCopy(state, detail = '') {
  const value = STATE_COPY[LEARNING_PAGE_STATES.includes(state) ? state : 'network_error']
  return {
    ...value,
    description: text(detail) || value.description
  }
}

export function classifyLearningError(error, fallback = 'network_error') {
  const status = Number(error?.statusCode || error?.status || error?.data?.status)
  const code = text(error?.data?.code || error?.code).toLowerCase()
  const message = learningErrorMessage(error).toLowerCase()
  if (status === 503 || /dependency|unavailable|knowledge.?base|知识库|依赖|模型服务/.test(`${code} ${message}`)) {
    return 'dependency_unavailable'
  }
  if (/generation[_ -]?failed|agent[_ -]?failed|生成失败/.test(`${code} ${message}`)) {
    return 'generation_failed'
  }
  if (/timeout|network|request:fail|连接|网络|fetch/.test(message)) return 'network_error'
  return LEARNING_PAGE_STATES.includes(fallback) ? fallback : 'network_error'
}

export function learningErrorMessage(error, fallback = '') {
  return text(error?.data?.msg || error?.data?.message || error?.msg || error?.errMsg || error?.message || fallback)
}

export function buildQueryString(params = {}) {
  return Object.entries(params)
    .filter(([, value]) => value !== undefined && value !== null && String(value) !== '')
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`)
    .join('&')
}

export function learningResourceReviewStatus(resource = {}, failed = false) {
  if (failed) return 'generation_failed'
  const grounding = text(resource.groundingStatus || resource.evidenceChain?.status).toLowerCase()
  if (grounding === 'model_only') return 'model_only'
  const review = text(resource.metadata?.reviewStatus || resource.reviewStatus).toLowerCase()
  return review || grounding || 'pending'
}

export function responseData(response) {
  return response?.data === undefined ? response : response.data
}

export function asList(value, keys = []) {
  if (Array.isArray(value)) return value
  for (const key of keys) {
    if (Array.isArray(value?.[key])) return value[key]
  }
  return []
}

export function displayPercent(value) {
  const number = Number(value)
  if (!Number.isFinite(number)) return 0
  const normalized = number > 0 && number <= 1 ? number * 100 : number
  return Math.max(0, Math.min(100, Math.round(normalized)))
}
