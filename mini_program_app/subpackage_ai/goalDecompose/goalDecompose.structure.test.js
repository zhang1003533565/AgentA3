const assert = require('node:assert/strict')
const { readFileSync } = require('node:fs')
const { join } = require('node:path')
const test = require('node:test')

const source = readFileSync(join(__dirname, 'goalDecompose.vue'), 'utf8')

test('execution task rows do not expose an unexplained draggable progress control', () => {
  assert.doesNotMatch(source, /<slider\b/)
  assert.match(source, /延期 1 天/)
  assert.match(source, /细分任务/)
  assert.match(source, /toggleSubtask/)
  assert.match(source, /task-group-head/)
  assert.match(source, /AI 补全/)
  assert.match(source, /expandStudyGoalSubtasks/)
})

test('study plan workflow exposes a deliberate hierarchy for preview and execution layouts', () => {
  assert.match(source, /class="[^"]*study-plan-page[^"]*"/)
  assert.match(source, /class="[^"]*preview-task-list[^"]*"/)
  assert.match(source, /class="[^"]*goal-summary-card[^"]*"/)
  assert.match(source, /class="[^"]*task-list-card[^"]*"/)
  assert.match(source, /class="task-group task-group--rail"/)
  assert.match(source, /class="[^"]*filter-card--compact[^"]*"/)
})
