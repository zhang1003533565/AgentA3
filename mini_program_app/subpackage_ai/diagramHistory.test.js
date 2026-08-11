const assert = require('node:assert/strict')
const { readFileSync } = require('node:fs')
const { join } = require('node:path')
const test = require('node:test')

test('diagram history cards keep short excerpts while sheet keeps full description', () => {
  const page = readFileSync(join(__dirname, 'diagramHistory/diagramHistory.vue'), 'utf8')

  assert.match(page, /function normalizeRecord\(record = \{\}, type, fallback = \{\}\)/)
  assert.match(page, /desc: cardDesc\(raw, type\)/)
  assert.match(page, /fullDesc: fullDesc\(raw, type\)/)
  assert.match(page, /\{\{ sheetDescText \}\}/)
  assert.match(page, /const sheetCanExpandDesc = computed/)
  assert.match(page, /const SHEET_DESC_LIMIT = 108/)
  assert.match(page, /查看更多/)
  assert.doesNotMatch(page, /sheet-star/)
  assert.match(page, /const CARD_DESC_LIMIT = 34/)
  assert.match(page, /\.card \{ height: 232rpx;/)
  assert.match(page, /\.card-desc \{ display: block; width: 100%; max-width: 100%;/)
  assert.match(page, /\.sheet-desc \{[^}]*white-space: pre-wrap;/s)
})

test('diagram history regenerate restores the original generation state', () => {
  const page = readFileSync(join(__dirname, 'diagramHistory/diagramHistory.vue'), 'utf8')

  assert.match(page, /aiMindmapRestoreDraft/)
  assert.match(page, /aiFlowchartRestoreDraft/)
  assert.match(page, /aiArchitectureRestoreDraft/)
  assert.match(page, /function buildRestoreDraft/)
  assert.match(page, /uni\.setStorageSync\(restoreKey, buildRestoreDraft\(detail, s\.type\)\)/)
  assert.match(page, /\$\{GEN_PATH\[s\.type\]\}\?restore=1/)
})
