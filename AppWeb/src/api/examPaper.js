import axios from 'axios'
import request from '../utils/request'

const base = '/api/exam/papers'

export const randomPreviewExamPaper = (data) => request.post(`${base}/random-preview`, data)

export const createExamPaper = (data) => request.post(base, data)

export const getExamPaperList = (params = {}) => request.get(base, {
  params: {
    current: params.current ?? params.page ?? 1,
    size: params.size ?? params.pageSize ?? 10,
  },
})

export const getExamPaperDetail = (id) => request.get(`${base}/${id}`)

const decodeFilename = (disposition, fallback) => {
  if (!disposition) return fallback

  const encoded = disposition.match(/filename\*\s*=\s*UTF-8''([^;]+)/i)?.[1]
  if (encoded) {
    try {
      return decodeURIComponent(encoded.replace(/^['"]|['"]$/g, ''))
    } catch {
      // Fall through to the plain filename parameter.
    }
  }

  const plain = disposition.match(/filename\s*=\s*(?:"([^"]+)"|([^;]+))/i)
  return (plain?.[1] || plain?.[2])?.trim() || fallback
}

export const downloadExamPaper = async (id, content) => {
  const defaultAdapter = axios.getAdapter(request.defaults.adapter)
  const response = await request({
    url: `${base}/${id}/download`,
    method: 'get',
    params: { content },
    responseType: 'blob',
    adapter: async (config) => {
      const rawResponse = await defaultAdapter(config)
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
