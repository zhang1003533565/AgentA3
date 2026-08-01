import { request } from '@/utils/request.js'
import { BASE_URL } from '@/utils/config.js'
import { getToken } from '@/utils/storage.js'
import { streamSse } from './ai.js'

const base = '/api/app/ai/ppt'

export const generatePptOutline = data => request({
  url: `${base}/outlines`,
  method: 'POST',
  data,
  timeout: 120000
})

export const generatePptSlides = data => request({
  url: `${base}/slides`,
  method: 'POST',
  data,
  timeout: 120000
})

export const createPptTask = data => request({
  url: `${base}/tasks`,
  method: 'POST',
  data,
  timeout: 120000
})

export const getPptTask = taskId => request({
  url: `${base}/tasks/${encodeURIComponent(taskId)}`,
  method: 'GET',
  showError: false
})

export const streamPptTask = (taskId, handlers = {}) => streamSse(
  `${base}/tasks/${encodeURIComponent(taskId)}/stream`,
  null,
  handlers,
  '当前运行环境无法读取 PPT 生成进度',
  'GET'
)

export function downloadPptTaskFile(taskId, format) {
  return downloadOwnedPptResource(`${base}/tasks/${encodeURIComponent(taskId)}/files/${encodeURIComponent(format)}`)
}

export function downloadPptPreview(taskId, slideIndex) {
  return downloadOwnedPptResource(`${base}/tasks/${encodeURIComponent(taskId)}/previews/${encodeURIComponent(slideIndex)}`)
}

function downloadOwnedPptResource(path) {
  const token = getToken()
  if (!token) return Promise.reject(new Error('登录状态已失效'))
  return new Promise((resolve, reject) => {
    uni.downloadFile({
      url: `${BASE_URL}${path}`,
      header: { Authorization: `Bearer ${token}` },
      timeout: 120000,
      success: response => {
        if (response.statusCode >= 200 && response.statusCode < 300 && response.tempFilePath) {
          resolve(response.tempFilePath)
        } else {
          reject(new Error(`文件下载失败: ${response.statusCode || 'unknown'}`))
        }
      },
      fail: reject
    })
  })
}
