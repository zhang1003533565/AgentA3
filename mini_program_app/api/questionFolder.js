import { request } from '../utils/request.js'

const BASE = '/api/exam/question-folders'

export function listQuestionFolders({ visibility, ownerUserId, ownerKeyword } = {}) {
  return request({
    url: BASE,
    method: 'GET',
    data: {
      visibility,
      ...(ownerUserId != null ? { ownerUserId } : {}),
      ...(ownerKeyword ? { ownerKeyword } : {})
    }
  })
}

export function createQuestionFolder(data) {
  return request({ url: BASE, method: 'POST', data })
}

export function renameQuestionFolder(id, data) {
  return request({ url: `${BASE}/${id}`, method: 'PUT', data })
}

export function deleteQuestionFolder(id) {
  return request({ url: `${BASE}/${id}`, method: 'DELETE' })
}

export function getQuestionFolderDetail(id) {
  return request({ url: `${BASE}/${id}`, method: 'GET' })
}

export function listQuestionFolderQuestions(id, { current = 1, size = 20 } = {}) {
  return request({
    url: `${BASE}/${id}/questions`,
    method: 'GET',
    data: { current, size }
  })
}
