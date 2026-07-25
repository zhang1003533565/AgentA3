const assert = require('node:assert/strict')
const { readFileSync } = require('node:fs')
const { join } = require('node:path')
const test = require('node:test')

function loadLearningApi(request, streamSse) {
  const source = readFileSync(join(__dirname, '../api/learning.js'), 'utf8')
    .replace(/^import .*$/gm, '')
    .replace(/export const /g, 'const ')
  const names = [
    'getPythonHome', 'submitProfileAnswer', 'getPythonPath', 'getPythonRecommendations',
    'getLearningWorkflow', 'retryLearningResource', 'startPathItem', 'completePathItem',
    'replanPythonPath', 'recordRecommendationInteraction', 'streamLearningResources'
  ]
  return new Function('request', 'streamSse', `${source}\nreturn { ${names.join(', ')} }`)(request, streamSse)
}

function loadSharedStreamSse() {
  const source = readFileSync(join(__dirname, '../api/ai.js'), 'utf8')
  const start = source.indexOf('export function streamSse')
  const end = source.indexOf('\nexport function getLeaderSessions', start)
  assert.ok(start >= 0 && end > start, 'streamSse implementation should remain extractable')
  const implementation = source
    .slice(start, end)
    .replace('export function streamSse', 'function streamSse')
  return new Function('getToken', 'BASE_URL', `${implementation}\nreturn streamSse`)(() => '', 'https://example.test')
}

function loadStreamLeaderAgent(streamSse) {
  const source = readFileSync(join(__dirname, '../api/ai.js'), 'utf8')
  const start = source.indexOf('export function streamLeaderAgent')
  const end = source.indexOf('\nexport function getProfileRadarSnapshot', start)
  assert.ok(start >= 0 && end > start, 'streamLeaderAgent implementation should remain extractable')
  const implementation = source
    .slice(start, end)
    .replace('export function streamLeaderAgent', 'function streamLeaderAgent')
  return new Function('streamSse', `${implementation}\nreturn streamLeaderAgent`)(streamSse)
}

test('learning API exposes the authenticated Python learning facade', async () => {
  const calls = []
  const api = loadLearningApi(
    async options => { calls.push(options); return options },
    async () => {}
  )

  await api.getPythonHome()
  await api.submitProfileAnswer({ questionId: 'python_goal', answer: '掌握切片' })
  await api.getPythonPath()
  await api.getPythonRecommendations()
  await api.getLearningWorkflow('wf /1')
  await api.retryLearningResource('wf /1', 'code_lab')
  await api.startPathItem(12)
  await api.completePathItem(12)
  await api.replanPythonPath()
  await api.recordRecommendationInteraction(12, { action: 'complete' })

  assert.deepEqual(calls.map(call => [call.method || 'GET', call.url]), [
    ['GET', '/api/app/learning/courses/python/home'],
    ['POST', '/api/app/learning/courses/python/profile-answers'],
    ['GET', '/api/app/learning/courses/python/path'],
    ['GET', '/api/app/learning/courses/python/recommendations'],
    ['GET', '/api/app/learning/workflows/wf%20%2F1'],
    ['POST', '/api/app/learning/workflows/wf%20%2F1/resources/code_lab/retry'],
    ['POST', '/api/app/learning/path-items/12/start'],
    ['POST', '/api/app/learning/path-items/12/complete'],
    ['POST', '/api/app/learning/courses/python/path/replan'],
    ['POST', '/api/app/learning/recommendations/12/interactions']
  ])
})

test('learning stream reuses the shared SSE parser and stable endpoint', async () => {
  const calls = []
  const handlers = { onEvent() {} }
  const api = loadLearningApi(async () => {}, async (...args) => { calls.push(args); return 'controller' })

  const result = await api.streamLearningResources({ courseKey: 'python' }, handlers)

  assert.equal(result, 'controller')
  assert.equal(calls[0][0], '/api/app/learning/resources/generate/stream')
  assert.deepEqual(calls[0][1], { courseKey: 'python' })
  assert.equal(calls[0][2], handlers)
  assert.match(calls[0][3], /学习资源生成进度/)
})

test('shared AI API exports SSE and accepts complete resource interactions', () => {
  const source = readFileSync(join(__dirname, '../api/ai.js'), 'utf8')
  assert.match(source, /export function streamSse\s*\(/)
  assert.match(source, /export function streamLeaderAgent\s*\(/)
  assert.match(source, /'complete'/)
})

test('leader stream returns the shared abortable task without async assimilation', () => {
  const calls = []
  const task = { abort() {}, done: Promise.resolve() }
  const streamLeaderAgent = loadStreamLeaderAgent((...args) => {
    calls.push(args)
    return task
  })
  const handlers = { onDelta() {} }

  const result = streamLeaderAgent({ sessionId: 's1', input: 'hello' }, handlers)

  assert.equal(result, task)
  assert.equal(calls[0][0], '/api/ai/leader/query/stream')
  assert.deepEqual(calls[0][1], {
    sessionId: 's1',
    input: 'hello',
    agentName: 'leader_agent',
    metadata: { source: 'app_ai_conversation' }
  })
  assert.equal(calls[0][2], handlers)
})

test('shared SSE exposes abort immediately and aborts an in-flight fetch', async () => {
  const originalFetch = globalThis.fetch
  let requestSignal
  globalThis.fetch = (_url, options) => {
    requestSignal = options.signal
    return new Promise((_resolve, reject) => {
      const rejectAbort = () => {
        const error = new Error('aborted')
        error.name = 'AbortError'
        reject(error)
      }
      if (requestSignal.aborted) rejectAbort()
      else requestSignal.addEventListener('abort', rejectAbort, { once: true })
    })
  }
  try {
    const streamSse = loadSharedStreamSse()
    const task = streamSse('/learning/stream', { courseKey: 'python' })

    assert.equal(typeof task.abort, 'function')
    assert.ok(task.done instanceof Promise)
    assert.equal(task.signal, requestSignal)
    assert.equal(task.abort('user_cancelled'), true)
    assert.equal(requestSignal.aborted, true)
    assert.equal(task.abort(), false)
    await assert.rejects(task.done, error => error.name === 'AbortError')
  } finally {
    globalThis.fetch = originalFetch
  }
})

test('shared SSE remains await-compatible and dispatches normal events', async () => {
  const originalFetch = globalThis.fetch
  const encoder = new TextEncoder()
  const chunks = [
    'event: session\ndata: {"sessionId":"s1"}\n\n',
    'event: delta\ndata: {"content":"hello"}\n\n',
    'event: status\ndata: {"stage":"answer"}\n\n',
    'event: done\ndata: {"answer":"hello"}\n\n'
  ].map(value => encoder.encode(value))
  let chunkIndex = 0
  let released = false
  globalThis.fetch = async () => ({
    ok: true,
    status: 200,
    body: {
      getReader() {
        return {
          async read() {
            if (chunkIndex >= chunks.length) return { done: true }
            return { done: false, value: chunks[chunkIndex++] }
          },
          releaseLock() {
            released = true
          }
        }
      }
    }
  })
  try {
    const events = []
    const streamSse = loadSharedStreamSse()
    const task = streamSse('/learning/stream', {}, {
      onSession: payload => events.push(['session', payload]),
      onDelta: content => events.push(['delta', content]),
      onDone: payload => events.push(['done', payload]),
      onEvent: (name, payload) => events.push([name, payload])
    })

    const awaitedResult = await task

    assert.equal(awaitedResult, task.controller)
    assert.equal(released, true)
    assert.deepEqual(events, [
      ['session', { sessionId: 's1' }],
      ['delta', 'hello'],
      ['status', { stage: 'answer' }],
      ['done', { answer: 'hello' }]
    ])
  } finally {
    globalThis.fetch = originalFetch
  }
})

test('shared SSE fallback is still delivered through await and marks normal-request fallback', async () => {
  const originalFetch = globalThis.fetch
  globalThis.fetch = undefined
  try {
    const streamSse = loadSharedStreamSse()
    const task = streamSse('/learning/stream', {}, {}, 'stream unsupported')

    assert.equal(typeof task.abort, 'function')
    await assert.rejects(task, error => {
      assert.equal(error.message, 'stream unsupported')
      assert.equal(error.fallbackToNormalRequest, true)
      return true
    })
  } finally {
    globalThis.fetch = originalFetch
  }
})

test('shared SSE errors retain the HTTP status used by learning six-state classification', async () => {
  const originalFetch = globalThis.fetch
  globalThis.fetch = async () => ({ ok: false, status: 503 })
  try {
    const streamSse = loadSharedStreamSse()
    await assert.rejects(
      streamSse('/learning/stream', { courseKey: 'python' }),
      error => {
        assert.equal(error.status, 503)
        assert.equal(error.statusCode, 503)
        return true
      }
    )
  } finally {
    globalThis.fetch = originalFetch
  }
})
