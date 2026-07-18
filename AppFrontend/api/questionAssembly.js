import { request } from '../utils/request.js'
import { BASE_URL } from '../utils/config.js'
import { getToken, clearAuth } from '../utils/storage.js'

export function getQuestionAssemblyOptions() {
  return request({ url: '/api/exam/question-assembly/options', method: 'GET' })
}

export function generateQuestionAssembly(data) {
  return request({ url: '/api/exam/question-assembly/generate', method: 'POST', data })
}

export function generateQuestionAssemblyWithFile(data, filePath) {
  const token = getToken()
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: `${BASE_URL}/api/exam/question-assembly/generate`,
      filePath,
      name: 'file',
      formData: { spec: JSON.stringify(data) },
      header: token ? { Authorization: `Bearer ${token}` } : {},
      success: (upload) => {
        let body
        try {
          body = typeof upload.data === 'string' ? JSON.parse(upload.data) : upload.data
        } catch (error) {
          reject(new Error('服务器返回内容无法解析'))
          return
        }
        if (upload.statusCode >= 200 && upload.statusCode < 300 && body?.code === 200) {
          resolve(body)
          return
        }
        if (body?.code === 401 || upload.statusCode === 401) clearAuth()
        uni.showToast({ title: body?.msg || body?.message || '上传生成失败', icon: 'none' })
        reject(body || upload)
      },
      fail: reject
    })
  })
}

export function commitPrivateQuestionAssembly(draftId) {
  return request({
    url: `/api/exam/question-assembly/${encodeURIComponent(draftId)}/commit-private`,
    method: 'POST'
  })
}
