import { API_BASE_URL, request } from './request'
import { getToken } from '../utils/auth'

/**
 * 题库列表（公开接口，仅上架题目摘要）
 * @returns {Promise} res.data 为 [{ id, number, title, difficulty, passRate, submissions, tags, judgeable }]
 */
export function getPythonProblemList() {
  return request({
    url: '/api/python-problem/list',
    method: 'GET',
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
  })
}

/**
 * AI 辅助编程（LeetCode 式：分级提示/思路讲解/代码解释/报错分析），SSE 流式
 * @param {Object} data { questionType, problem, userCode, judgeResult, followUp, history }
 * @param {Object} handlers { onStatus, onDelta, onDone, onError }
 */
export function streamPythonAssist(data, handlers = {}) {
  const controller = typeof AbortController === 'function' ? new AbortController() : null
  const done = (async () => {
    if (typeof fetch !== 'function' || !controller) {
      const error = new Error('当前运行环境暂不支持流式 AI 辅导')
      error.fallbackToNormalRequest = true
      throw error
    }

    const token = getToken()
    const response = await fetch(`${API_BASE_URL}/api/python-problem/ai-assist/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify(data || {}),
      signal: controller.signal,
    })

    if (!response.ok) {
      let message = `流式请求失败: ${response.status}`
      try {
        const raw = await response.text()
        if (raw) {
          const parsed = JSON.parse(raw)
          message = parsed.msg || parsed.message || parsed.data?.message || message
        }
      } catch {
        // Keep the status-code fallback when the server returns a non-JSON error body.
      }
      const error = new Error(message)
      error.status = response.status
      throw error
    }
    if (!response.body?.getReader) {
      const error = new Error('当前浏览器无法读取流式响应')
      error.fallbackToNormalRequest = true
      throw error
    }

    const decoder = new TextDecoder('utf-8')
    const reader = response.body.getReader()
    let buffer = ''
    const dispatch = (block) => {
      const lines = block.split(/\r?\n/)
      let eventName = 'message'
      const dataLines = []
      lines.forEach((line) => {
        if (line.startsWith('event:')) {
          eventName = line.slice(6).trim() || 'message'
        } else if (line.startsWith('data:')) {
          dataLines.push(line.slice(5).trim())
        }
      })
      if (!dataLines.length) return

      let payload = dataLines.join('\n')
      try {
        payload = JSON.parse(payload)
      } catch {
        // Text payloads are valid SSE data too.
      }

      if (eventName === 'status') handlers.onStatus?.(payload)
      else if (eventName === 'delta') handlers.onDelta?.(payload?.content || '')
      else if (eventName === 'done') handlers.onDone?.(payload)
      else if (eventName === 'error') handlers.onError?.(payload)
      else handlers.onEvent?.(eventName, payload)
    }

    try {
      while (true) {
        const { value, done: streamDone } = await reader.read()
        if (streamDone) break
        buffer += decoder.decode(value, { stream: true })
        const blocks = buffer.split(/\n\s*\n/)
        buffer = blocks.pop() || ''
        blocks.forEach(dispatch)
      }
      buffer += decoder.decode()
      if (buffer.trim()) dispatch(buffer)
    } finally {
      reader.releaseLock()
    }
  })()

  return {
    controller,
    signal: controller?.signal,
    done,
    abort(reason) {
      if (!controller || controller.signal.aborted) return false
      controller.abort(reason)
      return true
    },
    then(onFulfilled, onRejected) {
      return done.then(onFulfilled, onRejected)
    },
    catch(onRejected) {
      return done.catch(onRejected)
    },
    finally(onFinally) {
      return done.finally(onFinally)
    },
  }
}
