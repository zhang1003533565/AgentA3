/**
 * Python 在线编程题库接口封装
 * 题目数据由后端 python_problem 表提供，支持管理端动态增删改。
 */
import { request } from '@/utils/request.js'
import { streamSse } from './ai.js'

/**
 * 题库列表（公开接口，仅上架题目摘要）
 * @returns {Promise} res.data 为 [{ id, number, title, difficulty, passRate, submissions, tags, judgeable }]
 */
export function getPythonProblemList() {
  return request({
    url: '/api/python-problem/list',
    method: 'GET',
    showError: false
  })
}

/**
 * 题目详情（公开接口，含描述、示例、模板代码与测试用例）
 * @param {number|string} id 题目 id
 */
export function getPythonProblemDetail(id) {
  return request({
    url: '/api/python-problem/detail',
    method: 'GET',
    params: { id },
    showError: false
  })
}

/**
 * AI 辅助编程（LeetCode 式：分级提示/思路讲解/代码解释/报错分析），SSE 流式
 * @param {Object} data { questionType, problem, userCode, judgeResult, followUp, history }
 * @param {Object} handlers { onStatus, onDelta, onDone, onError }
 */
export function streamPythonAssist(data, handlers = {}) {
  return streamSse('/api/python-problem/ai-assist/stream', data, {
    onStatus: handlers.onStatus,
    onDelta: handlers.onDelta,
    onDone: handlers.onDone,
    onError: handlers.onError,
    onEvent: handlers.onEvent
  }, '当前运行环境暂不支持流式 AI 辅导')
}
