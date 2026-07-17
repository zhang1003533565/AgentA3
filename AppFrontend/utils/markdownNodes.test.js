const assert = require('node:assert/strict')
const { readFileSync } = require('node:fs')
const { join } = require('node:path')
const test = require('node:test')

const source = readFileSync(join(__dirname, 'markdownNodes.js'), 'utf8')
const modulePromise = import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`)

function walk(nodes) {
  const result = []
  for (const node of Array.isArray(nodes) ? nodes : []) {
    result.push(node)
    result.push(...walk(node.children))
  }
  return result
}

test('markdown converts learning content into the strict rich-text node set', async () => {
  const { markdownToNodes, SAFE_MARKDOWN_NODE_NAMES } = await modulePromise
  const nodes = markdownToNodes([
    '# 列表切片',
    '',
    '这是 **重点**，使用 `values[1:]`。',
    '',
    '- 左闭右开',
    '- 支持负索引',
    '',
    '| 写法 | 结果 |',
    '| --- | --- |',
    '| a[1:] | 尾部 |',
    '',
    '```python',
    'print([1, 2, 3][1:])',
    '```'
  ].join('\n'))
  const all = walk(nodes)

  assert.ok(all.some(node => node.name === 'h1'))
  assert.ok(all.some(node => node.name === 'ul'))
  assert.ok(all.some(node => node.name === 'table'))
  assert.ok(all.some(node => node.name === 'pre' && node.attrs?.['data-language'] === 'python'))
  assert.ok(all.every(node => !node.name || SAFE_MARKDOWN_NODE_NAMES.has(node.name)))
})

test('markdown ignores raw html and rejects unsafe link schemes', async () => {
  const { markdownToNodes } = await modulePromise
  const nodes = markdownToNodes([
    '<img src=x onerror=alert(1)>',
    '[危险链接](javascript:alert(1))',
    '[数据链接](data:text/html,unsafe)',
    '[课程依据](https://example.edu/python)'
  ].join('\n'))
  const all = walk(nodes)
  const links = all.filter(node => node.name === 'a')
  const text = JSON.stringify(nodes)

  assert.equal(links.length, 1)
  assert.equal(links[0].attrs.href, 'https://example.edu/python')
  assert.doesNotMatch(text, /onerror|javascript:|data:text/i)
})

test('mermaid fences remain source code and are never treated as executable html', async () => {
  const { markdownToNodes } = await modulePromise
  const nodes = markdownToNodes('```mermaid\ngraph TD\nA-->B\n```')
  const pre = walk(nodes).find(node => node.name === 'pre')

  assert.equal(pre.attrs['data-language'], 'mermaid')
  assert.match(JSON.stringify(pre.children), /graph TD/)
})

test('markdown renders quotes, rules, strike-through and non-interactive task lists', async () => {
  const { markdownToNodes, SAFE_MARKDOWN_NODE_NAMES } = await modulePromise
  const nodes = markdownToNodes([
    '> Quoted **answer**',
    '>',
    '> - quoted item',
    '',
    '---',
    '',
    'Keep ~~obsolete~~ current.',
    '',
    '- [x] completed',
    '- [ ] pending',
    '  continuation line',
    '  - nested item',
    '    nested continuation',
    '',
    '1. ordered item',
    '   1. nested ordered item'
  ].join('\n'))
  const all = walk(nodes)
  const taskItems = all.filter(node => node.name === 'li' && node.attrs?.['data-task-state'])
  const lists = all.filter(node => node.name === 'ul' || node.name === 'ol')
  const serialized = JSON.stringify(nodes)

  assert.ok(all.some(node => node.name === 'blockquote'))
  assert.ok(all.some(node => node.name === 'hr'))
  assert.ok(all.some(node => node.name === 's'))
  assert.equal(taskItems.length, 2)
  assert.equal(taskItems[0].attrs['data-task-state'], 'checked')
  assert.equal(taskItems[1].attrs['data-task-state'], 'unchecked')
  assert.ok(lists.length >= 4)
  assert.match(serialized, /☑/)
  assert.match(serialized, /☐/)
  assert.match(serialized, /continuation line/)
  assert.match(serialized, /nested continuation/)
  assert.ok(all.every(node => node.name !== 'input'))
  assert.ok(all.every(node => !node.name || SAFE_MARKDOWN_NODE_NAMES.has(node.name)))
})

test('table and code output expose mobile scroll and text selection affordances', async () => {
  const { markdownToNodes } = await modulePromise
  const nodes = markdownToNodes('| A | B |\n| --- | --- |\n| one | two |\n\n```js\nconst value = 1\n```')
  const all = walk(nodes)
  const wrapper = all.find(node => node.name === 'div' && node.attrs?.class === 'markdown-table-scroll')

  assert.ok(wrapper)
  assert.ok(walk(wrapper.children).some(node => node.name === 'table'))
  assert.ok(all.some(node => node.name === 'pre' && node.attrs?.['data-language'] === 'js'))

  const component = readFileSync(join(__dirname, '../components/safe-markdown/safe-markdown.vue'), 'utf8')
  assert.match(component, /<rich-text[^>]+selectable/)
  assert.match(component, /markdown-table-scroll[\s\S]*overflow-x:\s*auto/)
  assert.match(component, /white-space:\s*pre/)
  assert.match(component, /user-select:\s*text/)
  assert.doesNotMatch(component, /v-html/)
})

test('safe markdown keeps raw html and protocol-relative links inert', async () => {
  const { markdownToNodes, safeMarkdownHref } = await modulePromise
  assert.equal(safeMarkdownHref('//unsafe.example'), '')
  assert.equal(safeMarkdownHref('/' + '\\' + 'unsafe.example'), '')
  assert.equal(safeMarkdownHref('/safe/path'), '/safe/path')

  const nodes = markdownToNodes('<blockquote onclick="steal()">visible text</blockquote>')
  const serialized = JSON.stringify(nodes)
  assert.doesNotMatch(serialized, /onclick|steal/)
  assert.doesNotMatch(serialized, /"name":"blockquote"/)
  assert.match(serialized, /visible text/)
})
