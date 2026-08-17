const assert = require('node:assert/strict')
const { readFileSync } = require('node:fs')
const { join } = require('node:path')
const test = require('node:test')

function loadPptApi(request, streamSse) {
  const source = readFileSync(join(__dirname, '../api/ppt.js'), 'utf8')
    .replace(/^import .*$/gm, '')
    .replace(/export const /g, 'const ')
    .replace(/export function /g, 'function ')
  return new Function('request', 'streamSse', 'BASE_URL', 'PPT_OPTIONS_BYPASS_CACHE', 'getToken', 'uni', `${source}
    return { getPptOptions, generatePptOutline, generatePptSlides, createPptSlidesTask, createPptTask, getPptTask, retryPptTask, streamPptTask }
  `)(request, streamSse, 'https://example.test', false, () => 'token', {})
}

test('PPT API maps the confirmed app workflow to authenticated backend routes', async () => {
  const requests = []
  const streams = []
  const api = loadPptApi(
    async options => { requests.push(options); return options },
    (...args) => { streams.push(args); return { done: Promise.resolve() } }
  )

  await api.generatePptOutline({ sourceName: 'a.txt', sourceContent: '资料' })
  await api.generatePptSlides({ outline: { items: [] } })
  await api.createPptSlidesTask({ outline: { items: [{}, {}] } })
  await api.createPptTask({ slides: [{}, {}] })
  await api.getPptTask('ppt task/1')
  await api.retryPptTask('ppt task/1')
  api.streamPptTask('ppt task/1', { onEvent() {} })

  assert.deepEqual(requests.map(item => [item.method, item.url]), [
    ['POST', '/api/app/ai/ppt/outlines'],
    ['POST', '/api/app/ai/ppt/slides'],
    ['POST', '/api/app/ai/ppt/slides/tasks'],
    ['POST', '/api/app/ai/ppt/tasks'],
    ['GET', '/api/app/ai/ppt/tasks/ppt%20task%2F1'],
    ['POST', '/api/app/ai/ppt/tasks/ppt%20task%2F1/retry']
  ])
  assert.deepEqual(requests.slice(0, 4).map(item => item.timeout), [300000, 300000, 120000, 300000])
  assert.equal(streams[0][0], '/api/app/ai/ppt/tasks/ppt%20task%2F1/stream')
  assert.equal(streams[0][4], 'GET')
})

test('PPT options are requested once and reused from local cache', async () => {
  const requests = []
  const storage = new Map()
  const uni = {
    getStorageSync: key => storage.get(key),
    setStorageSync: (key, value) => storage.set(key, value)
  }
  const source = readFileSync(join(__dirname, '../api/ppt.js'), 'utf8')
    .replace(/^import .*$/gm, '')
    .replace(/export const /g, 'const ')
    .replace(/export function /g, 'function ')
  const api = new Function('request', 'streamSse', 'BASE_URL', 'PPT_OPTIONS_BYPASS_CACHE', 'getToken', 'uni', `${source}
    return { getPptOptions }
  `)(async options => {
    requests.push(options)
    return { data: {
      scenes: [{ value: 'review', label: '复习资料' }],
      templates: [{ id: 'general', name: '简约通用' }],
      cacheTtlSeconds: 86400
    } }
  }, () => {}, '', false, () => '', uni)

  const first = await api.getPptOptions()
  const second = await api.getPptOptions()

  assert.equal(requests.length, 1)
  assert.equal(requests[0].url, '/api/app/ai/ppt/options')
  assert.deepEqual(second, first)
})

test('PPT cache debug switch bypasses and clears stored options', async () => {
  const requests = []
  const storage = new Map([
    ['aiPptOptions:v1', { data: { scenes: [{ value: 'old' }] }, expiresAt: Date.now() + 60000 }],
    ['aiPptOptions:v3', { data: { scenes: [{ value: 'old' }] }, expiresAt: Date.now() + 60000 }]
  ])
  const uni = {
    getStorageSync: key => storage.get(key),
    setStorageSync: (key, value) => storage.set(key, value),
    removeStorageSync: key => storage.delete(key)
  }
  const source = readFileSync(join(__dirname, '../api/ppt.js'), 'utf8')
    .replace(/^import .*$/gm, '')
    .replace(/export const /g, 'const ')
    .replace(/export function /g, 'function ')
  const api = new Function('request', 'streamSse', 'BASE_URL', 'PPT_OPTIONS_BYPASS_CACHE', 'getToken', 'uni', `${source}
    return { getPptOptions }
  `)(async options => {
    requests.push(options)
    return { data: {
      scenes: [{ value: 'review', label: '复习资料' }],
      templates: [{ id: 'general', name: '简约通用' }],
      cacheTtlSeconds: 86400
    } }
  }, () => {}, '', true, () => '', uni)

  await api.getPptOptions()
  await api.getPptOptions()

  assert.equal(requests.length, 2)
  assert.equal(storage.has('aiPptOptions:v1'), false)
  assert.equal(storage.has('aiPptOptions:v3'), false)
})

test('PPT options do not cache an incomplete response without templates', async () => {
  const storage = new Map()
  const uni = {
    getStorageSync: key => storage.get(key),
    setStorageSync: (key, value) => storage.set(key, value)
  }
  const source = readFileSync(join(__dirname, '../api/ppt.js'), 'utf8')
    .replace(/^import .*$/gm, '')
    .replace(/export const /g, 'const ')
    .replace(/export function /g, 'function ')
  const api = new Function('request', 'streamSse', 'BASE_URL', 'PPT_OPTIONS_BYPASS_CACHE', 'getToken', 'uni', `${source}
    return { getPptOptions }
  `)(async () => ({ data: { scenes: [{ value: 'review' }] } }), () => {}, '', false, () => '', uni)

  await assert.rejects(api.getPptOptions(), /PPT 模板配置为空/)
  assert.equal(storage.has('aiPptOptions:v4'), false)
})
