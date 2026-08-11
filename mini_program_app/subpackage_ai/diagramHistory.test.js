const assert = require('node:assert/strict')
const { readFileSync } = require('node:fs')
const { join } = require('node:path')
const test = require('node:test')

test('diagram history cards keep short excerpts while sheet keeps full description', () => {
  const page = readFileSync(join(__dirname, 'diagramHistory/diagramHistory.vue'), 'utf8')

  assert.match(page, /desc: cardDesc\(r, type\)/)
  assert.match(page, /fullDesc: fullDesc\(r, type\)/)
  assert.match(page, /\{\{ sheet\.fullDesc \|\| sheet\.desc \}\}/)
  assert.match(page, /const CARD_DESC_LIMIT = 34/)
  assert.match(page, /\.card \{ height: 232rpx;/)
  assert.match(page, /\.card-desc \{ display: block; width: 100%; max-width: 100%;/)
  assert.match(page, /\.sheet-desc \{[^}]*white-space: pre-wrap;/s)
})
