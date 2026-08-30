import { request } from '../api/request'

/** 本地存储 key：已解决题目 id 数组（进度持久化） */
export const PROGRESS_STORAGE_KEY = 'py_online_solved'

/** 本地存储 key 前缀：每道题的代码草稿，完整 key 为 CODE_DRAFT_PREFIX + 题目 id */
export const CODE_DRAFT_PREFIX = 'py_online_code_'

export const DIFFICULTY_LABELS = { easy: '简单', medium: '中等', hard: '困难' }

export function makeResult(status, runtime, memory, testcases) {
  let passed = 0
  for (let i = 0; i < testcases.length; i++) {
    if (testcases[i].status === 'pass') passed++
  }
  const labels = {
    ac: '通过',
    wa: '解答错误',
    re: '运行错误',
    ce: '编译错误',
    tle: '执行超时',
    err: '服务异常',
    unsupported: '该题型暂不支持在线判题',
  }
  return {
    status,
    statusLabel: labels[status] || status,
    runtime: Math.round(Number(runtime) || 0),
    memory: parseFloat((Number(memory) || 0).toFixed(1)),
    passed,
    total: testcases.length,
    testcases,
  }
}

export async function executeCodeOnServer(code, problem, testcases) {
  const cases = (testcases && testcases.length) ? testcases : (problem.testcases || [])
  const funcName = problem.funcName || ''

  if (!funcName || !cases.length) {
    return makeResult('unsupported', 0, 0, [])
  }

  const requestBody = {
    code,
    funcName,
    testcases: cases,
  }

  try {
    const data = await request({
      url: '/api/code/execute',
      method: 'POST',
      data: requestBody,
    })

    if (data && data.data) {
      const result = data.data
      return makeResult(
        result.status,
        result.runtime,
        result.memory,
        result.testcases,
      )
    }
    return makeResult('err', 0, 0, [])
  } catch (err) {
    console.error('后端判题请求失败:', err)
    return makeResult('err', 0, 0, [])
  }
}
