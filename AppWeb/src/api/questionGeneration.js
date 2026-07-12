import request from '../utils/request'

const base = '/api/exam/question-generation'

export const getQuestionGenerationOptions = () => request.get(`${base}/options`)

export const generateQuestions = (data) => request.post(`${base}/generate`, data, {
  headers: { 'Content-Type': 'multipart/form-data' },
  timeout: 120000,
})

export const importGeneratedQuestions = (data) => request.post(`${base}/import`, data, { timeout: 60000 })
