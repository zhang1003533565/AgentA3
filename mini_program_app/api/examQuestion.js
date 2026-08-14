import { request } from '../utils/request.js'

/** 系统题库列表（当前用户可见：公共 + 自己的私有） */
export function listExamQuestions({ current = 1, size = 20, type, difficulty, keyword } = {}) {
  return request({
    url: '/api/exam/questions',
    method: 'GET',
    data: {
      current,
      size,
      ...(type ? { type } : {}),
      ...(difficulty ? { difficulty } : {}),
      ...(keyword ? { keyword } : {})
    }
  })
}
