const assert = require('node:assert/strict')
const { readFileSync } = require('node:fs')
const { join } = require('node:path')
const test = require('node:test')

const source = readFileSync(join(__dirname, 'request.js'), 'utf8')
  .replace("import { getApiBaseUrl } from './config.js'", "const getApiBaseUrl = () => 'http://localhost:8080'")
  .replace("import { getToken, clearAuth } from './storage.js'", 'const getToken = () => \'token\'; const clearAuth = () => {}')
const modulePromise = import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`)

test('request remains await-compatible and exposes the native uni.request abort task', async () => {
  let requestOptions
  let nativeAbortCount = 0
  const toasts = []
  globalThis.uni = {
    request(options) {
      requestOptions = options
      return {
        abort() {
          nativeAbortCount += 1
          options.fail({ errMsg: 'request:fail abort' })
        }
      }
    },
    showToast: (options) => toasts.push(options),
    reLaunch() {}
  }
  const { request } = await modulePromise
  const task = request({ url: '/api/example', method: 'POST', data: { value: 1 } })

  assert.equal(typeof task.then, 'function')
  assert.equal(typeof task.abort, 'function')
  assert.equal(task.done, task)
  assert.equal(task.abort('user_cancelled'), true)
  await assert.rejects(task, (error) => error?.name === 'AbortError' && error.message === 'user_cancelled')
  assert.equal(nativeAbortCount, 1)
  assert.deepEqual(toasts, [])
  assert.equal(requestOptions.header.Authorization, 'Bearer token')
})

test('request resolves the existing response envelope and cannot abort after completion', async () => {
  globalThis.uni = {
    request(options) {
      options.success({ statusCode: 200, data: { code: 200, data: { ok: true } } })
      return { abort() { throw new Error('must not abort settled request') } }
    },
    showToast() {},
    reLaunch() {}
  }
  const { request } = await modulePromise
  const task = request({ url: '/api/example' })

  assert.deepEqual(await task, { code: 200, data: { ok: true } })
  assert.equal(task.abort(), false)
})

test('request rejects HTML bodies so a frontend page is not treated as map-places data', async () => {
  const toasts = []
  globalThis.uni = {
    request(options) {
      options.success({
        statusCode: 200,
        data: '<!doctype html><html><body>index</body></html>'
      })
      return { abort() {} }
    },
    showToast: (options) => toasts.push(options),
    reLaunch() {}
  }
  const { request } = await modulePromise
  await assert.rejects(request({ url: '/api/v1/map-places', showError: false }), (error) => (
    /网页而非 JSON/.test(error?.message || '')
  ))
  assert.equal(toasts.length, 0)
})

test('request strips undefined query params so map-places is not filtered empty', async () => {
  let requestOptions
  globalThis.uni = {
    request(options) {
      requestOptions = options
      options.success({ statusCode: 200, data: { code: 200, data: [] } })
      return { abort() {} }
    },
    showToast() {},
    reLaunch() {}
  }
  const { request } = await modulePromise
  await request({
    url: '/api/v1/map-places',
    method: 'GET',
    params: { keyword: undefined, status: 'ENABLED' }
  })
  assert.deepEqual(requestOptions.data, { status: 'ENABLED' })
})
