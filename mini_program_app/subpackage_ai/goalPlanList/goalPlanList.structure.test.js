const assert = require('node:assert/strict')
const { readFileSync } = require('node:fs')
const { join } = require('node:path')
const test = require('node:test')

const source = readFileSync(join(__dirname, 'goalPlanList.vue'), 'utf8')

test('plan history keeps the primary entry action separate from secondary deletion', () => {
  assert.match(source, /class="section-card current-plan-card"/)
  assert.match(source, /class="section-card history-list"/)
  assert.match(source, /class="plan-actions history-plan-actions"/)
  assert.match(source, /class="plan-action plan-action--view"/)
  assert.match(source, /class="plan-action plan-action--delete"/)
  assert.match(source, /confirmDelete\(item\)/)
})

test('plan list separates the current plan from history and keeps both actions visible', () => {
  assert.match(source, /currentPlan/)
  assert.match(source, /当前计划/)
  assert.match(source, /继续学习/)
  assert.match(source, /historyPlans/)
  assert.match(source, /历史计划/)
  assert.match(source, /class="plan-action plan-action--view"/)
  assert.match(source, /class="plan-action plan-action--delete"/)
})

test('current plan exposes a visible delete action with the same confirmation flow', () => {
  assert.match(source, /class="plan-action plan-action--delete current-plan-delete"/)
  assert.match(source, /@tap\.stop="confirmDelete\(currentPlan\)"/)
})

test('current plan highlights the first unfinished task when the summary provides it', () => {
  assert.match(source, /function currentPlanNextAction\(item\) \{[\s\S]*?if \(item\.nextTaskName\) return item\.nextTaskName/)
})

test('plan list actions use outlined surfaces instead of filled buttons', () => {
  assert.match(source, /\.plan-action--view\s*\{[\s\S]*?background:\s*#FFFFFF[\s\S]*?border:/)
  assert.match(source, /\.plan-action--delete\s*\{[\s\S]*?background:\s*#FFFFFF[\s\S]*?border:/)
})

test('history count reflects the server total and deletion refreshes the first page', () => {
  assert.match(source, /const historyCount = computed\(\(\) => Math\.max\(0, total\.value - \(currentPlan\.value \? 1 : 0\)\)\)/)
  assert.match(source, /class="history-count">\{\{ historyCount \}\}/)
  assert.match(source, /deleteStudyGoal\(item\.id\)\.then\(\(\) => fetchPage\(1, false\)\)/)
})
