import { request } from '../utils/request.js'
import { BASE_URL } from '../utils/config.js'
import { getToken } from '../utils/storage.js'

export function getExamPapers(params = {}) {
  return request({ url: '/api/app/exam-papers', method: 'GET', data: params })
}

export function getExamPaperDetail(paperId) {
  return request({ url: `/api/app/exam-papers/${encodeURIComponent(paperId)}`, method: 'GET' })
}

export function startExamAttempt(paperId) {
  return request({ url: `/api/app/exam-papers/${encodeURIComponent(paperId)}/attempts`, method: 'POST' })
}

export function getExamAttempt(attemptId) {
  return request({ url: `/api/app/exam-attempts/${encodeURIComponent(attemptId)}`, method: 'GET' })
}

export function saveExamAnswer(attemptId, paperQuestionId, data) {
  return request({
    url: `/api/app/exam-attempts/${encodeURIComponent(attemptId)}/answers/${encodeURIComponent(paperQuestionId)}`,
    method: 'PUT',
    data
  })
}

export function submitExamAttempt(attemptId) {
  return request({ url: `/api/app/exam-attempts/${encodeURIComponent(attemptId)}/submit`, method: 'POST' })
}

export function getExamAttemptHistory(paperId) {
  return request({ url: `/api/app/exam-papers/${encodeURIComponent(paperId)}/attempts`, method: 'GET' })
}

export function getExamAttemptResult(attemptId) {
  return request({ url: `/api/app/exam-attempts/${encodeURIComponent(attemptId)}/result`, method: 'GET' })
}

export function downloadExamPaperPdf(paperId) {
  const token = getToken()
  return new Promise((resolve, reject) => {
    uni.downloadFile({
      url: `${BASE_URL}/api/app/exam-papers/${encodeURIComponent(paperId)}/pdf`,
      header: token ? { Authorization: `Bearer ${token}` } : {},
      success: (download) => {
        if (download.statusCode < 200 || download.statusCode >= 300) {
          reject(new Error(`试卷 PDF 下载失败: ${download.statusCode}`))
          return
        }
        uni.openDocument({
          filePath: download.tempFilePath,
          fileType: 'pdf',
          showMenu: true,
          success: () => resolve(download.tempFilePath),
          fail: reject
        })
      },
      fail: reject
    })
  })
}
