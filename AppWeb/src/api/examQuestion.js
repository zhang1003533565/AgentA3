import request from '../utils/request'

const base = '/api/exam/questions'

export const getExamQuestionList = (params = {}, requestOptions = {}) =>
  request({
    ...requestOptions,
    url: base,
    method: 'get',
    params: {
      current: params.current ?? params.page ?? 1,
      size: params.size ?? 10,
      type: params.type,
      difficulty: params.difficulty,
      keyword: params.keyword,
      bankId: params.bankId,
    },
  })

export const getExamQuestionDetail = (id) =>
  request({
    url: `${base}/${id}`,
    method: 'get',
  })

// 新增单题（待后端提供 POST /api/exam/questions）
export const createExamQuestion = (data) =>
  request({
    url: base,
    method: 'post',
    data,
  })

// 编辑单题（待后端提供 PUT /api/exam/questions/{id}）
export const updateExamQuestion = (id, data) =>
  request({
    url: `${base}/${id}`,
    method: 'put',
    data,
  })

// 删除单题（待后端提供 DELETE /api/exam/questions/{id}）
export const deleteExamQuestion = (id) =>
  request({
    url: `${base}/${id}`,
    method: 'delete',
  })

// 题库选项列表（待后端提供 GET /api/exam/banks，失败由调用方降级处理）
export const getExamBanks = () =>
  request({
    url: '/api/exam/banks',
    method: 'get',
  })

export const reviewExamQuestions = (data, expectedType) =>
  request({
    url: `${base}/review`,
    method: 'post',
    params: { expectedType },
    data,
  })

export const validateExamQuestions = (data, expectedType) =>
  request({
    url: `${base}/validate`,
    method: 'post',
    params: { expectedType },
    data,
  })

export const importExamQuestions = (data, expectedType) =>
  request({
    url: `${base}/import`,
    method: 'post',
    params: { expectedType },
    data,
    timeout: 60000,
  })
