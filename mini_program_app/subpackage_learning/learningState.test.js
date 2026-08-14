const assert = require('node:assert/strict')
const { readFileSync } = require('node:fs')
const { join } = require('node:path')
const test = require('node:test')

const source = readFileSync(join(__dirname, 'learningState.js'), 'utf8')
const modulePromise = import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`)

test('learning progress stays monotonic and resources are keyed by reviewed type', async () => {
  const { createLearningState, reduceLearningEvent } = await modulePromise
  let state = createLearningState('wf-1')
  state = reduceLearningEvent(state, 'agent_done', {
    workflowId: 'wf-1', progress: 49, resourceType: 'code_lab', resource: { id: 'r1' }
  })
  state = reduceLearningEvent(state, 'retrieval', { workflowId: 'wf-1', progress: 15 })

  assert.equal(state.progress, 49)
  assert.equal(state.resources.code_lab.id, 'r1')
  assert.equal(state.stage, 'retrieval')
  assert.equal(state.trace.length, 2)
})

test('workflow snapshots restore the persisted agent trace', async () => {
  const { createLearningState, restoreLearningState } = await modulePromise
  const trace = [{ sequence: 1, eventName: 'planning_done', status: 'completed' }]
  const restored = restoreLearningState(createLearningState('wf-trace'), {
    workflowId: 'wf-trace', trace
  })
  assert.deepEqual(restored.trace, trace)
})

test('events from a different workflow cannot corrupt the active generation', async () => {
  const { createLearningState, reduceLearningEvent } = await modulePromise
  const initial = createLearningState('wf-current')
  const next = reduceLearningEvent(initial, 'done', {
    workflowId: 'wf-old', progress: 100, resources: { knowledge_note: { id: 'old' } }
  })

  assert.deepEqual(next, initial)
})

test('terminal and retry events produce honest recoverable states', async () => {
  const { createLearningState, reduceLearningEvent } = await modulePromise
  let state = createLearningState('wf-2')
  state = reduceLearningEvent(state, 'error', {
    workflowId: 'wf-2', progress: 62, resourceType: 'presentation', message: '课件生成失败', retryable: true
  })
  assert.equal(state.status, 'generation_failed')
  assert.equal(state.errors.presentation.retryable, true)

  state = reduceLearningEvent(state, 'agent_start', {
    workflowId: 'wf-2', progress: 62, resourceType: 'presentation'
  })
  assert.equal(state.status, 'generating')
  assert.equal(state.errors.presentation, undefined)

  state = reduceLearningEvent(state, 'done', {
    workflowId: 'wf-2', progress: 100, resources: { presentation: { id: 'ppt-1' } }
  })
  assert.equal(state.status, 'ready')
  assert.equal(state.progress, 100)
  assert.equal(state.resources.presentation.id, 'ppt-1')
})

test('workflow snapshot recovery keeps server facts and never lowers local progress', async () => {
  const { createLearningState, reduceLearningEvent, restoreLearningState } = await modulePromise
  const current = reduceLearningEvent(createLearningState('wf-3'), 'planning', {
    workflowId: 'wf-3', progress: 30
  })
  const restored = restoreLearningState(current, {
    workflowId: 'wf-3', stage: 'retrieval', progress: 15, status: 'generating',
    resources: { knowledge_note: { id: 'note-1' } }
  })

  assert.equal(restored.progress, 30)
  assert.equal(restored.resources.knowledge_note.id, 'note-1')
})

test('workflow recovery keys canonical assistant resources by reviewed metadata kind', async () => {
  const { createLearningState, restoreLearningState } = await modulePromise
  const restored = restoreLearningState(createLearningState('wf-4'), {
    workflowId: 'wf-4',
    status: 'COMPLETED',
    progress: 100,
    resources: [
      { id: 'code-1', kind: 'document', metadata: { resourceKind: 'code_lab', reviewStatus: 'passed' } },
      { id: 'ppt-1', payload: { resourceType: 'presentation' } }
    ]
  })

  assert.equal(restored.resources.code_lab.id, 'code-1')
  assert.equal(restored.resources.document, undefined)
  assert.equal(restored.resources.presentation.id, 'ppt-1')
})

test('a terminal package with a failed resource stays honestly partial and retryable', async () => {
  const { createLearningState, reduceLearningEvent } = await modulePromise
  let state = reduceLearningEvent(createLearningState('wf-5'), 'agent_failed', {
    workflowId: 'wf-5', resourceType: 'presentation', progress: 70, message: 'PPT 导出失败', retryable: true
  })
  state = reduceLearningEvent(state, 'done', {
    workflowId: 'wf-5', progress: 100, resources: { knowledge_note: { id: 'note-5' } }
  })

  assert.equal(state.progress, 100)
  assert.equal(state.status, 'generation_failed')
  assert.equal(state.resources.knowledge_note.id, 'note-5')
  assert.equal(state.errors.presentation.retryable, true)
})
