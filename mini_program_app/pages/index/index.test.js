const assert = require('node:assert/strict')
const { readFileSync } = require('node:fs')
const { join } = require('node:path')
const test = require('node:test')

const FRONTEND_ROOT = join(__dirname, '..', '..')

test('home starts with the hero and does not render a title bar', () => {
  const source = readFileSync(join(__dirname, 'index.vue'), 'utf8')
  const pages = JSON.parse(readFileSync(join(FRONTEND_ROOT, 'pages.json'), 'utf8'))
  const homePage = pages.pages.find((page) => page.path === 'pages/index/index')

  assert.ok(homePage)
  assert.equal(homePage.style.navigationStyle, 'custom')
  assert.equal(homePage.style.navigationBarTitleText, '')
  assert.doesNotMatch(source, /common-page-header/i)
  assert.match(source, /<view class="home-page">\s*<view class="hero-shell">/)
})

test('background unread refresh degrades without a user-facing error toast', () => {
  const store = readFileSync(join(FRONTEND_ROOT, 'utils', 'messageStore.js'), 'utf8')
  assert.match(store, /getAppMessageUnreadCount\(\{ showError: false \}\)/)
  assert.match(store, /if \(!getToken\(\)\) return getMessageState\(\)/)
})
