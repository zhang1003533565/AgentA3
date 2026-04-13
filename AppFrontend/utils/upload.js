/**
 * 统一图片上传工具
 * 先用 uni.compressImage 压缩，再上传到服务器
 * H5 环境用原生 fetch + FormData（支持代理相对路径）
 * APP/小程序用 uni.uploadFile（绝对地址）
 */
import Compressor from 'compressorjs'
import { BASE_URL } from './config.js'
import { getToken } from './storage.js'

const MAX_QUALITY = 80  // 压缩质量
const MAX_UPLOAD_SIZE_MB = 5
const MAX_UPLOAD_SIZE_BYTES = MAX_UPLOAD_SIZE_MB * 1024 * 1024
const H5_MAX_DIMENSION = 1600
const H5_QUALITY = 0.8

function isUploadSizeExceededMessage(message) {
  if (!message) return false
  const normalized = String(message).toLowerCase()
  return normalized.includes('maximum upload size exceeded') ||
    normalized.includes('max upload size') ||
    normalized.includes('file size exceeds') ||
    normalized.includes('size exceeds') ||
    normalized.includes('文件大小') ||
    normalized.includes('文件过大') ||
    normalized.includes('超过 5mb') ||
    normalized.includes('超过5mb') ||
    normalized.includes('超出最大上传大小')
}

function isConnectionResetMessage(message) {
  if (!message) return false
  const normalized = String(message).toLowerCase()
  return normalized.includes('err_connection_reset') ||
    normalized.includes('connection reset') ||
    normalized.includes('fetch failed') ||
    normalized.includes('networkerror') ||
    normalized.includes('load failed')
}

export function getUploadErrorMessage(error) {
  const message = error?.msg || error?.message || ''
  if (isUploadSizeExceededMessage(message)) {
    return `图片超过 ${MAX_UPLOAD_SIZE_MB}MB`
  }
  if (isConnectionResetMessage(message)) {
    return `图片超过 ${MAX_UPLOAD_SIZE_MB}MB 或上传连接被重置`
  }
  return message || '图片上传失败'
}

function normalizeUploadError(error) {
  return {
    ...(error && typeof error === 'object' ? error : {}),
    msg: getUploadErrorMessage(error)
  }
}

async function compressBlobForH5(blob) {
  if (!blob || blob.size <= MAX_UPLOAD_SIZE_BYTES) {
    return blob
  }

  if ((blob.type || '').toLowerCase() === 'image/gif') {
    throw normalizeUploadError({ msg: `GIF 图片超过 ${MAX_UPLOAD_SIZE_MB}MB，请更换图片` })
  }

  const compressed = await new Promise((resolve, reject) => {
    new Compressor(blob, {
      quality: H5_QUALITY,
      maxWidth: H5_MAX_DIMENSION,
      maxHeight: H5_MAX_DIMENSION,
      convertSize: 0,
      success: resolve,
      error: reject
    })
  }).catch((error) => {
    throw normalizeUploadError(error)
  })

  if (!compressed || compressed.size > MAX_UPLOAD_SIZE_BYTES) {
    throw normalizeUploadError({ msg: `图片超过 ${MAX_UPLOAD_SIZE_MB}MB，请选择更小的图片` })
  }

  return compressed
}

/**
 * 压缩图片
 */
function compressImage(filePath) {
  return new Promise((resolve) => {
    if (uni.compressImage) {
      uni.compressImage({
        src: filePath,
        quality: MAX_QUALITY,
        success: (res) => resolve(res.tempFilePath),
        fail: () => resolve(filePath)
      })
    } else {
      resolve(filePath)
    }
  })
}

/**
 * H5 下通过 fetch + FormData 上传（直接请求后端，后端已配置 CORS 允许跨域）
 */
function uploadByFetch(filePath, token) {
  return new Promise((resolve, reject) => {
    fetch(filePath)
      .then(r => r.blob())
      .then(blob => compressBlobForH5(blob))
      .then(blob => {
        const ext = blob.type === 'image/jpeg' ? 'jpg' : ((blob.type || 'image/jpeg').split('/')[1] || 'jpg')
        const formData = new FormData()
        formData.append('file', blob, `upload.${ext}`)
        return fetch(`${BASE_URL}/api/upload/image`, {
          method: 'POST',
          headers: token ? { Authorization: `Bearer ${token}` } : {},
          body: formData
        })
      })
      .then(r => r.json())
      .then(data => {
        if (data.code === 200) {
          resolve(data.data?.url || '')
        } else {
          reject(normalizeUploadError(data))
        }
      })
      .catch(error => reject(normalizeUploadError(error)))
  })
}

/**
 * 上传图片到服务器（先压缩再上传）
 * @param {string} filePath 临时文件路径
 * @returns {Promise<string>} 服务器返回的图片 URL
 */
export function uploadImage(filePath) {
  const token = getToken()
  return new Promise(async (resolve, reject) => {
    const compressed = await compressImage(filePath)

    // #ifdef H5
    uploadByFetch(compressed, token).then(resolve).catch(reject)
    // #endif

    // #ifndef H5
    uni.uploadFile({
      url: `${BASE_URL}/api/upload/image`,
      filePath: compressed,
      name: 'file',
      header: token ? { Authorization: `Bearer ${token}` } : {},
      success: (res) => {
        try {
          const data = JSON.parse(res.data || '{}')
          if (res.statusCode >= 200 && res.statusCode < 300 && data.code === 200) {
            resolve(data.data?.url || '')
            return
          }
          reject(normalizeUploadError(data))
        } catch (error) {
          reject(normalizeUploadError(error))
        }
      },
      fail: (error) => reject(normalizeUploadError(error))
    })
    // #endif
  })
}

/**
 * 批量上传图片（带 loading 提示）
 * @param {string[]} filePaths 临时文件路径数组
 * @param {string} loadingText loading 提示文字
 * @returns {Promise<string[]>} 服务器 URL 数组
 */
export async function uploadImages(filePaths, loadingText = '图片上传中...') {
  uni.showLoading({ title: loadingText })
  try {
    const urls = await Promise.all(filePaths.map(fp => uploadImage(fp)))
    return urls.filter(Boolean)
  } finally {
    uni.hideLoading()
  }
}
