export const SAFE_MARKDOWN_NODE_NAMES = new Set([
  'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'p', 'strong', 'em', 'code', 'pre',
  'ul', 'ol', 'li', 'a', 'table', 'thead', 'tbody', 'tr', 'th', 'td', 'br',
  'blockquote', 'hr', 's', 'div'
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
  if (/^https?:\/\//i.test(href) || /^mailto:/i.test(href) || /^\/(?![\\/])/.test(href)) return href
  return ''
}

function inlineNodes(value) {
  const source = stripRawHtml(value)
  const pattern = /(`[^`\n]+`|~~[^~\n]+~~|\*\*[^*\n]+\*\*|\*[^*\n]+\*|\[[^\]\n]+\]\([^\s)]+\))/g
  const result = []
  let cursor = 0
  let match
  while ((match = pattern.exec(source)) !== null) {
    if (match.index > cursor) result.push(textNode(source.slice(cursor, match.index)))
    const token = match[0]
    if (token.startsWith('`')) {
      result.push(element('code', [textNode(token.slice(1, -1))]))
    } else if (token.startsWith('~~')) {
      result.push(element('s', inlineNodes(token.slice(2, -2))))
    } else if (token.startsWith('**')) {
      result.push(element('strong', inlineNodes(token.slice(2, -2))))
    } else if (token.startsWith('*')) {
      result.push(element('em', inlineNodes(token.slice(1, -1))))
    } else {
      const link = token.match(/^\[([^\]]+)\]\(([^)]+)\)$/)
      const href = safeMarkdownHref(link?.[2])
      if (href) result.push(element('a', inlineNodes(link[1]), { href }))
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

function indentation(value) {
  const whitespace = String(value || '').match(/^[ \t]*/)?.[0] || ''
  return whitespace.replace(/\t/g, '    ').length
}

function listMarker(value) {
  const match = String(value || '').match(/^([ \t]*)([-+*]|\d+[.)])[ \t]+(.*)$/)
  if (!match) return null
  return {
    indent: indentation(match[1]),
    ordered: /^\d/.test(match[2]),
    content: match[3]
  }
}

function taskItem(value) {
  const match = String(value || '').match(/^\[([ xX])\](?:[ \t]+(.*))?$/)
  if (!match) return null
  return { checked: match[1].toLowerCase() === 'x', content: match[2] || '' }
}

function horizontalRule(value) {
  const line = String(value || '').trim()
  return /^(?:\*[ \t]*){3,}$/.test(line) || /^(?:-[ \t]*){3,}$/.test(line) || /^(?:_[ \t]*){3,}$/.test(line)
}

function quoteNode(lines, start) {
  const quoted = []
  let cursor = start
  while (cursor < lines.length) {
    const match = lines[cursor].match(/^[ \t]{0,3}>[ \t]?(.*)$/)
    if (!match) break
    quoted.push(match[1])
    cursor += 1
  }
  return {
    node: element('blockquote', markdownToNodes(quoted.join('\n'))),
    next: cursor
  }
}

function listNode(lines, start) {
  const first = listMarker(lines[start])
  if (!first) return null
  const baseIndent = first.indent
  const ordered = first.ordered
  const items = []
  let hasTasks = false
  let cursor = start

  while (cursor < lines.length) {
    const marker = listMarker(lines[cursor])
    if (!marker || marker.indent < baseIndent || marker.ordered !== ordered) break
    if (marker.indent > baseIndent) {
      if (!items.length) break
      const nested = listNode(lines, cursor)
      if (!nested) break
      items[items.length - 1].children.push(nested.node)
      cursor = nested.next
      continue
    }

    const task = taskItem(marker.content)
    const children = task
      ? [textNode(task.checked ? '\u2611 ' : '\u2610 '), ...inlineNodes(task.content)]
      : inlineNodes(marker.content)
    const item = element('li', children, task ? {
      class: 'task-list-item',
      'data-task-state': task.checked ? 'checked' : 'unchecked'
    } : undefined)
    hasTasks = hasTasks || Boolean(task)
    items.push(item)
    cursor += 1

    while (cursor < lines.length) {
      const continuation = lines[cursor]
      const continuationText = continuation.trim()
      const nestedMarker = listMarker(continuation)

      if (nestedMarker) {
        if (nestedMarker.indent <= baseIndent) break
        const nested = listNode(lines, cursor)
        if (!nested) break
        item.children.push(nested.node)
        cursor = nested.next
        continue
      }

      if (!continuationText) {
        const nextLine = lines[cursor + 1]
        const nextMarker = listMarker(nextLine)
        const continuesItem = nextLine !== undefined && nextLine.trim() && (
          (nextMarker && nextMarker.indent > baseIndent) ||
          (!nextMarker && indentation(nextLine) > baseIndent)
        )
        if (!continuesItem) break
        item.children.push(element('br'))
        cursor += 1
        continue
      }

      if (indentation(continuation) <= baseIndent) break
      item.children.push(element('br'), ...inlineNodes(continuationText))
      cursor += 1
    }
  }

  return {
    node: element(ordered ? 'ol' : 'ul', items, hasTasks ? { class: 'task-list' } : undefined),
    next: cursor
  }
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
  const table = element('table', [
      element('thead', [headerRow]),
      element('tbody', bodyRows)
    ])
  return {
    node: element('div', [table], { class: 'markdown-table-scroll' }),
    next: cursor
  }
}

function startsBlock(lines, index) {
  const raw = lines[index] || ''
  const line = raw.trim()
  if (!line) return true
  return /^```[a-z0-9_-]*\s*$/i.test(line) ||
    /^(#{1,6})\s+/.test(line) ||
    /^[ \t]{0,3}>/.test(raw) ||
    horizontalRule(raw) ||
    Boolean(listMarker(raw)) ||
    (line.includes('|') && isTableDivider(lines[index + 1]))
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

    if (/^[ \t]{0,3}>/.test(raw)) {
      const quote = quoteNode(lines, index)
      nodes.push(quote.node)
      index = quote.next
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

    if (horizontalRule(raw)) {
      nodes.push(element('hr'))
      index += 1
      continue
    }

    const list = listNode(lines, index)
    if (list) {
      nodes.push(list.node)
      index = list.next
      continue
    }

    const paragraph = []
    while (index < lines.length && lines[index].trim()) {
      if (paragraph.length && startsBlock(lines, index)) break
      paragraph.push(lines[index].trim())
      index += 1
    }
    const content = stripRawHtml(paragraph.join(' ')).trim()
    if (content) nodes.push(element('p', inlineNodes(content)))
  }
  return nodes
}
