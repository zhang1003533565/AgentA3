import { request } from '../utils/request.js'
import { BASE_URL } from '../utils/config.js'
import { getToken, clearAuth } from '../utils/storage.js'

const BASE = '/api/exam/question-generation'

function isH5Runtime() {
  return typeof window !== 'undefined' && typeof document !== 'undefined' && typeof FormData !== 'undefined'
}

export function getQuestionGenerationOptions() {
  return request({ url: `${BASE}/options`, method: 'GET' })
}

/**
 * 生成题目（multipart）。text 直传；filePath 走 uploadFile / H5 FormData。
 */
export function generateQuestions({ sourceType, text, questionType, maxQuestions, difficulty, sourceTitle, filePath, fileName }) {
  const fields = {
    sourceType,
    questionType,
    ...(maxQuestions != null ? { maxQuestions: String(maxQuestions) } : {}),
    ...(difficulty ? { difficulty } : {}),
    ...(sourceTitle ? { sourceTitle } : {}),
    ...(text ? { text } : {})
  }

  if (isH5Runtime()) {
    return generateByFetch(fields, filePath, fileName)
  }
  if (filePath) {
    return generateByUpload(fields, filePath)
  }
  return generateTextAsTempFile(fields)
}

export function importGeneratedQuestions(data) {
  return request({
    url: `${BASE}/import`,
    method: 'POST',
    data,
    timeout: 60000
  })
}

export function reviewGeneratedQuestions(data, expectedType) {
  const query = expectedType ? `?expectedType=${encodeURIComponent(expectedType)}` : ''
  return request({
    url: `/api/exam/questions/review${query}`,
    method: 'POST',
    data,
    showError: false
  })
}

function parseUploadBody(upload) {
  let body
  try {
    body = typeof upload.data === 'string' ? JSON.parse(upload.data) : upload.data
  } catch (error) {
    throw new Error('服务器返回内容无法解析')
  }
  if (upload.statusCode >= 200 && upload.statusCode < 300 && body?.code === 200) {
    return body
  }
  if (body?.code === 401 || upload.statusCode === 401) clearAuth()
  const msg = body?.msg || body?.message || '题库生成失败'
  uni.showToast({ title: msg, icon: 'none' })
  throw body || new Error(msg)
}

function generateByFetch(fields, filePath, fileName) {
  const token = getToken()
  const formData = new FormData()
  Object.keys(fields).forEach((key) => {
    if (fields[key] !== undefined && fields[key] !== null && fields[key] !== '') {
      formData.append(key, fields[key])
    }
  })

  const run = async () => {
    if (filePath) {
      const blob = await fetch(filePath).then((r) => r.blob())
      const name = fileName || `material.${fields.sourceType === 'docx' ? 'docx' : 'txt'}`
      formData.append('file', blob, name)
    }
    const response = await fetch(`${BASE_URL}${BASE}/generate`, {
      method: 'POST',
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      body: formData
    })
    const body = await response.json()
    if (response.status >= 200 && response.status < 300 && body?.code === 200) {
      return body
    }
    if (body?.code === 401 || response.status === 401) clearAuth()
    const msg = body?.msg || body?.message || '题库生成失败'
    uni.showToast({ title: msg, icon: 'none' })
    throw body || new Error(msg)
  }

  return run()
}

function generateByUpload(fields, filePath) {
  const token = getToken()
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: `${BASE_URL}${BASE}/generate`,
      filePath,
      name: 'file',
      formData: fields,
      header: token ? { Authorization: `Bearer ${token}` } : {},
      timeout: 120000,
      success: (upload) => {
        try {
          resolve(parseUploadBody(upload))
        } catch (error) {
          reject(error)
        }
      },
      fail: reject
    })
  })
}

/** 非 H5 纯文本：写成临时 txt 再上传，避免 multipart 兼容问题 */
function generateTextAsTempFile(fields) {
  const content = fields.text || ''
  const fs = uni.getFileSystemManager()
  const basePath = (typeof uni.env !== 'undefined' && uni.env.USER_DATA_PATH) ? uni.env.USER_DATA_PATH : ''
  const filePath = `${basePath}/qbg_material_${Date.now()}.txt`
  return new Promise((resolve, reject) => {
    fs.writeFile({
      filePath,
      data: content,
      encoding: 'utf8',
      success: () => {
        const uploadFields = { ...fields, sourceType: 'txt' }
        delete uploadFields.text
        generateByUpload(uploadFields, filePath).then(resolve).catch(reject)
      },
      fail: (error) => reject(error)
    })
  })
}
