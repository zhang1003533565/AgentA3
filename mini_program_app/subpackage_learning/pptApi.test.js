const assert = require('node:assert/strict')
const { readFileSync } = require('node:fs')
const { join } = require('node:path')
const test = require('node:test')

function loadPptApi(request, streamSse) {
  const source = readFileSync(join(__dirname, '../api/ppt.js'), 'utf8')
    .replace(/^import .*$/gm, '')
    .replace(/export const /g, 'const ')
    .replace(/export function /g, 'function ')
  return new Function('request', 'streamSse', 'BASE_URL', 'getToken', 'uni', `${source}
    return { generatePptOutline, generatePptSlides, createPptTask, getPptTask, streamPptTask }
  `)(request, streamSse, 'https://example.test', () => 'token', {})
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
  await api.createPptTask({ slides: [{}, {}] })
  await api.getPptTask('ppt task/1')
  api.streamPptTask('ppt task/1', { onEvent() {} })

  assert.deepEqual(requests.map(item => [item.method, item.url]), [
    ['POST', '/api/app/ai/ppt/outlines'],
    ['POST', '/api/app/ai/ppt/slides'],
    ['POST', '/api/app/ai/ppt/tasks'],
    ['GET', '/api/app/ai/ppt/tasks/ppt%20task%2F1']
  ])
  assert.equal(streams[0][0], '/api/app/ai/ppt/tasks/ppt%20task%2F1/stream')
  assert.equal(streams[0][4], 'GET')
})
