import assert from 'node:assert/strict'
import test from 'node:test'

import { resolveApiBaseUrl } from './apiBase.js'

test('defaults API mode to local backend', () => {
  assert.equal(resolveApiBaseUrl({}), 'http://localhost:8080')
})

test('uses same-origin API routes for deployed single-server web', () => {
  assert.equal(resolveApiBaseUrl({ VITE_API_MODE: 'relative' }), '')
})

test('uses explicit remote API base only in remote mode', () => {
  assert.equal(resolveApiBaseUrl({
    VITE_API_MODE: 'remote',
    VITE_API_BASE_URL: ' http://120.27.207.149 ',
  }), 'http://120.27.207.149')
})

test('falls back to local when remote mode has no URL', () => {
  assert.equal(resolveApiBaseUrl({ VITE_API_MODE: 'remote' }), 'http://localhost:8080')
})
