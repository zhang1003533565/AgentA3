import { request, API_BASE_URL } from './request'
import { getToken } from '../utils/auth'

/**
 * 文档格式转换接口（与 app 端共用同一套后端 /api/ai/convert）。
 */

/**
 * 创建文档转换任务（multipart 直传）
 * @param {File} file 浏览器 File 对象
 * @param {string} convertType 转换类型：pdf_to_docx / ppt_to_docx / pdf_to_ppt / ppt_to_pdf / docx_to_pdf / docx_to_ppt
 * @param {string} [convertMode] 转换模式（部分类型需要）
 * @returns {Promise<Object>} 响应体（含 data 任务对象 { taskId, status, progress, message }）
 */
export async function createTask(file, convertType, convertMode = '') {
  const token = getToken()
  const formData = new FormData()
  formData.append('file', file, file.name || 'file')
  formData.append('convertType', convertType)
  formData.append('convertMode', convertMode || '')

  const response = await fetch(`${API_BASE_URL}/api/ai/convert/tasks`, {
    method: 'POST',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: formData,
  })

  let payload = {}
  try {
    payload = await response.json()
  } catch {
    /* 忽略非 JSON 响应，走下方统一报错 */
  }

  if (!response.ok || payload?.code !== 200) {
    throw new Error(payload?.msg || payload?.message || '创建转换任务失败')
  }
  return payload
}

/** 查询转换任务状态 */
export function getTask(taskId) {
  return request({
    url: `/api/ai/convert/tasks/${encodeURIComponent(taskId)}`,
    method: 'GET',
  })
}

/** 查询转换历史记录（分页，可按类型过滤） */
export function getHistory(convertType, page = 1, size = 20) {
  return request({
    url: '/api/ai/convert/tasks',
    method: 'GET',
    params: {
      page,
      size,
      ...(convertType ? { convertType } : {}),
    },
  })
}

/** 批量删除转换任务（仅终态记录） */
export function deleteConvertTasks(taskIds) {
  return request({
    url: '/api/ai/convert/tasks/batch-delete',
    method: 'POST',
    data: { taskIds },
  })
}

/**
 * 下载转换结果文件
 * @returns {Promise<string>} 结果文件的对象 URL
 */
export async function downloadResult(taskId) {
  const token = getToken()
  const response = await fetch(
    `${API_BASE_URL}/api/ai/convert/tasks/${encodeURIComponent(taskId)}/download`,
    {
      method: 'GET',
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    },
  )
  if (!response.ok) {
    throw new Error(`文件下载失败: ${response.status}`)
  }
  const blob = await response.blob()
  return URL.createObjectURL(blob)
}
