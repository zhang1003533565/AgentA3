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
    },
  })

export const getExamQuestionDetail = (id) =>
  request({
    url: `${base}/${id}`,
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
