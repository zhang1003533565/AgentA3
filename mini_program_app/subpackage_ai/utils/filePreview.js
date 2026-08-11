import { BASE_URL } from '@/utils/config.js'

function firstValue(...values) {
  return values.map(value => String(value || '').trim()).find(Boolean) || ''
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

export function previewUploadedDocument(file = {}) {
  const localPath = firstValue(file.filePath, file.tempFilePath, file.localPath, file.path)
  const remoteUrl = normalizePreviewUrl(firstValue(
    file.sourceFile,
    file.url,
    file.fileUrl,
    file.previewUrl,
    file.downloadUrl,
    file.downloadPath,
  ))
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
