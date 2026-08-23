// DOM 转 PNG 导出工具（H5 端专用，原生 foreignObject，不引第三方库）
// 把已渲染的 DOM 节点通过 SVG foreignObject 序列化进 SVG，再绘制到 canvas 导出 PNG
// 目标：屏幕所见即所得 —— 无损、完全、正确显示
//
// 限制：仅 H5 端可用（依赖 document/Image/canvas/Blob）

const DEFAULT_BACKGROUND = '#FAFBFC'

// 收集页面所有样式规则（scoped 规则带 data-v 属性，与克隆节点的 data-v 匹配）
function collectCssText() {
  let css = ''
  const sheets = document.styleSheets || []
  for (let i = 0; i < sheets.length; i++) {
    try {
      const rules = sheets[i].cssRules || []
      for (let j = 0; j < rules.length; j++) {
        css += rules[j].cssText + '\n'
      }
    } catch (e) {
      // 跨域样式表无法访问 cssRules，跳过
    }
  }
  return css
}

function escapeXml(s) {
  return String(s || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

// 把 UniApp H5 自定义元素转为标准 HTML 标签
// 规避 foreignObject 内 custom element 不被升级、样式不生效的问题
function normalizeHtml(html) {
  return html
    .replace(/<\/?uni-view/gi, m => m.replace(/uni-view/i, 'div'))
    .replace(/<\/?uni-text/gi, m => m.replace(/uni-text/i, 'span'))
    .replace(/<\/?uni-image/gi, m => m.replace(/uni-image/i, 'img'))
}

/**
 * 把指定 DOM 节点导出为 PNG 并触发下载（H5 端）
 * @param {String} selector  DOM 选择器（如 '.mindmap-stage'）
 * @param {Object} options
 * @param {Number} options.width     节点原始宽度（px，不含缩放）
 * @param {Number} options.height    节点原始高度（px，不含缩放）
 * @param {Number} [options.scale=2] 输出倍率（2 = 高清）
 * @param {String} [options.background] 背景色
 * @param {String} [options.title]    顶部标题（可选，SVG 原生渲染）
 * @param {String} [options.filename] 文件名（不含扩展名）
 */
export async function domToPng(selector, options = {}) {
  const el = document.querySelector(selector)
  if (!el) throw new Error('导出目标不存在：' + selector)

  const width = options.width || el.offsetWidth || 800
  const height = options.height || el.offsetHeight || 600
  const scale = options.scale || 2
  const background = options.background || DEFAULT_BACKGROUND
  const title = options.title || ''
  const titleH = title ? 56 : 0
  const totalW = width
  const totalH = height + titleH

  // 克隆节点：去除缩放变换，固定原始尺寸（导出原始大小，不受用户当前缩放影响）
  const clone = el.cloneNode(true)
  clone.setAttribute('style', `width:${width}px;height:${height}px;transform:none;margin:0;box-sizing:border-box;`)

  // 序列化为 HTML，并标准化 UniApp 自定义标签
  let html = new XMLSerializer().serializeToString(clone)
  html = normalizeHtml(html)

  // 补充 div/span/img 基础样式（uni-view 替换为 div 后的兜底默认值）
  const baseCss = 'div{display:block;box-sizing:border-box}span{display:inline}img{display:inline-block}'
  const cssText = baseCss + '\n' + collectCssText()

  // 构造 SVG：顶部标题（SVG 原生 text，矢量清晰）+ foreignObject 包裹 DOM
  const svgParts = []
  svgParts.push(`<svg xmlns="http://www.w3.org/2000/svg" width="${totalW}" height="${totalH}" viewBox="0 0 ${totalW} ${totalH}">`)
  svgParts.push(`<rect x="0" y="0" width="${totalW}" height="${totalH}" fill="${background}"/>`)
  if (title) {
    svgParts.push(`<text x="${totalW / 2}" y="36" text-anchor="middle" font-family="-apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', sans-serif" font-size="22" font-weight="700" fill="#1E293B">${escapeXml(title)}</text>`)
  }
  svgParts.push(`<foreignObject x="0" y="${titleH}" width="${totalW}" height="${height}">`)
  svgParts.push(`<style>${cssText}</style>`)
  svgParts.push(`<body xmlns="http://www.w3.org/1999/xhtml" style="margin:0;padding:0;background:${background};">`)
  svgParts.push(html)
  svgParts.push(`</body>`)
  svgParts.push(`</foreignObject>`)
  svgParts.push(`</svg>`)
  const svgStr = svgParts.join('')

  // 转 data URL（encodeURIComponent 处理中文；非 base64 避免编码体积膨胀）
  const svgDataUrl = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svgStr)

  // SVG → Image → Canvas → PNG Blob → 下载
  await new Promise((resolve, reject) => {
    const img = new Image()
    img.onload = () => {
      const canvas = document.createElement('canvas')
      canvas.width = totalW * scale
      canvas.height = totalH * scale
      const ctx = canvas.getContext('2d')
      ctx.fillStyle = background
      ctx.fillRect(0, 0, canvas.width, canvas.height)
      ctx.drawImage(img, 0, 0, canvas.width, canvas.height)
      canvas.toBlob(blob => {
        if (!blob) { reject(new Error('canvas.toBlob 失败')); return }
        const safeName = (options.filename || '导出图片').replace(/[\\/:*?"<>|]/g, '_').slice(0, 40)
        const ts = new Date().toISOString().slice(0, 16).replace(/[-:T]/g, '')
        downloadBlob(blob, `${safeName}_${ts}.png`)
        resolve()
      }, 'image/png')
    }
    img.onerror = () => reject(new Error('SVG 渲染失败，可能含不支持的内容'))
    img.src = svgDataUrl
  })
}
