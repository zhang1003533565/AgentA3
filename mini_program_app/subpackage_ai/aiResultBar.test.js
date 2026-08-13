const assert = require('node:assert/strict')
const { readFileSync } = require('node:fs')
const { join } = require('node:path')
const test = require('node:test')

test('ai result bar can collapse without removing actions', () => {
  const component = readFileSync(join(__dirname, 'components/AiResultBar.vue'), 'utf8')

  assert.match(component, /import \{ ref \} from 'vue'/)
  assert.match(component, /const collapsed = ref\(false\)/)
  assert.match(component, /ai-bar--collapsed/)
  assert.match(component, /collapsed = true/)
  assert.match(component, /collapsed = false/)
  assert.match(component, /\$emit\('export'\)/)
  assert.match(component, /\$emit\('optimize'\)/)
  assert.match(component, /\$emit\('share'\)/)
  assert.match(component, />工具</)
})
