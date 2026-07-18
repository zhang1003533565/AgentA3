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
