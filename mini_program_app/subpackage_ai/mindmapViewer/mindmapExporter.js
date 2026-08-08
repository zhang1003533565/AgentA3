// 思维导图导出工具（H5 端）
// 根据 buildMindMapLayout 输出的 layout（含 nodes、links、width、height）
// 生成 SVG 字符串，再转 PNG 触发下载

const ROOT_WIDTH = 200
const ROOT_HEIGHT = 60
const NODE_WIDTH = 140
const NODE_HEIGHT = 44

function escapeXml(s) {
  return String(s || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;')
}

// 把文本按最大宽度拆成多行，最多 maxLines 行（超出截断 + "..."）
function wrapText(text, maxCharsPerLine, maxLines = 2) {
  const str = String(text || '')
  if (!str.length) return ['']
  if (str.length <= maxCharsPerLine) return [str]
  const lines = []
  let cur = ''
  for (const ch of str) {
    cur += ch
    if (cur.length >= maxCharsPerLine) {
      lines.push(cur)
      cur = ''
    }
  }
  if (cur) lines.push(cur)
  // 限制行数
  if (lines.length > maxLines) {
    const kept = lines.slice(0, maxLines)
    // 最后一行末尾加 "..."
    const last = kept[maxLines - 1] || ''
    kept[maxLines - 1] = last.length > 1 ? last.slice(0, -1) + '…' : last + '…'
    return kept
  }
  return lines
}

// 把 px 尺寸转 SVG 单位（用 px，viewBox 跟 layout 尺寸一致即可）
function buildMindmapSVG(layout, title) {
  const W = layout.width
  const H = layout.height
  // 留白（标题区）
  const titleH = title ? 60 : 0
  const totalW = W
  const totalH = H + titleH

  const parts = []
  parts.push(`<svg xmlns="http://www.w3.org/2000/svg" width="${totalW}" height="${totalH}" viewBox="0 0 ${totalW} ${totalH}">`)
  // 背景
  parts.push(`<rect x="0" y="0" width="${totalW}" height="${totalH}" fill="#FAFBFC"/>`)

  // 标题
  if (title) {
    parts.push(
      `<text x="${totalW / 2}" y="38" text-anchor="middle" font-family="-apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', sans-serif" font-size="22" font-weight="700" fill="#1E293B">${escapeXml(title)}</text>`
    )
  }

  // 连线（在节点之下）
  for (const link of layout.links) {
    const color = link.branchColor?.main || '#4D6BFE'
    const opacity = link.depth === 1 ? 0.55 : 0.3
    // 贝塞尔曲线，控制点在水平中点
    const mx = (link.x1 + link.x2) / 2
    const d = `M ${link.x1} ${link.y1} C ${mx} ${link.y1}, ${mx} ${link.y2}, ${link.x2} ${link.y2}`
    parts.push(
      `<path d="${d}" stroke="${color}" stroke-width="${link.depth === 1 ? 2 : 1.5}" fill="none" stroke-linecap="round" opacity="${opacity}"/>`
    )
  }

  // 节点
  for (const node of layout.nodes) {
    const x = node.x - node.width / 2
    const y = node.y - node.height / 2 + titleH
    const w = node.width
    const h = node.height
    const r = Math.min(h / 2, 22)

    if (node.depth === 0) {
      // 根节点：深色大卡
      parts.push(
        `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${r}" ry="${r}" fill="#1E293B"/>`
      )
      const maxChars = Math.max(2, Math.floor((w - 64) / 16))
      const lines = wrapText(node.label, maxChars, 2)
      const lineH = 22
      const startY = y + h / 2 - ((lines.length - 1) * lineH) / 2 + 6
      lines.forEach((line, i) => {
        parts.push(
          `<text x="${node.x}" y="${startY + i * lineH}" text-anchor="middle" font-family="-apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', sans-serif" font-size="16" font-weight="700" fill="#FFFFFF">${escapeXml(line)}</text>`
        )
      })
    } else if (node.depth === 1) {
      // 一级分支节点：彩色药丸
      const color = node.branchColor?.main || '#4D6BFE'
      parts.push(
        `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${r}" ry="${r}" fill="${color}"/>`
      )
      const maxChars = Math.max(2, Math.floor((w - 48) / 13))
      const lines = wrapText(node.label, maxChars, 2)
      const lineH = 18
      const startY = y + h / 2 - ((lines.length - 1) * lineH) / 2 + 5
      lines.forEach((line, i) => {
        parts.push(
          `<text x="${node.x}" y="${startY + i * lineH}" text-anchor="middle" font-family="-apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', sans-serif" font-size="13" font-weight="700" fill="#FFFFFF">${escapeXml(line)}</text>`
        )
      })
    } else {
      // 子节点：白色卡 + 侧边色条
      const color = node.branchColor?.main || '#4D6BFE'
      const border = node.branchColor?.border || '#E2E8F0'
      // 卡（白色背景）
      parts.push(
        `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="9" ry="9" fill="#FFFFFF" stroke="${border}" stroke-width="1"/>`
      )
      // 侧边色条（左侧或右侧）
      const stripeW = 3
      if (node.side === 'left') {
        // 色条在节点右侧
        parts.push(
          `<rect x="${x + w - stripeW}" y="${y + 1}" width="${stripeW}" height="${h - 2}" fill="${color}"/>`
        )
      } else {
        // 色条在节点左侧
        parts.push(
          `<rect x="${x}" y="${y + 1}" width="${stripeW}" height="${h - 2}" fill="${color}"/>`
        )
      }
      // 文字居中对齐到节点中心（保守字符宽度 14px，色条窄到不挡文字）
      // 中文 11px 字符实际宽度 ≈ 13-14px
      const maxChars = Math.max(2, Math.floor((w - 14) / 14))
      const lines = wrapText(node.label, maxChars, 2)
      const lineH = 13
      const startY = y + h / 2 - ((lines.length - 1) * lineH) / 2 + 4
      lines.forEach((line, i) => {
        parts.push(
          `<text x="${node.x}" y="${startY + i * lineH}" text-anchor="middle" font-family="-apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', sans-serif" font-size="11" font-weight="600" fill="#334155">${escapeXml(line)}</text>`
        )
      })
    }
  }

  parts.push('</svg>')
  return parts.join('')
}

// 把 SVG 字符串转 PNG Blob（H5 端专用）
async function svgToPngBlob(svgString, scale = 2) {
  return new Promise((resolve, reject) => {
    // 解析尺寸
    const match = svgString.match(/<svg[^>]*\swidth="(\d+)"[^>]*\sheight="(\d+)"/)
    const w = match ? parseInt(match[1], 10) : 800
    const h = match ? parseInt(match[2], 10) : 600
    // 转 data URL
    const svgBlob = new Blob([svgString], { type: 'image/svg+xml;charset=utf-8' })
    const url = URL.createObjectURL(svgBlob)
    const img = new Image()
    img.onload = () => {
      const canvas = document.createElement('canvas')
      canvas.width = w * scale
      canvas.height = h * scale
      const ctx = canvas.getContext('2d')
      ctx.fillStyle = '#FAFBFC'
      ctx.fillRect(0, 0, canvas.width, canvas.height)
      ctx.drawImage(img, 0, 0, canvas.width, canvas.height)
      URL.revokeObjectURL(url)
      canvas.toBlob(blob => {
        if (blob) resolve(blob)
        else reject(new Error('canvas.toBlob 失败'))
      }, 'image/png')
    }
    img.onerror = e => {
      URL.revokeObjectURL(url)
      reject(e)
    }
    img.src = url
  })
}

// 触发浏览器下载
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

// 导出为 PNG 并下载（H5 端）
export async function exportMindmapAsPNG(layout, title) {
  const svg = buildMindmapSVG(layout, title)
  const blob = await svgToPngBlob(svg, 2)
  const safeTitle = (title || 'AI思维导图').replace(/[\\/:*?"<>|]/g, '_').slice(0, 40)
  const ts = new Date().toISOString().slice(0, 16).replace(/[-:T]/g, '')
  downloadBlob(blob, `${safeTitle}_${ts}.png`)
  return { width: layout.width, height: layout.height + (title ? 60 : 0) }
}

export { buildMindmapSVG }
