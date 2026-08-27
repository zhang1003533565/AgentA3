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

test('today view only exposes subtasks scheduled for today', () => {
  assert.match(source, /if \(activeFilter\.value === 'today'\)\s*\{\s*return subtasks\.filter\(\(subtask\) => isPlanTaskToday\(subtask\)\)/)
  assert.match(source, /class="[^"]*task-item--today[^"]*"/)
  assert.match(source, /今日 .*阶段共/)
  assert.match(source, /function isTodaySubtask\(subtask\)/)
})

test('task completion rollback restores the full previous task state', () => {
  assert.match(source, /const previous = \{\s*isCompleted: task\.isCompleted,\s*progressPercent: task\.progressPercent,\s*status: task\.status\s*\}/)
  assert.match(source, /\.catch\(\(\) => \{\s*Object\.assign\(task, previous\)/)
})

test('preview editors respect the supported stage and subtask limits', () => {
  assert.match(source, /const MAX_PREVIEW_TASKS = 30/)
  assert.match(source, /const MAX_PREVIEW_SUBTASKS = 6/)
  assert.match(source, /if \(previewTasks\.value\.length >= MAX_PREVIEW_TASKS\)/)
  assert.match(source, /if \(task\.subtasks\.length >= MAX_PREVIEW_SUBTASKS\)/)
})

test('collapse arrows use a centered geometry instead of a font glyph', () => {
  assert.match(source, /class="subtask-collapse-chevron"[^>]*:class="[^"]*subtask-collapse-chevron--expanded[^"]*"/)
  assert.match(source, /\.study-plan-page \.subtask-collapse-chevron\s*\{[\s\S]*?width:\s*12rpx[\s\S]*?height:\s*12rpx[\s\S]*?border-right:/)
  assert.match(source, /\.subtask-collapse-chevron--expanded\s*\{[\s\S]*?transform:\s*rotate\(-135deg\)/)
})

test('preview page follows the selected three-step editor hierarchy', () => {
  assert.match(source, /class="preview-workflow-stepper"/)
  assert.match(source, /输入资料/)
  assert.match(source, /AI拆解/)
  assert.match(source, /预览调整/)
  assert.match(source, /class="preview-stage-group"/)
  assert.match(source, /class="preview-stage-stats"/)
  assert.match(source, /class="[^"]*preview-action-bar[^"]*"/)
  assert.match(source, /确认并创建计划/)
})

test('preview page uses existing line icons for editable plan metadata', () => {
  assert.match(source, /static\/icons\/line\/edit-3\.svg/)
  assert.match(source, /static\/icons\/line\/calendar\.svg/)
  assert.match(source, /static\/icons\/line\/clock\.svg/)
})

test('preview stage metrics keep numbers and units in aligned value columns', () => {
  assert.match(source, /class="preview-stage-stat-value-line"/)
  assert.match(source, /class="preview-stage-stat-number"/)
  assert.match(source, /class="preview-stage-stat-unit"/)
  assert.match(source, /\.preview-stage-stat-value-line\s*\{[\s\S]*?min-width:\s*102rpx/)
  assert.match(source, /\.preview-stage-stat-number\s*\{[\s\S]*?width:\s*56rpx/)
  assert.match(source, /\.preview-stage-stat-unit\s*\{[\s\S]*?width:\s*28rpx/)
})

test('execution hierarchy gives long task copy the available width beside controls', () => {
  assert.match(source, /\.study-plan-page \.task-group-title-wrap\s*\{[\s\S]*?flex:\s*1[\s\S]*?min-width:\s*0/)
  assert.match(source, /\.study-plan-page \.task-group-title,[\s\S]*?\.study-plan-page \.task-group-meta\s*\{[\s\S]*?overflow-wrap:\s*anywhere/)
  assert.match(source, /\.study-plan-page \.task-body\s*\{[\s\S]*?flex:\s*1[\s\S]*?min-width:\s*0/)
})
