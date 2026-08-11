const assert = require('node:assert/strict')
const { readFileSync } = require('node:fs')
const { join } = require('node:path')
const test = require('node:test')

test('architecture preview optimizes through sheet and think window instead of returning to generate page', () => {
  const page = readFileSync(join(__dirname, 'architecturePreview/architecturePreview.vue'), 'utf8')

  assert.match(page, /@optimize="openOptimizeSheet"/)
  assert.doesNotMatch(page, /@optimize="regenerate"/)
  assert.match(page, /title="优化架构图"/)
  assert.match(page, /type="architecture"/)
  assert.match(page, /generateArchitecture/)
  assert.match(page, /buildArchitecturePayload/)
  assert.match(page, /aiArchitecturePendingPayload/)
  assert.match(page, /aiArchitectureResult:\$\{result\.id\}/)
  assert.match(page, /uni\.redirectTo\(\{\s*url: `\/subpackage_ai\/architecturePreview\/architecturePreview\?recordId=/s)
})
