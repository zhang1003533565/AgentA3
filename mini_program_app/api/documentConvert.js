import { request } from '@/utils/request.js'
import { BASE_URL } from '@/utils/config.js'
import { getToken } from '@/utils/storage.js'

/**
 * 创建文档转换任务（multipart 直传任务接口）
 * @param {string|Object} file 临时文件路径或选择器返回的文件对象（{ path, name }）
 * @param {string} convertType 转换类型：pdf_to_docx / ppt_to_docx
 * @returns {Promise<Object>} 任务对象 { taskId, status, progress, message }
 */
export function createTask(file, convertType) {
  const token = getToken()
  const filePath = typeof file === 'string'
    ? file
    : (file?.path || file?.tempFilePath || '')
  const fileName = typeof file === 'object' && file?.name
    ? file.name
    : (String(filePath).split('/').pop() || 'file')

  return new Promise((resolve, reject) => {
    // #ifdef H5
    const source = typeof File !== 'undefined' && file instanceof File
      ? Promise.resolve(file)
      : fetch(filePath).then((response) => response.blob())
    source
      .then((blob) => {
        const formData = new FormData()
        formData.append('file', blob, fileName)
        formData.append('convertType', convertType)
        return fetch(`${BASE_URL}/api/ai/convert/tasks`, {
          method: 'POST',
          headers: token ? { Authorization: `Bearer ${token}` } : {},
          body: formData
        })
      })
      .then((response) => response.json())
      .then((data) => {
        if (data.code === 200) resolve(data)
        else reject(data)
      })
      .catch(reject)
    // #endif

    // #ifndef H5
    uni.uploadFile({
      url: `${BASE_URL}/api/ai/convert/tasks`,
      filePath,
      name: 'file',
      formData: { convertType },
      header: token ? { Authorization: `Bearer ${token}` } : {},
      success: (response) => {
        try {
          const data = JSON.parse(response.data || '{}')
          if (response.statusCode >= 200 && response.statusCode < 300 && data.code === 200) {
            resolve(data)
            return
          }
          reject(data)
        } catch (error) {
          reject(error)
        }
      },
      fail: reject
    })
    // #endif
  })
}

/**
 * 查询转换任务状态
 * @param {string} taskId
 * @returns {Promise<Object>} 响应体（含 data 任务详情）
 */
export function getTask(taskId) {
  return request({
    url: `/api/ai/convert/tasks/${encodeURIComponent(taskId)}`,
    method: 'GET',
    showError: false
  })
}

/**
 * 查询转换历史记录（分页，可按类型过滤）
 * @param {string} [convertType] 转换类型，空则查全部
 * @param {number} [page]
 * @param {number} [size]
 * @returns {Promise<Object>} 响应体（含 data 分页结果）
 */
export function getHistory(convertType, page = 1, size = 20) {
  return request({
    url: '/api/ai/convert/tasks',
    method: 'GET',
    params: {
      page,
      size,
      ...(convertType ? { convertType } : {})
    }
  })
}

/**
 * 批量删除转换任务（仅终态记录）
 * @param {string[]} taskIds
 * @returns {Promise<Object>} 响应体
 */
export function deleteConvertTasks(taskIds) {
  return request({
    url: '/api/ai/convert/tasks/batch-delete',
    method: 'POST',
    data: { taskIds }
  })
}

/**
 * 下载转换结果文件
 * @param {string} taskId
 * @returns {Promise<string>} 临时文件路径
 */
export function downloadResult(taskId) {
  const token = getToken()
  if (!token) return Promise.reject(new Error('登录状态已失效'))
  return new Promise((resolve, reject) => {
    uni.downloadFile({
      url: `${BASE_URL}/api/ai/convert/tasks/${encodeURIComponent(taskId)}/download`,
      header: { Authorization: `Bearer ${token}` },
      timeout: 120000,
      success: (response) => {
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
