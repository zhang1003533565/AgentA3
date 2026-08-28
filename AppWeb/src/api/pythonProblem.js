import request from '../utils/request'

const base = '/api/python-problem'

/** 管理端题目列表（支持关键字 / 难度 / 上下架筛选） */
export const getPythonProblemAdminList = (params) =>
  request.get(`${base}/admin/list`, { params })

/** 新增题目 */
export const createPythonProblem = (data) => request.post(base, data)

/** 编辑题目 */
export const updatePythonProblem = (id, data) => request.put(`${base}/${id}`, data)

/** 删除题目 */
export const deletePythonProblem = (id) => request.delete(`${base}/${id}`)

/** AI 生成题目（管理端）：按主题/难度/数量生成草案，返回预览列表 */
export const aiGeneratePythonProblems = (data) =>
  request.post(`${base}/ai-generate`, data, { timeout: 180000 })
