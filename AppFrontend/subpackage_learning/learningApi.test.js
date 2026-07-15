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
  const start = source.indexOf('export async function streamSse')
  const end = source.indexOf('\nexport function getLeaderSessions', start)
  assert.ok(start >= 0 && end > start, 'streamSse implementation should remain extractable')
  const implementation = source
    .slice(start, end)
    .replace('export async function streamSse', 'async function streamSse')
  return new Function('getToken', 'BASE_URL', `${implementation}\nreturn streamSse`)(() => '', 'https://example.test')
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
  assert.match(source, /export async function streamSse\s*\(/)
  assert.match(source, /'complete'/)
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
