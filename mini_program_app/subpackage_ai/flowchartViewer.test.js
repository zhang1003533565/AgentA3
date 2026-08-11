const assert = require('node:assert/strict')
const { readFileSync } = require('node:fs')
const { join } = require('node:path')
const test = require('node:test')

test('flowchart viewer shows generated title in shared nav bar without extra title toolbar', () => {
  const page = readFileSync(join(__dirname, 'flowchartViewer/flowchartViewer.vue'), 'utf8')

  assert.match(page, /:title="chart\.title \|\| 'AI 流程图'"/)
  assert.match(page, /:fixed="true"/)
  assert.match(page, /:placeholder="true"/)
  assert.doesNotMatch(page, /<view class="toolbar"/)
  assert.doesNotMatch(page, /diagram-title/)
  assert.doesNotMatch(page, /diagram-type/)
  assert.doesNotMatch(page, /title="AI 流程图"/)
})

test('flowchart optimization opens the optimized result route', () => {
  const page = readFileSync(join(__dirname, 'flowchartViewer/flowchartViewer.vue'), 'utf8')

  assert.match(page, /uni\.setStorageSync\('aiFlowchartPendingPayload', newPayload\)/)
  assert.match(page, /uni\.redirectTo\(\{ url: `\/subpackage_ai\/flowchartViewer\/flowchartViewer\?id=\$\{encodeURIComponent\(r\.id\)\}` \}\)/)
  assert.doesNotMatch(page, /uni\.showToast\(\{ title: '已更新流程图'/)
})
