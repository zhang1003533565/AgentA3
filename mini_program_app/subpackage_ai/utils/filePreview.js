import { BASE_URL } from '@/utils/config.js'

function firstValue(...values) {
  return values.map(value => String(value || '').trim()).find(Boolean) || ''
}

function fileExt(value = '') {
  const clean = String(value || '').split(/[?#]/)[0]
  const match = clean.match(/\.([a-z0-9]+)$/i)
  return match ? match[1].toLowerCase() : ''
}

function safeDecode(value = '') {
  try {
    return decodeURIComponent(value)
  } catch (error) {
    return value
  }
}

function fileNameFromUrl(url = '') {
  const clean = String(url || '').split(/[?#]/)[0]
  const name = clean.split('/').pop() || ''
  return name ? safeDecode(name) : ''
}

export function normalizePreviewUrl(value = '') {
  const text = String(value || '').trim()
  if (!text) return ''
  if (/^(https?:|file:|blob:|wxfile:)/i.test(text)) return text
  if (text.startsWith('/')) return BASE_URL ? `${BASE_URL}${text}` : text
  return BASE_URL ? `${BASE_URL}/${text.replace(/^\/+/, '')}` : text
}

function openUrlInBrowser(url) {
  // #ifdef H5
  if (typeof window !== 'undefined' && url) {
    window.open(url, '_blank')
    return true
  }
  // #endif
  return false
}

export function openLocalDocument(filePath) {
  if (!filePath) {
    uni.showToast({ title: '暂无可预览文件', icon: 'none' })
    return
  }
  if (typeof uni.openDocument !== 'function') {
    if (!openUrlInBrowser(filePath)) {
      uni.showToast({ title: '当前平台不支持预览', icon: 'none' })
    }
    return
  }
  uni.openDocument({
    filePath,
    showMenu: true,
    fail: () => {
      if (!openUrlInBrowser(filePath)) {
        uni.showToast({ title: '文件暂时无法预览', icon: 'none' })
      }
    },
  })
}

export function buildPreviewTarget(file = {}) {
  const localPath = firstValue(file.filePath, file.tempFilePath, file.localPath, file.path)
  const remoteUrl = normalizePreviewUrl(firstValue(
    file.previewUrl,
    file.sourceFile,
    file.url,
    file.fileUrl,
    file.downloadUrl,
    file.downloadPath,
  ))
  const name = firstValue(file.fileName, file.name, file.title, fileNameFromUrl(remoteUrl), fileNameFromUrl(localPath), '已导入文件')
  const text = firstValue(file.text, file.parsedText, file.content, file.rawText)
  const summary = firstValue(file.summary, file.aiSummary, file.description)
  return {
    name,
    ext: fileExt(name) || fileExt(remoteUrl) || fileExt(localPath),
    url: remoteUrl,
    localPath,
    size: file.size || file.fileSize || 0,
    text,
    summary,
    textLength: file.textLength || (text ? text.length : 0),
    summaryStatus: file.summaryStatus || '',
    summaryModel: file.summaryModel || '',
  }
}

export function fallbackPreviewUploadedDocument(file = {}) {
  const { localPath, url: remoteUrl } = buildPreviewTarget(file)
  const localIsRemote = /^https?:\/\//i.test(localPath)

  if (localPath && !localIsRemote) {
    openLocalDocument(localPath)
    return
  }

  const previewUrl = localIsRemote ? localPath : remoteUrl
  if (!previewUrl) {
    uni.showToast({ title: '暂无可预览文件', icon: 'none' })
    return
  }

  if (!/^https?:\/\//i.test(previewUrl)) {
    openLocalDocument(previewUrl)
    return
  }

  if (typeof uni.downloadFile !== 'function') {
    if (!openUrlInBrowser(previewUrl)) {
      uni.showToast({ title: '当前平台不支持预览', icon: 'none' })
    }
    return
  }

  uni.showLoading({ title: '打开中...' })
  uni.downloadFile({
    url: previewUrl,
    success: res => {
      if (res.statusCode >= 200 && res.statusCode < 300 && res.tempFilePath) {
        openLocalDocument(res.tempFilePath)
      } else if (!openUrlInBrowser(previewUrl)) {
        uni.showToast({ title: '文件下载失败', icon: 'none' })
      }
    },
    fail: () => {
      if (!openUrlInBrowser(previewUrl)) {
        uni.showToast({ title: '文件下载失败', icon: 'none' })
      }
    },
    complete: () => uni.hideLoading(),
  })
}

export function previewUploadedDocument(file = {}) {
  const target = buildPreviewTarget(file)
  const canPreviewInApp = Boolean(target.text || target.summary || target.url)
  if (!canPreviewInApp) {
    fallbackPreviewUploadedDocument(file)
    return
  }

  const key = `file-preview-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
  try {
    uni.setStorageSync(`aiFilePreview:${key}`, target)
  } catch (error) {
    fallbackPreviewUploadedDocument(file)
    return
  }

  uni.navigateTo({
    url: `/subpackage_ai/filePreview/filePreview?key=${encodeURIComponent(key)}`,
    fail: () => fallbackPreviewUploadedDocument(file),
  })
}
