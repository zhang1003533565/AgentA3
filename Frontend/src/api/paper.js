import { API_BASE_URL, request } from './request'
import { getToken } from '../utils/auth'

const data = (promise) => promise.then((response) => response.data)

export const listPaperDictionaries = (type) => data(request({
  url: '/api/papers/dictionaries',
  params: { type },
}))

export const createPaperDictionary = (payload) => request({
  url: '/api/papers/dictionaries',
  method: 'POST',
  data: payload,
})

export const deletePaperDictionary = (id) => request({
  url: `/api/papers/dictionaries/${id}`,
  method: 'DELETE',
})

export const listPaperBanks = (params = {}) => data(request({
  url: '/api/papers/banks',
  params,
}))

export const createPaperBank = (payload) => data(request({
  url: '/api/papers/banks',
  method: 'POST',
  data: payload,
}))

export const updatePaperBank = (id, payload) => data(request({
  url: `/api/papers/banks/${id}`,
  method: 'PUT',
  data: payload,
}))

export const deletePaperBank = (id) => request({
  url: `/api/papers/banks/${id}`,
  method: 'DELETE',
})

export const getPaperBank = (id) => data(request({
  url: `/api/papers/banks/${id}`,
}))

export const addQuestionToBank = (bankId, questionId) => request({
  url: `/api/papers/banks/${bankId}/questions/${questionId}`,
  method: 'POST',
})

export const removeQuestionFromBank = (bankId, questionId) => request({
  url: `/api/papers/banks/${bankId}/questions/${questionId}`,
  method: 'DELETE',
})

export const listQuestions = (bankId, params = {}) => data(request({
  url: `/api/papers/banks/${bankId}/questions`,
  params,
}))

export const listPublicQuestions = (params = {}) => data(request({
  url: '/api/papers/banks/public/questions',
  params,
}))

export const listFavoriteQuestions = (params = {}) => data(request({
  url: '/api/papers/favorites',
  params,
}))

export const favoriteQuestion = (id) => request({
  url: `/api/papers/questions/${id}/favorite`,
  method: 'POST',
})

export const unfavoriteQuestion = (id) => request({
  url: `/api/papers/questions/${id}/favorite`,
  method: 'DELETE',
})

export const getQuestionDetail = (id, paperId) => data(request({
  url: `/api/papers/questions/${id}`,
  params: paperId ? { paperId } : undefined,
}))

export const listPapers = (params = {}) => data(request({
  url: '/api/papers',
  params,
}))

export const createPaper = (payload) => data(request({
  url: '/api/papers',
  method: 'POST',
  data: payload,
}))

export const getPaper = (id) => data(request({
  url: `/api/papers/${id}`,
}))

export const listPaperQuestions = (id) => data(request({
  url: `/api/papers/${id}/questions`,
}))

export const updatePaper = (id, payload) => data(request({
  url: `/api/papers/${id}`,
  method: 'PUT',
  data: payload,
}))

export const addPaperQuestion = (id, payload) => data(request({
  url: `/api/papers/${id}/questions`,
  method: 'POST',
  data: payload,
}))

export const updatePaperQuestion = (paperId, questionId, payload) => data(request({
  url: `/api/papers/${paperId}/questions/${questionId}`,
  method: 'PUT',
  data: payload,
}))

export const removePaperQuestion = (paperId, questionId) => data(request({
  url: `/api/papers/${paperId}/questions/${questionId}`,
  method: 'DELETE',
}))

export const completePaper = (id) => data(request({
  url: `/api/papers/${id}/complete`,
  method: 'POST',
}))

export const copyPaper = (id) => data(request({
  url: `/api/papers/${id}/copy`,
  method: 'POST',
}))

export const deletePaper = (id) => request({
  url: `/api/papers/${id}`,
  method: 'DELETE',
})

export const getPaperLayout = (id, defaults = false, templateName = '') => data(request({
  url: `/api/papers/${id}/layout`,
  params: defaults ? { defaults: true, templateName } : undefined,
}))

export const updatePaperLayout = (id, payload) => data(request({
  url: `/api/papers/${id}/layout`,
  method: 'PUT',
  data: payload,
}))

export function paperExportUrl(paperId, type = 'pdf', answers = false) {
  const query = answers ? '?answers=true' : ''
  return `${API_BASE_URL}/api/papers/${paperId}/export/${type}${query}`
}

export async function downloadPaperExport(paperId, type = 'pdf', answers = false, fileName = '') {
  const url = paperExportUrl(paperId, type, answers)
  const response = await fetch(url, {
    headers: { Authorization: `Bearer ${getToken()}` },
  })
  if (!response.ok) throw new Error(`导出失败：${response.status}`)
  const blob = await response.blob()
  const name = fileName || `${type === 'pdf' ? '试卷.pdf' : '试卷.docx'}`
  const href = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = href
  link.download = name
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(href)
}
