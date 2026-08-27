const assert = require('node:assert/strict')
const { readFileSync } = require('node:fs')
const { join } = require('node:path')
const test = require('node:test')

const source = readFileSync(join(__dirname, 'goalDecompose.vue'), 'utf8')

test('execution task rows do not expose an unexplained draggable progress control', () => {
  assert.doesNotMatch(source, /<slider\b/)
  assert.match(source, /延期 1 天/)
})
