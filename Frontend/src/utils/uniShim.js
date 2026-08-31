const toastTimer = { id: null }
let loadingCount = 0
let loadingEl = null
const fileInputMap = new Map()
const imageInputMap = new Map()

function ensureToastHost() {
  let host = document.getElementById('uni-shim-toast')
  if (host) return host
  host = document.createElement('div')
  host.id = 'uni-shim-toast'
  host.style.cssText = 'position:fixed;left:50%;bottom:72px;transform:translateX(-50%);z-index:9999;padding:10px 16px;border-radius:8px;background:rgba(24,32,51,.92);color:#fff;font-size:13px;max-width:min(420px,calc(100vw - 32px));text-align:center;pointer-events:none;opacity:0;transition:opacity .2s ease'
  document.body.appendChild(host)
  return host
}

function showToast({ title = '', icon = 'none', duration = 2000 } = {}) {
  const host = ensureToastHost()
  host.textContent = String(title || '')
  host.style.opacity = title ? '1' : '0'
  if (toastTimer.id) window.clearTimeout(toastTimer.id)
  if (title) {
    toastTimer.id = window.setTimeout(() => {
      host.style.opacity = '0'
    }, Math.max(1200, Number(duration) || 2000))
  }
  if (icon === 'error') console.error('[toast]', title)
}

function ensureLoadingHost() {
  if (loadingEl) return loadingEl
  loadingEl = document.createElement('div')
  loadingEl.id = 'uni-shim-loading'
  loadingEl.style.cssText = 'position:fixed;inset:0;z-index:9998;display:none;place-items:center;background:rgba(255,255,255,.45)'
  loadingEl.innerHTML = '<div style="padding:14px 18px;border-radius:10px;background:#fff;border:1px solid #dfe4ec;box-shadow:0 10px 30px rgba(35,50,92,.12);font-size:13px;color:#344f6a"></div>'
  document.body.appendChild(loadingEl)
  return loadingEl
}

function showLoading({ title = '加载中' } = {}) {
  loadingCount += 1
  const host = ensureLoadingHost()
  host.firstElementChild.textContent = String(title || '加载中')
  host.style.display = 'grid'
}

function hideLoading() {
  loadingCount = Math.max(0, loadingCount - 1)
  if (loadingCount === 0 && loadingEl) loadingEl.style.display = 'none'
}

function showModal({ title = '', content = '', confirmText = '确定', cancelText = '取消', showCancel = true, success }) {
  const message = [title, content].filter(Boolean).join('\n\n')
  if (!showCancel) {
    window.alert(message || title || content || '提示')
    success?.({ confirm: true, cancel: false })
    return
  }
  const confirmed = window.confirm(message || title || content || '确认操作？')
  success?.({ confirm: confirmed, cancel: !confirmed })
}

function createHiddenInput(accept, multiple, map, onPick) {
  const key = `${accept}|${multiple ? '1' : '0'}`
  let input = map.get(key)
  if (!input) {
    input = document.createElement('input')
    input.type = 'file'
    input.accept = accept
    input.multiple = multiple
    input.style.display = 'none'
    document.body.appendChild(input)
    map.set(key, input)
  }
  input.accept = accept
  input.onchange = () => {
    const files = Array.from(input.files || [])
    input.value = ''
    onPick(files)
  }
  input.click()
}

function chooseFile({ count = 1, extension = [], success, fail } = {}) {
  const accept = (extension.length ? extension : ['txt', 'doc', 'docx', 'ppt', 'pptx', 'pdf'])
    .map((item) => `.${String(item).replace(/^\./, '')}`)
    .join(',')
  createHiddenInput(accept, count > 1, fileInputMap, (files) => {
    if (!files.length) {
      fail?.({ errMsg: 'chooseFile:fail cancel' })
      return
    }
    const picked = files.slice(0, count)
    const tempFiles = picked.map((file) => ({
      name: file.name,
      size: file.size,
      path: file,
      file,
      tempFilePath: URL.createObjectURL(file),
    }))
    success?.({
      tempFiles,
      tempFilePaths: tempFiles.map((item) => item.tempFilePath),
    })
  })
}

function chooseImage({ count = 1, success, fail } = {}) {
  createHiddenInput('image/*', count > 1, imageInputMap, (files) => {
    if (!files.length) {
      fail?.({ errMsg: 'chooseImage:fail cancel' })
      return
    }
    const picked = files.slice(0, count)
    success?.({
      tempFiles: picked.map((file) => ({ name: file.name, size: file.size, path: file, file })),
      tempFilePaths: picked.map((file) => URL.createObjectURL(file)),
    })
  })
}

function previewImage({ urls = [], current } = {}) {
  const list = Array.isArray(urls) ? urls.filter(Boolean) : []
  const target = current || list[0]
  if (target) window.open(target, '_blank', 'noopener,noreferrer')
}

function openDocument({ filePath, fileType } = {}) {
  if (!filePath) return
  const anchor = document.createElement('a')
  anchor.href = filePath
  anchor.download = fileType ? `presentation.${fileType}` : 'presentation.pptx'
  anchor.target = '_blank'
  anchor.rel = 'noopener noreferrer'
  anchor.click()
}

function storageGet(key) {
  try {
    const raw = localStorage.getItem(String(key))
    if (!raw) return ''
    return JSON.parse(raw)
  } catch {
    return ''
  }
}

function storageSet(key, value) {
  localStorage.setItem(String(key), JSON.stringify(value))
}

function storageRemove(key) {
  localStorage.removeItem(String(key))
}

function createSelectorQuery() {
  let root = document
  const steps = []
  return {
    in(component) {
      root = component?.$el || document
      return this
    },
    select(selector) {
      steps.push({ action: 'select', selector })
      return this
    },
    boundingClientRect() {
      steps.push({ action: 'rect' })
      return this
    },
    exec(callback) {
      const rects = []
      let currentSelector = ''
      steps.forEach((step) => {
        if (step.action === 'select') currentSelector = step.selector
        if (step.action === 'rect') {
          const el = currentSelector ? root.querySelector?.(currentSelector) : null
          if (!el) {
            rects.push(null)
            return
          }
          const rect = el.getBoundingClientRect()
          rects.push({
            width: rect.width,
            height: rect.height,
            top: rect.top,
            left: rect.left,
          })
        }
      })
      callback?.(rects)
    },
  }
}

export const uni = {
  showToast,
  showLoading,
  hideLoading,
  showModal,
  chooseFile,
  chooseMessageFile: chooseFile,
  chooseImage,
  previewImage,
  openDocument,
  createSelectorQuery,
  getStorageSync: storageGet,
  setStorageSync: storageSet,
  removeStorageSync: storageRemove,
  getFileSystemManager: () => ({
    readFile: ({ filePath, encoding, success, fail }) => {
      if (filePath instanceof Blob || filePath instanceof File) {
        const reader = new FileReader()
        reader.onload = (event) => success?.({ data: event.target?.result || '' })
        reader.onerror = () => fail?.({ errMsg: 'readFile:fail' })
        if (encoding === 'utf8' || encoding === 'utf-8') reader.readAsText(filePath, 'UTF-8')
        else reader.readAsArrayBuffer(filePath)
        return
      }
      fail?.({ errMsg: 'readFile:fail unsupported path' })
    },
  }),
  saveFile: ({ tempFilePath, success, fail }) => {
    success?.({ savedFilePath: tempFilePath })
  },
}

if (typeof window !== 'undefined') {
  window.uni = uni
}
