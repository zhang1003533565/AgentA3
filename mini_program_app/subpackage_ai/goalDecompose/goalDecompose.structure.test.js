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

test('preview layout gives long text explicit containers and natural wrapping rules', () => {
  assert.match(source, /class="[^"]*preview-goal-card[^"]*"/)
  assert.match(source, /class="[^"]*preview-goal-title-row[^"]*"/)
  assert.match(source, /class="[^"]*preview-summary[^"]*"/)
  assert.match(source, /class="subtask-editor__head preview-subtask-head"/)
  assert.match(source, /class="task-edit-desc preview-stage-desc"[^>]*auto-height/)
  assert.match(source, /class="task-edit-desc subtask-desc-input"[^>]*auto-height/)
  assert.match(source, /overflow-wrap:\s*anywhere/)
  assert.match(source, /white-space:\s*normal/)
})

test('preview and execution pages expose stage-level subtask collapse controls', () => {
  assert.match(source, /SUBTASK_AUTO_EXPAND_LIMIT\s*=\s*3/)
  assert.match(source, /togglePreviewSubtasks/)
  assert.match(source, /isPreviewSubtasksExpanded/)
  assert.match(source, /toggleTaskGroup/)
  assert.match(source, /isTaskGroupExpanded/)
  assert.match(source, /v-if="isPreviewSubtasksExpanded\(task\)"/)
  assert.match(source, /v-if="isTaskGroupExpanded\(task\)"/)
  assert.match(source, /class="[^"]*subtask-collapse-trigger[^"]*"/)
  assert.match(source, /class="[^"]*task-group-toggle[^"]*"/)
})

test('today view keeps the full matched stage visible as task context', () => {
  assert.match(source, /if \(activeFilter\.value === 'today'\) return subtasks/)
  assert.match(source, /class="[^"]*task-item--today[^"]*"/)
  assert.match(source, /今日 .*阶段共/)
  assert.match(source, /function isTodaySubtask\(subtask\)/)
})
