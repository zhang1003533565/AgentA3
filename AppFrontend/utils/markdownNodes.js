export const SAFE_MARKDOWN_NODE_NAMES = new Set([
  'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'p', 'strong', 'em', 'code', 'pre',
  'ul', 'ol', 'li', 'a', 'table', 'thead', 'tbody', 'tr', 'th', 'td', 'br'
])

function textNode(text) {
  return { type: 'text', text: String(text || '') }
}

function element(name, children = [], attrs = undefined) {
  const node = { name, children }
  if (attrs && Object.keys(attrs).length) node.attrs = attrs
  return node
}

function stripRawHtml(value) {
  return String(value || '').replace(/<[^>]*>/g, '')
}

export function safeMarkdownHref(value) {
  const href = String(value || '').trim().replace(/[\u0000-\u001f\u007f]/g, '')
  const compact = href.replace(/\s+/g, '').toLowerCase()
  if (!href || /^(?:javascript|data|vbscript):/.test(compact)) return ''
  if (/^https?:\/\//i.test(href) || /^mailto:/i.test(href) || /^\/(?!\/)/.test(href)) return href
  return ''
}

function inlineNodes(value) {
  const source = stripRawHtml(value)
  const pattern = /(`[^`\n]+`|\*\*[^*\n]+\*\*|\*[^*\n]+\*|\[[^\]\n]+\]\([^\s)]+\))/g
  const result = []
  let cursor = 0
  let match
  while ((match = pattern.exec(source)) !== null) {
    if (match.index > cursor) result.push(textNode(source.slice(cursor, match.index)))
    const token = match[0]
    if (token.startsWith('`')) {
      result.push(element('code', [textNode(token.slice(1, -1))]))
    } else if (token.startsWith('**')) {
      result.push(element('strong', [textNode(token.slice(2, -2))]))
    } else if (token.startsWith('*')) {
      result.push(element('em', [textNode(token.slice(1, -1))]))
    } else {
      const link = token.match(/^\[([^\]]+)\]\(([^)]+)\)$/)
      const href = safeMarkdownHref(link?.[2])
      if (href) result.push(element('a', [textNode(link[1])], { href }))
      else result.push(textNode(link?.[1] || ''))
    }
    cursor = pattern.lastIndex
  }
  if (cursor < source.length) result.push(textNode(source.slice(cursor)))
  return result.filter(node => node.type !== 'text' || node.text)
}

function tableCells(line) {
  return String(line || '').trim().replace(/^\||\|$/g, '').split('|').map(cell => cell.trim())
}

function isTableDivider(line) {
  const cells = tableCells(line)
  return cells.length > 0 && cells.every(cell => /^:?-{3,}:?$/.test(cell))
}

function tableNode(lines, start) {
  if (!lines[start]?.includes('|') || !isTableDivider(lines[start + 1])) return null
  const header = tableCells(lines[start])
  const rows = []
  let cursor = start + 2
  while (cursor < lines.length && lines[cursor].trim() && lines[cursor].includes('|')) {
    rows.push(tableCells(lines[cursor]))
    cursor += 1
  }
  const headerRow = element('tr', header.map(cell => element('th', inlineNodes(cell))))
  const bodyRows = rows.map(row => element('tr', header.map((_, index) => element('td', inlineNodes(row[index] || '')))))
  return {
    node: element('table', [
      element('thead', [headerRow]),
      element('tbody', bodyRows)
    ]),
    next: cursor
  }
}

export function markdownToNodes(markdown) {
  const lines = String(markdown || '').replace(/\r\n?/g, '\n').split('\n')
  const nodes = []
  let index = 0
  while (index < lines.length) {
    const raw = lines[index]
    const line = raw.trim()
    if (!line) { index += 1; continue }

    const fence = line.match(/^```([a-z0-9_-]*)\s*$/i)
    if (fence) {
      const code = []
      index += 1
      while (index < lines.length && !/^```\s*$/.test(lines[index].trim())) {
        code.push(lines[index])
        index += 1
      }
      if (index < lines.length) index += 1
      nodes.push(element('pre', [element('code', [textNode(code.join('\n'))])], {
        'data-language': String(fence[1] || 'text').toLowerCase()
      }))
      continue
    }

    const table = tableNode(lines, index)
    if (table) {
      nodes.push(table.node)
      index = table.next
      continue
    }

    const heading = line.match(/^(#{1,6})\s+(.+)$/)
    if (heading) {
      nodes.push(element(`h${heading[1].length}`, inlineNodes(heading[2])))
      index += 1
      continue
    }

    const list = line.match(/^([-*]|\d+[.)])\s+(.+)$/)
    if (list) {
      const ordered = /^\d/.test(list[1])
      const items = []
      while (index < lines.length) {
        const item = lines[index].trim().match(ordered ? /^\d+[.)]\s+(.+)$/ : /^[-*]\s+(.+)$/)
        if (!item) break
        items.push(element('li', inlineNodes(item[1])))
        index += 1
      }
      nodes.push(element(ordered ? 'ol' : 'ul', items))
      continue
    }

    const paragraph = []
    while (index < lines.length && lines[index].trim()) {
      if (paragraph.length && (/^(?:#{1,6}\s|```|[-*]\s|\d+[.)]\s)/.test(lines[index].trim()))) break
      paragraph.push(lines[index].trim())
      index += 1
    }
    const content = stripRawHtml(paragraph.join(' ')).trim()
    if (content) nodes.push(element('p', inlineNodes(content)))
  }
  return nodes
}
