const assert = require('node:assert/strict')
const { readFileSync } = require('node:fs')
const { join } = require('node:path')
const test = require('node:test')

const source = readFileSync(join(__dirname, 'studyPlan.js'), 'utf8')
const modulePromise = import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`)

test('study plan scheduling creates consecutive inclusive date ranges', async () => {
  const { schedulePlanTasks } = await modulePromise
  const tasks = schedulePlanTasks([
    { taskName: 'A', estimatedDays: 2 },
    { taskName: 'B', estimatedDays: 3 }
  ], '2026-08-27')
  assert.deepEqual(tasks.map((task) => [task.plannedStartDate, task.plannedEndDate]), [
    ['2026-08-27', '2026-08-28'],
    ['2026-08-29', '2026-08-31']
  ])
})

test('study plan payload keeps mutually exclusive empty target date null', async () => {
  const { buildStudyGoalPayload } = await modulePromise
  const payload = buildStudyGoalPayload({ title: '目标' }, [{ taskName: '任务', estimatedDays: 0 }])
  assert.equal(payload.goal.targetDate, null)
  assert.equal(payload.tasks[0].estimatedDays, 1)
})

test('today matching uses the persisted inclusive schedule', async () => {
  const { isPlanTaskToday } = await modulePromise
  assert.equal(isPlanTaskToday({ plannedStartDate: '2026-08-27', plannedEndDate: '2026-08-28' }, '2026-08-28'), true)
  assert.equal(isPlanTaskToday({ plannedStartDate: '2026-08-27', plannedEndDate: '2026-08-28' }, '2026-08-29'), false)
})
