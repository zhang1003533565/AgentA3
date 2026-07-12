import axios from 'axios'
import request from '../utils/request'
import { PREVIEW_REQUEST_TIMEOUT } from './examPaperPreviewConfig'

const base = '/api/exam/papers'

export const randomPreviewExamPaper = (data) => request.post(`${base}/random-preview`, data, {
  skipGlobalErrorMessage: true,
})

export const createExamPaper = (data) => request.post(base, data, {
  skipGlobalErrorMessage: true,
})

export const createExamPaperPreview = (data, config = {}) => {
  const nonAbortableConfig = { ...config }
  delete nonAbortableConfig.signal
  return request.post(`${base}/preview`, data, {
    ...nonAbortableConfig,
    skipGlobalErrorMessage: true,
    timeout: Math.max(config.timeout || 0, PREVIEW_REQUEST_TIMEOUT),
  })
}

export const deleteExamPaperPreview = (token, config = {}) => request.delete(`${base}/preview/${encodeURIComponent(token)}`, {
  ...config,
  skipGlobalErrorMessage: true,
  timeout: Math.max(config.timeout || 0, PREVIEW_REQUEST_TIMEOUT),
})

export const getExamPaperList = (params = {}) => request.get(base, {
  skipGlobalErrorMessage: true,
  params: {
    current: params.current ?? params.page ?? 1,
    size: params.size ?? params.pageSize ?? 10,
    keyword: params.keyword?.trim() || undefined,
  },
})

export const getExamPaperDetail = (id) => request.get(`${base}/${id}`, {
  skipGlobalErrorMessage: true,
})

const safeFilename = (value, fallback) => {
  const unescaped = String(value || '')
    .replace(/\\([\\"])/g, '$1')
    .replace(/[\r\n]/g, '')
    .split(/[\\/]/)
    .pop()
    ?.trim()
  return unescaped || fallback
}

const decodeFilename = (disposition, fallback) => {
  if (!disposition) return fallback

  const encoded = disposition.match(/filename\*\s*=\s*UTF-8''([^;]+)/i)?.[1]
  if (encoded) {
    try {
      return safeFilename(decodeURIComponent(encoded.replace(/^['"]|['"]$/g, '')), fallback)
    } catch {
      // Fall through to the plain filename parameter.
    }
  }

  const plain = disposition.match(/filename\s*=\s*(?:"((?:\\.|[^"])*)"|([^;]+))/i)
  return safeFilename(plain?.[1] || plain?.[2], fallback)
}

const normalizeBlobError = async (error) => {
  const blob = error.response?.data
  const contentType = error.response?.headers?.['content-type'] || blob?.type || ''
  if (blob instanceof Blob && /(?:application|text)\/(?:[\w.+-]*\+)?json/i.test(contentType)) {
    try {
      error.response.data = JSON.parse(await blob.text())
    } catch {
      // Keep the original Blob when the response body is not valid JSON.
    }
  }
  throw error
}

export const getExamPaperPreviewPdf = async (token, config = {}) => {
  const defaultAdapter = axios.getAdapter(request.defaults.adapter)
  const response = await request({
    url: `${base}/preview/${encodeURIComponent(token)}`,
    method: 'get',
    responseType: 'blob',
    ...config,
    skipGlobalErrorMessage: true,
    timeout: Math.max(config.timeout || 0, PREVIEW_REQUEST_TIMEOUT),
    adapter: async (config) => {
      const rawResponse = await defaultAdapter(config).catch(normalizeBlobError)
      return {
        ...rawResponse,
        data: { code: 200, data: rawResponse.data },
      }
    },
  })
  return response.data
}

export const downloadExamPaper = async (id, content) => {
  const defaultAdapter = axios.getAdapter(request.defaults.adapter)
  const response = await request({
    url: `${base}/${id}/download`,
    method: 'get',
    params: { content },
    responseType: 'blob',
    skipGlobalErrorMessage: true,
    adapter: async (config) => {
      const rawResponse = await defaultAdapter(config).catch(normalizeBlobError)
      return {
        ...rawResponse,
        data: {
          code: 200,
          data: rawResponse.data,
          headers: rawResponse.headers,
        },
      }
    },
  })

  const blob = response.data
  const fallback = content === 'answer' ? '试卷-答案.docx' : '试卷.docx'
  const filename = decodeFilename(response.headers?.['content-disposition'], fallback)
  const objectUrl = URL.createObjectURL(blob)
  const anchor = document.createElement('a')

  try {
    anchor.href = objectUrl
    anchor.download = filename
    anchor.style.display = 'none'
    document.body.appendChild(anchor)
    anchor.click()
  } finally {
    anchor.remove()
    URL.revokeObjectURL(objectUrl)
  }
}
