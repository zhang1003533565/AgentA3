const assert = require('node:assert/strict')
const { readFileSync } = require('node:fs')
const { join } = require('node:path')
const test = require('node:test')

const source = readFileSync(join(__dirname, 'goalPlanList.vue'), 'utf8')

test('plan history keeps the primary entry action separate from secondary deletion', () => {
  assert.match(source, /class="[^"]*plan-card--quiet[^"]*"/)
  assert.match(source, /class="plan-summary"/)
  assert.match(source, /class="plan-actions"/)
  assert.match(source, /class="delete-plan-link delete-plan-link--quiet"/)
})
